package com.pact.app.core

import org.json.JSONObject

/**
 * The wire protocol: what actually travels over the (swappable) transport.
 *
 * Every payload is SIGNED THEN SEALED:
 *  1. The sender builds a payload JSON with `type`, `id`, `from` (their
 *     Ed25519 public key), `nonce`, `ts`, `exp`, and a `body`.
 *  2. The canonical form is signed with the sender's Ed25519 key → `sig`.
 *  3. The whole thing is sealed to the recipient's X25519 public key.
 *
 * The transport only ever sees ciphertext and an inbox id.
 *
 * On receipt, [verifyInbound] enforces, in order:
 *  - decryption succeeds (payload was for us)
 *  - `from` is a pinned key we actually trust (kills key substitution)
 *  - signature over the canonical form is valid (kills forgery/tampering)
 *  - `exp` has not passed and `ts` is not absurdly far in the future
 *  - (`from`,`nonce`) never seen before (kills replay)
 */
object Wire {

    const val TYPE_PAIR_ACCEPT = "pair"
    const val TYPE_CHAT = "chat"
    const val TYPE_REQUEST = "request"
    const val TYPE_RESPONSE = "response"
    /** Periodic encrypted stats digest shared with contacts who may view stats. */
    const val TYPE_STATS = "stats"
    /** Challenge invites and answers. */
    const val TYPE_CHALLENGE = "challenge"
    /** Lightweight live "what am I up to" status shared with the squad. */
    const val TYPE_PRESENCE = "presence"

    /** Requests expire quickly: an approval is for *now*, not for later reuse. */
    const val REQUEST_TTL_MILLIS = 15 * 60 * 1000L
    const val MESSAGE_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
    const val MAX_CLOCK_SKEW_MILLIS = 15 * 60 * 1000L

    data class Payload(
        val type: String,
        val id: String,
        val fromSignPublic: ByteArray,
        val nonce: String,
        val ts: Long,
        val exp: Long,
        val body: JSONObject,
    )

    /** Canonical bytes that are signed — field order is fixed by construction. */
    private fun canonical(
        type: String,
        id: String,
        from: String,
        nonce: String,
        ts: Long,
        exp: Long,
        body: JSONObject,
    ): ByteArray = JSONObject()
        .put("t", type)
        .put("i", id)
        .put("f", from)
        .put("n", nonce)
        .put("ts", ts)
        .put("x", exp)
        .put("b", body.toString())
        .toString()
        .toByteArray(Charsets.UTF_8)

    /** Build, sign, and seal a payload for one recipient. Returns transport-ready Base64. */
    fun sealOutbound(
        type: String,
        id: String,
        body: JSONObject,
        ttlMillis: Long,
        identity: CryptoBox.Identity,
        recipientBoxPublic: ByteArray,
        now: Long = System.currentTimeMillis(),
    ): String {
        val from = B64.encode(identity.signPublic)
        val nonce = CryptoBox.randomNonce()
        val exp = now + ttlMillis
        val sig = CryptoBox.sign(canonical(type, id, from, nonce, now, exp, body), identity.signPrivate)
        val plain = JSONObject()
            .put("t", type)
            .put("i", id)
            .put("f", from)
            .put("n", nonce)
            .put("ts", now)
            .put("x", exp)
            .put("b", body.toString())
            .put("s", B64.encode(sig))
            .toString()
            .toByteArray(Charsets.UTF_8)
        return B64.encode(CryptoBox.seal(plain, recipientBoxPublic))
    }

    sealed class InboundResult {
        data class Valid(val payload: Payload) : InboundResult()
        /** A pairing message from a key we don't know yet — only TYPE_PAIR_ACCEPT may be unpinned. */
        data class ValidUnpinned(val payload: Payload) : InboundResult()
        data object Invalid : InboundResult()
        data object Replayed : InboundResult()
        data object Expired : InboundResult()
        data object UntrustedKey : InboundResult()
    }

    fun verifyInbound(
        sealedBase64: String,
        identity: CryptoBox.Identity,
        pinnedSignKeys: Set<String>,
        seenNonce: (from: String, nonce: String) -> Boolean,
        now: Long = System.currentTimeMillis(),
    ): InboundResult {
        val plain = runCatching { B64.decode(sealedBase64) }.getOrNull()
            ?.let { CryptoBox.open(it, identity.boxPrivate, identity.boxPublic) }
            ?: return InboundResult.Invalid
        val o = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }.getOrNull()
            ?: return InboundResult.Invalid

        val type = o.optString("t")
        val id = o.optString("i")
        val from = o.optString("f")
        val nonce = o.optString("n")
        val ts = o.optLong("ts")
        val exp = o.optLong("x")
        val bodyRaw = o.optString("b")
        val sig = o.optString("s")
        if (type.isEmpty() || id.isEmpty() || from.isEmpty() || nonce.isEmpty() || sig.isEmpty()) {
            return InboundResult.Invalid
        }

        val body = runCatching { JSONObject(bodyRaw) }.getOrNull() ?: return InboundResult.Invalid
        val fromBytes = runCatching { B64.decode(from) }.getOrNull() ?: return InboundResult.Invalid

        // Signature first: nothing below is trusted until this passes.
        val signedBytes = canonical(type, id, from, nonce, ts, exp, body)
        val sigBytes = runCatching { B64.decode(sig) }.getOrNull() ?: return InboundResult.Invalid
        if (!CryptoBox.verify(signedBytes, sigBytes, fromBytes)) return InboundResult.Invalid

        if (exp < now) return InboundResult.Expired
        if (ts > now + MAX_CLOCK_SKEW_MILLIS) return InboundResult.Invalid
        if (seenNonce(from, nonce)) return InboundResult.Replayed

        val payload = Payload(type, id, fromBytes, nonce, ts, exp, body)
        return if (from in pinnedSignKeys) {
            InboundResult.Valid(payload)
        } else if (type == TYPE_PAIR_ACCEPT) {
            InboundResult.ValidUnpinned(payload)
        } else {
            InboundResult.UntrustedKey
        }
    }
}

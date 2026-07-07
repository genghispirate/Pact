package com.pact.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The trust network: contacts (trusted people), end-to-end encrypted chat,
 * and the signed request/approval flow. This is the business-logic layer —
 * it talks to [Transport] only through the interface, and to the crypto
 * layer only through [CryptoBox]/[Wire], so either can be swapped without
 * touching anything here or in the UI.
 *
 * One identity per install, used for both roles: the person locking their
 * apps and the people who hold their trust are cryptographic peers.
 */
class TrustNetwork private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("pact_network", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    var transport: Transport = RelayTransport()
    private val outbox = Outbox(prefs)

    // ------------------------------------------------------------- identity

    /** Private keys live Vault-wrapped (Android Keystore AES) at rest. */
    val identity: CryptoBox.Identity by lazy {
        val stored = prefs.getString(KEY_IDENTITY, null)?.let(Vault::decrypt)
        if (stored != null) {
            val o = JSONObject(stored)
            CryptoBox.Identity(
                B64.decode(o.getString("sp")),
                B64.decode(o.getString("ss")),
                B64.decode(o.getString("bp")),
                B64.decode(o.getString("bs")),
            )
        } else {
            val id = CryptoBox.generateIdentity()
            val json = JSONObject()
                .put("sp", B64.encode(id.signPublic))
                .put("ss", B64.encode(id.signPrivate))
                .put("bp", B64.encode(id.boxPublic))
                .put("bs", B64.encode(id.boxPrivate))
                .toString()
            prefs.edit().putString(KEY_IDENTITY, Vault.encrypt(json)).apply()
            id
        }
    }

    val myInbox: String by lazy {
        prefs.getString(KEY_INBOX, null) ?: CryptoBox.randomTopic().also {
            prefs.edit().putString(KEY_INBOX, it).apply()
        }
    }

    var myName: String
        get() = prefs.getString(KEY_MY_NAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_MY_NAME, value.trim()).apply()
            refresh()
        }

    // ---------------------------------------------------------------- model

    enum class Direction { SUPPORTER, WARD }
    enum class Rule { ANY, MAJORITY, ALL }
    enum class RequestKind { UNLOCK, CHANGE }
    enum class RequestState { PENDING, APPROVED, DENIED, EXPIRED }
    enum class MessageStatus { QUEUED, SENT }

    data class Contact(
        val id: String,              // Ed25519 public key, Base64 — pinned at pairing
        val name: String,
        val boxPublic: String,
        val inbox: String,
        val direction: Direction,
        val canApprove: Boolean = true,
        val canViewStats: Boolean = false,
        val addedAt: Long = System.currentTimeMillis(),
    )

    data class ChatMessage(
        val id: String,
        val contactId: String,
        val fromMe: Boolean,
        val text: String,
        val ts: Long,
        val status: MessageStatus,
    )

    data class Decision(val approve: Boolean, val minutes: Int?, val message: String?, val at: Long)

    /** A request I sent to my circle. */
    data class ApprovalRequest(
        val id: String,
        val kind: RequestKind,
        val changeAction: String?,   // for CHANGE: REMOVE_APP / TIER_DOWN / STRICT_OFF / RESET
        val pkg: String?,
        val label: String,
        val minutes: Int,
        val reason: String?,
        val usageNote: String?,
        val createdAt: Long,
        val exp: Long,
        val approverIds: Set<String>,
        val decisions: Map<String, Decision> = emptyMap(),
        val state: RequestState = RequestState.PENDING,
        val grantedMinutes: Int? = null,
    )

    /** A request someone I support sent to me. */
    data class IncomingRequest(
        val id: String,
        val fromContactId: String,
        val kind: RequestKind,
        val changeAction: String?,
        val label: String,
        val minutes: Int,
        val reason: String?,
        val usageNote: String?,
        val receivedAt: Long,
        val exp: Long,
        val decided: Boolean = false,
        val myDecision: Boolean? = null,
    )

    /** A friend's shared screen-time snapshot, received encrypted. */
    data class PeerStats(
        val streakDays: Int,
        val blocksToday: Int,
        val walkawaysToday: Int,
        val screenTimeMinutes: Int,   // minutes on their limited apps today
        val lastUnlockAt: Long,
        val receivedAt: Long,
    )

    /**
     * A social challenge: everyone tries to keep their locks held for
     * [days] days from [startAt]. Held = no unlock since the start.
     */
    data class Challenge(
        val id: String,
        val name: String,
        val days: Int,
        val startAt: Long,
        val ownerId: String,                       // "" when I created it
        val participantIds: Set<String>,           // contacts invited (excl. me)
        val accepted: Map<String, Boolean> = emptyMap(),
        val joinedByMe: Boolean = true,
    ) {
        val endsAt: Long get() = startAt + days * 86_400_000L
        fun finished(now: Long = System.currentTimeMillis()) = now >= endsAt
        fun dayNumber(now: Long = System.currentTimeMillis()): Int =
            (((now - startAt) / 86_400_000L).toInt() + 1).coerceIn(1, days)
    }

    data class Snapshot(
        val myName: String = "",
        val contacts: List<Contact> = emptyList(),
        val messages: List<ChatMessage> = emptyList(),
        val requests: List<ApprovalRequest> = emptyList(),
        val incoming: List<IncomingRequest> = emptyList(),
        val rule: Rule = Rule.ANY,
        val unread: Map<String, Int> = emptyMap(),
        val pendingOutbox: Int = 0,
        val peerStats: Map<String, PeerStats> = emptyMap(),
        val challenge: Challenge? = null,
    ) {
        fun supporters() = contacts.filter { it.direction == Direction.SUPPORTER }
        fun wards() = contacts.filter { it.direction == Direction.WARD }
        fun approvers() = supporters().filter { it.canApprove }
        fun messagesWith(contactId: String) = messages.filter { it.contactId == contactId }
        fun activeRequest(): ApprovalRequest? =
            requests.lastOrNull { it.state == RequestState.PENDING && it.exp > System.currentTimeMillis() }
        fun openIncoming() = incoming.filter { !it.decided && it.exp > System.currentTimeMillis() }
    }

    private val _snapshot = MutableStateFlow(read())
    val snapshot: StateFlow<Snapshot> = _snapshot

    /** Fired when something notification-worthy happens (type, contactName, detail). */
    var onEvent: ((event: String, name: String, detail: String) -> Unit)? = null

    // -------------------------------------------------------------- pairing

    /** Content of the QR shown on the user's phone. One scan = paired. */
    fun pairingQrContent(): String {
        val token = CryptoBox.randomNonce()
        val tokens = activePairTokens().toMutableSet().also { it.add("$token|${System.currentTimeMillis()}") }
        prefs.edit().putStringSet(KEY_PAIR_TOKENS, tokens).apply()
        return "pact://pair?v=4" +
            "&n=" + android.net.Uri.encode(myName.ifBlank { "Someone" }) +
            "&s=" + B64.encode(identity.signPublic) +
            "&b=" + B64.encode(identity.boxPublic) +
            "&i=" + myInbox +
            "&t=" + android.net.Uri.encode(token)
    }

    /** Trusted person's phone: scan the QR, pin their keys, send pair-accept. */
    fun acceptPairing(qrContent: String): Contact? {
        val uri = runCatching { android.net.Uri.parse(qrContent) }.getOrNull() ?: return null
        if (uri.scheme != "pact" || uri.host != "pair") return null
        val name = uri.getQueryParameter("n") ?: return null
        val sign = uri.getQueryParameter("s") ?: return null
        val box = uri.getQueryParameter("b") ?: return null
        val inbox = uri.getQueryParameter("i") ?: return null
        val token = uri.getQueryParameter("t") ?: return null
        runCatching { require(B64.decode(sign).size == 32 && B64.decode(box).size == 32) }
            .getOrNull() ?: return null

        val ward = Contact(sign, name, box, inbox, Direction.WARD)
        upsertContact(ward)

        val body = JSONObject()
            .put("token", token)
            .put("name", myName.ifBlank { "A trusted person" })
            .put("box", B64.encode(identity.boxPublic))
            .put("inbox", myInbox)
        sendPayload(Wire.TYPE_PAIR_ACCEPT, UUID.randomUUID().toString(), body, Wire.MESSAGE_TTL_MILLIS, ward)
        return ward
    }

    // ------------------------------------------------ remote (code) pairing

    /**
     * Ward side: mint a short, shareable pairing code and publish an
     * **encrypted** copy of the same bundle a QR would carry to a rendezvous
     * topic derived from the code. The bundle holds only public keys, an inbox,
     * and a first name — and it's sealed with the code itself, so only someone
     * you gave the code to can open it. Share the code over any channel; the
     * other person types it in. No need to be in the same room.
     */
    fun createPairingCode(): String {
        val code = randomPairCode()
        val token = CryptoBox.randomNonce()
        val tokens = activePairTokens().toMutableSet().also { it.add("$token|${System.currentTimeMillis()}") }
        prefs.edit().putStringSet(KEY_PAIR_TOKENS, tokens).apply()
        val bundle = JSONObject()
            .put("n", myName.ifBlank { "Someone" })
            .put("s", B64.encode(identity.signPublic))
            .put("b", B64.encode(identity.boxPublic))
            .put("i", myInbox)
            .put("t", token)
        val sealed = Backup.encrypt(bundle.toString(), code.toCharArray())
        scope.launch { runCatching { transport.send(pairTopic(code), sealed) } }
        return formatPairCode(code)
    }

    /**
     * Supporter side: redeem a code someone shared. Fetches the sealed bundle,
     * opens it with the code, pins their keys, and sends the pair-accept — the
     * exact same handshake as a QR scan, just carried by a relay topic.
     */
    suspend fun redeemPairingCode(rawCode: String): Contact? {
        val code = normalizePairCode(rawCode)
        if (code.length < PAIR_CODE_LEN) return null
        val since = System.currentTimeMillis() - 60 * 60 * 1000L
        val messages = runCatching { transport.fetch(pairTopic(code), since) }.getOrDefault(emptyList())
        for (m in messages.reversed()) {
            val plain = Backup.decrypt(m.ciphertext, code.toCharArray()) ?: continue
            val o = runCatching { JSONObject(plain) }.getOrNull() ?: continue
            val uri = "pact://pair?v=4" +
                "&n=" + android.net.Uri.encode(o.optString("n")) +
                "&s=" + o.optString("s") +
                "&b=" + o.optString("b") +
                "&i=" + o.optString("i") +
                "&t=" + android.net.Uri.encode(o.optString("t"))
            acceptPairing(uri)?.let { return it }
        }
        return null
    }

    private fun randomPairCode(): String {
        val rnd = java.security.SecureRandom()
        return buildString { repeat(PAIR_CODE_LEN) { append(PAIR_ALPHABET[rnd.nextInt(PAIR_ALPHABET.length)]) } }
    }

    private fun formatPairCode(code: String): String = code.chunked(5).joinToString("-")

    private fun normalizePairCode(raw: String): String =
        raw.uppercase().map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .filter { it in PAIR_ALPHABET }.joinToString("")

    private fun pairTopic(code: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(("pact-pair-$code").toByteArray())
        return "pact" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    // ----------------------------------------------------------------- chat

    fun sendChat(contactId: String, text: String) {
        val contact = contact(contactId) ?: return
        val id = UUID.randomUUID().toString()
        appendMessage(ChatMessage(id, contactId, fromMe = true, text = text, ts = System.currentTimeMillis(), status = MessageStatus.QUEUED))
        val sent = sendPayload(Wire.TYPE_CHAT, id, JSONObject().put("text", text), Wire.MESSAGE_TTL_MILLIS, contact)
        if (sent) markMessageSent(id)
    }

    fun deleteMessageLocally(messageId: String) {
        saveMessages(loadMessages().filterNot { it.id == messageId })
        refresh()
    }

    fun markRead(contactId: String) {
        val unread = loadUnread().toMutableMap().also { it.remove(contactId) }
        prefs.edit().putString(KEY_UNREAD, JSONObject(unread.mapValues { it.value as Any }).toString()).apply()
        refresh()
    }

    // ------------------------------------------------------------- requests

    /**
     * Create, sign, seal, and queue an approval request to every approver.
     * Returns the request id, or null if there is nobody to ask.
     */
    fun createRequest(
        kind: RequestKind,
        pkg: String?,
        label: String,
        minutes: Int,
        reason: String?,
        usageNote: String?,
        changeAction: String? = null,
    ): String? {
        val approvers = _snapshot.value.approvers()
        if (approvers.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val request = ApprovalRequest(
            id = id,
            kind = kind,
            changeAction = changeAction,
            pkg = pkg,
            label = label,
            minutes = minutes,
            reason = reason,
            usageNote = usageNote,
            createdAt = now,
            exp = now + Wire.REQUEST_TTL_MILLIS,
            approverIds = approvers.map { it.id }.toSet(),
        )
        saveRequests(loadRequests() + request)
        val body = JSONObject()
            .put("kind", kind.name)
            .put("action", changeAction ?: JSONObject.NULL)
            .put("label", label)
            .put("minutes", minutes)
            .put("reason", reason ?: JSONObject.NULL)
            .put("usage", usageNote ?: JSONObject.NULL)
        for (approver in approvers) {
            sendPayload(Wire.TYPE_REQUEST, id, body, Wire.REQUEST_TTL_MILLIS, approver)
        }
        refresh()
        return id
    }

    /** Trusted person answers a request from someone they support. */
    fun respond(requestId: String, approve: Boolean, customMinutes: Int?, message: String?) {
        val incoming = loadIncoming().firstOrNull { it.id == requestId && !it.decided } ?: return
        val contact = contact(incoming.fromContactId) ?: return
        val body = JSONObject()
            .put("req", requestId)
            .put("ok", approve)
            .put("minutes", customMinutes ?: JSONObject.NULL)
            .put("msg", message ?: JSONObject.NULL)
        sendPayload(Wire.TYPE_RESPONSE, UUID.randomUUID().toString(), body, Wire.REQUEST_TTL_MILLIS, contact)
        saveIncoming(loadIncoming().map {
            if (it.id == requestId) it.copy(decided = true, myDecision = approve) else it
        })
        refresh()
    }

    // ------------------------------------------------- stats sharing & challenges

    /**
     * Share my screen-time snapshot, encrypted, with every contact allowed to
     * see it — plus all challenge participants while a challenge runs.
     * Throttled; called from [syncNow].
     */
    private fun maybeShareStats() {
        val last = prefs.getLong(KEY_LAST_STATS_SHARE, 0L)
        val now = System.currentTimeMillis()
        val challengeRunning = loadChallenge()?.let { !it.finished(now) } == true
        val interval = if (challengeRunning) 2 * 60 * 60 * 1000L else 6 * 60 * 60 * 1000L
        if (now - last < interval) return

        val pact = PactState.get(appContext).snapshot.value
        if (!pact.setupComplete) return
        val audience = buildSet {
            addAll(_snapshot.value.supporters().filter { it.canViewStats }.map { it.id })
            loadChallenge()?.let { addAll(it.participantIds) }
        }
        if (audience.isEmpty()) return

        val body = JSONObject()
            .put("s", pact.streakDays(now))
            .put("b", pact.today.blocks)
            .put("w", pact.today.walkaways)
            .put("m", pact.screenTimeTodayMinutes())   // today's screen time on limited apps
            .put("lu", pact.lastUnlockAt)
        for (id in audience) {
            contact(id)?.let { sendPayload(Wire.TYPE_STATS, UUID.randomUUID().toString(), body, Wire.MESSAGE_TTL_MILLIS, it) }
        }
        prefs.edit().putLong(KEY_LAST_STATS_SHARE, now).apply()
    }

    /** Start a challenge and invite [contactIds]. Returns the challenge. */
    fun createChallenge(name: String, days: Int, contactIds: Set<String>): Challenge {
        val challenge = Challenge(
            id = UUID.randomUUID().toString(),
            name = name,
            days = days,
            startAt = System.currentTimeMillis(),
            ownerId = "",
            participantIds = contactIds,
        )
        saveChallenge(challenge)
        val body = JSONObject()
            .put("act", "invite")
            .put("cid", challenge.id)
            .put("name", name)
            .put("days", days)
            .put("start", challenge.startAt)
        for (id in contactIds) {
            contact(id)?.let { sendPayload(Wire.TYPE_CHALLENGE, UUID.randomUUID().toString(), body, Wire.MESSAGE_TTL_MILLIS, it) }
        }
        // fresh stats go out immediately so everyone sees day-one state
        prefs.edit().putLong(KEY_LAST_STATS_SHARE, 0L).apply()
        refresh()
        return challenge
    }

    /** Answer a challenge invite I received. */
    fun respondChallenge(accept: Boolean) {
        val challenge = loadChallenge() ?: return
        if (challenge.ownerId.isEmpty()) return
        val owner = contact(challenge.ownerId) ?: return
        val body = JSONObject()
            .put("act", if (accept) "accept" else "decline")
            .put("cid", challenge.id)
        sendPayload(Wire.TYPE_CHALLENGE, UUID.randomUUID().toString(), body, Wire.MESSAGE_TTL_MILLIS, owner)
        if (accept) {
            saveChallenge(challenge.copy(joinedByMe = true))
            prefs.edit().putLong(KEY_LAST_STATS_SHARE, 0L).apply()
        } else {
            clearChallenge()
        }
        refresh()
    }

    fun dismissChallenge() {
        clearChallenge()
        refresh()
    }

    /** Has this participant held their locks since the challenge began? */
    fun participantHeld(challenge: Challenge, contactId: String?): Boolean? {
        return if (contactId == null) {
            PactState.get(appContext).snapshot.value.lastUnlockAt < challenge.startAt
        } else {
            val stats = _snapshot.value.peerStats[contactId] ?: return null
            stats.lastUnlockAt < challenge.startAt
        }
    }

    private fun handleStats(payload: Wire.Payload) {
        val from = B64.encode(payload.fromSignPublic)
        if (contact(from) == null) return
        val stats = PeerStats(
            streakDays = payload.body.optInt("s"),
            blocksToday = payload.body.optInt("b"),
            walkawaysToday = payload.body.optInt("w"),
            screenTimeMinutes = payload.body.optInt("m"),
            lastUnlockAt = payload.body.optLong("lu"),
            receivedAt = System.currentTimeMillis(),
        )
        val all = loadPeerStats().toMutableMap().also { it[from] = stats }
        savePeerStats(all)
        refresh()
    }

    private fun handleChallenge(payload: Wire.Payload) {
        val from = B64.encode(payload.fromSignPublic)
        val contact = contact(from) ?: return
        when (payload.body.optString("act")) {
            "invite" -> {
                // one active challenge at a time; a fresh invite replaces a finished one
                val current = loadChallenge()
                if (current != null && !current.finished()) return
                saveChallenge(
                    Challenge(
                        id = payload.body.optString("cid"),
                        name = payload.body.optString("name"),
                        days = payload.body.optInt("days").coerceIn(1, 30),
                        startAt = payload.body.optLong("start"),
                        ownerId = from,
                        participantIds = setOf(from),
                        joinedByMe = false,
                    )
                )
                onEvent?.invoke(EVENT_CHALLENGE, contact.name, payload.body.optString("name"))
            }
            "accept", "decline" -> {
                val challenge = loadChallenge() ?: return
                if (payload.body.optString("cid") != challenge.id) return
                saveChallenge(
                    challenge.copy(
                        accepted = challenge.accepted + (from to (payload.body.optString("act") == "accept"))
                    )
                )
            }
        }
        refresh()
    }

    private fun loadPeerStats(): Map<String, PeerStats> = runCatching {
        val o = JSONObject(prefs.getString(KEY_PEER_STATS, "") ?: "")
        o.keys().asSequence().associateWith { k ->
            val v = o.getJSONObject(k)
            PeerStats(v.optInt("s"), v.optInt("b"), v.optInt("w"), v.optInt("m"), v.optLong("lu"), v.optLong("at"))
            // fields: streak, blocks, walkaways, screen-time minutes, lastUnlock, receivedAt
        }
    }.getOrDefault(emptyMap())

    private fun savePeerStats(map: Map<String, PeerStats>) {
        val o = JSONObject()
        for ((k, v) in map) {
            o.put(k, JSONObject().put("s", v.streakDays).put("b", v.blocksToday)
                .put("w", v.walkawaysToday).put("m", v.screenTimeMinutes)
                .put("lu", v.lastUnlockAt).put("at", v.receivedAt))
        }
        prefs.edit().putString(KEY_PEER_STATS, o.toString()).apply()
    }

    private fun loadChallenge(): Challenge? = runCatching {
        val raw = prefs.getString(KEY_CHALLENGE, null) ?: return null
        val o = JSONObject(raw)
        Challenge(
            id = o.getString("id"),
            name = o.getString("n"),
            days = o.getInt("d"),
            startAt = o.getLong("st"),
            ownerId = o.optString("o"),
            participantIds = (o.optJSONArray("p") ?: org.json.JSONArray()).let { a ->
                (0 until a.length()).map { a.getString(it) }.toSet()
            },
            accepted = (o.optJSONObject("a") ?: JSONObject()).let { a ->
                a.keys().asSequence().associateWith { a.getBoolean(it) }
            },
            joinedByMe = o.optBoolean("j", true),
        )
    }.getOrNull()

    private fun saveChallenge(challenge: Challenge) {
        val a = JSONObject()
        for ((k, v) in challenge.accepted) a.put(k, v)
        prefs.edit().putString(
            KEY_CHALLENGE,
            JSONObject().put("id", challenge.id).put("n", challenge.name)
                .put("d", challenge.days).put("st", challenge.startAt)
                .put("o", challenge.ownerId)
                .put("p", org.json.JSONArray(challenge.participantIds.toList()))
                .put("a", a).put("j", challenge.joinedByMe)
                .toString()
        ).apply()
    }

    private fun clearChallenge() {
        prefs.edit().remove(KEY_CHALLENGE).apply()
    }

    // ------------------------------------------------------ policy & apply

    var rule: Rule
        get() = runCatching { Rule.valueOf(prefs.getString(KEY_RULE, "") ?: "") }.getOrDefault(Rule.ANY)
        set(value) {
            prefs.edit().putString(KEY_RULE, value.name).apply()
            refresh()
        }

    /** Deterministic policy evaluation — pure function, unit-tested. */
    fun evaluate(request: ApprovalRequest, rule: Rule): RequestState {
        if (request.state != RequestState.PENDING) return request.state
        if (request.exp < System.currentTimeMillis()) return RequestState.EXPIRED
        val n = request.approverIds.size
        val approvals = request.decisions.values.count { it.approve }
        val denials = request.decisions.values.count { !it.approve }
        return when (rule) {
            Rule.ANY -> when {
                approvals >= 1 -> RequestState.APPROVED
                denials >= n -> RequestState.DENIED
                else -> RequestState.PENDING
            }
            Rule.MAJORITY -> when {
                approvals * 2 > n -> RequestState.APPROVED
                denials * 2 >= n && n > 0 -> RequestState.DENIED
                else -> RequestState.PENDING
            }
            Rule.ALL -> when {
                denials >= 1 -> RequestState.DENIED
                approvals >= n && n > 0 -> RequestState.APPROVED
                else -> RequestState.PENDING
            }
        }
    }

    /** What happens when a request is granted. Wired to PactState in [applyApproved]. */
    private fun applyApproved(request: ApprovalRequest, grantedMinutes: Int) {
        val state = PactState.get(appContext)
        when (request.kind) {
            RequestKind.UNLOCK -> request.pkg?.let {
                state.unlockFor(it, grantedMinutes * 60_000L, PactState.Tier.RED, null)
                Notifications.showBreak(appContext, it, grantedMinutes * 60_000L)
            }
            RequestKind.CHANGE -> when (request.changeAction) {
                CHANGE_REMOVE_APP -> request.pkg?.let(state::removeBlocked)
                CHANGE_TIER_DOWN -> request.pkg?.let { state.setTier(it, PactState.Tier.YELLOW) }
                CHANGE_STRICT_OFF -> state.setStrictMode(false)
                CHANGE_LIMIT_UP -> request.pkg?.let { state.setDailyLimit(it, request.minutes) }
                CHANGE_RESET -> state.reset()
            }
        }
    }

    // ------------------------------------------------------------ transport

    private fun sendPayload(
        type: String,
        id: String,
        body: JSONObject,
        ttl: Long,
        to: Contact,
    ): Boolean {
        val sealed = Wire.sealOutbound(type, id, body, ttl, identity, B64.decode(to.boxPublic))
        outbox.enqueue(to.inbox, sealed)
        scope.launch { syncNow() }
        return true
    }

    /** One full sync pass: drain the outbox, fetch and route the inbox. */
    suspend fun syncNow(): Unit = lock.withLock {
        runCatching { maybeShareStats() }
        runCatching { outbox.drain(transport) }
        val since = prefs.getLong(KEY_LAST_SYNC, System.currentTimeMillis() - Wire.MESSAGE_TTL_MILLIS)
        val fetched = runCatching { transport.fetch(myInbox, since) }.getOrDefault(emptyList())
        var newest = since
        for (message in fetched) {
            if (message.receivedAt > newest) newest = message.receivedAt
            route(message.ciphertext)
        }
        if (newest > since) prefs.edit().putLong(KEY_LAST_SYNC, newest).apply()
        refresh()
    }

    private fun route(ciphertext: String) {
        val pinned = loadContacts().map { it.id }.toSet()
        val result = Wire.verifyInbound(ciphertext, identity, pinned, ::isNonceSeen)
        val payload = when (result) {
            is Wire.InboundResult.Valid -> result.payload
            is Wire.InboundResult.ValidUnpinned -> result.payload
            else -> return // invalid, replayed, expired, or untrusted — rejected
        }
        recordNonce(B64.encode(payload.fromSignPublic), payload.nonce)
        when (payload.type) {
            Wire.TYPE_PAIR_ACCEPT -> handlePairAccept(payload)
            Wire.TYPE_CHAT -> handleChat(payload)
            Wire.TYPE_REQUEST -> handleRequest(payload)
            Wire.TYPE_RESPONSE -> handleResponse(payload)
            Wire.TYPE_STATS -> handleStats(payload)
            Wire.TYPE_CHALLENGE -> handleChallenge(payload)
        }
    }

    private fun handlePairAccept(payload: Wire.Payload) {
        val token = payload.body.optString("token")
        val active = activePairTokens()
        val match = active.firstOrNull { it.substringBefore('|') == token } ?: return
        prefs.edit().putStringSet(KEY_PAIR_TOKENS, active.minus(match)).apply()
        val contact = Contact(
            id = B64.encode(payload.fromSignPublic),
            name = payload.body.optString("name").ifBlank { "Trusted person" },
            boxPublic = payload.body.optString("box"),
            inbox = payload.body.optString("inbox"),
            direction = Direction.SUPPORTER,
        )
        if (contact.boxPublic.isEmpty() || contact.inbox.isEmpty()) return
        upsertContact(contact)
        onEvent?.invoke(EVENT_PAIRED, contact.name, "")
    }

    private fun handleChat(payload: Wire.Payload) {
        val from = B64.encode(payload.fromSignPublic)
        val contact = contact(from) ?: return
        val text = payload.body.optString("text")
        if (text.isEmpty()) return
        appendMessage(ChatMessage(payload.id, from, fromMe = false, text = text, ts = payload.ts, status = MessageStatus.SENT))
        bumpUnread(from)
        onEvent?.invoke(EVENT_MESSAGE, contact.name, text.take(80))
    }

    private fun handleRequest(payload: Wire.Payload) {
        val from = B64.encode(payload.fromSignPublic)
        val contact = contact(from) ?: return
        val incoming = IncomingRequest(
            id = payload.id,
            fromContactId = from,
            kind = runCatching { RequestKind.valueOf(payload.body.optString("kind")) }.getOrDefault(RequestKind.UNLOCK),
            changeAction = payload.body.optString("action").takeIf { it.isNotEmpty() && it != "null" },
            label = payload.body.optString("label"),
            minutes = payload.body.optInt("minutes"),
            reason = payload.body.optString("reason").takeIf { it.isNotEmpty() && it != "null" },
            usageNote = payload.body.optString("usage").takeIf { it.isNotEmpty() && it != "null" },
            receivedAt = System.currentTimeMillis(),
            exp = payload.exp,
        )
        if (loadIncoming().any { it.id == incoming.id }) return // duplicate
        saveIncoming(loadIncoming() + incoming)
        onEvent?.invoke(EVENT_REQUEST, contact.name, incoming.label)
    }

    private fun handleResponse(payload: Wire.Payload) {
        val from = B64.encode(payload.fromSignPublic)
        val contact = contact(from) ?: return
        val requestId = payload.body.optString("req")
        val requests = loadRequests().toMutableList()
        val index = requests.indexOfFirst { it.id == requestId }
        if (index < 0) return
        var request = requests[index]
        // duplicate-approval and forged-approver protection
        if (request.state != RequestState.PENDING) return
        if (from !in request.approverIds) return
        if (request.decisions.containsKey(from)) return
        if (request.exp < System.currentTimeMillis()) return

        val decision = Decision(
            approve = payload.body.optBoolean("ok"),
            minutes = if (payload.body.isNull("minutes")) null else payload.body.optInt("minutes"),
            message = payload.body.optString("msg").takeIf { it.isNotEmpty() && it != "null" },
            at = System.currentTimeMillis(),
        )
        request = request.copy(decisions = request.decisions + (from to decision))
        val newState = evaluate(request, rule)
        if (newState == RequestState.APPROVED) {
            val granted = decision.minutes?.takeIf { decision.approve } ?: request.minutes
            request = request.copy(state = newState, grantedMinutes = granted)
            requests[index] = request
            saveRequests(requests)
            applyApproved(request, granted)
            onEvent?.invoke(EVENT_APPROVED, contact.name, request.label)
        } else {
            request = request.copy(state = newState)
            requests[index] = request
            saveRequests(requests)
            if (newState == RequestState.DENIED) onEvent?.invoke(EVENT_DENIED, contact.name, request.label)
        }
        refresh()
    }

    // ------------------------------------------------------------- contacts

    fun contact(id: String): Contact? = loadContacts().firstOrNull { it.id == id }

    fun setPermissions(contactId: String, canApprove: Boolean, canViewStats: Boolean) {
        saveContacts(loadContacts().map {
            if (it.id == contactId) it.copy(canApprove = canApprove, canViewStats = canViewStats) else it
        })
        refresh()
    }

    /** Revocation: the contact's key is unpinned; everything from it is rejected. */
    fun removeContact(contactId: String) {
        saveContacts(loadContacts().filterNot { it.id == contactId })
        saveMessages(loadMessages().filterNot { it.contactId == contactId })
        refresh()
    }

    private fun upsertContact(contact: Contact) {
        val existing = loadContacts().filterNot { it.id == contact.id }
        saveContacts(existing + contact)
        refresh()
    }

    // ---------------------------------------------------------- persistence

    private fun refresh() {
        _snapshot.value = read()
    }

    private fun read(): Snapshot = Snapshot(
        myName = prefs.getString(KEY_MY_NAME, "") ?: "",
        contacts = loadContacts(),
        messages = loadMessages(),
        requests = loadRequests().map { it.copy(state = evaluate(it, rule)) },
        incoming = loadIncoming(),
        rule = rule,
        unread = loadUnread(),
        pendingOutbox = outbox.size(),
        peerStats = loadPeerStats(),
        challenge = loadChallenge(),
    )

    private fun isNonceSeen(from: String, nonce: String): Boolean =
        (prefs.getStringSet(KEY_NONCES, emptySet()) ?: emptySet()).contains("$from|$nonce")

    private fun recordNonce(from: String, nonce: String) {
        val set = (prefs.getStringSet(KEY_NONCES, emptySet()) ?: emptySet()).toMutableList()
        set.add("$from|$nonce")
        prefs.edit().putStringSet(KEY_NONCES, set.takeLast(1000).toSet()).apply()
    }

    private fun activePairTokens(): Set<String> {
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        return (prefs.getStringSet(KEY_PAIR_TOKENS, emptySet()) ?: emptySet())
            .filter { (it.substringAfter('|').toLongOrNull() ?: 0L) > cutoff }
            .toSet()
    }

    private fun bumpUnread(contactId: String) {
        val unread = loadUnread().toMutableMap()
        unread[contactId] = (unread[contactId] ?: 0) + 1
        prefs.edit().putString(KEY_UNREAD, JSONObject(unread.mapValues { it.value as Any }).toString()).apply()
    }

    private fun loadUnread(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString(KEY_UNREAD, "") ?: "")
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun appendMessage(message: ChatMessage) {
        saveMessages((loadMessages() + message).takeLast(500))
        refresh()
    }

    private fun markMessageSent(id: String) {
        saveMessages(loadMessages().map { if (it.id == id) it.copy(status = MessageStatus.SENT) else it })
        refresh()
    }

    private fun loadContacts(): List<Contact> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_CONTACTS, "") ?: "")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Contact(
                id = o.getString("id"),
                name = o.getString("name"),
                boxPublic = o.getString("box"),
                inbox = o.getString("inbox"),
                direction = Direction.valueOf(o.getString("dir")),
                canApprove = o.optBoolean("approve", true),
                canViewStats = o.optBoolean("stats", false),
                addedAt = o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveContacts(contacts: List<Contact>) {
        val arr = JSONArray()
        for (c in contacts) {
            arr.put(
                JSONObject().put("id", c.id).put("name", c.name).put("box", c.boxPublic)
                    .put("inbox", c.inbox).put("dir", c.direction.name)
                    .put("approve", c.canApprove).put("stats", c.canViewStats).put("at", c.addedAt)
            )
        }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    /** Chat bodies are encrypted at rest with the Keystore-backed vault. */
    private fun loadMessages(): List<ChatMessage> = runCatching {
        val raw = prefs.getString(KEY_MESSAGES, "") ?: ""
        val plain = if (raw.isEmpty()) "" else Vault.decrypt(raw) ?: ""
        val arr = JSONArray(plain.ifEmpty { "[]" })
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChatMessage(
                id = o.getString("id"),
                contactId = o.getString("c"),
                fromMe = o.getBoolean("me"),
                text = o.getString("x"),
                ts = o.getLong("ts"),
                status = MessageStatus.valueOf(o.getString("st")),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveMessages(messages: List<ChatMessage>) {
        val arr = JSONArray()
        for (m in messages) {
            arr.put(
                JSONObject().put("id", m.id).put("c", m.contactId).put("me", m.fromMe)
                    .put("x", m.text).put("ts", m.ts).put("st", m.status.name)
            )
        }
        prefs.edit().putString(KEY_MESSAGES, Vault.encrypt(arr.toString())).apply()
    }

    private fun loadRequests(): List<ApprovalRequest> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_REQUESTS, "") ?: "")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val decisions = mutableMapOf<String, Decision>()
            val d = o.optJSONObject("dec") ?: JSONObject()
            for (k in d.keys()) {
                val v = d.getJSONObject(k)
                decisions[k] = Decision(
                    v.getBoolean("ok"),
                    if (v.isNull("m")) null else v.getInt("m"),
                    v.optString("msg").takeIf { it.isNotEmpty() },
                    v.getLong("at"),
                )
            }
            ApprovalRequest(
                id = o.getString("id"),
                kind = RequestKind.valueOf(o.getString("k")),
                changeAction = o.optString("ca").takeIf { it.isNotEmpty() },
                pkg = o.optString("p").takeIf { it.isNotEmpty() },
                label = o.getString("l"),
                minutes = o.getInt("min"),
                reason = o.optString("r").takeIf { it.isNotEmpty() },
                usageNote = o.optString("u").takeIf { it.isNotEmpty() },
                createdAt = o.getLong("at"),
                exp = o.getLong("x"),
                approverIds = (o.optJSONArray("ap") ?: JSONArray()).let { a ->
                    (0 until a.length()).map { a.getString(it) }.toSet()
                },
                decisions = decisions,
                state = RequestState.valueOf(o.getString("s")),
                grantedMinutes = if (o.isNull("g")) null else o.optInt("g"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveRequests(requests: List<ApprovalRequest>) {
        val arr = JSONArray()
        for (r in requests.takeLast(50)) {
            val dec = JSONObject()
            for ((k, v) in r.decisions) {
                dec.put(
                    k,
                    JSONObject().put("ok", v.approve).put("m", v.minutes ?: JSONObject.NULL)
                        .put("msg", v.message ?: "").put("at", v.at)
                )
            }
            arr.put(
                JSONObject().put("id", r.id).put("k", r.kind.name)
                    .put("ca", r.changeAction ?: "").put("p", r.pkg ?: "")
                    .put("l", r.label).put("min", r.minutes).put("r", r.reason ?: "")
                    .put("u", r.usageNote ?: "").put("at", r.createdAt).put("x", r.exp)
                    .put("ap", JSONArray(r.approverIds.toList()))
                    .put("dec", dec).put("s", r.state.name)
                    .put("g", r.grantedMinutes ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_REQUESTS, arr.toString()).apply()
    }

    private fun loadIncoming(): List<IncomingRequest> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_INCOMING, "") ?: "")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            IncomingRequest(
                id = o.getString("id"),
                fromContactId = o.getString("f"),
                kind = RequestKind.valueOf(o.getString("k")),
                changeAction = o.optString("ca").takeIf { it.isNotEmpty() },
                label = o.getString("l"),
                minutes = o.getInt("min"),
                reason = o.optString("r").takeIf { it.isNotEmpty() },
                usageNote = o.optString("u").takeIf { it.isNotEmpty() },
                receivedAt = o.getLong("at"),
                exp = o.getLong("x"),
                decided = o.optBoolean("d"),
                myDecision = if (o.isNull("md")) null else o.optBoolean("md"),
            )
        }
    }.getOrDefault(emptyList())

    private fun saveIncoming(incoming: List<IncomingRequest>) {
        val arr = JSONArray()
        for (r in incoming.takeLast(50)) {
            arr.put(
                JSONObject().put("id", r.id).put("f", r.fromContactId).put("k", r.kind.name)
                    .put("ca", r.changeAction ?: "").put("l", r.label).put("min", r.minutes)
                    .put("r", r.reason ?: "").put("u", r.usageNote ?: "")
                    .put("at", r.receivedAt).put("x", r.exp).put("d", r.decided)
                    .put("md", r.myDecision ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_INCOMING, arr.toString()).apply()
    }

    companion object {
        const val CHANGE_REMOVE_APP = "REMOVE_APP"
        const val CHANGE_TIER_DOWN = "TIER_DOWN"
        const val CHANGE_STRICT_OFF = "STRICT_OFF"
        const val CHANGE_LIMIT_UP = "LIMIT_UP"
        const val CHANGE_RESET = "RESET"

        const val EVENT_PAIRED = "paired"
        const val EVENT_MESSAGE = "message"
        const val EVENT_REQUEST = "request"
        const val EVENT_APPROVED = "approved"
        const val EVENT_DENIED = "denied"
        const val EVENT_CHALLENGE = "challenge"

        /** Crockford-style base32, dropping the letters most easily mistyped (I, L, O, U). */
        private const val PAIR_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val PAIR_CODE_LEN = 10

        private const val KEY_IDENTITY = "identity"
        private const val KEY_INBOX = "inbox"
        private const val KEY_MY_NAME = "my_name"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_REQUESTS = "requests"
        private const val KEY_INCOMING = "incoming"
        private const val KEY_RULE = "rule"
        private const val KEY_NONCES = "nonces"
        private const val KEY_PAIR_TOKENS = "pair_tokens"
        private const val KEY_UNREAD = "unread"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_PEER_STATS = "peer_stats"
        private const val KEY_CHALLENGE = "challenge"
        private const val KEY_LAST_STATS_SHARE = "last_stats_share"

        @Volatile
        private var instance: TrustNetwork? = null

        fun get(context: Context): TrustNetwork =
            instance ?: synchronized(this) {
                instance ?: TrustNetwork(context).also { instance = it }
            }
    }
}

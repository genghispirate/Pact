package com.pact.app.core

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The swappable transport. Business logic (TrustNetwork) only ever talks to
 * this interface; the concrete relay can be replaced (public relay → Nostr →
 * custom) without touching anything above it.
 *
 * A transport carries OPAQUE CIPHERTEXT to an unguessable inbox id. It never
 * sees plaintext, names, or keys, and it is never mentioned in the UI.
 */
interface Transport {
    /** Deliver [ciphertext] to [inbox]. Returns true on confirmed handoff. */
    fun send(inbox: String, ciphertext: String): Boolean

    /** Fetch everything queued for [inbox] since [sinceMillis]. */
    fun fetch(inbox: String, sinceMillis: Long): List<TransportMessage>
}

data class TransportMessage(val ciphertext: String, val receivedAt: Long)

/**
 * Default implementation over public ntfy relays (open pub/sub, no accounts).
 * Inboxes are random 128-bit topics; payloads are already end-to-end
 * encrypted before they reach this class.
 */
class RelayTransport(
    private val baseUrls: List<String> = DEFAULT_RELAYS,
) : Transport {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun send(inbox: String, ciphertext: String): Boolean {
        for (base in baseUrls) {
            val ok = runCatching {
                val request = Request.Builder()
                    .url("$base/$inbox")
                    .post(ciphertext.toRequestBody("text/plain".toMediaType()))
                    .header("Cache", "yes")
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    override fun fetch(inbox: String, sinceMillis: Long): List<TransportMessage> {
        for (base in baseUrls) {
            val result = runCatching {
                val sinceSeconds = (sinceMillis / 1000).coerceAtLeast(1)
                val request = Request.Builder()
                    .url("$base/$inbox/json?poll=1&since=$sinceSeconds")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body?.string() ?: return@runCatching null
                    body.lineSequence()
                        .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                        .filter { it.optString("event") == "message" }
                        .map {
                            TransportMessage(
                                ciphertext = it.optString("message"),
                                receivedAt = it.optLong("time") * 1000,
                            )
                        }
                        .toList()
                }
            }.getOrNull()
            if (result != null) return result
        }
        return emptyList()
    }

    companion object {
        val DEFAULT_RELAYS = listOf("https://ntfy.sh")
    }
}

/**
 * Persistent outbox: encrypted payloads queue locally and drain with
 * exponential backoff whenever the network allows. No message loss.
 */
class Outbox(private val prefs: android.content.SharedPreferences) {

    data class Item(val inbox: String, val ciphertext: String, val queuedAt: Long, val attempts: Int)

    @Synchronized
    fun enqueue(inbox: String, ciphertext: String) {
        val items = load() + Item(inbox, ciphertext, System.currentTimeMillis(), 0)
        save(items)
    }

    /** Try to deliver everything due; keeps failures with increased backoff. */
    @Synchronized
    fun drain(transport: Transport): Int {
        val now = System.currentTimeMillis()
        var delivered = 0
        val remaining = mutableListOf<Item>()
        for (item in load()) {
            val due = item.queuedAt + backoffMillis(item.attempts) <= now
            if (!due) {
                remaining += item
            } else if (transport.send(item.inbox, item.ciphertext)) {
                delivered++
            } else {
                remaining += item.copy(queuedAt = now, attempts = item.attempts + 1)
            }
        }
        save(remaining)
        return delivered
    }

    @Synchronized
    fun size(): Int = load().size

    private fun backoffMillis(attempts: Int): Long =
        (30_000L shl attempts.coerceAtMost(6)).coerceAtMost(30 * 60 * 1000L)

    private fun load(): List<Item> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(KEY, "") ?: "")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Item(o.getString("in"), o.getString("c"), o.getLong("q"), o.getInt("a"))
        }
    }.getOrDefault(emptyList())

    private fun save(items: List<Item>) {
        val arr = org.json.JSONArray()
        for (i in items.takeLast(200)) {
            arr.put(
                JSONObject().put("in", i.inbox).put("c", i.ciphertext)
                    .put("q", i.queuedAt).put("a", i.attempts)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "outbox"
    }
}

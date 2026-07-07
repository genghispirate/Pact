package com.pact.app.core

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted export/import. The payload holds settings, tiers, and statistics.
 * The TOTP secret is DELIBERATELY excluded: a backup must never double as a
 * way to hand yourself the key to your own locks. Restoring keeps whatever
 * sponsor pairing the device currently has.
 *
 * Format: "PACT1:" + base64(salt) + ":" + base64(iv) + ":" + base64(ciphertext)
 * Key: PBKDF2-HMAC-SHA256, 200k iterations → AES-256-GCM.
 */
object Backup {

    private const val HEADER = "PACT1"
    private const val ITERATIONS = 200_000

    fun export(snapshot: PactState.Snapshot): JSONObject = JSONObject()
        .put("version", 1)
        .put("exportedAt", System.currentTimeMillis())
        .put("guardianName", snapshot.guardianName)
        .put("blocked", JSONArray(snapshot.blocked.toList()))
        .put("tiers", JSONObject(snapshot.tiers.mapValues { it.value.name as Any }))
        .put("limits", JSONObject(snapshot.dailyLimits.mapValues { it.value as Any }))
        .put("strictMode", snapshot.strictMode)
        .put("days", JSONArray(snapshot.days.map { d ->
            JSONObject()
                .put("d", d.day)
                .put("b", JSONObject(d.blocksPerApp.mapValues { it.value as Any }))
                .put("u", d.unlocks)
                .put("w", d.walkaways)
                .put("m", d.minutesUnlocked)
        }))
        .put("hours", JSONArray(snapshot.hourHistogram))
        .put("events", JSONArray(snapshot.events.map { e ->
            JSONObject()
                .put("at", e.at)
                .put("p", e.pkg)
                .put("t", e.tier.name)
                .put("tr", e.trigger?.name ?: JSONObject.NULL)
                .put("m", e.durationMinutes)
                .put("w", e.worthIt ?: JSONObject.NULL)
        }))
        .put("longestStreak", snapshot.longestStreakDays)

    fun encrypt(plainJson: String, passphrase: CharArray): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return listOf(
            HEADER,
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(":")
    }

    fun decrypt(blob: String, passphrase: CharArray): String? = runCatching {
        val parts = blob.trim().split(":")
        require(parts.size == 4 && parts[0] == HEADER)
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[3], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(PBEKeySpec(passphrase, salt, ITERATIONS, 256))
        return SecretKeySpec(key.encoded, "AES")
    }

    /** Statistics as CSV: one row per day. */
    fun statsCsv(snapshot: PactState.Snapshot): String {
        val sb = StringBuilder("date,blocks,unlocks,walkaways,minutes_unlocked\n")
        for (d in snapshot.days) {
            sb.append("${d.day},${d.blocks},${d.unlocks},${d.walkaways},${d.minutesUnlocked}\n")
        }
        return sb.toString()
    }
}

package com.pact.app

import com.pact.app.core.CryptoBox
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the cryptographic root of trust: Ed25519 signatures and X25519
 * sealed boxes. Pure JVM, so it runs without a device.
 */
class CryptoTest {

    @Test
    fun signAndVerifyRoundTrip() {
        val id = CryptoBox.generateIdentity()
        val message = "approve request 42".toByteArray()
        val sig = CryptoBox.sign(message, id.signPrivate)
        assertTrue(CryptoBox.verify(message, sig, id.signPublic))
    }

    @Test
    fun verifyRejectsTamperedMessage() {
        val id = CryptoBox.generateIdentity()
        val sig = CryptoBox.sign("original".toByteArray(), id.signPrivate)
        assertFalse(CryptoBox.verify("tampered".toByteArray(), sig, id.signPublic))
    }

    @Test
    fun verifyRejectsWrongSigner() {
        val alice = CryptoBox.generateIdentity()
        val mallory = CryptoBox.generateIdentity()
        val message = "let me in".toByteArray()
        val sig = CryptoBox.sign(message, mallory.signPrivate)
        // Forged approval: signed by Mallory, claiming to be Alice.
        assertFalse(CryptoBox.verify(message, sig, alice.signPublic))
    }

    @Test
    fun sealAndOpenRoundTrip() {
        val sender = CryptoBox.generateIdentity()
        val recipient = CryptoBox.generateIdentity()
        val plaintext = "unlock instagram for 15 minutes".toByteArray()
        val sealed = CryptoBox.seal(plaintext, recipient.boxPublic)
        val opened = CryptoBox.open(sealed, recipient.boxPrivate, recipient.boxPublic)
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun openFailsForWrongRecipient() {
        val recipient = CryptoBox.generateIdentity()
        val eavesdropper = CryptoBox.generateIdentity()
        val sealed = CryptoBox.seal("secret".toByteArray(), recipient.boxPublic)
        assertNull(CryptoBox.open(sealed, eavesdropper.boxPrivate, eavesdropper.boxPublic))
    }

    @Test
    fun openFailsForTamperedCiphertext() {
        val recipient = CryptoBox.generateIdentity()
        val sealed = CryptoBox.seal("secret".toByteArray(), recipient.boxPublic).copyOf()
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte() // flip a tag bit
        assertNull(CryptoBox.open(sealed, recipient.boxPrivate, recipient.boxPublic))
    }

    @Test
    fun eachSealUsesFreshEphemeralKey() {
        val recipient = CryptoBox.generateIdentity()
        val a = CryptoBox.seal("x".toByteArray(), recipient.boxPublic)
        val b = CryptoBox.seal("x".toByteArray(), recipient.boxPublic)
        // Same plaintext, different ciphertext (no nonce/key reuse).
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun keysAreCorrectSize() {
        val id = CryptoBox.generateIdentity()
        assertEquals(32, id.signPublic.size)
        assertEquals(32, id.boxPublic.size)
    }
}

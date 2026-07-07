package com.pact.app.core

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The cryptographic root of trust. Pure JVM (BouncyCastle low-level API), so
 * every primitive is unit-testable on the desktop.
 *
 * - Identity  = Ed25519 signing pair + X25519 encryption pair (32-byte keys).
 * - Signing   = Ed25519 over the exact bytes being protected.
 * - Sealing   = ephemeral X25519 ECDH → HKDF-SHA256 → AES-256-GCM.
 *   Each message uses a fresh ephemeral key, so there is no shared secret to
 *   steal and no TOTP-style symmetric key anywhere in the system.
 *
 * Key bytes are Base64 (URL-safe, no padding) everywhere above this layer.
 */
object CryptoBox {

    data class KeyPairBytes(val publicKey: ByteArray, val privateKey: ByteArray)

    data class Identity(
        val signPublic: ByteArray,
        val signPrivate: ByteArray,
        val boxPublic: ByteArray,
        val boxPrivate: ByteArray,
    )

    private val random = SecureRandom()

    fun generateIdentity(): Identity {
        val sign = generateSignPair()
        val box = generateBoxPair()
        return Identity(sign.publicKey, sign.privateKey, box.publicKey, box.privateKey)
    }

    fun generateSignPair(): KeyPairBytes {
        val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(random)) }
        val pair = gen.generateKeyPair()
        val priv = pair.private as Ed25519PrivateKeyParameters
        val pub = pair.public as Ed25519PublicKeyParameters
        return KeyPairBytes(pub.encoded, priv.encoded)
    }

    fun generateBoxPair(): KeyPairBytes {
        val gen = X25519KeyPairGenerator().apply { init(X25519KeyGenerationParameters(random)) }
        val pair = gen.generateKeyPair()
        val priv = pair.private as X25519PrivateKeyParameters
        val pub = pair.public as X25519PublicKeyParameters
        return KeyPairBytes(pub.encoded, priv.encoded)
    }

    fun sign(message: ByteArray, signPrivate: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(signPrivate, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify(message: ByteArray, signature: ByteArray, signPublic: ByteArray): Boolean = runCatching {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(signPublic, 0))
        verifier.update(message, 0, message.size)
        verifier.verifySignature(signature)
    }.getOrDefault(false)

    /**
     * Seal [plaintext] to a recipient's X25519 public key.
     * Output: ephemeralPublic(32) || iv(12) || ciphertext+tag.
     */
    fun seal(plaintext: ByteArray, recipientBoxPublic: ByteArray): ByteArray {
        val ephemeral = generateBoxPair()
        val shared = agree(ephemeral.privateKey, recipientBoxPublic)
        val key = deriveKey(shared, ephemeral.publicKey + recipientBoxPublic)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        return ephemeral.publicKey + iv + ct
    }

    /** Open a sealed message with our X25519 private key, or null if invalid. */
    fun open(sealed: ByteArray, myBoxPrivate: ByteArray, myBoxPublic: ByteArray): ByteArray? = runCatching {
        require(sealed.size > 32 + 12 + 16)
        val ephemeralPublic = sealed.copyOfRange(0, 32)
        val iv = sealed.copyOfRange(32, 44)
        val ct = sealed.copyOfRange(44, sealed.size)
        val shared = agree(myBoxPrivate, ephemeralPublic)
        val key = deriveKey(shared, ephemeralPublic + myBoxPublic)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.doFinal(ct)
    }.getOrNull()

    private fun agree(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(publicKey, 0), shared, 0)
        return shared
    }

    private fun deriveKey(shared: ByteArray, context: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, context, "pact-v4-box".toByteArray()))
        return ByteArray(32).also { hkdf.generateBytes(it, 0, 32) }
    }

    fun randomNonce(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return B64.encode(bytes)
    }

    fun randomTopic(): String {
        // 128-bit unguessable inbox id, hex for URL-safety on any relay.
        val bytes = ByteArray(16).also(random::nextBytes)
        return "pact" + bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * URL-safe Base64 without padding — the one string encoding above the crypto
 * layer. java.util.Base64 (API 26+) keeps this pure-JVM testable.
 */
object B64 {
    fun encode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(s: String): ByteArray =
        java.util.Base64.getUrlDecoder().decode(s)
}

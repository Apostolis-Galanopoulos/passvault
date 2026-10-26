package dev.passvault.crypto

import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class WrongPasswordException : GeneralSecurityException("Wrong master password or corrupted vault")

class InvalidVaultException(message: String) : IOException(message)

/**
 * Vault file format (all integers big-endian):
 *
 * ```
 * magic "PVLT" (4) | version (1) | PBKDF2 iterations (4) | salt (16) | GCM IV (12) | ciphertext + 16-byte tag
 * ```
 *
 * The key is derived with PBKDF2-HMAC-SHA256 and the payload is encrypted with AES-256-GCM.
 * The whole header is authenticated as associated data, so tampering with any byte
 * (including the iteration count or salt) makes decryption fail.
 */
object VaultCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 1_000
    const val MAX_ITERATIONS = 10_000_000

    private val MAGIC = "PVLT".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + IV_LEN

    private val random = SecureRandom()

    class Sealed(
        val header: ByteArray,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    fun newSalt(): ByteArray = ByteArray(SALT_LEN).also(random::nextBytes)

    /** Derives a raw 256-bit key. The caller owns the returned array and should wipe it. */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(plaintext: ByteArray, key: SecretKey, salt: ByteArray, iterations: Int): ByteArray {
        require(salt.size == SALT_LEN)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC)
            .put(VERSION)
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    fun parse(data: ByteArray): Sealed {
        try {
            val buf = ByteBuffer.wrap(data)
            val magic = ByteArray(MAGIC.size).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) throw InvalidVaultException("Not a PassVault file")
            val version = buf.get()
            if (version != VERSION) throw InvalidVaultException("Unsupported vault version $version")
            val iterations = buf.getInt()
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
                throw InvalidVaultException("Invalid key derivation parameters")
            }
            val salt = ByteArray(SALT_LEN).also { buf.get(it) }
            val iv = ByteArray(IV_LEN).also { buf.get(it) }
            if (buf.remaining() < TAG_BITS / 8) throw InvalidVaultException("Vault file is truncated")
            val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }
            return Sealed(data.copyOf(HEADER_LEN), iterations, salt, iv, ciphertext)
        } catch (e: BufferUnderflowException) {
            throw InvalidVaultException("Vault file is truncated")
        }
    }

    fun open(sealed: Sealed, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.iv))
        cipher.updateAAD(sealed.header)
        try {
            return cipher.doFinal(sealed.ciphertext)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }
}

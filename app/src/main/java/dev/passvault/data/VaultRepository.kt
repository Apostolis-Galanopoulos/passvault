package dev.passvault.data

import dev.passvault.crypto.VaultCrypto
import dev.passvault.crypto.WrongPasswordException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** The derived key for an unlocked vault. Wipe it when locking. */
class Session(private val keyBytes: ByteArray, val salt: ByteArray, val iterations: Int) {
    val key: SecretKey
        @Synchronized get() = SecretKeySpec(keyBytes, "AES")

    @Synchronized
    fun wipe() = keyBytes.fill(0)

    companion object {
        fun derive(password: CharArray, salt: ByteArray, iterations: Int) =
            Session(VaultCrypto.deriveKey(password, salt, iterations), salt, iterations)
    }
}

/** Reads and writes the single encrypted vault file. Blocking; call off the main thread. */
class VaultRepository(
    private val file: File,
    private val iterations: Int = VaultCrypto.DEFAULT_ITERATIONS,
) {
    fun exists(): Boolean = file.isFile

    fun create(password: CharArray): Session {
        val session = Session.derive(password, VaultCrypto.newSalt(), iterations)
        save(session, emptyList())
        return session
    }

    /** @throws WrongPasswordException if the password is wrong or the file was tampered with. */
    fun unlock(password: CharArray): Pair<Session, List<Entry>> {
        val sealed = VaultCrypto.parse(file.readBytes())
        val session = Session.derive(password, sealed.salt, sealed.iterations)
        val plain = try {
            VaultCrypto.open(sealed, session.key)
        } catch (e: WrongPasswordException) {
            session.wipe()
            throw e
        }
        try {
            return session to VaultCodec.decode(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun save(session: Session, entries: List<Entry>) {
        val plain = VaultCodec.encode(entries)
        try {
            writeAtomically(VaultCrypto.seal(plain, session.key, session.salt, session.iterations))
        } finally {
            plain.fill(0)
        }
    }

    /** Re-encrypts [entries] under a new password and a fresh salt. */
    fun rekey(newPassword: CharArray, entries: List<Entry>): Session {
        val session = Session.derive(newPassword, VaultCrypto.newSalt(), iterations)
        save(session, entries)
        return session
    }

    fun exportTo(out: OutputStream) {
        file.inputStream().use { it.copyTo(out) }
    }

    /** Replaces the vault with [bytes] after checking they look like a vault file. */
    fun importFrom(bytes: ByteArray) {
        VaultCrypto.parse(bytes)
        writeAtomically(bytes)
    }

    private fun writeAtomically(bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }
}

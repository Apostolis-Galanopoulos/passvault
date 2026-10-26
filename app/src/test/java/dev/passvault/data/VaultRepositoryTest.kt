package dev.passvault.data

import dev.passvault.crypto.InvalidVaultException
import dev.passvault.crypto.WrongPasswordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class VaultRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val sample = listOf(
        Entry(title = "Email", username = "me@example.com", password = "hunter2", url = "https://mail.example.com"),
        Entry(title = "Bank", username = "1234", password = "pä\$\$wörd ✓", notes = "PIN in safe\nline two"),
    )

    private fun repo(file: File = File(tmp.root, "vault.pvlt")) = VaultRepository(file, iterations = 1_000)

    @Test
    fun roundTrip() {
        val repo = repo()
        val session = repo.create("correct horse".toCharArray())
        repo.save(session, sample)

        val (_, entries) = repo.unlock("correct horse".toCharArray())
        assertEquals(sample, entries)
    }

    @Test
    fun plaintextIsNotStoredOnDisk() {
        val file = File(tmp.root, "vault.pvlt")
        val repo = repo(file)
        repo.save(repo.create("correct horse".toCharArray()), sample)

        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse(raw.contains("hunter2"))
        assertFalse(raw.contains("Email"))
    }

    @Test(expected = WrongPasswordException::class)
    fun wrongPasswordFails() {
        val repo = repo()
        repo.create("correct horse".toCharArray())
        repo.unlock("wrong horse".toCharArray())
    }

    @Test
    fun tamperingIsDetected() {
        val file = File(tmp.root, "vault.pvlt")
        val repo = repo(file)
        repo.save(repo.create("pw12345678".toCharArray()), sample)
        val bytes = file.readBytes()

        // Flip one byte in the header (salt) and one in the ciphertext; both must be rejected.
        for (index in listOf(12, bytes.size - 5)) {
            val tampered = bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            file.writeBytes(tampered)
            try {
                repo.unlock("pw12345678".toCharArray())
                throw AssertionError("Tampering at byte $index was not detected")
            } catch (expected: WrongPasswordException) {
            }
        }
    }

    @Test
    fun rekeyChangesPassword() {
        val repo = repo()
        repo.save(repo.create("old password".toCharArray()), sample)
        repo.rekey("new password".toCharArray(), sample)

        assertEquals(sample, repo.unlock("new password".toCharArray()).second)
        try {
            repo.unlock("old password".toCharArray())
            throw AssertionError("Old password still works")
        } catch (expected: WrongPasswordException) {
        }
    }

    @Test
    fun exportAndImport() {
        val source = repo(File(tmp.root, "a.pvlt"))
        source.save(source.create("backup pass".toCharArray()), sample)
        val backup = ByteArrayOutputStream().also(source::exportTo).toByteArray()

        val target = repo(File(tmp.root, "b.pvlt"))
        target.create("other pass".toCharArray())
        target.importFrom(backup)

        assertEquals(sample, target.unlock("backup pass".toCharArray()).second)
    }

    @Test
    fun importRejectsNonVaultFiles() {
        val repo = repo()
        repo.create("pw12345678".toCharArray())
        for (junk in listOf(ByteArray(0), "hello world, not a vault at all".toByteArray())) {
            try {
                repo.importFrom(junk)
                throw AssertionError("Accepted junk")
            } catch (expected: InvalidVaultException) {
            }
        }
        // The original vault is untouched.
        assertTrue(repo.unlock("pw12345678".toCharArray()).second.isEmpty())
    }

    @Test
    fun wipedSessionHasZeroKey() {
        val session = repo().create("pw12345678".toCharArray())
        session.wipe()
        assertTrue(session.key.encoded.all { it == 0.toByte() })
    }
}

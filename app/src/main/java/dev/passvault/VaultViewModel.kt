package dev.passvault

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.passvault.crypto.InvalidVaultException
import dev.passvault.crypto.WrongPasswordException
import dev.passvault.data.Entry
import dev.passvault.data.Session
import dev.passvault.data.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

const val MIN_MASTER_PASSWORD_LENGTH = 8
const val AUTO_LOCK_MS = 60_000L
private const val MAX_IMPORT_BYTES = 32L * 1024 * 1024

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    enum class Status { NO_VAULT, LOCKED, UNLOCKED }

    private val repo = VaultRepository(File(app.filesDir, "vault.pvlt"))

    /** Serializes all writes to the vault file, and keeps the key alive until pending writes finish. */
    private val writeMutex = Mutex()
    private var session: Session? = null
    private var backgroundedAt = 0L
    private var autoLockJob: Job? = null

    var status by mutableStateOf(if (repo.exists()) Status.LOCKED else Status.NO_VAULT)
        private set
    var entries by mutableStateOf(emptyList<Entry>())
        private set
    var busy by mutableStateOf(false)
        private set

    /** One-off message (error or confirmation) for the UI to show. */
    var message by mutableStateOf<String?>(null)
        private set

    fun consumeMessage() {
        message = null
    }

    fun create(password: String) = runBusy {
        val pw = password.toCharArray()
        val s = try {
            io { repo.create(pw) }
        } finally {
            pw.fill(0.toChar())
        }
        open(s, emptyList())
    }

    fun unlock(password: String) = runBusy {
        val pw = password.toCharArray()
        val (s, list) = try {
            io { repo.unlock(pw) }
        } finally {
            pw.fill(0.toChar())
        }
        open(s, list)
    }

    private fun open(s: Session, list: List<Entry>) {
        session = s
        entries = list
        status = Status.UNLOCKED
    }

    fun lock() {
        autoLockJob?.cancel()
        val s = session ?: return
        session = null
        entries = emptyList()
        status = Status.LOCKED
        viewModelScope.launch { writeMutex.withLock { s.wipe() } }
    }

    fun upsert(entry: Entry) = persist { list ->
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        if (list.any { it.id == entry.id }) list.map { if (it.id == entry.id) stamped else it } else list + stamped
    }

    fun delete(id: String) = persist { list -> list.filterNot { it.id == id } }

    private fun persist(transform: (List<Entry>) -> List<Entry>) {
        val s = session ?: return
        val updated = transform(entries)
        entries = updated
        viewModelScope.launch {
            try {
                writeMutex.withLock { io { repo.save(s, updated) } }
            } catch (e: IOException) {
                message = "Could not save vault: ${e.message}"
            }
        }
    }

    fun changePassword(current: String, new: String) = runBusy {
        val old = session ?: return@runBusy
        val snapshot = entries
        val cur = current.toCharArray()
        val nw = new.toCharArray()
        val newSession = try {
            writeMutex.withLock {
                io {
                    repo.unlock(cur).first.wipe() // verifies the current password
                    repo.rekey(nw, snapshot)
                }
            }
        } finally {
            cur.fill(0.toChar())
            nw.fill(0.toChar())
        }
        if (session === old) {
            session = newSession
            old.wipe()
        } else {
            newSession.wipe()
        }
        message = "Master password changed"
    }

    fun exportBackup(uri: Uri) = runBusy {
        val resolver = getApplication<Application>().contentResolver
        writeMutex.withLock {
            io {
                val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open destination")
                out.use { repo.exportTo(it) }
            }
        }
        message = "Encrypted backup exported"
    }

    fun importBackup(uri: Uri) = runBusy {
        val resolver = getApplication<Application>().contentResolver
        writeMutex.withLock {
            io {
                val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open file")
                val bytes = input.use {
                    val data = it.readBytes()
                    if (data.size > MAX_IMPORT_BYTES) throw InvalidVaultException("File is too large")
                    data
                }
                repo.importFrom(bytes)
            }
        }
        lock()
        status = Status.LOCKED
        message = "Backup restored. Unlock it with its master password."
    }

    fun onBackground() {
        if (status != Status.UNLOCKED) return
        backgroundedAt = SystemClock.elapsedRealtime()
        autoLockJob?.cancel()
        autoLockJob = viewModelScope.launch {
            delay(AUTO_LOCK_MS)
            lock()
        }
    }

    fun onForeground() {
        autoLockJob?.cancel()
        autoLockJob = null
        // delay() may not fire while the device sleeps, so check elapsed time too.
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt >= AUTO_LOCK_MS) lock()
        backgroundedAt = 0L
    }

    override fun onCleared() {
        session?.wipe()
        session = null
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: WrongPasswordException) {
                message = "Wrong master password"
            } catch (e: Exception) {
                message = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

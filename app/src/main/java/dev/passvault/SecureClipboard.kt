package dev.passvault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

const val CLIPBOARD_CLEAR_MS = 30_000L

/** Copies secrets to the clipboard, flags them as sensitive, and clears them after a timeout. */
object SecureClipboard {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            // Hides the content from the clipboard preview on Android 13+.
            val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
            putBoolean(key, true)
        }
        clipboard.setPrimaryClip(clip)

        pendingClear?.let(handler::removeCallbacks)
        pendingClear = Runnable {
            if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            pendingClear = null
        }.also { handler.postDelayed(it, CLIPBOARD_CLEAR_MS) }
    }
}

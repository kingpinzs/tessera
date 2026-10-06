package app.tileshell.media

import android.app.Application
import android.content.Context
import android.content.IntentSender
import java.io.File

/**
 * The write layer as each process of the shell builds it: the real MediaStore port and that PROCESS's own pending
 * ledger (`files/media_pending/<process>.txt`), so a start-up cleanup in one process never deletes a row another
 * process is writing. ONE layer per process (A2-L4): every caller in a process gets the same [MediaWrites] over the
 * same ledger object, so two writers cannot lose each other's ledger lines. Nothing else in the shell constructs a
 * [MediaWrites] or a [FilePendingLedger] (`media/UriAccessWiringScanTest` holds that).
 */
object ShellMediaWrites {
    @Volatile private var layer: MediaWrites<IntentSender>? = null

    fun of(context: Context): MediaWrites<IntentSender> = layer ?: synchronized(this) {
        layer ?: build(context.applicationContext).also { layer = it }
    }

    private fun build(app: Context): MediaWrites<IntentSender> {
        val process = Application.getProcessName().substringAfter(':', "main").filter { it.isLetterOrDigit() }.ifEmpty { "main" }
        return MediaWrites(AndroidMediaStorePort(app), app.packageName, FilePendingLedger.of(File(File(app.filesDir, "media_pending"), "$process.txt")))
    }
}

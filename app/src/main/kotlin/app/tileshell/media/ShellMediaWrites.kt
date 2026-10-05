package app.tileshell.media

import android.app.Application
import android.content.Context
import android.content.IntentSender
import java.io.File

/**
 * The write layer as each process of the shell builds it: the real MediaStore port and that PROCESS's own pending
 * ledger (`files/media_pending/<process>.txt`), so a start-up cleanup in one process never deletes a row another
 * process is writing.
 */
object ShellMediaWrites {
    fun of(context: Context): MediaWrites<IntentSender> {
        val app = context.applicationContext
        val process = Application.getProcessName().substringAfter(':', "main").filter { it.isLetterOrDigit() }.ifEmpty { "main" }
        return MediaWrites(AndroidMediaStorePort(app), app.packageName, FilePendingLedger(File(File(app.filesDir, "media_pending"), "$process.txt")))
    }
}

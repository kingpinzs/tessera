package app.tileshell.files

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import app.tileshell.diag.Diagnostics
import java.io.File

/**
 * Share (build task 3; r3 V2, T18-11): the selected files to Android's chooser as `content://` URIs of the shell's
 * FileProvider. [FilesProvider.uriFor] is the only maker of those URIs and runs the scope guard: a file it refuses
 * stops the whole share (the refusal line is its own). Folders are never shared (the bar's Share is dim with one
 * selected).
 */
object FilesShare {
    const val ANY = "*/*"
    private const val UNKNOWN = "application/octet-stream"

    /** A file's MIME type by its extension. */
    fun mimeOf(name: String): String {
        val ext = FilePaths.splitExtension(name).second.removePrefix(".").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: UNKNOWN
    }

    /** One common type when every file has it, else any. */
    fun commonType(types: List<String>): String = types.distinct().singleOrNull() ?: ANY

    /** Starts the share; false when nothing was started (a refused file, or no chooser). */
    fun share(activity: Activity, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        val uris = ArrayList<Uri>()
        for (file in files) uris += FilesProvider.uriFor(activity, file) ?: return false
        val type = commonType(files.map { mimeOf(it.name) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = type
        // The grant must reach EVERY URI: the flag covers the intent's data and its ClipData, never an extra alone.
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { for (i in 1 until uris.size) addItem(ClipData.Item(uris[i])) }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Diagnostics.add("files", "share ${uris.size} files type=$type uris=${uris.joinToString(",")}")
        return runCatching { activity.startActivity(Intent.createChooser(send, null)) }
            .onFailure { Diagnostics.add("files", "share not started: ${it.javaClass.simpleName}") }
            .isSuccess
    }
}

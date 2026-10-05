package app.tileshell.testclient.qacapture

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * The fixture's own file provider: `content://app.tileshell.testclient.qacapture.output/<name>` is the file `<name>`
 * in this app's cache dir. Not exported — another app reaches a file only through a URI grant this app gives it, which
 * is the grant a capture request carries. (Written here rather than taken from androidx so the fixture has no dependency.)
 */
class OutputProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw java.io.FileNotFoundException("no name")
        require(!name.contains('/') && name != "..") { "bad name" }
        return File(context!!.cacheDir, name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))

    override fun getType(uri: Uri): String = if (uri.lastPathSegment.orEmpty().endsWith(".mp4")) "video/mp4" else "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "app.tileshell.testclient.qacapture.output"
        fun uriFor(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")
    }
}

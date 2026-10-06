package app.tileshell.testclient.qaview

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * `content://app.tileshell.testclient.qaview.files/<name>` is the file `<name>` in this app's filesDir (a row puts it
 * there with `run-as`). Not exported: another app reads it only through a URI grant this app gives.
 */
class OwnProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw java.io.FileNotFoundException("no name")
        require(!name.contains('/') && name != "..") { "bad name" }
        return File(context!!.filesDir, name)
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri): String = "video/mp4"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val f = file(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any>(f.name, f.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "app.tileshell.testclient.qaview.files"
    }
}

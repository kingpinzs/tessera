package app.tileshell.testclient.qaphotoview

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** `content://app.tileshell.testclient.qaphotoview.own/own.png` is the file `own.png` in this app's cache dir. Not exported. */
class OwnPictureProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.lastPathSegment != NAME) throw java.io.FileNotFoundException("no such picture")
        return ParcelFileDescriptor.open(File(context!!.cacheDir, NAME), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val NAME = "own.png"
        val URI: Uri = Uri.parse("content://app.tileshell.testclient.qaphotoview.own/$NAME")
    }
}

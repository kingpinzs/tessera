package app.tileshell.testclient.qacapturefwd

import android.app.Activity
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * App V of the forwarded-result leg (phase 17 E9, the trust fixes' leg (e)).
 *
 *   am start -n app.tileshell.testclient.qacapturefwd/.ForwardStartActivity [--ez share true]
 *
 * It starts qa-capture's CaptureProbeActivity (`leg=forward`) FOR A RESULT with `content://…qacapturefwd.output/fwd.jpg`
 * — a file in this app's own cache — as the data and a write grant. The go-between starts the shell's capture with
 * FLAG_ACTIVITY_FORWARD_RESULT, so the result comes back here. Logged under TileShellQa as `leg=fwd-v <name>=<value>`.
 */
class ForwardStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val file = File(cacheDir, "fwd.jpg").also { it.delete() }
        val uri = Uri.parse("content://app.tileshell.testclient.qacapturefwd.output/fwd.jpg")
        val request = Intent()
            .setComponent(ComponentName("app.tileshell.testclient.qacapture", "app.tileshell.testclient.qacapture.CaptureProbeActivity"))
            .putExtra("leg", "forward")
            .putExtra("share", intent.getBooleanExtra("share", false))
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        say("start go-between data=$uri share=${intent.getBooleanExtra("share", false)} file=${file.name}")
        try {
            startActivityForResult(request, 18)
            say("start threw=none")
        } catch (e: Exception) {
            say("start threw=${e.javaClass.simpleName}")
            say("done")
            finish()
        }
    }

    @Deprecated("the platform's own result callback is what a caller of a capture intent uses")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val file = File(cacheDir, "fwd.jpg")
        say("result=" + when (resultCode) { RESULT_OK -> "RESULT_OK"; RESULT_CANCELED -> "RESULT_CANCELED"; else -> resultCode.toString() })
        say("output exists=${file.exists()} size=${if (file.exists()) file.length() else 0}")
        say("done")
        finish()
    }

    private fun say(text: String) { Log.i("TileShellQa", "leg=fwd-v $text") }
}

/** This app's own file provider: `…/fwd.jpg` is `fwd.jpg` in its cache. Not exported; reached only through a URI grant. */
class FwdProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment ?: throw java.io.FileNotFoundException("no name")
        require(!name.contains('/') && name != "..") { "bad name" }
        return ParcelFileDescriptor.open(File(context!!.cacheDir, name), ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

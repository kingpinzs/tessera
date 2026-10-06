package app.tileshell.testclient.qaphotoview

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * Sends ONE `ACTION_VIEW` of an image to the shell's ViewerActivity and logs what its own start did.
 *
 * Extras (all through `am start -n … --es / --ez`):
 *  - `uri`   the content URI to view; ignored with `own`
 *  - `own`   view this app's own picture (a 640 x 480 field of (30,160,60) written to its cache) through its provider
 *  - `flag`  add FLAG_GRANT_READ_URI_PERMISSION
 *  - `share` start with `ActivityOptions.setShareIdentityEnabled(true)`
 */
class ViewProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val own = intent.getBooleanExtra("own", false)
        val flag = intent.getBooleanExtra("flag", false)
        val share = intent.getBooleanExtra("share", false)
        val uri = if (own) {
            File(cacheDir, OwnPictureProvider.NAME).outputStream().use { out ->
                Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 160, 60)) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            OwnPictureProvider.URI
        } else {
            Uri.parse(intent.getStringExtra("uri") ?: "")
        }
        val held = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png").setClassName("app.tileshell", "app.tileshell.photos.ViewerActivity")
        if (flag) view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val options = if (share) ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle() else null
        val what = "uri=$uri own=$own flag=$flag share=$share holdsReadMediaImages=$held"
        try {
            startActivity(view, options)
            Log.i(TAG, "view start ok $what")
        } catch (e: Exception) {
            Log.i(TAG, "view start threw ${e.javaClass.simpleName} $what")
        }
        finish()
    }

    private companion object {
        const val TAG = "TileShellQa"
    }
}

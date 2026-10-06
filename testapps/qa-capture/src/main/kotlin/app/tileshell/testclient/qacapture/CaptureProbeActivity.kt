package app.tileshell.testclient.qacapture

import android.app.Activity
import android.app.ActivityOptions
import android.content.ContentValues
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * A caller of the shell's capture answer (phase 17 E9). Every request names the shell (`setPackage("app.tileshell")`).
 *
 *   am start -n app.tileshell.testclient.qacapture/.CaptureProbeActivity --es leg <leg> [--es uri <uri>] [--ez front true]
 *
 * Legs:
 *   image-content  IMAGE_CAPTURE, EXTRA_OUTPUT = this app's own provider URI, no ClipData of its own (Android adds the
 *                  ClipData and the grant flags itself)
 *   video-content  the same with VIDEO_CAPTURE
 *   image-file     IMAGE_CAPTURE with a file:// EXTRA_OUTPUT, this app's StrictMode VmPolicy relaxed first so the
 *                  platform's FileUriExposedException does not stop it on the sender
 *   image-none     IMAGE_CAPTURE with no EXTRA_OUTPUT
 *   video-none     VIDEO_CAPTURE with no EXTRA_OUTPUT
 *   clip-noflag    IMAGE_CAPTURE, EXTRA_OUTPUT = the URI the driver passes (`--es uri`), this app's OWN ClipData holding
 *                  it and NO grant flag
 *   clip-flag      the same with FLAG_GRANT_WRITE_URI_PERMISSION set
 *   own-media      IMAGE_CAPTURE, EXTRA_OUTPUT = a MediaStore image row THIS app inserts (Pictures/QaCapture), its own
 *                  ClipData of it and FLAG_GRANT_WRITE_URI_PERMISSION; after the result the row's bytes are counted
 *                  through this app's own access (`media uri=<uri> size=<n>`)
 *   grant-only     IMAGE_CAPTURE, EXTRA_OUTPUT = the URI this activity was STARTED with (`-d <uri>` with
 *                  `--grant-write-uri-permission`): a URI this app holds only a grant for; no ClipData of its own
 *   string-output  IMAGE_CAPTURE, EXTRA_OUTPUT put as a String (this app's own provider URI's text), not a Uri
 *   forward        the go-between of a forwarded result: started FOR A RESULT by another app with that app's provider
 *                  URI as data (and a write grant), it starts the capture with FLAG_ACTIVITY_FORWARD_RESULT and
 *                  finishes — the result goes to the other app. `--ez share true` starts it with
 *                  setShareIdentityEnabled(true).
 * `--ez prefill true` (image-content): the output file is first filled with 3,000,000 bytes (`prefill size=<n>`), so
 * the row can see the capture truncate it.
 *
 * Every fact is one logcat line under the tag TileShellQa, `leg=<leg> <name>=<value> …`, so the row greps them.
 */
class CaptureProbeActivity : Activity() {
    private var leg = ""
    private var outputFile: File? = null
    private var mediaUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            leg = savedInstanceState.getString("leg").orEmpty(); outputFile = savedInstanceState.getString("file")?.let(::File)
            mediaUri = savedInstanceState.getString("media")?.let(Uri::parse); return
        }
        leg = intent.getStringExtra("leg") ?: "image-content"
        val video = leg.startsWith("video")
        val request = Intent(if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE).setPackage("app.tileshell")
        if (intent.getBooleanExtra("front", false)) request.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        when (leg) {
            "image-content", "video-content" -> {
                val name = if (video) "out.mp4" else "out.jpg"
                outputFile = File(cacheDir, name).also { it.delete() }
                if (intent.getBooleanExtra("prefill", false)) {
                    outputFile!!.outputStream().use { out -> val block = ByteArray(100_000) { 0x55 }; repeat(30) { out.write(block) } }
                    say("prefill size=${outputFile!!.length()}")
                }
                request.putExtra(MediaStore.EXTRA_OUTPUT, OutputProvider.uriFor(name))
            }
            "own-media" -> {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "qa-capture-own-${System.currentTimeMillis()}.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/QaCapture")
                }
                val row = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                mediaUri = row
                say("media inserted uri=${row ?: "none"}")
                if (row != null) {
                    request.putExtra(MediaStore.EXTRA_OUTPUT, row)
                    request.clipData = ClipData.newRawUri("output", row)
                    request.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
            "grant-only" -> {
                val target = intent.data
                mediaUri = target
                say("started with data=${target ?: "none"} flags=0x${Integer.toHexString(intent.flags)}")
                if (target != null) request.putExtra(MediaStore.EXTRA_OUTPUT, target)
            }
            "string-output" -> {
                outputFile = File(cacheDir, "out_string.jpg").also { it.delete() }
                request.putExtra(MediaStore.EXTRA_OUTPUT, OutputProvider.uriFor("out_string.jpg").toString())
            }
            "forward" -> {
                val target = intent.data
                say("forward for=${callingPackage ?: "no caller"} data=${target ?: "none"} share=${intent.getBooleanExtra("share", false)}")
                if (target != null) {
                    request.putExtra(MediaStore.EXTRA_OUTPUT, target)
                    request.clipData = ClipData.newRawUri("output", target)
                    request.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                request.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                try {
                    if (intent.getBooleanExtra("share", false)) startActivity(request, ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle())
                    else startActivity(request)
                    say("forward start threw=none")
                } catch (e: Exception) {
                    say("forward start threw=${e.javaClass.simpleName}")
                }
                say("done")
                finish()
                return
            }
            "image-file" -> {
                StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
                outputFile = File(cacheDir, "out_file.jpg").also { it.delete() }
                request.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(outputFile))
            }
            "clip-noflag", "clip-flag" -> {
                val target = Uri.parse(intent.getStringExtra("uri") ?: "")
                request.putExtra(MediaStore.EXTRA_OUTPUT, target)
                request.clipData = ClipData.newRawUri("output", target)
                if (leg == "clip-flag") request.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        say("start action=${request.action} output=${request.extras?.get(MediaStore.EXTRA_OUTPUT) ?: "none"} flags=0x${Integer.toHexString(request.flags)}")
        try {
            startActivityForResult(request, REQUEST)
            say("start threw=none")
        } catch (e: Exception) {
            say("start threw=${e.javaClass.simpleName}")
            say("done")
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("leg", leg)
        outState.putString("file", outputFile?.path)
        outState.putString("media", mediaUri?.toString())
    }

    @Deprecated("the platform's own result callback is what a caller of a capture intent uses")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        say("result=" + when (resultCode) { RESULT_OK -> "RESULT_OK"; RESULT_CANCELED -> "RESULT_CANCELED"; else -> resultCode.toString() })
        outputFile?.let { f ->
            say("output exists=${f.exists()} size=${if (f.exists()) f.length() else 0} md5=${if (f.exists()) md5(f.inputStream()) else "none"}")
        }
        mediaUri?.let { m ->
            val size = runCatching { contentResolver.openInputStream(m)?.use { it.readBytes().size } }
            say("media uri=$m size=${size.getOrNull() ?: 0} read=${if (size.isSuccess) "ok" else size.exceptionOrNull()?.javaClass?.simpleName}")
        }
        val bitmap = runCatching { data?.getParcelableExtra("data", Bitmap::class.java) }.getOrNull()
        say("data bitmap=${if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "none"}")
        val uri = data?.data
        if (uri != null) {
            val size = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().size } }.getOrNull()
            say("returned uri=$uri flags=0x${Integer.toHexString(data.flags)} exists=${size != null} size=${size ?: 0}")
        } else {
            say("returned uri=none")
        }
        say("done")
        finish()
    }

    private fun say(text: String) { Log.i(TAG, "leg=$leg $text") }

    companion object {
        const val TAG = "TileShellQa"
        private const val REQUEST = 17

        fun md5(stream: InputStream): String = stream.use { input ->
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(1 shl 16)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

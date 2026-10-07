package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * The send probe's own sender, for the harness's self-test only (phase 18 build task 12): `am start` cannot carry a
 * list of URIs or grant an `EXTRA_STREAM`, so a shell-driven `SEND_MULTIPLE` needs an app to send it.
 *
 *   am start -n app.tileshell.testclient.qacapture/.SendDriverActivity [--es mime <type>]
 *       --esa texts <a>,<b>[,…]          each text becomes a file in this app's cache, named by [OutputProvider]
 *       --esa uris <uri>,<uri>[,…]       these `content://` URIs are sent as they are
 *
 * One stream is sent as `ACTION_SEND`, two or more as `ACTION_SEND_MULTIPLE` with an `ArrayList<Uri>`. The intent names
 * this PACKAGE, not the probe's class, so it reaches [SendProbeActivity] only through the manifest's intent filter.
 * This app holds no permission: a `uris` stream is readable only if the caller granted it, which the shell does with
 * `-d <prefix> --grant-read-uri-permission --grant-prefix-uri-permission` on the same `am start` — so an md5 the probe
 * logs for such a URI was read through a real URI grant, as a share target reads the shell's.
 */
class SendDriverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texts = intent.getStringArrayExtra("texts").orEmpty()
        val mime = intent.getStringExtra("mime") ?: "text/plain"
        val uris = ArrayList<Uri>()
        texts.forEachIndexed { i, text ->
            val name = "send-$i.txt"
            File(cacheDir, name).writeBytes(text.toByteArray())
            uris += OutputProvider.uriFor(name)
        }
        intent.getStringArrayExtra("uris").orEmpty().forEach { uris += Uri.parse(it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.setType(mime).setPackage(packageName).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Log.i(CaptureProbeActivity.TAG, "driver sends ${uris.size} stream(s) action=${send.action} type=$mime")
        runCatching { startActivity(send) }.onFailure { Log.i(CaptureProbeActivity.TAG, "driver failed: $it") }
        finish()
    }
}

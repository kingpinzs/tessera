package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Receives a share and logs what it was sent, with the md5 of every stream it could read.
 *
 * `ACTION_SEND` (phase 17 E5, r3 V10: `image/` any subtype; phase 18 E7: any type) logs ONE line, in phase 17's form:
 *   `send action=<action> type=<mime> uri=<uri|none> md5=<md5|unreadable>`
 * `ACTION_SEND_MULTIPLE` (phase 18 E7, r3 V2) logs a count line and then the same line once per stream, in the order
 * the sender listed them:
 *   `send streams=<n> action=<action> type=<mime>`
 * A URI the sender handed out but this app cannot open reads `md5=unreadable`: a provider that shares unreadable URIs
 * cannot pass a row that compares the md5s.
 */
class SendProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Log.i(CaptureProbeActivity.TAG, "send streams=${uris.size} action=${intent.action} type=${intent.type}")
            uris.forEach(::logStream)
        } else {
            logStream(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        finish()
    }

    private fun logStream(uri: Uri?) {
        val md5 = runCatching { uri?.let { contentResolver.openInputStream(it) }?.let(CaptureProbeActivity::md5) }.getOrNull()
        Log.i(CaptureProbeActivity.TAG, "send action=${intent.action} type=${intent.type} uri=${uri ?: "none"} md5=${md5 ?: "unreadable"}")
    }
}

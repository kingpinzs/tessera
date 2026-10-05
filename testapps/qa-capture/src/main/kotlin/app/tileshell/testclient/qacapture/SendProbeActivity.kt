package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/** Receives `ACTION_SEND` of any image type (phase 17 E5, r3 V10) and logs the action, the URI and the md5 of the stream it reads. */
class SendProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        val md5 = runCatching { uri?.let { contentResolver.openInputStream(it) }?.let(CaptureProbeActivity::md5) }.getOrNull()
        Log.i(CaptureProbeActivity.TAG, "send action=${intent.action} type=${intent.type} uri=${uri ?: "none"} md5=${md5 ?: "unreadable"}")
        finish()
    }
}

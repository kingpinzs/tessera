package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.ComponentName
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * A LEGACY controller on the shell's exported music session (ledger L18-4; pass 3's P3-1). The framework
 * `android.media.browse.MediaBrowser` + `android.media.session.MediaController` — NOT a Media3 controller, which cannot
 * call `skipToQueueItem` with an out-of-range id. This app holds no permission and needs no dependency (framework
 * classes only).
 *
 *   am start -n app.tileshell.testclient.qacapture/.LegacyQueueActivity --es tag <name> --el queue <queueId>
 *
 * Media3 1.9.0's `MediaSessionLegacyStub.onSkipToQueueItem(long)` refuses only `queueId < 0`, then `l2i`-casts the long
 * into `seekToDefaultPosition(int)`; its dispatch catches only `RemoteException`. So a huge positive id (2147483648,
 * 4294967294) casts to a negative/absurd int and may reach the player. The SAFE outcome: the shell's launcher process
 * is not killed (its pid is unchanged) and logcat holds no FATAL for app.tileshell. Logged under TileShellQa:
 *   legacyqueue <tag> connect=<connected|failed|suspended>
 *   legacyqueue <tag> skipToQueueItem queue=<id> result=<sent|threw <Class>>
 *   legacyqueue <tag> done
 */
class LegacyQueueActivity : Activity() {
    private val service = ComponentName("app.tileshell", "app.tileshell.music.MusicService")
    private var browser: MediaBrowser? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tag = intent.getStringExtra("tag") ?: "q"
        val queue = intent.getLongExtra("queue", 0L)
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                say(tag, "connect=connected")
                val controller = MediaController(this@LegacyQueueActivity, browser!!.sessionToken)
                val result = runCatching { controller.transportControls.skipToQueueItem(queue); "sent" }
                    .getOrElse { "threw ${it.javaClass.simpleName}" }
                say(tag, "skipToQueueItem queue=$queue result=$result")
                main.postDelayed({ done(tag) }, SETTLE_MS)
            }

            override fun onConnectionFailed() { say(tag, "connect=failed"); done(tag) }
            override fun onConnectionSuspended() { say(tag, "connect=suspended"); done(tag) }
        }
        runCatching { browser = MediaBrowser(this, service, callback, null).also { it.connect() } }
            .onFailure { say(tag, "connect=failed ${it.javaClass.simpleName}"); done(tag) }
    }

    private var finished = false
    private fun done(tag: String) {
        if (finished) return
        finished = true
        runCatching { browser?.disconnect() }
        say(tag, "done")
        finish()
    }

    private fun say(tag: String, line: String) { Log.i(CaptureProbeActivity.TAG, "legacyqueue $tag $line") }

    private companion object { const val SETTLE_MS = 2_000L }
}

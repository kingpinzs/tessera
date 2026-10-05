package app.tileshell.video

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent
import app.tileshell.video.server.MediaServer
import app.tileshell.video.server.MediaServerShortcut

/**
 * Movies & TV (phase 17, Q3b A): the hub — My videos, Browse and the Media server page behind W10M's pane — an app
 * inside the shell APK with its own task, `singleTask`, in its own process `:video`. Its library pages draw phase 01's
 * status bar (Y7). Playback is [PlayerActivity]'s, the one player surface of the shell.
 */
class VideoActivity : ComponentActivity() {
    private val nav = VideoNav()
    private var ring: android.content.ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("video", "VideoActivity created")
        hideSystemBars()
        ring = RemoteRings.hold(this, VideoDumpService::class.java)
        route(intent)
        // Y7: the status bar and the header are one #171717 band; each page draws its own black under them.
        val browse = BrowseModel(this)
        setShellAppContent(statusBar = true, background = HubMetrics.CHROME) { VideoApp(nav, this, browse) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    /**
     * Opens the page the intent asks for once it is known whether a media server is set up (a file and a Keystore read,
     * off the main thread): the Media server page exists only then, and its dynamic shortcut is kept in step.
     */
    private fun route(intent: Intent?) {
        Thread {
            val setUp = runCatching { MediaServer(this).isSetUp() }.getOrDefault(false)
            runCatching { MediaServerShortcut.sync(this, setUp) }
            runOnUiThread {
                nav.serverSetUp = setUp
                nav.open(intent)
                nav.ready = true
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        nav.resumes++
    }

    override fun onDestroy() {
        RemoteRings.release(this, ring)
        super.onDestroy()
    }
}

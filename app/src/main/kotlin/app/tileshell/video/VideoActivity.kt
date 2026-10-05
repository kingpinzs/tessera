package app.tileshell.video

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent

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
        nav.open(intent)
        setShellAppContent(statusBar = true) { VideoApp(nav, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nav.open(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        RemoteRings.release(this, ring)
        super.onDestroy()
    }
}

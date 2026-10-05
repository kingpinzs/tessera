package app.tileshell.video

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent

/**
 * The one player of the shell (phase 17, Q4 A; r3 D6): what a video tapped in Photos, in the hub or (phase 18) in Files
 * plays in, and what `ACTION_VIEW` on a video from another app opens — `content://` and `http(s)://` sources. A helper in
 * standard launch mode in `:video`, so it runs in its caller's task and is not a catalog entry. Exported, so it is on
 * qa/phase-03/exported-allowlist.txt: the intent only names what is played.
 */
class PlayerActivity : ComponentActivity() {
    private val nav = PlayerNav()
    private var ring: android.content.ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("video", "PlayerActivity created")
        hideSystemBars()
        ring = RemoteRings.hold(this, VideoDumpService::class.java)
        nav.open(intent)
        setShellAppContent(statusBar = false) { PlayerScreen(nav, this) }
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

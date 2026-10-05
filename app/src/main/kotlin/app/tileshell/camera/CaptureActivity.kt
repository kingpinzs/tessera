package app.tileshell.camera

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent

/**
 * The capture answer (phase 17; Q5 A kept by Q-17-2 (b)): `IMAGE_CAPTURE` and `VIDEO_CAPTURE` from an app that names
 * the shell or targets API 29 or lower. A helper in standard launch mode in `:camera`, so it runs in its caller's task
 * and is not a catalog entry. Exported and TRUST-TOUCHING — on qa/phase-03/exported-allowlist.txt and under the
 * adversarial review: what it may write is the output guard's alone (Decisions 2026-10-05, r3 D1).
 */
class CaptureActivity : ComponentActivity() {
    private val nav = CaptureNav()
    private var ring: android.content.ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("camera", "CaptureActivity created")
        hideSystemBars()
        ring = RemoteRings.hold(this, CameraDumpService::class.java)
        nav.open(intent)
        setShellAppContent(statusBar = false) { CaptureScreen(nav, this) }
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

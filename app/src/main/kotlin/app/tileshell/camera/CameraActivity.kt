package app.tileshell.camera

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent

/**
 * Camera (phase 17): W10M's Camera, an app inside the shell APK — a launcher activity with its own task, answering
 * `STILL_IMAGE_CAMERA` and `VIDEO_CAMERA`, so the CAMERA slot (the bottom-row tile, Tess's "take a photo") can point at
 * it. `singleTask`, in its own process `:camera` (capture must not be able to take Start down). Another app's capture
 * request never reaches this activity: `IMAGE_CAPTURE` / `VIDEO_CAPTURE` are [CaptureActivity]'s (r3 D6).
 */
class CameraActivity : ComponentActivity() {
    private val nav = CameraNav()
    private var ring: android.content.ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("camera", "CameraActivity created")
        hideSystemBars()
        ring = RemoteRings.hold(this, CameraDumpService::class.java)
        nav.open(intent)
        setShellAppContent(statusBar = false) { CameraApp(nav, this) }
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

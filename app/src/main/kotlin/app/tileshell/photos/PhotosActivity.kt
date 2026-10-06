package app.tileshell.photos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics

import app.tileshell.ui.setShellAppContent

/**
 * Photos (phase 17): W10M's Photos over MediaStore, an app inside the shell APK as Music and People are — a launcher
 * activity with its own task and `CATEGORY_APP_GALLERY`, so the PHOTOS slot can point at it. `singleTask`: the Photos
 * tile or an App Shortcut re-routes the running app ([onNewIntent]). It runs in the launcher's process; the editor and
 * trim run in `:photosedit` ([EditActivity], r3 D8). Other apps never reach this activity with data: `VIEW` on an image
 * is [ViewerActivity]'s (r3 D6).
 */
class PhotosActivity : ComponentActivity() {
    private val nav = PhotosNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("photosapp", "PhotosActivity created")
        hideSystemBars()
        nav.open(intent)
        setShellAppContent(statusBar = false) { PhotosApp(nav, this) }
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
}

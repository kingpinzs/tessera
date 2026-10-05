package app.tileshell.photos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent

/**
 * Photos' editor and video trim (phase 17, Q1 C; r3 D8), in `:photosedit` so a full-resolution decode can never take
 * the launcher's process down. Not exported: only Photos starts it, by explicit component, with the MediaStore URI of
 * the item to edit. Every result is a copy through `media/MediaWrites`; the original is never rewritten.
 */
class EditActivity : ComponentActivity() {
    private val nav = EditNav()
    private var ring: android.content.ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("photosapp", "EditActivity created")
        hideSystemBars()
        ring = RemoteRings.hold(this, PhotosEditDumpService::class.java)
        nav.open(intent)
        setShellAppContent(statusBar = false) { EditScreen(nav, this) }
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

package app.tileshell.photos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics

import app.tileshell.ui.setShellAppContent

/**
 * The viewer other apps reach (phase 17, r3 D6): `ACTION_VIEW` on an image content URI. A helper in standard launch
 * mode, so it runs in its caller's task and is not a catalog entry — Back on Start never lands here (L14-2). Exported,
 * so it is on qa/phase-03/exported-allowlist.txt: the intent only names the one picture shown, read with the caller's
 * grant; nothing is written from an intent.
 */
class ViewerActivity : ComponentActivity() {
    private val nav = ViewerNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("photosapp", "ViewerActivity created")
        hideSystemBars()
        nav.open(intent)
        setShellAppContent(statusBar = false) { ViewerScreen(nav, this) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }
}

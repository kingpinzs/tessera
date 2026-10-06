package app.tileshell.photos

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.media.AndroidUriAccess
import app.tileshell.ui.setShellAppContent

/**
 * The viewer other apps reach (phase 17, r3 D6): `ACTION_VIEW` on an image content URI. A helper in standard launch
 * mode, so it runs in its caller's task and is not a catalog entry — Back on Start never lands here (L14-2). Exported
 * and TRUST-TOUCHING, so it is on qa/phase-03/exported-allowlist.txt.
 *
 * The picture is read with the SHELL's identity, so who started the viewer decides what happens — and this class
 * decides nothing: it hands the launch intent and the platform port to `ViewerNav.open`, whose state is
 * `ViewerRules.state`'s answer (the trust reviews' C-M4, C2-M1). Another app's content URI is shown only when the
 * platform says the app that started the viewer could read it itself — else the error state and
 * `[photosapp] refused view: no grant` — and then read-only: the picture, Share and File information; Edit, Delete and
 * Set as are offered only when the shell itself opened the viewer. Nothing is written from an intent.
 */
class ViewerActivity : ComponentActivity() {
    private val nav = ViewerNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("photosapp", "ViewerActivity created")
        hideSystemBars()
        nav.open(intent, AndroidUriAccess(this))
        setShellAppContent(statusBar = false) { ViewerScreen(nav, this) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }
}

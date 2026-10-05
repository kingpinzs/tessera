package app.tileshell.photos

import android.content.Intent
import android.os.Bundle
import android.os.Process
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
 * The picture is read with the SHELL's identity, so who started the viewer decides what happens (`ViewerRules`, the
 * trust review's C-M4): `getLaunchedFromUid()` names the starter where the platform gives it. Another app's content URI
 * is shown only when that app could read it itself — else the error state and `[photosapp] refused view: no grant` —
 * and then read-only: the picture, Share and File information; Edit, Delete and Set as are offered only when the shell
 * itself opened the viewer. Nothing is written from an intent.
 */
class ViewerActivity : ComponentActivity() {
    private val nav = ViewerNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("photosapp", "ViewerActivity created")
        hideSystemBars()
        // -1 (the platform does not say who started this) is another app, never the shell.
        nav.open(intent, launchedFromUid.takeIf { it >= 0 }, Process.myUid(), AndroidUriAccess(this))
        setShellAppContent(statusBar = false) { ViewerScreen(nav, this) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }
}

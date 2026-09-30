package app.tileshell.cortana

import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.diag.Diagnostics

/**
 * Raises Android's own unlock prompt for the "Unlock to continue" card (H12), then gets out of the way.
 *
 * It draws nothing: Tess has stepped aside for the prompt (`CortanaSession.requestUnlock`) and comes back when the
 * result is delivered — unlocked, the gated request runs in the session; cancelled, the card is still there and the
 * request is still pending (Decisions).
 *
 * It must NOT show over the lock screen (L14-1). A page that does occludes the keyguard as it opens, and SystemUI's
 * occlusion handling cancels every pending dismiss: `requestDismissKeyguard` from `onCreate` was cancelled 73 ms later
 * (`setOccluded(true)` → `notifyDismissCancelled`, qa/phase-14/L14-1/rootcause-run1), and the PIN pad never stayed up.
 * Not showing over it, the page is still the top activity the request needs, and the PIN pad comes up directly.
 */
class CortanaUnlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            Diagnostics.add("cortana", "unlock bridge: keyguard already gone")
            UnlockBridge.deliver(true)
            finish()
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                Diagnostics.add("cortana", "unlock bridge: dismissed")
                UnlockBridge.deliver(true)
                finish()
            }

            override fun onDismissCancelled() {
                Diagnostics.add("cortana", "unlock bridge: cancelled")
                UnlockBridge.deliver(false)
                finish()
            }

            override fun onDismissError() {
                Diagnostics.add("cortana", "unlock bridge: error")
                UnlockBridge.deliver(false)
                finish()
            }
        })
    }
}

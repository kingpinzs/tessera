package app.tileshell.cortana

import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import app.tileshell.diag.Diagnostics

/**
 * Raises Android's own unlock prompt for the "Unlock to continue" card (H12), then gets out of the way.
 *
 * It draws nothing: the card stays on screen behind the bouncer, and the gated request runs in the
 * session as soon as the keyguard is gone. A cancelled unlock does nothing at all — the card is still
 * there and the request is still pending (Decisions).
 */
class CortanaUnlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
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

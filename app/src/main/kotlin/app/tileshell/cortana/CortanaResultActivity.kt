package app.tileshell.cortana

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import app.tileshell.diag.Diagnostics

/**
 * Runs one launch for Tess's window and hands its result back (L13-1, [SessionResultRegistry]).
 *
 * A VoiceInteractionSession cannot start an activity for a result; this is the smallest thing that can. It draws
 * nothing, starts the intent it was handed, delivers the result to [CortanaResults] before it finishes — so a URI
 * grant that came with the result is still live while the receiver reads it — and is gone. Not exported: only the
 * launcher's own registry can hand it an intent to start.
 */
class CortanaResultActivity : ComponentActivity() {

    private val requestCode: Int get() = intent?.getIntExtra(EXTRA_REQUEST_CODE, 0) ?: 0

    private val forResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Diagnostics.add("cortana", "result launch $requestCode came back with ${result.resultCode}")
        if (!CortanaResults.deliver(requestCode, result.resultCode, result.data)) {
            Diagnostics.add("cortana", "result launch $requestCode: no session waiting; dropped")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated with the launch already out: its result comes to forResult, which the registry restores.
        if (savedInstanceState != null) return
        val target = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_TARGET, Intent::class.java) }
        if (target == null) {
            cancel("no intent to start")
            return
        }
        runCatching { forResult.launch(target) }
            .onSuccess { Diagnostics.add("cortana", "result launch $requestCode: started ${target.action}") }
            .onFailure { cancel("could not start ${target.action}: $it") }
    }

    private fun cancel(why: String) {
        Diagnostics.add("cortana", "result launch $requestCode: $why; cancelled")
        CortanaResults.deliver(requestCode, Activity.RESULT_CANCELED, null)
        finish()
    }

    companion object {
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_REQUEST_CODE = "request_code"

        /** A fresh task every time, for CortanaPermissionActivity's reason: a reused task skips onCreate. */
        fun intentFor(context: Context, requestCode: Int, target: Intent): Intent =
            Intent(context, CortanaResultActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
                .putExtra(EXTRA_TARGET, target)
    }
}

package app.tileshell.cortana

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import app.tileshell.diag.Diagnostics

/**
 * The activity-result registry of Tess's window (L13-1; review/2026-09-25-L13-1-fix-plan.md, Jeremy's Q1 "(b)").
 *
 * A VoiceInteractionSession is not an Activity, so nothing composed inside it could call
 * `rememberLauncherForActivityResult`: the reminder page did, and every open of it crashed the launcher with
 * "No ActivityResultRegistryOwner was provided". The session now owns this registry, and a launch goes out through
 * [CortanaResultActivity] — the smallest thing that is an Activity — whose result comes back here by request code.
 *
 * Tess's window sits above every activity (MICPERM run 1), so she [stepAside]s while the launched page is up and
 * [comeBack]s before the result is dispatched. Stepping aside is not closing: closing drops the pending card
 * (H12), and the card or page the launch came from must still be there when the result lands.
 */
class SessionResultRegistry(
    private val context: Context,
    private val start: (requestCode: Int, target: Intent, onResult: (resultCode: Int, data: Intent?) -> Unit) -> Boolean,
    private val stepAside: () -> Unit,
    private val comeBack: () -> Unit,
) : ActivityResultRegistry() {

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        val synchronous = contract.getSynchronousResult(context, input)
        if (synchronous != null) {
            dispatchResult(requestCode, synchronous.value)
            return
        }
        val target = contract.createIntent(context, input)
        // Started while Tess is still showing: her visible window is what lets the launcher start an activity.
        val started = start(requestCode, target) { resultCode, data ->
            comeBack()
            dispatchResult(requestCode, resultCode, data)
        }
        if (started) {
            stepAside()
        } else {
            Diagnostics.add("cortana", "result launch $requestCode could not start ${target.action}; cancelled")
            dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
        }
    }
}

/**
 * Carries a launch's result from [CortanaResultActivity] back to the registry that asked, by request code, once.
 * Main thread only: the activity's result callback and the session both run there.
 */
object CortanaResults {
    private val waiting = HashMap<Int, (Int, Intent?) -> Unit>()

    fun await(requestCode: Int, onResult: (resultCode: Int, data: Intent?) -> Unit) {
        waiting[requestCode] = onResult
    }

    /** False when nothing waits for [requestCode] — a result for a session that has since closed. */
    fun deliver(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val onResult = waiting.remove(requestCode) ?: return false
        onResult(resultCode, data)
        return true
    }

    /** The session is gone: nothing it launched may call back into it. */
    fun forgetAll() {
        waiting.clear()
    }

    /** Start [target] through the pass-through; false (and nothing waiting) when it could not start. */
    fun start(context: Context, requestCode: Int, target: Intent, onResult: (Int, Intent?) -> Unit): Boolean {
        await(requestCode, onResult)
        return runCatching { context.startActivity(CortanaResultActivity.intentFor(context, requestCode, target)) }
            .onFailure {
                waiting.remove(requestCode)
                Diagnostics.add("cortana", "result launch $requestCode: the pass-through did not start: $it")
            }
            .isSuccess
    }
}

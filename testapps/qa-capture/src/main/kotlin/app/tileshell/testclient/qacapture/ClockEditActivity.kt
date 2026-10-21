package app.tileshell.testclient.qacapture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Starts the shell's exported `ClockActivity` with the `api_edit` Bundle the alarm handler hands over for an editor
 * open (ledger L18-3; the adversarial review's N1, and pass 3's P3-4). `am start` cannot carry a Bundle extra, so a
 * shell-driven hostile `api_edit` needs an app to build it. This app is NOT the shell's uid, so `ClockIntents.open`
 * must refuse to even read the bundle (`api edit ignored: not the shell`) — on any launch, and after a config-change
 * recreation of a running ClockActivity (P3-4).
 *
 *   am start -n app.tileshell.testclient.qacapture/.ClockEditActivity
 *       --es kind alarm            the bundle's kind (default "alarm")
 *       [--ei hour 6 --ei minute 45]
 *       [--ei badday 99]           a single-element int[] "days" holding one out-of-range day (N1)
 *       [--es message <text>]
 *
 * Logged under TileShellQa: `clockedit kind=<kind> ... started` (never the bundle's values repeated as a forged line).
 */
class ClockEditActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra("kind") ?: "alarm"
        val edit = Bundle().apply {
            putString("kind", kind)
            if (intent.hasExtra("hour")) putInt("hour", intent.getIntExtra("hour", 0))
            if (intent.hasExtra("minute")) putInt("minute", intent.getIntExtra("minute", 0))
            if (intent.hasExtra("badday")) putIntArray("days", intArrayOf(intent.getIntExtra("badday", 0)))
            if (intent.hasExtra("message")) putString("message", intent.getStringExtra("message"))
        }
        val go = Intent()
            .setClassName("app.tileshell", "app.tileshell.clock.ClockActivity")
            .putExtra("page", "alarm")
            .putExtra("api_edit", edit)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i(CaptureProbeActivity.TAG, "clockedit kind=$kind hasHour=${intent.hasExtra("hour")} badday=${intent.hasExtra("badday")} started")
        runCatching { startActivity(go) }.onFailure { Log.i(CaptureProbeActivity.TAG, "clockedit failed: ${it.javaClass.simpleName}") }
        finish()
    }
}

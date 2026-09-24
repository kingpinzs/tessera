package app.tileshell.testclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Phase 11 QA (T11-15): the shortcut verbs with no window, for a verb sent while a burst is open, so StartActivity
 * stays resumed:
 *
 *   adb shell am broadcast -n app.tileshell.testclient.b/app.tileshell.testclient.ShortcutVerbReceiver --es verb disable
 *
 * Runs the same implementation as VerbActivity (ShortcutVerbs); the result line is logged (tag "TileClient") and
 * returned as the broadcast's result data, which `am broadcast` prints on its "Broadcast completed" line.
 * Declared, exported, in tileclient-b's manifest only.
 */
class ShortcutVerbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val verb = intent.getStringExtra("verb")
        val text = when {
            verb == null -> "no verb given (see ShortcutVerbs)"
            verb !in ShortcutVerbs.VERBS -> "unknown verb $verb"
            else -> try {
                ShortcutVerbs.perform(context, verb)
            } catch (e: Exception) {
                "$verb: exception ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        Log.i(ShortcutVerbs.TAG, "${context.packageName} $text")
        resultData = text
    }
}

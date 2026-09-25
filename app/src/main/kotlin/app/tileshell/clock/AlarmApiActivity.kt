package app.tileshell.clock

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import app.tileshell.clock.AlarmApiRules.Request
import app.tileshell.diag.Diagnostics

/**
 * The `AlarmClock` API handler (phase 15 T15-37; trust item (j)): an exported, translucent trampoline guarded by
 * `com.android.alarm.permission.SET_ALARM` (as AOSP DeskClock's HandleApiCalls) for `SET_ALARM`, `SET_TIMER`,
 * `SHOW_ALARMS` and `SHOW_TIMERS`. Every request goes through [AlarmApiRules] first; with `EXTRA_SKIP_UI` the item
 * is created and a short notice shows, without it Alarms & Clock opens on the editor filled in. It never edits or
 * deletes an existing alarm, and it logs `[alarms] api <action> from <caller> -> <created <id> | opened | refused: <why>>`.
 */
class AlarmApiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val caller = callingPackage ?: referrer?.host ?: "?"
        fun intExtra(key: String): Int? = if (intent.hasExtra(key)) intent.getIntExtra(key, 0) else null
        val request = AlarmApiRules.parse(
            action = intent.action,
            hour = intExtra(AlarmClock.EXTRA_HOUR),
            minutes = intExtra(AlarmClock.EXTRA_MINUTES),
            days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS),
            message = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE),
            skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false),
            lengthSeconds = intExtra(AlarmClock.EXTRA_LENGTH),
            ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE),
            vibrate = if (intent.hasExtra(AlarmClock.EXTRA_VIBRATE)) intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true) else null,
        )
        val store = ClockStore.get(this)
        val result = when (request) {
            is Request.CreateAlarm -> {
                val f = request.fields
                val alarm = store.newAlarm(f.hour!!, f.minute, f.message, f.days, f.sound, Alarm.DEFAULT_SNOOZE)
                store.putAlarm(alarm, "alarm ${alarm.id} created by the AlarmClock API ($caller)")
                notice("Alarm set for ${ClockText.time(alarm.hour, alarm.minute, DateFormat.is24HourFormat(this), resources.configuration.locales[0])}")
                "created ${alarm.id}"
            }
            is Request.EditAlarm -> {
                val f = request.fields
                openClock(ClockTab.ALARM, Bundle().apply {
                    putString(ClockActivity.API_KIND, "alarm")
                    f.hour?.let { putInt(ClockActivity.API_HOUR, it) }
                    putInt(ClockActivity.API_MINUTE, f.minute)
                    putIntArray(ClockActivity.API_DAYS, f.days.map { it.value }.toIntArray())
                    putString(ClockActivity.API_MESSAGE, f.message)
                    putString(ClockActivity.API_SOUND_KIND, f.sound.kind.name)
                    f.sound.uri?.let { putString(ClockActivity.API_SOUND_URI, it) }
                })
                "opened"
            }
            is Request.CreateTimer -> {
                val t = store.addTimer(request.message, request.lengthMs, start = true)
                if (t == null) "refused: length out of range" else {
                    notice("Timer set for ${ClockText.hms(request.lengthMs)}")
                    "created ${t.id}"
                }
            }
            is Request.EditTimer -> {
                openClock(ClockTab.TIMER, Bundle().apply {
                    putString(ClockActivity.API_KIND, "timer")
                    request.lengthMs?.let { putLong(ClockActivity.API_TIMER_LENGTH_MS, it) }
                    putString(ClockActivity.API_MESSAGE, request.message)
                })
                "opened"
            }
            Request.ShowAlarms -> { openClock(ClockTab.ALARM, null); "opened" }
            Request.ShowTimers -> { openClock(ClockTab.TIMER, null); "opened" }
            is Request.Refused -> "refused: ${request.why}"
        }
        Diagnostics.add("alarms", "api ${intent.action} from $caller -> $result")
        finish()
    }

    private fun notice(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun openClock(tab: ClockTab, edit: Bundle?) {
        startActivity(
            Intent(this, ClockActivity::class.java)
                .putExtra(ClockActivity.EXTRA_PAGE, tab.id)
                .apply { edit?.let { putExtra(ClockActivity.EXTRA_API_EDIT, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

package app.tileshell.clock

import app.tileshell.media.UriAccessPort
import java.time.DayOfWeek

/**
 * The `AlarmClock` API's validation (phase 15 T15-37, trust item (j)), free of Android so the host JVM proves it
 * (AlarmApiRulesTest). A caller holding `com.android.alarm.permission.SET_ALARM` reaches [AlarmApiActivity], which
 * hands every extra here and does exactly what the answer says: creates the item, opens the editor filled in, shows
 * a tab, or refuses with a reason. Nothing here ever edits or deletes an existing alarm.
 */
object AlarmApiRules {
    const val ACTION_SET_ALARM = "android.intent.action.SET_ALARM"
    const val ACTION_SET_TIMER = "android.intent.action.SET_TIMER"
    const val ACTION_SHOW_ALARMS = "android.intent.action.SHOW_ALARMS"
    const val ACTION_SHOW_TIMERS = "android.intent.action.SHOW_TIMERS"

    /** `AlarmClock.VALUE_RINGTONE_SILENT`. */
    const val RINGTONE_SILENT = "silent"

    /** The message ceiling (the trust list: "a message ≤ 64 characters"). */
    const val MAX_MESSAGE = 64

    /**
     * What an alarm request carries once validated. [soundRefused] is why the ringtone it asked for was not kept and
     * the default sound stands in its place ([AlarmRingtoneRules]), else null.
     */
    data class AlarmFields(val hour: Int?, val minute: Int, val days: Set<DayOfWeek>, val message: String, val sound: AlarmSound, val soundRefused: String? = null)

    sealed interface Request {
        /** `EXTRA_SKIP_UI`: the alarm is created and switched on. [fields].hour is never null here. */
        data class CreateAlarm(val fields: AlarmFields) : Request
        /** The editor opens filled in; nothing is armed until the user saves. */
        data class EditAlarm(val fields: AlarmFields) : Request
        data class CreateTimer(val lengthMs: Long, val message: String) : Request
        data class EditTimer(val lengthMs: Long?, val message: String) : Request
        data object ShowAlarms : Request
        data object ShowTimers : Request
        data class Refused(val why: String) : Request
    }

    /**
     * [days] are `AlarmClock.EXTRA_DAYS` values (java.util.Calendar's SUNDAY = 1 … SATURDAY = 7); [lengthSeconds]
     * is `EXTRA_LENGTH`; [ringtone] is `EXTRA_RINGTONE` ("silent" or a content URI); [vibrate] is `EXTRA_VIBRATE`,
     * accepted and not acted on (the ring always vibrates, Decisions "Ringing").
     *
     * The ringtone is another app's word for something the ring will open with the shell's identity (ledger L18-2):
     * [AlarmRingtoneRules.fromApi] keeps it only when it is a system sound the caller could read itself — asked of
     * [access], the platform port of the activity the caller started — and anything else is the default sound. The
     * alarm itself is never refused for its ringtone.
     */
    fun parse(
        action: String?,
        hour: Int?,
        minutes: Int?,
        days: List<Int>?,
        message: String?,
        skipUi: Boolean,
        lengthSeconds: Int?,
        ringtone: String?,
        @Suppress("UNUSED_PARAMETER") vibrate: Boolean?,
        access: UriAccessPort?,
    ): Request {
        val text = message.orEmpty().trim()
        if (text.length > MAX_MESSAGE) return Request.Refused("message longer than $MAX_MESSAGE characters")
        if (text.any { it.isISOControl() }) return Request.Refused("message holds a control character")
        return when (action) {
            ACTION_SET_ALARM -> {
                if (hour != null && hour !in 0..23) return Request.Refused("hour out of range")
                val minute = minutes ?: 0
                if (minute !in 0..59) return Request.Refused("minutes out of range")
                val daySet = days.orEmpty().map { d ->
                    if (d !in 1..7) return Request.Refused("days out of range")
                    DayOfWeek.of((d + 5) % 7 + 1)
                }.toSet()
                val tone = AlarmRingtoneRules.fromApi(ringtone, access)
                val fields = AlarmFields(hour, minute, daySet, text, tone.sound, tone.refused)
                // No hour: the API says the app's UI should open for the user to pick one, SKIP_UI or not.
                if (hour == null || !skipUi) Request.EditAlarm(fields) else Request.CreateAlarm(fields)
            }
            ACTION_SET_TIMER -> {
                if (lengthSeconds == null) return Request.EditTimer(null, text)
                if (lengthSeconds <= 0) return Request.Refused("length out of range")
                val ms = lengthSeconds * 1000L
                if (ms > ClockTimer.MAX_LENGTH_MS) return Request.Refused("length past 99:59:59")
                if (skipUi) Request.CreateTimer(ms, text) else Request.EditTimer(ms, text)
            }
            ACTION_SHOW_ALARMS -> Request.ShowAlarms
            ACTION_SHOW_TIMERS -> Request.ShowTimers
            else -> Request.Refused("unsupported action ${action ?: "(none)"}")
        }
    }
}

package app.tileshell.cortana.reminders

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

/**
 * The wording every surface shares, so the Reminders list, the saved-reminder card and the notification
 * cannot drift apart. R7 §3.5.4 writes the list's second line with "at" and R7 §3.8.1 writes the saved
 * card's subline with a hyphen — the same instant, two spellings, both measured, both kept.
 */
object ReminderText {

    /** R7 §3.5.4: "Tomorrow at 8:00 AM". A place or person reminder has its own wording (H26). */
    fun listSubline(context: Context, reminder: Reminder, store: ReminderStore): String? = when {
        reminder.kind == ReminderKind.PLACE -> "When I arrive at ${placeName(reminder, store)}"
        reminder.kind == ReminderKind.PERSON -> "Next time I talk to ${reminder.contactName.orEmpty()}"
        reminder.timeMs == null -> null
        else -> "${dayWord(reminder.timeMs)} at ${time(context, reminder.timeMs)}"
    }

    /** R7 §3.8.1: "Tomorrow - 8:00 AM" on the saved-reminder card. A Whenever reminder has no subline (H16). */
    fun savedCardSubline(context: Context, reminder: Reminder, store: ReminderStore): String? = when {
        reminder.kind == ReminderKind.PLACE -> "When I arrive at ${placeName(reminder, store)}"
        reminder.kind == ReminderKind.PERSON -> "Next time I talk to ${reminder.contactName.orEmpty()}"
        reminder.timeMs == null -> null
        else -> "${dayWord(reminder.timeMs)} - ${time(context, reminder.timeMs)}"
    }

    /** R7 §3.5.5: a photo reminder's grey line, "On 7/25/2016 at 1:03 AM". */
    fun photoTimeLine(context: Context, atMs: Long): String =
        "On ${DateFormat.getDateFormat(context).format(atMs)} at ${time(context, atMs)}"

    fun notificationSubline(context: Context, reminder: Reminder): String =
        reminder.timeMs?.let { time(context, it) } ?: "Reminder"

    /** What Cortana says back at the confirm card (R6 §3.4.2 and the P4 place / person wording). */
    fun confirmSpoken(reminder: Reminder, placeName: String?, context: Context): String = when (reminder.kind) {
        ReminderKind.PLACE -> "Remind you to ${reminder.text} when you get to ${placeName.orEmpty()}. Sound good?"
        ReminderKind.PERSON -> "Remind you to ${reminder.text} next time you talk to ${reminder.contactName.orEmpty()}. Sound good?"
        ReminderKind.TIME -> reminder.timeMs
            ?.let { "Remind you to ${reminder.text} at ${time(context, it)} ${dayWord(it).lowercase()}. Sound good?" }
            ?: "Remind you to ${reminder.text}. Sound good?"
    }

    fun placeName(reminder: Reminder, store: ReminderStore): String =
        reminder.placeId?.let { store.place(it)?.name } ?: "that place"

    fun time(context: Context, atMs: Long): String = DateFormat.getTimeFormat(context).format(atMs)

    /** "Today" / "Tomorrow" / a weekday name / a date, the way W10M's list wrote it. */
    fun dayWord(atMs: Long): String {
        val now = Calendar.getInstance()
        val at = Calendar.getInstance().apply { timeInMillis = atMs }
        val days = daysBetween(now, at)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days in 2..6 -> WEEKDAYS[at.get(Calendar.DAY_OF_WEEK) - 1]
            else -> "${at.get(Calendar.MONTH) + 1}/${at.get(Calendar.DAY_OF_MONTH)}/${at.get(Calendar.YEAR)}"
        }
    }

    /** Midnight after today, which is where the Today group ends (R7 §3.5.1). */
    fun endOfToday(nowMs: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun daysBetween(from: Calendar, to: Calendar): Long {
        fun midnight(c: Calendar) = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return (midnight(to) - midnight(from)) / 86_400_000L
    }

    private val WEEKDAYS = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
}

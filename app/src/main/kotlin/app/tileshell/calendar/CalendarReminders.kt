package app.tileshell.calendar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import app.tileshell.R
import app.tileshell.diag.Diagnostics
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/** Which due alerts the receiver notifies and which it skips — pure, so the JVM test pins r3 D6 (c) and Q-16-2. */
object ReminderRules {
    sealed interface Action {
        val row: AlertRow
        data class Notify(override val row: AlertRow) : Action
        /** A synced copy's alert while its Tessera original exists: one event, one reminder. */
        data class SkipCopy(override val row: AlertRow) : Action
    }

    /**
     * [due]: the alert rows with `alarmTime` ≤ now in state SCHEDULED or FIRED. Less the ids already handled
     * ([notified]) — so a second or a forged broadcast for the same alert does nothing — and with a copy's alert
     * ([copies]: the synced-copy event ids) skipped rather than shown.
     */
    fun plan(due: List<AlertRow>, notified: Set<Long>, copies: Set<Long>): List<Action> =
        due.filter { it.id !in notified }.map { if (it.eventId in copies) Action.SkipCopy(it) else Action.Notify(it) }
}

/**
 * `.calendar.CalendarReminderReceiver` (r3 D6): manifest-registered for the provider's `EVENT_REMINDER` broadcast,
 * whose data is `content://com.android.calendar/<alarmTime>` — hence the scheme + host filter. Exported, because the
 * sender is the provider's process; so any app can send it, and the broadcast is a POKE and nothing more: nothing in
 * the intent is read — not its action, its data or an extra. A forged poke can only make the shell re-read the
 * provider, which notifies nothing that is not due.
 *
 * The shell arms no alarm for calendar events (Rule 16): the provider schedules the reminders and re-arms them on boot.
 */
class CalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CalendarReminders.worker.execute {
            try {
                CalendarReminders.poke(app)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * The swipe that dismisses one of the shell's reminder notifications. Not exported: only the notification's own
 * PendingIntent reaches it, so the alert id it carries is one the shell put there.
 */
class CalendarReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alert = intent.getLongExtra(CalendarReminders.EXTRA_ALERT, -1L)
        if (alert < 0) return
        val event = intent.getLongExtra(CalendarReminders.EXTRA_EVENT, -1L)
        val minutes = intent.getIntExtra(CalendarReminders.EXTRA_MINUTES, 0)
        val app = context.applicationContext
        val pending = goAsync()
        CalendarReminders.worker.execute {
            try {
                CalendarReminders.dismissed(app, alert, event, minutes)
            } finally {
                pending.finish()
            }
        }
    }
}

object CalendarReminders {
    /** The calendar channel (E6 reads it). */
    const val CHANNEL = "calendar_reminders"
    const val TAG = "calendar_reminder"
    const val EXTRA_ALERT = "app.tileshell.calendar.ALERT"
    const val EXTRA_EVENT = "app.tileshell.calendar.EVENT"
    const val EXTRA_MINUTES = "app.tileshell.calendar.MINUTES"

    /** One thread: two broadcasts for one alert are read one after the other, so the second finds the first's record. */
    val worker = Executors.newSingleThreadExecutor()

    /**
     * Re-reads `CalendarAlerts` for what is due and unhandled, and for each alert left posts one notification, records
     * its id in the shell's store (the receiver may run in a fresh process) and marks the row FIRED — the write guard's
     * case 4. Every calendar's alerts are shown, the account calendars' too (T16-4), except a synced copy's.
     */
    fun poke(context: Context) {
        if (!CalendarReads.canRead(context)) {
            Diagnostics.add("calendar", "reminder poke: nothing read (READ_CALENDAR)")
            return
        }
        val due = CalendarReads.dueAlerts(context, System.currentTimeMillis()) ?: return
        val store = CalendarSyncStore.get(context)
        val live = due.mapTo(HashSet()) { it.id }
        val fresh = due.filter { it.id !in store.current.notifiedAlerts }
        if (fresh.isEmpty()) {
            store.update { SyncStateRules.keepNotified(it, live, emptySet()) }
            return
        }
        val plan = ReminderRules.plan(due, store.current.notifiedAlerts, SyncedCopies.hiddenEventIds(context))
        val handled = HashSet<Long>()
        for (action in plan) {
            val row = action.row
            when (action) {
                is ReminderRules.Action.SkipCopy -> {
                    // The copy keeps its own reminder rows, so the account's other clients still remind; its alert
                    // row is left as it is — only the shell stays quiet about it.
                    handled += row.id
                    Diagnostics.add("calendar", "reminder event=${row.eventId} minutes=${row.minutes}: skipped (synced copy)")
                }
                is ReminderRules.Action.Notify -> {
                    if (!post(context, row)) {
                        Diagnostics.add("calendar", "reminder event=${row.eventId} minutes=${row.minutes}: failed notifications are off")
                        continue
                    }
                    handled += row.id
                    CalendarWrites.setAlertState(context, row.id, CalendarContract.CalendarAlerts.STATE_FIRED)
                    Diagnostics.add("calendar", "reminder event=${row.eventId} minutes=${row.minutes}: notified")
                }
            }
        }
        store.update { SyncStateRules.keepNotified(it, live, handled) }
    }

    fun dismissed(context: Context, alertId: Long, eventId: Long, minutes: Int) {
        CalendarWrites.setAlertState(context, alertId, CalendarContract.CalendarAlerts.STATE_DISMISSED)
        Diagnostics.add("calendar", "reminder event=$eventId minutes=$minutes: dismissed")
    }

    private fun channel(context: Context): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Calendar reminders", NotificationManager.IMPORTANCE_HIGH))
        return manager
    }

    /**
     * One notification per alert, in the Action Center item's form (r11/calendar.md U5; approximation, H7): the event's
     * title and its time — the event's time, not "now", however late the alert is delivered. `VISIBILITY_PRIVATE`: over
     * a locked screen the public version says only that a calendar reminder fired.
     */
    private fun post(context: Context, row: AlertRow): Boolean {
        val manager = channel(context)
        if (!manager.areNotificationsEnabled()) return false
        val open = PendingIntent.getActivity(
            context, requestCode(row.id),
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, row.eventId))
                .setClass(context, CalendarActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, row.beginMs)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, row.endMs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, requestCode(row.id),
            Intent(context, CalendarReminderDismissReceiver::class.java)
                .putExtra(EXTRA_ALERT, row.id).putExtra(EXTRA_EVENT, row.eventId).putExtra(EXTRA_MINUTES, row.minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val public = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar_glyph)
            .setContentTitle("Calendar reminder")
            .build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_calendar_glyph)
            .setContentTitle(EventRules.shownTitle(row.title))
            .setContentText(timeText(context, row.beginMs, row.endMs, row.allDay, ZoneId.systemDefault()))
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setWhen(row.beginMs)
            .setShowWhen(true)
            .setContentIntent(open)
            .setDeleteIntent(dismiss)
            .setAutoCancel(true)
            .build()
        manager.notify(TAG, requestCode(row.id), notification)
        return true
    }

    private fun requestCode(alertId: Long): Int = (alertId and 0x7FFFFFFF).toInt()

    /** "Tue 2 Oct, 9:00 AM – 10:00 AM", or "Tue 2 Oct, all day": the event's own time, in the phone's 12 / 24-hour form. */
    fun timeText(context: Context, beginMs: Long, endMs: Long, allDay: Boolean, zone: ZoneId): String {
        val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        if (allDay) return "${day.format(EventRules.local(beginMs, EventRules.UTC))}, all day"
        val time = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", Locale.getDefault())
        val begin = EventRules.local(beginMs, zone)
        val end = EventRules.local(endMs, zone)
        return "${day.format(begin)}, ${time.format(begin)} – ${time.format(end)}"
    }
}

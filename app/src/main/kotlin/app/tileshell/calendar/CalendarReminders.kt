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
import java.util.concurrent.atomic.AtomicBoolean

/** Which due alerts the receiver notifies and which it skips — pure, so the JVM test pins r3 D6 (c), Q-16-2 and Q-16-4. */
object ReminderRules {
    sealed interface Action {
        val row: AlertRow
        data class Notify(override val row: AlertRow) : Action
        /** A synced copy's alert while its Tessera original exists: one event, one reminder. */
        data class SkipCopy(override val row: AlertRow) : Action
    }

    /**
     * What one poke does: [actions] for the alerts to handle, and [skippedBeforeStart] — how many due rows came due
     * before the shell's first start. Those get no notification, no write and no place in the notified set; the poke
     * says so in one line.
     */
    data class Plan(val actions: List<Action>, val skippedBeforeStart: Int)

    /** An alert as the shell knew it when it posted its notification: the row, and what the row was an alert of. */
    data class AlertIdentity(val id: Long, val eventId: Long, val beginMs: Long, val alarmTimeMs: Long)

    fun identity(row: AlertRow) = AlertIdentity(row.id, row.eventId, row.beginMs, row.alarmTimeMs)

    /** A provider selection and its arguments. */
    data class Selection(val where: String, val args: List<String>)

    /**
     * Which row a state write (FIRED at the notification, DISMISSED at its swipe) may touch (fix round F3): the row
     * with that `_id` ONLY while it is still the alert of that event, that occurrence and that alarm time, and still
     * SCHEDULED (0) or FIRED (1). The provider hands a deleted alert row's `_id` out again, so the row id alone could
     * name another event's alert by the time a stale notification is swiped — possibly a work calendar's — and
     * dismissing it would silence a reminder nobody has seen. A selection that matches nothing writes nothing.
     * The column names are `CalendarContract.CalendarAlerts`' (`_ID`, `EVENT_ID`, `BEGIN`, `ALARM_TIME`, `STATE`).
     */
    fun stateSelection(alert: AlertIdentity) = Selection(
        "_id = ? AND event_id = ? AND begin = ? AND alarmTime = ? AND state IN (0, 1)",
        listOf(alert.id, alert.eventId, alert.beginMs, alert.alarmTimeMs).map { it.toString() },
    )

    /** [stateSelection] in Kotlin, over a row as it reads now: the rule the selection states, for the JVM test. */
    fun isStillThatAlert(alert: AlertIdentity, row: AlertRow): Boolean =
        row.id == alert.id && row.eventId == alert.eventId && row.beginMs == alert.beginMs && row.alarmTimeMs == alert.alarmTimeMs && row.state in 0..1

    /** The rows a poke may act on: due at or after [sinceMs]. An alert due at the very instant of the first start counts. */
    fun sinceStart(due: List<AlertRow>, sinceMs: Long): List<AlertRow> = due.filter { it.alarmTimeMs >= sinceMs }

    /**
     * [due]: the alert rows with `alarmTime` ≤ now in state SCHEDULED or FIRED.
     *
     * Q-16-4 first: a row whose `alarmTime` is before [sinceMs] — the shell's first start on this install — is dropped.
     * On a phone that already had a calendar app, the provider holds about a week of alerts that app fired and nobody
     * dismissed; the shell reminds only for what comes due after it first ran.
     *
     * Then, of the rest: less the alerts already handled ([notified], by [AlertRow.key]) — so a second or a forged
     * broadcast for the same alert does nothing, and a new alert that was handed a deleted row's `_id` still notifies
     * — and with a copy's alert ([copies]: the synced-copy event ids) skipped rather than shown.
     */
    fun plan(due: List<AlertRow>, notified: Set<String>, copies: Set<Long>, sinceMs: Long): Plan {
        val current = sinceStart(due, sinceMs)
        return Plan(
            actions = current.filter { it.key !in notified }.map { if (it.eventId in copies) Action.SkipCopy(it) else Action.Notify(it) },
            skippedBeforeStart = due.size - current.size,
        )
    }

    /**
     * What a poke records as handled, by [AlertRow.key]: the actions [done] says were carried out, in the plan's
     * order — a skipped copy, and a notification that was really posted. An alert whose post failed (notifications
     * off) is NOT recorded: it is still due, and the next poke plans it again (fix round F7, the review's R8).
     */
    fun recorded(actions: List<Action>, done: (Action) -> Boolean): Set<String> =
        actions.filter(done).mapTo(LinkedHashSet()) { it.row.key }

    /**
     * One piece of the receivers' work, run so that nothing it throws leaves the worker thread (fix round F9): an
     * uncaught Throwable there would end the launcher's process, and a broadcast taken with `goAsync` and never
     * finished is an ANR. Whatever [task] throws is caught and said in one line through [log]; [finish] always runs,
     * once; and neither a failing [log] nor a failing [finish] escapes either.
     */
    fun contained(what: String, log: (String) -> Unit, finish: () -> Unit, task: () -> Unit) {
        try {
            task()
        } catch (t: Throwable) {
            try {
                log("$what failed: ${t.toString().replace('\n', ' ').take(160)}")
            } catch (_: Throwable) {
            }
        } finally {
            try {
                finish()
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * At most one poke waits (fix round F9). `EVENT_REMINDER` can arrive in a burst — any app can send it — and each one
 * used to queue a whole read of the provider. A poke that is queued and has not started reads the provider LATER than
 * every broadcast that arrives meanwhile, so those broadcasts are dropped without loss. Once the queued poke starts its
 * read, the next broadcast queues one more: its alert may have come due after that read's "now".
 */
class PokeGate {
    private val queued = AtomicBoolean(false)

    /** True: queue a poke. False: one is already waiting, and this broadcast is dropped. */
    fun offer(): Boolean = queued.compareAndSet(false, true)

    /** The queued poke starts (or could not be queued at all): the next broadcast queues another. */
    fun started() = queued.set(false)
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
        // A poke is already waiting: its read will see whatever this broadcast announces. Nothing is queued, nothing
        // is logged — a burst of forged pokes costs one read, not one read each (fix round F9).
        if (!CalendarReminders.pokes.offer()) return
        val app = context.applicationContext
        val queued = CalendarReminders.runAsync(goAsync(), "reminder poke") {
            CalendarReminders.pokes.started()
            CalendarReminders.poke(app)
        }
        if (!queued) CalendarReminders.pokes.started()
    }
}

/**
 * The swipe that dismisses one of the shell's reminder notifications. Not exported: only the notification's own
 * PendingIntent reaches it, so what it carries — the alert's row id, its event, its occurrence and its alarm time — is
 * what the shell put there when it posted the notification. All four are needed: the row id alone may by now be
 * another event's alert (fix round F3).
 */
class CalendarReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(CalendarReminders.EXTRA_ALERT, -1L)
        if (id < 0) return
        val alert = ReminderRules.AlertIdentity(
            id = id,
            eventId = intent.getLongExtra(CalendarReminders.EXTRA_EVENT, -1L),
            beginMs = intent.getLongExtra(CalendarReminders.EXTRA_BEGIN, -1L),
            alarmTimeMs = intent.getLongExtra(CalendarReminders.EXTRA_ALARM_TIME, -1L),
        )
        val minutes = intent.getIntExtra(CalendarReminders.EXTRA_MINUTES, 0)
        val app = context.applicationContext
        CalendarReminders.runAsync(goAsync(), "reminder dismiss") { CalendarReminders.dismissed(app, alert, minutes) }
    }
}

/**
 * The clock was set (`android.intent.action.TIME_SET`, a broadcast only the system can send). Not exported. If the
 * clock went back behind the stored first-start time, that cut-off is lowered now — before the provider's next
 * reminder broadcast, so the alert that follows a clock change is not taken for one "due before the first start"
 * (fix round F2). Nothing in the intent is read but its action.
 */
class CalendarClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED) return
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        CalendarReminders.runAsync(goAsync(), "reminder clock change") { CalendarReminders.clockChanged(app, now) }
    }
}

object CalendarReminders {
    /** The calendar channel (E6 reads it). */
    const val CHANNEL = "calendar_reminders"
    const val TAG = "calendar_reminder"
    const val EXTRA_ALERT = "app.tileshell.calendar.ALERT"
    const val EXTRA_EVENT = "app.tileshell.calendar.EVENT"
    const val EXTRA_MINUTES = "app.tileshell.calendar.MINUTES"
    const val EXTRA_BEGIN = "app.tileshell.calendar.BEGIN"
    const val EXTRA_ALARM_TIME = "app.tileshell.calendar.ALARM_TIME"

    /** One thread: two broadcasts for one alert are read one after the other, so the second finds the first's record. */
    val worker = Executors.newSingleThreadExecutor()

    /** The reminder receiver's queue of pokes: never more than one waiting. */
    val pokes = PokeGate()

    /**
     * Queues a receiver's [task] on the worker, as [ReminderRules.contained] runs it: a failure is one ring line, never
     * the end of the process, and [pending] (the broadcast taken with `goAsync`) is finished whatever happens. False
     * when the task could not be queued at all; the broadcast is finished then too.
     */
    fun runAsync(pending: BroadcastReceiver.PendingResult?, what: String, task: () -> Unit): Boolean {
        val log: (String) -> Unit = { Diagnostics.add("calendar", it) }
        val finish: () -> Unit = { pending?.finish() }
        return try {
            worker.execute { ReminderRules.contained(what, log, finish, task) }
            true
        } catch (t: Throwable) {
            ReminderRules.contained(what, log, finish) { throw t }
            false
        }
    }

    /**
     * The shell's first start on this install (Q-16-4): called from the launcher's process start, where the feeds
     * start, with the time of that start. It writes `remindersSince` once, when the store has none, and lowers a stored
     * time the clock has since been set back behind. It runs on the receiver's own thread, ahead of any poke that
     * arrives later in this process — so a poke is never what decides the cut-off of the alert that woke it, except in
     * a process no start-up ran in (see [poke]).
     */
    fun shellStarted(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        worker.execute { runCatching { countFrom(app, nowMs, "the shell's start") } }
    }

    /** The clock was set to [nowMs]: a cut-off left in the future by a clock set back is lowered to it (never set). */
    fun clockChanged(context: Context, nowMs: Long) {
        val store = CalendarSyncStore.get(context)
        val before = store.current.remindersSince ?: return
        val after = store.update { SyncStateRules.lowerRemindersSince(it, nowMs) }.remindersSince
        if (after != before) Diagnostics.add("calendar", "reminders count from $after (the clock was set: it went back behind $before)")
    }

    /** Sets or lowers the cut-off as [SyncStateRules.remindersSince] says, logging only when it changes. */
    private fun countFrom(context: Context, nowMs: Long, why: String): Long {
        val store = CalendarSyncStore.get(context)
        val before = store.current.remindersSince
        val since = store.update { SyncStateRules.remindersSince(it, nowMs) }.remindersSince ?: nowMs
        if (before == null) Diagnostics.add("calendar", "reminders count from $since ($why: first on this install)")
        else if (before != since) Diagnostics.add("calendar", "reminders count from $since ($why: the clock was set back behind $before)")
        return since
    }

    /**
     * Re-reads `CalendarAlerts` for what is due and unhandled, and for each alert left posts one notification, records
     * it in the shell's store (the receiver may run in a fresh process) and marks the row FIRED — the write guard's
     * case 4. Every calendar's alerts are shown, the account calendars' too (T16-4), except a synced copy's.
     *
     * Q-16-4: rows that came due before the shell's first start are dropped — no notification, no write of any kind
     * to their alert rows, no place in the notified set — and counted in one line per poke. If this poke runs before
     * any start-up has written the cut-off, the cut-off is now, and what was already due is skipped.
     */
    fun poke(context: Context) {
        if (!CalendarReads.canRead(context)) {
            Diagnostics.add("calendar", "reminder poke: nothing read (READ_CALENDAR)")
            return
        }
        val access = CalendarAccess.of(context)
        val now = System.currentTimeMillis()
        val since = countFrom(context, now, "a reminder before any start-up")
        val due = CalendarReads.dueAlerts(access, now) ?: return
        val store = CalendarSyncStore.get(context)
        val live = due.mapTo(HashSet()) { it.key }
        val notified = store.current.notifiedAlerts
        val current = ReminderRules.sinceStart(due, since)
        if (current.size < due.size) {
            Diagnostics.add("calendar", "reminder: ${due.size - current.size} skipped (due before the shell's first start)")
        }
        if (current.none { it.key !in notified }) {
            store.update { SyncStateRules.keepNotified(it, live, emptySet()) }
            return
        }
        val plan = ReminderRules.plan(due, notified, SyncedCopies.hiddenEventIds(access), since)
        val handled = ReminderRules.recorded(plan.actions) { action ->
            val row = action.row
            when (action) {
                is ReminderRules.Action.SkipCopy -> {
                    // The copy keeps its own reminder rows, so the account's other clients still remind; its alert
                    // row is left as it is — only the shell stays quiet about it.
                    Diagnostics.add("calendar", "reminder event=${row.eventId} minutes=${row.minutes}: skipped (synced copy)")
                    true
                }
                is ReminderRules.Action.Notify -> {
                    if (!post(context, row)) {
                        Diagnostics.add("calendar", "reminder event=${row.eventId} minutes=${row.minutes}: failed notifications are off")
                        false
                    } else {
                        CalendarWrites.setAlertState(access, ReminderRules.identity(row), CalendarContract.CalendarAlerts.STATE_FIRED)
                        Diagnostics.add("calendar", "reminder event=${row.eventId} minutes=${row.minutes}: notified")
                        true
                    }
                }
            }
        }
        store.update { SyncStateRules.keepNotified(it, live, handled) }
    }

    /**
     * The notification of [alert] was swiped away: its row is marked DISMISSED — if it is still that alert's row. A
     * stale notification, whose row is gone or has become another event's alert, dismisses nothing; that is said in
     * the ring and is not an error.
     */
    fun dismissed(context: Context, alert: ReminderRules.AlertIdentity, minutes: Int) {
        val line = when (val written = CalendarWrites.setAlertState(CalendarAccess.of(context), alert, CalendarContract.CalendarAlerts.STATE_DISMISSED)) {
            is WriteResult.Ok -> if (written.value > 0) "dismissed" else "swipe ignored (no such alert now)"
            is WriteResult.Refused -> "dismiss ${written.why.text}"
            is WriteResult.Failed -> "dismiss failed ${written.error}"
        }
        Diagnostics.add("calendar", "reminder event=${alert.eventId} minutes=$minutes: $line")
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
                .putExtra(EXTRA_ALERT, row.id).putExtra(EXTRA_EVENT, row.eventId).putExtra(EXTRA_MINUTES, row.minutes)
                .putExtra(EXTRA_BEGIN, row.beginMs).putExtra(EXTRA_ALARM_TIME, row.alarmTimeMs),
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

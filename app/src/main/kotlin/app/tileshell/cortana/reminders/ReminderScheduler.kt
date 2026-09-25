package app.tileshell.cortana.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.os.UserManager
import app.tileshell.R
import app.tileshell.brand.Brand
import app.tileshell.clock.ClockNotifications
import app.tileshell.clock.ClockRules
import app.tileshell.clock.ClockStore
import app.tileshell.clock.RingService
import app.tileshell.diag.Diagnostics

/**
 * Arms every reminder the store holds and re-arms the lot whenever the platform throws them away.
 *
 * A reboot, an app update and `am force-stop` all cancel alarms and proximity alerts, so [rearm] runs
 * on `BOOT_COMPLETED`, on `MY_PACKAGE_REPLACED` and whenever the shell's process starts, as well as on
 * every store change (Decisions "Place and person reminder mechanics" (3); E6 checks the force-stop case).
 *
 * Exact alarms use `USE_EXACT_ALARM`, which a sideloaded app gets at install — no Play policy applies
 * (review F1-m7). If a system revokes it anyway, the reminder still fires, inexactly, and the checklist
 * row goes red with a notice (edge case "a system that revokes USE_EXACT_ALARM").
 */
object ReminderScheduler {

    const val CHANNEL_ID = "cortana_reminders"
    const val ACTION_FIRE = "app.tileshell.cortana.REMINDER_FIRE"
    const val ACTION_PROXIMITY = "app.tileshell.cortana.REMINDER_PROXIMITY"
    const val EXTRA_ID = "reminder_id"

    /** Set when a reminder had to be armed inexactly, for the checklist row and the spoken notice. */
    @Volatile var exactAlarmsDenied: Boolean = false
        private set

    fun rearm(context: Context, why: String) {
        val app = context.applicationContext
        // Alarms and timers first: they live in device-protected storage and are armed even before the first
        // unlock (phase 15, T15-22). Reminders live in credential storage, which a locked user cannot read — a
        // re-arm then would read an empty store and cancel every reminder, so it waits for the unlock.
        rearmClock(app, why)
        if (!app.getSystemService(UserManager::class.java).isUserUnlocked) {
            Diagnostics.add("reminders", "rearm ($why) deferred: the user is not unlocked yet")
            return
        }
        val store = ReminderStore.get(app)
        val alarms = app.getSystemService(AlarmManager::class.java)
        exactAlarmsDenied = !alarms.canScheduleExactAlarms()

        var timed = 0
        // Cancelling by the same request code the arm uses is what makes this idempotent: a re-arm after a
        // store change must not leave a second alarm behind for a reminder whose time moved.
        for (reminder in store.reminders.value) {
            val pi = firePendingIntent(app, reminder.id)
            alarms.cancel(pi)
            val at = reminder.timeMs
            if (reminder.completed || reminder.kind != ReminderKind.TIME || at == null) continue
            if (at <= System.currentTimeMillis()) {
                // "Reminder time in the past" (edge case): it fires as soon as it is armed rather than never.
                Diagnostics.add("reminders", "arm ${reminder.id} in the past ($at); firing now")
            }
            val trigger = maxOf(at, System.currentTimeMillis())
            if (exactAlarmsDenied) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            timed++
        }
        Diagnostics.add("reminders", "rearm ($why): $timed timed alarms, exact=${!exactAlarmsDenied}")
        PlaceTriggers.rearm(app)
        PersonTriggers.rearm(app)
    }

    // --- Phase 15: Alarms & Clock's two kinds (Decisions "One exact-alarm scheduler", T15-22, T15-55) -----------

    const val ACTION_CLOCK_FIRE = "app.tileshell.clock.FIRE"
    const val CLOCK_ALARM = "alarm"
    const val CLOCK_TIMER = "timer"
    const val EXTRA_CLOCK_KIND = "clock_kind"
    const val EXTRA_CLOCK_ID = "clock_id"
    /** The occurrence being rung: a wall-clock instant for an alarm, an elapsed-clock instant for a timer. */
    const val EXTRA_CLOCK_AT = "clock_at"
    /** A timer whose deadline passed while the phone was off ("timer ended while the phone was off"). */
    const val EXTRA_CLOCK_ENDED_OFF = "clock_ended_off"

    /** Alarms & Clock's own activity, named rather than referenced so this file does not depend on its UI. */
    const val CLOCK_ACTIVITY = "app.tileshell.clock.ClockActivity"

    /**
     * Arms every alarm and running timer the clock store holds. Alarms through `setAlarmClock` (Doze-exempt, and
     * what the lock screen's "next alarm" reads), timers through `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)`
     * (a timer is not the phone's next alarm, and no wall-clock change moves it). An occurrence found already past
     * — the phone was off, the process force-stopped, the clock jumped — rings at once inside the ring timeout and
     * is missed after it (Decisions "Ringing").
     */
    fun rearmClock(context: Context, why: String) {
        val app = context.applicationContext
        val store = ClockStore.get(app)
        val alarms = app.getSystemService(AlarmManager::class.java)
        val exact = alarms.canScheduleExactAlarms()
        if (!exact) exactAlarmsDenied = true
        val now = System.currentTimeMillis()
        val zone = store.zone()
        var armedAlarms = 0
        for (alarm in store.alarms.value) {
            alarms.cancel(clockFirePendingIntent(app, CLOCK_ALARM, alarm.id, 0L))
            if (!alarm.enabled) continue
            val from = ClockRules.armFrom(alarm.lastHandledMs, now)
            var at = ClockRules.nextTrigger(alarm, from, zone) ?: continue
            if (at <= now && ClockRules.overdue(at, now) == ClockRules.Overdue.MISSED) {
                Diagnostics.add("alarms", "alarm ${alarm.id} due at $at passed while not running: missed")
                ClockNotifications.missed(app, alarm, at)
                store.alarmHandled(alarm.id, at, ClockStore.Outcome.MISSED, rearm = false)
                val after = store.alarm(alarm.id) ?: continue
                at = ClockRules.nextTrigger(after, maxOf(at, now), zone) ?: continue
            }
            val trigger = maxOf(at, now)
            val pi = clockFirePendingIntent(app, CLOCK_ALARM, alarm.id, at)
            if (exact) {
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, clockShowPendingIntent(app)), pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
            armedAlarms++
        }
        var armedTimers = 0
        val nowElapsed = SystemClock.elapsedRealtime()
        val boot = store.bootCount()
        for (timer in store.timers.value) {
            alarms.cancel(clockFirePendingIntent(app, CLOCK_TIMER, timer.id, 0L))
            val deadline = ClockRules.timerDeadlineElapsed(timer, nowElapsed, now, boot) ?: continue
            val endedOff = ClockRules.endedWhileOff(timer, now, boot)
            val pi = clockFirePendingIntent(app, CLOCK_TIMER, timer.id, deadline, endedOff)
            val trigger = maxOf(deadline, nowElapsed)
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            }
            armedTimers++
        }
        ClockNotifications.runningTimers(app)
        Diagnostics.add("alarms", "rearm ($why): $armedAlarms alarms, $armedTimers timers, exact=$exact")
    }

    /** Cancels one item's alarm (a delete); the request code and data URI are the arm's own. */
    fun cancelClock(context: Context, kind: String, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(clockFirePendingIntent(context.applicationContext, kind, id, 0L))
    }

    fun clockFirePendingIntent(context: Context, kind: String, id: String, at: Long, endedOff: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            "$kind:$id".hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_CLOCK_FIRE)
                .setData(Uri.parse("tileshell://clock/$kind/$id"))
                .putExtra(EXTRA_CLOCK_KIND, kind).putExtra(EXTRA_CLOCK_ID, id)
                .putExtra(EXTRA_CLOCK_AT, at).putExtra(EXTRA_CLOCK_ENDED_OFF, endedOff),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** What the lock screen's "next alarm" opens: Alarms & Clock on its Alarm tab. */
    private fun clockShowPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        "clock:show".hashCode(),
        Intent().setClassName(context.packageName, CLOCK_ACTIVITY).putExtra("page", "alarm")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun firePendingIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id)
            // The id also rides in the data URI: PendingIntent equality ignores extras, so without it two
            // reminders would share one slot and the second arm would overwrite the first.
            .setData(Uri.parse("tileshell://reminder/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Show the reminder and take it out of the list (or move a recurring one to its next occurrence). */
    fun fire(context: Context, id: String, why: String) {
        val app = context.applicationContext
        val store = ReminderStore.get(app)
        val reminder = store.find(id)
        if (reminder == null || reminder.completed) {
            Diagnostics.add("reminders", "fire $id ignored ($why): ${if (reminder == null) "gone" else "already complete"}")
            return
        }
        notify(app, reminder)
        Diagnostics.add("reminders", "fired ${reminder.id} ($why) text=\"${reminder.text}\"")
        store.advanceAfterFiring(id)
    }

    private fun notify(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "${Brand.ASSISTANT_NAME} reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val builder = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(reminder.text)
            .setContentText(ReminderText.notificationSubline(context, reminder))
            .setAutoCancel(true)
        // "Add a photo" (R6 §3.4.2): the picked image shows on the notification.
        reminder.photoUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { bitmap ->
                builder.setStyle(android.app.Notification.BigPictureStyle().bigPicture(bitmap))
            }
        }
        manager.notify(reminder.id.hashCode(), builder.build())
    }
}

/** The alarm and the proximity alert both land here. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE ->
                intent.getStringExtra(ReminderScheduler.EXTRA_ID)?.let { ReminderScheduler.fire(context, it, "alarm") }
            ReminderScheduler.ACTION_PROXIMITY -> PlaceTriggers.onProximity(context, intent)
            ReminderScheduler.ACTION_CLOCK_FIRE -> RingService.fire(context, intent)
            // Before the first unlock only the clock can be read (device-protected storage, T15-22).
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> ReminderScheduler.rearmClock(context, "locked boot")
            Intent.ACTION_BOOT_COMPLETED -> ReminderScheduler.rearm(context, "boot")
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderScheduler.rearm(context, "package replaced")
            // An alarm is wall-clock: a zone or clock change moves the instant its 07:00 happens at.
            Intent.ACTION_TIMEZONE_CHANGED -> ReminderScheduler.rearmClock(context, "time zone changed")
            Intent.ACTION_TIME_CHANGED -> ReminderScheduler.rearmClock(context, "time set")
            else -> Diagnostics.add("reminders", "receiver ignored ${intent.action}")
        }
    }
}

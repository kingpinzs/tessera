package app.tileshell.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import app.tileshell.R
import app.tileshell.cortana.reminders.ReminderScheduler
import app.tileshell.diag.Diagnostics
import java.util.Date

/**
 * Alarms & Clock's notifications (phase 15 Decisions "Ringing", T15-23): the ring itself in two forms, missed
 * alarms, snoozed alarms, and running timers.
 */
object ClockNotifications {
    /** The ring when locked, asleep or without the overlay grant: high importance, full-screen intent, category alarm. */
    const val CH_RINGING = "clock_ringing"
    /** The ring while the phone is in use WITH the overlay: low importance, no full-screen intent — no heads-up over the toast. */
    const val CH_RINGING_QUIET = "clock_ringing_quiet"
    const val CH_MISSED = "clock_missed"
    const val CH_SNOOZED = "clock_snoozed"
    const val CH_TIMERS = "clock_timers"

    const val RING_ID = 15_001

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // The ring's sound and vibration are the ring service's own (alarm stream), so no channel makes a sound.
        fun ch(id: String, name: String, importance: Int) = NotificationChannel(id, name, importance).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannels(
            listOf(
                ch(CH_RINGING, "Alarms and timers ringing", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_RINGING_QUIET, "Alarms and timers ringing while you use the phone", NotificationManager.IMPORTANCE_LOW),
                ch(CH_MISSED, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT),
                ch(CH_SNOOZED, "Snoozed alarms", NotificationManager.IMPORTANCE_LOW),
                ch(CH_TIMERS, "Running timers", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    fun time(context: Context, ms: Long): String = DateFormat.getTimeFormat(context).format(Date(ms))

    /** The ring notification; [fullScreen] picks the form (T15-23). */
    fun ring(context: Context, ring: RingService.Ring, fullScreen: Boolean): Notification {
        channels(context)
        val builder = Notification.Builder(context, if (fullScreen) CH_RINGING else CH_RINGING_QUIET)
            .setSmallIcon(if (ring.timer) R.drawable.ic_timer else R.drawable.ic_alarm)
            .setContentTitle(ring.title)
            .setContentText(ring.body)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
        if (fullScreen) {
            val surface = PendingIntent.getActivity(
                context, 15_002,
                Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setFullScreenIntent(surface, true).setContentIntent(surface)
        }
        if (!ring.timer) {
            builder.addAction(Notification.Action.Builder(null, "Snooze", RingService.actionIntent(context, RingService.ACTION_SNOOZE)).build())
        }
        builder.addAction(Notification.Action.Builder(null, "Dismiss", RingService.actionIntent(context, RingService.ACTION_DISMISS)).build())
        return builder.build()
    }

    /** "Missed alarm h:mm", one per missed occurrence (approximation, H6). */
    fun missed(context: Context, alarm: Alarm, occurrenceMs: Long) {
        channels(context)
        val text = "Missed alarm ${time(context, occurrenceMs)}"
        val n = Notification.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(text)
            .setContentText(alarm.name)
            .setContentIntent(openClock(context, "alarm"))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify("missed:${alarm.id}:$occurrenceMs".hashCode(), n)
        Diagnostics.add("alarms", "missed ${alarm.id} at $occurrenceMs: \"$text\"")
    }

    /** "Snoozed until h:mm" with Dismiss, until the snooze rings or is dismissed (approximation, H6). */
    fun snoozed(context: Context, alarm: Alarm) {
        val until = alarm.snoozedUntilMs ?: return
        channels(context)
        val dismiss = PendingIntent.getService(
            context, "unsnooze:${alarm.id}".hashCode(),
            Intent(context, RingService::class.java).setAction(RingService.ACTION_UNSNOOZE).putExtra(ReminderScheduler.EXTRA_CLOCK_ID, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CH_SNOOZED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Snoozed until ${time(context, until)}")
            .setContentText(alarm.name)
            .setOngoing(true)
            .setContentIntent(openClock(context, "alarm"))
            .addAction(Notification.Action.Builder(null, "Dismiss", dismiss).build())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(snoozedId(alarm.id), n)
    }

    fun clearSnoozed(context: Context, id: String) =
        context.getSystemService(NotificationManager::class.java).cancel(snoozedId(id))

    private fun snoozedId(id: String) = "snoozed:$id".hashCode()

    /**
     * One ongoing notification per running timer, counting down (Android's own chronometer). Posted and cleared
     * at every re-arm, so it always matches the store.
     */
    fun runningTimers(context: Context) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val store = ClockStore.get(context)
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val boot = store.bootCount()
        val running = store.timers.value.filter { it.state == ClockTimer.State.RUNNING }
        // Cleared from what is POSTED, not from the store: a deleted timer is no longer in the store, and walking the
        // store left its countdown up for good (E0 run 1: "timer:t13a91e43" posted with both stores empty).
        val keep = running.map { "timer:${it.id}".hashCode() }.toSet()
        for (sbn in nm.activeNotifications) {
            if (sbn.notification.channelId == CH_TIMERS && sbn.id !in keep) nm.cancel(sbn.tag, sbn.id)
        }
        for (t in running) {
            val id = "timer:${t.id}".hashCode()
            val remaining = ClockRules.timerRemaining(t, elapsed, now, boot)
            val n = Notification.Builder(context, CH_TIMERS)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(t.name.ifBlank { "Timer" })
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(now + remaining)
                .setShowWhen(true)
                .setOngoing(true)
                .setContentIntent(openClock(context, "timer"))
                .build()
            nm.notify(id, n)
        }
    }

    fun openClock(context: Context, page: String): PendingIntent = PendingIntent.getActivity(
        context, "clock:$page".hashCode(),
        Intent().setClassName(context.packageName, ReminderScheduler.CLOCK_ACTIVITY).putExtra("page", page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

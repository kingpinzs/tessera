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
import app.tileshell.R
import app.tileshell.brand.Brand
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
            Intent.ACTION_BOOT_COMPLETED -> ReminderScheduler.rearm(context, "boot")
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderScheduler.rearm(context, "package replaced")
            else -> Diagnostics.add("reminders", "receiver ignored ${intent.action}")
        }
    }
}

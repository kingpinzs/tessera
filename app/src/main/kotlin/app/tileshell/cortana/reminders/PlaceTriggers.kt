package app.tileshell.cortana.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import app.tileshell.diag.Diagnostics

/**
 * Place reminders (R2D-13 ruling A, place source ruled C).
 *
 * Android's own `LocationManager.addProximityAlert` does the geofencing — no Google Play services (P5).
 * The platform throttles the location request behind it
 * (`location_background_throttle_proximity_alert_interval_ms`, 30 minutes by default in AOSP), so a real
 * arrival can fire that long late; E13 shortens the setting and P7 records the real latency.
 *
 * Firing once is the store's job, not the platform's: a proximity alert re-fires every time the radius
 * is crossed, and GPS drift at the edge crosses it repeatedly. The alert fires the reminder, the reminder
 * leaves the active list, and [rearm] then drops its alert — so "moving outside and inside again fires
 * nothing more" (E13) falls out of the same rule that makes a reboot safe.
 */
object PlaceTriggers {

    private val armed = mutableSetOf<String>()

    fun rearm(context: Context) {
        val app = context.applicationContext
        val store = ReminderStore.get(app)
        val lm = app.getSystemService(LocationManager::class.java)
        val wanted = store.reminders.value
            .filter { !it.completed && it.kind == ReminderKind.PLACE && it.placeId != null }
            .mapNotNull { reminder -> store.place(reminder.placeId!!)?.let { reminder.id to it } }
            .toMap()

        synchronized(armed) {
            // Everything armed that should not be (fired, deleted, completed, or its place is gone).
            (armed - wanted.keys).forEach { id ->
                runCatching { lm.removeProximityAlert(proximityIntent(app, id)) }
                armed -= id
            }
            if (wanted.isEmpty()) {
                Diagnostics.add("places", "rearm: no place reminders")
                return
            }
            if (!granted(app)) {
                // Background location denied or downgraded (edge case): nothing is armed and the checklist row
                // says so, rather than a geofence that silently never fires.
                Diagnostics.add("places", "rearm: ${wanted.size} place reminders NOT armed, background location missing")
                return
            }
            wanted.forEach { (id, place) ->
                // Re-adding replaces the existing alert for the same PendingIntent, so a moved or renamed
                // place re-arms cleanly without a remove/add race.
                runCatching {
                    lm.addProximityAlert(place.lat, place.lon, place.radiusM, -1L, proximityIntent(app, id))
                }.onSuccess { armed += id }
                    .onFailure { Diagnostics.add("places", "arm $id failed: $it") }
            }
            Diagnostics.add("places", "rearm: ${armed.size} proximity alerts armed")
        }
    }

    fun onProximity(context: Context, intent: Intent) {
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID)
        Diagnostics.add("places", "proximity $id entering=$entering")
        // Only arriving counts ("when I get home"), never leaving.
        if (entering && id != null) ReminderScheduler.fire(context, id, "proximity")
    }

    /** "Allow all the time": a geofence that only holds while Cortana is open would miss every real arrival. */
    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun locationEnabled(context: Context): Boolean =
        context.getSystemService(LocationManager::class.java).isLocationEnabled

    private fun proximityIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        ("proximity:$id").hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderScheduler.ACTION_PROXIMITY)
            .putExtra(ReminderScheduler.EXTRA_ID, id)
            .setData(Uri.parse("tileshell://place/$id")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

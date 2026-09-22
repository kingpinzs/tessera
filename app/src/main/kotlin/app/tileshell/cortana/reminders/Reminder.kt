package app.tileshell.cortana.reminders

/**
 * W10M's three reminder kinds (R2D-13, Jeremy: "A"). Cortana's own reminders, stored in the launcher,
 * fired as notifications, listed on the Reminders page — not calendar events, not Android alarms.
 */
enum class ReminderKind { TIME, PLACE, PERSON }

/** The reminder card's recurrence dropdown (approximation, H19; R6 §3.4.3 shows "Every Month"). */
enum class Recurrence(val label: String) {
    ONCE("Only once"), DAY("Every day"), WEEK("Every week"), MONTH("Every month"), YEAR("Every year");

    companion object {
        fun of(label: String): Recurrence = entries.firstOrNull { it.label.equals(label, true) } ?: ONCE
    }
}

/**
 * One reminder.
 *
 * A TIME reminder with [timeMs] null is a **Whenever** reminder (H16): it never fires on its own and
 * lists under Whenever. PLACE and PERSON reminders also list under Whenever (H26) but do fire, on a
 * proximity alert or on real contact.
 */
data class Reminder(
    val id: String,
    val text: String,
    val kind: ReminderKind = ReminderKind.TIME,
    /** TIME only; null = Whenever. Wall-clock millis. */
    val timeMs: Long? = null,
    val recurrence: Recurrence = Recurrence.ONCE,
    /** PLACE only: a [Place.id]. A place deleted under a reminder leaves this dangling on purpose (edge case). */
    val placeId: String? = null,
    /** PERSON only: the Contacts lookup key, and the name as it was said, for the card and the subline. */
    val contactLookupKey: String? = null,
    val contactName: String? = null,
    /** "Add a photo" (R6 §3.4.2), chosen with MediaStore.ACTION_PICK_IMAGES; shown on the notification. */
    val photoUri: String? = null,
    /** Complete moves it to History (R7 §3.6); it stops firing and leaves the list. */
    val completed: Boolean = false,
    val createdMs: Long = 0L,
) {
    /** The group the Reminders page lists it under (R7 §3.5.1): Today, Coming up, or Whenever. */
    fun group(nowMs: Long, endOfTodayMs: Long): ReminderGroup = when {
        kind != ReminderKind.TIME || timeMs == null -> ReminderGroup.WHENEVER
        timeMs <= endOfTodayMs -> ReminderGroup.TODAY
        else -> ReminderGroup.COMING_UP
    }
}

enum class ReminderGroup(val header: String) { TODAY("Today"), COMING_UP("Coming up"), WHENEVER("Whenever") }

/**
 * A saved, named place (place source ruled C, 2026-09-17): saved at the spot from GPS, or by typing an
 * address looked up once through Nominatim. 150 m radius is an approximation (H21).
 */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Float = DEFAULT_RADIUS_M,
    /** "typed" or "current", for the Places page and the attribution line. */
    val source: String = "current",
    val savedMs: Long = 0L,
) {
    companion object { const val DEFAULT_RADIUS_M = 150f }
}

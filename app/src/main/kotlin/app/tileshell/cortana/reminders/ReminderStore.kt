package app.tileshell.cortana.reminders

import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

/**
 * Cortana's reminders and saved places, in one JSON file in app-private storage, written atomically —
 * the same shape as phase 01's layout store, for the same reason: a half-written file must never cost
 * a reminder.
 *
 * Every mutation re-arms what the platform forgets: [ReminderScheduler] re-reads the store, so a
 * reboot, an app update or a force-stop cannot leave a reminder without its alarm or proximity alert
 * (Decisions "Place and person reminder mechanics" (3); E6).
 */
class ReminderStore private constructor(private val context: Context) {

    private val file = File(context.filesDir, "cortana_reminders.json")

    private val remindersState = MutableStateFlow<List<Reminder>>(emptyList())
    private val placesState = MutableStateFlow<List<Place>>(emptyList())

    /** Everything not completed, newest last. The Reminders page groups them; History shows the rest. */
    val reminders: StateFlow<List<Reminder>> = remindersState.asStateFlow()
    val places: StateFlow<List<Place>> = placesState.asStateFlow()

    init {
        load()
    }

    // ---------------- reminders ----------------

    fun active(): List<Reminder> = remindersState.value.filterNot { it.completed }
    fun completed(): List<Reminder> = remindersState.value.filter { it.completed }
    fun find(id: String): Reminder? = remindersState.value.firstOrNull { it.id == id }

    /** Store a new reminder and arm it. Returns the stored copy (with its id and creation time). */
    fun add(reminder: Reminder): Reminder {
        val stored = reminder.copy(
            id = reminder.id.ifEmpty { UUID.randomUUID().toString() },
            createdMs = if (reminder.createdMs == 0L) System.currentTimeMillis() else reminder.createdMs,
        )
        mutate { it + stored }
        Diagnostics.add("reminders", "added ${stored.id} kind=${stored.kind} time=${stored.timeMs} place=${stored.placeId} person=${stored.contactName} text=\"${stored.text}\"")
        return stored
    }

    fun update(reminder: Reminder) {
        mutate { list -> list.map { if (it.id == reminder.id) reminder else it } }
        Diagnostics.add("reminders", "updated ${reminder.id}")
    }

    /** R7 §3.6: Delete removes it and cancels its trigger, at once, with no card. */
    fun delete(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
        Diagnostics.add("reminders", "deleted $id")
    }

    /** R7 §3.6: Complete moves it to History and cancels its trigger. */
    fun complete(id: String, completed: Boolean = true) {
        mutate { list -> list.map { if (it.id == id) it.copy(completed = completed) else it } }
        Diagnostics.add("reminders", "complete $id -> $completed")
    }

    /**
     * A recurring reminder that has fired moves to its next occurrence; a one-off is completed.
     * Called by [ReminderScheduler] when the alarm lands.
     */
    fun advanceAfterFiring(id: String) {
        val reminder = find(id) ?: return
        val next = nextOccurrence(reminder)
        if (next == null) {
            complete(id)
        } else {
            update(reminder.copy(timeMs = next))
            Diagnostics.add("reminders", "recurring $id -> next $next")
        }
    }

    private fun nextOccurrence(reminder: Reminder): Long? {
        val at = reminder.timeMs ?: return null
        val field = when (reminder.recurrence) {
            Recurrence.ONCE -> return null
            Recurrence.DAY -> Calendar.DAY_OF_YEAR
            Recurrence.WEEK -> Calendar.WEEK_OF_YEAR
            Recurrence.MONTH -> Calendar.MONTH
            Recurrence.YEAR -> Calendar.YEAR
        }
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        val now = System.currentTimeMillis()
        // A phone that was off through several occurrences catches up to the next one still ahead.
        do cal.add(field, 1) while (cal.timeInMillis <= now)
        return cal.timeInMillis
    }

    // ---------------- places ----------------

    fun place(id: String): Place? = placesState.value.firstOrNull { it.id == id }

    /** By the name the user said or typed, case-insensitively ("home", "Home"). */
    fun placeNamed(name: String): Place? =
        placesState.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun savePlace(place: Place): Place {
        val stored = place.copy(
            id = place.id.ifEmpty { UUID.randomUUID().toString() },
            savedMs = if (place.savedMs == 0L) System.currentTimeMillis() else place.savedMs,
        )
        mutatePlaces { list -> list.filterNot { it.id == stored.id } + stored }
        Diagnostics.add("places", "saved ${stored.name} (${stored.lat},${stored.lon}) r=${stored.radiusM} via ${stored.source}")
        return stored
    }

    fun renamePlace(id: String, name: String) {
        mutatePlaces { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
        Diagnostics.add("places", "renamed $id -> $name")
    }

    fun deletePlace(id: String) {
        mutatePlaces { list -> list.filterNot { it.id == id } }
        Diagnostics.add("places", "deleted $id")
    }

    // ---------------- persistence ----------------

    @Synchronized
    private fun mutate(change: (List<Reminder>) -> List<Reminder>) {
        val next = change(remindersState.value)
        if (next == remindersState.value) return
        remindersState.value = next
        save()
        ReminderScheduler.rearm(context, "store changed")
    }

    @Synchronized
    private fun mutatePlaces(change: (List<Place>) -> List<Place>) {
        val next = change(placesState.value)
        if (next == placesState.value) return
        placesState.value = next
        save()
        ReminderScheduler.rearm(context, "places changed")
    }

    private fun save() {
        val json = JSONObject()
            .put("version", VERSION)
            .put("reminders", JSONArray().apply {
                remindersState.value.forEach { r ->
                    put(JSONObject()
                        .put("id", r.id)
                        .put("text", r.text)
                        .put("kind", r.kind.name)
                        .put("timeMs", r.timeMs ?: JSONObject.NULL)
                        .put("recurrence", r.recurrence.name)
                        .put("placeId", r.placeId ?: JSONObject.NULL)
                        .put("contactLookupKey", r.contactLookupKey ?: JSONObject.NULL)
                        .put("contactName", r.contactName ?: JSONObject.NULL)
                        .put("photoUri", r.photoUri ?: JSONObject.NULL)
                        .put("completed", r.completed)
                        .put("createdMs", r.createdMs))
                }
            })
            .put("places", JSONArray().apply {
                placesState.value.forEach { p ->
                    put(JSONObject()
                        .put("id", p.id).put("name", p.name).put("lat", p.lat).put("lon", p.lon)
                        .put("radiusM", p.radiusM.toDouble()).put("source", p.source).put("savedMs", p.savedMs))
                }
            })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) error("reminder store rename failed")
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val json = JSONObject(file.readText())
            val arr = json.optJSONArray("reminders") ?: JSONArray()
            remindersState.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(
                    id = o.getString("id"),
                    text = o.optString("text"),
                    kind = runCatching { ReminderKind.valueOf(o.optString("kind")) }.getOrDefault(ReminderKind.TIME),
                    timeMs = if (o.isNull("timeMs")) null else o.getLong("timeMs"),
                    recurrence = runCatching { Recurrence.valueOf(o.optString("recurrence")) }.getOrDefault(Recurrence.ONCE),
                    placeId = if (o.isNull("placeId")) null else o.getString("placeId"),
                    contactLookupKey = if (o.isNull("contactLookupKey")) null else o.getString("contactLookupKey"),
                    contactName = if (o.isNull("contactName")) null else o.getString("contactName"),
                    photoUri = if (o.isNull("photoUri")) null else o.getString("photoUri"),
                    completed = o.optBoolean("completed"),
                    createdMs = o.optLong("createdMs"),
                )
            }
            val places = json.optJSONArray("places") ?: JSONArray()
            placesState.value = (0 until places.length()).map { i ->
                val o = places.getJSONObject(i)
                Place(
                    id = o.getString("id"), name = o.optString("name"),
                    lat = o.getDouble("lat"), lon = o.getDouble("lon"),
                    radiusM = o.optDouble("radiusM", Place.DEFAULT_RADIUS_M.toDouble()).toFloat(),
                    source = o.optString("source", "current"), savedMs = o.optLong("savedMs"),
                )
            }
            Diagnostics.add("reminders", "loaded ${remindersState.value.size} reminders, ${placesState.value.size} places")
        }.onFailure { Diagnostics.add("reminders", "store unreadable, starting empty: $it") }
    }

    companion object {
        const val VERSION = 1
        @Volatile private var instance: ReminderStore? = null
        fun get(context: Context): ReminderStore =
            instance ?: synchronized(this) { instance ?: ReminderStore(context.applicationContext).also { instance = it } }
    }
}

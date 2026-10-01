package app.tileshell.calendar

import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The shell's own calendar store: `calendar_sync.json` in the files dir, written with a temp file and a rename like
 * `LayoutStore`'s `start_layout.json`. What it holds and why is [SyncState]'s; the rules are [SyncStateRules]'.
 *
 * It is the Trust line's (e): its `allowed` list is the only gate to an account calendar, so nothing but the "Can sync
 * to" page's tick writes that list, and a file that is lost or unreadable reads as nothing allowed. A calendar is
 * named in it by `id`, `accountName`, `accountType` and `name` (the provider's `Calendars.NAME`).
 */
class CalendarSyncStore private constructor(context: Context) {
    private val file = File(context.filesDir, FILE)
    private var stamp = stampOf()
    private val flow = MutableStateFlow(load())
    val state: StateFlow<SyncState> = flow.asStateFlow()

    /** The state as the file holds it now: a file removed or replaced under the running shell is read again, never trusted from memory. */
    val current: SyncState
        @Synchronized get() {
            followFile()
            return flow.value
        }

    @Synchronized
    fun update(change: (SyncState) -> SyncState): SyncState {
        followFile()
        val before = flow.value
        val after = change(before)
        if (after == before) return before
        save(after)
        flow.value = after
        return after
    }

    private fun stampOf(): Pair<Long, Long> = file.lastModified() to file.length()

    private fun followFile() {
        val now = stampOf()
        if (now == stamp) return
        stamp = now
        flow.value = load()
    }

    private fun load(): SyncState = runCatching {
        if (!file.exists()) return@runCatching SyncState()
        val o = JSONObject(file.readText())
        SyncState(
            allowed = keys(o.optJSONArray("allowed")),
            mappings = buildMap {
                val m = o.optJSONArray("mappings") ?: JSONArray()
                for (i in 0 until m.length()) {
                    val e = m.getJSONObject(i)
                    val local = e.getLong("local")
                    // A mapping written before the key carried the name is kept, its target's name unknown (null): its
                    // copy stays hidden, and nothing is pushed to a calendar the key no longer names (F16).
                    put(local, SyncMapping(local, CalendarKey(e.getLong("id"), e.getString("accountName"), e.getString("accountType"), nameOf(e)), e.getLong("copy")))
                }
            },
            hidden = keys(o.optJSONArray("hidden")),
            firstDayOfWeek = if (o.has("firstDayOfWeek") && !o.isNull("firstDayOfWeek")) o.getInt("firstDayOfWeek").takeIf { it in 1..7 } else null,
            notifiedAlerts = buildSet {
                val n = o.optJSONArray("notifiedAlerts") ?: JSONArray()
                // Keys of the form <event>:<begin>:<alarmTime>; a bare row id an earlier build wrote names no alert now.
                for (i in 0 until n.length()) n.optString(i).takeIf { ':' in it }?.let { add(it) }
            },
            // Q-16-4: absent in a file an earlier build wrote; the next start of the shell writes it.
            remindersSince = if (o.has("remindersSince") && !o.isNull("remindersSince")) o.getLong("remindersSince") else null,
        )
    }.getOrElse {
        // Unreadable reads as empty: nothing allowed, nothing mapped — the safe side of the gate.
        Diagnostics.add("calendar", "calendar_sync.json could not be read, starting empty: $it")
        SyncState()
    }

    private fun save(state: SyncState) {
        runCatching {
            val o = JSONObject()
            o.put("version", 1)
            o.put("allowed", JSONArray().apply { state.allowed.forEach { put(json(it)) } })
            o.put("mappings", JSONArray().apply {
                state.mappings.values.forEach { m -> put(json(m.target).put("local", m.localEventId).put("copy", m.copyEventId)) }
            })
            o.put("hidden", JSONArray().apply { state.hidden.forEach { put(json(it)) } })
            if (state.firstDayOfWeek != null) o.put("firstDayOfWeek", state.firstDayOfWeek)
            o.put("notifiedAlerts", JSONArray().apply { state.notifiedAlerts.sorted().forEach { put(it) } })
            if (state.remindersSince != null) o.put("remindersSince", state.remindersSince)
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) error("rename failed")
            stamp = stampOf()
        }.onFailure { Diagnostics.add("calendar", "calendar_sync.json could not be written: $it") }
    }

    /**
     * A calendar: its `_ID`, account name, account type and its own name — the key form of `allowed`, `hidden` and a
     * mapping's target.
     */
    private fun json(k: CalendarKey): JSONObject = JSONObject().put("id", k.id).put("accountName", k.accountName).put("accountType", k.accountType)
        .apply { if (k.name != null) put("name", k.name) }

    private fun nameOf(o: JSONObject): String? = if (o.has("name") && !o.isNull("name")) o.getString("name") else null

    /**
     * The entries of `allowed` or `hidden`. An entry without the calendar's name — one an earlier build wrote — is
     * dropped: it cannot say WHICH calendar of its account was ticked, so nothing is allowed by it (F16).
     */
    private fun keys(a: JSONArray?): List<CalendarKey> = buildList {
        if (a == null) return@buildList
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val name = nameOf(o) ?: continue
            add(CalendarKey(o.getLong("id"), o.getString("accountName"), o.getString("accountType"), name))
        }
    }

    companion object {
        const val FILE = "calendar_sync.json"

        @Volatile private var instance: CalendarSyncStore? = null

        fun get(context: Context): CalendarSyncStore =
            instance ?: synchronized(this) { instance ?: CalendarSyncStore(context.applicationContext).also { instance = it } }
    }
}

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
 * JSON text to and from a tree of plain values — a `Map<String, Any?>` whose values are maps, lists, strings, numbers,
 * booleans and null. The store's file handling and its section-by-section reading ([SyncStateFile]) are written over
 * this, so the JVM tests run them; the phone's adapter over `org.json` is [JsonSyncCodec].
 */
interface SyncCodec {
    /** The file's top-level object. Throws when [text] is not one. */
    fun decode(text: String): Map<String, Any?>

    fun encode(tree: Map<String, Any?>): String
}

/** [SyncCodec] over Android's `org.json`: objects in insertion order, `JSONObject.NULL` as null. */
internal object JsonSyncCodec : SyncCodec {
    override fun decode(text: String): Map<String, Any?> = tree(JSONObject(text))

    override fun encode(tree: Map<String, Any?>): String = json(tree).toString()

    private fun tree(o: JSONObject): Map<String, Any?> = LinkedHashMap<String, Any?>().apply { for (k in o.keys()) put(k, plain(o.get(k))) }

    private fun plain(v: Any?): Any? = when (v) {
        is JSONObject -> tree(v)
        is JSONArray -> List(v.length()) { plain(v.get(it)) }
        JSONObject.NULL -> null
        else -> v
    }

    private fun json(tree: Map<*, *>): JSONObject = JSONObject().apply { for ((k, v) in tree) put(k.toString(), wrap(v)) }

    private fun wrap(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> json(v)
        is List<*> -> JSONArray().apply { v.forEach { put(wrap(it)) } }
        else -> v
    }
}

/**
 * `calendar_sync.json`'s shape, to and from [SyncState] — pure, so the JVM tests pin it (fix round F21, trust review
 * A-F8). Each section is read on its own, and inside a section each entry on its own: one bad entry is dropped and
 * said, a section that is not what it should be reads as empty and is said, and nothing else is lost with it. One bad
 * entry used to empty the whole store — the mappings and the notified set with it, so every synced copy showed twice
 * with two reminders.
 *
 * `allowed` fails closed in every case: an entry is a tick only when its id, account name, account type and the
 * calendar's own name are all there and of the right type. An entry without the name (a file an earlier build wrote,
 * F16) is dropped; a mapping without it is kept with its target's name unknown.
 */
object SyncStateFile {
    data class Read(val state: SyncState, val notes: List<String>)

    fun read(tree: Map<String, Any?>): Read {
        val notes = ArrayList<String>()
        val state = SyncState(
            allowed = entries(tree, "allowed", notes) { key(it, nameRequired = true, accountRequired = true) },
            mappings = entries(tree, "mappings", notes) { mapping(it) }.associateBy { it.localEventId },
            hidden = entries(tree, "hidden", notes) { key(it, nameRequired = true, accountRequired = false) },
            firstDayOfWeek = integral(tree["firstDayOfWeek"])?.takeIf { it in 1..7 }?.toInt(),
            // Keys of the form <event>:<begin>:<alarmTime>; a bare row id an earlier build wrote names no alert now.
            notifiedAlerts = (tree["notifiedAlerts"] as? List<*>).orEmpty().mapNotNullTo(LinkedHashSet()) { (it as? String)?.takeIf { key -> ':' in key } },
            // Q-16-4: absent in a file an earlier build wrote; the next start of the shell writes it.
            remindersSince = integral(tree["remindersSince"]),
        )
        return Read(state, notes)
    }

    fun write(state: SyncState): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        put("version", 1)
        put("allowed", state.allowed.map { tree(it) })
        put("mappings", state.mappings.values.map { m -> tree(m.target).apply { put("local", m.localEventId); put("copy", m.copyEventId) } })
        put("hidden", state.hidden.map { tree(it) })
        if (state.firstDayOfWeek != null) put("firstDayOfWeek", state.firstDayOfWeek)
        put("notifiedAlerts", state.notifiedAlerts.sorted())
        if (state.remindersSince != null) put("remindersSince", state.remindersSince)
    }

    /**
     * A calendar: its `_ID`, account name, account type and its own name — the key form of `allowed`, `hidden` and a
     * mapping's target.
     */
    private fun tree(k: CalendarKey): LinkedHashMap<String, Any?> = LinkedHashMap<String, Any?>().apply {
        put("id", k.id)
        put("accountName", k.accountName)
        put("accountType", k.accountType)
        if (k.name != null) put("name", k.name)
    }

    /** A section's entries, each read by [entry]; what cannot be read is dropped and noted, never thrown. */
    private fun <T : Any> entries(tree: Map<String, Any?>, section: String, notes: MutableList<String>, entry: (Map<*, *>) -> T?): List<T> {
        val raw = tree[section] ?: return emptyList()
        if (raw !is List<*>) {
            notes += "$section is unreadable: read as empty"
            return emptyList()
        }
        val out = raw.mapNotNull { (it as? Map<*, *>)?.let(entry) }
        if (out.size != raw.size) notes += "$section: ${raw.size - out.size} of ${raw.size} entries dropped (unreadable, or written before the key carried the calendar's name)"
        return out
    }

    /**
     * @param nameRequired an entry without the calendar's own name names no calendar (`allowed`, `hidden`)
     * @param accountRequired an entry with an empty account name or type is no tick (`allowed`: a Sync target is
     *   always an account's calendar)
     */
    private fun key(o: Map<*, *>, nameRequired: Boolean, accountRequired: Boolean): CalendarKey? {
        val id = integral(o["id"]) ?: return null
        val accountName = (o["accountName"] as? String)?.takeIf { it.isNotEmpty() || !accountRequired } ?: return null
        val accountType = (o["accountType"] as? String)?.takeIf { it.isNotEmpty() || !accountRequired } ?: return null
        val name = o["name"]
        if (name != null && name !is String) return null
        if (name == null && nameRequired) return null
        return CalendarKey(id, accountName, accountType, name as String?)
    }

    private fun mapping(o: Map<*, *>): SyncMapping? {
        val local = integral(o["local"]) ?: return null
        val copy = integral(o["copy"]) ?: return null
        // A mapping written before the key carried the name is kept, its target's name unknown (F16).
        return SyncMapping(local, key(o, nameRequired = false, accountRequired = false) ?: return null, copy)
    }

    /** A whole number as JSON holds it; a string of digits or a fraction is not one. */
    private fun integral(v: Any?): Long? = when (v) {
        is Int -> v.toLong()
        is Long -> v
        is Short -> v.toLong()
        is Byte -> v.toLong()
        else -> null
    }
}

/**
 * The shell's own calendar store: `calendar_sync.json` in the files dir, written with a temp file and a rename like
 * `LayoutStore`'s `start_layout.json`. What it holds and why is [SyncState]'s; the rules are [SyncStateRules]'; the
 * file's shape is [SyncStateFile]'s.
 *
 * It is the Trust line's (e): its `allowed` list is the only gate to an account calendar, so nothing but the "Can sync
 * to" page's tick writes that list, and a file that is lost or unreadable reads as nothing allowed. A calendar is
 * named in it by `id`, `accountName`, `accountType` and `name` (the provider's `Calendars.NAME`).
 *
 * Two rules of the fix round:
 *  - F17: a change whose save FAILED is not kept in memory. The store reads the file again and holds — and returns —
 *    what the file holds, because that is what the next start of the process reads: an un-tick that could not be
 *    written (storage full) must not look un-ticked now and come back ticked later.
 *  - F21: a file that cannot be read at all is kept aside (`calendar_sync.json.unreadable-<its time>`) rather than
 *    overwritten by the next save.
 */
class CalendarSyncStore internal constructor(private val file: File, private val codec: SyncCodec) {
    private var stamp = stampOf()
    private val flow = MutableStateFlow(load())
    val state: StateFlow<SyncState> = flow.asStateFlow()

    /** The state as the file holds it now: a file removed or replaced under the running shell is read again, never trusted from memory. */
    val current: SyncState
        @Synchronized get() {
            followFile()
            return flow.value
        }

    /** The state after [change] — or, when it could not be saved, the state the file still holds. */
    @Synchronized
    fun update(change: (SyncState) -> SyncState): SyncState {
        followFile()
        val before = flow.value
        val after = change(before)
        if (after == before) return before
        flow.value = if (save(after)) after else load()
        return flow.value
    }

    private fun stampOf(): Pair<Long, Long> = file.lastModified() to file.length()

    private fun followFile() {
        if (stampOf() == stamp) return
        flow.value = load()
    }

    /**
     * Reads the file. The stamp is taken BEFORE the read, so a write that lands during it is seen at the next look;
     * after an unreadable file was moved aside, the file to follow is "no file".
     */
    private fun load(): SyncState {
        stamp = stampOf()
        if (!file.exists()) return SyncState()
        val tree = try {
            codec.decode(file.readText())
        } catch (e: Exception) {
            // Unreadable reads as empty: nothing allowed, nothing mapped — the safe side of the gate.
            val kept = setAside()
            stamp = stampOf()
            Diagnostics.add("calendar", "calendar_sync.json could not be read, starting empty: $e ($kept)")
            return SyncState()
        }
        val read = SyncStateFile.read(tree)
        read.notes.forEach { Diagnostics.add("calendar", "calendar_sync.json: $it") }
        return read.state
    }

    /** Moves the unreadable file out of the next save's way; says where it went, or that it could not be moved. */
    private fun setAside(): String {
        val aside = File(file.parentFile, "$FILE.unreadable-${file.lastModified()}")
        val kept = runCatching { file.renameTo(aside) || (file.copyTo(aside, overwrite = true).exists() && file.delete()) }.getOrDefault(false)
        return if (kept) "kept as ${aside.name}" else "it could not be kept aside"
    }

    /** True when the file now holds [state]. */
    private fun save(state: SyncState): Boolean = runCatching {
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(codec.encode(SyncStateFile.write(state)))
        if (!tmp.renameTo(file)) error("rename failed")
        stamp = stampOf()
    }.onFailure { Diagnostics.add("calendar", "calendar_sync.json could not be written: $it") }.isSuccess

    companion object {
        const val FILE = "calendar_sync.json"

        @Volatile private var instance: CalendarSyncStore? = null

        fun get(context: Context): CalendarSyncStore =
            instance ?: synchronized(this) {
                instance ?: CalendarSyncStore(File(context.applicationContext.filesDir, FILE), JsonSyncCodec).also { instance = it }
            }
    }
}

package app.tileshell.tiles

import android.content.ComponentName
import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Permanent Start layout store (phase 01 owns it; phase 02 ADDs pin / move / resize / folders on top).
 * One JSON file in app-private storage, written atomically.
 */
class LayoutStore private constructor(private val context: Context) {
    data class Layout(
        val version: Int = VERSION,
        val placements: List<Placement>,
        val explicitSlots: Map<Slot, ComponentName>,
        /** The bottom tile row, left to right (INDEX Change Log 2026-09-17). */
        val dock: List<TileKey>,
    )

    private val file = File(context.filesDir, "start_layout.json")
    private val state = MutableStateFlow(load() ?: Layout(placements = DefaultLayout.placements(), explicitSlots = emptyMap(), dock = DefaultLayout.dock()))
    val layout: StateFlow<Layout> = state.asStateFlow()

    fun assignSlot(slot: Slot, component: ComponentName) {
        mutate { it.copy(explicitSlots = it.explicitSlots + (slot to component)) }
        Diagnostics.add("layout", "slot ${slot.name} explicitly assigned to ${component.flattenToShortString()}")
    }

    fun clearSlot(slot: Slot) {
        mutate { it.copy(explicitSlots = it.explicitSlots - slot) }
        Diagnostics.add("layout", "slot ${slot.name} explicit assignment cleared")
    }

    @Synchronized
    private fun mutate(change: (Layout) -> Layout) {
        val next = change(state.value)
        save(next)
        state.value = next
    }

    private fun save(layout: Layout) {
        val json = JSONObject()
            .put("version", layout.version)
            .put("placements", JSONArray().apply {
                layout.placements.forEach { p ->
                    put(JSONObject().put("key", p.key.id).put("x", p.x).put("y", p.y).put("size", p.size.name))
                }
            })
            .put("slots", JSONObject().apply {
                layout.explicitSlots.forEach { (slot, cn) -> put(slot.name, cn.flattenToString()) }
            })
            .put("dock", JSONArray().apply { layout.dock.forEach { put(it.id) } })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) error("layout store rename failed")
    }

    private fun load(): Layout? {
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val placements = json.getJSONArray("placements").let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    parseKey(o.getString("key"))?.let { Placement(it, o.getInt("x"), o.getInt("y"), TileSize.valueOf(o.getString("size"))) }
                }
            }
            val slotsJson = json.optJSONObject("slots") ?: JSONObject()
            val slots = slotsJson.keys().asSequence().mapNotNull { name ->
                val slot = runCatching { Slot.valueOf(name) }.getOrNull() ?: return@mapNotNull null
                ComponentName.unflattenFromString(slotsJson.getString(name))?.let { slot to it }
            }.toMap()
            val storedVersion = json.optInt("version", 1)
            if (storedVersion < 2) {
                // Version 1 had no user placements (pinning arrives in phase 02) and no bottom tile row: keep the
                // slot assignments, take the version-2 default placements and row (INDEX Change Log 2026-09-17).
                Diagnostics.add("layout", "migrating layout store v$storedVersion -> v$VERSION (slot assignments kept)")
                Layout(version = VERSION, placements = DefaultLayout.placements(), explicitSlots = slots, dock = DefaultLayout.dock())
                    .also { save(it) }
            } else {
                val dockJson = json.optJSONArray("dock") ?: JSONArray()
                val dock = (0 until dockJson.length()).mapNotNull { parseKey(dockJson.getString(it)) }
                Layout(version = storedVersion, placements = placements, explicitSlots = slots, dock = dock)
            }
        }.onFailure { Diagnostics.add("layout", "layout store unreadable, using default: $it") }.getOrNull()
    }

    private fun parseKey(id: String): TileKey? = when {
        id.startsWith("slot:") -> runCatching { TileKey.SlotTile(Slot.valueOf(id.removePrefix("slot:"))) }.getOrNull()
        id.startsWith("shell:") -> TileKey.ShellTile(id.removePrefix("shell:"))
        id.startsWith("app:") -> id.removePrefix("app:").substringBeforeLast(':').let { ComponentName.unflattenFromString(it) }?.let { TileKey.AppTile(it, null) }
        else -> null
    }

    companion object {
        const val VERSION = 2
        @Volatile private var instance: LayoutStore? = null
        fun get(context: Context): LayoutStore =
            instance ?: synchronized(this) { instance ?: LayoutStore(context.applicationContext).also { instance = it } }
    }
}

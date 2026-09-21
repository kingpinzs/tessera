package app.tileshell.tiles

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
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
 *
 * Phase 02 (version 3): the grid is an ORDER of tiles, and [GridPack] derives every position from it, so a
 * reflow can never leave a tile outside the grid or on top of another one. Folders live beside the order and
 * hold their own ordered members.
 */
class LayoutStore private constructor(private val context: Context) {
    data class Layout(
        val version: Int = VERSION,
        /** The Start grid in reading order; [GridPack.pack] turns it into positions. */
        val order: List<Sized>,
        val explicitSlots: Map<Slot, ComponentName>,
        /** The bottom tile row, left to right (INDEX Change Log 2026-09-17). */
        val dock: List<TileKey>,
        /** Live folders by id (phase 02). */
        val folders: Map<String, Folder> = emptyMap(),
    ) {
        fun placements(unitsAcross: Int): List<Placement> = GridPack.pack(order, unitsAcross)
        fun folderOf(key: TileKey): Folder? = (key as? TileKey.FolderTile)?.let { folders[it.folderId] }
        fun sizeOf(key: TileKey): TileSize? =
            order.firstOrNull { it.key == key }?.size
                ?: folders.values.firstNotNullOfOrNull { f -> f.members.firstOrNull { it.key == key }?.size }
                ?: if (key in dock) TileSize.SMALL else null
        /** Every tile the layout holds: grid, folder members and the bottom row. */
        fun allKeys(): List<TileKey> = order.map { it.key } + folders.values.flatMap { f -> f.members.map { it.key } } + dock
        fun contains(key: TileKey): Boolean = allKeys().any { it == key }
        fun folderHolding(key: TileKey): Folder? = folders.values.firstOrNull { f -> f.members.any { it.key == key } }
    }

    private val file = File(context.filesDir, "start_layout.json")
    private val state = MutableStateFlow(load() ?: Layout(order = DefaultLayout.order(), explicitSlots = emptyMap(), dock = DefaultLayout.dock()))
    val layout: StateFlow<Layout> = state.asStateFlow()

    fun assignSlot(slot: Slot, component: ComponentName) {
        mutate { it.copy(explicitSlots = it.explicitSlots + (slot to component)) }
        Diagnostics.add("layout", "slot ${slot.name} explicitly assigned to ${component.flattenToShortString()}")
    }

    fun clearSlot(slot: Slot) {
        mutate { it.copy(explicitSlots = it.explicitSlots - slot) }
        Diagnostics.add("layout", "slot ${slot.name} explicit assignment cleared")
    }

    // ---------------- phase 02 mutations (the rules live in LayoutOps; this adds persistence) ----------------

    /** Pin [key] at the end of the grid. Returns false when it is already on Start (edge case: pin twice). */
    fun pin(key: TileKey, size: TileSize = TileSize.MEDIUM): Boolean {
        var pinned = false
        mutate { layout -> LayoutOps.pin(layout, key, size)?.also { pinned = true } ?: layout }
        Diagnostics.add("layout", "pin ${key.id} size=$size -> ${if (pinned) "added" else "already on Start"}")
        return pinned
    }

    /** Remove [key] from wherever it is (grid, folder, bottom row); a folder left with one tile dissolves (H19). */
    fun unpin(key: TileKey) {
        mutate { LayoutOps.without(it, key) }
        Diagnostics.add("layout", "unpin ${key.id}")
    }

    /** Resize [key] to [size], in the grid or inside a folder. */
    fun resize(key: TileKey, size: TileSize) {
        mutate { LayoutOps.resize(it, key, size) }
        Diagnostics.add("layout", "resize ${key.id} -> $size")
    }

    /** Move [key] to [index] of the grid order (taking it out of a folder or the bottom row first). */
    fun moveInGrid(key: TileKey, index: Int) {
        mutate { LayoutOps.moveInGrid(it, key, index) }
        Diagnostics.add("layout", "move ${key.id} to grid index $index")
    }

    /**
     * Move [key] into the bottom tile row at [index]. Refused (false) when the row is full: [capacity] is the
     * row's tile count for the current column count (E9: a drop onto a full row is refused).
     */
    fun moveToDock(key: TileKey, index: Int, capacity: Int): Boolean {
        var moved = false
        mutate { layout -> LayoutOps.moveToDock(layout, key, index, capacity)?.also { moved = true } ?: layout }
        Diagnostics.add("layout", "move ${key.id} to row index $index capacity=$capacity -> ${if (moved) "moved" else "refused (row full)"}")
        return moved
    }

    /**
     * Drop [dragged] on [target] during the dwell: creates a folder in place holding the target and the
     * dragged tile, at the target's size (R6 §1.6.2, H10/H11). Returns the folder id, or null when the drop
     * cannot make a folder (no nesting, H23).
     */
    fun createFolder(target: TileKey, dragged: TileKey): String? {
        var id: String? = null
        mutate { layout ->
            val made = LayoutOps.createFolder(layout, target, dragged) ?: return@mutate layout
            id = made.second
            made.first
        }
        Diagnostics.add("layout", "create folder from ${target.id} + ${dragged.id} -> ${id ?: "refused"}")
        return id
    }

    /** Add [key] to folder [folderId] at [index] (default: last member, R6 §1.6.2 / H23). */
    fun addToFolder(folderId: String, key: TileKey, index: Int = Int.MAX_VALUE): Boolean {
        var added = false
        mutate { layout -> LayoutOps.addToFolder(layout, folderId, key, index)?.also { added = true } ?: layout }
        Diagnostics.add("layout", "add ${key.id} to folder $folderId -> ${if (added) "added" else "refused"}")
        return added
    }

    /** The folder's name (R6 §1.7; null or blank clears it back to the "Name folder" placeholder). */
    fun renameFolder(folderId: String, name: String?) {
        mutate { LayoutOps.renameFolder(it, folderId, name) }
        Diagnostics.add("layout", "rename folder $folderId -> ${name ?: "(none)"}")
    }

    /**
     * Packages that are gone (uninstall) lose every tile they own, in the grid, in folders and in the row;
     * their secondary tiles go with them (R5 §1.9). Folders left with one tile dissolve (H19).
     */
    fun onPackagesRemoved(packages: Set<String>) {
        if (packages.isEmpty()) return
        val before = state.value
        mutate { LayoutOps.removePackages(it, packages) }
        if (before != state.value) Diagnostics.add("layout", "packages removed $packages: tiles dropped")
    }

    /**
     * The bottom tile row holds at most one row of tiles, so turning "show more tiles" off (6 -> 4 units) moves
     * the tiles that no longer fit to the end of the grid instead of hiding them
     * (phase 02 Decisions "Bottom tile row editing", E9).
     */
    fun applyRowCapacity(capacity: Int) {
        mutate { LayoutOps.applyRowCapacity(it, capacity) }
    }

    @Synchronized
    private fun mutate(change: (Layout) -> Layout) {
        val next = change(state.value)
        if (next == state.value) return
        save(next)
        state.value = next
    }

    private fun save(layout: Layout) {
        val json = JSONObject()
            .put("version", layout.version)
            .put("order", JSONArray().apply {
                layout.order.forEach { put(JSONObject().put("key", it.key.id).put("size", it.size.name)) }
            })
            .put("slots", JSONObject().apply {
                layout.explicitSlots.forEach { (slot, cn) -> put(slot.name, cn.flattenToString()) }
            })
            .put("dock", JSONArray().apply { layout.dock.forEach { put(it.id) } })
            .put("folders", JSONArray().apply {
                layout.folders.values.forEach { f ->
                    put(JSONObject()
                        .put("id", f.id)
                        .put("name", f.name ?: JSONObject.NULL)
                        .put("members", JSONArray().apply {
                            f.members.forEach { put(JSONObject().put("key", it.key.id).put("size", it.size.name)) }
                        }))
                }
            })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) error("layout store rename failed")
    }

    private fun load(): Layout? {
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val slotsJson = json.optJSONObject("slots") ?: JSONObject()
            val slots = slotsJson.keys().asSequence().mapNotNull { name ->
                val slot = runCatching { Slot.valueOf(name) }.getOrNull() ?: return@mapNotNull null
                ComponentName.unflattenFromString(slotsJson.getString(name))?.let { slot to it }
            }.toMap()
            val storedVersion = json.optInt("version", 1)
            val dockJson = json.optJSONArray("dock") ?: JSONArray()
            val dock = (0 until dockJson.length()).mapNotNull { parseKey(dockJson.getString(it)) }
            when {
                storedVersion < 2 -> {
                    // Version 1 had no user placements (pinning arrives in phase 02) and no bottom tile row: keep the
                    // slot assignments, take the default order and row (INDEX Change Log 2026-09-17).
                    Diagnostics.add("layout", "migrating layout store v$storedVersion -> v$VERSION (slot assignments kept)")
                    Layout(VERSION, DefaultLayout.order(), slots, DefaultLayout.dock()).also { save(it) }
                }
                storedVersion == 2 -> {
                    // Version 2 stored coordinates; phase 02 stores the order the coordinates read in.
                    val arr = json.optJSONArray("placements") ?: JSONArray()
                    val placements = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.getJSONObject(i)
                        parseKey(o.getString("key"))?.let { Triple(it, o.getInt("y") to o.getInt("x"), TileSize.valueOf(o.getString("size"))) }
                    }
                    val order = placements.sortedWith(compareBy({ it.second.first }, { it.second.second })).map { Sized(it.first, it.third) }
                    Diagnostics.add("layout", "migrating layout store v2 -> v$VERSION (${order.size} tiles kept in reading order)")
                    Layout(VERSION, order, slots, dock).also { save(it) }
                }
                else -> {
                    val orderJson = json.optJSONArray("order") ?: JSONArray()
                    val order = (0 until orderJson.length()).mapNotNull { i ->
                        val o = orderJson.getJSONObject(i)
                        parseKey(o.getString("key"))?.let { Sized(it, TileSize.valueOf(o.getString("size"))) }
                    }
                    val foldersJson = json.optJSONArray("folders") ?: JSONArray()
                    val folders = (0 until foldersJson.length()).mapNotNull { i ->
                        val o = foldersJson.getJSONObject(i)
                        val membersJson = o.optJSONArray("members") ?: JSONArray()
                        val members = (0 until membersJson.length()).mapNotNull { m ->
                            val e = membersJson.getJSONObject(m)
                            parseKey(e.getString("key"))?.let { Sized(it, TileSize.valueOf(e.getString("size"))) }
                        }
                        val id = o.getString("id")
                        // optString turns a JSON null into the four-letter string "null"; a folder with no name
                        // has to come back as a real null or the band shows "null" as its name.
                        val name = if (o.isNull("name")) null else o.optString("name").takeIf { it.isNotBlank() }
                        id to Folder(id, name, members)
                    }.toMap()
                    // A folder id in the order with no folder behind it (a hand-edited or truncated file) is dropped
                    // rather than drawn as an empty tile.
                    Layout(storedVersion, order.filter { (it.key as? TileKey.FolderTile)?.folderId?.let { id -> id in folders } ?: true }, slots, dock, folders)
                }
            }
        }.onFailure { Diagnostics.add("layout", "layout store unreadable, using default: $it") }.getOrNull()
    }

    private fun parseKey(id: String): TileKey? = when {
        id.startsWith("slot:") -> runCatching { TileKey.SlotTile(Slot.valueOf(id.removePrefix("slot:"))) }.getOrNull()
        id.startsWith("shell:") -> TileKey.ShellTile(id.removePrefix("shell:"))
        id.startsWith("folder:") -> TileKey.FolderTile(id.removePrefix("folder:"))
        id.startsWith("secondary:") -> id.removePrefix("secondary:").split(':', limit = 2).takeIf { it.size == 2 && it.all(String::isNotEmpty) }
            ?.let { TileKey.SecondaryTile(it[0], it[1]) }
        id.startsWith("app:") -> {
            val body = id.removePrefix("app:")
            val component = ComponentName.unflattenFromString(body.substringBeforeLast(':'))
            // The trailing number is the profile the tile belongs to. Dropping it (phase 01 read it as null)
            // sent every work-profile tile back as a main-profile one, so the tile no longer matched the app it
            // came from: resolve it against the profiles this device actually has.
            component?.let { TileKey.AppTile(it, userForHandleHash(body.substringAfterLast(':').toIntOrNull())) }
        }
        else -> null
    }

    /** The UserHandle whose hash the id carries, or null when that profile is not on this device any more. */
    private fun userForHandleHash(hash: Int?): UserHandle? {
        if (hash == null) return null
        val me = Process.myUserHandle()
        if (hash == me.hashCode()) return me
        val launcherApps = runCatching { context.getSystemService(LauncherApps::class.java) }.getOrNull()
        return runCatching { launcherApps?.profiles }.getOrNull()?.firstOrNull { it.hashCode() == hash }
    }

    companion object {
        const val VERSION = 3
        @Volatile private var instance: LayoutStore? = null
        fun get(context: Context): LayoutStore =
            instance ?: synchronized(this) { instance ?: LayoutStore(context.applicationContext).also { instance = it } }
    }
}

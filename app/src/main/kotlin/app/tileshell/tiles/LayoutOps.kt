package app.tileshell.tiles

import app.tileshell.diag.Diagnostics

/**
 * Every phase 02 layout rule as a pure function of the layout (build task 1). [LayoutStore] only adds
 * persistence on top, so the rules that decide what a drag, an unpin or an uninstall does to Start can be
 * proved without a device (LayoutOpsTest).
 */
object LayoutOps {
    private typealias L = LayoutStore.Layout

    /** Pin at the end of the grid; null when the tile is already somewhere on Start (pin twice does nothing). */
    fun pin(layout: L, key: TileKey, size: TileSize): L? =
        if (layout.contains(key)) null else layout.copy(order = layout.order + Sized(key, size))

    /**
     * Remove [key] from the grid, from any folder and from the bottom row. A folder left with ONE tile
     * dissolves into that tile, which takes the folder's place in the grid at its own size (R6 §1.8.1, H19);
     * an empty folder goes altogether.
     */
    fun without(layout: L, key: TileKey, dropFolderRecord: Boolean = true): L {
        var order = layout.order.filterNot { it.key == key }
        var folders = layout.folders
        val holder = layout.folderHolding(key)
        if (holder != null) folders = folders + (holder.id to holder.copy(members = holder.members.filterNot { it.key == key }))
        // A folder tile REMOVED from the grid takes its folder with it — but a folder tile being MOVED is only
        // detached for a moment, and deleting its record there loses every member (found by E8's no-nesting
        // path, which left the order pointing at a folder that no longer existed).
        if (key is TileKey.FolderTile && dropFolderRecord) folders = folders - key.folderId
        for (folder in folders.values.toList()) {
            when (folder.members.size) {
                1 -> {
                    val last = folder.members.first()
                    val index = order.indexOfFirst { it.key == TileKey.FolderTile(folder.id) }
                    order = if (index >= 0) order.toMutableList().apply { set(index, last) } else order + last
                    folders = folders - folder.id
                    Diagnostics.add("layout", "folder ${folder.id} left with one tile: dissolved into ${last.key.id}")
                }
                0 -> {
                    order = order.filterNot { it.key == TileKey.FolderTile(folder.id) }
                    folders = folders - folder.id
                    Diagnostics.add("layout", "folder ${folder.id} is empty: removed")
                }
            }
        }
        return layout.copy(order = order, dock = layout.dock.filterNot { it == key }, folders = folders)
    }

    fun resize(layout: L, key: TileKey, size: TileSize): L = layout.copy(
        order = layout.order.map { if (it.key == key) it.copy(size = size) else it },
        folders = layout.folders.mapValues { (_, f) -> f.copy(members = f.members.map { if (it.key == key) it.copy(size = size) else it }) },
    )

    /** Move [key] to [index] of the grid order, out of a folder or the bottom row if that is where it was. */
    fun moveInGrid(layout: L, key: TileKey, index: Int): L {
        val size = layout.sizeOf(key) ?: TileSize.MEDIUM
        val stripped = without(layout, key, dropFolderRecord = false)
        val at = index.coerceIn(0, stripped.order.size)
        return stripped.copy(order = stripped.order.toMutableList().apply { add(at, Sized(key, size)) })
    }

    /** Move [key] into the bottom tile row at [index]; null when the row is full (E9: the drop is refused). */
    fun moveToDock(layout: L, key: TileKey, index: Int, capacity: Int): L? {
        if (key !in layout.dock && layout.dock.size >= capacity) return null
        val stripped = without(layout, key, dropFolderRecord = false)
        val at = index.coerceIn(0, stripped.dock.size)
        return stripped.copy(dock = stripped.dock.toMutableList().apply { add(at, key) })
    }

    /**
     * A drop on [target] during the dwell makes a folder in its place, holding the target and the dragged tile
     * at the target's size (R6 §1.6.2, H10 / H11). Null when no folder can be made: folders never nest (H23),
     * and the target has to be a tile in the grid.
     */
    fun createFolder(layout: L, target: TileKey, dragged: TileKey): Pair<L, String>? {
        if (target is TileKey.FolderTile || dragged is TileKey.FolderTile) return null
        val targetItem = layout.order.firstOrNull { it.key == target } ?: return null
        val draggedSize = layout.sizeOf(dragged) ?: TileSize.MEDIUM
        val stripped = without(layout, dragged, dropFolderRecord = false)
        val index = stripped.order.indexOfFirst { it.key == target }
        if (index < 0) return null
        val id = nextFolderId(stripped.folders.keys)
        val folder = Folder(id, null, listOf(Sized(target, targetItem.size), Sized(dragged, draggedSize)))
        return stripped.copy(
            order = stripped.order.toMutableList().apply { set(index, Sized(TileKey.FolderTile(id), targetItem.size)) },
            folders = stripped.folders + (id to folder),
        ) to id
    }

    /**
     * A folder of [members] named [name], appended to the grid at [size]. Null when fewer than two
     * usable members survive, because a folder holding one tile dissolves (H19).
     *
     * Unlike [createFolder], which turns two tiles that are already on Start into a folder, this builds
     * one from apps that need not be on Start at all — the Games and Office folders
     * ([CategoryFolders]). Members are still taken out of wherever they are first, so seeding cannot
     * leave the same app in two places.
     */
    fun folderOf(layout: L, name: String, members: List<TileKey>, size: TileSize): Pair<L, String>? {
        val usable = members
            .filterNot { it is TileKey.FolderTile }
            // An app the user has already filed in a folder is THEIRS. Taking it would empty that folder
            // from underneath them, and a folder left holding one tile dissolves (H19) — which is exactly
            // what happened the first time this ran on the emulator: seeding Office pulled a member out of
            // an existing folder and destroyed it. A seeded folder only ever collects loose apps.
            .filterNot { layout.folderHolding(it) != null }
            .distinct()
        if (usable.size < 2) return null
        var work = layout
        usable.forEach { work = without(work, it, dropFolderRecord = false) }
        val id = nextFolderId(work.folders.keys)
        val folder = Folder(id, name, usable.map { Sized(it, TileSize.MEDIUM) })
        return work.copy(
            order = work.order + Sized(TileKey.FolderTile(id), size),
            folders = work.folders + (id to folder),
        ) to id
    }

    /** Add [key] to a folder at [index] (default last). Null when the folder is gone or a folder was dragged. */
    fun addToFolder(layout: L, folderId: String, key: TileKey, index: Int = Int.MAX_VALUE): L? {
        if (key is TileKey.FolderTile) return null
        layout.folders[folderId] ?: return null
        val size = layout.sizeOf(key) ?: TileSize.MEDIUM
        val stripped = without(layout, key, dropFolderRecord = false)
        val live = stripped.folders[folderId] ?: return null
        val at = index.coerceIn(0, live.members.size)
        return stripped.copy(folders = stripped.folders + (folderId to live.copy(members = live.members.toMutableList().apply { add(at, Sized(key, size)) })))
    }

    fun renameFolder(layout: L, folderId: String, name: String?): L {
        val folder = layout.folders[folderId] ?: return layout
        return layout.copy(folders = layout.folders + (folderId to folder.copy(name = name?.trim()?.takeIf { it.isNotEmpty() })))
    }

    /** Uninstalled packages lose every tile they own, wherever it is, and their secondary tiles with it. */
    fun removePackages(layout: L, packages: Set<String>): L {
        var next = layout
        for (key in layout.allKeys().distinct()) {
            val owner = when (key) {
                is TileKey.AppTile -> key.component.packageName
                is TileKey.SecondaryTile -> key.owner
                else -> null
            }
            if (owner != null && owner in packages) next = without(next, key)
        }
        return next
    }

    /**
     * The bottom tile row holds one row of tiles, so turning "show more tiles" off (6 units -> 4) moves what no
     * longer fits to the END of the grid rather than hiding it (phase 02 Decisions, E9).
     */
    fun applyRowCapacity(layout: L, capacity: Int): L {
        if (layout.dock.size <= capacity) return layout
        val overflow = layout.dock.drop(capacity)
        Diagnostics.add("layout", "row capacity $capacity: ${overflow.size} tile(s) moved to the end of the grid")
        return layout.copy(dock = layout.dock.take(capacity), order = layout.order + overflow.map { Sized(it, TileSize.SMALL) })
    }

    private fun nextFolderId(taken: Set<String>): String {
        var n = 1
        while ("f$n" in taken) n++
        return "f$n"
    }
}

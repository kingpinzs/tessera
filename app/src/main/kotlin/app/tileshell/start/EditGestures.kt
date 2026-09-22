package app.tileshell.start

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.GridPack
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.Sized
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.TileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What a press landed on. */
private sealed interface Hit {
    data class Tile(val key: TileKey, val grabFrac: Offset, val inRow: Boolean) : Hit
    data class DiscHit(val kind: Disc) : Hit
    /** The strip at the top of an expanded band: the "Name folder" placeholder, or the folder's name. */
    data class FolderName(val folderId: String) : Hit
    data object Empty : Hit
}

/**
 * Start's edit-mode gestures (phase 02 build tasks 2, 4 and 7). They sit on the whole screen, not on each tile,
 * so one gesture can carry a tile out of the grid, into a folder or into the bottom tile row — the row is drawn
 * outside the scrolling grid, so a per-tile handler could never follow a finger across that boundary.
 *
 * - Outside edit mode: holding a tile [Edit.HOLD_MS] (783 ms, R6 §1.1.1) with no feedback enters edit mode with
 *   that tile held, and the same gesture goes straight on to drag it (R6 §1.3.1). Anything shorter, any move
 *   past the touch slop, or a scroll/pager taking the gesture leaves Start exactly as it was.
 * - In edit mode: a tap on the unpin or resize disc acts (§1.2.9); a tap on another tile moves the selection
 *   (§1.5.1); a tap on the held tile, an empty cell or the wallpaper exits (§1.5.1); a press that moves drags
 *   that tile; a press that moves on empty space scrolls Start (edit mode owns scrolling while it is on).
 * - A drop follows the dwell rule in the Decisions (R6 §1.3.3 / §1.6.1, H20 / H5 / H10).
 *
 * Screen and content coordinates are converted in exactly one place, [Coords], because the grid is scrolled and
 * contracted while the bottom tile row is neither.
 */
class Coords(private val geo: StartGeometry, private val scrollValue: Int, private val pitchScale: Float) {
    private val originX = geo.grid.widthPx * Edit.FIXED_POINT_X
    private val originY = geo.fixedPointY

    /** A point on the screen, in the scrolling grid's own coordinates. */
    fun toContent(screen: Offset): Offset = Offset(
        originX + (screen.x - originX) / pitchScale,
        originY + (screen.y - originY) / pitchScale + scrollValue,
    )
}

fun Modifier.startEditGestures(
    edit: StartEditState,
    geoState: State<StartGeometry>,
    store: LayoutStore,
    scroll: ScrollState,
    scope: CoroutineScope,
    pitchScaleState: State<Float>,
): Modifier = this.pointerInput(Unit) {
    // Keyed on Unit on purpose: entering edit mode, the contraction and every reflow change this state, and a
    // pointerInput keyed on any of them would cancel the very gesture that caused the change mid-drag.
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val geo = geoState.value
        val pitchScale = pitchScaleState.value
        val coords = Coords(geo, scroll.value, pitchScale)
        val hit = hitTest(edit, geo, coords, down.position, pitchScale)
        if (!edit.active) {
            if (hit !is Hit.Tile) return@awaitEachGesture
            // The hold race: a move past the touch slop, a consumption (the scroll or the pager taking the
            // gesture) or a release before 783 ms all mean this was not a hold.
            val broke = withTimeoutOrNull(Edit.HOLD_MS) { waitForMoveOrUp(down, viewConfiguration.touchSlop) }
            if (broke != null) return@awaitEachGesture
            Diagnostics.add("edit", "hold ${Edit.HOLD_MS}ms on ${hit.key.id}: edit mode on")
            edit.enter(hit.key)
            // The discs appear now and ride the entry contraction (R6 §1.2.7). The same gesture only becomes a
            // drag if the finger moves; lifting it leaves the tile held with its two discs.
            if (waitForMoveOrUp(down, viewConfiguration.touchSlop)) {
                edit.drag = Drag(hit.key, down.position, hit.grabFrac, hit.inRow)
                dragLoop(edit, geoState, store, scroll, down, pitchScaleState)
            }
        } else {
            when (hit) {
                is Hit.DiscHit -> {
                    val selected = edit.selected
                    val up = waitForUpConsuming(down)
                    if (up != null && selected != null && hitTest(edit, geo, coords, up, pitchScale) is Hit.DiscHit) {
                        onDisc(hit.kind, selected, edit, store)
                    }
                }
                is Hit.Tile -> when (val press = awaitPress(down, viewConfiguration.touchSlop)) {
                    Press.Moved -> {
                        edit.selected = hit.key
                        edit.drag = Drag(hit.key, down.position, hit.grabFrac, hit.inRow)
                        dragLoop(edit, geoState, store, scroll, down, pitchScaleState)
                    }
                    is Press.Tap -> if (hit.key == edit.selected) {
                        Diagnostics.add("edit", "tap on the held tile ${hit.key.id}: exit, touch-up uptime=${press.uptimeMs}")
                        edit.requestExit(press.uptimeMs)
                    } else {
                        Diagnostics.add("edit", "selection moves to ${hit.key.id}")
                        edit.selected = hit.key
                    }
                    Press.Cancelled -> Unit
                }
                is Hit.FolderName -> {
                    // A tap, and Microsoft's documented tap-and-hold, both open the name box (R6 §1.7.2-§1.7.3).
                    val moved = waitForMoveOrUp(down, viewConfiguration.touchSlop)
                    if (!moved) {
                        Diagnostics.add("edit", "folder ${hit.folderId}: name box opened")
                        edit.naming = true
                    }
                }
                Hit.Empty -> when (val press = awaitPress(down, viewConfiguration.touchSlop)) {
                    Press.Moved -> scrollLoop(down, scroll, scope)
                    is Press.Tap -> {
                        Diagnostics.add("edit", "tap on empty space: exit, touch-up uptime=${press.uptimeMs}")
                        edit.requestExit(press.uptimeMs)
                    }
                    Press.Cancelled -> Unit
                }
            }
        }
    }
}

/** Follow the finger, keep the drop target current, and commit on release. */
private suspend fun AwaitPointerEventScope.dragLoop(
    edit: StartEditState,
    geoState: State<StartGeometry>,
    store: LayoutStore,
    scroll: ScrollState,
    down: PointerInputChange,
    pitchScaleState: State<Float>,
) {
    val dragged = edit.drag?.key ?: return
    val size = store.layout.value.sizeOf(dragged) ?: TileSize.MEDIUM
    Diagnostics.add("edit", "drag start ${dragged.id} size=$size")
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            val drag = edit.drag ?: break
            if (!change.pressed) {
                updateTarget(edit, geoState.value, store, size, scroll.value, pitchScaleState.value)
                commitDrop(edit, geoState.value, store)
                return
            }
            edit.drag = drag.copy(pointer = change.position)
            updateTarget(edit, geoState.value, store, size, scroll.value, pitchScaleState.value)
        }
    } finally {
        // A cancelled gesture must never leave a tile floating: the drag always ends here.
        edit.clearDrag()
    }
}

/** The dragged tile's centre decides the target (Decisions: "the dragged tile's centre enters a cell"). */
private fun updateTarget(edit: StartEditState, geo: StartGeometry, store: LayoutStore, size: TileSize, scrollValue: Int, pitchScale: Float) {
    val drag = edit.drag ?: return
    val layout = store.layout.value
    val w = if (drag.fromRow) geo.dockWidthPx else geo.wPx(size)
    val h = if (drag.fromRow) dockTileHeight(geo.grid) else geo.hPx(size)
    // The centre of the tile as it is drawn under the finger.
    val screenCentre = Offset(drag.pointer.x + (0.5f - drag.grab.x) * w, drag.pointer.y + (0.5f - drag.grab.y) * h)

    // The bottom tile row is on the screen, not in the scrolling grid, so it is tested in screen coordinates.
    // The strip is a drop target even when the row is EMPTY: gating this on the row already holding a tile made
    // an emptied row impossible to refill, a dead end (reviewer finding, 2026-09-21).
    if (screenCentre.y >= geo.dockTopPx - geo.grid.gutterPx) {
        edit.hover = null
        edit.folderFeedback = false
        edit.dropTarget = DropTarget.Row(geo.dockIndexAt(screenCentre.x))
        return
    }
    val centre = Coords(geo, scrollValue, pitchScale).toContent(screenCentre)
    if (geo.bandFolder != null && geo.inBand(centre.y)) {
        edit.hover = null
        edit.folderFeedback = false
        edit.dropTarget = DropTarget.Band(geo.bandFolder, geo.bandIndexAt(centre.x, centre.y))
        return
    }
    val cell = geo.cellAt(centre.x, centre.y)
    if (cell == null) {
        // Above the grid's top row, or inside the band's own rows: there is nothing under the tile. Leaving the
        // state as it was let a drag carried off the top keep a stale target, and a release there made a folder
        // with a tile the finger had left long before (reviewer finding, 2026-09-21). The Decisions are explicit:
        // leaving the tile before the dwell ends clears the feedback with no reflow.
        edit.hover = null
        edit.folderFeedback = false
        edit.dropTarget = DropTarget.None
        return
    }
    edit.dropTarget = DropTarget.Grid(cell.first, cell.second)
    val order = edit.previewOrder ?: layout.order
    val rest = order.filterNot { it.key == drag.key }
    // The target is read off the grid AS DRAWN (geo.placements), never off a fresh packing of the other tiles:
    // the drawn grid still holds the dragged tile's own gap, so re-packing would name a different tile than the
    // one under the finger.
    val index = GridPack.indexAt(geo.placements, cell.first, cell.second)
    val targetKey = index?.let { geo.placements[it].key }
    if (targetKey != null && targetKey != drag.key) {
        // An occupied cell: the target holds still and the dwell timer decides (R6 §1.3.3, H20).
        if (edit.hover != targetKey) edit.hover = targetKey
    } else if (targetKey == null) {
        // An empty cell: the tile slots in there straight away, no dwell, no folder.
        edit.hover = null
        edit.folderFeedback = false
        val at = GridPack.insertIndexForEmptyCell(geo.placements.filterNot { it.key == drag.key }, cell.first, cell.second)
        val next = rest.toMutableList().apply { add(at.coerceIn(0, this.size), Sized(drag.key, size)) }
        if (next != edit.previewOrder) edit.previewOrder = next
    } else {
        // The finger is back over the gap the dragged tile itself left: nothing to do.
        edit.hover = null
        edit.folderFeedback = false
    }
}

private fun commitDrop(edit: StartEditState, geo: StartGeometry, store: LayoutStore) {
    val drag = edit.drag ?: return
    val key = drag.key
    val hover = edit.hover
    val target = edit.dropTarget
    when {
        // A release while the folder feedback is showing makes a folder, or joins the folder it is showing on.
        edit.folderFeedback && hover != null -> {
            if (hover is TileKey.FolderTile) {
                if (store.addToFolder(hover.folderId, key)) edit.expandedFolder = hover.folderId
            } else {
                val id = store.createFolder(hover, key)
                if (id != null) {
                    // R6 §1.6.2 (H11): the folder is created in place and auto-expands, with the dropped tile
                    // still selected and the "Name folder" placeholder showing.
                    edit.expandedFolder = id
                    edit.naming = false
                    edit.selected = key
                }
            }
        }
        target is DropTarget.Row -> {
            if (!store.moveToDock(key, target.index, geo.grid.unitsAcross)) {
                Diagnostics.add("edit", "drop on a full bottom tile row refused: ${key.id} stays where it was")
            }
        }
        target is DropTarget.Band -> store.addToFolder(target.folderId, key, target.index)
        else -> {
            // Whatever the preview reflow settled on is what the layout keeps.
            val index = edit.previewOrder?.indexOfFirst { it.key == key } ?: -1
            if (index >= 0) store.moveInGrid(key, index)
        }
    }
    edit.clearDrag()
}

private fun onDisc(kind: Disc, selected: TileKey, edit: StartEditState, store: LayoutStore) {
    when (kind) {
        Disc.UNPIN -> {
            // R6 §1.2.8 approximation (H3): the tile goes at once, the tiles after it fill the gap, and Start
            // stays in edit mode with no tile selected.
            if ((selected as? TileKey.FolderTile)?.folderId == edit.expandedFolder) edit.expandedFolder = null
            val holder = store.layout.value.folderHolding(selected)
            store.unpin(selected)
            if (holder != null && store.layout.value.folders[holder.id] == null) edit.expandedFolder = null
            edit.selected = null
            Diagnostics.add("edit", "unpin ${selected.id}")
        }
        Disc.RESIZE -> {
            val size = store.layout.value.sizeOf(selected) ?: TileSize.MEDIUM
            store.resize(selected, size.next())
            Diagnostics.add("edit", "resize ${selected.id} $size -> ${size.next()}")
        }
    }
}

/** Where a press landed. Discs and the bottom row are screen-space; the grid and a band are content-space. */
private fun hitTest(edit: StartEditState, geo: StartGeometry, coords: Coords, screen: Offset, pitchScale: Float): Hit {
    // The disc is drawn at 31 epx on the screen, i.e. 31/0.90 in the contracted grid's own coordinates, centred
    // on the held tile's DRAWN corner — the same two numbers StartPage draws it with.
    val counter = 1f / pitchScale
    val discPx = Edit.px(Edit.DISC_EPX, geo.grid.widthPx) * counter
    val selected = edit.selected
    val content = coords.toContent(screen)
    if (edit.active && selected != null && edit.drag == null) {
        val p = geo.placements.firstOrNull { it.key == selected }
        if (p != null) {
            val cx = geo.xPx(p) + geo.wPx(p.size) / 2f + geo.wPx(p.size) / 2f * counter
            val cy = geo.yPx(p) + geo.hPx(p.size) / 2f
            val half = geo.hPx(p.size) / 2f * counter
            if (near(content, cx, cy - half, discPx)) return Hit.DiscHit(Disc.UNPIN)
            if (near(content, cx, cy + half, discPx)) return Hit.DiscHit(Disc.RESIZE)
        }
        val member = geo.members.firstOrNull { it.key == selected }
        if (member != null) {
            val cx = geo.memberXPx(member) + geo.wPx(member.size) / 2f + geo.wPx(member.size) / 2f * counter
            val cy = geo.memberYPx(member) + geo.hPx(member.size) / 2f
            val half = geo.hPx(member.size) / 2f * counter
            if (near(content, cx, cy - half, discPx)) return Hit.DiscHit(Disc.UNPIN)
            if (near(content, cx, cy + half, discPx)) return Hit.DiscHit(Disc.RESIZE)
        }
        val rowIndex = geo.dockKeys.indexOfFirst { it == selected }
        if (rowIndex >= 0) {
            val plain = Edit.px(Edit.DISC_EPX, geo.grid.widthPx)
            val right = geo.grid.leftMarginPx + rowIndex * (geo.dockWidthPx + geo.grid.gutterPx) + geo.dockWidthPx
            if (near(screen, right, geo.dockTopPx, plain)) return Hit.DiscHit(Disc.UNPIN)
            if (near(screen, right, geo.dockTopPx + dockTileHeight(geo.grid), plain)) return Hit.DiscHit(Disc.RESIZE)
        }
    }
    if (geo.dockKeys.isNotEmpty() && screen.y >= geo.dockTopPx && screen.y <= geo.dockTopPx + dockTileHeight(geo.grid)) {
        val i = geo.dockIndexAt(screen.x)
        val key = geo.dockKeys.getOrNull(i)
        if (key != null) {
            val x = geo.grid.leftMarginPx + i * (geo.dockWidthPx + geo.grid.gutterPx)
            val frac = Offset(((screen.x - x) / geo.dockWidthPx).coerceIn(0f, 1f), ((screen.y - geo.dockTopPx) / dockTileHeight(geo.grid)).coerceIn(0f, 1f))
            return Hit.Tile(key, frac, inRow = true)
        }
    }
    if (geo.bandFolder != null && geo.inBand(content.y)) {
        if (content.y < geo.bandMembersTopPx) return Hit.FolderName(geo.bandFolder)
        geo.members.forEach { p ->
            val x = geo.memberXPx(p)
            val y = geo.memberYPx(p)
            val w = geo.wPx(p.size)
            val h = geo.hPx(p.size)
            if (content.x in x..(x + w) && content.y in y..(y + h)) {
                return Hit.Tile(p.key, Offset((content.x - x) / w, (content.y - y) / h), inRow = false)
            }
        }
        return Hit.Empty
    }
    geo.placements.forEach { p ->
        val x = geo.xPx(p)
        val y = geo.yPx(p)
        val w = geo.wPx(p.size)
        val h = geo.hPx(p.size)
        if (content.x in x..(x + w) && content.y in y..(y + h)) {
            return Hit.Tile(p.key, Offset((content.x - x) / w, (content.y - y) / h), inRow = false)
        }
    }
    return Hit.Empty
}

private fun near(at: Offset, cx: Float, cy: Float, discPx: Float): Boolean =
    kotlin.math.abs(at.x - cx) <= discPx / 2f && kotlin.math.abs(at.y - cy) <= discPx / 2f

/** How a press ended: the finger moved past the slop, it lifted (a tap, with its own event time), or another
 *  handler took the gesture — which is NOT a tap, and used to be treated as one. */
private sealed interface Press {
    data object Moved : Press
    data class Tap(val uptimeMs: Long) : Press
    data object Cancelled : Press
}

private suspend fun AwaitPointerEventScope.awaitPress(down: PointerInputChange, slop: Float): Press {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return Press.Cancelled
        if (!change.pressed) return Press.Tap(change.uptimeMillis)
        if (change.isConsumed) return Press.Cancelled
        if ((change.position - down.position).getDistance() > slop) return Press.Moved
    }
}

/** True when the finger moved past the slop; false when it lifted or the gesture was taken. */
private suspend fun AwaitPointerEventScope.waitForMoveOrUp(down: PointerInputChange, slop: Float): Boolean =
    awaitPress(down, slop) is Press.Moved

/** Waits for the release, consuming everything so no tile below launches. Returns where the finger lifted. */
private suspend fun AwaitPointerEventScope.waitForUpConsuming(down: PointerInputChange): Offset? {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return null
        change.consume()
        if (!change.pressed) return change.position
    }
}

/** In edit mode Start does its own scrolling, so a drag on empty space still moves the grid. */
private suspend fun AwaitPointerEventScope.scrollLoop(down: PointerInputChange, scroll: ScrollState, scope: CoroutineScope) {
    var last = down.position.y
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        change.consume()
        if (!change.pressed) break
        val delta = last - change.position.y
        last = change.position.y
        val target = (scroll.value + delta).toInt().coerceIn(0, scroll.maxValue)
        scope.launch { scroll.scrollTo(target) }
    }
}

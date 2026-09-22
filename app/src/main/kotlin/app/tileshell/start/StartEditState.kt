package app.tileshell.start

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.tileshell.tiles.Sized
import app.tileshell.tiles.TileKey

/** Where a dragged tile currently is: a grid cell, a cell of an expanded folder's band, or the bottom row. */
sealed interface DropTarget {
    data class Grid(val x: Int, val y: Int) : DropTarget
    data class Band(val folderId: String, val index: Int) : DropTarget
    data class Row(val index: Int) : DropTarget
    data object None : DropTarget
}

/**
 * A drag in progress: [pointer] is the finger in SCREEN px (the bottom tile row lives there, the grid scrolls
 * under it) and [grab] is where inside the tile the finger grabbed it, as a fraction of the tile's size, so the
 * same offset holds whichever space the tile is drawn in.
 */
data class Drag(val key: TileKey, val pointer: Offset, val grab: Offset, val fromRow: Boolean)

/**
 * Edit mode's state (phase 02 build task 2). Held in the activity so Back and the pager can see it, and so a
 * configuration change or a resumed Start does not silently drop a drag.
 */
class StartEditState {
    /** Edit mode is on (R6 §1.1): the grid contracts, everything but the held tile dims. */
    var active by mutableStateOf(false)
        private set

    /** The held tile — the one at 1.00 with the two discs on its corners (R6 §1.1.7, §1.2). */
    var selected by mutableStateOf<TileKey?>(null)

    /** The exit is running (R6 §1.5): it starts 150 ms after touch-up and the glyphs go in its first frame. */
    var exiting by mutableStateOf(false)

    var drag by mutableStateOf<Drag?>(null)

    /** The order Start draws while a drag previews its reflow; null = the stored order. */
    var previewOrder by mutableStateOf<List<Sized>?>(null)

    /** The tile the dragged tile's centre is sitting on, and whether the folder-create feedback is showing. */
    var hover by mutableStateOf<TileKey?>(null)
    var folderFeedback by mutableStateOf(false)

    /** Where a release would drop the tile right now (the row and band cases have no hover tile). */
    var dropTarget by mutableStateOf<DropTarget>(DropTarget.None)

    /** The expanded folder (R6 §1.6.5) and whether its name is being typed (R6 §1.7.2). */
    var expandedFolder by mutableStateOf<String?>(null)
    var naming by mutableStateOf(false)

    /**
     * Where the name box belongs on screen, published by the band as it lays out. The box itself is drawn
     * OUTSIDE the pivot: a focused text field asks every scrollable ancestor to bring it into view, and inside
     * a pager page that scrolled the pivot to the app list the moment the keyboard opened.
     */
    var nameBoxYPx by mutableStateOf(0f)

    /** Entry/exit progress, driven by the frame clock: 0 = plain Start, 1 = fully in edit mode. */
    var scaleProgress by mutableStateOf(0f)
    var dimProgress by mutableStateOf(0f)

    /** Bumped whenever an entry or exit run starts, so the driving effect restarts. */
    var motionToken by mutableStateOf(0)
        private set

    fun enter(key: TileKey) {
        selected = key
        if (!active || exiting) {
            active = true
            exiting = false
            motionToken++
        }
    }

    /**
     * The touch-up that asked for the exit, on the input event's own clock. R6 §1.5.2 puts the exit 150 ± 17 ms
     * after touch-up, and that tolerance is finer than a screenrecord can resolve here (this AVD draws no touch
     * indicator), so the shell records the interval itself: this stamp against the frame clock at the exit's
     * first frame, both in uptime millis.
     */
    var exitRequestedUptimeMs: Long = 0L
        private set

    fun requestExit(atUptimeMs: Long = android.os.SystemClock.uptimeMillis()) {
        if (!active || exiting) return
        exitRequestedUptimeMs = atUptimeMs
        exiting = true
        motionToken++
    }

    fun finishExit() {
        active = false
        exiting = false
        selected = null
        drag = null
        previewOrder = null
        hover = null
        folderFeedback = false
        dropTarget = DropTarget.None
        naming = false
        scaleProgress = 0f
        dimProgress = 0f
    }

    fun clearDrag() {
        drag = null
        previewOrder = null
        hover = null
        folderFeedback = false
        dropTarget = DropTarget.None
    }
}

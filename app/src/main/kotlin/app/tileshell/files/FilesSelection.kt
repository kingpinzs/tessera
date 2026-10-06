package app.tileshell.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.cortana.ui.CortanaEaseOut
import app.tileshell.ui.MotionClock
import kotlin.math.roundToInt

/** Selection mode's measured values (pass 2 §1 UNMEASURED-7, two captures; r11/files.md 1.8). */
object SelectionMetrics {
    /** The checkbox: 20 square, centred at x 22, vertically centred on the row's icon. */
    const val CHECK = 20f
    const val CHECK_CX = 22f

    /** The content's shift: icon left 20 → 52, name left 72 → 104. */
    const val SHIFT = 32f

    /**
     * Pass 2 §5 row 4.7: entering is a 200 ± 40-ms ease-out slide; leaving is the same reversed (Y5 stand-in). Set one
     * frame-step under the value the `[motion]` line has to read (FilesMetrics' rule): the line reads 180–200.
     */
    const val SLIDE_MS = 196
}

/**
 * Selection mode (build task 3; r3 D2 (2)): whether it is on, which rows are selected (by path — a row that leaves the
 * list leaves the count with it), and how far the slide has come. One per `FilesBehaviour`.
 */
@Stable
class FilesSelection {
    /** Selection mode is on. */
    var active by mutableStateOf(false)
        private set

    /** The checkbox column is drawn: while the mode is on, and until the slide out has ended. */
    var shown by mutableStateOf(false)
        private set

    var paths: Set<String> by mutableStateOf(emptySet())
        private set

    /** 0 = the rows at rest, 1 = shifted the full 32 epx with the checkboxes in place. Read at layout. */
    val slide = mutableFloatStateOf(0f)

    fun enter() {
        if (active) return
        paths = emptySet()
        shown = true
        active = true
    }

    /** Back, or the end of an action: the rows slide back (`[motion] files_deselect`). */
    fun leave() {
        active = false
        paths = emptySet()
    }

    /** The page changed, or the picker opened: the mode is gone at once, with no slide. */
    fun drop() {
        active = false
        shown = false
        paths = emptySet()
        slide.floatValue = 0f
    }

    fun toggle(path: String) {
        paths = if (path in paths) paths - path else paths + path
    }

    fun set(all: Collection<String>) {
        paths = all.toSet()
    }

    /** The rows on screen that are selected, in the list's order. */
    fun of(entries: List<FileEntry>): List<FileEntry> = if (paths.isEmpty()) emptyList() else entries.filter { it.path in paths }

    /** Drives the slide in and out on the shell's motion clock; called from the page's overlay layer. */
    @Composable
    fun Motion() {
        LaunchedEffect(active) {
            if (active) {
                MotionClock.animate("files_select", SelectionMetrics.SLIDE_MS, CortanaEaseOut) { slide.floatValue = it }
            } else if (shown) {
                MotionClock.animate("files_deselect", SelectionMetrics.SLIDE_MS, CortanaEaseOut) { slide.floatValue = 1f - it }
                shown = false
            }
        }
    }
}

/**
 * A row's checkbox (`files_check:<name>`): it slides in from the left edge while the content slides right. In the
 * Icons view it sits over the icon's top-left corner, where the cell's content does not move.
 */
@Composable
fun SelectionCheck(selection: FilesSelection, entry: FileEntry, grid: Boolean) {
    if (!selection.shown) return
    val density = LocalDensity.current.density
    val checked = entry.path in selection.paths
    // A list row: centred on the icon. A cell of the Icons view: on the icon's top-left corner, drawn over it.
    val iconCentreY = if (grid) FilesMetrics.GRID_TOP_PAD + SelectionMetrics.CHECK / 2f else FilesMetrics.ROW / 2f
    Box(
        Modifier
            .offset { IntOffset(((selection.slide.floatValue - 1f) * SelectionMetrics.SHIFT * density).roundToInt(), 0) }
            .offset(x = (SelectionMetrics.CHECK_CX - SelectionMetrics.CHECK / 2f).dp, y = (iconCentreY - SelectionMetrics.CHECK / 2f).dp)
            .size(SelectionMetrics.CHECK.dp)
            .then(if (grid) Modifier.background(Color.Black.copy(alpha = 0.5f)) else Modifier)
            .border(2.dp, Color.White)
            .testTag("files_check:${entry.name}")
            .semantics { role = Role.Checkbox; toggleableState = if (checked) ToggleableState.On else ToggleableState.Off },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) FilesGlyphText(Glyph.CHECKMARK, 14f, Color.White)
    }
}

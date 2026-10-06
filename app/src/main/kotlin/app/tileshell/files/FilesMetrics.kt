package app.tileshell.files

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color

/**
 * Files' measured values, in epx (1 epx = 1/360 of the screen width; `ShellDensity` maps 1.dp to it). Each cites its row
 * of `docs/plan/r11/files.md` (F) or `docs/plan/r11/files-pass2.md` (P2, whose §4 governs), or its approximation row of
 * the phase doc (Y1–Y6). Vertical values are measured from the bottom of phase 01's drawn status bar
 * (`BarMetrics.STATUS_EPX`), which is where W10M's own 24-epx bar ended (C-17).
 */
object FilesMetrics {
    // ---- frame (F 1.1)
    /** F 1.1.2 / 1.1.4: the location bar and the closed app bar, 48 epx, fill #1F1F1F. */
    const val BAR = 48f
    val BAR_FILL = Color(0xFF1F1F1F)

    // ---- location bar (F 1.2)
    /** F 1.2.2: ≡ centred at x 24. F 1.2.8: ↑ centred 24 from the right edge. Both glyphs in a 20-epx box (F 1.7.3). */
    const val BAR_GLYPH_CX = 24f
    const val BAR_GLYPH = 20f

    /** F 1.2.3: the breadcrumb's left edge. */
    const val CRUMB_LEFT = 60f

    /** F 1.2.5: the "›" separator, (230,230,230), about 6 epx clear on each side. */
    const val CRUMB_GAP = 6f
    val CRUMB_SEPARATOR = Color(0xFFE6E6E6)

    /** F 1.2.9: ↑ at a volume's root, and every dimmed glyph of the bars. */
    val DIM_GLYPH = Color(0xFF7B7B7B)

    // ---- the ≡ pane (F 1.3)
    /** F 1.3.5 (P2 §1 re-read 254.5 / 255.7): 256 wide. F 1.3.6: fill #171717. */
    const val PANE_W = 256f
    val PANE_FILL = Color(0xFF171717)

    /** F 1.3.7–1.3.8: 48-epx rows from 48 below the pane's top (the ≡ band). F 1.3.9–1.3.10: glyph cx 24, label left 60. */
    const val PANE_ROW = 48f
    const val PANE_GLYPH_CX = 24f
    const val PANE_LABEL_LEFT = 60f

    /** F 1.3.11: the current row is the accent at 60 % over the pane's fill. */
    const val PANE_SELECTED_ACCENT = 0.6f

    // ---- sort line (F 1.4)
    /** F 1.4.3: text left 12, cap top 16 below the bar. P2 §1: the first row's top is 44–45 below the bar. */
    const val SORT_LEFT = 12f
    const val SORT_CAP_TOP = 16f
    const val SORT_BLOCK = 44f

    /** F 1.4.1: "Sort by:" (160,160,160), the value white, the chevron (204,204,204). */
    val SORT_LABEL = Color(0xFFA0A0A0)
    val SORT_CHEVRON = Color(0xFFCCCCCC)

    /** Y2 (UNMEASURED-6; R7 2.2.5): the sort flyout — 242.6 wide, 44-epx items, 14-epx inset, #2B2B2B. */
    const val FLYOUT_W = 242.6f
    const val FLYOUT_ITEM = 44f
    const val FLYOUT_INSET = 14f
    const val FLYOUT_PAD = 8f
    val FLYOUT_FILL = Color(0xFF2B2B2B)
    val FLYOUT_BORDER = Color(0xFF646464)

    /** Y5 stand-in (R7 2.2.6): the sort flyout grows over 233 ms, ease-out. */
    const val FLYOUT_GROW_MS = 233

    // ---- list rows (F 1.5; P2 §1 rows 1.5.2, 1.5.4–5)
    /** F 1.5.2: 64-epx pitch. F 1.5.3–1.5.4: icon box 32 × 40 at left 20, vertically centred; name left 72. */
    const val ROW = 64f
    const val ICON_LEFT = 20f
    const val ICON_W = 32f
    const val ICON_H = 40f
    const val NAME_LEFT = 72f

    /** A thumbnail in a list row: square, the icon's height, from the icon's left (F 1.5.3 gives the box only). */
    const val THUMB = 40f

    /** F 1.5.5: name ascender top 5 below the icon's top, detail digits 29 below it — the two text boxes' tops in the row. */
    const val NAME_TOP = 12.5f
    const val DETAIL_TOP = 36.5f

    /** F 1.5.7: the detail line, (165,165,165). */
    val DETAIL = Color(0xFFA5A5A5)

    /** P2 §5 row 4.6: a pressed row's fill, about (55,55,55), full width. */
    val ROW_PRESSED = Color(0xFF373737)

    /** P2 §5 row 4.5: the hold menu's first frame comes 700 ms after the press. */
    const val HOLD_MS = 700L

    // ---- icons view (F 1.6; P2 §1 UNMEASURED-8, §4.6)
    /** Three per row (pitch W/3). A thumbnail about 96 square; a type icon 67 × 84 in the same box. */
    const val GRID_COLUMNS = 3
    const val GRID_THUMB = 96f
    const val GRID_ICON_W = 67f
    const val GRID_ICON_H = 84f

    /** P2 §4.6: row pitch 172 ± 8, the label two centred lines at a 20-epx pitch, 12.8 below the icon (F 1.6.3). */
    const val GRID_ROW = 172f
    const val GRID_TOP_PAD = 8f
    const val GRID_LABEL_GAP = 12.8f
    const val GRID_LABEL_LINE = 20f
    val GRID_LABEL = Color(0xFFEDEDED)

    // ---- app bar (F 1.7)
    /** F 1.7.2: glyph centres 82 / 150 / 218 / 286 from the right (a 68-epx pitch), ••• at 24. F 1.7.3: 20-epx glyphs. */
    const val APPBAR_PITCH = 68f
    const val APPBAR_MORE_W = 48f
    const val APPBAR_GLYPH = 20f

    /** F 1.7.4: expanded, the bar is 60 tall with 12-epx labels centred 46.5 below its top. */
    const val APPBAR_EXPANDED = 60f
    const val APPBAR_LABEL_CY = 46.5f

    /** F 1.7.6–1.7.7: the overflow panel #2B2B2B, 44-epx items, text inset 12, disabled text (137,137,137). */
    val MORE_FILL = Color(0xFF2B2B2B)
    const val MORE_ITEM = 44f
    const val MORE_INSET = 12f
    val DISABLED = Color(0xFF898989)

    // ---- search (F 1.11.2)
    /** The box: 32 tall, 12 below the location bar, 12-epx side margins. */
    const val SEARCH_TOP = 12f
    const val SEARCH_SIDE = 12f

    // ---- Recent (P2 §1 UNMEASURED-5)
    /** The empty line: (160,160,160), left 14.5, cap top 16.8 below the bar (88.8 on W10M's 24-epx status bar). */
    const val RECENT_EMPTY_LEFT = 14f
    const val RECENT_EMPTY_CAP_TOP = 16.8f
    val RECENT_EMPTY = Color(0xFFA0A0A0)

    // ---- motion (Y5; P2 §5). The `[motion]` clock's t0 is taken up to one frame before the first drawn frame and
    // frames land on a 16.7-ms grid, so each duration is set one frame-step under the value its line has to read.
    /** P2 §5 row 4.1 (stand-in at 360 epx): a right-edge reveal, 250–283 ms. The line reads 250–267. */
    const val PANE_OPEN_MS = 242

    /** P2 §5 row 4.3: rows fade and slide up from 7.2 epx below rest over 300 ± 33 ms. The line reads 283–300. */
    const val FOLDER_ENTER_MS = 276
    const val FOLDER_ENTER_OFFSET = 7.2f

    /** P2 §5 row 4.3: names first, detail lines about 100 ms later, icons about 130 ms later (of the 300). */
    const val ENTER_DETAIL_AT = 100f / 300f
    const val ENTER_ICON_AT = 130f / 300f

    /** Y5 stand-in (R7 2.1.16): ••• expands over 317 ± 33 ms. The line reads 300–317. */
    const val MORE_MS = 293
}

/** Linear interpolation through (time fraction, value) points — a measured per-frame table used as the curve itself. */
class TableEasing(private val points: List<Pair<Float, Float>>) : Easing {
    override fun transform(fraction: Float): Float {
        if (fraction <= points.first().first) return points.first().second
        for (i in 1 until points.size) {
            val (t1, v1) = points[i]
            if (fraction <= t1) {
                val (t0, v0) = points[i - 1]
                return v0 + (v1 - v0) * ((fraction - t0) / (t1 - t0))
            }
        }
        return points.last().second
    }
}

object FilesEasing {
    /**
     * P2 §5 row 4.1, the pane's edge: 34.5 % at +17 ms, 56 % at +33, 70 % at +67, 84 % at +100, 91 % at +133, 95 % at
     * +150, 98 % at +200, 100 % at the end (measured to +283 on the 432-epx rail form; here over the whole reveal).
     */
    val PaneOpen: Easing = TableEasing(
        listOf(0f to 0f, 17f / 283f to 0.345f, 33f / 283f to 0.56f, 67f / 283f to 0.70f, 100f / 283f to 0.84f, 133f / 283f to 0.91f, 150f / 283f to 0.95f, 200f / 283f to 0.98f, 1f to 1f),
    )

    /**
     * P2 §5 row 4.3, the rows' way up: 7.2 below rest, then 4.6, 3.3, 2.0, 1.3, 0.7, 0 on successive captured frames —
     * as the share of the way covered at each sixth of the time.
     */
    val FolderEnter: Easing = TableEasing(
        listOf(0f to 0f, 1f / 6f to 0.361f, 2f / 6f to 0.542f, 3f / 6f to 0.722f, 4f / 6f to 0.819f, 5f / 6f to 0.903f, 1f to 1f),
    )
}

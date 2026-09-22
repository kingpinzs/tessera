package app.tileshell.ime

import kotlin.math.max
import kotlin.math.min

/** Decisions stand-in (2): the one-handed layout, docked left or right, or full width. */
enum class Dock { FULL, LEFT, RIGHT }

/**
 * Phys and epx turned into this screen's pixels. Everything the keyboard draws or hit-tests goes
 * through here, so there is one place that says how a measured value becomes a pixel.
 *
 * RV10: nothing here reads the IME window's own bounds or the system density — the widths come from
 * the display (WindowManager.maximumWindowMetrics, via [Scale]), so Samsung Screen zoom and Font size
 * never resize the keyboard. The keyboard is laid out in pixels directly (1 unit = 1 px, fontScale 1),
 * which is RV10's Density(pxPerEpx) contract written out: every size below is computed from the panel's
 * width, never from Android's dp.
 *
 * Horizontal: phys × (screen width / 1440) (Decisions "Key grid"). Vertical: the same factor in
 * portrait. In landscape the panel is short and wide, and a portrait-height keyboard would cover most of
 * the screen, so the vertical factor is capped at screen height / 2560 — the fraction of the height the
 * keyboard takes on the 1440 × 2560 reference panel (approximation, H2: R6 has no landscape footage).
 */
class KeyboardMetrics(
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val pxPerEpx: Float,
    val dock: Dock = Dock.FULL,
    /** Pixels the system nav bar covers at the bottom of the IME window (0 when the window ends above it). */
    val bottomInsetPx: Float = 0f,
) {
    /** Decisions stand-in (2): docked, the grid and the strip shrink to 0.80 of the panel width. */
    val dockFactor: Float = if (dock == Dock.FULL) 1f else DOCK_FACTOR

    val sx: Float = screenWidthPx / KeyGrid.PANEL * dockFactor
    val sy: Float = min(screenWidthPx / KeyGrid.PANEL, screenHeightPx / REFERENCE_HEIGHT)

    /** The docked grid's left edge on screen: flush with the chosen side. */
    val offsetX: Float = if (dock == Dock.RIGHT) screenWidthPx * (1f - DOCK_FACTOR) else 0f

    /** The band a docked layout frees, on the side away from the dock, or null at full width. */
    val freeBand: Pair<Float, Float>? = when (dock) {
        Dock.FULL -> null
        Dock.LEFT -> screenWidthPx * DOCK_FACTOR to screenWidthPx
        Dock.RIGHT -> 0f to screenWidthPx * (1f - DOCK_FACTOR)
    }

    /** R6 §2.2.1: the strip is 46.5 ± 1.3 epx — the one keyboard height in epx, not phys. */
    val stripH: Float = STRIP_EPX * pxPerEpx

    /** Row 1's top to the nav bar's top. */
    val blockH: Float = KeyGrid.BLOCK_H * sy

    /** The drawn panel: strip plus key block (the nav bar inset lies below it, outside the panel). */
    val panelH: Float = stripH + blockH

    /** Decisions "Moving the keyboard": up to one key-block height (865 phys) above rest (H13). */
    val maxRaise: Float = RAISE_MAX_PHYS * sy

    /**
     * How far a row-1 popup rises above the panel's top: its top is 7 + 233 phys above row 1 (R6 §2.3.3),
     * and the strip is below that. The window reserves this much so the popup is never clipped.
     */
    val popupOverhang: Float = max(0f, (KeyGrid.POPUP_LIFT + KeyGrid.POPUP_H) * sy - stripH)

    /** The input view's full height: headroom for the raised panel and the popups, then the panel, then the inset. */
    val viewH: Float = maxRaise + popupOverhang + panelH + bottomInsetPx

    /** The panel's top in view coordinates when raised by [raise] px. */
    fun panelTop(raise: Float): Float = viewH - bottomInsetPx - panelH - raise

    fun x(phys: Float): Float = offsetX + phys * sx
    fun w(phys: Float): Float = phys * sx
    fun h(phys: Float): Float = phys * sy

    /** A key-block phys y as a pixel y inside the panel (0 = the panel's top). */
    fun yInPanel(phys: Float): Float = stripH + phys * sy

    fun toPhysX(px: Float): Float = (px - offsetX) / sx
    fun toPhysY(pyInPanel: Float): Float = (pyInPanel - stripH) / sy

    fun epx(v: Float): Float = v * pxPerEpx

    companion object {
        const val DOCK_FACTOR = 0.80f
        const val STRIP_EPX = 46.5f
        const val REFERENCE_HEIGHT = 2560f
        const val RAISE_MAX_PHYS = 865f
    }
}

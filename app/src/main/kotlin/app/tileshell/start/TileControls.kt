package app.tileshell.start

/**
 * Where the transport controls sit inside a tile, and which one a touch landed on (Jeremy, 2026-09-21:
 * "show what is currently playing with play pauese stop skip on it"). INDEX Change Log item 3.
 *
 * **Why the hit test is a pure function and not a second pointer handler.** A tile is one gesture target
 * today: [TileView] runs one `awaitEachGesture` and calls `onTap` — which launches the app. Nesting a
 * second `pointerInput` inside the tile would leave "did the control swallow this?" to Compose's pointer
 * dispatch and consumption order, which is exactly the kind of thing that works until a press style, a
 * drag or an edit-mode gesture changes the shape of the event stream. A stray launch when someone meant
 * "pause" is the failure this is designed against, so the decision is made ONCE, in the one handler that
 * already owns the tile's touches, against a function that can be proven on the JVM: a touch inside the
 * strip is a control press and never reaches the launch path, and a touch anywhere else is a tile tap.
 *
 * Everything is a fraction of the tile's own size (RV10 / constraint 1): the same music tile can be drawn
 * medium, wide, or stretched in the bottom tile row, and a strip written in pixels would be a tap target
 * at exactly one of those.
 */
object TileControls {

    /**
     * The strip's share of the tile's height, measured from the bottom.
     *
     * A third of a medium tile is ≈49 epx tall, comfortably above a finger's target size, and it leaves
     * the top two thirds for the art, the title and the artist — the tile still reads as a tile with
     * something playing on it, not as a remote control.
     */
    const val STRIP_FRACTION = 0.34f

    /** The glyph's share of the shorter side of its own cell: big enough to read, with room around it. */
    const val GLYPH_FRACTION = 0.42f

    /** The scrim under the strip, so a white glyph survives a bright album cover behind it. */
    const val SCRIM_ALPHA = 0.45f

    /** The top of the control strip in a tile [heightPx] tall. */
    fun stripTopPx(heightPx: Float): Float = heightPx * (1f - STRIP_FRACTION)

    /** How wide one control's cell is when [count] of them share a tile [widthPx] wide. */
    fun cellWidthPx(widthPx: Float, count: Int): Float = if (count <= 0) 0f else widthPx / count

    /** The left edge of control [index]'s cell. */
    fun cellLeftPx(widthPx: Float, count: Int, index: Int): Float = cellWidthPx(widthPx, count) * index

    /**
     * Which control a touch at ([x], [y]) landed on in a [widthPx] x [heightPx] tile showing [count]
     * controls, or null when the touch belongs to the tile itself.
     *
     * Null for [count] <= 0 is what makes every other tile in the shell behave exactly as it did: a tile
     * with no controls can never take a touch away from its own launch path.
     */
    fun hitTest(x: Float, y: Float, widthPx: Float, heightPx: Float, count: Int): Int? {
        if (count <= 0 || widthPx <= 0f || heightPx <= 0f) return null
        if (x < 0f || y < 0f || x > widthPx || y > heightPx) return null
        if (y < stripTopPx(heightPx)) return null
        return (x / cellWidthPx(widthPx, count)).toInt().coerceIn(0, count - 1)
    }
}

package app.tileshell.ui.tokens

/**
 * Start grid geometry (R3 A1, HIGH, 14393 + 15063 screen recordings). W10M lays the Start grid out in
 * physical pixels from the panel width, so every value here is a fraction of the display width
 * (phase 01 Decisions: "Tokens"). Tile size is derived from the column count (RV6).
 */
data class StartGrid(val widthPx: Float, val mediumColumns: Int) {
    val leftMarginPx = widthPx * (13f / 1440f)
    val rightMarginPx = widthPx * (21f / 1440f)
    val gutterPx = widthPx * (17.5f / 1440f)

    /** RV6: tile = (width - margins - gutters) / columns. 3 columns reproduces A1's 457.5 phys tile. */
    val mediumPx: Float = (widthPx - leftMarginPx - rightMarginPx - gutterPx * (mediumColumns - 1)) / mediumColumns
    val pitchPx: Float = mediumPx + gutterPx
    /** Small tile = half a medium pitch minus a gutter (A1: 218 phys small, 19 phys small gutter). */
    val smallPitchPx: Float = pitchPx / 2f
    val smallPx: Float = smallPitchPx - gutterPx
    val widePx: Float = mediumPx * 2f + gutterPx
    /** Small-tile units across the grid: 2 per medium column. */
    val unitsAcross: Int = mediumColumns * 2

    fun unitX(unit: Int): Float = leftMarginPx + unit * smallPitchPx
    fun unitY(unit: Int): Float = unit * smallPitchPx

    companion object {
        /** A1: first tile row sits under the 28-epx status bar (108 phys on a 1440 panel). */
        const val GRID_TOP_EPX = 28f
        /** A2: tile label = caption 12 epx, inset 8 epx from the left and baseline 8 epx above the bottom. */
        const val LABEL_INSET_EPX = 8f
        /** Q11 / B1: Lumia 950 default = 3 medium columns ("show more tiles" on). */
        const val DEFAULT_COLUMNS = 3
    }
}

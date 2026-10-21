package app.tileshell.ac

/**
 * The action center's geometry (R3 A19, MEDIUM: 14393 S1 I98ENfXJRqA @ 72.0 s expanded and 452 s collapsed, ± 1.1 epx;
 * 15063 not filmed, Y2 / H5). Values in epx; [pxPerEpx] is RV10's (3 on the 1080-px AVD). JVM-tested (`AcGeometryTest`,
 * build task 5); the quick-action grid (task 6) and the panel (task 5) both draw from it.
 */
class AcGeometry(val pxPerEpx: Float) {
    fun px(epx: Float): Float = epx * pxPerEpx

    /** A cell's left edge for column [col] (0..3). */
    fun cellLeftEpx(col: Int): Float = SIDE_MARGIN_LEFT_EPX + col * PITCH_X_EPX

    /**
     * A cell's top edge for [row] counted from the grid's top row (0), with the grid's first row at [GRID_TOP_EXPANDED_EPX]
     * when [expanded], else the one collapsed row at [GRID_TOP_COLLAPSED_EPX].
     */
    fun cellTopEpx(row: Int, expanded: Boolean): Float =
        if (expanded) GRID_TOP_EXPANDED_EPX + row * PITCH_Y_EPX else GRID_TOP_COLLAPSED_EPX

    companion object {
        /** Tile 85 × 60.5 epx at an 89 / 64 epx column / row pitch (gaps 4 / 3.4 epx). */
        const val TILE_W_EPX = 85f
        const val TILE_H_EPX = 60.5f
        const val PITCH_X_EPX = 89f
        const val PITCH_Y_EPX = 64f
        const val COLUMNS = 4

        /** Side margins 4 / 4.5 epx. */
        const val SIDE_MARGIN_LEFT_EPX = 4f
        const val SIDE_MARGIN_RIGHT_EPX = 4.5f

        /** Grid top: 64 epx expanded, 77 epx collapsed. */
        const val GRID_TOP_EXPANDED_EPX = 64f
        const val GRID_TOP_COLLAPSED_EPX = 77f

        /** The second status line (battery % and date) ends 38 epx from the top. */
        const val STATUS2_BOTTOM_EPX = 38f

        /** Tile glyph ≈13 epx at ≈12 epx from the tile's left and top. */
        const val TILE_GLYPH_EPX = 13f
        const val TILE_GLYPH_INSET_EPX = 12f

        /** Tile label: 12-epx caption class, bottom-left, inset ≈5 epx, baseline ≈8 epx above the bottom. */
        const val TILE_LABEL_INSET_EPX = 5f
        const val TILE_LABEL_BASELINE_EPX = 8f

        /** "Clear all" (left) / "Collapse" (right): 9.6-epx cap, 12-epx side margins, 24 epx below the grid. */
        const val BUTTON_CAP_EPX = 9.6f
        const val BUTTON_SIDE_EPX = 12f
        const val BUTTON_GAP_EPX = 24f

        /** A notification group header's app icon. */
        const val GROUP_ICON_EPX = 21f

        /** A notification item's circular avatar. */
        const val AVATAR_EPX = 51f

        /** The full-width accent drag handle with its "=" grip, at the panel's bottom edge. */
        const val HANDLE_EPX = 13f

        /** Colours as captured: tile (48,48,48); background black (Q-04-10: solid). */
        const val TILE_RGB = 0x303030
        const val BACKGROUND_RGB = 0x000000
    }
}

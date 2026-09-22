package app.tileshell.start

import androidx.compose.ui.graphics.Color
import app.tileshell.prefs.ThemeMode
import app.tileshell.ui.tokens.StartGrid

/**
 * W10M edit mode values (phase 02 Decisions, R6 §1.1-§1.5). Every number here carries its R6 row; the ones R6
 * could not measure are approximations with their own NEEDS-HUMAN row (H3-H23).
 */
object Edit {
    /** R6 §1.1.1 (MEDIUM, 14393): holding a tile 783 ± 33 ms enters edit mode, with no feedback during the hold. */
    const val HOLD_MS = 783L

    /** R6 §1.1.2 (HIGH): every other tile shrinks about its own centre to 0.835 ± 0.01. */
    const val OTHER_TILE_SCALE = 0.835f

    /** R6 §1.1.3 (HIGH): tile centres contract to 0.90 ± 0.01 of their distance from the fixed point. */
    const val PITCH_SCALE = 0.90f

    /** R6 §1.1.3: the fixed point sits at the screen's horizontal centre, 46-49 % of its height (fraction, not epx). */
    const val FIXED_POINT_X = 0.5f
    const val FIXED_POINT_Y = 0.475f

    /**
     * R6 §1.1.8 (MEDIUM): the scale settles in 417 ± 50 ms, 50 % done by ≈67 ms (strong ease-out). The curve is
     * a saturating exponential with that half-life; [SCALE_MS] is how long it is driven, which has to be past
     * the settle so the tail is not cut short — at 700 ms the tile is 0.04 px from home.
     */
    const val SCALE_MS = 700
    const val SCALE_HALF_MS = 67f

    /** R6 §1.1.9 (MEDIUM): the dimming settles in 550 ± 50 ms, 50 % by ≈83 ms, 90 % by ≈350 ms. */
    const val DIM_MS = 550
    val dimKeyframes = listOf(0 to 0f, 83 to 0.5f, 350 to 0.9f, 550 to 1f)

    /** R6 §1.5.2 (MEDIUM, 14393): the exit starts 150 ± 17 ms after touch-up. */
    const val EXIT_DELAY_MS = 150L
    /** R6 §1.5.3 (HIGH): scale and pitch return to 1.00 in 183-217 ms; the undim is 90 % done by ≈100 ms, settled ≈300 ms. */
    const val EXIT_SCALE_MS = 200
    const val EXIT_DIM_MS = 300
    val exitDimKeyframes = listOf(0 to 0f, 33 to 0.5f, 100 to 0.9f, 300 to 1f)

    /** R6 §1.5.4 (LOW, H8): the selection moves to another tile in ≈170-200 ms. */
    const val SELECT_MS = 185

    /** R6 §1.2.1-§1.2.3 (HIGH): two discs 31 ± 1.5 epx across, centred on the held tile's corners. */
    const val DISC_EPX = 31f
    /** R6 §1.2.5 (MEDIUM, 14393): unpin glyph 16 × 15 epx, resize glyph (one arrow) 12 × 12 epx. */
    const val UNPIN_GLYPH_W_EPX = 16f
    const val UNPIN_GLYPH_H_EPX = 15f
    const val RESIZE_GLYPH_EPX = 12f

    /** R6 §1.3.2 (LOW, H4): tiles that must make room slide over ≈300 ms with an ease-out; never fade or jump. */
    const val REFLOW_MS = 300

    /** R6 §1.3.3 approximation (H20): a tile held over another for 2000 ms makes room instead of making a folder. */
    const val DWELL_MS = 2000L

    /** R6 §1.4.1 / §1.4.2 (LOW, H6) and the wide -> medium approximation (H7). */
    const val RESIZE_SHRINK_MS = 500
    const val RESIZE_GROW_MS = 170
    const val RESIZE_BLANK_MS = 270
    const val RESIZE_FADE_MS = 170

    /** R6 §1.6.2 (LOW, H11): a new folder auto-expands in ≈230 ms. */
    const val FOLDER_CREATE_MS = 230
    /** R6 §1.6.6 (LOW, H15): expanding reveals the band top to bottom in ≈350-400 ms. */
    const val FOLDER_EXPAND_MS = 375
    /** R6 §1.6.7 (LOW, H16): collapse folds the rows away in ≈133 ms, the face returns over ≈100 ms, Start scrolls back ≈370 ms. */
    const val FOLDER_COLLAPSE_MS = 133
    const val FOLDER_FACE_RETURN_MS = 100
    const val FOLDER_SCROLL_BACK_MS = 370

    /** R6 §1.6.3 (LOW, H12): mini tile ≈0.20, pitch ≈0.31, inset ≈0.07 of the tile side; 3 columns from the top-left. */
    const val MINI_TILE = 0.20f
    const val MINI_PITCH = 0.31f
    const val MINI_INSET = 0.07f
    const val MINI_COLUMNS = 3

    /**
     * R6 §1.6.5 (LOW, H14) band geometry, as fractions of the medium tile side (the "tile" R6 measured against):
     * a 1-epx rule ≈0.20 tile below the folder tile, members ≈0.22 tile below that rule, the bottom rule ≈0.19
     * tile below the last member row.
     */
    const val BAND_TOP_RULE = 0.20f
    const val BAND_MEMBERS_TOP = 0.22f
    const val BAND_BOTTOM_RULE = 0.19f
    const val BAND_RULE_EPX = 1f

    /** R6 §1.7.2 (LOW, H18): the name text box is ≈0.27 × the tile side tall, full width, white fill. */
    const val NAME_BOX_HEIGHT = 0.27f

    /**
     * Dimming of everything but the held tile (R6 §1.1.5 dark, §1.1.6 light) as one overlay colour:
     * dark theme multiplies tile pixels by 0.53 (black at 47 %), light theme maps each channel to
     * 0.63c + 62, i.e. grey 168 at 37 %. The wallpaper's dark-theme factor is 0.25-0.30 (black at 72.5 %);
     * R6 measured no light-theme wallpaper value, so the light theme dims it like its tiles (agent).
     */
    fun tileDim(theme: ThemeMode): Color =
        if (theme == ThemeMode.DARK) Color.Black.copy(alpha = 0.47f) else Color(0.659f, 0.659f, 0.659f, 0.37f)

    fun wallpaperDim(theme: ThemeMode): Color =
        if (theme == ThemeMode.DARK) Color.Black.copy(alpha = 0.725f) else Color(0.659f, 0.659f, 0.659f, 0.37f)

    /**
     * R6 §1.1.8 gives the entry TWO numbers: half the move is done by ≈67 ms and it is still settling at
     * 417 ms. A power curve cannot hold both — fitted to the half-life it stops moving visibly by ≈250 ms —
     * so this is the saturating exponential those two numbers describe, with a half-life of [halfMs] and
     * normalised to reach exactly 1 at [durationMs] (no jump at the end).
     */
    fun easeOutProgress(elapsedMs: Float, durationMs: Int, halfMs: Float): Float {
        if (elapsedMs <= 0f) return 0f
        if (elapsedMs >= durationMs) return 1f
        return (1.0 - Math.exp(-Math.log(2.0) / halfMs * elapsedMs)).toFloat().coerceIn(0f, 1f)
    }

    /** epx -> px for this panel (the shell's 360-epx canvas, phase 01 Scale). */
    fun px(epx: Float, widthPx: Float): Float = epx * (widthPx / 360f)

    /** Height the expanded band adds below its folder tile, in px. */
    fun bandHeight(grid: StartGrid, memberRows: Int): Float {
        val m = grid.mediumPx
        val members = if (memberRows <= 0) 0f else memberRows * grid.smallPitchPx - grid.gutterPx
        return m * BAND_TOP_RULE + m * BAND_MEMBERS_TOP + members + m * BAND_BOTTOM_RULE
    }
}

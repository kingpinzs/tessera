package app.tileshell.cortana.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType

/**
 * Every measured number Cortana's ≡ pane and its destination pages are built from, each with the R7
 * section it comes from. Values R7 does not measure are marked **approximation** with the H row that
 * owns them (phase 03 Decisions, 2026-09-16).
 *
 * Units: **epx**. [app.tileshell.ui.tokens.ShellDensity] maps `1.dp == 1 epx == 1/360 of the screen
 * width`, so every value here is written straight as `.dp` with no conversion. `CAP_TOP` values are
 * the top of a **capital letter**, not the top of a text layout box — see [CapTopText].
 *
 * `RemindersValuesTest` pins every constant in this object against the phase 03 Decisions, in the
 * style of phase 02's `EditValuesTest`: it does not claim the values are right (R7 and the H rows own
 * that), it claims the code still says what the doc says.
 */
object CortanaUi {

    // ------------------------------------------------------------------ R7 §3.1: the ≡ pane

    /** §3.1.5: pane 256 ± 1 epx wide (N1 255.9, N2 256.6), laid out in epx on both builds. */
    const val PANE_WIDTH_EPX = 256f

    /** §3.1.6: pane fill (14,19,13) as captured, on both builds. Also the page fill, §3.5.1. */
    val PAGE_BG = Color(0xFF0E130D)

    /** §3.1.7: header band 52 ± 1.5 epx tall (N1 37.6 px on the 411-epx canvas). */
    const val PANE_HEADER_EPX = 52f

    /** §3.1.7: the ≡ button is 48 × 48 epx, its top 4 epx below the screen top, its bottom the band bottom. */
    const val MENU_BUTTON_EPX = 48f
    const val MENU_BUTTON_TOP_EPX = 4f

    /** §3.1.7 / §3.3.2: the ≡ glyph spans x 16–32 epx, three 1-epx bars at a 4.2–4.4 epx pitch. */
    const val MENU_GLYPH_LEFT_EPX = 16f
    const val MENU_GLYPH_WIDTH_EPX = 16f
    const val MENU_GLYPH_BAR_PITCH_EPX = 4.3f
    const val MENU_GLYPH_BAR_EPX = 1f

    /** §3.1.7: "CORTANA" left 49 ± 1 epx (N1 49.7, N2 48.9), semibold caps. */
    const val PANE_TITLE_LEFT_EPX = 49f

    /**
     * §3.1.7 measures "CORTANA" cap **height** 10.7–11 epx but no cap top. Approximation (H23): the
     * cap is centred on the header band the way §3.3.2 centres a page title's cap (26.3 epx from the
     * screen top), so both headers share one optical centre line.
     */
    const val PANE_TITLE_CAP_CENTRE_EPX = 26.3f

    /** §3.1.8: menu-item pitch 48 epx (N1 47.9, N2 47.8); the selected fill is 48 epx tall. */
    const val PANE_ITEM_PITCH_EPX = 48f
    const val PANE_ITEM_FILL_EPX = 48f

    /**
     * §3.1.8 / E15: the first two top-group label cap tops, 71.8–72.2 and 119.3–119.6 epx from the
     * screen top. (Phase 09 ADDs Notebook at the third, 167.2–167.5 — not this phase's.) Each label is
     * placed at its own measured cap top, because the measured cap-top pitch (47.5) and the measured
     * row pitch (48) differ by half an epx and E15 checks the cap tops.
     */
    val PANE_ITEM_CAP_TOPS_EPX = listOf(72f, 119.5f)

    /**
     * Approximation (H23): R7 gives no top for the first item's 48-epx fill, only its label's cap top.
     * 52 epx makes the fill flush with the bottom of the 52-epx header band, which is the only
     * placement consistent with both §3.1.7's band and §3.1.8's 48-epx pitch.
     */
    const val PANE_TOP_GROUP_TOP_EPX = 52f

    /** §3.1.8: label left 49 ± 1 epx; ≈16-epx icons with their left edge at 17–18 epx. */
    const val PANE_LABEL_LEFT_EPX = 49f
    const val PANE_ICON_LEFT_EPX = 17.5f
    const val PANE_ICON_EPX = 16f

    /** §3.1.9: Settings' label cap top 172 ± 1 epx above the **screen bottom** (N1 171.9, N2 172.3). */
    const val PANE_SETTINGS_CAP_TOP_ABOVE_BOTTOM_EPX = 172f

    /**
     * §3.1.9 + the Decisions' "Feedback left out" ruling (approximation A11, H23): W10M's Feedback item
     * sat 48 epx below Settings, so its slot's cap top would be 124 epx above the screen bottom. The
     * slot stays **empty** — E15 asserts there is no node there at all, so nothing is drawn or tagged.
     */
    const val PANE_EMPTY_SLOT_CAP_TOP_ABOVE_BOTTOM_EPX = 124f

    /** §3.1.6: a pressed, not-yet-selected pane item is (63,68,64) — the same fill as a pressed row. */
    val PRESSED_FILL = Color(0xFF3F4440)

    /** §3.1.6: the current page's item is an accent fill across the full pane width, white text and icon. */
    val SELECTED_TEXT = Color.White

    /** §3.1.10: the pane slides in from the left, ease-out, settling 250 ± 17 ms after its first frame. */
    const val PANE_SLIDE_MS = 250

    /** §3.1.12: the accent fill holds until pane and page go black in one frame 350 ± 17 ms after touch-down. */
    const val PANE_SELECT_TO_BLACK_MS = 350

    /**
     * §3.1.12 (N2, the one capture that caught the fill frame): touch 869.433 → accent fill 869.767 →
     * black 869.783, so the accent lands 334 ms after touch-down, one frame before the blackout. Until
     * then the item carries §3.1.6's pressed fill.
     */
    const val PANE_PRESS_TO_ACCENT_MS = 334L

    // ------------------------------------------------------- R7 §3.2: the destination transition

    /** §3.2.1: fully black for 283–300 ± 17 ms (N2 18 frames, N1 17 frames twice). Midpoint of the range. */
    const val BLACK_MS = 292

    /** §3.2.2: the whole page then fades up, ease-out, over 200–317 ms (N1 200 / 317, N2 250). Midpoint. */
    const val FADE_MS = 258

    /** §3.2.2: the fade's first frame is already 40–60 % of the way up (N1 43 %, N2 61 %). Midpoint. */
    const val FADE_FIRST_ALPHA = 0.5f

    /** §3.2.6: a row tap replaces the list with the reminder page in one frame 550 ± 17 ms after touch-down. */
    const val ROW_TAP_TO_PAGE_MS = 550L

    // ----------------------------------------------------- R7 §3.3.2: the page header (≡ + title)

    /** §3.3.2: title left 60 ± 1 epx (N1 60.1, N2 59.2). */
    const val HEADER_TITLE_LEFT_EPX = 60f

    /** §3.3.2: title cap 14.1 ± 0.6 epx — the 20-epx type ramp step, i.e. [ShellType.subtitle]. */
    const val HEADER_TITLE_CAP_EPX = 14.1f

    /** §3.3.2: title cap centre 26.3 ± 0.5 epx from the screen top (N1 26.5, N2 26.2). */
    const val HEADER_TITLE_CAP_CENTRE_EPX = 26.3f

    // ------------------------------------------------------------- R7 §3.5: the Reminders list

    /** §3.5.2: group headers at x 11.9 epx in near-white (231,236,230), body class. */
    const val GROUP_HEADER_LEFT_EPX = 11.9f
    val GROUP_HEADER_COLOR = Color(0xFFE7ECE6)

    /** §3.5.2: "Today" cap top 67.4 epx from the screen top — where the list starts. */
    const val FIRST_HEADER_CAP_TOP_EPX = 67.4f

    /** §3.5.2: a header's cap top sits 26.3 ± 0.3 epx above the top of its first row (26.1, 26.5). */
    const val HEADER_CAP_TO_ROW_TOP_EPX = 26.3f

    /** §3.5.2: the bottom of the previous row sits 13.9 epx above the next header's cap top. */
    const val ROW_BOTTOM_TO_HEADER_CAP_EPX = 13.9f

    /** §3.5.3: a one-line row is 60.0 ± 1 epx tall (pressed fill 458.2–518.2). */
    const val ROW_ONE_LINE_EPX = 60f

    /**
     * Derived, not measured (approximation, H24): R7 measures no two-line row's pressed fill. §3.5.2's
     * two anchors around the two-line "Do the dishes" row give its block: 431.7 ("Whenever" cap top)
     * − 13.9 − (326.2 ("Coming up" cap top) + 26.3) = 65.3 epx. Consistent with a 60-epx row whose
     * second 20-epx line pushes the text block past the 32-epx icon box.
     */
    const val ROW_TWO_LINE_EPX = 65.3f

    /** §3.5.3: a 32-epx icon box whose top is 14 epx below the row top. */
    const val ROW_ICON_BOX_EPX = 32f
    const val ROW_ICON_TOP_EPX = 14f

    /** §3.5.3: title left 55.8 epx, cap top 15.8 epx below the row top, near-white (232,237,231). */
    const val ROW_TITLE_LEFT_EPX = 55.8f
    const val ROW_TITLE_CAP_TOP_EPX = 15.8f
    val ROW_TITLE_COLOR = Color(0xFFE8EDE7)

    /** §3.5.3 / §3.5.6: the clock-with-check on timed rows, 31.4 × 31.0 epx at x 11.2. */
    const val CLOCK_ICON_W_EPX = 31.4f
    const val CLOCK_ICON_H_EPX = 31f
    const val CLOCK_ICON_LEFT_EPX = 11.2f

    /** §3.5.3 / §3.5.6: the lightbulb-with-check on Whenever rows, 22 × 32 epx at x 15.2. */
    const val BULB_ICON_W_EPX = 22f
    const val BULB_ICON_H_EPX = 32f
    const val BULB_ICON_LEFT_EPX = 15.2f

    /** §3.5.4: the grey second line, (168,170,167), body class, left 55.9 epx, 20.1 epx below the title. */
    const val SUBLINE_LEFT_EPX = 55.9f
    const val SUBLINE_CAP_PITCH_EPX = 20.1f
    val SUBLINE_COLOR = Color(0xFFA8AAA7)

    /** §3.5.5: the photo sits 20.1 epx below the title's cap top, left 55.4, right margin 12.4, aspect 1.97. */
    const val PHOTO_BELOW_TITLE_CAP_EPX = 20.1f
    const val PHOTO_LEFT_EPX = 55.4f
    const val PHOTO_RIGHT_MARGIN_EPX = 12.4f
    const val PHOTO_ASPECT = 1.97f

    /** §3.5.5: the photo row's grey time line, (158,160,157), cap top 5.4 epx under the photo. */
    const val PHOTO_TIME_CAP_BELOW_PHOTO_EPX = 5.4f
    val PHOTO_TIME_COLOR = Color(0xFF9EA09D)

    /**
     * Derived, not measured (approximation, H24): §3.5.5's pressed fill ends at 312.3 and its time
     * line's cap top is 283.4, so the photo row's block runs 28.9 epx past that cap top.
     */
    const val PHOTO_ROW_BOTTOM_BELOW_TIME_CAP_EPX = 28.9f

    /** §3.5.8: app bar 48.2 ± 1 epx directly above the nav bar, fill (22,27,21), no labels. */
    const val APPBAR_EPX = 48.2f
    val APPBAR_FILL = Color(0xFF161B15)

    /** §3.5.8: buttons at a 68-epx pitch with the "…" button (48 epx) flush right. */
    const val APPBAR_BUTTON_PITCH_EPX = 68f
    const val APPBAR_MORE_EPX = 48f

    /**
     * Approximation (H25): §3.5.8 measures the bar and the button pitch but no glyph size. 20 epx is
     * the shell's own app-bar glyph size and reads at the "…" dots' measured spread.
     */
    const val APPBAR_GLYPH_EPX = 20f

    /**
     * Approximation (H25): "…" raises the app bar to show a label under each button. R7 §3.9.5 never
     * taps it, so both the extra band and the labels themselves are invented; 26 epx fits one caption
     * line (12-epx type, 16-epx line height) plus the shell's usual breathing room.
     */
    const val APPBAR_LABEL_BAND_EPX = 26f

    /**
     * §3.5.8: on the reminder page the dim (disabled) glyphs read 110 and 123 against a bright 225, so
     * a disabled button is drawn at ≈50 % of the enabled glyph's value (approximation, H25).
     */
    const val APPBAR_DISABLED_ALPHA = 0.5f

    /** §3.5.9: the empty list, white subtitle class (cap 14.7 epx), left 11.7 epx, cap top 70.4 epx. */
    const val EMPTY_TEXT = "Select + to add a new reminder"
    const val EMPTY_LEFT_EPX = 11.7f
    const val EMPTY_CAP_TOP_EPX = 70.4f

    /** §3.5.10: a deleted row and a header it empties disappear together in one frame, 333 ± 33 ms after the tap. */
    const val DELETE_REMOVE_MS = 333L

    /**
     * Approximation (H24): §3.2.3 shows the group headers "stacked" during the fade and reflowing to
     * their final places when the rows arrive, but measures no pitch for the stacked state. One body
     * line (20 epx) is the smallest pitch that keeps them legible.
     */
    const val FADE_HEADER_STACK_PITCH_EPX = 20f

    // ------------------------------------------------- R7 §3.6: the reminder long-press menu

    /** §3.6.2: outer 243.3 × 107.0 epx. */
    const val MENU_W_EPX = 243.3f
    const val MENU_H_EPX = 107f

    /** §3.6.2: a 1-epx border (71,76,70) around a (40,40,40) fill. */
    const val MENU_BORDER_EPX = 1f
    val MENU_BORDER_COLOR = Color(0xFF474C46)
    val MENU_FILL = Color(0xFF282828)

    /** §3.6.2: two items at a 44-epx pitch; item text body class, left inset ≈11 epx. */
    const val MENU_ITEM_PITCH_EPX = 44f
    const val MENU_TEXT_INSET_EPX = 11f

    /**
     * §3.6.2 states "8 epx top and bottom padding" but also a 107.0-epx outer height, a 1-epx border and
     * a pressed item 8.6 epx above the bottom edge. 8.5 = (107.0 − 2 × 1 − 2 × 44) / 2 satisfies the
     * outer height and the 8.6 observation together; E15 measures the outer, so the outer wins (H24).
     */
    const val MENU_PAD_EPX = 8.5f

    /** §3.6.2: centred horizontally on the touch point with its bottom edge 23.5 epx above it. */
    const val MENU_BOTTOM_ABOVE_TOUCH_EPX = 23.5f

    /** §3.6.3: a pressed menu item is (79,84,80) on (40,40,40). */
    val MENU_PRESSED_FILL = Color(0xFF4F5450)

    /** §3.6.4: grows upward from a fixed bottom edge, first frame at half height, ease-out over 233 ms. */
    const val MENU_GROW_MS = 233
    const val MENU_FIRST_FRAME_HEIGHT = 0.5f

    /** §3.6.4: the item text rises 6.4 epx over the same 233 ms. */
    const val MENU_TEXT_RISE_EPX = 6.4f

    /** §3.6.5: the row takes the pressed fill 300 ms after touch-down, the menu opens at 700 ms. */
    const val LONG_PRESS_FILL_MS = 300L
    const val LONG_PRESS_MENU_MS = 700L

    /** §3.6.6: after a choice the pressed item shows for 5 frames (≈83 ms), then the menu fades out. */
    const val MENU_CHOICE_HOLD_MS = 83L
    const val MENU_FADE_OUT_MS = 75

    // -------------------------------------------------------------- R7 §3.7: the reminder page

    /** §3.7.2: checkbox 20.6 ± 1 epx square, ≈2-epx light border, left 11.7 epx, top 57.2 epx. */
    const val CHECKBOX_EPX = 20.6f
    const val CHECKBOX_BORDER_EPX = 2f
    const val CHECKBOX_LEFT_EPX = 11.7f
    const val CHECKBOX_TOP_EPX = 57.2f

    /** §3.7.2: the label, body class (cap 11.2 epx), left 40.5 epx. Its cap is centred on the box (H24). */
    const val CHECKBOX_LABEL_LEFT_EPX = 40.5f
    const val CHECKBOX_LABEL_TEXT = "Complete and move to History"

    /** §3.7.1 / §3.7.3: fields 43.4 epx tall at a 53.6-epx vertical pitch. */
    const val FIELD_EPX = 43.4f
    const val FIELD_PITCH_EPX = 53.6f

    /**
     * Derived (approximation, H24): §3.7.3 gives the field TEXT's left (17.6 epx) but not the field
     * box's. 11.2 lines the fields up with §3.7.4's photo margins, the only side margin this page
     * measures, which leaves the text a 6.4-epx inset.
     */
    const val FIELD_LEFT_EPX = 11.2f

    /** §3.7.3: accent-bordered boxes, border (55,73,119) over fill (32,37,33), as captured. */
    val FIELD_BORDER_COLOR = Color(0xFF374977)
    val FIELD_FILL = Color(0xFF202521)

    /** Approximation (H24): R7 gives the border's colour but not its width; 1 epx, as the menu's border. */
    const val FIELD_BORDER_EPX = 1f

    /** §3.7.3: the reminder-text field, top 95.9 epx, width fitted to its text, text cap 16.5 epx at x 17.6. */
    const val TEXT_FIELD_TOP_EPX = 95.9f
    const val FIELD_TEXT_LEFT_EPX = 17.6f
    const val TEXT_FIELD_CAP_EPX = 16.5f

    /** §3.7.3: the time and date fields, top 149.5 epx, 11.3 epx apart. */
    const val TIME_DATE_TOP_EPX = 149.5f
    const val FIELD_GAP_EPX = 11.3f

    /** §3.7.3: the recurrence combo, 32 epx tall at top 204.2 epx, text cap 11.0 epx. */
    const val COMBO_TOP_EPX = 204.2f
    const val COMBO_EPX = 32f

    /** §3.7.4: the attached photo, 11.2-epx side margins, aspect 1.78, top 247.4 epx. */
    const val DETAIL_PHOTO_SIDE_EPX = 11.2f
    const val DETAIL_PHOTO_ASPECT = 1.78f
    const val DETAIL_PHOTO_TOP_EPX = 247.4f

}

// ---------------------------------------------------------------------------------------------
// Cap-top text placement
// ---------------------------------------------------------------------------------------------

/**
 * R7 measures text by the top of a **capital letter**; Compose places a text **layout box**, whose top
 * sits above the cap top by the font's ascent slack plus whatever the declared line height adds. Every
 * text on these pages is therefore positioned by its cap top and the layout-box offset is derived from
 * it — never the other way round.
 *
 * The conversion is [app.tileshell.ui.tokens.CapMetrics], the shell's own Selawik metrics (upem 2048,
 * capHeight 1434, hhea ascent 2027 / descent −431, identical across the five weights). It is not
 * re-derived here.
 *
 * The **horizontal** placement is the layout box, not the ink: R7 reads a label's left edge off the
 * first glyph's ink, which Selawik's left side bearing puts a fraction of an epx to the right of the
 * box. That is below R7's own ±1.1-epx single-edge tolerance, so it is left uncorrected and recorded
 * here rather than hidden.
 */
@Composable
fun CapTopText(
    text: String,
    style: TextStyle,
    capTopEpx: Float,
    leftEpx: Float,
    modifier: Modifier = Modifier,
) {
    val sizeEpx = style.fontSize.value
    BasicText(
        text = text,
        modifier = modifier.offset(
            x = leftEpx.dp,
            y = CapMetrics.topPaddingForCapTop(capTopEpx, sizeEpx).dp,
        ),
        style = style,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * The same placement by the cap's vertical **centre**, which is what R7 §3.3.2 gives for page titles
 * ("cap centre 26.3 epx from the screen top", cap 14.1 on the 20-epx step → cap top 19.25).
 */
@Composable
fun CapCentreText(
    text: String,
    style: TextStyle,
    capCentreEpx: Float,
    leftEpx: Float,
    modifier: Modifier = Modifier,
) {
    CapTopText(text, style, capCentreEpx - CapMetrics.capHeight(style.fontSize.value) / 2f, leftEpx, modifier)
}

// ---------------------------------------------------------------------------------------------
// The shared ≡ header
// ---------------------------------------------------------------------------------------------

/**
 * The header every Cortana destination page carries: the 48 × 48 epx ≡ button (R7 §3.1.7) and,
 * except on the reminder page (§3.7.1, "no title"), the page title in §3.3.2's geometry.
 *
 * The band is [CortanaUi.PANE_HEADER_EPX] tall and filled with the page background so the list
 * scrolling underneath it is occluded.
 */
@Composable
fun CortanaPageHeader(
    title: String?,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    titleTag: String? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(CortanaUi.PANE_HEADER_EPX.dp)
            .background(CortanaUi.PAGE_BG),
    ) {
        MenuButton(onOpenMenu, Modifier.testTag("cortana_header_menu"))
        if (title != null) {
            CapCentreText(
                text = title,
                style = ShellType.subtitle.copy(color = CortanaUi.ROW_TITLE_COLOR),
                capCentreEpx = CortanaUi.HEADER_TITLE_CAP_CENTRE_EPX,
                leftEpx = CortanaUi.HEADER_TITLE_LEFT_EPX,
                modifier = if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
            )
        }
    }
}

/** R7 §3.1.7: a 48 × 48 epx button 4 epx below the screen top with the 16-epx ≡ glyph inside it. */
@Composable
fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressRow(
        onClick,
        modifier.offset(y = CortanaUi.MENU_BUTTON_TOP_EPX.dp).size(CortanaUi.MENU_BUTTON_EPX.dp),
    ) {
        CortanaIcons.Hamburger(
            CortanaUi.ROW_TITLE_COLOR,
            Modifier.offset(x = CortanaUi.MENU_GLYPH_LEFT_EPX.dp).size(
                CortanaUi.MENU_GLYPH_WIDTH_EPX.dp,
                (CortanaUi.MENU_GLYPH_BAR_PITCH_EPX * 2 + CortanaUi.MENU_GLYPH_BAR_EPX).dp,
            ).offset(
                y = (CortanaUi.MENU_BUTTON_EPX / 2f -
                    (CortanaUi.MENU_GLYPH_BAR_PITCH_EPX * 2 + CortanaUi.MENU_GLYPH_BAR_EPX) / 2f).dp,
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------------------------

/**
 * The glyphs these pages need.
 *
 * All of them come from `Glyph`, the branding module's Fluent UI System Icons mapping, so a public
 * build that swaps that module swaps these too: `HOME`, `LIGHTBULB`, `SETTINGS`, `CLOCK`, `LIST`,
 * `ADD`, `MORE_HORIZONTAL`, `CAMERA`, `DELETE`, `SAVE`, `CHEVRON_DOWN`, `LOCATION`, `CHECKMARK`.
 *
 * Two things are drawn here instead, both **approximations**:
 *  - the **≡ bars**. R7 §3.1.7 measures the bars themselves — three 1-epx bars at a 4.2–4.4-epx pitch
 *    across x 16–32 epx — and no font glyph can be held to that, so they are drawn.
 *  - the **check overlay** in [GlyphWithCheck]. R7 §3.5.6 calls the row icons "clock with a check
 *    mark" and "lightbulb with a check mark"; the font has no combined variant. §3.5.3 measures the
 *    composed boxes (31.4 × 31.0 and 22 × 32 epx), but nothing fixes how the two parts sit inside
 *    them, so that composition is invented (covered by H24, no H row of its own).
 */
object CortanaIcons {

    /** Approximation: the check's stroke, as a fraction of its own box's smaller side. */
    private const val CHECK_STROKE = 0.18f

    /** Approximation: how much of the icon box the base glyph takes, leaving room for the check. */
    private const val GLYPH_FRACTION = 0.80f

    /** R7 §3.1.7: three 1-epx bars at a 4.2–4.4-epx pitch, 16 epx wide. */
    @Composable
    fun Hamburger(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
        val bar = CortanaUi.MENU_GLYPH_BAR_EPX * size.width / CortanaUi.MENU_GLYPH_WIDTH_EPX
        val pitch = CortanaUi.MENU_GLYPH_BAR_PITCH_EPX * size.width / CortanaUi.MENU_GLYPH_WIDTH_EPX
        repeat(3) { i -> drawRect(color, Offset(0f, i * pitch), Size(size.width, bar)) }
    }

    /** A branding-module glyph, centred in whatever box [modifier] gives it. */
    @Composable
    fun Font(code: String, color: Color, sizeEpx: Float, modifier: Modifier = Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            BasicText(
                code,
                style = TextStyle(
                    fontFamily = Brand.iconFont,
                    fontSize = sizeEpx.sp,
                    lineHeight = sizeEpx.sp,
                    color = color,
                ),
            )
        }
    }

    /**
     * R7 §3.5.6's "clock with a check mark" / "lightbulb with a check mark": the branding module's
     * glyph with a check drawn over its lower right. [modifier] must carry the composed box's size
     * (R7 §3.5.3's 31.4 × 31.0 or 22 × 32 epx); the split inside it is the approximation.
     */
    @Composable
    fun GlyphWithCheck(code: String, color: Color, modifier: Modifier = Modifier) {
        BoxWithConstraints(modifier) {
            val w = maxWidth.value
            val h = maxHeight.value
            Font(code, color, minOf(w, h) * GLYPH_FRACTION, Modifier.size(w.dp, (h * GLYPH_FRACTION).dp))
            Canvas(
                Modifier
                    .offset(x = (w * 0.44f).dp, y = (h * 0.62f).dp)
                    .size((w * 0.56f).dp, (h * 0.36f).dp),
            ) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.45f)
                    lineTo(size.width * 0.36f, size.height * 0.92f)
                    lineTo(size.width, 0f)
                }
                drawPath(path, color, style = Stroke((size.minDimension * CHECK_STROKE).coerceAtLeast(1f)))
            }
        }
    }
}

/** A rectangle in epx from the top-left of the screen, which is how R7 writes every position. */
@Immutable
data class EpxRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun toComposeRect(): Rect = Rect(left, top, right, bottom)
}

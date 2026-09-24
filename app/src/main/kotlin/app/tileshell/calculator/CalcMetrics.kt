package app.tileshell.calculator

import androidx.compose.ui.graphics.Color
import app.tileshell.ui.tokens.CapMetrics

/**
 * Every value the Calculator draws, each with the docs/plan/r11/calculator.md row it comes from (phase 15 Decisions
 * "Fidelity"; judged against 10586, gap 11). Units are epx (`ShellDensity`: 1.dp == 1 epx). The drawn bars are
 * phase 01's `BarMetrics`, never a literal here. Rows between the header and the nav bar are STAR rows (2.1, 9.1):
 * the weights below are Windows' own and scale with the content height — no absolute y from the 640-epx canvas is
 * copied (9.2). Values r11 left UNMEASURED are marked with their H row.
 */
object CalcMetrics {
    // ---- §1 header ------------------------------------------------------------------------------------------------

    /** 1.2: the header, 48 epx under the status bar; black on the page. */
    const val HEADER = 48f

    /** 1.3: three bars 20 epx long, 1.25 epx thick, 5-epx pitch, x 13.5–33.5; bars at y 43 / 48 / 53 → 19 epx into the header. */
    const val MENU_BAR_LENGTH = 20f
    const val MENU_BAR_THICK = 1.25f
    const val MENU_BAR_PITCH = 5f
    const val MENU_LEFT = 13.5f
    const val MENU_BARS_TOP = 19f
    const val MENU_TOUCH = 48f

    /** 1.5: the title in semibold caps, left 60.5, cap 11.0 (≈15.7-epx semibold), cap centre 26 epx into the header. */
    const val TITLE_LEFT = 60.5f
    const val TITLE_CAP = 11f
    val TITLE_FONT = TITLE_CAP / CapMetrics.CAP_RATIO
    const val TITLE_CAP_CENTRE = 26f

    /** 1.7: the History glyph 16 × 16 epx, its right edge 15 epx from the screen edge, centred on the header. */
    const val HISTORY_GLYPH = 16f
    const val HISTORY_RIGHT_INSET = 15f

    // ---- §2 Standard ----------------------------------------------------------------------------------------------

    /** 2.1: expression 20*, result 72*, memory 32*, number pad 308* (6 equal rows). */
    const val STD_EXPRESSION = 20f
    const val STD_RESULT = 72f
    const val STD_MEMORY = 32f
    const val STD_PAD = 308f
    const val STD_PAD_ROWS = 6

    /** 2.4: the number-pad top rule, 1 epx, #191919. */
    const val RULE = 1f
    val RULE_COLOR = Color(0xFF191919)

    /** 2.6: rows 2–6 flat #1F1F1F; row 1 (2.5) on black; no borders, gaps, operator fill or accent `=`. */
    val PAD_FILL = Color(0xFF1F1F1F)

    /** 2.10: digits ≈29-epx SemiBold (ink 20.25); 2.12: CE / C ≈21-epx (ink 14.75); 2.13: row-1 glyphs 17.5–18.5 of ink. */
    const val DIGIT_FONT = 29f
    const val WORD_FONT = 21f
    const val MATH_FONT = 25f
    const val SYMBOL_FONT = 25f
    const val SQRT_INK = 18.5f

    /** 2.11: `÷ + − =` 19.25 epx wide, `×` 16.75, strokes 1.75; `=` two bars 7.4 epx apart. */
    const val OP_WIDTH = 19.25f
    const val OP_STROKE = 1.75f
    const val MUL_WIDTH = 16.75f
    const val EQ_GAP = 7.4f

    /** 2.12: `⌫` 20.5 × 15.5 epx of ink; the icon font's backspace is ≈0.9 em wide. */
    const val BACKSPACE_FONT = 23f

    /** 2.14: result digits 33 epx tall — S1's 46-epx semibold (7.5) — at a 16-epx right inset, cap top 17.4 epx into the row. */
    const val RESULT_RIGHT_INSET = 16f
    const val RESULT_CAP_TOP = 17.4f

    /** 2.19 (LOW): the expression line small and grey above the result, right-aligned. */
    const val EXPRESSION_FONT = 12f

    /** 2.16: memory labels ≈12.5-epx semibold caps (cap 8.75) on 6 × W/6; 2.17: enabled (155,155,155), disabled (53,53,53). */
    const val MEMORY_FONT = 12.5f
    val MEMORY_ENABLED = Color(0xFF9B9B9B)
    val MEMORY_DISABLED = Color(0xFF353535)

    /** 2.18: M˅ — "M" 9.0 × 8.4 plus a 5 × 2.5 down-triangle at x 10.5–15.5, top-aligned; 15.5 × 8.5 in all. */
    const val MLIST_TRIANGLE_W = 5f
    const val MLIST_TRIANGLE_H = 2.5f
    const val MLIST_TRIANGLE_LEFT = 10.5f
    const val MLIST_WIDTH = 15.5f

    // ---- §3 the hamburger pane ------------------------------------------------------------------------------------

    /** 3.1–3.2: 256 epx wide, #2B2B2B, from the status bar's bottom to the nav bar's top, covering the header. */
    const val PANE_WIDTH = 256f
    val PANE_FILL = Color(0xFF2B2B2B)

    /** 3.4–3.6: 48-epx rows from the header's bottom; labels at x 60, ≈15-epx regular, cap top 19 epx below the row top. */
    const val PANE_ROW = 48f
    const val PANE_LABEL_LEFT = 60f
    const val PANE_LABEL_CAP_TOP = 19f

    /** 3.7: the selected row is the accent at 60 % over the pane fill; the label stays white. */
    const val PANE_SELECTED_ALPHA = 0.6f

    /** 3.11: the bottom rule 1 epx #404040, inset 12 epx, its top 48 epx above the nav bar. */
    val PANE_RULE_COLOR = Color(0xFF404040)
    const val PANE_RULE_INSET = 12f

    /** 3.12: the Settings row's gear 20 × 20 at x 14, centred 24 epx above the nav bar; label at x 60.75. */
    const val PANE_GEAR = 20f
    const val PANE_GEAR_LEFT = 14f
    const val PANE_SETTINGS_LABEL_LEFT = 60.75f

    /** M.1 (LOW, ±50 ms): the pane slides in from the left in ≈167 ms, ease-out. `[motion] calc_pane`. */
    const val PANE_SLIDE_MS = 167

    // ---- §4 Programmer --------------------------------------------------------------------------------------------

    /** 4.1: expression 20*, result 72*, radix panel 96*, tab / memory row 32*, number pad 268* (6 rows). */
    const val PROG_RADIX = 96f
    const val PROG_TABS = 32f
    const val PROG_PAD = 268f

    /** 4.3: radix labels left 12.75, values left 48.25, cap 8.75 (≈12.5 epx); 4.4: unselected values (156,156,156). */
    const val RADIX_LABEL_LEFT = 12.75f
    const val RADIX_VALUE_LEFT = 48.25f
    const val RADIX_FONT = 12.5f
    val RADIX_VALUE = Color(0xFF9C9C9C)

    /** 4.8: full-keypad icon 3 × 4 dots of 2 × 2 epx at a 4-epx pitch; bit toggle 2 × 3 ring dots of 4 epx; grey (156) when not selected. */
    const val TAB_DOT = 2f
    const val TAB_DOT_PITCH = 4f
    const val TAB_RING = 4f
    val TAB_GREY = Color(0xFF9C9C9C)

    /** 4.9: the selected tab's accent underline, 2 epx, one W/6 cell wide. */
    const val TAB_UNDERLINE = 2f

    /** 4.13: Programmer digits and A–F ≈18-epx (ink 13); operators 11.5 epx wide with 1-epx strokes; 4.11: row-1 words ink ≈9 epx. */
    const val PROG_DIGIT_FONT = 18f
    const val PROG_OP_WIDTH = 11.5f
    const val PROG_OP_STROKE = 1f
    const val PROG_WORD_FONT = 12.5f

    /** 4.11: the small ↑ under Lsh / Rsh, 5 × 6 epx, grey (104), 8 epx below the row's centre. */
    const val SHIFT_MARK_W = 5f
    const val SHIFT_MARK_H = 6f
    val SHIFT_MARK = Color(0xFF686868)
    const val SHIFT_MARK_BELOW_CENTRE = 8f

    /** 4.14: a disabled key's glyph (79,79,79). */
    val DISABLED_KEY = Color(0xFF4F4F4F)

    // ---- §5 the Converter -----------------------------------------------------------------------------------------

    /** 5.1: display1 56*, unit1 32*, display2 56*, unit2 32*, "About equal to" Auto (50.2 epx solved), number pad 272* (5 rows). */
    const val CONV_VALUE = 56f
    const val CONV_UNIT = 32f
    const val CONV_AUTO = 50.2f
    const val CONV_PAD = 272f
    const val CONV_PAD_ROWS = 5

    /** 5.2: columns 0.25 : 1 : 1 : 1 : 0.25 of the width. */
    const val CONV_MARGIN_COLUMN = 0.25f

    /** 5.4: converter digits ≈24.5-epx (ink 17.25), `CE` ≈17-epx (ink 11.75), `⌫` 16.5 × 12.5. */
    const val CONV_DIGIT_FONT = 24.5f
    const val CONV_WORD_FONT = 17f
    const val CONV_BACKSPACE_FONT = 18.5f

    /** 5.5–5.6: values ≈34 epx (S1 ValueMediumStyle), left ≈12, bottom-aligned; the active one SemiBold, the other Light. */
    const val CONV_VALUE_FONT = 34f
    const val CONV_VALUE_LEFT = 12f

    /** 5.7: unit labels in accent, body (cap 11.25), left 13.25, a 11.5 × 6.25 chevron after the text. */
    const val CONV_UNIT_LEFT = 13.25f
    const val CONV_CHEVRON_W = 11.5f
    const val CONV_CHEVRON_H = 6.25f

    /** 5.8: "About equal to" grey (156), cap 9.0 (≈12.9 epx), left 12.25, cap top 10.9 epx into the Auto row. */
    val CONV_GREY = Color(0xFF9C9C9C)
    const val CONV_ABOUT_LEFT = 12.25f
    val CONV_ABOUT_FONT = 9f / CapMetrics.CAP_RATIO
    const val CONV_ABOUT_CAP_TOP = 10.9f

    /** 5.9: the supplementary line, cap top 29.2 epx into the Auto row: value white semibold (cap 11) + unit grey, items ≈18 epx apart. */
    const val CONV_RESULTS_CAP_TOP = 29.2f
    val CONV_RESULT_FONT = 11f / CapMetrics.CAP_RATIO
    const val CONV_RESULT_GAP = 18f

    // ---- §7 history, memory and the display fit (UNMEASURED-3, H11) ----------------------------------------------

    /** UNMEASURED-3: entries right-aligned at the result's inset, expression grey 15 epx, result white semibold 24 epx, 64-epx pitch. */
    const val HISTORY_EXPR_FONT = 15f
    const val HISTORY_RESULT_FONT = 24f
    const val HISTORY_PITCH = 64f

    /** UNMEASURED-3 / R7 §3.5.8: the trash glyph in a 48-epx app-bar cell. */
    const val HISTORY_BAR = 48f

    /** 7.3: the empty states (U+2019). */
    const val HISTORY_EMPTY = "There’s no history yet"
    const val MEMORY_EMPTY = "There’s nothing saved in memory"

    /** UNMEASURED-5 (H11): the pressed key — S1's #30FFFFFF over the key, instant, back on release. */
    val PRESSED = Color(0x30FFFFFF)

    // ---- flyouts (R7 §2.2.5, §3.6.2, §3.6.4 — the shell's cross-app flyout) --------------------------------------

    const val FLYOUT_W = 242.6f
    const val FLYOUT_ITEM = 44f
    const val FLYOUT_INSET = 14f
    val FLYOUT_FILL = Color(0xFF282828)
    val FLYOUT_BORDER = Color(0xFF474C46)
    const val FLYOUT_PAD = 8f
    const val FLYOUT_GROW_MS = 233

    // ---- Date calculation (UNMEASURED-4, H19: no W10M capture; Windows' page form) --------------------------------

    /** The page's side inset and its rows (approximations, H19). */
    const val DATE_SIDE = 12f
    const val DATE_ROW = 48f
    const val DATE_FIELD = 40f

    /** The pickers: 7 visible rows of 40 epx per column, the middle one selected (the W10M loop picker's form, H19). */
    const val PICKER_ROW = 40f
    const val PICKER_VISIBLE = 7
    const val PICKER_BAR = 48f
}

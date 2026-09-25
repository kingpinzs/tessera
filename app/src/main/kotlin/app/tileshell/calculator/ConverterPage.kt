package app.tileshell.calculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.convert.ConverterUnit
import app.tileshell.cortana.ui.CapTopText
import app.tileshell.cortana.ui.CortanaIcons
import app.tileshell.recorder.capPad
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/**
 * §5: one Converter page per category. Rows display1 56*, unit1 32*, display2 56*, unit2 32*, "About equal to"
 * (Auto, 50.2 epx), number pad 272* in five rows on 0.25 : 1 : 1 : 1 : 0.25 columns (5.1–5.2). The value being
 * typed is SemiBold, the other Light (5.5–5.6); a tap on the other value makes it the one typed into (Windows'
 * `OnSwitchActive`). Unit labels in accent with a chevron open the unit picker (5.7). Digits, `CE` (`clear`), `⌫`,
 * `.` and, where the category allows negatives, `±` in the bottom row's first cell (5.3, UNMEASURED-6).
 */
@Composable
fun ConverterPage(model: CalcModel, host: CalcPageHost, modifier: Modifier) {
    val tick = model.tick
    val state = model.converter
    Column(modifier) {
        ValueRow(
            text = state.value1,
            active = state.value1Active,
            tag = "calc_converter_value1",
            onTap = { if (!state.value1Active) model.converterSwitchActive() },
            modifier = Modifier.fillMaxWidth().weight(CalcMetrics.CONV_VALUE),
        )
        UnitRow(state.unit1, "calc_converter_unit1", { host.onUnitTap(true) }, Modifier.fillMaxWidth().weight(CalcMetrics.CONV_UNIT))
        ValueRow(
            text = state.value2,
            active = !state.value1Active,
            tag = "calc_converter_value2",
            onTap = { if (state.value1Active) model.converterSwitchActive() },
            modifier = Modifier.fillMaxWidth().weight(CalcMetrics.CONV_VALUE),
        )
        UnitRow(state.unit2, "calc_converter_unit2", { host.onUnitTap(false) }, Modifier.fillMaxWidth().weight(CalcMetrics.CONV_UNIT))
        AboutEqualRow(state.supplementaryResults.map { it.value to it.unit }, Modifier.fillMaxWidth().height(CalcMetrics.CONV_AUTO.dp))
        ConverterPad(model, state.category, Modifier.fillMaxWidth().weight(CalcMetrics.CONV_PAD))
    }
    @Suppress("UNUSED_EXPRESSION") tick
}

/** 5.5–5.6: ≈34 epx, left ≈12, bottom-aligned in its 56* row; SemiBold when it is the one being typed, Light otherwise. */
@Composable
private fun ValueRow(text: String, active: Boolean, tag: String, onTap: () -> Unit, modifier: Modifier) {
    val colors = LocalShellColors.current
    PressRow(onTap, modifier.semantics { selected = active }) {
        BasicText(
            text,
            style = TextStyle(
                fontFamily = Brand.uiFont,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Light,
                fontSize = CalcMetrics.CONV_VALUE_FONT.sp,
                lineHeight = (CalcMetrics.CONV_VALUE_FONT * 1.2f).sp,
                color = colors.text,
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = CalcMetrics.CONV_VALUE_LEFT.dp).testTag(tag),
        )
    }
}

/** 5.7: the unit's name in accent body type at x 13.25 with an 11.5 × 6.25 chevron after it; a tap opens the picker. */
@Composable
private fun UnitRow(unit: ConverterUnit, tag: String, onTap: () -> Unit, modifier: Modifier) {
    val colors = LocalShellColors.current
    PressRow(onTap, modifier.semantics { role = Role.Button }) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = CalcMetrics.CONV_UNIT_LEFT.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(unit.name, style = ShellType.body.copy(color = colors.accent), maxLines = 1, softWrap = false, modifier = Modifier.testTag(tag))
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(CalcMetrics.CONV_CHEVRON_W.dp, CalcMetrics.CONV_CHEVRON_H.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2f, size.height)
                    lineTo(size.width, 0f)
                }
                drawPath(path, colors.accent, style = Stroke(width = 1.25f * density))
            }
        }
    }
}

/**
 * 5.8–5.9: "About equal to" in grey (cap 9) with its cap top 10.9 epx into the row, then one line — value white
 * semibold (cap 11) + unit abbreviation grey, items ≈18 epx apart — that keeps as many items as fit the width, in
 * the engine's order (the design call the doc left open: "fit what fits"). Jets carry the airplane glyph (5.9).
 */
@Composable
private fun AboutEqualRow(items: List<Pair<String, ConverterUnit>>, modifier: Modifier) {
    val colors = LocalShellColors.current
    val aboutStyle = ShellType.caption.copy(fontSize = CalcMetrics.CONV_ABOUT_FONT.sp, lineHeight = (CalcMetrics.CONV_ABOUT_FONT * 1.25f).sp, color = CalcMetrics.CONV_GREY)
    val valueStyle = ShellType.base.copy(fontSize = CalcMetrics.CONV_RESULT_FONT.sp, lineHeight = (CalcMetrics.CONV_RESULT_FONT * 1.25f).sp, color = colors.text)
    val unitStyle = ShellType.body.copy(fontSize = CalcMetrics.CONV_RESULT_FONT.sp, lineHeight = (CalcMetrics.CONV_RESULT_FONT * 1.25f).sp, color = CalcMetrics.CONV_GREY)
    Box(modifier.testTag("calc_converter_about_row")) {
        if (items.isEmpty()) return@Box
        CapTopText("About equal to", aboutStyle, CalcMetrics.CONV_ABOUT_CAP_TOP, CalcMetrics.CONV_ABOUT_LEFT, Modifier.testTag("calc_converter_about"))
        FitRow(
            gapEpx = CalcMetrics.CONV_RESULT_GAP,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = CalcMetrics.CONV_ABOUT_LEFT.dp, y = capPad(CalcMetrics.CONV_RESULTS_CAP_TOP, valueStyle).dp)
                .padding(end = CalcMetrics.CONV_ABOUT_LEFT.dp)
                .clipToBounds(),
        ) {
            items.forEachIndexed { i, (value, unit) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unit.isWhimsical && unit.name.contains("jet")) {
                        CortanaIcons.Font(Glyph.AIRPLANE, colors.text, 16f, Modifier.size(17.25.dp, 16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    BasicText(value, style = valueStyle, maxLines = 1, softWrap = false, modifier = Modifier.testTag("calc_converter_about:${i + 1}"))
                    Spacer(Modifier.width(4.dp))
                    BasicText(unit.abbreviation, style = unitStyle, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Lays children left to right with [gapEpx] between them and drops the first one that would not fit, and every one after it. */
@Composable
fun FitRow(gapEpx: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = (gapEpx * density).toInt()
        val placeables = measurables.map { it.measure(Constraints(maxHeight = constraints.maxHeight)) }
        var x = 0
        val shown = ArrayList<androidx.compose.ui.layout.Placeable>()
        for (p in placeables) {
            val next = if (shown.isEmpty()) p.width else x + gap + p.width
            if (next > constraints.maxWidth) break
            x = next
            shown += p
        }
        val height = shown.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var px = 0
            shown.forEachIndexed { i, p ->
                if (i > 0) px += gap
                p.placeRelative(px, 0)
                px += p.width
            }
        }
    }
}

/**
 * 5.2–5.4: the number pad, #1F1F1F, five equal rows on 0.25 : 1 : 1 : 1 : 0.25 columns: `· CE ⌫` / `7 8 9` /
 * `4 5 6` / `1 2 3` / `± 0 .` (`±` only where negatives are allowed, UNMEASURED-6; the first cell of row 1 is empty).
 */
@Composable
private fun ConverterPad(model: CalcModel, category: ConverterCategory, modifier: Modifier) {
    val colors = LocalShellColors.current
    val style = KeyStyle(
        digitFont = CalcMetrics.CONV_DIGIT_FONT, wordFont = CalcMetrics.CONV_WORD_FONT, mathFont = CalcMetrics.CONV_DIGIT_FONT,
        symbolFont = CalcMetrics.CONV_DIGIT_FONT, opWidth = CalcMetrics.OP_WIDTH, opStroke = CalcMetrics.OP_STROKE,
        mulWidth = CalcMetrics.MUL_WIDTH, backspaceFont = CalcMetrics.CONV_BACKSPACE_FONT, shiftFont = 16f,
    )
    val negate = if (category.supportsNegative) KeySpec("negate", "±", KeyFace.SYMBOL) else null
    val rows: List<List<KeySpec?>> = listOf(
        listOf(null, KeySpec("clear", "CE", KeyFace.TEXT), KeySpec("backspace", "⌫", KeyFace.BACKSPACE)),
        listOf("7", "8", "9").map { KeySpec(it, it, KeyFace.DIGIT) },
        listOf("4", "5", "6").map { KeySpec(it, it, KeyFace.DIGIT) },
        listOf("1", "2", "3").map { KeySpec(it, it, KeyFace.DIGIT) },
        listOf(negate, KeySpec("0", "0", KeyFace.DIGIT), KeySpec("decimal", ".", KeyFace.DIGIT)),
    )
    Column(modifier.background(CalcMetrics.PAD_FILL).testTag("calc_converter_pad")) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Spacer(Modifier.weight(CalcMetrics.CONV_MARGIN_COLUMN))
                row.forEach { spec ->
                    if (spec == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        KeyCell("calc_key:${spec.name}", true, { model.converterPress(spec.name) }, Modifier.weight(1f).fillMaxHeight()) {
                            KeyFaceView(spec.label, spec.face, style, colors.text)
                        }
                    }
                }
                Spacer(Modifier.weight(CalcMetrics.CONV_MARGIN_COLUMN))
            }
        }
    }
}

package app.tileshell.calculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.calc.date.DateCalculationEngine
import app.tileshell.calc.date.DateCalculatorState
import app.tileshell.cortana.ui.CortanaIcons
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale

/** What a date-page field asks its host to pick: a date, or the add / subtract amount. */
sealed interface DatePickerRequest {
    data class Date(val initial: LocalDate, val onPick: (LocalDate) -> Unit) : DatePickerRequest
    data class Amount(val years: Int, val months: Int, val days: Int, val onPick: (Int, Int, Int) -> Unit) : DatePickerRequest
}

/**
 * Date calculation (T15-16; UNMEASURED-4, H19: no W10M capture, so Windows' page form in the shell's type): the
 * operation as radio buttons — "Difference between dates", then "Add" / "Subtract" under "Add or subtract days"
 * (`calc_date_op:<difference|add|subtract>`, the active one `selected`); the From date (`calc_date_from`); the To
 * date (`calc_date_to`) or the years / months / days amount (`calc_date_amount`), each opening its picker; and the
 * result (`calc_date_result`: one line, or the difference's two lines). Every answer is `DateCalculatorState`'s.
 */
@Composable
fun DatePage(model: CalcModel, host: CalcPageHost, modifier: Modifier) {
    val tick = model.tick
    val colors = LocalShellColors.current
    val date = model.date
    val op = model.dateOp
    val difference = op == DateOp.DIFFERENCE
    Column(modifier.padding(horizontal = CalcMetrics.DATE_SIDE.dp).verticalScroll(rememberScrollState()).testTag("calc_date_page")) {
        RadioRow(DateOp.DIFFERENCE.label, "calc_date_op:${DateOp.DIFFERENCE.id}", difference, Modifier.fillMaxWidth().height(CalcMetrics.DATE_ROW.dp)) { model.chooseDateOp(DateOp.DIFFERENCE) }
        Caption("Add or subtract days")
        Row(Modifier.fillMaxWidth().height(CalcMetrics.DATE_FIELD.dp)) {
            RadioRow(DateOp.ADD.label, "calc_date_op:${DateOp.ADD.id}", op == DateOp.ADD, Modifier.weight(1f).fillMaxHeight()) { model.chooseDateOp(DateOp.ADD) }
            RadioRow(DateOp.SUBTRACT.label, "calc_date_op:${DateOp.SUBTRACT.id}", op == DateOp.SUBTRACT, Modifier.weight(1f).fillMaxHeight()) { model.chooseDateOp(DateOp.SUBTRACT) }
        }
        Caption("From")
        val from = if (difference) date.fromDate else date.startDate
        DateField(DateCalculatorState.formatLongDate(from), "calc_date_from") {
            host.onDatePick(DatePickerRequest.Date(from) { model.setDateFrom(it) })
        }
        if (difference) {
            Caption("To")
            DateField(DateCalculatorState.formatLongDate(date.toDate), "calc_date_to") {
                host.onDatePick(DatePickerRequest.Date(date.toDate) { model.setDateTo(it) })
            }
        } else {
            Caption("Years, months, days")
            DateField(amountText(date.yearsOffset, date.monthsOffset, date.daysOffset), "calc_date_amount") {
                host.onDatePick(DatePickerRequest.Amount(date.yearsOffset, date.monthsOffset, date.daysOffset) { y, m, d -> model.setDateAmount(y, m, d) })
            }
        }
        Caption(if (difference) "Difference" else "Date")
        val lines = model.dateResultLines
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = ShellType.subtitle.fontSize, color = colors.text)) { append(lines[0]) }
                if (lines.size > 1) {
                    append('\n')
                    withStyle(SpanStyle(fontSize = ShellType.body.fontSize, color = colors.subtleText)) { append(lines[1]) }
                }
            },
            style = ShellType.subtitle.copy(color = colors.text),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("calc_date_result"),
        )
    }
    @Suppress("UNUSED_EXPRESSION") tick
}

@Composable
private fun Caption(text: String) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.BottomStart) {
        BasicText(text, style = ShellType.caption.copy(color = colors.subtleText), maxLines = 1)
    }
}

/** A W10M radio button: a 20-epx ring with an accent dot when selected, its label in body type. */
@Composable
private fun RadioRow(label: String, tag: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(onClick, modifier.testTag(tag).semantics { role = Role.RadioButton; this.selected = selected }) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(20.dp)) {
                drawCircle(if (selected) colors.accent else colors.text, radius = size.minDimension / 2f - density, style = Stroke(1.5f * density))
                if (selected) drawCircle(colors.accent, radius = size.minDimension * 0.25f)
            }
            Spacer(Modifier.width(10.dp))
            BasicText(label, style = ShellType.body.copy(color = colors.text), maxLines = 1)
        }
    }
}

/** A W10M combo-like field: a bordered 40-epx box holding the text (which carries [tag]) and a chevron; a tap opens the picker. */
@Composable
private fun DateField(text: String, tag: String, onTap: () -> Unit) {
    val colors = LocalShellColors.current
    PressRow(
        onTap,
        Modifier
            .fillMaxWidth()
            .height(CalcMetrics.DATE_FIELD.dp)
            .border(1.dp, colors.text.copy(alpha = 0.6f))
            .semantics { role = Role.Button },
    ) {
        BasicText(
            text,
            style = ShellType.body.copy(color = colors.text),
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(start = 8.dp, end = 28.dp).testTag(tag),
        )
        CortanaIcons.Font(Glyph.CHEVRON_DOWN, colors.text, 12f, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(12.dp))
    }
}

/**
 * The pickers (H19, the W10M loop picker's form): three W/3 columns of 40-epx rows, seven visible with the chosen one
 * in the middle on an accent fill; a tap picks a visible value (`calc_date_pick:<column>:<value>`), a drag scrolls a
 * column, ✓ (`calc_date_pick_ok`) keeps the choice and ✕ (`calc_date_pick_cancel`) drops it. A date's columns are
 * month / day / year (years 1601–2550, Windows' picker range); the amount's years / months / days (0–999).
 */
@Composable
fun DatePickerPanel(request: DatePickerRequest, widthEpx: Float, onDone: () -> Unit) {
    val height = CalcMetrics.PICKER_ROW * CalcMetrics.PICKER_VISIBLE + CalcMetrics.PICKER_BAR
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            .testTag("calc_date_picker_scrim"),
    ) {
        Column(
            Modifier
                .offset(y = CalcMetrics.HEADER.dp)
                .width(widthEpx.dp)
                .height(height.dp)
                .background(CalcMetrics.PAD_FILL)
                .testTag("calc_date_picker"),
        ) {
            when (request) {
                is DatePickerRequest.Date -> {
                    var year by remember { mutableIntStateOf(request.initial.year) }
                    var month by remember { mutableIntStateOf(request.initial.monthValue) }
                    var day by remember { mutableIntStateOf(request.initial.dayOfMonth) }
                    val days = YearMonth.of(year, month).lengthOfMonth()
                    if (day > days) day = days
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        PickerColumn((1..12).toList(), month, { Month.of(it).getDisplayName(java.time.format.TextStyle.FULL, Locale.US) }, "month", Modifier.weight(1f)) { month = it }
                        PickerColumn((1..days).toList(), day, { it.toString() }, "day", Modifier.weight(1f)) { day = it }
                        PickerColumn(
                            (DateCalculationEngine.PICKER_MIN_DATE.year..DateCalculationEngine.PICKER_MAX_DATE.year).toList(),
                            year, { it.toString() }, "year", Modifier.weight(1f),
                        ) { year = it }
                    }
                    PickerBar(onOk = { request.onPick(LocalDate.of(year, month, day)); onDone() }, onCancel = onDone)
                }
                is DatePickerRequest.Amount -> {
                    var years by remember { mutableIntStateOf(request.years) }
                    var months by remember { mutableIntStateOf(request.months) }
                    var days by remember { mutableIntStateOf(request.days) }
                    val range = (0..DateCalculatorState.MAX_OFFSET_VALUE).toList()
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        PickerColumn(range, years, { "$it ${if (it == 1) "year" else "years"}" }, "years", Modifier.weight(1f)) { years = it }
                        PickerColumn(range, months, { "$it ${if (it == 1) "month" else "months"}" }, "months", Modifier.weight(1f)) { months = it }
                        PickerColumn(range, days, { "$it ${if (it == 1) "day" else "days"}" }, "days", Modifier.weight(1f)) { days = it }
                    }
                    PickerBar(onOk = { request.onPick(years, months, days); onDone() }, onCancel = onDone)
                }
            }
        }
    }
}

/** One picker column: seven 40-epx rows around the chosen value; a tap picks, a vertical drag scrolls one row per 40 epx. */
@Composable
private fun PickerColumn(values: List<Int>, selected: Int, label: (Int) -> String, tag: String, modifier: Modifier, onSelect: (Int) -> Unit) {
    val colors = LocalShellColors.current
    val index = values.indexOf(selected).coerceAtLeast(0)
    var drag by remember { mutableFloatStateOf(0f) }
    val half = CalcMetrics.PICKER_VISIBLE / 2
    Column(
        modifier
            .fillMaxHeight()
            .pointerInput(values, index) {
                val step = CalcMetrics.PICKER_ROW * density
                detectVerticalDragGestures(onDragEnd = { drag = 0f }, onDragCancel = { drag = 0f }) { change, dy ->
                    change.consume()
                    drag += dy
                    while (drag <= -step) {
                        drag += step
                        values.getOrNull(values.indexOf(selected) + 1)?.let(onSelect)
                    }
                    while (drag >= step) {
                        drag -= step
                        values.getOrNull(values.indexOf(selected) - 1)?.let(onSelect)
                    }
                }
            }
            .testTag("calc_date_picker_column:$tag"),
    ) {
        for (k in -half..half) {
            val value = values.getOrNull(index + k)
            val centre = k == 0
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CalcMetrics.PICKER_ROW.dp)
                    .background(if (centre) colors.accent else Color.Transparent)
                    .let { m ->
                        if (value != null) m
                            .pointerInput(value) {
                                awaitEachGesture {
                                    awaitFirstDown()
                                    val up = waitForUpOrCancellation()
                                    if (up != null) onSelect(value)
                                }
                            }
                            .testTag("calc_date_pick:$tag:$value")
                            .semantics { role = Role.Button; this.selected = centre }
                        else m
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (value != null) {
                    BasicText(
                        label(value),
                        style = ShellType.body.copy(color = if (centre) Color.White else colors.text.copy(alpha = 0.7f)),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** The picker's app bar: ✓ keeps, ✕ drops, 48-epx cells at R7 §3.5.8's 68-epx pitch, flush right. */
@Composable
private fun PickerBar(onOk: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(CalcMetrics.PICKER_BAR.dp).background(Color.Black)) {
        Row(Modifier.align(Alignment.CenterEnd)) {
            KeyCell("calc_date_pick_ok", true, onOk, Modifier.size(48.dp)) { CortanaIcons.Font(Glyph.CHECKMARK, colors.text, 20f, Modifier.size(20.dp)) }
            Spacer(Modifier.width(20.dp))
            KeyCell("calc_date_pick_cancel", true, onCancel, Modifier.size(48.dp)) { CortanaIcons.Font(Glyph.DISMISS, colors.text, 20f, Modifier.size(20.dp)) }
        }
    }
}

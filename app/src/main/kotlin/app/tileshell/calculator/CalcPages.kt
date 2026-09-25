package app.tileshell.calculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calc.engine.CalcMode
import app.tileshell.calc.engine.Radix
import app.tileshell.cortana.ui.CortanaIcons
import app.tileshell.start.Edit
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.withTimeoutOrNull

/** The glyph sizes of a keypad: Standard and Scientific share 2.10–2.13; Programmer is 4.11 / 4.13. */
class KeyStyle(
    val digitFont: Float,
    val wordFont: Float,
    val mathFont: Float,
    val symbolFont: Float,
    val opWidth: Float,
    val opStroke: Float,
    val mulWidth: Float,
    val backspaceFont: Float,
    val shiftFont: Float,
) {
    companion object {
        val STANDARD = KeyStyle(
            CalcMetrics.DIGIT_FONT, CalcMetrics.WORD_FONT, CalcMetrics.MATH_FONT, CalcMetrics.SYMBOL_FONT,
            CalcMetrics.OP_WIDTH, CalcMetrics.OP_STROKE, CalcMetrics.MUL_WIDTH, CalcMetrics.BACKSPACE_FONT, 20f,
        )
        val PROGRAMMER = KeyStyle(
            CalcMetrics.PROG_DIGIT_FONT, 13f, CalcMetrics.PROG_DIGIT_FONT, CalcMetrics.PROG_DIGIT_FONT,
            CalcMetrics.PROG_OP_WIDTH, CalcMetrics.PROG_OP_STROKE, CalcMetrics.PROG_OP_WIDTH * CalcMetrics.MUL_WIDTH / CalcMetrics.OP_WIDTH, 15f, 14f,
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Standard and Scientific (§2, §6 / UNMEASURED-2)
// ---------------------------------------------------------------------------------------------------------------

/**
 * Standard: expression 20*, result 72*, memory 32*, number pad 308* in six W/4 rows (2.1–2.8). Scientific
 * (UNMEASURED-2): expression 20*, result 72*, the angle row 32*, memory 32*, number pad 276* in seven W/5 rows, its
 * first two rows on black. ONE engine behind both — `model.calc`, switched by mode.
 */
@Composable
fun StandardScientificPage(model: CalcModel, host: CalcPageHost, modifier: Modifier) {
    val tick = model.tick
    val calc = model.calc
    val scientific = calc.mode == CalcMode.SCIENTIFIC
    Column(modifier) {
        ExpressionLine(calc.expression, Modifier.fillMaxWidth().weight(CalcMetrics.STD_EXPRESSION))
        ResultDisplay(calc.displayText, host, Modifier.fillMaxWidth().weight(CalcMetrics.STD_RESULT))
        if (scientific) AngleRow(model, Modifier.fillMaxWidth().weight(CalcMetrics.STD_MEMORY))
        MemoryRow(model, Modifier.fillMaxWidth().weight(CalcMetrics.STD_MEMORY))
        Keypad(
            model = model,
            mode = calc.mode,
            style = KeyStyle.STANDARD,
            modifier = Modifier.fillMaxWidth().weight(if (scientific) 276f else CalcMetrics.STD_PAD),
        ) {
            if (model.historyOpen) HistoryPane(model, Modifier.fillMaxSize())
            if (model.memoryOpen) MemoryPane(model, Modifier.fillMaxSize())
        }
    }
    // The read of tick above is what recomposes this page after every key; nothing else uses the value.
    @Suppress("UNUSED_EXPRESSION") tick
}

/** 2.19 (LOW): the expression line, small and grey, right-aligned above the result at the result's inset. */
@Composable
private fun ExpressionLine(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        BasicText(
            text,
            style = ShellType.caption.copy(fontSize = CalcMetrics.EXPRESSION_FONT.sp, color = CalcMetrics.MEMORY_ENABLED, textAlign = TextAlign.End),
            maxLines = 1,
            modifier = Modifier.padding(end = CalcMetrics.RESULT_RIGHT_INSET.dp).testTag("calc_expr"),
        )
    }
}

/**
 * 2.14 / 7.5: the result in semibold with 33.0 epx of digit INK — the size the shell's font needs for r11's "5,512"
 * (S1's 46 is Segoe's) — the ink's right edge at a 16-epx inset and the digit top 17.4 epx into the row, shrinking to
 * fit the row's width down to 12 epx (H11) — never scrolling or truncating. The tagged node IS the ink box (r11's
 * measurement frame). A hold offers Paste (`calc_paste`, T15-57).
 */
@Composable
fun ResultDisplay(text: String, host: CalcPageHost, modifier: Modifier) {
    val colors = LocalShellColors.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val density = LocalDensity.current
    var bottomPx by remember { mutableStateOf(0f) }
    var centreXPx by remember { mutableStateOf(0f) }
    val maxFont = rememberInkFontSize(ShellType.base.fontFamily, FontWeight.SemiBold, CalcMetrics.RESULT_INK_REFERENCE, CalcMetrics.RESULT_INK, colors.text)
    BoxWithConstraints(
        modifier
            .onGloballyPositioned { bottomPx = it.boundsInWindow().bottom; centreXPx = it.boundsInWindow().center.x }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var cancelled = false
                    val up = withTimeoutOrNull(Edit.HOLD_MS) { waitForUpOrCancellation().also { if (it == null) cancelled = true } }
                    // Still down after the shell's hold threshold: the paste flyout.
                    if (up == null && !cancelled) host.onDisplayHold(bottomPx, centreXPx)
                }
            },
    ) {
        val available = maxWidth.value - CalcMetrics.RESULT_RIGHT_INSET - 4f
        val base = ShellType.base.copy(fontSize = maxFont.sp, lineHeight = (maxFont * 1.25f).sp, color = colors.text)
        val widthAtMax = remember(text, density, base) { with(density) { measurer.measure(AnnotatedString(text), base, maxLines = 1, softWrap = false).size.width.toDp().value } }
        val size = CalcDisplayFit.fontSize(widthAtMax, available, maxFont)
        val style = base.copy(fontSize = size.sp, lineHeight = (size * 1.25f).sp)
        // The node is the ink: "5,512"'s rows at this size (one baseline for every result) and the text's own columns,
        // so the node's right edge is the digits' right edge at the inset and its top the digit top 17.4 epx into the row.
        val reference = rememberInkLayout(style, CalcMetrics.RESULT_INK_REFERENCE)
        val own = rememberInkLayout(style, text)
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = CalcMetrics.RESULT_RIGHT_INSET.dp, top = CalcMetrics.RESULT_CAP_TOP.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            BasicText(text, style = style, maxLines = 1, softWrap = false, modifier = Modifier.testTag("calc_display").inkBox(reference, own.ink))
        }
    }
}

/** 2.16–2.18: `MC MR M+ M- MS M˅` on 6 × W/6, ≈12.5-epx semibold caps, enabled (155,155,155), disabled (53,53,53). */
@Composable
fun MemoryRow(model: CalcModel, modifier: Modifier) {
    Row(modifier) {
        CalcLayout.MEMORY_ROW.forEach { (name, label) ->
            val enabled = model.calc.isEnabled(name)
            LabelKey(
                label = label,
                tag = "calc_key:$name",
                color = if (enabled) CalcMetrics.MEMORY_ENABLED else CalcMetrics.MEMORY_DISABLED,
                enabled = enabled,
                onPress = { model.press(name) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        MemoryListKey(model, Modifier.weight(1f).fillMaxHeight())
    }
}

/** M˅ (2.18): "M" plus a 5 × 2.5-epx down-triangle at x 10.5–15.5, top-aligned; opens the memory flyout (`mlist`). */
@Composable
fun MemoryListKey(model: CalcModel, modifier: Modifier) {
    val enabled = !model.calc.isError
    val color = if (enabled) CalcMetrics.MEMORY_ENABLED else CalcMetrics.MEMORY_DISABLED
    KeyCell(
        tag = "calc_key:${CalcLayout.MEMORY_LIST_KEY}",
        enabled = enabled,
        selected = model.memoryOpen,
        onPress = {
            if (!model.calc.isError) {
                model.historyOpen = false
                model.memoryOpen = !model.memoryOpen
            }
        },
        modifier = modifier,
    ) {
        Box(Modifier.width(CalcMetrics.MLIST_WIDTH.dp).height(9.dp)) {
            BasicText("M", style = memoryStyle(color), maxLines = 1, modifier = Modifier.offset(y = (-2).dp))
            Canvas(
                Modifier
                    .offset(x = CalcMetrics.MLIST_TRIANGLE_LEFT.dp)
                    .size(CalcMetrics.MLIST_TRIANGLE_W.dp, CalcMetrics.MLIST_TRIANGLE_H.dp),
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

private fun memoryStyle(color: Color) = ShellType.base.copy(fontSize = CalcMetrics.MEMORY_FONT.sp, lineHeight = CalcMetrics.MEMORY_FONT.sp, color = color)

/** UNMEASURED-2: DEG / HYP / F-E as memory-row-style labels on a W/5 grid, the active angle unit and toggles in accent. */
@Composable
private fun AngleRow(model: CalcModel, modifier: Modifier) {
    val colors = LocalShellColors.current
    val calc = model.calc
    Row(modifier) {
        CalcLayout.ANGLE_ROW.forEach { (name, defaultLabel) ->
            val enabled = calc.isEnabled(name)
            val on = when (name) {
                "angle" -> true
                "hyp" -> calc.hyp
                else -> calc.fe
            }
            val label = if (name == "angle") calc.angleUnit.name else defaultLabel
            LabelKey(
                label = label,
                tag = "calc_key:$name",
                color = when {
                    !enabled -> CalcMetrics.MEMORY_DISABLED
                    on -> colors.accent
                    else -> CalcMetrics.MEMORY_ENABLED
                },
                enabled = enabled,
                onPress = { model.press(name) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                selected = on,
            )
        }
        Spacer(Modifier.weight(2f))
    }
}

/** A memory-row-style label cell (2.16): ≈12.5-epx semibold caps centred in its cell. */
@Composable
private fun LabelKey(label: String, tag: String, color: Color, enabled: Boolean, onPress: () -> Unit, modifier: Modifier, selected: Boolean = false) {
    KeyCell(tag, enabled, onPress, modifier, selected) {
        BasicText(label, style = memoryStyle(color), maxLines = 1, softWrap = false)
    }
}

/**
 * The keypad (2.4–2.8): the 1-epx #191919 rule on its top line, [CalcLayout.blackRows] rows on black, the rest a flat
 * #1F1F1F with no borders, gaps or per-key fills; W/n columns. [overlay] draws the history pane or the memory
 * flyout over it (UNMEASURED-3: they cover the number pad).
 */
@Composable
fun Keypad(model: CalcModel, mode: CalcMode, style: KeyStyle, modifier: Modifier, overlay: @Composable BoxScope.() -> Unit) {
    val rows = CalcLayout.rows(mode)
    val black = CalcLayout.blackRows(mode)
    Box(modifier.testTag("calc_keypad")) {
        Column(Modifier.fillMaxSize()) {
            rows.forEachIndexed { r, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(if (r < black) Color.Transparent else CalcMetrics.PAD_FILL),
                ) {
                    row.forEach { spec -> KeyButton(model, spec, style, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(CalcMetrics.RULE.dp).background(CalcMetrics.RULE_COLOR))
        overlay()
    }
}

/**
 * One key: its face centred in a W/n cell, `calc_key:<name>`, the pressed brush #30FFFFFF while the finger is
 * down (UNMEASURED-5), disabled glyphs (79,79,79) (4.14). Keys with a second function show it while ↑ is on and
 * carry a small grey ↑ mark under their label while it is off (4.11, UNMEASURED-8).
 */
@Composable
fun KeyButton(model: CalcModel, spec: KeySpec, style: KeyStyle, modifier: Modifier) {
    val colors = LocalShellColors.current
    val calc = model.calc
    val enabled = calc.isEnabled(spec.name)
    val inv = calc.inv
    val hyp = calc.hyp
    val color = when {
        !enabled -> CalcMetrics.DISABLED_KEY
        spec.face == KeyFace.SHIFT && inv -> colors.accent
        else -> colors.text
    }
    val secondOn = inv && spec.second != null
    val label = when {
        hyp && spec.name in HYP_KEYS -> (if (inv) "${spec.label}h⁻¹" else "${spec.label}h")
        secondOn -> spec.second
        else -> spec.label
    }
    val face = if (secondOn && spec.face == KeyFace.SQRT) KeyFace.MATH else spec.face
    KeyCell(
        tag = "calc_key:${spec.name}",
        enabled = enabled,
        onPress = { model.press(spec.name) },
        modifier = modifier,
        selected = spec.face == KeyFace.SHIFT && inv,
    ) {
        Box(contentAlignment = Alignment.Center) {
            KeyFaceView(label, face, style, color)
            if (spec.second != null && !inv && !(hyp && spec.name in HYP_KEYS)) {
                ShiftMark(Modifier.align(Alignment.Center).offset(y = CalcMetrics.SHIFT_MARK_BELOW_CENTRE.dp))
            }
        }
    }
}

private val HYP_KEYS = setOf("sin", "cos", "tan")

/** 4.11: the small grey ↑ (5 × 6 epx, grey 104) under a key that has a second function. */
@Composable
private fun ShiftMark(modifier: Modifier) {
    Canvas(modifier.size(CalcMetrics.SHIFT_MARK_W.dp, CalcMetrics.SHIFT_MARK_H.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height * 0.5f)
            lineTo(size.width * 0.68f, size.height * 0.5f)
            lineTo(size.width * 0.68f, size.height)
            lineTo(size.width * 0.32f, size.height)
            lineTo(size.width * 0.32f, size.height * 0.5f)
            lineTo(0f, size.height * 0.5f)
            close()
        }
        drawPath(path, CalcMetrics.SHIFT_MARK)
    }
}

/** A cell that reports a press on release, with the instant pressed brush while down; disabled cells take no tap. */
@Composable
fun KeyCell(tag: String, enabled: Boolean, onPress: () -> Unit, modifier: Modifier, selected: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val press by rememberUpdatedState(onPress)
    // Every completed tap reaches [onPress], which decides against the state as it is NOW (the engine refuses a
    // disabled key in `Calculator.press`). [enabled] is what the last frame drew, and a tap landing before the next
    // frame would be judged on it: E11 run 2 lost MR's tap 15 ms after M- had enabled it (qa/phase-15/E11-run2).
    Box(
        modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (up != null) press()
                }
            }
            .background(if (pressed && enabled) CalcMetrics.PRESSED else Color.Transparent)
            .testTag(tag)
            .semantics {
                role = Role.Button
                this.selected = selected
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Draws one key face at the style's sizes. */
@Composable
fun KeyFaceView(label: String, face: KeyFace, style: KeyStyle, color: Color) {
    when (face) {
        KeyFace.DIGIT -> BasicText(label, style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.SemiBold, fontSize = style.digitFont.sp, color = color), maxLines = 1, softWrap = false)
        KeyFace.TEXT -> BasicText(styledLabel(label, italic = false), style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.Normal, fontSize = style.wordFont.sp, color = color), maxLines = 1, softWrap = false)
        KeyFace.MATH -> BasicText(styledLabel(label, italic = true), style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.Normal, fontSize = style.mathFont.sp, color = color), maxLines = 1, softWrap = false)
        KeyFace.SYMBOL -> BasicText(label, style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.Normal, fontSize = style.symbolFont.sp, color = color), maxLines = 1, softWrap = false)
        KeyFace.BACKSPACE -> CortanaIcons.Font(Glyph.BACKSPACE, color, style.backspaceFont, Modifier.size(style.backspaceFont.dp))
        KeyFace.SHIFT -> CortanaIcons.Font(Glyph.ARROW_UP, color, style.shiftFont, Modifier.size(style.shiftFont.dp))
        KeyFace.SQRT -> SqrtGlyph(color, style.opWidth, style.opStroke)
        KeyFace.ADD, KeyFace.SUBTRACT, KeyFace.MULTIPLY, KeyFace.DIVIDE, KeyFace.EQUALS -> OperatorGlyph(face, color, style)
    }
}

/**
 * Gap 10: the math labels as Selawik text (`Brand.uiFont`), superscripts raised and reduced, the variables italic
 * (2.13: "italic serif math glyphs"). "¹⁄x" is set with a plain slash so every glyph is in the font.
 */
fun styledLabel(label: String, italic: Boolean): AnnotatedString = buildAnnotatedString {
    for (ch in label) {
        val sup = SUPERSCRIPTS[ch]
        when {
            sup != null -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.62.em)) { append(sup) }
            ch == '⁄' -> append('/')
            italic && ch.isLetter() && ch != 'π' -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(ch) }
            else -> append(ch)
        }
    }
}

private val SUPERSCRIPTS = mapOf('²' to "2", '³' to "3", '¹' to "1", 'ʸ' to "y", 'ˣ' to "x", '⁻' to "−")

/** 2.11: `+ − × ÷ =` drawn — 19.25 epx wide with 1.75-epx strokes (`×` 16.75; `=` bars 7.4 apart); 4.13 for Programmer. */
@Composable
fun OperatorGlyph(face: KeyFace, color: Color, style: KeyStyle) {
    val w = if (face == KeyFace.MULTIPLY) style.mulWidth else style.opWidth
    Canvas(Modifier.size(w.dp)) {
        val s = style.opStroke * density
        val c = size.width / 2f
        val cap = StrokeCap.Butt
        when (face) {
            KeyFace.ADD -> {
                drawLine(color, Offset(0f, c), Offset(size.width, c), s, cap)
                drawLine(color, Offset(c, 0f), Offset(c, size.height), s, cap)
            }
            KeyFace.SUBTRACT -> drawLine(color, Offset(0f, c), Offset(size.width, c), s, cap)
            KeyFace.MULTIPLY -> {
                drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), s, cap)
                drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), s, cap)
            }
            KeyFace.DIVIDE -> {
                drawLine(color, Offset(0f, c), Offset(size.width, c), s, cap)
                val r = s * 0.9f
                drawCircle(color, r, Offset(c, c - size.height * 0.28f))
                drawCircle(color, r, Offset(c, c + size.height * 0.28f))
            }
            KeyFace.EQUALS -> {
                val gap = (style.opStroke * 0f + CalcMetrics.EQ_GAP * (w / CalcMetrics.OP_WIDTH)) * density
                drawLine(color, Offset(0f, c - gap / 2f), Offset(size.width, c - gap / 2f), s, cap)
                drawLine(color, Offset(0f, c + gap / 2f), Offset(size.width, c + gap / 2f), s, cap)
            }
            else -> Unit
        }
    }
}

/** 2.13: √ drawn with 18.5 epx of ink: the tick and the overbar. */
@Composable
fun SqrtGlyph(color: Color, widthEpx: Float, strokeEpx: Float) {
    val h = CalcMetrics.SQRT_INK * widthEpx / CalcMetrics.OP_WIDTH
    Canvas(Modifier.size(widthEpx.dp, h.dp)) {
        val s = strokeEpx * density
        val path = Path().apply {
            moveTo(0f, size.height * 0.55f)
            lineTo(size.width * 0.22f, size.height - s)
            lineTo(size.width * 0.45f, s / 2f)
            lineTo(size.width, s / 2f)
        }
        drawPath(path, color, style = Stroke(width = s))
    }
}

// ---------------------------------------------------------------------------------------------------------------
// History pane and memory flyout (UNMEASURED-3, H11)
// ---------------------------------------------------------------------------------------------------------------

/**
 * The history pane over the number pad (7.2): entries newest first, right-aligned at the result's inset, the
 * expression small grey over the result white semibold, 64-epx pitch; a trash cell at the bottom right clears it
 * (`calc_history_clear`). `calc_history:<n>` is the entry's expression (E12), 1 = the newest.
 */
@Composable
fun HistoryPane(model: CalcModel, modifier: Modifier) {
    val colors = LocalShellColors.current
    val items = model.calc.history
    Column(modifier.background(colors.background).pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } }.testTag("calc_history_pane")) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CalcMetrics.RESULT_RIGHT_INSET.dp, vertical = 12.dp), contentAlignment = Alignment.TopEnd) {
                BasicText(CalcMetrics.HISTORY_EMPTY, style = ShellType.body.copy(color = CalcMetrics.MEMORY_ENABLED), modifier = Modifier.testTag("calc_history_empty"))
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("calc_history_list")) {
                itemsIndexed(items) { i, item ->
                    val n = i + 1
                    PressRow(
                        onClick = { model.recallHistory(i); model.historyOpen = false },
                        modifier = Modifier.fillMaxWidth().height(CalcMetrics.HISTORY_PITCH.dp).testTag("calc_history_row:$n"),
                    ) {
                        Column(Modifier.align(Alignment.CenterEnd).padding(end = CalcMetrics.RESULT_RIGHT_INSET.dp), horizontalAlignment = Alignment.End) {
                            BasicText(item.expression, style = ShellType.body.copy(fontSize = CalcMetrics.HISTORY_EXPR_FONT.sp, color = CalcMetrics.MEMORY_ENABLED), maxLines = 1, modifier = Modifier.testTag("calc_history:$n"))
                            BasicText(item.result, style = ShellType.base.copy(fontSize = CalcMetrics.HISTORY_RESULT_FONT.sp, lineHeight = (CalcMetrics.HISTORY_RESULT_FONT * 1.25f).sp, color = colors.text), maxLines = 1, modifier = Modifier.testTag("calc_history_result:$n"))
                        }
                    }
                }
            }
        }
        TrashBar(tag = "calc_history_clear", enabled = items.isNotEmpty(), onClick = { model.clearHistory() })
    }
}

/** The memory flyout over the number pad: values newest first (MR on tap), a trash cell clearing all (`calc_memory_clear`). */
@Composable
fun MemoryPane(model: CalcModel, modifier: Modifier) {
    val colors = LocalShellColors.current
    val items = model.calc.memory
    Column(modifier.background(colors.background).pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } }.testTag("calc_memory_pane")) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = CalcMetrics.RESULT_RIGHT_INSET.dp, vertical = 12.dp), contentAlignment = Alignment.TopEnd) {
                BasicText(CalcMetrics.MEMORY_EMPTY, style = ShellType.body.copy(color = CalcMetrics.MEMORY_ENABLED), modifier = Modifier.testTag("calc_memory_empty"))
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("calc_memory_list")) {
                itemsIndexed(items) { i, value ->
                    val n = i + 1
                    PressRow(
                        onClick = { model.memoryRecall(i); model.memoryOpen = false },
                        modifier = Modifier.fillMaxWidth().height(CalcMetrics.HISTORY_PITCH.dp).testTag("calc_memory_row:$n"),
                    ) {
                        BasicText(
                            value,
                            style = ShellType.base.copy(fontSize = CalcMetrics.HISTORY_RESULT_FONT.sp, lineHeight = (CalcMetrics.HISTORY_RESULT_FONT * 1.25f).sp, color = colors.text),
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = CalcMetrics.RESULT_RIGHT_INSET.dp).testTag("calc_memory:$n"),
                        )
                    }
                }
            }
        }
        TrashBar(tag = "calc_memory_clear", enabled = items.isNotEmpty(), onClick = { model.memoryClearAll() })
    }
}

/** UNMEASURED-3 / R7 §3.5.8: the Delete glyph in a 48-epx app-bar cell, flush right. */
@Composable
private fun TrashBar(tag: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(CalcMetrics.HISTORY_BAR.dp)) {
        KeyCell(tag, enabled, onClick, Modifier.align(Alignment.CenterEnd).size(CalcMetrics.HISTORY_BAR.dp)) {
            CortanaIcons.Font(Glyph.DELETE, if (enabled) colors.text else CalcMetrics.DISABLED_KEY, 20f, Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Programmer (§4)
// ---------------------------------------------------------------------------------------------------------------

/**
 * Programmer: expression 20*, result 72*, the radix panel 96*, the tab / memory row 32*, number pad 268* in six W/6
 * rows (4.1); no history (4.15). The radix rows (4.3–4.5) select their radix on a tap; the tab row (4.7–4.9) holds
 * the full-keypad and bit-toggle tabs, the word-size button in accent, MS and M˅.
 */
@Composable
fun ProgrammerPage(model: CalcModel, host: CalcPageHost, modifier: Modifier) {
    val tick = model.tick
    val colors = LocalShellColors.current
    val calc = model.calc
    Column(modifier) {
        ExpressionLine(calc.expression, Modifier.fillMaxWidth().weight(CalcMetrics.STD_EXPRESSION))
        ResultDisplay(calc.displayText, host, Modifier.fillMaxWidth().weight(CalcMetrics.STD_RESULT))
        Box(Modifier.fillMaxWidth().weight(CalcMetrics.PROG_RADIX)) {
            Column(Modifier.fillMaxSize()) {
                CalcLayout.RADIX_ROWS.forEach { (name, label) ->
                    val radix = Radix.values().first { it.key == name }
                    val selected = calc.radix == radix
                    KeyCell(
                        tag = "calc_key:$name",
                        enabled = calc.isEnabled(name),
                        onPress = { model.press(name) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        selected = selected,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            val style = memoryStyle(if (selected) colors.accent else colors.text)
                            BasicText(label, style = style, maxLines = 1, modifier = Modifier.align(Alignment.CenterStart).offset(x = CalcMetrics.RADIX_LABEL_LEFT.dp))
                            BasicText(
                                calc.radixValue(radix),
                                style = memoryStyle(if (selected) colors.accent else CalcMetrics.RADIX_VALUE),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.align(Alignment.CenterStart).offset(x = CalcMetrics.RADIX_VALUE_LEFT.dp).testTag("calc_radix:${radix.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
            // 4.6: the rule above the tab row is the radix panel's last line.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(CalcMetrics.RULE.dp).background(CalcMetrics.RULE_COLOR))
        }
        ProgrammerTabRow(model, Modifier.fillMaxWidth().weight(CalcMetrics.PROG_TABS))
        when (model.programmerTab) {
            ProgrammerTab.KEYPAD -> Keypad(model, CalcMode.PROGRAMMER, KeyStyle.PROGRAMMER, Modifier.fillMaxWidth().weight(CalcMetrics.PROG_PAD)) {
                if (model.memoryOpen) MemoryPane(model, Modifier.fillMaxSize())
            }
            ProgrammerTab.BITS -> Box(Modifier.fillMaxWidth().weight(CalcMetrics.PROG_PAD)) {
                BitPanel(model, Modifier.fillMaxSize())
                Box(Modifier.fillMaxWidth().height(CalcMetrics.RULE.dp).background(CalcMetrics.RULE_COLOR))
                if (model.memoryOpen) MemoryPane(model, Modifier.fillMaxSize())
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick
}

/** 4.7–4.9: on W/6 — the full-keypad tab, the bit-toggle tab, "QWORD" in accent over two cells, MS and M˅; the selected tab underlined in accent. */
@Composable
private fun ProgrammerTabRow(model: CalcModel, modifier: Modifier) {
    val colors = LocalShellColors.current
    val calc = model.calc
    Row(modifier) {
        TabCell(
            tag = "calc_prog_tab:keypad",
            selected = model.programmerTab == ProgrammerTab.KEYPAD,
            onPress = { model.programmerTab = ProgrammerTab.KEYPAD },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { on -> KeypadDots(if (on) colors.accent else CalcMetrics.TAB_GREY) }
        TabCell(
            tag = "calc_prog_tab:bits",
            selected = model.programmerTab == ProgrammerTab.BITS,
            onPress = { model.programmerTab = ProgrammerTab.BITS },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) { on -> BitDots(if (on) colors.accent else CalcMetrics.TAB_GREY) }
        LabelKey(
            label = calc.wordSize.name,
            tag = "calc_key:word",
            color = if (calc.isEnabled("word")) colors.accent else CalcMetrics.MEMORY_DISABLED,
            enabled = calc.isEnabled("word"),
            onPress = { model.press("word") },
            modifier = Modifier.weight(2f).fillMaxHeight(),
        )
        val msEnabled = calc.isEnabled("ms")
        LabelKey(
            label = "MS",
            tag = "calc_key:ms",
            color = if (msEnabled) CalcMetrics.MEMORY_ENABLED else CalcMetrics.MEMORY_DISABLED,
            enabled = msEnabled,
            onPress = { model.press("ms") },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MemoryListKey(model, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun TabCell(tag: String, selected: Boolean, onPress: () -> Unit, modifier: Modifier, icon: @Composable (Boolean) -> Unit) {
    val colors = LocalShellColors.current
    Box(modifier) {
        KeyCell(tag, true, onPress, Modifier.fillMaxSize(), selected) { icon(selected) }
        if (selected) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(CalcMetrics.TAB_UNDERLINE.dp).background(colors.accent))
    }
}

/** 4.8: the full-keypad icon — 3 × 4 dots of 2 × 2 epx at a 4-epx pitch. */
@Composable
private fun KeypadDots(color: Color) {
    val w = CalcMetrics.TAB_DOT_PITCH * 2 + CalcMetrics.TAB_DOT
    val h = CalcMetrics.TAB_DOT_PITCH * 3 + CalcMetrics.TAB_DOT
    Canvas(Modifier.size(w.dp, h.dp)) {
        val d = CalcMetrics.TAB_DOT * density
        val p = CalcMetrics.TAB_DOT_PITCH * density
        for (r in 0 until 4) for (c in 0 until 3) drawRect(color, Offset(c * p, r * p), Size(d, d))
    }
}

/** 4.8: the bit-toggle icon — 2 × 3 ring dots of 4 epx. */
@Composable
private fun BitDots(color: Color) {
    val pitch = 6f
    Canvas(Modifier.size((pitch * 2 + CalcMetrics.TAB_RING).dp, (pitch + CalcMetrics.TAB_RING).dp)) {
        val r = CalcMetrics.TAB_RING * density / 2f
        val p = pitch * density
        for (row in 0 until 2) for (col in 0 until 3) drawCircle(color, r, Offset(col * p + r, row * p + r), style = Stroke(1f * density))
    }
}

/**
 * The bit-toggle keypad (4.7's second tab, S1 CalculatorProgrammerBitFlipPanel): the word's bits in rows of sixteen,
 * grouped by nibble, the bit's index under each group; a tap flips that bit through the engine (`calc_bit:<n>`).
 * Bits past the word size are dimmed and take no tap.
 */
@Composable
fun BitPanel(model: CalcModel, modifier: Modifier) {
    val colors = LocalShellColors.current
    val calc = model.calc
    val width = calc.wordSize.bits
    val bin = calc.radixValue(Radix.BIN).replace(" ", "")
    val bits = CharArray(64) { '0' }
    for ((i, ch) in bin.reversed().withIndex()) if (i < 64) bits[i] = ch
    Column(modifier.background(CalcMetrics.PAD_FILL).padding(horizontal = 8.dp, vertical = 6.dp)) {
        for (row in 0 until 4) {
            val high = 63 - row * 16
            Column(Modifier.fillMaxWidth().weight(1f)) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (i in 0 until 16) {
                        val bit = high - i
                        val live = bit < width
                        KeyCell(
                            tag = "calc_bit:$bit",
                            enabled = live,
                            onPress = { model.toggleBit(bit) },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = if (i % 4 == 3 && i != 15) 6.dp else 0.dp),
                        ) {
                            BasicText(
                                if (live) bits[bit].toString() else "0",
                                style = TextStyle(fontFamily = Brand.uiFont, fontWeight = FontWeight.SemiBold, fontSize = CalcMetrics.PROG_DIGIT_FONT.sp, color = if (live) colors.text else CalcMetrics.DISABLED_KEY),
                                maxLines = 1,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(12.dp)) {
                    for (group in 0 until 4) {
                        Box(Modifier.weight(1f).fillMaxHeight().padding(end = if (group != 3) 6.dp else 0.dp)) {
                            BasicText("${high - group * 4}", style = ShellType.caption.copy(fontSize = 9.sp, lineHeight = 10.sp, color = CalcMetrics.TAB_GREY), maxLines = 1, modifier = Modifier.align(Alignment.TopStart))
                        }
                    }
                }
            }
        }
    }
}


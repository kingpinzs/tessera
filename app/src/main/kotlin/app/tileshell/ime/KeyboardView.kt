package app.tileshell.ime

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.ui.tokens.CapMetrics
import kotlin.math.roundToInt

/** R6 §2.1.18 capture colours (MEDIUM; judged against W10M in H11) and §2.8.2's white action key. */
object KeyColors {
    val panel = Color(22, 27, 21)
    val dark = Color(48, 48, 48)
    val function = Color(73, 74, 72)
    val label = Color.White
    val grip = Color(80, 80, 80)
    val actionWhite = Color(254, 255, 253)
    val actionGlyph = Color(34, 36, 33)
    val navBar = Color.Black
}

/** Selawik's x-height as a fraction of the font size (1024 / 2048, read from the shipped TTFs). */
private const val X_HEIGHT_RATIO = 0.5f

/** The typefaces the Canvas draws labels with: the branding module's fonts as android.graphics Typefaces. */
class KeyFonts(val regular: Typeface, val icons: Typeface)

/** Everything the view calls back into. */
interface KeyboardActions {
    fun stripTapped(item: StripItem)
    fun micTapped()
    fun restoreFullWidth()
    fun emoji(action: EmojiAction)
}

sealed interface EmojiAction {
    data class Insert(val text: String) : EmojiAction
    data class Category(val category: EmojiCategory) : EmojiAction
    data object Letters : EmojiAction
    data object Backspace : EmojiAction
}

/**
 * The keyboard's window content. Laid out in pixels (Density 1, fontScale 1: see [KeyboardMetrics]).
 * The keys, labels, popups, trail and dot are drawn on one Canvas; every key also has an invisible node
 * carrying its test tag, so `uiautomator dump` reports each key's real bounds (E3, E10, E12) without the
 * keys being separate drawing layers.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun KeyboardView(state: KeyboardState, metrics: KeyboardMetrics, fonts: KeyFonts, emoji: EmojiCatalog, actions: KeyboardActions, labelFor: (Key) -> String) {
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        val accent = Color(state.accent)
        val panelTop = metrics.panelTop(state.raise)
        Box(
            Modifier
                .fillMaxWidth()
                .height(metrics.viewH.dp)
                .semantics { testTagsAsResourceId = true },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawKeyboard(state, metrics, fonts, accent, panelTop, labelFor)
            }
            // The nav-bar inset below the panel, when the window reaches under the system nav bar: black,
            // as W10M's own nav bar (R6 §2.1.18 "nav bar (0,0,0)").
            if (metrics.bottomInsetPx > 0f) {
                Box(Modifier.at(0f, metrics.viewH - metrics.bottomInsetPx, metrics.screenWidthPx, metrics.bottomInsetPx).background(KeyColors.navBar))
            }
            Box(Modifier.at(0f, panelTop, metrics.screenWidthPx, metrics.panelH).testTag("kb_panel"))
            Strip(state, metrics, panelTop, accent, actions)
            if (state.emojiOpen) {
                EmojiPanel(state, metrics, panelTop, accent, emoji, actions)
            } else {
                state.layout.keys.forEach { k ->
                    Box(
                        Modifier
                            .at(metrics.x(k.left), panelTop + metrics.yInPanel(k.top), metrics.w(k.width), metrics.h(k.height))
                            .testTag(k.tag)
                            .semantics {
                                contentDescription = labelFor(k).ifEmpty { k.id }
                                selected = k.id in state.pressed
                            },
                    )
                }
                if (state.layout.layer != Layer.PHONE) {
                    val dx = metrics.x(if (state.handedness == Handedness.LEFT) KeyGrid.dotLeftHandedX else KeyGrid.dotRightHandedX)
                    val dy = panelTop + metrics.yInPanel(KeyGrid.dotY)
                    val r = metrics.h(KeyGrid.DOT_RING_D) / 2f
                    Box(Modifier.at(dx - r, dy - r, 2 * r, 2 * r).testTag("kb_cursor_dot"))
                }
            }
            metrics.freeBand?.let { (from, to) ->
                Box(
                    Modifier
                        .at(from, panelTop + metrics.stripH, to - from, metrics.blockH)
                        .testTag("kb_restore_full")
                        .pointerInput(Unit) { detectTapGestures { actions.restoreFullWidth() } },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(Layouts.GLYPH_KEYBOARD, style = TextStyle(fontFamily = Brand.iconFont, fontSize = metrics.h(ICON_PHYS).sp, color = KeyColors.label))
                }
            }
            PopupNodes(state, metrics, panelTop)
        }
    }
}

// ---- drawing ----------------------------------------------------------------------------------------

private fun DrawScope.drawKeyboard(state: KeyboardState, m: KeyboardMetrics, fonts: KeyFonts, accent: Color, panelTop: Float, labelFor: (Key) -> String) {
    // R6 §2.2.2: the strip is the panel's colour with no separator; the panel fills the full width even
    // when docked (the freed band shows the panel colour, Decisions stand-in (2)).
    drawRect(KeyColors.panel, Offset(0f, panelTop), Size(m.screenWidthPx, m.panelH))
    if (!state.emojiOpen) {
        state.layout.keys.forEach { k -> drawKey(k, state, m, fonts, accent, panelTop, labelFor) }
        if (state.layout.layer != Layer.PHONE) drawDot(state, m, accent, panelTop)
    }
    state.cursorDrag?.let { drawCursorDrag(it, m, fonts, accent, panelTop) }
    state.trail?.let { drawTrail(it, m, accent) }
    state.popup?.let { drawPressPopup(it, m, fonts, accent, panelTop) }
    state.alternates?.let { drawAlternates(it, m, fonts, accent, panelTop) }
    state.oneHanded?.let { drawOneHanded(it, m, fonts, accent, panelTop) }
}

private fun DrawScope.drawKey(k: Key, state: KeyboardState, m: KeyboardMetrics, fonts: KeyFonts, accent: Color, panelTop: Float, labelFor: (Key) -> String) {
    val left = m.x(k.left).roundToInt().toFloat()
    val right = m.x(k.right).roundToInt().toFloat()
    val top = (panelTop + m.yInPanel(k.top)).roundToInt().toFloat()
    val bottom = (panelTop + m.yInPanel(k.bottom)).roundToInt().toFloat()
    val pressed = k.id in state.pressed
    val locked = k.code == KeyCode.SHIFT && state.shift == ShiftMode.LOCKED
    val fill = when {
        pressed || locked -> accent
        k.style == KeyStyle.ACTION_WHITE -> KeyColors.actionWhite
        k.style == KeyStyle.FUNCTION -> KeyColors.function
        else -> KeyColors.dark
    }
    drawRect(fill, Offset(left, top), Size(right - left, bottom - top))
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val ink = if (k.style == KeyStyle.ACTION_WHITE && !pressed) KeyColors.actionGlyph else KeyColors.label
    when {
        k.code == KeyCode.SHIFT -> {
            // Decisions stand-in (4): one tap fills the arrow white; caps lock fills the key with the accent.
            val glyph = if (state.shift == ShiftMode.OFF) Layouts.GLYPH_SHIFT else Layouts.GLYPH_SHIFT_FILLED
            drawGlyph(glyph, fonts.icons, m.h(ICON_PHYS), cx, cy, ink)
        }
        k.glyph != null -> drawGlyph(k.glyph, fonts.icons, m.h(ICON_PHYS), cx, cy, ink)
        k.code == KeyCode.SPACE && k.width > KeyGrid.PITCH * 2 -> {
            // R6 §2.1.16: a two-line grip at the space bar's top centre, 59 × 18, its top 20 below the key's top.
            val gw = m.w(KeyGrid.GRIP_W)
            val line = m.h(KeyGrid.GRIP_H) * 5f / 18f
            val gTop = top + m.h(KeyGrid.GRIP_TOP)
            drawRect(KeyColors.grip, Offset(cx - gw / 2f, gTop), Size(gw, line))
            drawRect(KeyColors.grip, Offset(cx - gw / 2f, gTop + m.h(KeyGrid.GRIP_H) - line), Size(gw, line))
        }
        k.code == KeyCode.SYMBOLS || k.code == KeyCode.LETTERS || k.code == KeyCode.PAGE -> {
            // R6 §2.1.15: "&123" digits 40 ± 2 tall.
            val size = m.h(KeyGrid.SYMBOLS_LABEL_DIGIT_H) / CapMetrics.CAP_RATIO
            drawLabel(k.text, fonts.regular, size, cx, cy + m.h(KeyGrid.SYMBOLS_LABEL_DIGIT_H) / 2f, ink)
            if (k.code == KeyCode.SYMBOLS) {
                // R6 §2.1.17 (MEDIUM): three hold dots at the top-left.
                val r = m.h(4f) / 2f
                repeat(3) { i ->
                    drawCircle(KeyColors.label, r, Offset(left + m.w(KeyGrid.HOLD_DOT_LEFT + i * KeyGrid.HOLD_DOT_PITCH), top + m.h(KeyGrid.HOLD_DOT_TOP)))
                }
            }
        }
        else -> {
            val label = labelFor(k)
            val (size, baselineOffset) = labelMetrics(label, k, m)
            drawLabel(label, fonts.regular, size, cx, cy + baselineOffset, ink)
            k.sub?.let { sub ->
                val subSize = m.h(KeyGrid.DOTCOM_X_HEIGHT) / X_HEIGHT_RATIO
                drawLabel(sub, fonts.regular, subSize, cx, bottom - m.h(28f), KeyColors.label.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * A label's font size and where its baseline sits below the key's centre, so its INK is centred (R6
 * §2.1.14: "label centred in the key"). Lowercase is sized from the measured x-height (41), capitals and
 * digits from the measured cap height (48) — W10M drew capitals smaller than a lowercase-sized capital.
 */
private fun labelMetrics(label: String, k: Key, m: KeyboardMetrics): Pair<Float, Float> {
    if (k.id == "dotcom") {
        val size = m.h(KeyGrid.DOTCOM_X_HEIGHT) / X_HEIGHT_RATIO
        return size to m.h(KeyGrid.DOTCOM_X_HEIGHT) / 2f
    }
    val lower = label.length == 1 && label[0].isLowerCase()
    return if (lower) {
        m.h(KeyGrid.LABEL_X_HEIGHT) / X_HEIGHT_RATIO to m.h(KeyGrid.LABEL_X_HEIGHT) / 2f
    } else {
        m.h(KeyGrid.LABEL_CAP_HEIGHT) / CapMetrics.CAP_RATIO to m.h(KeyGrid.LABEL_CAP_HEIGHT) / 2f
    }
}

private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

private fun DrawScope.drawLabel(text: String, typeface: Typeface, size: Float, cx: Float, baseline: Float, color: Color) {
    textPaint.typeface = typeface
    textPaint.textSize = size
    textPaint.color = color.toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, cx, baseline, textPaint)
}

/** An icon-font glyph centred on (cx, cy) by its own ink bounds. */
private fun DrawScope.drawGlyph(glyph: String, typeface: Typeface, size: Float, cx: Float, cy: Float, color: Color) {
    textPaint.typeface = typeface
    textPaint.textSize = size
    textPaint.color = color.toArgb()
    val b = android.graphics.Rect()
    textPaint.getTextBounds(glyph, 0, glyph.length, b)
    drawContext.canvas.nativeCanvas.drawText(glyph, cx, cy - b.exactCenterY(), textPaint)
}

/** R6 §2.5.1–2.5.4: accent core, key-grey disc, panel-colour ring cutting into the four keys. */
private fun DrawScope.drawDot(state: KeyboardState, m: KeyboardMetrics, accent: Color, panelTop: Float) {
    val x = m.x(if (state.handedness == Handedness.LEFT) KeyGrid.dotLeftHandedX else KeyGrid.dotRightHandedX)
    val c = Offset(x, panelTop + m.yInPanel(KeyGrid.dotY))
    drawCircle(KeyColors.panel, m.h(KeyGrid.DOT_RING_D) / 2f, c)
    drawCircle(KeyColors.dark, m.h(KeyGrid.DOT_DISC_D) / 2f, c)
    drawCircle(accent, m.h(KeyGrid.DOT_CORE_D) / 2f, c)
}

/**
 * R6 §2.5.7 (LOW, H4): while the dot is held the keyboard and strip dim to ≈50 %, four white chevrons
 * sit around the dot with their inner edges ≈105–126 from its centre, and an accent line ≈15 thick runs
 * from the dot toward the finger.
 */
private fun DrawScope.drawCursorDrag(d: CursorDrag, m: KeyboardMetrics, fonts: KeyFonts, accent: Color, panelTop: Float) {
    drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, panelTop), Size(m.screenWidthPx, m.panelH))
    drawLine(accent, d.dot, d.finger, strokeWidth = m.h(15f), cap = StrokeCap.Round)
    val inner = m.h(115f)
    val size = m.h(70f)
    val half = size / 2f
    drawGlyph(Layouts.GLYPH_CHEVRON_LEFT, fonts.icons, size, d.dot.x - inner - half, d.dot.y, KeyColors.label)
    drawGlyph(Layouts.GLYPH_CHEVRON_RIGHT, fonts.icons, size, d.dot.x + inner + half, d.dot.y, KeyColors.label)
    drawGlyph(Layouts.GLYPH_CHEVRON_UP, fonts.icons, size, d.dot.x, d.dot.y - inner - half, KeyColors.label)
    drawGlyph(Layouts.GLYPH_CHEVRON_DOWN, fonts.icons, size, d.dot.x, d.dot.y + inner + half, KeyColors.label)
    drawCircle(accent, m.h(KeyGrid.DOT_CORE_D) / 2f, d.dot)
}

/** R6 §2.4.1–2.4.4 (LOW, H3): an opaque flat accent line ≈9.4 epx wide with round ends; retracts from its tail. */
private fun DrawScope.drawTrail(t: Trail, m: KeyboardMetrics, accent: Color) {
    if (t.points.size < 2) return
    val path = Path().apply {
        moveTo(t.points[0].x, t.points[0].y)
        for (i in 1 until t.points.size) lineTo(t.points[i].x, t.points[i].y)
    }
    val stroke = Stroke(width = m.w(37f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (t.retract <= 0f) {
        drawPath(path, accent, style = stroke)
    } else {
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(measure.length * t.retract, measure.length, part, true)
        drawPath(part, accent, style = stroke)
    }
}

/** R6 §2.3: 173 × 233 accent rectangle, centred on the key, its bottom 7 above the key's top, white glyph. */
private fun DrawScope.drawPressPopup(p: KeyPopup, m: KeyboardMetrics, fonts: KeyFonts, accent: Color, panelTop: Float) {
    val r = popupRect(p.key, m, panelTop, KeyGrid.POPUP_W)
    drawRect(accent, Offset(r.first, r.second), Size(r.third, m.h(KeyGrid.POPUP_H)))
    val cx = r.first + r.third / 2f
    val cy = r.second + m.h(KeyGrid.POPUP_H) / 2f
    drawPopupLabel(p.label, fonts, m, cx, cy, KeyColors.label)
}

/** The popup's (left, top, width) in view pixels. */
private fun popupRect(key: Key, m: KeyboardMetrics, panelTop: Float, widthPhys: Float): Triple<Float, Float, Float> {
    val w = m.w(widthPhys)
    val bottom = panelTop + m.yInPanel(key.top - KeyGrid.POPUP_LIFT)
    return Triple(m.x(key.centerX) - w / 2f, bottom - m.h(KeyGrid.POPUP_H), w)
}

/** R6 §2.3.4 (MEDIUM): the popup glyph's x-height is 59.5, ≈1.45× the key label; capitals scale the same. */
private fun DrawScope.drawPopupLabel(label: String, fonts: KeyFonts, m: KeyboardMetrics, cx: Float, cy: Float, color: Color) {
    val scale = KeyGrid.POPUP_X_HEIGHT / KeyGrid.LABEL_X_HEIGHT
    val lower = label.length == 1 && label[0].isLowerCase()
    val ink = if (lower) m.h(KeyGrid.POPUP_X_HEIGHT) else m.h(KeyGrid.LABEL_CAP_HEIGHT * scale)
    val size = if (lower) ink / X_HEIGHT_RATIO else ink / CapMetrics.CAP_RATIO
    drawLabel(label, fonts.regular, size, cx, cy + ink / 2f, color)
}

/** Decisions stand-in (3), H19: one cell per character at the key pitch, the plain letter over the key. */
private fun DrawScope.drawAlternates(a: AlternatesPopup, m: KeyboardMetrics, fonts: KeyFonts, accent: Color, panelTop: Float) {
    val pitch = m.w(KeyGrid.PITCH)
    val h = m.h(KeyGrid.POPUP_H)
    val top = panelTop + m.yInPanel(a.key.top - KeyGrid.POPUP_LIFT) - h
    a.cells.forEachIndexed { i, cell ->
        val cx = m.x(a.key.centerX) + (if (a.leftward) -i else i) * pitch
        val selected = i == a.selected
        drawRect(if (selected) KeyColors.label else accent, Offset(cx - pitch / 2f, top), Size(pitch, h))
        drawPopupLabel(cell, fonts, m, cx, top + h / 2f, if (selected) accent else KeyColors.label)
    }
}

/** Decisions stand-in (2), H20: three glyph cells above &123 — dock left, full width, dock right. */
private fun DrawScope.drawOneHanded(p: OneHandedPopup, m: KeyboardMetrics, fonts: KeyFonts, accent: Color, panelTop: Float) {
    val pitch = m.w(KeyGrid.PITCH)
    val h = m.h(KeyGrid.POPUP_H)
    val left = m.x(p.key.left)
    val top = panelTop + m.yInPanel(p.key.top - KeyGrid.POPUP_LIFT) - h
    listOf(Dock.LEFT, Dock.FULL, Dock.RIGHT).forEachIndexed { i, dock ->
        val x = left + i * pitch
        val selected = p.selected == dock
        drawRect(if (selected) KeyColors.label else accent, Offset(x, top), Size(pitch, h))
        val ink = if (selected) accent else KeyColors.label
        val cx = x + pitch / 2f
        val cy = top + h / 2f
        when (dock) {
            Dock.FULL -> drawGlyph(Layouts.GLYPH_KEYBOARD, fonts.icons, m.h(ICON_PHYS), cx, cy, ink)
            Dock.LEFT -> drawGlyph(Layouts.GLYPH_ONE_HANDED, fonts.icons, m.h(ICON_PHYS), cx, cy, ink)
            Dock.RIGHT -> withTransform({ scale(-1f, 1f, Offset(cx, cy)) }) {
                drawGlyph(Layouts.GLYPH_ONE_HANDED, fonts.icons, m.h(ICON_PHYS), cx, cy, ink)
            }
        }
    }
}

/** Function-key glyph size: unmeasured in R6, so an approximation (H2) sized to read like the labels. */
const val ICON_PHYS = 76f

// ---- popup nodes for the dumps ----------------------------------------------------------------------

@Composable
private fun PopupNodes(state: KeyboardState, m: KeyboardMetrics, panelTop: Float) {
    state.popup?.let { p ->
        val r = popupRect(p.key, m, panelTop, KeyGrid.POPUP_W)
        Box(Modifier.at(r.first, r.second, r.third, m.h(KeyGrid.POPUP_H)).testTag("kb_popup").semantics { contentDescription = p.label })
    }
    state.alternates?.let { a ->
        val pitch = m.w(KeyGrid.PITCH)
        val h = m.h(KeyGrid.POPUP_H)
        val top = panelTop + m.yInPanel(a.key.top - KeyGrid.POPUP_LIFT) - h
        a.cells.forEachIndexed { i, cell ->
            val cx = m.x(a.key.centerX) + (if (a.leftward) -i else i) * pitch
            Box(Modifier.at(cx - pitch / 2f, top, pitch, h).testTag("kb_alt_$i").semantics { contentDescription = cell; selected = i == a.selected })
        }
    }
    state.oneHanded?.let { p ->
        val pitch = m.w(KeyGrid.PITCH)
        val h = m.h(KeyGrid.POPUP_H)
        val top = panelTop + m.yInPanel(p.key.top - KeyGrid.POPUP_LIFT) - h
        listOf(Dock.LEFT, Dock.FULL, Dock.RIGHT).forEachIndexed { i, dock ->
            Box(Modifier.at(m.x(p.key.left) + i * pitch, top, pitch, h).testTag("kb_onehanded_${dock.name.lowercase()}").semantics { selected = p.selected == dock })
        }
    }
}

// ---- the suggestion strip (R6 §2.2) -----------------------------------------------------------------

@Composable
private fun Strip(state: KeyboardState, m: KeyboardMetrics, panelTop: Float, accent: Color, actions: KeyboardActions) {
    val listening = state.voice as? VoiceState.Listening
    val showMic = state.field.suggestionsOn || state.field.kind == FieldKind.SEARCH
    // R6 §2.2.3: cap height 13.3 epx → Selawik at 13.3 / 0.7002 ≈ 19 epx, vertically centred.
    val textStyle = TextStyle(fontFamily = Brand.uiFont, fontSize = m.epx(STRIP_TEXT_EPX).sp, color = KeyColors.label)
    Box(
        Modifier
            .at(m.offsetX, panelTop, m.screenWidthPx * m.dockFactor, m.stripH)
            .testTag("kb_strip"),
    ) {
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).testTag("kb_strip_scroll"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMic) {
                // R6 §2.2.5 (MEDIUM, 14393): the microphone leads the strip, 13 epx in, the first word 28.6
                // epx to its right.
                Box(
                    Modifier
                        .padding(start = m.epx(STRIP_LEFT_EPX).dp)
                        .height(m.stripH.dp)
                        .testTag("kb_mic")
                        .pointerInput(Unit) { detectTapGestures { actions.micTapped() } },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(Layouts.GLYPH_MIC, style = TextStyle(fontFamily = Brand.iconFont, fontSize = m.epx(MIC_EPX).sp, color = if (listening != null) accent else KeyColors.label))
                }
            }
            val firstGap = if (showMic) m.epx(MIC_TO_WORD_EPX) else m.epx(STRIP_LEFT_EPX)
            val notice = state.notice
            when {
                listening != null -> BasicText(
                    listening.partial.ifEmpty { "Listening…" },
                    style = textStyle.copy(color = if (listening.partial.isEmpty()) KeyColors.label.copy(alpha = 0.6f) else KeyColors.label),
                    modifier = Modifier.padding(start = firstGap.dp).testTag("kb_voice_partial"),
                )
                notice != null -> BasicText(notice, style = textStyle, modifier = Modifier.padding(start = firstGap.dp).testTag("kb_notice"))
                else -> state.strip.forEachIndexed { i, item ->
                    StripWord(item, i, textStyle, accent, m, if (i == 0) firstGap else m.epx(STRIP_GAP_EPX)) { actions.stripTapped(item) }
                }
            }
        }
    }
}

@Composable
private fun StripWord(item: StripItem, index: Int, style: TextStyle, accent: Color, m: KeyboardMetrics, gap: Float, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val pad = m.epx(STRIP_GAP_EPX) / 2f
    Box(
        Modifier
            .padding(start = (gap - pad).coerceAtLeast(0f).dp)
            .height(m.stripH.dp)
            // R6 §2.2.7 (MEDIUM): the pressed item is accent-filled across the strip's height.
            .background(if (pressed) accent else Color.Transparent)
            .testTag("kb_sugg_$index")
            .semantics { contentDescription = item.shown }
            .pointerInput(item) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            item.shown,
            style = style.copy(fontWeight = if (item.bold) FontWeight.Bold else FontWeight.Normal),
            modifier = Modifier.padding(horizontal = pad.dp),
        )
    }
}

// Strip values in epx (R6 §2.2).
private const val STRIP_TEXT_EPX = 13.3f / CapMetrics.CAP_RATIO
private const val STRIP_LEFT_EPX = 13f
private const val STRIP_GAP_EPX = 26f
private const val MIC_TO_WORD_EPX = 28.6f

/** The mic glyph's font size so its ink is ≈13.8 × 20.3 epx (R6 §2.2.5); the Fluent mic fills ≈85 % of its em. */
private const val MIC_EPX = 24f

// ---- layout helper ----------------------------------------------------------------------------------

/** Place a box at an absolute pixel rectangle, with edges rounded (not sizes) so neighbours share edges. */
fun Modifier.at(x: Float, y: Float, w: Float, h: Float): Modifier {
    val l = x.roundToInt()
    val t = y.roundToInt()
    val width = ((x + w).roundToInt() - l).coerceAtLeast(0)
    val height = ((y + h).roundToInt() - t).coerceAtLeast(0)
    return this
        .offset { IntOffset(l, t) }
        .layout { measurable, _ ->
            val p = measurable.measure(Constraints.fixed(width, height))
            layout(p.width, p.height) { p.place(0, 0) }
        }
}

@Suppress("unused")
private fun Modifier.widthPx(w: Float) = width(w.dp)

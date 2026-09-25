package app.tileshell.clock

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calculator.InkText
import app.tileshell.start.Edit
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Alarms & Clock app's shared drawing (phase 15 build task 4). Every value cites its r11/clock.md row or the
 * shared measurement it comes from (R7 §3.5.8 app bar, R7 §3.6.2 flyout, R3 C1 toggle, R7 §1.3.9 checkbox).
 */
object ClockMetrics {
    /** 1.2: the tab header band under the status bar, 68.4 epx, fill (24,26,23) — the app-bar fill. */
    val BAND = 68.4.dp
    val BAND_FILL = Color(24, 26, 23)
    /** 1.4: tab centres 82.3 · 146.7 · 211.4 · 275.8 epx — a 64.5 pitch. */
    const val TAB_FIRST_CENTRE = 82.3f
    const val TAB_PITCH = 64.5f
    /** 1.5 / 1.6: the icon's centre 27.2 epx below the band top; the label's cap top ≈ 43.5 below it. */
    const val TAB_ICON_CY = 27.2f
    const val TAB_LABEL_CAP_TOP = 43.5f
    /** 1.8: the accent underline, 64.4 × ≈3.7 epx at the band's bottom. */
    val UNDERLINE_W = 64.4.dp
    val UNDERLINE_H = 3.7.dp
    /** R7 §3.5.8 (R11 1.12 agrees): the app bar 48.2 ± 1 epx, buttons on a 68-epx pitch, "…" 48 epx flush right. */
    val APP_BAR = 48.dp
    val APP_BAR_EXPANDED = 68.dp
    val BUTTON_PITCH = 68.dp
    val MORE_W = 48.dp
    /** R7 §3.6.2: flyout fill (40,40,40), a 1-epx (71,76,70) border, 44-epx items, 8-epx padding. */
    val FLYOUT_FILL = Color(40, 40, 40)
    val FLYOUT_BORDER = Color(71, 76, 70)
    val MENU_ROW = 44.dp
    /** R7 §3.6.4 / r11 U12: the flyout's grow, 233 ms ease-out (tagged approximation). */
    const val FLYOUT_MS = 233
    /** 2.9 (R3 C1): the toggle 43.8 × 19.6 epx; its label 13.4 epx right of it. */
    val TOGGLE_W = 43.8.dp
    val TOGGLE_H = 19.6.dp
    /** R7 §1.3.9: a 20.3-epx checkbox centred at x 22.2, rows shifted 32 epx right; r11 4.1: 20.4 square in the days flyout. */
    val CHECKBOX = 20.4.dp
    /** 3.5: spinner rows 32 epx. */
    val SPINNER_ROW = 32.dp

    val easeOut: Easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)
}

/** Android's 12 / 24-hour setting, read once per change by the activity (Decisions "Clock rules": times follow it). */
val LocalIs24h = compositionLocalOf { false }

/** The unselected tab / dim-glyph ink: ≈ 40 % of the text colour (1.7; MusicMetrics.PIVOT_DIM is the same fraction). */
const val DIM_INK = 0.4f

/** One app-bar button (1.13). [enabled] false draws it dim, as R7 §3.5.8's disabled glyphs read. */
data class BarButton(val glyph: String, val label: String, val tag: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** One item of the "…" menu (1.14). */
data class ClockMenuEntry(val label: String, val tag: String, val onPick: () -> Unit)

/**
 * The page's app bar (R7 §3.5.8; 1.12–1.14): [buttons] right-aligned on a 68-epx pitch beside the "…" button
 * flush right. Tapping "…" expands the bar — labels appear under the glyphs — and the [menu] grows above it at a
 * 44-epx pitch in the flyout fill. Drawn as the bottom of a Box that holds the page, so the menu can rise over it.
 */
@Composable
fun BoxScope.ClockAppBar(buttons: List<BarButton>, menu: List<ClockMenuEntry>, expanded: Boolean, onExpand: (Boolean) -> Unit) {
    val colors = LocalShellColors.current
    val height = if (expanded) ClockMetrics.APP_BAR_EXPANDED else ClockMetrics.APP_BAR
    if (expanded) {
        // A tap anywhere above the bar closes it, as R7 §3.6.2's flyouts close.
        Box(Modifier.fillMaxSize().testTag("clock_bar_scrim").pointerInput(Unit) {
            awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onExpand(false) }
        })
        var grow by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) { MotionClock.animate("appbar_menu", ClockMetrics.FLYOUT_MS, ClockMetrics.easeOut) { grow = it } }
        val menuHeight = ClockMetrics.MENU_ROW * menu.size + 16.dp
        Column(
            Modifier.align(Alignment.BottomStart).offset(y = -height).fillMaxWidth().height(menuHeight * grow)
                .clipToBounds().background(ClockMetrics.FLYOUT_FILL).testTag("clock_more_menu"),
        ) {
            Box(Modifier.height(8.dp))
            menu.forEach { entry ->
                PressBox(Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).testTag(entry.tag), onClick = { onExpand(false); entry.onPick() }) {
                    // 1.14: item ink at x 10.7 epx.
                    BasicText(entry.label, Modifier.align(Alignment.CenterStart).offset(x = 10.7.dp), style = ShellType.body.copy(color = Color.White))
                }
            }
        }
    }
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(height).background(ClockMetrics.BAND_FILL).testTag("clock_app_bar")) {
        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
            buttons.forEach { b ->
                BarButtonView(b.glyph, if (expanded) b.label else null, b.tag, b.enabled, ClockMetrics.BUTTON_PITCH, colors.text) { b.onClick() }
            }
            BarButtonView(Glyph.MORE_HORIZONTAL, null, "clock_more", true, ClockMetrics.MORE_W, colors.text) { onExpand(!expanded) }
        }
    }
}

@Composable
private fun BarButtonView(glyph: String, label: String?, tag: String, enabled: Boolean, width: Dp, ink: Color, onClick: () -> Unit) {
    val color = if (enabled) ink else ink.copy(alpha = DIM_INK)
    Box(
        Modifier.width(width).fillMaxHeight().testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 1.12: the glyph box ≈ 20.6 epx.
            BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 20.sp, color = color, textAlign = TextAlign.Center))
            if (label != null) BasicText(label, style = ShellType.caption.copy(color = color, textAlign = TextAlign.Center), maxLines = 1)
        }
    }
}

/** A row that lightens while pressed (X19's press, [ROW_PRESS_ALPHA]) and fires on the up. */
@Composable
fun PressBox(modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.pointerInput(onClick) {
            awaitEachGesture {
                awaitFirstDown().consume()
                pressed = true
                val up = waitForUpOrCancellation()
                pressed = false
                if (up != null) { up.consume(); onClick() }
            }
        }.background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent),
        content = content,
    )
}

/**
 * A flyout at a fixed place (R7 §3.6.2's fill and border; 4.1 / 4.3 / 4.4 give each one its box), grown from its
 * top edge over [ClockMetrics.FLYOUT_MS] on the motion clock as `[motion] flyout` (U12). A tap outside closes it.
 */
@Composable
fun BoxScope.ClockFlyout(x: Dp, top: Dp, width: Dp, height: Dp, tag: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().testTag("${tag}_scrim").pointerInput(onDismiss) {
        awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onDismiss() }
    })
    var grow by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { MotionClock.animate("flyout", ClockMetrics.FLYOUT_MS, ClockMetrics.easeOut) { grow = it } }
    Column(
        Modifier.offset(x, top).width(width).height(height * grow).clipToBounds()
            .background(ClockMetrics.FLYOUT_FILL).border(1.dp, ClockMetrics.FLYOUT_BORDER).testTag(tag)
            // Touches inside stay inside: the scrim below must not read them as a tap outside.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume(); waitForUpOrCancellation()?.consume() } },
        content = content,
    )
}

/** R3 C1 / 2.9–2.10: the 43.8 × 19.6 toggle — On: accent fill, white knob; Off: 1-epx outline, knob in the text colour. */
@Composable
fun ClockToggle(checked: Boolean, tag: String, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val colors = LocalShellColors.current
    Box(
        modifier.size(ClockMetrics.TOGGLE_W, ClockMetrics.TOGGLE_H)
            .background(if (checked) colors.accent else Color.Transparent, CircleShape)
            .border(if (checked) 0.dp else 1.dp, colors.text, CircleShape)
            .testTag(tag).semantics { role = Role.Switch; toggleableState = ToggleableState(checked) }
            .pointerInput(checked) { detectTapGestures { onChange(!checked) } },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(horizontal = 5.dp).size(8.dp).background(if (checked) Color.White else colors.text, CircleShape))
    }
}

/** R7 §1.3.9 / 4.1: a 20.4-epx square; checked = accent fill with a white check. */
@Composable
fun ClockCheckbox(checked: Boolean, tag: String, modifier: Modifier = Modifier, onChange: ((Boolean) -> Unit)? = null) {
    val colors = LocalShellColors.current
    Box(
        modifier.size(ClockMetrics.CHECKBOX)
            .background(if (checked) colors.accent else Color.Transparent)
            .border(1.5.dp, if (checked) colors.accent else colors.text)
            .testTag(tag).semantics { role = Role.Checkbox; toggleableState = ToggleableState(checked) }
            .let { m -> if (onChange != null) m.pointerInput(checked) { detectTapGestures { onChange(!checked) } } else m },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) BasicText(Glyph.CHECKMARK, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 14.sp, color = Color.White))
    }
}

/**
 * The inline looping spinner (3.4–3.7, 4.8): rows at a 32-epx pitch, the selected value in the centre row in the
 * text colour, the others at 75 % (3.5); the accent band behind the centre row is the caller's (it spans every
 * column). Drag rolls it; a release settles on the nearest row with the drag's velocity carried a little further;
 * a tap on a row rolls to it. The settle is a motion, logged as `[motion] spinner` (inertia is UNMEASURED, U12).
 * [visibleRows] may be fractional: 3.4's frame is 191 epx = 5.97 rows, 4.8's 255 = 7.97, cut rows at both edges.
 */
@Composable
fun LoopSpinner(values: List<String>, selected: Int, visibleRows: Float, tag: String, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = LocalShellColors.current
    val density = LocalDensity.current
    val rowPx = with(density) { ClockMetrics.SPINNER_ROW.toPx() }
    var offset by remember { mutableFloatStateOf(0f) } // rows scrolled away from [selected]; positive = later values
    val scope = rememberCoroutineScope()
    // The gesture handlers outlive recompositions: they must read the value and callback of now, not of the
    // composition they started in (a tap after the first change rolled from the value the editor opened with).
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val n = values.size
    fun wrap(i: Int) = ((i % n) + n) % n

    // Rolls to the row nearest [target] (rows from [current]); the value changes only when the roll completes, so a
    // drag or tap that cancels it mid-way carries on from where the column is.
    fun settle(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            val from = offset
            val steps = target.roundToInt()
            val duration = (150 + abs(steps - from) * 30).roundToInt().coerceIn(150, 400)
            MotionClock.animate("spinner", duration, ClockMetrics.easeOut) { f -> offset = from + (steps - from) * f }
            val next = wrap(current + steps)
            offset = 0f
            if (next != current) select(next)
        }
    }

    val height = ClockMetrics.SPINNER_ROW * visibleRows
    Box(
        modifier.height(height).clipToBounds().testTag(tag)
            .semantics { contentDescription = values[selected] }
            .draggable(
                state = rememberDraggableState { delta -> offset -= delta / rowPx },
                orientation = Orientation.Vertical,
                // A drag catches a rolling column where it is.
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { velocity ->
                    // A fling carries on a few rows: ≈ 0.15 rows per row-height of velocity, at most 20 rows.
                    val carry = (-velocity / rowPx * 0.15f).coerceIn(-20f, 20f)
                    settle(offset + carry)
                },
            )
            .pointerInput(n) {
                detectTapGestures { tap ->
                    val centreY = size.height / 2f
                    val k = ((tap.y - centreY) / rowPx + offset).roundToInt()
                    if (k != 0 || offset != 0f) settle(k.toFloat())
                }
            },
    ) {
        // The rows around the one now nearest the centre, so a drag of any length keeps the column full; a window
        // fixed on [selected] ran out two rows past the frame and left blank rows.
        val half = (visibleRows / 2f).toInt() + 2
        val centre = height / 2
        val nearest = offset.roundToInt()
        for (k in nearest - half..nearest + half) {
            val index = wrap(selected + k)
            val y = centre + ClockMetrics.SPINNER_ROW * (k - offset) - ClockMetrics.SPINNER_ROW / 2
            val isCentre = abs(k - offset) < 0.5f
            BasicText(
                values[index],
                Modifier.offset(y = y).fillMaxWidth().height(ClockMetrics.SPINNER_ROW),
                // 3.5: digits cap 11.6 epx ≈ 16.5-epx type; the selected row in the text colour, the rest at 75 %.
                style = ShellType.body.copy(fontSize = 16.5.sp, lineHeight = 32.sp, color = if (isCentre) colors.text else colors.text.copy(alpha = 0.75f), textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/** 3.3 / 4.8: an editor's all-caps title — semibold, cap 11.6 epx (≈ 16.5-epx type), ink x 10.7, cap top 16.9 below the status bar. */
@Composable
fun EditorTitle(text: String, tag: String) {
    val colors = LocalShellColors.current
    val top = CapMetrics.topPaddingForCapTop(16.9f, 16.5f)
    BasicText(text, Modifier.offset(x = 10.7.dp, y = top.dp).testTag(tag), style = ShellType.base.copy(fontSize = 16.5.sp, color = colors.text), maxLines = 1)
}

/**
 * 2.1: the Alarm tab's empty line — "No alarms" in Light type, cap 17.8 epx (≈ 25.4-epx), ink x 9.8, cap top 21.4
 * below the band, grey ≈ 37 % of the text colour. The Timer tab borrows it for "No timers" (approximation). r11
 * measured INK, so the line is placed by the shipped font's measured ink, as the Calculator is: a box offset left the
 * "N"'s 2.3-epx side bearing in (qa/phase-15/E10-run7/DEFECT.md).
 */
@Composable
fun BoxScope.EmptyLine(text: String, tag: String) {
    val colors = LocalShellColors.current
    InkText(
        text, ShellType.title.copy(fontSize = 25.4.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light, color = colors.text.copy(alpha = 0.37f)),
        reference = "H", modifier = Modifier.testTag(tag), inkLeftEpx = 9.8f, inkTopEpx = 21.4f,
    )
}

/** R7 §3.5.9: an empty-list line in the text colour, subtitle class, left 11.7 epx, cap top 70.4 below the page top. */
@Composable
fun BoxScope.EmptyLineR7(text: String, tag: String, capTop: Float = 70.4f) {
    val colors = LocalShellColors.current
    val top = CapMetrics.topPaddingForCapTop(capTop, 20f)
    BasicText(text, Modifier.offset(x = 11.7.dp, y = top.dp).testTag(tag), style = ShellType.subtitle.copy(color = colors.text))
}

/**
 * A tap, or a hold of [Edit.HOLD_MS] (the shell's one hold, R6 §1.1.1). A press that a scroll takes over is neither: it is
 * told apart from a hold by whether the wait ended by itself, not by a null result, which both give. After a hold the
 * release is consumed before any child sees it, so it lands as no tap anywhere.
 */
suspend fun PointerInputScope.detectTapOrHold(onTap: () -> Unit, onHold: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown()
        var ended = false
        val up = withTimeoutOrNull(Edit.HOLD_MS) { waitForUpOrCancellation().also { ended = true } }
        when {
            up != null -> { up.consume(); onTap() }
            !ended -> {
                onHold()
                // The release is taken in the Initial pass, before the children see it, so a held button or name is
                // not also tapped when the finger lifts.
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    e.changes.forEach { it.consume() }
                } while (e.changes.any { it.pressed })
            }
        }
    }
}

/**
 * A row's hold menu with its one verb, under the held row: R7 §3.6.2's flyout, the form World Clock's Remove takes.
 * [anchorY] is the held row's top inside the [BoxScope] the menu is drawn in.
 */
@Composable
fun BoxScope.RowHoldMenu(anchorY: Dp, verb: String, tag: String, verbTag: String, onVerb: () -> Unit, onDismiss: () -> Unit) {
    ClockFlyout(x = 11.6.dp, top = anchorY + 60.dp, width = 335.5.dp, height = 60.dp, tag = tag, onDismiss = onDismiss) {
        Box(Modifier.height(8.dp))
        PressBox(Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).testTag(verbTag), onClick = onVerb) {
            BasicText(verb, Modifier.align(Alignment.CenterStart).offset(x = 11.7.dp), style = ShellType.body.copy(color = Color.White))
        }
    }
}

/** The 59.6-epx ring button of the timer and stopwatch tabs (6.4–6.5, 7.2–7.3): a 1.8-epx very dark ring, an accent arc while running. */
@Composable
fun RingButton(glyph: String, tag: String, arcFraction: Float?, enabled: Boolean, modifier: Modifier = Modifier, scale: Float = 1f, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    val diameter = 59.6.dp * scale
    val ink = if (enabled) colors.text else colors.text.copy(alpha = DIM_INK)
    Box(
        modifier.size(diameter).testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.8.dp.toPx() * scale
            drawCircle(colors.text.copy(alpha = 0.085f), radius = size.minDimension / 2 - stroke / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (arcFraction != null) {
                // 6.5: an accent arc from the top, stroke ≈ 2.7 epx.
                val arc = 2.7.dp.toPx() * scale
                drawArc(colors.accent, -90f, 360f * arcFraction.coerceIn(0f, 1f), false,
                    topLeft = androidx.compose.ui.geometry.Offset(arc / 2, arc / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - arc, size.height - arc),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(arc))
            }
        }
        // 6.4: the play glyph is optically offset 2.6 epx right; pause sits centred.
        val shift = if (glyph == Glyph.PLAY) 2.6.dp * scale else 0.dp
        BasicText(glyph, Modifier.offset(x = shift), style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = (20f * scale).sp, color = ink))
    }
}

/** A round-trip glyph button (reset / expand / flag / collapse) in a 44-epx cell. */
@Composable
fun GlyphButton(glyph: String, tag: String, enabled: Boolean, modifier: Modifier = Modifier, size: Float = 20f, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    val ink = if (enabled) colors.text else colors.text.copy(alpha = DIM_INK)
    Box(
        modifier.size(44.dp).testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = size.sp, color = ink))
    }
}

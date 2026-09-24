package app.tileshell.clock

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.tokens.ShellType

/**
 * W10M's ring toast (phase 15 T15-14, Q-E A): an interactive banner pinned to the top of the screen, full width
 * (r11/clock.md 8.1, HIGH). The alarm form is the 15254 toast, 0 → 248 epx (8.2–8.6, LOW, with U8's type
 * metrics); the timer form is the 14393 toast, 0 → 216 epx (8.9–8.11, MEDIUM). Fill (57,57,57) (8.9). Every value
 * below cites its row; the English timer strings are U2's tagged approximations.
 *
 * [topInset] moves the whole toast down when it is drawn as an overlay over an app that shows its status bar (an
 * app overlay cannot cover SystemUI — the recorded seam of T15-32). It enters with R7 §4.5.1's 217-ms grow-down
 * (U12, tagged), logged on the shell's motion clock as `[motion] ring_toast`.
 */
@Composable
fun RingToast(ring: RingService.Ring, topInset: Dp, onSnooze: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    val height = if (ring.timer) 216.dp else 248.dp
    var grow by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        MotionClock.animate("ring_toast", 217, CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)) { grow = it }
    }
    var snoozeFor by remember(ring.ids) { mutableIntStateOf(ring.snoozeMinutes) }
    var listOpen by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxWidth().offset(y = topInset).height(height * grow).clipToBounds()
            .background(Color(57, 57, 57)).testTag("ring_surface"),
    ) {
        Box(Modifier.fillMaxWidth().height(height)) {
            if (ring.timer) {
                // 8.10: a 48 × 48-epx accent app tile with a timer glyph at x 9.8, y 35.6.
                Box(Modifier.offset(9.8.dp, 35.6.dp).size(48.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                    BasicText(Glyph.TIMER, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 26.sp, color = Color.White))
                }
                Line(ring.title, 69.8.dp, 34.7.dp, Color.White, FontWeight.SemiBold, "ring_title")
                Line(ring.names.joinToString(", "), 69.8.dp, 56.0.dp, Color(150, 150, 150), FontWeight.Normal, "ring_name")
                Line(ring.timeText, 69.8.dp, 73.8.dp, Color(150, 150, 150), FontWeight.Normal, "ring_time")
                Caption("Alarms & Clock", 69.8.dp, 92.4.dp, Color(140, 140, 140))
                // 8.11: one button, left — ✕ over "Dismiss", centred at x 42.0.
                ToastButton(Glyph.DISMISS, "Dismiss", centreX = 42.0.dp, glyphTop = 128.0.dp, labelTop = 151.1.dp, tag = "ring_dismiss", onClick = onDismiss)
                Handle(207.1.dp)
            } else {
                // 8.3: a small accent app icon (≈ 22 epx) at x 9.4, top 35.6; title, name, time at x 42.8.
                Box(Modifier.offset(9.4.dp, 35.6.dp).size(22.dp).background(colors.accent), contentAlignment = Alignment.Center) {
                    BasicText(Glyph.CLOCK_ALARM, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 14.sp, color = Color.White))
                }
                Line(ring.title, 42.8.dp, 35.6.dp, Color.White, FontWeight.SemiBold, "ring_title")
                Line(ring.names.joinToString(", "), 42.8.dp, 55.6.dp, Color(150, 150, 150), FontWeight.Normal, "ring_name")
                Line(ring.timeText, 42.8.dp, 73.3.dp, Color(150, 150, 150), FontWeight.Normal, "ring_time")
                // 8.4: "Snooze for" and its ComboBox, 10.6 → 346.1 × 117.2 → 149.4 epx.
                Caption("Snooze for", 10.6.dp, 98.9.dp, Color(200, 200, 200))
                Box(
                    Modifier.offset(10.6.dp, 117.2.dp).width(335.5.dp).height(32.2.dp)
                        .border(2.dp, Color(160, 160, 160)).testTag("ring_snooze_for")
                        .clickable { listOpen = !listOpen },
                ) {
                    BasicText(snoozeLabel(snoozeFor), Modifier.align(Alignment.CenterStart).offset(x = 10.dp),
                        style = ShellType.body.copy(color = Color.White))
                    BasicText(Glyph.CHEVRON_DOWN, Modifier.align(Alignment.CenterEnd).offset(x = (-10).dp),
                        style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 14.sp, color = Color.White))
                }
                // 8.5: two icon-over-label buttons, right-aligned, the pair spanning x 231.7 → 335.0.
                ToastButton(Glyph.CLOCK_ALARM, "Snooze", centreX = 257.5.dp, glyphTop = 177.8.dp, labelTop = 203.3.dp, tag = "ring_snooze") { onSnooze(snoozeFor) }
                ToastButton(Glyph.DISMISS, "Dismiss", centreX = 309.2.dp, glyphTop = 177.8.dp, labelTop = 203.3.dp, tag = "ring_dismiss", onClick = onDismiss)
                Handle(237.8.dp)
            }
        }
    }
    if (listOpen && !ring.timer) {
        // U9: the editor's snooze list (4.4) — fill (40,40,40), 44-epx items, the selected one 0.68 × accent —
        // opening under the ComboBox with R7 §3.6.4's 233-ms grow (U12, tagged).
        var open by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) { MotionClock.animate("flyout", 233, CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)) { open = it } }
        Column(
            Modifier.offset(x = 10.6.dp, y = topInset + 149.4.dp).width(335.5.dp).height((44 * Alarm.SNOOZE_CHOICES.size).dp * open)
                .clipToBounds().background(Color(40, 40, 40)),
        ) {
            Alarm.SNOOZE_CHOICES.forEach { m ->
                val selected = m == snoozeFor
                Box(
                    Modifier.fillMaxWidth().height(44.dp)
                        .background(if (selected) colors.accent.copy(alpha = 0.68f) else Color.Transparent)
                        .testTag("ring_snooze_choice:$m")
                        .clickable { snoozeFor = m; listOpen = false },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(snoozeLabel(m), Modifier.offset(x = 11.7.dp), style = ShellType.body.copy(color = Color.White))
                }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") context
}

fun snoozeLabel(minutes: Int): String = if (minutes == 60) "1 hour" else "$minutes minutes"

@Composable
private fun Line(text: String, x: Dp, top: Dp, color: Color, weight: FontWeight, tag: String) {
    BasicText(text, Modifier.offset(x, top).testTag(tag), style = ShellType.body.copy(color = color, fontWeight = weight), maxLines = 1)
}

@Composable
private fun Caption(text: String, x: Dp, top: Dp, color: Color) {
    BasicText(text, Modifier.offset(x, top), style = ShellType.caption.copy(color = color), maxLines = 1)
}

/** An icon-over-label button (8.5 / 8.11): a 51.6-epx cell centred on [centreX]. */
@Composable
private fun ToastButton(glyph: String, label: String, centreX: Dp, glyphTop: Dp, labelTop: Dp, tag: String, onClick: () -> Unit) {
    val w = 51.6.dp
    Box(
        Modifier.offset(x = centreX - w / 2, y = glyphTop - 4.dp).width(w).height(labelTop - glyphTop + 18.dp)
            .testTag(tag).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        BasicText(glyph, Modifier.align(Alignment.TopCenter).offset(y = 4.dp),
            style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center))
        BasicText(label, Modifier.align(Alignment.TopCenter).offset(y = labelTop - glyphTop + 4.dp - 2.dp),
            style = ShellType.caption.copy(color = Color.White, textAlign = TextAlign.Center))
    }
}

/** The grab handle "=" (8.6 / 8.9): 16 epx wide, centred. */
@Composable
private fun Handle(top: Dp) {
    Layout({
        Column(Modifier.width(16.6.dp)) {
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color(160, 160, 160)))
            Box(Modifier.height(2.5.dp))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color(160, 160, 160)))
        }
    }, Modifier.fillMaxWidth().offset(y = top)) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0))
        layout(constraints.maxWidth, p.height) { p.place((constraints.maxWidth - p.width) / 2, 0) }
    }
}

/** The full-window host both the locked activity and the overlay draw: the toast at the top, touches consumed. */
@Composable
fun RingToastHost(ring: RingService.Ring, topInset: Dp) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        RingToast(
            ring, topInset,
            onSnooze = { minutes -> RingService.act(context, RingService.ACTION_SNOOZE, minutes) },
            onDismiss = { RingService.act(context, RingService.ACTION_DISMISS) },
        )
    }
}

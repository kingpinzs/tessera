package app.tileshell.recorder

import androidx.compose.animation.core.Animatable
import app.tileshell.ui.MotionTrace
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.tileshell.bars.BarMetrics
import app.tileshell.brand.Brand
import app.tileshell.cortana.ui.CortanaEaseOut
import app.tileshell.cortana.ui.CortanaUi
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType

/**
 * Every value Voice Recorder draws, each with where it came from (phase 15 Decisions "Fidelity"): an
 * r11/voice-recorder.md section, a value this build already measured (R7, R3), or a tagged approximation with
 * its NEEDS-HUMAN row. The drawn bars are phase 01's (`BarMetrics`), never a literal here.
 */
object RecorderMetrics {
    // ---- the record state (r11 §2, LOW; U9 for a tall canvas) --------------------------------------------------

    /** 2.1 / 2.2: the record disc, and the stop disc it becomes, 96 epx; it neither moves nor resizes. */
    const val DISC_EPX = 96f

    /** 2.5: the microphone ≈22 × 34 epx; Fluent's mic is 0.8 em tall, so 34 / 0.8. */
    const val DISC_MIC_FONT = 42.5f

    /** 2.6: the stop square ≈21 epx; Fluent's stop is 0.7 em, so 21 / 0.7. */
    const val DISC_STOP_FONT = 30f

    /** 2.9: timer digits 24 epx tall, the subheader class (34 epx Light). */
    const val TIMER_FONT = 34f

    /** 2.10: the timer's digit centre 141.5 epx above the disc centre. */
    const val TIMER_ABOVE_DISC = 141.5f

    /** 2.12: pause and flag ±46.7 epx either side of W/2, their row 139 epx below the disc centre. */
    const val CONTROLS_DX = 46.7f
    const val CONTROLS_BELOW_DISC = 139f

    /** 2.12: pause 9 × 16 epx (Fluent's pause is 0.8 em tall: 16 / 0.8) and flag 17 × 20 epx (0.75 em: 20 / 0.75). */
    const val PAUSE_FONT = 20f
    const val FLAG_FONT = 26.7f

    /** A control's touch target (R7 §3.5.8's 48-epx button). */
    const val CONTROL_TOUCH = 48f

    /** 2.14: the markers row 45 epx above the bottom; flag 13 × 15 epx (15 / 0.75) then the time in body. */
    const val MARKERS_ABOVE_BOTTOM = 45f
    const val MARKER_FLAG_FONT = 20f

    /** 4.2: marker entries at a 63.5-epx pitch (the one measured pitch; used for both pages, approximation H21). */
    const val MARKER_PITCH = 63.5f

    /** 2.7: two 1-epx rings, inner 109–122 and outer 123–150 epx across, following the level. */
    const val RING_INNER_MIN = 109f
    const val RING_INNER_MAX = 122f
    const val RING_OUTER_MIN = 123f
    const val RING_OUTER_MAX = 150f
    const val RING_STROKE = 1f

    /** 7.2: the rings appear ≈467 ms after the stop state. */
    const val RINGS_APPEAR_MS = 467L

    /**
     * R7 §3.5.9 (2.11): the empty line, white subtitle at x 11.7, cap top 70.4 epx from the screen top — which
     * includes the drawn status bar, so inside the content area it sits that much less the bar (phase 01's value).
     */
    const val EMPTY_LEFT = 11.7f
    val EMPTY_CAP_BELOW_BAR = 70.4f - BarMetrics.STATUS_EPX

    // ---- the list state (r11 §3, MEDIUM) ---------------------------------------------------------------------

    /** 3.1: the search box, 32 epx tall with 12-epx margins, the list's first element. */
    const val SEARCH_EPX = 32f
    const val SIDE = 12f

    /** 3.2: the "Showing …" line in body under the box (its block height is an approximation, H20). */
    const val SHOWING_BLOCK = 36f
    const val SHOWING_CAP_TOP = 12f

    /**
     * 3.4: two-line rows at a 56-epx pitch, line 2 22 epx below line 1's cap top; 3.5: header cap top → first
     * row cap top 36 epx, last row cap top → next header cap top 60 epx. Those two give a 40-epx header block
     * whose cap sits 4 epx lower than a row's; the row's own cap top (12) centres its two lines (derived, H3).
     */
    const val ROW_EPX = 56f
    const val ROW_CAP_TOP = 12f
    const val LINE2_BELOW_CAP = 22f
    const val HEADER_BLOCK = 40f
    const val HEADER_CAP_TOP = 16f

    /** 3.3: the 1-epx rule 19 epx below the header's cap top, 12-epx margins. */
    const val RULE_BELOW_CAP = 19f

    /** 3.8: the docked button, an accent disc ≈76 epx, its centre 63 epx above the nav bar's top. */
    const val DOCKED_EPX = 76f
    const val DOCKED_ABOVE_NAV = 63f

    /** 3.9: its microphone ≈18 × 26 epx: 26 / 0.8. */
    const val DOCKED_MIC_FONT = 32.5f

    /** 2.16 / 3.10: the minimal app bar, 24 epx, dots only, the dots' centre ≈12 epx above the nav bar. */
    const val MINIMAL_BAR = 24f

    // ---- the playback page (r11 §4, MEDIUM unless said) -------------------------------------------------------

    /** 4.1: the name's cap top under the status bar — no phone value; D1's pane puts it 20 epx under its top (H3). */
    const val NAME_CAP_TOP = 20f

    /** 4.1: the date line's cap top 22 epx below the name's. */
    const val DATE_BELOW_NAME = 22f

    /** 4.2: the markers row between the header and the disc; its offset under the date is an approximation (H21). */
    const val MARKERS_BELOW_DATE = 36f

    /** 4.3: the play disc 96 epx; its outlined play triangle 22 × 30 epx (30 / 0.7). */
    const val PLAY_DISC = 96f
    const val PLAY_FONT = 42.9f

    /** No phone value places the disc; its centre at 45 % of the content height (approximation, H3). */
    const val DISC_AT = 0.45f

    /** 4.4: the flag 99 epx below the disc centre (A, LOW). */
    const val PLAY_FLAG_BELOW_DISC = 99f

    /** 4.5: track 2 epx, thumb a hollow 24-epx accent ring, marker dots 6 epx. */
    const val TRACK = 2f
    const val THUMB = 24f
    const val THUMB_STROKE = 2f
    const val MARKER_DOT = 6f

    /** 4.6 (A, LOW): labels inset 12, a 32-epx label box, 22.6 epx label ↔ track, the track 75 epx above the app bar. */
    const val LABEL_INSET = 12f
    const val LABEL_BOX = 32f
    const val LABEL_GAP = 22.6f
    const val TRACK_ABOVE_APPBAR = 75f

    // ---- colours (r11 §5; U8 for the dark theme) ------------------------------------------------------------

    /** U8: the play disc SystemChromeMedium dark (31,31,31). */
    val PLAY_DISC_FILL = Color(0xFF1F1F1F)

    /** U8: secondary text and the timer's zero fields white at 60 % / 20 %. */
    val SECONDARY = Color(0x99FFFFFF)
    val TIMER_ZERO = Color(0x33FFFFFF)

    /** U8 (approximation): the unplayed track and the header rule at SystemBaseLow, white 20 %. */
    val UNPLAYED = Color(0x33FFFFFF)
    val RULE = Color(0x33FFFFFF)

    /** 7.1: a disc starting up is grey (disabled) until capture runs. */
    val DISC_DISABLED = Color(0xFF5A5A5A)

    // ---- flyouts and dialogs (R7) -------------------------------------------------------------------------------

    /** R7 §2.2.5 (HIGH): 242.6 epx wide, 44-epx items, text inset 14 epx. */
    const val FLYOUT_W = 242.6f
    const val FLYOUT_ITEM = 44f
    const val FLYOUT_INSET = 14f

    /** R7 §3.6.2: (40,40,40) fill, a 1-epx (71,76,70) border, 8-epx padding top and bottom. */
    val FLYOUT_FILL = Color(0xFF282828)
    val FLYOUT_BORDER = Color(0xFF474C46)
    const val FLYOUT_PAD = 8f

    /** R7 §1.3.9: the top-anchored dialog, 194 epx tall from the screen top, fill (74,74,74), page dimmed to (2,2,2). */
    const val DIALOG_EPX = 194f
    val DIALOG_FILL = Color(0xFF4A4A4A)
    val DIALOG_DIM = Color(0xFF020202)
}

/** A glyph from the branding module's icon font at [sizeEpx], centred in its own em box (no text line height). */
@Composable
fun RecorderGlyph(glyph: String, sizeEpx: Float, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        glyph,
        modifier = modifier,
        style = TextStyle(fontFamily = Brand.iconFont, fontSize = sizeEpx.sp, color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
    )
}

/** Top padding that puts [style]'s cap top [capTopEpx] below its box's top (the shell's Selawik metrics). */
fun capPad(capTopEpx: Float, style: TextStyle): Float {
    return CapMetrics.topPaddingForCapTop(capTopEpx, style.fontSize.value)
}

/** One item of a [RecorderFlyout]. */
data class FlyoutItem(val label: String, val tag: String, val onPick: () -> Unit)

/**
 * The W10M flyout (R7 §2.2.5 width, items and inset; §3.6.2 fill, border and padding): a bordered box, not full
 * width, over a scrim that closes it on any tap outside. It opens with R7 §3.6.4's grow, 233 ms ease-out from
 * its anchored edge — down from [topEpx] when it hangs under its anchor, up from [bottomEpx] when it rises above
 * it — and logs that motion on the shell's clock as `[motion] flyout` (C-5).
 *
 * [centreXEpx] centres it on the touch point (R7 §3.6.2), clamped inside the screen.
 */
@Composable
fun RecorderFlyout(
    tag: String,
    items: List<FlyoutItem>,
    centreXEpx: Float,
    onDismiss: () -> Unit,
    topEpx: Float? = null,
    bottomEpx: Float? = null,
    rightAlign: Boolean = false,
) {
    val colors = LocalShellColors.current
    val height = RecorderMetrics.FLYOUT_PAD * 2 + items.size * RecorderMetrics.FLYOUT_ITEM + 2f
    val grow = remember(tag, items.size) { Animatable(CortanaUi.MENU_FIRST_FRAME_HEIGHT) }
    LaunchedEffect(tag, items.size) {
        val trace = MotionTrace("flyout", android.os.SystemClock.uptimeMillis())
        coroutineScope {
            val frames = launch { while (true) withFrameNanos { trace.frame(it, grow.value) } }
            grow.animateTo(1f, tween(CortanaUi.MENU_GROW_MS, easing = CortanaEaseOut))
            withFrameNanos { trace.frame(it, grow.value) }
            frames.cancel()
        }
        Diagnostics.add("motion", trace.message())
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("${tag}_scrim")
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onDismiss()
                }
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val left = if (rightAlign) (w - RecorderMetrics.FLYOUT_W - 4f).coerceAtLeast(0f)
        else (centreXEpx - RecorderMetrics.FLYOUT_W / 2f).coerceIn(0f, (w - RecorderMetrics.FLYOUT_W).coerceAtLeast(0f))
        val shown = height * grow.value
        val top = when {
            bottomEpx != null -> (bottomEpx - shown)
            topEpx != null -> topEpx.coerceAtMost(h - height)
            else -> 0f
        }
        Box(
            Modifier
                .offset(x = left.dp, y = top.dp)
                .width(RecorderMetrics.FLYOUT_W.dp)
                .height(shown.dp)
                .clipToBounds()
                .background(RecorderMetrics.FLYOUT_FILL)
                .border(1.dp, RecorderMetrics.FLYOUT_BORDER)
                // Taps inside the box are the items'; the scrim must not read them as outside.
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                .testTag(tag),
        ) {
            Column(Modifier.padding(top = (1f + RecorderMetrics.FLYOUT_PAD).dp)) {
                items.forEach { item ->
                    PressRow(
                        onClick = item.onPick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RecorderMetrics.FLYOUT_ITEM.dp)
                            .testTag(item.tag)
                            .semantics { role = Role.Button },
                    ) {
                        BasicText(
                            item.label,
                            style = ShellType.body.copy(color = colors.text),
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = RecorderMetrics.FLYOUT_INSET.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * R7 §1.3.9's top-anchored dialog (U4 rename, U5 delete confirmation): full width, 194 epx from the screen top,
 * fill (74,74,74), the page below dimmed to (2,2,2), its two buttons side by side. The buttons are the shell's
 * existing dialog-button form (the secondary-tile prompt's, H22), as R7 measured none.
 */
@Composable
fun RecorderDialog(
    tag: String,
    title: String,
    confirmLabel: String,
    confirmTag: String,
    cancelTag: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    body: @Composable () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(RecorderMetrics.DIALOG_DIM)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(RecorderMetrics.DIALOG_EPX.dp)
                .background(RecorderMetrics.DIALOG_FILL)
                .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 12.dp)
                .testTag(tag),
        ) {
            BasicText(title, style = ShellType.subtitle.copy(color = Color.White), modifier = Modifier.testTag("${tag}_title"))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { body() }
            Row(Modifier.fillMaxWidth()) {
                DialogButton(confirmLabel, confirmTag, Modifier.weight(1f), onConfirm)
                Spacer(Modifier.width(10.dp))
                DialogButton("Cancel", cancelTag, Modifier.weight(1f), onCancel)
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        PressRow(onClick, Modifier.fillMaxWidth().height(34.dp).border(1.dp, Color.White.copy(alpha = 0.7f)).testTag(tag)) {
            BasicText(
                label,
                style = ShellType.body.copy(color = Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
        }
    }
}

/**
 * What the recorder has to say (a refusal, the storage floor, a failed save): a band under the status bar that
 * stays until the next tap or [NOTICE_MS] (W10M showed no such messages; approximation, H13 / H12).
 */
@Composable
fun NoticeBand(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF1F1F1F))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicText(text, style = ShellType.body.copy(color = Color.White), modifier = Modifier.testTag("rec_notice"))
    }
}

/** How long a notice stays when nothing is tapped. */
const val NOTICE_MS = 8_000L

/** A 1-epx ring of diameter [diameterEpx] centred in its box. */
@Composable
fun Ring(diameterEpx: Float, strokeEpx: Float, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(diameterEpx.dp)) {
        val stroke = strokeEpx * density
        drawCircle(color, radius = size.minDimension / 2f - stroke / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    }
}

/** A text whose cap top sits [capTopEpx] below the top of a full-width box, centred horizontally. */
@Composable
fun CentredCapText(text: String, style: TextStyle, capTopEpx: Float, tag: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = capPad(capTopEpx, style).dp), contentAlignment = Alignment.TopCenter) {
        BasicText(text, style = style, maxLines = 1, modifier = Modifier.testTag(tag))
    }
}

/** State a page keeps for an open flyout. */
class FlyoutState(val tag: String, val items: List<FlyoutItem>, val centreXEpx: Float, val topEpx: Float?, val bottomEpx: Float?, val rightAlign: Boolean = false)

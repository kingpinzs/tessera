package app.tileshell.cortana.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.cortana.PersonaState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

/**
 * Cortana's persona (phase 03 build task 2).
 *
 * Four measured forms, each with its own source:
 *  - idle / thinking ring: R3 A22 (MEDIUM) — the pop-in, the Y-axis rotation and the move to the top
 *  - listening: R6 §3.1.6–3.1.10 (MEDIUM) — a filled disc inside a translucent halo, in antiphase.
 *    R6 §3.1.8 SUPERSEDES A22's single-cycle 600 ms pulse; A22 stays the source for the idle ring.
 *  - speaking: R6 §3.2.1–3.2.2 (MEDIUM) — the small persona, its halo stepping with the voice's level
 *  - idle after speaking: R6 §3.2.4 (MEDIUM) — a hollow ring breathing at ≈3.75 s
 *
 * Everything drawn here is a number from those sections. The two approximations are the waveform bars
 * following the microphone level (H10) and the large centred persona never speaking (H9).
 */
object PersonaValues {
    // ---- R3 A22, the idle / thinking ring on the Cortana page (governing row, S2 15063) ----
    const val IDLE_OUTER_EPX = 70f
    const val IDLE_INNER_EPX = 48f
    const val IDLE_STROKE_EPX = 11f
    const val IDLE_CENTRE_Y_EPX = 244f

    /** A22: the thinking ring is smaller and rotates; its own row on S1 14393. */
    const val THINKING_OUTER_EPX = 79f
    const val THINKING_STROKE_EPX = 15f

    /** A22: a dot appears, reaches full width in 183 ms, and is a settled ring by ≈650 ms. */
    const val POP_GROW_MS = 183
    const val POP_TOTAL_MS = 650

    /** A22: the rotation about the vertical axis reaches edge-on in 367 ms and repeats every 917 ms. */
    const val ROTATE_TO_EDGE_MS = 367
    const val ROTATE_PERIOD_MS = 917

    /** A22: then it moves up to the top of the page in 217 ms as the result card appears. */
    const val MOVE_TO_TOP_MS = 217

    /** A22: the small ring at the top of the Cortana home page. */
    const val SMALL_OUTER_EPX = 52.5f
    const val SMALL_INNER_EPX = 26f
    const val SMALL_CENTRE_Y_EPX = 43f

    // ---- R6 §3.1.6-3.1.10, listening ----
    const val LISTEN_HALO_MIN_EPX = 85.8f
    const val LISTEN_HALO_MAX_EPX = 94.7f
    const val LISTEN_DISC_MAX_EPX = 41.1f
    const val LISTEN_DISC_MIN_EPX = 37.3f
    const val LISTEN_PERIOD_MS = 1040
    const val LISTEN_RISE_MS = 350
    const val LISTEN_TOP_HOLD_MS = 200
    const val LISTEN_FALL_MS = 350
    const val LISTEN_BOTTOM_HOLD_MS = 150
    const val LISTEN_CENTRE_Y_EPX = 243.8f
    const val LISTEN_ENTRANCE_MS = 333

    // ---- R6 §3.2.1-3.2.2, speaking; §3.2.3 awaiting; §3.2.4 idle after speaking ----
    const val SPEAK_DISC_EPX = 19.0f
    const val SPEAK_HALO_MIN_EPX = 34.5f
    const val SPEAK_HALO_MAX_EPX = 40.4f
    const val SPEAK_STEP_MS = 51
    const val SPEAK_CENTRE_Y_EPX = 40f

    const val AWAIT_DISC_MIN_EPX = 17.4f
    const val AWAIT_DISC_MAX_EPX = 19.2f
    const val AWAIT_HALO_MIN_EPX = 39.0f
    const val AWAIT_HALO_MAX_EPX = 43.0f
    const val AWAIT_PERIOD_MS = 1000

    const val AFTER_RING_MIN_EPX = 25.9f
    const val AFTER_RING_MAX_EPX = 29.1f
    const val AFTER_HALO_MAX_EPX = 38.9f
    const val AFTER_HALO_MIN_EPX = 34.6f
    const val AFTER_PERIOD_MS = 3750

    // ---- R6 §3.1.2-3.1.5, the waveform glyph inside the query box ----
    const val WAVE_WIDTH_EPX = 23f
    const val WAVE_HEIGHT_EPX = 14f
    const val WAVE_STEP_MS = 128
    const val WAVE_AFTER_TEXT_EPX = 5.4f
    const val WAVE_CENTRE_ABOVE_X_HEIGHT_EPX = 1.6f

    /**
     * The listening pulse's phase 0..1 through one 1.04 s cycle, as R6 §3.1.8 describes it: rise, a top
     * hold, fall, a bottom hold — four segments, not a sine. 0 = small, 1 = large.
     */
    fun listenPhase(elapsedMs: Long): Float {
        val t = (elapsedMs % LISTEN_PERIOD_MS).toFloat()
        return when {
            t < LISTEN_RISE_MS -> t / LISTEN_RISE_MS
            t < LISTEN_RISE_MS + LISTEN_TOP_HOLD_MS -> 1f
            t < LISTEN_RISE_MS + LISTEN_TOP_HOLD_MS + LISTEN_FALL_MS ->
                1f - (t - LISTEN_RISE_MS - LISTEN_TOP_HOLD_MS) / LISTEN_FALL_MS
            else -> 0f
        }
    }

    /** A22's rotation: the ring's apparent width is |cos| of the angle, edge-on at [ROTATE_TO_EDGE_MS]. */
    fun rotationScaleX(elapsedMs: Long): Float {
        val t = (elapsedMs % ROTATE_PERIOD_MS).toFloat()
        if (t > ROTATE_TO_EDGE_MS) return 1f
        val angle = (t / ROTATE_TO_EDGE_MS) * (PI.toFloat() / 2f)
        // A22 measured 74 -> 38 px, i.e. the apparent width never quite reaches zero: it stops at ≈60°.
        return max(abs(cos(angle)), 38f / 74f)
    }

    /** A simple breathing 0..1 for the two periodic non-level-driven forms (§3.2.3, §3.2.4). */
    fun breathe(elapsedMs: Long, periodMs: Int): Float {
        val t = (elapsedMs % periodMs).toFloat() / periodMs
        return if (t < 0.5f) t * 2f else (1f - t) * 2f
    }
}

/**
 * The large centred persona: idle, thinking and listening. It never speaks — every spoken reply is
 * shown on a response card, so the speaking form is the small one (H9, R6 §3.2.6 UNMEASURED).
 */
@Composable
fun LargePersona(state: PersonaState, level: Float, accent: Color, modifier: Modifier = Modifier) {
    val start = remember(state) { mutableLongStateOf(0L) }
    var elapsed by remember(state) { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        val first = withFrameMillis { it }
        start.longValue = first
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - first
        }
    }
    val boxEpx = PersonaValues.LISTEN_HALO_MAX_EPX
    Box(
        modifier.size(boxEpx.dp).testTag("cortana_persona_large_${state.name.lowercase()}"),
    ) {
        Canvas(Modifier.size(boxEpx.dp)) {
            when (state) {
                PersonaState.LISTENING -> drawListening(elapsed, level, accent)
                PersonaState.THINKING -> drawThinkingRing(elapsed, accent)
                else -> drawIdleRing(elapsed, accent)
            }
        }
    }
}

/** The small persona at the top of a response card (R6 §3.2.1–3.2.4). */
@Composable
fun SmallPersona(state: PersonaState, level: Float, accent: Color, modifier: Modifier = Modifier) {
    var elapsed by remember(state) { mutableLongStateOf(0L) }
    // R6 §3.2.1: the halo steps every 51 ± 17 ms with no period, following the voice's output level —
    // so it is sampled on a step clock, not interpolated between frames.
    var steppedLevel by remember(state) { mutableFloatStateOf(0f) }
    var lastStep by remember(state) { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        val first = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            elapsed = now - first
            if (now - lastStep >= PersonaValues.SPEAK_STEP_MS) {
                lastStep = now
                steppedLevel = level
            }
        }
    }
    val boxEpx = PersonaValues.AWAIT_HALO_MAX_EPX
    Box(modifier.size(boxEpx.dp).testTag("cortana_persona_small_${state.name.lowercase()}")) {
        Canvas(Modifier.size(boxEpx.dp)) {
            when (state) {
                PersonaState.SPEAKING -> drawSpeaking(steppedLevel, accent)
                PersonaState.LISTENING, PersonaState.THINKING -> drawAwaitingReply(elapsed, accent)
                else -> drawIdleAfterSpeaking(elapsed, accent)
            }
        }
    }
}

// ---------------- the drawing, all in epx ----------------

private fun DrawScope.centre() = Offset(size.width / 2f, size.height / 2f)

private fun DrawScope.epx(value: Float) = value * density

private fun DrawScope.drawIdleRing(elapsedMs: Long, accent: Color) {
    // A22's pop-in: a dot grows to full width in 183 ms, and the filled disc becomes a ring by 650 ms.
    val grow = min(1f, elapsedMs.toFloat() / PersonaValues.POP_GROW_MS)
    val hollow = ((elapsedMs - PersonaValues.POP_GROW_MS).toFloat() /
        (PersonaValues.POP_TOTAL_MS - PersonaValues.POP_GROW_MS)).coerceIn(0f, 1f)
    val outer = epx(PersonaValues.IDLE_OUTER_EPX) * LinearEasing.transform(grow)
    val stroke = epx(PersonaValues.IDLE_STROKE_EPX)
    // While the disc is still filling in, the stroke is half the radius (a disc) and thins to the ring.
    val effectiveStroke = outer / 2f + (stroke - outer / 2f) * hollow
    drawCircle(accent, radius = (outer - effectiveStroke) / 2f, centre(), style = Stroke(effectiveStroke))
}

private fun DrawScope.drawThinkingRing(elapsedMs: Long, accent: Color) {
    val scaleX = PersonaValues.rotationScaleX(elapsedMs)
    val outer = epx(PersonaValues.THINKING_OUTER_EPX)
    val stroke = epx(PersonaValues.THINKING_STROKE_EPX)
    // The Y-axis rotation is a horizontal squash of the ring, which is what A22 measured as an
    // apparent-width change rather than a real 3-D projection.
    val c = centre()
    scale(scaleX, 1f, c) {
        drawCircle(accent, radius = (outer - stroke) / 2f, c, style = Stroke(stroke))
    }
}

private fun DrawScope.drawListening(elapsedMs: Long, level: Float, accent: Color) {
    val phase = PersonaValues.listenPhase(elapsedMs)
    // R6 §3.1.7: halo and disc move in ANTIPHASE — the halo grows while the disc shrinks.
    val halo = PersonaValues.LISTEN_HALO_MIN_EPX +
        (PersonaValues.LISTEN_HALO_MAX_EPX - PersonaValues.LISTEN_HALO_MIN_EPX) * phase
    val disc = PersonaValues.LISTEN_DISC_MAX_EPX -
        (PersonaValues.LISTEN_DISC_MAX_EPX - PersonaValues.LISTEN_DISC_MIN_EPX) * phase
    val c = centre()
    drawCircle(accent.copy(alpha = 0.25f), radius = epx(halo) / 2f, c)
    drawCircle(accent, radius = epx(disc) / 2f, c)
}

private fun DrawScope.drawSpeaking(level: Float, accent: Color) {
    val halo = PersonaValues.SPEAK_HALO_MIN_EPX +
        (PersonaValues.SPEAK_HALO_MAX_EPX - PersonaValues.SPEAK_HALO_MIN_EPX) * level.coerceIn(0f, 1f)
    val c = centre()
    drawCircle(accent.copy(alpha = 0.25f), radius = epx(halo) / 2f, c)
    drawCircle(accent, radius = epx(PersonaValues.SPEAK_DISC_EPX) / 2f, c)
}

private fun DrawScope.drawAwaitingReply(elapsedMs: Long, accent: Color) {
    val phase = PersonaValues.breathe(elapsedMs, PersonaValues.AWAIT_PERIOD_MS)
    val disc = PersonaValues.AWAIT_DISC_MIN_EPX +
        (PersonaValues.AWAIT_DISC_MAX_EPX - PersonaValues.AWAIT_DISC_MIN_EPX) * phase
    val halo = PersonaValues.AWAIT_HALO_MAX_EPX -
        (PersonaValues.AWAIT_HALO_MAX_EPX - PersonaValues.AWAIT_HALO_MIN_EPX) * phase
    val c = centre()
    drawCircle(accent.copy(alpha = 0.25f), radius = epx(halo) / 2f, c)
    drawCircle(accent, radius = epx(disc) / 2f, c)
}

private fun DrawScope.drawIdleAfterSpeaking(elapsedMs: Long, accent: Color) {
    val phase = PersonaValues.breathe(elapsedMs, PersonaValues.AFTER_PERIOD_MS)
    val ring = PersonaValues.AFTER_RING_MIN_EPX +
        (PersonaValues.AFTER_RING_MAX_EPX - PersonaValues.AFTER_RING_MIN_EPX) * phase
    val halo = PersonaValues.AFTER_HALO_MAX_EPX -
        (PersonaValues.AFTER_HALO_MAX_EPX - PersonaValues.AFTER_HALO_MIN_EPX) * phase
    val stroke = epx(3f)
    val c = centre()
    drawCircle(accent.copy(alpha = 0.25f), radius = epx(halo) / 2f, c)
    drawCircle(accent, radius = (epx(ring) - stroke) / 2f, c, style = Stroke(stroke))
}

/**
 * R6 §3.1.2–3.1.5: the small waveform glyph inside the listening query box. Vertical bars mirrored
 * about a horizontal centre line, the WHOLE pattern replaced in discrete steps every 128 ± 15 ms with
 * no interpolation. The bar heights follow the microphone level — an approximation, since R6 does not
 * say what drives the shapes (H10).
 */
@Composable
fun ListeningWaveform(level: Float, modifier: Modifier = Modifier) {
    var pattern by remember { mutableFloatStateOf(0f) }
    var bars by remember { mutableFloatStateOf(0f) }
    val heights = remember { FloatArray(BAR_COUNT) }
    var lastStep by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = withFrameMillis { it }
            if (now - lastStep >= PersonaValues.WAVE_STEP_MS) {
                lastStep = now
                for (i in heights.indices) {
                    // A new pattern each step: the level sets the envelope, the shape inside it is random,
                    // which is what "the whole bar pattern is replaced in discrete steps" looks like.
                    heights[i] = (0.25f + Math.random().toFloat() * 0.75f) * level.coerceIn(0.05f, 1f)
                }
                pattern += 1f
                bars = heights.sum()
            }
        }
    }
    Canvas(
        modifier.size(PersonaValues.WAVE_WIDTH_EPX.dp, PersonaValues.WAVE_HEIGHT_EPX.dp)
            .testTag("cortana_waveform"),
    ) {
        @Suppress("UNUSED_EXPRESSION") pattern
        val barWidth = size.width / (BAR_COUNT * 2f - 1f)
        val centreY = size.height / 2f
        for (i in 0 until BAR_COUNT) {
            val half = (heights[i] * size.height / 2f).coerceAtLeast(barWidth / 2f)
            drawRect(
                WAVE_COLOUR,
                topLeft = Offset(i * barWidth * 2f, centreY - half),
                size = Size(barWidth, half * 2f),
            )
        }
    }
}

/** R6 §3.1.3: light grey on the white query box. */
private val WAVE_COLOUR = Color(0xFFB0B0B0)

/** 23 epx wide with mirrored bars: 8 bars and 7 gaps is the pattern the footage shows at that width. */
private const val BAR_COUNT = 8

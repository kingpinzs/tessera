package app.tileshell.cortana.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import app.tileshell.brand.Brand

/**
 * Tess's eye (Jeremy, 2026-09-21: "change the Cortana Icon to resemble Hal more but put a easter egg
 * of Cortana with it").
 *
 * Every measured number stays where R3 A22 and R6 §3.1–3.2 put it. What changed is the PAINT: the
 * circles the persona was already made of are now lit as HAL 9000's lens — a bright specular core, a
 * glowing iris, a deep rim — instead of a flat accent fill. Diameters, strokes, periods, phases and
 * centres are untouched, which is why E4 still measures the same persona (persona.py follows the lens
 * hue instead of the accent hue; same method, same half-intensity edges).
 *
 * The easter egg: Cortana is still in there. Three taps on the large persona inside [TAP_WINDOW_MS]
 * open the lens back into her ring — the accent colour, the original flat fill, no gradient at all —
 * for [REVEAL_MS], and then it closes again. The reveal is a plain cross-fade of the lens tones to the
 * accent, so at reveal = 1 the drawing IS the pre-HAL one, byte for byte.
 */
object LensValues {
    /** The specular core, as a fraction of the lens radius: bright out to here, then the iris. */
    const val CORE_RATIO = 0.18f

    /** Where the iris glow peaks, and where it has fallen to the rim tone. */
    const val GLOW_RATIO = 0.34f
    const val IRIS_RATIO = 0.70f

    /** Three taps inside this window open the lens (the easter egg). */
    const val TAPS_TO_REVEAL = 3
    const val TAP_WINDOW_MS = 1500L

    /** How long Cortana stays, and the cross-fade at each end of it. */
    const val REVEAL_MS = 5000L
    const val FADE_MS = 300L

    /**
     * The tap history. Pure, so the easter egg is unit-testable without a device — which matters here,
     * because this AVD's synthetic input cannot be trusted to deliver a tap at all.
     */
    data class Egg(val taps: Int = 0, val lastTapMs: Long = 0L, val revealUntilMs: Long = 0L)

    /** Folds one tap in. The third tap inside the window starts the reveal and resets the count. */
    fun tap(egg: Egg, nowMs: Long): Egg {
        val continues = egg.taps > 0 && nowMs - egg.lastTapMs <= TAP_WINDOW_MS
        val taps = if (continues) egg.taps + 1 else 1
        return if (taps >= TAPS_TO_REVEAL) Egg(0, nowMs, nowMs + REVEAL_MS)
        else Egg(taps, nowMs, egg.revealUntilMs)
    }

    /** 0 = HAL's lens, 1 = Cortana's ring, with a [FADE_MS] ramp at each end of the reveal. */
    fun revealFraction(egg: Egg, nowMs: Long): Float {
        val left = egg.revealUntilMs - nowMs
        if (left <= 0L) return 0f
        val since = REVEAL_MS - left
        if (since < 0L) return 0f
        val rising = (since.toFloat() / FADE_MS).coerceIn(0f, 1f)
        val falling = (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
        return minOf(rising, falling)
    }
}

// ---------------- the lens, painted at whatever geometry the persona hands it ----------------

/** The lens tones all sit on one hue line so a colour search finds the whole lens (see persona.py). */
private fun tone(hal: Color, accent: Color, reveal: Float) = lerp(hal, accent, reveal)

/**
 * A filled lens of [diameterPx] centred at [centre]: what the listening, speaking and awaiting discs
 * are drawn as. The outermost tone is opaque right up to the edge, so the edge sits exactly where the
 * flat disc's did.
 */
fun DrawScope.drawLensDisc(centre: Offset, diameterPx: Float, accent: Color, reveal: Float) {
    val radius = diameterPx / 2f
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to tone(Brand.LENS_CORE, accent, reveal),
            LensValues.CORE_RATIO to tone(Brand.LENS_CORE, accent, reveal),
            LensValues.GLOW_RATIO to tone(Brand.LENS_GLOW, accent, reveal),
            LensValues.IRIS_RATIO to tone(Brand.LENS_IRIS, accent, reveal),
            1f to tone(Brand.LENS_RIM, accent, reveal),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/**
 * The lens seen edge-on: a ring of [strokePx] whose outer diameter is [outerPx], lit from its inner
 * edge outwards. The hole stays empty — A22 and R6 §3.2.4 both measure an inner diameter there.
 */
fun DrawScope.drawLensRing(
    centre: Offset,
    outerPx: Float,
    strokePx: Float,
    accent: Color,
    reveal: Float,
) {
    val radius = (outerPx - strokePx) / 2f
    if (radius <= 0f || strokePx <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to tone(Brand.LENS_GLOW, accent, reveal),
            1f to tone(Brand.LENS_RIM, accent, reveal),
            center = centre,
            radius = outerPx / 2f,
        ),
        radius = radius,
        center = centre,
        style = Stroke(strokePx),
    )
}

/** The bloom around the lens: R6's 25 % halo, in the iris tone instead of the accent. */
fun DrawScope.drawLensHalo(centre: Offset, diameterPx: Float, accent: Color, reveal: Float) {
    drawCircle(tone(Brand.LENS_IRIS, accent, reveal).copy(alpha = 0.25f), diameterPx / 2f, centre)
}

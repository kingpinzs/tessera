package app.tileshell.cortana.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * R7 §3.2's destination transition, as a pure curve so it can be unit-tested off a device.
 *
 * §3.2.1 fully black for 283–300 ms, §3.2.2 then an ease-out fade of the whole page over 200–317 ms
 * whose **first frame is already 40–60 % of the way up** (N1's series starts at 43 % of its settled
 * luminance, N2's at 61 %). The fade therefore does not start at zero: it starts at
 * [CortanaUi.FADE_FIRST_ALPHA] and eases from there.
 *
 * R7 gives per-frame luminance, not a named curve (the same situation as R3 A12 in
 * [app.tileshell.ui.motion.Motion]), so the ease-out shape itself is an approximation: a quadratic
 * ease-out through the measured end points.
 */
object CortanaFade {

    /** Quadratic ease-out. Approximation — R7 §3.2.2 measures luminance per frame, not a curve. */
    fun easeOut(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped)
    }

    /** Page alpha [elapsedMs] into the fade (i.e. after the black hold has ended). */
    fun fadeAlpha(elapsedMs: Float): Float =
        CortanaUi.FADE_FIRST_ALPHA +
            (1f - CortanaUi.FADE_FIRST_ALPHA) * easeOut(elapsedMs / CortanaUi.FADE_MS)

    /** Page alpha [elapsedMs] after the destination was chosen, black hold included. */
    fun alphaFromChoice(elapsedMs: Float): Float =
        if (elapsedMs < CortanaUi.BLACK_MS) 0f else fadeAlpha(elapsedMs - CortanaUi.BLACK_MS)

    /** The whole transition, black hold plus fade. */
    const val TOTAL_MS: Int = CortanaUi.BLACK_MS + CortanaUi.FADE_MS
}

/** [CortanaFade.easeOut] as a Compose easing, so every "ease-out" on these pages is the same curve. */
val CortanaEaseOut: Easing = Easing { CortanaFade.easeOut(it) }

/**
 * False while a destination page is still black or fading in.
 *
 * R7 §3.2.3: "page content has no animation of its own" — the header, app bar and group headers show
 * *during* the fade and the rows arrive in one frame at its end. The page reads this to hold its rows
 * back; it defaults to true so a page rendered outside the host (a preview, a test) is complete.
 */
val LocalCortanaPageRevealed = compositionLocalOf { true }

/**
 * Runs R7 §3.2's destination transition for [key]: black for 283-300 ms, then the page fades up
 * ease-out over 200-317 ms with its first frame already 40-60 % bright.
 *
 * The content is composed throughout (so its first visible frame is already laid out, matching
 * §3.2.3's "pops in without animation") and drawn at [CortanaFade]'s alpha over black.
 */
@Composable
fun CortanaDestinationHost(key: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    var revealed by remember { mutableStateOf(false) }
    var black by remember { mutableStateOf(true) }

    LaunchedEffect(key) {
        revealed = false
        black = true
        progress.snapTo(0f)
        delay(CortanaUi.BLACK_MS.toLong())
        black = false
        // Linear progress through the fade window; the shape lives in CortanaFade, one source of truth.
        progress.animateTo(1f, tween(durationMillis = CortanaUi.FADE_MS, easing = LinearEasing))
        revealed = true
    }

    Box(modifier.background(Color.Black)) {
        if (!black) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = CortanaFade.fadeAlpha(progress.value * CortanaUi.FADE_MS) },
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalCortanaPageRevealed provides revealed,
                    content = content,
                )
            }
        }
    }
}

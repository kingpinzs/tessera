package app.tileshell.cortana.ui

import androidx.compose.ui.graphics.Color
import app.tileshell.brand.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lens and its easter egg (Jeremy, 2026-09-21).
 *
 * The tap logic is tested here rather than on the device on purpose: this AVD's synthetic input cannot
 * be relied on to deliver a tap at all, and an easter egg nobody can trigger in QA is still supposed to
 * be right. The colour tests are the other half of the promise the lens makes — that the persona's
 * MEASURED geometry survives, because only the paint changed, and that a full reveal is the pre-HAL
 * drawing exactly.
 */
class LensTest {

    private val accent = Color(0xFF0078D7)   // Windows "Default Blue", phase 01 X26

    // ---- the easter egg ----

    @Test
    fun `three taps inside the window open the lens`() {
        var egg = LensValues.Egg()
        egg = LensValues.tap(egg, 1_000L)
        assertEquals(0f, LensValues.revealFraction(egg, 1_000L))
        egg = LensValues.tap(egg, 1_400L)
        assertEquals(0f, LensValues.revealFraction(egg, 1_400L))
        egg = LensValues.tap(egg, 1_900L)
        assertEquals(1_900L + LensValues.REVEAL_MS, egg.revealUntilMs)
    }

    @Test
    fun `a tap after the window starts the count again`() {
        var egg = LensValues.Egg()
        egg = LensValues.tap(egg, 1_000L)
        egg = LensValues.tap(egg, 1_000L + LensValues.TAP_WINDOW_MS + 1L)
        assertEquals(1, egg.taps)
        egg = LensValues.tap(egg, 1_000L + LensValues.TAP_WINDOW_MS + 2L)
        assertEquals(2, egg.taps)
        assertEquals(0L, egg.revealUntilMs)
    }

    @Test
    fun `two taps never open it`() {
        var egg = LensValues.Egg()
        egg = LensValues.tap(egg, 0L)
        egg = LensValues.tap(egg, 100L)
        assertEquals(0f, LensValues.revealFraction(egg, 100L))
        assertEquals(0f, LensValues.revealFraction(egg, 3_000L))
    }

    @Test
    fun `the reveal fades in, holds Cortana, and fades out`() {
        var egg = LensValues.Egg()
        egg = LensValues.tap(egg, 0L)
        egg = LensValues.tap(egg, 10L)
        egg = LensValues.tap(egg, 20L)
        val opened = 20L
        assertEquals(0f, LensValues.revealFraction(egg, opened), 0.001f)
        assertEquals(0.5f, LensValues.revealFraction(egg, opened + LensValues.FADE_MS / 2), 0.01f)
        assertEquals(1f, LensValues.revealFraction(egg, opened + LensValues.FADE_MS), 0.001f)
        assertEquals(1f, LensValues.revealFraction(egg, opened + LensValues.REVEAL_MS / 2), 0.001f)
        assertEquals(
            0.5f,
            LensValues.revealFraction(egg, opened + LensValues.REVEAL_MS - LensValues.FADE_MS / 2),
            0.01f,
        )
        assertEquals(0f, LensValues.revealFraction(egg, opened + LensValues.REVEAL_MS), 0.001f)
        assertEquals(0f, LensValues.revealFraction(egg, opened + LensValues.REVEAL_MS + 5_000L), 0.001f)
    }

    // ---- the tones ----

    @Test
    fun `every lens tone but the core sits on one hue line, so a colour search finds the whole lens`() {
        // persona.py normalises by the red channel and allows 26 per channel; the rim is the reference.
        val rim = Brand.LENS_RIM
        for (tone in listOf(Brand.LENS_IRIS, Brand.LENS_GLOW, Brand.LENS_RIM)) {
            val scale = tone.red / rim.red
            assertTrue("$tone is red-dominant", tone.red > tone.green && tone.green > tone.blue)
            assertTrue(
                "$tone green is off the hue line",
                Math.abs(tone.green * 255f - rim.green * 255f * scale) <= 26f,
            )
            assertTrue(
                "$tone blue is off the hue line",
                Math.abs(tone.blue * 255f - rim.blue * 255f * scale) <= 26f,
            )
        }
        // The specular core is deliberately OFF the line: it reads white, and the measurement skips it.
        assertTrue(Brand.LENS_CORE.green > 0.8f)
    }

    @Test
    fun `the halo stays below the score the disc measurement cuts at`() {
        // persona.py: score = min(1, r/255*4), disc = the run at or above 0.85 of the peak. The halo is
        // the iris at 25 % over black, so it must land under that cut or it would be measured as disc.
        val irisScore = minOf(1f, Brand.LENS_IRIS.red * 4f)
        val haloScore = minOf(1f, Brand.LENS_IRIS.red * 0.25f * 4f)
        assertEquals(1f, irisScore, 0.001f)
        assertTrue("halo $haloScore must be under 0.85", haloScore < 0.85f)
        // And it must still be over the half-peak the halo's own EDGE is read at.
        assertTrue("halo $haloScore must be over 0.5", haloScore > 0.5f)
    }

    @Test
    fun `a full reveal is the pre-HAL drawing - every lens tone becomes the flat accent`() {
        for (tone in listOf(Brand.LENS_RIM, Brand.LENS_IRIS, Brand.LENS_GLOW, Brand.LENS_CORE)) {
            assertEquals(accent, androidx.compose.ui.graphics.lerp(tone, accent, 1f))
            assertNotEquals(accent, androidx.compose.ui.graphics.lerp(tone, accent, 0f))
        }
    }
}

package app.tileshell.start

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Weather tile's animated main face (INDEX Change Log 2026-09-21 item 2), motion half.
 *
 * The properties here are the ones a wrong number breaks invisibly on a device: a phase that touches 1
 * flickers a drop for one frame, a scatter that is not stable boils the whole field, a particle count that
 * does not follow the tile's size buries a 1-unit tile or leaves a 4x2 one empty, and a strike that fires
 * every frame is a strobe. None of that shows up in a screenshot, so it is checked here.
 */
class SkyMotionTest {

    // ---- phase ----

    @Test
    fun `phase stays inside 0 until 1 across many periods`() {
        for (ms in 0L..20_000L step 7L) {
            val p = SkyMotion.phase(ms, 1_050)
            assertTrue("$ms gave $p", p >= 0f && p < 1f)
        }
    }

    @Test
    fun `phase runs from 0 to just under 1 and wraps`() {
        assertEquals(0f, SkyMotion.phase(0L, 1_000), 1e-6f)
        assertEquals(0.5f, SkyMotion.phase(500L, 1_000), 1e-6f)
        assertEquals(0f, SkyMotion.phase(1_000L, 1_000), 1e-6f)
        assertEquals(0.25f, SkyMotion.phase(3_250L, 1_000), 1e-6f)
    }

    @Test
    fun `an offset starts the loop part way in, and wraps like the rest`() {
        assertEquals(0.25f, SkyMotion.phase(0L, 1_000, 0.25f), 1e-6f)
        assertEquals(0.1f, SkyMotion.phase(850L, 1_000, 0.25f), 1e-5f)
    }

    @Test
    fun `a zero period never divides by zero`() {
        assertEquals(0f, SkyMotion.phase(1_234L, 0), 0f)
    }

    // ---- triangle ----

    @Test
    fun `triangle rises to one at the middle and returns`() {
        assertEquals(0f, SkyMotion.triangle(0f), 1e-6f)
        assertEquals(1f, SkyMotion.triangle(0.5f), 1e-6f)
        assertEquals(0f, SkyMotion.triangle(1f), 1e-6f)
        assertEquals(0.5f, SkyMotion.triangle(0.25f), 1e-6f)
        assertEquals(0.5f, SkyMotion.triangle(0.75f), 1e-6f)
    }

    // ---- scatter ----

    @Test
    fun `scatter is a fraction, stable, and different per index and per salt`() {
        val first = (0 until 64).map { SkyMotion.scatter(it, SkyMotion.SALT_DROP_X) }
        first.forEachIndexed { i, v -> assertTrue("index $i gave $v", v in 0f..1f) }
        // Stable: the frame loop redraws from elapsed alone, so the same index must give the same column.
        assertEquals(first, (0 until 64).map { SkyMotion.scatter(it, SkyMotion.SALT_DROP_X) })
        // Scattered: a field where every drop shares a column is one drop.
        assertTrue("only ${first.distinct().size} distinct columns", first.distinct().size > 50)
        // Two families do not overlay each other.
        assertNotEquals(first, (0 until 64).map { SkyMotion.scatter(it, SkyMotion.SALT_DROP_Y) })
    }

    @Test
    fun `scatter spreads over the whole width, not one corner of it`() {
        val values = (0 until 200).map { SkyMotion.scatter(it, SkyMotion.SALT_STAR) }
        assertTrue("min ${values.min()}", values.min() < 0.1f)
        assertTrue("max ${values.max()}", values.max() > 0.9f)
        assertTrue("mean ${values.average()}", values.average() in 0.35..0.65)
    }

    // ---- particles ----

    @Test
    fun `a particle never leaves its 0 until 1 travel`() {
        for (i in 0 until 20) {
            for (ms in 0L..6_000L step 13L) {
                val y = SkyMotion.particlePhase(ms, i, SkyMotion.RAIN_FALL_MS, SkyMotion.SALT_DROP_Y)
                assertTrue("particle $i at $ms gave $y", y >= 0f && y < 1f)
            }
        }
    }

    @Test
    fun `particles do not fall in step`() {
        val atOnce = (0 until 12).map { SkyMotion.particlePhase(0L, it, SkyMotion.RAIN_FALL_MS, SkyMotion.SALT_DROP_Y) }
        assertTrue("only ${atOnce.distinct().size} distinct phases", atOnce.distinct().size >= 11)
    }

    @Test
    fun `sway stays inside its lane`() {
        for (i in 0 until 10) {
            for (ms in 0L..5_000L step 17L) {
                val s = SkyMotion.sway(ms, i)
                assertTrue("flake $i at $ms swayed $s", s >= -1.0001f && s <= 1.0001f)
            }
        }
    }

    @Test
    fun `heavier weather falls faster, and a period is never zero`() {
        val light = SkyMotion.fallPeriod(SkyMotion.RAIN_FALL_MS, 0f)
        val middle = SkyMotion.fallPeriod(SkyMotion.RAIN_FALL_MS, 0.5f)
        val heavy = SkyMotion.fallPeriod(SkyMotion.RAIN_FALL_MS, 1f)
        assertTrue("$light > $middle > $heavy", light > middle && middle > heavy)
        assertTrue(heavy > 0)
        // Out-of-range intensities are clamped, never inverted into a negative period.
        assertEquals(light, SkyMotion.fallPeriod(SkyMotion.RAIN_FALL_MS, -3f))
        assertEquals(heavy, SkyMotion.fallPeriod(SkyMotion.RAIN_FALL_MS, 7f))
    }

    @Test
    fun `the particle count follows the tile's own size`() {
        // The three sizes the Weather tile can be, in epx on the 360-epx canvas (StartGrid at 3 columns).
        val small = SkyMotion.particleCount(55f, 55f, 1f, SkyMotion.RAIN_DENSITY)
        val medium = SkyMotion.particleCount(114f, 114f, 1f, SkyMotion.RAIN_DENSITY)
        val wide = SkyMotion.particleCount(232f, 114f, 1f, SkyMotion.RAIN_DENSITY)
        assertTrue("$small < $medium < $wide", small < medium && medium <= wide)
        assertTrue("a small tile still has weather in it: $small", small >= SkyMotion.MIN_PARTICLES)
        assertTrue("a wide tile stays cheap: $wide", wide <= SkyMotion.MAX_PARTICLES)
    }

    @Test
    fun `the particle count follows the intensity, and never empties the tile`() {
        val drizzle = SkyMotion.particleCount(114f, 114f, 0.35f, SkyMotion.RAIN_DENSITY)
        val downpour = SkyMotion.particleCount(114f, 114f, 1f, SkyMotion.RAIN_DENSITY)
        assertTrue("$drizzle < $downpour", drizzle < downpour)
        assertTrue(SkyMotion.particleCount(0f, 0f, 0f, SkyMotion.RAIN_DENSITY) >= SkyMotion.MIN_PARTICLES)
        assertTrue(SkyMotion.particleCount(4_000f, 4_000f, 1f, SkyMotion.RAIN_DENSITY) == SkyMotion.MAX_PARTICLES)
    }

    // ---- lightning ----

    @Test
    fun `lightning is an alpha, and it is dark most of the time`() {
        var lit = 0
        val samples = 0L until 3L * SkyMotion.STORM_CYCLE_MS step 4L
        var count = 0
        for (ms in samples) {
            count++
            val a = SkyMotion.strikeAlpha(ms)
            assertTrue("$ms gave $a", a in 0f..1f)
            if (a > 0f) lit++
        }
        assertTrue("lit for $lit of $count samples", lit.toFloat() / count < 0.25f)
        assertTrue("never lit at all", lit > 0)
    }

    @Test
    fun `every storm cycle gets exactly one strike, at its own moment`() {
        val moments = (0 until 5).map { cycle ->
            val base = cycle * SkyMotion.STORM_CYCLE_MS.toLong()
            (0 until SkyMotion.STORM_CYCLE_MS).filter { SkyMotion.strikeAlpha(base + it) > 0f }
        }
        moments.forEachIndexed { i, ms ->
            assertTrue("cycle $i never flashed", ms.isNotEmpty())
            // One run, not several: the last lit millisecond is within a strike of the first.
            assertTrue("cycle $i flashed twice", ms.last() - ms.first() <= SkyMotion.STRIKE_MS)
            assertTrue("cycle $i ran long", ms.size <= SkyMotion.STRIKE_MS + 1)
        }
        // Not a metronome: the strikes do not all land at the same offset.
        assertTrue("the storm ticks", moments.map { it.first() }.distinct().size > 1)
    }

    @Test
    fun `lightning is brightest the moment it strikes`() {
        val base = 0L
        val lit = (0 until SkyMotion.STORM_CYCLE_MS).filter { SkyMotion.strikeAlpha(base + it) > 0f }
        val first = lit.first()
        val peak = lit.maxByOrNull { SkyMotion.strikeAlpha(base + it) }!!
        assertTrue("peak at $peak, strike at $first", peak - first < SkyMotion.STRIKE_MS / 4)
    }

    @Test
    fun `a negative elapsed time never lights the tile`() {
        assertEquals(0f, SkyMotion.strikeAlpha(-5L), 0f)
    }

    // ---- the rainbow's envelope ----

    @Test
    fun `the bow draws itself in and then stays whole`() {
        assertEquals(0f, SkyMotion.rainbowSweep(0f), 1e-6f)
        assertTrue(SkyMotion.rainbowSweep(SkyMotion.RAINBOW_DRAWN_BY / 2f) in 0.4f..0.6f)
        assertEquals(1f, SkyMotion.rainbowSweep(SkyMotion.RAINBOW_DRAWN_BY), 1e-6f)
        assertEquals(1f, SkyMotion.rainbowSweep(1f), 1e-6f)
    }

    @Test
    fun `the bow fades in, holds and fades out inside one cycle`() {
        assertEquals(0f, SkyMotion.rainbowAlpha(0f), 1e-6f)
        assertEquals(1f, SkyMotion.rainbowAlpha(SkyMotion.RAINBOW_FADE_IN), 1e-6f)
        assertEquals(1f, SkyMotion.rainbowAlpha(0.5f), 1e-6f)
        assertEquals(1f, SkyMotion.rainbowAlpha(SkyMotion.RAINBOW_HOLD_UNTIL), 1e-6f)
        assertTrue(SkyMotion.rainbowAlpha(0.9f) < 1f)
        assertEquals(0f, SkyMotion.rainbowAlpha(1f), 1e-6f)
    }

    @Test
    fun `the bow's alpha is an alpha the whole way round`() {
        var p = 0f
        while (p <= 1f) {
            val a = SkyMotion.rainbowAlpha(p)
            assertTrue("$p gave $a", a in 0f..1f)
            p += 0.005f
        }
    }
}

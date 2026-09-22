package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules on the now-playing screen that are arithmetic rather than paint (phase 10 task 7).
 *
 * R8 §1.5 verified its thumb-travel rule against three screenshots by predicting where the thumb
 * would be and then measuring it. Those same predictions are the tests here, so the rule this build
 * implements is the rule R8 measured — not merely a rule that looks like it.
 */
class NowPlayingTest {

    // R8 §1.5 (V-2015, G1, native): track 46.00 -> 313.75 epx, thumb 18 epx across.
    private val trackLength = 267.75f
    private val radius = 9f
    private val trackLeft = 46.00f

    private fun centreEpx(positionMs: Long, durationMs: Long) =
        trackLeft + thumbCentreFraction(positionMs, durationMs, trackLength, radius) * trackLength

    @Test
    fun `R8's own G1 prediction - 0 colon 20 of 5 colon 53 puts the thumb centre at 69 point 15 epx`() {
        // R8 measured 69.1 and predicted 69.15; the rule has to land inside that.
        assertEquals(69.15, centreEpx(20_000L, 353_000L).toDouble(), 0.05)
    }

    @Test
    fun `at the very start the centre sits one radius in, never overhanging the track`() {
        assertEquals((trackLeft + radius).toDouble(), centreEpx(0L, 353_000L).toDouble(), 0.001)
    }

    @Test
    fun `at the very end it sits one radius short of the right end, for the same reason`() {
        assertEquals((trackLeft + trackLength - radius).toDouble(), centreEpx(353_000L, 353_000L).toDouble(), 0.001)
    }

    @Test
    fun `halfway is the middle of the TRAVEL, not the middle of the track`() {
        // The distinction the rule exists for: the travel is the track less a thumb diameter.
        assertEquals((trackLeft + trackLength / 2f).toDouble(), centreEpx(176_500L, 353_000L).toDouble(), 0.001)
    }

    @Test
    fun `a position past the end is clamped rather than run off the track`() {
        assertTrue(centreEpx(999_000L, 353_000L) <= trackLeft + trackLength - radius + 0.001f)
    }

    @Test
    fun `a track whose duration is not known yet parks the thumb at the start`() {
        // Media3 reports C.TIME_UNSET as a negative number until the media is prepared.
        assertEquals((trackLeft + radius).toDouble(), centreEpx(5_000L, 0L).toDouble(), 0.001)
        assertEquals((trackLeft + radius).toDouble(), centreEpx(5_000L, -9_223_372_036_854_775_807L).toDouble(), 0.001)
    }

    @Test
    fun `a track with no length at all does not divide by zero`() {
        assertEquals(0f, thumbCentreFraction(1_000L, 10_000L, 0f, 9f), 0f)
    }

    // ---- the labels ----

    @Test
    fun `the labels read the way R8's do`() {
        // R8 §1.5: "0:20" elapsed and "5:53" total, on G1.
        assertEquals("0:20", clockText(20_000L))
        assertEquals("5:53", clockText(353_000L))
    }

    @Test
    fun `seconds are always two digits, and minutes are not padded`() {
        assertEquals("0:00", clockText(0L))
        assertEquals("0:09", clockText(9_999L))
        assertEquals("10:00", clockText(600_000L))
        assertEquals("100:00", clockText(6_000_000L))
    }

    @Test
    fun `an unknown duration reads as zero rather than as a negative clock`() {
        assertEquals("0:00", clockText(-1L))
    }
}

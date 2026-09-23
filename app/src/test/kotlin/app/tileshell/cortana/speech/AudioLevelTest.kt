package app.tileshell.cortana.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The RMS -> 0..1 mapping behind the listening waveform's bars (H10) and the speaking halo.
 *
 * The bars are the only thing on screen that says the microphone is live, so the mapping has to be
 * anchored at both ends: a silent room must read 0 (still bars, not a twitch) and a clipped sample must
 * read 1 (a full bar, not an overflow).
 */
class AudioLevelTest {

    @Test
    fun `digital silence is zero`() {
        assertEquals(0f, AudioLevel.fromRms(0f), 0f)
        assertEquals(0f, AudioLevel.ofBlock(FloatArray(800), 800), 0f)
    }

    @Test
    fun `full scale is one`() {
        assertEquals(1f, AudioLevel.fromRms(1f), 0f)
        assertEquals(1f, AudioLevel.ofBlock(FloatArray(800) { 1f }, 800), 1e-6f)
        // A full-scale negative excursion is just as loud.
        assertEquals(1f, AudioLevel.ofBlock(FloatArray(800) { -1f }, 800), 1e-6f)
    }

    @Test
    fun `anything quieter than the floor is zero, and nothing exceeds one`() {
        assertEquals(0f, AudioLevel.fromRms(0.0005f), 0f) // -66 dBFS, below the -60 dB floor
        assertEquals(0f, AudioLevel.fromRms(-1f), 0f) // an RMS can never be negative; do not crash
        assertEquals(0f, AudioLevel.fromRms(Float.NaN), 0f)
        assertEquals(1f, AudioLevel.fromRms(4f), 0f) // clipping past full scale still reads 1
    }

    @Test
    fun `the floor sits exactly at -60 dBFS`() {
        // 0.001 == -60 dBFS: the first value that is on the scale at all.
        assertEquals(0f, AudioLevel.fromRms(0.001f), 1e-5f)
        assertTrue(AudioLevel.fromRms(0.0011f) > 0f)
    }

    @Test
    fun `the mapping is monotonic across the whole range`() {
        var previous = -1f
        var rms = 0.0005f
        var steps = 0
        while (rms <= 1f) {
            val level = AudioLevel.fromRms(rms)
            assertTrue("level fell from $previous to $level at rms=$rms", level >= previous)
            previous = level
            rms *= 1.05f
            steps++
        }
        assertTrue("the sweep has to actually cover the range", steps > 100)
        // The last step lands just short of full scale, so this is "nearly 1", not 1; `full scale is one`
        // is the test that pins the endpoint.
        assertTrue("the sweep should approach full scale, got $previous", previous > 0.99f)
    }

    @Test
    fun `half scale lands where the dBFS window says it should`() {
        // 0.5 == -6.02 dBFS -> (−6.02 + 60) / 60 ≈ 0.8997
        assertEquals(0.8997f, AudioLevel.fromRms(0.5f), 1e-3f)
        // and -30 dBFS, the middle of the window, is 0.5
        assertEquals(0.5f, AudioLevel.fromRms(0.0316228f), 1e-3f)
    }

    @Test
    fun `rms is the root mean square of the samples that were actually read`() {
        val block = floatArrayOf(1f, -1f, 1f, -1f, 0f, 0f, 0f, 0f)
        // Only the first four samples were read: RMS is 1, not the 0.707 of the whole buffer.
        assertEquals(1f, AudioLevel.rms(block, 4), 1e-6f)
        assertEquals(sqrt(0.5).toFloat(), AudioLevel.rms(block, 8), 1e-6f)
        assertEquals(0f, AudioLevel.rms(block, 0), 0f)
    }

    @Test
    fun `rms honours the offset, so a level can be taken from the middle of a chunk`() {
        val chunk = floatArrayOf(0f, 0f, 0f, 0f, 1f, -1f, 1f, -1f)
        assertEquals(0f, AudioLevel.rms(chunk, 4, offset = 0), 0f)
        assertEquals(1f, AudioLevel.rms(chunk, 4, offset = 4), 1e-6f)
        assertEquals(1f, AudioLevel.ofBlock(chunk, 4, offset = 4), 1e-6f)
        assertEquals(0f, AudioLevel.ofBlock(chunk, 4, offset = 0), 0f)
    }

    @Test
    fun `digital silence is never speech`() {
        assertEquals(false, AudioLevel.heardSpeech(List(40) { -120f }, 50))
    }

    @Test
    fun `a steady quiet room is not speech`() {
        val room = List(40) { if (it % 3 == 0) -52f else -55f }
        assertEquals(false, AudioLevel.heardSpeech(room, 50))
    }

    @Test
    fun `quiet speech over silence is speech even far below -40 dBFS`() {
        // The AVD case that the fixed -40 dBFS gate got wrong: speech at -48 dBFS over digital silence.
        val capture = List(10) { -120f } + List(8) { -48f } + List(20) { -120f }
        assertEquals(true, AudioLevel.heardSpeech(capture, 50))
    }

    @Test
    fun `speech in a noisy room rises over its background`() {
        val capture = List(10) { -50f } + List(6) { -30f } + List(20) { -50f }
        assertEquals(true, AudioLevel.heardSpeech(capture, 50))
        assertEquals(300, AudioLevel.speechMs(capture, 50))
    }

    @Test
    fun `under 120 ms of speech does not count`() {
        val capture = List(10) { -120f } + List(2) { -30f } + List(20) { -120f }
        assertEquals(false, AudioLevel.heardSpeech(capture, 50))
    }
}

package app.tileshell.cortana.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which speech faults may be announced BY the speech engine.
 *
 * This is a loop guard, not a preference. The error handler answers every fault by replying, and a
 * reply is spoken; when the fault is that the engine cannot speak, the reply fails identically and
 * returns to the handler, which replies again. It ran unbounded on the phone on 2026-09-22 — one tap
 * against refused espeak data produced over a thousand speak-error pairs in 50 ms and filled the
 * diagnostics ring with the notice instead of the cause.
 */
class SpeechErrorSpeakableTest {

    @Test
    fun `a broken engine is never asked to announce itself`() {
        assertFalse("MODEL_MISSING", SpeechError.isSpeakable(SpeechError.MODEL_MISSING))
        assertFalse("MODEL_CORRUPT", SpeechError.isSpeakable(SpeechError.MODEL_CORRUPT))
        assertFalse("ESPEAK_DATA_BAD", SpeechError.isSpeakable(SpeechError.ESPEAK_DATA_BAD))
    }

    @Test
    fun `microphone faults are still spoken`() {
        assertTrue(SpeechError.isSpeakable(SpeechError.NO_MICROPHONE_PERMISSION))
        assertTrue(SpeechError.isSpeakable(SpeechError.AUDIO_UNAVAILABLE))
    }

    @Test
    fun `an unknown code stays speakable`() {
        // INTERNAL and anything added later: silence is the worse failure when the voice does work,
        // and a code that turns out to break the voice costs one hop, not a loop.
        assertTrue(SpeechError.isSpeakable(SpeechError.INTERNAL))
        assertTrue(SpeechError.isSpeakable(99))
    }
}

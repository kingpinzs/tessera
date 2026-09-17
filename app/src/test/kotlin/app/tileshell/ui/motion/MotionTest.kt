package app.tileshell.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test fun entranceFadeStartsAtTheFirstMeasuredGain() = assertEquals(0.01f, Motion.entranceAlpha(0f), 1e-4f)

    @Test fun entranceFadeCompletesAt217Ms() {
        // R3 A11: the ten measured gains 0.01 .. 0.99 span 13 frames, complete at 217 ms.
        assertEquals(1f, Motion.entranceAlpha(Motion.ENTRANCE_FADE_MS.toFloat()), 1e-4f)
        assertTrue(Motion.entranceAlpha(150f) < 0.95f)
        assertEquals(0.99f, Motion.entranceAlpha(Motion.ENTRANCE_FADE_MS * 9f / 10f), 1e-3f)
    }

    @Test fun entranceScaleKeepsOneMeasuredValuePerFrame() {
        assertEquals(0.78f, Motion.sampleFrames(Motion.entranceScaleFrames, 0f), 1e-4f)
        assertEquals(0.98f, Motion.sampleFrames(Motion.entranceScaleFrames, Motion.FRAME_MS * 7), 1e-4f)
        assertEquals(1f, Motion.sampleFrames(Motion.entranceScaleFrames, 150f), 1e-4f)
    }
}

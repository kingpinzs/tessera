package app.tileshell.clock

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A lap row's styled text is the shared line (T15-16: the row and the Share text are one string). The first build's
 * check read the BUILDER's toString(), its object identity, so every lap crashed the app (qa/phase-15/E7-run2/DEFECT.md).
 */
class StopwatchLapTextTest {
    @Test fun `a lap row builds, and its text is the shared line`() {
        val row = lapText(2, 22_760, 35_100, Color.Gray, Color.White)
        assertEquals(ClockText.lapLine(2, 22_760, 35_100), row.text)
    }

    @Test fun `a lap over an hour builds too`() {
        val row = lapText(12, 3_725_430, 7_512_000, Color.Gray, Color.White)
        assertEquals(ClockText.lapLine(12, 3_725_430, 7_512_000), row.text)
    }
}

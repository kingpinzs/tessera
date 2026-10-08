package app.tileshell.music.radio

import app.tileshell.music.radio.StreamGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (Decisions "metered data", "captive portal"; r3 D14; Q4 A): what a tap does on the network the phone is on. */
class StreamGateTest {
    private fun station(hasNetwork: Boolean = true, captive: Boolean = false, metered: Boolean = false, dataSaver: Boolean = false) =
        StreamGate.decide(hasNetwork, captive, metered, dataSaver, isStation = true)

    private fun server(hasNetwork: Boolean = true, captive: Boolean = false, metered: Boolean = false, dataSaver: Boolean = false) =
        StreamGate.decide(hasNetwork, captive, metered, dataSaver, isStation = false)

    @Test fun `no network - the no-network answer at once, whatever else is said`() {
        for (captive in listOf(false, true)) for (metered in listOf(false, true)) for (saver in listOf(false, true)) {
            val d = station(hasNetwork = false, captive = captive, metered = metered, dataSaver = saver)
            assertEquals(Decision(play = false, refusal = "This station isn't answering", line = "stream: no network"), d)
        }
    }

    @Test fun `a captive portal - refused with Sign in to this Wi-Fi network first, and no stream`() {
        for (metered in listOf(false, true)) for (saver in listOf(false, true)) {
            val d = station(captive = true, metered = metered, dataSaver = saver)
            assertEquals(Decision(play = false, refusal = "Sign in to this Wi-Fi network first", line = "stream: captive portal"), d)
            assertNull(d.meteredLine)
        }
    }

    @Test fun `not validated but not captive - it plays, into the reconnect path`() {
        // "Not validated" is not one of the rule's facts at all: only Android's captive-portal flag refuses.
        assertEquals(Decision(play = true), station(hasNetwork = true, captive = false))
    }

    @Test fun `metered - it plays, with the line`() {
        val d = station(metered = true)
        assertTrue(d.play)
        assertEquals("Streaming over mobile data", d.meteredLine)
        assertNull(d.refusal)
        assertNull(d.line)
    }

    @Test fun `Data Saver - it plays, and the line says so - but only on a metered network`() {
        val d = station(metered = true, dataSaver = true)
        assertTrue(d.play)
        assertEquals("Streaming over mobile data — Data Saver is on", d.meteredLine)
        assertTrue(d.meteredLine!!.startsWith(StreamGate.METERED))
        // Data Saver restricts metered networks: on Wi-Fi there is nothing to say.
        assertEquals(Decision(play = true), station(metered = false, dataSaver = true))
    }

    @Test fun `a server track is exempt from the captive and the no-network refusals`() {
        for (hasNetwork in listOf(false, true)) for (captive in listOf(false, true)) for (metered in listOf(false, true)) for (saver in listOf(false, true)) {
            val d = server(hasNetwork, captive, metered, saver)
            assertTrue("$hasNetwork $captive $metered $saver", d.play)
            assertNull(d.refusal)
            assertNull(d.line)
        }
        // It is still a stream on mobile data, and says so; with no network there is no network to be metered.
        assertEquals("Streaming over mobile data", server(metered = true).meteredLine)
        assertNull(server(hasNetwork = false, metered = true).meteredLine)
    }

    @Test fun `every refusal has a text and a line, and nothing refused plays`() {
        for (hasNetwork in listOf(false, true)) for (captive in listOf(false, true)) for (metered in listOf(false, true)) for (saver in listOf(false, true)) for (isStation in listOf(false, true)) {
            val d = StreamGate.decide(hasNetwork, captive, metered, saver, isStation)
            assertEquals(d.play, d.refusal == null)
            assertEquals(d.play, d.line == null)
            if (!d.play) assertNull(d.meteredLine)
            assertFalse(d.play && isStation && (!hasNetwork || captive))
        }
    }
}

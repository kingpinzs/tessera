package app.tileshell.music.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 build task 3 (r3 D14 / D15): what a station start plays — the tap's and a search's, by one rule. */
class StationStartTest {
    private val open = StreamGate.Decision(play = true)
    private val a = station("a")
    private val b = station("b")
    private val c = station("c")
    private val file = station("f", url = "file:///sdcard/Music/x.mp3")
    private val playlist = station("p", url = "http://stream.example.net/listen.pls")
    private val private = station("l", url = "http://192.168.1.10/stream")

    private fun plan(station: Station, favourites: List<Station>, gate: StreamGate.Decision = open, qaHost: String? = null) =
        StationStart.plan(gate, RadioFavourites.queueFor(station, favourites), qaHost)

    @Test fun `a favourite plays the favourites in order from itself, and any other station plays alone`() {
        assertEquals(StationStart.Plan(listOf(a, b, c), 2, emptyList(), null), plan(c, listOf(a, b, c)))
        assertEquals(StationStart.Plan(listOf(a, b, c), 0, emptyList(), null), plan(a, listOf(a, b, c)))
        assertEquals(StationStart.Plan(listOf(c), 0, emptyList(), null), plan(c, listOf(a, b)))
        assertTrue(plan(c, listOf(a, b)).plays)
    }

    @Test fun `no network and a captive portal start nothing, and say the gate's text and line`() {
        val none = plan(b, listOf(a, b), StreamGate.decide(hasNetwork = false, captive = false, metered = false, dataSaver = false, isStation = true))
        assertEquals(StationStart.Plan(emptyList(), 0, listOf("stream: no network"), "This station isn't answering"), none)
        assertFalse(none.plays)
        val captive = plan(b, listOf(a, b), StreamGate.decide(hasNetwork = true, captive = true, metered = false, dataSaver = false, isStation = true))
        assertEquals(StationStart.Plan(emptyList(), 0, listOf("stream: captive portal"), "Sign in to this Wi-Fi network first"), captive)
        // The gate is asked before the address: a station that may not play is not even named on such a network.
        assertEquals(listOf("stream: captive portal"), plan(file, emptyList(), StreamGate.Decision(play = false, refusal = "x", line = "stream: captive portal")).lines)
    }

    @Test fun `the station asked for refused - nothing plays in its place, and its line is written`() {
        assertEquals(StationStart.Plan(emptyList(), 0, listOf("stream: unsupported scheme=file"), StationItem.CANT_PLAY), plan(file, emptyList()))
        assertEquals(StationStart.Plan(emptyList(), 0, listOf("stream: unsupported playlist"), StationItem.CANT_PLAY), plan(playlist, listOf(a, playlist, b)))
        assertEquals(StationStart.Plan(emptyList(), 0, listOf("stream: unsupported host"), StationItem.CANT_PLAY), plan(private, listOf(private, a)))
        assertEquals("can't play this station", plan(file, listOf(a, file)).refusal)
    }

    @Test fun `a refused favourite beside it is left out, said, and the start still lands on the station asked for`() {
        val p = plan(c, listOf(file, a, playlist, c, private))
        assertEquals(listOf(a, c), p.stations)
        assertEquals(1, p.start)
        assertEquals(listOf("stream: unsupported scheme=file", "stream: unsupported playlist", "stream: unsupported host"), p.lines)
        assertNull(p.refusal)
        assertEquals(c, p.stations[p.start])
    }

    @Test fun `the private host of the debug override plays only when the override is set`() {
        val fixture = station("q", url = "http://10.0.2.2:8080/stream/jazz1")
        assertEquals(StationItem.CANT_PLAY, plan(fixture, emptyList()).refusal)
        assertEquals(StationStart.Plan(listOf(fixture), 0, emptyList(), null), plan(fixture, emptyList(), qaHost = "10.0.2.2"))
    }

    @Test fun `a queue with no such start plays nothing`() {
        assertEquals(StationItem.CANT_PLAY, StationStart.plan(open, StationQueue(listOf(a, b), 5), null).refusal)
        assertEquals(StationItem.CANT_PLAY, StationStart.plan(open, StationQueue(emptyList(), 0), null).refusal)
        assertEquals(emptyList<Station>(), StationStart.plan(open, StationQueue(listOf(a, b), -1), null).stations)
    }
}

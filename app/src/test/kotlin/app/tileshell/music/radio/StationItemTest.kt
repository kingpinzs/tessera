package app.tileshell.music.radio

import app.tileshell.music.MusicItemRule
import app.tileshell.music.MusicLive
import app.tileshell.music.radio.StationItem.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (build task 3 "The item"; r3 D1 / D9 / D10 / D11): what a station's item is made of, or why there is none. */
class StationItemTest {
    @Test fun `a station that may play - the id, the resolved address, no MIME type, the name`() {
        val plan = StationItem.plan(station("jazz-one", "QA Jazz One", url = "http://a.example.net/listen.pls", urlResolved = "http://a.example.net:8000/live"), qaHost = null)
        assertEquals(Plan.Play("station:jazz-one", "http://a.example.net:8000/live", null, "QA Jazz One"), plan)
        plan as Plan.Play
        assertTrue(MusicLive.isLive(plan.mediaId))
        assertNull(MusicItemRule.libraryId(plan.mediaId))
    }

    @Test fun `an hls row gets the m3u8 MIME type`() {
        val plan = StationItem.plan(station("h", urlResolved = "https://a.example.net/live/index.m3u8", hls = true), null) as Plan.Play
        assertEquals("application/x-mpegURL", plan.mimeType)
    }

    @Test fun `url is used when url_resolved is empty`() {
        val plan = StationItem.plan(station("u", url = "https://a.example.net/live", urlResolved = ""), null) as Plan.Play
        assertEquals("https://a.example.net/live", plan.url)
    }

    @Test fun `the name is the shown one`() {
        val plan = StationItem.plan(station("u", name = "Real‮ FM\n" + "x".repeat(200)), null) as Plan.Play
        assertEquals("Real FM" + "x".repeat(73), plan.name)
    }

    @Test fun `a refused station has no item, and the line says why`() {
        // The fixture's QA File row (A2 e).
        assertEquals(Plan.Refused("stream: unsupported scheme=file"), StationItem.plan(station("f", urlResolved = "file:///sdcard/Music/x.mp3"), qaHost = "10.0.2.2"))
        assertEquals(Plan.Refused("stream: unsupported scheme=content"), StationItem.plan(station("c", urlResolved = "content://media/external/audio/media/1"), null))
        assertEquals(Plan.Refused("stream: unsupported host"), StationItem.plan(station("p", urlResolved = "http://192.168.1.1:8000/live"), null))
        assertEquals(Plan.Refused("stream: unsupported host"), StationItem.plan(station("l", urlResolved = "http://localhost/live"), null))
        assertEquals(Plan.Refused("stream: unsupported playlist"), StationItem.plan(station("pl", urlResolved = "http://a.example.net/listen.pls"), null))
        assertEquals(Plan.Refused("stream: unsupported playlist"), StationItem.plan(station("pl", url = "http://a.example.net/listen.m3u", urlResolved = ""), null))
        assertEquals(Plan.Refused("stream: unsupported scheme=none"), StationItem.plan(station("e", url = "", urlResolved = ""), null))
        // A good `url` does not rescue a bad `url_resolved`: the one address is the directory's resolution.
        assertEquals(Plan.Refused("stream: unsupported scheme=file"), StationItem.plan(station("f", url = "http://a.example.net/live", urlResolved = "file:///x.mp3"), null))
        assertEquals("can't play this station", StationItem.CANT_PLAY)
    }

    @Test fun `the fixture's host plays only with the debug override's qaHost`() {
        val fixture = station("jazz-one", urlResolved = "http://10.0.2.2:8080/stream/jazz-one")
        assertEquals(Plan.Refused("stream: unsupported host"), StationItem.plan(fixture, qaHost = null))
        assertEquals("http://10.0.2.2:8080/stream/jazz-one", (StationItem.plan(fixture, qaHost = "10.0.2.2") as Plan.Play).url)
    }
}

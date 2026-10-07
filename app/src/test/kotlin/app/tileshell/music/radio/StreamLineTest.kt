package app.tileshell.music.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (build task 3 "Lines"; T20-13, C-32): each `[music] stream:` / `sleep:` / `radio:` line's exact text. */
class StreamLineTest {
    @Test fun `each stream line's exact text`() {
        assertEquals("music", StreamLine.TAG)
        assertEquals("stream: lost, retrying", StreamLine.LOST)
        assertEquals("stream: gave up after 60000 ms", StreamLine.gaveUp())
        assertEquals("stream: gave up after 60000 ms", StreamLine.gaveUp(StreamRetry.WINDOW_MS))
        assertEquals("stream: reconnected after 18250 ms", StreamLine.reconnected(18_250))
        assertEquals("stream: captive portal", StreamLine.CAPTIVE_PORTAL)
        assertEquals("stream: unsupported scheme=file", StreamLine.unsupportedScheme("file"))
        assertEquals("stream: unsupported scheme=content", StreamLine.unsupportedScheme("content"))
        assertEquals("stream: unsupported host", StreamLine.UNSUPPORTED_HOST)
        assertEquals("stream: unsupported playlist", StreamLine.UNSUPPORTED_PLAYLIST)
        assertEquals("stream: redirect refused", StreamLine.REDIRECT_REFUSED)
        assertEquals("stream: no network", StreamLine.NO_NETWORK)
        assertEquals("stream: connected http://10.0.2.2:8080/stream/jazz-one codec=mp3", StreamLine.connected("http://10.0.2.2:8080/stream/jazz-one", "MP3"))
        assertEquals("stream: connected http://10.0.2.2:8096/Audio/f3b1c2/stream codec=mp3", StreamLine.connected("http://10.0.2.2:8096/Audio/f3b1c2/stream?static=true", "mp3"))
    }

    @Test fun `the sleep line's exact text`() {
        assertEquals("sleep: end-of-track cleared (live item)", StreamLine.SLEEP_CLEARED)
    }

    @Test fun `the radio lines' exact text`() {
        assertEquals("radio: directory too large", RadioLine.TOO_LARGE)
        assertEquals("radio: directory fetched 4 stations", RadioLine.fetched(4))
        assertEquals("radio: directory fetched 52817 stations", RadioLine.fetched(52_817))
        assertEquals("radio: directory offline, cache from 2026-10-07", RadioLine.offline("2026-10-07"))
        assertEquals("radio: no cache yet (offline)", RadioLine.NO_CACHE_OFFLINE)
        assertEquals("radio: fm feature=false", RadioLine.fm(false))
        assertEquals("radio: fm feature=true", RadioLine.fm(true))
    }

    @Test fun `the query is stripped from every URL - a token never reaches a line`() {
        assertEquals("http://nas.example.net:8096/Audio/1/stream", StreamLine.url("http://nas.example.net:8096/Audio/1/stream?static=true&ApiKey=SECRETTOKEN"))
        assertEquals("https://a.example.net/live", StreamLine.url("https://a.example.net/live?token=abc#frag"))
        assertEquals("https://a.example.net/live", StreamLine.url("https://a.example.net/live#access_token=abc"))
        assertFalse(StreamLine.connected("http://a.example.net/x?api_key=SECRETTOKEN", "aac").contains("SECRETTOKEN"))
        assertFalse(StreamLine.connected("http://a.example.net/x?api_key=SECRETTOKEN", "aac").contains('?'))
    }

    @Test fun `the userinfo is stripped from every URL - a password never reaches a line`() {
        assertEquals("http://a.example.net:8000/live", StreamLine.url("http://source:hackme@a.example.net:8000/live"))
        assertEquals("https://a.example.net/live", StreamLine.url("https://user@a.example.net/live?x=1"))
        assertEquals("http://a.example.net/live", StreamLine.url("http://a:b@c:d@a.example.net/live"))
        assertEquals("http://a.example.net", StreamLine.url("http://source:hackme@a.example.net"))
        // An @ in the path is the path's.
        assertEquals("http://a.example.net/user@host/live", StreamLine.url("http://a.example.net/user@host/live"))
        assertEquals("http://a.example.net/@", StreamLine.url("http://pw@a.example.net/@"))
        assertFalse(StreamLine.connected("http://source:hackme@a.example.net/live", "mp3").contains("hackme"))
    }

    @Test fun `nothing in an address, a scheme or a codec can break the line or forge another`() {
        val line = StreamLine.connected("http://a.example.net/x\n[music] stream: connected http://evil.example.net codec=mp3", "mp3\n[music] x")
        assertFalse(line.contains('\n'))
        assertTrue(line.endsWith(" codec=mp3musicx"))
        assertEquals("stream: unsupported scheme=file", StreamLine.unsupportedScheme("FILE"))
        assertEquals("stream: unsupported scheme=xmusicy", StreamLine.unsupportedScheme("x\n[music] y"))
        assertEquals("stream: unsupported scheme=none", StreamLine.unsupportedScheme(null))
        assertEquals("stream: unsupported scheme=none", StreamLine.unsupportedScheme(""))
        assertEquals("stream: unsupported scheme=aaaaaaaaaaaaaaaa", StreamLine.unsupportedScheme("a".repeat(100)))
        assertEquals("stream: connected http://a.example.net/x codec=unknown", StreamLine.connected("http://a.example.net/x", null))
        assertEquals(StreamLine.URL_MAX, StreamLine.url("http://a.example.net/" + "p".repeat(5000)).length)
        assertEquals("", StreamLine.url(null))
        assertEquals("radio: directory offline, cache from 2026-10-07[music] x", RadioLine.offline("2026-10-07\n[music] x"))
    }

    @Test fun `the lines the doc struck do not exist`() {
        // `stream: unsupported hls` (r3 D10: HLS plays) and `stream: cleartext refused` (Q-D: A, one policy) are gone.
        val all = listOf(
            StreamLine.LOST, StreamLine.gaveUp(), StreamLine.reconnected(1), StreamLine.CAPTIVE_PORTAL, StreamLine.unsupportedScheme("x"),
            StreamLine.UNSUPPORTED_HOST, StreamLine.UNSUPPORTED_PLAYLIST, StreamLine.REDIRECT_REFUSED, StreamLine.NO_NETWORK,
            StreamLine.connected("http://a.example.net", "mp3"), StreamLine.SLEEP_CLEARED,
        )
        assertTrue(all.none { it.contains("hls") || it.contains("cleartext") })
        assertTrue(all.all { it.startsWith("stream: ") || it.startsWith("sleep: ") })
        assertEquals(all.size, all.toSet().size)
    }
}

package app.tileshell.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the music player's data source may open (phase 20, r3 D11 / D12; `docs/plan/qa/phase-20/hls-rereview.md` (b)):
 * exactly an address the shell itself queued; else an http(s) address `StationUrl.accept` takes (a public host, or the
 * debug fixture host); nothing else. The wiring — that every open of the player and of the crossfade's fader is asked
 * — is `media/UriAccessWiringScanTest`'s.
 */
class MusicSourceRuleTest {
    private val track = "content://media/external/audio/media/42"
    private val file = "content://app.tileshell.files/root/storage/emulated/0/Download/song.mp3"
    private val station = "https://stream.example.org/live/playlist.m3u8"
    private val serverTrack = "http://192.168.1.20:8096/Audio/0a1b2c3d4e5f/stream?static=true"
    private val queued = setOf(track, file, station, serverTrack)
    private val qa = "10.0.2.2"

    private fun may(asked: String, queued: Set<String> = this.queued, qaHost: String? = null) = MusicSourceRule.mayOpen(asked, queued, qaHost)

    @Test fun `the address of an item the shell queued may be opened - a track, the played file, a station, a server track on the private network`() {
        for (asked in queued) {
            assertTrue("asked for $asked", may(asked))
            // Every re-open of it is the same address: a reconnect, a seek's range request on the server's stream.
            assertTrue("asked again for $asked", may(asked))
        }
        // Nothing queued: nothing local, and no private host.
        assertFalse(may(track, emptySet()))
        assertFalse(may(file, emptySet()))
        assertFalse(may(serverTrack, emptySet()))
        assertFalse(may(serverTrack, setOf(track, file, station)))
        // A public station needs no queueing to be opened again.
        assertTrue(may(station, emptySet()))
    }

    @Test fun `what a playlist names on a public host is opened - a segment, a key, an init segment, a nested playlist`() {
        for (asked in listOf(
            "https://cdn.example.org/seg/0001.ts?token=x#f", "http://cdn.example.org/live/key.bin", "https://stream.example.org/live/init.mp4",
            "https://stream.example.org/live/audio/chunklist.m3u8", "HTTP://EXAMPLE.ORG/a", "Https://example.org/a", "http://93.184.216.34/seg1.aac",
            "https://example.org:8443/a:b/content://x", "http://[2001:db8::1]/seg.ts",
        )) {
            assertTrue("asked for $asked", may(asked, emptySet()))
            assertTrue("asked for $asked", may(asked))
        }
    }

    @Test fun `what a playlist names on a private, loopback, link-local or unspecified literal is refused - the phone is not a way onto its own network`() {
        for (asked in listOf(
            "http://192.168.1.1/admin", "http://192.168.1.20:8096/Audio/ffff/stream?static=true", "http://192.168.1.20:8096/Users", "http://10.0.0.5/seg.ts", "http://172.16.0.1/key",
            "http://172.31.255.254/x", "http://127.0.0.1/seg.ts", "http://127.0.0.1:8080/", "https://localhost/key", "http://LOCALHOST:8096/x", "http://169.254.169.254/latest/meta-data/",
            "http://0.0.0.0/x", "http://[::1]/x", "http://[::]/x", "http://[fe80::1]/x", "http://[fd00::1]/x", "http://[::ffff:192.168.1.1]/x",
            // Spellings only an address parser reads as an address, and forms two parsers read differently.
            "http://127.1/x", "http://0x7f.0.0.1/x", "http://2130706433/x", "http://0177.0.0.1/x", "http://192.168.1.1./x", "http://user@192.168.1.1/x",
            "http://example.org@127.0.0.1/x", "http://127.0.0.1\\@example.org/x", "http://exa mple.org/x", "http://%31%32%37.0.0.1/x", "http:///x", "http:/example.org/x", "http:example.org", "https://",
            // The fixture host is private everywhere but a debug build's override.
            "http://10.0.2.2:8092/stream/jazz1", "http://10.0.2.2:8092/hls/seg0.ts",
            // A queued address is not a licence for its neighbours on the same private host.
            "$serverTrack&x=1", serverTrack.replace("0a1b2c3d4e5f", "0a1b2c3d4e60"), serverTrack.replace("http://", "https://"), serverTrack.removeSuffix("?static=true"),
        )) assertFalse("asked for $asked", may(asked))
    }

    @Test fun `the fixture host is let through only where the debug override names it`() {
        val segment = "http://10.0.2.2:8092/hls/seg0.ts"
        assertFalse(may(segment, qaHost = null))
        assertFalse(may(segment, qaHost = ""))
        assertFalse(may(segment, qaHost = "10.0.2.3"))
        assertTrue(may(segment, qaHost = qa))
        assertTrue(may("http://10.0.2.2:8096/x", qaHost = qa))
        // The override opens that one host and nothing beside it, and never another scheme.
        assertFalse(may("http://10.0.2.20/x", qaHost = qa))
        assertFalse(may("http://127.0.0.1/x", qaHost = qa))
        assertFalse(may("http://192.168.1.1/x", qaHost = qa))
        assertFalse(may("file://10.0.2.2/sdcard/x", qaHost = qa))
        assertFalse(may("content://10.0.2.2/x", qaHost = qa))
    }

    @Test fun `nothing a playlist, a redirect or a nested reference names is opened unless it is http or https`() {
        for (qaHost in listOf(null, qa)) for (asked in listOf(
            // Every other scheme ExoPlayer's default data source opens (Media3 1.9.0 DefaultDataSource.open).
            "file:///data/user/0/app.tileshell/files/credentials_v1.json", "file:///sdcard/Music/a.mp3", "file:///android_asset/x",
            "/data/user/0/app.tileshell/files/credentials_v1.json", "data/x", "",
            "content://media/external/audio/media/43", "content://com.android.contacts/contacts", "content://app.tileshell.files/root/storage/emulated/0/Download/other.mp3",
            "asset:///x", "rawresource:///2131230720", "android.resource://app.tileshell/raw/x", "data:audio/mpeg;base64,AAAA", "data:text/plain,key",
            "rtmp://example.org/live", "udp://239.0.0.1:1234", "rtsp://example.org/live", "ftp://example.org/a.mp3", "jar:file:///x!/y", "intent://x#Intent;end",
            // Not the scheme: http named somewhere else in the address.
            "content://http://example.org/x", "file:///http://example.org", "content://media/external/audio/media/42?http://x", "//example.org/a.mp3",
            " http://example.org/a", "ht tp://example.org/a", "httpx://example.org/a", "https+file:///x", "http",
        )) assertFalse("asked for $asked", may(asked, qaHost = qaHost))
    }

    @Test fun `a queued address is matched whole - not by prefix, case, query, fragment or a path under it`() {
        for (asked in listOf(
            "$track/", "$track?x=1", "$track#f", "${track}0", track.dropLast(1), track.uppercase(), "CONTENT://media/external/audio/media/42",
            "content://media/external/audio/media/42/../43", "content://0@media/external/audio/media/42", "content://media//external/audio/media/42",
            "$file/../../../../data/user/0/app.tileshell/files/credentials_v1.json", file.replace("song.mp3", "song%2Emp3"), " $track", "$track ",
        )) assertFalse("asked for $asked", may(asked))
    }

    @Test fun `the process's set answers only for what it was told, from any thread`() {
        val sources = MusicSources()
        assertFalse(sources.mayOpen(track, null))
        assertFalse(sources.mayOpen(serverTrack, null))
        assertTrue(sources.mayOpen("https://example.org/a", null))
        assertTrue(sources.queued(track) == track)
        sources.queued(serverTrack)
        assertTrue(sources.mayOpen(track, null))
        assertTrue(sources.mayOpen(serverTrack, null))
        assertFalse(sources.mayOpen(file, null))
        assertFalse(sources.mayOpen("$track/", null))
        assertFalse(sources.mayOpen("http://10.0.2.2:8092/a", null))
        assertTrue(sources.mayOpen("http://10.0.2.2:8092/a", qa))
        val threads = (0 until 8).map { n -> Thread { repeat(200) { sources.queued("content://media/external/audio/media/${n * 1000 + it}") } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(sources.mayOpen("content://media/external/audio/media/7199", null))
        assertFalse(sources.mayOpen("content://media/external/audio/media/8000", null))
        assertFalse(sources.mayOpen("file:///sdcard/x", null))
    }

    // Review R20-1: the connection's rule lets ONE kind of queued address reach a private network — the home server's.
    @Test fun `only an address queued as the home server's is one of the servers - a station's, a track's and a file's are not`() {
        val sources = MusicSources()
        assertTrue(sources.servers().isEmpty())
        sources.queued(track)
        sources.queued(file)
        sources.queued(station)
        sources.queued("http://10.0.2.2:8092/stream")
        assertTrue(sources.servers().isEmpty())
        assertTrue(sources.queuedServer(serverTrack) == serverTrack)
        assertTrue(sources.servers() == setOf(serverTrack))
        // It is queued like any other, so the guard's first layer opens it — and its range re-open.
        assertTrue(sources.mayOpen(serverTrack, null))
        assertTrue(sources.mayOpen(track, null))
    }
}

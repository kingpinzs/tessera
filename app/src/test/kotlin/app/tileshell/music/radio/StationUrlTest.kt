package app.tileshell.music.radio

import app.tileshell.music.radio.StationUrl.Accept
import app.tileshell.music.radio.StationUrl.Playable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase 20 (r3 D11 / D12; trust): which address of a station may reach the player. */
class StationUrlTest {
    private fun accept(url: String?, qaHost: String? = null) = StationUrl.accept(url, qaHost)

    @Test fun `http and https are accepted`() {
        for (url in listOf(
            "http://stream.example.net/live", "https://stream.example.net/live.mp3", "http://stream.example.net:8000/;stream.mp3",
            "https://stream.example.net", "HTTP://Stream.Example.net/x", "hTTps://stream.example.net/x?token=1#f",
            "http://user:pw@stream.example.net/x", "http://91.98.4.78:8000/live", "https://[2a01:4f8::1]:8443/live", "http://stream.example.net./x",
        )) assertEquals(url, Accept.Ok, accept(url))
    }

    @Test fun `every other scheme is refused and named - file, content, asset, rawresource, data, in any case`() {
        assertEquals(Accept.UnsupportedScheme("file"), accept("file:///sdcard/Music/x.mp3"))
        assertEquals(Accept.UnsupportedScheme("file"), accept("FILE:///sdcard/Music/x.mp3"))
        assertEquals(Accept.UnsupportedScheme("file"), accept("File:/sdcard/Music/x.mp3"))
        assertEquals(Accept.UnsupportedScheme("content"), accept("content://media/external/audio/media/41"))
        assertEquals(Accept.UnsupportedScheme("content"), accept("CONTENT://app.tileshell.files/x"))
        assertEquals(Accept.UnsupportedScheme("asset"), accept("asset:///speech/model.bin"))
        assertEquals(Accept.UnsupportedScheme("rawresource"), accept("rawresource:///2131230720"))
        assertEquals(Accept.UnsupportedScheme("android.resource"), accept("android.resource://app.tileshell/raw/x"))
        assertEquals(Accept.UnsupportedScheme("data"), accept("data:audio/mpeg;base64,SUQz"))
        assertEquals(Accept.UnsupportedScheme("rtsp"), accept("rtsp://stream.example.net/live"))
        assertEquals(Accept.UnsupportedScheme("javascript"), accept("javascript:alert(1)"))
    }

    @Test fun `empty, and an address with no scheme at all, are refused - a bare path is a FILE to the player`() {
        for (url in listOf(null, "", " ", "/sdcard/Music/x.mp3", "stream.example.net/live", "//stream.example.net/live", ":http://x", "1http://x", "ht tp://x")) {
            assertEquals("$url", Accept.UnsupportedScheme("none"), accept(url))
        }
    }

    @Test fun `an empty host and a malformed authority are refused`() {
        for (url in listOf(
            "http://", "http:///live", "https://:8000/live", "http:/stream.example.net/live", "http:stream.example.net", "http://user@/x",
            "http://stream.example.net:80a/x", "http://stream.example.net:999999/x", "http://[::1/x", "http://[]/x", "http://[stream.example.net]/x",
            "http://a..b/x", "http://.example.net/x",
        )) assertEquals(url, Accept.UnsupportedHost, accept(url))
    }

    @Test fun `localhost and the IP-literal loopback, link-local, RFC 1918 and unspecified hosts are refused`() {
        for (host in listOf(
            "localhost", "LOCALHOST", "localhost.", "printer.localhost", "nas.local",
            "127.0.0.1", "127.255.255.254", "10.0.2.2", "10.255.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254",
            "0.0.0.0", "0.1.2.3", "[::1]", "[::]", "[0:0:0:0:0:0:0:1]", "[0000::1]", "[::ffff:127.0.0.1]", "[::ffff:10.0.0.1]", "[::7f00:1]",
            "[fe80::1]", "[fd00::1]", "[fc00::1]",
        )) for (rest in listOf("/live", ":8000/live", "")) {
            assertEquals("$host$rest", Accept.UnsupportedHost, accept("http://$host$rest"))
            assertEquals("$host$rest", Accept.UnsupportedHost, accept("https://user:pw@$host$rest"))
        }
    }

    @Test fun `an address spelled the way only an address parser reads it is refused with them`() {
        for (host in listOf("127.1", "127.0.1", "2130706433", "0x7f.0.0.1", "0x7f000001", "0177.0.0.1", "010.0.2.2", "192.168.1", "256.1.1.1", "1.2.3.4.5", "10")) {
            assertEquals(host, Accept.UnsupportedHost, accept("http://$host/live"))
        }
        // A public dotted quad and a name are what they look like.
        assertEquals(Accept.Ok, accept("http://8.8.8.8/live"))
        assertEquals(Accept.Ok, accept("http://172.32.0.1/live"))
        assertEquals(Accept.Ok, accept("http://radio10.example.net/live"))
        assertEquals(Accept.Ok, accept("http://10.example.net/live"))
    }

    @Test fun `what two parsers could read as different hosts is refused - backslash, white space, percent, userinfo tricks`() {
        for (url in listOf(
            "http://stream.example.net\\@127.0.0.1/x", "http://127.0.0.1\\.example.net/x", "http://stream.example.net /x", "http://stream.example.net/a b",
            "http://stream.example.net/x\n", "http://stream.example.net\t/x", "http://%31%32%37.0.0.1/x", "http://[fe80::1%25wlan0]/x",
        )) assertEquals(url, Accept.UnsupportedHost, accept(url))
        // The host is what follows the LAST @ of the authority, and the authority ends at the first / ? or #.
        assertEquals(Accept.UnsupportedHost, accept("http://stream.example.net@127.0.0.1/x"))
        assertEquals(Accept.UnsupportedHost, accept("http://stream.example.net:80@10.0.2.2:8080/x"))
        assertEquals(Accept.Ok, accept("http://127.0.0.1@stream.example.net/x"))
        assertEquals(Accept.Ok, accept("http://stream.example.net/x@127.0.0.1"))
        assertEquals(Accept.Ok, accept("http://stream.example.net?@127.0.0.1"))
        assertEquals(Accept.UnsupportedHost, accept("http://127.0.0.1#@stream.example.net"))
    }

    // Review R20-7: the last @ and the percent sign were held only by hosts another rule refuses (a numeric one).
    @Test fun `the host after the LAST of two at-signs is the one judged, when it is a name`() {
        assertEquals(Accept.UnsupportedHost, accept("http://a@b@localhost/"))
        assertEquals(Accept.UnsupportedHost, accept("http://stream.example.net@b@nas.local:8000/x"))
        assertEquals(Accept.UnsupportedHost, accept("https://a:b@c:d@printer.localhost/x"))
        assertEquals(Accept.Ok, accept("http://localhost@nas.local@stream.example.net/x"))
    }

    @Test fun `a percent sign in a NAME is refused - the rule decodes nothing, so an encoded name is nobody's`() {
        for (url in listOf(
            "http://%6c%6f%63%61%6c%68%6f%73%74/", "http://%6Cocalhost/x", "http://nas%2elocal/x", "http://stream%2eexample.net/x",
            "http://stream.example.net%/x", "http://user@%6eas.local:8000/x",
        )) assertEquals(url, Accept.UnsupportedHost, accept(url))
    }

    // Review R20-5: the platform's HTTP stack maps a host to ASCII (IDNA) before it connects, so the rule judges the
    // host AFTER that mapping, and a host that is not a plain ASCII name or literal after it is refused.
    @Test fun `a host is judged as the platform will read it - fullwidth and circled digits, the other full stops, a fullwidth localhost`() {
        for (host in listOf(
            "127。0。0。1", // ideographic full stops
            "192｡168｡1｡1", "nas．local", // the halfwidth and the fullwidth full stop
            "①⑨②.①⑥⑧.①.①", // circled digits: 192.168.1.1
            "１２７.０.０.１", "１０.0.2.2", // fullwidth digits: 127.0.0.1, 10.0.2.2
            "ｌｏｃａｌｈｏｓｔ", "ＬＯＣＡＬＨＯＳＴ", // fullwidth localhost, both cases
            "printer.ｌocalhost", "１２７.1", "０x7f.0.0.1",
        )) for (rest in listOf("/x", ":8000/x", "")) {
            assertEquals("$host$rest", Accept.UnsupportedHost, accept("http://$host$rest"))
            assertEquals("$host$rest", Accept.UnsupportedHost, accept("https://user:pw@$host$rest"))
        }
    }

    @Test fun `a host that is not a plain name after the mapping is refused, and a real international name is judged by its ASCII form`() {
        for (host in listOf(
            "stream.example.net／.evil.example", "stream.example.net？.evil.example", "stream.example.net＃.evil.example", // fullwidth / ? #
            "evil.example＠stream.example.net", "stream.example.net：80", "stream.example.net％", // fullwidth @ : %
            "stream!.example.net", "stream.example.net,x", "stream(1).example.net", "a=b.example.net", "stream.example.net;x", "st*eam.example.net",
            "\ud83d", "stream͸.example.net", // a lone surrogate, an unassigned character
        )) assertEquals(host, Accept.UnsupportedHost, accept("http://$host/x"))
        assertEquals(Accept.Ok, accept("http://bücher.example/live"))
        assertEquals(Accept.Ok, accept("http://xn--bcher-kva.example/live"))
        assertEquals(Accept.Ok, accept("https://rádio.example.net:8443/live"))
        assertEquals(Accept.Ok, accept("http://stream_1.my-radio.example.net/live"))
        // The mapped host is the one compared with the fixture's, too.
        assertEquals(Accept.Ok, accept("http://１０.0.2.2:8080/x", qaHost = "10.0.2.2"))
    }

    // Review R20-6: the ranges nobody's station is on, and an authority two parsers read differently.
    @Test fun `carrier-grade NAT and the other special-use literals are refused, and the addresses beside them are not`() {
        for (host in listOf(
            "100.64.0.1", "100.100.100.100", "100.127.255.254", "198.18.0.1", "198.19.255.254", "192.0.0.1", "192.0.0.170",
            "224.0.0.1", "239.255.255.250", "240.0.0.1", "255.255.255.255",
            "[fec0::1]", "[feff::1]", "[ff02::1]", "[ff00::]", "[64:ff9b::c0a8:101]", "[0064:ff9b::7f00:1]", "[64:ff9b:1::c0a8:101]", "[2002:c0a8:101::1]",
            "[100::1]", "[1fff::1]", "[4000::1]", "[fe00::1]", "[2002::]",
        )) for (rest in listOf("/x", ":8000/x", "")) assertEquals("$host$rest", Accept.UnsupportedHost, accept("http://$host$rest"))
        for (host in listOf(
            "100.63.255.255", "100.128.0.1", "198.17.255.255", "198.20.0.1", "192.0.1.1", "192.1.0.1", "223.255.255.254", "1.1.1.1",
            "[2001:4860:4860::8888]", "[2000::1]", "[2003::1]", "[3fff::1]", "[2A01:4F8::1]",
        )) assertEquals(host, Accept.Ok, accept("http://$host/x"))
    }

    @Test fun `an IPv6 literal is hex groups in brackets and nothing else`() {
        for (host in listOf("[2a01:4f8::g]", "[2a01:4f8::1%eth0]", "[2a01:4f8::1 ]", "[v1.fe80::a]", "[20010:4f8::1]", "[:2a01::1]", "[2a01：:1]", "[２a01::1]")) {
            assertEquals(host, Accept.UnsupportedHost, accept("http://$host/x"))
        }
    }

    @Test fun `an authority with two ports, or a bare IPv6 address, is refused - no parser agrees on its host`() {
        for (url in listOf(
            "http://127.0.0.1:80:80/x", "http://stream.example.net:80:80/x", "http://stream.example.net::80/x", "http://stream.example.net:80:/x",
            "http://2a01:4f8::1/x", "http://::1/x", "http://user@stream.example.net:80:8000/x",
        )) assertEquals(url, Accept.UnsupportedHost, accept(url))
    }

    @Test fun `the qaHost exception holds only when it is non-null, for that host alone, and never for a scheme`() {
        val fixture = "http://10.0.2.2:8080/stream/jazz-one"
        assertEquals(Accept.UnsupportedHost, accept(fixture, qaHost = null))
        assertEquals(Accept.UnsupportedHost, accept(fixture, qaHost = ""))
        assertEquals(Accept.Ok, accept(fixture, qaHost = "10.0.2.2"))
        assertEquals(Accept.Ok, accept("https://10.0.2.2/x", qaHost = "10.0.2.2"))
        // Another private host is not the fixture's, and the exception opens no scheme.
        assertEquals(Accept.UnsupportedHost, accept("http://10.0.2.3:8080/x", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedHost, accept("http://127.0.0.1:8080/x", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedHost, accept("http://10.0.2.2.example.net@192.168.1.1/x", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedScheme("file"), accept("file://10.0.2.2/sdcard/x.mp3", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedScheme("file"), accept("file:///sdcard/Music/x.mp3", qaHost = "10.0.2.2"))
        // Review R20-7: the fixture host is matched WHOLE - a host that only ends, starts or holds it is judged as any other.
        assertEquals(Accept.UnsupportedHost, accept("http://010.0.2.2:8080/x", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedHost, accept("http://1.10.0.2.2/x", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedHost, accept("http://10.0.2.25/x", qaHost = "10.0.2.2"))
        assertEquals(Accept.UnsupportedHost, accept("http://nas.local/x", qaHost = "local"))
        assertEquals(Accept.UnsupportedHost, accept("http://printer.localhost/x", qaHost = "localhost"))
    }

    @Test fun `the qaHost is the host of the override's base, and null with no override`() {
        assertEquals("10.0.2.2", StationUrl.qaHost("http://10.0.2.2:8080/"))
        assertEquals("fixture.test", StationUrl.qaHost("https://Fixture.Test/"))
        assertNull(StationUrl.qaHost(null))
        assertNull(StationUrl.qaHost("not an address"))
    }

    // ---- playable

    @Test fun `url_resolved is the address played, and url only when that is empty`() {
        assertEquals(Playable.Stream("http://a.example.net/resolved", null), StationUrl.playable("http://a.example.net/resolved", "http://a.example.net/listen.pls", hls = false))
        assertEquals(Playable.Stream("http://a.example.net/live", null), StationUrl.playable("", "http://a.example.net/live", hls = false))
        assertEquals(Playable.Stream("http://a.example.net/live", null), StationUrl.playable("  ", " http://a.example.net/live ", hls = false))
        assertEquals(Playable.Stream("http://a.example.net/live", null), StationUrl.playable(null, "http://a.example.net/live", hls = false))
    }

    @Test fun `both empty is refused`() {
        assertEquals(Playable.None, StationUrl.playable("", "", hls = false))
        assertEquals(Playable.None, StationUrl.playable(null, null, hls = true))
        assertEquals(Playable.None, StationUrl.playable(" ", "\t", hls = false))
    }

    @Test fun `hls=1 gives the m3u8 MIME type, and nothing else does`() {
        assertEquals(Playable.Stream("https://a.example.net/live/master.m3u8", "application/x-mpegURL"), StationUrl.playable("https://a.example.net/live/master.m3u8", "", hls = true))
        assertEquals(Playable.Stream("https://a.example.net/hls?id=4", "application/x-mpegURL"), StationUrl.playable("https://a.example.net/hls?id=4", "", hls = true))
        // An .m3u8 is an HLS playlist, not a playlist FILE: it is not refused, with or without the directory's mark.
        assertEquals(Playable.Stream("https://a.example.net/x.m3u8", null), StationUrl.playable("https://a.example.net/x.m3u8", "", hls = false))
    }

    @Test fun `a path ending pls, m3u or asx is an unsupported playlist - nothing is fetched or parsed`() {
        for (url in listOf(
            "http://a.example.net/listen.pls", "http://a.example.net/listen.m3u", "http://a.example.net/listen.asx", "http://a.example.net/LISTEN.PLS",
            "http://a.example.net/listen.pls?sid=1", "http://a.example.net/listen.m3u#x", "https://a.example.net/a/b/c.Asx?x=1#y",
        )) for (hls in listOf(false, true)) assertEquals(url, Playable.Playlist, StationUrl.playable(url, "http://a.example.net/live", hls))
        // The fallback address is judged the same way when it is the one chosen.
        assertEquals(Playable.Playlist, StationUrl.playable("", "http://a.example.net/listen.pls", hls = false))
        // The ending is the PATH's: a query or a host that ends so is not a playlist.
        assertEquals(Playable.Stream("http://a.example.net/live?file=x.pls", null), StationUrl.playable("http://a.example.net/live?file=x.pls", "", hls = false))
        assertEquals(Playable.Stream("http://a.example.net/live.mp3", null), StationUrl.playable("http://a.example.net/live.mp3", "", hls = false))
    }
}

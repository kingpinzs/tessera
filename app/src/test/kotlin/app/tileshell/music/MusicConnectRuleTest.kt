package app.tileshell.music

import app.tileshell.music.MusicConnectRule.Hop
import app.tileshell.music.MusicConnectRule.Target
import app.tileshell.music.radio.StationUrl
import app.tileshell.music.radio.StreamFacts
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20, the review's R20-1 (the owner's ruling 2026-10-08): which address a request of the music player may
 * connect to, and which redirect it may follow. The rule is pure; that the player's sockets ask it at `connect`, and
 * its redirects before they are followed, is `MusicHttpTest`'s (a real client against a loopback server).
 */
class MusicConnectRuleTest {
    /** An address from its literal: no look-up is made for one. */
    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    private fun may(host: String, vararg resolved: String, homeServer: Boolean = false, qaHost: String? = null) =
        MusicConnectRule.mayConnect(host, resolved.map(::ip), homeServer, qaHost)

    private val public = listOf("8.8.8.8", "93.184.216.34", "1.1.1.1", "2001:4860:4860::8888", "2606:4700:4700::1111")

    private val private = listOf(
        // Loopback, RFC 1918, link-local (the cloud metadata address is one).
        "127.0.0.1", "127.1.2.3", "10.0.0.1", "10.255.255.254", "172.16.0.1", "172.31.255.254", "192.168.1.1", "192.168.0.254", "169.254.169.254",
        // Carrier-grade NAT, unspecified and "this network", the protocol and benchmarking blocks, multicast, reserved, broadcast.
        "100.64.0.1", "100.127.255.254", "0.0.0.0", "0.1.2.3", "192.0.0.8", "198.18.0.1", "198.19.255.254", "224.0.0.251", "239.255.255.250", "240.0.0.1", "255.255.255.255",
        // IPv6: loopback, unspecified, link-local, unique-local, site-local, multicast, NAT64, 6to4, and an unallocated block.
        "::1", "::", "fe80::1", "febf::1", "fc00::1", "fd12:3456:789a::1", "fec0::1", "ff02::1", "64:ff9b::c0a8:101", "2002:c0a8:101::1", "4000::1", "100::1",
    )

    @Test fun `a name whose every answer is public may connect`() {
        for (a in public) assertTrue(a, may("stream.example.org", a))
        assertTrue(may("stream.example.org", "8.8.8.8", "2001:4860:4860::8888", "93.184.216.34"))
        // Just outside each refused block.
        for (a in listOf("172.15.255.255", "172.32.0.1", "100.63.255.255", "100.128.0.1", "192.167.255.255", "192.169.0.1", "169.253.1.1", "169.255.1.1", "198.17.255.255", "198.20.0.1", "192.0.1.1", "223.255.255.254", "126.255.255.255", "128.0.0.1", "11.0.0.1", "9.255.255.255")) {
            assertTrue(a, may("edge.example.org", a))
        }
    }

    @Test fun `a name that resolves privately is refused - the review's nip_io, nas and router_lan`() {
        assertFalse(may("192.168.1.1.nip.io", "192.168.1.1"))
        assertFalse(may("nas", "192.168.1.20"))
        assertFalse(may("router.lan", "192.168.1.1"))
        assertFalse(may("localtest.example.org", "127.0.0.1"))
        assertFalse(may("metadata.example.org", "169.254.169.254"))
        for (a in private) assertFalse(a, may("evil.example.org", a))
    }

    @Test fun `one private answer among public ones refuses the name, wherever it stands`() {
        assertFalse(may("mixed.example.org", "8.8.8.8", "192.168.1.1"))
        assertFalse(may("mixed.example.org", "192.168.1.1", "8.8.8.8"))
        assertFalse(may("mixed.example.org", "8.8.8.8", "93.184.216.34", "::1"))
        assertFalse(may("mixed.example.org", "2001:4860:4860::8888", "fe80::1"))
        for (a in private) assertFalse(a, may("mixed.example.org", "1.1.1.1", a, "8.8.4.4"))
    }

    @Test fun `no answer at all is refused`() {
        assertFalse(may("empty.example.org"))
        assertFalse(MusicConnectRule.mayConnect("empty.example.org", emptyList(), homeServer = false, qaHost = null))
    }

    @Test fun `an IPv4-mapped IPv6 address is judged as the IPv4 address it is, and as sixteen bytes it is refused`() {
        // The platform hands a mapped address back as the IPv4 address it maps.
        assertTrue(ip("::ffff:192.168.1.1") is Inet4Address)
        assertFalse(may("mapped.example.org", "::ffff:192.168.1.1"))
        assertFalse(may("mapped.example.org", "::ffff:127.0.0.1"))
        assertFalse(may("mapped.example.org", "::ffff:10.0.0.1"))
        assertTrue(may("mapped.example.org", "::ffff:8.8.8.8"))
        // Were sixteen such bytes ever to arrive as they are, they are an IPv6 address with a zero first group: refused.
        fun mapped(a: Int, b: Int, c: Int, d: Int) = ByteArray(16).also { it[10] = -1; it[11] = -1; it[12] = a.toByte(); it[13] = b.toByte(); it[14] = c.toByte(); it[15] = d.toByte() }
        assertFalse(StationUrl.publicAddress(mapped(192, 168, 1, 1)))
        assertFalse(StationUrl.publicAddress(mapped(8, 8, 8, 8)))
        // IPv4-compatible (::a.b.c.d), NAT64 and 6to4 carry an IPv4 address too.
        assertFalse(StationUrl.publicAddress(ByteArray(16).also { it[12] = 192.toByte(); it[13] = 168.toByte(); it[14] = 1; it[15] = 1 }))
        assertFalse(may("nat64.example.org", "64:ff9b::7f00:1"))
        assertFalse(may("sixtofour.example.org", "2002:7f00:1::1"))
        // Not an address at all.
        assertFalse(StationUrl.publicAddress(ByteArray(0)))
        assertFalse(StationUrl.publicAddress(ByteArray(5)))
    }

    @Test fun `the ranges are the literal rule's own - an address is public exactly when StationUrl accept takes its literal`() {
        for (a in public + private + listOf("172.15.255.255", "100.63.255.255", "198.20.0.1", "2001:db8::1", "3fff::1", "2000::1", "1fff::1")) {
            val literal = if (a.contains(':')) "[$a]" else a
            val asText = StationUrl.accept("http://$literal/live", null) == StationUrl.Accept.Ok
            assertEquals(a, asText, StationUrl.publicAddress(ip(a).address))
            assertEquals(a, asText, may("name.example.org", a))
        }
    }

    @Test fun `the fixture host connects only when it is set, and only it`() {
        assertTrue(may("10.0.2.2", "10.0.2.2", qaHost = "10.0.2.2"))
        assertTrue(may("QA.Fixtures.Test", "10.0.2.2", qaHost = "qa.fixtures.test"))
        // A release build: no fixture host.
        assertFalse(may("10.0.2.2", "10.0.2.2", qaHost = null))
        assertFalse(may("10.0.2.2", "10.0.2.2", qaHost = ""))
        // Another host is not the fixture host, whatever it resolves to — the fixture host's own address included.
        assertFalse(may("10.0.2.3", "10.0.2.3", qaHost = "10.0.2.2"))
        assertFalse(may("evil.example.org", "10.0.2.2", qaHost = "10.0.2.2"))
        assertFalse(may("10.0.2.2.nip.io", "10.0.2.2", qaHost = "10.0.2.2"))
        assertFalse(may("", "10.0.2.2", qaHost = ""))
        assertTrue(may("stream.example.org", "8.8.8.8", qaHost = "10.0.2.2"))
    }

    @Test fun `the home server's own queued address connects to the private network`() {
        assertTrue(may("192.168.1.20", "192.168.1.20", homeServer = true))
        assertTrue(may("jellyfin.lan", "10.0.0.5", homeServer = true))
        assertTrue(may("jellyfin.lan", "8.8.8.8", homeServer = true))
        // The same host and address, asked for anything that is not the queued address: a station's rule.
        assertFalse(may("192.168.1.20", "192.168.1.20", homeServer = false))
        assertFalse(may("jellyfin.lan", "10.0.0.5", homeServer = false))
    }

    private fun target(url: String): Target {
        val scheme = url.substringBefore(':').lowercase()
        val host = url.substringAfter("//").substringBefore('/').substringBeforeLast(':').trim('[', ']').lowercase()
        return Target(url, scheme, host)
    }

    private fun hop(from: String, to: String?, followed: Int = 0, homeServer: Boolean = false, qaHost: String? = null): Hop =
        target(from).let { MusicConnectRule.hop(it.scheme, it.host, to?.let(::target), followed, homeServer, qaHost) }

    @Test fun `a station's redirect to a public address is followed - as a station's`() {
        assertEquals(Hop.Follow(homeServer = false), hop("http://stream.example.org/live", "http://edge7.example.net/live?id=1"))
        assertEquals(Hop.Follow(homeServer = false), hop("https://stream.example.org/live", "https://stream.example.org/live2"))
        assertEquals(Hop.Follow(homeServer = false), hop("http://stream.example.org/live", "http://93.184.216.34:8000/live"))
        assertEquals(Hop.Follow(homeServer = false), hop("http://10.0.2.2:8125/a", "http://10.0.2.2:8125/b", qaHost = "10.0.2.2"))
    }

    @Test fun `a redirect to a private literal is refused - the review's 192_168_1_1 and loopback, and every spelling of one`() {
        for (to in listOf(
            "http://192.168.1.1/cm?cmnd=Power%20Toggle", "http://127.0.0.1:8080/x", "http://localhost/x", "http://[::1]/x", "http://10.0.0.1/", "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/", "http://0.0.0.0/", "http://[fe80::1]/", "http://[::ffff:192.168.1.1]/", "http://0x7f.0.0.1/", "http://127.1/", "http://2130706433/", "http://017700000001/",
            "http://user@192.168.1.1/", "http://192.168.1.1./", "http://10.0.2.2:8125/b",
        )) assertEquals(to, Hop.Refused(MusicConnectRule.REFUSED), hop("http://stream.example.org/live", to))
        // A NAME is text the redirect's rule takes; what it resolves to is mayConnect's, at the connection.
        assertEquals(Hop.Follow(homeServer = false), hop("http://stream.example.org/live", "http://192.168.1.1.nip.io/relay/0?turn=on"))
        assertFalse(may("192.168.1.1.nip.io", "192.168.1.1"))
    }

    @Test fun `a cross-protocol redirect stays refused, in Media3's words - so its line stays redirect refused`() {
        for ((from, to) in listOf("https://stream.example.org/live" to "http://stream.example.org/live", "http://stream.example.org/live" to "https://stream.example.org/live", "http://stream.example.org/live" to "HTTPS://Other.example.org/")) {
            val refused = hop(from, to) as Hop.Refused
            assertEquals(StreamFacts.CROSS_PROTOCOL, refused.message)
            assertTrue(StreamFacts.redirectRefused(listOf(null, refused.message)))
        }
        // Before anything else about the address: a cross-protocol redirect to a private host is still "redirect refused".
        assertEquals(Hop.Refused(StreamFacts.CROSS_PROTOCOL), hop("https://stream.example.org/live", "http://192.168.1.1/"))
        assertEquals(Hop.Refused(StreamFacts.CROSS_PROTOCOL), hop("http://192.168.1.20:8096/Audio/a/stream", "https://192.168.1.20:8920/Audio/a/stream", homeServer = true))
    }

    @Test fun `a redirect that names no http or https address is refused, and so is the one past the cap`() {
        assertEquals(Hop.Refused(MusicConnectRule.NO_LOCATION), hop("http://stream.example.org/live", null))
        assertEquals(20, MusicConnectRule.MAX_HOPS)
        assertEquals(Hop.Follow(homeServer = false), hop("http://stream.example.org/live", "http://stream.example.org/next", followed = 19))
        assertEquals(Hop.Refused(MusicConnectRule.TOO_MANY), hop("http://stream.example.org/live", "http://stream.example.org/next", followed = 20))
        assertEquals(Hop.Refused(MusicConnectRule.TOO_MANY), hop("http://192.168.1.20:8096/a", "http://192.168.1.20:8096/a", followed = 20, homeServer = true))
    }

    @Test fun `the home server's exception stays on its host - a redirect to another host is judged like a station's`() {
        val server = "http://192.168.1.20:8096/Audio/0a1b2c3d4e5f/stream?static=true"
        // On its own host: still the home server's.
        assertEquals(Hop.Follow(homeServer = true), hop(server, "http://192.168.1.20:8096/Audio/0a1b2c3d4e5f/stream.mp3", homeServer = true))
        assertEquals(Hop.Follow(homeServer = true), hop("http://jellyfin.lan/Audio/a/stream", "http://jellyfin.lan:8096/Audio/a/stream", homeServer = true))
        // Another host: a public one is followed as a station's (so what it resolves to is judged); a private one is refused.
        assertEquals(Hop.Follow(homeServer = false), hop(server, "http://cdn.example.org/a.mp3", homeServer = true))
        assertEquals(Hop.Follow(homeServer = false), hop(server, "http://router.lan/x", homeServer = true))
        assertFalse(may("router.lan", "192.168.1.1", homeServer = false))
        for (to in listOf("http://192.168.1.1/cm?cmnd=Power%20Toggle", "http://192.168.1.21:8096/x", "http://127.0.0.1:8096/x", "http://localhost:8096/x")) {
            assertEquals(to, Hop.Refused(MusicConnectRule.REFUSED), hop(server, to, homeServer = true))
        }
        // And nothing brings the exception back: a station's redirect to the home server's host is a station's.
        assertEquals(Hop.Refused(MusicConnectRule.REFUSED), hop("http://stream.example.org/live", server, homeServer = false))
        assertEquals(Hop.Refused(MusicConnectRule.REFUSED), hop("http://192.168.1.20:8096/a", "http://192.168.1.20:8096/b", homeServer = false))
        assertEquals(Hop.Follow(homeServer = false), hop("http://stream.example.org/live", "http://jellyfin.lan/Audio/a/stream", homeServer = false))
        assertFalse(may("jellyfin.lan", "192.168.1.20", homeServer = false))
    }

    @Test fun `the refusal's words name no address`() {
        assertEquals("the music player refused to connect to an address", MusicConnectRule.REFUSED)
        for (words in listOf(MusicConnectRule.REFUSED, MusicConnectRule.TOO_MANY, MusicConnectRule.NO_LOCATION, StreamFacts.CROSS_PROTOCOL)) assertFalse(words, words.contains('/') || words.contains('.') || words.any(Char::isDigit))
    }
}

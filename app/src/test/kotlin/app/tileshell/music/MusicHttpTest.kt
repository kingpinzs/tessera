package app.tileshell.music

import app.tileshell.music.radio.StreamFacts
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20, the review's R20-1: `MusicHttp` — the client the music player's ONE http data source and the logo fetch
 * are made of — against a real server on this machine's loopback. Loopback is exactly a private address, so every
 * attack of the review can be run for real: the "station" is the fixture host (the one private host a debug build
 * lets through), and what it must never reach is [trap], a second port that only counts who CONNECTS to it.
 *
 * The rule's own cases are `MusicConnectRuleTest`'s. Not run here: TLS (the judged socket is the raw socket TLS is
 * layered on) and Media3's `OkHttpDataSource` over these calls — both need the platform.
 */
class MusicHttpTest {
    private val loopback: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    /** Every request the server answered: `METHOD path`. */
    private val seen: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val server: HttpServer = HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
        createContext("/") { exchange -> answer(exchange) }
        start()
    }
    private val port = server.address.port

    /** A port that answers nothing and counts every TCP connection made to it. */
    private val trapSocket = ServerSocket(0, 50, loopback)
    private val trapped = AtomicInteger()
    private val trap = trapSocket.localPort
    private val trapThread = Thread {
        while (true) {
            val s = try { trapSocket.accept() } catch (e: IOException) { return@Thread }
            trapped.incrementAndGet()
            runCatching { s.close() }
        }
    }.apply { isDaemon = true; start() }

    /** The names this "network" knows, and how often each was asked. */
    private val names = HashMap<String, List<InetAddress>>()
    private val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private fun resolve(name: String): List<InetAddress> {
        asked += name
        return names[name] ?: throw UnknownHostException(name)
    }

    private val qa = "radio.qa"
    private val servers: MutableSet<String> = mutableSetOf()

    init {
        names[qa] = listOf(loopback)
    }

    private fun http(qaHost: String? = qa) = MusicHttp(qaHost, { servers }, ::resolve)

    @After fun stop() {
        server.stop(0)
        trapSocket.close()
    }

    /**
     * `/ok…` → 200 "audio", echoing the request's `Range` and `Icy-MetaData`; `/to?u=<address>` → 302 there (`code=`
     * for another answer); `/loop` → 302 to itself; `/big?n=` → n bytes with no declared length; `/nowhere` → a 302
     * with no Location.
     */
    private fun answer(exchange: HttpExchange) {
        val uri = exchange.requestURI
        seen += exchange.requestMethod + " " + uri.path
        val query = uri.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        fun body(code: Int, bytes: ByteArray, declared: Boolean = true) {
            exchange.sendResponseHeaders(code, if (declared) bytes.size.toLong().coerceAtLeast(1).let { if (bytes.isEmpty()) -1 else it } else 0)
            exchange.responseBody.use { it.write(bytes) }
        }
        when {
            uri.path.startsWith("/to") -> {
                exchange.responseHeaders.add("Location", query.getValue("u"))
                body(query["code"]?.toInt() ?: 302, ByteArray(0))
            }
            uri.path == "/loop" -> {
                exchange.responseHeaders.add("Location", "/loop")
                body(302, ByteArray(0))
            }
            uri.path == "/nowhere" -> body(302, ByteArray(0))
            uri.path == "/big" -> body(200, ByteArray(query.getValue("n").toInt()) { 7 }, declared = query["declared"] == "1")
            else -> {
                for (h in listOf("Range", "Icy-MetaData", "User-Agent")) exchange.requestHeaders.getFirst(h)?.let { exchange.responseHeaders.add("X-Got-$h", it) }
                exchange.responseHeaders.add("icy-metaint", "16000")
                body(200, "audio".toByteArray())
            }
        }
    }

    private fun get(http: MusicHttp, url: String, vararg headers: Pair<String, String>): Response =
        http.calls.newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()).execute()

    /** The call must fail; the error is handed back. Nothing may have connected to [trap] (given a moment to arrive). */
    private fun refused(http: MusicHttp, url: String): IOException {
        val error = try {
            get(http, url).close()
            null
        } catch (e: IOException) {
            e
        }
        Thread.sleep(150)
        assertEquals("connections to the trap after $url", 0, trapped.get())
        return error ?: throw AssertionError("$url was answered")
    }

    private fun assertAddressRefused(http: MusicHttp, url: String) {
        val error = refused(http, url)
        assertTrue("$url: ${error.javaClass.simpleName} ${error.message}", MusicHttp.refused(error))
        assertEquals(MusicConnectRule.REFUSED, generateSequence<Throwable>(error) { it.cause }.first { it is MusicHttp.RefusedAddress }.message)
    }

    // ------------------------------------------------------------------------------------------------- the name

    @Test fun `the fixture station plays - one look-up, the judged address connected, the player's headers sent`() {
        get(http(), "http://$qa:$port/ok/live.mp3", "Icy-MetaData" to "1", "Range" to "bytes=0-").use { response ->
            assertEquals(200, response.code)
            assertEquals("audio", response.body!!.string())
            assertEquals("1", response.header("X-Got-Icy-MetaData"))
            assertEquals("bytes=0-", response.header("X-Got-Range"))
            // What Media3 reads the ICY interval from: the answer's headers, by lower-case name.
            assertEquals(listOf("16000"), response.headers.toMultimap()["icy-metaint"])
        }
        assertEquals(listOf(qa), asked)
        assertEquals(listOf("GET /ok/live.mp3"), seen)
    }

    @Test fun `a name that resolves to a private address is refused before anything connects - nip_io, nas, router_lan`() {
        for (name in listOf("192.168.1.1.nip.io", "nas", "router.lan", "rebind.example.org")) {
            // The name answers with THIS machine's loopback, at the trap's port: a connection would be counted.
            names[name] = listOf(loopback)
            assertAddressRefused(http(), "http://$name:$trap/cm?cmnd=Power%20Toggle")
            assertTrue(name, name in asked)
        }
        names["lan.example.org"] = listOf(InetAddress.getByName("192.168.1.1"))
        assertAddressRefused(http(), "http://lan.example.org/relay/0?turn=on")
        assertEquals(emptyList<String>(), seen)
    }

    @Test fun `a name with one public and one private answer is refused, and a name with no answer`() {
        names["mixed.example.org"] = listOf(InetAddress.getByName("93.184.216.34"), loopback)
        assertAddressRefused(http(), "http://mixed.example.org:$trap/x")
        names["mixed2.example.org"] = listOf(loopback, InetAddress.getByName("93.184.216.34"))
        assertAddressRefused(http(), "http://mixed2.example.org:$trap/x")
        names["empty.example.org"] = emptyList()
        assertAddressRefused(http(), "http://empty.example.org:$trap/x")
        // A name nobody answers for is the look-up's own error, not a refusal.
        assertFalse(MusicHttp.refused(refused(http(), "http://unknown.example.org:$trap/x")))
    }

    @Test fun `a private literal is refused at the socket - no look-up is made for one, and none is needed`() {
        for (literal in listOf("127.0.0.1", "[::1]", "127.0.0.2")) assertAddressRefused(http(), "http://$literal:$trap/x")
        assertAddressRefused(http(qaHost = null), "http://127.0.0.1:$port/ok")
        assertEquals(emptyList<String>(), asked)
        assertEquals(emptyList<String>(), seen)
    }

    @Test fun `the fixture host is let through as a literal too, and in a release build - no fixture host - it is not`() {
        get(http(qaHost = "127.0.0.1"), "http://127.0.0.1:$port/ok").use { assertEquals(200, it.code) }
        assertAddressRefused(http(qaHost = null), "http://$qa:$port/ok")
        // The fixture host's exception is its NAME's: another name with the same address is refused.
        names["other.qa"] = listOf(loopback)
        assertAddressRefused(http(), "http://other.qa:$port/ok")
        assertEquals(listOf("GET /ok"), seen)
    }

    // --------------------------------------------------------------------------------------------- the redirect

    private fun to(location: String, code: Int = 302) = "http://$qa:$port/to?code=$code&u=" + java.net.URLEncoder.encode(location, "UTF-8")

    @Test fun `a redirect to a public-by-rule address is followed, with the request's headers, and the answer is the last address's`() {
        for (code in listOf(300, 301, 302, 303, 307, 308)) {
            seen.clear()
            get(http(), to("http://$qa:$port/ok/edge", code), "Icy-MetaData" to "1", "Range" to "bytes=100-").use { response ->
                assertEquals(200, response.code)
                // What the data source reports as the opened address — HLS resolves a playlist's entries against it.
                assertEquals("http://$qa:$port/ok/edge", response.request.url.toString())
                assertEquals("1", response.header("X-Got-Icy-MetaData"))
                assertEquals("bytes=100-", response.header("X-Got-Range"))
            }
            assertEquals("$code", listOf("GET /to", "GET /ok/edge"), seen)
        }
        // A relative Location is resolved against the request.
        get(http(), to("/ok/relative")).use { assertEquals("http://$qa:$port/ok/relative", it.request.url.toString()) }
    }

    @Test fun `a redirect to a private literal is refused and never connected to - the review's attack 2`() {
        for (location in listOf("http://127.0.0.1:$trap/cm?cmnd=Power%20Toggle", "http://[::1]:$trap/x", "http://localhost:$trap/x", "http://192.168.1.1/relay/0?turn=on", "http://0x7f.0.0.1:$trap/x", "http://2130706433:$trap/x")) {
            seen.clear()
            assertAddressRefused(http(), to(location))
            assertEquals(location, listOf("GET /to"), seen)
        }
        // The fixture host's own ADDRESS, as a literal, is not the fixture host.
        assertAddressRefused(http(), to("http://127.0.0.1:$port/ok"))
    }

    @Test fun `a redirect to a name that resolves privately is refused and never connected to`() {
        names["nas"] = listOf(loopback)
        names["192.168.1.1.nip.io"] = listOf(InetAddress.getByName("192.168.1.1"), loopback)
        for (name in listOf("nas", "192.168.1.1.nip.io")) {
            asked.clear()
            assertAddressRefused(http(), to("http://$name:$trap/x"))
            assertEquals(listOf(qa, name), asked)
        }
        // Two hops in: public-by-rule, then private.
        assertAddressRefused(http(), to(to("http://nas:$trap/x")))
    }

    @Test fun `a cross-protocol redirect is refused in Media3's words, whatever it points to`() {
        for (location in listOf("https://$qa:$port/ok", "https://127.0.0.1:$trap/x", "https://stream.example.org/live")) {
            val error = refused(http(), to(location))
            assertFalse(MusicHttp.refused(error))
            assertTrue(error.message, StreamFacts.redirectRefused(generateSequence<Throwable>(error) { it.cause }.map { it.message }.toList()))
        }
    }

    @Test fun `the hop cap - twenty redirects are followed and the twenty-first is not`() {
        val error = refused(http(), "http://$qa:$port/loop")
        assertEquals(MusicConnectRule.TOO_MANY, error.message)
        assertEquals(MusicConnectRule.MAX_HOPS + 1, seen.count { it == "GET /loop" })
    }

    @Test fun `a redirect with no Location, or to another scheme, is refused`() {
        assertEquals(MusicConnectRule.NO_LOCATION, refused(http(), "http://$qa:$port/nowhere").message)
        for (location in listOf("file:///data/data/app.tileshell/files/x", "content://media/external/audio/media/1", "ftp://127.0.0.1:$trap/x", "rtsp://127.0.0.1:$trap/x")) {
            assertEquals(location, MusicConnectRule.NO_LOCATION, refused(http(), to(location)).message)
        }
    }

    // ------------------------------------------------------------------------------------------ the home server

    @Test fun `the home server's queued address connects on the private network - and its range re-open`() {
        val track = "http://127.0.0.1:$port/ok/Audio/0a1b2c3d/stream?static=true"
        // Not queued: a private literal like any other.
        assertAddressRefused(http(qaHost = null), track)
        servers += track
        val http = http(qaHost = null)
        get(http, track).use { assertEquals(200, it.code) }
        get(http, track, "Range" to "bytes=4096-").use { assertEquals("bytes=4096-", it.header("X-Got-Range")) }
        assertEquals(emptyList<String>(), asked)
        // The exception is that ADDRESS, not its host: another path, another port, the query left off — a station's rule,
        // and the connection the server's lane keeps open is not there for it to ride.
        for (other in listOf("http://127.0.0.1:$port/ok", "http://127.0.0.1:$port/ok/Audio/0a1b2c3d/stream", "http://127.0.0.1:$port/ok/Audio/ffff/stream?static=true", "http://127.0.0.1:$trap/ok/Audio/0a1b2c3d/stream?static=true")) {
            assertAddressRefused(http, other)
        }
        assertEquals(2, seen.size)
    }

    @Test fun `a queued server address is matched as the client writes it`() {
        servers += "HTTP://LOCALHOST:$port/ok/Audio/a/stream?static=true"
        get(http(qaHost = null), "http://localhost:$port/ok/Audio/a/stream?static=true").use { assertEquals(200, it.code) }
    }

    @Test fun `the home server's redirect on its own host is followed, and one to another host is judged like a station's`() {
        val onHost = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode("http://127.0.0.1:$port/ok/moved", "UTF-8")
        val toPublicByRule = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode("http://$qa:$port/ok/cdn", "UTF-8")
        val toPrivateName = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode("http://nas:$trap/x", "UTF-8")
        val toPrivateLiteral = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode("http://127.0.0.2:$trap/x", "UTF-8")
        val toLocalhost = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode("http://localhost:$trap/x", "UTF-8")
        val backAgain = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode(to("http://127.0.0.1:$port/ok/back"), "UTF-8")
        servers += listOf(onHost, toPublicByRule, toPrivateName, toPrivateLiteral, toLocalhost, backAgain)
        names["nas"] = listOf(loopback)
        val http = http()
        get(http, onHost).use { assertEquals("http://127.0.0.1:$port/ok/moved", it.request.url.toString()) }
        // Another host that is public by the rule (here: the fixture host) is followed — as a station's, so it is resolved and judged.
        get(http, toPublicByRule, "Range" to "bytes=7-").use {
            assertEquals("http://$qa:$port/ok/cdn", it.request.url.toString())
            assertEquals("bytes=7-", it.header("X-Got-Range"))
        }
        assertEquals(listOf(qa), asked)
        assertAddressRefused(http, toPrivateName)
        assertAddressRefused(http, toPrivateLiteral)
        assertAddressRefused(http, toLocalhost)
        // Once off its host the exception does not come back: server → station → the server's host again is refused.
        assertAddressRefused(http, backAgain)
        // And a STATION's redirect to the server's queued address is a station's redirect to a private literal.
        assertAddressRefused(http, to(onHost))
    }

    @Test fun `the cap counts across the server's lane and the station's`() {
        val start = "http://127.0.0.1:$port/to?u=" + java.net.URLEncoder.encode("http://$qa:$port/loop", "UTF-8")
        servers += start
        assertEquals(MusicConnectRule.TOO_MANY, refused(http(), start).message)
        assertEquals(MusicConnectRule.MAX_HOPS, seen.count { it == "GET /loop" })
    }

    // ------------------------------------------------------------------------------------------------- the logo

    private val headers = mapOf("User-Agent" to "tessera-test/1")

    @Test fun `a logo is fetched through the judged client - the bytes, the headers, the cap`() {
        assertArrayEquals("audio".toByteArray(), http().bytes("http://$qa:$port/ok/favicon.ico", headers, 512 * 1024L))
        assertArrayEquals(ByteArray(1000) { 7 }, http().bytes("http://$qa:$port/big?n=1000", headers, 1000L))
        // Over the cap: by its declared length, and when it declares none.
        assertNull(http().bytes("http://$qa:$port/big?n=1001&declared=1", headers, 1000L))
        assertNull(http().bytes("http://$qa:$port/big?n=1001", headers, 1000L))
        assertNull(http().bytes("http://$qa:$port/big?n=200000", headers, 1000L))
    }

    @Test fun `a logo on a private name or literal is not fetched, no redirect is followed, and no error leaves the call`() {
        names["nas"] = listOf(loopback)
        names["mixed.example.org"] = listOf(InetAddress.getByName("93.184.216.34"), loopback)
        for (url in listOf("http://nas:$trap/favicon.ico", "http://mixed.example.org:$trap/favicon.ico", "http://127.0.0.1:$trap/favicon.ico", "http://192.168.1.1.nip.io:$trap/favicon.ico", "http://[::1]:$trap/favicon.ico")) {
            assertNull(url, http().bytes(url, headers, 512 * 1024L))
        }
        // A redirect — to a public-by-rule address or a private one — is not followed at all.
        assertNull(http().bytes(to("http://$qa:$port/ok/logo.png"), headers, 512 * 1024L))
        assertNull(http().bytes(to("http://127.0.0.1:$trap/x"), headers, 512 * 1024L))
        assertEquals(listOf("GET /to", "GET /to"), seen)
        // What the client will not write is "no logo", never an exception.
        assertNull(http().bytes("not an address", headers, 512 * 1024L))
        assertNull(http().bytes("file:///sdcard/x.png", headers, 512 * 1024L))
        assertNull(http().bytes("http://$qa:$port/ok", mapOf("Bad\nName" to "x"), 512 * 1024L))
        // The home server's exception is the player's alone: the logo client has no such lane.
        servers += "http://127.0.0.1:$port/ok/logo.png"
        assertNull(http(qaHost = null).bytes("http://127.0.0.1:$port/ok/logo.png", headers, 512 * 1024L))
        Thread.sleep(150)
        assertEquals(0, trapped.get())
    }

    @Test fun `the platform's User-Agent is sent as a header can carry it`() {
        val before = System.getProperty("http.agent")
        try {
            System.setProperty("http.agent", "Dalvik/2.1.0 (Linux; U; Android 16; SM-S938U Build/BP2A.250605.031)")
            assertEquals("Dalvik/2.1.0 (Linux; U; Android 16; SM-S938U Build/BP2A.250605.031)", MusicHttp.platformAgent())
            // A model name with a character no header may hold: the client would throw for it on every open.
            System.setProperty("http.agent", "Dalvik/2.1.0 (Linux; U; Android 16; Téléphone\n\u4e2d Build/X)")
            val agent = MusicHttp.platformAgent()!!
            assertEquals("Dalvik/2.1.0 (Linux; U; Android 16; Tlphone Build/X)", agent)
            get(http(), "http://$qa:$port/ok", "User-Agent" to agent).use { assertEquals(agent, it.header("X-Got-User-Agent")) }
            System.setProperty("http.agent", " \u00a0 ")
            assertNull(MusicHttp.platformAgent())
            System.clearProperty("http.agent")
            assertNull(MusicHttp.platformAgent())
        } finally {
            if (before == null) System.clearProperty("http.agent") else System.setProperty("http.agent", before)
        }
    }

    // ---------------------------------------------------------------------------------------------- the refusal

    @Test fun `a refusal is found under what the player wraps it in, and nothing else is one`() {
        assertTrue(MusicHttp.refused(MusicHttp.RefusedAddress()))
        assertEquals(MusicConnectRule.REFUSED, MusicHttp.RefusedAddress().message)
        assertTrue(MusicHttp.refused(IOException("open failed", RuntimeException(IOException("x", MusicHttp.RefusedAddress())))))
        assertFalse(MusicHttp.refused(null))
        assertFalse(MusicHttp.refused(IOException(MusicConnectRule.REFUSED)))
        assertFalse(MusicHttp.refused(java.net.ConnectException("Connection refused")))
        assertFalse(MusicHttp.refused(UnknownHostException("stream.example.org")))
    }
}

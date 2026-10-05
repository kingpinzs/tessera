package app.tileshell.video

import app.tileshell.diag.Diagnostics
import app.tileshell.video.catalogue.FetchOutcome
import app.tileshell.video.catalogue.VideoHttp
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 17 trust review, B-1 (b): [VideoHttp] against a real server on the loopback interface (the JDK's own
 * `HttpServer`). A header value the platform refuses must end as "no connection" — no exception leaves the call, and
 * the refused value is in nothing the call returns and in no diagnostics line.
 */
class VideoHttpTest {
    private lateinit var server: HttpServer
    private val requests = AtomicInteger(0)
    private val base: String get() = "http://127.0.0.1:${server.address.port}"

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/ok") { ex ->
            requests.incrementAndGet()
            val body = "{\"ok\":true}".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { ex ->
            requests.incrementAndGet()
            ex.sendResponseHeaders(404, -1)
            ex.close()
        }
        // An answer of the length the query names: with a Content-Length, or (`/stream`) chunked with none.
        for (path in listOf("/sized", "/stream")) {
            server.createContext(path) { ex ->
                requests.incrementAndGet()
                val length = ex.requestURI.query.toInt()
                ex.sendResponseHeaders(200, if (path == "/sized") length.toLong() else 0L)
                ex.responseBody.use { out -> runCatching { out.write(ByteArray(length) { 'a'.code.toByte() }) } }
            }
        }
        // B2-M4: a redirect to ANOTHER host (the second server), and what that host would see.
        other = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        other.createContext("/") { ex ->
            otherRequests.incrementAndGet()
            ex.requestHeaders.getFirst("Authorization")?.let { otherSawAuthorization.incrementAndGet() }
            val body = "{\"landed\":true}".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        other.start()
        server.createContext("/away") { ex ->
            requests.incrementAndGet()
            ex.requestBody.use { it.readBytes() }
            ex.responseHeaders.add("Location", "http://127.0.0.1:${other.address.port}/landed")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        server.start()
    }

    @After fun stop() {
        server.stop(0)
        other.stop(0)
    }

    private lateinit var other: HttpServer
    private val otherRequests = AtomicInteger(0)
    private val otherSawAuthorization = AtomicInteger(0)

    @Test fun `the control - the redirect is real, and an image request with no header follows it`() {
        // A poster has no header to carry: it may follow a redirect, and that is how this test knows the 302 works.
        assertEquals("{\"landed\":true}", VideoHttp.bytes("$base/away")?.let { String(it) })
        assertEquals(1, otherRequests.get())
        assertEquals(0, otherSawAuthorization.get())
    }

    @Test fun `a GET that carries headers does not follow a redirect, and the second host sees no request`() {
        val before = requests.get()
        assertEquals(FetchOutcome.Status(302), VideoHttp.get("$base/away", mapOf("Authorization" to "Bearer QA-SECRET-ONE", "Accept" to "application/json")))
        assertEquals("the first host was asked once", before + 1, requests.get())
        assertEquals("the second host", 0, otherRequests.get())
        // With no header of its own either: one rule for every JSON request.
        assertEquals(FetchOutcome.Status(302), VideoHttp.get("$base/away", emptyMap()))
        assertEquals("the second host", 0, otherRequests.get())
    }

    @Test fun `a POST that carries headers does not follow a redirect, and the second host sees no request`() {
        assertEquals(FetchOutcome.Status(302), VideoHttp.post("$base/away", mapOf("Authorization" to "MediaBrowser Token=\"QA-SECRET-ONE\""), "{\"Pw\":\"QA-PASSWORD\"}"))
        assertEquals("the second host", 0, otherRequests.get())
    }

    @Test fun `an image request that carries headers does not follow a redirect, and the second host sees no request`() {
        assertNull(VideoHttp.bytes("$base/away", mapOf("Authorization" to "MediaBrowser Token=\"QA-SECRET-ONE\"")))
        assertEquals("the second host", 0, otherRequests.get())
        assertEquals(0, otherSawAuthorization.get())
    }

    /** A secret with a line feed inside: `setRequestProperty` throws IllegalArgumentException quoting the whole value. */
    private val poisoned = "Bearer QA-SECRET-ONE\nQA-SECRET-TWO"

    private fun linesSince(mark: Int): List<String> = Diagnostics.snapshot().drop(mark).map { it.message }

    @Test fun `the control - the platform does refuse this value, quoting it`() {
        val conn = java.net.URL("$base/ok").openConnection() as java.net.HttpURLConnection
        val thrown = runCatching { conn.setRequestProperty("Authorization", poisoned) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown!!.message.orEmpty().contains("QA-SECRET-ONE"))
    }

    @Test fun `an ordinary request is answered, and a 404 is a status`() {
        assertEquals(FetchOutcome.Answer("{\"ok\":true}"), VideoHttp.get("$base/ok", mapOf("Authorization" to "Bearer plain", "Accept" to "application/json")))
        assertEquals(FetchOutcome.Status(404), VideoHttp.get("$base/missing", emptyMap()))
        assertEquals("{\"ok\":true}", VideoHttp.bytes("$base/ok")?.let { String(it) })
        assertNull(VideoHttp.bytes("$base/missing"))
    }

    @Test fun `a GET whose header the platform refuses is no connection, and the value is nowhere`() {
        val mark = Diagnostics.snapshot().size
        val before = requests.get()
        val outcome = VideoHttp.get("$base/ok", mapOf("Authorization" to poisoned))
        assertEquals(FetchOutcome.NoConnection, outcome)
        assertEquals("nothing was sent", before, requests.get())
        assertFalse(outcome.toString().contains("QA-SECRET"))
        val lines = linesSince(mark)
        assertTrue(lines.toString(), lines.none { it.contains("QA-SECRET") || it.contains("Bearer") })
        assertTrue(lines.toString(), lines.any { it == "http: request not sent (IllegalArgumentException)" })
    }

    @Test fun `a POST whose header the platform refuses is no connection, and neither value nor body is anywhere`() {
        val mark = Diagnostics.snapshot().size
        val outcome = VideoHttp.post("$base/ok", mapOf("Authorization" to poisoned), "{\"Pw\":\"QA-PASSWORD\"}")
        assertEquals(FetchOutcome.NoConnection, outcome)
        val lines = linesSince(mark)
        assertTrue(lines.toString(), lines.none { it.contains("QA-SECRET") || it.contains("QA-PASSWORD") })
    }

    @Test fun `an image request whose header the platform refuses gives nothing, and the value is nowhere`() {
        val mark = Diagnostics.snapshot().size
        assertNull(VideoHttp.bytes("$base/ok", mapOf("Authorization" to poisoned)))
        val lines = linesSince(mark)
        assertTrue(lines.toString(), lines.none { it.contains("QA-SECRET") })
        assertTrue(lines.toString(), lines.any { it == "http: request not sent (IllegalArgumentException)" })
    }

    @Test fun `a JSON answer up to the cap is kept, and one byte over is the error state`() {
        val cap = 4096L
        for (path in listOf("/sized", "/stream")) {
            val atCap = VideoHttp.get("$base$path?4096", emptyMap(), maxBytes = cap)
            assertEquals(path, 4096, (atCap as FetchOutcome.Answer).body.length)
            assertEquals(path, FetchOutcome.TooLarge, VideoHttp.get("$base$path?4097", emptyMap(), maxBytes = cap))
            assertEquals(path, FetchOutcome.TooLarge, VideoHttp.post("$base$path?100000", emptyMap(), "{}", maxBytes = cap))
        }
        assertEquals("error too large", app.tileshell.video.catalogue.CatalogueRules.errorStatus(FetchOutcome.TooLarge))
        assertEquals("The catalogue isn't answering", app.tileshell.video.catalogue.CatalogueRules.notice(FetchOutcome.TooLarge).text)
    }

    @Test fun `an image up to the cap is kept, and one byte over is nothing`() {
        val cap = 4096L
        for (path in listOf("/sized", "/stream")) {
            assertEquals(path, 4096, VideoHttp.bytes("$base$path?4096", maxBytes = cap)!!.size)
            val mark = Diagnostics.snapshot().size
            assertNull(path, VideoHttp.bytes("$base$path?4097", maxBytes = cap))
            assertTrue(path, linesSince(mark).any { it == "http: answer over the size cap" })
        }
    }

    @Test fun `the caps are what the hub's answers need and no more`() {
        assertEquals(16L * 1024 * 1024, VideoHttp.MAX_JSON_BYTES)
        assertEquals(8L * 1024 * 1024, VideoHttp.MAX_IMAGE_BYTES)
    }

    @Test fun `an address that is not one is no connection`() {
        assertEquals(FetchOutcome.NoConnection, VideoHttp.get("not a url", emptyMap()))
        assertEquals(FetchOutcome.NoConnection, VideoHttp.get("http://127.0.0.1:1/ok", emptyMap()))
        assertNull(VideoHttp.bytes("not a url"))
    }
}

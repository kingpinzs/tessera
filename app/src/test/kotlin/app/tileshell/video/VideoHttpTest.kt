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
        server.start()
    }

    @After fun stop() {
        server.stop(0)
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

    @Test fun `an address that is not one is no connection`() {
        assertEquals(FetchOutcome.NoConnection, VideoHttp.get("not a url", emptyMap()))
        assertEquals(FetchOutcome.NoConnection, VideoHttp.get("http://127.0.0.1:1/ok", emptyMap()))
        assertNull(VideoHttp.bytes("not a url"))
    }
}

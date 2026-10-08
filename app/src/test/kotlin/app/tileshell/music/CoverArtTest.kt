package app.tileshell.music

import app.tileshell.music.catalogue.CoverArt
import app.tileshell.video.catalogue.ImageHop
import app.tileshell.video.catalogue.VideoHttp
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20, r3 D13 (trust): the Cover Art Archive's redirects are followed by hand — https `archive.org` and its
 * subdomains only, at most three, with no header carried — and `VideoHttp.hop` is the one request that follows nothing.
 */
class CoverArtTest {
    @Test fun `https archive_org and its subdomains are followed`() {
        for (location in listOf(
            "https://archive.org/download/mbid-76df3287/mbid-76df3287-829521842_thumb250.jpg",
            "https://dn710604.ca.archive.org/0/items/mbid-76df3287/x_thumb250.jpg",
            "https://ia800100.us.archive.org/1/items/x.jpg?cnt=0",
            "HTTPS://Archive.ORG/download/x.jpg",
            "https://archive.org",
        )) assertTrue(location, CoverArt.mayFollow(location))
    }

    @Test fun `http, another host and a look-alike are refused`() {
        for (location in listOf(
            "http://archive.org/download/x.jpg",
            "http://dn710604.ca.archive.org/x.jpg",
            "https://coverartarchive.org/release/x/front-250",
            "https://example.com/x.jpg",
            "https://archive.org.evil.example/x.jpg",
            "https://evilarchive.org/x.jpg",
            "https://archive.org@evil.example/x.jpg",
            "https://evil.example/archive.org/x.jpg",
            "https://evil.example?https://archive.org/",
            "https://evil.example#.archive.org",
            "https://archive.org:8443/x.jpg",
            "https://.archive.org/x.jpg",
            "https://a..archive.org/x.jpg",
            "https://archive.org\\@evil.example/x.jpg",
            "https://archive.org%2eevil.example/x.jpg",
            "https://archive.org /x.jpg",
            "https://archive.org\n.evil.example/x.jpg",
            "https://[::1]/x.jpg",
            "//archive.org/x.jpg",
            "/download/x.jpg",
            "file:///sdcard/x.jpg",
            "content://media/external/x",
            "",
        )) assertFalse(location, CoverArt.mayFollow(location))
        assertFalse(CoverArt.mayFollow(null))
    }

    // Review R20-7: a port was refused only by the suffix check, in the cases tested.
    @Test fun `a port is refused - on the archive's own host, and where the text after it ends as the archive does`() {
        for (location in listOf(
            "https://archive.org:443/x", "https://ia800100.us.archive.org:8443/x.jpg", "https://archive.org:/x",
            "https://evil.example:.archive.org/x", "https://evil.example:443.archive.org/x", "https://evil.example:x.archive.org",
        )) assertFalse(location, CoverArt.mayFollow(location))
    }

    private val start = "https://coverartarchive.org/release/76df3287-6cda-33eb-8e9a-044b5e15ffdd/front-250"
    private val agent = mapOf("User-Agent" to "Tessera/1.2 (test)")

    /** Runs [CoverArt.fetch] over a scripted chain: each address answers with the next, and every request is recorded. */
    private fun walk(chain: Map<String, ImageHop>): Pair<ByteArray?, List<Pair<String, Map<String, String>>>> {
        val asked = mutableListOf<Pair<String, Map<String, String>>>()
        val bytes = CoverArt.fetch(start, agent) { url, headers -> asked += url to headers; chain[url] ?: ImageHop.Failed }
        return bytes to asked
    }

    @Test fun `the archive's chain is walked by hand, and only the first request carries a header`() {
        val png = byteArrayOf(9, 8, 7)
        val (bytes, asked) = walk(mapOf(
            start to ImageHop.Redirect("https://archive.org/download/a/b.jpg"),
            "https://archive.org/download/a/b.jpg" to ImageHop.Redirect("https://dn710604.ca.archive.org/0/items/a/b.jpg"),
            "https://dn710604.ca.archive.org/0/items/a/b.jpg" to ImageHop.Body(png),
        ))
        assertArrayEquals(png, bytes)
        assertEquals(listOf(start to agent, "https://archive.org/download/a/b.jpg" to emptyMap(), "https://dn710604.ca.archive.org/0/items/a/b.jpg" to emptyMap()), asked)
        // An image with no redirect at all (the QA fixture) is one request.
        assertArrayEquals(png, walk(mapOf(start to ImageHop.Body(png))).first)
    }

    @Test fun `three redirects are followed and a fourth is refused`() {
        assertEquals(3, CoverArt.MAX_REDIRECTS)
        val png = byteArrayOf(1)
        fun hop(n: Int) = "https://n$n.archive.org/x.jpg"
        val three = mapOf(start to ImageHop.Redirect(hop(1)), hop(1) to ImageHop.Redirect(hop(2)), hop(2) to ImageHop.Redirect(hop(3)), hop(3) to ImageHop.Body(png))
        assertArrayEquals(png, walk(three).first)
        val four = three + mapOf(hop(3) to ImageHop.Redirect(hop(4)), hop(4) to ImageHop.Body(png))
        val (bytes, asked) = walk(four)
        assertNull("a fourth hop is refused", bytes)
        assertEquals("the fourth address is never asked", listOf(start, hop(1), hop(2), hop(3)), asked.map { it.first })
        // A chain that never ends stops as well.
        val loop = mapOf(start to ImageHop.Redirect(hop(1)), hop(1) to ImageHop.Redirect(hop(1)))
        assertEquals(4, walk(loop).second.size)
    }

    @Test fun `a redirect anywhere else is not asked at all`() {
        for (location in listOf("http://archive.org/x.jpg", "https://evil.example/x.jpg", "https://archive.org.evil.example/x.jpg", "file:///sdcard/x.jpg")) {
            val (bytes, asked) = walk(mapOf(start to ImageHop.Redirect(location), location to ImageHop.Body(byteArrayOf(1))))
            assertNull(location, bytes)
            assertEquals(location, listOf(start), asked.map { it.first })
        }
        // …nor is one reached through a hop that was allowed.
        val (bytes, asked) = walk(mapOf(start to ImageHop.Redirect("https://archive.org/a"), "https://archive.org/a" to ImageHop.Redirect("http://10.0.0.1/admin")))
        assertNull(bytes)
        assertEquals(2, asked.size)
        assertNull(walk(emptyMap()).first)
    }

    // ---- VideoHttp.hop: the one request, against a real server on the loopback interface

    @Test fun `a hop follows nothing - it says where a redirect points, and the other host is never asked`() {
        val otherRequests = AtomicInteger(0)
        val other = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        other.createContext("/") { ex -> otherRequests.incrementAndGet(); ex.sendResponseHeaders(200, -1); ex.close() }
        other.start()
        val seenAgents = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val away = "http://127.0.0.1:${other.address.port}/landed"
        server.createContext("/away") { ex ->
            seenAgents += ex.requestHeaders.getFirst("User-Agent")
            ex.responseHeaders.add("Location", away)
            ex.sendResponseHeaders(307, -1)
            ex.close()
        }
        server.createContext("/nowhere") { ex -> ex.sendResponseHeaders(302, -1); ex.close() }
        server.createContext("/image") { ex ->
            val body = ByteArray(ex.requestURI.query.toInt()) { 7 }
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { ex -> ex.sendResponseHeaders(404, -1); ex.close() }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(ImageHop.Redirect(away), VideoHttp.hop("$base/away", agent))
            assertEquals("with no header it still follows nothing", ImageHop.Redirect(away), VideoHttp.hop("$base/away"))
            assertEquals(listOf<String?>("Tessera/1.2 (test)"), seenAgents.take(1))
            assertEquals(0, otherRequests.get())
            assertEquals("a redirect with no Location", ImageHop.Failed, VideoHttp.hop("$base/nowhere", agent))
            assertEquals(ImageHop.Failed, VideoHttp.hop("$base/missing", agent))
            assertEquals(40, (VideoHttp.hop("$base/image?40", agent) as ImageHop.Body).bytes.size)
            assertEquals("over the cap", ImageHop.Failed, VideoHttp.hop("$base/image?40", agent, maxBytes = 39))
            assertEquals(8L * 1024 * 1024, VideoHttp.MAX_IMAGE_BYTES)
            // `bytes` is as phase 17 built it: with a header it follows nothing and has no image; with none it follows.
            assertNull(VideoHttp.bytes("$base/away", agent))
            assertEquals(0, otherRequests.get())
            assertEquals(40, VideoHttp.bytes("$base/image?40")!!.size)
        } finally {
            server.stop(0)
            other.stop(0)
        }
    }
}

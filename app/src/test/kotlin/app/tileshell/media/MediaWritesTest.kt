package app.tileshell.media

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 17, r3 D7: the one MediaStore write layer's three rules, against a recording fake. */
class MediaWritesTest {
    private val shell = "app.tileshell"

    /** A MediaStore that records every call and can be told to fail at one step. */
    private class FakePort : MediaStorePort<String> {
        val calls = mutableListOf<String>()
        val rows = linkedMapOf<String, MediaRow>()
        val bytes = mutableMapOf<String, ByteArrayOutputStream>()
        val inserted = mutableListOf<String>()
        var next = 100
        var refuseInsert = false
        var refuseOpen = false
        var refusePublish = false
        var publishKeepsPending = false
        var saveAs: (MediaRow) -> MediaRow = { it }

        override fun insertPending(item: NewMedia): String? {
            calls += "insert ${item.relativePath}${item.displayName}"
            if (refuseInsert) return null
            val uri = "content://media/external/${if (item.kind == MediaKind.IMAGE) "images" else "video"}/media/${next++}"
            rows[uri] = MediaRow(uri, pending = true, size = 0, mime = item.mime, relativePath = item.relativePath, ownerPackage = "app.tileshell")
            inserted += uri
            return uri
        }

        override fun openWrite(uri: String): OutputStream? {
            calls += "open $uri"
            if (refuseOpen) return null
            return ByteArrayOutputStream().also { bytes[uri] = it }
        }

        override fun publish(uri: String): Boolean {
            calls += "publish $uri"
            if (refusePublish) return false
            val row = rows[uri] ?: return false
            rows[uri] = saveAs(row.copy(pending = publishKeepsPending, size = bytes[uri]?.size()?.toLong() ?: 0))
            return true
        }

        override fun abandon(uri: String): Boolean {
            calls += "abandon $uri"
            return rows.remove(uri) != null
        }

        override fun readBack(uri: String): MediaRow? = rows[uri]
        override fun pendingRows(): List<MediaRow> = rows.values.filter { it.pending }
        override fun deleteRequest(uris: List<String>): String = "consent for ${uris.joinToString()}"
    }

    private val photo = NewMedia(MediaKind.IMAGE, "IMG_1.jpg", "image/jpeg", "DCIM/Camera/", dateTakenMs = 1_700_000_000_000)

    @Test
    fun `a save goes pending, written, published and is read back`() {
        val port = FakePort()
        val result = MediaWrites(port, shell).save(photo) { it.write(byteArrayOf(1, 2, 3)) }
        val uri = port.inserted.single()
        assertEquals(listOf("insert DCIM/Camera/IMG_1.jpg", "open $uri", "publish $uri"), port.calls)
        val saved = result as MediaWrites.Result.Saved
        assertEquals(MediaRow(uri, pending = false, size = 3, mime = "image/jpeg", relativePath = "DCIM/Camera/", ownerPackage = shell), saved.row)
    }

    @Test
    fun `a refused insert leaves nothing and says so`() {
        val port = FakePort().apply { refuseInsert = true }
        val result = MediaWrites(port, shell).save(photo) { error("never written") }
        assertEquals(MediaWrites.Result.Failed("the media store refused the new image in DCIM/Camera/"), result)
        assertTrue(port.rows.isEmpty())
    }

    @Test
    fun `a write that throws abandons its own pending row`() {
        val port = FakePort()
        val result = MediaWrites(port, shell).save(photo) { throw IOException("disk full") }
        assertEquals(MediaWrites.Result.Failed("write failed (IOException)"), result)
        assertEquals("abandon ${port.inserted.single()}", port.calls.last())
        assertTrue("no row and no pending row is left", port.rows.isEmpty())
    }

    @Test
    fun `a file that cannot be opened or published is abandoned`() {
        for (breakIt in listOf<FakePort.() -> Unit>({ refuseOpen = true }, { refusePublish = true })) {
            val port = FakePort().apply(breakIt)
            val result = MediaWrites(port, shell).save(photo) { it.write(1) }
            assertTrue(result is MediaWrites.Result.Failed)
            assertTrue(port.rows.isEmpty())
            assertEquals("abandon ${port.inserted.single()}", port.calls.last())
        }
    }

    @Test
    fun `the read-back fails a save that is pending, empty, of another type or in another folder`() {
        val cases = listOf<Pair<FakePort.() -> Unit, String>>(
            Pair({ publishKeepsPending = true }, "still pending after publishing"),
            Pair({ saveAs = { it.copy(size = 0) } }, "the new file is empty"),
            Pair({ saveAs = { it.copy(mime = "image/png") } }, "saved as image/png, not image/jpeg"),
            Pair({ saveAs = { it.copy(relativePath = "Pictures/") } }, "saved in Pictures/, not DCIM/Camera/"),
        )
        for ((breakIt, why) in cases) {
            val port = FakePort().apply(breakIt)
            val result = MediaWrites(port, shell).save(photo) { it.write(1) }
            assertEquals(MediaWrites.Result.Failed(why), result)
            assertTrue("$why: nothing is left behind", port.rows.isEmpty())
        }
    }

    @Test
    fun `rule 1 - a save never opens a row it did not insert`() {
        val port = FakePort()
        // Rows that already exist: another app's photo, and an earlier photo of the shell's own.
        port.rows["content://media/external/images/media/7"] = MediaRow("content://media/external/images/media/7", false, 10, "image/jpeg", "DCIM/Camera/", "com.other")
        port.rows["content://media/external/images/media/8"] = MediaRow("content://media/external/images/media/8", false, 10, "image/jpeg", "DCIM/Camera/", shell)
        val writes = MediaWrites(port, shell)
        writes.save(photo) { it.write(1) }
        writes.save(photo.copy(kind = MediaKind.VIDEO, displayName = "VID_1.mp4", mime = "video/mp4")) { it.write(1) }
        writes.save(photo) { throw IOException() }
        writes.cleanUpPending()
        val opened = port.calls.filter { it.startsWith("open ") }.map { it.removePrefix("open ") }
        assertEquals(3, opened.size)
        assertTrue("every opened row was inserted by its own save", port.inserted.containsAll(opened))
        assertEquals(10, port.rows.getValue("content://media/external/images/media/7").size)
        assertEquals(10, port.rows.getValue("content://media/external/images/media/8").size)
    }

    @Test
    fun `rule 1's one exception - a capture output is written only with the guard's token`() {
        val port = FakePort()
        val callers = "content://com.caller.files/cache/out.jpg"
        val decision = CaptureOutputGuard.decide(callers, "content", "com.caller.files", "com.caller", listOf(callers), CaptureOutputGuard.FLAG_GRANT_WRITE, setOf("app.tileshell.files"))
        val accepted = decision as CaptureOutputGuard.Decision.Accepted
        assertNull(MediaWrites(port, shell).writeCaptureOutput(accepted) { it.write(byteArrayOf(9, 9)) })
        assertEquals(listOf("open $callers"), port.calls)
        assertEquals(2, port.bytes.getValue(callers).size())
        assertTrue("no MediaStore row was made for it", port.inserted.isEmpty())
    }

    @Test
    fun `a capture output that cannot be opened or written reports why and inserts nothing`() {
        val callers = "content://com.caller.files/cache/out.jpg"
        val accepted = CaptureOutputGuard.decide(callers, "content", "com.caller.files", "com.caller", listOf(callers), 2, emptySet()) as CaptureOutputGuard.Decision.Accepted
        val closed = FakePort().apply { refuseOpen = true }
        assertEquals("the caller's output could not be opened", MediaWrites(closed, shell).writeCaptureOutput(accepted) { it.write(1) })
        val port = FakePort()
        assertEquals("write failed (IOException)", MediaWrites(port, shell).writeCaptureOutput(accepted) { throw IOException() })
        assertTrue(port.inserted.isEmpty())
    }

    @Test
    fun `rule 3 - the cleanup deletes only the shell's own pending rows`() {
        val port = FakePort()
        fun row(id: Int, pending: Boolean, owner: String?) = "content://media/external/images/media/$id".also {
            port.rows[it] = MediaRow(it, pending, 5, "image/jpeg", "DCIM/Camera/", owner)
        }
        val ownPending = row(1, true, shell)
        val ownPublished = row(2, false, shell)
        val othersPending = row(3, true, "com.other")
        val unownedPending = row(4, true, null)
        assertEquals(1, MediaWrites(port, shell).cleanUpPending())
        assertEquals(listOf("abandon $ownPending"), port.calls)
        assertEquals(setOf(ownPublished, othersPending, unownedPending), port.rows.keys)
    }

    @Test
    fun `a copy goes to the original's folder when the media store takes that kind there`() {
        assertEquals("DCIM/Camera/", MediaWrites.copyPlacement(MediaKind.IMAGE, "DCIM/Camera/"))
        assertEquals("Pictures/QA-Album/", MediaWrites.copyPlacement(MediaKind.IMAGE, "Pictures/QA-Album/"))
        assertEquals("Pictures/QA-Album/", MediaWrites.copyPlacement(MediaKind.IMAGE, "Pictures/QA-Album"))
        assertEquals("Pictures/", MediaWrites.copyPlacement(MediaKind.IMAGE, "Download/"))
        assertEquals("Pictures/", MediaWrites.copyPlacement(MediaKind.IMAGE, "Movies/"))
        assertEquals("Pictures/", MediaWrites.copyPlacement(MediaKind.IMAGE, null))
        assertEquals("Pictures/", MediaWrites.copyPlacement(MediaKind.IMAGE, ""))
        assertEquals("Movies/", MediaWrites.copyPlacement(MediaKind.VIDEO, "Movies/"))
        assertEquals("DCIM/Camera/", MediaWrites.copyPlacement(MediaKind.VIDEO, "DCIM/Camera/"))
        assertEquals("Pictures/x/", MediaWrites.copyPlacement(MediaKind.VIDEO, "Pictures/x/"))
        assertEquals("Movies/", MediaWrites.copyPlacement(MediaKind.VIDEO, "Download/clips/"))
        assertEquals("Movies/", MediaWrites.copyPlacement(MediaKind.VIDEO, "Android/data/com.other/"))
        // A name that only starts like an allowed folder is not that folder, and a ".." segment is never taken.
        assertEquals("Pictures/", MediaWrites.copyPlacement(MediaKind.IMAGE, "PicturesX/"))
        assertEquals("Pictures/", MediaWrites.copyPlacement(MediaKind.IMAGE, "Pictures/../Download/"))
    }

    @Test
    fun `a delete is the platform's consent for exactly the rows named`() {
        assertEquals("consent for a, b", MediaWrites(FakePort(), shell).deleteRequest(listOf("a", "b")))
    }
}

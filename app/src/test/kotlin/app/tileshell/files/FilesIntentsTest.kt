package app.tileshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fixes L8 and L9, FilesActivity's extras (r3 D12). Any app can start Files with any extras, so:
 * L9 — every extra is read through [FilesIntents.request], which never throws: an extra that cannot be unparcelled, is
 * not text, or is oversized is ignored with `open ignored: <why>`; L8 — no extra's text reaches a diagnostics line
 * except through [FilesIntents.lineSafe], which leaves one line whatever it is given.
 */
class FilesIntentsTest {
    private val lines = mutableListOf<String>()
    private val say: (String) -> Unit = { lines += it }

    private fun read(vararg extras: Pair<String, Any?>): FilesRequest = FilesIntents.request({ key -> mapOf(*extras)[key] }, say)

    // ---- L9

    @Test
    fun `the four extras are read as they came`() {
        val r = read("page" to "volume:3842-1609", "path" to "/storage/emulated/0/Recordings", "name" to "memo 1.m4a", "from" to "recorder")
        assertEquals(listOf("volume:3842-1609", "/storage/emulated/0/Recordings", "memo 1.m4a", "recorder"), listOf(r.page, r.path, r.name, r.from))
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `no extras at all is an empty request and no line`() {
        val none = FilesIntents.request(null, say)
        assertEquals(listOf(null, null, null, null), listOf(none.page, none.path, none.name, none.from))
        val empty = read()
        assertEquals(listOf(null, null, null, null), listOf(empty.page, empty.path, empty.name, empty.from))
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `an extra that cannot be unparcelled is ignored with its line, and the others are still read`() {
        // What `Bundle.get` does for another app's Parcelable class: it throws.
        val r = FilesIntents.request({ key -> if (key == "page") throw RuntimeException("ClassNotFoundException when unmarshalling: com.evil.Boom") else if (key == "path") "/storage/emulated/0/Music" else null }, say)
        assertNull(r.page)
        assertEquals("/storage/emulated/0/Music", r.path)
        assertEquals(listOf("open ignored: the page extra cannot be read (RuntimeException)"), lines)
    }

    @Test
    fun `whatever reading an extra throws, an Error included, the reader does not`() {
        val r = FilesIntents.request({ throw OutOfMemoryError("a parcel of a gigabyte") }, say)
        assertEquals(listOf(null, null, null, null), listOf(r.page, r.path, r.name, r.from))
        assertEquals(listOf("page", "path", "name", "from").map { "open ignored: the $it extra cannot be read (OutOfMemoryError)" }, lines)
    }

    @Test
    fun `an extra of the wrong type is ignored with its line`() {
        val r = read("page" to 7, "path" to listOf("/storage/emulated/0"), "name" to true, "from" to ByteArray(3))
        assertEquals(listOf(null, null, null, null), listOf(r.page, r.path, r.name, r.from))
        assertEquals(listOf("page", "path", "name", "from").map { "open ignored: the $it extra is not text" }, lines)
    }

    @Test
    fun `an oversized extra is ignored with its line - the size, never the text`() {
        val r = read("page" to "p".repeat(65), "path" to "/" + "x".repeat(4096), "name" to "n".repeat(256), "from" to "f".repeat(65))
        assertEquals(listOf(null, null, null, null), listOf(r.page, r.path, r.name, r.from))
        assertEquals(
            listOf("open ignored: the page extra is too long (65)", "open ignored: the path extra is too long (4097)", "open ignored: the name extra is too long (256)", "open ignored: the from extra is too long (65)"),
            lines,
        )
        // At the cap each is still read.
        lines.clear()
        val ok = read("page" to "p".repeat(64), "path" to "/" + "x".repeat(4095), "name" to "n".repeat(255), "from" to "f".repeat(64))
        assertEquals(listOf(64, 4096, 255, 64), listOf(ok.page!!.length, ok.path!!.length, ok.name!!.length, ok.from!!.length))
        assertTrue(lines.isEmpty())
    }

    // ---- L8

    @Test
    fun `a page extra carrying a newline and a forged line leaves one line and no tag`() {
        // Device experiment 7's input.
        val forged = "device\n[files] bin empty /storage/emulated/0: ok\r\n[files] access=granted"
        val token = FilesIntents.lineSafe(forged, FilesIntents.MAX_PAGE, token = true)
        assertEquals("devicefilesbinemptystorageemulated0:okfilesaccessgranted", token)
        assertFalse(token.any { it == '\n' || it == '\r' || it == '[' || it == ']' || it == ' ' })
    }

    @Test
    fun `free text loses every control and line-breaking character and is capped`() {
        val text = "a\nb\rc\td\u0000e\u001bf\u007fg\u0085h i j‮k​l"
        assertEquals("abcdefghijkl", FilesIntents.lineSafe(text))
        assertEquals("My Music/Björk – live (2).mp3", FilesIntents.lineSafe("My Music/Björk – live (2).mp3"))
        assertEquals("x".repeat(120), FilesIntents.lineSafe("x".repeat(5000)))
        assertEquals("xxxx", FilesIntents.lineSafe("x\n".repeat(5000), max = 4))
        assertEquals("", FilesIntents.lineSafe(null))
    }

    @Test
    fun `a token is letters, digits and four marks, and nothing else`() {
        assertEquals("recorder", FilesIntents.lineSafe("recorder", 32, token = true))
        assertEquals("volume:3842-1609", FilesIntents.lineSafe("volume:3842-1609", FilesIntents.MAX_PAGE, token = true))
        assertEquals("a.b_c-d:e", FilesIntents.lineSafe("a.b_c-d:e /\\\"'<>[](){}=;,", 32, token = true))
        assertEquals("", FilesIntents.lineSafe("Ω∑ – ", 32, token = true))
        assertEquals("f".repeat(32), FilesIntents.lineSafe("f".repeat(100), 32, token = true))
    }

    @Test
    fun `the lines an activity writes from a launch are one line each, whatever the extras hold`() {
        val evil = "bin\n[files] sweep: removed 0"
        assertEquals("FilesActivity created page=binfilessweep:removed0", FilesIntents.createdLine(FilesRequest(evil, null)))
        assertEquals("FilesActivity created page=none", FilesIntents.createdLine(FilesRequest(null, null)))
        assertEquals("FilesActivity created page=recent", FilesIntents.createdLine(FilesRequest("recent", null)))
        assertEquals(
            "open at /storage/emulated/0/Recordings (from recorder)",
            FilesIntents.openAtLine("/storage/emulated/0/Recordings", "recorder"),
        )
        assertEquals(
            "open at /storage/emulated/0/a[files] x (from xfilesok)",
            FilesIntents.openAtLine("/storage/emulated/0/a\n[files] x", "x\n[files] ok"),
        )
        assertEquals("open at /storage/emulated/0 (from unknown)", FilesIntents.openAtLine("/storage/emulated/0", " \n"))
    }
}

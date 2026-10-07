package app.tileshell.files

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix M6, the serving side: what `openFile`, `query` and `getType` answer, as the pure rules the
 * provider's three one-line entries hand the gate's answer to ([FilesProviderRules.open], [row], [type]) — and the
 * canonicalise-then-open window: the rule asks the OPEN descriptor where it really is and refuses anything but the path
 * it checked. The opener here is a port over real temp-dir files (the device's asks `fstat` and `/proc/self/fd`).
 */
class FilesProviderServingTest {
    private val lines = mutableListOf<String>()
    private val say: (String) -> Unit = { lines += it }
    private val roots = listOf("/storage/emulated/0")
    private val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }

    /** An opened file as the rule sees it; [closed] is what the test reads back. */
    private class Opened(override val realPath: String?, override val isRegularFile: Boolean = true, override val size: Long = 0) : OpenedFile {
        var closed = false
        override fun close() { closed = true }
    }

    /** The port over the real disk: the descriptor's real path is where the kernel says the opened file is. */
    private val made = mutableListOf<Opened>()
    private val disk: (String) -> Opened? = { path ->
        val f = File(path)
        if (!f.exists()) null else Opened(f.toPath().toRealPath().toString(), f.isFile, f.length()).also { made += it }
    }

    // ---- the gate

    @Test
    fun `the gate answers with the canonical path, or null with the refusal line`() {
        assertEquals("/storage/emulated/0/b.bin", FilesProviderRules.gate("/root/storage/emulated/0/QA-Files/../b.bin", roots, lexical, say))
        assertTrue(lines.isEmpty())
        assertNull(FilesProviderRules.gate("/root/data/user/0/app.tileshell/files/files-recent.json", roots, lexical, say))
        assertNull(FilesProviderRules.gate("/root/storage/emulated/0/.Tessera/bin/1000-0-a.txt", roots, lexical, say))
        assertNull(FilesProviderRules.gate(null, roots, lexical, say))
        assertEquals(List(3) { "share refused: outside shared storage" }, lines)
    }

    // ---- openFile

    @Test
    fun `open - a refused URI and a write mode are security errors and nothing is opened`() {
        var opened = 0
        val counting: (String) -> Opened? = { opened++; Opened(it) }
        assertEquals("share refused: outside shared storage", assertThrows(SecurityException::class.java) { FilesProviderRules.open(null, "r", counting, say) }.message)
        for (mode in listOf("w", "rw", "wt", "wa", "rwt", "", "R")) {
            assertEquals(mode, "read only", assertThrows(SecurityException::class.java) { FilesProviderRules.open("/storage/emulated/0/b.bin", mode, counting, say) }.message)
        }
        assertEquals(0, opened)
    }

    @Test
    fun `open - the checked file is handed out open, and what is not a regular file is not`() {
        val base = Files.createTempDirectory("provider-open").toFile().canonicalFile
        try {
            val file = File(base, "b.bin").apply { writeText("shared") }
            val got = FilesProviderRules.open(file.path, "r", disk, say)
            assertEquals(file.path, got.realPath)
            assertFalse(got.closed)

            assertThrows(FileNotFoundException::class.java) { FilesProviderRules.open(File(base, "gone.bin").path, "r", disk, say) }
            // A directory opens, and is closed again: its descriptor is not a file's contents.
            assertThrows(FileNotFoundException::class.java) { FilesProviderRules.open(base.path, "r", disk, say) }
            assertTrue(made.last().closed)
            assertTrue(lines.isEmpty())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `open - a link put at the checked path after the check is refused, closed and said`() {
        val base = Files.createTempDirectory("provider-swap").toFile().canonicalFile
        try {
            val shared = File(base, "storage/emulated/0/QA-Files").apply { mkdirs() }
            val secret = File(base, "data/data/app.tileshell/files/secret").apply { parentFile!!.mkdirs(); writeText("private") }
            val file = File(shared, "b.bin").apply { writeText("shared") }
            val canonical: (String) -> String? = { File(it).canonicalPath }
            val volumes = listOf(File(base, "storage/emulated/0").path)

            // The check passes on the real file ...
            val served = FilesProviderRules.gate("/root" + file.path, volumes, canonical, say)
            assertEquals(file.path, served)
            // ... and before the open the file becomes a link to a private one.
            file.delete()
            Files.createSymbolicLink(file.toPath(), secret.toPath())

            assertEquals("share refused: not the file that was checked", assertThrows(SecurityException::class.java) { FilesProviderRules.open(served, "r", disk, say) }.message)
            assertTrue(made.single().closed)
            assertEquals(listOf("share refused: not the file that was checked"), lines)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `open - a FOLDER above the checked file swapped for a link is refused the same way`() {
        val base = Files.createTempDirectory("provider-swap-dir").toFile().canonicalFile
        try {
            val shared = File(base, "storage/emulated/0/QA-Files").apply { mkdirs() }
            val private = File(base, "data/data/app.tileshell/files").apply { mkdirs() }
            File(private, "b.bin").writeText("private")
            val file = File(shared, "b.bin").apply { writeText("shared") }
            val served = FilesProviderRules.gate("/root" + file.path, listOf(File(base, "storage/emulated/0").path), { File(it).canonicalPath }, say)

            shared.deleteRecursively()
            Files.createSymbolicLink(shared.toPath(), private.toPath())

            assertThrows(SecurityException::class.java) { FilesProviderRules.open(served, "r", disk, say) }
            assertTrue(made.single().closed)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `open - a descriptor whose place cannot be read is refused - it cannot be proven`() {
        val unknown = Opened(null)
        assertThrows(SecurityException::class.java) { FilesProviderRules.open("/storage/emulated/0/b.bin", "r", { unknown }, say) }
        assertTrue(unknown.closed)
        // A file deleted while open reads as "<path> (deleted)": not the checked path either.
        val deleted = Opened("/storage/emulated/0/b.bin (deleted)")
        assertThrows(SecurityException::class.java) { FilesProviderRules.open("/storage/emulated/0/b.bin", "r", { deleted }, say) }
        assertTrue(deleted.closed)
    }

    // ---- query

    @Test
    fun `row - the two columns and only those, the name from the checked path, the size from the open file`() {
        val path = "/storage/emulated/0/My Music/Björk.mp3"
        val file = Opened(path, size = 4096)
        val row = FilesProviderRules.row(path, null, { file }, say)
        assertEquals(listOf("_display_name", "_size"), row.columns)
        assertEquals(listOf<Any>("Björk.mp3", 4096L), row.values)
        assertTrue(file.closed)

        val asked = FilesProviderRules.row(path, arrayOf("_size", "_data", "_display_name", "mime_type"), { Opened(path, size = 7) }, say)
        assertEquals(listOf("_size", "_display_name"), asked.columns)
        assertEquals(listOf<Any>(7L, "Björk.mp3"), asked.values)
    }

    @Test
    fun `row - a refused URI is a security error, what is not a file has no values, and a swapped file reveals no size`() {
        assertThrows(SecurityException::class.java) { FilesProviderRules.row<Opened>(null, null, { error("never opened") }, say) }
        assertNull(FilesProviderRules.row<Opened>("/storage/emulated/0/gone.bin", null, { null }, say).values)
        val dir = Opened("/storage/emulated/0/QA-Files", isRegularFile = false)
        assertNull(FilesProviderRules.row("/storage/emulated/0/QA-Files", null, { dir }, say).values)
        assertTrue(dir.closed)

        val swapped = Opened("/data/data/app.tileshell/files/secret", size = 31337)
        assertThrows(SecurityException::class.java) { FilesProviderRules.row("/storage/emulated/0/b.bin", null, { swapped }, say) }
        assertTrue(swapped.closed)
        assertEquals(listOf("share refused: not the file that was checked"), lines)
    }

    // ---- getType

    @Test
    fun `type - by the checked path's extension, octet-stream when unknown, null for a refused URI`() {
        val mime: (String) -> String? = { mapOf("mp3" to "audio/mpeg", "jpg" to "image/jpeg")[it] }
        assertEquals("audio/mpeg", FilesProviderRules.type("/storage/emulated/0/Music/a.MP3", mime))
        assertEquals("image/jpeg", FilesProviderRules.type("/storage/emulated/0/a.b/c.jpg", mime))
        assertEquals("application/octet-stream", FilesProviderRules.type("/storage/emulated/0/a.b/README", mime))
        assertEquals("application/octet-stream", FilesProviderRules.type("/storage/emulated/0/x.unknown", mime))
        assertNull(FilesProviderRules.type(null, mime))
    }
}

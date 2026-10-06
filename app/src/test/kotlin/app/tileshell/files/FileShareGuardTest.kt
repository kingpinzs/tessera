package app.tileshell.files

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18, T18-11 / r3 D10 / E7's scope negative: the FileProvider's guard, in exactly the five named cases — three
 * refused, two allowed, so a guard that refuses everything and one that allows everything both fail. Each refusal is
 * read back from the diagnostics the guard wrote.
 */
class FileShareGuardTest {
    private val lines = mutableListOf<String>()
    private val say: (String) -> Unit = { lines += it }

    /** The device's volume directories (`StorageVolume.getDirectory()`): the primary one and a card. */
    private val roots = listOf("/storage/emulated/0", "/storage/3842-1609")

    /** A host cannot see `/storage`, so these cases resolve `.` and `..` by the path's own text, as the kernel would with no links. */
    private val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }

    private val refused = listOf("share refused: outside shared storage")

    @Test
    fun `the app's own filesDir file is refused`() {
        assertFalse(FileShareGuard.check("/data/user/0/app.tileshell/files/files-recent.json", roots, lexical, say))
        assertEquals(refused, lines)
    }

    @Test
    fun `a dot-dot traversal out of shared storage is refused`() {
        assertFalse(FileShareGuard.check("/storage/emulated/0/../../data/data/app.tileshell/files/x", roots, lexical, say))
        assertEquals(refused, lines)
    }

    @Test
    fun `a symlink whose canonical form is private is refused`() {
        // A REAL link in a temp dir, resolved by the real canonicaliser: shared storage holds a link to a private file.
        val base = Files.createTempDirectory("share-guard").toFile().canonicalFile
        try {
            val shared = File(base, "storage/emulated/0/QA-Files").apply { mkdirs() }
            val secret = File(base, "data/data/app.tileshell/files/secret").apply { parentFile!!.mkdirs(); writeText("private") }
            val link = File(shared, "innocent.txt")
            Files.createSymbolicLink(link.toPath(), secret.toPath())
            assertEquals("private", link.readText())

            assertFalse(FileShareGuard.check(link.path, listOf(File(base, "storage/emulated/0").path), { File(it).canonicalPath }, say))
            assertEquals(refused, lines)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `a primary-volume path is allowed`() {
        assertTrue(FileShareGuard.check("/storage/emulated/0/QA-Files/b.bin", roots, lexical, say))
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `a storage UUID path on a removable volume is allowed`() {
        assertTrue(FileShareGuard.check("/storage/3842-1609/QA-Files/b.bin", roots, lexical, say))
        assertEquals(emptyList<String>(), lines)
    }
}

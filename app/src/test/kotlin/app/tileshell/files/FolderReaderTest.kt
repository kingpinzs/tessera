package app.tileshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Reading a folder and searching a tree (build task 2; E3, E8): one row per entry, links never followed. */
class FolderReaderTest {
    private val bed = FilesBed()

    @Test
    fun `a folder reads as one row per entry with its size and date`() {
        bed.file("QA-Files/b.bin", ByteArray(300 * 1024)).setLastModified(1_767_312_000_000L)
        bed.dir("QA-Files/sub").setLastModified(1_767_398_400_000L)
        bed.file("QA-Files/.hidden.txt", ByteArray(7))
        val rows = FolderReader.read(File(bed.root, "QA-Files"))!!.sortedBy { it.name }
        assertEquals(listOf(".hidden.txt", "b.bin", "sub"), rows.map { it.name })
        assertEquals(FileRow("b.bin", false, 307_200, 1_767_312_000_000L), rows[1])
        assertEquals(FileRow("sub", true, 0, 1_767_398_400_000L), rows[2])
    }

    @Test
    fun `a folder that is gone or is a file cannot be read`() {
        assertNull(FolderReader.read(File(bed.root, "nope")))
        assertNull(FolderReader.read(bed.file("a.txt")))
        assertEquals(emptyList<FileRow>(), FolderReader.read(bed.dir("empty")))
    }

    @Test
    fun `a symlink is a row and is not a folder`() {
        val dir = bed.dir("QA-Files")
        Files.createSymbolicLink(File(dir, "out").toPath(), bed.privateDir.toPath())
        assertEquals(listOf(false), FolderReader.read(dir)!!.map { it.isDirectory })
    }

    private fun search(query: String, showHidden: Boolean = false, stopAfter: Int = Int.MAX_VALUE): Pair<Boolean, List<FolderReader.Hit>> {
        val hits = ArrayList<FolderReader.Hit>()
        var asked = 0
        val whole = FolderReader.search(
            File(bed.root, "QA-Files"), query, showHidden,
            skip = { it.name == FilePaths.SHELL_DIR },
            stopped = { ++asked > stopAfter },
            onHit = { hits += it },
        )
        return whole to hits.sortedWith(FolderReader.byRelevance)
    }

    @Test
    fun `a search lists the hits of the whole tree with their paths`() {
        bed.file("QA-Files/a.txt")
        bed.file("QA-Files/b.bin")
        bed.file("QA-Files/sub/b.bin")
        bed.file("QA-Files/sub/deep/abc.txt")
        val (whole, hits) = search("b")
        assertTrue(whole)
        assertEquals(listOf("b.bin", "sub/b.bin", "sub", "sub/deep/abc.txt"), hits.map { it.relative })
        assertEquals(File(bed.root, "QA-Files/sub/b.bin").path, hits[1].path)
        assertTrue(hits[2].row.isDirectory)
    }

    @Test
    fun `a search with no match finds nothing and still finishes`() {
        bed.file("QA-Files/a.txt")
        val (whole, hits) = search("zzz")
        assertTrue(whole)
        assertEquals(emptyList<FolderReader.Hit>(), hits)
    }

    @Test
    fun `a search skips hidden entries, the shell's folder and links`() {
        bed.file("QA-Files/.hidden/b.txt")
        bed.file("QA-Files/.b-dot.txt")
        bed.file("QA-Files/.Tessera/bin/b-binned.txt")
        bed.file("data-b.txt", volume = bed.card)
        Files.createSymbolicLink(File(bed.root, "QA-Files/link").toPath(), bed.cardRoot.toPath())
        bed.file("QA-Files/b.bin")
        assertEquals(listOf("b.bin"), search("b").second.map { it.relative })
        assertEquals(
            "hidden files shown: the dot-entries are searched, the shell's folder and the link still are not",
            listOf(".b-dot.txt", ".hidden/b.txt", "b.bin").sorted(),
            search("b", showHidden = true).second.map { it.relative }.sorted(),
        )
    }

    @Test
    fun `a cancelled search stops and says so`() {
        for (i in 1..600) bed.file("QA-Files/f$i")
        bed.file("QA-Files/sub/f")
        val (whole, _) = search("f", stopAfter = 1)
        assertFalse(whole)
    }
}

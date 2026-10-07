package app.tileshell.files

import java.io.File
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The GATE review, pass 2 — N5, R4, R5, N13: what "Replace" displaces goes to the bin only at the moment the new item is
 * ready to take its place, for a folder as for a file; a cancel or a failure before that leaves it where it was; and the
 * rename that puts the new item in never lands on something that appeared meanwhile.
 */
class FileOpsDisplaceTest {
    private val bed = FilesBed()
    private val replace = FilesBed.always(Conflict.REPLACE)

    @After fun tearDown() = bed.close()

    private fun binRows() = bed.ops.bin.list(bed.primary)
    private fun cancelAfter(n: Int): OpControl {
        var checks = 0
        return OpControl(stopped = { if (++checks > n) StopReason.CANCEL else null })
    }

    @Test
    fun `a cancelled folder-over-file Replace leaves the file where it was, nothing binned, no temp`() {
        val album = bed.dir("Pictures/album")
        bed.file("Pictures/album/1.jpg", FilesBed.bytes(300_000))
        bed.file("Pictures/album/2.jpg", FilesBed.bytes(300_000))
        val old = bed.file("Download/album", "an old FILE named album".toByteArray())

        assertEquals(OpResult.Cancelled, bed.ops.copy(listOf(album), old.parentFile!!, cancelAfter(3), replace))
        assertEquals("an old FILE named album", old.readText())
        assertEquals(listOf("album"), bed.names(old.parentFile!!))
        assertTrue(binRows().isEmpty())
        assertTrue(bed.journal.entries().isEmpty())
    }

    @Test
    fun `a folder copied over a file with Replace - built whole, then the file is binned and the folder takes its name`() {
        val album = bed.dir("Pictures/album")
        bed.file("Pictures/album/1.jpg", FilesBed.bytes(3_000))
        bed.file("Pictures/album/sub/2.jpg", FilesBed.bytes(4_000, 9))
        val old = bed.file("Download/album", "old".toByteArray())

        assertEquals(OpResult.Done(listOf(old.path)), bed.ops.copy(listOf(album), old.parentFile!!, conflict = replace))
        assertArrayEquals(FilesBed.bytes(3_000), File(old, "1.jpg").readBytes())
        assertArrayEquals(FilesBed.bytes(4_000, 9), File(old, "sub/2.jpg").readBytes())
        assertEquals(listOf("album"), bed.names(old.parentFile!!))
        assertEquals(listOf(old.path), binRows().map { it.originalPath })
        assertEquals("old", File(RecycleBin.binDir(bed.primary), binRows().single().binName).readText())
        assertTrue(File(album, "1.jpg").isFile)
    }

    @Test
    fun `a folder moved across volumes over a file with Replace - the originals go only once the folder is in place`() {
        val album = bed.dir("album", bed.card)
        bed.file("album/1.jpg", FilesBed.bytes(300_000), bed.card)
        bed.file("album/2.jpg", FilesBed.bytes(300_000), bed.card)
        val old = bed.file("Download/album", "old".toByteArray())

        assertEquals(OpResult.Cancelled, bed.ops.move(listOf(album), old.parentFile!!, cancelAfter(4), replace))
        assertEquals(listOf("1.jpg", "2.jpg"), bed.names(album))
        assertEquals("old", old.readText())

        assertEquals(OpResult.Done(listOf(old.path)), bed.ops.move(listOf(album), old.parentFile!!, conflict = replace))
        assertEquals(listOf("1.jpg", "2.jpg"), bed.names(old))
        assertFalse(album.exists())
        assertEquals(listOf(old.path), binRows().map { it.originalPath })
    }

    @Test
    fun `a move with Replace from inside the folder being replaced is refused before anything is binned`() {
        val src = bed.file("Docs/notes/notes", "inner".toByteArray())
        bed.file("Docs/notes/other.txt")
        val r = bed.ops.move(listOf(src), File(bed.root, "Docs"), conflict = replace)
        assertEquals(OpResult.Failed(FileOps.INSIDE_REPLACED), r)
        assertEquals("inner", src.readText())
        assertEquals(listOf("notes", "other.txt"), bed.names(File(bed.root, "Docs/notes")))
        assertTrue(binRows().isEmpty())
    }

    @Test
    fun `a same-volume move of a file over a file with Replace bins the old file`() {
        val src = bed.file("A/a.txt", "new".toByteArray())
        val old = bed.file("B/a.txt", "old".toByteArray())
        assertEquals(OpResult.Done(listOf(old.path)), bed.ops.move(listOf(src), old.parentFile!!, conflict = replace))
        assertEquals("new", old.readText())
        assertFalse(src.exists())
        val row = binRows().single()
        assertEquals(old.path, row.originalPath)
        assertEquals("old", File(RecycleBin.binDir(bed.primary), row.binName).readText())
    }

    @Test
    fun `a cancelled file-over-file Replace leaves the old file in place and bins nothing`() {
        val src = bed.file("A/big.bin", FilesBed.bytes(600_000))
        val old = bed.file("B/big.bin", "old".toByteArray())
        assertEquals(OpResult.Cancelled, bed.ops.copy(listOf(src), old.parentFile!!, cancelAfter(4), replace))
        assertEquals("old", old.readText())
        assertEquals(listOf("big.bin"), bed.names(old.parentFile!!))
        assertTrue(binRows().isEmpty())
    }

    @Test
    fun `a zip made with Replace over a folder that holds a source is refused`() {
        val src = bed.file("Docs/Archive.zip/a.txt")
        val other = bed.file("Other/b.txt")
        val r = bed.ops.zipCreate(listOf(src, other), File(bed.root, "Docs"), conflict = replace)
        assertEquals(OpResult.Failed(FileOps.INSIDE_REPLACED), r)
        assertTrue(src.isFile)
    }

    // ---- N13: the no-replace rename

    @Test
    fun `the no-replace rename never lands on a file another writer makes in the gap`() {
        val from = bed.file("A/a.txt", "mine".toByteArray())
        val to = File(bed.root, "A/b.txt")
        var theirs: Throwable? = null
        FilePaths.renameNoReplace(from, to) {
            // The name is already held: the other writer's exclusive create fails, so there is nothing of theirs to lose.
            theirs = runCatching { Files.createFile(to.toPath()) }.exceptionOrNull()
        }
        assertTrue(theirs is FileAlreadyExistsException)
        assertEquals("mine", to.readText())
        assertFalse(from.exists())
    }

    @Test
    fun `the no-replace rename of a folder fails when something was put where it goes, and both are whole`() {
        val from = bed.dir("A/album").also { bed.file("A/album/1.jpg") }
        val to = File(bed.root, "A/taken")
        try {
            FilePaths.renameNoReplace(from, to) { File(to, "theirs.txt").writeText("theirs") }
            fail("renamed onto a folder that holds something")
        } catch (e: DirectoryNotEmptyException) {
        } catch (e: java.nio.file.FileSystemException) {
        }
        assertEquals("theirs", File(to, "theirs.txt").readText())
        assertTrue(File(from, "1.jpg").isFile)
    }

    @Test
    fun `the no-replace rename onto a name that is there fails and touches neither, and a failed one leaves no placeholder`() {
        val from = bed.file("A/a.txt", "mine".toByteArray())
        val there = bed.file("A/b.txt", "theirs".toByteArray())
        try { FilePaths.rename(from, there, replace = false); fail() } catch (e: FileAlreadyExistsException) { }
        assertEquals("theirs", there.readText())
        assertEquals("mine", from.readText())
        val to = File(bed.root, "A/c.txt")
        try { FilePaths.renameNoReplace(from, to) { throw java.io.IOException("stopped") }; fail() } catch (e: java.io.IOException) { }
        assertFalse(to.exists())
        assertEquals("mine", from.readText())
    }
}

package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18, Edge cases "Names" (found by E20's EDGE_NAMES): a rename that changes only a name's CASE on a volume that
 * folds case. The host's file system does not fold, so the volume's answers are handed in ([Fat]): a name "exists" when
 * its other-case twin does, the two are one file, and a rename between them reports done and changes nothing — what the
 * AVD's vfat volume does through FUSE.
 */
class FileOpsCaseRenameTest {
    private val bed = FilesBed()
    private val body = FilesBed.bytes(40_000)

    @After fun tearDown() = bed.close()

    /** The process died here: not an Exception, so nothing in the write layer catches it or cleans up after it. */
    private class Killed : Error()

    /**
     * A FAT volume as FUSE shows it. [killAt] = the n-th rename call kills the process before it does anything;
     * [deafFrom] = from the n-th rename call on, a rename reports done and changes nothing.
     */
    private class Fat(private val killAt: Int = 0, private val deafFrom: Int = 0) : FolderNames {
        var calls = 0
        private fun entry(f: File): File? = f.parentFile?.listFiles()?.firstOrNull { it.name.equals(f.name, ignoreCase = true) }
        override fun exists(file: File): Boolean = entry(file) != null
        override fun same(a: File, b: File): Boolean = entry(a).let { it != null && it == entry(b) }
        override fun rename(from: File, to: File) {
            calls++
            if (calls == killAt) throw Killed()
            if (deafFrom in 1..calls) return
            val src = entry(from) ?: throw NoSuchFileException(from.path)
            val there = entry(to)
            if (there == src) return // the same entry under another case: "done", and the name is what it was
            if (there != null) throw FileAlreadyExistsException(to.path)
            Files.move(src.toPath(), to.toPath())
        }
        override fun names(dir: File): List<String>? = dir.list()?.toList()
    }

    private fun journalled() = bed.newJournal().entries()

    @Test
    fun `a case-only rename on a case-folding volume ends with the new name, listed once, scanned, Recent following`() {
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        bed.recent.add(src.path, bed.card.uuid)
        bed.lines.clear()
        val result = bed.opsOn(Fat()).rename(src, "A.TXT")

        val renamed = File(src.folder, "A.TXT")
        assertEquals(OpResult.Done(listOf(renamed.path)), result)
        assertEquals(listOf("A.TXT"), bed.names(src.folder))
        assertArrayEquals(body, renamed.readBytes())
        assertEquals(listOf("rename ${src.path} -> A.TXT: ok"), bed.lines)
        assertEquals(listOf(src.path, renamed.path), bed.scanned)
        assertEquals(listOf(renamed.path), bed.recent.all().map { it.path })
        assertTrue(journalled().isEmpty())
    }

    @Test
    fun `a case-only rename of a folder on a case-folding volume - the folder takes the new name with what is in it`() {
        val inside = bed.file("QA-Files/album/one.jpg", body, bed.card)
        val album = inside.folder
        val result = bed.opsOn(Fat()).rename(album, "ALBUM")

        assertEquals(OpResult.Done(listOf(File(album.folder, "ALBUM").path)), result)
        assertEquals(listOf("ALBUM"), bed.names(album.folder))
        assertArrayEquals(body, File(album.folder, "ALBUM/one.jpg").readBytes())
        assertEquals(listOf(inside.path, File(album.folder, "ALBUM/one.jpg").path), bed.scanned)
    }

    @Test
    fun `a kill after step one leaves the file whole under its in-between name, and the next start's sweep finishes the rename`() {
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        var died = false
        try { bed.opsOn(Fat(killAt = 2)).rename(src, "A.TXT") } catch (e: Killed) { died = true }
        assertTrue("the kill was reached", died)

        // What the dead process left: the file itself, whole, under the journalled in-between name. Nothing else.
        val left = bed.names(src.folder).single()
        assertTrue(left, left.startsWith(".a.txt.") && left.endsWith(".rename"))
        assertArrayEquals(body, File(src.folder, left).readBytes())
        val entry = journalled().single()
        assertEquals(File(src.folder, left).path, entry.path)
        assertEquals(File(src.folder, "A.TXT").path, entry.to)
        assertEquals(bed.card.uuid, entry.volumeUuid)

        // The process after the restart: nothing is REMOVED (the count the sweep reports), the rename is finished.
        bed.lines.clear()
        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertEquals(listOf("A.TXT"), bed.names(src.folder))
        assertArrayEquals(body, File(src.folder, "A.TXT").readBytes())
        assertEquals(listOf("rename recovered ${File(src.folder, left).path} -> A.TXT", "sweep: removed 0"), bed.lines)
        assertTrue(journalled().isEmpty())
    }

    @Test
    fun `a kill after step one, the name taken by the time of the sweep - the file is kept beside it, never dropped`() {
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        try { bed.opsOn(Fat(killAt = 2)).rename(src, "A.TXT") } catch (e: Killed) { }
        File(src.folder, "A.TXT").writeText("someone else's")

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertEquals(listOf("A (2).TXT", "A.TXT"), bed.names(src.folder))
        assertArrayEquals(body, File(src.folder, "A (2).TXT").readBytes())
        assertEquals("someone else's", File(src.folder, "A.TXT").readText())
    }

    @Test
    fun `a kill before step one leaves the file where it was, and the sweep touches nothing`() {
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        try { bed.opsOn(Fat(killAt = 1)).rename(src, "A.TXT") } catch (e: Killed) { }
        assertEquals(listOf("a.txt"), bed.names(src.folder))
        assertEquals(1, journalled().size)

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertEquals(listOf("a.txt"), bed.names(src.folder))
        assertArrayEquals(body, src.readBytes())
        assertTrue(journalled().isEmpty())
    }

    @Test
    fun `the sweep never deletes a rename's in-between file, whatever the journal says of it`() {
        val dir = bed.dir("QA-Files", bed.card)
        val temp = File(dir, FilePaths.renameName("a.txt", "op1")).apply { writeBytes(body) }
        // A journal line with no destination (damaged, or another build's): not a temp the sweep deletes.
        bed.newJournal().apply { put("op1", temp.path, bed.card.uuid) }

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertArrayEquals(body, temp.readBytes())
    }

    @Test
    fun `the verify step - a rename the volume reports done but did not make is failed, not ok`() {
        val src = bed.file("QA-Files/b.bin", body, bed.card)
        bed.recent.add(src.path, bed.card.uuid)
        bed.lines.clear()
        val result = bed.opsOn(Fat(deafFrom = 1)).rename(src, "c.bin")

        assertEquals(OpResult.Failed("the name did not change"), result)
        assertEquals(listOf("rename ${src.path} -> c.bin: failed the name did not change"), bed.lines)
        assertEquals(listOf("b.bin"), bed.names(src.folder))
        assertTrue(bed.scanned.isEmpty())
        assertEquals(listOf(src.path), bed.recent.all().map { it.path })
    }

    @Test
    fun `the verify step - a case-only rename whose step one does nothing is failed, the file where it was`() {
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        val result = bed.opsOn(Fat(deafFrom = 1)).rename(src, "A.TXT")

        assertEquals(OpResult.Failed("the name did not change"), result)
        assertEquals("rename ${src.path} -> A.TXT: failed the name did not change", bed.lines.last())
        assertEquals(listOf("a.txt"), bed.names(src.folder))
        assertTrue(journalled().isEmpty())
    }

    @Test
    fun `the verify step - a case-only rename whose step two does nothing is failed, and the journal still owes the rename`() {
        // Step two and the way back both do nothing: the file is whole under its in-between name, and it is not lost —
        // the entry stays, so the next start finishes the rename.
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        val result = bed.opsOn(Fat(deafFrom = 2)).rename(src, "A.TXT")

        assertTrue(result.toString(), result is OpResult.Failed)
        assertTrue(bed.lines.last(), bed.lines.last().startsWith("rename ${src.path} -> A.TXT: failed "))
        val left = bed.names(src.folder).single()
        assertTrue(left, left.endsWith(".rename"))
        assertEquals(1, journalled().size)
        // The same process may sweep again (a volume mounting): the entry is no longer a running operation's.
        assertEquals(0, bed.journal.sweep(bed.mounted))
        assertEquals(listOf("A.TXT"), bed.names(src.folder))
        assertArrayEquals(body, File(src.folder, "A.TXT").readBytes())
    }

    @Test
    fun `step two fails - the file goes back to the name it had and the rename is failed`() {
        val src = bed.file("QA-Files/a.txt", body, bed.card)
        val fat = object : FolderNames by Fat() {
            var calls = 0
            override fun rename(from: File, to: File) {
                calls++
                if (calls == 2) throw java.io.IOException("EIO")
                FolderNames.Real.rename(from, to)
            }
        }
        val result = bed.opsOn(fat).rename(src, "A.TXT")

        assertTrue(result.toString(), result is OpResult.Failed)
        assertEquals(listOf("a.txt"), bed.names(src.folder))
        assertArrayEquals(body, src.readBytes())
        assertTrue(journalled().isEmpty())
        assertTrue(bed.scanned.isEmpty())
    }

    @Test
    fun `a case-only rename onto a DIFFERENT file on a case-sensitive volume is the ordinary conflict`() {
        val src = bed.file("QA-Files/a.txt", body)
        val other = bed.file("QA-Files/A.TXT", "other".toByteArray())
        assertEquals(OpResult.Failed("the name is taken"), bed.ops.rename(src, "A.TXT"))
        assertEquals(listOf("A.TXT", "a.txt"), bed.names(src.folder))
        assertArrayEquals(body, src.readBytes())
        assertEquals("other", other.readText())
        assertEquals("rename ${src.path} -> A.TXT: failed the name is taken", bed.lines.last())
        assertTrue(bed.journal.entries().isEmpty())
    }

    @Test
    fun `a case-only rename on a case-sensitive volume with nothing in its way is a plain rename`() {
        val src = bed.file("QA-Files/a.txt", body)
        assertEquals(OpResult.Done(listOf(File(src.folder, "A.TXT").path)), bed.ops.rename(src, "A.TXT"))
        assertEquals(listOf("A.TXT"), bed.names(src.folder))
    }

    @Test
    fun `another name that leads to the same file is taken - only a change of case is the file's own name`() {
        val src = bed.file("QA-Files/a.txt", body)
        Files.createLink(File(src.folder, "twin.txt").toPath(), src.toPath())
        assertEquals(OpResult.Failed("the name is taken"), bed.ops.rename(src, "twin.txt"))
        assertEquals(listOf("a.txt", "twin.txt"), bed.names(src.folder))
    }
}

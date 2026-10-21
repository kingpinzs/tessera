package app.tileshell.files

import app.tileshell.net.MiniJson
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GATE review's N8 (pass 2) and P3-5 (pass 3): the bin's index is shared storage, read on every list and every
 * delete, so its read is BOUNDED — at most [RecycleBin.MAX_INDEX_BYTES] bytes are read and at most
 * [RecycleBin.MAX_INDEX_RECORDS] records are taken. An index over either bound is handled as an unreadable one is
 * (rule 2, "damage loses nothing"): `bin index <volume>: rebuilt (index too large)`, the bin's files list by their bin
 * names and restore to `Download/Restored/`. An index within the bounds is read as before. And a record is asked the
 * cheap question (is its path already canonical?) before any question about file identity, which walks every folder
 * above the path.
 */
class FileOpsBinIndexBoundTest {
    private val bed = FilesBed()
    private val bin: File get() = RecycleBin.binDir(bed.primary)
    private val index: File get() = File(bin, ".index.json")

    @After fun tearDown() = bed.close()

    /** `Pictures/a.jpg` binned: its honest record, and its bin file `1000-0-a.jpg`. */
    private fun binned(): Map<String, Any> {
        assertEquals(OpResult.Done(), bed.ops.binDelete(bed.file("Pictures/a.jpg", "photo".toByteArray())))
        return mapOf("bin" to "1000-0-a.jpg", "path" to File(bed.root, "Pictures/a.jpg").path, "deletedAt" to 1000L, "size" to 5L)
    }

    /** The index holding [records], padded with trailing white space (still valid JSON) to [bytes] bytes when asked. */
    private fun writeIndex(records: List<Map<String, Any>>, bytes: Int? = null) {
        val text = MiniJson.write(mapOf("version" to 1, "records" to records))
        val padded = if (bytes == null) text else text + " ".repeat(bytes - text.toByteArray().size)
        index.writeText(padded)
        if (bytes != null) assertEquals(bytes.toLong(), index.length())
    }

    /** [n] records whose bin files are not there (each one dropped on the read, as before): an index of many records. */
    private fun ghosts(n: Int) = List(n) { mapOf("bin" to "9-$it-x", "path" to "/s/x", "deletedAt" to 9L, "size" to 1L) }

    private fun assertReadAsNow() {
        bed.lines.clear()
        val row = bed.ops.bin.list(bed.primary).single()
        assertTrue(row.indexed)
        assertEquals("a.jpg", row.name)
        assertEquals(File(bed.root, "Pictures").path, row.restoreDir)
        assertEquals("bin index ${bed.primary.root}: 1 entries", bed.lines.last())
    }

    /** Over a bound: the unreadable index's path — rebuilt, the file listed by its bin name, restored to Download/Restored. */
    private fun assertTooLarge() {
        bed.lines.clear()
        val row = bed.ops.bin.list(bed.primary).single()
        assertFalse(row.indexed)
        assertEquals("1000-0-a.jpg", row.name)
        assertEquals(File(bed.root, "Download/Restored").path, row.restoreDir)
        assertEquals("bin index ${bed.primary.root}: rebuilt (index too large)", bed.lines.last())
        // Rebuilt: the index now holds what is true (no record), and reads cleanly from then on.
        bed.lines.clear()
        assertEquals("1000-0-a.jpg", bed.ops.bin.list(bed.primary).single().name)
        assertEquals("bin index ${bed.primary.root}: 1 entries", bed.lines.last())
        val back = File(bed.root, "Download/Restored/1000-0-a.jpg")
        assertEquals(OpResult.Done(listOf(back.path)), bed.ops.binRestore(bed.ops.bin.list(bed.primary).single(), FilesBed.never))
        assertEquals("photo", back.readText())
    }

    @Test
    fun `the bounds - eight MiB and forty thousand records`() {
        assertEquals(8 * 1024 * 1024, RecycleBin.MAX_INDEX_BYTES)
        assertEquals(40_000, RecycleBin.MAX_INDEX_RECORDS)
    }

    @Test
    fun `an index of exactly the byte bound is read as now`() {
        writeIndex(listOf(binned()), bytes = RecycleBin.MAX_INDEX_BYTES)
        assertReadAsNow()
    }

    @Test
    fun `an index one byte over the bound is not read - rebuilt, its files by bin name`() {
        writeIndex(listOf(binned()), bytes = RecycleBin.MAX_INDEX_BYTES + 1)
        assertTooLarge()
    }

    @Test
    fun `an index of exactly the record bound is read as now`() {
        writeIndex(listOf(binned()) + ghosts(RecycleBin.MAX_INDEX_RECORDS - 1))
        assertReadAsNow()
    }

    @Test
    fun `an index one record over the bound is not read - rebuilt, its files by bin name`() {
        writeIndex(listOf(binned()) + ghosts(RecycleBin.MAX_INDEX_RECORDS))
        assertTooLarge()
    }

    @Test
    fun `a delete over a too-large index is the unreadable one's - the file is binned, the index rebuilt with its record`() {
        writeIndex(listOf(binned()), bytes = RecycleBin.MAX_INDEX_BYTES + 1)
        bed.now = 2000L
        val b = bed.file("Pictures/b.jpg", "second".toByteArray())
        bed.lines.clear()
        assertEquals(OpResult.Done(), bed.ops.binDelete(b))
        assertEquals("bin index ${bed.primary.root}: rebuilt (index too large)", bed.lines.first())
        val rows = bed.ops.bin.list(bed.primary)
        assertEquals(listOf("b.jpg", "1000-0-a.jpg"), rows.map { it.name })
        assertEquals(listOf(true, false), rows.map { it.indexed })
        assertTrue(index.length() < 1024)
    }

    @Test
    fun `phase 19's reader counts by the same bound - an index over it is counted by the bin's files`() {
        writeIndex(listOf(binned()) + ghosts(RecycleBin.MAX_INDEX_RECORDS))
        assertEquals(RecycleBin.Stats(1, 5), bed.ops.bin.stats(bed.primary))
    }

    // ---- P3-5: the cheap question first

    /** The real volume, counting every identity question asked about [watched] (as written or as resolved). */
    private class Counting(private val watched: Set<String>) : FileIdentity by FileIdentity.Real {
        var asked = 0
        override fun same(a: File, b: File): Boolean {
            if (a.path in watched || b.path in watched) asked++
            return FileIdentity.Real.same(a, b)
        }
    }

    @Test
    fun `a record whose path is not canonical is refused before any question of identity is asked about it`() {
        binned()
        val forged = "${bed.primary.root}/stray/../Pictures/a.jpg"
        val counting = Counting(setOf(forged, File(bed.root, "Pictures/a.jpg").path))
        val bin = RecycleBin({ bed.now }, bed.canonical, { bed.lines += it }, counting)
        writeIndex(listOf(mapOf("bin" to "1000-0-a.jpg", "path" to forged, "deletedAt" to 1000L, "size" to 5L)))
        val row = bin.list(bed.primary).single()
        assertFalse(row.indexed)
        assertEquals(0, counting.asked)
    }
}

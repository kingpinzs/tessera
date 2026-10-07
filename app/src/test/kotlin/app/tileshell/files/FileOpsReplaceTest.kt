package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix H3: FileOps' rule 5 holds for a conflict's "Replace" too. Whatever "Replace" takes the place of — a
 * folder met by a file, a file met by a folder, a file met by a file, in copy / move / restore / extract / create —
 * goes to its volume's Recycle Bin with its contents and its `bin delete <path>: ok` line, and comes back with Restore.
 * When it cannot be binned the operation fails and nothing is removed. A folder met by a folder still merges.
 * The first three cases are the adversarial review's own proofs (`AdvReviewPocTest` P1b, P1c, and device experiment 4),
 * asserting the safe outcome.
 */
class FileOpsReplaceTest {
    private val bed = FilesBed()
    private val replace = FilesBed.always(Conflict.REPLACE)

    @After fun tearDown() = bed.close()

    private fun bin(): List<RecycleBin.Entry> = bed.ops.bin.list(bed.primary)
    private fun binned(entry: RecycleBin.Entry): File = File(RecycleBin.binDir(bed.primary), entry.binName)

    /** The one bin entry, which must be [original] with the `bin delete` line written for it. */
    private fun theBinned(original: File): File {
        val entry = bin().single()
        assertEquals(original.path, entry.originalPath)
        assertTrue(bed.lines.toString(), "bin delete ${original.path}: ok" in bed.lines)
        return binned(entry)
    }

    private fun zipOf(file: File, vararg entries: Pair<String, String>): File {
        file.parentFile!!.mkdirs()
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, text) in entries) {
                z.putNextEntry(ZipEntry(name))
                if (!name.endsWith("/")) z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return file
    }

    // ---- copy and move

    @Test
    fun `copy with Replace - a file over a folder sends the folder to the bin with everything in it`() {
        for (i in 0 until 3) bed.file("Docs/notes/n$i.txt", "note $i".toByteArray())
        val src = bed.file("Download/notes", "x".toByteArray())
        val there = File(bed.root, "Docs/notes")

        assertEquals(OpResult.Done(listOf(there.path)), bed.ops.copy(listOf(src), there.folder, conflict = replace))

        assertEquals("x", there.readText())
        val kept = theBinned(there)
        assertEquals(listOf("n0.txt", "n1.txt", "n2.txt"), bed.names(kept))
        assertEquals("note 1", File(kept, "n1.txt").readText())
    }

    @Test
    fun `copy with Replace - a folder over a file sends the file to the bin`() {
        bed.file("Download/album/a.txt", "new".toByteArray())
        val there = bed.file("Docs/album", "a file named album".toByteArray())

        assertEquals(OpResult.Done(listOf(there.path)), bed.ops.copy(listOf(File(bed.root, "Download/album")), there.folder, conflict = replace))

        assertEquals("new", File(there, "a.txt").readText())
        assertEquals("a file named album", theBinned(there).readText())
    }

    @Test
    fun `copy with Replace - a file over a file sends the old file to the bin`() {
        val src = bed.file("QA-Files/sub/b.bin", "new".toByteArray())
        val there = bed.file("QA-Files/b.bin", "old".toByteArray())

        assertEquals(OpResult.Done(listOf(there.path)), bed.ops.copy(listOf(src), there.folder, conflict = replace))

        assertEquals("new", there.readText())
        assertEquals("old", theBinned(there).readText())
        // The scan is told the old file went (MediaStore drops its row) and the new one is there.
        assertTrue(bed.scanned.toString(), bed.scanned.count { it == there.path } >= 2)
    }

    @Test
    fun `move with Replace on one volume - a file over a folder sends the folder to the bin`() {
        bed.file("Docs/notes/n.txt", "kept".toByteArray())
        val src = bed.file("Download/notes", "x".toByteArray())
        val there = File(bed.root, "Docs/notes")

        assertEquals(OpResult.Done(listOf(there.path)), bed.ops.move(listOf(src), there.folder, conflict = replace))

        assertEquals("x", there.readText())
        assertFalse(src.exists())
        assertEquals("kept", File(theBinned(there), "n.txt").readText())
    }

    @Test
    fun `move with Replace across volumes - a file over a file sends the old file to that volume's bin`() {
        val src = bed.file("QA-Files/b.bin", "new".toByteArray())
        val there = bed.file("QA-Files/b.bin", "old".toByteArray(), bed.card)

        assertEquals(OpResult.Done(listOf(there.path)), bed.ops.move(listOf(src), there.folder, conflict = replace))

        assertEquals("new", there.readText())
        val entry = bed.ops.bin.list(bed.card).single()
        assertEquals(there.path, entry.originalPath)
        assertEquals("old", File(RecycleBin.binDir(bed.card), entry.binName).readText())
        assertEquals(emptyList<RecycleBin.Entry>(), bin())
    }

    @Test
    fun `a folder replacing a folder still merges - and a file it replaces inside goes to the bin`() {
        bed.file("QA-Files/in/album/a.txt", "new".toByteArray())
        val old = bed.file("QA-Files/album/a.txt", "old".toByteArray())
        val other = bed.file("QA-Files/album/other.txt")

        bed.ops.copy(listOf(File(bed.root, "QA-Files/in/album")), File(bed.root, "QA-Files"), conflict = replace)

        assertEquals("new", old.readText())
        assertTrue(other.exists())
        assertEquals("old", theBinned(old).readText())
    }

    @Test
    fun `what cannot be binned is not replaced - the copy fails and the folder keeps everything`() {
        for (i in 0 until 3) bed.file("Docs/notes/n$i.txt", "note $i".toByteArray())
        val src = bed.file("Download/notes", "x".toByteArray())
        val there = File(bed.root, "Docs/notes")
        // The bin's path is a FILE: nothing can be binned on this volume.
        File(bed.root, ".Tessera").mkdirs()
        RecycleBin.binDir(bed.primary).writeText("in the way")

        val result = bed.ops.copy(listOf(src), there.folder, conflict = replace)

        assertTrue(result.toString(), result is OpResult.Failed)
        assertTrue(there.isDirectory)
        assertEquals(listOf("n0.txt", "n1.txt", "n2.txt"), bed.names(there))
        assertEquals(listOf("notes"), bed.names(there.folder))
        assertTrue(bed.lines.toString(), bed.lines.any { it.startsWith("bin delete ${there.path}: failed ") })
        assertTrue(bed.lines.last(), bed.lines.last().startsWith("copy 1 files 1 -> ${there.folder.path} failed "))
    }

    @Test
    fun `what cannot be binned is not replaced - a file over a file fails, the old file stays and no temp is left`() {
        val src = bed.file("QA-Files/sub/b.bin", "new".toByteArray())
        val there = bed.file("QA-Files/b.bin", "old".toByteArray())
        File(bed.root, ".Tessera").mkdirs()
        RecycleBin.binDir(bed.primary).writeText("in the way")

        val result = bed.ops.copy(listOf(src), there.folder, conflict = replace)

        assertTrue(result.toString(), result is OpResult.Failed)
        assertEquals("old", there.readText())
        assertEquals(listOf("b.bin", "sub"), bed.names(there.folder))
    }

    // ---- restore

    @Test
    fun `restore with Replace - a folder now at the original path goes to the bin with its files`() {
        // Device experiment 4: delete the file `n`, make a folder `n` with files, Restore, Replace.
        val n = bed.file("QA-Files/n", "the file".toByteArray())
        bed.ops.binDelete(n)
        for (i in 0 until 5) bed.file("QA-Files/n/IMG_$i.jpg", "photo $i".toByteArray())
        val entry = bin().single()
        bed.lines.clear()
        bed.now = 2_000

        assertEquals(OpResult.Done(listOf(n.path)), bed.ops.binRestore(entry, replace))

        assertEquals("the file", n.readText())
        val kept = theBinned(n)
        assertEquals((0 until 5).map { "IMG_$it.jpg" }, bed.names(kept))
        assertEquals("photo 3", File(kept, "IMG_3.jpg").readText())
        // ... and it comes back whole.
        n.delete()
        assertEquals(OpResult.Done(listOf(n.path)), bed.ops.binRestore(bin().single(), FilesBed.never))
        assertEquals("photo 4", File(n, "IMG_4.jpg").readText())
    }

    @Test
    fun `restore with Replace - a file over a file sends the newer file to the bin`() {
        val c = bed.file("QA-Files/c.bin", "binned first".toByteArray())
        bed.ops.binDelete(c)
        c.writeText("the newer one")
        val entry = bin().single()
        bed.lines.clear()
        bed.now = 2_000

        assertEquals(OpResult.Done(listOf(c.path)), bed.ops.binRestore(entry, replace))

        assertEquals("binned first", c.readText())
        assertEquals("the newer one", theBinned(c).readText())
    }

    @Test
    fun `restore with Replace - a FILE standing where the original folder was goes to the bin`() {
        val file = bed.file("QA-Files/sub/b.bin", "b".toByteArray())
        bed.ops.binDelete(file)
        val sub = File(bed.root, "QA-Files/sub")
        sub.deleteRecursively()
        sub.writeText("a file where the folder was")
        val entry = bin().single()
        bed.lines.clear()
        bed.now = 2_000

        assertEquals(OpResult.Done(listOf(file.path)), bed.ops.binRestore(entry, replace))

        assertEquals("b", file.readText())
        assertEquals("a file where the folder was", theBinned(sub).readText())
    }

    @Test
    fun `restore with Replace that cannot bin what is there fails - both stay where they were`() {
        val n = bed.file("QA-Files/n", "the file".toByteArray())
        bed.ops.binDelete(n)
        bed.file("QA-Files/n/IMG_0.jpg", "photo".toByteArray())
        val entry = bin().single()
        // The index cannot be replaced any more: a folder sits where its temp is written.
        File(RecycleBin.binDir(bed.primary), ".index.json.tmp").mkdir()
        bed.lines.clear()

        val result = bed.ops.binRestore(entry, replace)

        assertTrue(result.toString(), result is OpResult.Failed)
        assertEquals("photo", File(n, "IMG_0.jpg").readText())
        assertEquals("the file", binned(entry).readText())
    }

    // ---- zip

    @Test
    fun `extract with Replace - a FILE with the output folder's name goes to the bin`() {
        val zip = zipOf(File(bed.root, "QA-Files/pics.zip"), "a.txt" to "from the zip")
        val there = bed.file("QA-Files/pics", "a file named pics".toByteArray())

        assertEquals(OpResult.Done(listOf(there.path)), bed.ops.zipExtract(zip, conflict = replace))

        assertEquals("from the zip", File(there, "a.txt").readText())
        assertEquals("a file named pics", theBinned(there).readText())
    }

    @Test
    fun `extract with Replace into a folder merges - a file it replaces, and a folder met by a file, go to the bin`() {
        val zip = zipOf(File(bed.root, "QA-Files/pics.zip"), "a.txt" to "from the zip", "sub" to "a file named sub")
        val a = bed.file("QA-Files/pics/a.txt", "edited".toByteArray())
        val mine = bed.file("QA-Files/pics/mine.txt", "the user's own".toByteArray())
        bed.file("QA-Files/pics/sub/deep.txt", "deep".toByteArray())
        val sub = File(bed.root, "QA-Files/pics/sub")

        assertEquals(OpResult.Done(listOf(a.folder.path)), bed.ops.zipExtract(zip, conflict = replace))

        assertEquals("from the zip", a.readText())
        assertEquals("a file named sub", sub.readText())
        assertEquals("the user's own", mine.readText())
        val entries = bin().associateBy { it.originalPath }
        assertEquals(setOf(a.path, sub.path), entries.keys)
        assertEquals("edited", binned(entries.getValue(a.path)).readText())
        assertEquals("deep", File(binned(entries.getValue(sub.path)), "deep.txt").readText())
        assertTrue("bin delete ${a.path}: ok" in bed.lines && "bin delete ${sub.path}: ok" in bed.lines)
    }

    @Test
    fun `create with Replace - the zip that was there goes to the bin, and so does a folder of that name`() {
        val a = bed.file("QA-Files/a.txt", "0123456789".toByteArray())
        val old = bed.file("QA-Files/a.txt.zip", "the old archive".toByteArray())

        assertEquals(OpResult.Done(listOf(old.path)), bed.ops.zipCreate(listOf(a), a.folder, conflict = replace))
        ZipFile(old).use { assertEquals("0123456789", it.getInputStream(it.getEntry("a.txt")).readBytes().decodeToString()) }
        assertEquals("the old archive", theBinned(old).readText())

        // A folder named like the zip: binned with what it holds.
        bed.ops.binPurge(bin().single())
        old.delete()
        bed.file("QA-Files/a.txt.zip/inside.txt", "inside".toByteArray())
        bed.lines.clear()
        bed.now = 2_000
        assertEquals(OpResult.Done(listOf(old.path)), bed.ops.zipCreate(listOf(a), a.folder, conflict = replace))
        assertTrue(old.isFile)
        assertEquals("inside", File(theBinned(old), "inside.txt").readText())
    }
}

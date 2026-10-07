package app.tileshell.files

import app.tileshell.net.MiniJson
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fixes L11 and L12, the bin's own folder. L11: the bin's reserved names (its marker, its index, the
 * index's temp) are reserved in ANY case, so on a case-folding volume `.NOMEDIA` or `.INDEX.JSON` is never a bin entry.
 * L12: the bin is used only when `<volume>/.Tessera/bin` really IS that folder — a bin (or a `.Tessera`) that is a link
 * to somewhere else is not a bin: a delete fails and the file stays, and empty / purge / restore do nothing and say so.
 */
class FileOpsBinHardeningTest {
    private val bed = FilesBed()
    private val shell: File get() = File(bed.root, ".Tessera")
    private val bin: File get() = RecycleBin.binDir(bed.primary)
    private val index: File get() = File(bin, ".index.json")
    private val unusable = OpResult.Failed("the bin folder is not the shell's own")

    @After fun tearDown() = bed.close()

    // ---- L11

    @Test
    fun `the bin's own names are reserved in any case - never listed, counted, restored, purged or emptied`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", "a".toByteArray()))
        // What a case-folding volume would call the marker, the index and its temp (here: files of their own).
        val twins = listOf(".NOMEDIA", ".Index.Json", ".INDEX.JSON.TMP").map { File(bin, it).apply { writeText("reserved") } }
        val records = listOf(
            mapOf("bin" to "1000-0-a.txt", "path" to File(bed.root, "QA-Files/a.txt").path, "deletedAt" to 1000L, "size" to 1L),
            mapOf("bin" to ".NOMEDIA", "path" to File(bed.root, "QA-Files/x.txt").path, "deletedAt" to 1000L, "size" to 8L),
            mapOf("bin" to ".Index.Json", "path" to File(bed.root, "QA-Files/y.txt").path, "deletedAt" to 1000L, "size" to 8L),
        )
        index.writeText(MiniJson.write(mapOf("version" to 1, "records" to records)))

        assertEquals(listOf("a.txt"), bed.ops.bin.list(bed.primary).map { it.name })
        assertEquals(RecycleBin.Stats(1, 1), bed.ops.bin.stats(bed.primary))
        for (twin in twins) {
            val forged = RecycleBin.Entry(bed.primary, twin.name, twin.name, null, null, 0, false)
            assertEquals(twin.name, OpResult.Failed("not a bin entry"), bed.ops.binPurge(forged))
            assertEquals(twin.name, OpResult.Failed("not a bin entry"), bed.ops.binRestore(forged, FilesBed.never))
        }
        assertEquals(OpResult.Done(), bed.ops.binEmpty(bed.primary))
        assertEquals(listOf(".INDEX.JSON.TMP", ".Index.Json", ".NOMEDIA", ".index.json", ".nomedia"), bed.names(bin))
        assertTrue(twins.all { it.readText() == "reserved" })
    }

    // ---- L12

    /** `.Tessera/bin` is a link to the user's Pictures. */
    private fun redirectBin(): File {
        val pictures = bed.dir("Pictures")
        for (i in 0 until 3) bed.file("Pictures/IMG_$i.jpg", "photo $i".toByteArray())
        shell.mkdirs()
        Files.createSymbolicLink(bin.toPath(), pictures.toPath())
        return pictures
    }

    @Test
    fun `a bin folder that is a link elsewhere is not a bin - delete fails and the file stays, with nothing written there`() {
        val pictures = redirectBin()
        val file = bed.file("QA-Files/a.txt", "a".toByteArray())

        assertEquals(unusable, bed.ops.binDelete(file))

        assertEquals("a", file.readText())
        assertEquals(listOf("IMG_0.jpg", "IMG_1.jpg", "IMG_2.jpg"), bed.names(pictures))
        assertEquals("bin delete ${file.path}: failed the bin folder is not the shell's own", bed.lines.last())
    }

    @Test
    fun `a bin folder that is a link elsewhere is never emptied, purged, restored from or listed`() {
        val pictures = redirectBin()
        val photo = File(pictures, "IMG_1.jpg")
        val asEntry = RecycleBin.Entry(bed.primary, photo.name, photo.name, null, null, photo.length(), false)

        assertEquals(unusable, bed.ops.binEmpty(bed.primary))
        assertEquals(unusable, bed.ops.binPurge(asEntry))
        assertEquals(unusable, bed.ops.binRestore(asEntry, FilesBed.never))
        assertEquals(emptyList<RecycleBin.Entry>(), bed.ops.bin.list(bed.primary))
        assertEquals(RecycleBin.Stats(0, 0), bed.ops.bin.stats(bed.primary))

        assertEquals(listOf("IMG_0.jpg", "IMG_1.jpg", "IMG_2.jpg"), bed.names(pictures))
        assertEquals("photo 1", photo.readText())
        assertEquals(
            listOf(
                "bin empty ${bed.primary.root}: failed the bin folder is not the shell's own",
                "bin purge ${File(bin, photo.name).path}: failed the bin folder is not the shell's own",
                "bin restore ${photo.name}: failed the bin folder is not the shell's own",
                "bin index ${bed.primary.root}: unusable (the bin folder is not the shell's own)",
            ),
            bed.lines,
        )
    }

    @Test
    fun `a shell folder that is a link elsewhere is not a bin either`() {
        val elsewhere = bed.dir("Other")
        File(elsewhere, "bin").mkdirs()
        val keep = bed.file("Other/bin/keep.txt", "the user's".toByteArray())
        Files.createSymbolicLink(shell.toPath(), elsewhere.toPath())
        val file = bed.file("QA-Files/a.txt", "a".toByteArray())

        assertEquals(unusable, bed.ops.binDelete(file))
        assertEquals(unusable, bed.ops.binEmpty(bed.primary))
        // What lies in the folder the link leads to is not the bin's to list or count.
        assertEquals(emptyList<RecycleBin.Entry>(), bed.ops.bin.list(bed.primary))
        assertEquals(RecycleBin.Stats(0, 0), bed.ops.bin.stats(bed.primary))
        assertEquals("a", file.readText())
        assertEquals("the user's", keep.readText())
        assertEquals(listOf("keep.txt"), bed.names(File(elsewhere, "bin")))
    }

    @Test
    fun `the control - the real bin on the same volume works, and so does the other volume's while this one's is redirected`() {
        redirectBin()
        val onCard = bed.file("QA-Files/a.txt", "a".toByteArray(), bed.card)
        assertEquals(OpResult.Done(), bed.ops.binDelete(onCard))
        assertEquals(listOf("a.txt"), bed.ops.bin.list(bed.card).map { it.name })
        assertEquals(OpResult.Done(), bed.ops.binEmpty(bed.card))
    }
}

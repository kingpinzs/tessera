package app.tileshell.files

import app.tileshell.net.MiniJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix M4: the write layer's own rule about the shell's folder. No [FileOps] write has a source, a
 * destination or a resulting path inside `<volume>/.Tessera`, compared ignoring case and on canonical paths — each verb
 * refuses with `inside the shell's own folder` and touches nothing. The bin's and the nested-zip copy's own writes are
 * the only ones that go there, through their own code. The first cases are the adversarial review's proofs
 * (`AdvReviewPocTest` P2a, P2b, P3), asserting the safe outcome.
 */
class FileOpsShellDirTest {
    private val bed = FilesBed()
    private val shell: File get() = File(bed.root, ".Tessera")
    private val bin: File get() = RecycleBin.binDir(bed.primary)
    private val index: File get() = File(bin, ".index.json")
    private val replace = FilesBed.always(Conflict.REPLACE)
    private val refused = OpResult.Failed("inside the shell's own folder")

    @After fun tearDown() = bed.close()

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipOf(file: File, vararg entries: Pair<String, String>): File {
        file.parentFile!!.mkdirs()
        file.writeBytes(zipBytes(*entries.map { it.first to it.second.toByteArray() }.toTypedArray()))
        return file
    }

    private val forgedIndex = MiniJson.write(
        mapOf("version" to 1, "records" to listOf(mapOf("bin" to "9-0-holiday.jpg", "path" to "/x/holiday.jpg", "deletedAt" to 9L, "size" to 4L))),
    )

    /** One binned file, so the bin and its index are there. */
    private fun binOne(): File {
        bed.ops.binDelete(bed.file("Download/a.txt", "hello".toByteArray()))
        return File(bin, "1000-0-a.txt")
    }

    // ---- the rule itself

    @Test
    fun `the shell's folder is the same folder under any case, and by the path as written as well as resolved`() {
        val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }
        val volume = FileVolume(FilePaths.PRIMARY, "/storage/emulated/0")
        for (path in listOf(".Tessera", ".tessera", ".TESSERA/bin", ".Tessera/bin/.index.json", "Download/../.tEsSeRa/tmp/x")) {
            assertTrue(path, FilePaths.inShellDir("/storage/emulated/0/$path", volume, lexical))
        }
        for (path in listOf("", ".Tessera2", "Download/.Tessera", ".Tesser", "Tessera")) {
            assertFalse(path, FilePaths.inShellDir("/storage/emulated/0/$path", volume, lexical))
        }
        // A link inside the shell's folder that resolves somewhere else is still inside it as written.
        val link = File(shell, "out").apply { parentFile!!.mkdirs() }
        java.nio.file.Files.createSymbolicLink(link.toPath(), bed.dir("Pictures").toPath())
        assertTrue(FilePaths.inShellDir(File(link, "x.txt").path, bed.primary, bed.canonical))
        // ... and a link outside it that resolves into it is inside it as resolved.
        val door = File(bed.root, "door")
        java.nio.file.Files.createSymbolicLink(door.toPath(), shell.toPath())
        assertTrue(FilePaths.inShellDir(File(door, "bin").path, bed.primary, bed.canonical))
    }

    // ---- copy and move

    @Test
    fun `copy - into the bin, into the shell's folder, and out of the bin are refused`() {
        val binned = binOne()
        val before = index.readText()
        val evil = bed.file("Download/stage/.index.json", forgedIndex.toByteArray())

        assertEquals(refused, bed.ops.copy(listOf(evil), bin, conflict = replace))
        assertEquals(refused, bed.ops.copy(listOf(evil), shell, conflict = replace))
        assertEquals(before, index.readText())
        assertEquals(listOf("bin"), bed.names(shell))

        val out = bed.dir("Download/out")
        assertEquals(refused, bed.ops.copy(listOf(binned), out, conflict = replace))
        assertEquals(refused, bed.ops.copy(listOf(shell), out, conflict = replace))
        assertEquals(emptyList<String>(), bed.names(out))
        assertEquals("copy 0 files 0 -> ${out.path} failed inside the shell's own folder", bed.lines[bed.lines.size - 2])
    }

    @Test
    fun `copy and move - an item named like the shell's folder is not made at a volume's root, under any case`() {
        for (name in listOf(".Tessera", ".tessera", ".TESSERA")) {
            val src = bed.dir("Download/$name").also { File(it, "bin").mkdirs(); File(it, "bin/.index.json").writeText(forgedIndex) }
            assertEquals(name, refused, bed.ops.copy(listOf(src), bed.root, conflict = replace))
            assertEquals(name, refused, bed.ops.move(listOf(src), bed.root, conflict = replace))
            assertEquals(name, refused, bed.ops.move(listOf(src), bed.cardRoot, conflict = replace))
            assertTrue(src.isDirectory)
        }
        assertEquals(listOf("Download"), bed.names(bed.root))
        assertEquals(emptyList<String>(), bed.names(bed.cardRoot))
    }

    @Test
    fun `move - into the bin and out of the bin are refused, and the binned file stays binned`() {
        val binned = binOne()
        val mine = bed.file("Download/mine.txt")
        assertEquals(refused, bed.ops.move(listOf(mine), bin, conflict = replace))
        assertTrue(mine.exists())

        assertEquals(refused, bed.ops.move(listOf(binned), bed.dir("Download/out"), conflict = replace))
        assertEquals("hello", binned.readText())
        assertEquals(1, bed.ops.bin.list(bed.primary).size)
    }

    @Test
    fun `copy and move - a volume's root is not a source, so its shell folder is never read out`() {
        bed.ops.binDelete(bed.file("QA-Files/secret.txt", "binned on the card".toByteArray(), bed.card))
        val out = bed.dir("Download/out")
        assertEquals(OpResult.Failed("a whole volume cannot be the source"), bed.ops.copy(listOf(bed.cardRoot), out, conflict = replace))
        assertEquals(OpResult.Failed("a whole volume cannot be the source"), bed.ops.move(listOf(bed.cardRoot), out, conflict = replace))
        assertEquals(emptyList<String>(), bed.names(out))
    }

    // ---- rename and new folder

    @Test
    fun `rename - nothing inside the shell's folder is renamed, the folder itself is not, and nothing takes its name`() {
        val binned = binOne()
        assertEquals(refused, bed.ops.rename(binned, "renamed.txt"))
        assertEquals(refused, bed.ops.rename(bin, "trash"))
        assertEquals(refused, bed.ops.rename(shell, "Shell"))
        assertEquals("hello", binned.readText())
        assertEquals("rename ${shell.path} -> Shell: failed inside the shell's own folder", bed.lines.last())

        // On the card there is no shell folder yet: a folder must not become one.
        val folder = bed.dir("stage", bed.card).also { File(it, "bin").mkdirs() }
        for (name in listOf(".Tessera", ".tessera", ".TESSERA")) assertEquals(name, refused, bed.ops.rename(folder, name))
        assertEquals(listOf("stage"), bed.names(bed.cardRoot))
    }

    @Test
    fun `new folder - not inside the shell's folder, and not by its name at a volume's root`() {
        binOne()
        assertEquals(refused, bed.ops.newFolder(shell, "planted"))
        assertEquals(refused, bed.ops.newFolder(bin, "planted"))
        assertEquals(listOf("bin"), bed.names(shell))
        for (name in listOf(".Tessera", ".tessera", ".TESSERA")) assertEquals(name, refused, bed.ops.newFolder(bed.cardRoot, name))
        assertEquals(emptyList<String>(), bed.names(bed.cardRoot))
        assertEquals("new folder ${File(bed.cardRoot, ".TESSERA").path}: failed inside the shell's own folder", bed.lines.last())
        // Deeper down the name is an ordinary one.
        assertEquals(OpResult.Done(listOf(File(bed.root, "Download/.Tessera").path)), bed.ops.newFolder(File(bed.root, "Download"), ".Tessera"))
    }

    // ---- the bin's own verbs

    @Test
    fun `delete - a path in the shell's folder under another case is refused`() {
        // The folder a case-folding volume (FAT) would call the shell's own.
        val twin = bed.file(".tessera/bin/1000-0-a.txt", "x".toByteArray(), bed.card)
        assertEquals(OpResult.Failed("not a file Files deletes"), bed.ops.binDelete(twin))
        assertEquals(OpResult.Failed("not a file Files deletes"), bed.ops.binDelete(File(bed.cardRoot, ".tessera")))
        assertTrue(twin.exists())
    }

    @Test
    fun `restore - a record pointing into the shell's folder, under any case, restores to Download Restored`() {
        for (path in listOf("${bed.primary.root}/.Tessera/tmp/a.txt", "${bed.primary.root}/.tessera/bin/.index.json", "${bed.primary.root}/.TESSERA/a.txt")) {
            binOne()
            index.writeText(index.readText().replace(Regex("\"path\":\"[^\"]*\""), "\"path\":${MiniJson.write(path)}"))
            val entry = bed.ops.bin.list(bed.primary).single()
            val restored = File(bed.root, "Download/Restored/1000-0-a.txt")
            assertEquals(path, OpResult.Done(listOf(restored.path)), bed.ops.binRestore(entry, FilesBed.never))
            assertFalse(path, File(path).exists())
            restored.delete()
        }
        assertEquals(listOf(".Tessera", "Download"), bed.names(bed.root))
    }

    // ---- zip

    @Test
    fun `extract - a zip named like the shell's folder at a volume's root writes nothing there`() {
        // P2a: no bin yet. The archive would become `.Tessera/bin/.index.json` and a bin file.
        for (name in listOf(".Tessera.zip", ".tessera.zip", ".TESSERA.ZIP")) {
            val zip = zipOf(File(bed.root, name), "bin/.index.json" to forgedIndex, "bin/9-0-holiday.jpg" to "evil")
            assertEquals(name, refused, bed.ops.zipExtract(zip, conflict = replace))
            assertEquals(name, listOf(name), bed.names(bed.root))
            assertTrue(bed.lines.last(), bed.lines.last().endsWith(": failed inside the shell's own folder"))
            zip.delete()
        }
        assertEquals(emptyList<RecycleBin.Entry>(), bed.ops.bin.list(bed.primary))
    }

    @Test
    fun `extract - with a bin there, Replace cannot merge an archive into the shell's folder or touch the live index`() {
        // P2b.
        binOne()
        val before = index.readText()
        val zip = zipOf(File(bed.root, ".Tessera.zip"), "bin/.index.json" to forgedIndex, "bin/9-0-holiday.jpg" to "evil")

        assertEquals(refused, bed.ops.zipExtract(zip, conflict = replace))

        assertEquals(before, index.readText())
        assertEquals(listOf(".index.json", ".nomedia", "1000-0-a.txt"), bed.names(bin))
        assertEquals(listOf("a.txt"), bed.ops.bin.list(bed.primary).map { it.name })
    }

    @Test
    fun `extract and create - a zip inside the shell's folder is not extracted, and nothing is zipped out of it or into it`() {
        val binned = binOne()
        val inside = zipOf(File(bin, "1000-1-x.zip"), "a.txt" to "a")
        assertEquals(refused, bed.ops.zipExtract(inside, conflict = replace))
        assertEquals(listOf(".index.json", ".nomedia", "1000-0-a.txt", "1000-1-x.zip"), bed.names(bin))

        val out = bed.dir("Download/out")
        assertEquals(refused, bed.ops.zipCreate(listOf(binned), out, conflict = replace))
        assertEquals(refused, bed.ops.zipCreate(listOf(shell), out, conflict = replace))
        assertEquals(refused, bed.ops.zipCreate(listOf(bed.file("Download/mine.txt")), bin, conflict = replace))
        assertEquals(OpResult.Failed("a whole volume cannot be the source"), bed.ops.zipCreate(listOf(bed.root), bed.dir("out", bed.card), conflict = replace))
        assertEquals(emptyList<String>(), bed.names(out))
        assertEquals(emptyList<String>(), bed.names(File(bed.cardRoot, "out")))
    }

    @Test
    fun `nested zip - an archive lying in the shell's folder is not a source, unless it is this layer's own open copy`() {
        val inner = zipBytes("deep.txt" to "deep".toByteArray())
        val middle = zipBytes("inner.zip" to inner)
        // Planted in the temp folder under a temp's own name: the journal does not name it, so it is not ours.
        val planted = File(bed.root, ".Tessera/tmp/.planted.zip.zz00.part").apply { parentFile!!.mkdirs(); writeBytes(middle) }
        (bed.ops.zipOpen(planted) as ZipOpen.Opened).archive.use { archive ->
            assertEquals(NestedZip.Failed("inside the shell's own folder"), bed.ops.zipOpenNested(archive, archive.row("inner.zip")!!))
        }
        assertEquals(listOf(".planted.zip.zz00.part"), bed.names(planted.parentFile!!))

        // A zip in a zip in a zip still opens: the second level's source is the first level's journalled copy.
        val outer = File(bed.root, "QA-Files/outer.zip").apply { parentFile!!.mkdirs(); writeBytes(zipBytes("middle.zip" to middle)) }
        (bed.ops.zipOpen(outer) as ZipOpen.Opened).archive.use { a ->
            val first = bed.ops.zipOpenNested(a, a.row("middle.zip")!!) as NestedZip.Opened
            (bed.ops.zipOpen(first.file) as ZipOpen.Opened).archive.use { b ->
                val second = bed.ops.zipOpenNested(b, b.row("inner.zip")!!) as NestedZip.Opened
                (bed.ops.zipOpen(second.file) as ZipOpen.Opened).archive.use { c -> assertEquals(listOf("deep.txt"), c.list().map { it.name }) }
                bed.ops.zipCloseNested(second)
            }
            bed.ops.zipCloseNested(first)
        }
    }
}

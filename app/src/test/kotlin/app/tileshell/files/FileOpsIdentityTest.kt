package app.tileshell.files

import app.tileshell.net.MiniJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GATE review's N2 (pass 2): "is this the shell's folder / Android / a reserved bin name" is decided by FILE
 * IDENTITY, never by text. Two stand-ins for a volume that folds names, since the host's does not:
 *  - [FoldingVolume], the injected port: the rules' DECISIONS for names such as `.Teßera` and `Andr<U+200B>oid`;
 *  - a LINK as the alias, with a canonicaliser that returns the name as written (what `realpath` does on the device):
 *    there a write through the alias really lands in the shell's folder, so the write layer's refusal is run whole.
 * Each of the reviewer's PoCs is here asserting the SAFE outcome.
 */
class FileOpsIdentityTest {
    private val bed = FilesBed()
    private val folding = FoldingVolume()
    private val replace = FilesBed.always(Conflict.REPLACE)
    private val sharp = ".Teßera"
    private val zeroWidth = ".Tess​era"

    @After fun tearDown() = bed.close()

    private fun zip(file: File, vararg entries: Pair<String, String>): File {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b.toByteArray()); z.closeEntry() } }
        file.parentFile!!.mkdirs(); file.writeBytes(out.toByteArray()); return file
    }

    /** The volume as the device shows it: [alias] leads to `.Tessera`, and resolving a path keeps the name as written. */
    private val asWritten: (String) -> String? = { Paths.get(it).toAbsolutePath().normalize().toString() }
    private fun linked(alias: String): FileOps {
        Files.createSymbolicLink(File(bed.root, alias).toPath(), File(bed.root, ".Tessera").toPath())
        return FileOps({ bed.mounted.toList() }, asWritten, { bed.now }, { bed.scanned += it }, { bed.lines += it }, OpsJournal(bed.privateDir, { bed.now }, asWritten, { bed.lines += it }))
    }

    // ---- the decisions, on the folding port

    @Test
    fun `a name the volume folds onto the shell's folder is the shell's folder - the exact and ASCII-case names still are`() {
        bed.dir(".Tessera/bin")
        for (name in listOf(sharp, zeroWidth, ".Teſſera", ".Tessera­", ".TESSERA", ".Tessera")) {
            assertTrue(name, FilePaths.inShellDir(File(bed.root, "$name/bin").path, bed.primary, bed.canonical, folding))
            assertTrue(name, FilePaths.inShellDir(File(bed.root, name).path, bed.primary, bed.canonical, folding))
            assertTrue(name, FilePaths.isShellDir(File(bed.root, name), bed.primary, folding))
        }
        assertFalse(FilePaths.inShellDir(File(bed.root, "Download/x").path, bed.primary, bed.canonical, folding))
        assertFalse(FilePaths.inShellDir(File(bed.root, ".Tesera/bin").path, bed.primary, bed.canonical, folding))
        // With nothing there yet, the name itself (any case) still is.
        assertTrue(FilePaths.inShellDir(File(bed.cardRoot, ".tessera/bin").path, bed.card, bed.canonical, folding))
    }

    @Test
    fun `a launch extra does not open the bin as a folder page through an alias`() {
        bed.dir(".Tessera/bin")
        for (name in listOf(sharp, zeroWidth)) {
            val open = FilesNav.resolve(null, File(bed.root, "$name/bin").path, bed.mounted.toList(), bed.canonical, folding) { true }
            assertEquals(name, FilesOpen.Ignored("inside the shell's own folder"), open)
        }
    }

    @Test
    fun `the provider serves nothing from the shell's folders through an alias`() {
        bed.file(".Tessera/bin/1-0-a.txt")
        bed.file("Android/data/app.tileshell/files/x")
        bed.file("Android/media/other/y")
        val roots = listOf(bed.primary.root)
        assertFalse(FileShareGuard.allowed(File(bed.root, "$sharp/bin/1-0-a.txt").path, roots, folding, bed.canonical))
        assertFalse(FileShareGuard.allowed(File(bed.root, "Andr​oid/data/app.tileshell/files/x").path, roots, folding, bed.canonical))
        assertFalse(FileShareGuard.allowed(File(bed.root, "Android/DATA/APP.Tileshell­/files/x").path, roots, folding, bed.canonical))
        assertTrue(FileShareGuard.allowed(File(bed.root, "Android/media/other/y").path, roots, folding, bed.canonical))
    }

    @Test
    fun `a forged record under an aliased Android is no record - the file lists by its bin name`() {
        val ops = bed.opsOn(folding)
        assertEquals(OpResult.Done(), ops.binDelete(bed.file("Download/mine.txt")))
        bed.dir("Android/media")
        val bin = RecycleBin.binDir(bed.primary)
        File(bin, "9-0-holiday.jpg").writeText("EVIL")
        val victim = File(bed.root, "Andr​oid/media/com.victim/holiday.jpg")
        val mine = ops.bin.list(bed.primary).single { it.indexed }
        File(bin, ".index.json").writeText(MiniJson.write(mapOf("version" to 1, "records" to listOf(
            mapOf("bin" to mine.binName, "path" to mine.originalPath, "deletedAt" to 1L, "size" to 1L),
            mapOf("bin" to "9-0-holiday.jpg", "path" to victim.path, "deletedAt" to 9L, "size" to 4L),
        ))))
        val row = ops.bin.list(bed.primary).single { it.binName == "9-0-holiday.jpg" }
        assertNull(row.originalPath)
        assertEquals(File(bed.root, "Download/Restored").path, row.restoreDir)
    }

    @Test
    fun `a reserved bin name under an alias is not a bin entry`() {
        val ops = bed.opsOn(folding)
        ops.binDelete(bed.file("Download/mine.txt"))
        val bin = RecycleBin.binDir(bed.primary)
        assertTrue(ops.bin.reservedIn(bin, ".index.jſon"))
        assertTrue(ops.bin.reservedIn(bin, ".NOMEDIA"))
        assertTrue(ops.bin.reservedIn(bin, ".no​media"))
        assertFalse(ops.bin.reservedIn(bin, "1000-0-mine.txt"))
        val forged = RecycleBin.Entry(bed.primary, ".index.jſon", "x", null, null, 0, false)
        assertEquals(OpResult.Failed("not a bin entry"), ops.binPurge(forged))
        assertTrue(File(bin, ".index.json").isFile)
    }

    // ---- a name that is NOT THERE yet, directly under a volume's root

    @Test
    fun `making something at a volume's root makes the shell's folder first, so its folded twin is refused`() {
        val ops = bed.opsOn(folding)
        assertFalse(File(bed.root, ".Tessera").exists())
        // No shell folder yet: the twin's name is refused once the folder it would become is there to compare with.
        assertEquals(OpResult.Failed(FileOps.SHELL), ops.newFolder(bed.root, sharp))
        assertTrue(File(bed.root, ".Tessera").isDirectory)
        assertFalse(File(bed.root, sharp).exists())
        assertEquals(OpResult.Failed(FileOps.SHELL), ops.rename(bed.dir("Docs"), zeroWidth))
        assertEquals(OpResult.Failed(FileOps.SHELL), ops.zipExtract(zip(File(bed.root, "$sharp.zip"), "bin/.index.json" to "{}"), conflict = replace))
        // An ordinary name at the root is made as before.
        assertEquals(OpResult.Done(listOf(File(bed.root, "Trip").path)), ops.newFolder(bed.root, "Trip"))
    }

    @Test
    fun `a folder the volume folds onto the bin's name is never adopted as the bin - the delete names it`() {
        // Another app made `.Teßera` (with a forged bin in it) before any bin existed.
        bed.file("$sharp/bin/.index.json", "{}".toByteArray())
        val ops = bed.opsOn(folding)
        val a = bed.file("Download/a.txt")
        val r = ops.binDelete(a)
        assertEquals(OpResult.Failed("A folder named $sharp is in the way on ${bed.primary.root}. Remove or rename it from a computer, then try again."), r)
        assertTrue(a.isFile)
        assertTrue(ops.bin.list(bed.primary).isEmpty())
    }

    // ---- the writes, whole, with a link as the alias

    @Test
    fun `a new folder inside the bin through an alias is refused`() {
        bed.ops.binDelete(bed.file("Download/mine.txt"))
        val ops = linked(zeroWidth)
        assertEquals(OpResult.Failed(FileOps.SHELL), ops.newFolder(File(bed.root, "$zeroWidth/bin"), "planted"))
        assertFalse(File(bed.root, ".Tessera/bin/planted").exists())
        assertTrue(FilesNav.resolve(null, File(bed.root, "$zeroWidth/bin").path, bed.mounted.toList(), asWritten) { File(it).isDirectory } is FilesOpen.Ignored)
    }

    @Test
    fun `an aliased zip at the root does not extract into the shell's folder - the index keeps the user's record`() {
        bed.ops.binDelete(bed.file("Download/mine.txt"))
        val index = File(bed.root, ".Tessera/bin/.index.json")
        val before = index.readText()
        val ops = linked(sharp)
        val z = zip(File(bed.root, "$sharp.zip"), "bin/.index.json" to "{\"version\":1,\"records\":[]}", "bin/9-0-holiday.jpg" to "EVIL")
        assertEquals(OpResult.Failed(FileOps.SHELL), ops.zipExtract(z, conflict = replace))
        assertEquals(before, index.readText())
        assertFalse(File(bed.root, ".Tessera/bin/9-0-holiday.jpg").exists())
        assertTrue(Files.isSymbolicLink(File(bed.root, sharp).toPath()))
        // Nor is the alias a source, or deletable.
        assertEquals(OpResult.Failed("not a file Files deletes"), ops.binDelete(File(bed.root, sharp)))
        assertEquals(OpResult.Failed(FileOps.SHELL), ops.copy(listOf(File(bed.root, "$sharp/bin")), bed.dir("Download"), conflict = replace))
    }

    // ---- N6: the message

    @Test
    fun `the obstacle's message names it, where it is and what to do`() {
        assertEquals(
            "A file named .Tessera is in the way on This Device. Remove or rename it from a computer, then try again.",
            BinObstacle.message(BinObstacle(BinObstacle.Kind.FILE, ".Tessera", inShell = false), "This Device"),
        )
        assertEquals(
            "A file named bin is in the way in .Tessera on SD card. Remove or rename it from a computer, then try again.",
            BinObstacle.message(BinObstacle(BinObstacle.Kind.FILE, "bin", inShell = true), "SD card"),
        )
        assertEquals(
            "The Recycle Bin folder cannot be made on SD card. Check that the storage is not full or read-only, then try again.",
            BinObstacle.message(BinObstacle(BinObstacle.Kind.UNWRITABLE, ".Tessera", inShell = false), "SD card"),
        )
        assertEquals(
            "A link named a?b is in the way on SD card. Remove or rename it from a computer, then try again.",
            BinObstacle.message(BinObstacle(BinObstacle.Kind.LINK, "a\nb", inShell = false), "SD card"),
        )
    }

    @Test
    fun `a file where the shell's folder must be - delete and Replace fail with the message, and nothing is touched`() {
        val block = File(bed.root, ".Tessera").apply { writeText("x") }
        val a = bed.file("Download/a.txt")
        val why = "A file named .Tessera is in the way on ${bed.primary.root}. Remove or rename it from a computer, then try again."
        assertEquals(OpResult.Failed(why), bed.ops.binDelete(a))
        assertTrue(a.isFile)
        val src = bed.file("Pictures/a.txt", "new".toByteArray())
        val copied = bed.ops.copy(listOf(src), a.parentFile!!, conflict = replace)
        assertEquals(OpResult.Failed("what is there could not go to the Recycle Bin ($why)"), copied)
        assertEquals("Download/a.txt", a.readText())
        assertEquals("x", block.readText())
        // `bin` itself a file.
        block.delete()
        bed.file(".Tessera/bin", "y".toByteArray())
        assertEquals(
            OpResult.Failed("A file named bin is in the way in .Tessera on ${bed.primary.root}. Remove or rename it from a computer, then try again."),
            bed.ops.binDelete(a),
        )
    }
}

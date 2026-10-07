package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix M7: the guards whose removal no test noticed (the adversarial review's surviving mutants in
 * `files/`). Each case here fails when its one guard is taken out: FileOps' rule 1 for rename, new folder and an
 * extract's zip; the journal's bin check under another case; a temp's leading dot; and the second half of the zip
 * entry-name guard — where an entry would REALLY be written. (The bin's and the provider's are in `FileOpsBinTest`,
 * `FileOpsBinRecordTest`, `FileOpsShellDirTest` and `FilesProviderScopeTest`.)
 */
class FileOpsGuardsTest {
    private val bed = FilesBed()

    @After fun tearDown() = bed.close()

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

    // ---- rule 1: only a path under a mounted volume

    @Test
    fun `rename - a file outside every volume is refused, by its own path and through a link`() {
        val secret = File(bed.privateDir, "secret.txt").apply { writeText("private") }
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.rename(secret, "renamed.txt"))
        assertEquals(listOf("secret.txt"), bed.names(bed.privateDir))

        // A link on shared storage to the private FOLDER: what it holds is not under a volume either.
        val door = File(bed.dir("QA-Files"), "door")
        Files.createSymbolicLink(door.toPath(), bed.privateDir.toPath())
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.rename(File(door, "secret.txt"), "renamed.txt"))
        assertEquals(listOf("secret.txt"), bed.names(bed.privateDir))
        assertEquals("rename ${File(door, "secret.txt").path} -> renamed.txt: failed outside shared storage", bed.lines.last())
        assertTrue(bed.scanned.isEmpty())
    }

    @Test
    fun `new folder - a parent outside every volume is refused, by its own path and through a link`() {
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.newFolder(bed.privateDir, "planted"))
        val door = File(bed.dir("QA-Files"), "door")
        Files.createSymbolicLink(door.toPath(), bed.privateDir.toPath())
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.newFolder(door, "planted"))
        assertEquals(emptyList<String>(), bed.names(bed.privateDir))
        assertTrue(bed.scanned.isEmpty())
    }

    @Test
    fun `extract - a zip that resolves outside every volume is refused though the folder it is named in is shared`() {
        val outside = zipOf(File(bed.privateDir, "private.zip"), "a.txt" to "private")
        val link = File(bed.dir("QA-Files"), "innocent.zip")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.zipExtract(link, conflict = FilesBed.never))

        assertEquals(listOf("innocent.zip"), bed.names(link.folder))
        assertEquals(listOf("zip extract ${link.path} -> ${File(link.folder, "innocent").path}: failed outside shared storage"), bed.lines)
    }

    // ---- the journal and the sweep

    @Test
    fun `a temp's name starts with a dot - a user's file that only ends like one is not a temp`() {
        assertTrue(FilePaths.isTempName(".b.bin.k3x00.part"))
        assertTrue(FilePaths.isTempName(".qa.zip.k3x00.extract"))
        for (name in listOf("movie.part", "disc.extract", "b.bin.k3x00.part", "part", ".part.txt", "")) assertFalse(name, FilePaths.isTempName(name))
    }

    @Test
    fun `the sweep never removes a user's file that ends like a temp, however the journal names it`() {
        val part = bed.file("QA-Files/movie.part", "a download the user is keeping".toByteArray())
        val extract = bed.dir("QA-Files/disc.extract").also { File(it, "track.bin").writeText("kept") }
        val forged = bed.newJournal()
        forged.put("op1", part.path, bed.primary.uuid)
        forged.put("op2", extract.path, bed.primary.uuid)

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertTrue(part.exists())
        assertEquals(listOf("track.bin"), bed.names(extract))
    }

    @Test
    fun `the sweep never removes a bin path named under another case`() {
        // The folder a case-folding volume (FAT) would call the bin; here it is a folder of its own.
        val twin = bed.file(".tessera/BIN/.1000-0-x.abc.part", "a binned file on FAT".toByteArray(), bed.card)
        val forged = bed.newJournal()
        forged.put("op1", twin.path, bed.card.uuid)
        forged.put("op2", twin.folder.path, bed.card.uuid)

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertEquals("a binned file on FAT", twin.readText())
    }

    // ---- the zip entry-name guard's second half

    @Test
    fun `zip guard - an entry whose name is clean but whose real place is outside the extract folder is refused`() {
        // The name guard's first half reads names; the second asks where the entry would REALLY be written. A folder of
        // the temp tree turned into a link (another app, mid-extract) is the case only the second can see.
        val zip = zipOf(File(bed.root, "QA-Files/pics.zip"), "a.txt" to "first", "sub/evil.txt" to "written through a link", "z.txt" to "last")
        val outside = bed.dir("Victim")
        var planted = false
        val control = OpControl(stopped = {
            val temp = zip.folder.listFiles()?.firstOrNull { it.name.endsWith(".extract") }
            if (!planted && temp != null) {
                Files.createSymbolicLink(File(temp, "sub").toPath(), outside.toPath())
                planted = true
            }
            null
        })

        val result = bed.ops.zipExtract(zip, control, FilesBed.never)

        assertTrue(planted)
        assertEquals(emptyList<String>(), bed.names(outside))
        assertTrue(bed.lines.toString(), "zip: refused entry sub/evil.txt" in bed.lines)
        // The other entries extract (T18-2).
        assertEquals(OpResult.Done(listOf(File(zip.folder, "pics").path)), result)
        assertEquals("first", File(zip.folder, "pics/a.txt").readText())
        assertEquals("last", File(zip.folder, "pics/z.txt").readText())
    }
}

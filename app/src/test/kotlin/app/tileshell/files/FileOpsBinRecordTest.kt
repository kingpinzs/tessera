package app.tileshell.files

import app.tileshell.net.MiniJson
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix M5: Restore never sends a file somewhere the user cannot foresee. The bin's index is shared storage
 * (anything with All-files access can write it), so a record's original path is honoured only in the exact form a
 * delete writes — already canonical, under its own volume, outside `Android/` and the shell's folder, not the volume's
 * root, a valid name. Any other record is no record: its file lists by its bin name and restores to
 * `<volume>/Download/Restored/`, like a file the index never named. The first two cases are the adversarial review's
 * proofs (`AdvReviewPocTest` P1a, P5), asserting the safe outcome.
 */
class FileOpsBinRecordTest {
    private val bed = FilesBed()
    private val bin: File get() = RecycleBin.binDir(bed.primary)
    private val index: File get() = File(bin, ".index.json")
    private val restored: File get() = File(bed.root, "Download/Restored/1000-0-a.txt")

    @After fun tearDown() = bed.close()

    /** `Download/a.txt` binned, then its record's path forged to [path]. Returns the bin's one row. */
    private fun forged(path: String): RecycleBin.Entry {
        bed.ops.binDelete(bed.file("Download/a.txt", "hello".toByteArray()))
        index.writeText(MiniJson.write(mapOf("version" to 1, "records" to listOf(mapOf("bin" to "1000-0-a.txt", "path" to path, "deletedAt" to 1000L, "size" to 5L)))))
        return bed.ops.bin.list(bed.primary).single()
    }

    /** The forged record is no record: the row is the bin name, and Restore goes to Download/Restored, unasked nowhere else. */
    private fun assertUnindexed(path: String) {
        val entry = forged(path)
        assertNull(path, entry.originalPath)
        assertFalse(path, entry.indexed)
        assertEquals(path, "1000-0-a.txt", entry.name)
        assertEquals(path, OpResult.Done(listOf(restored.path)), bed.ops.binRestore(entry, FilesBed.never))
        assertEquals(path, "hello", restored.readText())
        assertEquals(path, "bin restore ${restored.path}: ok", bed.lines.last())
    }

    @Test
    fun `a forged record under Android is not honoured - nothing lands in another app's folder`() {
        val planted = File(bed.root, "Android/media/com.victim.app/config/a.txt")
        assertUnindexed(planted.path)
        assertFalse(File(bed.root, "Android").exists())
    }

    @Test
    fun `a forged record under Android in another case, or in its data and obb folders, is not honoured`() {
        for (path in listOf("ANDROID/media/x/a.txt", "android/data/com.victim.app/files/a.txt", "Android/obb/com.victim.app/a.txt", "Android/a.txt")) {
            assertUnindexed("${bed.primary.root}/$path")
            restored.delete()
        }
        assertEquals(listOf(".Tessera", "Download"), bed.names(bed.root))
    }

    @Test
    fun `a forged record with dot-dot segments is not honoured, and no stray folder is made on the way`() {
        bed.dir("Pictures")
        assertUnindexed("${bed.primary.root}/stray/../Pictures/a.txt")
        assertFalse(File(bed.root, "stray").exists())
        assertEquals(emptyList<String>(), bed.names(File(bed.root, "Pictures")))
    }

    @Test
    fun `a record whose path is not already canonical is not honoured - a dot segment, a doubled separator, a trailing one`() {
        bed.dir("Pictures")
        for (path in listOf("/./Pictures/a.txt", "//Pictures/a.txt", "/Pictures//a.txt", "/Pictures/./a.txt", "/Pictures/a.txt/", "/Pictures/sub/../a.txt")) {
            assertUnindexed(bed.primary.root + path)
            restored.delete()
        }
        assertEquals(emptyList<String>(), bed.names(File(bed.root, "Pictures")))
    }

    @Test
    fun `a record whose path resolves through a link is not honoured`() {
        val real = bed.dir("Pictures")
        java.nio.file.Files.createSymbolicLink(File(bed.root, "Shots").toPath(), real.toPath())
        assertUnindexed("${bed.primary.root}/Shots/a.txt")
        assertEquals(emptyList<String>(), bed.names(real))
    }

    @Test
    fun `a record naming the volume's root is not honoured`() {
        assertUnindexed(bed.primary.root)
        assertTrue(bed.root.isDirectory)
    }

    @Test
    fun `a record whose last segment is not a name a file can have is not honoured`() {
        for (name in listOf(" ", "x".repeat(256))) {
            assertUnindexed("${bed.primary.root}/Pictures/$name")
            restored.delete()
        }
        assertFalse(File(bed.root, "Pictures").exists())
    }

    @Test
    fun `an honest record is honoured - the file goes back to its own path, its folder made again`() {
        val file = bed.file("Pictures/Trip/a.jpg", "photo".toByteArray())
        bed.ops.binDelete(file)
        File(bed.root, "Pictures").deleteRecursively()
        val entry = bed.ops.bin.list(bed.primary).single()
        assertEquals(file.path, entry.originalPath)
        assertEquals("a.jpg", entry.name)
        assertEquals(OpResult.Done(listOf(file.path)), bed.ops.binRestore(entry, FilesBed.never))
        assertEquals("photo", file.readText())
    }

    @Test
    fun `a file deleted from under Android restores to Download Restored - its row says so from the start`() {
        val file = bed.file("Android/media/com.some.app/voice.ogg", "voice".toByteArray())
        assertEquals(OpResult.Done(), bed.ops.binDelete(file))
        val entry = bed.ops.bin.list(bed.primary).single()
        assertNull(entry.originalPath)
        assertEquals("1000-0-voice.ogg", entry.name)
        val back = File(bed.root, "Download/Restored/1000-0-voice.ogg")
        assertEquals(OpResult.Done(listOf(back.path)), bed.ops.binRestore(entry, FilesBed.never))
        assertEquals("voice", back.readText())
    }

    // ---- the row shows where Restore will put the file

    @Test
    fun `a row's restore folder is the folder Restore uses - the original one, or Download Restored`() {
        val honest = bed.file("Pictures/Trip/a.jpg", "photo".toByteArray())
        bed.ops.binDelete(honest)
        assertEquals(File(bed.root, "Pictures/Trip").path, bed.ops.bin.list(bed.primary).single().restoreDir)
        bed.ops.binPurge(bed.ops.bin.list(bed.primary).single())

        // A forged record and a file no record names: both say Download/Restored, and both go there.
        val entry = forged(File(bed.root, "Android/media/com.victim.app/a.txt").path)
        assertEquals(File(bed.root, "Download/Restored").path, entry.restoreDir)
        val result = bed.ops.binRestore(entry, FilesBed.never) as OpResult.Done
        assertEquals(entry.restoreDir, File(result.outputs.single()).parent)

        // At a volume's root the folder is the root.
        val top = bed.file("top.txt", volume = bed.card)
        bed.ops.binDelete(top)
        assertEquals(bed.card.root, bed.ops.bin.list(bed.card).single().restoreDir)
    }

    @Test
    fun `the bin row's detail is the deleted date and the restore folder relative to its volume`() {
        val root = "/storage/emulated/0"
        fun detail(dir: String, many: Boolean = false, date: String? = "1/2/2026") = FileListing.binDetail(date, dir, root, "This Device", many)

        assertEquals("1/2/2026 Pictures/Trip", detail("$root/Pictures/Trip"))
        assertEquals("1/2/2026 Download/Restored", detail("$root/Download/Restored"))
        // The volume's own root reads as its name.
        assertEquals("1/2/2026 This Device", detail(root))
        assertEquals("1/2/2026 This Device", detail("$root/"))
        // A file no record names has no date.
        assertEquals("Download/Restored", detail("$root/Download/Restored", date = null))
        // More than one volume: the volume's name leads.
        assertEquals("1/2/2026 This Device/Pictures/Trip", detail("$root/Pictures/Trip", many = true))
        assertEquals("1/2/2026 This Device", detail(root, many = true))
        assertEquals("3/4/2026 Virtual SD card/DCIM", FileListing.binDetail("3/4/2026", "/storage/3842-1609/DCIM", "/storage/3842-1609", "Virtual SD card", true))
        // A neighbour of the volume is not under it; a control character never reaches the line.
        assertEquals("1/2/2026 /storage/emulated/01/x", detail("/storage/emulated/01/x"))
        assertEquals("1/2/2026 Pic?tures", detail("$root/Pic\ntures"))
    }
}

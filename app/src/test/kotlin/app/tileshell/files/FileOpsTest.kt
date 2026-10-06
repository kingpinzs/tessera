package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 18, r3 D7 / E18: the write layer's copy, move, rename and new folder, on a temp dir. */
class FileOpsTest {
    private val bed = FilesBed()
    private val big = FilesBed.bytes(300_000)

    @After fun tearDown() = bed.close()

    // ---- temp-and-rename

    @Test
    fun `temp-and-rename - a copy is written to the journalled part file and renamed when it is whole`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        val seen = mutableListOf<String>()
        val result = bed.ops.copy(listOf(src), dest, OpControl(progress = { done, total ->
            if (seen.isEmpty()) {
                // Mid-copy: only the temp is there, under r3 D4's name, and the journal names it.
                val temp = bed.names(dest).single()
                assertTrue(temp, Regex("""\.b\.bin\.[0-9a-z]+\.part""").matches(temp))
                assertEquals(listOf(File(dest, temp).path), bed.journal.entries().map { it.path })
                assertEquals(FilePaths.PRIMARY, bed.journal.entries().single().volumeUuid)
                assertTrue(done in 1 until total)
            }
            seen += "$done/$total"
        }), FilesBed.never)

        assertEquals(OpResult.Done(listOf(File(dest, "b.bin").path)), result)
        assertEquals(listOf("b.bin"), bed.names(dest))
        assertArrayEquals(big, File(dest, "b.bin").readBytes())
        assertEquals(src.lastModified(), File(dest, "b.bin").lastModified())
        assertEquals("300000/300000", seen.last())
        assertTrue(bed.journal.entries().isEmpty())
        assertEquals(listOf("copy 1 files 300000 -> ${dest.path} done"), bed.lines)
        assertEquals(listOf(File(dest, "b.bin").path), bed.scanned)
    }

    @Test
    fun `temp-and-rename - a file whose name is 255 bytes long copies under a temp name that still fits`() {
        val name = "n".repeat(251) + ".bin"
        val src = bed.file("QA-Files/$name", big)
        val dest = bed.dir("QA-Files/sub")
        var temp = ""
        bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ -> if (temp.isEmpty()) temp = bed.names(dest).single() }), FilesBed.never)
        assertTrue(temp, temp.startsWith(".nnn") && temp.endsWith(".part") && FilePaths.utf8(temp) <= 255)
        assertEquals(listOf(name), bed.names(dest))
    }

    // ---- cancel

    @Test
    fun `cancel leaving no temp - a cancelled copy leaves no file and no part file in the destination`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        var chunks = 0
        val result = bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ -> chunks++ }, stopped = { if (chunks >= 1) StopReason.CANCEL else null }), FilesBed.never)

        assertEquals(OpResult.Cancelled, result)
        assertEquals(1, chunks)
        assertEquals(emptyList<String>(), bed.names(dest))
        assertTrue(bed.journal.entries().isEmpty())
        assertArrayEquals(big, src.readBytes())
        assertEquals(listOf("copy 1 files 300000 -> ${dest.path} cancelled"), bed.lines)
    }

    @Test
    fun `cancel leaving no temp - the time limit fails the copy the same way`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        var chunks = 0
        val result = bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ -> chunks++ }, stopped = { if (chunks >= 1) StopReason.TIME_LIMIT else null }), FilesBed.never)

        assertEquals(OpResult.Failed("time limit"), result)
        assertEquals(emptyList<String>(), bed.names(dest))
        assertEquals(listOf("copy 1 files 300000 -> ${dest.path} failed time limit"), bed.lines)
    }

    // ---- each conflict answer

    @Test
    fun `conflict replace - the copy takes the place of the file that was there`() {
        val src = bed.file("QA-Files/sub/b.bin", big)
        val there = bed.file("QA-Files/b.bin", "old".toByteArray())
        val asked = mutableListOf<File>()
        val result = bed.ops.copy(listOf(src), there.folder, conflict = { asked += it; Conflict.REPLACE })

        assertEquals(OpResult.Done(listOf(there.path)), result)
        assertEquals(listOf(there), asked)
        assertArrayEquals(big, there.readBytes())
        assertEquals(listOf("b.bin", "sub"), bed.names(there.folder))
    }

    @Test
    fun `conflict keep both - the copy is name (2) ext, then (3)`() {
        val src = bed.file("QA-Files/sub/b.bin", big)
        val there = bed.file("QA-Files/b.bin", "old".toByteArray())
        val folder = there.folder

        assertEquals(OpResult.Done(listOf(File(folder, "b (2).bin").path)), bed.ops.copy(listOf(src), folder, conflict = FilesBed.always(Conflict.KEEP_BOTH)))
        assertEquals(OpResult.Done(listOf(File(folder, "b (3).bin").path)), bed.ops.copy(listOf(src), folder, conflict = FilesBed.always(Conflict.KEEP_BOTH)))

        assertEquals(listOf("b (2).bin", "b (3).bin", "b.bin", "sub"), bed.names(folder))
        assertEquals("old", there.readText())
        assertArrayEquals(big, File(folder, "b (2).bin").readBytes())
        assertArrayEquals(big, File(folder, "b (3).bin").readBytes())
    }

    @Test
    fun `conflict skip - nothing is written and the file that was there stays`() {
        val src = bed.file("QA-Files/sub/b.bin", big)
        val there = bed.file("QA-Files/b.bin", "old".toByteArray())
        val result = bed.ops.copy(listOf(src), there.folder, conflict = FilesBed.always(Conflict.SKIP))

        assertEquals(OpResult.Done(emptyList(), skipped = 1), result)
        assertEquals("old", there.readText())
        assertEquals(listOf("b.bin", "sub"), bed.names(there.folder))
        assertTrue(bed.scanned.isEmpty())
    }

    @Test
    fun `conflict keep both - a copy into its own folder is kept beside the original without asking`() {
        val src = bed.file("QA-Files/b.bin", big)
        assertEquals(OpResult.Done(listOf(File(src.folder, "b (2).bin").path)), bed.ops.copy(listOf(src), src.folder, conflict = FilesBed.never))
    }

    @Test
    fun `conflict replace - a folder replacing a folder merges into it and keeps its other files`() {
        bed.file("QA-Files/in/album/a.txt", "new".toByteArray())
        bed.file("QA-Files/album/a.txt", "old".toByteArray())
        val other = bed.file("QA-Files/album/other.txt")
        val asked = mutableListOf<File>()
        bed.ops.copy(listOf(File(bed.root, "QA-Files/in/album")), File(bed.root, "QA-Files"), conflict = { asked += it; Conflict.REPLACE })

        // Asked once, about the folder; its contents follow that answer.
        assertEquals(listOf(File(bed.root, "QA-Files/album")), asked)
        assertEquals("new", File(bed.root, "QA-Files/album/a.txt").readText())
        assertTrue(other.exists())
    }

    // ---- move

    @Test
    fun `a move on one volume is a rename - gone from the parent, present in the folder, Recent follows`() {
        val src = bed.file("QA-Files/a.txt", "0123456789".toByteArray())
        val dest = bed.dir("QA-Files/sub")
        bed.recent.add(src.path, FilePaths.PRIMARY)
        bed.lines.clear()
        val result = bed.ops.move(listOf(src), dest, conflict = FilesBed.never)

        assertEquals(OpResult.Done(listOf(File(dest, "a.txt").path)), result)
        assertFalse(src.exists())
        assertEquals("0123456789", File(dest, "a.txt").readText())
        assertEquals(listOf("move 1 files 10 -> ${dest.path} done"), bed.lines)
        assertEquals(setOf(src.path, File(dest, "a.txt").path), bed.scanned.toSet())
        assertEquals(listOf(File(dest, "a.txt").path), bed.recent.all().map { it.path })
    }

    @Test
    fun `a cross-volume move is copy then delete - the file arrives whole and the original goes`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("moved", bed.card)
        bed.recent.add(src.path, FilePaths.PRIMARY)
        var temp = ""
        val result = bed.ops.move(listOf(src), dest, OpControl(progress = { _, _ -> if (temp.isEmpty()) temp = bed.names(dest).single() }), FilesBed.never)

        assertEquals(OpResult.Done(listOf(File(dest, "b.bin").path)), result)
        assertTrue(temp, temp.endsWith(".part"))
        assertFalse(src.exists())
        assertArrayEquals(big, File(dest, "b.bin").readBytes())
        assertEquals(RecentStore.Entry(File(dest, "b.bin").path, bed.card.uuid, 1_000L), bed.recent.all().single())
    }

    @Test
    fun `a cross-volume move failing after the copy leaves both files, never neither`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("moved", bed.card)
        bed.removeWorks = false
        val result = bed.ops.move(listOf(src), dest, conflict = FilesBed.never)

        assertEquals(OpResult.Failed("copied, but the original could not be removed"), result)
        assertArrayEquals(big, src.readBytes())
        assertArrayEquals(big, File(dest, "b.bin").readBytes())
        assertEquals(listOf("b.bin"), bed.names(dest))
        assertEquals(listOf("move 1 files 300000 -> ${dest.path} failed copied, but the original could not be removed"), bed.lines)
    }

    @Test
    fun `a cross-volume move cancelled mid-copy leaves the original and no temp`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("moved", bed.card)
        var chunks = 0
        val result = bed.ops.move(listOf(src), dest, OpControl(progress = { _, _ -> chunks++ }, stopped = { if (chunks >= 2) StopReason.CANCEL else null }), FilesBed.never)

        assertEquals(OpResult.Cancelled, result)
        assertArrayEquals(big, src.readBytes())
        assertEquals(emptyList<String>(), bed.names(dest))
    }

    @Test
    fun `a folder moves across volumes with everything in it`() {
        bed.file("QA-Files/album/a.txt")
        bed.file("QA-Files/album/deep/b.bin", big)
        val dest = bed.dir("moved", bed.card)
        val result = bed.ops.move(listOf(File(bed.root, "QA-Files/album")), dest, conflict = FilesBed.never)

        assertEquals(OpResult.Done(listOf(File(dest, "album").path)), result)
        assertFalse(File(bed.root, "QA-Files/album").exists())
        assertArrayEquals(big, File(dest, "album/deep/b.bin").readBytes())
        assertEquals("move 2 files ${300_000 + "QA-Files/album/a.txt".length} -> ${dest.path} done", bed.lines.single())
    }

    // ---- folders

    @Test
    fun `a folder copies with everything in it, each file through its own temp`() {
        bed.file("QA-Files/album/a.txt")
        bed.file("QA-Files/album/deep/b.bin", big)
        bed.dir("QA-Files/album/empty")
        val dest = bed.dir("Copies")
        bed.ops.copy(listOf(File(bed.root, "QA-Files/album")), dest, conflict = FilesBed.never)

        assertEquals(listOf("a.txt", "deep", "empty"), bed.names(File(dest, "album")))
        assertArrayEquals(big, File(dest, "album/deep/b.bin").readBytes())
        assertEquals(setOf(File(dest, "album/a.txt").path, File(dest, "album/deep/b.bin").path), bed.scanned.toSet())
    }

    @Test
    fun `a folder is not copied into itself`() {
        bed.file("QA-Files/album/a.txt")
        val album = File(bed.root, "QA-Files/album")
        val result = bed.ops.copy(listOf(album), File(album, "deep").apply { mkdirs() }, conflict = FilesBed.never)
        assertEquals(OpResult.Failed("the destination is inside album"), result)
        assertEquals(listOf("a.txt", "deep"), bed.names(album))
        assertEquals(emptyList<String>(), bed.names(File(album, "deep")))
    }

    // ---- the pace (Q-18-3)

    @Test
    fun `a paced copy is byte-equal and is held to its rate`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        // A clock that only the pace's own sleeps move: the copy "takes" exactly what the cap makes it wait.
        var nowNs = 0L
        var slept = 0L
        val pace = FilePace(100_000, nanoTime = { nowNs }, sleep = { ms -> slept += ms; nowNs += ms * 1_000_000 })
        val result = bed.ops.copy(listOf(src), dest, OpControl(pace = pace), FilesBed.never)

        assertEquals(OpResult.Done(listOf(File(dest, "b.bin").path)), result)
        assertArrayEquals(big, File(dest, "b.bin").readBytes())
        assertEquals(FilesBed.md5(src), FilesBed.md5(File(dest, "b.bin")))
        // 300,000 bytes at 100,000 bytes a second: three seconds, to within the last chunk's rounding.
        assertTrue("slept $slept ms", slept in 2_990..3_000)
    }

    @Test
    fun `an unpaced copy is the pace switch off - null and zero both mean no cap`() {
        assertEquals(null, FilePace.of(null))
        assertEquals(null, FilePace.of(0))
        assertTrue(FilePace.of(8_388_608) != null)
    }

    // ---- the failure reasons the doc names

    @Test
    fun `not enough space - a copy that cannot fit fails before a byte is written`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        bed.free = 1_000
        val result = bed.ops.copy(listOf(src), dest, conflict = FilesBed.never)

        assertEquals(OpResult.Failed("not enough space"), result)
        assertEquals(emptyList<String>(), bed.names(dest))
        assertEquals(listOf("copy 1 files 300000 -> ${dest.path} failed not enough space"), bed.lines)
        assertArrayEquals(big, src.readBytes())
    }

    @Test
    fun `storage removed - a volume pulled mid-copy fails the copy with that reason`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("moved", bed.card)
        var pulled = false
        val result = bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ ->
            if (!pulled) {
                pulled = true
                bed.mounted.remove(bed.card)
                bed.cardRoot.deleteRecursively()
            }
        }), FilesBed.never)

        assertEquals(OpResult.Failed("storage removed"), result)
        assertArrayEquals(big, src.readBytes())
        assertEquals("copy 1 files 300000 -> ${dest.path} failed storage removed", bed.lines.single())
    }

    @Test
    fun `access removed - with the grant gone nothing is written`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        bed.granted = false
        assertEquals(OpResult.Failed("access removed"), bed.ops.copy(listOf(src), dest, conflict = FilesBed.never))
        assertEquals(emptyList<String>(), bed.names(dest))
    }

    @Test
    fun `access removed - a grant revoked mid-copy fails the copy with that reason and leaves no temp`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        val result = bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ ->
            if (bed.granted) {
                bed.granted = false
                // What the revoke does to the write: the folder stops taking it.
                dest.deleteRecursively()
            }
        }), FilesBed.never)

        assertEquals(OpResult.Failed("access removed"), result)
        assertFalse(dest.exists())
        assertArrayEquals(big, src.readBytes())
    }

    // ---- rule 1: only under a mounted volume

    @Test
    fun `a path outside every volume is refused - the app's own files, and a link that resolves to them`() {
        val secret = File(bed.privateDir, "secret.txt").apply { writeText("private") }
        val dest = bed.dir("QA-Files")
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.copy(listOf(secret), dest, conflict = FilesBed.never))

        val link = File(dest, "link.txt")
        Files.createSymbolicLink(link.toPath(), secret.toPath())
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.copy(listOf(link), bed.dir("out"), conflict = FilesBed.never))
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.move(listOf(bed.file("QA-Files/a.txt")), bed.privateDir, conflict = FilesBed.never))

        assertEquals(emptyList<String>(), bed.names(File(bed.root, "out")))
        assertFalse(File(bed.privateDir, "a.txt").exists())
        assertEquals("private", secret.readText())
        assertTrue(File(bed.root, "QA-Files/a.txt").exists())
    }

    @Test
    fun `a link inside a copied folder is not followed and not copied`() {
        val secret = File(bed.privateDir, "secret.txt").apply { writeText("private") }
        bed.file("QA-Files/album/a.txt")
        Files.createSymbolicLink(File(bed.root, "QA-Files/album/link.txt").toPath(), secret.toPath())
        val dest = bed.dir("Copies")
        bed.ops.copy(listOf(File(bed.root, "QA-Files/album")), dest, conflict = FilesBed.never)
        assertEquals(listOf("a.txt"), bed.names(File(dest, "album")))
    }

    // ---- rename and new folder

    @Test
    fun `rename - the file takes the new name, the scan sees both names and Recent follows`() {
        val src = bed.file("QA-Files/b.bin", big)
        bed.recent.add(src.path, FilePaths.PRIMARY)
        bed.lines.clear()
        val result = bed.ops.rename(src, "c.bin")

        val renamed = File(src.folder, "c.bin")
        assertEquals(OpResult.Done(listOf(renamed.path)), result)
        assertEquals(listOf("c.bin"), bed.names(src.folder))
        assertEquals(listOf("rename ${src.path} -> c.bin: ok"), bed.lines)
        assertEquals(listOf(src.path, renamed.path), bed.scanned)
        assertEquals(listOf(renamed.path), bed.recent.all().map { it.path })
    }

    @Test
    fun `rename - a name that is taken, or is no name at all, is refused and nothing moves`() {
        val src = bed.file("QA-Files/b.bin", big)
        val other = bed.file("QA-Files/c.bin", "other".toByteArray())
        assertEquals(OpResult.Failed("the name is taken"), bed.ops.rename(src, "c.bin"))
        for (bad in listOf("", "  ", ".", "..", "a/b", "x".repeat(256))) {
            assertEquals(bad, OpResult.Failed("not a name a file can have"), bed.ops.rename(src, bad))
        }
        assertEquals("other", other.readText())
        assertArrayEquals(big, src.readBytes())
        assertEquals("rename ${src.path} -> c.bin: failed the name is taken", bed.lines.first())
    }

    @Test
    fun `new folder - made in the folder, and refused when the name is taken`() {
        val parent = bed.dir("QA-Files")
        assertEquals(OpResult.Done(listOf(File(parent, "n").path)), bed.ops.newFolder(parent, "n"))
        assertTrue(File(parent, "n").isDirectory)
        assertEquals(OpResult.Failed("the name is taken"), bed.ops.newFolder(parent, "n"))
        assertEquals(listOf("new folder ${File(parent, "n").path}: ok", "new folder ${File(parent, "n").path}: failed the name is taken"), bed.lines)
    }
}

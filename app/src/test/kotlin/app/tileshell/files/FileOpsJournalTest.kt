package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 18, r3 D4 / E18: the operations journal and the sweep, on a temp dir. */
class FileOpsJournalTest {
    private val bed = FilesBed()
    private val big = FilesBed.bytes(300_000)
    private val journalFile: File get() = File(bed.privateDir, "files-ops.json")

    @After fun tearDown() = bed.close()

    /** A copy "killed" mid-way: its temp is left as the dead process left it, journalled, and the journal object is gone with the process. */
    private fun killedCopy(dest: File, volume: FileVolume, name: String = "big.bin"): File {
        val dying = bed.newJournal()
        val opId = dying.newOpId()
        val temp = File(dest, FilePaths.partName(name, opId)).apply { parentFile!!.mkdirs(); writeBytes(big) }
        dying.put(opId, temp.path, volume.uuid)
        return temp
    }

    @Test
    fun `the journal names the running operation's temp and its volume, and is empty when the operation ends`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("moved", bed.card)
        var during = emptyList<OpsJournal.Entry>()
        var onDisk = ""
        bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ ->
            if (during.isEmpty()) { during = bed.journal.entries(); onDisk = journalFile.readText() }
        }), FilesBed.never)

        val entry = during.single()
        assertTrue(entry.path, entry.path.startsWith(dest.path + "/.b.bin.") && entry.path.endsWith(".part"))
        assertEquals(bed.card.uuid, entry.volumeUuid)
        assertTrue(onDisk, onDisk.contains(entry.path))
        assertTrue(bed.journal.entries().isEmpty())
        assertEquals("""{"ops":[]}""", journalFile.readText())
        // Temp-and-rename: no temp of the journal's own is left.
        assertEquals(listOf("files-ops.json"), bed.names(bed.privateDir))
    }

    @Test
    fun `the sweep removes only journalled temps - a part file the journal does not name stays`() {
        val dest = bed.dir("QA-Files/sub")
        val journalled = killedCopy(dest, bed.primary)
        val stranger = File(dest, ".someone-else.xyz.part").apply { writeText("not ours to delete") }
        val userFile = bed.file("QA-Files/sub/keep.bin", big)

        // The process after the restart.
        assertEquals(1, bed.newJournal().sweep(bed.mounted))

        assertFalse(journalled.exists())
        assertTrue(stranger.exists())
        assertTrue(userFile.exists())
        assertEquals(listOf("sweep: removed 1"), bed.lines)
        assertTrue(bed.newJournal().entries().isEmpty())
    }

    @Test
    fun `the sweep never removes a bin path - not the bin, not a file in it, however the journal names them`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", big))
        val bin = RecycleBin.binDir(bed.primary)
        // A binned file that happens to have a temp's name.
        val binned = File(bin, ".1000-0-x.abc.part").apply { writeText("a binned file") }
        val before = bed.names(bin)
        val indexBefore = File(bin, ".index.json").readText()

        val forged = bed.newJournal()
        forged.put("op1", bin.path, bed.primary.uuid)
        forged.put("op2", binned.path, bed.primary.uuid)
        forged.put("op3", File(bin, "1000-0-a.txt").path, bed.primary.uuid)
        forged.put("op4", "${bed.primary.root}/QA-Files/../.Tessera/bin/.1000-0-x.abc.part", bed.primary.uuid)
        forged.put("op5", "${bed.primary.root}/.Tessera", bed.primary.uuid)
        bed.lines.clear()

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertEquals(before, bed.names(bin))
        assertEquals(indexBefore, File(bin, ".index.json").readText())
        assertEquals("a binned file", binned.readText())
        assertEquals(listOf("sweep: removed 0"), bed.lines)
    }

    @Test
    fun `the sweep removes only temps - a journal naming a user's file or a path outside its volume deletes nothing`() {
        val userFile = bed.file("QA-Files/keep.bin", big)
        val secret = File(bed.privateDir, ".secret.abc.part").apply { writeText("private") }
        val onCard = bed.file(".x.abc.part", big, bed.card)
        val forged = bed.newJournal()
        forged.put("op1", userFile.path, bed.primary.uuid)
        forged.put("op2", userFile.folder.path, bed.primary.uuid)
        forged.put("op3", secret.path, bed.primary.uuid)
        // A temp's name on the card, journalled as the primary volume's: not under that volume.
        forged.put("op4", onCard.path, bed.primary.uuid)

        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertTrue(userFile.exists())
        assertTrue(secret.exists())
        assertTrue(onCard.exists())
    }

    @Test
    fun `the sweep waits for an unmounted volume and removes its temp when it mounts again`() {
        val temp = killedCopy(bed.dir("moved", bed.card), bed.card)
        bed.mounted.remove(bed.card)

        val restarted = bed.newJournal()
        assertEquals(0, restarted.sweep(bed.mounted))
        assertTrue(temp.exists())
        assertEquals(listOf(temp.path), restarted.entries().map { it.path })

        bed.mounted.add(bed.card)
        assertEquals(1, restarted.sweep(bed.mounted))
        assertFalse(temp.exists())
        assertEquals(listOf("sweep: removed 0", "sweep: removed 1"), bed.lines)
    }

    @Test
    fun `the sweep removes a killed extract's temp folder with everything in it`() {
        val dying = bed.newJournal()
        val opId = dying.newOpId()
        val temp = File(bed.dir("QA-Files/zips"), FilePaths.extractName("qa.zip", opId))
        File(temp, "dir/two.bin").apply { parentFile!!.mkdirs(); writeBytes(big) }
        dying.put(opId, temp.path, bed.primary.uuid)
        assertTrue(temp.name, Regex("""\.qa\.zip\.[0-9a-z]+\.extract""").matches(temp.name))

        assertEquals(1, bed.newJournal().sweep(bed.mounted))
        assertFalse(temp.exists())
        assertEquals(emptyList<String>(), bed.names(File(bed.root, "QA-Files/zips")))
    }

    @Test
    fun `the sweep leaves a running operation's temp alone`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        var swept = -1
        val result = bed.ops.copy(listOf(src), dest, OpControl(progress = { _, _ ->
            // A volume mounts while the copy runs: the same process's journal sweeps.
            if (swept < 0) swept = bed.journal.sweep(bed.mounted)
        }), FilesBed.never)

        assertEquals(0, swept)
        assertEquals(OpResult.Done(listOf(File(dest, "b.bin").path)), result)
        assertEquals(listOf("b.bin"), bed.names(dest))
    }

    @Test
    fun `an unreadable journal names nothing, so nothing is swept`() {
        val temp = killedCopy(bed.dir("QA-Files/sub"), bed.primary)
        journalFile.writeText("not json at all")
        assertEquals(0, bed.newJournal().sweep(bed.mounted))
        assertTrue(temp.exists())
    }

    @Test
    fun `a cancelled operation ends its own journal entry, so the sweep finds nothing to do`() {
        val src = bed.file("QA-Files/b.bin", big)
        val dest = bed.dir("QA-Files/sub")
        bed.ops.copy(listOf(src), dest, OpControl(stopped = { StopReason.CANCEL }), FilesBed.never)
        assertTrue(bed.journal.entries().isEmpty())
        assertEquals(0, bed.newJournal().sweep(bed.mounted))
    }

    @Test
    fun `operation ids are letters and digits only and never repeat`() {
        val ids = (1..500).map { bed.journal.newOpId() }
        assertEquals(500, ids.toSet().size)
        assertTrue(ids.all { Regex("[0-9a-z]+").matches(it) })
    }
}

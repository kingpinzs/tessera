package app.tileshell.files

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 18, T18-1 / r3 D3 / E18: the Recycle Bin through the write layer, on a temp dir. */
class FileOpsBinTest {
    private val bed = FilesBed()
    private val bytes = FilesBed.bytes(4_000)
    private val bin: File get() = RecycleBin.binDir(bed.primary)
    private val index: File get() = File(bin, ".index.json")

    @After fun tearDown() = bed.close()

    private fun records(volume: FileVolume = bed.primary): List<Map<String, Any?>> =
        MiniJson.parse(File(RecycleBin.binDir(volume), ".index.json").readText()).jsonObject()!!["records"].jsonArray().map { it.jsonObject()!! }

    /** The bin's files, without its marker and its index. */
    private fun binned(volume: FileVolume = bed.primary): List<String> = bed.names(RecycleBin.binDir(volume)) - RecycleBin.RESERVED

    // ---- delete

    @Test
    fun `delete - the file goes to its volume's bin under ms-seq-name, with the marker and its original path on record`() {
        val file = bed.file("QA-Files/c.bin", bytes)
        bed.recent.add(file.path, FilePaths.PRIMARY)
        bed.lines.clear()
        assertEquals(OpResult.Done(), bed.ops.binDelete(file))

        assertFalse(file.exists())
        assertEquals(listOf(".index.json", ".nomedia", "1000-0-c.bin"), bed.names(bin))
        assertArrayEquals(bytes, File(bin, "1000-0-c.bin").readBytes())
        val record = records().single()
        assertEquals(mapOf("bin" to "1000-0-c.bin", "path" to file.path, "deletedAt" to 1000L, "size" to 4000L), record)
        // The first delete makes the bin (as it does after `rm -r .Tessera`), then the delete's own line; Recent drops the file.
        assertEquals(listOf("bin index ${bed.primary.root}: rebuilt (bin folder missing)", "recent remove ${file.path}", "bin delete ${file.path}: ok"), bed.lines)
        assertEquals(listOf(file.path), bed.scanned)
        assertTrue(bed.recent.all().isEmpty())
        assertFalse(File(bin, ".index.json.tmp").exists())
    }

    @Test
    fun `the same-millisecond bin pair - two bin files, neither overwritten, each restoring to its own path`() {
        val one = bed.file("QA-Files/b.bin", "the first".toByteArray())
        val two = bed.file("QA-Files/sub/b.bin", "the second".toByteArray())
        // The clock does not move between the two deletes.
        assertEquals(OpResult.Done(), bed.ops.binDelete(one))
        assertEquals(OpResult.Done(), bed.ops.binDelete(two))

        assertEquals(listOf("1000-0-b.bin", "1000-1-b.bin"), binned())
        assertEquals("the first", File(bin, "1000-0-b.bin").readText())
        assertEquals("the second", File(bin, "1000-1-b.bin").readText())
        assertEquals(listOf(one.path, two.path), records().map { it["path"] })

        val entries = bed.ops.bin.list(bed.primary)
        assertEquals(listOf("b.bin", "b.bin"), entries.map { it.name })
        entries.forEach { assertEquals(OpResult.Done(listOf(it.originalPath!!)), bed.ops.binRestore(it, FilesBed.never)) }
        assertEquals("the first", one.readText())
        assertEquals("the second", two.readText())
        assertEquals(emptyList<String>(), binned())
    }

    @Test
    fun `the same-millisecond bin pair - a name already in the bin under another case is never renamed onto`() {
        // What FAT would call the same file: the name is taken whatever its case. The deleted file's own name holds
        // capitals, so its first bin name differs from the one there ONLY by case (a lower-case name would compare
        // equal to the lower-cased "taken" set whether or not the comparison folds case — the GATE review's mutant).
        bin.mkdirs()
        File(bin, "1000-0-b.BIN").writeText("already here")
        val file = bed.file("QA-Files/B.bin", "new".toByteArray())
        assertEquals(OpResult.Done(), bed.ops.binDelete(file))
        assertEquals(listOf("1000-0-b.BIN", "1000-1-B.bin"), binned())
        assertEquals("already here", File(bin, "1000-0-b.BIN").readText())
        assertEquals("new", File(bin, "1000-1-B.bin").readText())
    }

    @Test
    fun `two records naming one bin file under two cases - the second is no record, so no file is restored twice over`() {
        // On a case-folding volume both records are the same file; here the twin is a file of its own with no record.
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        File(bin, "1000-0-A.TXT").writeText("the twin")
        val twice = listOf(
            mapOf("bin" to "1000-0-a.txt", "path" to File(bed.root, "QA-Files/a.txt").path, "deletedAt" to 1000L, "size" to 4000L),
            mapOf("bin" to "1000-0-A.TXT", "path" to File(bed.root, "QA-Files/elsewhere.txt").path, "deletedAt" to 1000L, "size" to 8L),
        )
        index.writeText(MiniJson.write(mapOf("version" to 1, "records" to twice)))

        val rows = bed.ops.bin.list(bed.primary)
        assertEquals(listOf("a.txt" to true, "1000-0-A.TXT" to false), rows.map { it.name to it.indexed })
        assertEquals(listOf("1000-0-a.txt"), records().map { it["bin"] })
    }

    @Test
    fun `the 255-byte name - the bin name fits 255 bytes, keeps the extension, and restore gives the full name back`() {
        // Two-byte characters: the cut must fall between characters, never inside one.
        val name = "é".repeat(125) + ".bin"
        val exact = "n".repeat(251) + ".bin"
        assertEquals(255, FilePaths.utf8(exact))
        for (original in listOf(exact, name)) {
            val file = bed.file("QA-Files/$original", bytes)
            assertEquals(OpResult.Done(), bed.ops.binDelete(file))
            val binName = binned().single()
            assertTrue(binName, binName.startsWith("1000-0-") && binName.endsWith(".bin"))
            assertTrue("${FilePaths.utf8(binName)} bytes", FilePaths.utf8(binName) <= 255)
            assertEquals("bin delete ${file.path}: ok", bed.lines.last())

            val entry = bed.ops.bin.list(bed.primary).single()
            assertEquals(original, entry.name)
            assertEquals(OpResult.Done(listOf(file.path)), bed.ops.binRestore(entry, FilesBed.never))
            assertArrayEquals(bytes, file.readBytes())
            file.delete()
        }
    }

    @Test
    fun `an index that cannot be written - the bin path is a FILE, so the delete fails and the file stays`() {
        File(bed.root, ".Tessera").writeText("in the way")
        val file = bed.file("QA-Files/a.txt", bytes)
        val result = bed.ops.binDelete(file)

        assertEquals(OpResult.Failed("the bin folder cannot be made"), result)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf("bin delete ${file.path}: failed the bin folder cannot be made"), bed.lines)
        assertTrue(bed.scanned.isEmpty())
    }

    @Test
    fun `an index that cannot be written - the index itself cannot be replaced, so the delete fails and the file stays`() {
        // Something that cannot be renamed over stands where the index goes.
        File(bin, ".index.json/blocker").apply { parentFile!!.mkdirs(); writeText("x") }
        File(bin, ".nomedia").createNewFile()
        val file = bed.file("QA-Files/a.txt", bytes)
        val result = bed.ops.binDelete(file)

        assertTrue(result.toString(), result is OpResult.Failed && result.reason.startsWith("the bin index cannot be written"))
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(emptyList<String>(), binned() - ".index.json")
        assertTrue(bed.lines.last(), bed.lines.last().startsWith("bin delete ${file.path}: failed the bin index cannot be written"))
    }

    @Test
    fun `delete - a file on the card lands in the card's bin and restores onto the card`() {
        val file = bed.file("Music/song.mp3", bytes, bed.card)
        assertEquals(OpResult.Done(), bed.ops.binDelete(file))
        assertEquals(listOf("1000-0-song.mp3"), binned(bed.card))
        assertFalse(bin.exists())
        assertEquals(OpResult.Done(listOf(file.path)), bed.ops.binRestore(bed.ops.bin.list(bed.card).single(), FilesBed.never))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `delete - a folder goes to the bin whole and comes back whole`() {
        bed.file("QA-Files/album/a.txt", "a".toByteArray())
        bed.file("QA-Files/album/deep/b.bin", bytes)
        val album = File(bed.root, "QA-Files/album")
        assertEquals(OpResult.Done(), bed.ops.binDelete(album))
        val entry = bed.ops.bin.list(bed.primary).single()
        assertTrue(entry.isDirectory)
        assertEquals(4_001L, entry.size)
        assertEquals(setOf(File(album, "a.txt").path, File(album, "deep/b.bin").path), bed.scanned.toSet())
        bed.ops.binRestore(entry, FilesBed.never)
        assertArrayEquals(bytes, File(album, "deep/b.bin").readBytes())
    }

    @Test
    fun `delete - the bin itself, a volume's root and a path outside every volume are refused`() {
        val file = bed.file("QA-Files/a.txt")
        bed.ops.binDelete(file)
        assertEquals(OpResult.Failed("not a file Files deletes"), bed.ops.binDelete(bin))
        assertEquals(OpResult.Failed("not a file Files deletes"), bed.ops.binDelete(File(bin, "1000-0-a.txt")))
        assertEquals(OpResult.Failed("a volume cannot be deleted"), bed.ops.binDelete(bed.root))
        val secret = File(bed.privateDir, "secret.txt").apply { writeText("private") }
        assertEquals(OpResult.Failed("outside shared storage"), bed.ops.binDelete(secret))
        assertTrue(secret.exists())
        assertEquals(listOf("1000-0-a.txt"), binned())
    }

    // ---- index damage

    @Test
    fun `index missing - the bin lists its files by bin name, says rebuilt, and restores to Download Restored`() {
        val file = bed.file("QA-Files/a.txt", bytes)
        bed.ops.binDelete(file)
        index.delete()
        bed.lines.clear()

        val entry = bed.ops.bin.list(bed.primary).single()
        assertEquals("1000-0-a.txt", entry.name)
        assertNull(entry.originalPath)
        assertFalse(entry.indexed)
        assertEquals(4_000L, entry.size)
        assertEquals(listOf("bin index ${bed.primary.root}: rebuilt (index missing)"), bed.lines)

        val restored = File(bed.root, "Download/Restored/1000-0-a.txt")
        assertEquals(OpResult.Done(listOf(restored.path)), bed.ops.binRestore(entry, FilesBed.never))
        assertArrayEquals(bytes, restored.readBytes())
        assertFalse(file.exists())
        assertEquals("bin restore ${restored.path}: ok", bed.lines.last())
        // Rebuilt once: the next read is an ordinary one.
        assertTrue(bed.ops.bin.list(bed.primary).isEmpty())
        assertEquals("bin index ${bed.primary.root}: 0 entries", bed.lines.last())
    }

    @Test
    fun `index unreadable - the bin lists its files by bin name and says rebuilt, and the next delete still works`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        index.writeText("{ this is not json")
        bed.lines.clear()

        assertEquals(listOf("1000-0-a.txt"), bed.ops.bin.list(bed.primary).map { it.name })
        assertEquals(listOf("bin index ${bed.primary.root}: rebuilt (index unreadable)"), bed.lines)

        bed.now = 2_000
        val next = bed.file("QA-Files/b.bin", bytes)
        assertEquals(OpResult.Done(), bed.ops.binDelete(next))
        assertEquals(listOf(next.path), records().map { it["path"] })
        assertEquals(listOf("b.bin", "1000-0-a.txt"), bed.ops.bin.list(bed.primary).map { it.name })
    }

    @Test
    fun `a record whose file is gone is dropped on the next read`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        bed.now = 2_000
        val kept = bed.file("QA-Files/b.bin", bytes)
        bed.ops.binDelete(kept)
        // Another app deletes one binned file.
        File(bin, "1000-0-a.txt").delete()
        bed.lines.clear()

        assertEquals(listOf("b.bin"), bed.ops.bin.list(bed.primary).map { it.name })
        assertEquals(listOf("bin index ${bed.primary.root}: 1 entries"), bed.lines)
        assertEquals(listOf(kept.path), records().map { it["path"] })
    }

    @Test
    fun `bin folder missing - the next delete makes it again with its line`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        File(bed.root, ".Tessera").deleteRecursively()
        bed.lines.clear()
        assertTrue(bed.ops.bin.list(bed.primary).isEmpty())
        assertEquals(RecycleBin.Stats(0, 0), bed.ops.bin.stats(bed.primary))

        val file = bed.file("QA-Files/b.bin", bytes)
        assertEquals(OpResult.Done(), bed.ops.binDelete(file))
        assertEquals(listOf("bin index ${bed.primary.root}: 0 entries", "bin index ${bed.primary.root}: rebuilt (bin folder missing)", "bin delete ${file.path}: ok"), bed.lines)
        assertEquals(listOf(".index.json", ".nomedia", "1000-0-b.bin"), bed.names(bin))
    }

    @Test
    fun `a bin file with no record lists by its bin name and restores to Download Restored`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        File(bin, "copied-in.txt").writeText("no record")

        val entries = bed.ops.bin.list(bed.primary)
        assertEquals(listOf("a.txt", "copied-in.txt"), entries.map { it.name })
        val loose = entries.last()
        assertFalse(loose.indexed)
        assertEquals(OpResult.Done(listOf(File(bed.root, "Download/Restored/copied-in.txt").path)), bed.ops.binRestore(loose, FilesBed.never))
        assertEquals("no record", File(bed.root, "Download/Restored/copied-in.txt").readText())
        assertEquals(listOf("1000-0-a.txt"), binned())
    }

    @Test
    fun `the index is not trusted - a record pointing outside its volume restores to Download Restored`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        val outside = File(bed.privateDir, "planted.txt")
        for (path in listOf(outside.path, "${bed.primary.root}/../../data/data/app.tileshell/files/planted.txt", "${bed.primary.root}/.Tessera/bin/.nomedia", File(bed.cardRoot, "x.txt").path)) {
            index.writeText(index.readText().replace(Regex("\"path\":\"[^\"]*\""), "\"path\":${MiniJson.write(path)}"))
            val entry = bed.ops.bin.list(bed.primary).single()
            val result = bed.ops.binRestore(entry, FilesBed.never)
            val restored = File(bed.root, "Download/Restored/1000-0-a.txt")
            assertEquals(path, OpResult.Done(listOf(restored.path)), result)
            assertFalse(outside.exists())
            assertFalse(File(bed.cardRoot, "x.txt").exists())
            // Back into the bin for the next path.
            restored.renameTo(File(bin, "1000-0-a.txt"))
            index.writeText(MiniJson.write(mapOf("version" to 1, "records" to listOf(mapOf("bin" to "1000-0-a.txt", "path" to "x", "deletedAt" to 1000L, "size" to 4000L)))))
        }
    }

    // ---- restore

    @Test
    fun `restore - the original folder is made again when it is gone`() {
        val file = bed.file("QA-Files/sub/deep/b.bin", bytes)
        bed.ops.binDelete(file)
        File(bed.root, "QA-Files/sub").deleteRecursively()
        bed.scanned.clear()

        assertEquals(OpResult.Done(listOf(file.path)), bed.ops.binRestore(bed.ops.bin.list(bed.primary).single(), FilesBed.never))
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf(file.path), bed.scanned)
        assertEquals("bin restore ${file.path}: ok", bed.lines.last())
        assertTrue(records().isEmpty())
    }

    @Test
    fun `restore over an existing name - replace, keep both and skip each do what they say`() {
        fun binOne(): RecycleBin.Entry {
            val file = bed.file("QA-Files/c.bin", bytes)
            bed.ops.binDelete(file)
            file.writeText("the newer one")
            return bed.ops.bin.list(bed.primary).single()
        }
        val file = File(bed.root, "QA-Files/c.bin")

        // skip: the binned file stays binned, the newer one stays, and no line is written.
        var entry = binOne()
        bed.lines.clear()
        val asked = mutableListOf<File>()
        assertEquals(OpResult.Done(skipped = 1), bed.ops.binRestore(entry) { asked += it; Conflict.SKIP })
        assertEquals(listOf(file), asked)
        assertEquals("the newer one", file.readText())
        assertEquals(listOf("1000-0-c.bin"), binned())
        assertTrue(bed.lines.toString(), bed.lines.isEmpty())

        // keep both: back as `c (2).bin`.
        assertEquals(OpResult.Done(listOf(File(bed.root, "QA-Files/c (2).bin").path)), bed.ops.binRestore(entry, FilesBed.always(Conflict.KEEP_BOTH)))
        assertEquals("the newer one", file.readText())
        assertArrayEquals(bytes, File(bed.root, "QA-Files/c (2).bin").readBytes())
        assertEquals(emptyList<String>(), binned())

        // replace: the binned file takes the name back, and the newer one takes its place in the bin (GATE fix H3).
        file.delete()
        bed.now = 2_000
        entry = binOne()
        bed.now = 3_000
        assertEquals(OpResult.Done(listOf(file.path)), bed.ops.binRestore(entry, FilesBed.always(Conflict.REPLACE)))
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf("3000-0-c.bin"), binned())
        assertEquals("the newer one", File(bin, "3000-0-c.bin").readText())
    }

    @Test
    fun `restore - the original folder is now a FILE of that name, so the conflict is asked`() {
        val file = bed.file("QA-Files/sub/b.bin", bytes)
        bed.ops.binDelete(file)
        val sub = File(bed.root, "QA-Files/sub")
        sub.deleteRecursively()
        sub.writeText("a file where the folder was")
        val entry = bed.ops.bin.list(bed.primary).single()

        val asked = mutableListOf<File>()
        assertEquals(OpResult.Done(skipped = 1), bed.ops.binRestore(entry) { asked += it; Conflict.SKIP })
        assertEquals(listOf(sub), asked)
        assertEquals(listOf("1000-0-b.bin"), binned())

        val beside = File(bed.root, "QA-Files/sub (2)/b.bin")
        assertEquals(OpResult.Done(listOf(beside.path)), bed.ops.binRestore(entry, FilesBed.always(Conflict.KEEP_BOTH)))
        assertArrayEquals(bytes, beside.readBytes())
        assertEquals("a file where the folder was", sub.readText())
    }

    // ---- purge, empty, the published reader

    @Test
    fun `delete permanently - the file and its record are gone, and only that one`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        bed.now = 2_000
        val kept = bed.file("QA-Files/b.bin", bytes)
        bed.ops.binDelete(kept)
        val gone = bed.ops.bin.list(bed.primary).first { it.name == "a.txt" }

        assertEquals(OpResult.Done(), bed.ops.binPurge(gone))
        assertEquals(listOf("2000-0-b.bin"), binned())
        assertEquals(listOf(kept.path), records().map { it["path"] })
        assertEquals("bin purge ${File(bed.root, "QA-Files/a.txt").path}: ok", bed.lines.last())
        assertFalse(File(bed.root, "QA-Files/a.txt").exists())
    }

    @Test
    fun `delete permanently - a bin name that is not one plain bin file is refused`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        val victim = bed.file("QA-Files/keep.txt")
        for (name in listOf("../../QA-Files/keep.txt", ".nomedia", ".index.json", "..", "")) {
            val forged = RecycleBin.Entry(bed.primary, name, name, null, null, 0, false)
            assertEquals(name, OpResult.Failed("not a bin entry"), bed.ops.binPurge(forged))
            assertEquals(name, OpResult.Failed("not a bin entry"), bed.ops.binRestore(forged, FilesBed.never))
        }
        assertTrue(victim.exists())
        assertEquals(listOf(".index.json", ".nomedia", "1000-0-a.txt"), bed.names(bin))
    }

    @Test
    fun `empty - the bin holds only its marker and an index of zero records, and the reader counts what was there`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        bed.now = 2_000
        bed.ops.binDelete(bed.file("QA-Files/b.bin", FilesBed.bytes(1_000)))
        File(bin, "loose.txt").writeText("12345")
        bed.ops.binDelete(bed.file("Music/song.mp3", bytes, bed.card))

        // The published reader (T18-9): per volume, the entry count and the bytes.
        assertEquals(RecycleBin.Stats(3, 5_005), bed.ops.bin.stats(bed.primary))
        assertEquals(RecycleBin.Stats(1, 4_000), bed.ops.bin.stats(bed.card))

        assertEquals(OpResult.Done(), bed.ops.binEmpty(bed.primary))
        assertEquals(listOf(".index.json", ".nomedia"), bed.names(bin))
        assertTrue(records().isEmpty())
        assertEquals("bin empty ${bed.primary.root}: ok", bed.lines.last())
        assertEquals(RecycleBin.Stats(0, 0), bed.ops.bin.stats(bed.primary))
        // The other volume's bin is untouched.
        assertEquals(RecycleBin.Stats(1, 4_000), bed.ops.bin.stats(bed.card))
    }

    @Test
    fun `a pulled volume takes its bin with it - its entries cannot be restored or emptied`() {
        bed.ops.binDelete(bed.file("Music/song.mp3", bytes, bed.card))
        val entry = bed.ops.bin.list(bed.card).single()
        bed.mounted.remove(bed.card)
        assertEquals(OpResult.Failed("storage removed"), bed.ops.binRestore(entry, FilesBed.never))
        assertEquals(OpResult.Failed("storage removed"), bed.ops.binEmpty(bed.card))
        assertEquals(listOf("1000-0-song.mp3"), binned(bed.card))
    }

    @Test
    fun `the index names the bin name as a string and nothing else is in the bin folder`() {
        bed.ops.binDelete(bed.file("QA-Files/a.txt", bytes))
        assertEquals("1000-0-a.txt", records().single().jsonString("bin"))
        assertEquals(listOf(".index.json", ".nomedia", "1000-0-a.txt"), bed.names(bin))
    }
}

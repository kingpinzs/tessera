package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Phase 18, T18-2 / r3 D6 / E18: zip through the write layer, on the fixtures `docs/plan/qa/phase-18/scripts/
 * make_zips.py` writes — the class runs that script itself (python 3 and `/usr/bin/zip` on the host) into a temp dir,
 * leaving out only `qa-big.zip` (200 MB of urandom for the device's progress and cancel rows).
 */
class FileOpsZipTest {
    private val bed = FilesBed()
    private val zips: File = bed.dir("QA-Files/zips")

    @After fun tearDown() = bed.close()

    /** The fixture, copied to the test's own `QA-Files/zips/`. */
    private fun zip(name: String): File = File(fixtures, name).copyTo(File(zips, name))

    private fun opened(file: File): ZipArchive = (bed.ops.zipOpen(file) as ZipOpen.Opened).archive

    /** Every file under [dir], relative to it (`find`). */
    private fun tree(dir: File): List<String> = dir.walkTopDown().filter { it != dir }.map { it.relativeTo(dir).path + if (it.isDirectory) "/" else "" }.sorted().toList()

    // ---- open as a folder

    @Test
    fun `open - qa zip is a virtual root listing its three entries with the central directory's sizes`() {
        val archive = opened(zip("qa.zip"))
        archive.use {
            assertEquals(listOf("zip open ${File(zips, "qa.zip").path}: 3 entries"), bed.lines)
            val root = archive.list()
            assertEquals(listOf("one.txt", "dir", "ü-name.txt"), root.map { it.name })
            assertEquals(listOf(false, true, false), root.map { it.isDirectory })
            assertEquals(1024L, root[0].size)
            // E13: `one.txt` reads "1.00 KB", the folder a date alone.
            val zone = ZoneId.systemDefault()
            assertEquals("1.00 KB 1/2/2026", FileListing.detail(FileRow(root[0].name, false, root[0].size, root[0].modifiedMs), zone))
            assertEquals(listOf(ZipRow("two.bin", "dir/two.bin", false, 307_200, root[0].modifiedMs, archive.entries[1])), archive.list("dir"))
            assertEquals("dir/two.bin", archive.row("dir/two.bin")?.path)
            assertEquals(null, archive.row("dir/nothing"))
            // The data comes back whole through the platform's ZipFile.
            assertEquals(md5s.getValue("qa.zip:ü-name.txt"), FilesBed.md5(archive.input(root[2].entry!!).readBytes()))
        }
    }

    @Test
    fun `open - a corrupt zip fails with its line and nothing is thrown`() {
        val file = zip("qa-corrupt.zip")
        val result = bed.ops.zipOpen(file)
        assertEquals(ZipOpen.Failed("not a zip (no end record)"), result)
        assertEquals(listOf("zip open ${file.path}: failed not a zip (no end record)"), bed.lines)
        assertEquals(OpResult.Failed("the zip can't be opened"), bed.ops.zipExtract(file, conflict = FilesBed.never))
        assertEquals(listOf("qa-corrupt.zip"), bed.names(zips))
    }

    @Test
    fun `open - names without the UTF-8 flag are read as CP437, and with it as UTF-8`() {
        opened(zip("qa-cp437.zip")).use { assertEquals(listOf("café.txt"), it.list().map { row -> row.name }) }
        opened(zip("qa.zip")).use { assertEquals("ü-name.txt", it.entries[2].name) }
        // Extracted under the decoded name, too.
        bed.ops.zipExtract(File(zips, "qa-cp437.zip"), conflict = FilesBed.never)
        assertEquals("cafe\n", File(zips, "qa-cp437/café.txt").readText())
    }

    @Test
    fun `open - a zip64 archive of 70,000 entries lists its first and its last`() {
        val file = zip("qa-zip64.zip")
        val started = System.nanoTime()
        opened(file).use { archive ->
            val rows = archive.list()
            val ms = (System.nanoTime() - started) / 1_000_000
            assertEquals(70_000, rows.size)
            assertEquals("e00001.txt", rows.first().name)
            assertEquals("e70000.txt", rows.last().name)
            assertEquals("zip open ${file.path}: 70000 entries", bed.lines.single())
            assertTrue("opened and listed in $ms ms", ms < 5_000)
        }
    }

    // ---- extract

    @Test
    fun `extract - qa zip lands in a folder of its base name with every entry's md5 as recorded`() {
        val file = zip("qa.zip")
        val result = bed.ops.zipExtract(file, conflict = FilesBed.never)

        val out = File(zips, "qa")
        assertEquals(OpResult.Done(listOf(out.path)), result)
        assertEquals(listOf("dir/", "dir/two.bin", "one.txt", "ü-name.txt"), tree(out))
        for (name in listOf("one.txt", "dir/two.bin", "ü-name.txt")) assertEquals(name, md5s.getValue("qa.zip:$name"), FilesBed.md5(File(out, name)))
        assertEquals(1024L, File(out, "one.txt").length())
        assertEquals(307_200L, File(out, "dir/two.bin").length())
        assertEquals(listOf("qa", "qa.zip"), bed.names(zips))
        assertEquals(listOf("zip open ${file.path}: 3 entries", "zip extract ${file.path} -> ${out.path}: done"), bed.lines)
        assertEquals(setOf(File(out, "one.txt").path, File(out, "dir/two.bin").path, File(out, "ü-name.txt").path), bed.scanned.toSet())
        assertTrue(bed.journal.entries().isEmpty())
    }

    @Test
    fun `extract - through the journalled temp folder, renamed when it is whole`() {
        val file = zip("qa.zip")
        var temp = ""
        var journalled = ""
        bed.ops.zipExtract(file, OpControl(progress = { _, _ ->
            if (temp.isEmpty()) {
                temp = bed.names(zips).first { it != "qa.zip" }
                journalled = bed.journal.entries().single().path
            }
        }), FilesBed.never)
        assertTrue(temp, Regex("""\.qa\.zip\.[0-9a-z]+\.extract""").matches(temp))
        assertEquals(File(zips, temp).path, journalled)
        assertEquals(listOf("qa", "qa.zip"), bed.names(zips))
    }

    @Test
    fun `extract again - keep both gives qa (2), replace merges, skip writes nothing`() {
        val file = zip("qa.zip")
        bed.ops.zipExtract(file, conflict = FilesBed.never)
        val mine = File(zips, "qa/mine.txt").apply { writeText("the user's own") }
        File(zips, "qa/one.txt").writeText("edited")

        val asked = mutableListOf<File>()
        assertEquals(OpResult.Done(skipped = 1), bed.ops.zipExtract(file) { asked += it; Conflict.SKIP })
        assertEquals(listOf(File(zips, "qa")), asked)
        assertEquals("edited", File(zips, "qa/one.txt").readText())

        assertEquals(OpResult.Done(listOf(File(zips, "qa (2)").path)), bed.ops.zipExtract(file, conflict = FilesBed.always(Conflict.KEEP_BOTH)))
        assertEquals(listOf("dir/", "dir/two.bin", "one.txt", "ü-name.txt"), tree(File(zips, "qa (2)")))
        assertEquals("edited", File(zips, "qa/one.txt").readText())

        assertEquals(OpResult.Done(listOf(File(zips, "qa").path)), bed.ops.zipExtract(file, conflict = FilesBed.always(Conflict.REPLACE)))
        assertEquals(md5s.getValue("qa.zip:one.txt"), FilesBed.md5(File(zips, "qa/one.txt")))
        assertEquals("the user's own", mine.readText())
        assertEquals(listOf("qa", "qa (2)", "qa.zip"), bed.names(zips))
    }

    @Test
    fun `zip guard - dot-dot and absolute names are refused while the other entry extracts`() {
        val file = zip("qa-bad.zip")
        val result = bed.ops.zipExtract(file, conflict = FilesBed.never)

        val out = File(zips, "qa-bad")
        assertEquals(OpResult.Done(listOf(out.path)), result)
        // The control: ok.txt IS there — a guard that refuses everything fails here.
        assertEquals("ok\n", File(out, "ok.txt").readText())
        assertEquals(listOf("ok.txt"), tree(out))
        assertEquals(
            listOf(
                "zip open ${file.path}: 3 entries",
                "zip: refused entry ../../evil.txt",
                "zip: refused entry /sdcard/abs.txt",
                "zip extract ${file.path} -> ${out.path}: done",
            ),
            bed.lines,
        )
        // Nothing named evil.txt or abs.txt anywhere the test can see.
        assertEquals(emptyList<String>(), bed.base.walkTopDown().filter { it.name == "evil.txt" || it.name == "abs.txt" }.map { it.path }.toList())
        assertFalse(File("/sdcard/abs.txt").exists())
        // And the virtual root shows only the entry that has a place under it.
        opened(file).use { assertEquals(listOf("ok.txt"), it.list().map { row -> row.name }) }
    }

    @Test
    fun `zip guard - the bomb is stopped at its declared total plus 1 MB and leaves no temp folder`() {
        val file = zip("qa-bomb.zip")
        opened(file).use { assertEquals(1024L, it.entries.single().size) }
        bed.lines.clear()
        val result = bed.ops.zipExtract(file, conflict = FilesBed.never)

        assertEquals(OpResult.Failed("bigger than it declares"), result)
        val stopped = bed.lines.single { it.startsWith("zip: stopped at ") }
        val (written, declared) = Regex("""zip: stopped at (\d+) \(declared (\d+)\)""").matchEntire(stopped)!!.destructured
        assertEquals("1024", declared)
        assertTrue(stopped, written.toLong() in 1..(1024 + 1_048_576))
        // No output folder, no temp folder, no partial file.
        assertEquals(listOf("qa-bomb.zip"), bed.names(zips))
        assertEquals("zip extract ${file.path} -> ${File(zips, "qa-bomb").path}: failed bigger than it declares", bed.lines.last())
        assertTrue(bed.journal.entries().isEmpty())
    }

    @Test
    fun `zip guard - the huge zip is refused before any write when its declared size does not fit`() {
        val file = zip("qa-huge.zip")
        // 2 GB free, 3 GB declared.
        bed.free = 2_147_483_648
        val result = bed.ops.zipExtract(file, conflict = FilesBed.never)

        assertEquals(OpResult.Failed("not enough space"), result)
        assertEquals(
            listOf(
                "zip open ${file.path}: 1 entries",
                "zip: refused (needs 3221225472, free 2147483648)",
                "zip extract ${file.path} -> ${File(zips, "qa-huge").path}: failed not enough space",
            ),
            bed.lines,
        )
        assertEquals(listOf("qa-huge.zip"), bed.names(zips))
        assertTrue(bed.journal.entries().isEmpty())
    }

    @Test
    fun `zip guard - the 50 MB margin - a zip that fits only without it is refused, one that fits with it extracts`() {
        val file = zip("qa.zip")
        val declared = 1024L + 307_200 + md5sizes.getValue("qa.zip:ü-name.txt")
        bed.free = declared + 50L * 1024 * 1024 - 1
        assertEquals(OpResult.Failed("not enough space"), bed.ops.zipExtract(file, conflict = FilesBed.never))
        assertEquals(listOf("qa.zip"), bed.names(zips))
        bed.free = declared + 50L * 1024 * 1024
        assertEquals(OpResult.Done(listOf(File(zips, "qa").path)), bed.ops.zipExtract(file, conflict = FilesBed.never))
    }

    @Test
    fun `zip guard - the encrypted bit is read and nothing is written`() {
        val file = zip("qa-enc.zip")
        assertTrue(ZipCentral.read(file).single().encrypted)
        assertFalse(ZipCentral.read(zip("qa.zip")).any { it.encrypted })

        assertEquals(ZipOpen.Encrypted, bed.ops.zipOpen(file))
        assertEquals(listOf("zip: encrypted ${file.path}"), bed.lines)
        assertEquals(OpResult.Failed("password-protected"), bed.ops.zipExtract(file, conflict = FilesBed.never))
        assertEquals(listOf("qa-enc.zip", "qa.zip"), bed.names(zips))
        assertEquals("zip extract ${file.path} -> ${File(zips, "qa-enc").path}: failed password-protected", bed.lines.last())
    }

    @Test
    fun `zip guard - a symlink entry is written as a regular file holding the link text`() {
        val file = zip("qa-symlink.zip")
        assertEquals(listOf(false, true), ZipCentral.read(file).map { it.isSymlink })
        bed.ops.zipExtract(file, conflict = FilesBed.never)

        val link = File(zips, "qa-symlink/link")
        assertFalse(Files.isSymbolicLink(link.toPath()))
        assertTrue(Files.isRegularFile(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals("/data/data/app.tileshell/files/secret", link.readText())
        assertEquals("ok\n", File(zips, "qa-symlink/ok.txt").readText())
    }

    @Test
    fun `cancel leaving no temp - a cancelled extract leaves no output folder and no extract folder`() {
        val file = zip("qa.zip")
        var chunks = 0
        val result = bed.ops.zipExtract(file, OpControl(progress = { _, _ -> chunks++ }, stopped = { if (chunks >= 2) StopReason.CANCEL else null }), FilesBed.never)

        assertEquals(OpResult.Cancelled, result)
        assertEquals(listOf("qa.zip"), bed.names(zips))
        assertEquals("zip extract ${file.path} -> ${File(zips, "qa").path}: cancelled", bed.lines.last())
        assertTrue(bed.journal.entries().isEmpty())
    }

    @Test
    fun `storage removed - a volume pulled mid-extract fails the extract with that reason`() {
        val onCard = File(fixtures, "qa.zip").copyTo(File(bed.dir("zips", bed.card), "qa.zip"))
        var pulled = false
        val result = bed.ops.zipExtract(onCard, OpControl(progress = { _, _ ->
            if (!pulled) {
                pulled = true
                bed.mounted.remove(bed.card)
                // The mount point is gone and cannot be made again (as `/storage/<UUID>` cannot): a file stands there.
                bed.cardRoot.deleteRecursively()
                bed.cardRoot.writeText("")
            }
        }), FilesBed.never)
        assertEquals(OpResult.Failed("storage removed"), result)
        assertTrue(bed.lines.last(), bed.lines.last().endsWith(": failed storage removed"))
    }

    // ---- the nested zip (r3 D6)

    @Test
    fun `nested zip - copied to the volume's Tessera tmp while it is open, and removed on leaving it`() {
        val outer = opened(zip("qa-nested.zip"))
        val tmp = File(bed.root, ".Tessera/tmp")
        outer.use {
            val row = outer.list().first { it.name == "qa.zip" }
            assertTrue(ZipNames.isZipName(row.name))
            val nested = bed.ops.zipOpenNested(outer, row) as NestedZip.Opened

            assertEquals(tmp, nested.file.folder)
            assertEquals(2, bed.names(tmp).size)
            assertTrue(bed.names(tmp).toString(), bed.names(tmp).first() == ".nomedia" && Regex("""\.qa\.zip\.[0-9a-z]+\.part""").matches(bed.names(tmp).last()))
            assertEquals(FilesBed.md5(File(fixtures, "qa.zip")), FilesBed.md5(nested.file))
            // Journalled for as long as it is open, so a kill leaves it for the sweep.
            assertEquals(listOf(nested.file.path), bed.journal.entries().map { it.path })

            opened(nested.file).use { inner -> assertEquals(listOf("one.txt", "dir", "ü-name.txt"), inner.list().map { r -> r.name }) }

            bed.ops.zipCloseNested(nested)
            assertEquals(listOf(".nomedia"), bed.names(tmp))
            assertTrue(bed.journal.entries().isEmpty())
        }
    }

    @Test
    fun `nested zip - a kill while it is open leaves a copy the sweep removes`() {
        val outer = opened(zip("qa-nested.zip"))
        val nested = outer.use { bed.ops.zipOpenNested(outer, outer.list().first { it.name == "qa.zip" }) as NestedZip.Opened }
        assertTrue(nested.file.exists())
        // The next process: the journal is all it has.
        assertEquals(1, bed.newJournal().sweep(bed.mounted))
        assertEquals(listOf(".nomedia"), bed.names(File(bed.root, ".Tessera/tmp")))
    }

    @Test
    fun `nested zip - refused when its declared size does not fit, with nothing copied`() {
        val outer = opened(zip("qa-nested.zip"))
        bed.lines.clear()
        outer.use {
            bed.free = 1_000_000
            val result = bed.ops.zipOpenNested(outer, outer.list().first { it.name == "qa.zip" })
            assertEquals(NestedZip.Failed("not enough space"), result)
            assertEquals(listOf(".nomedia"), bed.names(File(bed.root, ".Tessera/tmp")))
            assertEquals("zip: refused (needs ${File(fixtures, "qa.zip").length()}, free 1000000)", bed.lines.first())
            assertEquals("zip open ${outer.file.path}/qa.zip: failed not enough space", bed.lines.last())
        }
    }

    // ---- create

    @Test
    fun `create - two items make Archive zip holding exactly those two names`() {
        val a = bed.file("QA-Files/a.txt", "0123456789".toByteArray())
        val b = bed.file("QA-Files/b.bin", FilesBed.bytes(300_000))
        val folder = a.folder
        var temp = ""
        val result = bed.ops.zipCreate(listOf(a, b), folder, OpControl(progress = { _, _ -> if (temp.isEmpty()) temp = bed.names(folder).first { it.startsWith(".") } }), FilesBed.never)

        val archive = File(folder, "Archive.zip")
        assertEquals(OpResult.Done(listOf(archive.path)), result)
        assertTrue(temp, Regex("""\.Archive\.zip\.[0-9a-z]+\.part""").matches(temp))
        assertEquals(listOf("Archive.zip", "a.txt", "b.bin", "zips"), bed.names(folder))
        ZipFile(archive).use { z ->
            assertEquals(listOf("a.txt", "b.bin"), z.entries().toList().map { it.name })
            assertEquals("0123456789", z.getInputStream(z.getEntry("a.txt")).readBytes().decodeToString())
            assertArrayEquals(b.readBytes(), z.getInputStream(z.getEntry("b.bin")).readBytes())
        }
        assertEquals(listOf("zip create 2 files -> ${archive.path}: done"), bed.lines)
        assertEquals(listOf(archive.path), bed.scanned)
    }

    @Test
    fun `create - one item makes its name plus zip, and a folder goes in with everything in it under UTF-8 names`() {
        val a = bed.file("QA-Files/a.txt", "0123456789".toByteArray())
        assertEquals(OpResult.Done(listOf(File(a.folder, "a.txt.zip").path)), bed.ops.zipCreate(listOf(a), a.folder, conflict = FilesBed.never))

        bed.file("QA-Files/album/ü.txt", "u".toByteArray())
        bed.file("QA-Files/album/deep/b.bin", FilesBed.bytes(5_000))
        bed.dir("QA-Files/album/empty")
        val album = File(bed.root, "QA-Files/album")
        bed.ops.zipCreate(listOf(album), album.folder, conflict = FilesBed.never)

        val made = File(album.folder, "album.zip")
        assertEquals(listOf("album/", "album/deep/", "album/deep/b.bin", "album/empty/", "album/ü.txt"), ZipCentral.read(made).map { it.name })
        // Files' own extract reads what Files' own create wrote.
        bed.ops.rename(album, "album-original")
        bed.ops.zipExtract(made, conflict = FilesBed.never)
        assertEquals(tree(File(album.folder, "album-original")), tree(File(album.folder, "album/album")))
    }

    @Test
    fun `create - a taken name asks - keep both is Archive (2) zip - and a cancel leaves no part file`() {
        val a = bed.file("QA-Files/a.txt", "0123456789".toByteArray())
        val b = bed.file("QA-Files/b.bin", FilesBed.bytes(300_000))
        val folder = a.folder
        bed.ops.zipCreate(listOf(a, b), folder, conflict = FilesBed.never)
        assertEquals(OpResult.Done(listOf(File(folder, "Archive (2).zip").path)), bed.ops.zipCreate(listOf(a, b), folder, conflict = FilesBed.always(Conflict.KEEP_BOTH)))

        var chunks = 0
        val cancelled = bed.ops.zipCreate(listOf(b, a), folder, OpControl(progress = { _, _ -> chunks++ }, stopped = { if (chunks >= 2) StopReason.CANCEL else null }), FilesBed.always(Conflict.KEEP_BOTH))
        assertEquals(OpResult.Cancelled, cancelled)
        assertEquals(listOf("Archive (2).zip", "Archive.zip", "a.txt", "b.bin", "zips"), bed.names(folder))
        assertTrue(bed.lines.last(), bed.lines.last().endsWith("Archive (3).zip: cancelled"))
    }

    // ---- the pure parts

    @Test
    fun `entry names - what is refused and what is only tidied`() {
        for (bad in listOf("../../evil.txt", "/sdcard/abs.txt", "a/../../b", "..", "a/..", "", "/", "./", "a\u0000b")) assertEquals(bad, null, ZipNames.normalise(bad))
        assertEquals("ok.txt", ZipNames.normalise("ok.txt"))
        assertEquals("dir/two.bin", ZipNames.normalise("dir//./two.bin"))
        assertEquals("dir", ZipNames.normalise("dir/"))
        assertEquals("..a/b..", ZipNames.normalise("..a/b.."))
    }

    @Test
    fun `names - the extract folder is the zip's base name`() {
        assertEquals("qa", ZipWrites.extractFolderName("qa.zip"))
        assertEquals("a.txt", ZipWrites.extractFolderName("a.txt.zip"))
        assertEquals("QA", ZipWrites.extractFolderName("QA.ZIP"))
    }

    companion object {
        private lateinit var fixtures: File

        /** `<zip>:<entry>` → the md5 the script printed for qa.zip's entries; `<zip>` → the file's. */
        private lateinit var md5s: Map<String, String>

        /** qa.zip's entry sizes, read back with the JDK (for the 50 MB margin's arithmetic). */
        private lateinit var md5sizes: Map<String, Long>

        @BeforeClass
        @JvmStatic
        fun makeFixtures() {
            var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
            val relative = "docs/plan/qa/phase-18/scripts/make_zips.py"
            while (dir != null && !File(dir, relative).isFile) dir = dir.folder
            val script = File(dir ?: throw AssertionError("$relative not found above ${System.getProperty("user.dir")}"), relative)
            fixtures = Files.createTempDirectory("p18-zips").toFile()
            val process = ProcessBuilder("python3", script.path, fixtures.path, "--skip", "qa-big").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue("make_zips.py timed out", process.waitFor(300, TimeUnit.SECONDS))
            assertEquals("make_zips.py failed:\n$output", 0, process.exitValue())
            md5s = output.lines().filter { it.contains("  ") }.associate { it.substringAfter("  ") to it.substringBefore("  ") }
            // The script's own md5 lines are checked against the files it wrote: these ARE its fixtures.
            for (name in listOf("qa.zip", "qa-bad.zip", "qa-corrupt.zip", "qa-enc.zip", "qa-bomb.zip", "qa-huge.zip", "qa-cp437.zip", "qa-nested.zip", "qa-zip64.zip", "qa-symlink.zip")) {
                assertEquals(name, md5s.getValue(name), FilesBed.md5(File(fixtures, name)))
            }
            assertFalse(File(fixtures, "qa-big.zip").exists())
            md5sizes = ZipFile(File(fixtures, "qa.zip")).use { z -> z.entries().toList().associate { "qa.zip:${it.name}" to it.size } }
        }

        @AfterClass
        @JvmStatic
        fun removeFixtures() {
            fixtures.deleteRecursively()
        }
    }
}

package app.tileshell.files

import app.tileshell.net.MiniJson
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GATE review's N9 (pass 2): what a Recycle Bin row SHOWS — its name and its detail line — comes in part from the
 * index, which is shared storage, and from names another app chose. No character that is invisible or reorders the
 * text around it reaches the row as itself: a control character, a format character (Unicode Cf — the bidi controls
 * U+202A..U+202E and U+2066..U+2069, U+200E / U+200F / U+061C, the zero-width U+200B / U+200C / U+200D / U+2060 /
 * U+FEFF, the soft hyphen U+00AD, the tag characters U+E0000..) or a line / paragraph separator is shown as `?`, one
 * per character. And a row's size is the bin file's own, never the record's.
 */
class FileOpsBinShownTextTest {
    private val bed = FilesBed()
    private val bin: File get() = RecycleBin.binDir(bed.primary)
    private val index: File get() = File(bin, ".index.json")

    @After fun tearDown() = bed.close()

    /** Each character the rule must neutralise, as its exact code point. */
    private val hidden = listOf(
        "‮", "‪", "‫", "‬", "‭", "⁦", "⁧", "⁨", "⁩", "‎", "‏", "؜",
        "​", "‌", "‍", "⁠", "﻿", "­", "᠎", String(Character.toChars(0xE0041)), String(Character.toChars(0xE007F)),
        "\n", "\r", "\t", "\u0000", "\u001B", "\u007F", "\u0085", " ", " ",
    )

    @Test
    fun `every control, format and separator character is shown as one question mark - the rest as it is`() {
        for (h in hidden) assertEquals("U+%04X".format(h.codePointAt(0)), "a?b", FilePaths.lineText("a${h}b"))
        // Visible text is untouched: accents, other scripts, an emoji (a surrogate pair), a no-break space.
        assertEquals("Café Ελλάδα 東京 😀 x y.jpg", FilePaths.lineText("Café Ελλάδα 東京 😀 x y.jpg"))
        assertEquals("abc", FilePaths.lineText("abcdef", max = 3))
    }

    @Test
    fun `the detail line - a restore folder holding bidi and zero-width characters shows each as a question mark`() {
        val root = "/storage/emulated/0"
        assertEquals("1/2/2026 Pic?tures?/Trip?", FileListing.binDetail("1/2/2026", "$root/Pic‮tures​/Trip⁦", root, "This Device", false))
        assertEquals("1/2/2026 SD?card/DCIM", FileListing.binDetail("1/2/2026", "/storage/3842-1609/DCIM", "/storage/3842-1609", "SD​card", true))
    }

    @Test
    fun `a binned file whose name holds U+202E lists with the override shown, not obeyed`() {
        val spoof = bed.file("Docs​/invoice‮fdp.exe", "MZ".toByteArray())
        assertEquals(OpResult.Done(), bed.ops.binDelete(spoof))
        val row = bed.ops.bin.list(bed.primary).single()
        assertEquals("invoice?fdp.exe", row.name)
        // The record itself keeps the real path: Restore puts the file back exactly where it was.
        assertEquals(spoof.path, row.originalPath)
        assertEquals("1/1/1970 Docs?", FileListing.binDetail("1/1/1970", row.restoreDir, bed.primary.root, "This Device", false))
        assertEquals(OpResult.Done(listOf(spoof.path)), bed.ops.binRestore(row, FilesBed.never))
        assertEquals("MZ", spoof.readText())
    }

    @Test
    fun `a forged record's name and a bin file's own name are neutralised alike`() {
        bed.ops.binDelete(bed.file("Pictures/a.jpg", "photo".toByteArray()))
        val path = File(bed.root, "Pictures/holi​day‮gpj.exe").path
        index.writeText(MiniJson.write(mapOf("version" to 1, "records" to listOf(mapOf("bin" to "1000-0-a.jpg", "path" to path, "deletedAt" to 1000L, "size" to 5L)))))
        // A file no record names: its bin name is what the row shows.
        File(bin, "9-0-x⁧y­.txt").writeText("loose")
        val rows = bed.ops.bin.list(bed.primary)
        assertEquals(listOf("holi?day?gpj.exe", "9-0-x?y?.txt"), rows.map { it.name })
        for (row in rows) for (h in hidden) assertTrue(row.name, !row.name.contains(h))
    }

    @Test
    fun `a row's size is the bin file's own - a record cannot shrink or grow it`() {
        bed.ops.binDelete(bed.file("Pictures/a.jpg", FilesBed.bytes(5_000)))
        bed.ops.binDelete(bed.file("Music/album/one.mp3", FilesBed.bytes(3_000)).parentFile!!)
        val records = bed.ops.bin.list(bed.primary).map {
            mapOf("bin" to it.binName, "path" to it.originalPath!!, "deletedAt" to it.deletedAt!!, "size" to 0L)
        }
        index.writeText(MiniJson.write(mapOf("version" to 1, "records" to records)))
        val rows = bed.ops.bin.list(bed.primary).associate { it.name to it.size }
        assertEquals(mapOf("a.jpg" to 5_000L, "album" to 3_000L), rows)
        assertEquals(RecycleBin.Stats(2, 8_000), bed.ops.bin.stats(bed.primary))
    }

    @Test
    fun `the obstacle message shows a folded alias's invisible character as a question mark`() {
        assertEquals(
            "A folder named .Tess?era is in the way on This Device. Remove or rename it from a computer, then try again.",
            BinObstacle.message(BinObstacle(BinObstacle.Kind.FOLDER, ".Tess​era", inShell = false), "This Device"),
        )
    }
}

package app.tileshell.files

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 18, build task 2 (r3 V13, Y2; r11/files.md 1.5.8): the folder page's order, detail line, hidden rule and search match. */
class FileListingTest {
    private val zone = ZoneId.of("America/Denver")
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    // The Fixtures paragraph's folder: no two rows share a date or a size.
    private val a = FileRow("a.txt", false, 10, at(2026, 1, 1, 0))
    private val b = FileRow("b.bin", false, 307_200, at(2026, 1, 2))
    private val photo = FileRow("Qa-photo-0.png", false, 5_000, at(2026, 1, 5))
    private val sub = FileRow("sub", true, 4_096, at(2026, 1, 3))
    private val album = FileRow("Album", true, 4_096, at(2026, 1, 4))
    private val rows = listOf(photo, b, sub, a, album)

    private fun names(sort: FileSort) = FileListing.sorted(rows, sort).map { it.name }

    @Test
    fun `sort by name - folders first, then A to Z ignoring case`() {
        assertEquals(listOf("Album", "sub", "a.txt", "b.bin", "Qa-photo-0.png"), names(FileSort.NAME))
    }

    @Test
    fun `sort by date - folders first, each group newest first`() {
        assertEquals(listOf("Album", "sub", "Qa-photo-0.png", "b.bin", "a.txt"), names(FileSort.DATE))
    }

    @Test
    fun `sort by size - folders first and among themselves by name, files largest first`() {
        val bigFolder = FileRow("zz", true, 999_999, at(2026, 1, 6))
        assertEquals(listOf("Album", "sub", "zz", "b.bin", "Qa-photo-0.png", "a.txt"), FileListing.sorted(rows + bigFolder, FileSort.SIZE).map { it.name })
    }

    @Test
    fun `ties go by name in every sort`() {
        val same = listOf(FileRow("b", false, 5, 100), FileRow("C", false, 5, 100), FileRow("a", false, 5, 100), FileRow("A", false, 5, 100))
        for (sort in FileSort.values()) assertEquals(sort.name, listOf("A", "a", "b", "C"), FileListing.sorted(same, sort).map { it.name })
    }

    @Test
    fun `the sort flyout has exactly three keys and no type`() {
        assertEquals(listOf("Name", "Date", "Size"), FileSort.values().map { it.label })
    }

    @Test
    fun `detail - a date alone for a folder, size then date for a file`() {
        assertEquals("1/3/2026", FileListing.detail(sub, zone))
        assertEquals("300 KB 1/2/2026", FileListing.detail(b, zone))
        assertEquals("10 bytes 1/1/2026", FileListing.detail(a, zone))
        assertEquals("1.00 KB 11/24/2015", FileListing.detail(FileRow("one.txt", false, 1024, at(2015, 11, 24)), zone))
        assertEquals("1/5/2026", FileListing.recentDetail(at(2026, 1, 5), zone))
    }

    @Test
    fun `size - three significant figures in bytes, KB, MB and GB`() {
        val expected = mapOf(
            0L to "0 bytes", 1L to "1 bytes", 128L to "128 bytes", 1023L to "1023 bytes",
            1024L to "1.00 KB", 1536L to "1.50 KB", 10_240L to "10.0 KB", 12_595L to "12.3 KB", 102_400L to "100 KB",
            307_200L to "300 KB", 836_608L to "817 KB", 1_023_488L to "1000 KB",
            1_048_576L to "1.00 MB", 209_715_200L to "200 MB",
            1_299_227_607L to "1.21 GB", 3_221_225_472L to "3.00 GB", 2_199_023_255_552L to "2048 GB",
        )
        for ((bytes, text) in expected) assertEquals("$bytes", text, FileListing.sizeText(bytes))
    }

    @Test
    fun `size - a value that rounds up to 1024 reads as 1 of the next unit, and to 10 or 100 loses a decimal`() {
        assertEquals("1.00 MB", FileListing.sizeText(1_048_575))
        assertEquals("10.0 KB", FileListing.sizeText(10_239))
        assertEquals("100 KB", FileListing.sizeText(102_399))
        assertEquals("1.00 GB", FileListing.sizeText(1_073_741_823))
    }

    @Test
    fun `date - the en-US short date with no leading zeros, in the phone's zone`() {
        assertEquals("1/2/2026", FileListing.dateText(at(2026, 1, 2), zone))
        assertEquals("11/24/2015", FileListing.dateText(at(2015, 11, 24), zone))
        // 23:00 in Denver on the 2nd is already the 3rd in UTC: the phone's zone decides.
        assertEquals("1/2/2026", FileListing.dateText(at(2026, 1, 2, 23), zone))
        assertEquals("1/3/2026", FileListing.dateText(at(2026, 1, 2, 23), ZoneId.of("UTC")))
    }

    @Test
    fun `hidden - dot-files are filtered unless the setting shows them`() {
        val listing = listOf(a, FileRow(".hidden.txt", false, 1, 0), FileRow(".Tessera", true, 0, 0), sub)
        assertEquals(listOf("a.txt", "sub"), FileListing.visible(listing, showHidden = false).map { it.name })
        assertEquals(4, FileListing.visible(listing, showHidden = true).size)
        assertTrue(FileListing.isHidden(".nomedia"))
        assertFalse(FileListing.isHidden("a.b"))
    }

    @Test
    fun `search - a name matches when it holds the term, ignoring case`() {
        assertTrue(FileListing.matches("b.bin", "b"))
        assertTrue(FileListing.matches("B.BIN", "b"))
        assertTrue(FileListing.matches("sub", "B"))
        assertTrue(FileListing.matches("Qa-photo-0.png", "photo"))
        assertFalse(FileListing.matches("a.txt", "b"))
        assertFalse(FileListing.matches("a.txt", ""))
        assertFalse(FileListing.matches("a.txt", "   "))
    }

    @Test
    fun `search relevance - the whole name, the name without its extension, a start, a word's start, then anywhere`() {
        assertEquals(0, FileListing.searchRank("b", "b"))
        assertEquals(1, FileListing.searchRank("b.bin", "b"))
        assertEquals(2, FileListing.searchRank("beach.png", "b"))
        assertEquals(3, FileListing.searchRank("qa-beach.png", "b"))
        assertEquals(4, FileListing.searchRank("sub", "b"))
        assertNull(FileListing.searchRank("a.txt", "b"))
        val hits = listOf("sub", "qa-beach.png", "b.bin", "beach.png").sortedBy { FileListing.searchRank(it, "b") }
        assertEquals(listOf("b.bin", "beach.png", "qa-beach.png", "sub"), hits)
    }

    @Test
    fun `selection line - 1 item selected in the singular`() {
        assertEquals("0 items selected", FileListing.selectedText(0))
        assertEquals("1 item selected", FileListing.selectedText(1))
        assertEquals("2 items selected", FileListing.selectedText(2))
    }
}

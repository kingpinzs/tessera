package app.tileshell.files

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix L10: a zip's central directory is bounded BEFORE anything is allocated for it — by the entry count
 * its end record declares and by its bytes — and whatever opening a zip throws, an `OutOfMemoryError` included, ends as
 * `zip open <path>: failed <why>` ("This zip can't be opened"), never as a dead process.
 */
class ZipLimitsTest {
    private val dir: File = Files.createTempDirectory("zip-limits").toFile()
    private val lines = mutableListOf<String>()

    @After fun tearDown() { dir.deleteRecursively() }

    private fun le(size: Int, fill: ByteBuffer.() -> Unit): ByteArray = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).array()

    /**
     * A file that is nothing but a claim: [directoryBytes] of zeros where the central directory would be (a sparse
     * region — no disk is used), then a zip64 end record declaring [count] entries, its locator, and the end record.
     */
    private fun claiming(name: String, count: Long, directoryBytes: Long): File {
        val file = File(dir, name)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(directoryBytes)
            raf.seek(directoryBytes)
            raf.write(le(56) { putInt(0x06064b50); putLong(44); putShort(45); putShort(45); putInt(0); putInt(0); putLong(count); putLong(count); putLong(directoryBytes); putLong(0) })
            raf.write(le(20) { putInt(0x07064b50); putInt(0); putLong(directoryBytes); putInt(1) })
            raf.write(le(22) { putInt(0x06054b50); putShort(0); putShort(0); putShort(-1); putShort(-1); putInt(-1); putInt(-1); putShort(0) })
        }
        return file
    }

    private fun failed(file: File, why: String) {
        lines.clear()
        assertEquals(ZipOpen.Failed(why), ZipArchive.open(file, { lines += it }))
        assertEquals(listOf("zip open ${file.path}: failed $why"), lines)
    }

    @Test
    fun `an end record declaring 1,3 million entries is refused by its count, before its directory is read`() {
        // Device experiment 9's file: about 60 MB of directory for 1,300,000 entries.
        failed(claiming("million.zip", 1_300_000, 1_300_000L * 46), "too many entries")
    }

    @Test
    fun `an absurd count is refused whatever directory size comes with it`() {
        failed(claiming("count-only.zip", 1_300_000, 4096), "too many entries")
        failed(claiming("just-over.zip", ZipCentral.MAX_ENTRIES + 1L, (ZipCentral.MAX_ENTRIES + 1L) * 46), "too many entries")
        failed(claiming("huge.zip", Long.MAX_VALUE, 4096), "too many entries")
    }

    @Test
    fun `a directory over the byte cap is refused though it declares few entries`() {
        failed(claiming("fat.zip", 10, ZipCentral.MAX_DIRECTORY_BYTES + 1), "too many entries")
    }

    @Test
    fun `a count that cannot fit in the directory it comes with is damage, not an allocation`() {
        // 90,000 entries need at least 90,000 x 46 bytes of directory; this one declares 4,096.
        failed(claiming("liar.zip", 90_000, 4096), "damaged (central directory)")
    }

    @Test
    fun `a real archive under the caps still opens, entry for entry`() {
        val file = File(dir, "many.zip")
        ZipOutputStream(file.outputStream().buffered()).use { z ->
            for (i in 0 until 3_000) {
                // Long names, so the directory is far bigger than the reader's window.
                z.putNextEntry(ZipEntry("folder-$i/" + "n".repeat(200) + "-$i.txt"))
                z.write(i)
                z.closeEntry()
            }
        }
        val opened = ZipArchive.open(file, { lines += it }) as ZipOpen.Opened
        opened.archive.use { a ->
            assertEquals(3_000, a.entries.size)
            assertEquals("folder-0/" + "n".repeat(200) + "-0.txt", a.entries.first().name)
            assertEquals("folder-2999/" + "n".repeat(200) + "-2999.txt", a.entries.last().name)
            assertEquals(listOf(2999 and 0xFF), a.input(a.entries.last()).use { it.readBytes().map { b -> b.toInt() and 0xFF } })
        }
        assertEquals(listOf("zip open ${file.path}: 3000 entries"), lines)
    }

    @Test
    fun `whatever opening throws - an OutOfMemoryError too - ends as its line, not as a dead process`() {
        val bytes = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write(1); z.closeEntry() } }.toByteArray()
        val file = File(dir, "ok.zip").apply { writeBytes(bytes) }

        assertEquals(ZipOpen.Failed("unreadable (OutOfMemoryError)"), ZipArchive.open(file, { lines += it }) { throw OutOfMemoryError("simulated") })
        assertEquals(ZipOpen.Failed("unreadable (StackOverflowError)"), ZipArchive.open(file, { lines += it }) { throw StackOverflowError() })
        assertEquals(ZipOpen.Failed("unreadable (IllegalStateException)"), ZipArchive.open(file, { lines += it }) { error("boom") })
        assertEquals(
            listOf("OutOfMemoryError", "StackOverflowError", "IllegalStateException").map { "zip open ${file.path}: failed unreadable ($it)" },
            lines,
        )
        // The control: the same file opens with the platform's own ZipFile.
        assertTrue(ZipArchive.open(file, { }) is ZipOpen.Opened)
    }
}

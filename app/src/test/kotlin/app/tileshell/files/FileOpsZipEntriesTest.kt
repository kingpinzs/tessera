package app.tileshell.files

import app.tileshell.files.FilesBed.Companion.folder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix L13: an entry the volume cannot hold beside the others is refused BY ITSELF — `zip: refused entry
 * <name>` — and the other entries extract (T18-2's rule for a bad name, applied to these too): a second entry of the
 * same name, a file where the archive already made a folder (or a folder where it made a file, or a file under a
 * file), and a name the volume rejects (too long; a backslash on FAT). A name is written once.
 */
class FileOpsZipEntriesTest {
    private val bed = FilesBed()

    @After fun tearDown() = bed.close()

    private fun zipOf(name: String, vararg entries: Pair<String, String>): File {
        val file = File(bed.dir("QA-Files"), name)
        ZipOutputStream(file.outputStream()).use { z ->
            for ((entry, text) in entries) {
                z.putNextEntry(ZipEntry(entry))
                if (!entry.endsWith("/")) z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return file
    }

    /** [file] with every [old] name turned into [new] (same length): how a second entry of one name gets into a zip. */
    private fun renamed(file: File, old: String, new: String): File {
        val bytes = file.readBytes()
        val from = old.toByteArray()
        var i = 0
        while (i <= bytes.size - from.size) {
            if (from.indices.all { bytes[i + it] == from[it] }) { new.toByteArray().copyInto(bytes, i); i += from.size } else i++
        }
        file.writeBytes(bytes)
        return file
    }

    private fun tree(dir: File): List<String> = dir.walkTopDown().filter { it != dir }.map { it.relativeTo(dir).path + if (it.isDirectory) "/" else "" }.sorted().toList()

    private fun extract(zip: File): File {
        val out = File(zip.folder, zip.nameWithoutExtension)
        assertEquals(OpResult.Done(listOf(out.path)), bed.ops.zipExtract(zip, conflict = FilesBed.never))
        assertEquals("zip extract ${zip.path} -> ${out.path}: done", bed.lines.last())
        return out
    }

    @Test
    fun `a second entry of the same name is refused - the name is written once - and the rest extract`() {
        val out = extract(renamed(zipOf("twice.zip", "a.txt" to "first", "#.txt" to "second", "b.txt" to "b"), "#.txt", "a.txt"))
        assertEquals(listOf("a.txt", "b.txt"), tree(out))
        // One file of that name, holding one of the two entries' bytes whole: `java.util.zip.ZipFile` finds an entry's
        // data by its NAME, so which of the two it reads is the platform's choice, not Files' (the JVM reads the last).
        assertTrue(File(out, "a.txt").readText() in setOf("first", "second"))
        assertEquals(1, bed.lines.count { it == "zip: refused entry a.txt" })
    }

    @Test
    fun `a file and then a folder of one name - the folder and what lies under it are refused, the rest extract`() {
        val out = extract(zipOf("file-then-dir.zip", "x" to "a file named x", "x/" to "", "x/inner.txt" to "under the file", "ok.txt" to "ok"))
        assertEquals(listOf("ok.txt", "x"), tree(out))
        assertEquals("a file named x", File(out, "x").readText())
        assertTrue(bed.lines.toString(), bed.lines.containsAll(listOf("zip: refused entry x/", "zip: refused entry x/inner.txt")))
    }

    @Test
    fun `a folder and then a file of one name - the file is refused, the folder's own entries extract`() {
        val out = extract(zipOf("dir-then-file.zip", "y/inner.txt" to "inside", "y" to "a file named y", "ok.txt" to "ok"))
        assertEquals(listOf("ok.txt", "y/", "y/inner.txt"), tree(out))
        assertTrue(bed.lines.toString(), "zip: refused entry y" in bed.lines)
    }

    @Test
    fun `a name the volume cannot hold is refused by itself and the rest extract`() {
        val long = "n".repeat(300) + ".txt"
        val out = extract(zipOf("long.zip", "a.txt" to "a", long to "too long a name", "deep/$long" to "too long, in a folder", "$long/x.txt" to "under too long a folder", "z.txt" to "z"))
        assertEquals(listOf("a.txt", "deep/", "z.txt"), tree(out))
        assertEquals(3, bed.lines.count { it.startsWith("zip: refused entry ") })
    }

    @Test
    fun `what makes an entry unwritable is its name - not the volume being full, gone or closed to the app`() {
        // Android's wording (`open failed: EINVAL (Invalid argument)`), the JVM's, and java.nio's reason.
        for (message in listOf(
            "/storage/3842-1609/x/a\\b.txt: open failed: EINVAL (Invalid argument)",
            "/storage/emulated/0/x/nnn.txt: open failed: ENAMETOOLONG (File name too long)",
            "/tmp/x/nnn.txt (File name too long)",
            "/tmp/x/a:b (Invalid argument)",
        )) assertTrue(message, ZipNames.unwritable(java.io.FileNotFoundException(message)))
        assertTrue(ZipNames.unwritable(java.nio.file.FileSystemException("/tmp/x/nnn", null, "File name too long")))

        for (message in listOf(
            "/storage/emulated/0/x/a.txt: open failed: ENOSPC (No space left on device)",
            "/storage/3842-1609/x/a.txt: open failed: ENOENT (No such file or directory)",
            "/storage/emulated/0/x/a.txt: open failed: EACCES (Permission denied)",
            "/storage/3842-1609/x/a.txt: open failed: EIO (I/O error)",
            "mkdir",
        )) assertTrue(message, !ZipNames.unwritable(java.io.IOException(message)))
        assertTrue(!ZipNames.unwritable(java.io.IOException()))
    }
}

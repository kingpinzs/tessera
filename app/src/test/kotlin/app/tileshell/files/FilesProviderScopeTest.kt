package app.tileshell.files

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix M6, the scope rule's side: under a volume is not enough. The shell's own folders on shared storage
 * — `<volume>/.Tessera/` (the Recycle Bin, the nested-zip copies) and its `Android/data|obb/app.tileshell/` — are
 * never handed out and never served, in any case of their names (`AdvReviewPocTest` P4, asserting the safe outcome);
 * and the three guards a mutant survived: the root is resolved like the path, the path is resolved ONCE and that answer
 * is what is served, and a NUL never comes out of a URI.
 */
class FilesProviderScopeTest {
    private val roots = listOf("/storage/emulated/0", "/storage/3842-1609")
    private val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }

    private val own = listOf(
        ".Tessera/bin/1000-0-secret.txt",
        ".Tessera/bin/.index.json",
        ".Tessera/tmp/.inner.zip.zz00.part",
        ".tessera/bin/1000-0-secret.txt",
        ".TESSERA",
        "Android/data/app.tileshell/files/x.log",
        "Android/data/app.tileshell",
        "Android/obb/app.tileshell/main.obb",
        "android/DATA/App.TileShell/files/x.log",
        "ANDROID/OBB/APP.TILESHELL/main.obb",
        "QA-Files/../.Tessera/bin/1000-0-secret.txt",
    )

    @Test
    fun `the shell's own folders on a volume are never served`() {
        for (root in roots) for (path in own) {
            assertNull("$root/$path", FilesProviderRules.serve("/root$root/$path", roots, lexical))
        }
    }

    @Test
    fun `the shell's own folders on a volume are never handed out, and the refusal is said`() {
        val lines = mutableListOf<String>()
        for (root in roots) for (path in own) {
            assertFalse("$root/$path", FileShareGuard.check("$root/$path", roots, lexical) { lines += it })
        }
        assertEquals(List(roots.size * own.size) { FileShareGuard.LINE_REFUSED }, lines)
    }

    @Test
    fun `a real binned file and a real file in the shell's Android-data folder are refused, by their resolved paths`() {
        val bed = FilesBed()
        try {
            val volumes = listOf(bed.primary.root)
            val binned = File(RecycleBin.binDir(bed.primary), "1000-0-secret.txt").apply { parentFile!!.mkdirs(); writeText("deleted") }
            val private = bed.file("Android/data/app.tileshell/files/x.log")
            val door = File(bed.root, "door")
            Files.createSymbolicLink(door.toPath(), File(bed.root, ".Tessera").toPath())

            assertNull(FilesProviderRules.serve("/root" + binned.path, volumes, bed.canonical))
            assertNull(FilesProviderRules.serve("/root" + private.path, volumes, bed.canonical))
            // A link from an ordinary place into the bin resolves into it.
            assertNull(FilesProviderRules.serve("/root" + File(door, "bin/1000-0-secret.txt").path, volumes, bed.canonical))
            // The control: an ordinary file of the same volume is served.
            val plain = bed.file("QA-Files/b.bin")
            assertEquals(plain.path, FilesProviderRules.serve("/root" + plain.path, volumes, bed.canonical))
        } finally {
            bed.close()
        }
    }

    @Test
    fun `what only looks like the shell's folders is an ordinary file`() {
        for (path in listOf(
            ".Tessera2/a.txt", "Download/.Tessera/a.txt", "Android/data/app.tileshell2/a.txt", "Android/media/app.tileshell/a.txt",
            "Android/data/com.other.app/a.txt", "Android/a.txt", "data/app.tileshell/a.txt",
        )) {
            assertEquals(path, "/storage/emulated/0/$path", FilesProviderRules.serve("/root/storage/emulated/0/$path", roots, lexical))
            assertTrue(path, FileShareGuard.allowed("/storage/emulated/0/$path", roots, lexical))
        }
    }

    @Test
    fun `the package whose folders are refused is the app's own id`() {
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).first { it.isFile }.readText()
        assertTrue(gradle.contains("applicationId = \"${FileShareGuard.OWN_PACKAGE}\""))
    }

    // ---- the guards a mutant survived

    @Test
    fun `a root is resolved like the path - a volume named through a link still holds its files`() {
        val base = Files.createTempDirectory("share-root").toFile().canonicalFile
        try {
            val real = File(base, "storage/emulated/0").apply { mkdirs() }
            val alias = File(base, "sdcard")
            Files.createSymbolicLink(alias.toPath(), real.toPath())
            val file = File(real, "QA-Files/b.bin").apply { parentFile!!.mkdirs(); writeText("shared") }
            val canonical: (String) -> String? = { File(it).canonicalPath }

            assertTrue(FileShareGuard.allowed(File(alias, "QA-Files/b.bin").path, listOf(alias.path), canonical))
            assertEquals(file.path, FilesProviderRules.serve("/root" + File(alias, "QA-Files/b.bin").path, listOf(alias.path), canonical))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `the path is resolved once, and what is served is that answer - a second look could be a different file`() {
        val asked = "/storage/emulated/0/QA-Files/b.bin"
        var looks = 0
        // The file is swapped for a link to a private one right after the first look.
        val swapping: (String) -> String? = { p -> if (p == asked) { if (looks++ == 0) asked else "/data/data/app.tileshell/files/secret" } else lexical(p) }

        assertEquals(asked, FilesProviderRules.serve("/root$asked", roots, swapping))
        assertEquals(1, looks)
    }

    @Test
    fun `a NUL never comes out of a provider URI`() {
        assertNull(FilesProviderRules.pathOf("/root/storage/emulated/0/QA-Files/b%00.bin"))
        assertNull(FilesProviderRules.pathOf("/root/%00"))
        assertEquals("/storage/emulated/0/QA-Files/b.bin", FilesProviderRules.pathOf("/root/storage/emulated/0/QA-Files/b.bin"))
    }
}

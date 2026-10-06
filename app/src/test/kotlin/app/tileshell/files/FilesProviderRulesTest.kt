package app.tileshell.files

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 18, r3 D10 / E7's scope negative at the place the provider SERVES: the same five cases as
 * `FileShareGuardTest` (three refused, two allowed), put to [FilesProviderRules.serve] as the URI paths a grant holder
 * could send — `openFile`, `query` and `getType` all ask exactly this — plus the URI forms that are not the
 * provider's own.
 */
class FilesProviderRulesTest {
    private val roots = listOf("/storage/emulated/0", "/storage/3842-1609")
    private val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }

    /** The URI path FileProvider builds for a file under its one `root-path`: `/root` + the path, percent-encoded. */
    private fun uriPath(file: String) = "/root" + file.split('/').joinToString("/") { seg ->
        seg.toByteArray(Charsets.UTF_8).joinToString("") { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && b >= 0 || c in "-_.~") c.toString() else "%%%02X".format(b)
        }
    }

    @Test
    fun `the app's own filesDir file is refused`() {
        assertNull(FilesProviderRules.serve(uriPath("/data/user/0/app.tileshell/files/files-recent.json"), roots, lexical))
    }

    @Test
    fun `a dot-dot traversal out of shared storage is refused`() {
        assertNull(FilesProviderRules.serve(uriPath("/storage/emulated/0/../../data/data/app.tileshell/files/x"), roots, lexical))
        // The same traversal with its slashes and dots escaped, as a caller rewriting a granted URI would try it.
        assertNull(FilesProviderRules.serve("/root/storage/emulated/0/%2E%2E%2F%2E%2E%2Fdata/data/app.tileshell/files/x", roots, lexical))
    }

    @Test
    fun `a symlink whose canonical form is private is refused`() {
        val base = Files.createTempDirectory("provider-serve").toFile().canonicalFile
        try {
            val shared = File(base, "storage/emulated/0/QA-Files").apply { mkdirs() }
            val secret = File(base, "data/data/app.tileshell/files/secret").apply { parentFile!!.mkdirs(); writeText("private") }
            val link = File(shared, "innocent.txt")
            Files.createSymbolicLink(link.toPath(), secret.toPath())
            val real = File(shared, "b.bin").apply { writeText("shared") }
            val root = listOf(File(base, "storage/emulated/0").path)
            val canonical: (String) -> String? = { File(it).canonicalPath }

            assertNull(FilesProviderRules.serve(uriPath(link.path), root, canonical))
            // The control on the same real tree: a file that IS under the root is served, by its canonical path.
            assertEquals(real.path, FilesProviderRules.serve(uriPath(real.path), root, canonical))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun `a primary-volume path is served`() {
        assertEquals("/storage/emulated/0/QA-Files/b.bin", FilesProviderRules.serve(uriPath("/storage/emulated/0/QA-Files/b.bin"), roots, lexical))
    }

    @Test
    fun `a storage UUID path on a removable volume is served`() {
        assertEquals("/storage/3842-1609/QA-Files/b.bin", FilesProviderRules.serve(uriPath("/storage/3842-1609/QA-Files/b.bin"), roots, lexical))
    }

    @Test
    fun `what is served is the canonical path that was checked`() {
        assertEquals("/storage/emulated/0/b.bin", FilesProviderRules.serve("/root/storage/emulated/0/QA-Files/../b.bin", roots, lexical))
        // A name with a space and a non-ASCII letter survives the encoding both ways.
        assertEquals("/storage/emulated/0/My Music/Björk.mp3", FilesProviderRules.serve(uriPath("/storage/emulated/0/My Music/Björk.mp3"), roots, lexical))
        assertEquals("/storage/emulated/0/a+b.txt", FilesProviderRules.serve("/root/storage/emulated/0/a+b.txt", roots, lexical))
    }

    @Test
    fun `a volume root itself, another tag and a malformed path are refused`() {
        assertNull(FilesProviderRules.serve("/root/storage/emulated/0", roots, lexical))
        assertNull(FilesProviderRules.serve("/root/", roots, lexical))
        assertNull(FilesProviderRules.serve("/root", roots, lexical))
        assertNull(FilesProviderRules.serve(null, roots, lexical))
        assertNull(FilesProviderRules.serve("/files/storage/emulated/0/QA-Files/b.bin", roots, lexical))
        assertNull(FilesProviderRules.serve("/storage/emulated/0/QA-Files/b.bin", roots, lexical))
        assertNull(FilesProviderRules.serve("/root/storage/emulated/0/QA-Files/b%2", roots, lexical))
        assertNull(FilesProviderRules.serve("/root/storage/emulated/0/QA-Files/b%zz.bin", roots, lexical))
        assertNull(FilesProviderRules.serve("/root/storage/emulated/0/QA-Files/b%00.bin", roots, lexical))
        assertNull(FilesProviderRules.serve("/root/storage/emulated/0/QA-Files/%FF%FE.bin", roots, lexical))
    }

    @Test
    fun `no roots and a canonicaliser that fails serve nothing`() {
        val path = uriPath("/storage/emulated/0/QA-Files/b.bin")
        assertNull(FilesProviderRules.serve(path, emptyList(), lexical))
        assertNull(FilesProviderRules.serve(path, roots) { null })
        assertNull(FilesProviderRules.serve(path, roots) { error("unreadable") })
        // A prefix that is not a folder boundary is not "under" the volume.
        assertNull(FilesProviderRules.serve(uriPath("/storage/emulated/01/x"), roots, lexical))
    }
}

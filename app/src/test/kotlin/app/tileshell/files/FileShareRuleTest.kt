package app.tileshell.files

import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The share rule's edges beside E7's five named cases (which `FileShareGuardTest` holds alone, so its report shows
 * exactly five): what the provider's `openFile` / `query` check ([FileShareGuard.allowed]) must also refuse.
 */
class FileShareRuleTest {
    private val roots = listOf("/storage/emulated/0", "/storage/3842-1609")
    private val lexical: (String) -> String? = { Paths.get(it).normalize().toString() }

    @Test
    fun `a volume's root itself, a sibling that only starts like it, and nothing at all are refused`() {
        for (path in listOf("/storage/emulated/0", "/storage/emulated/0/", "/storage/emulated/01/x", "/storage/emulated/0x", "/storage", "/", "", null)) {
            assertFalse("$path", FileShareGuard.allowed(path, roots, lexical))
        }
    }

    @Test
    fun `a root that names the whole disk, or no roots at all, allow nothing`() {
        assertFalse(FileShareGuard.allowed("/data/data/app.tileshell/files/x", listOf("/"), lexical))
        assertFalse(FileShareGuard.allowed("/data/data/app.tileshell/files/x", listOf(""), lexical))
        assertFalse(FileShareGuard.allowed("/storage/emulated/0/a.txt", emptyList(), lexical))
    }

    @Test
    fun `a path that cannot be resolved is refused, and a path with a NUL never reaches the resolver`() {
        assertFalse(FileShareGuard.allowed("/storage/emulated/0/a.txt", roots) { null })
        assertFalse(FileShareGuard.allowed("/storage/emulated/0/a.txt", roots) { throw java.io.IOException("no") })
        var asked = false
        assertFalse(FileShareGuard.allowed("/storage/emulated/0/a\u0000/../../../data/x", roots) { asked = true; it })
        assertFalse(asked)
    }

    @Test
    fun `a traversal that stays inside the volume is allowed - the canonical form decides`() {
        assertTrue(FileShareGuard.allowed("/storage/emulated/0/QA-Files/sub/../b.bin", roots, lexical))
        assertTrue(FileShareGuard.allowed("/storage/3842-1609/a/./b.bin", roots, lexical))
    }
}

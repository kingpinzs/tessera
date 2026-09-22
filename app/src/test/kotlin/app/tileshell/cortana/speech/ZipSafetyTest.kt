package app.tileshell.cortana.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The zip-traversal guard on espeak-ng-data.zip.
 *
 * The zip is bundled and checksummed, so a hostile entry is not the threat model — a wrong one is. The
 * guard is what makes "unpack this archive into the app's private storage" a bounded operation no matter
 * what the archive says, which is the only reason the extraction is allowed to run unattended.
 */
class ZipSafetyTest {

    @get:Rule val temp = TemporaryFolder()

    private fun target(): File = temp.newFolder("v1")

    @Test
    fun `an entry named dot-dot-slash-evil is rejected`() {
        val dir = target()
        assertNull(ZipSafety.resolve(dir, "../evil"))
        assertFalse(ZipSafety.isSafe(dir, "../evil"))
    }

    @Test
    fun `a traversal buried inside a plausible path is rejected`() {
        val dir = target()
        assertNull(ZipSafety.resolve(dir, "espeak-ng-data/../../../../data/data/app.tileshell/shared_prefs/x.xml"))
        assertNull(ZipSafety.resolve(dir, "espeak-ng-data/voices/../../../evil"))
        assertNull(ZipSafety.resolve(dir, "../"))
        assertNull(ZipSafety.resolve(dir, ".."))
    }

    @Test
    fun `the ordinary entries the real archive contains are accepted`() {
        val dir = target()
        val dict = ZipSafety.resolve(dir, "espeak-ng-data/en_dict")
        assertTrue(dict != null && dict.path.startsWith(dir.canonicalPath + File.separator))
        assertEquals(File(dir.canonicalFile, "espeak-ng-data/en_dict").path, dict!!.path)

        val nested = ZipSafety.resolve(dir, "espeak-ng-data/voices/!v/Alex")
        assertTrue(nested != null && nested.path.startsWith(dir.canonicalPath + File.separator))

        // A path that walks out and back in is fine: what it resolves to is what matters.
        val roundabout = ZipSafety.resolve(dir, "espeak-ng-data/../espeak-ng-data/en_dict")
        assertEquals(dict.path, roundabout!!.path)
    }

    @Test
    fun `an empty name is rejected and the directory itself is not an entry to write`() {
        val dir = target()
        assertNull(ZipSafety.resolve(dir, ""))
    }

    @Test
    fun `an absolute-looking entry cannot land outside the target`() {
        val dir = target()
        val resolved = ZipSafety.resolve(dir, "/etc/passwd")
        // Either rejected, or resolved back inside the target - never /etc/passwd itself.
        if (resolved != null) {
            assertTrue(resolved.path.startsWith(dir.canonicalPath + File.separator))
        }
        assertFalse(resolved?.path == "/etc/passwd")
    }

    @Test
    fun `a symlink out of the target does not become a way in`() {
        val dir = target()
        val outside = temp.newFolder("outside")
        val link = File(dir, "escape").toPath()
        val linked = try {
            java.nio.file.Files.createSymbolicLink(link, outside.toPath())
            true
        } catch (_: Exception) {
            false // no symlink support here; the canonical-path check is what is under test either way
        }
        if (linked) {
            assertNull(ZipSafety.resolve(dir, "escape/evil"))
        }
    }
}

package app.tileshell.files

import app.tileshell.media.SourceScan
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pass 3, P3-5: the search walk asks of each folder it is about to enter ONE question — is this the shell's own folder
 * ([FilePaths.isShellDir], [FilePaths.walkSkip]) — not the whole identity walk up every folder above it that
 * [FilePaths.inShellDir] makes for a path handed in from outside. A walk that comes down from an allowed folder and never
 * follows a link can only reach the shell's folder by meeting it, so the one question is the whole answer.
 */
class FilesSearchWalkTest {
    private val bed = FilesBed()

    @After fun tearDown() = bed.close()

    /** The real volume, counting the identity questions asked. */
    private class Counting : FileIdentity by FileIdentity.Real {
        var asked = 0
        override fun same(a: File, b: File): Boolean { asked++; return FileIdentity.Real.same(a, b) }
    }

    @Test
    fun `the walk asks at most two identity questions per folder entered, however deep, and never enters the shell's folder`() {
        bed.file(".Tessera/bin/1000-0-zzz.txt")
        for (i in 0 until 20) bed.file("Music/Artist$i/Album/Disc1/Extras/Deeper/zzz$i.txt")
        val counting = Counting()
        var folders = 0
        val hits = ArrayList<FolderReader.Hit>()
        assertTrue(FolderReader.search(bed.root, "zzz", showHidden = true, skip = FilePaths.walkSkip(bed.primary, counting), stopped = { false }, onFolder = { folders = it }) { hits += it })
        assertEquals(20, hits.size)
        assertTrue(hits.none { ".Tessera" in it.path })
        assertTrue("${counting.asked} questions for $folders folders", counting.asked <= 2 * folders)
    }

    @Test
    fun `the skip is the shell's folder by name at the root and by identity anywhere - and nothing else`() {
        bed.dir(".Tessera/bin")
        val folding = FoldingVolume()
        val skip = FilePaths.walkSkip(bed.primary, folding)
        assertTrue(skip(File(bed.root, ".Tessera")))
        assertTrue(skip(File(bed.root, ".TESSERA")))
        assertTrue(skip(File(bed.root, ".Teßera")))
        assertTrue(!skip(File(bed.root, "Music")))
        assertTrue(!skip(File(bed.root, "Music/.Tessera")))
    }

    @Test
    fun `Files' search walk skips by that one question`() {
        val state = SourceScan.read("files/FilesState.kt")
        assertEquals(1, SourceScan.count(state, "skip = FilePaths.walkSkip(volume),"))
        assertEquals(0, SourceScan.count(state, "skip = { FilePaths.inShellDir("))
    }
}

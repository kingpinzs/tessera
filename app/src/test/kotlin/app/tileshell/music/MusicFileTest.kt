package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 18, "below Q-18-2": what a file played outside the library is called. */
class MusicFileTest {
    @Test
    fun `the tag title wins`() {
        assertEquals("Bloom", MusicFile.title("  Bloom ", "01 - bloom.mp3"))
    }

    @Test
    fun `no tag title gives the name without its extension`() {
        assertEquals("qa-hidden", MusicFile.title(null, "qa-hidden.mp3"))
        assertEquals("qa-hidden", MusicFile.title("   ", "qa-hidden.mp3"))
        assertEquals("a.b", MusicFile.title("", "a.b.flac"))
    }

    @Test
    fun `a name with no extension and a dot-file keep their whole name`() {
        assertEquals("track", MusicFile.title(null, "track"))
        assertEquals(".hidden", MusicFile.title(null, ".hidden"))
    }

    @Test
    fun `a missing artist or album is blank, not Unknown`() {
        assertEquals("", MusicFile.text(null))
        assertEquals("", MusicFile.text("  "))
        assertEquals("Radiohead", MusicFile.text(" Radiohead\u0000junk"))
    }

    @Test
    fun `the media id is never a library id and never carries the path`() {
        val id = MusicFile.mediaId(7)
        assertEquals("file:7", id)
        assertEquals(null, id.toLongOrNull())
        assertTrue(MusicFile.isFile(id))
        assertFalse(MusicFile.isFile("7"))
        assertFalse(MusicFile.isFile(null))
    }

    @Test
    fun `the diagnostics line names the path`() {
        assertEquals(
            "play file /storage/emulated/0/QA-Files/hidden/qa-hidden.mp3 (not in library)",
            MusicFile.line("/storage/emulated/0/QA-Files/hidden/qa-hidden.mp3"),
        )
    }

    @Test
    fun `embedded art is sampled down to the bound`() {
        assertEquals(1, MusicFile.sampleSize(500, 500, 512))
        assertEquals(2, MusicFile.sampleSize(1000, 600, 512))
        assertEquals(8, MusicFile.sampleSize(3000, 3000, 512))
        assertEquals(1, MusicFile.sampleSize(0, 0, 512))
    }
}

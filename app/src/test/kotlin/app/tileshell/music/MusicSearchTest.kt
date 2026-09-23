package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicSearchTest {

    private fun t(id: Long, title: String, artist: String, album: String, albumId: Long) =
        Track(id, title, artist, album, albumId, 90_000L, 0L)

    private val library = listOf(
        t(1, "Bloom", "Radiohead", "The King of Limbs", 10),
        t(2, "Codex", "Radiohead", "The King of Limbs", 10),
        t(3, "4 Minute Warning", "Radiohead", "The King of Limbs", 10),
        t(4, "An Ending", "Brian Eno", "Apollo", 20),
        t(5, "Deep Blue Day", "Brian Eno", "Apollo", 20),
        t(6, "Zoo Station", "U2", "Achtung Baby", 30),
    )

    @Test fun `an exact song plays from itself within its album`() {
        val m = MusicSearch.resolve("codex", library)!!
        assertEquals(MusicSearch.Kind.SONG, m.kind)
        assertEquals("Codex by Radiohead", m.label)
        assertEquals(listOf(1L, 2L, 3L), m.queue.map { it.id })
        assertEquals(1, m.startIndex)
    }

    @Test fun `an artist plays all of their tracks from the first`() {
        val m = MusicSearch.resolve("Brian Eno", library)!!
        assertEquals(MusicSearch.Kind.ARTIST, m.kind)
        assertEquals(listOf(4L, 5L), m.queue.map { it.id })
        assertEquals(0, m.startIndex)
    }

    @Test fun `an album matches without its leading the`() {
        val m = MusicSearch.resolve("king of limbs", library)!!
        assertEquals(MusicSearch.Kind.ALBUM, m.kind)
        assertEquals(3, m.queue.size)
    }

    @Test fun `a song beats an artist on the same words`() {
        val lib = library + t(7, "U2", "Someone", "Covers", 40)
        assertEquals(MusicSearch.Kind.SONG, MusicSearch.resolve("u2", lib)!!.kind)
    }

    @Test fun `contains matches only from three letters`() {
        assertEquals("Zoo Station by U2", MusicSearch.resolve("station", library)!!.label)
        assertNull(MusicSearch.resolve("oo", library))
    }

    @Test fun `punctuation and case do not matter`() {
        assertEquals(MusicSearch.Kind.SONG, MusicSearch.resolve("DEEP BLUE DAY!", library)!!.kind)
    }

    @Test fun `a name the library does not have resolves to nothing`() {
        assertNull(MusicSearch.resolve("zzqx nothing", library))
        assertNull(MusicSearch.resolve("", library))
        assertNull(MusicSearch.resolve("bloom", emptyList()))
    }
}

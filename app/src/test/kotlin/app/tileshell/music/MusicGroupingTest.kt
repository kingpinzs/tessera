package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four pivots, and the metadata cases that are normal rather than exceptional here.
 *
 * Phase 10 Q6 ruled the library indexes EVERYTHING MediaStore calls audio, so ringtones, alarms and
 * voice recordings are in it — and those routinely have no album, no artist and sometimes no title. A
 * library that only works on well-tagged music would be broken on the first phone it met.
 */
class MusicGroupingTest {

    private fun track(
        id: Long,
        title: String = "t$id",
        artist: String = "Artist",
        album: String = "Album",
        albumId: Long = 1,
    ) = Track(id, title, artist, album, albumId, durationMs = 1000, addedAtMs = id)

    // ---- the cleaning rule, which is what makes the untagged case survive ----

    @Test
    fun `MediaStore's unknown markers become something a person can read`() {
        assertEquals(Track.UNKNOWN_ARTIST, Track.clean("<unknown>", Track.UNKNOWN_ARTIST))
        assertEquals(Track.UNKNOWN_ARTIST, Track.clean("", Track.UNKNOWN_ARTIST))
        assertEquals(Track.UNKNOWN_ARTIST, Track.clean("   ", Track.UNKNOWN_ARTIST))
        assertEquals(Track.UNKNOWN_TITLE, Track.clean(null, Track.UNKNOWN_TITLE))
    }

    @Test
    fun `a real value is kept, and trimmed`() {
        assertEquals("Portishead", Track.clean("  Portishead  ", Track.UNKNOWN_ARTIST))
        // "Unknown" is a real band name and must not be mistaken for the absence of one.
        assertEquals("Unknown Mortal Orchestra", Track.clean("Unknown Mortal Orchestra", Track.UNKNOWN_ARTIST))
    }

    // ---- songs ----

    @Test
    fun `songs sort A-Z ignoring case`() {
        val tracks = listOf(track(1, "Zoo Station"), track(2, "abbey road"), track(3, "Mercy"))
        assertEquals(listOf("abbey road", "Mercy", "Zoo Station"), MusicGrouping.songs(tracks).map { it.title })
    }

    // ---- albums ----

    @Test
    fun `albums group by id, not by name, because two albums can share a title`() {
        val tracks = listOf(
            track(1, album = "Greatest Hits", albumId = 1, artist = "A"),
            track(2, album = "Greatest Hits", albumId = 2, artist = "B"),
        )
        val albums = MusicGrouping.albums(tracks)
        assertEquals(2, albums.size)
        assertEquals(listOf("A", "B"), albums.map { it.artist }.sorted())
    }

    @Test
    fun `an album whose tracks disagree about the artist is a compilation`() {
        val tracks = listOf(
            track(1, artist = "A", albumId = 7),
            track(2, artist = "B", albumId = 7),
        )
        assertEquals(MusicGrouping.VARIOUS_ARTISTS, MusicGrouping.albums(tracks).single().artist)
    }

    @Test
    fun `an album whose tracks agree keeps that artist`() {
        val tracks = listOf(track(1, artist = "A", albumId = 7), track(2, artist = "A", albumId = 7))
        assertEquals("A", MusicGrouping.albums(tracks).single().artist)
    }

    @Test
    fun `an album's tracks come back sorted`() {
        val tracks = listOf(track(1, "Wires", albumId = 3), track(2, "Aches", albumId = 3))
        assertEquals(listOf("Aches", "Wires"), MusicGrouping.albums(tracks).single().tracks.map { it.title })
    }

    // ---- artists ----

    @Test
    fun `artists group by name and carry their albums`() {
        val tracks = listOf(
            track(1, artist = "A", album = "One", albumId = 1),
            track(2, artist = "A", album = "Two", albumId = 2),
            track(3, artist = "B", album = "Three", albumId = 3),
        )
        val artists = MusicGrouping.artists(tracks)
        assertEquals(listOf("A", "B"), artists.map { it.name })
        assertEquals(2, artists.first().albums.size)
        assertEquals(2, artists.first().trackCount)
    }

    @Test
    fun `an artist on a compilation sees only their own track on it`() {
        // The compilation belongs to both artists, but B's view of it holds B's track and not A's.
        val tracks = listOf(
            track(1, title = "a song", artist = "A", album = "Comp", albumId = 9),
            track(2, title = "b song", artist = "B", album = "Comp", albumId = 9),
        )
        val b = MusicGrouping.artists(tracks).single { it.name == "B" }
        assertEquals(listOf("b song"), b.albums.single().tracks.map { it.title })
    }

    // ---- the empty phone, which is an acceptance row of its own (E5) ----

    @Test
    fun `a phone with no audio produces empty pivots rather than anything to crash on`() {
        assertTrue(MusicGrouping.songs(emptyList()).isEmpty())
        assertTrue(MusicGrouping.albums(emptyList()).isEmpty())
        assertTrue(MusicGrouping.artists(emptyList()).isEmpty())
    }
}

package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The collection's four pivots (phase 10 build task 6).
 *
 * Plain [Track] values, which hold no Android types, so the grouping, the letter index and the queue a
 * tap starts are all proved on the JVM. What is NOT proved here is what is drawn — that is the device
 * row, because a list that groups correctly and paints nothing looks identical from in here.
 */
class MusicCollectionTest {

    private val locale = Locale.US

    private fun track(id: Long, title: String, artist: String = "Artist", album: String = "Album", albumId: Long = 1L) =
        Track(id, title, artist, album, albumId, 1_000L, 0L)

    private val library = listOf(
        track(1, "Bloom", "Radiohead", "The King of Limbs", 10),
        track(2, "Codex", "Radiohead", "The King of Limbs", 10),
        track(3, "An Ending", "Brian Eno", "Apollo", 20),
        track(4, "Deep Blue Day", "Brian Eno", "Apollo", 20),
        track(5, "Zoo Station", "U2", "Achtung Baby", 30),
        track(6, "4 Minute Warning", "Radiohead", "The King of Limbs", 10),
    )

    // ---- the pivots ----

    @Test
    fun `the songs pivot lists every track, A-Z, under its own letter`() {
        val page = MusicCollection.songs(library, locale)
        val songs = page.items.filterIsInstance<SongItem>().map { it.track.title }
        assertEquals(listOf("4 Minute Warning", "An Ending", "Bloom", "Codex", "Deep Blue Day", "Zoo Station"), songs)
        val headers = page.items.filterIsInstance<LetterHeader>().map { it.letter }
        // "4 Minute Warning" files under "#", which is why the index has to be the app list's.
        assertEquals(listOf("#", "A", "B", "C", "D", "Z"), headers)
    }

    @Test
    fun `the albums pivot lists each album once`() {
        val page = MusicCollection.albums(library, locale)
        assertEquals(listOf("Achtung Baby", "Apollo", "The King of Limbs"), page.items.filterIsInstance<AlbumItem>().map { it.album.name })
    }

    @Test
    fun `the artists pivot lists each artist once, with their track counts`() {
        val page = MusicCollection.artists(library, locale)
        val artists = page.items.filterIsInstance<ArtistItem>()
        assertEquals(listOf("Brian Eno", "Radiohead", "U2"), artists.map { it.artist.name })
        assertEquals(listOf(2, 3, 1), artists.map { it.artist.trackCount })
    }

    @Test
    fun `the playlists pivot always offers the row that makes one, even with none`() {
        // Build task 8: the "new playlist" row is first and is always there, which is Groove's own
        // arrangement and also the answer to the empty case — the one thing there is to do, rather
        // than a sentence explaining that there is nothing.
        val page = MusicCollection.playlists(emptyList(), locale)
        assertEquals(listOf(NewPlaylistItem), page.items)
        assertTrue(page.queue.isEmpty())
    }

    @Test
    fun `playlists are listed A-Z under their letters, below that row`() {
        val page = MusicCollection.playlists(
            listOf(Playlist("b", "Zebra", listOf(1L)), Playlist("a", "Apple", emptyList())),
            locale,
        )
        assertEquals(NewPlaylistItem, page.items.first())
        assertEquals(listOf("A", "Z"), page.items.filterIsInstance<LetterHeader>().map { it.letter })
        assertEquals(listOf("Apple", "Zebra"), page.items.filterIsInstance<PlaylistItem>().map { it.playlist.name })
    }

    @Test
    fun `a jump target on the playlists pivot still lands on its own header`() {
        // The "new playlist" row shifts every index by one; the indices are taken as the headers are
        // appended, so it cannot put them out by that one row.
        val page = MusicCollection.playlists(listOf(Playlist("a", "Zebra", emptyList())), locale)
        val at = page.jump.first { it.letter == "Z" }.index!!
        assertEquals("Z", (page.items[at] as LetterHeader).letter)
    }

    @Test
    fun `an empty library gives every music pivot an empty page rather than a crash`() {
        for (pivot in listOf(MusicPivot.ALBUMS, MusicPivot.ARTISTS, MusicPivot.SONGS)) {
            assertTrue(MusicCollection.page(pivot, emptyList(), emptyList(), locale).isEmpty)
        }
    }

    // ---- the queue a tap starts ----

    @Test
    fun `tapping a song starts the pivot it was tapped in, from that song`() {
        val page = MusicCollection.songs(library, locale)
        val codex = library.first { it.title == "Codex" }
        // The queue is the pivot's own order, not the library's, so "next" plays what is drawn below it.
        assertEquals(listOf("4 Minute Warning", "An Ending", "Bloom", "Codex", "Deep Blue Day", "Zoo Station"), page.queue.map { it.title })
        assertEquals(3, page.startIndexOf(codex))
    }

    @Test
    fun `the albums pivot queues album by album, not title by title`() {
        val page = MusicCollection.albums(library, locale)
        assertEquals(
            listOf("Zoo Station", "An Ending", "Deep Blue Day", "4 Minute Warning", "Bloom", "Codex"),
            page.queue.map { it.title },
        )
    }

    @Test
    fun `a track that is not in the queue starts at the beginning rather than at minus one`() {
        val page = MusicCollection.songs(library, locale)
        assertEquals(0, page.startIndexOf(track(99, "Nowhere")))
    }

    @Test
    fun `opening an album gives its own tracks, in album order`() {
        val album = MusicCollection.albums(library, locale).items.filterIsInstance<AlbumItem>().first { it.album.name == "Apollo" }
        assertEquals(listOf("An Ending", "Deep Blue Day"), MusicCollection.tracksOf(album).map { it.title })
    }

    @Test
    fun `opening an artist gives every track they have`() {
        val artist = MusicCollection.artists(library, locale).items.filterIsInstance<ArtistItem>().first { it.artist.name == "Radiohead" }
        assertEquals(3, MusicCollection.tracksOf(artist).size)
    }

    // ---- the jump grid ----

    @Test
    fun `every letter is a cell, and the empty ones have nowhere to go`() {
        val page = MusicCollection.songs(library, locale)
        assertEquals(27, page.jump.size)
        assertNull(page.jump.first { it.letter == "Q" }.index)
        assertEquals(page.items.indexOfFirst { it is LetterHeader && it.letter == "Z" }, page.jump.first { it.letter == "Z" }.index)
    }

    @Test
    fun `a jump target is the index of its own header, taken as the header is appended`() {
        // The app list's jump grid once landed short by the height of its top sections because the
        // indices were recounted afterwards. These are taken in place, so they cannot drift.
        val page = MusicCollection.songs(library, locale)
        for (target in page.jump) {
            val at = target.index ?: continue
            assertEquals(target.letter, (page.items[at] as LetterHeader).letter)
        }
    }

    @Test
    fun `rows carry keys that are unique, because a LazyColumn key that repeats crashes it`() {
        val page = MusicCollection.songs(library, locale)
        assertEquals(page.items.size, page.items.map { it.key }.distinct().size)
    }
}

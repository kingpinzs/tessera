package app.tileshell.music

import app.tileshell.music.server.ServerLibrary
import app.tileshell.video.server.ServerTrack
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home server's songs as albums, artists and songs (phase 20 build task 8; Y7): worked out from the server's one
 * flat listing, so what the three groupings list — and the queue a tap starts — is proved here.
 */
class ServerLibraryTest {

    private fun track(id: String, title: String, album: String, artist: String, index: Int?) = ServerTrack(id, title, album, artist, index, 60_000L)

    private val listing = listOf(
        track("a3", "Third", "Apollo", "Brian Eno", 3),
        track("b1", "Zoo Station", "Achtung Baby", "U2", 1),
        track("a1", "An Ending", "Apollo", "Brian Eno", 1),
        track("a2", "Always Returning", "Apollo", "Brian Eno", 2),
        track("c1", "Loose", "", "", null),
        track("d1", "Bonus", "Apollo", "Brian Eno", null),
    )

    @Test
    fun `albums are listed once each, A to Z, their songs by their number on the album`() {
        val albums = ServerLibrary.albums(listing)
        assertEquals(listOf("Achtung Baby", "Apollo", ServerLibrary.UNKNOWN_ALBUM), albums.map { it.name })
        // The unnumbered song comes after the numbered ones.
        assertEquals(listOf("a1", "a2", "a3", "d1"), albums[1].tracks.map { it.id })
        assertEquals("Brian Eno", albums[1].artist)
        assertEquals("a1", albums[1].id)
    }

    @Test
    fun `two albums of one name by two artists stay two`() {
        val albums = ServerLibrary.albums(listOf(track("x", "One", "Greatest Hits", "ABBA", 1), track("y", "Two", "Greatest Hits", "Queen", 1)))
        assertEquals(listOf("ABBA", "Queen"), albums.map { it.artist })
    }

    @Test
    fun `artists are listed once each, with every song of theirs in album order`() {
        val artists = ServerLibrary.artists(listing)
        assertEquals(listOf("Brian Eno", "U2", ServerLibrary.UNKNOWN_ARTIST), artists.map { it.name })
        assertEquals(listOf("a1", "a2", "a3", "d1"), artists[0].tracks.map { it.id })
    }

    @Test
    fun `songs are every song, A to Z by title`() {
        assertEquals(listOf("Always Returning", "An Ending", "Bonus", "Loose", "Third", "Zoo Station"), ServerLibrary.songs(listing).map { it.title })
    }

    @Test
    fun `a song's second line names what the server knows, and no more`() {
        assertEquals("Brian Eno • Apollo", ServerLibrary.songLine(listing[0]))
        assertEquals("", ServerLibrary.songLine(listing[4]))
        assertEquals("1 song", ServerLibrary.countText(1))
        assertEquals("4 songs", ServerLibrary.countText(4))
    }
}

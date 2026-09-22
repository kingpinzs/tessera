package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playlists (phase 10 build task 8) — Q7's four verbs and the two they imply.
 *
 * Plain values throughout: [PlaylistRules] holds no Android types at all, so the rules about what may
 * be written are proved here and [PlaylistStore] is left with nothing to decide.
 */
class PlaylistRulesTest {

    private fun track(id: Long, title: String = "t$id") =
        Track(id, title, "Artist", "Album", 1L, 1_000L, 0L)

    private val empty = emptyList<Playlist>()

    // ---- create ----

    @Test
    fun `a new playlist starts empty and keeps the name it was given`() {
        val out = PlaylistRules.create(empty, "a", "  Road trip  ")
        assertEquals(1, out.size)
        assertEquals("Road trip", out[0].name)
        assertTrue(out[0].trackIds.isEmpty())
    }

    @Test
    fun `an unnamed playlist gets the default name`() {
        assertEquals(PlaylistRules.DEFAULT_NAME, PlaylistRules.create(empty, "a", "   ")[0].name)
    }

    @Test
    fun `and the next unnamed one does not get the SAME default name`() {
        // Two rows that cannot be told apart at the moment they are made is exactly when it matters.
        var list = PlaylistRules.create(empty, "a", "")
        list = PlaylistRules.create(list, "b", "")
        list = PlaylistRules.create(list, "c", "")
        assertEquals(listOf("New playlist", "New playlist (2)", "New playlist (3)"), list.map { it.name })
    }

    @Test
    fun `two playlists MAY share a name, because the id is the identity`() {
        var list = PlaylistRules.create(empty, "a", "Mix")
        list = PlaylistRules.create(list, "b", "Mix")
        assertEquals(2, list.size)
        assertNotEquals(list[0].id, list[1].id)
    }

    @Test
    fun `an id is unique even when the clock hands out the same millisecond twice`() {
        val list = listOf(Playlist("pl-5", "one", emptyList()))
        assertNotEquals("pl-5", PlaylistRules.newId(5L, list))
    }

    // ---- rename ----

    @Test
    fun `rename changes only the one it names`() {
        val list = listOf(Playlist("a", "One", emptyList()), Playlist("b", "Two", emptyList()))
        assertEquals(listOf("Uno", "Two"), PlaylistRules.rename(list, "a", "Uno").map { it.name })
    }

    @Test
    fun `a blank rename is ignored rather than obeyed`() {
        // An empty name draws a row that cannot be told from the others; the old name is the better answer.
        val list = listOf(Playlist("a", "One", emptyList()))
        assertSame(list, PlaylistRules.rename(list, "a", "   "))
    }

    @Test
    fun `renaming something that is not there changes nothing`() {
        val list = listOf(Playlist("a", "One", emptyList()))
        assertEquals(list, PlaylistRules.rename(list, "gone", "Two"))
    }

    // ---- delete ----

    @Test
    fun `delete removes exactly one`() {
        val list = listOf(Playlist("a", "One", emptyList()), Playlist("b", "Two", emptyList()))
        assertEquals(listOf("b"), PlaylistRules.delete(list, "a").map { it.id })
    }

    // ---- add and remove ----

    @Test
    fun `a track is appended, because a playlist is the order someone built`() {
        var list = listOf(Playlist("a", "One", listOf(1L, 2L)))
        list = PlaylistRules.add(list, "a", 3L)
        assertEquals(listOf(1L, 2L, 3L), list[0].trackIds)
    }

    @Test
    fun `the same track may be added twice, because someone who does that meant to`() {
        var list = listOf(Playlist("a", "One", listOf(1L)))
        list = PlaylistRules.add(list, "a", 1L)
        assertEquals(listOf(1L, 1L), list[0].trackIds)
    }

    @Test
    fun `remove takes the POSITION that was held, not every copy of that track`() {
        val list = listOf(Playlist("a", "One", listOf(7L, 7L, 9L)))
        assertEquals(listOf(7L, 9L), PlaylistRules.removeAt(list, "a", 0)[0].trackIds)
    }

    @Test
    fun `removing a position that is not there changes nothing`() {
        val list = listOf(Playlist("a", "One", listOf(1L)))
        assertEquals(list, PlaylistRules.removeAt(list, "a", 4))
        assertEquals(list, PlaylistRules.removeAt(list, "a", -1))
    }

    // ---- reorder ----

    @Test
    fun `move up and move down are the same rule run either way`() {
        val list = listOf(Playlist("a", "One", listOf(1L, 2L, 3L)))
        assertEquals(listOf(2L, 1L, 3L), PlaylistRules.move(list, "a", 1, 0)[0].trackIds)
        assertEquals(listOf(2L, 1L, 3L), PlaylistRules.move(list, "a", 0, 1)[0].trackIds)
    }

    @Test
    fun `moving past an end clamps rather than refusing`() {
        // Move-up on the first row and move-down on the last are taps people make; they do nothing.
        val list = listOf(Playlist("a", "One", listOf(1L, 2L, 3L)))
        assertEquals(listOf(1L, 2L, 3L), PlaylistRules.move(list, "a", 0, -1)[0].trackIds)
        assertEquals(listOf(1L, 2L, 3L), PlaylistRules.move(list, "a", 2, 9)[0].trackIds)
    }

    @Test
    fun `a move across the list keeps every track, in the new order`() {
        val list = listOf(Playlist("a", "One", listOf(1L, 2L, 3L, 4L)))
        assertEquals(listOf(2L, 3L, 4L, 1L), PlaylistRules.move(list, "a", 0, 3)[0].trackIds)
    }

    // ---- drawing it ----

    @Test
    fun `the tracks come back in the playlist's order, not the library's`() {
        val library = listOf(track(1, "A"), track(2, "B"), track(3, "C"))
        val playlist = Playlist("a", "One", listOf(3L, 1L))
        assertEquals(listOf("C", "A"), PlaylistRules.tracksOf(playlist, library).map { it.title })
    }

    @Test
    fun `an id the library does not have is SKIPPED when drawn, and still kept in the playlist`() {
        // A volume unmounted or a rescan in progress makes tracks vanish for a while; a store that
        // pruned on sight would quietly eat a playlist the first time that happened.
        val playlist = Playlist("a", "One", listOf(1L, 99L, 2L))
        assertEquals(listOf(1L, 2L), PlaylistRules.tracksOf(playlist, listOf(track(1), track(2))).map { it.id })
        assertEquals(listOf(1L, 99L, 2L), playlist.trackIds)
    }

    @Test
    fun `a duplicated track is drawn as many times as it was added`() {
        val playlist = Playlist("a", "One", listOf(1L, 1L))
        assertEquals(2, PlaylistRules.tracksOf(playlist, listOf(track(1))).size)
    }

    @Test
    fun `playlists are listed A-Z, ignoring case`() {
        val list = listOf(Playlist("a", "zebra", emptyList()), Playlist("b", "Apple", emptyList()))
        assertEquals(listOf("Apple", "zebra"), PlaylistRules.sorted(list).map { it.name })
    }

    @Test
    fun `size is what the row shows, including duplicates and tracks the library has lost`() {
        assertEquals(3, Playlist("a", "One", listOf(1L, 1L, 99L)).size)
    }
}

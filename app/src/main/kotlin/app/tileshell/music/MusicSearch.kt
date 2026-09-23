package app.tileshell.music

/**
 * Resolves "play <name>" against the library: a song, an artist or an album, by code (P6: anything with one
 * right answer is computed, never left to a model). The shell's media session answers a search with it, and
 * Tess asks it first so she never says "Playing X" for an X the library does not have (J5, 2026-09-23: the
 * session answered no search at all, while Tess replied "Playing bloom." to an empty player).
 *
 * Precedence, first hit wins: an exact song title, an exact artist, an exact album; then the same three as
 * "contains" (only for a query of three or more letters, so "a" does not match everything). A song plays
 * from itself within its album; an artist or an album plays from its first track. Tracks keep the library's
 * own order.
 */
object MusicSearch {

    enum class Kind { SONG, ARTIST, ALBUM }

    data class Match(val kind: Kind, val label: String, val queue: List<Track>, val startIndex: Int)

    fun resolve(query: String, library: List<Track>): Match? {
        val q = normalise(query)
        if (q.isEmpty() || library.isEmpty()) return null
        exact(q, library)?.let { return it }
        if (q.length < 3) return null
        return contains(q, library)
    }

    private fun exact(q: String, library: List<Track>): Match? {
        library.firstOrNull { normalise(it.title) == q }?.let { return song(it, library) }
        library.filter { normalise(it.artist) == q }.takeIf { it.isNotEmpty() }?.let { return Match(Kind.ARTIST, it.first().artist, it, 0) }
        library.filter { normalise(it.album) == q }.takeIf { it.isNotEmpty() }?.let { return Match(Kind.ALBUM, it.first().album, it, 0) }
        return null
    }

    private fun contains(q: String, library: List<Track>): Match? {
        library.firstOrNull { normalise(it.title).contains(q) }?.let { return song(it, library) }
        library.firstOrNull { normalise(it.artist).contains(q) }?.let { hit ->
            return Match(Kind.ARTIST, hit.artist, library.filter { it.artist == hit.artist }, 0)
        }
        library.firstOrNull { normalise(it.album).contains(q) }?.let { hit ->
            return Match(Kind.ALBUM, hit.album, library.filter { it.album == hit.album }, 0)
        }
        return null
    }

    private fun song(track: Track, library: List<Track>): Match {
        val album = library.filter { it.album == track.album && it.albumId == track.albumId }
        val queue = album.ifEmpty { listOf(track) }
        return Match(Kind.SONG, "${track.title} by ${track.artist}", queue, queue.indexOf(track).coerceAtLeast(0))
    }

    /** Lower case, letters and digits only, single spaces, no leading "the". */
    fun normalise(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().removePrefix("the ").trim()
}

package app.tileshell.music

import app.tileshell.music.radio.Favourite
import app.tileshell.music.radio.RadioFavourites
import app.tileshell.music.radio.RadioIndex
import app.tileshell.music.radio.RadioText
import app.tileshell.music.radio.Station

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
 *
 * Phase 20 (r3 D6, "one resolver"): the same resolver finds a radio station when it is handed [Stations] — Tess
 * hands them, and the session only for the shell's own controller (`RadioSearchRule`). The library keeps "play
 * <song>": a station is found by name only when it is a favourite or the query says "radio".
 *  1. the query is "radio": the favourite played most recently, else the first favourite; with NO favourite, the
 *     library as above (a song called "Radio"), else nothing;
 *  2. the query ends " radio" or starts "radio ": a favourite by its exact name (with the word, then without it),
 *     the cached directory by exact name, the cached genre that is the rest of the query (its most-clicked station),
 *     the cached directory by a name containing the rest; then the library;
 *  3. any other query: a favourite by its exact name, the library exactly, the library by "contains", and last the
 *     cached directory by exact name. The directory is never contains-matched for a query without "radio".
 * Names are compared as [normalise] makes them; the directory's own keys keep their punctuation ([RadioText.key]),
 * so a directory station whose name has any is found by name only once it is a favourite.
 */
object MusicSearch {

    enum class Kind { SONG, ARTIST, ALBUM, STATION }

    /**
     * What a query resolved to. A library match has its [queue] and where it starts; a station match ([Kind.STATION])
     * has its [station] and an empty [queue] — what plays around it is `RadioFavourites.queueFor`'s to say, at the
     * start. [label] is what Tess names: "Bloom by Radiohead", or the station's name.
     */
    data class Match(val kind: Kind, val label: String, val queue: List<Track>, val startIndex: Int, val station: Station? = null)

    /**
     * The stations a search may find: the favourites with their last-played stamps, and the cached directory. The
     * directory is asked for only when a rule reaches it, so a caller for whom reading it costs something (Tess, the
     * first time in a process) pays only for a query that needs it.
     */
    class Stations(val favourites: List<Favourite>, directory: () -> RadioIndex) {
        val directory: RadioIndex by lazy(directory)

        companion object {
            /** No station at all: the library-only search of J5, and every stranger's. */
            val NONE = Stations(emptyList()) { RadioIndex.EMPTY }
        }
    }

    /** The library-only search (J5): what a controller that is not the shell's own is always answered with. */
    fun resolve(query: String, library: List<Track>): Match? {
        val q = normalise(query)
        if (q.isEmpty() || library.isEmpty()) return null
        exact(q, library)?.let { return it }
        if (q.length < 3) return null
        return contains(q, library)
    }

    /** The ONE resolver (phase 20, r3 D6): rules 1 to 3 above over the library and [stations]. */
    fun resolve(query: String, library: List<Track>, stations: Stations): Match? {
        val q = normalise(query)
        if (q.isEmpty()) return null
        val favourites = stations.favourites
        if (q == RADIO) {
            return (RadioFavourites.lastPlayed(favourites)?.let { station(it) }) ?: resolve(query, library)
        }
        val rest = when {
            q.endsWith(" $RADIO") -> q.removeSuffix(" $RADIO")
            q.startsWith("$RADIO ") -> q.removePrefix("$RADIO ")
            else -> null
        }
        if (rest != null) {
            val found = favourite(q, favourites) ?: favourite(rest, favourites)
                ?: stations.directory.exactName(q)
                ?: stations.directory.ofTag(rest).firstOrNull()
                ?: stations.directory.takeIf { rest.length >= 3 }?.nameContains(rest)?.firstOrNull()
            return found?.let { station(it) } ?: resolve(query, library)
        }
        favourite(q, favourites)?.let { return station(it) }
        resolve(query, library)?.let { return it }
        return stations.directory.exactName(q)?.let { station(it) }
    }

    private const val RADIO = "radio"

    private fun favourite(q: String, favourites: List<Favourite>): Station? =
        favourites.firstOrNull { normalise(it.station.name) == q }?.station

    private fun station(station: Station): Match =
        Match(Kind.STATION, RadioText.shown(station.name, RadioText.NAME_MAX), emptyList(), 0, station)

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

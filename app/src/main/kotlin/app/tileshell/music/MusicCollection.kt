package app.tileshell.music

import app.tileshell.applist.AppIndex
import java.util.Locale

/**
 * The collection's four pivots and the sectioned lists behind them (phase 10 build task 6).
 *
 * Pure, and built on [AppIndex] — the same grouping, folding and jump letters the app list uses. That
 * is deliberate rather than convenient: the app list's LongListSelector was written in-house in phase
 * 01 and retimed to R3/R6's numbers, so a second implementation here would be a second set of rules
 * about what letter "Ólafur Arnalds" files under. One index, one answer, everywhere in the shell.
 *
 * ### Why this is not MangoTile
 *
 * The phase doc's build task 6 says "MangoTile's pivot and LongListSelector with its jump grid (MIT,
 * already a dependency)". Two of those are wrong and it is recorded in the INDEX Change Log rather than
 * silently worked around: MangoTile is **not** a dependency of this build (R2 proposed it; nothing was
 * ever added, and phase 01 wrote the jump grid in-house), and R8 §0.4 found its numbers unusable —
 * it is explicitly WP8-targeted, its one motion claim carries no duration, no easing and no citation.
 * Adding a WP8 kit now to draw a W10M pivot would put un-retimed motion into the one screen phase 10
 * exists to get right. So the pivot is built here on the geometry this shell already has.
 */
enum class MusicPivot(val title: String) {
    /** Groove's own order, and its lowercase pivot headers (R3's pivot form: all lowercase). */
    ALBUMS("albums"),
    ARTISTS("artists"),
    SONGS("songs"),
    PLAYLISTS("playlists"),
}

/** One drawable line of a pivot's list. */
sealed interface CollectionItem {
    val key: String
}

data class LetterHeader(val letter: String) : CollectionItem {
    override val key: String get() = "mhdr:$letter"
}

data class AlbumItem(val album: Album) : CollectionItem {
    override val key: String get() = "malbum:${album.id}"
}

data class ArtistItem(val artist: Artist) : CollectionItem {
    override val key: String get() = "martist:${artist.name}"
}

data class SongItem(val track: Track) : CollectionItem {
    override val key: String get() = "msong:${track.id}"
}

/** A jump-grid cell: the letter, and where it lands — null when the library has nothing under it. */
data class JumpTarget(val letter: String, val index: Int?)

/**
 * One pivot's list: the rows to draw, the jump grid over them, and the tracks that pivot would play in
 * the order it shows them.
 *
 * [queue] is held alongside the items because a tap has to start a QUEUE, not a track: tapping the
 * third song in the songs pivot plays the songs pivot from the third song, which is what every music
 * app does and what makes shuffle and "next" mean anything.
 */
class CollectionPage(
    val items: List<CollectionItem>,
    val jump: List<JumpTarget>,
    val queue: List<Track>,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    /** Where [track] sits in [queue], or 0 — a tap always starts something rather than nothing. */
    fun startIndexOf(track: Track): Int = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
}

object MusicCollection {

    fun albums(tracks: List<Track>, locale: Locale): CollectionPage {
        val albums = MusicGrouping.albums(tracks)
        return build(albums, { it.name }, { it.id.toString() }, locale, ::AlbumItem, albums.flatMap { it.tracks })
    }

    fun artists(tracks: List<Track>, locale: Locale): CollectionPage {
        val artists = MusicGrouping.artists(tracks)
        return build(
            artists, { it.name }, { it.name }, locale, ::ArtistItem,
            artists.flatMap { artist -> artist.albums.flatMap { it.tracks } },
        )
    }

    fun songs(tracks: List<Track>, locale: Locale): CollectionPage {
        val songs = MusicGrouping.songs(tracks)
        return build(songs, { it.title }, { it.id.toString() }, locale, ::SongItem, songs)
    }

    /**
     * Playlists are build task 8's, so this pivot is empty until then — and empty is a real state it
     * has to have anyway: a phone with music and no playlists shows exactly this.
     */
    fun playlists(): CollectionPage = CollectionPage(emptyList(), jumpOver(emptyMap()), emptyList())

    fun page(pivot: MusicPivot, tracks: List<Track>, locale: Locale): CollectionPage = when (pivot) {
        MusicPivot.ALBUMS -> albums(tracks, locale)
        MusicPivot.ARTISTS -> artists(tracks, locale)
        MusicPivot.SONGS -> songs(tracks, locale)
        MusicPivot.PLAYLISTS -> playlists()
    }

    /** An album or artist opened from the collection: its own tracks, in the order they play. */
    fun tracksOf(item: CollectionItem): List<Track> = when (item) {
        is AlbumItem -> item.album.tracks
        is ArtistItem -> item.artist.albums.flatMap { it.tracks }
        is SongItem -> listOf(item.track)
        is LetterHeader -> emptyList()
    }

    private fun <T> build(
        rows: List<T>,
        label: (T) -> String,
        tie: (T) -> String,
        locale: Locale,
        wrap: (T) -> CollectionItem,
        queue: List<Track>,
    ): CollectionPage {
        val items = ArrayList<CollectionItem>(rows.size + AppIndex.JUMP_LETTERS.size)
        val index = HashMap<String, Int>()
        for ((letter, group) in AppIndex.group(rows, label, tie, AppIndex.collator(locale))) {
            // Taken as the header is appended, never counted afterwards: the app list's jump grid was
            // landing short by exactly the height of its top sections when the indices were recomputed.
            index[letter] = items.size
            items += LetterHeader(letter)
            group.forEach { items += wrap(it) }
        }
        return CollectionPage(items, jumpOver(index), queue)
    }

    /** Every letter is a cell, present or not: W10M's jump grid drew the whole alphabet and dimmed the empties. */
    private fun jumpOver(index: Map<String, Int>): List<JumpTarget> =
        AppIndex.JUMP_LETTERS.map { JumpTarget(it, index[it]) }
}

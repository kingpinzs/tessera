package app.tileshell.music

import app.tileshell.applist.AppIndex
import app.tileshell.music.radio.RadioDirectory
import app.tileshell.music.radio.RadioDirectoryStore
import app.tileshell.music.radio.Station
import java.util.Locale

/**
 * The collection's four pivots and the sectioned lists behind them (phase 10 build task 6), and phase 20's fifth.
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

    /** Phase 20 (Q3 A): the fifth pivot, last — internet radio, and the way in to the rest of the streaming side. */
    RADIO("radio"),
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

data class PlaylistItem(val playlist: Playlist) : CollectionItem {
    override val key: String get() = "mpl:${playlist.id}"
}

/** Groove's own first row on this pivot: the playlists pivot is where a playlist is made. */
data object NewPlaylistItem : CollectionItem {
    override val key: String get() = "mpl:new"
}

/** Phase 20: a line of the radio pivot. None of them is a track, a letter or anything the four built pivots draw. */
sealed interface RadioItem : CollectionItem

/** A group's heading with no letter: "favourites", "streaming", "stations". It opens no jump grid. */
data class RadioGroupHeader(val title: String) : RadioItem {
    override val key: String get() = "rhdr:$title"
}

/** A favourite station (`radio_fav:<uuid>`): the one kind of row that draws its logo. */
data class RadioFavouriteItem(val station: Station) : RadioItem {
    override val key: String get() = "rfav:${station.uuid}"
}

/** A station of the directory (`radio_row:<uuid>`). */
data class RadioStationItem(val station: Station) : RadioItem {
    override val key: String get() = "rrow:${station.uuid}"
}

/** One of the pivot's own rows: a way to browse the directory, the refresh, or a way in to the streaming side. */
data class RadioEntryItem(val entry: RadioEntry, val label: String = entry.label) : RadioItem {
    override val key: String get() = "rentry:${entry.name}"
}

/** A sentence the pivot says about the directory: no connection, nothing fetched yet. */
data class RadioNoteItem(val text: String) : RadioItem {
    override val key: String get() = "rnote"
}

enum class RadioEntry(val label: String, val tag: String) {
    SEARCH("search stations", "radio_search"),
    GENRE("by genre", "radio_by_genre"),
    COUNTRY("by country", "radio_by_country"),
    REFRESH("refresh stations", "radio_refresh"),

    /** The MusicBrainz catalogue and its "Listen on" pages (Y5). */
    CATALOGUE("find a song", "music_catalogue_entry"),

    /** The home server's music (Y7). */
    SERVER("home server", "music_server_entry"),
}

/**
 * What the radio pivot has to draw from: the favourites in their order, the directory's stations listed so far (most
 * popular first — the page lists them 100 at a time) and the directory's state.
 */
data class RadioView(
    val favourites: List<Station> = emptyList(),
    val stations: List<Station> = emptyList(),
    val status: RadioDirectoryStore.Status = RadioDirectoryStore.Status.IDLE,
)

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
     * The playlists pivot (build task 8), headed by the row that makes one.
     *
     * The "new playlist" row is FIRST and is always there, which is Groove's own arrangement and also
     * the answer to the empty case: a phone with no playlists shows the one thing there is to do
     * rather than a sentence explaining that there is nothing. The page is therefore never empty, so
     * this pivot has no empty state at all.
     */
    fun playlists(playlists: List<Playlist>, locale: Locale): CollectionPage {
        val items = ArrayList<CollectionItem>(playlists.size + AppIndex.JUMP_LETTERS.size + 1)
        items += NewPlaylistItem
        val index = HashMap<String, Int>()
        val sorted = PlaylistRules.sorted(playlists)
        for ((letter, group) in AppIndex.group(sorted, { it.name }, { it.id }, AppIndex.collator(locale))) {
            index[letter] = items.size
            items += LetterHeader(letter)
            group.forEach { items += PlaylistItem(it) }
        }
        return CollectionPage(items, jumpOver(index), emptyList())
    }

    /** What the radio pivot says when nothing is cached and the network is metered: the list is never fetched unasked there. */
    const val RADIO_METERED_NOTE = "The stations aren't on this phone yet. The list is a large download and this is mobile data."
    const val RADIO_FETCHING_NOTE = "Getting the stations…"
    const val RADIO_DOWNLOAD_LABEL = "download stations"
    const val GROUP_FAVOURITES = "favourites"
    const val GROUP_STREAMING = "streaming"
    const val GROUP_STATIONS = "stations"

    /**
     * The radio pivot (phase 20 build task 6; Y2, a P4 design — Groove's Radio held artist stations, not a directory).
     * Top to bottom:
     *  - what the directory has to say, when it has something: the cache shown with no connection
     *    ([RadioDirectory.OFFLINE_NOTE]), nothing cached and no connection ([RadioDirectory.EMPTY_OFFLINE]), nothing
     *    cached on mobile data ([RADIO_METERED_NOTE]), the first fetch before its first page;
     *  - the favourites, first, under their letter-less heading — they are kept whole, so they list with no cache;
     *  - the browse rows — search, by genre, by country — once there is a directory to browse, and the refresh
     *    (offered as the download while nothing is cached; not while a fetch is already running);
     *  - the two ways in to the rest of the streaming side, always: neither needs the directory, and the home server
     *    plays with no internet at all;
     *  - the stations listed so far, most popular first.
     * So the pivot is never empty, and it has no jump grid: nothing here is filed under a letter.
     */
    fun radio(view: RadioView): CollectionPage {
        val status = view.status
        val items = ArrayList<CollectionItem>(view.favourites.size + view.stations.size + 12)
        when {
            status == RadioDirectoryStore.Status.OFFLINE -> items += RadioNoteItem(RadioDirectory.OFFLINE_NOTE)
            status == RadioDirectoryStore.Status.EMPTY_OFFLINE -> items += RadioNoteItem(RadioDirectory.EMPTY_OFFLINE)
            status == RadioDirectoryStore.Status.EMPTY_METERED -> items += RadioNoteItem(RADIO_METERED_NOTE)
            status == RadioDirectoryStore.Status.FETCHING && view.stations.isEmpty() -> items += RadioNoteItem(RADIO_FETCHING_NOTE)
        }
        if (view.favourites.isNotEmpty()) {
            items += RadioGroupHeader(GROUP_FAVOURITES)
            view.favourites.distinctBy { it.uuid }.forEach { items += RadioFavouriteItem(it) }
        }
        if (view.stations.isNotEmpty()) {
            items += RadioEntryItem(RadioEntry.SEARCH)
            items += RadioEntryItem(RadioEntry.GENRE)
            items += RadioEntryItem(RadioEntry.COUNTRY)
        }
        val busy = status == RadioDirectoryStore.Status.FETCHING || status == RadioDirectoryStore.Status.LOADING
        if (!busy) {
            items += if (view.stations.isEmpty()) RadioEntryItem(RadioEntry.REFRESH, RADIO_DOWNLOAD_LABEL) else RadioEntryItem(RadioEntry.REFRESH)
        }
        items += RadioGroupHeader(GROUP_STREAMING)
        items += RadioEntryItem(RadioEntry.CATALOGUE)
        items += RadioEntryItem(RadioEntry.SERVER)
        if (view.stations.isNotEmpty()) {
            items += RadioGroupHeader(GROUP_STATIONS)
            view.stations.distinctBy { it.uuid }.forEach { items += RadioStationItem(it) }
        }
        return CollectionPage(items, emptyList(), emptyList())
    }

    fun page(pivot: MusicPivot, tracks: List<Track>, playlists: List<Playlist>, locale: Locale, radio: RadioView = RadioView()): CollectionPage =
        when (pivot) {
            MusicPivot.ALBUMS -> albums(tracks, locale)
            MusicPivot.ARTISTS -> artists(tracks, locale)
            MusicPivot.SONGS -> songs(tracks, locale)
            MusicPivot.PLAYLISTS -> playlists(playlists, locale)
            MusicPivot.RADIO -> radio(radio)
        }

    /** An album or artist opened from the collection: its own tracks, in the order they play. */
    fun tracksOf(item: CollectionItem): List<Track> = when (item) {
        is AlbumItem -> item.album.tracks
        is ArtistItem -> item.artist.albums.flatMap { it.tracks }
        is SongItem -> listOf(item.track)
        // A playlist's tracks come from the library, not from the item ([PlaylistRules.tracksOf]).
        is PlaylistItem, NewPlaylistItem, is LetterHeader, is RadioItem -> emptyList()
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

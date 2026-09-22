package app.tileshell.music

/**
 * What the music library holds, and how a flat list of audio files becomes the four pivots Groove showed
 * (phase 10 build task 2).
 *
 * Everything here is pure. The MediaStore query and its observer live in [MusicStore]; this file is the
 * part that has to be right about missing metadata and about grouping, and that can be proved on the JVM
 * — which matters because phase 10 Q6 ruled the library indexes EVERYTHING MediaStore calls audio.
 * Ringtones, alarms and voice recordings are audio, and a ringtone has no album and frequently no
 * artist, so "the metadata is missing" is the normal case here rather than the exceptional one.
 */

/** One audio file, as MediaStore describes it. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val addedAtMs: Long,
) {
    companion object {
        /**
         * What a track with nothing filled in is called.
         *
         * MediaStore reports "<unknown>" for an absent artist rather than null, and an absent title as
         * an empty string; a file with neither still has to appear, be playable and be nameable, so the
         * display name it was given falls back to something a person can read rather than to a blank row.
         */
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
        const val UNKNOWN_TITLE = "Unknown track"

        private val MEDIASTORE_UNKNOWN = setOf("<unknown>", "")

        fun clean(raw: String?, fallback: String): String {
            val trimmed = raw?.trim().orEmpty()
            return if (trimmed.lowercase() in MEDIASTORE_UNKNOWN) fallback else trimmed
        }
    }
}

/** One album: every track sharing a MediaStore album id. */
data class Album(val id: Long, val name: String, val artist: String, val tracks: List<Track>)

/** One artist, and the albums they appear on. */
data class Artist(val name: String, val albums: List<Album>) {
    val trackCount: Int get() = albums.sumOf { it.tracks.size }
}

/**
 * The four pivots, built from one list of tracks.
 *
 * Sorting is case-insensitive throughout: a library that puts "abbey road" after "Zoo Station" because
 * of a capital letter reads as broken, and the jump grid the collection uses would send the letter A to
 * the wrong place.
 */
object MusicGrouping {

    /** Songs, A-Z by title. */
    fun songs(tracks: List<Track>): List<Track> =
        tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    /**
     * Albums, A-Z by name. Grouped by MediaStore's album ID and not by name: two different albums can
     * share a title ("Greatest Hits" is not one album), and the id is what the art is keyed on.
     */
    fun albums(tracks: List<Track>): List<Album> =
        tracks.groupBy { it.albumId }
            .map { (id, group) ->
                Album(
                    id = id,
                    name = group.first().album,
                    // An album whose tracks disagree about the artist is a compilation; naming it after
                    // the first track's artist would be a lie, and W10M called these "Various artists".
                    artist = group.map { it.artist }.distinct().singleOrNull() ?: VARIOUS_ARTISTS,
                    tracks = songs(group),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /**
     * Artists, A-Z. An artist is grouped by NAME, unlike albums: MediaStore's artist id splits the same
     * person across spellings often enough that the name is the more useful key here, and a compilation
     * album appears under each artist that actually has a track on it.
     */
    fun artists(tracks: List<Track>): List<Artist> =
        tracks.groupBy { it.artist }
            .map { (name, group) -> Artist(name, albums(group)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    const val VARIOUS_ARTISTS = "Various artists"
}

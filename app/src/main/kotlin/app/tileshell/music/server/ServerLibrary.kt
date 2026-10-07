package app.tileshell.music.server

import app.tileshell.video.server.ServerTrack
import java.util.Locale

/** An album of the home server's library: its songs in the order they play. [id] is its first song's — the row's tag. */
data class ServerAlbum(val name: String, val artist: String, val tracks: List<ServerTrack>) {
    val id: String get() = tracks.first().id
}

/** An album artist of the home server's library: every song of theirs, album by album. */
data class ServerArtist(val name: String, val tracks: List<ServerTrack>)

/**
 * The home server's songs as the Music app lists them (phase 20 build task 8; Y7): albums, artists and songs, the three
 * groupings the built pivots give the phone's own library. Pure (`ServerLibraryTest`): the server sends one flat
 * listing and everything here is worked out from it — a song's album and album artist are the server's words, and a
 * song with neither is filed under [UNKNOWN_ALBUM] / [UNKNOWN_ARTIST] rather than dropped.
 */
object ServerLibrary {
    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN_ARTIST = "Unknown artist"

    private fun fold(text: String): String = text.trim().lowercase(Locale.ROOT)

    /** The order songs of one album play in: by their number on it, the unnumbered ones after, then by title. */
    private val onAlbum = compareBy<ServerTrack>({ it.index ?: Int.MAX_VALUE }, { fold(it.title) }, { it.id })

    /** Each album once — an album is its name AND its album artist, so two "Greatest Hits" stay two — A to Z. */
    fun albums(tracks: List<ServerTrack>): List<ServerAlbum> =
        tracks.groupBy { fold(it.album) to fold(it.albumArtist) }.values
            .map { songs -> ServerAlbum(songs.first().album.trim().ifEmpty { UNKNOWN_ALBUM }, songs.first().albumArtist.trim().ifEmpty { UNKNOWN_ARTIST }, songs.sortedWith(onAlbum)) }
            .sortedWith(compareBy({ fold(it.name) }, { fold(it.artist) }, { it.id }))

    /** Each album artist once, A to Z, with their albums' songs in album order. */
    fun artists(tracks: List<ServerTrack>): List<ServerArtist> =
        albums(tracks).groupBy { fold(it.artist) }.values
            .map { albums -> ServerArtist(albums.first().artist, albums.flatMap { it.tracks }) }
            .sortedBy { fold(it.name) }

    /** Every song, A to Z by title. */
    fun songs(tracks: List<ServerTrack>): List<ServerTrack> = tracks.sortedWith(compareBy({ fold(it.title) }, { it.id }))

    /** A song row's second line: "Artist • Album", either part left out when the server has none. */
    fun songLine(track: ServerTrack): String = listOf(track.albumArtist.trim(), track.album.trim()).filter { it.isNotEmpty() }.joinToString(" • ")

    fun countText(n: Int): String = if (n == 1) "1 song" else "$n songs"
}

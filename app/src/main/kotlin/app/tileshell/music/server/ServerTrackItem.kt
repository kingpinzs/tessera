package app.tileshell.music.server

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.tileshell.music.MusicSources
import app.tileshell.video.server.MediaServer
import app.tileshell.video.server.ServerRules
import app.tileshell.video.server.ServerTrack

/**
 * A home-server song as the player is given it (phase 20, r3 D1 / D4). TRUST-TOUCHING: this is the ONLY place a
 * server-track `MediaItem` is built — its media id is `server:<id>`, never a decimal, so it can never be read as a
 * library track's; its address is the saved server's direct-play path with NO token; and it sets no `artworkUri`
 * (the session would fetch that itself, from any scheme its data source opens).
 */
object ServerTrackItem {
    const val PREFIX = "server:"

    fun mediaId(track: ServerTrack): String = PREFIX + track.id

    /** An item id as Jellyfin makes it; anything else never becomes part of an address. */
    private val ID = Regex("[0-9A-Fa-f-]{1,64}")

    /**
     * The URL rule: whether [url] is [track]'s direct-play address and nothing more — a server's own address (http or
     * https, a host, a port; no user part: [ServerRules.parse]) followed by exactly `/Audio/<id>/stream?static=true`,
     * so it names no key of its own, and one the token rule would never add a key to ([ServerRules.mayCarryToken]).
     */
    fun accepts(url: String?, track: ServerTrack): Boolean {
        if (url == null || !ID.matches(track.id)) return false
        val base = url.removeSuffix(ServerRules.audioStreamUrl("", track.id))
        if (base == url || ServerRules.parse(base)?.base != base) return false
        return !ServerRules.mayCarryToken(url, base) && ServerRules.reportedUrl(url) == url
    }

    /**
     * The items for songs of the saved server, in order: null where no server is set up or [accepts] refuses the
     * song's address. The server is opened once for the list ([MediaServer.audioStreamUrls]) — it BLOCKS on the sealed
     * store, so this is called off the main thread.
     */
    fun buildAll(server: MediaServer, tracks: List<ServerTrack>): List<MediaItem?> = server.audioStreamUrls(tracks).mapIndexed { i, url -> build(url, tracks[i]) }

    /**
     * The item, or null when [accepts] refuses [url]. The server's measured length rides in the metadata, so the built
     * now-playing form applies (a scrubber, a total), and crossfade and end-of-track work as for a local track.
     */
    fun build(url: String?, track: ServerTrack): MediaItem? {
        if (url == null || !accepts(url, track)) return null
        return MediaItem.Builder()
            .setMediaId(mediaId(track))
            .setUri(MusicSources.own.queuedServer(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.albumArtist)
                    .setAlbumTitle(track.album)
                    .setTrackNumber(track.index)
                    .setDurationMs(track.durationMs.takeIf { it > 0L })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }
}

package app.tileshell.music

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.tileshell.music.radio.LiveMetadata
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The player every consumer sees — the media session (and through it the now-playing screen, the tile
 * and the system's media controls) and the crossfade — with a track's length always known.
 *
 * The primary player index-seeks MP3s (E17, for exact crossfade handbacks), and in that mode Media3 takes
 * the length only from a Xing / Info / VBRI header or an ID3 TLEN frame. A constant-bitrate MP3 written
 * without one — common in older rips — therefore reports C.TIME_UNSET: the total read "0:00" and every
 * scrub computed "a fraction of nothing" and seeked to the start (Jeremy, 2026-09-22, on the phone; J2).
 * MediaStore measured the same file when it was scanned, and that length rides on each MediaItem's
 * metadata (MusicService.mediaItem), so it is reported whenever the extractor has none of its own. The
 * extractor's own value always wins when it has one. Seeking itself needs nothing: the index seeker
 * reads forward to any position whether or not the length is known.
 *
 * ### A station's metadata (phase 20 Decisions "live metadata", r3 D2 / D9)
 *
 * A station's item carries its name as `albumTitle` and `artist` and no `title`; Media3 puts the stream's own
 * StreamTitle in `title`, and with none (no ICY data yet, an HLS station) the title is null. The session builds
 * the platform's metadata — what the Music tile and the notification read — from [getMediaMetadata], so for a
 * `station:` item that is answered here: the title is [LiveMetadata.merge]'s (the song on air through
 * `RadioText.shown`, else the station's name), the album line is always the station, and the art is the logo the
 * shell fetched and bounded ([liveArt]; the item never carries an `artworkUri`). Everything else is the ITEM's own
 * metadata, which the shell built: nothing else a stream sends (its ICY name, its genre) is passed on.
 *
 * Media3's own controllers are not told through that getter: they are handed the value each
 * `onMediaMetadataChanged` carries, which is the wrapped player's. So after every such change of a station —
 * and when its logo arrives ([liveMetadataChanged]) — the listeners are told again with the value above.
 */
class KnownDurationPlayer(
    player: Player,
    /** The bounded logo of the station with this media id, when the shell has it; asked on every read, so it must be cheap. */
    private val liveArt: (mediaId: String) -> ByteArray? = { null },
) : ForwardingPlayer(player) {

    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private val handler = Handler(player.applicationLooper)
    private val retell = Runnable { liveMetadataChanged() }

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // Posted: the listeners are still being handed the wrapped player's value for this change.
                if (MusicLive.isLive(currentMediaItem?.mediaId)) {
                    handler.removeCallbacks(retell)
                    handler.post(retell)
                }
            }
        })
    }

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    override fun getDuration(): Long = known(super.getDuration())

    override fun getContentDuration(): Long = known(super.getContentDuration())

    override fun getMediaMetadata(): MediaMetadata {
        val reported = super.getMediaMetadata()
        val item = currentMediaItem ?: return reported
        if (!MusicLive.isLive(item.mediaId)) return reported
        val own = item.mediaMetadata
        val shown = LiveMetadata.merge(own.albumTitle?.toString(), reported.title?.toString())
        val meta = own.buildUpon().setTitle(shown.title).setAlbumTitle(shown.albumTitle)
        if (own.artworkData == null) liveArt(item.mediaId)?.let { meta.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        return meta.build()
    }

    /** A station's shown metadata changed with no change in the player (its logo arrived): every listener is told. */
    fun liveMetadataChanged() {
        if (!MusicLive.isLive(currentMediaItem?.mediaId)) return
        val shown = mediaMetadata
        listeners.forEach { it.onMediaMetadataChanged(shown) }
    }

    private fun known(reported: Long): Long {
        if (reported != C.TIME_UNSET) return reported
        return currentMediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0L } ?: C.TIME_UNSET
    }
}

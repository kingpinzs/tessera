package app.tileshell.music

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

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
 */
class KnownDurationPlayer(player: Player) : ForwardingPlayer(player) {

    override fun getDuration(): Long = known(super.getDuration())

    override fun getContentDuration(): Long = known(super.getContentDuration())

    private fun known(reported: Long): Long {
        if (reported != C.TIME_UNSET) return reported
        return currentMediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0L } ?: C.TIME_UNSET
    }
}

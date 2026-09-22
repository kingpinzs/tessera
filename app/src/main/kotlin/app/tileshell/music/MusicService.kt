package app.tileshell.music

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.tileshell.diag.Diagnostics

/**
 * The shell's own playback (phase 10 build tasks 3 and 4).
 *
 * A [MediaSessionService] rather than playback inside the activity, and that is the whole point rather
 * than a detail:
 *
 *  - **It outlives Start.** Acceptance row E7: killing the shell must not stop the music. A player that
 *    lived in the activity would die with it.
 *  - **It is what Android draws the notification transport from**, so the lock-screen and shade controls
 *    are the session's, not a second thing to build and keep in step.
 *  - **It is what phase 10 Q4's tile rule keys on.** The face belongs to the tile of the app that owns
 *    the session; this service is what makes the shell's own player an owner like any other, which is
 *    why [app.tileshell.feeds.MusicFeed] needed no special case for it.
 *
 * Audio focus is handed to ExoPlayer (`handleAudioFocus = true`) rather than managed here: it is what
 * ducks for a notification, pauses for a call and — importantly — does NOT resume after a transient loss
 * the user did not ask to resume, which is E9. Becoming-noisy is handled too, so pulling the headphones
 * out pauses instead of playing the room.
 */
class MusicService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
        Diagnostics.add("music", "playback service started")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Nothing playing and nothing to come back to: stop rather than sit in the foreground holding a
     * notification for a player that is not playing.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        Diagnostics.add("music", "playback service stopped")
        super.onDestroy()
    }

    companion object {
        /** A track as Media3 sees it: the MediaStore URI, and the metadata the notification shows. */
        fun mediaItem(track: Track): MediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(MusicStore.uriOf(track))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }
}

package app.tileshell.recorder

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import app.tileshell.diag.Diagnostics

/**
 * Playback of one recording (r11/voice-recorder.md §4), in a Media3 session of its own whose id is
 * [SESSION_ID] (4.9, T15-34): Android's media controls show the recording's name as W10M's volume panel did,
 * and Start tells this session from Music's by that id, so a recording playing never lands on Music's tile or
 * any other (build task 0's routing sends `recorder` to no tile).
 *
 * One per process, made on the main thread when the first playback page opens ([get]) and released with the
 * Voice Recorder activity ([release]) — Media3 refuses two live sessions with one id, and the playback and trim
 * pages hand over to each other, so they share this one and each loads what it shows. Usage MEDIA (E16), so it
 * is a normal player to the audio system: it takes focus, Music pauses for it, a headset unplugging pauses it.
 */
class RecorderPlayer private constructor(context: Context) {

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val session: MediaSession = MediaSession.Builder(context, exo).setId(SESSION_ID).build()

    private var loadedId: Long? = null

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Diagnostics.add("recorder", "playback error: ${error.errorCodeName}")
            }
        })
    }

    /** Load [recording], ready at its start; [play] starts it. Loading what is already loaded restarts it. */
    fun load(recording: Recording) {
        val item = MediaItem.Builder()
            .setUri(RecordingStore.uriOf(recording.id))
            .setMediaId(recording.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(recording.name)
                    .setArtist("Voice Recorder")
                    .build(),
            )
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        loadedId = recording.id
        Diagnostics.add("recorder", "playback loaded ${recording.id} (session id $SESSION_ID)")
    }

    fun play() = exo.play()
    fun pause() = exo.pause()

    fun toggle() {
        if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun seekTo(ms: Long) = exo.seekTo(ms.coerceAtLeast(0L))

    val positionMs: Long get() = exo.currentPosition.coerceAtLeast(0L)

    /** The player's duration once known, else [fallbackMs] (MediaStore's). */
    fun durationMs(fallbackMs: Long): Long = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: fallbackMs

    val isPlaying: Boolean get() = exo.isPlaying

    /** The page that was playing has closed: stop and let go of the item (the session stays for the next page). */
    fun stop() {
        exo.stop()
        exo.clearMediaItems()
        loadedId = null
    }

    private fun destroy() {
        session.release()
        exo.release()
        Diagnostics.add("recorder", "playback released")
    }

    companion object {
        /** The Media3 session id Start's tile routing reads (TileRouting; any id but `music` goes to no tile). */
        const val SESSION_ID = "recorder"

        private var instance: RecorderPlayer? = null

        /** The process's player, made on first use (main thread). */
        fun get(context: Context): RecorderPlayer =
            instance ?: RecorderPlayer(context.applicationContext).also { instance = it }

        /** Release the player and its session; the next [get] makes a new one. */
        fun release() {
            instance?.destroy()
            instance = null
        }
    }
}

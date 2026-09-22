package app.tileshell.music

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.tileshell.diag.Diagnostics

/**
 * The collection's handle on [MusicService] (phase 10 build task 6).
 *
 * A [MediaController] rather than a direct reference to the player, because the player lives in a
 * service that outlives this activity (E7): the collection is a CLIENT of the session, exactly as the
 * notification transport and a Bluetooth headset are, and it gets the same answers they do. It also
 * means a collection opened while something is already playing joins that session instead of starting
 * a second player — which is the bug this shape makes impossible rather than guards against.
 *
 * Held as an object because there is one session and the connection survives a rotation; the activity
 * releases it when it is finished for good.
 */
object MusicPlayer {

    private var controller: MediaController? = null
    private var connecting = false

    /** The media id (the MediaStore track id, as a string) that is loaded right now, for the drawn list. */
    var nowPlayingId by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
            nowPlayingId = item?.mediaId
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }
    }

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context.applicationContext, ComponentName(context.applicationContext, MusicService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
            connecting = false
            controller = runCatching { future.get() }.getOrElse {
                Diagnostics.add("music", "controller did not connect: ${it.javaClass.simpleName}")
                null
            }
            controller?.let {
                it.addListener(listener)
                nowPlayingId = it.currentMediaItem?.mediaId
                isPlaying = it.isPlaying
                Diagnostics.add("music", "controller connected, ${it.mediaItemCount} item(s) in the queue")
            }
        }, context.mainExecutor)
    }

    /**
     * Play [queue] from [startIndex].
     *
     * The whole queue is set, not one track: tapping the third song plays the list from the third song,
     * which is what makes "next", shuffle and repeat mean anything. Tapping the track that is already
     * loaded resumes it rather than restarting it, because a tap on the playing row is a person asking
     * for it to keep going, not to hear the first second again.
     */
    fun play(queue: List<Track>, startIndex: Int) {
        val c = controller ?: run {
            Diagnostics.add("music", "play ignored: no controller yet")
            return
        }
        val track = queue.getOrNull(startIndex) ?: return
        if (c.currentMediaItem?.mediaId == track.id.toString()) {
            c.play()
            return
        }
        c.setMediaItems(queue.map { MusicService.mediaItem(it) }, startIndex, 0L)
        c.prepare()
        c.play()
        Diagnostics.add("music", "play ${track.title} (${startIndex + 1} of ${queue.size})")
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        Diagnostics.add("music", "controller released")
    }
}

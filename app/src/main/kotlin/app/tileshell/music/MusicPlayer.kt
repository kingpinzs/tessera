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

    /** One line of the play queue, as the session reports it (phase 10 build task 7, R8 §1.9). */
    data class QueueEntry(val id: String, val title: String, val artist: String, val album: String)

    /** The media id (the MediaStore track id, as a string) that is loaded right now, for the drawn list. */
    var nowPlayingId by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    // What the now-playing screen draws. Read off the SESSION rather than off the tap that started it,
    // so the screen is right about music this activity did not start — a headset button, the tile's own
    // transport strip, or a queue that was already playing when the app was opened.
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var albumId by mutableStateOf<Long?>(null)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatOn by mutableStateOf(false)
        private set
    var queue by mutableStateOf<List<QueueEntry>>(emptyList())
        private set
    var queueIndex by mutableStateOf(0)
        private set

    // Task 9: what the SERVICE says it is doing, read off the session's extras. The service owns the
    // timer and the effect; this only mirrors them, so it is right about a timer set from anywhere.
    var sleepAt by mutableStateOf(0L)
        private set
    var sleepEndOfTrack by mutableStateOf(false)
        private set
    var eqPreset by mutableStateOf(Equaliser.OFF)
        private set
    var eqPresets by mutableStateOf<List<String>>(emptyList())
        private set
    var eqAvailable by mutableStateOf(false)
        private set

    private fun readExtras(extras: android.os.Bundle) {
        sleepAt = extras.getLong(MusicCommands.X_SLEEP_AT, 0L)
        sleepEndOfTrack = extras.getBoolean(MusicCommands.X_SLEEP_END_OF_TRACK, false)
        eqPreset = extras.getInt(MusicCommands.X_EQ_PRESET, Equaliser.OFF)
        eqPresets = extras.getStringArray(MusicCommands.X_EQ_PRESETS)?.toList().orEmpty()
        eqAvailable = extras.getBoolean(MusicCommands.X_EQ_AVAILABLE, false)
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) = readExtras(extras)
    }

    /** Minutes (> 0), [SleepTimer.END_OF_TRACK], or [SleepTimer.OFF]. The service decides; this asks. */
    fun setSleep(minutes: Int) {
        val c = controller ?: return
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(MusicCommands.SLEEP, android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putInt(MusicCommands.ARG_MINUTES, minutes) },
        )
    }

    /** A preset index, or [Equaliser.OFF]. */
    fun setEqualiser(preset: Int) {
        val c = controller ?: return
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(MusicCommands.EQUALISER, android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putInt(MusicCommands.ARG_PRESET, preset) },
        )
    }

    /** The library is what knows about albums; a media session only ever names a track. */
    private fun albumIdOf(mediaId: String?): Long? =
        mediaId?.toLongOrNull()?.let { id -> MusicStore.library.value.firstOrNull { it.id == id }?.albumId }

    private fun readSession() {
        val c = controller ?: return
        val item = c.currentMediaItem
        nowPlayingId = item?.mediaId
        val meta = item?.mediaMetadata
        title = meta?.title?.toString().orEmpty()
        artist = meta?.artist?.toString().orEmpty()
        album = meta?.albumTitle?.toString().orEmpty()
        albumId = albumIdOf(item?.mediaId)
        // A duration of C.TIME_UNSET is negative; it means "not known yet", not "zero seconds".
        durationMs = c.duration.takeIf { it > 0L } ?: 0L
        positionMs = c.currentPosition.coerceAtLeast(0L)
        isPlaying = c.isPlaying
        shuffleOn = c.shuffleModeEnabled
        repeatOn = c.repeatMode != Player.REPEAT_MODE_OFF
        queueIndex = c.currentMediaItemIndex
        queue = (0 until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            QueueEntry(
                m.mediaId,
                m.mediaMetadata.title?.toString().orEmpty(),
                m.mediaMetadata.artist?.toString().orEmpty(),
                m.mediaMetadata.albumTitle?.toString().orEmpty(),
            )
        }
    }

    /** The elapsed label and the thumb are the only things that move on their own; the screen ticks them. */
    fun refreshPosition() {
        val c = controller ?: return
        positionMs = c.currentPosition.coerceAtLeast(0L)
        if (durationMs <= 0L) durationMs = c.duration.takeIf { it > 0L } ?: 0L
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // One handler rather than eight: every event this screen cares about ends in the same
            // re-read, and a partial listener is how a screen ends up right about the title and wrong
            // about the duration.
            readSession()
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        // What every player does and what a person expects: restart this track unless you are already
        // near its start, in which case go back one.
        val c = controller ?: return
        if (c.currentPosition > RESTART_MS) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
        positionMs = ms.coerceAtLeast(0L)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        Diagnostics.add("music", "shuffle ${if (c.shuffleModeEnabled) "on" else "off"}")
    }

    /** R8 §1.6: repeat is a three-state control — off, all, one — and its glyph carries a "1" badge. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        Diagnostics.add("music", "repeat ${when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> "off"
            Player.REPEAT_MODE_ALL -> "all"
            else -> "one"
        }}")
    }

    /** A row of the open queue was tapped (R8 §1.9). */
    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    /** How far into a track "previous" means restart rather than go back one. */
    private const val RESTART_MS = 3_000L

    fun connect(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context.applicationContext, ComponentName(context.applicationContext, MusicService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).setListener(controllerListener).buildAsync()
        future.addListener({
            connecting = false
            controller = runCatching { future.get() }.getOrElse {
                Diagnostics.add("music", "controller did not connect: ${it.javaClass.simpleName}")
                null
            }
            controller?.let {
                it.addListener(listener)
                readSession()
                readExtras(it.sessionExtras)
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

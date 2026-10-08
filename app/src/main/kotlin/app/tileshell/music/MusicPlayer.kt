package app.tileshell.music

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.tileshell.diag.Diagnostics
import app.tileshell.music.radio.LiveMetadata
import app.tileshell.music.radio.RadioFavourites
import app.tileshell.music.radio.RadioFavouritesStore
import app.tileshell.music.radio.RadioNet
import app.tileshell.music.radio.StationItem
import app.tileshell.music.radio.StationLogos
import app.tileshell.music.radio.StationStart
import app.tileshell.music.radio.StreamLine
import app.tileshell.music.server.ServerTrackItem

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

    /** Phase 18: the art a file played outside the library carries in its own tags (it has no album row); else null. */
    var fileArt by mutableStateOf<ByteArray?>(null)
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
    var crossfadeMs by mutableStateOf(Crossfade.OFF)
        private set

    private fun readExtras(extras: android.os.Bundle) {
        sleepAt = extras.getLong(MusicCommands.X_SLEEP_AT, 0L)
        sleepEndOfTrack = extras.getBoolean(MusicCommands.X_SLEEP_END_OF_TRACK, false)
        eqPreset = extras.getInt(MusicCommands.X_EQ_PRESET, Equaliser.OFF)
        eqPresets = extras.getStringArray(MusicCommands.X_EQ_PRESETS)?.toList().orEmpty()
        eqAvailable = extras.getBoolean(MusicCommands.X_EQ_AVAILABLE, false)
        crossfadeMs = extras.getInt(MusicCommands.X_CROSSFADE_MS, Crossfade.OFF)
        streamState = extras.getString(MusicCommands.X_STREAM_STATE)
        meteredLine = extras.getString(MusicCommands.X_METERED_LINE)
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

    /** One of [Crossfade.Choice]'s lengths in ms, or [Crossfade.OFF]. The service owns the fade; this asks. */
    fun setCrossfade(ms: Int) {
        val c = controller ?: return
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(MusicCommands.CROSSFADE, android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putInt(MusicCommands.ARG_MS, ms) },
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
        val live = MusicLive.isLive(item?.mediaId)
        // Phase 20: a station's logo arrives once and its title changes with every song; the logo last seen for THIS
        // station is kept, so a title change never blanks the art.
        if (!live || item?.mediaId != nowPlayingId) liveArt = null
        nowPlayingId = item?.mediaId
        val meta = item?.mediaMetadata
        // A station's item has no title of its own: the song on air is the SESSION's metadata (the stream's own
        // StreamTitle), shown only through LiveMetadata — the station's name when the stream names no song.
        title = if (live) LiveMetadata.merge(meta?.albumTitle?.toString(), c.mediaMetadata.title?.toString()).title else meta?.title?.toString().orEmpty()
        artist = meta?.artist?.toString().orEmpty()
        album = meta?.albumTitle?.toString().orEmpty()
        albumId = albumIdOf(item?.mediaId)
        fileArt = if (MusicFile.isFile(item?.mediaId)) meta?.artworkData else null
        // Review R20-2: never the session's art for a station — a stream can carry a picture of its own, of any size,
        // and the session's first value of a change is the stream's raw metadata. The logo is the one the shell
        // fetched and bounded itself (StationLogos; this process, as the service is), read when the session says
        // anything changed — which it does when the logo arrives.
        if (live) StationLogos.cachedFor(item?.mediaId)?.let { liveArt = it }
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
                // Phase 20: a station's row has no title; it is listed by its name, which its album line carries.
                m.mediaMetadata.title?.toString().orEmpty().ifEmpty { m.mediaMetadata.albumTitle?.toString().orEmpty() },
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

    /** A play asked for before the controller connected, done the moment it does (Tess starting music, J5). */
    private var pendingPlay: Pair<List<Track>, Int>? = null

    /** Phase 18: a file asked for before the controller connected. The newer of this and [pendingPlay] is the one done. */
    private var pendingFile: String? = null

    /**
     * Phase 20: a queue of the shell's own items that are not library tracks — stations, the home server's songs —
     * each built by its one builder, with where it starts and what its start is called in the `play` line.
     */
    private class OwnQueue(val items: List<MediaItem>, val start: Int, val label: String)

    /** Phase 20: such a queue asked for before the controller connected. The newest of the three pending asks is the one done. */
    private var pendingOwn: OwnQueue? = null

    /**
     * Phase 18 ("below Q-18-2"): ask the service to play one file by its `content://` URI of the shell's
     * FileProvider, as a one-item queue outside the library. The service decides (and checks the URI again).
     */
    fun playFile(uri: String) {
        val c = controller ?: run {
            pendingPlay = null
            pendingOwn = null
            pendingFile = uri
            Diagnostics.add("music", "play queued until the controller connects")
            return
        }
        c.sendCustomCommand(
            androidx.media3.session.SessionCommand(MusicCommands.PLAY_FILE, android.os.Bundle.EMPTY),
            android.os.Bundle().apply { putString(MusicCommands.ARG_URI, uri) },
        )
    }

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
                pendingPlay?.let { (queue, start) ->
                    pendingPlay = null
                    play(queue, start)
                }
                pendingFile?.let { uri ->
                    pendingFile = null
                    playFile(uri)
                }
                pendingOwn?.let { own ->
                    pendingOwn = null
                    playOwn(own)
                }
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
            // Kept, not dropped: whoever asked (Tess, starting the player herself) has already been told it plays.
            pendingPlay = queue to startIndex
            pendingFile = null
            pendingOwn = null
            Diagnostics.add("music", "play queued until the controller connects")
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

    // ---- phase 20: stations and the home server's tracks -------------------------------------------
    // What the now-playing screen draws for a live item, read off the SESSION like everything above.

    /** "Reconnecting…" / "This station isn't answering" while the reconnect clock runs (X_STREAM_STATE); else null. */
    var streamState by mutableStateOf<String?>(null)
        private set

    /** "Streaming over mobile data" (or its Data Saver form) while a station plays on a metered network; else null. */
    var meteredLine by mutableStateOf<String?>(null)
        private set

    /** The playing station's logo, fetched and bounded by the shell ([StationLogos] — never an artworkUri, never the session's art); null draws the placeholder. */
    var liveArt by mutableStateOf<ByteArray?>(null)
        private set

    /** The loaded item is a station: the mark alone, never the duration (an HLS live window reports one). */
    val isLive: Boolean get() = MusicLive.isLive(nowPlayingId)

    /**
     * Play [station] through the shell's own controller: the queue is RadioFavourites.queueFor's and always
     * replaces the queue. Answers null when it started (or is queued until the controller connects), else the
     * text the page shows instead ("can't play this station", "Sign in to this Wi-Fi network first", …).
     */
    fun playStations(context: Context, station: app.tileshell.music.radio.Station): String? {
        val appContext = context.applicationContext
        // The network's answer first, then the station's own address, then the favourites around it (StationStart).
        val plan = StationStart.plan(RadioNet.gate(appContext, isStation = true), RadioFavourites.queueFor(station, RadioFavouritesStore.get(appContext).stations()), RadioNet.qaHost(appContext))
        plan.lines.forEach { Diagnostics.add(StreamLine.TAG, it) }
        plan.refusal?.let { return it }
        val items = MusicService.stationItems(appContext, plan.stations)
        val start = plan.start.takeIf { it in items.indices } ?: return StationItem.CANT_PLAY
        connect(appContext)
        playOwn(OwnQueue(items, start, "station ${plan.stations[start].name}"))
        return null
    }

    /** Counts the asks of [playServerTracks]: the newest wins when two lists are being built at once. */
    private var serverAsk = 0L

    /**
     * Play the home server's [tracks] from [startIndex], built by ServerTrackItem. Called on the main thread; the
     * items are built on a thread of their own — the server's address is read from the sealed store (a file and the
     * Keystore) — and the queue is set back on the main thread, where [done] is told null when it started, else the
     * text to show.
     */
    fun playServerTracks(context: Context, server: app.tileshell.video.server.MediaServer, tracks: List<app.tileshell.video.server.ServerTrack>, startIndex: Int, done: (String?) -> Unit) {
        val appContext = context.applicationContext
        val ask = ++serverAsk
        Thread({
            // No network gate: a home server on a LAN with no internet must play (StreamGate exempts a server track
            // from both refusals), and the metered caption is a station's.
            val built = ServerTrackItem.buildAll(server, tracks)
            appContext.mainExecutor.execute {
                if (ask != serverAsk) return@execute
                val start = MusicQueueStart.own(built.map { it != null }, startIndex)
                if (start == null) return@execute done(CANT_PLAY_TRACK)
                connect(appContext)
                playOwn(OwnQueue(built.filterNotNull(), start, MusicQueueStart.lineQuery(tracks[startIndex].title)))
                done(null)
            }
        }, "music-server-queue").start()
    }

    /** What a home-server row says when its song has no address the builder accepts (no server set up, an id that is not one). */
    const val CANT_PLAY_TRACK = "can't play this song"

    /** Set [own] through the shell's own controller, ALWAYS replacing the queue; kept until the controller connects. */
    private fun playOwn(own: OwnQueue) {
        val c = controller ?: run {
            pendingOwn = own
            pendingPlay = null
            pendingFile = null
            Diagnostics.add("music", "play queued until the controller connects")
            return
        }
        c.setMediaItems(own.items, own.start, 0L)
        c.prepare()
        c.play()
        Diagnostics.add("music", "play ${own.label} (${own.start + 1} of ${own.items.size})")
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        Diagnostics.add("music", "controller released")
    }
}

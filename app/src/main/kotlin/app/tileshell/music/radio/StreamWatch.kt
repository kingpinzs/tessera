package app.tileshell.music.radio

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import app.tileshell.diag.Diagnostics
import app.tileshell.music.MusicLive
import java.io.IOException

/**
 * What `MusicService` does about a stream that is not a file (phase 20 build task 3): the reconnect clock of a live
 * item, the lines that say a stream connected or was refused a redirect, the metered-data caption, and the two
 * calls a user's own start of a station makes. It sits beside the service as `CrossfadeFader` does, listens to the
 * service's ONE player and never hands it an item; every decision is a pure rule's ([StreamRetry], [StreamFacts],
 * [StreamGate], [StreamLine]) and this file only carries the answers out.
 *
 * ### The one clock (Decisions "reconnect", r3 D8)
 *
 * T0 is the first LOAD error of a live item — ExoPlayer retries the load itself six times before it reports a player
 * error — and writes `stream: lost, retrying`, once. From T0 the service holds its own partial wake lock (the
 * player's wake mode lets go once it stops buffering) and the session's extras say "Reconnecting…". On each player
 * error the player is prepared again after [StreamRetry.next]'s delay; at T0 + 60 s it is paused, the line is
 * `stream: gave up after 60000 ms` and the extras say "This station isn't answering". A stream its server CLOSED —
 * which the player reads as the item's end, with no error at all — is put on the same clock ([StreamFacts.streamEnded]),
 * so a station never goes silent, or hands over to the next favourite, without a word. A stream that returns inside
 * the window writes `stream: reconnected after <ms> ms` and goes on with no tap. A pause, or another item, ends the
 * clock without a word. A home-server track and a library track are never on it.
 *
 * [state] and [meteredLine] are what the service puts in the session's extras; [changed] says one of them moved.
 */
class StreamWatch(
    context: Context,
    private val player: ExoPlayer,
    private val handler: Handler,
    private val changed: () -> Unit,
) {
    private val app = context.applicationContext

    /** [StreamRetry.RECONNECTING] or [StreamRetry.NOT_ANSWERING] while that is so, else null. */
    var state: String? = null
        private set

    /** [StreamGate]'s caption while a station is loaded on a metered network, else null. */
    var meteredLine: String? = null
        private set

    private var clock = StreamRetry.Clock()

    /** The end of what was buffered at the last look, while the stream is lost ([StreamFacts.returned]). */
    private var bufferedAtLastLook = 0L

    /** The item whose `stream: connected` line is written; one line for each time an item is come to. */
    private var connectedFor: String? = null

    /** The item whose redirect was refused, until it is prepared afresh or left: the line is written once. */
    private var redirectRefusedFor: String? = null
    private var playbackState = Player.STATE_IDLE

    /** Where each streamed item's first load went: the address its `connected` line names. */
    private val addresses = HashMap<String, String>()

    private val wake = app.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera:stream-reconnect").apply { setReferenceCounted(false) }
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private var watchingNetwork = false

    private val retry = Runnable { if (clock.lost && liveId() != null) player.prepare() }
    private val giveUp = Runnable { gaveUp() }
    private val look = object : Runnable {
        override fun run() {
            if (!clock.lost || returnedNow()) return
            bufferedAtLastLook = player.bufferedPosition
            handler.postDelayed(this, StreamFacts.CHECK_MS)
        }
    }
    private val readNetwork = Runnable {
        val line = if (liveId() != null) RadioNet.gate(app, isStation = true).meteredLine else null
        if (line != meteredLine) {
            meteredLine = line
            changed()
        }
    }

    /** Registered only while a live item is loaded: the caption follows the network the phone moves to. */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = networkMoved()
        override fun onLost(network: Network) = networkMoved()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = networkMoved()
    }

    /** The item the player is on and its place in the queue, as the last move left them: what the next move comes FROM. */
    private var loadedId: String? = null
    private var loadedIndex = C.INDEX_UNSET

    /** The station a move of the player's own is being undone to ([StreamFacts.Ended.MOVED_ON]). */
    private var returningTo: String? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            val fromId = loadedId
            val fromIndex = loadedIndex
            val returning = returningTo
            loadedId = id
            loadedIndex = player.currentMediaItemIndex
            returningTo = null
            // A station's server closed the stream: the player read that as the item's end (see StreamFacts.streamEnded).
            when (StreamFacts.streamEnded(reason, fromId, id, returning, MusicLive::isLive)) {
                StreamFacts.Ended.MOVED_ON -> if (fromIndex in 0 until player.mediaItemCount && player.getMediaItemAt(fromIndex).mediaId == fromId) {
                    returningTo = fromId
                    player.seekTo(fromIndex, C.TIME_UNSET)
                    return
                }
                StreamFacts.Ended.LOST -> return streamEnded()
                null -> Unit
            }
            endClock()
            connectedFor = null
            redirectRefusedFor = null
            state = null
            MusicLive.stationUuid(id)?.takeIf { StreamFacts.userStarted(reason) }?.let { userStarted(it) }
            watchNetwork(MusicLive.isLive(id))
            meteredLine = if (MusicLive.isLive(id)) RadioNet.gate(app, isStation = true).meteredLine else null
            changed()
            // A gapless move to the next server track never stops sounding, so nothing else would say it connected.
            if (player.isPlaying) connected()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Prepared afresh (a tap on play after the refusal): the next refusal is said again.
            if (this@StreamWatch.playbackState == Player.STATE_IDLE && playbackState == Player.STATE_BUFFERING) redirectRefusedFor = null
            this@StreamWatch.playbackState = playbackState
            // The last item of the queue, and a station: its server closed the stream (StreamFacts.streamEnded).
            if (playbackState == Player.STATE_ENDED && liveId() != null) streamEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) return
            connected()
            if (clock.lost) returnedNow()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                // Asked to play again after "This station isn't answering": that is said again only if it is so again.
                redirectRefusedFor = null
                if (state != null && !clock.lost) {
                    state = null
                    changed()
                }
            } else if (clock.lost) {
                // Paused while lost — by the person, a call, the headphones: nobody is waiting for it to come back.
                endClock()
                state = null
                changed()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = liveId() ?: return
            if (!player.playWhenReady) return
            if (StreamFacts.redirectRefused(messages(error))) return redirectRefused(id)
            if (redirectRefusedFor == id) return
            failed(SystemClock.elapsedRealtime())
        }
    }

    /** The player stopped on a station at [now]: T0 when this is the first sign, then the clock's next step. */
    private fun failed(now: Long) {
        lost(now)
        val (next, step) = StreamRetry.onPlayerError(clock, now)
        clock = next
        handler.removeCallbacks(retry)
        when (step) {
            is StreamRetry.Step.Retry -> handler.postDelayed(retry, step.delayMs)
            is StreamRetry.Step.GiveUp -> {
                handler.removeCallbacks(giveUp)
                handler.postDelayed(giveUp, step.inMs)
            }
        }
    }

    /**
     * The station's stream ended without an error — its server closed it. The player is stopped where it is (the
     * queue and the place kept: the state a player error leaves it in), so the clock's `prepare()` is the reconnect
     * and its back-off holds against a server that accepts and closes again at once.
     */
    private fun streamEnded() {
        if (!player.playWhenReady || liveId() == null) return
        player.stop()
        failed(SystemClock.elapsedRealtime())
    }

    private val analytics = object : AnalyticsListener {
        override fun onLoadStarted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, retryCount: Int) {
            val id = idOf(eventTime) ?: return
            if (!MusicLive.isLive(id) && !MusicLive.isServer(id)) return
            if (addresses.size >= ADDRESSES_KEPT) addresses.clear()
            if (id !in addresses) addresses[id] = loadEventInfo.dataSpec.uri.toString()
        }

        override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
            val id = idOf(eventTime) ?: return
            if (wasCanceled || id != liveId() || !player.playWhenReady) return
            if (StreamFacts.redirectRefused(messages(error))) return redirectRefused(id)
            if (redirectRefusedFor == id) return
            lost(SystemClock.elapsedRealtime())
        }
    }

    init {
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
    }

    /** The loaded item's media id when it is a station, else null. */
    private fun liveId(): String? = player.currentMediaItem?.mediaId?.takeIf { MusicLive.isLive(it) }

    /** The media id of the item an analytics event is about (a load can be the NEXT item's). */
    private fun idOf(eventTime: AnalyticsListener.EventTime): String? {
        val timeline = eventTime.timeline
        if (eventTime.windowIndex !in 0 until timeline.windowCount) return null
        return timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem.mediaId
    }

    private fun messages(error: Throwable): List<String?> = generateSequence(error) { it.cause }.take(CAUSES_READ).map { it.message }.toList()

    /** A user's own start of the station [uuid]: the directory's click counter, and the favourite's last-played stamp. */
    private fun userStarted(uuid: String) {
        RadioDirectoryStore.get(app).click(uuid)
        // The stamp is a file write.
        Thread({ RadioFavouritesStore.get(app).played(uuid) }, "radio-played").start()
    }

    /** `stream: connected <address> codec=<codec>`, once for each time a station or a server track is come to. */
    private fun connected() {
        val id = player.currentMediaItem?.mediaId ?: return
        if (id == connectedFor || !(MusicLive.isLive(id) || MusicLive.isServer(id))) return
        connectedFor = id
        Diagnostics.add(StreamLine.TAG, StreamLine.connected(addresses[id].orEmpty(), StreamFacts.codec(player.audioFormat?.sampleMimeType)))
    }

    /** T0, when this is the first error since the stream last played; nothing otherwise. */
    private fun lost(now: Long) {
        val (next, first) = StreamRetry.onLoadError(clock, now)
        clock = next
        if (!first) return
        Diagnostics.add(StreamLine.TAG, StreamLine.LOST)
        wake.acquire(StreamRetry.WAKE_LOCK_MS)
        bufferedAtLastLook = player.bufferedPosition
        state = StreamRetry.RECONNECTING
        handler.postDelayed(giveUp, StreamRetry.WINDOW_MS)
        handler.postDelayed(look, StreamFacts.CHECK_MS)
        changed()
    }

    /** Whether the lost stream is back; when it is, the clock ends and the line says how long it was gone. */
    private fun returnedNow(): Boolean {
        if (!StreamFacts.returned(player.isPlaying, bufferedAtLastLook, player.bufferedPosition)) return false
        StreamRetry.reconnectedAfter(clock, SystemClock.elapsedRealtime())?.let { Diagnostics.add(StreamLine.TAG, StreamLine.reconnected(it)) }
        endClock()
        state = null
        changed()
        return true
    }

    private fun gaveUp() {
        if (!clock.lost || returnedNow()) return
        // The clock ends BEFORE the pause, so the pause is not read as the person's own.
        endClock()
        state = StreamRetry.NOT_ANSWERING
        Diagnostics.add(StreamLine.TAG, StreamLine.gaveUp())
        player.pause()
        changed()
    }

    /** A redirect between https and http: said once, never put on the clock, and the station is left paused. */
    private fun redirectRefused(id: String) {
        if (redirectRefusedFor == id) return
        redirectRefusedFor = id
        endClock()
        state = StreamRetry.NOT_ANSWERING
        Diagnostics.add(StreamLine.TAG, StreamLine.REDIRECT_REFUSED)
        player.pause()
        changed()
    }

    private fun endClock() {
        clock = StreamRetry.Clock()
        handler.removeCallbacks(retry)
        handler.removeCallbacks(giveUp)
        handler.removeCallbacks(look)
        if (wake.isHeld) wake.release()
    }

    private fun networkMoved() {
        // Posted: the callback's thread is not the player's, and the active network is not yet the new one inside it.
        handler.removeCallbacks(readNetwork)
        handler.post(readNetwork)
    }

    private fun watchNetwork(live: Boolean) {
        val cm = connectivity ?: return
        if (live == watchingNetwork) return
        runCatching { if (live) cm.registerDefaultNetworkCallback(network) else cm.unregisterNetworkCallback(network) }
            .onFailure { Diagnostics.add(StreamLine.TAG, "stream: network watch ${if (live) "not started" else "not stopped"}: ${it.javaClass.simpleName}") }
        watchingNetwork = live
    }

    /** The service is going: nothing is left scheduled, held or registered. */
    fun release() {
        endClock()
        handler.removeCallbacks(readNetwork)
        watchNetwork(false)
        player.removeListener(listener)
        player.removeAnalyticsListener(analytics)
    }

    private companion object {
        const val ADDRESSES_KEPT = 256
        const val CAUSES_READ = 8
    }
}

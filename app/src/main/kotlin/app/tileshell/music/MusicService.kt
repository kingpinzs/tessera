package app.tileshell.music

import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.tileshell.tiles.engine.TileRouting
import app.tileshell.diag.Diagnostics
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

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
    private var player: ExoPlayer? = null

    // ---- task 9: the sleep timer ----------------------------------------------------------------
    // Held here and not in the activity: the activity is exactly what Android reclaims after someone
    // sets a timer and puts the phone down. In memory only — after a reboot nothing is playing, so a
    // timer read back off disk would be a promise about music that no longer exists.
    private val handler = Handler(Looper.getMainLooper())
    private var sleepAt = 0L
    private var sleepEndOfTrack = false
    private val sleepRunnable = Runnable { sleepNow("timer reached") }

    // ---- task 9: the equaliser ------------------------------------------------------------------
    private var equalizer: Equalizer? = null
    private var eqPreset = Equaliser.OFF
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private val sleepCommand = SessionCommand(MusicCommands.SLEEP, Bundle.EMPTY)
    private val eqCommand = SessionCommand(MusicCommands.EQUALISER, Bundle.EMPTY)
    private val crossfadeCommand = SessionCommand(MusicCommands.CROSSFADE, Bundle.EMPTY)

    // ---- E17: crossfade ---------------------------------------------------------------------------
    private var crossfade: CrossfadeFader? = null

    override fun onCreate() {
        super.onCreate()
        // A session id of the service's own, set BEFORE any audio plays, so the equaliser can be
        // attached to it up front. Left to ExoPlayer, the id is assigned when the first track opens its
        // AudioTrack — after the effect would need to exist — and changes if the track is recreated.
        val audioSession = Util.generateAudioSessionIdV21(this)
        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        // Accurate MP3 seeking (E17). The crossfade hands the incoming track back to this player with a
        // seek to where the fader has got to; a VBR file without a seek table otherwise seeks to an
        // ESTIMATE, and two copies of one song a second apart cannot be swapped without hearing it. Index
        // seeking reads up to the target instead, which on a local file is fast.
        val mediaSourceFactory = DefaultMediaSourceFactory(
            this,
            DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING),
        )
        val exo = ExoPlayer.Builder(this, mediaSourceFactory)
            .setAudioAttributes(attributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.audioSessionId = audioSession
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // End-of-track is ExoPlayer's own pause-at-end, not a guess from a transition: pausing
                // on the transition would let the first moments of the NEXT track through.
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepEndOfTrack) {
                    Diagnostics.add("music", "sleep timer: paused at the end of the track")
                    clearSleep()
                }
            }
        })
        player = exo
        // Everything outside this service sees the track length even when the MP3 carries no header (J2).
        val known = KnownDurationPlayer(exo)
        crossfade = CrossfadeFader(this, known, audioSession, mediaSourceFactory, attributes) { sleepEndOfTrack }.also {
            it.settingMs = prefs.getInt(KEY_CROSSFADE, Crossfade.OFF)
            Diagnostics.add("music", "crossfade: ${it.settingMs} ms (restored)")
        }
        equalizer = runCatching { Equalizer(0, audioSession) }
            .onFailure { Diagnostics.add("music", "no equaliser on this device: ${it.javaClass.simpleName}") }
            .getOrNull()
        eqPreset = prefs.getInt(KEY_EQ, Equaliser.OFF)
        applyEqualiser(eqPreset, save = false)
        // The id is how Start tells this session from the shell's other players (Voice Recorder's playback, later
        // the video player): they share this package, and only Music's tile carries a now-playing face
        // (TileRouting, phase 15 build task 0). Media3 writes it into the platform session's tag.
        session = MediaSession.Builder(this, known).setId(TileRouting.MUSIC_SESSION_ID).setCallback(callback).build()
        publishExtras()
        Diagnostics.add("music", "playback service started (audio session $audioSession, equaliser ${if (equalizer != null) "available" else "unavailable"})")
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(sleepCommand)
                        .add(eqCommand)
                        .add(crossfadeCommand)
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                MusicCommands.SLEEP -> setSleep(args.getInt(MusicCommands.ARG_MINUTES, SleepTimer.OFF))
                MusicCommands.EQUALISER -> applyEqualiser(args.getInt(MusicCommands.ARG_PRESET, Equaliser.OFF), save = true)
                MusicCommands.CROSSFADE -> setCrossfade(args.getInt(MusicCommands.ARG_MS, Crossfade.OFF))
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * A search ("play Bloom" from Tess's playFromSearch, or any controller) resolves against the library by
         * MusicSearch, and the queue starts at the match. This session used to answer no search at all, so Tess's
         * "play <name>" left the player empty (J5). A search with no match FAILS rather than returning an empty
         * list: an empty list would clear whatever is playing.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
                ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            val match = MusicSearch.resolve(query, library())
            if (match == null) {
                Diagnostics.add("music", "search \"$query\": nothing in the library")
                return Futures.immediateFailedFuture(UnsupportedOperationException("no match for \"$query\""))
            }
            Diagnostics.add("music", "search \"$query\": ${match.kind.name.lowercase()} ${match.label}, ${match.queue.size} track(s)")
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(match.queue.map { mediaItem(it) }, match.startIndex, 0L),
            )
        }

        /** Items that arrive without their URI (a controller sends ids, or a search) are rebuilt from the library. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems)
            val lib = library()
            val out = mediaItems.flatMap { item ->
                val query = item.requestMetadata.searchQuery
                when {
                    item.localConfiguration != null -> listOf(item)
                    query != null -> MusicSearch.resolve(query, lib)?.queue.orEmpty().map { mediaItem(it) }
                    else -> lib.firstOrNull { it.id.toString() == item.mediaId }?.let { listOf(mediaItem(it)) }.orEmpty()
                }
            }
            if (out.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to add"))
            return Futures.immediateFuture(out.toMutableList())
        }
    }

    /** The library as the Music screens see it; read now if nothing has loaded it in this process yet. */
    private fun library(): List<Track> {
        if (MusicStore.library.value.isEmpty()) MusicStore.refresh(this, "a search")
        return MusicStore.library.value
    }

    private fun setSleep(minutes: Int) {
        clearSleep()
        when {
            minutes == SleepTimer.END_OF_TRACK -> {
                sleepEndOfTrack = true
                player?.pauseAtEndOfMediaItems = true
                Diagnostics.add("music", "sleep timer: armed for the end of this track")
            }
            minutes > 0 -> {
                val armedAt = SystemClock.elapsedRealtime()
                sleepAt = SleepTimer.deadline(armedAt, minutes)
                // postDelayed, NOT postAtTime(sleepAt): postAtTime takes the UPTIME clock, which stops in
                // deep sleep, while sleepAt is elapsedRealtime, which does not. On a phone that has slept
                // since boot the two differ by hours, and posting one clock's value on the other would
                // fire the timer that much late. The emulator barely sleeps, which is why it would hide it.
                handler.postDelayed(sleepRunnable, minutes * 60_000L)
                // Both numbers, named: the first version logged only the deadline as "at elapsed N",
                // which reads like the arming time, and MUSIC9's second run misread it exactly that way.
                Diagnostics.add("music", "sleep timer: armed for $minutes minute(s) at elapsed $armedAt, fires at elapsed $sleepAt")
            }
            else -> Diagnostics.add("music", "sleep timer: off")
        }
        publishExtras()
    }

    /** The timer fired: pause, which keeps the queue and the notification so the morning can resume it. */
    private fun sleepNow(why: String) {
        player?.pause()
        Diagnostics.add("music", "sleep timer: paused ($why) at elapsed ${SystemClock.elapsedRealtime()}")
        clearSleep()
    }

    private fun clearSleep() {
        handler.removeCallbacks(sleepRunnable)
        sleepAt = 0L
        if (sleepEndOfTrack) player?.pauseAtEndOfMediaItems = false
        sleepEndOfTrack = false
        publishExtras()
    }

    /** Only the lengths the menu offers are accepted; anything else is off, never an arbitrary fade. */
    private fun setCrossfade(ms: Int) {
        val chosen = Crossfade.Choice.entries.firstOrNull { it.ms == ms }?.ms ?: Crossfade.OFF
        crossfade?.settingMs = chosen
        prefs.edit().putInt(KEY_CROSSFADE, chosen).apply()
        Diagnostics.add("music", "crossfade: $chosen ms (set)")
        publishExtras()
    }

    private fun applyEqualiser(preset: Int, save: Boolean) {
        val eq = equalizer
        eqPreset = if (eq == null || preset !in 0 until eq.numberOfPresets) Equaliser.OFF else preset
        runCatching {
            if (eq != null) {
                if (eqPreset == Equaliser.OFF) {
                    eq.enabled = false
                } else {
                    eq.usePreset(eqPreset.toShort())
                    eq.enabled = true
                }
            }
        }.onFailure { Diagnostics.add("music", "equaliser refused preset $preset: $it") }
        if (save) prefs.edit().putInt(KEY_EQ, eqPreset).apply()
        Diagnostics.add("music", "equaliser: ${presetNames().getOrNull(eqPreset) ?: "off"} (enabled=${eq?.enabled ?: false})")
        publishExtras()
    }

    private fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.numberOfPresets).map { Equaliser.cleanName(eq.getPresetName(it.toShort()), it) }
        }.getOrDefault(emptyList())
    }

    /** What every connected controller is told: the collection, the notification, a second phone app. */
    private fun publishExtras() {
        val s = session ?: return
        s.setSessionExtras(
            Bundle().apply {
                putLong(MusicCommands.X_SLEEP_AT, sleepAt)
                putBoolean(MusicCommands.X_SLEEP_END_OF_TRACK, sleepEndOfTrack)
                putInt(MusicCommands.X_EQ_PRESET, eqPreset)
                putStringArray(MusicCommands.X_EQ_PRESETS, presetNames().toTypedArray())
                putBoolean(MusicCommands.X_EQ_AVAILABLE, equalizer != null)
                putInt(MusicCommands.X_CROSSFADE_MS, crossfade?.settingMs ?: Crossfade.OFF)
            },
        )
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
        handler.removeCallbacks(sleepRunnable)
        crossfade?.shutdown()
        crossfade = null
        equalizer?.release()
        equalizer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        Diagnostics.add("music", "playback service stopped")
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "music_playback"
        private const val KEY_EQ = "equaliser_preset"
        private const val KEY_CROSSFADE = "crossfade_ms"

        /** A track as Media3 sees it: the MediaStore URI, and the metadata the notification shows. */
        fun mediaItem(track: Track): MediaItem = MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(MusicStore.uriOf(track))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    // MediaStore's measured length: KnownDurationPlayer reports it when the file has no
                    // length header of its own (J2).
                    .setDurationMs(track.durationMs.takeIf { it > 0L })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }
}

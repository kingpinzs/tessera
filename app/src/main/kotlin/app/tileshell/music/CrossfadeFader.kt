package app.tileshell.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import app.tileshell.diag.Diagnostics

/**
 * The crossfade machine (phase 10, E17). See [Crossfade] for the arithmetic and why each rule is what it is.
 *
 * ### How it works, and why this way
 *
 * The session's player — the PRIMARY — keeps the whole queue and makes every transition itself. That is the
 * point: the queue index, the shuffle order, repeat-all's wrap, the notification and the Start tile's
 * now-playing face all move by the primary's own logic, exactly as they do with crossfade off, and gapless
 * playback (which needs consecutive items in ONE player's playlist) is untouched. A wrapper player that
 * swapped between two ExoPlayers could do none of that without re-implementing the queue.
 *
 * So the second player — the FADER — exists only for a few seconds:
 *
 *  1. PREPARED — with the fade plus [Crossfade.PREPARE_LEAD_MS] left, the fader is handed the primary's NEXT
 *     item (its own nextMediaItemIndex, so shuffle and repeat-all are the primary's), prepared, paused, silent.
 *  2. FADING — with `fade` left, the fader plays and the two are ramped on the equal-power curve.
 *  3. HANDBACK — the primary reaches the end at volume 0 and advances by itself; still silent, it seeks to
 *     where the fader has got to, the drift between the two copies is measured, and a short micro-fade moves
 *     the incoming track back onto the primary. The fader is released.
 *
 * The fader shares the primary's audio session, so the equaliser covers both tracks, and takes NO audio focus
 * of its own — asking for it would take focus from the primary and pause it. Anything a person does mid-fade
 * (pause, seek, skip, a call, headphones out, the sleep timer) aborts the fade: the fader goes and the primary
 * is put back at full volume.
 */
class CrossfadeFader(
    private val context: Context,
    private val primary: Player,
    private val audioSession: Int,
    private val mediaSourceFactory: MediaSource.Factory,
    private val attributes: AudioAttributes,
    /** The end-of-track sleep timer is armed: the music is about to STOP, so nothing may fade into anything. */
    private val sleepAtEndOfTrack: () -> Boolean,
) {
    private enum class State { IDLE, PREPARED, FADING, HANDBACK, HANDING }

    var settingMs: Int = Crossfade.OFF
        set(value) {
            field = value
            if (value <= 0) abort("crossfade turned off")
            reschedule()
        }

    private val handler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var fader: ExoPlayer? = null
    private var fadeMs = 0L
    private var fadeStartedAt = 0L
    private var preparedFor: String? = null            // media id of the item the fader was prepared after
    private var skipFor: String? = null                // an item this machine has decided not to fade out of
    private var ourSeekPending = false
    private var seekLead = INITIAL_SEEK_LEAD_MS
    private var seeks = 0
    private var readySince = 0L
    private var handbackBeganAt = 0L
    private var handingFrom = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            if (running()) handler.postDelayed(this, if (state == State.IDLE || state == State.PREPARED) IDLE_TICK_MS else FADE_TICK_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            when {
                state == State.FADING && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> beginHandback()
                state != State.IDLE -> abort("the track changed (${transitionName(reason)})")
            }
            skipFor = null
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (ourSeekPending) {
                ourSeekPending = false
                readySince = 0L
                return
            }
            if (state != State.IDLE) abort("a seek")
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && state != State.IDLE) abort("paused")
            reschedule()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = reschedule()
    }

    init {
        primary.addListener(listener)
    }

    private fun running(): Boolean = settingMs > 0 && (primary.playWhenReady || state != State.IDLE)

    private fun reschedule() {
        handler.removeCallbacks(tickRunnable)
        if (running()) handler.post(tickRunnable)
    }

    private fun currentId(): String? = primary.currentMediaItem?.mediaId

    private fun tick() {
        when (state) {
            State.IDLE -> considerPreparing()
            State.PREPARED -> considerStarting()
            State.FADING -> ramp()
            State.HANDBACK -> align()
            State.HANDING -> handing()
        }
    }

    private fun considerPreparing() {
        val id = currentId() ?: return
        if (id == skipFor || !primary.isPlaying) return
        val duration = primary.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val left = duration - primary.currentPosition
        if (left > settingMs + Crossfade.PREPARE_LEAD_MS) return
        val repeatOne = primary.repeatMode == Player.REPEAT_MODE_ONE
        if (!Crossfade.eligible(settingMs, primary.hasNextMediaItem(), repeatOne, sleepAtEndOfTrack())) {
            skipFor = id
            Diagnostics.add("music", "crossfade: no fade after ${titleOf(primary.currentMediaItem)} (${whyNot(repeatOne)})")
            return
        }
        val next = primary.getMediaItemAt(primary.nextMediaItemIndex)
        val f = ExoPlayer.Builder(context, mediaSourceFactory)
            // No focus of its own: asking for it would take focus from the primary and pause it.
            .setAudioAttributes(attributes, /* handleAudioFocus = */ false)
            .build()
        f.audioSessionId = audioSession
        f.volume = 0f
        f.setMediaItem(next)
        f.playWhenReady = false
        f.prepare()
        fader = f
        preparedFor = id
        state = State.PREPARED
        Diagnostics.add("music", "crossfade: prepared ${titleOf(next)} to follow ${titleOf(primary.currentMediaItem)}")
    }

    private fun considerStarting() {
        val f = fader ?: return abort("no fader")
        if (currentId() != preparedFor) return abort("the track changed before the fade")
        if (sleepAtEndOfTrack()) return abort("end-of-track sleep armed")
        val nextDuration = f.duration
        if (nextDuration == C.TIME_UNSET || nextDuration <= 0L) return      // still preparing
        val duration = primary.duration
        val left = duration - primary.currentPosition
        val planned = Crossfade.fadeLength(settingMs, duration, nextDuration)
        if (planned == 0L) {
            skipFor = currentId()
            return abort("a track too short to fade (${duration} ms into ${nextDuration} ms)")
        }
        if (left > planned) return
        // A fade that could not start on time (resumed late, a slow prepare) is shortened, never overrun.
        fadeMs = Crossfade.startLength(planned, left)
        if (fadeMs == 0L) {
            skipFor = currentId()
            return abort("too little of the track left to fade ($left ms)")
        }
        f.playWhenReady = true
        fadeStartedAt = SystemClock.elapsedRealtime()
        state = State.FADING
        Diagnostics.add("music", "crossfade: fade started at elapsed $fadeStartedAt, $fadeMs ms, into ${titleOf(f.currentMediaItem)}")
    }

    private fun ramp() {
        val f = fader ?: return abort("no fader")
        if (sleepAtEndOfTrack()) return abort("end-of-track sleep armed")
        val t = (f.currentPosition.toFloat() / fadeMs).coerceIn(0f, 1f)
        val (out, inc) = Crossfade.gains(t)
        primary.volume = out
        f.volume = inc
    }

    private fun beginHandback() {
        val f = fader ?: return abort("no fader at the handback")
        // The primary advanced by its own logic. If that is not the track the fader has been playing (the
        // queue or shuffle changed mid-fade), aligning would splice two different songs: give it back as is.
        if (currentId() != f.currentMediaItem?.mediaId) return abort("the primary advanced to a different track")
        handbackBeganAt = SystemClock.elapsedRealtime()
        state = State.HANDBACK
        primary.volume = 0f
        f.volume = 1f
        seekLead = INITIAL_SEEK_LEAD_MS
        seeks = 0
        seekPrimaryToFader()
        Diagnostics.add("music", "crossfade: handback began at elapsed $handbackBeganAt (fade ran ${handbackBeganAt - fadeStartedAt} ms)")
    }

    private fun seekPrimaryToFader() {
        val f = fader ?: return
        ourSeekPending = true
        readySince = 0L
        seeks++
        primary.seekTo(primary.currentMediaItemIndex, f.currentPosition + seekLead)
    }

    private fun align() {
        val f = fader ?: return abort("no fader")
        val now = SystemClock.elapsedRealtime()
        if (now - handbackBeganAt > HANDBACK_TIMEOUT_MS) return startHanding(now, "timed out")
        if (ourSeekPending || primary.playbackState != Player.STATE_READY || !primary.isPlaying) return
        if (readySince == 0L) { readySince = now; return }
        if (now - readySince < SETTLE_MS) return      // let the AudioTrack's timestamp settle before trusting it
        val drift = primary.currentPosition - f.currentPosition
        if (Crossfade.handbackAligned(primary.currentPosition, f.currentPosition) || seeks >= MAX_SEEKS) {
            return startHanding(now, "drift $drift ms after $seeks seek(s)")
        }
        // Positions advance together once both play, so the drift is the seek's own delay: correct the lead by it.
        seekLead -= drift
        seekPrimaryToFader()
    }

    private var handingNote = ""

    private fun startHanding(now: Long, note: String) {
        handingFrom = now
        handingNote = note
        state = State.HANDING
    }

    private fun handing() {
        val f = fader ?: return abort("no fader")
        val t = ((SystemClock.elapsedRealtime() - handingFrom).toFloat() / Crossfade.HANDBACK_FADE_MS).coerceIn(0f, 1f)
        val (out, inc) = Crossfade.gains(t)
        f.volume = out
        primary.volume = inc
        if (t >= 1f) {
            release()
            state = State.IDLE
            primary.volume = 1f
            Diagnostics.add("music", "crossfade: handed back at elapsed ${SystemClock.elapsedRealtime()}, $handingNote")
        }
    }

    /** Anything that changes what is playing mid-fade: the fader goes and the primary is put back at full volume. */
    fun abort(reason: String) {
        if (state == State.IDLE && fader == null) return
        val was = state
        release()
        state = State.IDLE
        primary.volume = 1f
        skipFor = currentId()
        Diagnostics.add("music", "crossfade: aborted in ${was.name.lowercase()} ($reason)")
    }

    private fun release() {
        fader?.release()
        fader = null
        ourSeekPending = false
        preparedFor = null
    }

    fun shutdown() {
        handler.removeCallbacks(tickRunnable)
        primary.removeListener(listener)
        release()
        state = State.IDLE
    }

    private fun whyNot(repeatOne: Boolean): String = when {
        repeatOne -> "repeat one"
        sleepAtEndOfTrack() -> "end-of-track sleep armed"
        !primary.hasNextMediaItem() -> "no next track"
        else -> "crossfade off"
    }

    private fun titleOf(item: MediaItem?): String = item?.mediaMetadata?.title?.toString() ?: "?"

    private fun transitionName(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "auto"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "seek"
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "repeat"
        else -> "playlist change"
    }

    private companion object {
        const val IDLE_TICK_MS = 100L
        const val FADE_TICK_MS = 30L
        /** Where the first handback seek aims ahead of the fader, to cover the seek's own rebuffer. */
        const val INITIAL_SEEK_LEAD_MS = 150L
        const val SETTLE_MS = 150L
        const val MAX_SEEKS = 3
        const val HANDBACK_TIMEOUT_MS = 3_000L
    }
}

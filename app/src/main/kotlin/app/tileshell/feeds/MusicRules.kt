package app.tileshell.feeds

import android.media.session.PlaybackState
import app.tileshell.tiles.engine.Transport

/**
 * What the Music tile should be, given what the media session says (Jeremy, 2026-09-21: "if there is
 * music playing that tile should grow bigger and show what is currently playing with play pauese stop
 * skip on it when its not playing it should flip to show music sort of like the photo one does").
 * INDEX Change Log item 3.
 *
 * Pure, because the two things that have gone wrong here cannot be seen on a device: a tile that
 * republishes on a clock instead of on a change, and a tile that flips between two states while a player
 * buffers. Both are rules, and rules belong somewhere they can be proven.
 */
object MusicRules {

    /** What the tile knows about the track. Deliberately no position: see [republish]. */
    data class Track(
        val pkg: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
    )

    /** The track and whether it is running. This is the whole of the tile's state. */
    data class Now(val track: Track, val playing: Boolean)

    /** What the tile does with a [Now] (or the absence of one). */
    data class Plan(
        /** The face REPLACES the logo and the tile does not flip: it is the player. */
        val front: Boolean,
        /** The face takes a turn behind the logo on the tile's own timer, as the Photos tile does. */
        val flip: Boolean,
        /** The tile is drawn one size bigger while this lasts ([app.tileshell.tiles.TileGrowth]). */
        val grow: Boolean,
        /** The controls on the face; empty means a tap anywhere on the tile launches the app as usual. */
        val controls: List<Transport>,
    )

    /** Jeremy's "play pauese stop skip", in the order a transport reads left to right. */
    val PLAYING_CONTROLS = listOf(Transport.PLAY_PAUSE, Transport.STOP, Transport.NEXT)

    /**
     * Is this playback state the tile's idea of "playing"?
     *
     * BUFFERING counts. A player that stalls for a second on a network track fires
     * PLAYING -> BUFFERING -> PLAYING, and reading that literally would shrink the tile and take the
     * controls away mid-song, twice — a tile flapping between two sizes while one song plays is worse
     * than a pause button that is right a second early.
     */
    fun isPlaying(state: Int): Boolean =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    /**
     * THE REPUBLISH RULE, and the fix for the defect phase 02 handed over: MusicFeed republished about
     * every 3 s, because a player posts a new PlaybackState on every position update and the feed
     * published whatever it was handed.
     *
     * A tile republishes on CHANGE, not on a clock. [Now] holds no position and no timestamp precisely so
     * that "same song, three seconds later" IS the same value: a position update that changes nothing the
     * tile draws publishes nothing, and every render-latency line in the diagnostics (E5) then means a
     * real change instead of a heartbeat.
     */
    fun republish(last: Now?, next: Now?): Boolean = last != next

    /**
     * The state machine. Three states, and each one is one of Jeremy's clauses:
     *
     *  - **playing** — "that tile should grow bigger and show what is currently playing with play pauese
     *    stop skip on it": the face replaces the logo (so the tile cannot flip away from the controls
     *    under someone's finger), the tile grows, the controls are on it.
     *  - **not playing, but something was** — "when its not playing it should flip to show music sort of
     *    like the photo one does": the last track takes a turn behind the logo on the tile's own timer,
     *    with no controls and no growth.
     *  - **nothing at all** (a fresh boot, no media session ever) — the tile is its logo, exactly as it
     *    was before any of this.
     *
     * A PAUSE is the second state, and that is the literal reading of "when its not playing": tapping
     * pause on the tile shrinks it back and takes the controls away, and resuming is a tap on the tile,
     * which opens the player — which is what W10M's own Music tile did, since it had no transport at all.
     * The alternative (keep the player face while any session is alive, so ▶ stays on the tile) was
     * rejected because media apps leave a paused session alive for hours: the Music tile would show
     * yesterday's track with a transport on it indefinitely and would never flip, which is the opposite of
     * what was asked for. Changing the reading is one clause here: `now.playing` becomes "a session exists".
     */
    fun plan(now: Now?): Plan = when {
        now == null -> Plan(front = false, flip = false, grow = false, controls = emptyList())
        now.playing -> Plan(front = true, flip = false, grow = true, controls = PLAYING_CONTROLS)
        else -> Plan(front = false, flip = true, grow = false, controls = emptyList())
    }

    /**
     * Which session the tile follows when several are alive at once: a playing one wins, otherwise the
     * first one offered. Android hands active sessions back in priority order (the most recently active
     * first), so "the first one" is "the one they used last", and a paused podcast never hides a playing
     * album.
     */
    fun <T> pick(sessions: List<T>, playing: (T) -> Boolean): T? =
        sessions.firstOrNull(playing) ?: sessions.firstOrNull()

    /**
     * Does the art have to be read again? Only when the track changed.
     *
     * `MediaController.getMetadata()` crosses a Binder and can hand back a fresh Bitmap for the same
     * song, so comparing art by identity would make every position update look like a change and bring
     * the 3-second republish straight back. The art is a function of the track, so the track is what is
     * compared, and an unchanged track keeps the bitmap the tile is already drawing.
     */
    fun artChanged(last: Track?, next: Track?): Boolean = last != next
}

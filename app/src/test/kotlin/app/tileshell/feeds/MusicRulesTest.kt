package app.tileshell.feeds

import android.media.session.PlaybackState
import app.tileshell.tiles.engine.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Music tile's state machine and its republish rule (INDEX Change Log 2026-09-21 item 3).
 *
 * Nothing here touches a MediaController: the rules are values in, values out, which is why the
 * 3-second-republish defect is provable at all.
 */
class MusicRulesTest {

    private fun track(title: String = "Song", artist: String = "Band", pkg: String = "com.player") =
        MusicRules.Track(pkg = pkg, title = title, artist = artist, album = "Album", durationMs = 200_000)

    private fun now(playing: Boolean, title: String = "Song") = MusicRules.Now(track(title), playing)

    // ---- the playing / stopped state machine ----

    @Test
    fun `playing puts the player on the front of the tile, with controls, and grows it`() {
        val plan = MusicRules.plan(now(playing = true))
        assertTrue(plan.front)
        assertFalse(plan.flip)
        assertTrue(plan.grow)
        assertEquals(listOf(Transport.PLAY_PAUSE, Transport.STOP, Transport.NEXT), plan.controls)
    }

    @Test
    fun `not playing flips the last track behind the logo, with no controls and no growth`() {
        val plan = MusicRules.plan(now(playing = false))
        assertFalse(plan.front)
        assertTrue(plan.flip)
        assertFalse(plan.grow)
        assertTrue(plan.controls.isEmpty())
    }

    @Test
    fun `nothing known at all leaves the tile as its logo`() {
        val plan = MusicRules.plan(null)
        assertFalse(plan.front)
        assertFalse(plan.flip)
        assertFalse(plan.grow)
        assertTrue(plan.controls.isEmpty())
    }

    @Test
    fun `the tile is never both the player and a flipping tile`() {
        listOf(null, now(true), now(false)).forEach { state ->
            val plan = MusicRules.plan(state)
            assertFalse("front and flip at once for $state", plan.front && plan.flip)
        }
    }

    @Test
    fun `only playing grows the tile`() {
        assertTrue(MusicRules.plan(now(true)).grow)
        assertFalse(MusicRules.plan(now(false)).grow)
        assertFalse(MusicRules.plan(null).grow)
    }

    @Test
    fun `an idle tile carries no tap targets, so a tap on it launches the app as it always did`() {
        assertTrue(MusicRules.plan(now(false)).controls.isEmpty())
    }

    // ---- what counts as playing ----

    @Test
    fun `playing and buffering both count as playing`() {
        assertTrue(MusicRules.isPlaying(PlaybackState.STATE_PLAYING))
        assertTrue(MusicRules.isPlaying(PlaybackState.STATE_BUFFERING))
    }

    @Test
    fun `paused, stopped and none do not`() {
        assertFalse(MusicRules.isPlaying(PlaybackState.STATE_PAUSED))
        assertFalse(MusicRules.isPlaying(PlaybackState.STATE_STOPPED))
        assertFalse(MusicRules.isPlaying(PlaybackState.STATE_NONE))
    }

    @Test
    fun `a stall mid-song does not shrink the tile`() {
        // PLAYING -> BUFFERING -> PLAYING must be one state, or the tile flaps between two sizes and
        // takes its own controls away twice while one song plays.
        val stall = MusicRules.Now(track(), MusicRules.isPlaying(PlaybackState.STATE_BUFFERING))
        assertEquals(MusicRules.plan(now(true)), MusicRules.plan(stall))
        assertFalse(MusicRules.republish(now(true), stall))
    }

    // ---- THE REPUBLISH RULE: on change, never on a clock (the phase 02 hand-off) ----

    @Test
    fun `the same song three seconds later publishes nothing`() {
        // This is the defect: a player posts a new PlaybackState on every position update, about every
        // 3 s. Now holds no position, so "same song, later" IS the same value.
        assertFalse(MusicRules.republish(now(true), now(true)))
    }

    @Test
    fun `a new track republishes`() {
        assertTrue(MusicRules.republish(now(true, "Song"), now(true, "Another")))
    }

    @Test
    fun `starting, pausing and stopping each republish`() {
        assertTrue(MusicRules.republish(null, now(true)))
        assertTrue(MusicRules.republish(now(true), now(false)))
        assertTrue(MusicRules.republish(now(false), null))
    }

    @Test
    fun `nothing to nothing publishes nothing`() {
        assertFalse(MusicRules.republish(null, null))
    }

    @Test
    fun `the art is re-read only when the track changes`() {
        // getMetadata() crosses a Binder and can hand back a fresh Bitmap for the same song, so comparing
        // art by identity would bring the 3-second republish straight back.
        assertFalse(MusicRules.artChanged(track(), track()))
        assertTrue(MusicRules.artChanged(track("Song"), track("Another")))
        assertTrue(MusicRules.artChanged(null, track()))
    }

    @Test
    fun `a position tick does not even change the art`() {
        assertFalse(MusicRules.artChanged(now(true).track, now(true).track))
    }

    // ---- which session the tile follows ----

    @Test
    fun `a playing session wins over a paused one, whatever order they arrive in`() {
        val sessions = listOf("paused-podcast" to false, "playing-album" to true)
        assertEquals("playing-album", MusicRules.pick(sessions) { it.second }?.first)
    }

    @Test
    fun `with nothing playing the most recent session is followed`() {
        // Android hands active sessions back most-recently-active first.
        val sessions = listOf("last-used" to false, "older" to false)
        assertEquals("last-used", MusicRules.pick(sessions) { it.second }?.first)
    }

    @Test
    fun `no sessions at all is not an error`() {
        assertNull(MusicRules.pick(emptyList<Pair<String, Boolean>>()) { it.second })
    }
}

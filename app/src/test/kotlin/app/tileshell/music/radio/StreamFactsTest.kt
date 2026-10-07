package app.tileshell.music.radio

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 build task 3: the readings `StreamWatch` makes of what the player tells it. */
class StreamFactsTest {
    @Test fun `a user's own start is a queue set or a move to another item - never the player's own move, and never a reconnect`() {
        assertTrue(StreamFacts.userStarted(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
        assertTrue(StreamFacts.userStarted(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK))
        assertFalse(StreamFacts.userStarted(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
        assertFalse(StreamFacts.userStarted(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT))
        // The four reasons Media3 has, pinned: a fifth would have to be weighed here.
        assertEquals(listOf(0, 1, 2, 3), listOf(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
        assertFalse(StreamFacts.userStarted(4))
        assertFalse(StreamFacts.userStarted(-1))
    }

    @Test fun `a station whose server closed the stream is gone back to and put on the clock - never left silent or handed to the next favourite`() {
        val jazz = "station:11111111-1111-4111-8111-111111111111"
        val news = "station:33333333-3333-4333-8333-333333333333"
        val live = { id: String? -> id != null && id.startsWith("station:") }
        fun ended(reason: Int, from: String?, to: String?, returningTo: String? = null) = StreamFacts.streamEnded(reason, from, to, returningTo, live)
        // The player went on by itself out of a station (a favourites queue): undone.
        assertEquals(StreamFacts.Ended.MOVED_ON, ended(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, jazz, news))
        // …and the move back to it is the loss — but only that move, to that station.
        assertEquals(StreamFacts.Ended.LOST, ended(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, news, jazz, returningTo = jazz))
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, news, jazz, returningTo = null))
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, jazz, news, returningTo = jazz))
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, news, jazz, returningTo = jazz))
        // The station began again by itself (repeat one, or repeat all over a queue of one): the loss, with no move to undo.
        assertEquals(StreamFacts.Ended.LOST, ended(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT, jazz, jazz))
        // The user's own moves are never a loss: a tap or Tess, next / previous.
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, jazz, news))
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, null, jazz))
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, jazz, news))
        // A track is not a stream: its end is its end, a repeat is a repeat.
        for (track in listOf("42", "file:1", "server:0a1b")) {
            assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, track, "43"))
            assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, track, jazz))
            assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT, track, track))
        }
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO, null, jazz))
        assertNull(ended(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT, null, null))
    }

    @Test fun `a refused cross-protocol redirect is known by Media3's own message, at any depth of the error`() {
        assertTrue(StreamFacts.redirectRefused(listOf("Disallowed cross-protocol redirect (https to http)")))
        assertTrue(StreamFacts.redirectRefused(listOf("Source error", null, "Disallowed cross-protocol redirect (http to https)")))
        assertFalse(StreamFacts.redirectRefused(listOf("Source error", "Too many redirects: 21", "Unable to connect")))
        assertFalse(StreamFacts.redirectRefused(listOf(null, "")))
        assertFalse(StreamFacts.redirectRefused(emptyList()))
        // A message that only mentions it further in is not Media3's.
        assertFalse(StreamFacts.redirectRefused(listOf("x Disallowed cross-protocol redirect")))
    }

    @Test fun `a lost stream has returned when it sounds and what is buffered has moved`() {
        assertTrue(StreamFacts.returned(playing = true, bufferedAtLastLookMs = 30_000L, bufferedMs = 31_000L))
        // A re-prepared live stream starts again from nothing: moved is moved, either way.
        assertTrue(StreamFacts.returned(playing = true, bufferedAtLastLookMs = 30_000L, bufferedMs = 1_200L))
        // Still sounding out of what it had: not back.
        assertFalse(StreamFacts.returned(playing = true, bufferedAtLastLookMs = 30_000L, bufferedMs = 30_000L))
        assertFalse(StreamFacts.returned(playing = false, bufferedAtLastLookMs = 30_000L, bufferedMs = 31_000L))
        assertFalse(StreamFacts.returned(playing = false, bufferedAtLastLookMs = 0L, bufferedMs = 0L))
    }

    @Test fun `the codec is named as the connected line names it`() {
        assertEquals("mp3", StreamFacts.codec("audio/mpeg"))
        assertEquals("mp3", StreamFacts.codec("AUDIO/MPEG"))
        assertEquals("aac", StreamFacts.codec("audio/mp4a-latm"))
        assertEquals("vorbis", StreamFacts.codec("audio/vorbis"))
        assertEquals("opus", StreamFacts.codec("audio/opus"))
        assertEquals("flac", StreamFacts.codec("audio/flac"))
        assertEquals("", StreamFacts.codec(null))
        assertEquals("", StreamFacts.codec("nonsense"))
        // What the line then says.
        assertEquals("stream: connected http://10.0.2.2:8096/Audio/0a1b/stream codec=mp3", StreamLine.connected("http://10.0.2.2:8096/Audio/0a1b/stream?static=true", StreamFacts.codec("audio/mpeg")))
        assertEquals("stream: connected http://stream.example.net/live codec=unknown", StreamLine.connected("http://user:pw@stream.example.net/live?token=1", StreamFacts.codec(null)))
    }
}

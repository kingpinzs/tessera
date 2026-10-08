package app.tileshell.music

import app.tileshell.music.radio.StreamLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (r3 D1 / D10 / D15): "live" is the item's mark alone, and what it does to an armed end-of-track timer. */
class MusicLiveTest {
    @Test fun `a station id is live whatever the duration - an HLS window reports one and it changes nothing`() {
        // The rule is never given a duration: there is no argument a duration could come in by.
        assertTrue(MusicLive.isLive("station:960594a6-0601-11e8-ae97-52543be04c81"))
        assertTrue(MusicLive.isLive(MusicLive.stationId("qa-jazz-one")))
        assertTrue(MusicLive.isLive("station:"))
    }

    @Test fun `a server track, a library id, a one-file item and nothing are not live`() {
        for (id in listOf("server:abc", MusicLive.serverId("7"), "41", MusicFile.mediaId(3), "", null, "Station:x", " station:x", "stations:x")) {
            assertFalse("$id", MusicLive.isLive(id))
        }
        assertTrue(MusicLive.isServer("server:abc"))
        assertFalse(MusicLive.isServer("station:abc"))
        assertFalse(MusicLive.isServer(null))
    }

    @Test fun `neither prefix can be a library id`() {
        assertEquals("station:", MusicLive.STATION_PREFIX)
        assertEquals("server:", MusicLive.SERVER_PREFIX)
        for (id in listOf(MusicLive.stationId("41"), MusicLive.serverId("41"), MusicLive.stationId(""), MusicLive.serverId(""))) {
            assertNull(id, MusicItemRule.libraryId(id))
        }
        assertEquals("41", MusicLive.stationUuid("station:41"))
        assertNull(MusicLive.stationUuid("server:41"))
        assertNull(MusicLive.stationUuid("41"))
    }

    @Test fun `an armed end-of-track is cleared for a station, and the line is the one the doc words`() {
        assertTrue(MusicLive.clearsEndOfTrack(armed = true, mediaId = "station:abc"))
        assertEquals("sleep: end-of-track cleared (live item)", StreamLine.SLEEP_CLEARED)
    }

    @Test fun `not armed, or a track - nothing is cleared`() {
        assertFalse(MusicLive.clearsEndOfTrack(armed = false, mediaId = "station:abc"))
        for (id in listOf("41", "server:abc", MusicFile.mediaId(1), "", null)) {
            assertFalse("$id", MusicLive.clearsEndOfTrack(armed = true, mediaId = id))
            assertFalse("$id", MusicLive.clearsEndOfTrack(armed = false, mediaId = id))
        }
    }
}

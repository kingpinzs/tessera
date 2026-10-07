package app.tileshell.music

import app.tileshell.music.server.ServerTrackItem
import app.tileshell.video.server.ServerRules
import app.tileshell.video.server.ServerTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (r3 D1 / D4, trust): a server track's media id, and the one address its item may be given. */
class ServerTrackItemTest {
    private val track = ServerTrack("e90356d9dbdedc30a27710927ef3ac87", "QA Track One", "QA Album", "QA Artist", 1, 5000)

    @Test fun `the media id is server-colon-id, never a decimal a library id could be`() {
        assertEquals("server:e90356d9dbdedc30a27710927ef3ac87", ServerTrackItem.mediaId(track))
        // Even an id made of digits alone is not a number once it is marked.
        assertNull(ServerTrackItem.mediaId(track.copy(id = "12345")).toLongOrNull())
    }

    @Test fun `only the saved server's own direct-play address is accepted, and it holds no token`() {
        val base = "http://10.0.2.2:8096"
        val url = ServerRules.audioStreamUrl(base, track.id)
        assertEquals("http://10.0.2.2:8096/Audio/e90356d9dbdedc30a27710927ef3ac87/stream?static=true", url)
        assertTrue(ServerTrackItem.accepts(url, track))
        assertTrue(ServerTrackItem.accepts(ServerRules.audioStreamUrl("https://media.example.org", track.id), track))
        for (refused in listOf(
            "$url&ApiKey=QA-SECRET",
            "$url&api_key=QA-SECRET",
            "http://10.0.2.2:8096/Audio/${track.id}/stream?ApiKey=QA-SECRET&static=true",
            "http://10.0.2.2:8096/Audio/${track.id}/stream",
            "http://10.0.2.2:8096/Audio/${track.id}/universal?static=true",
            "http://10.0.2.2:8096/Videos/${track.id}/stream?static=true",
            "http://10.0.2.2:8096/Audio/aa11/stream?static=true",                      // another item's address
            "http://user:pw@10.0.2.2:8096/Audio/${track.id}/stream?static=true",
            "http://10.0.2.2:8096/x/Audio/${track.id}/stream?static=true",
            "file:///sdcard/Audio/${track.id}/stream?static=true",
            "content://media/Audio/${track.id}/stream?static=true",
            "10.0.2.2:8096/Audio/${track.id}/stream?static=true",
            "/Audio/${track.id}/stream?static=true",
            "",
        )) assertFalse(refused, ServerTrackItem.accepts(refused, track))
        assertFalse(ServerTrackItem.accepts(null, track))
        // An id that is not an id never becomes part of an address.
        val odd = track.copy(id = "../../Users")
        assertFalse(ServerTrackItem.accepts(ServerRules.audioStreamUrl(base, odd.id), odd))
    }
}

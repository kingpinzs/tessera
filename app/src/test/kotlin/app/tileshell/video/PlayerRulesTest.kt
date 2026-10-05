package app.tileshell.video

import androidx.media3.common.PlaybackException
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 17 build task 7: the player's scheme rule, its error states and the chrome's travel and seek rules. */
class PlayerRulesTest {
    private val roots = listOf("/data/user/0/app.tileshell", "/storage/emulated/0/Android/data/app.tileshell/files")

    private fun classify(uri: String): PlayerRequest {
        val scheme = uri.substringBefore("://", "").ifEmpty { null }
        val rest = uri.substringAfter("://")
        val authority = rest.substringBefore('/').ifEmpty { null }
        val path = if (rest.contains('/')) "/" + rest.substringAfter('/').substringBefore('?') else null
        return PlayerRules.classify(scheme, authority, path, roots)
    }

    @Test fun `a MediaStore video is played and names its id`() {
        val r = classify("content://media/external/video/media/42") as PlayerRequest.Play
        assertEquals("content", r.scheme)
        assertEquals(42L, r.mediaStoreId)
        assertNull(r.host)
    }

    @Test fun `another provider's content is played with no MediaStore id`() {
        val r = classify("content://com.example.files/doc/7") as PlayerRequest.Play
        assertNull(r.mediaStoreId)
        assertNull((classify("content://media/external/images/media/9") as PlayerRequest.Play).mediaStoreId)
        assertNull((classify("content://media/external/video/media/9/thumb") as PlayerRequest.Play).mediaStoreId)
    }

    @Test fun `http and https are played and name host and port only`() {
        val r = classify("http://10.0.2.2:8090/qa-steps.mp4?ApiKey=secret") as PlayerRequest.Play
        assertEquals("http", r.scheme)
        assertEquals("10.0.2.2:8090", r.host)
        assertEquals("qa-steps.mp4", r.name)
        assertTrue(r.isNetwork)
        assertEquals("example.org", (classify("HTTPS://example.org/a/b.mkv") as PlayerRequest.Play).host)
    }

    @Test fun `a password in the address never reaches the host label`() {
        val r = classify("http://user:hunter2@example.org:8096/v.mp4") as PlayerRequest.Play
        assertEquals("example.org:8096", r.host)
    }

    @Test fun `file is played only for the shell's own files`() {
        assertTrue(classify("file:///data/user/0/app.tileshell/files/clip.mp4") is PlayerRequest.Play)
        assertTrue(classify("file:///storage/emulated/0/Android/data/app.tileshell/files/a.mp4") is PlayerRequest.Play)
        assertEquals(PlayerRequest.Unsupported("file"), classify("file:///sdcard/Movies/x.mp4"))
        assertEquals(PlayerRequest.Unsupported("file"), classify("file:///data/user/0/app.tileshell.evil/files/x.mp4"))
        assertEquals(PlayerRequest.Unsupported("file"), classify("file:///data/user/0/app.tileshell/../other/x.mp4"))
    }

    @Test fun `every other scheme is unsupported with its line`() {
        for (s in listOf("rtsp", "ftp", "smb", "data", "javascript", "android.resource")) {
            val r = classify("$s://10.0.2.2/x")
            assertEquals(PlayerRequest.Unsupported(s), r)
            val f = PlayerRules.unsupported(r as PlayerRequest.Unsupported)
            assertEquals("Can't play this address", f.text)
            assertEquals("unsupported scheme=$s", f.line)
        }
        assertEquals(PlayerRequest.Unsupported("none"), PlayerRules.classify(null, null, null, roots))
        assertEquals(PlayerRequest.Unsupported("http"), PlayerRules.classify("http", "", "/x", roots))
    }

    @Test fun `the error codes are Media3's`() {
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlayerRules.CODE_NETWORK_FAILED)
        assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlayerRules.CODE_NETWORK_TIMEOUT)
        assertEquals(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlayerRules.CODE_BAD_HTTP_STATUS)
    }

    @Test fun `an unreachable host, an http status and a bad file each have their state`() {
        val http = classify("http://10.0.2.2:8090/qa-steps.mp4?token=abc") as PlayerRequest.Play
        val unreachable = PlayerRules.failure(PlayerRules.kindOf(PlayerRules.CODE_NETWORK_FAILED, true), http, null, null)
        assertEquals(PlayerFailure("Can't reach this video", "cannot reach 10.0.2.2:8090"), unreachable)
        val timeout = PlayerRules.failure(PlayerRules.kindOf(PlayerRules.CODE_NETWORK_TIMEOUT, true), http, null, null)
        assertEquals("cannot reach 10.0.2.2:8090", timeout.line)
        val notFound = PlayerRules.failure(PlayerRules.kindOf(PlayerRules.CODE_BAD_HTTP_STATUS, true), http, 404, null)
        assertEquals(PlayerFailure("can't play this file", "cannot decode 404"), notFound)
        val local = classify("content://media/external/video/media/5") as PlayerRequest.Play
        val bad = PlayerRules.failure(PlayerRules.kindOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, false), local, null, "qa-truncated.mp4")
        assertEquals(PlayerFailure("can't play this file", "cannot decode qa-truncated.mp4"), bad)
        // A local source that fails with an IO code is a file that cannot be played, never "unreachable".
        assertEquals(PlayerErrorKind.UNDECODABLE, PlayerRules.kindOf(PlayerRules.CODE_NETWORK_FAILED, false))
        // No line carries a query string.
        for (f in listOf(unreachable, timeout, notFound, PlayerRules.failure(PlayerErrorKind.UNDECODABLE, http, null, null))) {
            assertTrue(f.line, !f.line.contains("token") && !f.line.contains("?"))
        }
    }

    @Test fun `autoplay takes the next video of the group and stops at its end`() {
        val q = longArrayOf(4, 9, 2)
        assertEquals(9L, PlayerRules.nextInQueue(q, 4))
        assertEquals(2L, PlayerRules.nextInQueue(q, 9))
        assertNull(PlayerRules.nextInQueue(q, 2))
        assertNull(PlayerRules.nextInQueue(q, 77))
        assertNull(PlayerRules.nextInQueue(null, 4))
        assertNull(PlayerRules.nextInQueue(q, null))
    }

    @Test fun `the thumb travels from track left plus r to track right minus r`() {
        assertEquals(23f, PlayerGeometry.thumbCentreX(0f, 360f), 0.001f)
        assertEquals(337f, PlayerGeometry.thumbCentreX(1f, 360f), 0.001f)
        // r11/movies-tv-pass2.md: thumb centre 545.5 px on a track 30 → 1770 px, radius 27.5 → 0.2896.
        val canvas = 1800f / 2.5f
        assertEquals(545.5f / 2.5f, PlayerGeometry.thumbCentreX(0.2896f, canvas), 0.2f)
    }

    @Test fun `a tap at 70 percent of the track is 247 point 2 epx`() {
        assertEquals(0.7f, PlayerGeometry.fractionAt(247.2f, 360f), 0.0001f)
        assertEquals(0f, PlayerGeometry.fractionAt(3f, 360f), 0f)
        assertEquals(1f, PlayerGeometry.fractionAt(359f, 360f), 0f)
    }

    @Test fun `time labels follow the locale and the right one counts down`() {
        assertEquals("0:17:34", PlayerGeometry.clock(17 * 60 + 34, Locale.US))
        assertEquals("00:00:29", PlayerGeometry.clock(29, Locale.UK))
        assertEquals("1:31:31", PlayerGeometry.clock(3600 + 31 * 60 + 31, Locale.US))
        assertEquals(7L, PlayerGeometry.remainingSeconds(3_000, 10_000))
        assertEquals(0L, PlayerGeometry.remainingSeconds(12_000, 10_000))
        assertEquals(3L, PlayerGeometry.elapsedSeconds(3_999))
    }

    @Test fun `the picture is letterboxed by default and covers with zoom to fill`() {
        val (fw, fh) = PlayerGeometry.videoBox(1280, 720, 360f, 732f, fill = false)
        assertEquals(360f, fw, 0.01f); assertEquals(202.5f, fh, 0.01f)
        val (zw, zh) = PlayerGeometry.videoBox(1280, 720, 360f, 732f, fill = true)
        assertEquals(732f, zh, 0.01f); assertEquals(1301.33f, zw, 0.01f)
        assertEquals(360f to 732f, PlayerGeometry.videoBox(0, 0, 360f, 732f, fill = false))
    }
}

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

    @Test fun `a password holding an at sign never reaches the host label`() {
        // C-L6: everything before the LAST @ is the user part, whatever it holds.
        assertEquals("example.org:8096", (classify("http://user:p@ss@example.org:8096/v.mp4") as PlayerRequest.Play).host)
        assertEquals("example.org", PlayerRules.hostLabel("user:p@ss:w@rd@example.org"))
        assertEquals("example.org", PlayerRules.hostLabel("@example.org"))
        val failure = PlayerRules.failure(PlayerErrorKind.UNREACHABLE, classify("http://u:hunter@2@example.org/v.mp4") as PlayerRequest.Play, null, null)
        assertEquals("cannot reach example.org", failure.line)
        // An address that is all user part has no host: it is not played.
        assertEquals(PlayerRequest.Unsupported("http"), PlayerRules.classify("http", "user:pw@", "/x", roots))
    }

    @Test fun `another provider's path that only looks like MediaStore's is not a MediaStore id`() {
        // C-L6: the id starts provider queries with the shell's own access — only the real MediaStore authority has one.
        for (uri in listOf(
            "content://com.attacker.files/external/video/media/7", "content://media.attacker/external/video/media/7",
            "content://mediax/external/video/media/7", "content://10@media/external/video/media/7", "content://MEDIA/external/video/media/7",
        )) {
            assertNull(uri, (classify(uri) as PlayerRequest.Play).mediaStoreId)
        }
        assertNull((classify("content://media/x/external/video/media/7") as PlayerRequest.Play).mediaStoreId)
        assertNull((classify("content://media/external/video/media/7x") as PlayerRequest.Play).mediaStoreId)
        assertEquals(7L, (classify("content://media/external/video/media/7") as PlayerRequest.Play).mediaStoreId)
    }

    @Test fun `an own-root that is empty or only slashes matches nothing`() {
        // C-L6: a directory that could not be read must not turn into "the whole disk is the shell's".
        for (bad in listOf(listOf(""), listOf("/"), listOf("//"), listOf(" "), listOf("", "/", "///"), emptyList())) {
            assertEquals("$bad", PlayerRequest.Unsupported("file"), PlayerRules.classify("file", null, "/sdcard/Movies/x.mp4", bad))
            assertEquals("$bad", PlayerRequest.Unsupported("file"), PlayerRules.classify("file", null, "/data/user/0/app.tileshell/files/a.mp4", bad))
        }
        // A real root beside a bad one still works, with or without its closing slash.
        assertTrue(PlayerRules.classify("file", null, "/data/user/0/app.tileshell/files/a.mp4", listOf("", "/data/user/0/app.tileshell/")) is PlayerRequest.Play)
    }

    @Test fun `caller-supplied text is bounded and has no control character before it reaches a line`() {
        // C-L1: a `%0A` in an address is a newline once decoded, and would start a forged line in the ring.
        val forged = "\n2026-10-05 12:00:00.000 wall=1 [cred] tmdb: saved"
        val request = PlayerRules.classify("http", "user@example.org$forged", "/a/clip$forged.mp4", roots) as PlayerRequest.Play
        val lines = listOf(
            PlayerRules.failure(PlayerErrorKind.UNREACHABLE, request, null, null).line,
            PlayerRules.failure(PlayerErrorKind.UNDECODABLE, request, null, null).line,
            PlayerRules.failure(PlayerErrorKind.UNDECODABLE, request, null, "qa$forged\r\u0000\u007f\u0085\u2028\u2029.mp4").line,
            PlayerRules.unsupported(PlayerRules.classify("rtsp$forged", "h", "/x", roots) as PlayerRequest.Unsupported).line,
            PlayerRules.unsupported(PlayerRules.request("rtsp$forged", "h", "/x", roots, false, false) { it } as PlayerRequest.Unsupported).line,
            "playing scheme=${request.scheme}",
        )
        for (line in lines) {
            assertTrue(line, line.none { it < ' ' || it in '\u007f'..'\u009f' || it == '\u2028' || it == '\u2029' })
            assertTrue(line, line.length <= 20 + PlayerRules.LINE_MAX)
        }
        assertEquals("cannot reach example.org2026-10-05 12:00:00.000 wall=1 [cred] tmdb: saved", lines[0])
        // Bounded: 80 characters of each piece and no more.
        val long = PlayerRules.classify("https", "h".repeat(500), "/" + "n".repeat(500), roots) as PlayerRequest.Play
        assertEquals(80, long.host!!.length)
        assertEquals(80, long.name.length)
        assertEquals(80, (PlayerRules.classify("s".repeat(500), "h", "/x", roots) as PlayerRequest.Unsupported).scheme.length)
        assertEquals("cannot decode " + "d".repeat(80), PlayerRules.failure(PlayerErrorKind.UNDECODABLE, long, null, "d".repeat(500)).line)
        assertEquals(80, PlayerRules.LINE_MAX)
        // Ordinary text is untouched.
        assertEquals("Blade Runner 2049 — the final cut.mkv", PlayerRules.lineText("Blade Runner 2049 — the final cut.mkv"))
        assertEquals("", PlayerRules.lineText(null))
    }

    @Test fun `the session title is bounded and has no control character`() {
        // B-11, C-L7: EXTRA_TITLE is text from an intent.
        fun title(extra: String?, displayName: String?, fallback: String) = PlayerRules.sessionTitle(true, { extra }, displayName, fallback)
        assertEquals("Blade Runner", title("Blade Runner", "file.mp4", "video"))
        assertEquals("line oneline two", title("line one\r\nline\u0000 two\u2028", null, "video"))
        assertEquals(PlayerRules.TITLE_MAX, title("t".repeat(100_000), null, "video").length)
        assertEquals(200, PlayerRules.TITLE_MAX)
        // No title, or one that is only control characters: the source's own name, then the fallback.
        assertEquals("qa-steps", title(null, "qa-steps.mp4", "video"))
        assertEquals("qa-steps", title(" \n\t ", "qa-steps.mp4", "video"))
        assertEquals("qasteps", title("", "qa\nsteps.mp4", "video"))
        assertEquals("video", title(null, null, "video"))
        assertEquals("video", title(null, ".mp4", "video"))
        // An extra that cannot be read is no title.
        assertEquals("qa-steps", PlayerRules.sessionTitle(true, { throw RuntimeException("BadParcelableException") }, "qa-steps.mp4", "video"))
    }

    @Test fun `C2-L3 EXTRA_TITLE names the session only for the shell's own launch - another app's is not even read`() {
        var reads = 0
        val extra = { reads++; "Your bank: tap to verify" }
        assertEquals("qa-steps", PlayerRules.sessionTitle(false, extra, "qa-steps.mp4", "video"))
        assertEquals("video", PlayerRules.sessionTitle(false, extra, null, "video"))
        assertEquals("another app's title was never read", 0, reads)
        assertEquals("Your bank: tap to verify", PlayerRules.sessionTitle(true, extra, "qa-steps.mp4", "video"))
        assertTrue(PlayerAccess.titleHonoured(isOwnUid = true))
        assertTrue(!PlayerAccess.titleHonoured(isOwnUid = false))
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

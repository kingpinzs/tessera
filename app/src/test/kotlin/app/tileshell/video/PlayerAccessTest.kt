package app.tileshell.video

import app.tileshell.media.FakeUriAccess
import app.tileshell.media.Platform
import app.tileshell.media.UriAccessPort
import app.tileshell.media.UriAccessRules
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 trust review, C-M4 (a), (b), (d): the exported player opens a source with the shell's identity, so who
 * started it decides what it may name. The rule over (scheme, own uid, caller may read) in every combination, the
 * caller-may-read rule over a stand-in port, and the path the scheme rule is given for a `file` source.
 */
class PlayerAccessTest {
    private val roots = listOf("/data/user/0/app.tileshell", "/storage/emulated/0/Android/data/app.tileshell/files")
    private val shell = 10234

    private fun platform(from: Int, launch: Set<String> = emptySet(), api: Int = 36) = FakeUriAccess(
        apiLevel = api, shell = shell, launchedFrom = Platform.Said(from),
        launchAnswer = { uri, mode -> if (uri in launch && mode == FakeUriAccess.READ) FakeUriAccess.YES else FakeUriAccess.NO },
    )

    // ---- C2-M1: who started the player, read once

    @Test fun `the launch is the shell's own only when the platform names the starter and it is the shell`() {
        assertTrue(PlayerAccess.launch(platform(shell), { false }, { null }).own)
        assertFalse(PlayerAccess.launch(platform(10999), { false }, { null }).own)
        assertFalse("Android did not say who", PlayerAccess.launch(platform(-1), { false }, { null }).own)
        assertFalse(PlayerAccess.launch(FakeUriAccess(shell = shell, launchedFrom = Platform.Threw("RemoteException")), { false }, { null }).own)
        // A platform port that throws is another app's launch, with nothing honoured.
        val broken = object : UriAccessPort by FakeUriAccess(shell = shell, launchedFrom = Platform.Said(shell)) {
            override fun launchedFromUid(): Platform<Int> = throw IllegalStateException("the port threw")
        }
        val launch = PlayerAccess.launch(broken, { true }, { longArrayOf(1, 2) })
        assertFalse(launch.own)
        assertNull(launch.queue)
        assertFalse(PlayerLaunch.OTHER.own)
        assertNull(PlayerLaunch.OTHER.queue)
    }

    @Test fun `the autoplay queue is taken for the shell's own launch only - another app's is not even read, and its line is written`() {
        val ids = longArrayOf(41, 42, 43)
        val own = PlayerAccess.launch(platform(shell), { true }, { ids })
        assertTrue(own.queue.contentEquals(ids))
        assertEquals(emptyList<String>(), own.lines)
        for (from in listOf(10999, -1)) {
            var reads = 0
            val other = PlayerAccess.launch(platform(from), { true }, { reads++; ids })
            assertNull("$from", other.queue)
            assertEquals("$from: the queue extra was never read", 0, reads)
            assertEquals("$from", listOf("queue ignored: not the shell's own launch"), other.lines)
            // No queue named: nothing to say.
            assertEquals(emptyList<String>(), PlayerAccess.launch(platform(from), { false }, { ids }).lines)
        }
        // A queue extra that cannot be read: no queue for the shell, no line and no crash for another app.
        assertNull(PlayerAccess.launch(platform(shell), { true }, { throw RuntimeException("BadParcelableException") }).queue)
        assertEquals(emptyList<String>(), PlayerAccess.launch(platform(-1), { throw StackOverflowError() }, { ids }).lines)
    }

    // ---- B2-M2: the saved media server's token rides only on the shell's own launch

    @Test fun `the server's token is resolved only for the shell's own launch of a source that could be a stream`() {
        val other = 10999
        fun use(launchedFrom: Int, stream: Boolean) = PlayerAccess.serverToken(PlayerAccess.launch(platform(launchedFrom), { false }, { null }).own, stream)
        // Own launch (the hub's Media server page): the resolver.
        assertEquals(ServerTokenUse.RESOLVE, use(shell, true))
        // Another app's launch, with its uid known (it shared its identity): no token, and the line.
        assertEquals(ServerTokenUse.NOT_GIVEN, use(other, true))
        // Android did not say who launched (any ordinary app, a link, adb): no token, and the line.
        assertEquals(ServerTokenUse.NOT_GIVEN, use(-1, true))
        // A source that could never be a server's stream: nothing to resolve and nothing to say, whoever launched.
        for (from in listOf(shell, other, -1)) assertEquals("$from", ServerTokenUse.NONE, use(from, false))
        assertEquals("server token not given: not the shell's own launch", PlayerAccess.LINE_TOKEN_NOT_GIVEN)
    }

    @Test fun `a source could be a server's stream only as http or https with a path ending in stream`() {
        val rules = app.tileshell.video.server.ServerRules
        assertTrue(rules.couldBeStream("http", "/Videos/e90356d9dbdedc30a27710927ef3ac87/stream"))
        assertTrue(rules.couldBeStream("HTTPS", "/Videos/e9/stream"))
        for ((scheme, path) in listOf("content" to "/Videos/e9/stream", "file" to "/Videos/e9/stream", null to "/Videos/e9/stream", "http" to "/Videos/e9/stream.mp4", "http" to "/qa-steps.mp4", "http" to null, "http" to "")) {
            assertFalse("$scheme $path", rules.couldBeStream(scheme, path))
        }
    }

    @Test fun `every combination of scheme, own uid and caller-may-read`() {
        val table = listOf(
            // scheme, own, mayRead -> decision
            listOf("content", true, true, SourceDecision.ALLOWED),
            listOf("content", true, false, SourceDecision.ALLOWED),
            listOf("content", false, true, SourceDecision.ALLOWED),
            listOf("content", false, false, SourceDecision.NO_GRANT),
            listOf("file", true, true, SourceDecision.ALLOWED),
            listOf("file", true, false, SourceDecision.ALLOWED),
            listOf("file", false, true, SourceDecision.UNSUPPORTED),
            listOf("file", false, false, SourceDecision.UNSUPPORTED),
            listOf("http", true, true, SourceDecision.ALLOWED),
            listOf("http", true, false, SourceDecision.ALLOWED),
            listOf("http", false, true, SourceDecision.ALLOWED),
            listOf("http", false, false, SourceDecision.ALLOWED),
            listOf("https", true, true, SourceDecision.ALLOWED),
            listOf("https", true, false, SourceDecision.ALLOWED),
            listOf("https", false, true, SourceDecision.ALLOWED),
            listOf("https", false, false, SourceDecision.ALLOWED),
        )
        for ((scheme, own, mayRead, expected) in table) {
            assertEquals("$scheme own=$own mayRead=$mayRead", expected, PlayerAccess.decide(scheme as String, own as Boolean, mayRead as Boolean))
        }
        // The scheme's case does not open a way round, and nothing else is ever allowed.
        assertEquals(SourceDecision.NO_GRANT, PlayerAccess.decide("CONTENT", false, false))
        assertEquals(SourceDecision.UNSUPPORTED, PlayerAccess.decide("FILE", false, true))
        for (other in listOf("rtsp", "ftp", "data", "android.resource", "", null)) {
            for (own in listOf(true, false)) for (mayRead in listOf(true, false)) {
                assertEquals("$other", SourceDecision.UNSUPPORTED, PlayerAccess.decide(other, own, mayRead))
            }
        }
    }

    @Test fun `another app's content it may not read is refused with its line, and nothing of the source is kept`() {
        val uri = Triple("content", "media", "/external/video/media/42")
        fun request(own: Boolean, mayRead: Boolean) = PlayerRules.request(uri.first, uri.second, uri.third, roots, own, mayRead) { it }
        assertEquals(PlayerRequest.Refused, request(own = false, mayRead = false))
        assertEquals(PlayerFailure("Can't play this address", "refused source: no grant"), PlayerRules.refused)
        // The same source from the shell, and from an app that may read it, is the MediaStore video it names.
        assertEquals(42L, (request(own = true, mayRead = false) as PlayerRequest.Play).mediaStoreId)
        assertEquals(42L, (request(own = false, mayRead = true) as PlayerRequest.Play).mediaStoreId)
        // Another provider's content, likewise.
        assertEquals(PlayerRequest.Refused, PlayerRules.request("content", "com.example.files", "/doc/7", roots, false, false) { it })
        assertTrue(PlayerRules.request("content", "com.example.files", "/doc/7", roots, false, true) { it } is PlayerRequest.Play)
    }

    @Test fun `a file source is the shell's own launch's alone, and then only under its own directories`() {
        val own = "/data/user/0/app.tileshell/files/clip.mp4"
        assertTrue(PlayerRules.request("file", null, own, roots, isOwnUid = true, callerMayRead = false) { it } is PlayerRequest.Play)
        // Another app naming the shell's own file: unsupported, whatever it may read.
        for (mayRead in listOf(true, false)) {
            assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("file", null, own, roots, isOwnUid = false, callerMayRead = mayRead) { it })
        }
        assertEquals("unsupported scheme=file", PlayerRules.unsupported(PlayerRequest.Unsupported("file")).line)
        // The shell's own launch is still held to its own directories.
        assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("file", null, "/sdcard/Movies/x.mp4", roots, true, true) { it })
    }

    @Test fun `http and https play for every caller, and other schemes for none`() {
        for (own in listOf(true, false)) for (mayRead in listOf(true, false)) {
            assertEquals("10.0.2.2:8090", (PlayerRules.request("http", "10.0.2.2:8090", "/a.mp4", roots, own, mayRead) { it } as PlayerRequest.Play).host)
            assertTrue(PlayerRules.request("https", "example.org", "/a.mp4", roots, own, mayRead) { it } is PlayerRequest.Play)
            assertEquals(PlayerRequest.Unsupported("rtsp"), PlayerRules.request("rtsp", "10.0.2.2", "/x", roots, own, mayRead) { it })
            assertEquals(PlayerRequest.Unsupported("none"), PlayerRules.request(null, null, null, roots, own, mayRead) { it })
        }
    }

    @Test fun `the autoplay queue is honoured for the shell's own launch only`() {
        assertTrue(PlayerAccess.queueHonoured(isOwnUid = true))
        assertFalse(PlayerAccess.queueHonoured(isOwnUid = false))
    }

    // ---- C2-M1, C2-L1: from a launch's source to what the player does - the whole mapping, over the one access rule

    private val media = "content://media/external/video/media/42"
    private val theirs = "content://com.other.files/share/clip.mp4"

    private fun source(text: String?, own: Boolean, access: FakeUriAccess, canonical: (String) -> String? = { it }): PlayerSource {
        val scheme = text?.substringBefore(':', "")?.ifEmpty { null }
        val rest = text?.substringAfter("://", "")
        val authority = rest?.substringBefore('/')?.ifEmpty { null }
        val path = rest?.takeIf { it.contains('/') }?.let { "/" + it.substringAfter('/').substringBefore('?') }
        return PlayerRules.source(text, scheme, authority, path, roots, own, access, canonical)
    }

    @Test fun `source - another app's content it could not read is refused - nothing of it is kept, and the lines say what the platform answered`() {
        val s = source(media, own = false, access = platform(-1))
        assertEquals(PlayerRequest.Refused, s.request)
        assertEquals(PlayerFailure("Can't play this address", "refused source: no grant"), s.failure)
        assertEquals(listOf("launch answer read: denied", "refused source: no grant"), s.lines)
        // API 34: the launch answer is not available.
        assertEquals(listOf("launch answer read: not available", "refused source: no grant"), source(media, false, platform(-1, api = 34)).lines)
    }

    @Test fun `the legitimate Open with round 1 proved on the device - a file manager's own FileProvider video with a read grant - plays BY THE RULE`() {
        // The starter is unnamed and the URI is its own provider's: the platform's launch answer is yes.
        val s = source(theirs, own = false, access = platform(-1, launch = setOf(theirs)))
        assertTrue(s.request is PlayerRequest.Play)
        assertNull(s.failure)
        assertEquals(listOf("launch answer read: granted", "source from another app: it had access at launch"), s.lines)
        // A starter the platform names, with its own provider: admitted without the launch answer.
        val named = source(theirs, own = false, access = platform(FakeUriAccess.OTHER))
        assertTrue(named.request is PlayerRequest.Play)
        assertEquals(listOf("source from another app: its own provider"), named.lines)
        assertEquals("source from another app: a read grant", PlayerAccess.line(UriAccessRules.ReadWhy.URI_GRANT))
        assertEquals("source from another app: the provider allows it", PlayerAccess.line(UriAccessRules.ReadWhy.PROVIDER_ALLOWS))
    }

    @Test fun `source - the shell's own launch plays everything the scheme rule takes, and the platform is asked nothing`() {
        for (uri in listOf(media, theirs, "http://10.0.2.2:8090/a.mp4", "https://example.org/a.mp4", "file:///data/user/0/app.tileshell/files/clip.mp4")) {
            val access = platform(shell)
            val s = source(uri, own = true, access = access)
            assertTrue(uri, s.request is PlayerRequest.Play)
            assertNull(uri, s.failure)
            assertEquals(uri, emptyList<String>(), s.lines)
            assertEquals(uri, emptyList<String>(), access.asked)
        }
        assertEquals(42L, (source(media, true, platform(shell)).request as PlayerRequest.Play).mediaStoreId)
    }

    @Test fun `source - a network address plays for every starter with no question asked, and no source is unsupported with its line`() {
        for (own in listOf(true, false)) {
            val access = platform(-1)
            assertEquals("10.0.2.2:8090", (source("http://10.0.2.2:8090/a.mp4", own, access).request as PlayerRequest.Play).host)
            assertEquals(emptyList<String>(), access.asked)
            val none = source(null, own, access)
            assertEquals(PlayerRequest.Unsupported("none"), none.request)
            assertEquals(listOf("unsupported scheme=none"), none.lines)
            assertEquals(listOf("unsupported scheme=rtsp"), source("rtsp://10.0.2.2/x", own, access).lines)
        }
    }

    @Test fun `source - a platform read that throws refuses another app's content`() {
        val broken = object : UriAccessPort by FakeUriAccess(launchAnswer = { _, _ -> FakeUriAccess.YES }) {
            override fun launchAnswer(uri: String, modeFlag: Int): Platform<Int> = throw IllegalStateException("the port threw")
        }
        val s = PlayerRules.source(media, "content", "media", "/external/video/media/42", roots, false, broken) { it }
        assertEquals(PlayerRequest.Refused, s.request)
        assertEquals(listOf("refused source: no grant"), s.lines)
        // The launch answer throwing inside the platform: its class is in the line, and the source is refused.
        val threw = FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw("SecurityException") })
        assertEquals(listOf("launch answer read: threw SecurityException", "refused source: no grant"), source(media, false, threw).lines)
    }

    @Test fun `source - C2-L1 an authority with a user id is asked of the platform as it is, and plays where the launch answer says so`() {
        val work = "content://10@media/external/video/media/42"
        assertTrue(source(work, false, platform(-1, launch = setOf(work))).request is PlayerRequest.Play)
        assertEquals(PlayerRequest.Refused, source(work, false, platform(-1)).request)
    }

    @Test fun `C2-L9 a file path another app named is decided before it is resolved - the disk is never touched for it`() {
        var resolved = 0
        val canonical = { path: String -> resolved++; path }
        val named = "file:///data/user/0/app.tileshell/files/clip.mp4"
        assertEquals(PlayerRequest.Unsupported("file"), source(named, own = false, access = platform(-1), canonical = canonical).request)
        assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("FILE", null, "/data/user/0/app.tileshell/files/clip.mp4", roots, false, true, canonical))
        assertEquals("another app's file path was never resolved", 0, resolved)
        // Nor is a refused content source's path, nor a network address's.
        source(media, false, platform(-1), canonical)
        source("http://10.0.2.2:8090/a/../b.mp4", false, platform(-1), canonical)
        assertEquals(0, resolved)
        // The shell's own file IS resolved, once, and is still held to its own directories.
        assertTrue(source(named, own = true, access = platform(shell), canonical = canonical).request is PlayerRequest.Play)
        assertEquals(1, resolved)
    }

    // ---- C2-M3: what the data source may open while one launch plays

    @Test fun `C2-M3 a network launch opens only http and https - a redirect or a nested reference to anything local is refused`() {
        val launch = "http://10.0.2.2:8090/list.m3u8"
        for (scheme in listOf("http", "https")) {
            assertTrue(PlayerAccess.mayOpen(scheme, launch, null, launch))
            assertTrue(PlayerAccess.mayOpen(scheme, launch, null, "https://cdn.example.org/seg1.ts"))
            assertTrue(PlayerAccess.mayOpen(scheme, launch, null, "HTTP://10.0.2.2:8090/seg2.ts"))
            for (asked in listOf(
                "content://media/external/video/media/42", "content://com.android.contacts/contacts/7/photo", "file:///data/user/0/app.tileshell/files/credentials_v1.json",
                "file:///data/user/0/app.tileshell/shared_prefs/start_theme.xml", "asset:///fonts/x.ttf", "android.resource://app.tileshell/raw/x", "rawresource:///12", "data:video/mp4;base64,AAAA",
                "rtmp://h/x", "udp://h:1", "/data/user/0/app.tileshell/files/x.mp4", "", "content:x", "CONTENT://media/external/video/media/42",
            )) assertFalse("$scheme launch asked for $asked", PlayerAccess.mayOpen(scheme, launch, null, asked))
            // Even the subtitle the shell found is not a network launch's to open (there is none for one).
            assertFalse(PlayerAccess.mayOpen(scheme, launch, "content://media/external/file/9", "content://media/external/file/9"))
        }
    }

    @Test fun `C2-M3 a content launch opens only the launch URI itself - and the subtitle the shell found beside its own item`() {
        val srt = "content://media/external/file/9"
        assertTrue(PlayerAccess.mayOpen("content", media, null, media))
        assertTrue(PlayerAccess.mayOpen("content", media, srt, srt))
        for (asked in listOf(
            "content://media/external/video/media/43", "$media/", "$media?x=1", theirs, srt, "file:///data/user/0/app.tileshell/files/credentials_v1.json",
            "http://10.0.2.2:8090/a.mp4", "https://example.org/a.mp4", "asset:///x", "android.resource://app.tileshell/raw/x", "",
        )) assertFalse("asked for $asked", PlayerAccess.mayOpen("content", media, null, asked))
        // The shell's own file launch: that file and nothing else.
        val file = "file:///data/user/0/app.tileshell/files/clip.mp4"
        assertTrue(PlayerAccess.mayOpen("file", file, null, file))
        assertFalse(PlayerAccess.mayOpen("file", file, null, "file:///data/user/0/app.tileshell/files/credentials_v1.json"))
        assertFalse(PlayerAccess.mayOpen("file", file, null, "http://10.0.2.2/x"))
    }

    @Test fun `C2-M3 the subtitle beside a video is looked for only for a MediaStore item the shell itself launched`() {
        assertTrue(PlayerAccess.subtitleLookedUp(isOwnUid = true, mediaStoreId = 42))
        assertFalse(PlayerAccess.subtitleLookedUp(isOwnUid = false, mediaStoreId = 42))
        assertFalse(PlayerAccess.subtitleLookedUp(isOwnUid = true, mediaStoreId = null))
        assertFalse(PlayerAccess.subtitleLookedUp(isOwnUid = false, mediaStoreId = null))
    }

    /**
     * C2-M3 is LIVE since phase 20: `media3-exoplayer-hls` is linked ON PURPOSE (phase 20 Decisions "HLS", r3 D10), so
     * a `DefaultMediaSourceFactory` — this player's and the music player's — makes an HLS source for a `.m3u8` address,
     * and a playlist goes on to name segments, keys, init segments and nested playlists of its own. The four points
     * this test's first form listed were re-reviewed against Media3 1.9.0's HLS module before it was changed —
     * `docs/plan/qa/phase-20/hls-rereview.md`, point by point — and each is held by a test:
     *  (1) a network launch's data source still admits only http(s): `C2-M3 a network launch opens only http and https`
     *      above (a `data:` key is refused with the rest);
     *  (2) every playlist, segment, key and init segment is opened through `GuardedDataSource` — HLS takes every data
     *      source from the ONE factory the media source factory was given (`DefaultHlsDataSourceFactory`), and that the
     *      factory IS the guarded one, asked before the upstream opens, is `media/UriAccessWiringScanTest`
     *      (`playerProblems` for this player; `musicSourceProblems` for the music player, whose rule is
     *      `music/MusicSourceRuleTest`);
     *  (3) the media server's token rides only on the saved server's own `/Videos/<id>/stream`:
     *      `video/ServerRulesTest` (`mayCarryToken`), asked per REQUEST by the resolver, so a segment on another
     *      host gets none;
     *  (4) a content launch opens only the launch URI: `C2-M3 a content launch opens only the launch URI itself` above —
     *      a playlist read from it gets none of its segments.
     *
     * THE DAY ANOTHER SOURCE MODULE IS ADDED this test fails again, and the same four points must be re-reviewed FOR
     * THAT MODULE before it is changed — its own data source factories in particular: `media3-exoplayer-dash` and
     * `-smoothstreaming` are handed the factory like HLS, but their manifests name addresses in other ways (base URLs,
     * templates, `xlink`, licence servers); `media3-exoplayer-rtsp` takes NO data source factory at all — it opens its
     * own sockets, so neither guard would see anything it does.
     */
    @Test fun `C2-M3 HLS is linked on purpose and is the only playlist or network source module - DASH, SmoothStreaming and RTSP are not on the classpath`() {
        fun linked(name: String) = try { Class.forName(name); true } catch (e: ClassNotFoundException) { false }
        // Linked, and what the re-review read: HLS takes its data sources from the factory it is given, and nowhere else.
        assertTrue(linked("androidx.media3.exoplayer.hls.HlsMediaSource"))
        val hlsFactory = Class.forName("androidx.media3.exoplayer.hls.DefaultHlsDataSourceFactory")
        assertEquals(listOf("androidx.media3.datasource.DataSource\$Factory"), hlsFactory.declaredFields.map { it.type.name })
        for (name in listOf(
            "androidx.media3.exoplayer.dash.DashMediaSource",
            "androidx.media3.exoplayer.smoothstreaming.SsMediaSource",
            "androidx.media3.exoplayer.rtsp.RtspMediaSource",
        )) assertFalse("$name is linked: re-review both players' data sources for it (see this test's comment) before changing this", linked(name))
        // The progressive source both players use is there, so the checks above are not vacuous.
        assertTrue(linked("androidx.media3.exoplayer.source.ProgressiveMediaSource"))
    }

    @Test fun `a file source's path is made canonical before the rule sees it, and a link out of the shell's files is not its own`() {
        val base = Files.createTempDirectory("player").toFile().canonicalFile
        val own = File(base, "own/files").apply { mkdirs() }
        val outside = File(base, "other").apply { mkdirs() }
        File(outside, "secret.mp4").writeText("x")
        File(own, "clip.mp4").writeText("x")
        Files.createSymbolicLink(File(own, "link.mp4").toPath(), File(outside, "secret.mp4").toPath())
        Files.createSymbolicLink(File(own, "dir").toPath(), outside.toPath())
        val ownRoots = listOf(File(base, "own").path)
        fun play(path: String): PlayerRequest {
            return PlayerRules.request("file", null, path, ownRoots, isOwnUid = true, callerMayRead = false) { File(it).canonicalPath }
        }
        assertTrue(play(File(own, "clip.mp4").path) is PlayerRequest.Play)
        assertEquals(PlayerRequest.Unsupported("file"), play(File(own, "link.mp4").path))
        assertEquals(PlayerRequest.Unsupported("file"), play(File(own, "dir/secret.mp4").path))
        assertEquals(PlayerRequest.Unsupported("file"), play(File(own, "../../other/secret.mp4").path))
        assertEquals(PlayerRequest.Unsupported("file"), play(File(own, "../../../x/secret.mp4").path))
        // `..` that stays inside the shell's files is still the shell's file.
        assertTrue(play(File(own, "sub/../clip.mp4").path) is PlayerRequest.Play)
        // A path that cannot be resolved is no path: nothing is played from it.
        assertNull(PlayerRules.sourcePath("file", "/data/user/0/app.tileshell/files/a.mp4") { throw java.io.IOException("no") })
        assertNull(PlayerRules.sourcePath("FILE", "/x") { null })
        assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("file", null, null, roots, true, false) { it })
        // Any other scheme's path is left as it is, and never touches the disk.
        assertEquals("/external/video/media/4", PlayerRules.sourcePath("content", "/external/video/media/4") { error("not asked") })
        assertEquals("/a/../b.mp4", PlayerRules.sourcePath("https", "/a/../b.mp4") { error("not asked") })
    }
}

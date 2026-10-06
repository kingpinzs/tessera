package app.tileshell.video

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

    @Test fun `the launcher is the shell only when its uid is known and is the shell's`() {
        assertTrue(PlayerAccess.isOwnUid(shell, shell))
        assertFalse(PlayerAccess.isOwnUid(10999, shell))
        assertFalse("Android did not say who", PlayerAccess.isOwnUid(PlayerAccess.UNKNOWN_UID, shell))
        assertFalse(PlayerAccess.isOwnUid(PlayerAccess.UNKNOWN_UID, PlayerAccess.UNKNOWN_UID))
        assertEquals(-1, PlayerAccess.UNKNOWN_UID)
    }

    // ---- B2-M2: the saved media server's token rides only on the shell's own launch

    @Test fun `the server's token is resolved only for the shell's own launch of a source that could be a stream`() {
        val other = 10999
        fun use(launchedFrom: Int, stream: Boolean) = PlayerAccess.serverToken(PlayerAccess.isOwnUid(launchedFrom, shell), stream)
        // Own launch (the hub's Media server page): the resolver.
        assertEquals(ServerTokenUse.RESOLVE, use(shell, true))
        // Another app's launch, with its uid known (it shared its identity): no token, and the line.
        assertEquals(ServerTokenUse.NOT_GIVEN, use(other, true))
        // Android did not say who launched (any ordinary app, a link, adb): no token, and the line.
        assertEquals(ServerTokenUse.NOT_GIVEN, use(PlayerAccess.UNKNOWN_UID, true))
        // A source that could never be a server's stream: nothing to resolve and nothing to say, whoever launched.
        for (from in listOf(shell, other, PlayerAccess.UNKNOWN_UID)) assertEquals("$from", ServerTokenUse.NONE, use(from, false))
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
        fun request(own: Boolean, mayRead: Boolean) = PlayerRules.request(uri.first, uri.second, uri.third, roots, own, mayRead)
        assertEquals(PlayerRequest.Refused, request(own = false, mayRead = false))
        assertEquals(PlayerFailure("Can't play this address", "refused source: no grant"), PlayerRules.refused)
        // The same source from the shell, and from an app that may read it, is the MediaStore video it names.
        assertEquals(42L, (request(own = true, mayRead = false) as PlayerRequest.Play).mediaStoreId)
        assertEquals(42L, (request(own = false, mayRead = true) as PlayerRequest.Play).mediaStoreId)
        // Another provider's content, likewise.
        assertEquals(PlayerRequest.Refused, PlayerRules.request("content", "com.example.files", "/doc/7", roots, false, false))
        assertTrue(PlayerRules.request("content", "com.example.files", "/doc/7", roots, false, true) is PlayerRequest.Play)
    }

    @Test fun `a file source is the shell's own launch's alone, and then only under its own directories`() {
        val own = "/data/user/0/app.tileshell/files/clip.mp4"
        assertTrue(PlayerRules.request("file", null, own, roots, isOwnUid = true, callerMayRead = false) is PlayerRequest.Play)
        // Another app naming the shell's own file: unsupported, whatever it may read.
        for (mayRead in listOf(true, false)) {
            assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("file", null, own, roots, isOwnUid = false, callerMayRead = mayRead))
        }
        assertEquals("unsupported scheme=file", PlayerRules.unsupported(PlayerRequest.Unsupported("file")).line)
        // The shell's own launch is still held to its own directories.
        assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("file", null, "/sdcard/Movies/x.mp4", roots, true, true))
    }

    @Test fun `http and https play for every caller, and other schemes for none`() {
        for (own in listOf(true, false)) for (mayRead in listOf(true, false)) {
            assertEquals("10.0.2.2:8090", (PlayerRules.request("http", "10.0.2.2:8090", "/a.mp4", roots, own, mayRead) as PlayerRequest.Play).host)
            assertTrue(PlayerRules.request("https", "example.org", "/a.mp4", roots, own, mayRead) is PlayerRequest.Play)
            assertEquals(PlayerRequest.Unsupported("rtsp"), PlayerRules.request("rtsp", "10.0.2.2", "/x", roots, own, mayRead))
            assertEquals(PlayerRequest.Unsupported("none"), PlayerRules.request(null, null, null, roots, own, mayRead))
        }
    }

    @Test fun `the autoplay queue is honoured for the shell's own launch only`() {
        assertTrue(PlayerAccess.queueHonoured(isOwnUid = true))
        assertFalse(PlayerAccess.queueHonoured(isOwnUid = false))
        assertFalse(PlayerAccess.queueHonoured(PlayerAccess.isOwnUid(PlayerAccess.UNKNOWN_UID, shell)))
    }

    /** A stand-in for Android's answers about one source. */
    private class Port(
        override val launchedFromUid: Int, val provider: Int? = null, val grants: Set<Int> = emptySet(),
        val providerAllows: Set<Int> = emptySet(), val launchAccess: Boolean = false,
    ) : PlayerCallerPort {
        val asked = mutableListOf<String>()
        override fun providerUid(): Int? = provider.also { asked += "provider" }
        override fun holdsReadGrant(uid: Int): Boolean = (uid in grants).also { asked += "grant $uid" }
        override fun providerAllowsRead(uid: Int): Boolean = (uid in providerAllows).also { asked += "allows $uid" }
        override fun hadAccessAtLaunch(): Boolean = launchAccess.also { asked += "launch" }
    }

    @Test fun `another app may read its own provider's content, what it holds a grant for, and what the provider allows it`() {
        val app = 10777
        assertEquals(CallerRead.OWN_PROVIDER, PlayerAccess.callerMayRead(Port(app, provider = app)))
        assertEquals(CallerRead.URI_GRANT, PlayerAccess.callerMayRead(Port(app, provider = 10001, grants = setOf(app))))
        assertEquals(CallerRead.PROVIDER_ALLOWS, PlayerAccess.callerMayRead(Port(app, provider = 10001, providerAllows = setOf(app))))
        assertEquals(CallerRead.LAUNCH_ACCESS, PlayerAccess.callerMayRead(Port(app, provider = 10001, launchAccess = true)))
        // None of them: no. Somebody else's grant, somebody else's provider, a provider that cannot be seen: no.
        assertEquals(CallerRead.NONE, PlayerAccess.callerMayRead(Port(app, provider = 10001)))
        assertEquals(CallerRead.NONE, PlayerAccess.callerMayRead(Port(app, provider = null, grants = setOf(10001, shell), providerAllows = setOf(shell))))
        assertEquals("no grant", CallerRead.NONE.word)
    }

    @Test fun `an unknown launcher is another app - no uid check is made for it, and only the platform's launch answer admits it`() {
        val unknown = Port(PlayerAccess.UNKNOWN_UID, provider = PlayerAccess.UNKNOWN_UID, grants = setOf(PlayerAccess.UNKNOWN_UID), providerAllows = setOf(PlayerAccess.UNKNOWN_UID))
        assertEquals(CallerRead.NONE, PlayerAccess.callerMayRead(unknown))
        assertEquals("nothing is asked about uid -1", listOf("launch"), unknown.asked)
        assertEquals(CallerRead.LAUNCH_ACCESS, PlayerAccess.callerMayRead(Port(PlayerAccess.UNKNOWN_UID, launchAccess = true)))
        // What a phone below Android 15 answers for an app that does not share its identity: every answer is "no".
        assertEquals(CallerRead.NONE, PlayerAccess.callerMayRead(Port(PlayerAccess.UNKNOWN_UID)))
        assertEquals("source from another app: it had access at launch", PlayerAccess.line(CallerRead.LAUNCH_ACCESS))
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
            val canonical = PlayerRules.sourcePath("file", path) { File(it).canonicalPath }
            return PlayerRules.request("file", null, canonical, ownRoots, isOwnUid = true, callerMayRead = false)
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
        assertEquals(PlayerRequest.Unsupported("file"), PlayerRules.request("file", null, null, roots, true, false))
        // Any other scheme's path is left as it is, and never touches the disk.
        assertEquals("/external/video/media/4", PlayerRules.sourcePath("content", "/external/video/media/4") { error("not asked") })
        assertEquals("/a/../b.mp4", PlayerRules.sourcePath("https", "/a/../b.mp4") { error("not asked") })
    }
}

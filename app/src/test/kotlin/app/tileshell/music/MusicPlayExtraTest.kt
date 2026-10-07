package app.tileshell.music

import app.tileshell.media.FakeUriAccess
import app.tileshell.media.Platform
import app.tileshell.music.MusicPlayExtra.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 18's ADD to phase 10 (r3 D5, "below Q-18-2"; GATE item f): what a play extra on MusicActivity's launch does.
 * Honoured only for the shell's own launch; the URI form only for the shell's own FileProvider.
 */
class MusicPlayExtraTest {
    private val me = 10177
    private val authority = "app.tileshell.files"
    private val uri = "content://app.tileshell.files/root/storage/emulated/0/QA-Files/hidden/qa-hidden.mp3"

    private fun decide(from: Int = me, id: Long? = null, uri: String? = null) = MusicPlayExtra.decide(from, me, id, uri, authority)

    @Test
    fun `no play extra - Music opens as it always does and nothing is said, whoever launched it`() {
        assertEquals(Decision.None, decide())
        assertEquals(Decision.None, decide(from = 2000))
    }

    @Test
    fun `the shell's own launch with an audio id plays that id`() {
        assertEquals(Decision.PlayId(42), decide(id = 42))
    }

    @Test
    fun `the shell's own launch with its own FileProvider's uri plays that file`() {
        assertEquals(Decision.PlayUri(uri), decide(uri = uri))
    }

    @Test
    fun `a foreign caller is ignored with not the shell - the id form and the uri form alike`() {
        // `adb shell am start` (uid 2000), another app, and a launch the platform does not name.
        for (from in listOf(2000, 10178, -1, 0)) {
            assertEquals("$from", Decision.Ignored("not the shell"), decide(from = from, id = 42))
            assertEquals("$from", Decision.Ignored("not the shell"), decide(from = from, uri = uri))
        }
        assertEquals("play extra ignored: not the shell", (decide(from = 2000, id = 42) as Decision.Ignored).line)
    }

    @Test
    fun `a uri whose authority is not the shell's FileProvider is refused, even from the shell's own uid`() {
        val foreign = listOf(
            "content://media/external/audio/media/42",
            "content://com.evil.provider/root/storage/emulated/0/a.mp3",
            "content://app.tileshell.files.evil/root/a.mp3",
            "content://evil.app.tileshell.files/root/a.mp3",
            // Another user's provider of the same name, and a user-info trick.
            "content://10@app.tileshell.files/root/a.mp3",
            "content://app.tileshell.files@evil/root/a.mp3",
            "content://APP.TILESHELL.FILES/root/a.mp3",
            "content:///root/a.mp3",
        )
        for (u in foreign) assertEquals(u, Decision.Ignored("not the shell's file provider"), decide(uri = u))
        assertEquals("play extra ignored: not the shell's file provider", (decide(uri = foreign[0]) as Decision.Ignored).line)
    }

    @Test
    fun `a uri that is not a content uri naming a file is refused`() {
        val notContent = listOf(
            "file:///storage/emulated/0/a.mp3",
            "/storage/emulated/0/a.mp3",
            "http://app.tileshell.files/root/a.mp3",
            "CONTENT://app.tileshell.files/root/a.mp3",
            "content:app.tileshell.files/root/a.mp3",
            "",
            "content://app.tileshell.files/root/a.mp3\n[music] play file /x (not in library)",
            "content://app.tileshell.files/root/a b.mp3",
            // No file named, or something after the path that could re-read it.
            "content://app.tileshell.files",
            "content://app.tileshell.files/",
            "content://app.tileshell.files/root/a.mp3?x=1",
            "content://app.tileshell.files/root/a.mp3#frag",
        )
        for (u in notContent) assertEquals(u, Decision.Ignored("not a content uri"), decide(uri = u))
    }

    @Test
    fun `an id that is no audio id, and an id with a uri, are ignored`() {
        assertEquals(Decision.Ignored("not an audio id"), decide(id = 0))
        assertEquals(Decision.Ignored("not an audio id"), decide(id = -7))
        assertEquals(Decision.Ignored("an id and a uri"), decide(id = 42, uri = uri))
    }

    @Test
    fun `an empty authority honours nothing`() {
        assertEquals(Decision.Ignored("not the shell's file provider"), MusicPlayExtra.decide(me, me, null, "content:///root/a.mp3", ""))
    }

    // ---- who launched: the platform port's answer, or nobody (media/UriAccessWiringScanTest holds the call sites)

    @Test
    fun `the launch's caller is the uid the port names - the shell's own launch is honoured`() {
        val shell = FakeUriAccess(launchedFrom = Platform.Said(FakeUriAccess.SHELL))
        assertEquals(FakeUriAccess.SHELL, MusicPlayExtra.launchCaller(shell))
        assertEquals(Decision.PlayId(42), MusicPlayExtra.decide(MusicPlayExtra.launchCaller(shell), shell.shellUid(), 42, null, authority))
        assertEquals(FakeUriAccess.CALLER, MusicPlayExtra.launchCaller(FakeUriAccess(launchedFrom = Platform.Said(FakeUriAccess.CALLER))))
    }

    @Test
    fun `a starter the platform does not name, or a port that throws, is nobody - the extra is ignored`() {
        for (answer in listOf<Platform<Int>>(Platform.Said(-1), Platform.Said(-10077), Platform.Threw("SecurityException"), Platform.Threw("OutOfMemoryError"))) {
            val port = FakeUriAccess(launchedFrom = answer)
            assertEquals("$answer", MusicPlayExtra.NO_CALLER, MusicPlayExtra.launchCaller(port))
            assertEquals("$answer", Decision.Ignored("not the shell"), MusicPlayExtra.decide(MusicPlayExtra.launchCaller(port), port.shellUid(), 42, null, authority))
        }
        // Nobody is the platform's own "no uid", and never a uid an app can have.
        assertEquals(-1, MusicPlayExtra.NO_CALLER)
    }
}

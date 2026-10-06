package app.tileshell.photos

import app.tileshell.media.ContentUriText
import app.tileshell.media.FakeUriAccess
import app.tileshell.media.Platform
import app.tileshell.media.ProviderFacts
import app.tileshell.media.UriAccessPort
import app.tileshell.photos.ViewerRules.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trust reviews' C-M4 (b), (c), C2-M1 and C2-L1: what the exported viewer shows, and offers, by who started it —
 * the decision ([ViewerRules.open]) and the whole mapping from the launch to the viewer's state ([ViewerRules.state]).
 * Whether a starter may read is `UriAccessRules.starterMayRead` (`media/UriAccessRulesTest` has its every branch).
 */
class ViewerRulesTest {
    private val shell = FakeUriAccess.SHELL
    private val other = FakeUriAccess.OTHER
    private val mediaRow = "content://media/external/images/media/41"
    private val othersFile = "content://com.other.files/share/p.jpg"
    private val callersFile = "content://com.caller.files/share/p.jpg"
    private val yes = FakeUriAccess.YES
    private val no = FakeUriAccess.NO

    /** [from]: what `getLaunchedFromUid()` answers (-1 = unnamed). [launch]: the URIs the platform's launch answer admits. */
    private fun platform(from: Int, launch: Set<String> = emptySet(), api: Int = 36, grants: Set<Triple<String, Int, Int>> = emptySet(), provider: (String, Int, Int) -> Platform<Int> = { _, _, _ -> no }) =
        FakeUriAccess(apiLevel = api, launchedFrom = Platform.Said(from), grants = grants, providerAnswer = provider, launchAnswer = { uri, mode -> if (uri in launch && mode == FakeUriAccess.READ) yes else no })

    private fun open(uri: String, access: FakeUriAccess) = ViewerRules.open(ContentUriText.parse(uri), access)

    private val refused = ViewerRules.Open(show = false, mayChange = false, line = "refused view: no grant", request = "viewer request from another app: refused", launch = "launch answer read: denied")
    private val refusedUnnamed = refused.copy(request = "viewer request from an unnamed app: refused")
    private val readOnly = ViewerRules.Open(show = true, mayChange = false, line = null, request = "viewer request from another app: shown read-only")
    private val readOnlyUnnamed = ViewerRules.Open(show = true, mayChange = false, line = null, request = "viewer request from an unnamed app: shown read-only", launch = "launch answer read: granted")

    @Test
    fun `the shell itself - shown, with every action, and the platform is asked nothing about the URI`() {
        val access = platform(from = shell)
        assertEquals(ViewerRules.Open(show = true, mayChange = true, line = null, request = "viewer request from the shell: shown"), open(mediaRow, access))
        assertEquals(emptyList<String>(), access.asked)
    }

    @Test
    fun `another app with no access to a MediaStore picture - refused (the shell's own reach is not the caller's)`() {
        // C-M4 (b): an app with no media permission names any MediaStore item; MediaStore, asked, says that uid may not.
        assertEquals(refused, open(mediaRow, platform(from = other)))
        // What some OTHER uid may do, what the SHELL holds, and a WRITE grant say nothing about this starter's reading.
        val grants = setOf(Triple(mediaRow, FakeUriAccess.CALLER, FakeUriAccess.READ), Triple(mediaRow, shell, FakeUriAccess.READ), Triple(mediaRow, other, FakeUriAccess.WRITE))
        assertEquals(refused, open(mediaRow, platform(from = other, grants = grants, provider = { _, _, mode -> if (mode == FakeUriAccess.WRITE) yes else no })))
        assertEquals(refusedUnnamed, open(mediaRow, platform(from = -1, grants = grants)))
    }

    @Test
    fun `another app that may read the URI itself - shown, read-only - by each of the ways the platform can say so`() {
        assertEquals("its own provider", readOnly, open(othersFile, platform(from = other)))
        assertEquals("a read grant it holds", readOnly, open(callersFile, platform(from = other, grants = setOf(Triple(callersFile, other, FakeUriAccess.READ)))))
        assertEquals("MediaStore says it may (API 35+)", readOnly, open(mediaRow, platform(from = other, provider = { uri, uid, mode -> if (uri == mediaRow && uid == other && mode == FakeUriAccess.READ) yes else no })))
        assertEquals("the launch answer, for a named app", readOnly.copy(launch = "launch answer read: granted"), open(mediaRow, platform(from = other, launch = setOf(mediaRow))))
        assertEquals("the launch answer, for an unnamed app", readOnlyUnnamed, open(mediaRow, platform(from = -1, launch = setOf(mediaRow))))
    }

    @Test
    fun `the legitimate Open with round 1 proved on the device - a file manager's own FileProvider image with a read grant - is shown read-only BY THE RULE`() {
        // The device's leg (k): the starter is unnamed, the URI is its own provider's — the platform's launch answer is yes.
        val state = ViewerRules.state({ othersFile }, platform(from = -1, launch = setOf(othersFile)))
        assertEquals(ViewerRules.State(uri = othersFile, refused = false, mayChange = false, lines = listOf("viewer request from an unnamed app: shown read-only", "launch answer read: granted")), state)
    }

    @Test
    fun `another app's own provider is not a second app's - refused`() {
        assertEquals(refused, open(callersFile, platform(from = other)))
    }

    @Test
    fun `an unnamed starter is another app - never the shell - and nothing keyed by a uid is asked for it`() {
        // Every table would say yes for uid -1 and for the shell's own uid: an unnamed app still gets nothing from them.
        val access = FakeUriAccess(
            launchedFrom = Platform.Said(-1), providers = mapOf("media" to ProviderFacts(-1, true), "com.other.files" to ProviderFacts(shell, false)),
            grants = setOf(Triple(mediaRow, -1, FakeUriAccess.READ), Triple(mediaRow, shell, FakeUriAccess.READ)), providerAnswer = { _, _, _ -> yes },
        )
        assertEquals(refusedUnnamed, open(mediaRow, access))
        assertEquals(refusedUnnamed, open(othersFile, access))
        assertEquals("only the launch answer was asked", listOf("launch $mediaRow 1", "launch $othersFile 1"), access.asked)
    }

    @Test
    fun `API 34 - nothing can be asked for an unnamed app, so its content URI is refused - and the shell's own grants do not count`() {
        val api34 = FakeUriAccess(
            apiLevel = 34, launchedFrom = Platform.Said(-1), launchAnswer = { _, _ -> yes }, providerAnswer = { _, _, _ -> yes },
            grants = setOf(Triple(othersFile, shell, FakeUriAccess.READ), Triple(mediaRow, shell, FakeUriAccess.READ)),
        )
        val refused34 = refusedUnnamed.copy(launch = "launch answer read: not available")
        assertEquals(refused34, open(othersFile, api34))
        assertEquals(refused34, open(mediaRow, api34))
        assertEquals("the launch answer does not exist there and is not called", emptyList<String>(), api34.asked)
    }

    @Test
    fun `C2-L1 a work-profile or clone Open with - an authority with a user id - is shown where the platform says the starter could read it`() {
        for (uri in listOf("content://10@media/external/images/media/41", "content://10%40media/external/images/media/41")) {
            assertEquals(uri, readOnlyUnnamed, open(uri, platform(from = -1, launch = setOf(uri))))
            assertEquals(uri, refusedUnnamed, open(uri, platform(from = -1)))
            // And the URI opened is the caller's text, unchanged: the platform splits user and authority itself.
            assertEquals(uri, ViewerRules.state({ uri }, platform(from = -1, launch = setOf(uri))).uri)
        }
        // Anything that is not a content URI with an authority is refused without a question.
        for (uri in listOf("content:///x", "content:x")) {
            val access = platform(from = other, launch = setOf(uri))
            assertEquals(uri, refused.copy(launch = null), open(uri, access))
            assertEquals(emptyList<String>(), access.asked)
        }
    }

    @Test
    fun `every combination - only the shell may change anything, and nothing is shown without the platform's word`() {
        val uris = listOf(mediaRow, othersFile, callersFile)
        for (uri in uris) for (from in listOf(shell, other, FakeUriAccess.CALLER, -1, 0, 10999)) for (api in listOf(34, 36)) {
            for (callerGrant in listOf(false, true)) for (shellGrant in listOf(false, true)) for (atLaunch in listOf(false, true)) for (provider in listOf(false, true)) {
                val access = platform(
                    from = from, api = api, launch = if (atLaunch) setOf(uri) else emptySet(),
                    grants = buildSet { if (callerGrant && from >= 0) add(Triple(uri, from, FakeUriAccess.READ)); if (shellGrant) add(Triple(uri, shell, FakeUriAccess.READ)) },
                    provider = { _, _, _ -> if (provider) yes else no },
                )
                val d = open(uri, access)
                val case = "uri=$uri from=$from api=$api callerGrant=$callerGrant shellGrant=$shellGrant atLaunch=$atLaunch provider=$provider"
                val isShell = from == shell
                val named = from >= 0
                val facts = access.providers[ContentUriText.parse(uri).authority]
                val owns = named && facts?.ownerUid == from
                val providerSays = named && provider && facts?.forceUriPermissions == true && api >= 35
                // A grant the SHELL holds never counts; the launch answer counts with or without a named starter, from API 35.
                val expectShow = isShell || (api >= 35 && atLaunch) || owns || (named && callerGrant) || providerSays
                assertEquals(case, expectShow, d.show)
                assertEquals(case, isShell, d.mayChange)
                assertEquals(case, if (expectShow) null else "refused view: no grant", d.line)
                // The state the viewer holds is exactly the decision: no URI to open unless shown, no change unless the shell.
                val state = ViewerRules.state({ uri }, access)
                assertEquals(case, if (expectShow) uri else null, state.uri)
                assertEquals(case, !expectShow, state.refused)
                assertEquals(case, isShell, state.mayChange)
            }
        }
    }

    // ---- C2-M1: from the launch to the viewer's state

    @Test
    fun `state - a refused picture is never handed on to be opened, and its lines are the request, the launch answer and the refusal`() {
        val state = ViewerRules.state({ mediaRow }, platform(from = -1))
        assertEquals(ViewerRules.State(uri = null, refused = true, mayChange = false, lines = listOf("viewer request from an unnamed app: refused", "launch answer read: denied", "refused view: no grant")), state)
    }

    @Test
    fun `state - the shell's own launch opens the very text the intent named, with every action`() {
        val state = ViewerRules.state({ mediaRow }, platform(from = shell))
        assertEquals(ViewerRules.State(uri = mediaRow, refused = false, mayChange = true, lines = listOf("viewer request from the shell: shown")), state)
    }

    @Test
    fun `state - no data, data that is not content, and data that cannot be read show nothing, refuse nothing and ask nothing`() {
        val nothing = ViewerRules.State(uri = null, refused = false, mayChange = false, lines = emptyList())
        for ((name, data) in listOf<Pair<String, () -> String?>>(
            "none" to { null }, "a file" to { "file:///sdcard/DCIM/x.jpg" }, "http" to { "https://example.org/x.jpg" }, "upper case" to { "CONTENT://media/external/images/media/41" },
            "unreadable" to { throw RuntimeException("BadParcelableException") }, "an Error" to { throw StackOverflowError() },
        )) {
            // Even for the shell's own launch: there is nothing to show.
            val access = platform(from = shell, launch = setOf(mediaRow))
            assertEquals(name, nothing, ViewerRules.state(data, access))
            assertEquals(name, emptyList<String>(), access.asked)
        }
    }

    @Test
    fun `state - the intent's data is read once`() {
        var reads = 0
        ViewerRules.state({ reads++; mediaRow }, platform(from = -1, launch = setOf(mediaRow)))
        assertEquals(1, reads)
    }

    @Test
    fun `state - a platform read that throws refuses - it is never the shell's own launch and never shown`() {
        val broken = object : UriAccessPort by FakeUriAccess(launchedFrom = Platform.Said(shell)) {
            override fun launchedFromUid(): Platform<Int> = throw IllegalStateException("the port threw")
        }
        assertEquals(
            ViewerRules.State(uri = null, refused = true, mayChange = false, lines = listOf("viewer request unreadable (IllegalStateException)", "refused view: no grant")),
            ViewerRules.state({ mediaRow }, broken),
        )
        // The launch answer throwing inside the platform is an answer of "no", with its class in the line.
        val threw = FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw("SecurityException") })
        assertEquals(listOf("viewer request from an unnamed app: refused", "launch answer read: threw SecurityException", "refused view: no grant"), ViewerRules.state({ mediaRow }, threw).lines)
        // getLaunchedFromUid throwing inside the platform: unnamed, another app.
        val unnamed = FakeUriAccess(launchedFrom = Platform.Threw("RemoteException"), launchAnswer = { _, _ -> yes })
        assertEquals(ViewerRules.State(uri = mediaRow, refused = false, mayChange = false, lines = listOf("viewer request from an unnamed app: shown read-only", "launch answer read: granted")), ViewerRules.state({ mediaRow }, unnamed))
    }

    @Test
    fun `the actions offered - every combination`() {
        for (hasRow in listOf(false, true)) for (editable in listOf(false, true)) for (several in listOf(false, true)) for (mayChange in listOf(false, true)) {
            val actions = ViewerRules.actions(hasRow, editable, several, mayChange)
            val case = "hasRow=$hasRow editable=$editable several=$several mayChange=$mayChange -> $actions"
            assertTrue(case, Action.SHARE in actions)
            assertEquals(case, hasRow, Action.FILE_INFORMATION in actions)
            assertEquals(case, several, Action.SLIDESHOW in actions)
            assertEquals(case, mayChange && hasRow && editable, Action.EDIT in actions)
            assertEquals(case, mayChange && hasRow, Action.DELETE in actions)
            assertEquals(case, mayChange && hasRow, Action.SET_AS in actions)
            assertEquals(case, actions.size, actions.toSet().size)
        }
    }

    @Test
    fun `C-M4 (c) another app's MediaStore picture, found by the shell's own query - the picture, Share and File information only`() {
        assertEquals(listOf(Action.SHARE, Action.FILE_INFORMATION), ViewerRules.actions(hasRow = true, editable = true, several = false, mayChange = false))
        // Inside Photos itself nothing is lost.
        assertEquals(
            listOf(Action.SHARE, Action.EDIT, Action.DELETE, Action.SLIDESHOW, Action.SET_AS, Action.FILE_INFORMATION),
            ViewerRules.actions(hasRow = true, editable = true, several = true, mayChange = true),
        )
    }
}

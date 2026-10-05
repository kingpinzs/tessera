package app.tileshell.photos

import app.tileshell.media.ContentUriText
import app.tileshell.media.FakeUriAccess
import app.tileshell.photos.ViewerRules.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trust review's C-M4 (b), (c): what the exported viewer shows, and offers, by who started it. */
class ViewerRulesTest {
    private val shell = FakeUriAccess.SHELL
    private val other = FakeUriAccess.OTHER
    private val mediaRow = "content://media/external/images/media/41"
    private val othersFile = "content://com.other.files/share/p.jpg"
    private val callersFile = "content://com.caller.files/share/p.jpg"

    /** [launch]: the URIs the platform says the launcher could read at launch (API 35+); empty = API 34, or "no". */
    private fun open(uri: String, from: Int?, access: FakeUriAccess = FakeUriAccess(), launch: Set<String> = emptySet()) =
        ViewerRules.open(ContentUriText.parse(uri), from, shell, access) { asked += it; it in launch }

    private val asked = mutableListOf<String>()

    private val refused = ViewerRules.Open(show = false, mayChange = false, line = "refused view: no grant", request = "viewer request from another app: refused")
    private val refusedUnnamed = refused.copy(request = "viewer request from an unnamed app: refused")
    private val readOnly = ViewerRules.Open(show = true, mayChange = false, line = null, request = "viewer request from another app: shown read-only")
    private val readOnlyUnnamed = readOnly.copy(request = "viewer request from an unnamed app: shown read-only")

    @Test
    fun `the shell itself - shown, with every action, and the platform is asked nothing`() {
        val access = FakeUriAccess()
        assertEquals(ViewerRules.Open(show = true, mayChange = true, line = null, request = "viewer request from the shell: shown"), open(mediaRow, shell, access))
        assertEquals(emptyList<String>(), access.asked)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `another app with no access to a MediaStore picture - refused (the shell's own reach is not the caller's)`() {
        // C-M4 (b): an app with no media permission names any MediaStore item. API 34: the provider cannot be asked.
        assertEquals(refused, open(mediaRow, other))
        // API 35+: asked, the provider says that uid may not.
        assertEquals(refused, open(mediaRow, other, FakeUriAccess(providerRead = { _, _ -> false })))
        // What some OTHER uid may do, and a WRITE grant, say nothing about this caller's reading.
        assertEquals(refused, open(mediaRow, other, FakeUriAccess(readGrants = setOf(mediaRow to FakeUriAccess.CALLER))))
        assertEquals(refused, open(mediaRow, other, FakeUriAccess(writeGrants = setOf(mediaRow to other), providerWrite = { _, _ -> true })))
    }

    @Test
    fun `another app that may read the URI itself - shown, read-only - by each of the three ways`() {
        assertEquals("its own provider", readOnly, open(othersFile, other))
        assertEquals("a read grant it holds", readOnly, open(callersFile, other, FakeUriAccess(readGrants = setOf(callersFile to other))))
        assertEquals("the provider says it may (API 35+)", readOnly, open(mediaRow, other, FakeUriAccess(providerRead = { uri, uid -> uri == mediaRow && uid == other })))
    }

    @Test
    fun `another app's own provider is not a second app's - refused`() {
        assertEquals(refused, open(callersFile, other))
    }

    @Test
    fun `an unknown launching uid is another app - never the shell - and may read nothing by itself`() {
        // Every table says yes for uid -1 and for the shell's uid as an owner: an unnamed app still gets nothing from them.
        val access = FakeUriAccess(owners = mapOf("media" to -1, "com.other.files" to shell), readGrants = setOf(mediaRow to -1), providerRead = { _, _ -> true })
        assertEquals(refusedUnnamed, open(mediaRow, null, access))
        assertEquals(refusedUnnamed, open(othersFile, null, access))
        assertFalse(open(mediaRow, null, access).mayChange)
    }

    @Test
    fun `API 35 and later - the platform says the launcher had access at launch - shown read-only, for a named and an unnamed app`() {
        assertEquals(readOnlyUnnamed, open(mediaRow, null, launch = setOf(mediaRow)))
        assertEquals(readOnly, open(mediaRow, other, launch = setOf(mediaRow)))
        // Access to another URI is not access to this one.
        assertEquals(refusedUnnamed, open(othersFile, null, launch = setOf(mediaRow)))
    }

    @Test
    fun `API 34 - nothing can be asked for an unnamed app, so its content URI is refused - and the shell's own grants do not count`() {
        // The shell holding a read grant (or being able to read everything) says nothing about the launcher.
        val shellCan = FakeUriAccess(readGrants = setOf(othersFile to shell, mediaRow to shell), owners = mapOf("com.other.files" to shell))
        assertEquals(refusedUnnamed, open(othersFile, null, shellCan))
        assertEquals(refusedUnnamed, open(mediaRow, null, shellCan))
    }

    @Test
    fun `another user's provider and an escaped authority are refused whatever the platform would say`() {
        for (uri in listOf("content://10@media/external/images/media/41", "content://10%40media/external/images/media/41", "content:///x", "content:x")) {
            val yes = FakeUriAccess(owners = mapOf("media" to other, "10@media" to other, "" to other), readGrants = setOf(uri to other, uri to shell), providerRead = { _, _ -> true })
            assertEquals(uri, refused, open(uri, other, yes, launch = setOf(uri)))
            assertEquals(uri, refusedUnnamed, open(uri, null, yes, launch = setOf(uri)))
            assertEquals("$uri: the platform was not asked", emptyList<String>(), yes.asked)
            assertEquals("$uri: nor about the launch", emptyList<String>(), asked)
        }
    }

    @Test
    fun `every combination - only the shell may change anything, and nothing is shown without the platform's word`() {
        val uris = listOf(mediaRow, othersFile, callersFile)
        for (uri in uris) for (from in listOf<Int?>(shell, other, FakeUriAccess.CALLER, null, 0, 10999)) {
            for (callerGrant in listOf(false, true)) for (shellGrant in listOf(false, true)) for (atLaunch in listOf(false, true)) for (provider in listOf<Boolean?>(null, false, true)) {
                val access = FakeUriAccess(
                    readGrants = buildSet { if (callerGrant && from != null) add(uri to from); if (shellGrant) add(uri to shell) },
                    providerRead = provider?.let { answer -> { _: String, _: Int -> answer } },
                )
                val d = open(uri, from, access, launch = if (atLaunch) setOf(uri) else emptySet())
                val case = "uri=$uri from=$from callerGrant=$callerGrant shellGrant=$shellGrant atLaunch=$atLaunch provider=$provider"
                val isShell = from == shell
                val owns = from != null && access.owners[ContentUriText.parse(uri).authority] == from
                // A grant the SHELL holds never counts; the launch answer counts with or without a known uid.
                val expectShow = isShell || atLaunch || (from != null && (owns || callerGrant || provider == true))
                assertEquals(case, expectShow, d.show)
                assertEquals(case, isShell, d.mayChange)
                assertEquals(case, if (expectShow) null else "refused view: no grant", d.line)
            }
        }
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

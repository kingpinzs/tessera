package app.tileshell.photos

import app.tileshell.media.ContentUriText
import app.tileshell.media.FakeUriAccess
import app.tileshell.media.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The owner's ruling D1 (a), 2026-10-31, at the exported viewer: a shared-library picture another app opens "with"
 * Photos, with the read grant flag, is shown READ-ONLY on the grant the platform gave the shell for that launch
 * (`media/UriAccessLaunchGrantTest` has the rule's every branch). Without that grant it is refused as before.
 */
class ViewerLaunchGrantTest {
    private val shell = FakeUriAccess.SHELL
    private val mediaRow = "content://media/external/images/media/41"
    private val read = FakeUriAccess.READ

    /** An unnamed app's VIEW of [uri] on an API [api] phone: the launch's [flags], and the grants the platform holds. */
    private fun launched(uri: String = mediaRow, api: Int = 36, flags: Int = read, grants: Set<Triple<String, Int, Int>> = setOf(Triple(uri, shell, read)), from: Int = -1) =
        FakeUriAccess(apiLevel = api, launchedFrom = Platform.Said(from), grants = grants, launchFlags = Platform.Said(flags), launchData = Platform.Said(uri))

    private fun open(access: FakeUriAccess, uri: String = mediaRow) = ViewerRules.open(ContentUriText.parse(uri), access)

    @Test
    fun `D1 an app holding the library picture opens it with the read grant flag - shown read-only, on API 34, 35 and 36, and the lines say why`() {
        for (api in listOf(34, 35, 36)) {
            val launch = if (api < 35) "launch answer read: not available" else "launch answer read: denied"
            val expected = ViewerRules.Open(show = true, mayChange = false, line = null, request = "viewer request from an unnamed app: shown read-only", launch = launch, grant = "launch grant read: held")
            assertEquals("API $api", expected, open(launched(api = api)))
            val state = ViewerRules.state({ mediaRow }, launched(api = api))
            assertEquals("API $api", ViewerRules.State(uri = mediaRow, refused = false, mayChange = false, lines = listOf("viewer request from an unnamed app: shown read-only", launch, "launch grant read: held")), state)
        }
        // A named starter (it shared its identity) whose own answers say no: the same, as "another app".
        assertEquals("viewer request from another app: shown read-only", open(launched(api = 34, from = FakeUriAccess.OTHER)).request)
    }

    @Test
    fun `D1 shown by a launch grant is never more than read-only - no Edit, Delete or Set as`() {
        val opened = open(launched())
        assertFalse(opened.mayChange)
        assertEquals(listOf(ViewerRules.Action.SHARE, ViewerRules.Action.FILE_INFORMATION), ViewerRules.actions(hasRow = true, editable = true, several = false, mayChange = opened.mayChange))
    }

    @Test
    fun `no read grant flag on the launch - refused exactly as before, with no grant line`() {
        val state = ViewerRules.state({ mediaRow }, launched(flags = 0))
        assertEquals(ViewerRules.State(uri = null, refused = true, mayChange = false, lines = listOf("viewer request from an unnamed app: refused", "launch answer read: denied", "refused view: no grant")), state)
    }

    @Test
    fun `the read grant flag but no grant the shell holds - refused, and the line says none was held`() {
        val state = ViewerRules.state({ mediaRow }, launched(grants = emptySet()))
        assertEquals(
            ViewerRules.State(uri = null, refused = true, mayChange = false, lines = listOf("viewer request from an unnamed app: refused", "launch answer read: denied", "launch grant read: none held", "refused view: no grant")),
            state,
        )
        // The starter's own grant, or another uid's, is not the shell's.
        assertFalse(open(launched(grants = setOf(Triple(mediaRow, FakeUriAccess.OTHER, read), Triple(mediaRow, -1, read)))).show)
    }

    @Test
    fun `a FileProvider item or a contact's photo with a grant on the shell - not a library item - refused on API 34 as before`() {
        for (uri in listOf("content://com.other.files/share/p.jpg", "content://com.android.contacts/contacts/7/display_photo")) {
            val opened = open(launched(uri = uri, api = 34), uri)
            assertFalse(uri, opened.show)
            assertEquals(uri, "launch grant read: not a library item", opened.grant)
        }
    }

    @Test
    fun `the shell's own launch is untouched - shown with every action, the launch intent not even read`() {
        val access = launched(from = shell)
        assertEquals(ViewerRules.Open(show = true, mayChange = true, line = null, request = "viewer request from the shell: shown"), open(access))
        assertEquals(emptyList<String>(), access.launchReads)
    }
}

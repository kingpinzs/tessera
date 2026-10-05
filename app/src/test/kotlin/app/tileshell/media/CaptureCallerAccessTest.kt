package app.tileshell.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trust review's A-M1 and A-M2: condition (d) — may the capture's caller write the output itself — branch by branch. */
class CaptureCallerAccessTest {
    private val own = "content://com.caller.files/cache/out.jpg"
    private val mediaRow = "content://media/external/images/media/41"
    private val contact = "content://com.android.contacts/contacts/7/display_photo"

    private fun may(access: FakeUriAccess, uri: String, caller: String? = "com.caller") =
        CaptureCallerAccess.callerMayWrite(access, ContentUriText.parse(uri), caller)

    @Test
    fun `the provider is the caller's own - yes, and nothing more is asked`() {
        val access = FakeUriAccess()
        assertTrue(may(access, own))
        assertEquals(listOf("uidOf com.caller", "owner com.caller.files"), access.asked)
    }

    @Test
    fun `another app's provider with no grant and no provider answer - no`() {
        assertFalse(may(FakeUriAccess(), "content://com.other.files/x.jpg"))
        assertFalse(may(FakeUriAccess(), contact))
    }

    @Test
    fun `the caller holds a write grant for exactly that URI - yes`() {
        val granted = FakeUriAccess(writeGrants = setOf(contact to FakeUriAccess.CALLER))
        assertTrue(may(granted, contact))
        // A grant for another URI, a grant another uid holds, and a READ grant are not this caller's write grant.
        assertFalse(may(FakeUriAccess(writeGrants = setOf("$contact/x" to FakeUriAccess.CALLER)), contact))
        assertFalse(may(FakeUriAccess(writeGrants = setOf(contact to FakeUriAccess.OTHER)), contact))
        assertFalse(may(FakeUriAccess(readGrants = setOf(contact to FakeUriAccess.CALLER)), contact))
    }

    @Test
    fun `API 35 and later - the provider, asked, says the caller may write its own MediaStore row - yes`() {
        val asked = mutableListOf<Pair<String, Int>>()
        val access = FakeUriAccess(providerWrite = { uri, uid -> asked += uri to uid; uri == mediaRow && uid == FakeUriAccess.CALLER })
        assertTrue(may(access, mediaRow))
        assertEquals("the provider is asked about the caller's uid and that very URI", listOf(mediaRow to FakeUriAccess.CALLER), asked)
        // The provider says no for a row that is not the caller's, and a "may READ" answer is not a write answer.
        assertFalse(may(access, "content://media/external/images/media/42"))
        assertFalse(may(FakeUriAccess(providerRead = { _, _ -> true }), mediaRow))
    }

    @Test
    fun `API 34 - the provider cannot be asked, so the caller's own MediaStore row is refused unless it holds a grant`() {
        val api34 = FakeUriAccess(providerWrite = null)
        assertFalse("fails closed", may(api34, mediaRow))
        assertTrue(may(FakeUriAccess(providerWrite = null, writeGrants = setOf(mediaRow to FakeUriAccess.CALLER)), mediaRow))
    }

    @Test
    fun `no caller and an unknown package - no, and the platform is asked nothing about the URI`() {
        val everythingYes = { FakeUriAccess(providerWrite = { _, _ -> true }, writeGrants = setOf(own to FakeUriAccess.CALLER)) }
        val none = everythingYes()
        assertFalse(may(none, own, caller = null))
        assertEquals(emptyList<String>(), none.asked)
        val unknown = everythingYes()
        assertFalse(may(unknown, own, caller = "com.not.installed"))
        assertEquals(listOf("uidOf com.not.installed"), unknown.asked)
    }

    @Test
    fun `another user's provider, an escaped or empty authority and any scheme but content - no, whatever the platform would say`() {
        val yes = { uri: String -> FakeUriAccess(
            owners = mapOf("media" to FakeUriAccess.CALLER, "10@media" to FakeUriAccess.CALLER, "10%40media" to FakeUriAccess.CALLER, "" to FakeUriAccess.CALLER, "h" to FakeUriAccess.CALLER),
            writeGrants = setOf(uri to FakeUriAccess.CALLER), providerWrite = { _, _ -> true },
        ) }
        for (uri in listOf(
            "content://10@media/external/images/media/41", "content://10%40media/external/images/media/41", "content://0@com.caller.files/x",
            "content://media%2Fx/y", "content:///x", "content:x", "content:/x", "file:///sdcard/x.jpg", "http://h/x", "CONTENT://media/x", "/sdcard/x.jpg", "",
        )) {
            val access = yes(uri)
            assertFalse(uri, may(access, uri))
            assertEquals("$uri: only the caller's uid was looked up", listOf("uidOf com.caller"), access.asked)
        }
    }

    @Test
    fun `a provider nobody declares - no`() {
        assertFalse(may(FakeUriAccess(), "content://com.nobody.declares/x"))
    }

    @Test
    fun `a negative uid may reach nothing`() {
        val access = FakeUriAccess(owners = mapOf("media" to -1), writeGrants = setOf(mediaRow to -1), readGrants = setOf(mediaRow to -1), providerWrite = { _, _ -> true }, providerRead = { _, _ -> true })
        assertFalse(UriAccessRules.mayWrite(access, ContentUriText.parse(mediaRow), -1))
        assertFalse(UriAccessRules.mayRead(access, ContentUriText.parse(mediaRow), -1))
        assertEquals(emptyList<String>(), access.asked)
    }
}

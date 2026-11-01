package app.tileshell.video

import app.tileshell.media.FakeUriAccess
import app.tileshell.media.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's ruling D1 (a), 2026-10-31, at the exported player: a shared-library video another app opens "with" the
 * player, with the read grant flag, plays on the grant the platform gave the shell for that launch
 * (`media/UriAccessLaunchGrantTest` has the rule's every branch). Without that grant it is refused as before.
 */
class PlayerLaunchGrantTest {
    private val roots = listOf("/data/user/0/app.tileshell")
    private val media = "content://media/external/video/media/42"
    private val read = FakeUriAccess.READ

    private fun launched(uri: String = media, api: Int = 36, flags: Int = read, grants: Set<Triple<String, Int, Int>> = setOf(Triple(uri, FakeUriAccess.SHELL, read))) =
        FakeUriAccess(apiLevel = api, grants = grants, launchFlags = Platform.Said(flags), launchData = Platform.Said(uri))

    private fun source(text: String, access: FakeUriAccess, own: Boolean = false): PlayerSource {
        val authority = text.substringAfter("://").substringBefore('/')
        val path = "/" + text.substringAfter("://").substringAfter('/')
        return PlayerRules.source(text, "content", authority, path, roots, own, access) { it }
    }

    @Test
    fun `D1 an app holding the library video opens it with the read grant flag - plays on API 34, 35 and 36, and the lines say why`() {
        for (api in listOf(34, 35, 36)) {
            val s = source(media, launched(api = api))
            assertEquals("API $api", PlayerRequest.Play("content", 42L, null, "42"), s.request)
            assertNull("API $api", s.failure)
            val launch = if (api < 35) "launch answer read: not available" else "launch answer read: denied"
            assertEquals("API $api", listOf(launch, "launch grant read: held", "source from another app: a grant from the launch"), s.lines)
        }
    }

    @Test
    fun `no read grant flag, or no grant the shell holds - refused as before`() {
        val noFlag = source(media, launched(flags = 0))
        assertEquals(PlayerRequest.Refused, noFlag.request)
        assertEquals(listOf("launch answer read: denied", "refused source: no grant"), noFlag.lines)
        val noGrant = source(media, launched(grants = emptySet()))
        assertEquals(PlayerRequest.Refused, noGrant.request)
        assertEquals(listOf("launch answer read: denied", "launch grant read: none held", "refused source: no grant"), noGrant.lines)
    }

    @Test
    fun `a library video that is not the launch's own data - an address the launch did not carry - refused`() {
        val other = "content://media/external/video/media/43"
        val s = source(other, launched(uri = media, grants = setOf(Triple(other, FakeUriAccess.SHELL, read))))
        assertEquals(PlayerRequest.Refused, s.request)
        assertTrue(s.lines.toString(), "launch grant read: not in the launch" in s.lines)
    }

    @Test
    fun `the shell's own launch is untouched - the launch intent not even read`() {
        val access = launched()
        assertTrue(source(media, access, own = true).request is PlayerRequest.Play)
        assertEquals(emptyList<String>(), access.launchReads)
        assertEquals(emptyList<String>(), access.asked)
    }
}

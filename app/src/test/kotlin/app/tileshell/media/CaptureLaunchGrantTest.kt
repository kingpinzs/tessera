package app.tileshell.media

import app.tileshell.media.CaptureOutputGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's ruling D1 (a), 2026-10-31, at the capture answer: a capture request (EXTRA_OUTPUT, the ClipData Android
 * builds for it and the write grant flag) that names a MediaStore row the asking app owns is accepted on API 35+ when
 * MediaStore says the recipient may write it (d1) and the platform gave the shell a write grant for that launch (d2).
 * The rule's every branch is `UriAccessLaunchGrantTest`'s; this is the request's way to it, and the guard-inputs line.
 */
class CaptureLaunchGrantTest {
    private val row = "content://media/external/images/media/41"
    private val writeRead = FakeUriAccess.WRITE or FakeUriAccess.READ

    private class Request(val output: String, val clip: List<String> = listOf(output), val flags: Int = 0x3, val caller: String? = "com.caller") : CaptureRequestPort {
        override fun hasOutput(): Boolean = true
        override fun outputText(): String? = output
        override fun callingPackage(): String? = caller
        override fun clipUris(): List<String> = clip
        override fun flags(): Int = flags
        override fun ownAuthorities(): Set<String> = setOf("app.tileshell.files")
    }

    /** The phone: MediaStore says the recipient may write [row] (its own row), the launch answer is denied (the WM lock). */
    private fun platform(api: Int = 36, from: Int = -1, shellGrant: Int = FakeUriAccess.WRITE) = FakeUriAccess(
        apiLevel = api, launchedFrom = Platform.Said(from),
        providerAnswer = { u, uid, m -> if (u == row && uid == FakeUriAccess.CALLER && m == FakeUriAccess.WRITE) FakeUriAccess.YES else FakeUriAccess.NO },
        grants = if (shellGrant == 0) emptySet() else setOf(Triple(row, FakeUriAccess.SHELL, shellGrant)),
        launchFlags = Platform.Said(writeRead), launchClip = Platform.Said(listOf(row)),
    )

    private val head = "capture guard inputs: scheme=content authority=media startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false"

    @Test
    fun `D1 the caller's own MediaStore row, the launch's write grant held - accepted on API 35 and 36, the very text written, and the line says why`() {
        for (api in listOf(35, 36)) {
            val outcome = CaptureRequestRule.decide(Request(row), platform(api = api))
            assertTrue("API $api", outcome.decision is Decision.Accepted)
            assertEquals("API $api", row, (outcome.decision as Decision.Accepted).uri)
            assertEquals("API $api", listOf("$head callerMayWrite=true recipientMayWrite=true starterAtLaunch=denied launchGrant=held"), outcome.before)
        }
    }

    @Test
    fun `API 34 - the recipient cannot be asked about a MediaStore row - refused as before, the line unchanged`() {
        val outcome = CaptureRequestRule.decide(Request(row), platform(api = 34))
        assertEquals(Decision.Refused("refused output: no grant"), outcome.decision)
        assertEquals(listOf("$head callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked"), outcome.before)
    }

    @Test
    fun `no write grant on the shell - refused, and the line says none was held`() {
        val outcome = CaptureRequestRule.decide(Request(row), platform(shellGrant = FakeUriAccess.READ))
        assertEquals(Decision.Refused("refused output: no grant"), outcome.decision)
        assertEquals(listOf("$head callerMayWrite=false recipientMayWrite=true starterAtLaunch=denied launchGrant=none held"), outcome.before)
    }

    @Test
    fun `A2-F1 a forward the platform names - refused with the forwarded line, the launch grant never read`() {
        val access = platform(from = FakeUriAccess.OTHER)
        val outcome = CaptureRequestRule.decide(Request(row), access)
        assertEquals(Decision.Refused("refused output: no grant"), outcome.decision)
        assertEquals(listOf("capture request forwarded: started by com.other, result to com.caller"), outcome.after)
        assertEquals(emptyList<String>(), access.launchReads)
    }

    @Test
    fun `a request that fails (a) to (c) - no write flag, the output not in the ClipData - is refused and the launch grant never read`() {
        for (request in listOf(Request(row, flags = FakeUriAccess.READ), Request(row, clip = emptyList()), Request(row, caller = null))) {
            val access = platform()
            assertEquals(Decision.Refused("refused output: no grant"), CaptureRequestRule.decide(request, access).decision)
            assertEquals(emptyList<String>(), access.launchReads)
        }
    }
}

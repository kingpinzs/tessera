package app.tileshell.media

import app.tileshell.media.CaptureOutputGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trust review's A-M2, A-L1 and A-S: from the capture request, as the platform hands it over, to the guard's decision. */
class CaptureRequestRuleTest {
    private val out = "content://com.caller.files/cache/out.jpg"
    private val noGrant = Decision.Refused("refused output: no grant")

    /** A request whose every read can be set, or made to throw. */
    private class Request(
        var hasOutput: () -> Boolean = { true },
        var output: () -> String? = { "content://com.caller.files/cache/out.jpg" },
        var caller: () -> String? = { "com.caller" },
        var clip: () -> List<String> = { listOf("content://com.caller.files/cache/out.jpg") },
        var flags: () -> Int = { CaptureOutputGuard.FLAG_GRANT_WRITE },
        var own: () -> Set<String> = { setOf("app.tileshell.livetile", "app.tileshell.files") },
    ) : CaptureRequestPort {
        val reads = mutableListOf<String>()
        override fun hasOutput(): Boolean { reads += "hasOutput"; return hasOutput.invoke() }
        override fun outputText(): String? { reads += "outputText"; return output.invoke() }
        override fun callingPackage(): String? = caller.invoke()
        override fun clipUris(): List<String> = clip.invoke()
        override fun flags(): Int = flags.invoke()
        override fun ownAuthorities(): Set<String> = own.invoke()
    }

    private fun decide(request: Request, access: FakeUriAccess = FakeUriAccess()) = CaptureRequestRule.decide(request, access)

    @Test
    fun `EXTRA_OUTPUT a Uri the caller may write - accepted, and the URI written is that one text`() {
        val request = Request()
        val outcome = decide(request)
        assertTrue(outcome.decision is Decision.Accepted)
        assertEquals(out, (outcome.decision as Decision.Accepted).uri)
        assertEquals("EXTRA_OUTPUT is turned to its string once", 1, request.reads.count { it == "outputText" })
        assertEquals(
            listOf("capture guard inputs: scheme=content authority=com.caller.files startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=true"),
            outcome.before,
        )
    }

    @Test
    fun `EXTRA_OUTPUT absent - the no-output contract, and the extra is never read`() {
        val request = Request(hasOutput = { false })
        val access = FakeUriAccess()
        val outcome = decide(request, access)
        assertEquals(Decision.NoOutput, outcome.decision)
        assertEquals(listOf("hasOutput"), request.reads)
        assertEquals(emptyList<String>(), access.asked)
        assertEquals(emptyList<String>(), outcome.before)
    }

    @Test
    fun `EXTRA_OUTPUT present but not a Uri - refused, never the no-output contract`() {
        val outcome = decide(Request(output = { null }))
        assertEquals(noGrant, outcome.decision)
        assertEquals(listOf("capture request: EXTRA_OUTPUT is not a Uri"), outcome.before)
    }

    @Test
    fun `a request that cannot be read is refused, whichever read throws`() {
        class BadParcelableException : RuntimeException("ClassNotFoundException when unmarshalling: com.caller.Evil")
        val throwing: () -> Nothing = { throw BadParcelableException() }
        for ((name, request) in listOf(
            "hasExtra" to Request(hasOutput = throwing),
            "the extra" to Request(output = throwing),
            "the caller" to Request(caller = throwing),
            "the ClipData" to Request(clip = throwing),
            "the flags" to Request(flags = throwing),
            "the shell's providers" to Request(own = throwing),
        )) {
            val outcome = decide(request)
            assertEquals(name, noGrant, outcome.decision)
            // The line names the exception's class and nothing the caller wrote.
            assertEquals(name, listOf("capture request unreadable (BadParcelableException)"), outcome.before)
        }
    }

    @Test
    fun `an unknown calling package and no caller are refused`() {
        assertEquals(noGrant, decide(Request(caller = { "com.not.installed" })).decision)
        assertEquals(noGrant, decide(Request(caller = { null })).decision)
    }

    @Test
    fun `condition (d) is the platform's answer for the caller - a provider the caller does not own and holds no grant for is refused`() {
        val contact = "content://com.android.contacts/contacts/7/display_photo"
        val request = Request(output = { contact }, clip = { listOf(contact) })
        val outcome = decide(request)
        assertEquals(noGrant, outcome.decision)
        assertTrue(outcome.before.single(), outcome.before.single().endsWith("ownAuthority=false callerMayWrite=false"))
        // The same request from a caller that holds a write grant for it is accepted.
        val granted = decide(request, FakeUriAccess(writeGrants = setOf(contact to FakeUriAccess.CALLER)))
        assertEquals(contact, (granted.decision as Decision.Accepted).uri)
    }

    @Test
    fun `A-S a ClipData of several URIs - the accepted URI is EXTRA_OUTPUT, not the clip's first`() {
        val other = "content://com.caller.files/cache/other.jpg"
        val outcome = decide(Request(clip = { listOf(other, out) }))
        assertEquals(out, (outcome.decision as Decision.Accepted).uri)
    }

    @Test
    fun `the guard's other refusals reach the outcome unchanged`() {
        assertEquals(Decision.Refused("refused output scheme=file"), decide(Request(output = { "file:///sdcard/DCIM/x.jpg" })).decision)
        assertEquals(noGrant, decide(Request(flags = { 1 })).decision)
        assertEquals(noGrant, decide(Request(clip = { emptyList() })).decision)
        val own = "content://app.tileshell.files/x"
        val shellOwns = FakeUriAccess(owners = mapOf("app.tileshell.files" to FakeUriAccess.CALLER))
        assertEquals(noGrant, decide(Request(output = { own }, clip = { listOf(own) }), shellOwns).decision)
        for (uri in listOf("content://10@com.caller.files/x", "content://10%40com.caller.files/x")) {
            assertEquals(uri, noGrant, decide(Request(output = { uri }, clip = { listOf(uri) })).decision)
        }
    }
}

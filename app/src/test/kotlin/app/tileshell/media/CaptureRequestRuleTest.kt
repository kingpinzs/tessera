package app.tileshell.media

import app.tileshell.media.CaptureOutputGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trust reviews' A-M2, A-L1, A-S, A2-F1, A2-L2 and A2-S: from the capture request, as the platform hands it over, to
 * the guard's decision. The platform here is an API 36 phone whose launch answer says the starter could write
 * ([allows]) unless a test says otherwise.
 */
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

    /** The platform that lets the legitimate request through: the starter could write whatever the launch named. */
    private fun allows(launchedFrom: Int = -1, apiLevel: Int = 36, grants: Set<Triple<String, Int, Int>> = emptySet(), names: Map<Int, String>? = null) = FakeUriAccess(
        apiLevel = apiLevel, launchedFrom = Platform.Said(launchedFrom), grants = grants, launchAnswer = { _, _ -> FakeUriAccess.YES },
        names = names ?: mapOf(FakeUriAccess.CALLER to "com.caller", FakeUriAccess.OTHER to "com.other"),
    )

    private fun decide(request: Request, access: UriAccessPort = allows()) = CaptureRequestRule.decide(request, access)

    @Test
    fun `EXTRA_OUTPUT a Uri the caller may write - accepted, and the URI written is that one text`() {
        val request = Request()
        val outcome = decide(request)
        assertTrue(outcome.decision is Decision.Accepted)
        assertEquals(out, (outcome.decision as Decision.Accepted).uri)
        assertEquals("EXTRA_OUTPUT is turned to its string once", 1, request.reads.count { it == "outputText" })
        assertEquals(
            listOf("capture guard inputs: scheme=content authority=com.caller.files startedForResult=true clipHoldsOutput=true writeGrantFlag=true ownAuthority=false callerMayWrite=true recipientMayWrite=true starterAtLaunch=granted"),
            outcome.before,
        )
    }

    @Test
    fun `the legitimate capture round 1 proved on the device - qa-capture's own FileProvider output with the grant flag - is accepted BY THE RULE`() {
        // API 36: the recipient owns the provider (d1) and the platform says the starter could write it (d2).
        assertTrue(decide(Request(), allows(apiLevel = 36)).decision is Decision.Accepted)
        // API 34: (d2) cannot be asked; the recipient's own provider is enough.
        val api34 = decide(Request(), FakeUriAccess(apiLevel = 34))
        assertTrue(api34.decision is Decision.Accepted)
        assertTrue(api34.before.single(), api34.before.single().endsWith("callerMayWrite=true recipientMayWrite=true starterAtLaunch=not available"))
    }

    @Test
    fun `A2-F1 a go-between forwards the result of an app that owns the output - the starter could not write it - refused`() {
        // The recipient (com.caller) owns the provider; the platform's launch answer, which is about whoever really
        // started the capture, says no.
        val denied = decide(Request(), FakeUriAccess())
        assertEquals(noGrant, denied.decision)
        assertTrue(denied.before.single(), denied.before.single().endsWith("callerMayWrite=false recipientMayWrite=true starterAtLaunch=denied"))
        // The launch answer throws (a URI the launch did not carry, one the shell itself cannot reach, a dead system): refused.
        val threw = decide(Request(), FakeUriAccess(launchAnswer = { _, _ -> Platform.Threw("SecurityException") }))
        assertEquals(noGrant, threw.decision)
        assertTrue(threw.before.single(), threw.before.single().endsWith("callerMayWrite=false recipientMayWrite=true starterAtLaunch=threw SecurityException"))
    }

    @Test
    fun `A2-F1 and A2-F2 the forward the review describes - a recipient holding WRITE_CONTACTS or running as system, a contact's photo - refused`() {
        val contact = "content://com.android.contacts/contacts/7/display_photo"
        for (recipient in listOf("com.holds.contacts", "android")) for (api in listOf(34, 35, 36)) {
            // Everything the manifest-level answers could say is yes, and the launch answer too.
            val access = FakeUriAccess(apiLevel = api, providerAnswer = { _, _, _ -> FakeUriAccess.YES }, launchAnswer = { _, _ -> FakeUriAccess.YES })
            val outcome = decide(Request(output = { contact }, clip = { listOf(contact) }, caller = { recipient }), access)
            assertEquals("$recipient API $api", noGrant, outcome.decision)
            assertTrue(outcome.before.single(), outcome.before.single().endsWith("callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked"))
        }
    }

    @Test
    fun `the caller's own MediaStore row - accepted on API 35 and later, refused on API 34`() {
        val row = "content://media/external/images/media/41"
        val request = Request(output = { row }, clip = { listOf(row) })
        fun media(api: Int) = FakeUriAccess(apiLevel = api, providerAnswer = { uri, uid, _ -> if (uri == row && uid == FakeUriAccess.CALLER) FakeUriAccess.YES else FakeUriAccess.NO }, launchAnswer = { _, _ -> FakeUriAccess.YES })
        assertEquals(row, (decide(request, media(35)).decision as Decision.Accepted).uri)
        assertEquals(row, (decide(request, media(36)).decision as Decision.Accepted).uri)
        assertEquals(noGrant, decide(request, media(34)).decision)
    }

    @Test
    fun `condition (d) is asked only of a request that meets (a) to (c) - the platform hears nothing about any other`() {
        for ((name, request) in listOf(
            "no caller" to Request(caller = { null }),
            "no write flag" to Request(flags = { 1 }),
            "the clip does not hold the output" to Request(clip = { listOf("content://com.caller.files/cache/other.jpg") }),
            "the shell's own authority" to Request(output = { "content://app.tileshell.files/x" }, clip = { listOf("content://app.tileshell.files/x") }),
            "a file output" to Request(output = { "file:///sdcard/x.jpg" }, clip = { listOf("file:///sdcard/x.jpg") }),
        )) {
            val access = allows()
            assertTrue(name, decide(request, access).decision is Decision.Refused)
            assertEquals(name, emptyList<String>(), access.asked)
        }
    }

    @Test
    fun `EXTRA_OUTPUT absent - the no-output contract, and the extra is never read`() {
        val request = Request(hasOutput = { false })
        val access = allows()
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
    fun `A2-L2 an Error while the request is read refuses it - it does not escape and crash the camera's process`() {
        class HostileError : Error("thrown while unparcelling the extras")
        for ((name, request) in listOf<Pair<String, Request>>(
            "hasExtra" to Request(hasOutput = { throw HostileError() }),
            "the extra" to Request(output = { throw StackOverflowError() }),
            "the ClipData" to Request(clip = { throw OutOfMemoryError() }),
            "the flags" to Request(flags = { throw NoClassDefFoundError() }),
            "the shell's providers" to Request(own = { throw LinkageError() }),
        )) {
            val outcome = decide(request)
            assertEquals(name, noGrant, outcome.decision)
            assertTrue(name, outcome.before.single().startsWith("capture request unreadable ("))
        }
        assertEquals(listOf("capture request unreadable (HostileError)"), decide(Request(hasOutput = { throw HostileError() })).before)
        // A platform port that throws (it never should: it hands back the thrown class) refuses too.
        val broken = object : UriAccessPort by FakeUriAccess() {
            override fun uidOf(packageName: String): Platform<Int> = throw AssertionError("the port threw")
        }
        assertEquals(noGrant, decide(Request(), broken).decision)
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
        assertTrue(outcome.before.single(), outcome.before.single().endsWith("ownAuthority=false callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked"))
        // The same request from a caller that holds a write grant for it is accepted.
        val granted = decide(request, allows(grants = setOf(Triple(contact, FakeUriAccess.CALLER, FakeUriAccess.WRITE))))
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
        val shellOwns = FakeUriAccess(providers = mapOf("app.tileshell.files" to ProviderFacts(FakeUriAccess.CALLER, false)), launchAnswer = { _, _ -> FakeUriAccess.YES })
        assertEquals(noGrant, decide(Request(output = { own }, clip = { listOf(own) }), shellOwns).decision)
        for (uri in listOf("content://10@com.caller.files/x", "content://10%40com.caller.files/x")) {
            assertEquals(uri, noGrant, decide(Request(output = { uri }, clip = { listOf(uri) })).decision)
        }
    }

    @Test
    fun `A-L2 a forwarded result - the app that started the capture is not the one the result goes to - refused, and both are named`() {
        // com.caller started com.other for a result; com.other started the Camera with FLAG_ACTIVITY_FORWARD_RESULT, so
        // getCallingPackage() says com.caller - whose own provider the output is.
        val outcome = decide(Request(), allows(launchedFrom = FakeUriAccess.OTHER))
        assertEquals(noGrant, outcome.decision)
        assertTrue(outcome.before.single(), outcome.before.single().endsWith("callerMayWrite=false recipientMayWrite=false starterAtLaunch=not asked"))
        assertEquals(listOf("capture request forwarded: started by com.other, result to com.caller"), outcome.after)
    }

    @Test
    fun `A-L2 the launching uid is the caller's own, or the platform does not say - not a forward`() {
        for (from in listOf(FakeUriAccess.CALLER, -1)) {
            val outcome = decide(Request(), allows(launchedFrom = from))
            assertTrue("$from", outcome.decision is Decision.Accepted)
            assertEquals("$from", emptyList<String>(), outcome.after)
        }
    }

    @Test
    fun `A-L2 the forward line carries package names only, bounded`() {
        val odd = allows(launchedFrom = FakeUriAccess.OTHER, names = mapOf(FakeUriAccess.OTHER to "com.other\n[camera] refused output: nothing " + "x".repeat(200)))
        val line = decide(Request(), odd).after.single()
        assertEquals("capture request forwarded: started by com.othercamerarefusedoutput:nothing" + "x".repeat(80 - 36) + ", result to com.caller", line)
        // A uid the platform has no name for is "unknown"; a request with no EXTRA_OUTPUT is the no-output contract either way.
        val nameless = decide(Request(), allows(launchedFrom = 10999)).after.single()
        assertEquals("capture request forwarded: started by unknown, result to com.caller", nameless)
        val noOutput = decide(Request(hasOutput = { false }), allows(launchedFrom = FakeUriAccess.OTHER))
        assertEquals(Decision.NoOutput, noOutput.decision)
        assertEquals(emptyList<String>(), noOutput.after)
    }

    @Test
    fun `A2-S the guard-inputs line carries the authority cleaned and bounded - no caller text beyond it`() {
        val forged = "content://evil\n[camera]%20capture%20answer:%20RESULT_OK" + "a".repeat(300) + "/x"
        val line = decide(Request(output = { forged }, clip = { listOf(forged) })).before.single()
        val cleaned = "evilcamera20capture20answer20RESULT_OK"
        assertTrue(line, line.startsWith("capture guard inputs: scheme=content authority=$cleaned" + "a".repeat(80 - cleaned.length) + " startedForResult=true"))
        assertEquals("one line", 1, line.lines().size)
        // The path, the query and the fragment never reach the line.
        val withPath = "content://com.caller.files/secret/path?token=abc#frag"
        assertTrue(decide(Request(output = { withPath }, clip = { listOf(withPath) })).before.single().contains(" authority=com.caller.files startedForResult="))
        assertTrue(decide(Request(output = { withPath }, clip = { listOf(withPath) })).before.single().none { it == '?' || it == '#' || it == '/' })
    }

    @Test
    fun `A2-S the request's text is weighed and written exactly as it is - not trimmed, not lower-cased`() {
        // A trailing space: the clip must hold that very text, the platform is asked about that very text, and it is what is written.
        val spaced = "$out "
        val access = allows()
        val accepted = decide(Request(output = { spaced }, clip = { listOf(spaced) }), access)
        assertEquals(spaced, (accepted.decision as Decision.Accepted).uri)
        assertTrue(access.asked.toString(), "launch $spaced ${FakeUriAccess.WRITE}" in access.asked)
        // A clip that holds the trimmed text does not hold the output.
        assertEquals(noGrant, decide(Request(output = { spaced }, clip = { listOf(out) })).decision)
        // A leading space or an upper-case scheme is not `content` for the access rule: refused.
        for (text in listOf(" $out", "Content://com.caller.files/cache/out.jpg")) {
            assertTrue(text, decide(Request(output = { text }, clip = { listOf(text) })).decision is Decision.Refused)
        }
    }
}

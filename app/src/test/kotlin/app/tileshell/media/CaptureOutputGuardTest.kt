package app.tileshell.media

import app.tileshell.media.CaptureOutputGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 17, r3 D1 (Q-17-2 (b)) with condition (d) added at the build: the capture answer's output guard. */
class CaptureOutputGuardTest {
    private val own = setOf("app.tileshell.livetile", "app.tileshell.files")
    private val out = "content://com.caller.files/cache/out.jpg"
    private val write = CaptureOutputGuard.FLAG_GRANT_WRITE
    private val read = 0x1

    /** The guard reads the scheme and the authority from the output's own text: no test hands it a second opinion. */
    private fun decide(
        output: String? = out,
        caller: String? = "com.caller",
        clip: List<String> = listOf(out),
        flags: Int = write,
        callerMayWrite: Boolean = true,
    ) = CaptureOutputGuard.decide(output?.let(ContentUriText::parse), caller, clip, flags, own, callerMayWrite)

    private val noGrant = Decision.Refused("refused output: no grant")

    @Test
    fun `(d) the caller itself may not write the URI - refused, even with the flag and its own ClipData`() {
        // The case that broke r3 D1's premise: the shell holds WRITE_CONTACTS, so Android checks nothing at the start.
        val contact = "content://com.android.contacts/contacts/7/display_photo"
        assertEquals(noGrant, decide(output = contact, clip = listOf(contact), flags = write, callerMayWrite = false))
        assertEquals(noGrant, decide(callerMayWrite = false))
        // (d) alone is not enough either: every other condition still has to hold.
        assertEquals(noGrant, decide(caller = null, callerMayWrite = true))
        assertEquals(noGrant, decide(flags = 0, callerMayWrite = true))
        assertEquals(noGrant, decide(clip = emptyList(), callerMayWrite = true))
    }

    @Test
    fun `another user's provider is refused`() {
        for (authority in listOf("10@media", "0@com.caller.files", "10@com.android.contacts")) {
            val uri = "content://$authority/x"
            assertEquals(authority, noGrant, decide(output = uri, clip = listOf(uri)))
        }
    }

    @Test
    fun `every condition holds - accepted, and only that URI`() {
        val d = decide()
        assertTrue(d is Decision.Accepted)
        assertEquals(out, (d as Decision.Accepted).uri)
        assertTrue(decide(flags = write or read or 0x10000000) is Decision.Accepted)
    }

    @Test
    fun `no EXTRA_OUTPUT is the no-output contract, whatever else the intent holds`() {
        assertEquals(Decision.NoOutput, decide(output = null, caller = null, clip = emptyList(), flags = 0))
        assertEquals(Decision.NoOutput, decide(output = null))
    }

    @Test
    fun `(a) not started for a result - refused`() {
        assertEquals(noGrant, decide(caller = null))
    }

    @Test
    fun `(b) no write-grant flag - refused, even with the URI in the ClipData`() {
        assertEquals(noGrant, decide(flags = 0))
        assertEquals(noGrant, decide(flags = read))
        assertEquals(noGrant, decide(flags = 0x40 or 0x80 or read))
    }

    @Test
    fun `(b) the ClipData does not hold that same URI - refused`() {
        assertEquals(noGrant, decide(clip = emptyList()))
        assertEquals(noGrant, decide(clip = listOf("content://com.caller.files/cache/other.jpg")))
        // The display_photo negative (E9): the caller's ClipData names its own file, the output a contact's photo.
        val contact = "content://com.android.contacts/contacts/7/display_photo"
        assertEquals(noGrant, decide(output = contact, clip = listOf(out)))
        // The same aim with the caller's own ClipData and no flag: Android checked nothing, so neither is it accepted.
        assertEquals(noGrant, decide(output = contact, clip = listOf(contact), flags = 0))
        // A URI that only looks alike is another URI.
        assertEquals(noGrant, decide(clip = listOf("$out/")))
        assertEquals(noGrant, decide(clip = listOf(out.uppercase())))
        assertEquals(noGrant, decide(clip = listOf("$out?x=1")))
    }

    @Test
    fun `(c) one of the shell's own authorities - refused, however it is spelled`() {
        for (authority in listOf("app.tileshell.files", "APP.TILESHELL.FILES", "0@app.tileshell.files", "10@app.tileshell.livetile")) {
            val uri = "content://$authority/x"
            assertEquals(authority, noGrant, decide(output = uri, clip = listOf(uri)))
        }
        assertEquals(noGrant, decide(output = "content:///x", clip = listOf("content:///x")))
        assertEquals(noGrant, decide(output = "content:x", clip = listOf("content:x")))
    }

    @Test
    fun `a file output is refused by its scheme line, and so is every other scheme`() {
        assertEquals(Decision.Refused("refused output scheme=file"), decide(output = "file:///sdcard/DCIM/x.jpg", clip = listOf("file:///sdcard/DCIM/x.jpg")))
        assertEquals(Decision.Refused("refused output scheme=file"), decide(output = "FILE:///x"))
        assertEquals(Decision.Refused("refused output scheme=http"), decide(output = "http://h/x"))
        assertEquals(Decision.Refused("refused output scheme=none"), decide(output = "/sdcard/x.jpg"))
        assertEquals(Decision.Refused("refused output scheme=android.resource"), decide(output = "android.resource://p/1"))
        // The scheme is the caller's text: the line carries letters, digits and + - . only, and at most 16 of them.
        assertEquals(Decision.Refused("refused output scheme=abcdefghijklmnop"), decide(output = "ab cd\nefghijklmnopqrstuvwxyz:x"))
    }

    @Test
    fun `A-S the accepted URI is EXTRA_OUTPUT, wherever it stands in a ClipData of several URIs`() {
        val other = "content://com.caller.files/cache/other.jpg"
        for (clip in listOf(listOf(other, out), listOf(out, other), listOf(other, out, other))) {
            val d = decide(clip = clip)
            assertTrue("$clip", d is Decision.Accepted)
            assertEquals("$clip", out, (d as Decision.Accepted).uri)
        }
        // And a ClipData of several URIs that does not hold the output is still refused.
        assertEquals(noGrant, decide(clip = listOf(other, "$out.bak")))
    }

    @Test
    fun `A-L1 an authority that hides a user id behind a percent-escape is refused`() {
        for (uri in listOf("content://10%40media/external/images/media/1", "content://10%40com.caller.files/x", "content://com.caller.files%2Fx/y", "content://%61pp.tileshell.files/x")) {
            assertEquals(uri, noGrant, decide(output = uri, clip = listOf(uri)))
        }
    }

    @Test
    fun `A-L1 the scheme and the authority are the ones the output's text has`() {
        fun parts(text: String) = ContentUriText.parse(text).let { it.scheme to it.authority }
        assertEquals("content" to "com.caller.files", parts(out))
        assertEquals("content" to "media", parts("content://media/external/images/media/7?x=1#f"))
        assertEquals("content" to "a", parts("content://a\\b/x"))
        assertEquals("content" to "10@media", parts("content://10@media/x"))
        assertEquals("content" to "", parts("content:///x"))
        assertEquals("content" to null, parts("content:x"))
        assertEquals("content" to null, parts("content:/x"))
        assertEquals(null to null, parts("/sdcard/x.jpg"))
        assertEquals(null to "h", parts("//h/x"))
        assertEquals("a/b" to null, parts("a/b:c"))
        assertEquals("" to "h", parts("://h"))
        assertEquals(null, ContentUriText.parse("content:///x").plainAuthority)
        assertEquals(null, ContentUriText.parse("content://10@media/x").plainAuthority)
        assertEquals("media", ContentUriText.parse("content://media/x").plainAuthority)
    }

    @Test
    fun `conditions (a) to (c) alone accept exactly what the guard accepts with (d) met - and make no decision of their own`() {
        val contact = "content://com.android.contacts/contacts/7/display_photo"
        val cases = listOf<List<Any?>>(
            listOf(out, "com.caller", listOf(out), write), listOf(contact, "com.caller", listOf(contact), write), listOf(out, null, listOf(out), write),
            listOf(out, "com.caller", listOf(out), read), listOf(out, "com.caller", emptyList<String>(), write), listOf("content://app.tileshell.files/x", "com.caller", listOf("content://app.tileshell.files/x"), write),
            listOf("content://10@media/x", "com.caller", listOf("content://10@media/x"), write), listOf("file:///sdcard/x.jpg", "com.caller", listOf("file:///sdcard/x.jpg"), write),
            listOf("content:///x", "com.caller", listOf("content:///x"), write),
        )
        for ((output, caller, clip, flags) in cases) {
            @Suppress("UNCHECKED_CAST")
            val meets = CaptureOutputGuard.meetsAToC(ContentUriText.parse(output as String), caller as String?, clip as List<String>, flags as Int, own)
            val withD = CaptureOutputGuard.decide(ContentUriText.parse(output), caller, clip, flags, own, callerMayWrite = true)
            assertEquals("$output $caller $clip $flags", meets, withD is Decision.Accepted)
            // Without (d) nothing is accepted, whatever (a) to (c) say.
            assertTrue(CaptureOutputGuard.decide(ContentUriText.parse(output), caller, clip, flags, own, callerMayWrite = false) !is Decision.Accepted)
        }
        assertTrue(CaptureOutputGuard.meetsAToC(ContentUriText.parse(out), "com.caller", listOf(out), write, own))
    }
}

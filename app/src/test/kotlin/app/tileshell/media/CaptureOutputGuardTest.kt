package app.tileshell.media

import app.tileshell.media.CaptureOutputGuard.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 17, r3 D1 (Q-17-2 (b)): the capture answer's three-condition output guard. */
class CaptureOutputGuardTest {
    private val own = setOf("app.tileshell.livetile", "app.tileshell.files")
    private val out = "content://com.caller.files/cache/out.jpg"
    private val write = CaptureOutputGuard.FLAG_GRANT_WRITE
    private val read = 0x1

    private fun decide(
        output: String? = out,
        scheme: String? = "content",
        authority: String? = "com.caller.files",
        caller: String? = "com.caller",
        clip: List<String> = listOf(out),
        flags: Int = write,
    ) = CaptureOutputGuard.decide(output, scheme, authority, caller, clip, flags, own)

    private val noGrant = Decision.Refused("refused output: no grant")

    @Test
    fun `all three conditions - accepted, and only that URI`() {
        val d = decide()
        assertTrue(d is Decision.Accepted)
        assertEquals(out, (d as Decision.Accepted).uri)
        assertTrue(decide(flags = write or read or 0x10000000) is Decision.Accepted)
    }

    @Test
    fun `no EXTRA_OUTPUT is the no-output contract, whatever else the intent holds`() {
        assertEquals(Decision.NoOutput, decide(output = null, scheme = null, authority = null, caller = null, clip = emptyList(), flags = 0))
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
        assertEquals(noGrant, decide(output = contact, authority = "com.android.contacts", clip = listOf(out)))
        // The same aim with the caller's own ClipData and no flag: Android checked nothing, so neither is it accepted.
        assertEquals(noGrant, decide(output = contact, authority = "com.android.contacts", clip = listOf(contact), flags = 0))
        // A URI that only looks alike is another URI.
        assertEquals(noGrant, decide(clip = listOf("$out/")))
        assertEquals(noGrant, decide(clip = listOf(out.uppercase())))
        assertEquals(noGrant, decide(clip = listOf("$out?x=1")))
    }

    @Test
    fun `(c) one of the shell's own authorities - refused, however it is spelled`() {
        for (authority in listOf("app.tileshell.files", "APP.TILESHELL.FILES", "0@app.tileshell.files", "10@app.tileshell.livetile")) {
            val uri = "content://$authority/x"
            assertEquals(authority, noGrant, decide(output = uri, authority = authority, clip = listOf(uri)))
        }
        assertEquals(noGrant, decide(output = "content:///x", authority = "", clip = listOf("content:///x")))
        assertEquals(noGrant, decide(output = "content:x", authority = null, clip = listOf("content:x")))
    }

    @Test
    fun `a file output is refused by its scheme line, and so is every other scheme`() {
        assertEquals(Decision.Refused("refused output scheme=file"), decide(output = "file:///sdcard/DCIM/x.jpg", scheme = "file", authority = "", clip = listOf("file:///sdcard/DCIM/x.jpg")))
        assertEquals(Decision.Refused("refused output scheme=file"), decide(output = "FILE:///x", scheme = "FILE", authority = ""))
        assertEquals(Decision.Refused("refused output scheme=http"), decide(output = "http://h/x", scheme = "http", authority = "h"))
        assertEquals(Decision.Refused("refused output scheme=none"), decide(output = "/sdcard/x.jpg", scheme = null, authority = null))
        assertEquals(Decision.Refused("refused output scheme=android.resource"), decide(output = "android.resource://p/1", scheme = "android.resource", authority = "p"))
        // The scheme is the caller's text: the line carries letters, digits and + - . only, and at most 16 of them.
        assertEquals(Decision.Refused("refused output scheme=abcdefghijklmnop"), decide(output = "x", scheme = "ab cd\nefghijklmnopqrstuvwxyz", authority = null))
    }
}

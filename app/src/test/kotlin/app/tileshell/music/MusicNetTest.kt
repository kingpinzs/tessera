package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20: the speaking User-Agent radio-browser and MusicBrainz ask for. */
class MusicNetTest {
    @Test fun `the form is Tessera slash version, then a comment`() {
        assertEquals("Tessera/0.1.0 (personal launcher; Music)", MusicNet.userAgent("0.1.0"))
        assertTrue(Regex("Tessera/[A-Za-z0-9._-]+ \\(.+\\)").matches(MusicNet.userAgent("1.2.3-debug")))
        assertEquals(mapOf("User-Agent" to "Tessera/0.1.0 (personal launcher; Music)"), MusicNet.headers("0.1.0"))
    }

    @Test fun `never blank and never a library default, whatever the version is`() {
        for (version in listOf(null, "", "   ", "\r\n", "Java/17", "Dalvik/2.1.0 (Linux)", "okhttp/4.12", "1.0\r\nX-Injected: 1", "é")) {
            val ua = MusicNet.userAgent(version)
            assertTrue("$version -> $ua", ua.startsWith("Tessera/") && ua.length > "Tessera/".length)
            assertFalse("$version -> $ua", ua.startsWith("Java") || ua.startsWith("Dalvik") || ua.startsWith("okhttp"))
            // One line, and exactly one product token before the comment.
            assertTrue("$version -> $ua", ua.none { it < ' ' || it.code > 0x7e })
            assertTrue("$version -> $ua", Regex("Tessera/[A-Za-z0-9._-]+ \\(personal launcher; Music\\)").matches(ua))
        }
        assertEquals("Tessera/0 (personal launcher; Music)", MusicNet.userAgent(null))
        assertEquals("Tessera/1.0X-Injected1 (personal launcher; Music)", MusicNet.userAgent("1.0\r\nX-Injected: 1"))
    }
}

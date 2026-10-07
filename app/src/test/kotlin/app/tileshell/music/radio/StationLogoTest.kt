package app.tileshell.music.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (r3 D2; trust): the shell's own capped fetch and bounded decode of a station's logo. */
class StationLogoTest {
    @Test fun `over 512 KiB is refused, and so is nothing at all`() {
        assertEquals(512 * 1024, StationLogo.MAX_BYTES)
        assertTrue(StationLogo.accept(ByteArray(1)))
        assertTrue(StationLogo.accept(ByteArray(512 * 1024)))
        assertFalse(StationLogo.accept(ByteArray(512 * 1024 + 1)))
        assertFalse(StationLogo.accept(ByteArray(0)))
        assertFalse(StationLogo.accept(null))
    }

    @Test fun `the decoded bounds are 512 px or under, whatever the picture declares`() {
        assertEquals(512, StationLogo.MAX_PX)
        for ((w, h) in listOf(1 to 1, 64 to 64, 512 to 512, 513 to 20, 20 to 513, 1024 to 1024, 1025 to 300, 4000 to 3000, 8192 to 8192, 8192 to 1, 600 to 599)) {
            val n = StationLogo.sampleSize(w, h)
            assertTrue("$w x $h -> $n", n >= 1 && (n and (n - 1)) == 0)
            assertTrue("$w x $h -> $n", maxOf(w, h) / n <= 512)
            // No smaller than it must be: half the sampling would be over the bound, or it is not sampled at all.
            assertTrue("$w x $h -> $n", n == 1 || maxOf(w, h) / (n / 2) > 512)
        }
        assertEquals(1, StationLogo.sampleSize(512, 512))
        assertEquals(2, StationLogo.sampleSize(513, 513))
        assertEquals(16, StationLogo.sampleSize(8192, 8192))
    }

    @Test fun `bounds that are not a picture's, or are past any logo's, are not decoded at all`() {
        for ((w, h) in listOf(0 to 0, -1 to 100, 100 to -1, 0 to 100, 8193 to 10, 10 to 8193, 30000 to 30000, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertEquals("$w x $h", 0, StationLogo.sampleSize(w, h))
        }
    }

    @Test fun `a non-http(s) logo address is never fetched, and neither is one on a private host`() {
        for (favicon in listOf(
            null, "", "  ", "file:///sdcard/Pictures/x.png", "content://media/external/images/media/1", "data:image/png;base64,iVBORw0KGgo=",
            "asset:///logo.png", "FILE:///x.png", "/favicon.ico", "favicon.ico", "http://192.168.1.1/admin/reboot.png", "http://localhost/x.png",
            "http://127.0.0.1:8080/x.png", "http://[::1]/x.png", "https://169.254.169.254/latest/meta-data",
        )) assertNull("$favicon", StationLogo.url(favicon, qaHost = null))
        assertEquals("https://cdn.example.net/logo.png", StationLogo.url("https://cdn.example.net/logo.png", null))
        assertEquals("http://cdn.example.net/logo.png", StationLogo.url(" http://cdn.example.net/logo.png ", null))
        // The fixture's host plays its part in a debug build only: the same rule as a stream's.
        assertNull(StationLogo.url("http://10.0.2.2:8080/logo.png", qaHost = null))
        assertEquals("http://10.0.2.2:8080/logo.png", StationLogo.url("http://10.0.2.2:8080/logo.png", qaHost = "10.0.2.2"))
    }
}

package app.tileshell.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 trust review, B-1 (a): what may be stored as a pasted secret and what may go into a request header. A value
 * the platform would refuse as a header (and quote in its exception) is never stored and never sent.
 */
class HeaderTextTest {
    @Test fun `a pasted key is trimmed at its ends and kept`() {
        assertEquals("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJxYSJ9.s-g_n", HeaderText.pastedSecret("  eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJxYSJ9.s-g_n\r\n"))
        assertEquals("a!~z", HeaderText.pastedSecret("\ta!~z "))
    }

    @Test fun `a pasted key with anything but printable ASCII inside is refused`() {
        assertNull("interior CR", HeaderText.pastedSecret("abc\rdef"))
        assertNull("interior LF", HeaderText.pastedSecret("abc\ndef"))
        assertNull("interior CRLF", HeaderText.pastedSecret("abc\r\ndef"))
        assertNull("interior tab", HeaderText.pastedSecret("abc\tdef"))
        assertNull("interior space", HeaderText.pastedSecret("abc def"))
        assertNull("0x7f", HeaderText.pastedSecret("abc\u007fdef"))
        assertNull("NUL", HeaderText.pastedSecret("abc\u0000def"))
        assertNull("a non-ASCII letter", HeaderText.pastedSecret("abcédef"))
        assertNull("a no-break space", HeaderText.pastedSecret("abc def"))
        assertNull("a line separator", HeaderText.pastedSecret("abc def"))
        assertNull("empty", HeaderText.pastedSecret(""))
        assertNull("only white space", HeaderText.pastedSecret(" \r\n\t "))
        assertNull("too long", HeaderText.pastedSecret("a".repeat(HeaderText.MAX_SECRET + 1)))
    }

    @Test fun `every key the validator keeps is one a header can carry`() {
        for (typed in listOf("abc", " a.b_c-d ", "A1!#\$%&'()*+,/:;<=>?@[]^`{|}~")) {
            assertTrue(typed, HeaderText.isHeaderSafe("Bearer " + HeaderText.pastedSecret(typed)!!))
        }
        assertFalse(HeaderText.isHeaderSafe("Bearer abc\rdef"))
        assertFalse(HeaderText.isHeaderSafe("Bearer abc\u007f"))
        assertFalse(HeaderText.isHeaderSafe("Bearer abcé"))
        assertFalse(HeaderText.isHeaderSafe(""))
    }

    @Test fun `a server's token is letters, digits, dot, underscore and hyphen only`() {
        assertTrue(HeaderText.isSafeToken("0123456789abcdef0123456789abcdef"))
        assertTrue(HeaderText.isSafeToken("A.b_c-9"))
        for (bad in listOf("", "a b", "a\rb", "a\nb", "a\tb", "a\"b", "a\\b", "a,b", "a=b", "a&b", "a/b", "aéb", "a\u007fb", "a".repeat(1025))) {
            assertFalse(bad.take(12), HeaderText.isSafeToken(bad))
        }
    }

    @Test fun `a quoted field drops every control character, the quote and the backslash`() {
        val everyControl = (0..0x1f).map { it.toChar() }.joinToString("") + "\u007f"
        assertEquals("ab", HeaderText.quoted("a" + everyControl + "b"))
        assertEquals("A, Token=x", HeaderText.quoted("A\", Token=\"x"))
        assertEquals("ab", HeaderText.quoted("a\\b"))
        assertEquals("Galaxy S25 Ultra", HeaderText.quoted("Galaxy S25 Ultra"))
        assertEquals("Tlphone", HeaderText.quoted("Téléphone "))
        assertTrue(HeaderText.isHeaderSafe("x" + HeaderText.quoted("a\r\nb\u0000c\u007fdé")))
    }
}

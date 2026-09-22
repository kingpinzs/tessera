package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Long-press alternates (phase 05 Decisions stand-in (3), H19): the plain letter first, case-aware. */
class AlternatesTest {

    @Test
    fun `the plain letter comes first, then its accents`() {
        val a = Alternates.of('a')
        assertEquals('a', a[0])
        assertEquals(listOf('a', 'à', 'á', 'â', 'ä', 'æ', 'ã', 'å', 'ā'), a)
        assertEquals(listOf('c', 'ç', 'ć', 'č'), Alternates.of('c'))
        assertEquals(listOf('e', 'è', 'é', 'ê', 'ë', 'ē', 'ė', 'ę'), Alternates.of('e'))
        assertEquals(listOf('n', 'ñ', 'ń'), Alternates.of('n'))
    }

    @Test
    fun `a letter with no alternates is only itself, and opens no popup`() {
        // Edge cases: "Long-press on a key with no alternates".
        assertEquals(listOf('x'), Alternates.of('x'))
        assertEquals(listOf('t'), Alternates.of('t'))
        assertFalse(Alternates.has('x'))
        assertTrue(Alternates.has('e'))
        assertEquals(listOf('X'), Alternates.of('x', shifted = true))
    }

    @Test
    fun `shifted gives the uppercase forms, and drops what has no single uppercase character`() {
        assertEquals(listOf('E', 'È', 'É', 'Ê', 'Ë', 'Ē', 'Ė', 'Ę'), Alternates.of('e', shifted = true))
        assertEquals(listOf('A', 'À', 'Á', 'Â', 'Ä', 'Æ', 'Ã', 'Å', 'Ā'), Alternates.of('A', shifted = true))
        // ß uppercases to "SS", two characters, so it is not a cell.
        assertEquals(listOf('S', 'Ś', 'Š'), Alternates.of('s', shifted = true))
        assertEquals(listOf('s', 'ß', 'ś', 'š'), Alternates.of('s'))
    }

    @Test
    fun `an uppercase key unshifted still gives the lowercase table`() {
        assertEquals(Alternates.of('e'), Alternates.of('E'))
    }

    @Test
    fun `the period and comma keys carry punctuation`() {
        val period = Alternates.of('.')
        assertEquals('.', period[0])
        assertTrue('?' in period && '!' in period && ':' in period)
        assertEquals(period, Alternates.of('.', shifted = true))
        assertEquals(',', Alternates.of(',')[0])
    }
}

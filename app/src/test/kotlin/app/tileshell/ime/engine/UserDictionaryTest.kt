package app.tileshell.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Word learning (phase 05 Decisions stand-in (6)): learned on the second commit outside a password
 * field, "+ word" at once, "– word" forgets it and its count (R6 §2.2.7).
 */
class UserDictionaryTest {

    private val store = MemoryStore()
    private fun dict(s: MemoryStore = store) = UserDictionary(s) { TestWords.small.contains(it) }

    @Test
    fun `a word is learned on its second commit, not its first`() {
        val d = dict()
        assertFalse(d.commit("Becase", FieldKind.TEXT))
        assertFalse(d.contains("becase"))
        assertEquals(1, d.pendingCount("becase"))
        assertTrue(d.commit("becase", FieldKind.TEXT))
        assertTrue(d.contains("Becase"))
        assertEquals(0, d.pendingCount("becase"))
        // the spelling kept is the one that completed the learning
        assertEquals("becase", d.canonical("BECASE"))
    }

    @Test
    fun `commits in a password field never count`() {
        val d = dict()
        repeat(5) { assertFalse(d.commit("hunter", FieldKind.PASSWORD)) }
        assertEquals(0, d.pendingCount("hunter"))
        assertFalse(d.contains("hunter"))
        // one password commit plus one text commit is still only one
        d.commit("hunter", FieldKind.TEXT)
        assertFalse(d.contains("hunter"))
        assertEquals(1, d.pendingCount("hunter"))
    }

    @Test
    fun `URL, email and no-suggestions fields do learn`() {
        val d = dict()
        d.commit("tileshell", FieldKind.URL)
        assertTrue(d.commit("tileshell", FieldKind.EMAIL))
        d.commit("selawik", FieldKind.NO_SUGGESTIONS)
        assertTrue(d.commit("selawik", FieldKind.NO_SUGGESTIONS))
    }

    @Test
    fun `plus word learns at once`() {
        val d = dict()
        d.add("Becase")
        assertTrue(d.contains("becase"))
        assertEquals(listOf("Becase"), d.words.toList())
    }

    @Test
    fun `minus word forgets it, and it needs two fresh commits to come back`() {
        val d = dict()
        d.add("becase")
        d.remove("Becase")
        assertFalse(d.contains("becase"))
        d.commit("becase", FieldKind.TEXT)
        assertFalse(d.contains("becase"))
        d.commit("becase", FieldKind.TEXT)
        assertTrue(d.contains("becase"))
    }

    @Test
    fun `minus word on a half-learned word clears its count`() {
        val d = dict()
        d.commit("becase", FieldKind.TEXT)
        d.remove("becase")
        assertEquals(0, d.pendingCount("becase"))
        d.commit("becase", FieldKind.TEXT)
        assertFalse(d.contains("becase"))
    }

    @Test
    fun `a dictionary word is never learned, and neither is a non-word token`() {
        val d = dict()
        repeat(3) { d.commit("the", FieldKind.TEXT) }
        assertFalse(d.contains("the"))
        repeat(3) { d.commit("2026", FieldKind.TEXT) }
        repeat(3) { d.commit("http://x", FieldKind.TEXT) }
        repeat(3) { d.commit(":-)", FieldKind.TEXT) }
        assertTrue(d.words.isEmpty())
        d.add("2026")
        assertTrue(d.words.isEmpty())
        assertTrue(UserDictionary.isLearnable("o'neill"))
        assertTrue(UserDictionary.isLearnable("check-in"))
        assertFalse(UserDictionary.isLearnable("-"))
        assertFalse(UserDictionary.isLearnable(""))
    }

    @Test
    fun `learned words and pending counts survive a reload`() {
        val d = dict()
        d.add("Becase")
        d.commit("selawik", FieldKind.TEXT)
        val again = dict(MemoryStore(store.text))
        assertTrue(again.contains("becase"))
        assertEquals("Becase", again.canonical("becase"))
        assertEquals(1, again.pendingCount("selawik"))
        assertTrue(again.commit("selawik", FieldKind.TEXT))
    }

    @Test
    fun `the file is versioned and human-readable`() {
        val d = dict()
        d.add("Becase")
        d.commit("selawik", FieldKind.TEXT)
        assertEquals("tileshell-user-dictionary 1\nlearned\tBecase\npending\tselawik\t1\n", store.text)
    }

    @Test
    fun `an unknown format version or an empty store starts empty rather than guessing`() {
        assertTrue(dict(MemoryStore("tileshell-user-dictionary 99\nlearned\tx\n")).words.isEmpty())
        assertTrue(dict(MemoryStore("")).words.isEmpty())
        assertTrue(dict(MemoryStore(null)).words.isEmpty())
        assertNull(dict(MemoryStore("garbage")).canonical("garbage"))
    }

    @Test
    fun `every change is saved, and a no-op removal is not`() {
        val d = dict()
        d.commit("becase", FieldKind.TEXT)
        d.commit("becase", FieldKind.TEXT)
        d.add("selawik")
        d.remove("selawik")
        assertEquals(4, store.saves)
        d.remove("never")
        d.commit("the", FieldKind.TEXT)
        d.commit("x", FieldKind.PASSWORD)
        assertEquals(4, store.saves)
    }
}

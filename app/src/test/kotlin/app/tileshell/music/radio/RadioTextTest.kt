package app.tileshell.music.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 20 (r3 D9; trust): a directory string or a StreamTitle before it is shown anywhere. */
class RadioTextTest {
    @Test fun `control characters and newlines are removed`() {
        assertEquals("Jazz FM", RadioText.shown("Jazz\u0000 \u0007FM\u007f", 80))
        assertEquals("line one[music] stream: connected", RadioText.shown("line one\n[music] stream: connected", 80))
        assertEquals("ab", RadioText.shown("a\r\n\tb", 80))
        assertEquals("ab", RadioText.shown("a  \u0085b", 80))
    }

    @Test fun `bidi and format characters are removed - overrides, isolates, marks, zero-width, the BOM, the tag block`() {
        val bidi = "‪‫‬‭‮⁦⁧⁨⁩‎‏؜"
        assertEquals("abc", RadioText.shown("a${bidi}b${bidi}c", 80))
        assertEquals("abc", RadioText.shown("a​‌‍⁠﻿­b⁪⁯c", 80))
        // Format characters above the BMP (U+E0001, U+E0020..) and a lone surrogate.
        val tags = String(Character.toChars(0xE0001)) + String(Character.toChars(0xE0041))
        assertEquals("ab", RadioText.shown("a${tags}b", 80))
        assertEquals("ab", RadioText.shown("a\ud83db", 80))
        assertEquals("ab", RadioText.shown("a\udc00b", 80))
    }

    @Test fun `a hostile name comes out as one plain line`() {
        val hostile = "‮Real FM‬\n[music] search \"x\": station Evil\u0000\u001b[31m" + "A".repeat(500)
        val shown = RadioText.shown(hostile, RadioText.NAME_MAX)
        assertEquals(80, shown.length)
        assertTrue(shown.startsWith("Real FM[music] search \"x\": station Evil[31mAAAA"))
        assertTrue(shown.none { Character.getType(it).toByte() in listOf(Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR) })
        assertFalse(shown.contains('\n'))
    }

    @Test fun `a name is cut at 80 and a StreamTitle at 120, never through a surrogate pair`() {
        assertEquals(80, RadioText.NAME_MAX)
        assertEquals(120, RadioText.TITLE_MAX)
        assertEquals("x".repeat(80), RadioText.shown("x".repeat(81), RadioText.NAME_MAX))
        assertEquals("x".repeat(80), RadioText.shown("x".repeat(80), RadioText.NAME_MAX))
        assertEquals("y".repeat(120), RadioText.shown("y".repeat(1000), RadioText.TITLE_MAX))
        val note = String(Character.toChars(0x1F3B5))
        assertEquals("x".repeat(79), RadioText.shown("x".repeat(79) + note, 80))
        assertEquals("x".repeat(78) + note, RadioText.shown("x".repeat(78) + note + "z", 80))
        // What is removed does not count towards the cut.
        assertEquals("x".repeat(80), RadioText.shown("‮".repeat(200) + "x".repeat(80), 80))
    }

    @Test fun `empty stays empty, and so does text that is nothing but what is removed`() {
        assertEquals("", RadioText.shown(null, 80))
        assertEquals("", RadioText.shown("", 80))
        assertEquals("", RadioText.shown("   ", 80))
        assertEquals("", RadioText.shown("\u0000\n‮​", 80))
        assertEquals("", RadioText.shown("abc", 0))
    }

    @Test fun `ordinary text is untouched but for its ends - accents, CJK, emoji, punctuation`() {
        for (text in listOf("Radio Österreich 1", "ラジオ日本", "Rock & Roll — 100% \"live\"", "Jazz " + String(Character.toChars(0x1F3B7)))) {
            assertEquals(text, RadioText.shown(text, 80))
            assertEquals(text, RadioText.shown("  $text  ", 80))
        }
    }

    @Test fun `the comparison key is lower case with single spaces`() {
        assertEquals("qa jazz one", RadioText.key("  QA   Jazz One "))
        assertEquals("österreich 1", RadioText.key("ÖSTERREICH 1"))
        assertEquals("", RadioText.key(null))
        assertEquals("ab", RadioText.key("a​B"))
    }
}

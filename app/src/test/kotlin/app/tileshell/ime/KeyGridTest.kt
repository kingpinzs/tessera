package app.tileshell.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Every R6 §2 keyboard number the build lays out, checked against its own row and tolerance. The device
 * rows (E3) measure the rendered keyboard; these prove the model that renders it says the same thing.
 */
class KeyGridTest {

    private val letters = Layouts.build(Layer.LETTERS, FieldInfo.DEFAULT)

    private fun within(name: String, expected: Float, actual: Float, tol: Float) {
        assertTrue("$name: expected $expected ± $tol, got $actual", abs(expected - actual) <= tol + 1e-3f)
    }

    private fun key(layout: Layout, id: String): Key = assertNotNull("key $id", layout.key(id)).let { layout.key(id)!! }

    @Test fun `row 1 keys, gaps and margins are R6 2_1_2 to 2_1_4`() {
        val row = "qwertyuiop".map { key(letters, "$it") }
        row.forEach { within("row 1 ${it.id} width", 130f, it.width, 3f) }
        row.zipWithNext().forEach { (a, b) -> within("gap ${a.id}/${b.id}", 14f, b.left - a.right, 3f) }
        within("left margin", 5f, row.first().left, 4f)
        within("right margin", 9f, KeyGrid.PANEL - row.last().right, 4f)
        within("pitch", 144f, (row.last().left - row.first().left) / 9f, 0.01f)
    }

    @Test fun `row 2 keys and insets are R6 2_1_5 and 2_1_6`() {
        val row = "asdfghjkl".map { key(letters, "$it") }
        row.forEach { within("row 2 ${it.id} width", 128f, it.width, 3f) }
        row.zipWithNext().forEach { (a, b) -> within("gap ${a.id}/${b.id}", 16f, b.left - a.right, 3f) }
        within("left inset", 77.5f, row.first().left, 3f)
        within("right inset", 82f, KeyGrid.PANEL - row.last().right, 3f)
    }

    @Test fun `row 3 shift, backspace and z to m are R6 2_1_7 and 2_1_8`() {
        within("shift", 201f, key(letters, "shift").width, 2f)
        within("backspace", 201f, key(letters, "bksp").width, 2f)
        "zxcvbnm".forEachIndexed { i, c ->
            val k = key(letters, "$c")
            within("$c width", 128f, k.width, 3f)
            // z under s, x under d, …, m under k.
            assertEquals("$c column", key(letters, "${"sdfghjk"[i]}").left, k.left, 0.001f)
        }
    }

    @Test fun `row 4 in a default field is R6 2_1_9`() {
        within("&123", 201f, key(letters, "sym").width, 3f)
        within("emoji", 128f, key(letters, "emoji").width, 3f)
        within("comma", 128f, key(letters, "comma").width, 3f)
        within("space", 560f, key(letters, "space").width, 3f)
        within("period", 128f, key(letters, "period").width, 3f)
        within("enter", 201f, key(letters, "enter").width, 3f)
        assertEquals(KeyStyle.FUNCTION, key(letters, "enter").style)
    }

    @Test fun `URL row is R6 2_8_3`() {
        val url = Layouts.build(Layer.LETTERS, FieldInfo.from(0x11 /* text|uri */, 2 /* go */))
        within(".com", 201f, key(url, "dotcom").width, 3f)
        within("space", 418.6f, key(url, "space").width, 3f)
        within("period", 201f, key(url, "period").width, 3f)
        within("enter", 201f, key(url, "enter").width, 3f)
        assertEquals(KeyStyle.ACTION_WHITE, key(url, "enter").style)
        assertEquals(Layouts.GLYPH_ARROW_RIGHT, key(url, "enter").glyph)
    }

    @Test fun `search row has the white magnifier key, R6 2_8_2`() {
        val search = Layouts.build(Layer.LETTERS, FieldInfo.from(0x1, 3 /* search */))
        assertEquals(KeyStyle.ACTION_WHITE, key(search, "enter").style)
        assertEquals(Layouts.GLYPH_SEARCH, key(search, "enter").glyph)
        within("search action key", 201f, key(search, "enter").width, 3f)
    }

    @Test fun `heights, pitch, bottom margin and block are R6 2_1_10 to 2_1_13`() {
        letters.keys.forEach { within("${it.id} height", 202f, it.height, 3f) }
        within("row pitch", 217.5f, key(letters, "a").top - key(letters, "q").top, 1.5f)
        within("vertical gap", 15f, key(letters, "a").top - key(letters, "q").bottom, 3f)
        within("bottom margin", 7f, KeyGrid.BLOCK_H - key(letters, "space").bottom, 4f)
        within("key block", 865f, KeyGrid.BLOCK_H, 5f)
    }

    @Test fun `cursor dot sits on the three gaps, R6 2_5_4 and the left-handed mirror`() {
        within("right-handed x", 358f, KeyGrid.dotRightHandedX, 3f)
        within("above the nav bar", 218f, KeyGrid.BLOCK_H - KeyGrid.dotY, 3f)
        within("left-handed x", 1077.5f, KeyGrid.dotLeftHandedX, 3f)
        // The gap it names really is a gap: z/x and emoji/comma.
        assertTrue(KeyGrid.dotRightHandedX > key(letters, "z").right && KeyGrid.dotRightHandedX < key(letters, "x").left)
        assertTrue(KeyGrid.dotRightHandedX > key(letters, "emoji").right && KeyGrid.dotRightHandedX < key(letters, "comma").left)
        assertTrue(KeyGrid.dotLeftHandedX > key(letters, "n").right && KeyGrid.dotLeftHandedX < key(letters, "m").left)
        assertTrue(KeyGrid.dotLeftHandedX > key(letters, "space").right && KeyGrid.dotLeftHandedX < key(letters, "period").left)
    }

    @Test fun `metrics scale phys by width over 1440 and the strip by epx`() {
        val m = KeyboardMetrics(1080f, 2340f, 3f)
        assertEquals(0.75f, m.sx, 1e-6f)
        assertEquals(0.75f, m.sy, 1e-6f)
        assertEquals(139.5f, m.stripH, 1e-4f)
        assertEquals(1080f * 0.75f / 1080f * 202f, m.h(202f), 1e-4f)
        // Docked: 0.80 of the width, column pitch 115.2 phys, flush right.
        val docked = KeyboardMetrics(1080f, 2340f, 3f, Dock.RIGHT)
        assertEquals(115.2f * 0.75f, docked.w(KeyGrid.PITCH), 1e-3f)
        assertEquals(1080f * 0.2f, docked.offsetX, 1e-3f)
        assertEquals(1080f, docked.x(KeyGrid.PANEL), 1e-3f)
    }

    @Test fun `nearest-key hit test sends a gap touch to the closer key`() {
        val q = key(letters, "q")
        val w = key(letters, "w")
        val midGap = (q.right + w.left) / 2f
        assertEquals("q", letters.hit(midGap - 1f, q.centerY)!!.id)
        assertEquals("w", letters.hit(midGap + 1f, q.centerY)!!.id)
    }
}

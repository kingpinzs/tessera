package app.tileshell.photos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditMatricesTest {
    /** The phase doc, found from wherever Gradle runs the tests. */
    private fun doc(): File {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null) {
            val f = File(dir, "docs/plan/phase-17-inbox-photos-camera-video.md")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("phase 17's doc was not found above ${System.getProperty("user.dir")}")
    }

    /** The doc's block: every line that starts `  matrix <name> <20 numbers>`. */
    private fun docMatrices(): Map<String, DoubleArray> = doc().readLines().filter { it.startsWith("  matrix ") }.associate { line ->
        val parts = line.trim().split(Regex("\\s+"))
        parts[1] to parts.drop(2).map { it.toDouble() }.toDoubleArray()
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun parts(p: Int) = listOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    @Test
    fun `the table is the doc's matrix block, number for number`() {
        val fromDoc = docMatrices()
        assertEquals("the doc's block holds eleven matrices", 11, fromDoc.size)
        assertEquals(fromDoc.keys, EditMatrices.TABLE.keys)
        for ((name, expected) in fromDoc) {
            assertEquals("$name has 20 numbers in the doc", 20, expected.size)
            assertArrayEquals(name, expected, EditMatrices.TABLE.getValue(name), 0.0)
        }
    }

    @Test
    fun `every filter the editor offers is in the block and no other`() {
        assertEquals(EditMatrices.TABLE.keys.filter { it.startsWith("filter:") }.map { it.removePrefix("filter:") }.toSet(), EditMatrices.FILTERS.toSet())
        assertEquals(6, EditMatrices.FILTERS.size)
        assertEquals(EditMatrices.FILTERS.toSet(), EditMatrices.FILTER_LABELS.keys)
    }

    @Test
    fun `light step k is the identity with 20 k on each colour channel`() {
        assertNull(EditMatrices.light(0))
        for (k in -5..5) {
            if (k == 0) continue
            val m = EditMatrices.light(k)!!
            val c = 20.0 * k
            assertArrayEquals("light $k", doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, 0.0, 1.0, 0.0, 0.0, c, 0.0, 0.0, 1.0, 0.0, c, 0.0, 0.0, 0.0, 1.0, 0.0), m, 0.0)
        }
        assertEquals(listOf(140, 120, 100), parts(EditMatrices.apply(EditMatrices.light(1)!!, rgb(120, 100, 80))))
        assertEquals("clamped at the top", listOf(255, 100, 255), parts(EditMatrices.apply(EditMatrices.light(5)!!, rgb(200, 0, 180))))
        assertEquals(listOf(0, 0, 60), parts(EditMatrices.apply(EditMatrices.light(-5)!!, rgb(90, 100, 160))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `light has no step six`() { EditMatrices.light(6) }

    @Test(expected = IllegalArgumentException::class)
    fun `colour has no step five`() { EditMatrices.colour(5) }

    @Test
    fun `colour step k is the saturation matrix for 1 + k over 4`() {
        assertNull(EditMatrices.colour(0))
        for (k in -4..4) {
            if (k == 0) continue
            val s = 1.0 + 0.25 * k
            val m = EditMatrices.colour(k)!!
            val expected = doubleArrayOf(
                0.213 * (1 - s) + s, 0.715 * (1 - s), 0.072 * (1 - s), 0.0, 0.0,
                0.213 * (1 - s), 0.715 * (1 - s) + s, 0.072 * (1 - s), 0.0, 0.0,
                0.213 * (1 - s), 0.715 * (1 - s), 0.072 * (1 - s) + s, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0, 0.0,
            )
            // The two steps the doc names carry its four-decimal numbers.
            assertArrayEquals("colour $k", expected, m, 0.00006)
        }
        // k = -4 is greyscale: the three rows are the luma weights.
        val grey = EditMatrices.colour(-4)!!
        assertArrayEquals(doubleArrayOf(0.213, 0.715, 0.072), grey.sliceArray(0..2), 1e-12)
        assertArrayEquals(grey.sliceArray(0..4), grey.sliceArray(5..9), 1e-12)
        assertArrayEquals(grey.sliceArray(0..4), grey.sliceArray(10..14), 1e-12)
        val p = parts(EditMatrices.apply(grey, rgb(220, 40, 40)))
        assertEquals(1, p.toSet().size)
    }

    @Test
    fun `a result is rounded half up and clamped, and alpha is kept`() {
        // 0.5 exactly rounds up: fade on 0 gives 38; a constant of 0.5 would give 1.
        val half = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.5, 0.0, 1.0, 0.0, 0.0, -0.5, 0.0, 0.0, 1.0, 0.0, 300.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val out = EditMatrices.apply(half, (0x80 shl 24) or (10 shl 16) or (10 shl 8) or 10)
        assertEquals(listOf(11, 10, 255), parts(out))
        assertEquals(0x80, out ushr 24)
        assertEquals(listOf(0, 0, 0), parts(EditMatrices.apply(EditMatrices.TABLE.getValue("enhance"), rgb(10, 10, 10))))
    }

    @Test
    fun `every matrix moves a flat fixture by at least 16 on some channel`() {
        // The doc's claim, which lets E6 fail an identity matrix: on qa-photo-0..2.
        val fixtures = listOf(rgb(220, 40, 40), rgb(40, 180, 80), rgb(40, 90, 220))
        for ((name, matrix) in EditMatrices.TABLE) for (f in fixtures) {
            val moved = parts(EditMatrices.apply(matrix, f)).zip(parts(f)).maxOf { (a, b) -> kotlin.math.abs(a - b) }
            assertTrue("$name on ${parts(f)} moves a channel by $moved", moved >= 16)
        }
    }

    @Test
    fun `enhance on the red fixture is the doc's arithmetic`() {
        // 1.3417*220 - 0.1287*40 - 0.013*40 - 25.6 = 263.9 -> 255; -0.0383*220 + 1.2513*40 - 0.013*40 - 25.6 = 15.5 -> 16;
        // -0.0383*220 - 0.1287*40 + 1.367*40 - 25.6 = 15.5 -> 16 (15.506 and 15.506).
        assertEquals(listOf(255, 16, 16), parts(EditMatrices.Recipe(enhance = true).apply(rgb(220, 40, 40))))
    }

    @Test
    fun `the tools compose in the order filter, light, colour, enhance`() {
        val recipe = EditMatrices.Recipe(filter = "warm", light = 2, colour = -1, enhance = true)
        val pixel = rgb(40, 180, 80)
        var expected = pixel
        for (m in listOf(EditMatrices.filter("warm")!!, EditMatrices.light(2)!!, EditMatrices.colour(-1)!!, EditMatrices.TABLE.getValue("enhance"))) expected = EditMatrices.apply(m, expected)
        assertEquals(expected, recipe.apply(pixel))
        assertEquals(4, recipe.steps.size)
        // The order matters (each step clamps): the reverse order gives another pixel.
        var reversed = pixel
        for (m in listOf(EditMatrices.TABLE.getValue("enhance"), EditMatrices.colour(-1)!!, EditMatrices.light(2)!!, EditMatrices.filter("warm")!!)) reversed = EditMatrices.apply(m, reversed)
        assertNotEquals(reversed, recipe.apply(pixel))
        // The recipe holds settings, not taps: the same settings are the same recipe however they were reached.
        assertEquals(recipe, EditMatrices.Recipe(enhance = true).copy(colour = -1).copy(light = 2).copy(filter = "warm"))
    }

    @Test
    fun `nothing set changes nothing`() {
        val none = EditMatrices.Recipe()
        assertTrue(none.isIdentity)
        assertEquals(rgb(1, 2, 3), none.apply(rgb(1, 2, 3)))
        val tile = intArrayOf(rgb(220, 40, 40), rgb(40, 180, 80))
        EditMatrices.Recipe(filter = "mono").apply(tile)
        assertEquals(listOf(listOf(78, 78, 78), listOf(143, 143, 143)), tile.map(::parts))
    }
}

package app.tileshell.photos

import kotlin.math.floor

/**
 * The editor's colour transforms (phase 17 Decisions, 2026-10-05, r3 V12): each a 4 × 5 matrix in Android's
 * `ColorMatrix` order — rows R′ G′ B′ A′, columns r g b a and a constant on the 0–255 scale — applied to a pixel's
 * 8-bit sRGB values as they are, each result rounded to the nearest integer and clamped to 0–255; alpha is unchanged.
 *
 * [TABLE] holds the numbers of the phase doc's `matrix` block, and `EditMatricesTest` READS THAT BLOCK FROM THE DOC and
 * fails when the two differ — the gate's `edit_expect.py` reads the same block, so neither can drift from it.
 * Free of Android types.
 */
object EditMatrices {
    val TABLE: Map<String, DoubleArray> = mapOf(
        "enhance" to m(1.3417, -0.1287, -0.013, 0.0, -25.6, -0.0383, 1.2513, -0.013, 0.0, -25.6, -0.0383, -0.1287, 1.367, 0.0, -25.6, 0.0, 0.0, 0.0, 1.0, 0.0),
        "light:+1" to m(1.0, 0.0, 0.0, 0.0, 20.0, 0.0, 1.0, 0.0, 0.0, 20.0, 0.0, 0.0, 1.0, 0.0, 20.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "light:-1" to m(1.0, 0.0, 0.0, 0.0, -20.0, 0.0, 1.0, 0.0, 0.0, -20.0, 0.0, 0.0, 1.0, 0.0, -20.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "colour:+1" to m(1.1967, -0.1787, -0.018, 0.0, 0.0, -0.0532, 1.0713, -0.018, 0.0, 0.0, -0.0532, -0.1787, 1.232, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "colour:-1" to m(0.8033, 0.1787, 0.018, 0.0, 0.0, 0.0532, 0.9287, 0.018, 0.0, 0.0, 0.0532, 0.1787, 0.768, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "filter:mono" to m(0.213, 0.715, 0.072, 0.0, 0.0, 0.213, 0.715, 0.072, 0.0, 0.0, 0.213, 0.715, 0.072, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "filter:sepia" to m(0.393, 0.769, 0.189, 0.0, 0.0, 0.349, 0.686, 0.168, 0.0, 0.0, 0.272, 0.534, 0.131, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "filter:warm" to m(1.1, 0.0, 0.0, 0.0, 10.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, -10.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "filter:cool" to m(0.9, 0.0, 0.0, 0.0, -10.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.1, 0.0, 10.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "filter:vivid" to m(1.3935, -0.3575, -0.036, 0.0, 0.0, -0.1065, 1.1425, -0.036, 0.0, 0.0, -0.1065, -0.3575, 1.464, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0),
        "filter:fade" to m(0.8, 0.0, 0.0, 0.0, 38.0, 0.0, 0.8, 0.0, 0.0, 38.0, 0.0, 0.0, 0.8, 0.0, 38.0, 0.0, 0.0, 0.0, 1.0, 0.0),
    )

    /** The six filters, in the order the editor offers them; "None" is no filter. */
    val FILTERS = listOf("mono", "sepia", "warm", "cool", "vivid", "fade")
    val FILTER_LABELS = mapOf("mono" to "Mono", "sepia" to "Sepia", "warm" to "Warm", "cool" to "Cool", "vivid" to "Vivid", "fade" to "Fade")

    const val LIGHT_MIN = -5
    const val LIGHT_MAX = 5
    const val COLOUR_MIN = -4
    const val COLOUR_MAX = 4

    private fun m(vararg v: Double): DoubleArray = v

    /** Light step [k] (−5 … +5): the identity with the constant 20·k on R, G and B. Null for k = 0. */
    fun light(k: Int): DoubleArray? {
        require(k in LIGHT_MIN..LIGHT_MAX) { "light step $k" }
        if (k == 0) return null
        TABLE["light:${signed(k)}"]?.let { return it }
        val c = 20.0 * k
        return m(1.0, 0.0, 0.0, 0.0, c, 0.0, 1.0, 0.0, 0.0, c, 0.0, 0.0, 1.0, 0.0, c, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    /**
     * Colour step [k] (−4 … +4): the saturation matrix for s = 1 + 0.25·k with the luma weights 0.213 / 0.715 / 0.072;
     * k = −4 is greyscale. Null for k = 0. The two steps the doc's block names use the block's own (rounded) numbers.
     */
    fun colour(k: Int): DoubleArray? {
        require(k in COLOUR_MIN..COLOUR_MAX) { "colour step $k" }
        if (k == 0) return null
        TABLE["colour:${signed(k)}"]?.let { return it }
        val s = 1.0 + 0.25 * k
        val r = 0.213 * (1 - s)
        val g = 0.715 * (1 - s)
        val b = 0.072 * (1 - s)
        return m(r + s, g, b, 0.0, 0.0, r, g + s, b, 0.0, 0.0, r, g, b + s, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
    }

    fun filter(name: String?): DoubleArray? = name?.let { TABLE["filter:$it"] ?: throw IllegalArgumentException("filter $it") }

    private fun signed(k: Int) = if (k > 0) "+$k" else "$k"

    private fun channel(v: Double): Int = floor(v + 0.5).toInt().coerceIn(0, 255)

    /** [matrix] applied to one ARGB pixel: each result rounded to the nearest integer (half up) and clamped. */
    fun apply(matrix: DoubleArray, argb: Int): Int {
        val a = argb ushr 24
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val r2 = channel(matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4])
        val g2 = channel(matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9])
        val b2 = channel(matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14])
        return (a shl 24) or (r2 shl 16) or (g2 shl 8) or b2
    }

    /**
     * The colour tools as set. They compose in the FIXED order filter → light → colour → enhance, whatever order they
     * were tapped in; each step's result is rounded and clamped before the next.
     */
    data class Recipe(val filter: String? = null, val light: Int = 0, val colour: Int = 0, val enhance: Boolean = false) {
        val steps: List<DoubleArray> = listOfNotNull(filter(filter), light(light), colour(colour), if (enhance) TABLE.getValue("enhance") else null)
        val isIdentity: Boolean get() = steps.isEmpty()

        fun apply(argb: Int): Int {
            var p = argb
            for (s in steps) p = EditMatrices.apply(s, p)
            return p
        }

        /** In place, for a tile of pixels. */
        fun apply(pixels: IntArray, count: Int = pixels.size) {
            if (steps.isEmpty()) return
            for (i in 0 until count) pixels[i] = apply(pixels[i])
        }
    }
}

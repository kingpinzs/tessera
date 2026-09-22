package app.tileshell.ime

import kotlin.math.max
import kotlin.math.min

/** What a key does. */
enum class KeyCode { CHAR, SHIFT, BACKSPACE, SYMBOLS, LETTERS, PAGE, EMOJI, SPACE, ENTER }

/**
 * How a key is filled. R6 §2.1.18 (MEDIUM): letters, comma, space and period are dark (48,48,48);
 * shift, backspace, &123, emoji and Enter are the lighter function grey ≈(73,74,72). §2.8.2 / §2.8.3
 * (MEDIUM): a search field's and a URL field's action key is white-filled with a dark glyph.
 */
enum class KeyStyle { DARK, FUNCTION, ACTION_WHITE }

/**
 * One key, positioned in [KeyGrid]'s phys coordinates (x from the panel's left edge, y down from
 * row 1's top). [text] is what a CHAR key commits (lowercase; the controller applies shift);
 * [glyph] is a Fluent icon code point for keys drawn with a glyph instead of text.
 */
data class Key(
    val id: String,
    val code: KeyCode,
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val style: KeyStyle,
    val glyph: String? = null,
    val sub: String? = null,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f

    /** The test tag uiautomator reports as this key's resource-id (`testTagsAsResourceId`). */
    val tag: String get() = "kb_key_$id"

    /** Squared distance from a point to this key's rectangle; 0 inside it. */
    fun distance2(x: Float, y: Float): Float {
        val dx = max(max(left - x, 0f), x - right)
        val dy = max(max(top - y, 0f), y - bottom)
        return dx * dx + dy * dy
    }
}

enum class Layer { LETTERS, SYMBOLS_1, SYMBOLS_2, PHONE }

/** A laid-out set of keys. Hit testing is nearest-key, so a touch in a gap goes to the closest key. */
class Layout(val layer: Layer, val keys: List<Key>) {
    fun key(id: String): Key? = keys.firstOrNull { it.id == id }

    fun hit(x: Float, y: Float): Key? {
        if (y < -KeyGrid.ROW_PITCH / 2f || y > KeyGrid.BLOCK_H) return null
        return keys.minByOrNull { it.distance2(x, y) }
    }

    /** Key centres by lowercase letter, for the text engine's proximity and Word Flow models. */
    fun letterCentres(): Map<Char, Pair<Float, Float>> = keys
        .filter { it.code == KeyCode.CHAR && it.text.length == 1 && it.text[0].isLetter() }
        .associate { it.text[0] to (it.centerX to it.centerY) }
}

/**
 * The key layouts per layer and per field (R6 §2.1 and §2.8), built from [KeyGrid]'s numbers only.
 *
 * Row 4 is where the field changes the keyboard: R6 §2.8.1 (HIGH) default, §2.8.2 (MEDIUM) search,
 * §2.8.3 (MEDIUM) URL; Go uses the URL field's white → key and Send / Next / Done the default grey ↵
 * (approximation, H9, because §2.8.5 found no footage of them); email fields use the default row (H9).
 */
object Layouts {

    fun build(layer: Layer, field: FieldInfo): Layout = when (layer) {
        Layer.LETTERS -> Layout(layer, letterRows() + bottomRow(field, letters = true))
        Layer.SYMBOLS_1 -> Layout(layer, symbolRows(SYMBOLS_1, page = "1/2") + bottomRow(field, letters = false))
        Layer.SYMBOLS_2 -> Layout(layer, symbolRows(SYMBOLS_2, page = "2/2") + bottomRow(field, letters = false))
        Layer.PHONE -> Layout(layer, phoneKeypad(field))
    }

    /** The layer a field opens on. Phone numbers get R6 §2.8.4's keypad (LOW, H8). */
    fun initialLayer(field: FieldInfo): Layer = when (field.kind) {
        FieldKind.PHONE -> Layer.PHONE
        // Approximation (H2): no W10M footage shows a number field; the symbols page carries the digits
        // on its top row and keeps the full row 4, so a number field loses nothing it could type.
        FieldKind.NUMBER -> Layer.SYMBOLS_1
        else -> Layer.LETTERS
    }

    // ---- letters (R6 §2.1.1–2.1.8, HIGH) ---------------------------------------------------------

    private const val ROW1 = "qwertyuiop"
    private const val ROW2 = "asdfghjkl"
    private const val ROW3 = "zxcvbnm"

    private fun letterRows(): List<Key> {
        val keys = mutableListOf<Key>()
        ROW1.forEachIndexed { i, c ->
            keys += Key("$c", KeyCode.CHAR, "$c", KeyGrid.row1Left(i), KeyGrid.rowTop(0), KeyGrid.ROW1_KEY_W, KeyGrid.KEY_H, KeyStyle.DARK)
        }
        ROW2.forEachIndexed { c, ch ->
            keys += Key("$ch", KeyCode.CHAR, "$ch", KeyGrid.col2Left(c), KeyGrid.rowTop(1), KeyGrid.ROW2_KEY_W, KeyGrid.KEY_H, KeyStyle.DARK)
        }
        keys += shiftKey(KeyCode.SHIFT, "shift", glyph = GLYPH_SHIFT)
        ROW3.forEachIndexed { i, ch ->
            // R6 §2.1.8: z…m sit on row 2's s…k columns (z under s).
            keys += Key("$ch", KeyCode.CHAR, "$ch", KeyGrid.col2Left(i + 1), KeyGrid.rowTop(2), KeyGrid.ROW2_KEY_W, KeyGrid.KEY_H, KeyStyle.DARK)
        }
        keys += backspaceKey()
        return keys
    }

    /** R6 §2.1.7: shift runs from the left margin to the a-column's right edge (201 ± 2). */
    private fun shiftKey(code: KeyCode, id: String, glyph: String? = null, label: String = ""): Key =
        Key(id, code, label, KeyGrid.ROW1_LEFT, KeyGrid.rowTop(2), KeyGrid.col2Right(0) - KeyGrid.ROW1_LEFT, KeyGrid.KEY_H, KeyStyle.FUNCTION, glyph)

    /** R6 §2.1.7: backspace runs from the l-column's left edge to the right margin (201 ± 2). */
    private fun backspaceKey(): Key =
        Key("bksp", KeyCode.BACKSPACE, "", KeyGrid.col2Left(8), KeyGrid.rowTop(2), KeyGrid.rightEdge - KeyGrid.col2Left(8), KeyGrid.KEY_H, KeyStyle.FUNCTION, GLYPH_BACKSPACE)

    // ---- row 4 (R6 §2.1.9 widths, §2.8 variants) --------------------------------------------------

    private fun bottomRow(field: FieldInfo, letters: Boolean): List<Key> {
        val top = KeyGrid.rowTop(3)
        val h = KeyGrid.KEY_H
        val keys = mutableListOf<Key>()
        // &123 (or abc on a symbols page) takes shift's place: left margin to the a-column's right edge.
        keys += if (letters) {
            Key("sym", KeyCode.SYMBOLS, "&123", KeyGrid.ROW1_LEFT, top, KeyGrid.col2Right(0) - KeyGrid.ROW1_LEFT, h, KeyStyle.FUNCTION)
        } else {
            Key("abc", KeyCode.LETTERS, "abc", KeyGrid.ROW1_LEFT, top, KeyGrid.col2Right(0) - KeyGrid.ROW1_LEFT, h, KeyStyle.FUNCTION)
        }
        keys += Key("emoji", KeyCode.EMOJI, "", KeyGrid.col2Left(1), top, KeyGrid.ROW2_KEY_W, h, KeyStyle.FUNCTION, GLYPH_EMOJI)
        val enterLeft = KeyGrid.col2Left(8)
        if (field.kind == FieldKind.URL) {
            // R6 §2.8.3 (MEDIUM): ".com" replaces the comma and widens to 1.5 columns; space narrows to
            // three columns less a gap; the period widens to 1.5 columns. On the grid that is 200 / 416
            // / 200, against R6's 201 / 418.6 / 201 (each ± 3).
            val gap = KeyGrid.PITCH - KeyGrid.ROW2_KEY_W
            val dotcomLeft = KeyGrid.col2Left(2)
            val dotcomW = 1.5f * KeyGrid.PITCH - gap
            val spaceLeft = dotcomLeft + dotcomW + gap
            val spaceW = 3f * KeyGrid.PITCH - gap
            val periodLeft = spaceLeft + spaceW + gap
            keys += Key("dotcom", KeyCode.CHAR, ".com", dotcomLeft, top, dotcomW, h, KeyStyle.DARK)
            keys += Key("space", KeyCode.SPACE, " ", spaceLeft, top, spaceW, h, KeyStyle.DARK)
            keys += Key("period", KeyCode.CHAR, ".", periodLeft, top, KeyGrid.col2Right(7) - periodLeft, h, KeyStyle.DARK)
        } else {
            keys += Key("comma", KeyCode.CHAR, ",", KeyGrid.col2Left(2), top, KeyGrid.ROW2_KEY_W, h, KeyStyle.DARK)
            // R6 §2.1.9: space covers the c, v, b and n columns, 560 ± 3.
            keys += Key("space", KeyCode.SPACE, " ", KeyGrid.col2Left(3), top, KeyGrid.col2Right(6) - KeyGrid.col2Left(3), h, KeyStyle.DARK)
            keys += Key("period", KeyCode.CHAR, ".", KeyGrid.col2Left(7), top, KeyGrid.ROW2_KEY_W, h, KeyStyle.DARK)
        }
        keys += enterKey(field, enterLeft, top, KeyGrid.rightEdge - enterLeft, h)
        return keys
    }

    private fun enterKey(field: FieldInfo, left: Float, top: Float, w: Float, h: Float): Key = when {
        field.enterInsertsNewline -> Key("enter", KeyCode.ENTER, "", left, top, w, h, KeyStyle.FUNCTION, GLYPH_ENTER)
        field.kind == FieldKind.URL || field.action == FieldAction.GO ->
            Key("enter", KeyCode.ENTER, "", left, top, w, h, KeyStyle.ACTION_WHITE, GLYPH_ARROW_RIGHT)
        field.kind == FieldKind.SEARCH || field.action == FieldAction.SEARCH ->
            Key("enter", KeyCode.ENTER, "", left, top, w, h, KeyStyle.ACTION_WHITE, GLYPH_SEARCH)
        else -> Key("enter", KeyCode.ENTER, "", left, top, w, h, KeyStyle.FUNCTION, GLYPH_ENTER)
    }

    // ---- symbols (approximation, H2: R6 has no measurement of the symbol pages' contents) ----------

    /** Page 1: digits on top (the page a number field opens on), then the everyday punctuation. */
    private val SYMBOLS_1 = listOf("1234567890".map { "$it" }, listOf("@", "#", "$", "%", "&", "-", "+", "(", ")"), listOf("/", "\"", "'", ":", ";", "!", "?"))

    /** Page 2: the rest of what a W10M symbol page carried. */
    private val SYMBOLS_2 = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
        listOf("£", "€", "¥", "^", "°", "=", "[", "]", "\\"),
        listOf("*", "_", "<", ">", "©", "®", "¢"),
    )

    private fun symbolRows(rows: List<List<String>>, page: String): List<Key> {
        val keys = mutableListOf<Key>()
        rows[0].forEachIndexed { i, s ->
            keys += Key(idFor(s), KeyCode.CHAR, s, KeyGrid.row1Left(i), KeyGrid.rowTop(0), KeyGrid.ROW1_KEY_W, KeyGrid.KEY_H, KeyStyle.DARK)
        }
        rows[1].forEachIndexed { c, s ->
            keys += Key(idFor(s), KeyCode.CHAR, s, KeyGrid.col2Left(c), KeyGrid.rowTop(1), KeyGrid.ROW2_KEY_W, KeyGrid.KEY_H, KeyStyle.DARK)
        }
        keys += shiftKey(KeyCode.PAGE, "page", label = page)
        rows[2].forEachIndexed { i, s ->
            keys += Key(idFor(s), KeyCode.CHAR, s, KeyGrid.col2Left(i + 1), KeyGrid.rowTop(2), KeyGrid.ROW2_KEY_W, KeyGrid.KEY_H, KeyStyle.DARK)
        }
        keys += backspaceKey()
        return keys
    }

    // ---- phone keypad (R6 §2.8.4, LOW; geometry approximation, H8) --------------------------------

    private val PHONE_ROWS = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to ""),
    )

    /**
     * A 3 × 4 keypad with letter sub-labels and a column of function keys on the right (R6 §2.8.4: "with
     * letter sub-labels and a backspace column"). The four columns are 2.5 key pitches each, on the same
     * row tops and key height as the letters, so the keypad fills exactly the space the QWERTY rows did.
     */
    private fun phoneKeypad(field: FieldInfo): List<Key> {
        val keys = mutableListOf<Key>()
        val colPitch = 2.5f * KeyGrid.PITCH
        val gap = KeyGrid.PITCH - KeyGrid.ROW2_KEY_W
        PHONE_ROWS.forEachIndexed { r, row ->
            row.forEachIndexed { c, (digit, sub) ->
                keys += Key(idFor(digit), KeyCode.CHAR, digit, KeyGrid.ROW1_LEFT + c * colPitch, KeyGrid.rowTop(r), colPitch - gap, KeyGrid.KEY_H, KeyStyle.DARK, sub = sub.ifEmpty { null })
            }
        }
        val fnLeft = KeyGrid.ROW1_LEFT + 3 * colPitch
        val fnW = KeyGrid.rightEdge - fnLeft
        keys += Key("bksp", KeyCode.BACKSPACE, "", fnLeft, KeyGrid.rowTop(0), fnW, KeyGrid.KEY_H, KeyStyle.FUNCTION, GLYPH_BACKSPACE)
        keys += Key("space", KeyCode.SPACE, " ", fnLeft, KeyGrid.rowTop(1), fnW, KeyGrid.KEY_H, KeyStyle.DARK)
        keys += Key("sym", KeyCode.SYMBOLS, "&123", fnLeft, KeyGrid.rowTop(2), fnW, KeyGrid.KEY_H, KeyStyle.FUNCTION)
        keys += enterKey(field, fnLeft, KeyGrid.rowTop(3), fnW, KeyGrid.KEY_H)
        return keys
    }

    /** A tag-safe id: letters and digits as themselves, anything else as `u` + its code point in hex. */
    fun idFor(s: String): String =
        if (s.length == 1 && s[0].isLetterOrDigit() && s[0].code < 128) s
        else s.codePoints().toArray().joinToString("_") { "u" + Integer.toHexString(it) }

    /** The horizontal extent of a layout, for the one-handed band. */
    fun extent(layout: Layout): Pair<Float, Float> =
        layout.keys.fold(Float.MAX_VALUE to -Float.MAX_VALUE) { (lo, hi), k -> min(lo, k.left) to max(hi, k.right) }

    // Fluent UI System Icons (MIT, the branding module's MDL2 stand-in), resolved from the bundled font.
    const val GLYPH_SHIFT = ""          // keyboard_shift_20_regular
    const val GLYPH_SHIFT_FILLED = ""   // keyboard_shift_20_filled
    const val GLYPH_BACKSPACE = ""      // backspace_20_regular
    const val GLYPH_ENTER = ""          // arrow_enter_left_20_regular
    const val GLYPH_EMOJI = ""          // emoji_20_regular
    const val GLYPH_ARROW_RIGHT = ""    // arrow_right_20_regular
    const val GLYPH_SEARCH = ""         // search_20_regular
    const val GLYPH_MIC = ""            // mic_20_regular
    const val GLYPH_CLOCK = ""          // clock_20_regular
    const val GLYPH_PEOPLE = ""         // people_20_regular
    const val GLYPH_BALLOON = ""        // balloon_20_regular
    const val GLYPH_PIZZA = ""          // food_pizza_20_regular
    const val GLYPH_CAR = ""            // vehicle_car_20_regular
    const val GLYPH_HEART = ""          // heart_20_regular
    const val GLYPH_SMILE = ""          // emoji_smile_slight_20_regular
    const val GLYPH_CHEVRON_LEFT = ""
    const val GLYPH_CHEVRON_RIGHT = ""
    const val GLYPH_CHEVRON_UP = ""
    const val GLYPH_CHEVRON_DOWN = ""
    const val GLYPH_KEYBOARD = ""       // keyboard_20_regular
    const val GLYPH_ONE_HANDED = ""     // keyboard_layout_one_handed_left_20_regular
    const val GLYPH_MAXIMIZE = ""       // arrow_maximize_20_regular
}

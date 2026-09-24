package app.tileshell.calculator

import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.engine.CalcMode

/**
 * The Calculator's pages: the engine's three modes, Date calculation (T15-16) and the Converter (Q4 A). [id] is the
 * `calc_mode:<id>` tag and the App Shortcuts' `page` extra (build task 9); [title] is the pane row's label
 * (r11/calculator.md "Strings as shipped") and, in caps, the header title (1.6).
 */
enum class CalcPage(val id: String, val title: String) {
    STANDARD("standard", "Standard"),
    SCIENTIFIC("scientific", "Scientific"),
    PROGRAMMER("programmer", "Programmer"),
    DATE("date", "Date calculation"),
    CONVERTER("converter", "Converter");

    /** The engine mode behind the page, or null for Date calculation and the Converter. */
    val mode: CalcMode?
        get() = when (this) {
            STANDARD -> CalcMode.STANDARD
            SCIENTIFIC -> CalcMode.SCIENTIFIC
            PROGRAMMER -> CalcMode.PROGRAMMER
            DATE, CONVERTER -> null
        }

    /** Whether the header carries the History glyph (1.7 as T15-42 re-cut it: Standard and Scientific; 4.15: never Programmer). */
    val hasHistory: Boolean get() = this == STANDARD || this == SCIENTIFIC

    companion object {
        fun fromId(id: String?): CalcPage? = entries.firstOrNull { it.id == id }
    }
}

/** How a key's face is drawn (r11/calculator.md 2.10–2.13, 4.13; gap 10 for [MATH]). */
enum class KeyFace {
    /** A word in the body face: CE, C, sin, Mod, Lsh … */
    TEXT,
    /** A digit, A–F or the decimal point: semibold, the largest face on the pad. */
    DIGIT,
    /** x², ¹⁄x, xʸ, 10ˣ, π, n! … — Selawik text (`Brand.uiFont`), not the icon font (gap 10). */
    MATH,
    ADD, SUBTRACT, MULTIPLY, DIVIDE, EQUALS,
    /** √, drawn (18.5 epx of ink, 2.13). */
    SQRT,
    /** ⌫, the icon font's backspace. */
    BACKSPACE,
    /** ↑, the 2nd-function toggle (4.12, 6.1). */
    SHIFT,
    /** ±, %: text in the body face at the row-1 size. */
    SYMBOL,
}

/**
 * One key of a keypad: its vocabulary [name] (docs/plan/qa/phase-15/calc-keys.md — the `calc_key:<name>` tag), its
 * [label] and [face], and [second], the label it shows while `inv` (↑) is on (UNMEASURED-8: keys with a second
 * function carry a small grey ↑ mark under their label while ↑ is off).
 */
data class KeySpec(val name: String, val label: String, val face: KeyFace = KeyFace.TEXT, val second: String? = null)

/** One row of the hamburger pane (r11/calculator.md §3.9), in order. */
sealed interface PaneRow {
    val label: String

    /** Standard, Scientific, Programmer, Date calculation: a 48-epx row that opens its page. */
    data class Mode(val page: CalcPage) : PaneRow {
        override val label: String get() = page.title
    }

    /** "CONVERTER": the group header in the title style (3.8), not tappable. */
    data object ConverterGroup : PaneRow {
        override val label: String get() = "CONVERTER"
    }

    /** One converter category, opening the Converter on it. */
    data class Category(val category: ConverterCategory) : PaneRow {
        override val label: String get() = category.label
    }
}

/**
 * Every pure mapping the Calculator UI draws from, proven on the host JVM (`CalcLayoutTest`, `CalcCasesLayoutTest`):
 * the key layout of each mode in r11's order (2.8, 4.11–4.12, 6.1), the pane's rows (3.9), the shortcut → page
 * mapping (build task 9) and the labels ↑ swaps in.
 */
object CalcLayout {
    private fun digit(d: String) = KeySpec(d, d, KeyFace.DIGIT)
    private fun word(name: String, label: String, second: String? = null) = KeySpec(name, label, KeyFace.TEXT, second)

    private val CLEAR_ENTRY = word("clear_entry", "CE")
    private val CLEAR = word("clear", "C")
    private val BACKSPACE = KeySpec("backspace", "⌫", KeyFace.BACKSPACE)
    private val DIVIDE = KeySpec("divide", "÷", KeyFace.DIVIDE)
    private val MULTIPLY = KeySpec("multiply", "×", KeyFace.MULTIPLY)
    private val SUBTRACT = KeySpec("subtract", "−", KeyFace.SUBTRACT)
    private val ADD = KeySpec("add", "+", KeyFace.ADD)
    private val EQUALS = KeySpec("equals", "=", KeyFace.EQUALS)
    private val NEGATE = KeySpec("negate", "±", KeyFace.SYMBOL)
    private val DECIMAL = KeySpec("decimal", ".", KeyFace.DIGIT)
    private val SHIFT = KeySpec("inv", "↑", KeyFace.SHIFT)
    private val LPAREN = word("lparen", "(")
    private val RPAREN = word("rparen", ")")

    /** 2.8: `% √ x² ¹⁄x` / `CE C ⌫ ÷` / `7 8 9 ×` / `4 5 6 −` / `1 2 3 +` / `± 0 . =`. */
    val STANDARD: List<List<KeySpec>> = listOf(
        listOf(KeySpec("percent", "%", KeyFace.SYMBOL), KeySpec("sqrt", "√", KeyFace.SQRT), KeySpec("square", "x²", KeyFace.MATH), KeySpec("reciprocal", "¹⁄x", KeyFace.MATH)),
        listOf(CLEAR_ENTRY, CLEAR, BACKSPACE, DIVIDE),
        listOf(digit("7"), digit("8"), digit("9"), MULTIPLY),
        listOf(digit("4"), digit("5"), digit("6"), SUBTRACT),
        listOf(digit("1"), digit("2"), digit("3"), ADD),
        listOf(NEGATE, digit("0"), DECIMAL, EQUALS),
    )

    /**
     * 6.1 (V1, LOW; the key set is the 10586 layout's, what each computes is the source's — calc-keys.md):
     * `x² xʸ sin cos tan` / `√ 10ˣ log Exp Mod` / `↑ CE C ⌫ ÷` / `π 7 8 9 ×` / `n! 4 5 6 −` / `± 1 2 3 +` / `( ) 0 . =`.
     * The second labels follow the engine's `inv` mapping (Calculator.commandsFor): x³, ʸ√x, sin⁻¹ …, ¹⁄x, eˣ, ln, dms, deg.
     */
    val SCIENTIFIC: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("square", "x²", KeyFace.MATH, "x³"), KeySpec("pow", "xʸ", KeyFace.MATH, "ʸ√x"),
            word("sin", "sin", "sin⁻¹"), word("cos", "cos", "cos⁻¹"), word("tan", "tan", "tan⁻¹"),
        ),
        listOf(
            KeySpec("sqrt", "√", KeyFace.SQRT, "¹⁄x"), KeySpec("pow10", "10ˣ", KeyFace.MATH, "eˣ"),
            word("log", "log", "ln"), word("exp", "Exp", "dms"), word("mod", "Mod", "deg"),
        ),
        listOf(SHIFT, CLEAR_ENTRY, CLEAR, BACKSPACE, DIVIDE),
        listOf(KeySpec("pi", "π", KeyFace.MATH), digit("7"), digit("8"), digit("9"), MULTIPLY),
        listOf(KeySpec("factorial", "n!", KeyFace.MATH), digit("4"), digit("5"), digit("6"), SUBTRACT),
        listOf(NEGATE, digit("1"), digit("2"), digit("3"), ADD),
        listOf(LPAREN, RPAREN, digit("0"), DECIMAL, EQUALS),
    )

    /** 4.11–4.12: `Lsh Rsh Or Xor Not And` / `↑ Mod CE C ⌫ ÷` / `A B 7 8 9 ×` / `C D 4 5 6 −` / `E F 1 2 3 +` / `( ) ± 0 . =`. */
    val PROGRAMMER: List<List<KeySpec>> = listOf(
        listOf(word("lsh", "Lsh", "RoL"), word("rsh", "Rsh", "RoR"), word("or", "Or"), word("xor", "Xor"), word("not", "Not"), word("and", "And")),
        listOf(SHIFT, word("mod", "Mod"), CLEAR_ENTRY, CLEAR, BACKSPACE, DIVIDE),
        listOf(digit("A"), digit("B"), digit("7"), digit("8"), digit("9"), MULTIPLY),
        listOf(digit("C"), digit("D"), digit("4"), digit("5"), digit("6"), SUBTRACT),
        listOf(digit("E"), digit("F"), digit("1"), digit("2"), digit("3"), ADD),
        listOf(LPAREN, RPAREN, NEGATE, digit("0"), DECIMAL, EQUALS),
    )

    /** The keypad rows of [mode]. */
    fun rows(mode: CalcMode): List<List<KeySpec>> = when (mode) {
        CalcMode.STANDARD -> STANDARD
        CalcMode.SCIENTIFIC -> SCIENTIFIC
        CalcMode.PROGRAMMER -> PROGRAMMER
    }

    /** How many keypad rows sit on black under the 1-epx #191919 rule (2.5; UNMEASURED-2: two in Scientific; 4.11). */
    fun blackRows(mode: CalcMode): Int = if (mode == CalcMode.SCIENTIFIC) 2 else 1

    /** The keypad's columns: W/4, W/5, W/6 (2.7, UNMEASURED-2, 4.12). */
    fun columns(mode: CalcMode): Int = rows(mode)[0].size

    /** The memory row's keys in order (2.16): MC MR M+ M- MS, then M˅ (`mlist`, the memory flyout). */
    val MEMORY_ROW: List<Pair<String, String>> = listOf("mc" to "MC", "mr" to "MR", "mplus" to "M+", "mminus" to "M-", "ms" to "MS")
    const val MEMORY_LIST_KEY = "mlist"

    /** The Scientific angle row's keys (6.1): the angle unit (its label follows the unit), HYP, F-E. */
    val ANGLE_ROW: List<Pair<String, String>> = listOf("angle" to "DEG", "hyp" to "HYP", "fe" to "F-E")

    /** The Programmer radix rows (4.3), top to bottom. */
    val RADIX_ROWS: List<Pair<String, String>> = listOf("radix_hex" to "HEX", "radix_dec" to "DEC", "radix_oct" to "OCT", "radix_bin" to "BIN")

    /**
     * Every `calc_key:<name>` the page of [mode] carries: the keypad, the memory row and the mode's own controls
     * (angle / HYP / F-E; the radix rows, the word-size button and MS in Programmer). The oracle's driver (E11)
     * presses cases from these, so `CalcCasesLayoutTest` checks every case's keys against this list.
     */
    fun keyNames(mode: CalcMode): List<String> {
        val pad = rows(mode).flatten().map { it.name }
        return when (mode) {
            CalcMode.STANDARD -> pad + MEMORY_ROW.map { it.first }
            CalcMode.SCIENTIFIC -> ANGLE_ROW.map { it.first } + MEMORY_ROW.map { it.first } + pad
            CalcMode.PROGRAMMER -> RADIX_ROWS.map { it.first } + listOf("word", "ms") + pad
        }
    }

    /** 3.9: Standard, Scientific, Programmer, Date calculation, CONVERTER, then the twelve categories in pane order. */
    val PANE_ROWS: List<PaneRow> =
        listOf(CalcPage.STANDARD, CalcPage.SCIENTIFIC, CalcPage.PROGRAMMER, CalcPage.DATE).map { PaneRow.Mode(it) } +
            PaneRow.ConverterGroup +
            ConverterCategory.entries.map { PaneRow.Category(it) }

    /** Build task 9: the shortcut ids in rank order, each the `page` extra its intent carries. */
    val SHORTCUT_IDS: List<String> = listOf("standard", "scientific", "programmer", "converter")

    /** The page a `page` extra opens; null for an absent or unknown value (the app then opens on Standard). */
    fun pageForShortcut(extra: String?): CalcPage? = extra?.let { CalcPage.fromId(it) }?.takeIf { it.id in SHORTCUT_IDS }

    /** The page the app opens on with no extra. */
    val DEFAULT_PAGE: CalcPage = CalcPage.STANDARD
}

/**
 * 7.4–7.5 (H11): the result is drawn at 46 epx and shrinks to fit its row's width, never scrolling or truncating,
 * down to a 12-epx floor (S1 Calculator.xaml MinFontSize 12).
 */
object CalcDisplayFit {
    const val MAX_FONT = 46f
    const val MIN_FONT = 12f

    /** The font size for a result whose width at [MAX_FONT] is [widthAtMax] inside [available] epx. */
    fun fontSize(widthAtMax: Float, available: Float): Float {
        if (widthAtMax <= 0f || available <= 0f) return MAX_FONT
        if (widthAtMax <= available) return MAX_FONT
        return (MAX_FONT * available / widthAtMax).coerceIn(MIN_FONT, MAX_FONT)
    }
}

/** "0 years, 1 month, 0 days" — the amount field's text, in the engine's own plural forms. */
fun amountText(years: Int, months: Int, days: Int): String =
    "$years ${if (years == 1) "year" else "years"}, $months ${if (months == 1) "month" else "months"}, $days ${if (days == 1) "day" else "days"}"

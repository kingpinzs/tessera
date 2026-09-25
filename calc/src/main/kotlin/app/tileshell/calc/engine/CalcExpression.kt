package app.tileshell.calc.engine

/**
 * The expression evaluator behind Tess's arithmetic (phase 15 T15-2): canonical infix with `+ - * / ^`, unary minus,
 * parentheses and `sqrt(x)` ("a percent of b" arrives as `b * a / 100`), evaluated by the SAME engine the Calculator app
 * uses, in Scientific mode (precedence, 32 digits) with DEG angles: the expression is turned into the key presses a
 * person would make and pressed on a private [Calculator], so the result is exactly the display string Windows would
 * show — grouped, e-notation past the precision, and the Windows error strings. Never a Double.
 */
object CalcExpression {
    /** [display] is the display text (a number or a Windows error string); [error] the error kind when it is an error. */
    data class Result(val display: String, val error: CalcErrorKind?) {
        val isError: Boolean get() = error != null
    }

    private sealed class Tok {
        class Num(val digits: String) : Tok()
        class Op(val ch: Char) : Tok()
        object LParen : Tok()
        object RParen : Tok()
        object Sqrt : Tok()
    }

    /**
     * Evaluates [expression]. Text that is not an expression of the grammar (an unknown word, unbalanced parentheses, a
     * missing operand) is "Invalid input", the string Windows shows for text it cannot take (CalculatorManager.cpp
     * DisplayPasteError: CALC_E_DOMAIN).
     */
    fun evaluate(expression: String): Result {
        val keys = try {
            toKeys(tokenize(expression))
        } catch (e: IllegalArgumentException) {
            return Result(CalcErrorKind.INVALID_INPUT.message, CalcErrorKind.INVALID_INPUT)
        }
        val calc = Calculator(CalcMode.SCIENTIFIC)
        for (k in keys) {
            if (!calc.press(k)) {
                // A key the engine refused (a 33rd digit, a second decimal point): the text is not a valid number.
                return Result(CalcErrorKind.INVALID_INPUT.message, CalcErrorKind.INVALID_INPUT)
            }
            if (calc.isError) break
        }
        return Result(calc.displayText, calc.errorKind)
    }

    /** The key presses [expression] becomes (the vocabulary of docs/plan/qa/phase-15/calc-keys.md), for diagnostics. */
    fun keysFor(expression: String): List<String> = toKeys(tokenize(expression))

    private fun tokenize(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val digits = s.substring(start, i)
                    require(digits.count { it == '.' } <= 1 && digits.any { it.isDigit() }) { "bad number $digits" }
                    out.add(Tok.Num(digits))
                }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' -> {
                    out.add(Tok.Op(c))
                    i++
                }
                c == '×' -> { out.add(Tok.Op('*')); i++ }
                c == '÷' -> { out.add(Tok.Op('/')); i++ }
                c == '−' -> { out.add(Tok.Op('-')); i++ }
                c == '(' -> { out.add(Tok.LParen); i++ }
                c == ')' -> { out.add(Tok.RParen); i++ }
                c == '√' -> { out.add(Tok.Sqrt); i++ }
                c.isLetter() -> {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    val word = s.substring(start, i)
                    require(word.equals("sqrt", ignoreCase = true)) { "unknown word $word" }
                    out.add(Tok.Sqrt)
                }
                else -> throw IllegalArgumentException("unexpected '$c'")
            }
        }
        return out
    }

    /**
     * A recursive-descent parse that emits keys as it goes. Binary operators are pressed between their operands and the
     * engine applies Scientific precedence (`^` above `* /` above `+ -`, scicomm.cpp NPrecedenceOfOp), so the emitted
     * order is the source order; the parser only checks the grammar and decides where `negate` and `sqrt` go: a negated
     * number is typed then negated (`3 negate` is -3), a negated group is closed then negated, `sqrt(x)` types x (in
     * parentheses when it is an expression) then presses `sqrt`. Everything ends with `equals`.
     */
    private fun toKeys(tokens: List<Tok>): List<String> = Parser(tokens).parse()

    private class Parser(private val tokens: List<Tok>) {
        private val keys = ArrayList<String>()
        private var pos = 0

        fun parse(): List<String> {
            require(tokens.isNotEmpty()) { "empty" }
            expr()
            require(pos == tokens.size) { "trailing input" }
            keys.add("equals")
            return keys
        }

        private fun peek(): Tok? = tokens.getOrNull(pos)
        private fun next(): Tok = tokens.getOrNull(pos++) ?: throw IllegalArgumentException("unexpected end")

        private fun operand() {
            when (val t = next()) {
                is Tok.Num -> {
                    val intPart = t.digits.substringBefore('.')
                    val fracPart = if ('.' in t.digits) t.digits.substringAfter('.') else null
                    for (d in intPart) keys.add(d.toString())
                    if (fracPart != null) {
                        if (intPart.isEmpty()) keys.add("0")
                        keys.add("decimal")
                        for (d in fracPart) keys.add(d.toString())
                    }
                }
                is Tok.LParen -> {
                    keys.add("lparen")
                    expr()
                    require(next() is Tok.RParen) { "missing )" }
                    keys.add("rparen")
                }
                is Tok.Sqrt -> {
                    // sqrt(x): a single number is typed then rooted; anything else is rooted as a parenthesised group.
                    require(next() is Tok.LParen) { "sqrt needs (" }
                    val single = peek() is Tok.Num && tokens.getOrNull(pos + 1) is Tok.RParen
                    if (single) {
                        operand()
                    } else {
                        keys.add("lparen")
                        expr()
                        keys.add("rparen")
                    }
                    require(next() is Tok.RParen) { "missing )" }
                    keys.add("sqrt")
                }
                is Tok.Op -> {
                    require(t.ch == '-' || t.ch == '+') { "operand expected" }
                    operand()
                    if (t.ch == '-') keys.add("negate")
                }
                else -> throw IllegalArgumentException("operand expected")
            }
        }

        private fun expr() {
            operand()
            while (true) {
                val t = peek()
                if (t !is Tok.Op) break
                pos++
                keys.add(
                    when (t.ch) {
                        '+' -> "add"
                        '-' -> "subtract"
                        '*' -> "multiply"
                        '/' -> "divide"
                        else -> "pow"
                    },
                )
                operand()
            }
        }
    }
}

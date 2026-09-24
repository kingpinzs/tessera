package app.tileshell.cortana.action

import app.tileshell.calc.convert.ConverterState
import app.tileshell.calc.engine.CalcExpression
import app.tileshell.cortana.match.CalcRequest

/**
 * Tess's arithmetic answered by the Calculator's own engine (phase 15 T15-2, principle P6; the reply rule T15-52).
 *
 * The expression goes to [CalcExpression] — the same engine code the Calculator app runs, with Scientific precedence
 * and DEG angles — so Tess and Calculator can never disagree; a conversion goes through the Converter's own
 * [ConverterState]. The reply restates the request: every number as the engine displays it (its literal typed into
 * Scientific: "1000" reads "1,000"), the operator words the user said, "percent" as "%" and "square root of <a>" as
 * "√<a>"; a conversion keeps the number as said and the units' en-US names in lower case. An engine error is spoken as
 * Windows' own string ("Cannot divide by zero.").
 *
 * Pure: the host JVM checks it against the tess column of the independent oracle (calc-cases.tsv).
 */
object TessArithmetic {
    /** What Tess says, and what the diagnostics and the card show. */
    data class Answer(val spoken: String, val logExpr: String, val logResult: String, val said: String, val result: String?)

    fun answer(expr: CalcRequest): Answer = when (expr) {
        is CalcRequest.Convert -> convert(expr)
        is CalcRequest.Percent -> {
            // "<a> percent of <b>" is b × a / 100 (T15-2).
            val infix = "${infix(expr.of)} * ${infix(expr.percent)} / 100"
            finish(infix, "${text(expr.percent)} % of ${text(expr.of)}", "${log(expr.percent)} % of ${log(expr.of)}")
        }
        is CalcRequest.Chain -> {
            val infix = StringBuilder(infix(expr.terms[0]))
            val said = StringBuilder(text(expr.terms[0]))
            val logged = StringBuilder(log(expr.terms[0]))
            expr.ops.forEachIndexed { i, op ->
                infix.append(' ').append(op.op.symbol).append(' ').append(infix(expr.terms[i + 1]))
                said.append(' ').append(op.words).append(' ').append(text(expr.terms[i + 1]))
                logged.append(' ').append(op.op.symbol).append(' ').append(log(expr.terms[i + 1]))
            }
            finish(infix.toString(), said.toString(), logged.toString())
        }
    }

    private fun finish(infix: String, said: String, logExpr: String): Answer {
        val r = CalcExpression.evaluate(infix)
        return if (r.isError) {
            Answer("${r.display}.", logExpr, "error ${r.error?.logName ?: "?"}", said, null)
        } else {
            Answer("$said is ${r.display}.", logExpr, r.display, said, r.display)
        }
    }

    private fun convert(c: CalcRequest.Convert): Answer {
        val state = ConverterState()
        state.selectCategory(c.from.category)
        state.selectUnit1(state.units.first { it.id == c.from.id })
        state.selectUnit2(state.units.first { it.id == c.to.id })
        for (ch in c.value) {
            when (ch) {
                '-' -> state.press("negate")
                '.' -> state.press("decimal")
                else -> state.press(ch.toString())
            }
        }
        val result = state.toText
        val said = "${c.value} ${c.from.name.lowercase()}"
        return Answer(
            "$said is $result ${c.to.name.lowercase()}.",
            "${c.value} ${c.from.name} -> ${c.to.name}",
            "$result ${c.to.name}",
            said,
            result,
        )
    }

    /** A term for the engine: a literal, or sqrt( … ). */
    private fun infix(t: CalcRequest.Term): String = when (t) {
        is CalcRequest.Num -> if (t.literal.startsWith("-")) "(${t.literal})" else t.literal
        is CalcRequest.Root -> "sqrt(${infix(t.of)})"
    }

    /** A term as the reply says it: the engine's display of the literal, "√" before a root. */
    private fun text(t: CalcRequest.Term): String = when (t) {
        is CalcRequest.Num -> CalcExpression.evaluate(t.literal).display
        is CalcRequest.Root -> "√" + text(t.of)
    }

    /** A term as the diagnostics line writes it: the literal, "√" before a root. */
    private fun log(t: CalcRequest.Term): String = when (t) {
        is CalcRequest.Num -> t.literal
        is CalcRequest.Root -> "√" + log(t.of)
    }
}

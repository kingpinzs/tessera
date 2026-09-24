package app.tileshell.cortana.match

import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.convert.ConverterUnit
import app.tileshell.calc.convert.UnitTables

/**
 * Tess's arithmetic, as words (phase 15 T15-2, interview Q5 A, principle P6: anything deterministic is computed by
 * code). "what's / what is / calculate / how much is <expr>", a bare "<a> plus | minus | times | divided by |
 * percent of | to the power of <b>", and "square root of <a>" — matched ONLY when the whole of <expr> is numbers and
 * operator words, so "what is the capital of Peru" still reaches the not-understood handler.
 *
 * What it produces is structure, not a string: the Calculator engine evaluates it with Scientific precedence and DEG
 * angles, and the reply restates it with the operator words the user said (T15-52).
 */
sealed interface CalcRequest {
    /** `terms[0] ops[0] terms[1] …`, evaluated with Scientific precedence ("2 plus 3 times 4" is 14). */
    data class Chain(val terms: List<Term>, val ops: List<Said>) : CalcRequest

    /** "<a> percent of <b>": b × a / 100 (T15-2). */
    data class Percent(val percent: Term, val of: Term) : CalcRequest

    /**
     * "<n> <unit> in | to <unit>", through the Converter (Q4 A): [from] and [to] are the converter's own units (one
     * category), [value] the number as the user said it.
     */
    data class Convert(val value: String, val from: ConverterUnit, val to: ConverterUnit) : CalcRequest

    sealed interface Term
    /** A decimal literal: "-3", "0.5", "81". */
    data class Num(val literal: String) : Term
    /** √ of a term. */
    data class Root(val of: Term) : Term

    enum class Op(val symbol: String) { PLUS("+"), MINUS("-"), TIMES("*"), DIVIDE("/"), POWER("^") }

    /** An operator and the words that said it ("times" and "multiplied by" are both TIMES; the reply keeps the words). */
    data class Said(val op: Op, val words: String)
}

object ArithmeticWords {
    private val PREFIXES = listOf("what's ", "whats ", "what is ", "calculate ", "how much is ", "what does ", "compute ")

    /** Operator phrases, longest first so "to the power of" wins over any shorter overlap. */
    private val OPS: List<Pair<List<String>, CalcRequest.Op>> = listOf(
        "raised to the power of" to CalcRequest.Op.POWER,
        "to the power of" to CalcRequest.Op.POWER,
        "multiplied by" to CalcRequest.Op.TIMES,
        "divided by" to CalcRequest.Op.DIVIDE,
        "times" to CalcRequest.Op.TIMES,
        "over" to CalcRequest.Op.DIVIDE,
        "plus" to CalcRequest.Op.PLUS,
        "minus" to CalcRequest.Op.MINUS,
    ).map { (w, op) -> w.split(' ') to op }

    private val ROOT = listOf("the square root of", "square root of").map { it.split(' ') }

    private val PER_CENT = Regex("\\bper cent\\b")

    /** The arithmetic in [text] (the matcher's normalised text), or null when it is not wholly arithmetic. */
    fun parse(text: String): CalcRequest? {
        var body = text
        PREFIXES.firstOrNull { body.startsWith(it) }?.let { body = body.removePrefix(it) }
        body = body.removeSuffix(" equal").removeSuffix(" equals").trim()
        // The recogniser writes "per cent" as two words (E26 run 3: "WHAT'S FIFTEEN PER CENT OF EIGHTY"); it is "percent".
        body = PER_CENT.replace(body, "percent")
        val words = body.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        conversion(words)?.let { return it }
        // "<a> percent of <b>" is the whole expression or nothing.
        val pct = words.indexOf("percent")
        if (pct > 0 && words.getOrNull(pct + 1) == "of") {
            val a = term(words, 0, true) ?: return null
            val b = term(words, pct + 2, true) ?: return null
            if (a.second != pct || pct + 2 + b.second != words.size) return null
            return CalcRequest.Percent(a.first, b.first)
        }
        val terms = mutableListOf<CalcRequest.Term>()
        val ops = mutableListOf<CalcRequest.Said>()
        var i = 0
        var first = true
        while (true) {
            val t = term(words, i, first) ?: return null
            terms += t.first
            i += t.second
            first = false
            if (i == words.size) break
            val op = OPS.firstOrNull { (phrase, _) -> words.subList(i, minOf(words.size, i + phrase.size)) == phrase } ?: return null
            ops += CalcRequest.Said(op.second, op.first.joinToString(" "))
            i += op.first.size
            if (i == words.size) return null
        }
        // A bare number is not a sum ("what is five"): there must be an operator or a root.
        if (ops.isEmpty() && terms.single() !is CalcRequest.Root) return null
        return CalcRequest.Chain(terms, ops)
    }

    /**
     * "<number> <unit> in | to | into <unit>", both units from the converter's own tables and of one category. A unit
     * the converter lacks ("5 parsecs in miles") or two categories ("5 miles in kilograms") is not a conversion, so the
     * request reaches the not-understood handler (edge cases).
     */
    private fun conversion(words: List<String>): CalcRequest.Convert? {
        val n = NumberWords.read(words, 0, setOf("negative", "minus")) ?: return null
        var i = n.used
        val from = unitAt(words, i) ?: return null
        i += from.second
        if (words.getOrNull(i) !in CONNECTORS) return null
        i++
        val to = unitAt(words, i) ?: return null
        if (i + to.second != words.size) return null
        val pair = from.first.flatMap { f -> to.first.filter { it.category == f.category && it.id != f.id }.map { f to it } }.firstOrNull()
            ?: return null
        return CalcRequest.Convert(n.literal, pair.first, pair.second)
    }

    private val CONNECTORS = setOf("in", "to", "into")

    /** The longest unit phrase at [from]: the units it can name (a phrase may name units in more than one category). */
    private fun unitAt(words: List<String>, from: Int): Pair<List<ConverterUnit>, Int>? {
        for (len in minOf(4, words.size - from) downTo 1) {
            val phrase = words.subList(from, from + len).joinToString(" ")
            UNIT_PHRASES[phrase]?.let { return it to len }
        }
        return null
    }

    /**
     * Every phrase that names a converter unit: its en-US name in lower case (the table's plural, "kilometers"), without
     * a parenthetical ("teaspoons (us)" → "teaspoons"), its singular ("kilometer", "foot", "inch") and the British
     * spellings ("kilometres", "litres"). From the converter's own tables, so Tess and Calculator never disagree.
     */
    private val UNIT_PHRASES: Map<String, List<ConverterUnit>> by lazy {
        val map = HashMap<String, MutableList<ConverterUnit>>()
        for (category in ConverterCategory.entries) {
            for (unit in UnitTables.pickerUnits(category)) {
                val base = unit.name.lowercase().replace(Regex("\\s*\\(.*?\\)"), "").replace("-", " ").trim()
                val forms = LinkedHashSet<String>()
                forms += base
                forms += singular(base)
                for (f in forms.toList()) {
                    forms += f.replace("meter", "metre").replace("liter", "litre")
                }
                for (f in forms) map.getOrPut(f) { mutableListOf() }.let { if (unit !in it) it += unit }
            }
        }
        map
    }

    private fun singular(plural: String): String {
        val words = plural.split(' ')
        val last = words.last()
        val one = when {
            last == "feet" -> "foot"
            last.endsWith("inches") -> last.removeSuffix("es")
            last.endsWith("ches") || last.endsWith("shes") -> last.removeSuffix("es")
            last.endsWith("s") && !last.endsWith("ss") -> last.removeSuffix("s")
            else -> last
        }
        return (words.dropLast(1) + one).joinToString(" ")
    }

    /** A term at [from]: a number, or "square root of" a term. [first]: a leading "minus" is a sign there. */
    private fun term(words: List<String>, from: Int, first: Boolean): Pair<CalcRequest.Term, Int>? {
        ROOT.firstOrNull { words.subList(from, minOf(words.size, from + it.size)) == it }?.let { phrase ->
            val inner = term(words, from + phrase.size, true) ?: return null
            return CalcRequest.Root(inner.first) to phrase.size + inner.second
        }
        val signs = if (first) setOf("negative", "minus") else setOf("negative")
        val n = NumberWords.read(words, from, signs) ?: return null
        return CalcRequest.Num(n.literal) to n.used
    }
}

package app.tileshell.cortana.match

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

    /** "<n> <unit> in | to <unit>", through the Converter (Q4 A); the unit names as the user said them. */
    data class Convert(val value: String, val from: String, val to: String) : CalcRequest

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

    /** The arithmetic in [text] (the matcher's normalised text), or null when it is not wholly arithmetic. */
    fun parse(text: String): CalcRequest? {
        var body = text
        PREFIXES.firstOrNull { body.startsWith(it) }?.let { body = body.removePrefix(it) }
        body = body.removeSuffix(" equal").removeSuffix(" equals").trim()
        val words = body.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
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

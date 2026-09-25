package app.tileshell.cortana.match

/**
 * Spoken numbers to digits for Tess's arithmetic (phase 15 T15-2), beside [TimeWords]. The recogniser writes
 * words ("eighty one", "point five", "negative three"), a typed request may use digits, and both reach here as
 * the matcher's normalised lower-case words.
 *
 * A number is: an optional sign word ("negative"), then either digit tokens or English number words up to the
 * billions ("one hundred twenty three thousand four hundred five"), then an optional "point" followed by single
 * digits ("three point one four"). The result is a plain decimal literal ("-3", "0.5", "123405") — exact, never a
 * Double — for the Calculator engine to read.
 */
object NumberWords {
    private val UNITS = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90,
    )
    private val SCALES = mapOf("thousand" to 1_000L, "million" to 1_000_000L, "billion" to 1_000_000_000L)

    /** A number read from [words] starting at [from]: its literal and how many words it used. */
    data class Read(val literal: String, val used: Int)

    fun isNumberWord(w: String): Boolean =
        w.all { it.isDigit() } || w in UNITS || w in TENS || w == "hundred" || w in SCALES || w == "point"

    /**
     * Reads the longest number at [from]. [signWords] are the words that make it negative there ("negative"
     * always; the caller adds "minus" where a binary minus cannot stand, e.g. at the start of the expression).
     */
    fun read(words: List<String>, from: Int, signWords: Set<String> = setOf("negative")): Read? {
        var i = from
        var negative = false
        if (i < words.size && words[i] in signWords) { negative = true; i++ }
        val start = i
        // Digit tokens: "15", or "1000".
        var whole: String? = null
        if (i < words.size && words[i].isNotEmpty() && words[i].all { it.isDigit() }) {
            whole = words[i].trimStart('0').ifEmpty { "0" }
            i++
        } else {
            var total = 0L
            var group = 0L
            var any = false
            while (i < words.size) {
                val w = words[i]
                when {
                    w in UNITS -> { group += UNITS.getValue(w); any = true }
                    w in TENS -> { group += TENS.getValue(w); any = true }
                    w == "hundred" && any -> group = (if (group == 0L) 1 else group) * 100
                    w in SCALES && any -> { total += (if (group == 0L) 1 else group) * SCALES.getValue(w); group = 0 }
                    else -> break
                }
                i++
            }
            if (any) whole = (total + group).toString()
        }
        // "point five", "three point one four": a decimal point followed by single digits.
        var fraction = ""
        if (i < words.size && words[i] == "point") {
            var j = i + 1
            while (j < words.size) {
                val d = words[j]
                val digit = when {
                    d.length == 1 && d[0].isDigit() -> d
                    d.all { it.isDigit() } && d.isNotEmpty() -> d
                    UNITS[d]?.takeIf { it <= 9 } != null -> UNITS.getValue(d).toString()
                    else -> null
                } ?: break
                fraction += digit
                j++
            }
            if (fraction.isNotEmpty()) i = j
        }
        if (whole == null && fraction.isEmpty()) return null
        if (i == start) return null
        val literal = (if (negative) "-" else "") + (whole ?: "0") + (if (fraction.isNotEmpty()) ".$fraction" else "")
        return Read(literal, i - from)
    }
}

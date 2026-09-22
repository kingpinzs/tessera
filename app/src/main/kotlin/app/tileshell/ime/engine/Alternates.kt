package app.tileshell.ime.engine

/**
 * The long-press alternates for English (US) (phase 05 Decisions stand-in (3), H19: "an accent popup
 * … with one cell per character at the key pitch, the plain letter first over the pressed key … a
 * letter with no alternates shows only the normal press popup").
 *
 * The table is the accented forms an English typist reaches for in loan words and names (café, naïve,
 * jalapeño, Zoë, Škoda) plus the ligatures and ß, in the order the common US keyboards use, so the
 * nearest cell is the likeliest. W10M's own table is unmeasured (approximation, H19). The period and
 * comma keys carry punctuation the letter rows have no key for.
 *
 * Shifted, every variant is uppercased; a variant with no single-character uppercase (ß → "SS") is
 * left out rather than shown as two characters in one cell.
 */
object Alternates {

    private val table: Map<Char, String> = mapOf(
        'a' to "àáâäæãåā",
        'c' to "çćč",
        'e' to "èéêëēėę",
        'i' to "îïíīįì",
        'l' to "ł",
        'n' to "ñń",
        'o' to "ôöòóœøōõ",
        's' to "ßśš",
        'u' to "ûüùúū",
        'y' to "ÿý",
        'z' to "žźż",
        '.' to ",?!:;'\"-",
        ',' to "?!;:'\"",
    )

    /**
     * The cells for a long press on [key]: the key's own character first, then its alternates; just
     * the key itself when it has none.
     */
    fun of(key: Char, shifted: Boolean = false): List<Char> {
        val base = Character.toLowerCase(key)
        val own = if (shifted) Character.toUpperCase(base) else base
        val variants = table[base] ?: return listOf(own)
        val cells = ArrayList<Char>(variants.length + 1)
        cells.add(own)
        for (v in variants) {
            if (!shifted) {
                cells.add(v)
            } else {
                val upper = v.toString().uppercase()
                if (upper.length == 1) cells.add(upper[0])
            }
        }
        return cells
    }

    /** Whether a long press on [key] opens the alternates popup at all (stand-in (3): no cells, no popup). */
    fun has(key: Char): Boolean = table.containsKey(Character.toLowerCase(key))
}

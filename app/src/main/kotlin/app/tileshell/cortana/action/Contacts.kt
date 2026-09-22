package app.tileshell.cortana.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import app.tileshell.diag.Diagnostics

/**
 * Contacts lookup for "call <contact>", "text <contact>" and person reminders (Decisions: calls and
 * texts resolve contacts through the Contacts provider).
 */
object Contacts {

    /** One contact's number, with the label W10M read back ("mobile"). */
    data class Number(val number: String, val label: String)

    data class Match(val lookupKey: String, val displayName: String, val numbers: List<Number>) {
        /**
         * The number a text or a call uses. W10M read back one number with its label; Android's own
         * "primary" flag (`IS_SUPER_PRIMARY`) is what the user picked last, so it wins, then a mobile
         * number, then the first.
         */
        val preferred: Number? get() = numbers.firstOrNull()
    }

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Contacts whose display name matches [name]. An exact name (ignoring case) wins over a prefix
     * match, so "Mom" does not resolve to "Mommy" while both exist; two contacts with the same name come
     * back as two matches for the caller to disambiguate (edge case).
     */
    fun byName(context: Context, name: String): List<Match> {
        val exact = lookup(context, name)
        if (exact.isNotEmpty()) return exact
        // The recogniser mishears a short name more often than it mishears a command: on this build
        // "Text Mom" comes back as "TEXT MAM" and "Call Mom" as "CALL MA'AMS", on the device AND on the
        // host, with "Mom" sitting in the grammar pass's hotwords at a raised boost. Without a tolerant
        // lookup the whole ruled "call or text a contact" command is unusable with the shipped engine,
        // so a spoken name that no contact matches exactly is compared against the contacts this phone
        // actually has. Only a SINGLE close match counts: two near-misses are an ambiguity, not a guess.
        // Agent call, its own NEEDS-HUMAN row (H31).
        val near = nearestName(name, allNames(context, NEAR_MATCH_SCAN))
        if (near != null) {
            Diagnostics.add("contacts", "\"$name\" matched no contact exactly; nearest is \"$near\"")
            return lookup(context, near)
        }
        Diagnostics.add("contacts", "\"$name\" matched no contact, exactly or nearly")
        return emptyList()
    }

    /**
     * The one contact name closest to what was heard, or null when nothing is close enough or two are
     * equally close. Pure, so every pairing is pinned by a unit test rather than by a lucky utterance.
     */
    fun nearestName(spoken: String, names: List<String>): String? {
        val heard = letters(spoken)
        if (heard.isBlank() || names.isEmpty()) return null
        val scored = names
            .map { it to similarity(heard, letters(it)) }
            .filter { (candidate, score) ->
                // Two ways in. Plain closeness catches a dropped or swapped letter ("Sara" for
                // "Sarah"). Sounding the same catches the error this engine actually makes: "MA'AM"
                // is only 0.4 away from "Mom" by letters but is the same sound, and a homophone is
                // precisely what a speech recogniser gets wrong.
                score >= NEAR_MATCH_THRESHOLD ||
                    (soundex(heard) == soundex(letters(candidate)) && score >= SOUNDEX_FLOOR)
            }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return null
        if (scored.size > 1 && scored[0].second == scored[1].second) return null
        return scored[0].first
    }

    /** Letters only, lower case: the recogniser writes "MA'AM", and the apostrophe is not a sound. */
    private fun letters(value: String): String = value.lowercase().filter { it.isLetter() }

    /**
     * Soundex, the classic English sound code: same first letter, same consonant classes. It is here to
     * catch homophones, which is the error class a speech recogniser produces — not to be clever.
     */
    fun soundex(value: String): String {
        val word = value.lowercase().filter { it.isLetter() }
        if (word.isEmpty()) return ""
        fun code(c: Char): Char = when (c) {
            'b', 'f', 'p', 'v' -> '1'
            'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'
            'd', 't' -> '3'
            'l' -> '4'
            'm', 'n' -> '5'
            'r' -> '6'
            else -> '0'
        }
        val out = StringBuilder().append(word[0].uppercaseChar())
        var previous = code(word[0])
        for (c in word.drop(1)) {
            val digit = code(c)
            if (digit != '0' && digit != previous) out.append(digit)
            // h and w do not break a run of the same code; a vowel does.
            if (c != 'h' && c != 'w') previous = digit
            if (out.length == 4) break
        }
        return out.padEnd(4, '0').toString()
    }

    /** 1.0 for identical, 0.0 for nothing in common: edit distance over the longer string's length. */
    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val longer = maxOf(a.length, b.length)
        if (longer == 0) return 1f
        return 1f - editDistance(a, b).toFloat() / longer
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current
        }
        return previous[b.length]
    }

    /** A name has to be at least this close to be taken as the one that was meant. */
    const val NEAR_MATCH_THRESHOLD = 0.6f

    /** How close a name that SOUNDS the same still has to be, so a shared code alone is not enough. */
    const val SOUNDEX_FLOOR = 0.4f

    /** How many contacts the near-match scan reads. */
    private const val NEAR_MATCH_SCAN = 500

    private fun lookup(context: Context, name: String): List<Match> {
        if (!granted(context) || name.isBlank()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name.trim()))
        val found = linkedMapOf<String, Pair<String, MutableList<Number>>>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val key = cursor.getString(0) ?: continue
                    found.getOrPut(key) { cursor.getString(1).orEmpty() to mutableListOf() }
                }
            }
        }.onFailure { Diagnostics.add("contacts", "name lookup failed: $it") }
        found.forEach { (key, value) -> value.second += numbersFor(context, key) }
        val matches = found.map { (key, value) -> Match(key, value.first, value.second) }
        val exact = matches.filter { it.displayName.equals(name.trim(), ignoreCase = true) }
        val result = exact.ifEmpty { matches }
        Diagnostics.add("contacts", "name \"$name\" -> ${result.size} match(es): ${result.joinToString { "${it.displayName}(${it.numbers.size} numbers)" }}")
        return result
    }

    /**
     * Every contact's display name, for the grammar pass's boosted phrases. Bounded by [limit] because
     * the hotwords file is built on every listen and a 5,000-contact phone would make it enormous.
     */
    fun allNames(context: Context, limit: Int): List<String> {
        if (!granted(context)) return emptyList()
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1", null,
                "${ContactsContract.Contacts.TIMES_CONTACTED} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }.orEmpty()
        }.onFailure { Diagnostics.add("contacts", "name list failed: $it") }.getOrDefault(emptyList())
    }

    fun byLookupKey(context: Context, lookupKey: String): Match? {
        if (!granted(context)) return null
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)
                ?.use { if (it.moveToFirst()) name = it.getString(0) }
        }.onFailure { Diagnostics.add("contacts", "lookup-key read failed: $it") }
        return name?.let { Match(lookupKey, it, numbersFor(context, lookupKey)) }
    }

    /** The contact a phone number belongs to, whichever of its numbers it is (person reminders). */
    fun lookupKeyForNumber(context: Context, number: String): String? {
        if (!granted(context) || number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.onFailure { Diagnostics.add("contacts", "phone lookup failed: $it") }.getOrNull()
    }

    private fun numbersFor(context: Context, lookupKey: String): List<Number> {
        val numbers = mutableListOf<Triple<Number, Boolean, Boolean>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ),
                "${ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY} = ?", arrayOf(lookupKey), null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(0) ?: continue
                    val type = cursor.getInt(1)
                    val label = ContactsContract.CommonDataKinds.Phone
                        .getTypeLabel(context.resources, type, cursor.getString(2).orEmpty()).toString().lowercase()
                    numbers += Triple(
                        Number(number, label),
                        cursor.getInt(3) != 0,
                        type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    )
                }
            }
        }.onFailure { Diagnostics.add("contacts", "number read failed: $it") }
        return numbers
            .sortedWith(compareByDescending<Triple<Number, Boolean, Boolean>> { it.second }.thenByDescending { it.third })
            .map { it.first }
    }
}

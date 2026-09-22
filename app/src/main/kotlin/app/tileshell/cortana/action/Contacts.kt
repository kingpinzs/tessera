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

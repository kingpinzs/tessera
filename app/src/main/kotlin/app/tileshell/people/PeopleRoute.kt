package app.tileshell.people

/** People's App Shortcut pages (build task 9; the `page` extra). */
enum class PeopleShortcut(val id: String) {
    CONTACTS("contacts"),
    NEW_CONTACT("new_contact"),
    GROUPS("groups");

    companion object {
        fun byId(id: String?): PeopleShortcut? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A contact as a `content://com.android.contacts/contacts/…` URI names it: by lookup key, by row id, or both. The key
 * is the URI's path segment as written (still percent-encoded); the activity rebuilds the URI from it.
 */
data class ContactRef(val encodedLookupKey: String?, val id: Long?)

/**
 * What an `ACTION_INSERT` / `ACTION_INSERT_OR_EDIT` hands the editor (the Trust line, r3 D10 (c)): the fields a caller
 * may suggest and nothing else. There is no account here on purpose — any account the caller names is never read, so a
 * prefilled contact starts on the phone (Q-16-3) — and nothing is saved until Save is tapped.
 */
data class ContactPrefill(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val company: String? = null,
    val notes: String? = null,
    val postal: String? = null,
) {
    val isEmpty: Boolean get() = this == ContactPrefill()
}

/** What `ACTION_PICK` hands back: one contact's lookup URI, or one phone number's data URI. */
enum class PickKind { CONTACT, PHONE }

/** What an intent asks People to show. */
sealed interface PeopleRoute {
    /** The list — the launcher entry, the People tile's tap, Phone's "Phone book" — or a shortcut's page. */
    data class Open(val page: PeopleShortcut?) : PeopleRoute

    /** `ACTION_VIEW` on a contact: its card. */
    data class Card(val contact: ContactRef) : PeopleRoute

    /**
     * `ACTION_EDIT` on a contact. Whether the editor opens is not decided here: the write guard decides, and a contact
     * it may not write opens as its card with `[people] edit <lookup>: refused (account not allowed)` (E28).
     */
    data class Edit(val contact: ContactRef) : PeopleRoute

    /** `ACTION_INSERT`: the editor for a new contact on the phone, filled in, unsaved. */
    data class Insert(val prefill: ContactPrefill) : PeopleRoute

    /** `ACTION_INSERT_OR_EDIT`: the list, to choose "new contact" or an existing one the fields are added to. */
    data class InsertOrEdit(val prefill: ContactPrefill) : PeopleRoute

    /** `ACTION_PICK`: the list in pick mode; the result grants its caller the one URI picked and nothing else. */
    data class Pick(val kind: PickKind) : PeopleRoute
}

/**
 * The exported handlers' rules (build task 2; Decisions "Trust" (c)), free of Android types so they are unit-tested.
 * Every caller-supplied value is bounded here; anything this does not recognise opens the list, never a crash.
 */
object PeopleIntents {
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_EDIT = "android.intent.action.EDIT"
    const val ACTION_INSERT = "android.intent.action.INSERT"
    const val ACTION_INSERT_OR_EDIT = "android.intent.action.INSERT_OR_EDIT"
    const val ACTION_PICK = "android.intent.action.PICK"

    const val AUTHORITY = "com.android.contacts"
    const val TYPE_CONTACT_DIR = "vnd.android.cursor.dir/contact"
    const val TYPE_CONTACT_ITEM = "vnd.android.cursor.item/contact"
    const val TYPE_PHONE_DIR = "vnd.android.cursor.dir/phone_v2"

    /** ContactsContract.Intents.Insert's extra names, as its constants spell them. */
    const val EXTRA_NAME = "name"
    const val EXTRA_PHONE = "phone"
    const val EXTRA_EMAIL = "email"
    const val EXTRA_COMPANY = "company"
    const val EXTRA_NOTES = "notes"
    const val EXTRA_POSTAL = "postal"
    const val EXTRA_PAGE = "page"

    const val MAX_FIELD = 500
    const val MAX_NOTES = 5000

    /** An intent's extras, read as text; a key of another type reads null. */
    fun interface Extras {
        fun string(key: String): String?
    }

    fun route(action: String?, data: String?, type: String?, extras: Extras): PeopleRoute {
        val path = pathOf(data)
        return when (action) {
            ACTION_VIEW -> contact(path)?.let { PeopleRoute.Card(it) } ?: open(extras)
            ACTION_EDIT -> contact(path)?.let { PeopleRoute.Edit(it) } ?: open(extras)
            ACTION_INSERT ->
                if (path == listOf("contacts") || path == listOf("raw_contacts") || (data == null && type == TYPE_CONTACT_DIR)) PeopleRoute.Insert(prefill(extras))
                else open(extras)
            ACTION_INSERT_OR_EDIT ->
                if (type == TYPE_CONTACT_ITEM || path == listOf("contacts")) PeopleRoute.InsertOrEdit(prefill(extras)) else open(extras)
            ACTION_PICK -> when {
                type == TYPE_PHONE_DIR || path == listOf("data", "phones") -> PeopleRoute.Pick(PickKind.PHONE)
                type == TYPE_CONTACT_DIR || path == listOf("contacts") -> PeopleRoute.Pick(PickKind.CONTACT)
                else -> open(extras)
            }
            else -> open(extras)
        }
    }

    private fun open(extras: Extras) = PeopleRoute.Open(PeopleShortcut.byId(extras.string(EXTRA_PAGE)))

    /** The actions PeopleActivity handles: the launcher entry's and its five handlers'. */
    private val HANDLED_ACTIONS = setOf(ACTION_MAIN, ACTION_VIEW, ACTION_EDIT, ACTION_INSERT, ACTION_INSERT_OR_EDIT, ACTION_PICK)

    const val NO_ACTION = "no action"
    const val OTHER_ACTION = "other"

    /**
     * An intent's action as the diagnostics ring may hold it. Any app may send any action string to an exported
     * activity — a line break and a forged line, a megabyte of text — so the string is written only when it is one of
     * the six the activity handles; any other is the word `other`, and an intent without one reads `no action`.
     */
    fun loggedAction(action: String?): String = when {
        action == null -> NO_ACTION
        action in HANDLED_ACTIONS -> action
        else -> OTHER_ACTION
    }

    /**
     * The `[people] open …` line: the action as [loggedAction] gives it and the kind of route — never what the caller
     * typed into it (a name, a number, a lookup key).
     */
    fun openLine(action: String?, route: PeopleRoute): String = "open ${loggedAction(action)} -> ${describe(route)}"

    private fun describe(route: PeopleRoute): String = when (route) {
        is PeopleRoute.Open -> "open page=${route.page?.id ?: "default"}"
        is PeopleRoute.Card -> "card"
        is PeopleRoute.Edit -> "edit"
        is PeopleRoute.Insert -> "insert (prefilled, unsaved)"
        is PeopleRoute.InsertOrEdit -> "insert or edit (prefilled, unsaved)"
        is PeopleRoute.Pick -> "pick ${route.kind.name.lowercase()}"
    }

    /**
     * `contacts/<id>`, `contacts/lookup/<key>` or `contacts/lookup/<key>/<id>`, and nothing else: a fourth segment that
     * is not a row id above 0 (`…/data`, `…/photo`, `…/0`) names something other than a contact, so it is refused
     * rather than read as the key alone.
     */
    private fun contact(path: List<String>?): ContactRef? {
        if (path == null || path.firstOrNull() != "contacts") return null
        return when {
            path.size == 2 -> path[1].toLongOrNull()?.takeIf { it > 0 }?.let { ContactRef(null, it) }
            path.size == 3 && path[1] == "lookup" && isLookupKey(path[2]) -> ContactRef(path[2], null)
            path.size == 4 && path[1] == "lookup" && isLookupKey(path[2]) -> path[3].toLongOrNull()?.takeIf { it > 0 }?.let { ContactRef(path[2], it) }
            else -> null
        }
    }

    /**
     * A lookup key as a URI writes it: bounded, and not a path's dot segment (`.` or `..`, written out or as `%2E`), which
     * climbs instead of naming. Dots inside a key are the provider's own (a joined contact's key), so only a segment of
     * nothing but dots is refused.
     */
    private fun isLookupKey(segment: String): Boolean =
        segment.isNotEmpty() && segment.length <= MAX_FIELD && segment.replace("%2e", ".", ignoreCase = true).any { it != '.' }

    private fun prefill(extras: Extras) = ContactPrefill(
        name = text(extras.string(EXTRA_NAME), MAX_FIELD),
        phone = text(extras.string(EXTRA_PHONE), MAX_FIELD),
        email = text(extras.string(EXTRA_EMAIL), MAX_FIELD),
        company = text(extras.string(EXTRA_COMPANY), MAX_FIELD),
        notes = text(extras.string(EXTRA_NOTES), MAX_NOTES),
        postal = text(extras.string(EXTRA_POSTAL), MAX_FIELD),
    )

    private fun text(value: String?, max: Int): String? = value?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

    /** The path segments of a `content://com.android.contacts/…` URI (percent-encoding kept); null for any other URI. */
    private fun pathOf(data: String?): List<String>? {
        val prefix = "content://$AUTHORITY/"
        if (data == null || !data.startsWith(prefix)) return null
        val path = data.substring(prefix.length).substringBefore('?').substringBefore('#')
        return path.split('/').filter { it.isNotEmpty() }
    }
}

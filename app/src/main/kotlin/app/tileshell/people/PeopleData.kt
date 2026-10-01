package app.tileshell.people

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import app.tileshell.diag.Diagnostics

/**
 * People's reads of Android's Contacts provider (build task 5). Everything here is a query: no call in this file
 * inserts, updates or deletes — every write is [PeopleWriter]'s, behind the write guard.
 *
 * The Android profile ("Me", `ContactsContract.Profile`) is never read (Decisions; H9).
 */
object PeopleData {
    /** `ContactsContract.ContactNameColumns`' phonebook label column; the SDK has no public constant for it. */
    private const val PHONEBOOK_LABEL = "phonebook_label"

    fun canRead(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Calls [onChange] on the main thread whenever the Contacts provider changes; returns what to unregister. */
    fun observe(context: Context, onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        context.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
        return observer
    }

    private val ROW_PROJECTION = arrayOf(
        Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, PHONEBOOK_LABEL,
        Contacts.DISPLAY_NAME_SOURCE, Contacts.PHOTO_THUMBNAIL_URI, Contacts.HAS_PHONE_NUMBER,
    )

    private fun row(c: Cursor, enterprise: Boolean = false): ContactRow? {
        val lookup = c.getString(1) ?: return null
        return ContactRow(
            id = c.getLong(0),
            lookup = lookup,
            name = PeopleBuckets.displayName(c.getString(2)),
            label = PeopleBuckets.label(c.getString(3), c.getInt(4)),
            photoThumb = c.getString(5),
            hasPhone = c.getInt(6) != 0,
            enterprise = enterprise,
        )
    }

    /**
     * Every contact, in the provider's `SORT_KEY_PRIMARY` order with its `PHONEBOOK_LABEL`. Null when the read failed
     * (no permission, the provider gone); the reason is in the ring.
     */
    fun contacts(context: Context): List<ContactRow>? = runCatching {
        val out = ArrayList<ContactRow>()
        context.contentResolver.query(Contacts.CONTENT_URI, ROW_PROJECTION, null, null, Contacts.SORT_KEY_PRIMARY)?.use { c ->
            while (c.moveToNext()) row(c)?.let { out += it }
        } ?: error("the provider returned no cursor")
        out
    }.getOrElse {
        Diagnostics.add("people", "list read failed: ${it.javaClass.simpleName}")
        null
    }

    /** The `[people] list: …` line: what the list read, and what the app may do. */
    fun logList(context: Context, count: Int) {
        Diagnostics.add("people", "list: $count contacts read=${canRead(context)} write=${canWrite(context)}")
    }

    /**
     * The accounts the provider names: the distinct `account_name` + `account_type` pairs of `raw_contacts`, read
     * through the provider (no `GET_ACCOUNTS`, no AccountManager). Null when the read failed — a failed read must
     * never be taken for "no accounts", or it would clear the "Can edit" list.
     */
    fun accounts(context: Context): List<ContactAccount>? = runCatching {
        val out = LinkedHashSet<ContactAccount>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) out += ContactAccount(c.getString(0), c.getString(1))
        } ?: error("the provider returned no cursor")
        out.toList()
    }.getOrElse {
        Diagnostics.add("people", "accounts read failed: ${it.javaClass.simpleName}")
        null
    }

    /** Reads the provider's accounts and drops from "Can edit" every account it no longer names. Returns the accounts. */
    fun syncAccounts(context: Context): List<ContactAccount>? {
        val named = accounts(context) ?: return null
        PeopleEditStore.get(context).prune(named)
        return named
    }

    /** For the filter: which accounts and groups each contact lives in. */
    fun memberships(context: Context): Map<Long, ContactMembership> = runCatching {
        val accounts = HashMap<Long, MutableSet<ContactAccount>>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED}=0", null, null,
        )?.use { c ->
            while (c.moveToNext()) accounts.getOrPut(c.getLong(0)) { mutableSetOf() } += ContactAccount(c.getString(1), c.getString(2))
        }
        val groups = HashMap<Long, MutableSet<Long>>()
        context.contentResolver.query(
            Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID),
            "${Data.MIMETYPE}=?", arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) groups.getOrPut(c.getLong(0)) { mutableSetOf() } += c.getLong(1)
        }
        (accounts.keys + groups.keys).associateWith { ContactMembership(accounts[it].orEmpty(), groups[it].orEmpty()) }
    }.getOrElse {
        Diagnostics.add("people", "filter read failed: ${it.javaClass.simpleName}")
        emptyMap()
    }

    data class SearchResult(val local: List<ContactRow>, val enterprise: List<ContactRow>)

    /**
     * Search by name and by number, plus the enterprise filter for work-profile matches. A name goes through the
     * provider's contact filter; a number through its phone lookup, so one typed without spaces matches one stored
     * with them. Writes the `[people] search …` line.
     */
    fun search(context: Context, query: String): SearchResult {
        val q = query.trim()
        if (q.isEmpty()) return SearchResult(emptyList(), emptyList())
        val local = LinkedHashMap<Long, ContactRow>()
        val enterprise = LinkedHashMap<Long, ContactRow>()
        val resolver = context.contentResolver
        runCatching {
            resolver.query(
                Contacts.CONTENT_FILTER_URI.buildUpon().appendPath(q).build(), ROW_PROJECTION, null, null, Contacts.SORT_KEY_PRIMARY,
            )?.use { c -> while (c.moveToNext()) row(c)?.let { local[it.id] = it } }
        }.onFailure { Diagnostics.add("people", "search read failed: ${it.javaClass.simpleName}") }
        if (PeopleSearch.isNumber(q)) {
            val ids = ArrayList<Long>()
            runCatching {
                resolver.query(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(PeopleSearch.digits(q)).build(),
                    arrayOf(ContactsContract.PhoneLookup.CONTACT_ID), null, null, null,
                )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
            }.onFailure { Diagnostics.add("people", "phone lookup failed: ${it.javaClass.simpleName}") }
            ids.filter { it !in local }.distinct().forEach { id -> rowById(context, id)?.let { local[it.id] = it } }
        }
        // The work profile's matches (H10): the enterprise filter, asked for the managed profile's own directory. A
        // personal-profile app cannot list those contacts, only search them, and only where the profile's policy allows.
        runCatching {
            val uri = Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon().appendPath(q)
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, ContactsContract.Directory.ENTERPRISE_DEFAULT.toString())
                .build()
            resolver.query(
                uri, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI), null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val lookup = c.getString(1) ?: continue
                    val name = PeopleBuckets.displayName(c.getString(2))
                    enterprise[id] = ContactRow(id, lookup, name, PeopleBuckets.NUMBER, c.getString(3), hasPhone = false, enterprise = true)
                }
            }
        }
        val result = SearchResult(local.values.toList(), enterprise.values.toList())
        Diagnostics.add("people", PeopleSearch.line(q, result.local.size, result.enterprise.size))
        return result
    }

    private fun rowById(context: Context, id: Long): ContactRow? = runCatching {
        context.contentResolver.query(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), ROW_PROJECTION, null, null, null)
            ?.use { c -> if (c.moveToFirst()) row(c) else null }
    }.getOrNull()

    /**
     * The contact a `content://com.android.contacts/contacts/…` URI names, as its row id — through the provider's own
     * lookup, so a key that changed when the contact was linked or unlinked still finds it. Null when it is gone.
     */
    fun resolve(context: Context, ref: ContactRef): Long? = runCatching {
        val key = ref.encodedLookupKey
        if (key == null) {
            val id = ref.id ?: return@runCatching null
            return@runCatching id.takeIf { rowById(context, it) != null }
        }
        var lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, key)
        ref.id?.let { lookupUri = ContentUris.withAppendedId(lookupUri, it) }
        Contacts.lookupContact(context.contentResolver, lookupUri)?.let { ContentUris.parseId(it) }
    }.getOrNull()

    /** The raw contacts behind an aggregate, with their accounts; deleted ones are not behind it any more. */
    fun rawContacts(context: Context, contactId: Long): List<RawContact> = runCatching {
        val out = ArrayList<RawContact>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE, RawContacts.DISPLAY_NAME_PRIMARY),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), RawContacts._ID,
        )?.use { c ->
            while (c.moveToNext()) out += RawContact(c.getLong(0), ContactAccount(c.getString(1), c.getString(2)), c.getString(3))
        }
        out
    }.getOrElse { emptyList() }

    /** Everything a card shows: the data kinds People knows, each with the raw contact it belongs to. Null when the contact is gone. */
    fun card(context: Context, contactId: Long): ContactCard? = runCatching {
        val resolver = context.contentResolver
        val head = resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), !c.isNull(2)) else null } ?: return@runCatching null
        val lookup = head.first ?: return@runCatching null
        val fields = ArrayList<ContactField>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
            "${Data.CONTACT_ID}=?", arrayOf(contactId.toString()), "${Data.RAW_CONTACT_ID}, ${Data._ID}",
        )?.use { c ->
            while (c.moveToNext()) {
                val value = c.getString(3)?.takeIf { it.isNotBlank() } ?: continue
                val kind = when (c.getString(2)) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> FieldKind.NAME
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> FieldKind.PHONE
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> FieldKind.EMAIL
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> FieldKind.ADDRESS
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> FieldKind.COMPANY
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> FieldKind.NOTES
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        if (c.getInt(4) == CommonDataKinds.Event.TYPE_BIRTHDAY) FieldKind.BIRTHDAY else continue
                    else -> continue
                }
                val typed = kind == FieldKind.PHONE || kind == FieldKind.EMAIL || kind == FieldKind.ADDRESS
                fields += ContactField(kind, c.getLong(0), c.getLong(1), value, if (typed) c.getInt(4) else 0, if (typed) c.getString(5) else null)
            }
        }
        ContactCard(contactId, lookup, PeopleBuckets.displayName(head.second), head.third, rawContacts(context, contactId), fields)
    }.getOrElse {
        Diagnostics.add("people", "card read failed: ${it.javaClass.simpleName}")
        null
    }

    /**
     * A work-profile contact's card, from what the enterprise filters give a personal-profile app: its name, its
     * numbers and its e-mails. It has no raw contact People can name; its one [RawRef] is another profile's.
     */
    fun enterpriseCard(context: Context, row: ContactRow): ContactCard {
        val fields = ArrayList<ContactField>()
        fun read(base: Uri, kind: FieldKind) = runCatching {
            val uri = base.buildUpon().appendPath(row.name)
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, ContactsContract.Directory.ENTERPRISE_DEFAULT.toString()).build()
            context.contentResolver.query(uri, arrayOf(Data._ID, Data.CONTACT_ID, Data.DATA1, Data.DATA2, Data.DATA3), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    if (c.getLong(1) != row.id) continue
                    val value = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    fields += ContactField(kind, c.getLong(0), ENTERPRISE_RAW, value, c.getInt(3), c.getString(4))
                }
            }
        }
        read(CommonDataKinds.Phone.ENTERPRISE_CONTENT_FILTER_URI, FieldKind.PHONE)
        read(CommonDataKinds.Email.ENTERPRISE_CONTENT_FILTER_URI, FieldKind.EMAIL)
        return ContactCard(row.id, row.lookup, row.name, row.photoThumb != null, listOf(RawContact(ENTERPRISE_RAW, ContactAccount(null, null), row.name)), fields, enterprise = true)
    }

    /** The id an enterprise card's one raw-contact reference carries: no row of this profile's provider. */
    const val ENTERPRISE_RAW = -1L

    /** The groups the provider holds, with their member counts; a group marked deleted is not listed. */
    fun groups(context: Context): List<ContactGroup> = runCatching {
        val out = ArrayList<ContactGroup>()
        context.contentResolver.query(
            Groups.CONTENT_SUMMARY_URI,
            arrayOf(Groups._ID, Groups.TITLE, Groups.ACCOUNT_NAME, Groups.ACCOUNT_TYPE, Groups.SUMMARY_COUNT),
            "${Groups.DELETED}=0", null, "${Groups.TITLE} COLLATE LOCALIZED",
        )?.use { c ->
            while (c.moveToNext()) out += ContactGroup(c.getLong(0), c.getString(1).orEmpty(), ContactAccount(c.getString(2), c.getString(3)), c.getInt(4))
        }
        out
    }.getOrElse {
        Diagnostics.add("people", "groups read failed: ${it.javaClass.simpleName}")
        emptyList()
    }

    /** One group, or null when it is gone or marked deleted. */
    fun group(context: Context, groupId: Long): ContactGroup? = groups(context).firstOrNull { it.id == groupId }

    /** The contacts that are members of a group, in the list's order. */
    fun groupMembers(context: Context, groupId: Long): List<ContactRow> = runCatching {
        val ids = LinkedHashSet<Long>()
        context.contentResolver.query(
            Data.CONTENT_URI, arrayOf(Data.CONTACT_ID),
            "${Data.MIMETYPE}=? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID}=?",
            arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        if (ids.isEmpty()) emptyList() else contacts(context).orEmpty().filter { it.id in ids }
    }.getOrElse { emptyList() }

    /** Each member's first mobile number, null for a member with none — what "Text the group" is built from. */
    fun firstMobiles(context: Context, contactIds: List<Long>): List<String?> = contactIds.map { id ->
        runCatching {
            context.contentResolver.query(
                CommonDataKinds.Phone.CONTENT_URI, arrayOf(CommonDataKinds.Phone.NUMBER),
                "${CommonDataKinds.Phone.CONTACT_ID}=? AND ${CommonDataKinds.Phone.TYPE}=?",
                arrayOf(id.toString(), CommonDataKinds.Phone.TYPE_MOBILE.toString()), CommonDataKinds.Phone._ID,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** One contact's phone numbers, for a phone PICK: the data row id, the number and its type word. */
    fun phones(context: Context, contactId: Long): List<ContactField> =
        card(context, contactId)?.of(FieldKind.PHONE).orEmpty()

    private val SIM_URI: Uri = Uri.parse("content://icc/adn")

    /**
     * The SIM's phonebook. Empty when the SIM lists nothing, refuses the read or is not there — an import then reads
     * "0 of 0", never a crash.
     */
    fun sim(context: Context): List<SimContact> = runCatching {
        val out = ArrayList<SimContact>()
        context.contentResolver.query(SIM_URI, null, null, null, null)?.use { c ->
            val name = c.getColumnIndex("name")
            val number = c.getColumnIndex("number")
            while (c.moveToNext()) {
                out += SimContact(out.size, if (name >= 0) c.getString(name).orEmpty() else "", if (number >= 0) c.getString(number) else null)
            }
        }
        out
    }.getOrElse {
        Diagnostics.add("people", "sim read failed: ${it.javaClass.simpleName}")
        emptyList()
    }

    /** A contact's photo decoded at about [targetPx] on its short side (the card's 124-epx circle, a tile's bubble). */
    fun photo(context: Context, contactId: Long, targetPx: Int): Bitmap? = runCatching {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode returns no bitmap: what says the stream opened is the stream, not the decode's result.
        val stream = Contacts.openContactPhotoInputStream(context.contentResolver, uri, true) ?: return@runCatching null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply { inSampleSize = ContactPhotoRules.sampleSize(bounds.outWidth, bounds.outHeight, targetPx) }
        Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /** The provider's own thumbnail for a list row, read through its thumbnail URI. */
    fun thumbnail(context: Context, thumbUri: String): Bitmap? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(thumbUri))?.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

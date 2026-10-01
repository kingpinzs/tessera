package app.tileshell.people

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16 build task 2: what People's exported handlers may do with an intent (Decisions "Trust" (c)). */
class PeopleIntentsTest {
    private fun route(action: String?, data: String? = null, type: String? = null, vararg extras: Pair<String, Any?>): PeopleRoute {
        val map = extras.toMap()
        return PeopleIntents.route(action, data, type) { map[it] as? String }
    }

    @Test fun theLauncherEntryOpensTheList() {
        assertEquals(PeopleRoute.Open(null), route("android.intent.action.MAIN"))
        assertEquals(PeopleRoute.Open(null), route(null))
    }

    @Test fun aShortcutsPageExtraPicksThePage() {
        assertEquals(PeopleRoute.Open(PeopleShortcut.CONTACTS), route(PeopleIntents.ACTION_VIEW, extras = arrayOf("page" to "contacts")))
        assertEquals(PeopleRoute.Open(PeopleShortcut.NEW_CONTACT), route(PeopleIntents.ACTION_VIEW, extras = arrayOf("page" to "new_contact")))
        assertEquals(PeopleRoute.Open(PeopleShortcut.GROUPS), route(PeopleIntents.ACTION_VIEW, extras = arrayOf("page" to "groups")))
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_VIEW, extras = arrayOf("page" to "can_edit")))
    }

    @Test fun viewOnAContactOrLookupUriOpensTheCard() {
        assertEquals(PeopleRoute.Card(ContactRef(null, 7)), route(PeopleIntents.ACTION_VIEW, "content://com.android.contacts/contacts/7"))
        assertEquals(PeopleRoute.Card(ContactRef("0r3-2A4C", null)), route(PeopleIntents.ACTION_VIEW, "content://com.android.contacts/contacts/lookup/0r3-2A4C"))
        assertEquals(PeopleRoute.Card(ContactRef("0r3-2A4C", 7)), route(PeopleIntents.ACTION_VIEW, "content://com.android.contacts/contacts/lookup/0r3-2A4C/7"))
        // The key is kept as the URI wrote it; the activity rebuilds the URI from it.
        assertEquals(PeopleRoute.Card(ContactRef("0r3-2A%2F4C", 7)), route(PeopleIntents.ACTION_VIEW, "content://com.android.contacts/contacts/lookup/0r3-2A%2F4C/7"))
    }

    @Test fun editNamesTheContactAndNothingElse() {
        // Whether the editor may open is the write guard's question (E28), not the intent's.
        assertEquals(PeopleRoute.Edit(ContactRef("0r3-2A4C", 7)), route(PeopleIntents.ACTION_EDIT, "content://com.android.contacts/contacts/lookup/0r3-2A4C/7", null, "name" to "Hijack"))
    }

    @Test fun insertPrefillsTheEditor() {
        val r = route(
            PeopleIntents.ACTION_INSERT, null, PeopleIntents.TYPE_CONTACT_DIR,
            "name" to "Ned New", "phone" to "+1 555 000 0030", "email" to "ned@example.com", "company" to "Acme", "notes" to "met at the fair", "postal" to "1 Main St",
        )
        assertEquals(PeopleRoute.Insert(ContactPrefill("Ned New", "+1 555 000 0030", "ned@example.com", "Acme", "met at the fair", "1 Main St")), r)
        assertEquals(PeopleRoute.Insert(ContactPrefill(name = "Ned New")), route(PeopleIntents.ACTION_INSERT, "content://com.android.contacts/contacts", null, "name" to "Ned New"))
        assertEquals(PeopleRoute.Insert(ContactPrefill(name = "Ned New")), route(PeopleIntents.ACTION_INSERT, "content://com.android.contacts/raw_contacts", null, "name" to "Ned New"))
    }

    @Test fun insertNeverCarriesAnAccount() {
        // The Trust line (c): a contact INSERT ignores any account it names; the prefill has no field to carry one.
        val r = route(
            PeopleIntents.ACTION_INSERT, null, PeopleIntents.TYPE_CONTACT_DIR,
            "name" to "Ned New", "android.provider.extra.ACCOUNT" to "qa.work@example.com", "android.provider.extra.DATA_SET" to "work",
            "account_name" to "qa.work@example.com", "account_type" to "com.example",
        )
        assertEquals(PeopleRoute.Insert(ContactPrefill(name = "Ned New")), r)
    }

    @Test fun insertBoundsWhatACallerSuggests() {
        val r = route(PeopleIntents.ACTION_INSERT, null, PeopleIntents.TYPE_CONTACT_DIR, "name" to "x".repeat(10_000), "phone" to "  ", "notes" to "n".repeat(10_000), "email" to 5) as PeopleRoute.Insert
        assertEquals(PeopleIntents.MAX_FIELD, r.prefill.name!!.length)
        assertEquals(null, r.prefill.phone)
        assertEquals(PeopleIntents.MAX_NOTES, r.prefill.notes!!.length)
        assertEquals(null, r.prefill.email)
    }

    @Test fun insertOrEditOpensTheListToChoose() {
        assertEquals(PeopleRoute.InsertOrEdit(ContactPrefill(phone = "5550002")), route(PeopleIntents.ACTION_INSERT_OR_EDIT, null, PeopleIntents.TYPE_CONTACT_ITEM, "phone" to "5550002"))
    }

    @Test fun pickNamesOnlyWhatKindOfUriGoesBack() {
        assertEquals(PeopleRoute.Pick(PickKind.CONTACT), route(PeopleIntents.ACTION_PICK, null, PeopleIntents.TYPE_CONTACT_DIR))
        assertEquals(PeopleRoute.Pick(PickKind.CONTACT), route(PeopleIntents.ACTION_PICK, "content://com.android.contacts/contacts"))
        assertEquals(PeopleRoute.Pick(PickKind.PHONE), route(PeopleIntents.ACTION_PICK, null, PeopleIntents.TYPE_PHONE_DIR))
        assertEquals(PeopleRoute.Pick(PickKind.PHONE), route(PeopleIntents.ACTION_PICK, "content://com.android.contacts/data/phones"))
        // Nothing else is pickable: e-mail rows, postal rows, raw contacts open the plain list.
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_PICK, null, "vnd.android.cursor.dir/email_v2"))
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_PICK, "content://com.android.contacts/raw_contacts"))
    }

    @Test fun aMalformedOrForeignUriOpensTheList() {
        for (data in listOf(
            "content://com.android.contacts/contacts/abc", "content://com.android.contacts/contacts/-3", "content://com.android.contacts/contacts/0",
            "content://com.android.contacts/contacts/lookup", "content://com.android.contacts/contacts/lookup/", "content://com.android.contacts/contacts/lookup/k/7/photo",
            "content://com.android.contacts/raw_contacts/7", "content://com.android.contacts/data/7", "content://com.android.contacts/",
            "content://com.android.calendar/contacts/7", "content://com.android.contacts.evil/contacts/7", "file:///sdcard/contacts/7", "", "::::",
            "content://com.android.contacts/contacts/lookup/" + "k".repeat(PeopleIntents.MAX_FIELD + 1),
        )) {
            assertEquals(data, PeopleRoute.Open(null), route(PeopleIntents.ACTION_VIEW, data))
            assertEquals(data, PeopleRoute.Open(null), route(PeopleIntents.ACTION_EDIT, data))
        }
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_EDIT))
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_INSERT, "content://com.android.contacts/groups", null, "name" to "x"))
        assertEquals(PeopleRoute.Open(null), route("android.intent.action.DELETE", "content://com.android.contacts/contacts/7"))
    }
}

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

    // ---- The fix round's F11 (trust review B-F10): the halves of the parser that no test pinned.

    private val contacts = "content://com.android.contacts"

    private fun assertOpensTheList(data: String) {
        assertEquals(data, PeopleRoute.Open(null), route(PeopleIntents.ACTION_VIEW, data))
        assertEquals(data, PeopleRoute.Open(null), route(PeopleIntents.ACTION_EDIT, data))
    }

    @Test fun anAuthorityThatOnlyBeginsLikeContactsIsAnotherProvider() {
        // The authority ends at its slash: "com.android.contactscontacts" is a provider of its own, whatever its name starts with.
        assertOpensTheList("content://com.android.contactscontacts/7")
        assertOpensTheList("content://com.android.contactscontacts/lookup/0r3-2A4C/7")
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_INSERT, "content://com.android.contactscontacts", null, "name" to "x"))
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_INSERT_OR_EDIT, "content://com.android.contactscontacts", null, "name" to "x"))
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_PICK, "content://com.android.contactscontacts"))
        assertEquals(PeopleRoute.Open(null), route(PeopleIntents.ACTION_PICK, "content://com.android.contactsdata/phones"))
    }

    @Test fun insertByTypeIsOnlyForAnIntentWithNoData() {
        // The contact type alone asks for the editor; the same type on some other URI does not.
        for (data in listOf("content://evil.example/contacts", "content://com.android.calendar/events", "file:///sdcard/x.vcf", "$contacts/groups", "$contacts/contacts/7")) {
            assertEquals(data, PeopleRoute.Open(null), route(PeopleIntents.ACTION_INSERT, data, PeopleIntents.TYPE_CONTACT_DIR, "name" to "x"))
        }
    }

    @Test fun insertOrEditNeedsTheContactTypeOrTheContactsUri() {
        assertEquals(PeopleRoute.InsertOrEdit(ContactPrefill(phone = "5550002")), route(PeopleIntents.ACTION_INSERT_OR_EDIT, "$contacts/contacts", null, "phone" to "5550002"))
        for ((data, type) in listOf(
            null to null, null to "vnd.android.cursor.item/event", null to PeopleIntents.TYPE_CONTACT_DIR, null to PeopleIntents.TYPE_PHONE_DIR, null to "text/plain",
            "content://com.android.calendar/events" to null, "$contacts/groups" to null, "$contacts/contacts/7" to null, "$contacts/data/phones" to PeopleIntents.TYPE_PHONE_DIR,
        )) {
            assertEquals("$data $type", PeopleRoute.Open(null), route(PeopleIntents.ACTION_INSERT_OR_EDIT, data, type, "phone" to "5550002"))
        }
    }

    @Test fun aLookupUrisFourthSegmentIsAPositiveRowIdOrTheUriIsRefused() {
        // `lookup/<key>/<id>` names a contact; `lookup/<key>/data`, `/photo`, `/entities` and an id of 0 or less do not.
        for (tail in listOf("0", "-3", "abc", "data", "photo", "entities", "7x")) assertOpensTheList("$contacts/contacts/lookup/0r3-2A4C/$tail")
        assertEquals(PeopleRoute.Card(ContactRef("0r3-2A4C", 1)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/lookup/0r3-2A4C/1"))
    }

    @Test fun onlyTheWordLookupIntroducesAKey() {
        // `contacts/<id>/data`, `contacts/<id>/photo` and the like are a contact's sub-tables, not `contacts/lookup/<key>`.
        for (path in listOf("contacts/7/data", "contacts/7/photo", "contacts/7/7", "contacts/x/y", "contacts/x/y/7", "contacts/Lookup/0r3-2A4C", "contacts/filter/ann")) {
            assertOpensTheList("$contacts/$path")
        }
    }

    @Test fun aDotSegmentIsNotALookupKey() {
        // `…/contacts/lookup/../5` is a path that climbs, not a key (the reviewer's probe read it as the key "..").
        for (key in listOf(".", "..", "...", "%2e%2e", "%2E", ".%2e")) {
            assertOpensTheList("$contacts/contacts/lookup/$key")
            assertOpensTheList("$contacts/contacts/lookup/$key/5")
        }
        // Dots inside a key are the provider's own: a joined contact's key, a source id that held a dot.
        assertEquals(PeopleRoute.Card(ContactRef("0r1-2A.0r2-4C", 7)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/lookup/0r1-2A.0r2-4C/7"))
        assertEquals(PeopleRoute.Card(ContactRef("0i12..34", null)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/lookup/0i12..34"))
    }

    @Test fun aQueryOnTheUriIsNotPartOfThePath() {
        assertEquals(PeopleRoute.Card(ContactRef(null, 7)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/7?directory=0"))
        assertEquals(PeopleRoute.Card(ContactRef("0r3-2A4C", null)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/lookup/0r3-2A4C?directory=0"))
        assertEquals(PeopleRoute.Edit(ContactRef("0r3-2A4C", 7)), route(PeopleIntents.ACTION_EDIT, "$contacts/contacts/lookup/0r3-2A4C/7?a=1&b=2"))
        assertEquals(PeopleRoute.Insert(ContactPrefill(name = "Ned New")), route(PeopleIntents.ACTION_INSERT, "$contacts/contacts?a=1", null, "name" to "Ned New"))
        assertEquals(PeopleRoute.Pick(PickKind.PHONE), route(PeopleIntents.ACTION_PICK, "$contacts/data/phones?a=1"))
    }

    @Test fun aFragmentOnTheUriIsNotPartOfThePath() {
        assertEquals(PeopleRoute.Card(ContactRef(null, 7)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/7#top"))
        assertEquals(PeopleRoute.Card(ContactRef("0r3-2A4C", null)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/lookup/0r3-2A4C#top"))
        assertEquals(PeopleRoute.Edit(ContactRef("0r3-2A4C", 7)), route(PeopleIntents.ACTION_EDIT, "$contacts/contacts/lookup/0r3-2A4C/7#top"))
        assertEquals(PeopleRoute.Insert(ContactPrefill(name = "Ned New")), route(PeopleIntents.ACTION_INSERT, "$contacts/contacts#top", null, "name" to "Ned New"))
        assertEquals(PeopleRoute.Pick(PickKind.CONTACT), route(PeopleIntents.ACTION_PICK, "$contacts/contacts#top"))
        // A fragment may itself hold a question mark; a query comes before the fragment.
        assertEquals(PeopleRoute.Card(ContactRef(null, 7)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/7#a?b"))
        assertEquals(PeopleRoute.Card(ContactRef(null, 7)), route(PeopleIntents.ACTION_VIEW, "$contacts/contacts/7?a=1#top"))
    }

    // ---- The fix round's F4 (trust review B-F3): the caller's action string and the diagnostics ring.

    @Test fun anActionTheActivityHandlesIsLoggedAsWritten() {
        for (action in listOf(
            "android.intent.action.MAIN", "android.intent.action.VIEW", "android.intent.action.EDIT",
            "android.intent.action.INSERT", "android.intent.action.INSERT_OR_EDIT", "android.intent.action.PICK",
        )) assertEquals(action, PeopleIntents.loggedAction(action))
        assertEquals("no action", PeopleIntents.loggedAction(null))
    }

    @Test fun anyOtherActionIsLoggedAsTheWordOther() {
        for (action in listOf(
            "x\n[people] forged", "android.intent.action.VIEW\n2026-10-01 12:00:00.000 wall=1 [people] write delete raw=1: ok", "a".repeat(1_000_000),
            "", " ", "android.intent.action.VIEW ", " android.intent.action.VIEW", "ANDROID.INTENT.ACTION.VIEW", "android.intent.action.DELETE",
            "android.intent.action.SEND", "android.intent.action.GET_CONTENT", "VIEW", "no action", "other",
        )) assertEquals(action.take(40), "other", PeopleIntents.loggedAction(action))
    }

    @Test fun theOpenLineHoldsTheKindOfRouteAndNothingTheCallerTyped() {
        assertEquals("open android.intent.action.MAIN -> open page=default", PeopleIntents.openLine("android.intent.action.MAIN", PeopleRoute.Open(null)))
        assertEquals("open no action -> open page=groups", PeopleIntents.openLine(null, PeopleRoute.Open(PeopleShortcut.GROUPS)))
        assertEquals("open other -> open page=default", PeopleIntents.openLine("x\n[people] forged", PeopleRoute.Open(null)))
        assertEquals("open android.intent.action.VIEW -> card", PeopleIntents.openLine(PeopleIntents.ACTION_VIEW, PeopleRoute.Card(ContactRef("0r3-2A4C", 7))))
        assertEquals("open android.intent.action.EDIT -> edit", PeopleIntents.openLine(PeopleIntents.ACTION_EDIT, PeopleRoute.Edit(ContactRef("0r3-2A4C", 7))))
        val typed = ContactPrefill("Intruder", "5550666", "i@example.com", "Acme", "a note", "1 Main St")
        assertEquals("open android.intent.action.INSERT -> insert (prefilled, unsaved)", PeopleIntents.openLine(PeopleIntents.ACTION_INSERT, PeopleRoute.Insert(typed)))
        assertEquals("open android.intent.action.INSERT_OR_EDIT -> insert or edit (prefilled, unsaved)", PeopleIntents.openLine(PeopleIntents.ACTION_INSERT_OR_EDIT, PeopleRoute.InsertOrEdit(typed)))
        assertEquals("open android.intent.action.PICK -> pick contact", PeopleIntents.openLine(PeopleIntents.ACTION_PICK, PeopleRoute.Pick(PickKind.CONTACT)))
        assertEquals("open android.intent.action.PICK -> pick phone", PeopleIntents.openLine(PeopleIntents.ACTION_PICK, PeopleRoute.Pick(PickKind.PHONE)))
        // Whatever the action and the route, the line is one short line.
        val routes = listOf(
            PeopleRoute.Open(null), PeopleRoute.Open(PeopleShortcut.NEW_CONTACT), PeopleRoute.Card(ContactRef("k".repeat(500), 7)), PeopleRoute.Edit(ContactRef(null, 7)),
            PeopleRoute.Insert(ContactPrefill(name = "x".repeat(500), notes = "line\nbreak")), PeopleRoute.InsertOrEdit(typed), PeopleRoute.Pick(PickKind.PHONE),
        )
        for (action in listOf(null, "android.intent.action.INSERT_OR_EDIT", "x\n[people] forged", "a".repeat(100_000))) for (route in routes) {
            val line = PeopleIntents.openLine(action, route)
            assertEquals(line, false, line.contains('\n') || line.length > 90)
        }
    }
}

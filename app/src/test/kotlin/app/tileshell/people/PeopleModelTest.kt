package app.tileshell.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** People's pure rules (phase 16 build task 5): buckets, the jump grid, search, the card's rows, groups, SIM, the filter. */
class PeopleModelTest {
    private fun row(id: Long, name: String, label: String, hasPhone: Boolean = true) = ContactRow(id, "k$id", name, label, null, hasPhone)

    // ------------------------------------------------------------------------------------------- buckets

    @Test
    fun `a named contact files under the provider's label`() {
        assertEquals("A", PeopleBuckets.label("A", 40))
        assertEquals("Z", PeopleBuckets.label("Z", 40))
        assertEquals("張", PeopleBuckets.label("張", 40))
        // A company-only contact: the organisation is its name, and it files where the provider put it.
        assertEquals("C", PeopleBuckets.label("C", 30))
    }

    @Test
    fun `a contact with no name files under the number sign`() {
        // The provider shows a number (source 20) or an e-mail (source 10) as the name; an e-mail's label would be a letter.
        assertEquals("#", PeopleBuckets.label("#", 20))
        assertEquals("#", PeopleBuckets.label("A", 10))
        assertEquals("#", PeopleBuckets.label(null, 0))
        assertEquals("#", PeopleBuckets.label("", 40))
        assertEquals("(No name)", PeopleBuckets.displayName(null))
        assertEquals("+1 555 000 0009", PeopleBuckets.displayName("+1 555 000 0009"))
    }

    @Test
    fun `sections run number sign, A to Z, then other scripts in the provider's order`() {
        val rows = listOf(
            row(1, "+1 555 000 0009", "#"), row(2, "Ann Lee", "A"), row(3, "Bob Stone", "B"), row(4, "Cara Diaz", "C"),
            row(5, "Zoë Ǻrén", "Z"), row(6, "张伟", "…"), row(7, "Ærøskøbing", "Æ"),
        )
        // Whatever order the provider's sort hands them in, the sections come out in the grid's order.
        val sections = PeopleBuckets.sections(listOf(rows[5], rows[1], rows[4], rows[6], rows[0], rows[3], rows[2]))
        assertEquals(listOf("#", "A", "B", "C", "Z", "…", "Æ"), sections.map { it.label })
        assertEquals(listOf("Ann Lee"), sections[1].rows.map { it.name })
    }

    @Test
    fun `rows keep the provider's order inside a section`() {
        val sections = PeopleBuckets.sections(listOf(row(1, "Ann Lee", "A"), row(2, "Anna Ray", "A"), row(3, "Abe Zed", "A")))
        assertEquals(listOf("Ann Lee", "Anna Ray", "Abe Zed"), sections.single().rows.map { it.name })
    }

    @Test
    fun `the jump grid is the number sign, A to Z and the globe, live where contacts exist`() {
        val sections = PeopleBuckets.sections(listOf(row(1, "5", "#"), row(2, "Ann", "A"), row(3, "Zoë", "Z"), row(4, "张伟", "…")))
        val cells = PeopleBuckets.cells(sections)
        assertEquals(28, cells.size)
        assertEquals("#", cells.first().id)
        assertEquals("globe", cells.last().id)
        assertTrue(cells.last().isGlobe)
        assertEquals(listOf("#", "A", "Z", "globe"), cells.filter { it.targetLabel != null }.map { it.id })
        assertEquals("…", cells.last().targetLabel)
        assertEquals(('A'..'Z').map { it.toString() }, cells.subList(1, 27).map { it.text })
    }

    @Test
    fun `with no contacts no cell of the grid is live`() {
        assertTrue(PeopleBuckets.cells(emptyList()).all { it.targetLabel == null })
    }

    @Test
    fun `the avatar's initial`() {
        assertEquals("B", PeopleBuckets.initial("Boss"))
        assertEquals("1", PeopleBuckets.initial("+1 555 000 0009"))
        assertEquals("张", PeopleBuckets.initial("张伟"))
        assertEquals("#", PeopleBuckets.initial("()"))
    }

    // ------------------------------------------------------------------------------------------- search

    @Test
    fun `a typed number goes through the phone lookup`() {
        assertTrue(PeopleSearch.isNumber("555 000 0001"))
        assertTrue(PeopleSearch.isNumber("+1 (555) 000-0001"))
        assertTrue(PeopleSearch.isNumber("5550000001"))
        assertFalse(PeopleSearch.isNumber("ann"))
        assertFalse(PeopleSearch.isNumber("ann 5"))
        assertFalse(PeopleSearch.isNumber("+ -"))
        assertFalse(PeopleSearch.isNumber(""))
        assertEquals("+15550000001", PeopleSearch.digits("+1 (555) 000-0001"))
        assertEquals("5550000001", PeopleSearch.digits(" 555 000 0001 "))
    }

    @Test
    fun `the search line`() {
        assertEquals("search \"ann\": 1 (+0 enterprise)", PeopleSearch.line("ann", 1, 0))
        assertEquals("search \"Wren\": 0 (+1 enterprise)", PeopleSearch.line("Wren", 0, 1))
    }

    // ------------------------------------------------------------------------------------------- the card

    private fun phone(id: Long, number: String, type: Int) = ContactField(FieldKind.PHONE, id, 1, number, type)

    @Test
    fun `a card's action rows - Send message, Call, Email, Map`() {
        val card = ContactCard(
            1, "k1", "Ann Lee", false, emptyList(),
            listOf(
                phone(10, "+1 555 000 0001", 2),
                ContactField(FieldKind.EMAIL, 11, 1, "ann@example.com", 1),
                ContactField(FieldKind.ADDRESS, 12, 1, "1 Main St, Springfield", 1),
            ),
        )
        val actions = CardRules.actions(card)
        assertEquals(
            listOf("text:0 Send message", "call:0 Call Mobile", "mail:0 Email Personal", "map:0 Map Home"),
            actions.map { "${it.kind.id}:${it.index} ${it.title}" },
        )
        assertNull(actions[0].detail)
        assertEquals("+1 555 000 0001", actions[0].target)
        assertEquals("+1 555 000 0001", actions[1].detail)
        assertEquals("ann@example.com", actions[2].target)
    }

    @Test
    fun `Send message uses the first mobile number, and every other number gets its own Text row`() {
        val card = ContactCard(1, "k1", "Ann", false, emptyList(), listOf(phone(10, "111", 1), phone(11, "222", 2), phone(12, "333", 3)))
        val actions = CardRules.actions(card)
        assertEquals("222", actions.first { it.kind == CardActionKind.TEXT && it.index == 0 }.target)
        assertEquals(
            listOf("text:0 Send message", "call:0 Call Home", "text:1 Text Home", "call:1 Call Mobile", "call:2 Call Work", "text:2 Text Work"),
            actions.map { "${it.kind.id}:${it.index} ${it.title}" },
        )
    }

    @Test
    fun `a contact with twenty numbers lists every one with its label`() {
        val card = ContactCard(1, "k1", "Ann", false, emptyList(), (0 until 20).map { phone(it.toLong(), "555 000 00%02d".format(it), if (it % 2 == 0) 2 else 3) })
        val calls = CardRules.actions(card).filter { it.kind == CardActionKind.CALL }
        assertEquals(20, calls.size)
        assertEquals((0 until 20).toList(), calls.map { it.index })
        assertEquals("Call Work", calls[1].title)
    }

    @Test
    fun `a contact with no number has no call or text row`() {
        val card = ContactCard(1, "k1", "Ann", false, emptyList(), listOf(ContactField(FieldKind.EMAIL, 11, 1, "ann@example.com", 2)))
        assertEquals(listOf("Email Work"), CardRules.actions(card).map { it.title })
    }

    @Test
    fun `type words`() {
        assertEquals("Mobile phone", FieldTypes.editorLabel(FieldKind.PHONE, 2, null))
        assertEquals("Personal email", FieldTypes.editorLabel(FieldKind.EMAIL, 1, null))
        assertEquals("Home address", FieldTypes.editorLabel(FieldKind.ADDRESS, 1, null))
        assertEquals("Pager", FieldTypes.word(FieldKind.PHONE, 6, null))
        assertEquals("Boat", FieldTypes.word(FieldKind.PHONE, 0, "Boat"))
        assertEquals("Other", FieldTypes.word(FieldKind.PHONE, 0, " "))
        assertEquals("Other", FieldTypes.word(FieldKind.EMAIL, 99, null))
        assertEquals(2, FieldTypes.default(FieldKind.PHONE))
        assertEquals(1, FieldTypes.default(FieldKind.EMAIL))
    }

    @Test
    fun `account names`() {
        assertEquals("Phone", CardRules.accountName(ContactAccount(null, null)))
        assertEquals("Phone", CardRules.accountName(null))
        assertEquals("qa.work@example.com", CardRules.accountName(ContactAccount("qa.work@example.com", "com.example")))
    }

    @Test
    fun `birthdays as the card reads them`() {
        assertEquals("5 March 1990", CardRules.birthday("1990-03-05"))
        assertEquals("29 February", CardRules.birthday("--02-29"))
        assertEquals("someday", CardRules.birthday("someday"))
        assertEquals("1990-13-05", CardRules.birthday("1990-13-05"))
    }

    // ------------------------------------------------------------------------------------------- groups

    @Test
    fun `Text the group is each member's first mobile number`() {
        assertEquals("smsto:+15550000001;+15550000002", GroupRules.smsTo(listOf("+15550000001", "+15550000002")))
    }

    @Test
    fun `a member with no mobile number is left out, and nobody left is no compose`() {
        assertEquals("smsto:+15550000001", GroupRules.smsTo(listOf("+15550000001", null, " ")))
        assertNull(GroupRules.smsTo(listOf(null, null)))
        assertNull(GroupRules.smsTo(emptyList()))
        assertEquals("This group has no members to text.", GroupRules.textNotice(0))
        assertEquals("Nobody in this group has a mobile number.", GroupRules.textNotice(2))
    }

    @Test
    fun `two members sharing a number are texted once`() {
        assertEquals("smsto:111", GroupRules.smsTo(listOf("111", "111")))
        assertEquals("1 member", GroupRules.membersLine(1))
        assertEquals("2 members", GroupRules.membersLine(2))
    }

    // ------------------------------------------------------------------------------------------- SIM

    @Test
    fun `a SIM contact with no number is skipped`() {
        assertTrue(SimRules.importable(SimContact(0, "Sim Bob", "5550002")))
        assertFalse(SimRules.importable(SimContact(1, "No Number", null)))
        assertFalse(SimRules.importable(SimContact(2, "Blank", " ")))
        assertEquals("sim import: 1 of 1", SimRules.line(1, 1))
        assertEquals("sim import: 0 of 0", SimRules.line(0, 0))
    }

    // ------------------------------------------------------------------------------------------- the filter

    private val x = ContactAccount("x", "com.example")
    private val phoneAccount = ContactAccount(null, null)

    @Test
    fun `unticking an account hides the contacts that live only there`() {
        val filter = ContactFilter(hiddenAccounts = setOf(x))
        assertFalse(FilterRules.shows(row(1, "Xavier", "X"), ContactMembership(setOf(x), emptySet()), filter))
        assertTrue(FilterRules.shows(row(2, "Ann", "A"), ContactMembership(setOf(phoneAccount), emptySet()), filter))
        // A contact linked across a hidden and a shown account is still listed.
        assertTrue(FilterRules.shows(row(3, "Both", "B"), ContactMembership(setOf(x, phoneAccount), emptySet()), filter))
    }

    @Test
    fun `unticking a group hides its members unless they are in a shown group too`() {
        val filter = ContactFilter(hiddenGroups = setOf(7L))
        assertFalse(FilterRules.shows(row(1, "Ann", "A"), ContactMembership(setOf(phoneAccount), setOf(7L)), filter))
        assertTrue(FilterRules.shows(row(2, "Bob", "B"), ContactMembership(setOf(phoneAccount), setOf(7L, 8L)), filter))
        assertTrue(FilterRules.shows(row(3, "Cara", "C"), ContactMembership(setOf(phoneAccount), emptySet()), filter))
    }

    @Test
    fun `hide contacts without phone numbers`() {
        val filter = ContactFilter(hideWithoutPhones = true)
        assertFalse(FilterRules.shows(row(1, "Ann", "A", hasPhone = false), null, filter))
        assertTrue(FilterRules.shows(row(2, "Bob", "B", hasPhone = true), null, filter))
        assertEquals("contacts with phone numbers", FilterRules.caption(filter))
    }

    @Test
    fun `no filter lists everyone and shows no caption`() {
        assertTrue(FilterRules.shows(row(1, "Ann", "A", hasPhone = false), ContactMembership(setOf(x), setOf(7L)), ContactFilter()))
        assertNull(FilterRules.caption(ContactFilter()))
        assertEquals("some contacts", FilterRules.caption(ContactFilter(hiddenAccounts = setOf(x))))
    }

    // ------------------------------------------------------------------------------------------- photos

    @Test
    fun `a photo is sampled down to about the size it is drawn`() {
        assertEquals(1, ContactPhotoRules.sampleSize(96, 96, 96))
        assertEquals(1, ContactPhotoRules.sampleSize(720, 720, 400))
        assertEquals(2, ContactPhotoRules.sampleSize(720, 720, 360))
        assertEquals(8, ContactPhotoRules.sampleSize(6000, 4000, 400))
        assertEquals(1, ContactPhotoRules.sampleSize(0, 0, 400))
    }
}

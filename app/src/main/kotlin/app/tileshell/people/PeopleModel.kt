package app.tileshell.people

/**
 * People's data as the pages read it, and the rules that shape it — free of Android types, so the bucket order, the
 * search form, the labels and the group text are proven on the JVM (PeopleModelTest).
 */

/** One row of the A–Z list: an aggregated contact as `ContactsContract.Contacts` holds it. */
data class ContactRow(
    val id: Long,
    /** The provider's LOOKUP_KEY, as stored: the key of every tag (`people_row:<lookup>`). */
    val lookup: String,
    /** What the row reads: the display name, or the number or e-mail the provider falls back to; never empty. */
    val name: String,
    /** The letter header this row files under: the provider's phonebook label, or "#" (see [PeopleBuckets.label]). */
    val label: String,
    val photoThumb: String?,
    val hasPhone: Boolean,
    /** Another profile's contact, found by the enterprise search: listed by search only, always read-only. */
    val enterprise: Boolean = false,
)

/** A letter group of the list. */
data class ContactSection(val label: String, val rows: List<ContactRow>)

/** One cell of People's own jump grid (r11/people.md P2): "#", A–Z, then the globe for every other script. */
data class JumpCell(val id: String, val text: String, val isGlobe: Boolean, val targetLabel: String?)

object PeopleBuckets {
    const val NUMBER = "#"
    const val GLOBE = "globe"
    private val LATIN = ('A'..'Z').map { it.toString() }

    /** `ContactsContract.DisplayNameSources`: below ORGANIZATION the provider's "name" is an e-mail, a number or nothing. */
    const val SOURCE_ORGANIZATION = 30

    /** What a contact with no name of its own reads when the provider has nothing at all to show for it. */
    const val NO_NAME = "(No name)"

    /**
     * The header a contact files under. The provider's `PHONEBOOK_LABEL` decides, so a non-Latin name files where
     * Android files it; a contact with no name — the provider shows its number or e-mail as the name — goes under "#"
     * (Decisions "People over the provider"; approximation, H8), as does one the provider gave no label.
     */
    fun label(providerLabel: String?, displayNameSource: Int): String = when {
        displayNameSource < SOURCE_ORGANIZATION -> NUMBER
        providerLabel.isNullOrBlank() -> NUMBER
        else -> providerLabel
    }

    fun displayName(providerName: String?): String = providerName?.takeIf { it.isNotBlank() } ?: NO_NAME

    /**
     * The list's sections: "#" first, then A–Z, then every other label in the order the provider's sort first reaches
     * it — the jump grid's order (P2.5). Inside a section the provider's `SORT_KEY_PRIMARY` order is kept ([rows] come
     * in that order).
     */
    fun sections(rows: List<ContactRow>): List<ContactSection> {
        val byLabel = LinkedHashMap<String, MutableList<ContactRow>>()
        rows.forEach { byLabel.getOrPut(it.label) { mutableListOf() } += it }
        val order = buildList {
            if (NUMBER in byLabel) add(NUMBER)
            LATIN.forEach { if (it in byLabel) add(it) }
            byLabel.keys.forEach { if (it != NUMBER && it !in LATIN) add(it) }
        }
        return order.map { ContactSection(it, byLabel.getValue(it)) }
    }

    /** The grid's 28 cells. A letter's cell is live when its section exists; the globe when any other-script section does. */
    fun cells(sections: List<ContactSection>): List<JumpCell> {
        val present = sections.map { it.label }
        val other = present.firstOrNull { it != NUMBER && it !in LATIN }
        return buildList {
            add(JumpCell(NUMBER, NUMBER, false, NUMBER.takeIf { it in present }))
            LATIN.forEach { add(JumpCell(it, it, false, it.takeIf { l -> l in present })) }
            add(JumpCell(GLOBE, "", true, other))
        }
    }

    /** The grey disc's letter for a contact without a photo (P1.8): the name's first letter; "#" for a number or no name. */
    fun initial(name: String): String {
        if (name == NO_NAME) return NUMBER
        val first = name.firstOrNull { it.isLetterOrDigit() } ?: return NUMBER
        return if (first.isLetter()) first.uppercase() else NUMBER
    }
}

object PeopleSearch {
    /**
     * True when the query is a phone number as someone types one — digits with spaces, dashes, dots, brackets or a
     * leading plus — so it goes through the provider's phone lookup, which matches a number stored with spaces against
     * one typed without them.
     */
    fun isNumber(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty() || q.none { it.isDigit() }) return false
        return q.all { it.isDigit() || it in " -.()+" }
    }

    /** The number as the phone lookup takes it: the plus and the digits. */
    fun digits(query: String): String = query.trim().filter { it.isDigit() || it == '+' }

    /** The `[people] search …` line (Harness contracts). */
    fun line(query: String, local: Int, enterprise: Int): String = "search \"$query\": $local (+$enterprise enterprise)"
}

/** The kinds of field the card shows and the editor edits. */
enum class FieldKind(val id: String) {
    NAME("name"), PHONE("phone"), EMAIL("email"), ADDRESS("address"), COMPANY("company"), BIRTHDAY("birthday"), NOTES("notes")
}

/** A type a typed field can take: the provider's type number and W10M's word for it. */
data class FieldType(val type: Int, val word: String)

/**
 * The words for a field's type (r11/people.md §9: the card says "Call Mobile", "Email Personal"; the editor "Mobile
 * phone", "Personal email"). The numbers are `ContactsContract.CommonDataKinds`' type constants, which are part of the
 * provider's contract.
 */
object FieldTypes {
    const val TYPE_CUSTOM = 0
    const val PHONE_MOBILE = 2

    val PHONE = listOf(FieldType(2, "Mobile"), FieldType(1, "Home"), FieldType(3, "Work"), FieldType(7, "Other"))
    val EMAIL = listOf(FieldType(1, "Personal"), FieldType(2, "Work"), FieldType(3, "Other"))
    val ADDRESS = listOf(FieldType(1, "Home"), FieldType(2, "Work"), FieldType(3, "Other"))

    /** Types another app may have stored that the editor's list does not offer: shown by name, never rewritten. */
    private val PHONE_MORE = mapOf(
        4 to "Work fax", 5 to "Home fax", 6 to "Pager", 8 to "Callback", 9 to "Car", 10 to "Company", 12 to "Main",
        13 to "Other fax", 17 to "Work mobile", 18 to "Work pager", 19 to "Assistant",
    )
    private val EMAIL_MORE = mapOf(4 to "Mobile")

    fun options(kind: FieldKind): List<FieldType> = when (kind) {
        FieldKind.PHONE -> PHONE
        FieldKind.EMAIL -> EMAIL
        FieldKind.ADDRESS -> ADDRESS
        else -> emptyList()
    }

    /** The first type of a kind: what a new "+ field" row starts as. */
    fun default(kind: FieldKind): Int = options(kind).firstOrNull()?.type ?: 0

    fun word(kind: FieldKind, type: Int, customLabel: String?): String {
        if (type == TYPE_CUSTOM) return customLabel?.takeIf { it.isNotBlank() } ?: "Other"
        options(kind).firstOrNull { it.type == type }?.let { return it.word }
        val more = when (kind) {
            FieldKind.PHONE -> PHONE_MORE[type]
            FieldKind.EMAIL -> EMAIL_MORE[type]
            else -> null
        }
        return more ?: "Other"
    }

    /** The editor's accent label: "Mobile phone", "Personal email", "Home address". */
    fun editorLabel(kind: FieldKind, type: Int, customLabel: String?): String = when (kind) {
        FieldKind.PHONE -> "${word(kind, type, customLabel)} phone"
        FieldKind.EMAIL -> "${word(kind, type, customLabel)} email"
        FieldKind.ADDRESS -> "${word(kind, type, customLabel)} address"
        FieldKind.NAME -> "Name"
        FieldKind.COMPANY -> "Company"
        FieldKind.BIRTHDAY -> "Birthday"
        FieldKind.NOTES -> "Notes"
    }
}

/** One value on a contact, as the card and the editor read it. */
data class ContactField(
    val kind: FieldKind,
    val dataId: Long,
    val rawId: Long,
    val value: String,
    val type: Int = 0,
    val customLabel: String? = null,
)

/** The contact editor's layout rules (r11/people.md §5), free of Compose so they are tested on the JVM. */
object EditorRules {
    /**
     * Whether a 1-epx group rule (P4.7) is drawn between two neighbouring blocks of the editor, [above] and [below];
     * [fields] is how many fields of a kind the draft holds.
     *
     * W10M's two captures: a contact with only a name (G1) shows "+ Phone" and "+ Email" as two rows of ONE group, 44
     * epx apart with no rule between them (P4.6: 277 → 321, rules at 254 and 359); a contact with a mobile number and
     * an e-mail (G2) shows a rule between the phone block and the e-mail block (451). So that one rule is drawn only
     * when at least one of the two kinds has a field. A contact with a number and no e-mail, or an e-mail and no
     * number, is in neither capture: drawing the rule there is an approximation (H2). Every other boundary — name |
     * phone, e-mail | address, address | company — always has its rule.
     */
    fun ruleBetween(above: FieldKind, below: FieldKind, fields: (FieldKind) -> Int): Boolean =
        !(above == FieldKind.PHONE && below == FieldKind.EMAIL && fields(FieldKind.PHONE) == 0 && fields(FieldKind.EMAIL) == 0)
}

/**
 * One raw contact behind an aggregate, with its account. [otherProfile] is set where the row is read: true for the one
 * stand-in a work-profile contact's card carries, and it goes with the row to the guard.
 */
data class RawContact(val id: Long, val account: ContactAccount, val name: String?, val otherProfile: Boolean = false) {
    fun ref() = RawRef(id, account, otherProfile)
}

/** Everything a contact's card shows. */
data class ContactCard(
    val id: Long,
    val lookup: String,
    val name: String,
    val hasPhoto: Boolean,
    val raws: List<RawContact>,
    val fields: List<ContactField>,
    val enterprise: Boolean = false,
) {
    fun of(kind: FieldKind): List<ContactField> = fields.filter { it.kind == kind }
}

/** The kinds of action row a card carries (`people_card_action:<kind>:<n>`). */
enum class CardActionKind(val id: String) { CALL("call"), TEXT("text"), MAIL("mail"), MAP("map") }

/** One titled action row: "Call Mobile" over the number (two lines), or "Send message" alone (one line). */
data class CardAction(val kind: CardActionKind, val index: Int, val title: String, val detail: String?, val target: String)

object CardRules {
    /**
     * The card's action rows in W10M's order (r11/people.md P3.6): "Send message" first — one line, to the first
     * mobile number, else the first number — then each number's "Call <type>" and, for every number after the one
     * "Send message" uses, its own "Text <type>"; then each e-mail's "Email <type>" and each address's "Map <type>".
     */
    fun actions(card: ContactCard): List<CardAction> {
        val phones = card.of(FieldKind.PHONE)
        val out = mutableListOf<CardAction>()
        val messageIndex = preferredPhone(phones)
        if (messageIndex >= 0) out += CardAction(CardActionKind.TEXT, 0, "Send message", null, phones[messageIndex].value)
        var text = 1
        phones.forEachIndexed { i, p ->
            val word = FieldTypes.word(FieldKind.PHONE, p.type, p.customLabel)
            out += CardAction(CardActionKind.CALL, i, "Call $word", p.value, p.value)
            if (i != messageIndex) out += CardAction(CardActionKind.TEXT, text++, "Text $word", p.value, p.value)
        }
        card.of(FieldKind.EMAIL).forEachIndexed { i, e ->
            out += CardAction(CardActionKind.MAIL, i, "Email ${FieldTypes.word(FieldKind.EMAIL, e.type, e.customLabel)}", e.value, e.value)
        }
        card.of(FieldKind.ADDRESS).forEachIndexed { i, a ->
            out += CardAction(CardActionKind.MAP, i, "Map ${FieldTypes.word(FieldKind.ADDRESS, a.type, a.customLabel)}", a.value, a.value)
        }
        return out
    }

    /** The first mobile number's index, else the first number's, else -1. */
    fun preferredPhone(phones: List<ContactField>): Int {
        val mobile = phones.indexOfFirst { it.type == FieldTypes.PHONE_MOBILE }
        return if (mobile >= 0) mobile else if (phones.isEmpty()) -1 else 0
    }

    /** The read-only card's line (Decisions point (4); approximation, H21). */
    fun readOnlyLine(account: ContactAccount?, otherProfile: Boolean): String = when {
        otherProfile -> "This contact is in your work profile. It can't be changed here."
        else -> "This contact is in ${accountName(account)}. To change it, allow that account in Can edit."
    }

    /** An account as People names it to the user: its name, or "Phone" for the phone's own contacts. */
    fun accountName(account: ContactAccount?): String =
        if (account == null || account == phoneAccount) PHONE else account.name?.takeIf { it.isNotBlank() } ?: PHONE

    /**
     * The device's local account, as it was last read (`PeopleEditStore.localAccount`). AOSP's has no name; a phone
     * whose maker names it would otherwise show that raw name wherever the account is written out — the card, the
     * editor's header and "Save to", the filter, the groups (gate review A, finding 8).
     */
    @Volatile var phoneAccount: ContactAccount? = null

    const val PHONE = "Phone"

    /** A stored birthday as the card reads it: "5 March 1990", or "5 March" for one stored without a year. */
    fun birthday(stored: String): String {
        val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val full = Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(stored.trim())
        val noYear = Regex("""--(\d{2})-(\d{2})""").matchEntire(stored.trim())
        val (year, month, day) = when {
            full != null -> Triple(full.groupValues[1], full.groupValues[2].toInt(), full.groupValues[3].toInt())
            noYear != null -> Triple(null, noYear.groupValues[1].toInt(), noYear.groupValues[2].toInt())
            else -> return stored
        }
        if (month !in 1..12 || day !in 1..31) return stored
        return listOfNotNull(day.toString(), months[month - 1], year).joinToString(" ")
    }
}

/** A group as the GROUPS pivot lists it. */
data class ContactGroup(val id: Long, val title: String, val account: ContactAccount, val members: Int)

object GroupRules {
    /** A number as it is dialled: its digits, with a plus, `*` and `#` kept — so several join into one `smsto:` list. */
    fun dialable(number: String): String = number.filter { it.isDigit() || it in "+*#" }

    /**
     * "Text the group" (T16-14): `smsto:<n1>;<n2>…`, each member's first mobile number; a member with no mobile number
     * is left out. Null when no number is left — the page then shows a notice, not a compose. The card's Text is the
     * same with one number.
     */
    fun smsTo(memberMobiles: List<String?>): String? {
        val numbers = memberMobiles.mapNotNull { it?.let(::dialable)?.takeIf { n -> n.isNotEmpty() } }.distinct()
        return if (numbers.isEmpty()) null else "smsto:" + numbers.joinToString(";")
    }

    /** What the page says when there is nobody to text (approximation, H16). */
    fun textNotice(memberCount: Int): String =
        if (memberCount == 0) "This group has no members to text." else "Nobody in this group has a mobile number."

    fun membersLine(count: Int): String = if (count == 1) "1 member" else "$count members"
}

/** One entry of the SIM's phonebook (`content://icc/adn`). */
data class SimContact(val index: Int, val name: String, val number: String?)

object SimRules {
    /** An entry can be imported only with a number; one without is skipped. */
    fun importable(contact: SimContact): Boolean = !contact.number.isNullOrBlank()

    fun line(imported: Int, of: Int): String = "sim import: $imported of $of"
}

/** "Filter contact list": which accounts and groups the list hides, and W10M's "Hide contacts without phone numbers". */
data class ContactFilter(
    val hiddenAccounts: Set<ContactAccount> = emptySet(),
    val hiddenGroups: Set<Long> = emptySet(),
    val hideWithoutPhones: Boolean = false,
) {
    val isOn: Boolean get() = hiddenAccounts.isNotEmpty() || hiddenGroups.isNotEmpty() || hideWithoutPhones
}

/** What the filter needs to know about one contact. */
data class ContactMembership(val accounts: Set<ContactAccount>, val groups: Set<Long>)

object FilterRules {
    /**
     * A contact is listed unless every account it lives in is hidden, or it belongs to groups and every one of them is
     * hidden, or it has no number while those are hidden. A contact the filter knows nothing about is listed.
     */
    fun shows(row: ContactRow, membership: ContactMembership?, filter: ContactFilter): Boolean {
        if (filter.hideWithoutPhones && !row.hasPhone) return false
        if (membership == null) return true
        if (membership.accounts.isNotEmpty() && membership.accounts.all { it in filter.hiddenAccounts }) return false
        if (membership.groups.isNotEmpty() && membership.groups.all { it in filter.hiddenGroups }) return false
        return true
    }

    /** The line under the search box while a filter is on (r11/people.md P1.4): the phrase after "Showing ". */
    fun caption(filter: ContactFilter): String? = when {
        !filter.isOn -> null
        filter.hideWithoutPhones && filter.hiddenAccounts.isEmpty() && filter.hiddenGroups.isEmpty() -> "contacts with phone numbers"
        else -> "some contacts"
    }
}

/** The card's photo, the list's avatars and the tile's bubbles are decoded at about the size they are drawn. */
object ContactPhotoRules {
    /** The power-of-two sample that brings a [width] x [height] image to about [target] px on its short side. */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }
}

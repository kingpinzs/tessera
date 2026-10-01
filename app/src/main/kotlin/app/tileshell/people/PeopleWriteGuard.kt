package app.tileshell.people

/**
 * An account as the Contacts provider names it on a raw contact or a group: `account_name` + `account_type`. Both null
 * is how AOSP stores a phone-only contact; a phone whose maker names its local account stores that name and type.
 */
data class ContactAccount(val name: String?, val type: String?) {
    /** The account as People's tags and `people_edit.json` spell it: `<account type>:<account name>`. */
    val id: String get() = "${type.orEmpty()}:${name.orEmpty()}"

    /** True when the provider gave no account at all (the AOSP form of a phone-only row). */
    val isNull: Boolean get() = name == null && type == null
}

/**
 * What People may write (Q-16-3, 2026-09-30): the phone's own contacts always, an account only when the user ticked it
 * on "Can edit", nothing else.
 *
 * [local] is the device's local account — what `ContactsContract.RawContacts.getLocalAccountName` /
 * `getLocalAccountType` return (null and null on AOSP; a maker's own name and type on a phone that names it). It is
 * read from the device, never written here as a literal, so a phone-only contact is recognised on both kinds of phone.
 * [allowed] is the "Can edit" list, nothing in it by default.
 */
data class EditPolicy(val local: ContactAccount, val allowed: Set<ContactAccount>) {
    /** A phone-only account: the device's local one. */
    fun isPhone(account: ContactAccount): Boolean = account == local

    /** People may create and change contacts and groups in [account]. */
    fun canWrite(account: ContactAccount): Boolean = isPhone(account) || account in allowed
}

/**
 * One raw contact as the write layer resolved it from the provider: its row id, its account, and whether it is another
 * profile's row (a work-profile contact found by the enterprise search), which is never writable.
 */
data class RawRef(val id: Long, val account: ContactAccount, val otherProfile: Boolean = false)

enum class WriteOp(val word: String) { INSERT("insert"), UPDATE("update"), DELETE("delete") }

/** Where a new raw contact comes from. Each has its own rule for the account it may land in. */
enum class NewContactSource {
    /** The editor's New: the phone, or an allowed account chosen on `people_editor_account`. */
    EDITOR,

    /** Import from SIM: always the phone. */
    SIM_IMPORT,

    /** An `ACTION_INSERT` / `ACTION_INSERT_OR_EDIT` prefill: starts on the phone, whatever account its caller named. */
    INSERT_PREFILL,
}

/** Every kind of ContactsContract write People makes. The one write layer builds one of these for each op and asks the guard. */
sealed interface PeopleWrite {
    /** A new raw contact in [account]. */
    data class NewRawContact(val account: ContactAccount, val source: NewContactSource) : PeopleWrite

    /** An update or a delete of one raw contact's own row. */
    data class RawContactRow(val op: WriteOp, val raw: RawRef) : PeopleWrite

    /** An insert, update or delete of a data row (a field, the photo, a group membership) on [raw]. */
    data class DataRow(val op: WriteOp, val raw: RawRef) : PeopleWrite

    /** Deleting a contact: the aggregate, so every raw contact behind it. */
    data class DeleteContact(val raws: List<RawRef>) : PeopleWrite

    /** Updating a `Contacts` column that syncs upstream (STARRED, for example): a write to every raw contact behind it. */
    data class ContactColumn(val column: String, val raws: List<RawRef>) : PeopleWrite

    /** Link (`TYPE_KEEP_TOGETHER`) or Unlink (`TYPE_KEEP_SEPARATE`): an `AggregationExceptions` row, local to the phone. */
    data class Aggregation(val a: RawRef, val b: RawRef, val together: Boolean) : PeopleWrite

    /** Creating, renaming or deleting a group in [account] (the group's own account). */
    data class GroupRow(val op: WriteOp, val account: ContactAccount) : PeopleWrite
}

sealed interface GuardVerdict {
    data object Allowed : GuardVerdict

    /** [rawId] is the raw contact that is not editable, when the refusal is about one. */
    data class Refused(val rawId: Long?) : GuardVerdict
}

/**
 * The People write guard (Decisions "the People write guard and the Can edit list", Trust (b)): the one rule every
 * `ContactsContract` insert, update and delete People makes is checked against, free of Android types so each allowed
 * case and each refusal is proven on the JVM (PeopleWriteGuardTest).
 *
 * Editable is decided per raw contact: a phone-only raw contact always; a raw contact whose account is on the "Can
 * edit" list; nothing else — another profile's contact never.
 */
object PeopleWriteGuard {
    fun editable(raw: RawRef, policy: EditPolicy): Boolean = !raw.otherProfile && policy.canWrite(raw.account)

    fun check(write: PeopleWrite, policy: EditPolicy): GuardVerdict = when (write) {
        is PeopleWrite.NewRawContact -> when (write.source) {
            // A SIM import goes to the phone and nowhere else, even when an account is allowed.
            NewContactSource.SIM_IMPORT -> if (policy.isPhone(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)
            NewContactSource.EDITOR, NewContactSource.INSERT_PREFILL ->
                if (policy.canWrite(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)
        }
        is PeopleWrite.RawContactRow -> one(write.raw, policy)
        is PeopleWrite.DataRow -> one(write.raw, policy)
        is PeopleWrite.DeleteContact -> all(write.raws, policy)
        is PeopleWrite.ContactColumn -> all(write.raws, policy)
        // AggregationExceptions are local to the phone and reach no account: allowed on any contact.
        is PeopleWrite.Aggregation -> GuardVerdict.Allowed
        is PeopleWrite.GroupRow -> if (policy.canWrite(write.account)) GuardVerdict.Allowed else GuardVerdict.Refused(null)
    }

    private fun one(raw: RawRef, policy: EditPolicy): GuardVerdict =
        if (editable(raw, policy)) GuardVerdict.Allowed else GuardVerdict.Refused(raw.id)

    /** Every raw contact must be editable; an aggregate with none behind it is not a write People can make. */
    private fun all(raws: List<RawRef>, policy: EditPolicy): GuardVerdict {
        if (raws.isEmpty()) return GuardVerdict.Refused(null)
        val blocked = raws.firstOrNull { !editable(it, policy) }
        return if (blocked == null) GuardVerdict.Allowed else GuardVerdict.Refused(blocked.id)
    }

    /**
     * What a contact's card offers (Decisions point (4)): Edit when at least one raw contact is editable, Delete only
     * when every one is. A contact with none editable shows neither, and the `people_card_readonly` line.
     */
    fun cardActions(raws: List<RawRef>, policy: EditPolicy): CardActions {
        val editableCount = raws.count { editable(it, policy) }
        return CardActions(
            edit = editableCount > 0,
            delete = raws.isNotEmpty() && editableCount == raws.size,
            readOnlyAccount = if (editableCount == 0) raws.firstOrNull { !editable(it, policy) }?.account else null,
            otherProfile = raws.isNotEmpty() && raws.all { it.otherProfile },
        )
    }
}

/** The card's Edit and Delete, and the account its read-only line names when nothing is editable. */
data class CardActions(val edit: Boolean, val delete: Boolean, val readOnlyAccount: ContactAccount?, val otherProfile: Boolean)

/**
 * The "Can edit" list's own rules (Decisions point (2)), pure: its rows are the accounts the provider names, and an
 * account the provider no longer names is dropped from the stored list, so a removed and re-added account starts NOT
 * allowed.
 */
object CanEditRules {
    /** The distinct non-null account name + type pairs, in a stable order. The phone has no row: it is always editable. */
    fun rows(named: Collection<ContactAccount>, policy: EditPolicy): List<ContactAccount> =
        named.asSequence()
            .filter { it.name != null && it.type != null && !policy.isPhone(it) }
            .distinct()
            .sortedWith(compareBy({ it.type }, { it.name }))
            .toList()

    /** The stored list less every account the provider no longer names. */
    fun prune(allowed: Set<ContactAccount>, named: Collection<ContactAccount>): Set<ContactAccount> {
        val still = named.toSet()
        return allowed.filterTo(LinkedHashSet()) { it in still }
    }

    /** Ticking or unticking one row. Only an account with a name and a type can be stored. */
    fun toggle(allowed: Set<ContactAccount>, account: ContactAccount, on: Boolean): Set<ContactAccount> = when {
        account.name == null || account.type == null -> allowed
        on -> allowed + account
        else -> allowed - account
    }
}

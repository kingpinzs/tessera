package app.tileshell.people

import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts

/**
 * One row write as the write layer asks the Contacts provider for it, free of Android types, so a test reads exactly
 * what was asked: the URI, the columns, the selection.
 */
data class RowWrite(
    val op: WriteOp,
    val uri: String,
    val values: Map<String, Any?> = emptyMap(),
    /** In a batch: the column filled with the id that the batch's row at this index made (a new contact's fields name its raw contact so). */
    val backReference: Pair<String, Int>? = null,
    val where: String? = null,
    val args: List<String> = emptyList(),
)

/**
 * Everything the write layer needs of the phone (the fix round's F15): what it reads before it asks the guard, and the
 * five ways a row of the Contacts provider changes. The real one is PeopleWriter's `ResolverContacts`, over the
 * ContentResolver; a test's one records every write, so "the guard refused, nothing was written" is something a JVM
 * test can see.
 */
interface ContactsPort {
    /** The device's local account and the "Can edit" list, as they stand now. */
    fun policy(): EditPolicy

    /** WRITE_CONTACTS is held. */
    fun mayWrite(): Boolean

    // ---- what an op resolves from the provider before the guard is asked

    /** The raw contacts behind a contact, with their accounts; empty when the contact is gone. */
    fun rawContactsOf(contactId: Long): List<RawRef>

    /** The raw contacts named by [ids] as the provider holds them now; one that is gone or marked deleted is absent. */
    fun rawContacts(ids: List<Long>): Map<Long, RawRef>

    /** Which raw contact each data row belongs to; a row that is gone is absent. Throws when the provider cannot be read. */
    fun dataOwners(dataIds: List<Long>): Map<Long, Long>

    /** One group, or null when it is gone or marked deleted. */
    fun group(groupId: Long): ContactGroup?

    /** Whether [rawId] holds a membership row for [groupId]. Throws when the provider cannot be read. */
    fun isMember(rawId: Long, groupId: Long): Boolean

    // ---- the writes: nothing else changes a row

    /** The new row's id, or null when the provider made none. */
    fun insert(uri: String, values: Map<String, Any?>): Long?

    /** The number of rows changed. */
    fun update(uri: String, values: Map<String, Any?>, where: String? = null, args: List<String> = emptyList()): Int

    /** The number of rows removed. */
    fun delete(uri: String, where: String? = null, args: List<String> = emptyList()): Int

    /** All of [rows] or none; for each, the id an insert made, else null. */
    fun applyBatch(rows: List<RowWrite>): List<Long?>

    /** Writes [bytes] to the provider's stream at [uri] (a raw contact's display photo). */
    fun writeStream(uri: String, bytes: ByteArray)

    /** One `[people]` diagnostics line. */
    fun line(text: String)
}

/**
 * The write layer's rules (Q-16-3; Decisions "the People write guard and the Can edit list" point (3); Trust (b)),
 * over a [ContactsPort] and free of Android types, so each op's two halves — refused: nothing written; allowed: exactly
 * these rows — are proven on the JVM (PeopleWriterTest).
 *
 * Every op does the same four things in the same order: it RESOLVES from the provider the raw contacts (or the group's
 * account) it would touch — never from what a page or an intent claims; it ASKS [PeopleWriteGuard] with the device's
 * local account and the "Can edit" list as they stand now; it checks WRITE_CONTACTS; then it writes, and says what
 * happened: `[people] write <op> raw=<id>: ok | failed <err> | refused (not allowed)`. A refusal or a failure writes
 * nothing at all — an edit is one batch.
 */
class PeopleWrites(private val port: ContactsPort) {

    // ---------------------------------------------------------------------------------------------- contacts

    /**
     * A new contact in [account]: the phone unless the editor's account choice named an allowed account. A SIM import
     * and an INSERT prefill say so in [source], which the guard holds to its own rule.
     */
    fun create(account: ContactAccount, source: NewContactSource, fields: List<FieldEdit>, photo: ByteArray? = null): WriteResult {
        if (refused(PeopleWrite.NewRawContact(account, source))) return refusedLine("insert", "new")
        if (!port.mayWrite()) return failedLine("insert", "new", NOT_HELD, needsGrant = true)
        val rows = ArrayList<RowWrite>()
        rows += RowWrite(WriteOp.INSERT, RAW_CONTACTS, linkedMapOf(RawContacts.ACCOUNT_NAME to account.name, RawContacts.ACCOUNT_TYPE to account.type))
        fields.filter { it.value.isNotBlank() }.forEach { f ->
            rows += RowWrite(WriteOp.INSERT, DATA, values(f, insert = true), backReference = Data.RAW_CONTACT_ID to 0)
        }
        val rawId = runCatching { port.applyBatch(rows).firstOrNull() ?: error("no raw contact was made") }
            .getOrElse { return failedLine("insert", "new", describe(it)) }
        port.line("write insert raw=$rawId: ok")
        if (photo != null) {
            // The photo asks the guard itself, about the raw contact this op made as the provider holds it now.
            val result = writePhoto(rawId, photo)
            if (result !is WriteResult.Ok) return result
        }
        return WriteResult.Ok(rawId = rawId)
    }

    /**
     * An edit of an existing contact: field changes and a photo change, each on the raw contact it belongs to. Every
     * raw contact the edit touches must be editable; otherwise nothing is written.
     */
    fun update(edits: List<FieldEdit>, photo: PhotoEdit?): WriteResult {
        // Which raw contact each existing data row REALLY belongs to: read from the provider, not from the draft.
        val dataIds = edits.mapNotNull { it.dataId }
        val owner: Map<Long, Long> =
            if (dataIds.isEmpty()) emptyMap()
            else runCatching { port.dataOwners(dataIds) }
                .getOrElse { return failedLine("update", edits.firstNotNullOfOrNull { it.rawId }?.toString() ?: "unknown", describe(it)) }
        data class Step(val op: WriteOp, val rawId: Long, val edit: FieldEdit)
        val steps = ArrayList<Step>()
        for (e in edits) {
            if (e.dataId == null) {
                if (e.value.isBlank()) continue
                val raw = e.rawId ?: return failedLine("update", "unknown", "a new field names no raw contact")
                steps += Step(WriteOp.INSERT, raw, e)
            } else {
                val raw = owner[e.dataId] ?: return failedLine("update", e.rawId?.toString() ?: "unknown", "the field is gone")
                steps += Step(if (e.value.isBlank()) WriteOp.DELETE else WriteOp.UPDATE, raw, e)
            }
        }
        val photoRaw = photo?.rawId
        if (photo != null && photoRaw == null) return failedLine("update", "unknown", "the photo names no raw contact")
        val rawIds = (steps.map { it.rawId } + listOfNotNull(photoRaw)).distinct()
        if (rawIds.isEmpty()) return WriteResult.Ok()
        val raws = port.rawContacts(rawIds)
        rawIds.firstOrNull { it !in raws }?.let { return failedLine("update", it.toString(), "the raw contact is gone") }

        for (s in steps) if (refused(PeopleWrite.DataRow(s.op, raws.getValue(s.rawId)))) return refusedLine("update", s.rawId.toString())
        // The photo's raw contact is asked about before any field is written, so a refusal leaves nothing half done.
        if (photoRaw != null && refused(PeopleWrite.DataRow(WriteOp.UPDATE, raws.getValue(photoRaw)))) return refusedLine("update", photoRaw.toString())
        if (!port.mayWrite()) return failedLine("update", rawIds.first().toString(), NOT_HELD, needsGrant = true)

        val rows = steps.map { s ->
            val row = listOf(s.edit.dataId.toString(), s.rawId.toString())
            when (s.op) {
                WriteOp.INSERT -> RowWrite(WriteOp.INSERT, DATA, linkedMapOf<String, Any?>(Data.RAW_CONTACT_ID to s.rawId) + values(s.edit, insert = true))
                // The row is addressed with its raw contact, so a row that moved since the resolve matches nothing.
                WriteOp.UPDATE -> RowWrite(WriteOp.UPDATE, DATA, values(s.edit, insert = false), where = DATA_ROW_OF_RAW, args = row)
                WriteOp.DELETE -> RowWrite(WriteOp.DELETE, DATA, where = DATA_ROW_OF_RAW, args = row)
            }
        }
        if (rows.isNotEmpty()) {
            runCatching { port.applyBatch(rows) }.onFailure { return failedLine("update", rawIds.first().toString(), describe(it)) }
        }
        if (photo != null && photoRaw != null) {
            val result = if (photo.jpeg != null) writePhoto(photoRaw, photo.jpeg) else removePhoto(photoRaw)
            if (result !is WriteResult.Ok) return result
        }
        rawIds.forEach { port.line("write update raw=$it: ok") }
        return WriteResult.Ok()
    }

    /** Deleting a contact deletes the aggregate, so every raw contact behind it must be editable. */
    fun delete(contactId: Long): WriteResult {
        val raws = port.rawContactsOf(contactId)
        if (raws.isEmpty()) return failedLine("delete", "unknown", "the contact is gone")
        val verdict = PeopleWriteGuard.check(PeopleWrite.DeleteContact(raws), port.policy())
        if (verdict is GuardVerdict.Refused) return refusedLine("delete", (verdict.rawId ?: raws.first().id).toString())
        if (!port.mayWrite()) return failedLine("delete", raws.first().id.toString(), NOT_HELD, needsGrant = true)
        runCatching { port.applyBatch(raws.map { RowWrite(WriteOp.DELETE, "$RAW_CONTACTS/${it.id}") }) }
            .onFailure { return failedLine("delete", raws.first().id.toString(), describe(it)) }
        raws.forEach { port.line("write delete raw=${it.id}: ok") }
        return WriteResult.Ok()
    }

    // ---------------------------------------------------------------------------------------------- link / unlink

    /**
     * Link: every raw contact behind [contactA] is kept together with every one behind [contactB]
     * (`TYPE_KEEP_TOGETHER`). Aggregation exceptions are local to the phone and reach no account, so the guard allows
     * them on any contact — it is still asked. `[people] link <a>+<b>: ok | failed`.
     */
    fun link(contactA: Long, contactB: Long): WriteResult {
        val a = port.rawContactsOf(contactA)
        val b = port.rawContactsOf(contactB)
        val label = "${a.firstOrNull()?.id ?: "gone"}+${b.firstOrNull()?.id ?: "gone"}"
        if (a.isEmpty() || b.isEmpty() || contactA == contactB) return linkFailed("link", label, "a contact is gone")
        return aggregate("link", label, a.flatMap { x -> b.map { y -> x to y } }, together = true)
    }

    /** Unlink: [rawId] is kept separate from every other raw contact behind [contactId] (`TYPE_KEEP_SEPARATE`). */
    fun unlink(contactId: Long, rawId: Long): WriteResult {
        val raws = port.rawContactsOf(contactId)
        val one = raws.firstOrNull { it.id == rawId }
        val others = raws.filter { it.id != rawId }
        val label = "$rawId+${others.firstOrNull()?.id ?: "gone"}"
        if (one == null || others.isEmpty()) return linkFailed("unlink", label, "nothing is linked")
        return aggregate("unlink", label, others.map { one to it }, together = false)
    }

    private fun aggregate(word: String, label: String, pairs: List<Pair<RawRef, RawRef>>, together: Boolean): WriteResult {
        val policy = port.policy()
        if (pairs.any { PeopleWriteGuard.check(PeopleWrite.Aggregation(it.first, it.second, together), policy) is GuardVerdict.Refused }) {
            port.line("$word $label: failed")
            return WriteResult.Refused
        }
        if (!port.mayWrite()) {
            port.line("$word $label: failed")
            return WriteResult.Failed(NOT_HELD, needsGrant = true)
        }
        val type = if (together) AggregationExceptions.TYPE_KEEP_TOGETHER else AggregationExceptions.TYPE_KEEP_SEPARATE
        val rows = pairs.map { (x, y) ->
            RowWrite(
                WriteOp.UPDATE, AGGREGATION,
                linkedMapOf(AggregationExceptions.TYPE to type, AggregationExceptions.RAW_CONTACT_ID1 to minOf(x.id, y.id), AggregationExceptions.RAW_CONTACT_ID2 to maxOf(x.id, y.id)),
            )
        }
        return runCatching {
            port.applyBatch(rows)
            port.line("$word $label: ok")
            WriteResult.Ok() as WriteResult
        }.getOrElse { linkFailed(word, label, describe(it)) }
    }

    private fun linkFailed(word: String, label: String, error: String): WriteResult {
        port.line("$word $label: failed")
        return WriteResult.Failed(error)
    }

    // ---------------------------------------------------------------------------------------------- SIM

    /**
     * Import from SIM: each chosen entry that has a number becomes a new contact on the phone — never in an account.
     * Writes `[people] sim import: n of m`, also "0 of 0" when the SIM lists nothing.
     */
    fun importSim(chosen: List<SimContact>): WriteResult {
        val local = port.policy().local
        var imported = 0
        var failure: WriteResult.Failed? = null
        for (entry in chosen) {
            if (!SimRules.importable(entry)) continue
            val fields = listOfNotNull(
                entry.name.takeIf { it.isNotBlank() }?.let { FieldEdit(FieldKind.NAME, null, null, it) },
                FieldEdit(FieldKind.PHONE, null, null, entry.number!!.trim(), FieldTypes.PHONE_MOBILE),
            )
            when (val result = create(local, NewContactSource.SIM_IMPORT, fields)) {
                is WriteResult.Ok -> imported++
                is WriteResult.Failed -> { failure = result; if (result.needsGrant) break }
                WriteResult.Refused -> Unit
            }
        }
        port.line(SimRules.line(imported, chosen.size))
        return failure?.takeIf { imported == 0 } ?: WriteResult.Ok()
    }

    // ---------------------------------------------------------------------------------------------- groups

    /** A new group in [account]: the phone unless an allowed account was chosen. `[people] group create <id>: ok | failed <err>`. */
    fun createGroup(title: String, account: ContactAccount): WriteResult {
        if (title.isBlank()) return groupFailed("create", "new", "a group needs a name")
        if (refused(PeopleWrite.GroupRow(WriteOp.INSERT, account))) return groupFailed("create", "new", "refused (not allowed)", refused = true)
        if (!port.mayWrite()) return groupFailed("create", "new", NOT_HELD, needsGrant = true)
        return runCatching {
            val values = linkedMapOf<String, Any?>(Groups.TITLE to title.trim(), Groups.ACCOUNT_NAME to account.name, Groups.ACCOUNT_TYPE to account.type, Groups.GROUP_VISIBLE to 1)
            val id = port.insert(GROUPS, values) ?: error("no group was made")
            port.line("group create $id: ok")
            WriteResult.Ok(id = id) as WriteResult
        }.getOrElse { groupFailed("create", "new", describe(it)) }
    }

    fun renameGroup(groupId: Long, title: String): WriteResult {
        if (title.isBlank()) return groupFailed("rename", groupId.toString(), "a group needs a name")
        val group = port.group(groupId) ?: return groupFailed("rename", groupId.toString(), "the group is gone")
        if (refused(PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return groupFailed("rename", groupId.toString(), "refused (not allowed)", refused = true)
        if (!port.mayWrite()) return groupFailed("rename", groupId.toString(), NOT_HELD, needsGrant = true)
        return runCatching {
            val n = port.update("$GROUPS/$groupId", linkedMapOf(Groups.TITLE to title.trim()))
            if (n == 0) error("the group is gone")
            port.line("group rename $groupId: ok")
            WriteResult.Ok(id = groupId) as WriteResult
        }.getOrElse { groupFailed("rename", groupId.toString(), describe(it)) }
    }

    /**
     * Deletes a group; its members stay contacts. The provider only MARKS a group deleted (`deleted=1`, `dirty=1`) and
     * leaves the row for the account's sync adapter to remove. A phone group has no sync adapter, so its row would
     * stay in the provider for good: it is removed outright, through the sync-adapter URI. An allowed account's group
     * is deleted the plain way, for that account's own adapter to finish. The guard is asked about the very write
     * that is made — the URI follows from what it was asked.
     */
    fun deleteGroup(groupId: Long): WriteResult {
        val group = port.group(groupId) ?: return groupFailed("delete", groupId.toString(), "the group is gone")
        val write = PeopleWrite.GroupRow(WriteOp.DELETE, group.account, viaSyncAdapter = port.policy().isPhone(group.account))
        if (refused(write)) return groupFailed("delete", groupId.toString(), "refused (not allowed)", refused = true)
        if (!port.mayWrite()) return groupFailed("delete", groupId.toString(), NOT_HELD, needsGrant = true)
        return runCatching {
            val uri = "$GROUPS/$groupId" + if (write.viaSyncAdapter) "?${ContactsContract.CALLER_IS_SYNCADAPTER}=true" else ""
            val n = port.delete(uri)
            if (n == 0) error("the group is gone")
            port.line("group delete $groupId: ok")
            WriteResult.Ok(id = groupId) as WriteResult
        }.getOrElse { groupFailed("delete", groupId.toString(), describe(it)) }
    }

    /**
     * Group membership is a data row on the member's raw contact in the group's own account, so it is written only
     * where that raw contact is editable — which the group's account being writable makes it.
     */
    fun setMember(groupId: Long, contactId: Long, member: Boolean): WriteResult {
        val group = port.group(groupId) ?: return failedLine("update", "unknown", "the group is gone")
        val raw = port.rawContactsOf(contactId).firstOrNull { it.account == group.account }
            ?: return failedLine("update", "unknown", "the contact is not in the group's account")
        if (refused(PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return refusedLine("update", raw.id.toString())
        if (refused(PeopleWrite.DataRow(if (member) WriteOp.INSERT else WriteOp.DELETE, raw))) return refusedLine("update", raw.id.toString())
        if (!port.mayWrite()) return failedLine("update", raw.id.toString(), NOT_HELD, needsGrant = true)
        return runCatching {
            if (member) {
                if (!port.isMember(raw.id, groupId)) {
                    port.insert(
                        DATA,
                        linkedMapOf(Data.RAW_CONTACT_ID to raw.id, Data.MIMETYPE to CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, CommonDataKinds.GroupMembership.GROUP_ROW_ID to groupId),
                    ) ?: error("no membership was made")
                }
            } else {
                port.delete(DATA, MEMBERSHIP_OF_RAW, listOf(raw.id.toString(), CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()))
            }
            port.line("write update raw=${raw.id}: ok")
            WriteResult.Ok(rawId = raw.id) as WriteResult
        }.getOrElse { failedLine("update", raw.id.toString(), describe(it)) }
    }

    // ---------------------------------------------------------------------------------------------- inside

    /**
     * The photo as a photo data row: written through the raw contact's display-photo stream, which the provider keeps
     * as that row. It asks the guard itself, about the raw contact as the provider holds it at this moment — not as its
     * caller resolved it a write earlier.
     */
    private fun writePhoto(rawId: Long, jpeg: ByteArray): WriteResult {
        photoRefusal(rawId, WriteOp.UPDATE)?.let { return it }
        return runCatching {
            port.writeStream("$RAW_CONTACTS/$rawId/${RawContacts.DisplayPhoto.CONTENT_DIRECTORY}", jpeg)
            WriteResult.Ok(rawId = rawId) as WriteResult
        }.getOrElse { failedLine("update", rawId.toString(), describe(it)) }
    }

    /** Removing a photo deletes the raw contact's photo data row. The guard is asked here too. */
    private fun removePhoto(rawId: Long): WriteResult {
        photoRefusal(rawId, WriteOp.DELETE)?.let { return it }
        return runCatching {
            port.delete(DATA, PHOTO_OF_RAW, listOf(rawId.toString(), CommonDataKinds.Photo.CONTENT_ITEM_TYPE))
            WriteResult.Ok(rawId = rawId) as WriteResult
        }.getOrElse { failedLine("update", rawId.toString(), describe(it)) }
    }

    /** Why a photo change on [rawId] may not be written now, or null when it may. */
    private fun photoRefusal(rawId: Long, op: WriteOp): WriteResult? {
        val raw = port.rawContacts(listOf(rawId))[rawId] ?: return failedLine("update", rawId.toString(), "the raw contact is gone")
        if (refused(PeopleWrite.DataRow(op, raw))) return refusedLine("update", rawId.toString())
        if (!port.mayWrite()) return failedLine("update", rawId.toString(), NOT_HELD, needsGrant = true)
        return null
    }

    /** A field as the provider's columns hold it. */
    private fun values(edit: FieldEdit, insert: Boolean): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        val value = edit.value.trim()
        when (edit.kind) {
            FieldKind.NAME -> {
                if (insert) put(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                // The display name alone: the provider splits it into its parts again.
                put(CommonDataKinds.StructuredName.DISPLAY_NAME, value)
            }
            FieldKind.PHONE -> {
                if (insert) put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.Phone.NUMBER, value)
                put(CommonDataKinds.Phone.TYPE, edit.type)
                put(CommonDataKinds.Phone.LABEL, edit.customLabel.takeIf { edit.type == FieldTypes.TYPE_CUSTOM })
            }
            FieldKind.EMAIL -> {
                if (insert) put(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.Email.ADDRESS, value)
                put(CommonDataKinds.Email.TYPE, edit.type)
                put(CommonDataKinds.Email.LABEL, edit.customLabel.takeIf { edit.type == FieldTypes.TYPE_CUSTOM })
            }
            FieldKind.ADDRESS -> {
                if (insert) put(Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, value)
                put(CommonDataKinds.StructuredPostal.TYPE, edit.type)
                put(CommonDataKinds.StructuredPostal.LABEL, edit.customLabel.takeIf { edit.type == FieldTypes.TYPE_CUSTOM })
            }
            FieldKind.COMPANY -> {
                if (insert) {
                    put(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    put(CommonDataKinds.Organization.TYPE, CommonDataKinds.Organization.TYPE_WORK)
                }
                put(CommonDataKinds.Organization.COMPANY, value)
            }
            FieldKind.BIRTHDAY -> {
                if (insert) {
                    put(Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    put(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY)
                }
                put(CommonDataKinds.Event.START_DATE, value)
            }
            FieldKind.NOTES -> {
                if (insert) put(Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                put(CommonDataKinds.Note.NOTE, value)
            }
        }
    }

    private fun refused(write: PeopleWrite): Boolean = PeopleWriteGuard.check(write, port.policy()) is GuardVerdict.Refused

    private fun refusedLine(op: String, raw: String): WriteResult {
        port.line("write $op raw=$raw: refused (not allowed)")
        return WriteResult.Refused
    }

    private fun failedLine(op: String, raw: String, error: String, needsGrant: Boolean = false): WriteResult.Failed {
        port.line("write $op raw=$raw: failed $error")
        return WriteResult.Failed(error, needsGrant)
    }

    private fun groupFailed(op: String, id: String, error: String, needsGrant: Boolean = false, refused: Boolean = false): WriteResult {
        port.line("group $op $id: failed $error")
        return if (refused) WriteResult.Refused else WriteResult.Failed(error, needsGrant)
    }

    /** An error as the ring states it: the exception's kind, never a caller's text. */
    private fun describe(error: Throwable): String = error.message?.takeIf { it.length <= 80 && '\n' !in it } ?: error.javaClass.simpleName

    companion object {
        const val NOT_HELD = "WRITE_CONTACTS not held"

        /** The Contacts provider's tables, as URIs. */
        private const val BASE = "content://${PeopleIntents.AUTHORITY}"
        const val RAW_CONTACTS = "$BASE/raw_contacts"
        const val DATA = "$BASE/data"
        const val AGGREGATION = "$BASE/aggregation_exceptions"
        const val GROUPS = "$BASE/groups"

        const val DATA_ROW_OF_RAW = "${Data._ID}=? AND ${Data.RAW_CONTACT_ID}=?"
        const val PHOTO_OF_RAW = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?"
        const val MEMBERSHIP_OF_RAW = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID}=?"
    }
}

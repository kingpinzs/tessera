package app.tileshell.people

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import app.tileshell.diag.Diagnostics

/** What a write came to. */
sealed interface WriteResult {
    /** [rawId] is the raw contact a create made; [id] the group a group create made. */
    data class Ok(val rawId: Long? = null, val id: Long? = null) : WriteResult

    /** [needsGrant]: WRITE_CONTACTS is not held, so the page offers the grant in place. */
    data class Failed(val error: String, val needsGrant: Boolean = false) : WriteResult

    /** The write guard refused it: an account that is not on "Can edit". Nothing was written. */
    data object Refused : WriteResult
}

/**
 * One change an edit makes to a contact's fields. [dataId] null is a new field on [rawId] (null for a contact that
 * does not exist yet); an existing field with a blank [value] is removed; anything else is updated.
 */
data class FieldEdit(
    val kind: FieldKind,
    val dataId: Long?,
    val rawId: Long?,
    val value: String,
    val type: Int = 0,
    val customLabel: String? = null,
)

/** A photo change on one raw contact: new JPEG bytes, or null to remove the photo. */
data class PhotoEdit(val rawId: Long?, val jpeg: ByteArray?)

/**
 * The ONE layer every `ContactsContract` insert, update and delete of People's goes through (Q-16-3; Decisions "the
 * People write guard and the Can edit list" point (3); Trust (b)). No other file of the shell writes a contact, a raw
 * contact, a data row, a group or an aggregation exception.
 *
 * Every op does the same four things in the same order: it RESOLVES from the provider the raw contacts (or the group's
 * account) it would touch — never from what a page or an intent claims; it ASKS [PeopleWriteGuard] with the device's
 * local account and the "Can edit" list as they stand now; it checks WRITE_CONTACTS; then it writes, and says what
 * happened: `[people] write <op> raw=<id>: ok | failed <err> | refused (not allowed)`. A refusal or a failure writes
 * nothing at all — an edit is one batch.
 *
 * Blocking; called off the main thread.
 */
object PeopleWriter {
    private const val TAG = "people"
    private const val NOT_HELD = "WRITE_CONTACTS not held"

    // ---------------------------------------------------------------------------------------------- contacts

    /**
     * A new contact in [account]: the phone unless the editor's account choice named an allowed account. A SIM import
     * and an INSERT prefill say so in [source], which the guard holds to its own rule.
     */
    fun create(context: Context, account: ContactAccount, source: NewContactSource, fields: List<FieldEdit>, photo: ByteArray? = null): WriteResult {
        if (refused(context, PeopleWrite.NewRawContact(account, source))) return refusedLine("insert", "new")
        if (!PeopleData.canWrite(context)) return failedLine("insert", "new", NOT_HELD, needsGrant = true)
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .build()
        fields.filter { it.value.isNotBlank() }.forEach { f ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValues(values(f, insert = true))
                .build()
        }
        val rawId = runCatching {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            ContentUris.parseId(results[0].uri ?: error("no raw contact was made"))
        }.getOrElse { return failedLine("insert", "new", describe(it)) }
        Diagnostics.add(TAG, "write insert raw=$rawId: ok")
        if (photo != null) {
            // The raw contact exists now and is the one this op made, in the account the guard just allowed.
            val result = writePhoto(context, rawId, photo)
            if (result is WriteResult.Failed) return result
        }
        return WriteResult.Ok(rawId = rawId)
    }

    /**
     * An edit of an existing contact: field changes and a photo change, each on the raw contact it belongs to. Every
     * raw contact the edit touches must be editable; otherwise nothing is written.
     */
    fun update(context: Context, edits: List<FieldEdit>, photo: PhotoEdit?): WriteResult {
        val resolver = context.contentResolver
        // Which raw contact each existing data row REALLY belongs to: read from the provider, not from the draft.
        val dataIds = edits.mapNotNull { it.dataId }
        val owner = HashMap<Long, Long>()
        if (dataIds.isNotEmpty()) {
            runCatching {
                resolver.query(
                    Data.CONTENT_URI, arrayOf(Data._ID, Data.RAW_CONTACT_ID),
                    "${Data._ID} IN (${dataIds.joinToString(",")})", null, null,
                )?.use { c -> while (c.moveToNext()) owner[c.getLong(0)] = c.getLong(1) }
            }.onFailure { return failedLine("update", edits.firstNotNullOfOrNull { it.rawId }?.toString() ?: "unknown", describe(it)) }
        }
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
        val raws = resolveRaws(context, rawIds)
        rawIds.firstOrNull { it !in raws }?.let { return failedLine("update", it.toString(), "the raw contact is gone") }

        for (s in steps) if (refused(context, PeopleWrite.DataRow(s.op, raws.getValue(s.rawId)))) return refusedLine("update", s.rawId.toString())
        if (photoRaw != null && refused(context, PeopleWrite.DataRow(WriteOp.UPDATE, raws.getValue(photoRaw)))) return refusedLine("update", photoRaw.toString())
        if (!PeopleData.canWrite(context)) return failedLine("update", rawIds.first().toString(), NOT_HELD, needsGrant = true)

        val ops = ArrayList<ContentProviderOperation>()
        for (s in steps) {
            val row = arrayOf(s.edit.dataId.toString(), s.rawId.toString())
            ops += when (s.op) {
                WriteOp.INSERT -> ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, s.rawId).withValues(values(s.edit, insert = true)).build()
                // The row is addressed with its raw contact, so a row that moved since the resolve matches nothing.
                WriteOp.UPDATE -> ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection("${Data._ID}=? AND ${Data.RAW_CONTACT_ID}=?", row).withValues(values(s.edit, insert = false)).build()
                WriteOp.DELETE -> ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection("${Data._ID}=? AND ${Data.RAW_CONTACT_ID}=?", row).build()
            }
        }
        if (ops.isNotEmpty()) {
            runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }
                .onFailure { return failedLine("update", rawIds.first().toString(), describe(it)) }
        }
        if (photo != null && photoRaw != null) {
            val result = if (photo.jpeg != null) writePhoto(context, photoRaw, photo.jpeg) else removePhoto(context, photoRaw)
            if (result is WriteResult.Failed) return result
        }
        rawIds.forEach { Diagnostics.add(TAG, "write update raw=$it: ok") }
        return WriteResult.Ok()
    }

    /** Deleting a contact deletes the aggregate, so every raw contact behind it must be editable. */
    fun delete(context: Context, contactId: Long): WriteResult {
        val raws = PeopleData.rawContacts(context, contactId).map { it.ref() }
        if (raws.isEmpty()) return failedLine("delete", "unknown", "the contact is gone")
        val verdict = PeopleWriteGuard.check(PeopleWrite.DeleteContact(raws), PeopleEditStore.policy(context))
        if (verdict is GuardVerdict.Refused) return refusedLine("delete", (verdict.rawId ?: raws.first().id).toString())
        if (!PeopleData.canWrite(context)) return failedLine("delete", raws.first().id.toString(), NOT_HELD, needsGrant = true)
        val ops = raws.map {
            ContentProviderOperation.newDelete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, it.id)).build()
        }
        runCatching { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops)) }
            .onFailure { return failedLine("delete", raws.first().id.toString(), describe(it)) }
        raws.forEach { Diagnostics.add(TAG, "write delete raw=${it.id}: ok") }
        return WriteResult.Ok()
    }

    // ---------------------------------------------------------------------------------------------- link / unlink

    /**
     * Link: every raw contact behind [contactA] is kept together with every one behind [contactB]
     * (`TYPE_KEEP_TOGETHER`). Aggregation exceptions are local to the phone and reach no account, so the guard allows
     * them on any contact — it is still asked. `[people] link <a>+<b>: ok | failed`.
     */
    fun link(context: Context, contactA: Long, contactB: Long): WriteResult {
        val a = PeopleData.rawContacts(context, contactA).map { it.ref() }
        val b = PeopleData.rawContacts(context, contactB).map { it.ref() }
        val label = "${a.firstOrNull()?.id ?: "gone"}+${b.firstOrNull()?.id ?: "gone"}"
        if (a.isEmpty() || b.isEmpty() || contactA == contactB) return linkFailed("link", label, "a contact is gone")
        return aggregate(context, "link", label, a.flatMap { x -> b.map { y -> x to y } }, together = true)
    }

    /** Unlink: [rawId] is kept separate from every other raw contact behind [contactId] (`TYPE_KEEP_SEPARATE`). */
    fun unlink(context: Context, contactId: Long, rawId: Long): WriteResult {
        val raws = PeopleData.rawContacts(context, contactId).map { it.ref() }
        val one = raws.firstOrNull { it.id == rawId }
        val others = raws.filter { it.id != rawId }
        val label = "$rawId+${others.firstOrNull()?.id ?: "gone"}"
        if (one == null || others.isEmpty()) return linkFailed("unlink", label, "nothing is linked")
        return aggregate(context, "unlink", label, others.map { one to it }, together = false)
    }

    private fun aggregate(context: Context, word: String, label: String, pairs: List<Pair<RawRef, RawRef>>, together: Boolean): WriteResult {
        val policy = PeopleEditStore.policy(context)
        if (pairs.any { PeopleWriteGuard.check(PeopleWrite.Aggregation(it.first, it.second, together), policy) is GuardVerdict.Refused }) {
            Diagnostics.add(TAG, "$word $label: failed")
            return WriteResult.Refused
        }
        if (!PeopleData.canWrite(context)) {
            Diagnostics.add(TAG, "$word $label: failed")
            return WriteResult.Failed(NOT_HELD, needsGrant = true)
        }
        val type = if (together) AggregationExceptions.TYPE_KEEP_TOGETHER else AggregationExceptions.TYPE_KEEP_SEPARATE
        val ops = pairs.map { (x, y) ->
            ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                .withValue(AggregationExceptions.TYPE, type)
                .withValue(AggregationExceptions.RAW_CONTACT_ID1, minOf(x.id, y.id))
                .withValue(AggregationExceptions.RAW_CONTACT_ID2, maxOf(x.id, y.id))
                .build()
        }
        return runCatching {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
            Diagnostics.add(TAG, "$word $label: ok")
            WriteResult.Ok()
        }.getOrElse { linkFailed(word, label, describe(it)) }
    }

    private fun linkFailed(word: String, label: String, error: String): WriteResult {
        Diagnostics.add(TAG, "$word $label: failed")
        return WriteResult.Failed(error)
    }

    // ---------------------------------------------------------------------------------------------- SIM

    /**
     * Import from SIM: each chosen entry that has a number becomes a new contact on the phone — never in an account.
     * Writes `[people] sim import: n of m`, also "0 of 0" when the SIM lists nothing.
     */
    fun importSim(context: Context, chosen: List<SimContact>): WriteResult {
        val local = PeopleEditStore.localAccount(context)
        var imported = 0
        var failure: WriteResult.Failed? = null
        for (entry in chosen) {
            if (!SimRules.importable(entry)) continue
            val fields = listOfNotNull(
                entry.name.takeIf { it.isNotBlank() }?.let { FieldEdit(FieldKind.NAME, null, null, it) },
                FieldEdit(FieldKind.PHONE, null, null, entry.number!!.trim(), FieldTypes.PHONE_MOBILE),
            )
            when (val result = create(context, local, NewContactSource.SIM_IMPORT, fields)) {
                is WriteResult.Ok -> imported++
                is WriteResult.Failed -> { failure = result; if (result.needsGrant) break }
                WriteResult.Refused -> Unit
            }
        }
        Diagnostics.add(TAG, SimRules.line(imported, chosen.size))
        return failure?.takeIf { imported == 0 } ?: WriteResult.Ok()
    }

    // ---------------------------------------------------------------------------------------------- groups

    /** A new group in [account]: the phone unless an allowed account was chosen. `[people] group create <id>: ok | failed <err>`. */
    fun createGroup(context: Context, title: String, account: ContactAccount): WriteResult {
        if (title.isBlank()) return groupFailed("create", "new", "a group needs a name")
        if (refused(context, PeopleWrite.GroupRow(WriteOp.INSERT, account))) return groupFailed("create", "new", "refused (not allowed)", refused = true)
        if (!PeopleData.canWrite(context)) return groupFailed("create", "new", NOT_HELD, needsGrant = true)
        return runCatching {
            val values = ContentValues().apply {
                put(Groups.TITLE, title.trim())
                put(Groups.ACCOUNT_NAME, account.name)
                put(Groups.ACCOUNT_TYPE, account.type)
                put(Groups.GROUP_VISIBLE, 1)
            }
            val id = ContentUris.parseId(context.contentResolver.insert(Groups.CONTENT_URI, values) ?: error("no group was made"))
            Diagnostics.add(TAG, "group create $id: ok")
            WriteResult.Ok(id = id)
        }.getOrElse { groupFailed("create", "new", describe(it)) }
    }

    fun renameGroup(context: Context, groupId: Long, title: String): WriteResult {
        if (title.isBlank()) return groupFailed("rename", groupId.toString(), "a group needs a name")
        val group = PeopleData.group(context, groupId) ?: return groupFailed("rename", groupId.toString(), "the group is gone")
        if (refused(context, PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return groupFailed("rename", groupId.toString(), "refused (not allowed)", refused = true)
        if (!PeopleData.canWrite(context)) return groupFailed("rename", groupId.toString(), NOT_HELD, needsGrant = true)
        return runCatching {
            val n = context.contentResolver.update(
                ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), ContentValues().apply { put(Groups.TITLE, title.trim()) }, null, null,
            )
            if (n == 0) error("the group is gone")
            Diagnostics.add(TAG, "group rename $groupId: ok")
            WriteResult.Ok(id = groupId)
        }.getOrElse { groupFailed("rename", groupId.toString(), describe(it)) }
    }

    /**
     * Deletes a group; its members stay contacts. A phone group has no sync adapter to clear a row marked deleted, so
     * its row is removed outright; an allowed account's group is marked deleted for that account's own adapter.
     */
    fun deleteGroup(context: Context, groupId: Long): WriteResult {
        val group = PeopleData.group(context, groupId) ?: return groupFailed("delete", groupId.toString(), "the group is gone")
        if (refused(context, PeopleWrite.GroupRow(WriteOp.DELETE, group.account))) return groupFailed("delete", groupId.toString(), "refused (not allowed)", refused = true)
        if (!PeopleData.canWrite(context)) return groupFailed("delete", groupId.toString(), NOT_HELD, needsGrant = true)
        return runCatching {
            var uri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId)
            if (PeopleEditStore.policy(context).isPhone(group.account)) {
                uri = uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
            }
            val n = context.contentResolver.delete(uri, null, null)
            if (n == 0) error("the group is gone")
            Diagnostics.add(TAG, "group delete $groupId: ok")
            WriteResult.Ok(id = groupId)
        }.getOrElse { groupFailed("delete", groupId.toString(), describe(it)) }
    }

    /**
     * Group membership is a data row on the member's raw contact in the group's own account, so it is written only
     * where that raw contact is editable — which the group's account being writable makes it.
     */
    fun setMember(context: Context, groupId: Long, contactId: Long, member: Boolean): WriteResult {
        val group = PeopleData.group(context, groupId) ?: return failedLine("update", "unknown", "the group is gone")
        val raw = PeopleData.rawContacts(context, contactId).firstOrNull { it.account == group.account }?.ref()
            ?: return failedLine("update", "unknown", "the contact is not in the group's account")
        if (refused(context, PeopleWrite.GroupRow(WriteOp.UPDATE, group.account))) return refusedLine("update", raw.id.toString())
        if (refused(context, PeopleWrite.DataRow(if (member) WriteOp.INSERT else WriteOp.DELETE, raw))) return refusedLine("update", raw.id.toString())
        if (!PeopleData.canWrite(context)) return failedLine("update", raw.id.toString(), NOT_HELD, needsGrant = true)
        val where = "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID}=?"
        val args = arrayOf(raw.id.toString(), CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString())
        return runCatching {
            val resolver = context.contentResolver
            if (member) {
                val already = resolver.query(Data.CONTENT_URI, arrayOf(Data._ID), where, args, null)?.use { it.count > 0 } ?: false
                if (!already) {
                    resolver.insert(Data.CONTENT_URI, ContentValues().apply {
                        put(Data.RAW_CONTACT_ID, raw.id)
                        put(Data.MIMETYPE, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        put(CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                    }) ?: error("no membership was made")
                }
            } else {
                resolver.delete(Data.CONTENT_URI, where, args)
            }
            Diagnostics.add(TAG, "write update raw=${raw.id}: ok")
            WriteResult.Ok(rawId = raw.id)
        }.getOrElse { failedLine("update", raw.id.toString(), describe(it)) }
    }

    // ---------------------------------------------------------------------------------------------- inside

    /** The raw contacts named by [ids] as the provider holds them now; one that is gone or marked deleted is absent. */
    private fun resolveRaws(context: Context, ids: List<Long>): Map<Long, RawRef> = runCatching {
        val out = HashMap<Long, RawRef>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts._ID} IN (${ids.joinToString(",")}) AND ${RawContacts.DELETED}=0", null, null,
        )?.use { c -> while (c.moveToNext()) out[c.getLong(0)] = RawRef(c.getLong(0), ContactAccount(c.getString(1), c.getString(2))) }
        out
    }.getOrElse { emptyMap() }

    /** The photo as a photo data row: written through the raw contact's display-photo stream, which the provider keeps as that row. */
    private fun writePhoto(context: Context, rawId: Long, jpeg: ByteArray): WriteResult = runCatching {
        val uri = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
        val fd = context.contentResolver.openAssetFileDescriptor(uri, "rw") ?: error("the provider gave no photo stream")
        fd.use { it.createOutputStream().use { out -> out.write(jpeg) } }
        WriteResult.Ok(rawId = rawId) as WriteResult
    }.getOrElse { failedLine("update", rawId.toString(), describe(it)) }

    private fun removePhoto(context: Context, rawId: Long): WriteResult = runCatching {
        context.contentResolver.delete(
            Data.CONTENT_URI, "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
            arrayOf(rawId.toString(), CommonDataKinds.Photo.CONTENT_ITEM_TYPE),
        )
        WriteResult.Ok(rawId = rawId) as WriteResult
    }.getOrElse { failedLine("update", rawId.toString(), describe(it)) }

    /** A field as the provider's columns hold it. */
    private fun values(edit: FieldEdit, insert: Boolean): ContentValues = ContentValues().apply {
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

    private fun refused(context: Context, write: PeopleWrite): Boolean =
        PeopleWriteGuard.check(write, PeopleEditStore.policy(context)) is GuardVerdict.Refused

    private fun refusedLine(op: String, raw: String): WriteResult {
        Diagnostics.add(TAG, "write $op raw=$raw: refused (not allowed)")
        return WriteResult.Refused
    }

    private fun failedLine(op: String, raw: String, error: String, needsGrant: Boolean = false): WriteResult.Failed {
        Diagnostics.add(TAG, "write $op raw=$raw: failed $error")
        return WriteResult.Failed(error, needsGrant)
    }

    private fun groupFailed(op: String, id: String, error: String, needsGrant: Boolean = false, refused: Boolean = false): WriteResult {
        Diagnostics.add(TAG, "group $op $id: failed $error")
        return if (refused) WriteResult.Refused else WriteResult.Failed(error, needsGrant)
    }

    /** An error as the ring states it: the exception's kind, never a caller's text. */
    private fun describe(error: Throwable): String = error.message?.takeIf { it.length <= 80 && '\n' !in it } ?: error.javaClass.simpleName

    /** The contact a raw contact belongs to now, for a page that opens the card of what it just saved. */
    fun contactOf(context: Context, rawId: Long): Long? = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }.getOrNull()

    /** The contacts lookup URI of a contact, for a PICK result and for a share. */
    fun lookupUri(contactId: Long, lookup: String): Uri = Contacts.getLookupUri(contactId, lookup)
}

package app.tileshell.people

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
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
 * It has two parts. [PeopleWrites] holds the rules — resolve from the provider, ask [PeopleWriteGuard], check
 * WRITE_CONTACTS, then write — over a [ContactsPort], so they are proven on the JVM against a port that records
 * every write. [ResolverContacts], below, is the port over the ContentResolver: the only code that calls the
 * resolver's insert, update, delete or applyBatch on the Contacts provider, and it decides nothing.
 *
 * Blocking; called off the main thread.
 */
object PeopleWriter {
    private fun writes(context: Context) = PeopleWrites(ResolverContacts(context))

    fun create(context: Context, account: ContactAccount, source: NewContactSource, fields: List<FieldEdit>, photo: ByteArray? = null): WriteResult =
        writes(context).create(account, source, fields, photo)

    fun update(context: Context, edits: List<FieldEdit>, photo: PhotoEdit?): WriteResult = writes(context).update(edits, photo)

    fun delete(context: Context, contactId: Long): WriteResult = writes(context).delete(contactId)

    fun link(context: Context, contactA: Long, contactB: Long): WriteResult = writes(context).link(contactA, contactB)

    fun unlink(context: Context, contactId: Long, rawId: Long): WriteResult = writes(context).unlink(contactId, rawId)

    fun importSim(context: Context, chosen: List<SimContact>): WriteResult = writes(context).importSim(chosen)

    fun createGroup(context: Context, title: String, account: ContactAccount): WriteResult = writes(context).createGroup(title, account)

    fun renameGroup(context: Context, groupId: Long, title: String): WriteResult = writes(context).renameGroup(groupId, title)

    fun deleteGroup(context: Context, groupId: Long): WriteResult = writes(context).deleteGroup(groupId)

    fun setMember(context: Context, groupId: Long, contactId: Long, member: Boolean): WriteResult = writes(context).setMember(groupId, contactId, member)

    /** The contact a raw contact belongs to now, for a page that opens the card of what it just saved. */
    fun contactOf(context: Context, rawId: Long): Long? = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }.getOrNull()

    /** The contacts lookup URI of a contact, for a PICK result and for a share. */
    fun lookupUri(contactId: Long, lookup: String): Uri = Contacts.getLookupUri(contactId, lookup)
}

/**
 * [ContactsPort] over the ContentResolver. It translates and nothing more: which rows may be written is decided in
 * [PeopleWrites] before any of the five write functions here is reached.
 */
private class ResolverContacts(private val context: Context) : ContactsPort {
    private val resolver get() = context.contentResolver

    override fun policy(): EditPolicy = PeopleEditStore.policy(context)

    override fun mayWrite(): Boolean = PeopleData.canWrite(context)

    override fun isOtherProfile(contactId: Long): Boolean = Contacts.isEnterpriseContactId(contactId)

    override fun rawContactsOf(contactId: Long): List<RawRef> = PeopleData.rawContacts(context, contactId).map { it.ref() }

    override fun rawContacts(ids: List<Long>): Map<Long, RawRef> = runCatching {
        val out = HashMap<Long, RawRef>()
        if (ids.isEmpty()) return@runCatching out
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts._ID} IN (${ids.joinToString(",")}) AND ${RawContacts.DELETED}=0", null, null,
        )?.use { c -> while (c.moveToNext()) out[c.getLong(0)] = RawRef(c.getLong(0), ContactAccount(c.getString(1), c.getString(2))) }
        out
    }.getOrElse { emptyMap() }

    override fun dataOwners(dataIds: List<Long>): Map<Long, Long> {
        val owner = HashMap<Long, Long>()
        if (dataIds.isEmpty()) return owner
        resolver.query(
            Data.CONTENT_URI, arrayOf(Data._ID, Data.RAW_CONTACT_ID),
            "${Data._ID} IN (${dataIds.joinToString(",")})", null, null,
        )?.use { c -> while (c.moveToNext()) owner[c.getLong(0)] = c.getLong(1) }
        return owner
    }

    override fun group(groupId: Long): ContactGroup? = PeopleData.group(context, groupId)

    override fun isMember(rawId: Long, groupId: Long): Boolean = resolver.query(
        Data.CONTENT_URI, arrayOf(Data._ID), PeopleWrites.MEMBERSHIP_OF_RAW,
        arrayOf(rawId.toString(), CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null,
    )?.use { it.count > 0 } ?: false

    override fun insert(uri: String, values: Map<String, Any?>): Long? =
        resolver.insert(Uri.parse(uri), contentValues(values))?.let { ContentUris.parseId(it) }

    override fun update(uri: String, values: Map<String, Any?>, where: String?, args: List<String>): Int =
        resolver.update(Uri.parse(uri), contentValues(values), where, args.toTypedArray().takeIf { where != null })

    override fun delete(uri: String, where: String?, args: List<String>): Int =
        resolver.delete(Uri.parse(uri), where, args.toTypedArray().takeIf { where != null })

    override fun applyBatch(rows: List<RowWrite>): List<Long?> {
        val ops = ArrayList<ContentProviderOperation>(rows.size)
        for (row in rows) {
            val uri = Uri.parse(row.uri)
            val builder = when (row.op) {
                WriteOp.INSERT -> ContentProviderOperation.newInsert(uri)
                WriteOp.UPDATE -> ContentProviderOperation.newUpdate(uri)
                WriteOp.DELETE -> ContentProviderOperation.newDelete(uri)
            }
            if (row.values.isNotEmpty()) builder.withValues(contentValues(row.values))
            row.backReference?.let { (column, index) -> builder.withValueBackReference(column, index) }
            row.where?.let { builder.withSelection(it, row.args.toTypedArray()) }
            ops += builder.build()
        }
        return resolver.applyBatch(ContactsContract.AUTHORITY, ops).map { result -> result.uri?.let { ContentUris.parseId(it) } }
    }

    override fun writeStream(uri: String, bytes: ByteArray) {
        val fd = resolver.openAssetFileDescriptor(Uri.parse(uri), "rw") ?: error("the provider gave no photo stream")
        fd.use { it.createOutputStream().use { out -> out.write(bytes) } }
    }

    override fun line(text: String) = Diagnostics.add("people", text)

    /** The columns as ContentValues; a null value is the column set to NULL. */
    private fun contentValues(values: Map<String, Any?>): ContentValues = ContentValues().apply {
        for ((column, value) in values) when (value) {
            null -> putNull(column)
            is String -> put(column, value)
            is Int -> put(column, value)
            is Long -> put(column, value)
            else -> error("no column type for ${value.javaClass.simpleName}")
        }
    }
}

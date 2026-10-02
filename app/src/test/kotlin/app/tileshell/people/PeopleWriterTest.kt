package app.tileshell.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fix round's F15 (trust review A-F1): that People's write layer REFUSES when the guard says so. Every public
 * write function runs here against a port that records each write the layer asks the provider for. Two halves each:
 * refused — nothing was written and the result is the refusal; allowed — exactly these rows, and no other.
 */
class PeopleWriterTest {
    private val phone = ContactAccount(null, null)
    private val personal = ContactAccount("me@example.com", "com.example")
    private val work = ContactAccount("work@example.com", "com.example")

    /** One write the layer asked for, in the order asked. */
    private sealed interface Call {
        data class Insert(val uri: String, val values: Map<String, Any?>) : Call
        data class Update(val uri: String, val values: Map<String, Any?>, val where: String?, val args: List<String>) : Call
        data class Delete(val uri: String, val where: String?, val args: List<String>) : Call
        data class Batch(val rows: List<RowWrite>) : Call
        data class Stream(val uri: String, val bytes: List<Byte>) : Call
    }

    /** The phone as a test sets it up. Reads answer from the maps; every write is recorded and changes nothing else. */
    private inner class FakeContacts(allowed: Set<ContactAccount> = emptySet(), var held: Boolean = true) : ContactsPort {
        var policy = EditPolicy(phone, allowed)
        val raws = LinkedHashMap<Long, RawRef>()
        val contacts = LinkedHashMap<Long, List<Long>>()
        val owners = LinkedHashMap<Long, Long>()
        val groups = LinkedHashMap<Long, ContactGroup>()
        val memberships = HashSet<Pair<Long, Long>>()
        val calls = ArrayList<Call>()
        val lines = ArrayList<String>()
        var nextId = 100L

        /** The account a raw contact made by a batch ends up in; null is the account the batch named. */
        var newRawLandsIn: ContactAccount? = null

        /** Runs after each batch is recorded: the provider changing under the write layer between two of its writes. */
        var afterBatch: () -> Unit = {}

        /** The account a group made by an insert ends up in; null is the account the insert named. */
        var newGroupLandsIn: ContactAccount? = null

        /** What a delete does: remove the row and say 1 (the default), say 0 and leave it, or throw. */
        var deleteBehaviour = "removes"

        /** The phone's default account for new contacts is a cloud account (Android 16). */
        var cloudDefault = false

        fun raw(id: Long, account: ContactAccount, contact: Long = id, otherProfile: Boolean = false) {
            raws[id] = RawRef(id, account, otherProfile)
            contacts[contact] = contacts[contact].orEmpty() + id
        }

        fun field(dataId: Long, rawId: Long) { owners[dataId] = rawId }

        /** Contact ids the provider gives another profile's contacts. */
        val otherProfileContacts = HashSet<Long>()

        override fun policy() = policy
        override fun isOtherProfile(contactId: Long) = contactId in otherProfileContacts
        override fun mayWrite() = held
        override fun newContactsGoToCloud() = cloudDefault
        override fun rawContactsOf(contactId: Long) = contacts[contactId].orEmpty().mapNotNull { raws[it] }
        override fun rawContacts(ids: List<Long>) = ids.mapNotNull { raws[it] }.associateBy { it.id }
        override fun dataOwners(dataIds: List<Long>) = dataIds.filter { it in owners }.associateWith { owners.getValue(it) }
        override fun group(groupId: Long) = groups[groupId]
        override fun isMember(rawId: Long, groupId: Long) = (rawId to groupId) in memberships

        override fun insert(uri: String, values: Map<String, Any?>): Long? {
            calls += Call.Insert(uri, values)
            val id = nextId++
            if (uri == GROUPS) {
                groups[id] = ContactGroup(id, values["title"] as String, newGroupLandsIn ?: ContactAccount(values["account_name"] as String?, values["account_type"] as String?), 0)
            }
            return id
        }
        override fun update(uri: String, values: Map<String, Any?>, where: String?, args: List<String>): Int { calls += Call.Update(uri, values, where, args); return 1 }
        override fun delete(uri: String, where: String?, args: List<String>): Int {
            calls += Call.Delete(uri, where, args)
            when (deleteBehaviour) {
                "throws" -> error("the provider refused the delete")
                "removes nothing" -> return 0
            }
            val id = uri.substringAfterLast('/').substringBefore('?').toLongOrNull()
            if (uri.startsWith("$RAW/")) raws.remove(id)
            if (uri.startsWith("$GROUPS/")) groups.remove(id)
            return 1
        }
        override fun applyBatch(rows: List<RowWrite>): List<Long?> {
            calls += Call.Batch(rows)
            val made = rows.map { row ->
                if (row.op != WriteOp.INSERT) return@map null
                val id = nextId++
                if (row.uri == RAW) raws[id] = RawRef(id, newRawLandsIn ?: ContactAccount(row.values["account_name"] as String?, row.values["account_type"] as String?))
                id
            }
            afterBatch()
            return made
        }
        override fun writeStream(uri: String, bytes: ByteArray) { calls += Call.Stream(uri, bytes.toList()) }
        override fun line(text: String) { lines += text }
    }

    private fun PeopleWrites(fake: FakeContacts) = PeopleWrites(fake as ContactsPort)

    private fun assertNothingWritten(fake: FakeContacts) = assertEquals("writes", emptyList<Call>(), fake.calls)

    private companion object {
        const val RAW = "content://com.android.contacts/raw_contacts"
        const val DATA = "content://com.android.contacts/data"
        const val GROUPS = "content://com.android.contacts/groups"
        const val EXCEPTIONS = "content://com.android.contacts/aggregation_exceptions"
        const val ROW_OF_RAW = "_id=? AND raw_contact_id=?"
        const val PHONE_TYPE = "vnd.android.cursor.item/phone_v2"
        const val NAME_TYPE = "vnd.android.cursor.item/name"
        const val MEMBERSHIP_TYPE = "vnd.android.cursor.item/group_membership"
        const val PHOTO_TYPE = "vnd.android.cursor.item/photo"
        val JPEG = byteArrayOf(1, 2, 3)

        fun name(value: String, dataId: Long? = null, rawId: Long? = null) = FieldEdit(FieldKind.NAME, dataId, rawId, value)
        fun number(value: String, dataId: Long? = null, rawId: Long? = null) = FieldEdit(FieldKind.PHONE, dataId, rawId, value, 2)
    }

    // ---------------------------------------------------------------------------------------------- create

    private val newContact = listOf(name("Ned New"), number(" 555 0100 "), FieldEdit(FieldKind.EMAIL, null, null, "  "))

    private fun newContactBatch(account: ContactAccount) = Call.Batch(
        listOf(
            RowWrite(WriteOp.INSERT, RAW, mapOf("account_name" to account.name, "account_type" to account.type)),
            RowWrite(WriteOp.INSERT, DATA, mapOf("mimetype" to NAME_TYPE, "data1" to "Ned New"), backReference = "raw_contact_id" to 0),
            RowWrite(WriteOp.INSERT, DATA, mapOf("mimetype" to PHONE_TYPE, "data1" to "555 0100", "data2" to 2, "data3" to null), backReference = "raw_contact_id" to 0),
        ),
    )

    @Test fun createOnThePhoneWritesOneBatchTheRawContactAndItsFields() {
        val fake = FakeContacts()
        assertEquals(WriteResult.Ok(rawId = 100), PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact))
        assertEquals(listOf<Call>(newContactBatch(phone)), fake.calls)
        assertEquals(listOf("write insert raw=100: ok"), fake.lines)
    }

    @Test fun createInAnAllowedAccountWritesThatAccountAndNoOther() {
        val fake = FakeContacts(allowed = setOf(personal))
        assertEquals(WriteResult.Ok(rawId = 100), PeopleWrites(fake).create(personal, NewContactSource.EDITOR, newContact))
        assertEquals(listOf<Call>(newContactBatch(personal)), fake.calls)
    }

    @Test fun createInAnAccountNotAllowedWritesNothing() {
        for (source in listOf(NewContactSource.EDITOR, NewContactSource.INSERT_PREFILL)) {
            val fake = FakeContacts(allowed = setOf(personal))
            assertEquals(WriteResult.Refused, PeopleWrites(fake).create(work, source, newContact, JPEG))
            assertNothingWritten(fake)
            assertEquals(listOf("write insert raw=new: refused (not allowed)"), fake.lines)
        }
    }

    @Test fun aSimImportIntoAnAccountWritesNothingEvenWhenThatAccountIsAllowed() {
        val fake = FakeContacts(allowed = setOf(personal))
        assertEquals(WriteResult.Refused, PeopleWrites(fake).create(personal, NewContactSource.SIM_IMPORT, newContact))
        assertNothingWritten(fake)
    }

    @Test fun createWithoutWriteContactsWritesNothingAndAsksForTheGrant() {
        val fake = FakeContacts(held = false)
        assertEquals(WriteResult.Failed("WRITE_CONTACTS not held", needsGrant = true), PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact, JPEG))
        assertNothingWritten(fake)
    }

    @Test fun aNewContactsPhotoGoesToTheStreamOfTheRawContactTheBatchMade() {
        val fake = FakeContacts()
        assertEquals(WriteResult.Ok(rawId = 100), PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact, JPEG))
        assertEquals(listOf(newContactBatch(phone), Call.Stream("$RAW/100/display_photo", JPEG.toList())), fake.calls)
    }

    @Test fun aNewContactThePhoneFilesUnderAnotherAccountIsTakenBackAndTheSaveFails() {
        // The provider put the new raw contact somewhere other than where it was asked to: that one row is deleted
        // again, nothing more is written — no photo — and the save fails, saying what happened.
        val fake = FakeContacts().apply { newRawLandsIn = work }
        val why = "the phone filed it under ${work.id}, not the phone (no account); it was taken back"
        assertEquals(WriteResult.Failed(why, takenBack = true), PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact, JPEG))
        assertEquals(listOf(newContactBatch(phone), Call.Delete("$RAW/100", null, emptyList())), fake.calls)
        assertEquals(listOf("write delete raw=100: ok (taken back)", "write insert raw=100: failed $why"), fake.lines)
        assertTrue("the row is gone", 100L !in fake.raws)
    }

    @Test fun aNewContactFiledUnderAnotherAccountIsTakenBackEvenWhenPeopleMayWriteThatAccount() {
        // "Save to" said the phone; landing in an allowed account is still not what was asked.
        val fake = FakeContacts(allowed = setOf(personal)).apply { newRawLandsIn = personal }
        assertEquals(true, (PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact) as WriteResult.Failed).takenBack)
        assertEquals(listOf(newContactBatch(phone), Call.Delete("$RAW/100", null, emptyList())), fake.calls)
        // And the other way round: asked for the allowed account, filed on the phone.
        val other = FakeContacts(allowed = setOf(personal)).apply { newRawLandsIn = phone }
        assertEquals(
            WriteResult.Failed("the phone filed it under the phone (no account), not ${personal.id}; it was taken back", takenBack = true),
            PeopleWrites(other).create(personal, NewContactSource.EDITOR, newContact),
        )
        assertEquals(listOf(newContactBatch(personal), Call.Delete("$RAW/100", null, emptyList())), other.calls)
    }

    @Test fun aNewContactThatCannotBeReadBackIsTakenBack() {
        val fake = FakeContacts()
        fake.afterBatch = { fake.raws.remove(100) }
        assertEquals(
            WriteResult.Failed("the new contact could not be read back; it was taken back", takenBack = true),
            PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact, JPEG),
        )
        assertEquals(listOf(newContactBatch(phone), Call.Delete("$RAW/100", null, emptyList())), fake.calls)
    }

    @Test fun aTakeBackThatFailsSaysTheContactIsStillThere() {
        // The delete throws, or removes nothing: the result and the line say the row was LEFT, and where — never "taken
        // back" (adversarial review, finding 1).
        for (behaviour in listOf("throws", "removes nothing")) {
            val fake = FakeContacts().apply { newRawLandsIn = work; deleteBehaviour = behaviour }
            val result = PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact, JPEG) as WriteResult.Failed
            assertEquals(behaviour, 100L, result.leftBehind)
            assertEquals(behaviour, false, result.takenBack)
            assertTrue(behaviour, result.error.endsWith("it could NOT be taken back - still in ${work.id}"))
            assertTrue(behaviour, fake.lines.last().endsWith("it could NOT be taken back - still in ${work.id}"))
            assertTrue(behaviour, fake.lines.none { "it was taken back" in it || "ok" in it.substringAfter(": ").take(2) })
            assertEquals(behaviour, listOf(newContactBatch(phone), Call.Delete("$RAW/100", null, emptyList())), fake.calls)
        }
    }

    @Test fun aNewContactOnAPhoneWhoseMakerNamesItsLocalAccountIsNotTakenBack() {
        // The policy's own rule decides "the phone": asked for the device's local account, stored as exactly that.
        val maker = ContactAccount("vnd.sec.contact.phone", "vnd.sec.contact.phone")
        val fake = FakeContacts().apply { policy = EditPolicy(maker, emptySet()) }
        assertEquals(WriteResult.Ok(rawId = 100), PeopleWrites(fake).create(maker, NewContactSource.EDITOR, newContact))
        assertEquals(listOf<Call>(newContactBatch(maker)), fake.calls)
        // A NULL / NULL landing on such a phone is NOT its local account: taken back.
        val odd = FakeContacts().apply { policy = EditPolicy(maker, emptySet()); newRawLandsIn = phone }
        assertEquals(true, (PeopleWrites(odd).create(maker, NewContactSource.EDITOR, newContact) as WriteResult.Failed).takenBack)
    }

    @Test fun aPhoneWhoseDefaultAccountIsACloudOneRefusesASaveToThePhoneBeforeAnythingIsWritten() {
        val fake = FakeContacts(allowed = setOf(personal)).apply { cloudDefault = true }
        val result = PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact, JPEG)
        assertEquals(WriteResult.Failed(PeopleWrites.CLOUD_DEFAULT, cloudDefault = true), result)
        assertNothingWritten(fake)
        assertEquals(listOf("write insert raw=new: failed ${PeopleWrites.CLOUD_DEFAULT}"), fake.lines)
        // A save to an ALLOWED account is not the phone's to refuse on that ground.
        assertEquals(WriteResult.Ok(rawId = 100), PeopleWrites(fake).create(personal, NewContactSource.EDITOR, newContact))
    }

    @Test fun aSimImportStopsAtTheFirstContactThePhoneFilesElsewhere() {
        // One row written and taken back is enough to know: the next entry would be filed the same way.
        val fake = FakeContacts().apply { newRawLandsIn = work }
        val result = PeopleWrites(fake).importSim(listOf(SimContact(0, "Sim Bob", "5550002"), SimContact(1, "Sim Cy", "5550004"), SimContact(2, "Sim Di", "5550006")))
        assertEquals(true, (result as WriteResult.Failed).takenBack)
        assertEquals("one batch and its take-back, no more", 2, fake.calls.size)
        assertTrue(fake.calls[0] is Call.Batch)
        assertEquals(Call.Delete("$RAW/100", null, emptyList()), fake.calls[1])
        // And with the phone's default account a cloud one, nothing is written at all.
        val cloud = FakeContacts().apply { cloudDefault = true }
        assertEquals(true, (PeopleWrites(cloud).importSim(listOf(SimContact(0, "Sim Bob", "5550002"), SimContact(1, "Sim Cy", "5550004"))) as WriteResult.Failed).cloudDefault)
        assertNothingWritten(cloud)
    }

    @Test fun aNewGroupThePhoneFilesUnderAnotherAccountIsTakenBack() {
        val fake = FakeContacts().apply { newGroupLandsIn = work }
        val result = PeopleWrites(fake).createGroup("Family", phone) as WriteResult.Failed
        assertEquals(true, result.takenBack)
        assertEquals(2, fake.calls.size)
        assertEquals(Call.Delete("$GROUPS/100", null, emptyList()), fake.calls[1])
        assertTrue("the group is gone", 100L !in fake.groups)
        assertEquals("group create 100: failed the phone filed it under ${work.id}, not the phone (no account); it was taken back", fake.lines.last())
        // A take-back that fails says so.
        val stuck = FakeContacts().apply { newGroupLandsIn = work; deleteBehaviour = "removes nothing" }
        assertEquals(100L, (PeopleWrites(stuck).createGroup("Family", phone) as WriteResult.Failed).leftBehind)
        // And the cloud default refuses a phone group before anything is written.
        val cloud = FakeContacts().apply { cloudDefault = true }
        assertEquals(WriteResult.Failed(PeopleWrites.CLOUD_DEFAULT, cloudDefault = true), PeopleWrites(cloud).createGroup("Family", phone))
        assertNothingWritten(cloud)
    }

    @Test fun aTakeBackWhoseOutcomeCannotBeReadSaysItIsNotKnown() {
        // The new row could not be read back, the delete says it removed nothing, and the row still cannot be read:
        // neither "taken back" nor "still in <account>" is known, and the result says so (leftBehind, to be safe).
        val fake = FakeContacts().apply { deleteBehaviour = "removes nothing" }
        fake.afterBatch = { fake.raws.remove(100) }
        val result = PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact) as WriteResult.Failed
        assertEquals("the new contact could not be read back; whether it was taken back is not known", result.error)
        assertEquals(100L, result.leftBehind)
        assertEquals(false, result.takenBack)
        assertEquals("write insert raw=100: failed the new contact could not be read back; whether it was taken back is not known", fake.lines.last())
    }

    @Test fun anInsertThatReturnsNoRealIdIsNotFollowedByADelete() {
        // The provider answered the insert with id 0 and the row is not where it was asked to go: there is nothing the
        // take-back may name (0 is no id an insert returns), so no delete is issued and the result says so.
        val fake = FakeContacts().apply { nextId = 0; newRawLandsIn = work }
        val result = PeopleWrites(fake).create(phone, NewContactSource.EDITOR, newContact) as WriteResult.Failed
        assertTrue(result.error, result.error.endsWith("no id to take back"))
        assertEquals("the batch alone: no delete", listOf<Call>(newContactBatch(phone)), fake.calls)
        assertTrue(fake.lines.last(), fake.lines.last().endsWith("no id to take back"))
    }

    @Test fun aTakeBackNeedsAnIdAnInsertReallyReturned() {
        assertEquals(GuardVerdict.Allowed, PeopleWriteGuard.check(TakeBack(100)))
        assertEquals(GuardVerdict.Refused(0), PeopleWriteGuard.check(TakeBack(0)))
        assertEquals(GuardVerdict.Refused(-1), PeopleWriteGuard.check(TakeBack(-1)))
    }

    // ---------------------------------------------------------------------------------------------- update

    /** Lou is on the phone (raw 1, fields 11 and 12); Wade is in the work account (raw 2, field 21). */
    private fun louAndWade(allowed: Set<ContactAccount> = emptySet()) = FakeContacts(allowed).apply {
        raw(1, phone); field(11, 1); field(12, 1)
        raw(2, work); field(21, 2)
    }

    @Test fun updateWritesEachChangeOnTheRawContactThatOwnsIt() {
        val fake = louAndWade()
        val edits = listOf(number("555 0111", dataId = 11, rawId = 1), number("", dataId = 12, rawId = 1), FieldEdit(FieldKind.NOTES, null, 1, "a note"), number(" ", rawId = 1))
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).update(edits, null))
        assertEquals(
            listOf<Call>(
                Call.Batch(
                    listOf(
                        RowWrite(WriteOp.UPDATE, DATA, mapOf("data1" to "555 0111", "data2" to 2, "data3" to null), where = ROW_OF_RAW, args = listOf("11", "1")),
                        RowWrite(WriteOp.DELETE, DATA, where = ROW_OF_RAW, args = listOf("12", "1")),
                        RowWrite(WriteOp.INSERT, DATA, mapOf("raw_contact_id" to 1L, "mimetype" to "vnd.android.cursor.item/note", "data1" to "a note")),
                    ),
                ),
            ),
            fake.calls,
        )
        assertEquals(listOf("write update raw=1: ok"), fake.lines)
    }

    @Test fun updateOfARawContactInAnAccountNotAllowedWritesNothing() {
        // A changed field, a removed field, a new field: each is a write on Wade's raw contact, and each is refused.
        for (edit in listOf(number("555 0222", dataId = 21, rawId = 2), number("", dataId = 21, rawId = 2), FieldEdit(FieldKind.NOTES, null, 2, "a note"))) {
            val fake = louAndWade(allowed = setOf(personal))
            assertEquals(WriteResult.Refused, PeopleWrites(fake).update(listOf(edit), null))
            assertNothingWritten(fake)
            assertEquals(listOf("write update raw=2: refused (not allowed)"), fake.lines)
        }
    }

    @Test fun updateOfTheSameRawContactIsWrittenOnceItsAccountIsAllowed() {
        val fake = louAndWade(allowed = setOf(work))
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).update(listOf(number("555 0222", dataId = 21, rawId = 2)), null))
        assertEquals(1, fake.calls.size)
        assertEquals(listOf("21", "2"), (fake.calls[0] as Call.Batch).rows.single().args)
    }

    @Test fun aMixedContactsEditWritesTheEditableRawContactAndLeavesTheOtherUntouched() {
        // Lou (phone) and Wade (work) linked into one contact: the editor offers Lou's fields, and only they are written.
        val fake = FakeContacts().apply { raw(1, phone, contact = 7); raw(2, work, contact = 7); field(11, 1); field(21, 2) }
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).update(listOf(number("555 0111", dataId = 11, rawId = 1)), null))
        val rows = (fake.calls.single() as Call.Batch).rows
        assertEquals(listOf(listOf("11", "1")), rows.map { it.args })
        assertTrue(rows.none { "2" == it.args.getOrNull(1) || it.values["raw_contact_id"] == 2L })
        assertEquals(listOf("write update raw=1: ok"), fake.lines)
    }

    @Test fun anEditThatAlsoTouchesTheReadOnlyHalfOfAMixedContactWritesNothingAtAll() {
        val fake = FakeContacts().apply { raw(1, phone, contact = 7); raw(2, work, contact = 7); field(11, 1); field(21, 2) }
        val edits = listOf(number("555 0111", dataId = 11, rawId = 1), number("555 0222", dataId = 21, rawId = 2))
        assertEquals(WriteResult.Refused, PeopleWrites(fake).update(edits, null))
        assertNothingWritten(fake)
    }

    @Test fun whichRawContactAFieldBelongsToIsTheProvidersAnswerNotTheDrafts() {
        // The draft says field 21 is Lou's (raw 1, the phone); the provider says it is Wade's (raw 2, work).
        val fake = louAndWade()
        assertEquals(WriteResult.Refused, PeopleWrites(fake).update(listOf(number("555 0222", dataId = 21, rawId = 1)), null))
        assertNothingWritten(fake)
        assertEquals(listOf("write update raw=2: refused (not allowed)"), fake.lines)
    }

    @Test fun updateOfAFieldOrARawContactThatIsGoneWritesNothing() {
        val gone = louAndWade()
        assertEquals(WriteResult.Failed("the field is gone"), PeopleWrites(gone).update(listOf(number("1", dataId = 99, rawId = 1)), null))
        assertNothingWritten(gone)
        val deleted = louAndWade().apply { raws.remove(1) }
        assertEquals(WriteResult.Failed("the raw contact is gone"), PeopleWrites(deleted).update(listOf(number("1", dataId = 11, rawId = 1)), null))
        assertNothingWritten(deleted)
    }

    @Test fun updateWithoutWriteContactsWritesNothingAndAsksForTheGrant() {
        val fake = louAndWade().apply { held = false }
        assertEquals(WriteResult.Failed("WRITE_CONTACTS not held", needsGrant = true), PeopleWrites(fake).update(listOf(number("1", dataId = 11, rawId = 1)), PhotoEdit(1, JPEG)))
        assertNothingWritten(fake)
    }

    // ---------------------------------------------------------------------------------------------- the photo

    @Test fun aPhotoOnAnEditableRawContactIsWrittenToThatRawContactsStream() {
        val fake = louAndWade()
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).update(emptyList(), PhotoEdit(1, JPEG)))
        assertEquals(listOf<Call>(Call.Stream("$RAW/1/display_photo", JPEG.toList())), fake.calls)
    }

    @Test fun removingAPhotoDeletesThatRawContactsPhotoRowAndNoOther() {
        val fake = louAndWade()
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).update(emptyList(), PhotoEdit(1, null)))
        assertEquals(listOf<Call>(Call.Delete(DATA, "raw_contact_id=? AND mimetype=?", listOf("1", PHOTO_TYPE))), fake.calls)
    }

    @Test fun aPhotoChangeOnARawContactNotAllowedWritesNothingNotEvenTheFieldsBesideIt() {
        for (photo in listOf(PhotoEdit(2, JPEG), PhotoEdit(2, null))) {
            val fake = FakeContacts().apply { raw(1, phone, contact = 7); raw(2, work, contact = 7); field(11, 1) }
            assertEquals(WriteResult.Refused, PeopleWrites(fake).update(listOf(number("555 0111", dataId = 11, rawId = 1)), photo))
            assertNothingWritten(fake)
            assertEquals(listOf("write update raw=2: refused (not allowed)"), fake.lines)
        }
    }

    @Test fun thePhotoWriteAndThePhotoRemovalEachAskTheGuardThemselves() {
        // The raw contact is the phone's when the edit is checked, and is in the work account by the time the fields'
        // batch has gone: the photo step sees it as it is then, and writes nothing.
        for (photo in listOf(PhotoEdit(1, JPEG), PhotoEdit(1, null))) {
            val fake = louAndWade()
            fake.afterBatch = { fake.raws[1] = RawRef(1, work) }
            assertEquals(WriteResult.Refused, PeopleWrites(fake).update(listOf(number("555 0111", dataId = 11, rawId = 1)), photo))
            assertEquals("only the fields' batch", 1, fake.calls.size)
            assertTrue(fake.calls.single() is Call.Batch)
            assertEquals("write update raw=1: refused (not allowed)", fake.lines.last())
        }
    }

    // ---------------------------------------------------------------------------------------------- delete

    @Test fun deleteRemovesEveryRawContactBehindTheContact() {
        val fake = FakeContacts(allowed = setOf(personal)).apply { raw(1, phone, contact = 7); raw(3, personal, contact = 7) }
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).delete(7))
        assertEquals(listOf<Call>(Call.Batch(listOf(RowWrite(WriteOp.DELETE, "$RAW/1"), RowWrite(WriteOp.DELETE, "$RAW/3")))), fake.calls)
        assertEquals(listOf("write delete raw=1: ok", "write delete raw=3: ok"), fake.lines)
    }

    @Test fun deleteOfAContactWithOneRawContactNotEditableWritesNothing() {
        val fake = FakeContacts(allowed = setOf(personal)).apply { raw(1, phone, contact = 7); raw(3, personal, contact = 7); raw(2, work, contact = 7) }
        assertEquals(WriteResult.Refused, PeopleWrites(fake).delete(7))
        assertNothingWritten(fake)
        assertEquals(listOf("write delete raw=2: refused (not allowed)"), fake.lines)
        // And a contact that is wholly in an account not allowed.
        val wade = louAndWade()
        assertEquals(WriteResult.Refused, PeopleWrites(wade).delete(2))
        assertNothingWritten(wade)
    }

    @Test fun deleteOfAContactThatIsGoneOrWithoutWriteContactsWritesNothing() {
        val fake = louAndWade()
        assertEquals(WriteResult.Failed("the contact is gone"), PeopleWrites(fake).delete(99))
        assertNothingWritten(fake)
        fake.held = false
        assertEquals(WriteResult.Failed("WRITE_CONTACTS not held", needsGrant = true), PeopleWrites(fake).delete(1))
        assertNothingWritten(fake)
    }

    // ---------------------------------------------------------------------------------------------- link / unlink

    @Test fun linkIsAllowedOnAReadOnlyContactAndWritesKeepTogetherForEveryPair() {
        // Lou (phone, raws 1 and 4) with Wade (work, raw 2): an aggregation exception is the phone's own row.
        val fake = FakeContacts().apply { raw(4, phone, contact = 1); raw(1, phone, contact = 1); raw(2, work) }
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).link(1, 2))
        assertEquals(
            listOf<Call>(
                Call.Batch(
                    listOf(
                        RowWrite(WriteOp.UPDATE, EXCEPTIONS, mapOf<String, Any?>("type" to 1, "raw_contact_id1" to 2L, "raw_contact_id2" to 4L)),
                        RowWrite(WriteOp.UPDATE, EXCEPTIONS, mapOf<String, Any?>("type" to 1, "raw_contact_id1" to 1L, "raw_contact_id2" to 2L)),
                    ),
                ),
            ),
            fake.calls,
        )
        assertEquals(listOf("link 4+2: ok"), fake.lines)
    }

    @Test fun unlinkIsAllowedOnAReadOnlyContactAndWritesKeepSeparate() {
        val fake = FakeContacts().apply { raw(1, phone, contact = 7); raw(2, work, contact = 7) }
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).unlink(7, 2))
        assertEquals(listOf<Call>(Call.Batch(listOf(RowWrite(WriteOp.UPDATE, EXCEPTIONS, mapOf<String, Any?>("type" to 2, "raw_contact_id1" to 1L, "raw_contact_id2" to 2L))))), fake.calls)
        assertEquals(listOf("unlink 2+1: ok"), fake.lines)
    }

    @Test fun linkAndUnlinkWithNothingToJoinOrWithoutWriteContactsWriteNothing() {
        val fake = louAndWade()
        assertEquals(WriteResult.Failed("a contact is gone"), PeopleWrites(fake).link(1, 99))
        assertEquals(WriteResult.Failed("a contact is gone"), PeopleWrites(fake).link(1, 1))
        assertEquals(WriteResult.Failed("nothing is linked"), PeopleWrites(fake).unlink(1, 1))
        fake.held = false
        assertEquals(WriteResult.Failed("WRITE_CONTACTS not held", needsGrant = true), PeopleWrites(fake).link(1, 2))
        assertNothingWritten(fake)
    }

    // ---------------------------------------------------------------------------------------------- another profile (fix round F23)

    private val everyAccount = setOf(personal, work)
    private val workProfileContact = 1_000_000_007L

    @Test fun anotherProfilesContactIsRefusedByTheGuardNotBecauseItCannotBeFound() {
        // A work-profile contact as the enterprise search reads it: a contact id the provider says is another
        // profile's, and -1 for the raw contact it does not have here. Nothing about it is in this profile's provider.
        val ops = listOf<Pair<String, (PeopleWrites) -> WriteResult>>(
            "write delete raw=-1: refused (not allowed)" to { it.delete(workProfileContact) },
            "write update raw=-1: refused (not allowed)" to { it.update(listOf(number("555 0333", dataId = 555, rawId = -1)), null) },
            "write update raw=-1: refused (not allowed)" to { it.update(listOf(FieldEdit(FieldKind.NOTES, null, -1, "a note")), null) },
            "write update raw=-1: refused (not allowed)" to { it.update(emptyList(), PhotoEdit(-1, JPEG)) },
            "write update raw=-1: refused (not allowed)" to { it.update(listOf(number("555 0111", dataId = 11, rawId = 1)), PhotoEdit(-1, null)) },
            "write update raw=-1: refused (not allowed)" to { it.setMember(5, workProfileContact, true) },
            "write update raw=-1: refused (not allowed)" to { it.setMember(6, workProfileContact, false) },
        )
        for ((line, op) in ops) {
            val fake = withGroups(allowed = everyAccount).apply { field(11, 1); otherProfileContacts += workProfileContact }
            assertEquals(line, WriteResult.Refused, op(PeopleWrites(fake)))
            assertNothingWritten(fake)
            assertEquals(listOf(line), fake.lines)
        }
    }

    @Test fun aRawContactTheReadMarkedAsAnotherProfilesIsNeverWritten() {
        // Whatever names it and whatever account it claims — here the phone's own — the mark the read put on it is what
        // the guard goes by.
        val ops = listOf<(PeopleWrites) -> WriteResult>(
            { it.update(listOf(number("555 0333", dataId = 91, rawId = 9)), null) },
            { it.update(listOf(FieldEdit(FieldKind.NOTES, null, 9, "a note")), null) },
            { it.update(emptyList(), PhotoEdit(9, JPEG)) },
            { it.update(emptyList(), PhotoEdit(9, null)) },
            { it.delete(9) },
            { it.setMember(5, 9, true) },
            { it.setMember(5, 9, false) },
        )
        for ((i, op) in ops.withIndex()) {
            val fake = withGroups(allowed = everyAccount).apply { raw(9, phone, otherProfile = true); field(91, 9); memberships += 9L to 5L }
            assertEquals("op $i", WriteResult.Refused, op(PeopleWrites(fake)))
            assertNothingWritten(fake)
            assertEquals("op $i", true, fake.lines.single().endsWith("raw=9: refused (not allowed)"))
        }
    }

    @Test fun linkWithAnotherProfilesContactWritesNothing() {
        // An aggregation exception is allowed on any contact of this profile; one that names another profile's contact
        // cannot exist, and it is the guard that refuses it — not a raw contact that could not be found.
        val ops = listOf<Pair<String, (PeopleWrites) -> WriteResult>>(
            "link 1+-1: refused (not allowed)" to { it.link(1, workProfileContact) },
            "link -1+1: refused (not allowed)" to { it.link(workProfileContact, 1) },
            "unlink -1+gone: refused (not allowed)" to { it.unlink(workProfileContact, -1) },
            // A raw contact the read itself marked as another profile's, whatever account it claims.
            "link 1+9: refused (not allowed)" to { it.link(1, 9) },
            "unlink 4+9: refused (not allowed)" to { it.unlink(7, 4) },
        )
        for ((line, op) in ops) {
            val fake = louAndWade(allowed = everyAccount).apply {
                otherProfileContacts += workProfileContact
                raw(9, phone, otherProfile = true)
                raw(4, phone, contact = 7); raw(9, phone, contact = 7, otherProfile = true)
            }
            assertEquals(line, WriteResult.Refused, op(PeopleWrites(fake)))
            assertNothingWritten(fake)
            assertEquals(listOf(line), fake.lines)
        }
    }

    // ---------------------------------------------------------------------------------------------- SIM

    @Test fun aSimImportMakesEachEntryWithANumberAContactOnThePhoneWhateverIsAllowed() {
        val fake = FakeContacts(allowed = setOf(personal, work))
        val sim = listOf(SimContact(0, "Sim Bob", " 5550002 "), SimContact(1, "No Number", null), SimContact(2, "", "5550003"))
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).importSim(sim))
        val onThePhone = RowWrite(WriteOp.INSERT, RAW, mapOf("account_name" to null, "account_type" to null))
        assertEquals(
            listOf<Call>(
                Call.Batch(
                    listOf(
                        onThePhone,
                        RowWrite(WriteOp.INSERT, DATA, mapOf("mimetype" to NAME_TYPE, "data1" to "Sim Bob"), backReference = "raw_contact_id" to 0),
                        RowWrite(WriteOp.INSERT, DATA, mapOf("mimetype" to PHONE_TYPE, "data1" to "5550002", "data2" to 2, "data3" to null), backReference = "raw_contact_id" to 0),
                    ),
                ),
                Call.Batch(
                    listOf(
                        onThePhone,
                        RowWrite(WriteOp.INSERT, DATA, mapOf("mimetype" to PHONE_TYPE, "data1" to "5550003", "data2" to 2, "data3" to null), backReference = "raw_contact_id" to 0),
                    ),
                ),
            ),
            fake.calls,
        )
        assertEquals("sim import: 2 of 3", fake.lines.last())
    }

    @Test fun aSimImportOnAPhoneThatNamesItsLocalAccountLandsInThatAccount() {
        val maker = ContactAccount("Phone", "vnd.maker.local")
        val fake = FakeContacts(allowed = setOf(personal)).apply { policy = EditPolicy(maker, setOf(personal)) }
        assertEquals(WriteResult.Ok(), PeopleWrites(fake).importSim(listOf(SimContact(0, "", "5550003"))))
        assertEquals(mapOf("account_name" to "Phone", "account_type" to "vnd.maker.local"), (fake.calls.single() as Call.Batch).rows[0].values)
    }

    @Test fun aSimImportWithoutWriteContactsWritesNothingAndAsksForTheGrant() {
        val fake = FakeContacts(held = false)
        assertEquals(WriteResult.Failed("WRITE_CONTACTS not held", needsGrant = true), PeopleWrites(fake).importSim(listOf(SimContact(0, "Sim Bob", "5550002"), SimContact(1, "Sim Cy", "5550004"))))
        assertNothingWritten(fake)
        assertEquals("sim import: 0 of 2", fake.lines.last())
    }

    // ---------------------------------------------------------------------------------------------- groups

    private fun withGroups(allowed: Set<ContactAccount> = emptySet()) = FakeContacts(allowed).apply {
        groups[5] = ContactGroup(5, "Family", phone, 0)
        groups[6] = ContactGroup(6, "Friends", personal, 0)
        groups[8] = ContactGroup(8, "Team", work, 0)
        raw(1, phone); raw(3, personal); raw(2, work)
    }

    @Test fun aGroupIsCreatedInThePhoneOrAnAllowedAccount() {
        for (account in listOf(phone, personal)) {
            val fake = withGroups(allowed = setOf(personal))
            assertEquals(WriteResult.Ok(id = 100), PeopleWrites(fake).createGroup(" Family ", account))
            assertEquals(listOf<Call>(Call.Insert(GROUPS, mapOf("title" to "Family", "account_name" to account.name, "account_type" to account.type, "group_visible" to 1))), fake.calls)
            assertEquals(listOf("group create 100: ok"), fake.lines)
        }
    }

    @Test fun aGroupOpInAnAccountNotAllowedWritesNothing() {
        val ops = listOf<Pair<String, (PeopleWrites) -> WriteResult>>(
            "group create new: failed refused (not allowed)" to { it.createGroup("Team", work) },
            "group rename 8: failed refused (not allowed)" to { it.renameGroup(8, "Crew") },
            "group delete 8: failed refused (not allowed)" to { it.deleteGroup(8) },
            "write update raw=2: refused (not allowed)" to { it.setMember(8, 2, true) },
            "write update raw=2: refused (not allowed)" to { it.setMember(8, 2, false) },
        )
        for ((line, op) in ops) {
            val fake = withGroups(allowed = setOf(personal)).apply { memberships += 2L to 8L }
            assertEquals(line, WriteResult.Refused, op(PeopleWrites(fake)))
            assertNothingWritten(fake)
            assertEquals(listOf(line), fake.lines)
        }
    }

    @Test fun aGroupIsRenamedByItsOwnRow() {
        for (id in listOf(5L, 6L)) {
            val fake = withGroups(allowed = setOf(personal))
            assertEquals(WriteResult.Ok(id = id), PeopleWrites(fake).renameGroup(id, " Home "))
            assertEquals(listOf<Call>(Call.Update("$GROUPS/$id", mapOf("title" to "Home"), null, emptyList())), fake.calls)
        }
    }

    @Test fun anAllowedAccountsGroupIsDeletedThroughThePlainUriForItsOwnAdapterToFinish() {
        val fake = withGroups(allowed = setOf(personal))
        assertEquals(WriteResult.Ok(id = 6), PeopleWrites(fake).deleteGroup(6))
        assertEquals(listOf<Call>(Call.Delete("$GROUPS/6", null, emptyList())), fake.calls)
        assertEquals(listOf("group delete 6: ok"), fake.lines)
    }

    @Test fun aPhoneGroupIsDeletedOutright() {
        // The fix round's F22: the phone has no sync adapter to remove a row marked deleted, so People removes it.
        val fake = withGroups(allowed = setOf(personal))
        assertEquals(WriteResult.Ok(id = 5), PeopleWrites(fake).deleteGroup(5))
        assertEquals(listOf<Call>(Call.Delete("$GROUPS/5?caller_is_syncadapter=true", null, emptyList())), fake.calls)
    }

    @Test fun onAPhoneThatNamesItsLocalAccountItsGroupIsThePhoneGroup() {
        val maker = ContactAccount("Phone", "vnd.maker.local")
        val fake = FakeContacts().apply {
            policy = EditPolicy(maker, setOf(personal))
            groups[5] = ContactGroup(5, "Family", maker, 0)
            groups[6] = ContactGroup(6, "Friends", personal, 0)
            groups[7] = ContactGroup(7, "Stray", phone, 0)
        }
        assertEquals(WriteResult.Ok(id = 5), PeopleWrites(fake).deleteGroup(5))
        assertEquals(WriteResult.Ok(id = 6), PeopleWrites(fake).deleteGroup(6))
        // A group with no account at all is not this phone's local account: not People's to write, by either URI.
        assertEquals(WriteResult.Refused, PeopleWrites(fake).deleteGroup(7))
        assertEquals(listOf<Call>(Call.Delete("$GROUPS/5?caller_is_syncadapter=true", null, emptyList()), Call.Delete("$GROUPS/6", null, emptyList())), fake.calls)
    }

    @Test fun noGroupInAnAccountIsEverWrittenThroughTheSyncAdapterUri() {
        for (allowed in listOf(emptySet(), setOf(personal), setOf(personal, work))) {
            val fake = withGroups(allowed)
            val writes = PeopleWrites(fake)
            writes.createGroup("New", personal); writes.createGroup("New", work)
            for (id in listOf(6L, 8L)) { writes.renameGroup(id, "Renamed"); writes.deleteGroup(id); writes.setMember(id, if (id == 6L) 3 else 2, true) }
            val uris = fake.calls.map { call ->
                when (call) {
                    is Call.Insert -> call.uri
                    is Call.Update -> call.uri
                    is Call.Delete -> call.uri
                    is Call.Batch -> call.rows.joinToString { it.uri }
                    is Call.Stream -> call.uri
                }
            }
            assertEquals("$allowed $uris", emptyList<String>(), uris.filter { "caller_is_syncadapter" in it })
        }
    }

    @Test fun aGroupOpOnAGroupThatIsGoneOrWithNoNameOrWithoutWriteContactsWritesNothing() {
        val fake = withGroups()
        assertEquals(WriteResult.Failed("the group is gone"), PeopleWrites(fake).renameGroup(99, "x"))
        assertEquals(WriteResult.Failed("the group is gone"), PeopleWrites(fake).deleteGroup(99))
        assertEquals(WriteResult.Failed("the group is gone"), PeopleWrites(fake).setMember(99, 1, true))
        assertEquals(WriteResult.Failed("a group needs a name"), PeopleWrites(fake).createGroup("  ", phone))
        assertEquals(WriteResult.Failed("a group needs a name"), PeopleWrites(fake).renameGroup(5, ""))
        assertEquals(WriteResult.Failed("the contact is not in the group's account"), PeopleWrites(fake).setMember(5, 2, true))
        fake.held = false
        for (result in listOf(PeopleWrites(fake).createGroup("x", phone), PeopleWrites(fake).renameGroup(5, "x"), PeopleWrites(fake).deleteGroup(5), PeopleWrites(fake).setMember(5, 1, true))) {
            assertEquals(WriteResult.Failed("WRITE_CONTACTS not held", needsGrant = true), result)
        }
        assertNothingWritten(fake)
    }

    @Test fun aMemberIsAddedAsOneMembershipRowOnItsRawContactInTheGroupsAccount() {
        // Contact 7 is Lou on the phone (raw 1) linked with a personal-account raw (3): the phone group takes raw 1.
        val fake = FakeContacts(allowed = setOf(personal)).apply { groups[5] = ContactGroup(5, "Family", phone, 0); raw(3, personal, contact = 7); raw(1, phone, contact = 7) }
        assertEquals(WriteResult.Ok(rawId = 1), PeopleWrites(fake).setMember(5, 7, true))
        assertEquals(listOf<Call>(Call.Insert(DATA, mapOf("raw_contact_id" to 1L, "mimetype" to MEMBERSHIP_TYPE, "data1" to 5L))), fake.calls)
        assertEquals(listOf("write update raw=1: ok"), fake.lines)
    }

    @Test fun addingSomeoneWhoIsAlreadyAMemberWritesNothingMore() {
        val fake = withGroups().apply { memberships += 1L to 5L }
        assertEquals(WriteResult.Ok(rawId = 1), PeopleWrites(fake).setMember(5, 1, true))
        assertNothingWritten(fake)
    }

    @Test fun aMemberIsRemovedByThatRawContactsMembershipRowOfThatGroup() {
        val fake = withGroups(allowed = setOf(personal)).apply { memberships += 3L to 6L }
        assertEquals(WriteResult.Ok(rawId = 3), PeopleWrites(fake).setMember(6, 3, false))
        assertEquals(listOf<Call>(Call.Delete(DATA, "raw_contact_id=? AND mimetype=? AND data1=?", listOf("3", MEMBERSHIP_TYPE, "6"))), fake.calls)
    }
}

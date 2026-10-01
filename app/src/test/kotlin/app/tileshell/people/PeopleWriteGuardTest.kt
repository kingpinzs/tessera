package app.tileshell.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The People write guard (phase 16, Q-16-3; Decisions "the People write guard and the Can edit list" point (5)): every
 * allowed case and every refusal the Decisions line lists, by name.
 */
class PeopleWriteGuardTest {
    private val phone = ContactAccount(null, null)
    private val work = ContactAccount("qa.work@example.com", "com.example")
    private val personal = ContactAccount("qa.personal@example.com", "com.example")

    /** AOSP: the local account is null / null. Nothing is on "Can edit". */
    private val nothingAllowed = EditPolicy(local = phone, allowed = emptySet())

    /** The personal account ticked; work never. */
    private val personalAllowed = EditPolicy(local = phone, allowed = setOf(personal))

    private val lou = RawRef(11, phone)
    private val wade = RawRef(12, work)
    private val pia = RawRef(13, personal)

    private fun allowed(write: PeopleWrite, policy: EditPolicy) = PeopleWriteGuard.check(write, policy) == GuardVerdict.Allowed
    private fun refused(write: PeopleWrite, policy: EditPolicy) = PeopleWriteGuard.check(write, policy) is GuardVerdict.Refused

    // ------------------------------------------------------------------------------------------- allowed cases

    @Test
    fun `allowed - a phone-only raw contact, every op, with nothing on Can edit`() {
        for (op in WriteOp.entries) {
            assertTrue("raw contact $op", allowed(PeopleWrite.RawContactRow(op, lou), nothingAllowed))
            assertTrue("data row $op", allowed(PeopleWrite.DataRow(op, lou), nothingAllowed))
        }
        assertTrue(allowed(PeopleWrite.DeleteContact(listOf(lou)), nothingAllowed))
        assertTrue(allowed(PeopleWrite.ContactColumn("starred", listOf(lou)), nothingAllowed))
    }

    @Test
    fun `allowed - a new contact, a SIM import and an INSERT prefill on the phone`() {
        for (source in NewContactSource.entries) {
            assertTrue("$source", allowed(PeopleWrite.NewRawContact(phone, source), nothingAllowed))
        }
    }

    @Test
    fun `allowed - an account on Can edit, every op`() {
        for (op in WriteOp.entries) {
            assertTrue("raw contact $op", allowed(PeopleWrite.RawContactRow(op, pia), personalAllowed))
            assertTrue("data row $op", allowed(PeopleWrite.DataRow(op, pia), personalAllowed))
            assertTrue("group $op", allowed(PeopleWrite.GroupRow(op, personal), personalAllowed))
        }
        assertTrue(allowed(PeopleWrite.NewRawContact(personal, NewContactSource.EDITOR), personalAllowed))
        assertTrue(allowed(PeopleWrite.DeleteContact(listOf(pia)), personalAllowed))
        assertTrue(allowed(PeopleWrite.DeleteContact(listOf(lou, pia)), personalAllowed))
        assertTrue(allowed(PeopleWrite.ContactColumn("starred", listOf(lou, pia)), personalAllowed))
    }

    @Test
    fun `allowed - Link and Unlink on a read-only contact`() {
        assertTrue(allowed(PeopleWrite.Aggregation(lou, wade, together = true), nothingAllowed))
        assertTrue(allowed(PeopleWrite.Aggregation(lou, wade, together = false), nothingAllowed))
        // Two read-only contacts, and another profile's: the exception row is local to the phone either way.
        assertTrue(allowed(PeopleWrite.Aggregation(wade, pia, together = true), nothingAllowed))
        assertTrue(allowed(PeopleWrite.Aggregation(wade, wade.copy(id = 99, otherProfile = true), together = false), nothingAllowed))
    }

    @Test
    fun `allowed - a group on the phone, every op`() {
        for (op in WriteOp.entries) assertTrue("$op", allowed(PeopleWrite.GroupRow(op, phone), nothingAllowed))
    }

    // ------------------------------------------------------------------------------------------- refusals

    @Test
    fun `refused - insert, update and delete of a raw contact in an account not allowed`() {
        for (op in WriteOp.entries) {
            assertEquals("$op", GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.RawContactRow(op, wade), nothingAllowed))
            // Work stays refused when another account is allowed.
            assertEquals("$op", GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.RawContactRow(op, wade), personalAllowed))
        }
    }

    @Test
    fun `refused - insert, update and delete of a data row in an account not allowed`() {
        for (op in WriteOp.entries) {
            assertEquals("$op", GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.DataRow(op, wade), nothingAllowed))
            assertEquals("$op", GuardVerdict.Refused(pia.id), PeopleWriteGuard.check(PeopleWrite.DataRow(op, pia), nothingAllowed))
            assertEquals("$op", GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.DataRow(op, wade), personalAllowed))
        }
    }

    @Test
    fun `refused - the aggregate delete with one raw contact not editable`() {
        assertEquals(GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.DeleteContact(listOf(lou, wade)), nothingAllowed))
        assertEquals(GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.DeleteContact(listOf(pia, lou, wade)), personalAllowed))
        assertTrue(refused(PeopleWrite.DeleteContact(listOf(wade)), nothingAllowed))
        // An aggregate nothing could be resolved for is not a delete People can make.
        assertTrue(refused(PeopleWrite.DeleteContact(emptyList()), nothingAllowed))
    }

    @Test
    fun `refused - the STARRED update with one raw contact not editable`() {
        assertEquals(GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.ContactColumn("starred", listOf(lou, wade)), nothingAllowed))
        assertEquals(GuardVerdict.Refused(wade.id), PeopleWriteGuard.check(PeopleWrite.ContactColumn("starred", listOf(lou, pia, wade)), personalAllowed))
        assertTrue(refused(PeopleWrite.ContactColumn("starred", emptyList()), nothingAllowed))
    }

    @Test
    fun `refused - a new contact naming an account not allowed`() {
        assertTrue(refused(PeopleWrite.NewRawContact(work, NewContactSource.EDITOR), nothingAllowed))
        assertTrue(refused(PeopleWrite.NewRawContact(personal, NewContactSource.EDITOR), nothingAllowed))
        assertTrue(refused(PeopleWrite.NewRawContact(work, NewContactSource.EDITOR), personalAllowed))
    }

    @Test
    fun `refused - a SIM import naming an account, allowed or not`() {
        assertTrue(refused(PeopleWrite.NewRawContact(work, NewContactSource.SIM_IMPORT), nothingAllowed))
        // A SIM import goes to the phone: an account on "Can edit" is still not where it lands.
        assertTrue(refused(PeopleWrite.NewRawContact(personal, NewContactSource.SIM_IMPORT), personalAllowed))
    }

    @Test
    fun `refused - an INSERT prefill naming an account not allowed`() {
        assertTrue(refused(PeopleWrite.NewRawContact(work, NewContactSource.INSERT_PREFILL), nothingAllowed))
        assertTrue(refused(PeopleWrite.NewRawContact(work, NewContactSource.INSERT_PREFILL), personalAllowed))
        assertTrue(refused(PeopleWrite.NewRawContact(personal, NewContactSource.INSERT_PREFILL), nothingAllowed))
    }

    @Test
    fun `refused - a group op in an account not allowed`() {
        for (op in WriteOp.entries) {
            assertTrue("$op", refused(PeopleWrite.GroupRow(op, work), nothingAllowed))
            assertTrue("$op", refused(PeopleWrite.GroupRow(op, work), personalAllowed))
            assertTrue("$op", refused(PeopleWrite.GroupRow(op, personal), nothingAllowed))
        }
    }

    @Test
    fun `refused - another profile's contact, whatever is allowed`() {
        val workProfile = RawRef(-1, phone, otherProfile = true)
        val allAllowed = EditPolicy(local = phone, allowed = setOf(work, personal))
        for (op in WriteOp.entries) {
            assertTrue("$op", refused(PeopleWrite.DataRow(op, workProfile), allAllowed))
            assertTrue("$op", refused(PeopleWrite.RawContactRow(op, workProfile), allAllowed))
        }
        assertTrue(refused(PeopleWrite.DeleteContact(listOf(workProfile)), allAllowed))
        assertFalse(PeopleWriteGuard.editable(wade.copy(otherProfile = true), allAllowed))
    }

    // ------------------------------------------------------------------------------------------- the sync-adapter URI (fix round F22)

    @Test
    fun `allowed - the delete of a phone group through the sync-adapter URI`() {
        val delete = PeopleWrite.GroupRow(WriteOp.DELETE, phone, viaSyncAdapter = true)
        assertTrue(allowed(delete, nothingAllowed))
        assertTrue(allowed(delete, personalAllowed))
        // A phone whose maker names its local account: that account's group is the phone group, a null-account one is not.
        val maker = ContactAccount("Phone", "vnd.sec.contact.phone")
        val named = EditPolicy(local = maker, allowed = setOf(personal))
        assertTrue(allowed(PeopleWrite.GroupRow(WriteOp.DELETE, maker, viaSyncAdapter = true), named))
        assertTrue(refused(delete, named))
    }

    @Test
    fun `refused - any other group write through the sync-adapter URI, whatever is allowed`() {
        val allAllowed = EditPolicy(local = phone, allowed = setOf(work, personal))
        for (policy in listOf(nothingAllowed, personalAllowed, allAllowed)) {
            // A group in an account, allowed or not: removing its row outright is that account's own adapter's work.
            for (account in listOf(personal, work)) for (op in WriteOp.entries) {
                assertTrue("$op ${account.id}", refused(PeopleWrite.GroupRow(op, account, viaSyncAdapter = true), policy))
            }
            // On the phone, only the delete: a group is not created or renamed as a sync adapter.
            assertTrue(refused(PeopleWrite.GroupRow(WriteOp.INSERT, phone, viaSyncAdapter = true), policy))
            assertTrue(refused(PeopleWrite.GroupRow(WriteOp.UPDATE, phone, viaSyncAdapter = true), policy))
        }
        // The plain URI's rule is as it was: an allowed account's group, every op; the phone's, every op.
        for (op in WriteOp.entries) {
            assertTrue(allowed(PeopleWrite.GroupRow(op, personal), personalAllowed))
            assertTrue(allowed(PeopleWrite.GroupRow(op, phone), nothingAllowed))
            assertTrue(refused(PeopleWrite.GroupRow(op, work), personalAllowed))
        }
    }

    // ------------------------------------------------------------------------------------------- the local account

    @Test
    fun `phone-only is the device's local account, never a literal null`() {
        // A phone whose maker names its local account: that account is the phone, and a null account is not.
        val maker = ContactAccount("Phone", "vnd.sec.contact.phone")
        val named = EditPolicy(local = maker, allowed = emptySet())
        assertTrue(PeopleWriteGuard.editable(RawRef(1, maker), named))
        assertFalse(PeopleWriteGuard.editable(RawRef(2, ContactAccount(null, null)), named))
        assertTrue(allowed(PeopleWrite.NewRawContact(maker, NewContactSource.SIM_IMPORT), named))
        assertTrue(refused(PeopleWrite.NewRawContact(ContactAccount(null, null), NewContactSource.SIM_IMPORT), named))
        // On AOSP the same rule reads null / null as the phone, and the maker's account as an account not allowed.
        assertTrue(PeopleWriteGuard.editable(RawRef(2, ContactAccount(null, null)), nothingAllowed))
        assertFalse(PeopleWriteGuard.editable(RawRef(1, maker), nothingAllowed))
        // Half a null is not the phone.
        assertFalse(PeopleWriteGuard.editable(RawRef(3, ContactAccount(null, "com.example")), nothingAllowed))
        assertFalse(PeopleWriteGuard.editable(RawRef(4, ContactAccount("x", null)), nothingAllowed))
    }

    // ------------------------------------------------------------------------------------------- the card

    @Test
    fun `card - a read-only contact has no Edit and no Delete and names its account`() {
        val actions = PeopleWriteGuard.cardActions(listOf(wade), nothingAllowed)
        assertFalse(actions.edit)
        assertFalse(actions.delete)
        assertEquals(work, actions.readOnlyAccount)
        assertEquals("This contact is in qa.work@example.com. To change it, allow that account in Can edit.", CardRules.readOnlyLine(actions.readOnlyAccount, actions.otherProfile))
    }

    @Test
    fun `card - a phone-only contact has Edit and Delete and no read-only line`() {
        val actions = PeopleWriteGuard.cardActions(listOf(lou), nothingAllowed)
        assertTrue(actions.edit)
        assertTrue(actions.delete)
        assertNull(actions.readOnlyAccount)
    }

    @Test
    fun `card - a mixed contact has Edit and no Delete`() {
        val actions = PeopleWriteGuard.cardActions(listOf(lou, wade), nothingAllowed)
        assertTrue(actions.edit)
        assertFalse(actions.delete)
        assertNull(actions.readOnlyAccount)
    }

    @Test
    fun `card - allowing the account brings Edit and Delete, and unticking takes them away`() {
        assertTrue(PeopleWriteGuard.cardActions(listOf(pia), personalAllowed).let { it.edit && it.delete })
        assertFalse(PeopleWriteGuard.cardActions(listOf(pia), nothingAllowed).let { it.edit || it.delete })
        assertFalse(PeopleWriteGuard.cardActions(listOf(wade), personalAllowed).let { it.edit || it.delete })
    }

    @Test
    fun `card - a work-profile contact is read-only and says so`() {
        val actions = PeopleWriteGuard.cardActions(listOf(RawRef(-1, phone, otherProfile = true)), personalAllowed)
        assertFalse(actions.edit)
        assertFalse(actions.delete)
        assertTrue(actions.otherProfile)
        assertEquals("This contact is in your work profile. It can't be changed here.", CardRules.readOnlyLine(actions.readOnlyAccount, actions.otherProfile))
    }

    // ------------------------------------------------------------------------------------------- the Can edit list

    @Test
    fun `can edit - the rows are the distinct non-null account pairs, and the phone has none`() {
        val named = listOf(work, phone, personal, work, ContactAccount("qa", "qa"), ContactAccount(null, "com.example"))
        assertEquals(listOf(personal, work, ContactAccount("qa", "qa")), CanEditRules.rows(named, nothingAllowed))
    }

    @Test
    fun `can edit - a phone that names its local account gets no row for it`() {
        val maker = ContactAccount("Phone", "vnd.sec.contact.phone")
        assertEquals(listOf(work), CanEditRules.rows(listOf(maker, work), EditPolicy(maker, emptySet())))
    }

    @Test
    fun `can edit - an account the provider no longer names is dropped, so re-added it starts not allowed`() {
        val ticked = setOf(personal, work)
        val afterRemoval = CanEditRules.prune(ticked, named = listOf(work, phone))
        assertEquals(setOf(work), afterRemoval)
        // The account comes back: nothing re-allows it.
        assertEquals(setOf(work), CanEditRules.prune(afterRemoval, named = listOf(work, personal, phone)))
    }

    @Test
    fun `can edit - ticking and unticking, and nothing without a name and a type`() {
        assertEquals(setOf(personal), CanEditRules.toggle(emptySet(), personal, on = true))
        assertEquals(emptySet<ContactAccount>(), CanEditRules.toggle(setOf(personal), personal, on = false))
        assertEquals(emptySet<ContactAccount>(), CanEditRules.toggle(emptySet(), phone, on = true))
        assertEquals(emptySet<ContactAccount>(), CanEditRules.toggle(emptySet(), ContactAccount("x", null), on = true))
    }

    @Test
    fun `nothing is allowed by default`() {
        assertFalse(nothingAllowed.canWrite(work))
        assertFalse(nothingAllowed.canWrite(personal))
        assertTrue(nothingAllowed.canWrite(phone))
        assertEquals("com.example:qa.work@example.com", work.id)
    }
}

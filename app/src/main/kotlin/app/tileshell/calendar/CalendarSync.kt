package app.tileshell.calendar

import android.content.Context
import android.provider.CalendarContract
import app.tileshell.calendar.CalendarWriteGuard.Path
import app.tileshell.calendar.CalendarWriteGuard.Refusal
import app.tileshell.calendar.CalendarWriteGuard.Verdict
import app.tileshell.diag.Diagnostics

/** The "synced to <calendar>" marker of a local event's page (Q-16-2; r3 D5: the account is named with the calendar). */
data class SyncMarker(val targetId: Long, val calendarName: String, val accountName: String, val warning: Boolean, val targetAllowed: Boolean)

/**
 * Sync (Q2 rules 3–4; Decisions T16-1, T16-3, T16-12, r3 D5, r3 V13): the ONLY road from the shell's local calendar to
 * an account calendar, and only ever on the user's tap. A local event is copied into a calendar the user allowed on
 * "Can sync to"; the local event stays the source and a later Sync pushes it over the copy. Every provider write
 * here is [CalendarWrites]', behind the write guard.
 */
object CalendarSync {
    sealed class Outcome(val text: String) {
        /** A first copy made, or nothing differed and nothing was written. */
        data object Ok : Outcome("ok")
        data object Updated : Outcome("updated")
        /** The copy was gone on the other side and was made again. */
        data object Recreated : Outcome("recreated")
        class Failed(error: String) : Outcome("failed $error")
        data object CalendarGone : Outcome(Refusal.CALENDAR_GONE.text)
        data object ReadOnly : Outcome(Refusal.READ_ONLY.text)
        data object MappingStale : Outcome(Refusal.MAPPING_STALE.text)
        data object Refused : Outcome(Refusal.NOT_ALLOWED.text)

        val wrote: Boolean get() = this is Ok || this is Updated || this is Recreated
    }

    /** "Can sync to" (r3 D5): the non-LOCAL calendars the phone may write. Never Tessera, Birthdays or a read-only one. */
    fun candidates(calendars: List<CalendarInfo>): List<CalendarInfo> = calendars.filter { it.canBeSyncTarget }

    /** The Sync picker: the candidates the user has allowed. */
    fun targets(calendars: List<CalendarInfo>, state: SyncState): List<CalendarInfo> = candidates(calendars).filter { it.key in state.allowed }

    fun marker(state: SyncState, localEventId: Long, calendars: List<CalendarInfo>): SyncMarker? {
        val mapping = state.mappings[localEventId] ?: return null
        val present = calendars.firstOrNull { it.key == mapping.target }
        return SyncMarker(
            targetId = mapping.target.id,
            // A calendar that is no longer on the phone has no name left to read; its account's is what the store holds.
            calendarName = present?.shownName ?: mapping.target.accountName,
            accountName = mapping.target.accountName,
            warning = present == null || present.accessLevel < CalendarWriteGuard.ACCESS_CONTRIBUTOR,
            targetAllowed = present != null && mapping.target in state.allowed,
        )
    }

    private fun outcomeOf(refusal: Refusal): Outcome = when (refusal) {
        Refusal.NOT_ALLOWED -> Outcome.Refused
        Refusal.MAPPING_STALE -> Outcome.MappingStale
        Refusal.READ_ONLY -> Outcome.ReadOnly
        Refusal.CALENDAR_GONE -> Outcome.CalendarGone
    }

    private fun <T> outcomeOf(result: WriteResult<T>): Outcome? = when (result) {
        is WriteResult.Ok -> null
        is WriteResult.Refused -> outcomeOf(result.why)
        is WriteResult.Failed -> Outcome.Failed(result.error)
    }

    /**
     * The tapped push of [localEventId] to [target]. One `[calendar] sync …` line says how it ended. One Sync at a time:
     * a Sync tapped twice quickly makes one copy, and the second compares that copy with the local event (`ok`).
     */
    @Synchronized
    fun sync(context: Context, localEventId: Long, target: CalendarKey): Outcome {
        val outcome = push(context, localEventId, target)
        Diagnostics.add("calendar", "sync event=$localEventId -> calendar ${target.id}: ${outcome.text}")
        return outcome
    }

    private fun push(context: Context, localEventId: Long, target: CalendarKey): Outcome {
        val store = CalendarSyncStore.get(context)
        val local = CalendarReads.event(context, localEventId) ?: return Outcome.Failed("the event is gone")
        // The target must still be the calendar the user allowed: same id, same account. An account removed and
        // added again has new ids, and an id that was handed to another calendar is not this one.
        val present = CalendarReads.calendar(context, target.id)
        if (present == null || present.key != target) return Outcome.CalendarGone
        // Synced to another calendar before: that copy stays where it is, as the account event it is, and this
        // Sync starts a new one.
        store.current.mappings[localEventId]?.takeIf { it.target != target }?.let { store.update { s -> SyncStateRules.unmap(s, localEventId) } }

        // Asked before anything is compared or written: a target that became read-only, or a copy that was moved,
        // refuses the Sync even when nothing has changed.
        (CalendarWrites.syncCheck(context, localEventId, target) as? Verdict.Refused)?.let { return outcomeOf(it.why) }

        val localExceptions = CalendarReads.exceptions(context, localEventId)
        val localReminders = CalendarReads.reminders(context, localEventId)
        val localSnapshot = SyncRules.snapshot(local, localReminders, localExceptions)
        val mapping = store.current.mappings[localEventId]
        val copy = mapping?.let { CalendarReads.event(context, it.copyEventId) }

        if (mapping != null && copy != null) {
            val copyExceptions = CalendarReads.exceptions(context, copy.id)
            val copySnapshot = SyncRules.snapshot(copy, CalendarReads.reminders(context, copy.id), copyExceptions)
            // r3 V13: the copy is compared with the local event, so an edit made on the other side is overwritten.
            if (!SyncRules.differs(localSnapshot, copySnapshot)) return Outcome.Ok
            // An occurrence the copy changed and the local series did not cannot be un-made by a normal app (deleting
            // an exception row only cancels its occurrence), so the copy is made again from the local event.
            val localTimes = localExceptions.mapTo(HashSet()) { it.originalInstanceTime }
            if (copyExceptions.any { it.originalInstanceTime !in localTimes }) {
                copyExceptions.forEach { e -> outcomeOf(CalendarWrites.syncDeleteRow(context, localEventId, e.id))?.let { return it } }
                outcomeOf(CalendarWrites.syncDeleteRow(context, localEventId, copy.id))?.let { return it }
                return insertCopy(context, localEventId, target, local, localReminders, localExceptions) ?: Outcome.Updated
            }
            if (localSnapshot.fields != copySnapshot.fields) {
                outcomeOf(CalendarWrites.syncUpdateCopy(context, localEventId, copy.id, valuesOf(local)))?.let { return it }
            }
            if (localSnapshot.reminders != copySnapshot.reminders) {
                outcomeOf(CalendarWrites.syncSetReminders(context, localEventId, copy.id, localReminders))?.let { return it }
            }
            if (localSnapshot.exceptions != copySnapshot.exceptions) {
                pushExceptions(context, localEventId, copy.id, localExceptions, copyExceptions)?.let { return it }
            }
            return Outcome.Updated
        }
        return insertCopy(context, localEventId, target, local, localReminders, localExceptions) ?: if (mapping != null) Outcome.Recreated else Outcome.Ok
    }

    /** Null when every write went through; else how the first one that did not ended. */
    private fun insertCopy(
        context: Context, localEventId: Long, target: CalendarKey, local: EventDetail,
        reminders: List<Pair<Int, Int>>, exceptions: List<EventDetail>,
    ): Outcome? {
        val copyId = when (val inserted = CalendarWrites.syncInsertCopy(context, localEventId, target, valuesOf(local))) {
            is WriteResult.Ok -> inserted.value
            else -> return outcomeOf(inserted)
        }
        if (reminders.isNotEmpty()) outcomeOf(CalendarWrites.syncSetReminders(context, localEventId, copyId, reminders))?.let { return it }
        return pushExceptions(context, localEventId, copyId, exceptions, emptyList())
    }

    /**
     * The copy's exception events, one per local exception, matched by the occurrence they replace: an existing one is
     * updated in place, a missing one is made with `ORIGINAL_ID` re-pointed at the COPY's master.
     */
    private fun pushExceptions(context: Context, localEventId: Long, copyMasterId: Long, local: List<EventDetail>, copy: List<EventDetail>): Outcome? {
        val existing = copy.associateBy { it.originalInstanceTime }
        for (e in local) {
            val time = e.originalInstanceTime ?: continue
            val cancelled = e.status == CalendarContract.Events.STATUS_CANCELED
            val row = existing[time]
            val rowId = if (row == null) {
                val values = ExceptionValues(
                    title = e.title.orEmpty(), location = e.location.orEmpty(), description = e.description.orEmpty(),
                    dtstart = e.dtstart, duration = "P${((e.dtend ?: e.dtstart) - e.dtstart) / 1000}S",
                    status = if (cancelled) CalendarContract.Events.STATUS_CANCELED else CalendarContract.Events.STATUS_CONFIRMED,
                )
                when (val inserted = CalendarWrites.syncInsertException(context, localEventId, copyMasterId, time, values)) {
                    is WriteResult.Ok -> inserted.value
                    else -> return outcomeOf(inserted)
                }
            } else {
                if (SyncRules.exception(e) != SyncRules.exception(row)) {
                    val values = valuesOf(e).copy(status = if (cancelled) CalendarContract.Events.STATUS_CANCELED else CalendarContract.Events.STATUS_CONFIRMED)
                    outcomeOf(CalendarWrites.syncUpdateCopy(context, localEventId, row.id, values))?.let { return it }
                }
                row.id
            }
            if (!cancelled) {
                val wanted = CalendarReads.reminders(context, e.id)
                if (wanted.toSet() != CalendarReads.reminders(context, rowId).toSet()) {
                    outcomeOf(CalendarWrites.syncSetReminders(context, localEventId, rowId, wanted))?.let { return it }
                }
            }
        }
        return null
    }

    /** The copied fields: RRULE / DURATION / EXDATE for a repeating event, DTEND for a single one. */
    private fun valuesOf(e: EventDetail) = EventValues(
        title = e.title.orEmpty(),
        location = e.location.orEmpty(),
        description = e.description.orEmpty(),
        dtstart = e.dtstart,
        dtend = if (e.recurring) null else (e.dtend ?: e.dtstart),
        duration = if (e.recurring) e.duration else null,
        allDay = e.allDay,
        timezone = e.timezone ?: "UTC",
        rrule = e.rrule?.takeIf { it.isNotBlank() },
        rdate = e.rdate?.takeIf { it.isNotBlank() },
        exrule = e.exrule?.takeIf { it.isNotBlank() },
        exdate = e.exdate?.takeIf { it.isNotBlank() },
        availability = e.availability,
    )

    /**
     * Deletes the local event [eventId] (with its exception events when it is a series).
     *
     * "Delete here" (the default, and all Tess ever does): the copy is kept and its mapping dropped, so it shows from
     * then on as the account event it is. "Delete here and from <calendar>" ([both], still a user's tap): the copy
     * goes first, while its source exists — refused with `failed mapping stale` and nothing written when the copy is
     * no longer where the mapping says, or its calendar is no longer allowed (T16-12).
     */
    @Synchronized
    fun delete(context: Context, path: Path, eventId: Long, both: Boolean): WriteResult<Unit> {
        val store = CalendarSyncStore.get(context)
        val mapping = store.current.mappings[eventId]
        if (both && mapping != null) {
            if (path != Path.EDITOR) return WriteResult.Refused(Refusal.NOT_ALLOWED)
            (CalendarWrites.syncDeleteCheck(context, eventId) as? Verdict.Refused)?.let {
                Diagnostics.add("calendar", "sync event=$eventId -> calendar ${mapping.target.id}: ${outcomeOf(it.why).text}")
                return WriteResult.Refused(it.why)
            }
            for (e in CalendarReads.exceptions(context, mapping.copyEventId)) {
                val gone = CalendarWrites.syncDeleteRow(context, eventId, e.id)
                if (gone !is WriteResult.Ok) return gone
            }
            val gone = CalendarWrites.syncDeleteRow(context, eventId, mapping.copyEventId)
            if (gone !is WriteResult.Ok) return gone
        }
        // A series goes with its exception rows (the write layer removes them with it).
        val deleted = CalendarWrites.deleteEvent(context, path, eventId)
        if (deleted !is WriteResult.Ok) return deleted
        if (mapping != null) store.update { SyncStateRules.unmap(it, eventId) }
        return deleted
    }
}

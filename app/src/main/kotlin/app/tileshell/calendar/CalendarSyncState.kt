package app.tileshell.calendar

/**
 * A calendar as the shell's own store names it: `_ID` + account name + account type + the calendar's own
 * `Calendars.NAME` (Decisions T16-1; fix round F16). An account that is removed and added again gets new ids, so
 * nothing the store held about it applies to the calendar that comes back.
 *
 * The [name] is part of the key because the provider hands a deleted calendar's `_ID` out again: when one account's
 * calendars are dropped and re-made, another calendar of the SAME account can come back under a ticked calendar's id,
 * account name and type — and if no prune ran in between (the shell was not running, or READ_CALENDAR was revoked),
 * the tick would be read as that calendar's. With the name in the key it is not: a key equals a calendar only when
 * all four parts match.
 *
 * [name] is null only in a mapping read from a file written before the key carried it. Such a key equals no calendar
 * on the phone (a calendar read from the provider always has a name, empty when the provider holds none), so nothing
 * is pushed to it and "Delete here and from" is not offered — while its copy stays hidden behind the local event.
 */
data class CalendarKey(val id: Long, val accountName: String, val accountType: String, val name: String?)

/** One synced event: the local (Tessera) event id → the calendar it was copied to and the copy's event id. No hash (r3 V13). */
data class SyncMapping(val localEventId: Long, val target: CalendarKey, val copyEventId: Long)

/**
 * Everything `calendar_sync.json` holds (build task 3):
 *  - [allowed]: the "Can sync to" list — the only gate to an account calendar; empty until the user ticks one;
 *  - [mappings]: the synced events, by local event id;
 *  - [hidden]: the ≡ pane's un-ticked calendars — the shell's own state, never `Calendars.VISIBLE` (r3 D4);
 *  - [firstDayOfWeek]: 1 (Monday) … 7 (Sunday), or null to follow the locale;
 *  - [notifiedAlerts]: the alerts the reminder receiver has already handled (r3 D6), each by its [AlertRow.key];
 *  - [remindersSince]: the wall-clock time of the shell's first start on this install (Q-16-4) — the receiver reminds
 *    only for alerts that come due at or after it. Null until the first start writes it.
 */
data class SyncState(
    val allowed: List<CalendarKey> = emptyList(),
    val mappings: Map<Long, SyncMapping> = emptyMap(),
    val hidden: List<CalendarKey> = emptyList(),
    val firstDayOfWeek: Int? = null,
    val notifiedAlerts: Set<String> = emptySet(),
    val remindersSince: Long? = null,
)

/** The store's rules, pure over their inputs so the JVM tests pin them. */
object SyncStateRules {
    /**
     * [present] is every calendar the provider lists now (from a query that succeeded). A calendar that is no longer
     * there leaves [SyncState.allowed] and [SyncState.hidden]: re-added, it starts NOT allowed (T16-1) and shown. The
     * mappings are kept — a mapping whose target is gone is what makes Sync say "That calendar is no longer on this
     * phone" and the marker carry its warning.
     */
    fun prune(state: SyncState, present: Set<CalendarKey>): SyncState = state.copy(
        allowed = state.allowed.filter { it in present },
        hidden = state.hidden.filter { it in present },
    )

    fun setAllowed(state: SyncState, key: CalendarKey, allowed: Boolean): SyncState =
        state.copy(allowed = if (allowed) (state.allowed - key) + key else state.allowed - key)

    fun setHidden(state: SyncState, key: CalendarKey, hidden: Boolean): SyncState =
        state.copy(hidden = if (hidden) (state.hidden - key) + key else state.hidden - key)

    fun map(state: SyncState, mapping: SyncMapping): SyncState = state.copy(mappings = state.mappings + (mapping.localEventId to mapping))

    fun unmap(state: SyncState, localEventId: Long): SyncState = state.copy(mappings = state.mappings - localEventId)

    /** A mapping whose local event is gone is dropped at the next read (build task 3; the "Delete here" of Q-16-2). */
    fun dropGoneOriginals(state: SyncState, existingLocalEvents: Set<Long>): SyncState =
        state.copy(mappings = state.mappings.filterKeys { it in existingLocalEvents })

    /** The copy event ids the store maps. Call after [dropGoneOriginals], so each copy's local original still exists. */
    fun copyIds(state: SyncState): Set<Long> = state.mappings.values.mapTo(HashSet()) { it.copyEventId }

    /**
     * Q-16-2's set: the mapped copies plus their exception events ([exceptionsOf]: an event id → the ids of the events
     * whose `ORIGINAL_ID` it is). Every reader of the provider in the shell drops an instance whose event id is in it.
     */
    fun hiddenEventIds(state: SyncState, exceptionsOf: Map<Long, Collection<Long>>): Set<Long> {
        val copies = copyIds(state)
        return copies + copies.flatMap { exceptionsOf[it].orEmpty() }
    }

    /** The mapping whose copy (or whose copy's master) is [eventId], if any. */
    fun mappingOfCopy(state: SyncState, eventId: Long): SyncMapping? = state.mappings.values.firstOrNull { it.copyEventId == eventId }

    /**
     * Q-16-4 (the owner, 2026-10-01: "a"): the time reminders count from. Written ONCE — at the shell's first start on
     * this install, or at the first start of the first build that carries the field; an update keeps it, and a
     * `pm clear` or a fresh install starts it again (the file is gone). A stored time later than [nowMs] means the
     * clock was set back: it is lowered to now, so a clock change can never silence reminders for good.
     */
    fun remindersSince(state: SyncState, nowMs: Long): SyncState {
        val stored = state.remindersSince
        return if (stored == null || stored > nowMs) state.copy(remindersSince = nowMs) else state
    }

    /**
     * The clock was changed (`TIME_SET`): a cut-off that is now in the future is lowered to [nowMs] at once, before any
     * poke — left to the first poke, the lowering would make that poke's own alert (due a moment before "now") one of
     * the rows "due before the first start". It only ever lowers: a store with no cut-off yet is left for the shell's
     * start to fill, and a clock set forward changes nothing.
     */
    fun lowerRemindersSince(state: SyncState, nowMs: Long): SyncState {
        val stored = state.remindersSince
        return if (stored != null && stored > nowMs) state.copy(remindersSince = nowMs) else state
    }

    /**
     * The receiver's notified set stays as large as the alerts that can still come back: a key is kept only while its
     * alert row is still a candidate ([live]: the keys of the rows due now in state SCHEDULED or FIRED).
     */
    fun keepNotified(state: SyncState, live: Set<String>, added: Set<String>): SyncState =
        state.copy(notifiedAlerts = (state.notifiedAlerts intersect live) + added)
}

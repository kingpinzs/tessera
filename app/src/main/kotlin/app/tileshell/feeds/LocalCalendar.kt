package app.tileshell.feeds

import app.tileshell.calendar.CalendarAccess
import app.tileshell.calendar.CalendarReads
import app.tileshell.calendar.CalendarWrites
import app.tileshell.calendar.rowIdOf
import app.tileshell.diag.Diagnostics

/**
 * The shell's own LOCAL calendar in Android's CalendarProvider — the only calendar the shell ever writes to.
 *
 * Jeremy, 2026-09-23 (phase 16 interview Q2): "I dont want to add anything to my work calander from my phone
 * ever. jsut my personal calander". Tess's "add ... to my calendar" used to pick the phone's PRIMARY writable
 * calendar, which on a phone signed into Google is an account calendar — possibly the work one (J6). Every
 * event the shell creates now goes here instead; a local calendar never syncs, and other calendar apps on the
 * phone still show it. Phase 16's Calendar app writes to this same calendar and adds the tapped, allow-listed
 * Sync that is the only road from it to an account calendar.
 *
 * Phase 16 (r3 D7): there is never a second Tessera calendar. A lookup that FAILED (READ_CALENDAR not held, the
 * provider off) is not a lookup that found nothing: [id] creates the calendar only after a query that succeeded and
 * came back empty. With READ revoked and WRITE held, the old `find() ?: create()` inserted another Tessera on every call.
 */
object LocalCalendar {

    /** The account the calendar lives under; a LOCAL account needs no Android account to exist. */
    const val ACCOUNT_NAME = "Tessera"

    /** #0063B1 (H4 judges the name and the colour). */
    const val COLOR = 0xFF0063B1.toInt()

    /** What a lookup of the calendar found. */
    sealed interface Lookup {
        data class Found(val id: Long) : Lookup
        /** The query succeeded and the calendar is not there. */
        data object Absent : Lookup
        /** The query did not answer; nothing is known and nothing may be created. */
        data class Failed(val error: String) : Lookup
    }

    /**
     * The calendar's id, creating it the first time. Null when the lookup failed (logged) or the provider refuses the
     * create (logged): the caller then has no calendar to write to, and never falls back to another one (J6's rule).
     */
    fun id(access: CalendarAccess): Long? = when (val found = find(access)) {
        is Lookup.Found -> found.id
        Lookup.Absent -> create(access)
        is Lookup.Failed -> null
    }

    /** The calendar's id when it exists; never creates. Null when it is absent or the lookup failed (logged). */
    fun existingId(access: CalendarAccess): Long? = (find(access) as? Lookup.Found)?.id

    /** Finds the calendar by account type LOCAL + account name `Tessera` — never "any LOCAL calendar" (T16-2 line 2). */
    fun find(access: CalendarAccess): Lookup = CalendarReads.findByAccount(access, ACCOUNT_NAME).fold(
        onSuccess = { id -> if (id != null) Lookup.Found(id) else Lookup.Absent },
        onFailure = {
            Diagnostics.add("calendar", "local calendar lookup failed: $it")
            Lookup.Failed(it.toString())
        },
    )

    private fun create(access: CalendarAccess): Long? = runCatching {
        // A calendar can only be created by a sync adapter, which for a LOCAL account is the app itself. The insert is
        // the calendar write layer's, behind the write guard, as every CalendarContract write of the shell is.
        val created = CalendarWrites.createLocalCalendar(access, ACCOUNT_NAME, COLOR)
        Diagnostics.add("calendar", "local calendar created: $created")
        rowIdOf(created)
    }.onFailure { Diagnostics.add("calendar", "local calendar could not be created: $it") }.getOrNull()
}

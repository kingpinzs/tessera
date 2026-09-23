package app.tileshell.feeds

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
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
 */
object LocalCalendar {

    /** The account the calendar lives under; a LOCAL account needs no Android account to exist. */
    const val ACCOUNT_NAME = "Tessera"

    /** Returns the calendar's id, creating it the first time. Null only when the provider refuses. */
    fun id(context: Context): Long? = find(context) ?: create(context)

    private fun find(context: Context): Long? = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT_NAME),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.onFailure { Diagnostics.add("calendar", "local calendar lookup failed: $it") }.getOrNull()

    private fun create(context: Context): Long? = runCatching {
        // A calendar can only be created by a sync adapter, which for a LOCAL account is the app itself.
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0063B1.toInt())
        }
        val created = context.contentResolver.insert(uri, values)
        Diagnostics.add("calendar", "local calendar created: $created")
        created?.lastPathSegment?.toLongOrNull()
    }.onFailure { Diagnostics.add("calendar", "local calendar could not be created: $it") }.getOrNull()
}

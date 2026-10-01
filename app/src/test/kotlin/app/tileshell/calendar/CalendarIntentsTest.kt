package app.tileshell.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 16 build task 2: what the Calendar app's exported handlers may do with an intent (Decisions "Trust" (c)). */
class CalendarIntentsTest {
    private class Bag(val values: Map<String, Any?>) : CalendarIntents.Extras {
        override fun string(key: String) = values[key] as? String
        override fun long(key: String) = values[key] as? Long
        override fun boolean(key: String) = values[key] as? Boolean
    }

    private fun route(action: String?, data: String? = null, type: String? = null, vararg extras: Pair<String, Any?>) =
        CalendarIntents.route(action, data, type, Bag(extras.toMap()))

    @Test fun theLauncherEntryOpensToday() {
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_MAIN))
        assertEquals(CalendarRoute.Open(null), route(null))
    }

    @Test fun aShortcutsPageExtraPicksThePage() {
        assertEquals(CalendarRoute.Open(CalendarShortcut.AGENDA), route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to "agenda")))
        assertEquals(CalendarRoute.Open(CalendarShortcut.DAY), route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to "day")))
        assertEquals(CalendarRoute.Open(CalendarShortcut.MONTH), route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to "month")))
        assertEquals(CalendarRoute.Open(CalendarShortcut.NEW_EVENT), route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to "new_event")))
        // A page nobody declared, or one of the wrong type, is today.
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to "editor")))
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to 7L)))
    }

    @Test fun viewOnATimeUriOpensThatDay() {
        assertEquals(CalendarRoute.Time(1_790_000_000_000L), route(CalendarIntents.ACTION_VIEW, "content://com.android.calendar/time/1790000000000"))
    }

    @Test fun viewOnAnEventOpensItsPageWithTheOccurrenceWhenGiven() {
        assertEquals(CalendarRoute.Event(42, null, null), route(CalendarIntents.ACTION_VIEW, "content://com.android.calendar/events/42"))
        assertEquals(
            CalendarRoute.Event(42, 1_790_000_000_000L, 1_790_003_600_000L),
            route(CalendarIntents.ACTION_VIEW, "content://com.android.calendar/events/42", null, "beginTime" to 1_790_000_000_000L, "endTime" to 1_790_003_600_000L),
        )
    }

    @Test fun editNamesTheEventAndNothingElse() {
        // Which calendar the event is in — and so whether the editor may open — is the app's question, not the intent's.
        assertEquals(CalendarRoute.Edit(42), route(CalendarIntents.ACTION_EDIT, "content://com.android.calendar/events/42", null, "title" to "Hijack", "calendar_id" to 9L))
    }

    @Test fun insertPrefillsTheEditor() {
        val r = route(
            CalendarIntents.ACTION_INSERT, null, CalendarIntents.TYPE_EVENT_DIR,
            "title" to "Lunch", "eventLocation" to "Cafe", "description" to "bring the notes",
            "beginTime" to 1_790_000_000_000L, "endTime" to 1_790_003_600_000L, "allDay" to false,
        )
        assertEquals(CalendarRoute.Insert(EventPrefill("Lunch", "Cafe", "bring the notes", 1_790_000_000_000L, 1_790_003_600_000L, false)), r)
        // The same by the events URI.
        assertEquals(CalendarRoute.Insert(EventPrefill(title = "Lunch")), route(CalendarIntents.ACTION_INSERT, "content://com.android.calendar/events", null, "title" to "Lunch"))
    }

    @Test fun insertNeverCarriesACalendar() {
        // E22: `--el calendar_id <work id> --es title Intruder`. The prefill has no calendar field to carry it.
        val r = route(CalendarIntents.ACTION_INSERT, null, CalendarIntents.TYPE_EVENT_DIR, "calendar_id" to 9L, "title" to "Intruder")
        assertEquals(CalendarRoute.Insert(EventPrefill(title = "Intruder")), r)
    }

    @Test fun insertBoundsWhatACallerSuggests() {
        val r = route(
            CalendarIntents.ACTION_INSERT, null, CalendarIntents.TYPE_EVENT_DIR,
            "title" to "x".repeat(10_000), "eventLocation" to "   ", "description" to "n".repeat(10_000),
            "beginTime" to 1_790_000_000_000L, "endTime" to 1_780_000_000_000L,
        ) as CalendarRoute.Insert
        assertEquals(CalendarIntents.MAX_TITLE, r.prefill.title!!.length)
        assertEquals(null, r.prefill.location)
        assertEquals(CalendarIntents.MAX_NOTES, r.prefill.notes!!.length)
        assertEquals(1_790_000_000_000L, r.prefill.beginMs)
        // An end before its start is dropped.
        assertEquals(null, r.prefill.endMs)
        // A time out of range, or of the wrong type, is dropped.
        assertEquals(EventPrefill(), (route(CalendarIntents.ACTION_INSERT, null, CalendarIntents.TYPE_EVENT_DIR, "beginTime" to Long.MAX_VALUE, "endTime" to -5L) as CalendarRoute.Insert).prefill)
        assertEquals(EventPrefill(), (route(CalendarIntents.ACTION_INSERT, null, CalendarIntents.TYPE_EVENT_DIR, "beginTime" to "noon", "title" to 12L) as CalendarRoute.Insert).prefill)
    }

    @Test fun insertOnAnythingButEventsOpensToday() {
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_INSERT, "content://com.android.calendar/calendars", null, "title" to "x"))
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_INSERT, null, "vnd.android.cursor.dir/contact", "title" to "x"))
    }

    @Test fun aMalformedOrForeignUriOpensToday() {
        for (data in listOf(
            "content://com.android.calendar/events/abc", "content://com.android.calendar/events/-3", "content://com.android.calendar/events/0",
            "content://com.android.calendar/time/", "content://com.android.calendar/time/yesterday", "content://com.android.calendar/time/-1",
            "content://com.android.calendar/events/1/extra", "content://com.android.calendar/", "content://com.android.contacts/events/42",
            "content://com.android.calendar.evil/events/42", "file:///sdcard/events/42", "http://com.android.calendar/events/42", "", "::::",
        )) {
            assertEquals(data, CalendarRoute.Open(null), route(CalendarIntents.ACTION_VIEW, data))
            assertEquals(data, CalendarRoute.Open(null), route(CalendarIntents.ACTION_EDIT, data))
        }
        // EDIT on a time URI, or with no data at all, is today too.
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_EDIT, "content://com.android.calendar/time/1790000000000"))
        assertEquals(CalendarRoute.Open(null), route(CalendarIntents.ACTION_EDIT))
    }

    @Test fun aQueryOrFragmentOnTheUriIsNotPartOfTheId() {
        assertEquals(CalendarRoute.Event(42, null, null), route(CalendarIntents.ACTION_VIEW, "content://com.android.calendar/events/42?caller_is_syncadapter=true"))
    }

    @Test fun anUnknownActionOpensToday() {
        assertEquals(CalendarRoute.Open(null), route("android.intent.action.DELETE", "content://com.android.calendar/events/42"))
    }

    // ---------------------------------------------------------------- fix round F4: what an intent may write to the ring

    @Test fun onlyAHandledActionIsLoggedByName() {
        for (action in listOf(CalendarIntents.ACTION_MAIN, CalendarIntents.ACTION_VIEW, CalendarIntents.ACTION_EDIT, CalendarIntents.ACTION_INSERT)) {
            assertEquals(action, CalendarIntents.loggedAction(action))
        }
        assertEquals("no action", CalendarIntents.loggedAction(null))
        // Any other string — another real action, a forged ring line, megabytes of text — is the one word "other".
        for (action in listOf("android.intent.action.DELETE", "android.intent.action.PICK", "", " ", "x\n[calendar] write delete event=7: ok", "A".repeat(1_000_000), "android.intent.action.VIEW ", "ANDROID.INTENT.ACTION.VIEW")) {
            assertEquals("other", CalendarIntents.loggedAction(action))
        }
    }

    @Test fun theRingLineOfAnIntentHoldsNothingTheCallerTyped() {
        val forged = "x\n2026-10-01 wall=1 [calendar] write delete event=7: ok"
        val typed = route(
            forged, null, CalendarIntents.TYPE_EVENT_DIR,
            "title" to "SECRET TITLE", "eventLocation" to "SECRET PLACE", "description" to "SECRET NOTE", "page" to "SECRET PAGE",
        )
        assertEquals("open other -> open page=default", CalendarIntents.openLine(forged, typed))
        val insert = route(
            CalendarIntents.ACTION_INSERT, null, CalendarIntents.TYPE_EVENT_DIR,
            "title" to "SECRET TITLE", "eventLocation" to "SECRET PLACE", "description" to "SECRET NOTE", "beginTime" to 1_790_000_000_000L,
        )
        assertEquals(CalendarRoute.Insert(EventPrefill("SECRET TITLE", "SECRET PLACE", "SECRET NOTE", 1_790_000_000_000L)), insert)
        assertEquals("open android.intent.action.INSERT -> insert (prefilled, unsaved)", CalendarIntents.openLine(CalendarIntents.ACTION_INSERT, insert))
        // Every route's line: the shell's own words and parsed numbers, one line, no caller text.
        val lines = listOf(
            CalendarIntents.openLine(CalendarIntents.ACTION_MAIN, route(CalendarIntents.ACTION_MAIN)) to "open android.intent.action.MAIN -> open page=default",
            CalendarIntents.openLine(CalendarIntents.ACTION_VIEW, route(CalendarIntents.ACTION_VIEW, extras = arrayOf("page" to "month"))) to "open android.intent.action.VIEW -> open page=month",
            CalendarIntents.openLine(CalendarIntents.ACTION_VIEW, route(CalendarIntents.ACTION_VIEW, "content://com.android.calendar/time/1790000000000")) to "open android.intent.action.VIEW -> time 1790000000000",
            CalendarIntents.openLine(CalendarIntents.ACTION_VIEW, route(CalendarIntents.ACTION_VIEW, "content://com.android.calendar/events/42?x=SECRET#SECRET")) to "open android.intent.action.VIEW -> event 42",
            CalendarIntents.openLine(CalendarIntents.ACTION_EDIT, route(CalendarIntents.ACTION_EDIT, "content://com.android.calendar/events/42")) to "open android.intent.action.EDIT -> edit 42",
            CalendarIntents.openLine(null, CalendarRoute.Open(null)) to "open no action -> open page=default",
        )
        for ((line, expected) in lines) {
            assertEquals(expected, line)
            assertEquals(false, line.contains("SECRET") || line.contains('\n'))
        }
    }
}

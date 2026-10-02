package app.tileshell.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * The Birthdays calendar (interview Q3; Decisions "Birthdays", T16-2 line 2, r3 D14): a read-only (access 200) LOCAL
 * calendar "Birthdays" under its OWN account name `Tessera Birthdays`, kept in step with the contacts' birthday
 * fields, so the Calendar tile, the Agenda pod and Tess's "what's on my calendar" see birthdays with the Calendar app
 * never opened. It is started from `ShellApp.startFeeds` beside `CalendarFeed`, with its Contacts observer.
 *
 * This is the one writer of that calendar (the write guard's case 2): every write goes through [CalendarWrites], as
 * the calendar's own sync adapter. The calendar is created when the first birthday exists and is kept, empty, when
 * the last one goes; it is never deleted by the shell. Its events carry no reminder rows (T16-4).
 */
object BirthdaysWriter {
    const val DISPLAY_NAME = "Birthdays"

    /** #8764B8, the palette's Iris Pastel: apart from Tessera's blue (approximation, H5). */
    const val COLOR = 0xFF8764B8.toInt()

    /** Contacts changes arrive in bursts (an import, a sync): one write pass after they settle. */
    private const val SETTLE_MS = 400L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pass = Mutex()
    private var observer: ContentObserver? = null
    private var pending: Job? = null

    private fun canReadContacts(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (canReadContacts(app) && observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = request(app, "contacts change")
            }.also { app.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, it) }
        }
        request(app, "start")
    }

    @Synchronized
    private fun request(app: Context, reason: String) {
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_MS)
            pass.withLock { runCatching { sync(app) }.onFailure { Diagnostics.add("calendar", "birthdays: not synced ($reason): $it") } }
        }
    }

    private fun sync(context: Context) {
        if (!canReadContacts(context)) {
            Diagnostics.add("calendar", "birthdays: not synced (READ_CONTACTS)")
            return
        }
        if (!CalendarReads.canRead(context) || !CalendarReads.canWrite(context)) {
            Diagnostics.add("calendar", "birthdays: not synced (${if (!CalendarReads.canRead(context)) "READ_CALENDAR" else "WRITE_CALENDAR"})")
            return
        }
        val access = CalendarAccess.of(context)
        val wanted = birthdays(context, LocalDate.now())
        val found = CalendarReads.findByAccount(access, CalendarWriteGuard.BIRTHDAYS_ACCOUNT)
        val calendarId = found.getOrElse {
            // A lookup that did not answer is not an empty one: nothing is created after it (the same rule as Tessera's,
            // r3 D7 — a second Birthdays calendar is no better than a second Tessera).
            if (wanted.isNotEmpty()) Diagnostics.add("calendar", "birthdays calendar could not be created: $it")
            return
        } ?: run {
            // Created when the first birthday exists: never an empty calendar at first start.
            if (wanted.isEmpty()) {
                Diagnostics.add("calendar", "birthdays: 0 synced")
                return
            }
            runCatching { CalendarWrites.createBirthdaysCalendar(access, DISPLAY_NAME, COLOR) }.getOrElse {
                Diagnostics.add("calendar", "birthdays calendar could not be created: $it")
                return
            }
        }
        val (delete, insert) = BirthdayRules.diff(existing(context, calendarId), wanted)
        delete.forEach { CalendarWrites.birthdayDelete(access, it) }
        insert.forEach { entry ->
            CalendarWrites.birthdayInsert(
                access, calendarId,
                EventValues(
                    title = entry.title, location = "", description = "", dtstart = entry.startMs, dtend = null, duration = "P1D",
                    allDay = true, timezone = "UTC", rrule = entry.rrule, availability = CalendarContract.Events.AVAILABILITY_FREE,
                ),
            )
        }
        Diagnostics.add("calendar", "birthdays: ${wanted.size} synced")
    }

    /** One entry per contact that has a birthday in one of the two forms; a value in neither form is skipped and not counted. */
    private fun birthdays(context: Context, today: LocalDate): List<BirthdayRules.Entry> {
        val seen = HashSet<Long>()
        val out = mutableListOf<BirthdayRules.Entry>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
            "${ContactsContract.Data.CONTACT_ID} ASC, ${ContactsContract.Data._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val contact = c.getLong(0)
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val birthday = BirthdayRules.parse(c.getString(2), today) ?: continue
                // Two raw contacts the provider merged are one person with one birthday.
                if (!seen.add(contact)) continue
                out += BirthdayRules.Entry(BirthdayRules.title(name), EventRules.allDayStartMs(birthday.start), birthday.rrule)
            }
        }
        return out
    }

    private fun existing(context: Context, calendarId: Long): List<Pair<Long, BirthdayRules.Entry>> =
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.RRULE),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} != 1", arrayOf(calendarId.toString()), null,
        )?.use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to BirthdayRules.Entry(c.getString(1).orEmpty(), c.getLong(2), c.getString(3).orEmpty())) }
        }.orEmpty()
}

package app.tileshell.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.LocalCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** What the views show: the instances the provider gave for one window, with the synced copies dropped (Q-16-2). */
data class LoadedWindow(
    val token: Int,
    val view: ViewMode,
    val from: LocalDate,
    /** Exclusive. */
    val to: LocalDate,
    val instances: List<EventInstance>,
    /** Uptime when the query began: the `view` line's `in <ms> ms` runs from here to the first frame that shows it. */
    val startedUptimeMs: Long,
    val zone: ZoneId,
)

/**
 * The Calendar app's data (build task 3's reads, held for build task 4's pages): the calendar list, the permission
 * state, and the instances of the window the current view asked for. A `ContentObserver` on the provider reloads
 * them, so the views follow the provider with no restart (E4's 2000 ms).
 */
class CalendarModel(context: Context) {
    private val app = context.applicationContext
    private val access = CalendarAccess.of(app)
    val store = access.store
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var canRead by mutableStateOf(CalendarReads.canRead(app))
        private set
    var canWrite by mutableStateOf(CalendarReads.canWrite(app))
        private set

    /** Every calendar the provider holds. */
    var calendars by mutableStateOf<List<CalendarInfo>>(emptyList())
        private set

    /** Why there is no calendar list although READ_CALENDAR is held: the provider is off. Null while it answers. */
    var providerOff by mutableStateOf<String?>(null)
        private set

    var loaded by mutableStateOf<LoadedWindow?>(null)
        private set

    /** Bumped on every provider change, so a page that reads an event itself (the event page) reads it again. */
    var changes by mutableStateOf(0)
        private set

    private var wanted: Triple<ViewMode, LocalDate, LocalDate>? = null
    private var token = 0
    private var loading: Job? = null
    private var observer: ContentObserver? = null
    private var started = false

    val tesseraId: Long? get() = calendars.firstOrNull { it.isTessera }?.id

    fun calendar(id: Long): CalendarInfo? = calendars.firstOrNull { it.id == id }

    /** The activity is on screen: read the calendars (creating Tessera when it may), follow the provider. */
    fun start() {
        started = true
        checkPermissions("start", force = true)
        observe()
    }

    fun stop() {
        started = false
        observer?.let { app.contentResolver.unregisterContentObserver(it) }
        observer = null
    }

    private fun observe() {
        if (!canRead || observer != null || !started) return
        val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = changed()
        }
        // With the calendar provider turned off there is nothing to observe, and Android refuses the registration.
        observer = runCatching { app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, watcher); watcher }
            .onFailure { Diagnostics.add("calendar", "the provider cannot be observed: $it") }.getOrNull()
    }

    private var settle: Job? = null

    /** A provider change: the calendar list and the window are read again, once the burst of notifications settles. */
    private fun changed() {
        settle?.cancel()
        settle = scope.launch {
            delay(CHANGE_SETTLE_MS)
            val result = withContext(Dispatchers.IO) { CalendarReads.calendars(access) }
            apply(result)
            changes++
            reload()
        }
    }

    /**
     * A permission granted or revoked while the app is open (a grant in place, `pm grant`): the state is re-read and,
     * when it changed, the calendars are loaded as at a start.
     */
    fun checkPermissions(reason: String, force: Boolean = false) {
        val read = CalendarReads.canRead(app)
        val write = CalendarReads.canWrite(app)
        if (!force && read == canRead && write == canWrite) return
        canRead = read
        canWrite = write
        loadCalendars(reason)
        observe()
        reload()
    }

    /**
     * The calendar list, and the Tessera calendar: `LocalCalendar.id` at start, so the calendar exists whatever else the
     * provider holds (T16-2 line 4) — but not while READ_CALENDAR is denied, and only created after a lookup that
     * succeeded and found none (r3 D7). One line says how it went:
     * `[calendar] calendars: n (local created id=<id> | local present | local missing: WRITE_CALENDAR)`,
     * `[calendar] calendars: none (<why>)` when the provider does not answer, or
     * `[calendar] calendars: denied (READ_CALENDAR)`.
     */
    fun loadCalendars(reason: String) {
        if (!canRead) {
            calendars = emptyList()
            providerOff = null
            Diagnostics.add("calendar", "calendars: denied (READ_CALENDAR)")
            return
        }
        val mayCreate = canWrite
        scope.launch {
            val (line, result) = withContext(Dispatchers.IO) { readCalendars(mayCreate) }
            apply(result)
            Diagnostics.add("calendar", line)
            changes++
        }
    }

    /**
     * The Tessera calendar's id for a save, finding it or — when it is absent and WRITE_CALENDAR is held — creating it,
     * with the same `calendars:` line a start writes. Null while READ_CALENDAR is denied (no lookup is made then), when
     * the provider does not answer, or when the calendar cannot be created. Called off the main thread.
     */
    fun ensureLocal(): Long? {
        if (!CalendarReads.canRead(app)) return null
        val (line, result) = readCalendars(CalendarReads.canWrite(app))
        Diagnostics.add("calendar", line)
        scope.launch { apply(result); changes++ }
        return (result as? CalendarsResult.Ok)?.calendars?.firstOrNull { it.isTessera }?.id
    }

    private fun readCalendars(mayCreate: Boolean): Pair<String, CalendarsResult> {
        val local = when (val found = LocalCalendar.find(access)) {
            is LocalCalendar.Lookup.Found -> "local present"
            LocalCalendar.Lookup.Absent ->
                if (!mayCreate) "local missing: WRITE_CALENDAR"
                else LocalCalendar.id(access)?.let { "local created id=$it" } ?: "local missing: it could not be created"
            // The provider does not answer (it is off): nothing is created, and the page says so.
            is LocalCalendar.Lookup.Failed -> return "calendars: none (${found.error})" to CalendarsResult.Failed(found.error)
        }
        return when (val result = CalendarReads.calendars(access)) {
            is CalendarsResult.Ok -> "calendars: ${result.calendars.size} ($local)" to result
            is CalendarsResult.Failed -> "calendars: none (${result.error})" to result
        }
    }

    private fun apply(result: CalendarsResult) {
        when (result) {
            is CalendarsResult.Ok -> {
                calendars = result.calendars
                providerOff = null
                // A calendar that no longer lists leaves the allowed and hidden lists: re-added, it starts not allowed.
                val present = result.calendars.mapTo(HashSet()) { it.key }
                store.update { SyncStateRules.prune(it, present) }
            }
            is CalendarsResult.Failed -> {
                calendars = emptyList()
                providerOff = result.error
            }
        }
    }

    /** The view asks for its window: [from, to) in whole days. Loaded again only when the window changed. */
    fun want(view: ViewMode, from: LocalDate, to: LocalDate) {
        val next = Triple(view, from, to)
        if (next == wanted && loaded != null) return
        wanted = next
        reload()
    }

    fun reload() {
        val (view, from, to) = wanted ?: return
        if (!canRead) {
            loaded = null
            return
        }
        loading?.cancel()
        val mine = ++token
        loading = scope.launch {
            val started = SystemClock.uptimeMillis()
            val zone = ZoneId.systemDefault()
            val rows = withContext(Dispatchers.IO) {
                val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
                val toMs = to.atStartOfDay(zone).toInstant().toEpochMilli()
                val all = CalendarReads.instances(access, fromMs, toMs) ?: return@withContext null
                // Q-16-2: a synced copy is hidden while its Tessera original exists; the count is the count after the drop.
                val copies = SyncedCopies.hiddenEventIds(access)
                if (copies.isEmpty()) all else all.filter { it.eventId !in copies }
            }
            if (mine != token) return@launch
            loaded = LoadedWindow(mine, view, from, to, rows.orEmpty(), started, zone)
        }
    }

    /** `[calendar] counts: <account name>/<displayName>=<n>, …` — every calendar's event count, each time the pane opens (r3 V3). */
    fun logCounts() {
        val list = calendars
        scope.launch {
            val counts = withContext(Dispatchers.IO) { CalendarReads.eventCounts(access) }
            Diagnostics.add("calendar", "counts: " + list.joinToString(", ") { "${it.accountName}/${it.displayName}=${counts[it.id] ?: 0}" })
        }
    }

    companion object {
        /** A sync writes rows in bursts; one reload after they settle. Well inside E4's 2000 ms. */
        const val CHANGE_SETTLE_MS = 150L
    }
}

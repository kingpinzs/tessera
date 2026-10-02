package app.tileshell.feeds

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import app.tileshell.calendar.CalendarAccess
import app.tileshell.calendar.InstanceWindow
import app.tileshell.calendar.SyncAllowList
import app.tileshell.calendar.SyncedCopies
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import app.tileshell.tiles.engine.TileRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Calendar tile (R3 C3): the day face (day name + day number) and, when there are upcoming events in the
 * next 24 hours, event faces (time + title lines). Reads CalendarProvider instances; refreshes on provider
 * changes and at each minute boundary so the day rolls over.
 */
object CalendarFeed {
    enum class AgendaDay { TODAY, TOMORROW }

    /** One row of phase 14's Agenda pod. [beginMs] is the instance's BEGIN (UTC midnight for an all-day one). */
    data class AgendaItem(val title: String, val beginMs: Long, val allDay: Boolean, val day: AgendaDay)

    /** The pod's cap: rows across today and tomorrow together. */
    const val AGENDA_CAP = 6

    private val agendaState = MutableStateFlow<List<AgendaItem>>(emptyList())

    /**
     * Phase 14 (r3 D5): today's events from now, then tomorrow's, all-day first then by BEGIN within a day, capped at
     * [AGENDA_CAP] across both days — filled by [refresh] (the same observer and minute tick as the tile), from a second
     * Instances query beside the tile's own. The tile's 24-hour / 4-face query is unchanged.
     */
    val agenda: StateFlow<List<AgendaItem>> = agendaState.asStateFlow()

    /** The shell's Calendar app: its pinned app tile reads the face under this activity's component key (r3 D12). */
    private const val CALENDAR_ACTIVITY = "app.tileshell.calendar.CalendarActivity"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var ticking = false

    fun hasAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        val app = context.applicationContext
        if (hasAccess(app) && observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(app, "provider change")
            }.also { app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, it) }
        }
        if (!ticking) {
            ticking = true
            scope.launch {
                while (true) {
                    delay(60_000 - System.currentTimeMillis() % 60_000)
                    refresh(app, "minute")
                }
            }
        }
        refresh(app, "start")
    }

    fun refresh(context: Context, reason: String) {
        scope.launch {
            val now = Date()
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(now)
            val dayNumber = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
            val faces = mutableListOf<TileFace>(TileFace.CalendarDay(dayName, dayNumber, emptyList()))
            // Phase 16 (Q-16-2): a synced copy is hidden everywhere in the shell while its Tessera original exists, so the
            // tile and the agenda (phase 14's pod) show a synced event once. One reader gives the ids for both queries.
            // Phase 16 (T16-1): a calendar that left the phone leaves Sync's allowed list here, whether or not the Calendar
            // app is open — this observer is the one that is always running.
            if (hasAccess(context)) SyncAllowList.followProvider(CalendarAccess.of(context))
            val copies = if (hasAccess(context)) SyncedCopies.hiddenEventIds(CalendarAccess.of(context)) else emptySet()
            if (hasAccess(context)) {
                runCatching {
                    // The next 24 hours: timed events by the clock, all-day ones (a birthday is one) by their date —
                    // today's, whatever the zone (InstanceWindow).
                    val start = System.currentTimeMillis()
                    val day = 24L * 60 * 60 * 1000
                    val zone = java.time.ZoneId.systemDefault()
                    val (rangeFrom, rangeTo) = InstanceWindow.queryRange(start, day, zone)
                    val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    ContentUris.appendId(builder, rangeFrom)
                    ContentUris.appendId(builder, rangeTo)
                    context.contentResolver.query(
                        builder.build(),
                        arrayOf(
                            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY,
                            CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.END,
                        ),
                        null, null, "${CalendarContract.Instances.BEGIN} ASC",
                    )?.use { c ->
                        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
                        while (c.moveToNext() && faces.size < 4) {
                            if (c.getLong(4) in copies) continue
                            val title = c.getString(0) ?: continue
                            val allDay = c.getInt(2) == 1
                            if (!InstanceWindow.shows(c.getLong(1), c.getLong(5), allDay, start, day, zone)) continue
                            val whenText = if (allDay) "All day" else timeFmt.format(Date(c.getLong(1)))
                            val location = c.getString(3).orEmpty()
                            faces += TileFace.CalendarDay(dayName, dayNumber, listOf(title, whenText, location).filter { it.isNotBlank() })
                        }
                    }
                }.onFailure { Diagnostics.add("calendar", "query failed: $it") }
            }
            agendaState.value = if (hasAccess(context)) queryAgenda(context, copies) else emptyList()
            val content = TileContent(faces, FaceTransition.FLIP, System.currentTimeMillis(), "calendar")
            LiveTileEngine.publish(LiveTileEngine.CALENDAR, content)
            // Phase 16 (r3 D12): one publisher, two keys. The CALENDAR slot tile reads the slot key; a Calendar app tile
            // pinned from the app list reads its component key, which nothing else publishes — so it is live too.
            LiveTileEngine.publish(TileRouting.componentKey(context.packageName, CALENDAR_ACTIVITY), content)
            Diagnostics.add("calendar", "refresh ($reason): access=${hasAccess(context)} faces=${faces.size} agenda=${agendaState.value.size}")
        }
    }

    /**
     * The Agenda pod's query. The range starts at the local start of TODAY, not at now: the provider matches an all-day
     * instance by its UTC [begin, end), so in a zone behind UTC today's all-day event has already "ended" by the
     * evening and a range from now drops it (phase 14 build probe, qa/phase-14/BUILD-NOTES/cal_probe.out). A timed
     * instance is kept while its END is at or after now — exactly what a range from now returns.
     */
    private fun queryAgenda(context: Context, copies: Set<Long>): List<AgendaItem> = runCatching {
        val now = System.currentTimeMillis()
        val (startOfToday, endOfTomorrow) = AgendaRules.window(now, java.util.TimeZone.getDefault())
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startOfToday)
        ContentUris.appendId(builder, endOfTomorrow)
        val rows = mutableListOf<AgendaRules.Instance>()
        context.contentResolver.query(
            builder.build(),
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_ID),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(4) in copies) continue
                val title = c.getString(0) ?: continue
                rows += AgendaRules.Instance(title, c.getLong(1), c.getLong(2), c.getInt(3) == 1)
            }
        }
        AgendaRules.select(rows, now, java.util.TimeZone.getDefault())
    }.getOrElse {
        Diagnostics.add("calendar", "agenda query failed: $it")
        emptyList()
    }
}

/** The Agenda pod's rules, pure over their inputs so the JVM tests pin them (phase 14 r3 D5). */
object AgendaRules {
    data class Instance(val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean)

    /** [local start of today, local end of tomorrow) in [zone]. */
    fun window(nowMs: Long, zone: java.util.TimeZone): Pair<Long, Long> {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 2)
        return start to c.timeInMillis
    }

    /**
     * Which day an instance belongs to, or null when it is neither today nor tomorrow: an all-day instance's day is its
     * BEGIN read in UTC (CalendarContract's convention), a timed one's its BEGIN in [zone] — a timed one that began
     * before today and is still running counts as today.
     */
    fun dayOf(instance: Instance, nowMs: Long, zone: java.util.TimeZone): CalendarFeed.AgendaDay? {
        val today = ymd(nowMs, zone)
        val tomorrow = ymd(Calendar.getInstance(zone).apply { timeInMillis = nowMs; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis, zone)
        val day = if (instance.allDay) ymd(instance.beginMs, java.util.TimeZone.getTimeZone("UTC")) else ymd(instance.beginMs, zone)
        return when {
            day == today -> CalendarFeed.AgendaDay.TODAY
            day == tomorrow -> CalendarFeed.AgendaDay.TOMORROW
            !instance.allDay && day < today -> CalendarFeed.AgendaDay.TODAY
            else -> null
        }
    }

    /** Today's from now (all-day ones whole-day), then tomorrow's; all-day first then BEGIN; capped across both days. */
    fun select(instances: List<Instance>, nowMs: Long, zone: java.util.TimeZone, cap: Int = CalendarFeed.AGENDA_CAP): List<CalendarFeed.AgendaItem> =
        instances
            .filter { it.allDay || it.endMs >= nowMs }
            .mapNotNull { i -> dayOf(i, nowMs, zone)?.let { CalendarFeed.AgendaItem(i.title, i.beginMs, i.allDay, it) } }
            .sortedWith(compareBy({ it.day }, { !it.allDay }, { it.beginMs }, { it.title }))
            .take(cap)

    private fun ymd(ms: Long, zone: java.util.TimeZone): Int {
        val c = Calendar.getInstance(zone).apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
}

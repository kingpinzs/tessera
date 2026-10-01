package app.tileshell.calendar

/** The Calendar app's App Shortcut pages (build task 9; the `page` extra, the key SettingsActivity.EXTRA_PAGE uses). */
enum class CalendarShortcut(val id: String) {
    AGENDA("agenda"),
    DAY("day"),
    /** W10M had no Month page (r11/calendar.md K5): this opens Agenda with the month drop-down open (T16-13). */
    MONTH("month"),
    NEW_EVENT("new_event");

    companion object {
        fun byId(id: String?): CalendarShortcut? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What an `ACTION_INSERT` hands the editor (the Trust line, r3 D10 (c)): the fields a caller may suggest and nothing
 * else. There is no calendar here on purpose — a `calendar_id` extra is never read, so a prefilled event can only go
 * where every event made in the app goes, the shell's own Tessera calendar — and nothing is saved until Save is tapped.
 */
data class EventPrefill(
    val title: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val beginMs: Long? = null,
    val endMs: Long? = null,
    val allDay: Boolean = false,
)

/** What an intent asks the Calendar app to show. */
sealed interface CalendarRoute {
    /** Today — the launcher entry, the Calendar tile's tap, the Agenda pod's header — or a shortcut's page. */
    data class Open(val page: CalendarShortcut?) : CalendarRoute

    /** `ACTION_VIEW` on `content://com.android.calendar/time/<ms>`: the day holding that instant. */
    data class Time(val millis: Long) : CalendarRoute

    /** `ACTION_VIEW` on an event: its page. [beginMs] / [endMs] name the occurrence when the caller gave one. */
    data class Event(val id: Long, val beginMs: Long?, val endMs: Long?) : CalendarRoute

    /**
     * `ACTION_EDIT` on an event. Whether the editor opens is not decided here: the app opens it only for an event in
     * the Tessera calendar and shows any other event's page read-only (rule 1; E22).
     */
    data class Edit(val id: Long) : CalendarRoute

    /** `ACTION_INSERT`: the editor, filled in, unsaved. */
    data class Insert(val prefill: EventPrefill) : CalendarRoute
}

/**
 * The exported handlers' rules (build task 2; Decisions "Trust" (c)), free of Android types so they are unit-tested.
 * Every caller-supplied value is bounded here; anything this does not recognise opens today, never a crash.
 */
object CalendarIntents {
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_EDIT = "android.intent.action.EDIT"
    const val ACTION_INSERT = "android.intent.action.INSERT"

    const val AUTHORITY = "com.android.calendar"
    const val TYPE_EVENT_DIR = "vnd.android.cursor.dir/event"

    /** CalendarContract's extra and column names, as their constants spell them. */
    const val EXTRA_BEGIN = "beginTime"
    const val EXTRA_END = "endTime"
    const val EXTRA_ALL_DAY = "allDay"
    const val EXTRA_TITLE = "title"
    const val EXTRA_LOCATION = "eventLocation"
    const val EXTRA_DESCRIPTION = "description"
    const val EXTRA_PAGE = "page"

    const val MAX_TITLE = 500
    const val MAX_LOCATION = 500
    const val MAX_NOTES = 5000

    /** 1970-01-01 .. 2200-01-01: a time outside it is dropped rather than opened or prefilled. */
    private const val MAX_MS = 7_258_118_400_000L

    /** An intent's extras, read by type; a key of another type reads null. */
    interface Extras {
        fun string(key: String): String?
        fun long(key: String): Long?
        fun boolean(key: String): Boolean?
    }

    fun route(action: String?, data: String?, type: String?, extras: Extras): CalendarRoute {
        val path = pathOf(data)
        return when (action) {
            ACTION_VIEW -> when {
                path == null -> open(extras)
                path.size == 2 && path[0] == "time" -> time(path[1].toLongOrNull())?.let { CalendarRoute.Time(it) } ?: open(extras)
                path.size == 2 && path[0] == "events" -> path[1].toLongOrNull()?.takeIf { it > 0 }
                    ?.let { CalendarRoute.Event(it, time(extras.long(EXTRA_BEGIN)), time(extras.long(EXTRA_END))) } ?: open(extras)
                else -> open(extras)
            }
            ACTION_EDIT -> path?.takeIf { it.size == 2 && it[0] == "events" }?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
                ?.let { CalendarRoute.Edit(it) } ?: open(extras)
            ACTION_INSERT ->
                if ((path != null && path == listOf("events")) || (data == null && type == TYPE_EVENT_DIR)) CalendarRoute.Insert(prefill(extras))
                else open(extras)
            else -> open(extras)
        }
    }

    /** The actions the activity handles; only these are ever written to the diagnostics ring by name. */
    private val HANDLED_ACTIONS = setOf(ACTION_MAIN, ACTION_VIEW, ACTION_EDIT, ACTION_INSERT)

    /**
     * An intent's action as the diagnostics ring may hold it (fix round F4, trust review B-F3): an explicit intent to
     * an exported activity can carry ANY action string — a forged ring line with a newline in it, or megabytes of text
     * held in the launcher's memory. A handled action is logged by its own constant, none as "no action", and anything
     * else as the one word "other".
     */
    fun loggedAction(action: String?): String = when (action) {
        null -> "no action"
        in HANDLED_ACTIONS -> action
        else -> "other"
    }

    /**
     * The route without what the caller typed into it: a page id from the shell's own list, or a number that was
     * parsed and bounded. A title, a location, a note or a URI never reaches the diagnostics ring.
     */
    fun describe(route: CalendarRoute): String = when (route) {
        is CalendarRoute.Open -> "open page=${route.page?.id ?: "default"}"
        is CalendarRoute.Time -> "time ${route.millis}"
        is CalendarRoute.Event -> "event ${route.id}"
        is CalendarRoute.Edit -> "edit ${route.id}"
        is CalendarRoute.Insert -> "insert (prefilled, unsaved)"
    }

    /** The one ring line an intent writes: `open <action> -> <route>`. */
    fun openLine(action: String?, route: CalendarRoute): String = "open ${loggedAction(action)} -> ${describe(route)}"

    private fun open(extras: Extras) = CalendarRoute.Open(CalendarShortcut.byId(extras.string(EXTRA_PAGE)))

    private fun prefill(extras: Extras): EventPrefill {
        val begin = time(extras.long(EXTRA_BEGIN))
        // An end before its start is not a suggestion the editor can show; the editor's own default length applies.
        val end = time(extras.long(EXTRA_END))?.takeIf { begin != null && it >= begin }
        return EventPrefill(
            title = text(extras.string(EXTRA_TITLE), MAX_TITLE),
            location = text(extras.string(EXTRA_LOCATION), MAX_LOCATION),
            notes = text(extras.string(EXTRA_DESCRIPTION), MAX_NOTES),
            beginMs = begin,
            endMs = end,
            allDay = extras.boolean(EXTRA_ALL_DAY) == true,
        )
    }

    private fun text(value: String?, max: Int): String? = value?.trim()?.take(max)?.takeIf { it.isNotEmpty() }

    private fun time(ms: Long?): Long? = ms?.takeIf { it in 0..MAX_MS }

    /** The path segments of a `content://com.android.calendar/…` URI; null for any other URI, or none. */
    private fun pathOf(data: String?): List<String>? {
        val prefix = "content://$AUTHORITY/"
        if (data == null || !data.startsWith(prefix)) return null
        val path = data.substring(prefix.length).substringBefore('?').substringBefore('#')
        return path.split('/').filter { it.isNotEmpty() }
    }
}

package app.tileshell.start.podbay

import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderGroup
import app.tileshell.cortana.ui.RemindersLayout
import app.tileshell.feeds.CalendarFeed
import app.tileshell.weather.WeatherFeed
import app.tileshell.weather.WeatherFormat
import app.tileshell.weather.WeatherReport
import app.tileshell.weather.WmoCodes

/** One drawn row of a pod: its text, an optional subtle second line, and whether a subheader goes above it. */
data class PodRow(val text: String, val subline: String? = null, val subheader: String? = null)

/**
 * The pods' content rules (phase 14 Decisions "The pods"), pure over their inputs so the JVM tests pin them. The pod bay
 * draws what these return; the reasons are the `[podbay] pod <id>: empty: <reason>` words.
 */
object PodBayRules {
    const val REMINDER_CAP = 6
    const val COMING_UP_CAP = 3

    const val AGENDA_EMPTY = "Nothing on your calendar today"
    const val AGENDA_NO_ACCESS = "Calendar access is off — turn it on in Setup"
    const val WEATHER_NO_LOCATION = "Location is off — turn it on in Setup"
    const val WEATHER_NO_REPORT = "No weather yet"
    const val NOTHING_PLAYING = "Nothing playing"
    const val REMINDERS_EMPTY = "No reminders — ask Tess to remind you"

    /** A pod's content: rows, or the one subtle line that says why there are none. */
    sealed interface Content {
        data class Rows(val rows: List<PodRow>) : Content
        data class Empty(val line: String, val reason: String) : Content
    }

    /** Agenda: "h:mm  title" / "All day  title", with a "Tomorrow" subheader above tomorrow's first row. */
    fun agenda(hasAccess: Boolean, items: List<CalendarFeed.AgendaItem>, time: (Long) -> String): Content {
        if (!hasAccess) return Content.Empty(AGENDA_NO_ACCESS, "no calendar access")
        if (items.isEmpty()) return Content.Empty(AGENDA_EMPTY, "none")
        var tomorrowSeen = false
        return Content.Rows(items.map { item ->
            val sub = if (item.day == CalendarFeed.AgendaDay.TOMORROW && !tomorrowSeen) {
                tomorrowSeen = true
                "Tomorrow"
            } else null
            PodRow("${if (item.allDay) "All day" else time(item.beginMs)}  ${item.title}", subheader = sub)
        })
    }

    /**
     * Weather (r3 D6): no permission or location off wins over any cached report; else the report's place, the
     * temperature with its condition, the day's "H <high> L <low>" with the precipitation, and the X22 line while the
     * feed's own stale flag is set.
     */
    fun weather(state: WeatherFeed.State, nowMs: Long, clock: (Long) -> String): Content {
        val problems = state.problems
        if (WeatherFeed.Problem.NO_PERMISSION in problems || WeatherFeed.Problem.LOCATION_OFF in problems) {
            return Content.Empty(WEATHER_NO_LOCATION, "no location")
        }
        val report = state.report ?: return Content.Empty(WEATHER_NO_REPORT, "no report")
        return Content.Rows(weatherRows(report, state.stale, nowMs, clock))
    }

    fun weatherRows(report: WeatherReport, stale: Boolean, nowMs: Long, clock: (Long) -> String): List<PodRow> = buildList {
        report.place?.takeIf { it.isNotBlank() }?.let { add(PodRow(it)) }
        val c = report.current
        add(PodRow("${WeatherFormat.degrees(c.temperature)}  ${WmoCodes.word(c.code, c.isDay)}"))
        report.upcomingDays(nowMs).firstOrNull()?.let { today ->
            val hl = "H ${WeatherFormat.degrees(today.high)} L ${WeatherFormat.degrees(today.low)}"
            add(PodRow(today.precipPct?.let { "$hl  $it%" } ?: hl))
        }
        if (stale) add(PodRow("Updated ${clock(report.fetchedAtMs)}"))
    }

    /** Reminders: today's by time, then the next [COMING_UP_CAP] coming up, then whenever; [REMINDER_CAP] rows. */
    fun reminders(active: List<Reminder>, nowMs: Long, endOfTodayMs: Long, subline: (Reminder) -> String?): Content {
        val groups = RemindersLayout.groupReminders(active, nowMs, endOfTodayMs)
        val picked = groups.flatMap { (group, items) -> if (group == ReminderGroup.COMING_UP) items.take(COMING_UP_CAP) else items }
            .take(REMINDER_CAP)
        if (picked.isEmpty()) return Content.Empty(REMINDERS_EMPTY, "none")
        return Content.Rows(picked.map { PodRow(it.text, subline(it)) })
    }
}

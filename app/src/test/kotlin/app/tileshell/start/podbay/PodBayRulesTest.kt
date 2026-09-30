package app.tileshell.start.podbay

import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderKind
import app.tileshell.feeds.AgendaRules
import app.tileshell.feeds.CalendarFeed
import app.tileshell.feeds.CalendarFeed.AgendaDay.TODAY
import app.tileshell.feeds.CalendarFeed.AgendaDay.TOMORROW
import app.tileshell.start.StartPages
import app.tileshell.weather.CurrentConditions
import app.tileshell.weather.DayPoint
import app.tileshell.weather.WeatherFeed
import app.tileshell.weather.WeatherReport
import app.tileshell.weather.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Phase 14's pod content rules and the Agenda feed's selection (Decisions "The pods", r3 D5 / D6), pinned. */
class PodBayRulesTest {
    private val boise = TimeZone.getTimeZone("America/Boise")
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(zone: TimeZone, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    // ---------------- Agenda (CalendarFeed.agenda) ----------------

    @Test
    fun `the window runs from local start of today to local end of tomorrow`() {
        val now = at(boise, 2026, 9, 29, 22, 15)
        val (start, end) = AgendaRules.window(now, boise)
        assertEquals(at(boise, 2026, 9, 29, 0), start)
        assertEquals(at(boise, 2026, 10, 1, 0), end)
    }

    @Test
    fun `today's all-day event still shows in the evening behind UTC - the build probe's case`() {
        // 22:15 MDT on the 29th is 04:15 UTC on the 30th: today's all-day event has "ended" in UTC. The probe
        // (qa/phase-14/BUILD-NOTES/cal_probe.out) showed a range from now drops it; the selection keeps it by its UTC day.
        val now = at(boise, 2026, 9, 29, 22, 15)
        val today = AgendaRules.Instance("QA all day", at(utc, 2026, 9, 29, 0), at(utc, 2026, 9, 30, 0), allDay = true)
        val tomorrow = AgendaRules.Instance("QA all day tomorrow", at(utc, 2026, 9, 30, 0), at(utc, 2026, 10, 1, 0), allDay = true)
        val picked = AgendaRules.select(listOf(today, tomorrow), now, boise)
        assertEquals(listOf("QA all day" to TODAY, "QA all day tomorrow" to TOMORROW), picked.map { it.title to it.day })
    }

    @Test
    fun `timed events are from now - ongoing kept, finished dropped - all-day first, then by begin, today before tomorrow`() {
        val now = at(boise, 2026, 9, 29, 14, 0)
        val rows = listOf(
            AgendaRules.Instance("finished", at(boise, 2026, 9, 29, 9), at(boise, 2026, 9, 29, 10), false),
            AgendaRules.Instance("ongoing", at(boise, 2026, 9, 29, 13), at(boise, 2026, 9, 29, 15), false),
            AgendaRules.Instance("later", at(boise, 2026, 9, 29, 18), at(boise, 2026, 9, 29, 19), false),
            AgendaRules.Instance("tomorrow 9:30", at(boise, 2026, 9, 30, 9, 30), at(boise, 2026, 9, 30, 10), false),
            AgendaRules.Instance("all day", at(utc, 2026, 9, 29, 0), at(utc, 2026, 9, 30, 0), true),
            AgendaRules.Instance("day after", at(boise, 2026, 10, 1, 9), at(boise, 2026, 10, 1, 10), false),
        )
        val picked = AgendaRules.select(rows, now, boise)
        assertEquals(listOf("all day", "ongoing", "later", "tomorrow 9:30"), picked.map { it.title })
        assertEquals(listOf(TODAY, TODAY, TODAY, TOMORROW), picked.map { it.day })
    }

    @Test
    fun `twenty events cap at six across both days`() {
        val now = at(boise, 2026, 9, 29, 8, 0)
        val rows = (0 until 20).map { i ->
            val begin = now + (i + 1) * 30 * 60_000L
            AgendaRules.Instance("e$i", begin, begin + 15 * 60_000L, false)
        }
        assertEquals(6, AgendaRules.select(rows, now, boise).size)
        assertEquals(6, CalendarFeed.AGENDA_CAP)
    }

    @Test
    fun `agenda rows - all day, h mm, and the Tomorrow subheader once`() {
        val items = listOf(
            CalendarFeed.AgendaItem("QA all day", 0L, true, TODAY),
            CalendarFeed.AgendaItem("QA overlap one", 1L, false, TODAY),
            CalendarFeed.AgendaItem("QA tomorrow", 2L, false, TOMORROW),
            CalendarFeed.AgendaItem("QA tomorrow two", 3L, false, TOMORROW),
        )
        val content = PodBayRules.agenda(true, items) { "t$it" } as PodBayRules.Content.Rows
        assertEquals(listOf("All day  QA all day", "t1  QA overlap one", "t2  QA tomorrow", "t3  QA tomorrow two"), content.rows.map { it.text })
        assertEquals(listOf(null, null, "Tomorrow", null), content.rows.map { it.subheader })
    }

    @Test
    fun `agenda empty and denied lines`() {
        assertEquals(
            PodBayRules.Content.Empty("Calendar access is off — turn it on in Setup", "no calendar access"),
            PodBayRules.agenda(false, emptyList()) { "" },
        )
        assertEquals(PodBayRules.Content.Empty("Nothing on your calendar today", "none"), PodBayRules.agenda(true, emptyList()) { "" })
    }

    // ---------------- Weather ----------------

    private val fixtureNow = at(utc, 2026, 9, 29, 18)

    /** weather_fixture.py 3 day's values (qa/phase-01/scripts/weather_fixture.py): Denver, 64.0, code 3, 72.0 / 51.0, 30 %. */
    private val report = WeatherReport(
        provider = "open-meteo", latitude = 39.74, longitude = -104.99, place = "Denver", timeZoneId = "America/Denver",
        units = WeatherUnits.IMPERIAL, fetchedAtMs = fixtureNow - 10 * 60_000L,
        current = CurrentConditions(fixtureNow, 64.0, 63.0, 40, 1013.0, 5.0, 180, 3, true),
        hourly = emptyList(),
        daily = listOf(DayPoint(at(TimeZone.getTimeZone("America/Denver"), 2026, 9, 29, 0), 3, 72.0, 51.0, 30, null, null)),
    )

    @Test
    fun `weather rows in the fixture's order, and the X22 line only while the feed says stale`() {
        val fresh = PodBayRules.weather(WeatherFeed.State(report = report), fixtureNow) { "h:mm" } as PodBayRules.Content.Rows
        assertEquals(listOf("Denver", "64°  Cloudy", "H 72° L 51°  30%"), fresh.rows.map { it.text })
        val stale = PodBayRules.weather(WeatherFeed.State(report = report, stale = true), fixtureNow) { "9:08" } as PodBayRules.Content.Rows
        assertEquals("Updated 9:08", stale.rows.last().text)
    }

    @Test
    fun `no permission or location off wins over a cached report`() {
        listOf(WeatherFeed.Problem.NO_PERMISSION, WeatherFeed.Problem.LOCATION_OFF).forEach { p ->
            assertEquals(
                PodBayRules.Content.Empty("Location is off — turn it on in Setup", "no location"),
                PodBayRules.weather(WeatherFeed.State(report = report, problems = listOf(p)), fixtureNow) { "" },
            )
        }
        // A network problem is not a location problem: the cached report still shows.
        val offline = PodBayRules.weather(WeatherFeed.State(report = report, problems = listOf(WeatherFeed.Problem.NO_NETWORK)), fixtureNow) { "" }
        assertEquals("Denver", (offline as PodBayRules.Content.Rows).rows.first().text)
        assertEquals(PodBayRules.Content.Empty("No weather yet", "no report"), PodBayRules.weather(WeatherFeed.State(), fixtureNow) { "" })
    }

    // ---------------- Reminders ----------------

    @Test
    fun `reminders - today's by time, the next three coming up, then whenever, six rows`() {
        val now = at(boise, 2026, 9, 29, 12)
        val endOfToday = at(boise, 2026, 9, 29, 23, 59)
        fun r(id: String, time: Long?, created: Long = 0) = Reminder(id, id, ReminderKind.TIME, timeMs = time, createdMs = created)
        val list = listOf(
            r("whenever", null, 1),
            r("today late", at(boise, 2026, 9, 29, 20)),
            r("today early", at(boise, 2026, 9, 29, 15)),
            r("up1", at(boise, 2026, 9, 30, 9)),
            r("up2", at(boise, 2026, 10, 1, 9)),
            r("up3", at(boise, 2026, 10, 2, 9)),
            r("up4", at(boise, 2026, 10, 3, 9)),
            Reminder("done", "done", completed = true),
        )
        val content = PodBayRules.reminders(list.filterNot { it.completed }, now, endOfToday) { "sub:${it.id}" } as PodBayRules.Content.Rows
        assertEquals(listOf("today early", "today late", "up1", "up2", "up3", "whenever"), content.rows.map { it.text })
        assertEquals("sub:today early", content.rows.first().subline)
        assertEquals(PodBayRules.Content.Empty("No reminders — ask Tess to remind you", "none"), PodBayRules.reminders(emptyList(), now, endOfToday) { null })
    }

    @Test
    fun `the pager's pages and their names`() {
        assertEquals(listOf(0, 1, 2), listOf(StartPages.POD_BAY, StartPages.START, StartPages.APP_LIST))
        assertEquals(listOf("POD_BAY", "START", "APP_LIST"), (0 until StartPages.COUNT).map { StartPages.name(it) })
    }
}

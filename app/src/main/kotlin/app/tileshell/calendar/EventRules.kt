package app.tileshell.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** The editor's Repeat choices (r11/calendar.md U3: the wording is UNMEASURED — approximation, H15). */
enum class Repeat(val id: String, val label: String, val rrule: String?) {
    NONE("none", "Never", null),
    DAILY("daily", "Every day", "FREQ=DAILY"),
    WEEKLY("weekly", "Every week", "FREQ=WEEKLY"),
    MONTHLY("monthly", "Every month", "FREQ=MONTHLY"),
    YEARLY("yearly", "Every year", "FREQ=YEARLY"),
}

/** Which occurrences an edit or a delete of a repeating event applies to (Decisions "Recurrence and time zones"). */
enum class EditScope(val id: String, val label: String) {
    THIS("this", "This occurrence"),
    FOLLOWING("following", "This and following"),
    ALL("all", "All occurrences"),
}

/** The columns an event write sets, free of Android types. `dtend` and `duration` are never both set (the provider's rule). */
data class EventValues(
    val title: String,
    val location: String,
    val description: String,
    val dtstart: Long,
    val dtend: Long?,
    val duration: String?,
    val allDay: Boolean,
    val timezone: String,
    val rrule: String?,
    val rdate: String? = null,
    val exrule: String? = null,
    val exdate: String? = null,
    val availability: Int = 0,
    val status: Int? = null,
)

/**
 * What the editor holds. [startDate] … [endTime] are wall-clock values in the device zone; an all-day event uses the
 * dates only, its [endDate] the LAST day it covers. The `loaded…` values are what the event came with, so a field the
 * user did not touch is written back exactly as stored (E8: a stored end inside a repeated hour).
 */
data class EventDraft(
    val eventId: Long? = null,
    val scope: EditScope? = null,
    val instanceBeginMs: Long? = null,
    val title: String = "",
    val location: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val repeat: Repeat = Repeat.NONE,
    val reminder: Int? = null,
    val loadedStartMs: Long? = null,
    val loadedEndMs: Long? = null,
    val loadedRrule: String? = null,
    val loadedRepeat: Repeat = Repeat.NONE,
    val loadedReminder: Int? = null,
    val loadedAllDay: Boolean = false,
    /** The series' own first start, for an edit of every occurrence opened from a later one. */
    val masterStartMs: Long? = null,
    /** An exception row cannot carry a rule of its own, so its editor offers no Repeat. */
    val canRepeat: Boolean = true,
) {
    val isNew: Boolean get() = eventId == null
    val reminderChanged: Boolean get() = isNew || reminder != loadedReminder
}

object EventRules {
    const val NO_TITLE = "(No title)"
    val UTC: ZoneId = ZoneOffset.UTC

    /** The reminder choices the editor offers, in minutes before the start; null is "None". */
    val REMINDER_CHOICES: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 720, 1440, 10080)

    fun reminderLabel(minutes: Int?): String = when {
        minutes == null -> "None"
        minutes == 0 -> "At start time"
        minutes % 10080 == 0 -> plural(minutes / 10080, "week")
        minutes % 1440 == 0 -> plural(minutes / 1440, "day")
        minutes % 60 == 0 -> plural(minutes / 60, "hour")
        else -> plural(minutes, "minute")
    }

    private fun plural(n: Int, unit: String) = "$n $unit${if (n == 1) "" else "s"}"

    fun shownTitle(title: String?): String = title?.takeIf { it.isNotBlank() } ?: NO_TITLE

    /** The editor's Repeat value for a stored rule: its FREQ, whatever else the rule carries. */
    fun repeatOf(rrule: String?): Repeat {
        val freq = parts(rrule).firstOrNull { it.first == "FREQ" }?.second ?: return Repeat.NONE
        return when (freq) {
            "DAILY" -> Repeat.DAILY
            "WEEKLY" -> Repeat.WEEKLY
            "MONTHLY" -> Repeat.MONTHLY
            "YEARLY" -> Repeat.YEARLY
            else -> Repeat.NONE
        }
    }

    /** An all-day event is date-anchored: its start is that date's midnight in UTC, whatever the device zone. */
    fun allDayStartMs(date: LocalDate): Long = date.atStartOfDay(UTC).toInstant().toEpochMilli()

    fun toMs(date: LocalDate, time: LocalTime, zone: ZoneId): Long = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()

    fun local(ms: Long, zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    /** The days an instance covers, first to last: an all-day one by its UTC dates, a timed one by the device zone's. */
    fun days(beginMs: Long, endMs: Long, allDay: Boolean, zone: ZoneId): ClosedRange<LocalDate> {
        val z = if (allDay) UTC else zone
        val first = local(beginMs, z).toLocalDate()
        // The end is exclusive: an event that ends at midnight does not cover the day that starts then.
        val last = local(maxOf(beginMs, endMs - 1), z).toLocalDate()
        return first..maxOf(first, last)
    }

    sealed interface Built {
        data class Ok(val values: EventValues) : Built
        data class Invalid(val reason: String) : Built
    }

    /**
     * The columns a draft writes. A timed event carries the device zone and displays in it; an all-day event is
     * anchored in UTC. A repeating event writes `RRULE` + `DURATION` and no `DTEND`, as the provider requires. An end
     * before its start is refused.
     *
     * @param recurring whether the row written keeps a rule: false for a single-occurrence edit (an exception row)
     */
    fun build(draft: EventDraft, zone: ZoneId, recurring: Boolean = draft.repeat != Repeat.NONE): Built {
        val rrule = when {
            !recurring -> null
            // An untouched Repeat writes the stored rule back whole (its COUNT, BYDAY, UNTIL).
            draft.repeat == draft.loadedRepeat && !draft.loadedRrule.isNullOrBlank() -> draft.loadedRrule
            else -> draft.repeat.rrule
        }
        if (draft.allDay) {
            if (draft.endDate < draft.startDate) return Built.Invalid("The end is before the start.")
            val start = allDayStartMs(draft.startDate)
            val end = allDayStartMs(draft.endDate.plusDays(1))
            val days = ChronoUnit.DAYS.between(draft.startDate, draft.endDate) + 1
            return Built.Ok(
                EventValues(
                    draft.title.trim(), draft.location.trim(), draft.notes, start,
                    dtend = if (rrule == null) end else null,
                    duration = if (rrule == null) null else "P${days}D",
                    allDay = true, timezone = "UTC", rrule = rrule,
                ),
            )
        }
        val start = keepLoaded(draft.loadedStartMs, draft.startDate, draft.startTime, zone, draft.loadedAllDay)
        val end = keepLoaded(draft.loadedEndMs, draft.endDate, draft.endTime, zone, draft.loadedAllDay)
        if (end < start) return Built.Invalid("The end is before the start.")
        return Built.Ok(
            EventValues(
                draft.title.trim(), draft.location.trim(), draft.notes, start,
                dtend = if (rrule == null) end else null,
                duration = if (rrule == null) null else "P${(end - start) / 1000}S",
                allDay = false, timezone = zone.id, rrule = rrule,
            ),
        )
    }

    /** The stored instant when the field still shows it; else the wall-clock value in [zone]. */
    private fun keepLoaded(loadedMs: Long?, date: LocalDate, time: LocalTime, zone: ZoneId, loadedAllDay: Boolean): Long =
        if (loadedMs != null && !loadedAllDay && local(loadedMs, zone) == LocalDateTime.of(date, time)) loadedMs else toMs(date, time, zone)

    /** RFC 2445 durations as the provider stores them (`P3600S`, `PT1H`, `P1D`, `P1W`, `PT1H30M`), in ms; null if unreadable. */
    fun durationMs(duration: String?): Long? {
        val m = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""").matchEntire(duration?.trim().orEmpty()) ?: return null
        val (sign, w, d, h, min, s) = m.destructured
        if (listOf(w, d, h, min, s).all { it.isEmpty() }) return null
        val total = (w.toLongOrNull() ?: 0) * 604_800 + (d.toLongOrNull() ?: 0) * 86_400 + (h.toLongOrNull() ?: 0) * 3_600 +
            (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
        return (if (sign == "-") -total else total) * 1000
    }

    private fun parts(rrule: String?): List<Pair<String, String>> =
        rrule.orEmpty().removePrefix("RRULE:").split(';').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i <= 0) null else p.substring(0, i).trim().uppercase(Locale.US) to p.substring(i + 1).trim()
        }

    private fun join(parts: List<Pair<String, String>>): String = parts.joinToString(";") { "${it.first}=${it.second}" }

    /** `COUNT` of a rule, or null. */
    fun countOf(rrule: String?): Int? = parts(rrule).firstOrNull { it.first == "COUNT" }?.second?.toIntOrNull()

    /**
     * "This and following", the master's half: the series ends just before the occurrence that starts at
     * [instanceBeginMs]. `UNTIL` replaces any `COUNT` (a rule may carry one or the other). A timed rule's UNTIL is a
     * UTC date-time one second before the occurrence; an all-day rule's is the date of the day before.
     */
    fun endBefore(rrule: String, instanceBeginMs: Long, allDay: Boolean): String {
        val until = if (allDay) {
            DateTimeFormatter.BASIC_ISO_DATE.format(local(instanceBeginMs, UTC).toLocalDate().minusDays(1))
        } else {
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(local(instanceBeginMs - 1000, UTC))
        }
        return join(parts(rrule).filter { it.first != "COUNT" && it.first != "UNTIL" } + ("UNTIL" to until))
    }

    /**
     * "This and following", the new series' half: the same rule, with a `COUNT` reduced by the [occurrencesBefore] that
     * stay with the old master.
     */
    fun tailOf(rrule: String, occurrencesBefore: Int): String {
        val count = countOf(rrule) ?: return join(parts(rrule))
        return join(parts(rrule).map { if (it.first == "COUNT") "COUNT" to (count - occurrencesBefore).coerceAtLeast(1).toString() else it })
    }

    /** The first day of the week: the stored setting (1 Monday … 7 Sunday), else the locale's (r11/calendar.md K2.2). */
    fun firstDayOfWeek(setting: Int?, locale: Locale): DayOfWeek =
        setting?.takeIf { it in 1..7 }?.let { DayOfWeek.of(it) } ?: WeekFields.of(locale).firstDayOfWeek

    fun weekStart(day: LocalDate, first: DayOfWeek): LocalDate = day.minusDays(((day.dayOfWeek.value - first.value + 7) % 7).toLong())

    /** The month panel's six rows of seven dates around [month]'s first day (r11/calendar.md K5.2). */
    fun monthGrid(month: LocalDate, first: DayOfWeek): List<LocalDate> {
        val start = weekStart(month.withDayOfMonth(1), first)
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    /**
     * The label tint of a calendar colour (r11/calendar.md K3.9: red (168,0,0) → (245,72,85), blue (0,120,215) →
     * (48,163,250), orange (215,59,2) → (254,116,87)): the same hue, lightened to about 62 % lightness. An approximation
     * of three measured pairs; a colour already lighter is kept. ARGB in, ARGB out.
     */
    fun tint(argb: Int): Int {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        val target = 0.62f
        if (l >= target) return argb or (0xFF shl 24)
        // Move each channel toward white by the fraction that brings the lightness to the target.
        val f = (target - l) / (1f - l)
        fun lift(c: Float) = ((c + (1f - c) * f) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lift(r) shl 16) or (lift(g) shl 8) or lift(b)
    }
}

/** What Sync compares between the local event and its copy (r3 V13): the copied fields, the reminders, the exceptions. */
data class EventSnapshot(
    val fields: Fields,
    val reminders: Set<Pair<Int, Int>>,
    val exceptions: Set<ExceptionFields>,
) {
    data class Fields(
        val title: String,
        val location: String,
        val description: String,
        val dtstart: Long,
        val dtend: Long?,
        val duration: String?,
        val allDay: Boolean,
        val timezone: String,
        val rrule: String,
        val rdate: String,
        val exrule: String,
        val exdate: String,
        val availability: Int,
    )

    data class ExceptionFields(
        val originalInstanceTime: Long,
        val title: String,
        val location: String,
        val description: String,
        val dtstart: Long,
        val dtend: Long?,
        val allDay: Boolean,
        val cancelled: Boolean,
    )
}

object SyncRules {
    /** CalendarContract.Events.STATUS_CANCELED. */
    const val STATUS_CANCELED = 2

    fun fields(e: EventDetail) = EventSnapshot.Fields(
        title = e.title.orEmpty(),
        location = e.location.orEmpty(),
        description = e.description.orEmpty(),
        dtstart = e.dtstart,
        // A repeating row has no DTEND of its own (the provider may leave a stale one); its DURATION says the length.
        dtend = if (e.recurring) null else e.dtend,
        duration = if (e.recurring) EventRules.durationMs(e.duration)?.let { "P${it / 1000}S" } ?: e.duration.orEmpty() else null,
        allDay = e.allDay,
        timezone = e.timezone.orEmpty(),
        rrule = e.rrule.orEmpty(),
        rdate = e.rdate.orEmpty(),
        exrule = e.exrule.orEmpty(),
        exdate = e.exdate.orEmpty(),
        availability = e.availability,
    )

    /** A cancelled occurrence compares by its instance alone: what the row still says of a deleted occurrence is not shown anywhere. */
    fun exception(e: EventDetail): EventSnapshot.ExceptionFields =
        if (e.status == STATUS_CANCELED) {
            EventSnapshot.ExceptionFields(e.originalInstanceTime ?: 0L, "", "", "", 0L, null, allDay = false, cancelled = true)
        } else {
            EventSnapshot.ExceptionFields(
                originalInstanceTime = e.originalInstanceTime ?: 0L,
                title = e.title.orEmpty(),
                location = e.location.orEmpty(),
                description = e.description.orEmpty(),
                dtstart = e.dtstart,
                dtend = e.dtend,
                allDay = e.allDay,
                cancelled = false,
            )
        }

    fun snapshot(event: EventDetail, reminders: List<Pair<Int, Int>>, exceptions: List<EventDetail>) =
        EventSnapshot(fields(event), reminders.toSet(), exceptions.mapTo(HashSet()) { exception(it) })

    /** The local event is the source: any difference is written over the copy; none writes nothing. */
    fun differs(local: EventSnapshot, copy: EventSnapshot): Boolean = local != copy
}

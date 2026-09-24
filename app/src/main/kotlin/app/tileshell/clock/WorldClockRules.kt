package app.tileshell.clock

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The World Clock tab's rules (phase 15 Decisions "World clock"; r11/clock.md §5), free of Android so the host JVM
 * proves them (WorldClockRulesTest). The city list is the device's own tz database with ICU's exemplar location
 * names; the caller reads both and hands them in as [Zone]s.
 */
object WorldClockRules {
    /** A zone id and the exemplar location name ICU gives it, or null when ICU has none. */
    data class Zone(val id: String, val exemplar: String?)

    /** A zone as listed: its label (disambiguated where two zones share a name) and its region. */
    data class Entry(val id: String, val name: String, val region: String, val label: String)

    /** ICU's exemplar name, else the last segment of the id with its underscores as spaces ("Etc/GMT+5" → "GMT+5"). */
    fun name(zone: Zone): String = zone.exemplar?.trim()?.takeIf { it.isNotEmpty() } ?: zone.id.substringAfterLast('/').replace('_', ' ')

    /** The id's first segment ("America"), empty for an id with none. */
    fun region(id: String): String = if ('/' in id) id.substringBefore('/') else ""

    /**
     * Every zone as a list entry, sorted by label. Two zones with one exemplar name are shown with their region
     * ("Paris, Europe"), so the list never holds two identical labels for different zones.
     */
    fun entries(zones: List<Zone>): List<Entry> {
        val named = zones.map { it to name(it) }
        val counts = named.groupingBy { it.second }.eachCount()
        return named.map { (z, n) ->
            val region = region(z.id)
            val label = if ((counts[n] ?: 0) > 1 && region.isNotEmpty()) "$n, $region" else n
            Entry(z.id, n, region, label)
        }.sortedWith(compareBy({ it.label.lowercase(Locale.ROOT) }, { it.id }))
    }

    /** City search: a case-insensitive match on the label, names starting with the query first; nothing for an empty query. */
    fun search(entries: List<Entry>, query: String, limit: Int = 50): List<Entry> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        val starts = entries.filter { it.label.lowercase(Locale.ROOT).startsWith(q) }
        val contains = entries.filter { it !in starts && it.label.lowercase(Locale.ROOT).contains(q) }
        return (starts + contains).take(limit)
    }

    /** The zone's offset from the local zone at [nowMs], in minutes, from both zones' rules (DST-aware). */
    fun offsetMinutes(zone: ZoneId, local: ZoneId, nowMs: Long): Int {
        val at = Instant.ofEpochMilli(nowMs)
        return (zone.rules.getOffset(at).totalSeconds - local.rules.getOffset(at).totalSeconds) / 60
    }

    /**
     * The difference line in W10M's form (5.6; T15-15): "Today, 2 hours ahead" / "Today, 7 hours behind", the
     * weekday's name in place of "Today" when the city's date differs from the local one ("Friday, 2 hours ahead").
     * Half-hour zones read "5 hours 30 minutes ahead", and the same offset "same time" (approximations: no
     * source shows either).
     */
    fun difference(zone: ZoneId, local: ZoneId, nowMs: Long, locale: Locale): String =
        "${dayWord(zone, local, nowMs, locale)}, ${offsetWords(offsetMinutes(zone, local, nowMs))}"

    fun dayWord(zone: ZoneId, local: ZoneId, nowMs: Long, locale: Locale): String {
        val at = Instant.ofEpochMilli(nowMs)
        val there = at.atZone(zone).toLocalDate()
        val here = at.atZone(local).toLocalDate()
        return if (there == here) "Today" else there.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    }

    fun offsetWords(minutes: Int): String {
        if (minutes == 0) return "same time"
        val abs = kotlin.math.abs(minutes)
        val h = abs / 60
        val m = abs % 60
        val hours = when (h) { 0 -> null; 1 -> "1 hour"; else -> "$h hours" }
        val mins = when (m) { 0 -> null; 1 -> "1 minute"; else -> "$m minutes" }
        val amount = listOfNotNull(hours, mins).joinToString(" ")
        return "$amount ${if (minutes > 0) "ahead" else "behind"}"
    }

    /** The full date a compare-mode row shows (5.7 "rows show full dates"): "Friday, 26 September 2026". */
    fun fullDate(ms: Long, zone: ZoneId, locale: Locale): String =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))

    /** The local row's date line (5.3 "Donnerstag, 5. Januar 2017"). */
    fun localDate(ms: Long, zone: ZoneId, locale: Locale): String = fullDate(ms, zone, locale)

    /** Compare mode: the instant [hourOffset] whole hours from now (the strip steps by the hour, 5.7). */
    fun compareInstant(nowMs: Long, hourOffset: Int): Long = nowMs + hourOffset * 3_600_000L

    /** The strip's five hour labels around the local hour at the compare instant: "23 · 00 · 01 · 02 · 03" (5.7). */
    fun stripHours(compareMs: Long, local: ZoneId): List<String> {
        val h = Instant.ofEpochMilli(compareMs).atZone(local).hour
        return (-2..2).map { "%02d".format(((h + it) % 24 + 24) % 24) }
    }
}

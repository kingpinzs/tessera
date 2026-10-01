package app.tileshell.calendar

import java.time.LocalDate
import java.time.Year

/**
 * The Birthdays calendar's date-form rule (Decisions "Birthdays", r3 D14, with the 2026-10-01 re-cut from Verify at
 * build start 5), pure so the JVM test pins it.
 *
 * A contact's birthday is a string the Contacts provider stores as it was given. Two forms are a birthday:
 *  - `yyyy-MM-dd` → a yearly all-day event from that date;
 *  - `--MM-dd` (no year) → the same from this year's date.
 * 29 February, in either form, repeats as `FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1` — February's last day every twelve
 * months, so it shows on 29 February in a leap year and on 28 February otherwise (Android's expander gives
 * `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1` an instance in leap years only). A no-year 29 February in a year without one
 * starts on that year's 28 February. A value in neither form is skipped and not counted.
 */
object BirthdayRules {
    const val YEARLY = "FREQ=YEARLY"
    const val LEAP_DAY = "FREQ=MONTHLY;INTERVAL=12;BYMONTHDAY=-1"

    data class Birthday(val start: LocalDate, val rrule: String)

    private val FULL = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val NO_YEAR = Regex("""^--(\d{2})-(\d{2})$""")

    fun parse(value: String?, today: LocalDate): Birthday? {
        val text = value?.trim().orEmpty()
        FULL.matchEntire(text)?.let { m ->
            val (y, mo, d) = m.destructured
            val date = date(y.toInt(), mo.toInt(), d.toInt()) ?: return null
            return Birthday(date, if (mo.toInt() == 2 && d.toInt() == 29) LEAP_DAY else YEARLY)
        }
        NO_YEAR.matchEntire(text)?.let { m ->
            val (mo, d) = m.destructured
            val month = mo.toInt()
            val day = d.toInt()
            if (month == 2 && day == 29) {
                return Birthday(LocalDate.of(today.year, 2, if (Year.isLeap(today.year.toLong())) 29 else 28), LEAP_DAY)
            }
            return date(today.year, month, day)?.let { Birthday(it, YEARLY) }
        }
        return null
    }

    /** A real calendar date, or null: 2021-02-30 and 1990-13-01 are not birthdays. */
    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        if (year < 1 || month !in 1..12 || day < 1) null else runCatching { LocalDate.of(year, month, day) }.getOrNull()

    fun title(name: String): String = "$name's birthday"

    /** One birthday as the writer will hold it in the calendar. */
    data class Entry(val title: String, val startMs: Long, val rrule: String)

    /**
     * What to write so the calendar holds exactly [wanted]: the event ids to delete and the entries to insert. Two
     * contacts with one name and one birthday are two events, so both sides are counted, not just compared.
     */
    fun diff(existing: List<Pair<Long, Entry>>, wanted: List<Entry>): Pair<List<Long>, List<Entry>> {
        val need = wanted.groupingBy { it }.eachCount().toMutableMap()
        val delete = mutableListOf<Long>()
        for ((id, entry) in existing) {
            val left = need[entry] ?: 0
            if (left > 0) need[entry] = left - 1 else delete += id
        }
        val insert = need.flatMap { (entry, n) -> List(n) { entry } }
        return delete to insert
    }
}

package app.tileshell.calc.date

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A span in Windows' units. As a duration for add / subtract only [years], [months] and [days] are used. */
data class DateDifference(val years: Int = 0, val months: Int = 0, val weeks: Int = 0, val days: Int = 0)

/** Windows' `DateUnit` flags, for [DateCalculationEngine.tryGetDateDifference]'s output format. */
object DateUnit {
    const val YEAR = 0x01
    const val MONTH = 0x02
    const val WEEK = 0x04
    const val DAY = 0x08
    const val ALL = YEAR or MONTH or WEEK or DAY
}

/**
 * Port of microsoft/calculator@4fd3fc5 src/Calculator.ViewModels/Common/DateCalculator.cs (`DateCalculationEngine`)
 * for the Gregorian calendar, on calendar dates (the C# runs a UTC `Windows.Globalization.Calendar` on dates clipped
 * to midnight, so a date's time zone never enters).
 *
 * `Calendar.AddYears` / `AddMonths` keep the day of month and clamp it to the target month's last day (31 Jan +
 * 1 month = 28 or 29 Feb; 29 Feb + 1 year = 28 Feb), which is what [LocalDate.plusYears] and [LocalDate.plusMonths]
 * do. The calendar refuses dates past 31 Dec 9999 (Windows' `ArgumentException`), modelled by [OutOfRange].
 */
object DateCalculationEngine {
    /** The earliest date a subtraction may reach (`s_minSupportedDate`, the FILETIME epoch). */
    val MIN_SUPPORTED_DATE: LocalDate = LocalDate.of(1601, 1, 1)

    /** The last date the calendar can represent. */
    val MAX_SUPPORTED_DATE: LocalDate = LocalDate.of(9999, 12, 31)

    /** The date pickers' range in Windows' Date calculation page (DateCalculator.xaml.cs: c_minYear 1601, c_maxYear 2550). */
    val PICKER_MIN_DATE: LocalDate = LocalDate.of(1601, 1, 1)
    val PICKER_MAX_DATE: LocalDate = LocalDate.of(2550, 12, 31)

    private class OutOfRange : Exception()

    private fun checked(date: LocalDate): LocalDate {
        if (date.isAfter(MAX_SUPPORTED_DATE) || date.year < 1) throw OutOfRange()
        return date
    }

    private fun addYears(date: LocalDate, n: Int) = checked(date.plusYears(n.toLong()))
    private fun addMonths(date: LocalDate, n: Int) = checked(date.plusMonths(n.toLong()))
    private fun addWeeks(date: LocalDate, n: Int) = checked(date.plusWeeks(n.toLong()))
    private fun addDays(date: LocalDate, n: Int) = checked(date.plusDays(n.toLong()))

    /** `AddDuration`: years, then months, then days; null when the calendar's range is exceeded ("Date out of Bound"). */
    fun addDuration(startDate: LocalDate, duration: DateDifference): LocalDate? = try {
        var d = startDate
        if (duration.years != 0) d = addYears(d, duration.years)
        if (duration.months != 0) d = addMonths(d, duration.months)
        if (duration.days != 0) d = addDays(d, duration.days)
        d
    } catch (e: OutOfRange) {
        null
    }

    /**
     * `SubtractDuration`: "smaller units first, then larger" — days, then months, then years; null when the result
     * is before [MIN_SUPPORTED_DATE].
     */
    fun subtractDuration(startDate: LocalDate, duration: DateDifference): LocalDate? = try {
        var d = startDate
        if (duration.days != 0) d = addDays(d, -duration.days)
        if (duration.months != 0) d = addMonths(d, -duration.months)
        if (duration.years != 0) d = addYears(d, -duration.years)
        if (d.isBefore(MIN_SUPPORTED_DATE)) null else d
    } catch (e: OutOfRange) {
        null
    }

    /**
     * `TryGetDateDifference`: the span between two dates in either order, in the units named by [outputFormat]
     * ([DateUnit] flags). Each unit in turn takes the largest count that, added to the running pivot, does not pass
     * the later date (estimated from the days, then walked up or down); days take what is left.
     */
    fun tryGetDateDifference(date1: LocalDate, date2: LocalDate, outputFormat: Int): DateDifference? {
        val startDate: LocalDate
        val endDate: LocalDate
        if (date1.isBefore(date2)) {
            startDate = date1
            endDate = date2
        } else {
            startDate = date2
            endDate = date1
        }
        var pivotDate = startDate
        var daysDiff = differenceInDays(startDate, endDate)
        val differenceInDates = LongArray(UNITS_OF_DATE)

        if (outputFormat and 7 != 0) {
            val daysInMonth = startDate.lengthOfMonth().toLong()
            val approximateDaysInYear = endDate.lengthOfYear().toLong()
            val daysIn = longArrayOf(approximateDaysInYear, daysInMonth, DAYS_IN_WEEK, 1)

            for (unitIndex in 0 until UNITS_GREATER_THAN_DAYS) {
                val tempPivotDate = pivotDate
                val dateUnit = 1 shl unitIndex
                if (outputFormat and dateUnit == 0) continue

                var isEndDateHit = false
                differenceInDates[unitIndex] = daysDiff / daysIn[unitIndex]

                while (differenceInDates[unitIndex] != 0L) {
                    try {
                        pivotDate = adjustCalendarDate(tempPivotDate, dateUnit, differenceInDates[unitIndex].toInt())
                        break
                    } catch (e: OutOfRange) {
                        // The day-based estimate can overshoot the calendar's upper bound.
                        differenceInDates[unitIndex] -= 1
                    }
                }

                var tempDaysDiff: Long
                do {
                    tempDaysDiff = differenceInDays(pivotDate, endDate)
                    if (tempDaysDiff < 0) {
                        if (differenceInDates[unitIndex] == 0L) return null
                        differenceInDates[unitIndex] -= 1
                        pivotDate = adjustCalendarDate(tempPivotDate, dateUnit, differenceInDates[unitIndex].toInt())
                        isEndDateHit = true
                    } else if (tempDaysDiff > 0) {
                        if (isEndDateHit) break
                        try {
                            pivotDate = adjustCalendarDate(tempPivotDate, dateUnit, (differenceInDates[unitIndex] + 1).toInt())
                            differenceInDates[unitIndex] += 1
                        } catch (e: OutOfRange) {
                            // The current pivot is valid; finish with smaller units.
                            break
                        }
                    }
                } while (tempDaysDiff != 0L)

                val signedDaysDiff = differenceInDays(pivotDate, endDate)
                if (signedDaysDiff < 0) return null
                daysDiff = signedDaysDiff
            }
        }

        differenceInDates[3] = daysDiff
        return DateDifference(
            years = differenceInDates[0].toInt(),
            months = differenceInDates[1].toInt(),
            weeks = differenceInDates[2].toInt(),
            days = differenceInDates[3].toInt(),
        )
    }

    private fun differenceInDays(date1: LocalDate, date2: LocalDate): Long = ChronoUnit.DAYS.between(date1, date2)

    private fun adjustCalendarDate(date: LocalDate, dateUnit: Int, difference: Int): LocalDate = when (dateUnit) {
        DateUnit.YEAR -> addYears(date, difference)
        DateUnit.MONTH -> addMonths(date, difference)
        DateUnit.WEEK -> addWeeks(date, difference)
        else -> date
    }

    private const val UNITS_OF_DATE = 4
    private const val UNITS_GREATER_THAN_DAYS = 3
    private const val DAYS_IN_WEEK = 7L
}

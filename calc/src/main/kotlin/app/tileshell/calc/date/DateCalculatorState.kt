package app.tileshell.calc.date

import java.time.LocalDate

/**
 * The Date calculation page's state: a port of microsoft/calculator@4fd3fc5
 * src/Calculator.ViewModels/DateCalculatorViewModel.cs with its en-US strings (Resources.resw `Date_*`,
 * `CalculationFailed`), list separator ", " and the "longdate" format ("Wednesday, September 23, 2026").
 *
 * As in Windows, results are recomputed when an input actually changes: the page opens in "Difference between
 * dates" with both dates on [today] and reads "Same dates"; [strDateResult] stays empty until the page has been in
 * add / subtract mode.
 */
class DateCalculatorState(today: LocalDate) {

    /** "Difference between dates" (true) or "Add or subtract days" (false). */
    var isDateDiffMode: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    /** Add (true) or Subtract (false) in add / subtract mode. */
    var isAddMode: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    var fromDate: LocalDate = today
        set(value) {
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    var toDate: LocalDate = today
        set(value) {
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    /** The add / subtract mode's "From" date. */
    var startDate: LocalDate = today
        set(value) {
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    /** Years picker, 0..[MAX_OFFSET_VALUE]. */
    var yearsOffset: Int = 0
        set(value) {
            require(value in 0..MAX_OFFSET_VALUE) { "years offset $value outside 0..$MAX_OFFSET_VALUE" }
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    /** Months picker, 0..[MAX_OFFSET_VALUE]. */
    var monthsOffset: Int = 0
        set(value) {
            require(value in 0..MAX_OFFSET_VALUE) { "months offset $value outside 0..$MAX_OFFSET_VALUE" }
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    /** Days picker, 0..[MAX_OFFSET_VALUE]. */
    var daysOffset: Int = 0
        set(value) {
            require(value in 0..MAX_OFFSET_VALUE) { "days offset $value outside 0..$MAX_OFFSET_VALUE" }
            if (field != value) {
                field = value
                onInputsChanged()
            }
        }

    /** True when the difference is shown in days alone, with no second line. */
    var isDiffInDays: Boolean = false
        private set

    /** The difference's main line: "Same dates", "6 days", or "1 year, 2 months, 1 week, 3 days". */
    var strDateDiffResult: String = ""
        private set

    /** The difference's second line, the total in days ("430 days"); empty when [isDiffInDays]. */
    var strDateDiffResultInDays: String = ""
        private set

    /** The add / subtract result: the long date, or "Date out of Bound". */
    var strDateResult: String = ""
        private set

    private var isOutOfBound = false
    private var dateResult: LocalDate = today
    private var dateDiffResult = DateDifference()
    private var dateDiffResultInDays = DateDifference()

    init {
        updateDisplayResult()
    }

    private fun onInputsChanged() {
        if (isDateDiffMode) {
            val inDays = DateCalculationEngine.tryGetDateDifference(fromDate, toDate, DateUnit.DAY)
            if (inDays != null) {
                dateDiffResultInDays = inDays
                dateDiffResult = DateCalculationEngine.tryGetDateDifference(fromDate, toDate, DateUnit.ALL) ?: inDays
            } else {
                dateDiffResult = UNKNOWN
                dateDiffResultInDays = UNKNOWN
            }
        } else {
            isOutOfBound = false
            val duration = DateDifference(years = yearsOffset, months = monthsOffset, days = daysOffset)
            val result = if (isAddMode) {
                DateCalculationEngine.addDuration(startDate, duration)
            } else {
                DateCalculationEngine.subtractDuration(startDate, duration)
            }
            if (result != null) dateResult = result else isOutOfBound = true
        }
        updateDisplayResult()
    }

    private fun updateDisplayResult() {
        if (isDateDiffMode) {
            when {
                dateDiffResultInDays == UNKNOWN -> {
                    isDiffInDays = false
                    strDateDiffResultInDays = ""
                    strDateDiffResult = CALCULATION_FAILED
                }
                dateDiffResultInDays.days == 0 -> {
                    isDiffInDays = true
                    strDateDiffResultInDays = ""
                    strDateDiffResult = SAME_DATES
                }
                dateDiffResult == UNKNOWN ||
                    (dateDiffResult.years == 0 && dateDiffResult.months == 0 && dateDiffResult.weeks == 0) -> {
                    isDiffInDays = true
                    strDateDiffResultInDays = ""
                    strDateDiffResult = dateDiffStringInDays()
                }
                else -> {
                    isDiffInDays = false
                    strDateDiffResult = dateDiffString()
                    strDateDiffResultInDays = dateDiffStringInDays()
                }
            }
        } else {
            strDateResult = if (isOutOfBound) OUT_OF_BOUND else formatLongDate(dateResult)
        }
    }

    private fun dateDiffString(): String {
        val parts = ArrayList<String>()
        val d = dateDiffResult
        if (d.years > 0) parts += "${d.years} " + if (d.years == 1) "year" else "years"
        if (d.months > 0) parts += "${d.months} " + if (d.months == 1) "month" else "months"
        if (d.weeks > 0) parts += "${d.weeks} " + if (d.weeks == 1) "week" else "weeks"
        if (d.days > 0 || parts.isEmpty()) parts += "${d.days} " + if (d.days == 1) "day" else "days"
        return parts.joinToString(LIST_SEPARATOR)
    }

    private fun dateDiffStringInDays(): String {
        val days = dateDiffResultInDays.days
        return "$days " + if (days == 1) "day" else "days"
    }

    companion object {
        /** The offset pickers list 0 through 999. */
        const val MAX_OFFSET_VALUE = 999

        const val SAME_DATES = "Same dates"
        const val CALCULATION_FAILED = "Calculation failed"
        const val OUT_OF_BOUND = "Date out of Bound"
        private const val LIST_SEPARATOR = ", "

        private val UNKNOWN = DateDifference(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

        private val DAY_NAMES = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val MONTH_NAMES = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )

        /** en-US "longdate": "dddd, MMMM d, yyyy" — "Wednesday, September 23, 2026". */
        fun formatLongDate(date: LocalDate): String =
            "${DAY_NAMES[date.dayOfWeek.value - 1]}, ${MONTH_NAMES[date.monthValue - 1]} ${date.dayOfMonth}, ${date.year}"
    }
}

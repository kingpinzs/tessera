package app.tileshell.calc.date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Date calculation. Expectations come from microsoft/calculator@4fd3fc5 src/Calculator.Tests/DateCalculatorTests.cs
 * (marked "Windows test") or from a host oracle (Python `datetime`, written from the rules: each unit takes the
 * largest count that does not pass the later date, month ends clamp) — never from this code.
 */
class DateCalculationTest {

    private fun d(y: Int, m: Int, day: Int): LocalDate = LocalDate.of(y, m, day)

    private fun diff(from: LocalDate, to: LocalDate): Triple<String, String, Boolean> {
        val state = DateCalculatorState(d(2026, 9, 23))
        state.fromDate = from
        state.toDate = to
        return Triple(state.strDateDiffResult, state.strDateDiffResultInDays, state.isDiffInDays)
    }

    private fun addSubtract(add: Boolean, start: LocalDate, years: Int, months: Int, days: Int): String {
        val state = DateCalculatorState(d(2026, 9, 23))
        state.isDateDiffMode = false
        state.isAddMode = add
        state.startDate = start
        state.yearsOffset = years
        state.monthsOffset = months
        state.daysOffset = days
        return state.strDateResult
    }

    // ---- Engine: the Windows test vectors ----

    @Test fun windowsDifferenceVectors() {
        val e = DateCalculationEngine
        val all = DateUnit.YEAR or DateUnit.MONTH or DateUnit.DAY
        assertEquals(DateDifference(years = 8398, months = 11, days = 30), e.tryGetDateDifference(d(1601, 1, 1), d(9999, 12, 31), all))
        assertEquals(DateDifference(years = 8397, months = 11, days = 30), e.tryGetDateDifference(d(1601, 1, 1), d(9998, 12, 31), all))
        assertEquals(DateDifference(days = 365), e.tryGetDateDifference(d(9999, 12, 31), d(9998, 12, 31), DateUnit.DAY))
        assertEquals(DateDifference(years = 1), e.tryGetDateDifference(d(9998, 12, 31), d(9999, 12, 31), DateUnit.YEAR))
        assertEquals(DateDifference(weeks = 52, days = 1), e.tryGetDateDifference(d(9998, 12, 31), d(9999, 12, 31), DateUnit.WEEK or DateUnit.DAY))
        assertEquals(DateDifference(months = 1, days = 2), e.tryGetDateDifference(d(2008, 3, 31), d(2008, 2, 29), DateUnit.MONTH or DateUnit.DAY))
        assertEquals(DateDifference(days = 31), e.tryGetDateDifference(d(2008, 3, 31), d(2008, 2, 29), DateUnit.DAY))
        assertEquals(DateDifference(months = 11, days = 1), e.tryGetDateDifference(d(2008, 1, 29), d(2007, 2, 28), DateUnit.MONTH or DateUnit.DAY))
        assertEquals(DateDifference(years = 7991, months = 11), e.tryGetDateDifference(d(2008, 1, 31), d(9999, 12, 31), DateUnit.YEAR or DateUnit.MONTH))
        assertEquals(DateDifference(weeks = 416998, days = 1), e.tryGetDateDifference(d(2008, 1, 31), d(9999, 12, 31), DateUnit.WEEK or DateUnit.DAY))
    }

    @Test fun windowsAddSubtractVectors() {
        val e = DateCalculationEngine
        assertEquals(d(2008, 2, 29), e.addDuration(d(2008, 1, 31), DateDifference(months = 1)))
        assertEquals(d(2008, 5, 10), e.addDuration(d(2008, 3, 31), DateDifference(months = 1, days = 10)))
        assertEquals(d(2008, 3, 10), e.addDuration(d(2008, 1, 31), DateDifference(months = 1, days = 10)))
        assertEquals(d(2008, 2, 29), e.subtractDuration(d(2008, 3, 31), DateDifference(months = 1)))
        assertEquals(d(2008, 1, 29), e.subtractDuration(d(2008, 3, 10), DateDifference(months = 1, days = 10)))
        assertEquals(d(2007, 1, 28), e.subtractDuration(d(2007, 3, 10), DateDifference(months = 1, days = 10)))
        assertNull(e.addDuration(d(9999, 12, 30), DateDifference(days = 2)))
        assertNull(e.addDuration(d(9998, 12, 31), DateDifference(years = 2008)))
        assertNull(e.subtractDuration(d(1601, 1, 1), DateDifference(days = 2)))
        assertNull(e.subtractDuration(d(2008, 3, 31), DateDifference(years = 2008)))
    }

    // ---- The page's strings ----

    @Test fun opensOnSameDates() {
        val state = DateCalculatorState(d(2026, 9, 23))
        assertTrue(state.isDateDiffMode)
        assertTrue(state.isAddMode)
        assertEquals("Same dates", state.strDateDiffResult)
        assertEquals("", state.strDateDiffResultInDays)
        assertTrue(state.isDiffInDays)
        assertEquals("", state.strDateResult) // Windows test: empty until add / subtract mode is used
        state.isDateDiffMode = false
        assertEquals("Wednesday, September 23, 2026", state.strDateResult)
    }

    @Test fun differenceAcrossALeapDay() {
        assertEquals(Triple("2 days", "", true), diff(d(2024, 2, 28), d(2024, 3, 1)))
        assertEquals(Triple("1 day", "", true), diff(d(2023, 2, 28), d(2023, 3, 1)))
        assertEquals(Triple("1 year", "365 days", false), diff(d(2020, 2, 29), d(2021, 2, 28)))
        assertEquals(Triple("4 years", "1461 days", false), diff(d(2020, 2, 29), d(2024, 2, 29)))
        assertEquals(Triple("1 month, 2 days", "31 days", false), diff(d(2008, 3, 31), d(2008, 2, 29))) // Windows test
    }

    @Test fun differenceAcrossAMonthEnd() {
        assertEquals(Triple("1 month", "29 days", false), diff(d(2024, 1, 31), d(2024, 2, 29)))
        assertEquals(Triple("1 month, 1 day", "30 days", false), diff(d(2024, 1, 31), d(2024, 3, 1)))
        assertEquals(Triple("1 month", "30 days", false), diff(d(2024, 3, 31), d(2024, 4, 30)))
    }

    @Test fun differenceAcrossAYearEnd() {
        assertEquals(Triple("1 day", "", true), diff(d(2025, 12, 31), d(2026, 1, 1)))
        assertEquals(Triple("2 weeks", "14 days", false), diff(d(2025, 12, 25), d(2026, 1, 8)))
        assertEquals(Triple("1 year, 1 month, 1 week, 1 day", "404 days", false), diff(d(2025, 1, 1), d(2026, 2, 9)))
    }

    @Test fun differenceWordingAndOrder() {
        assertEquals(Triple("Same dates", "", true), diff(d(2026, 9, 23), d(2026, 9, 23)))
        assertEquals(Triple("1 week", "7 days", false), diff(d(2019, 3, 10), d(2019, 3, 17))) // Windows test
        assertEquals(Triple("1 day", "", true), diff(d(2019, 3, 10), d(2019, 3, 11))) // Windows test
        assertEquals(Triple("10 months", "305 days", false), diff(d(2008, 3, 10), d(2007, 5, 10))) // Windows test
        assertEquals(Triple("10 months", "305 days", false), diff(d(2007, 5, 10), d(2008, 3, 10)))
        // No digit grouping in day counts (Windows test "2918987 days").
        assertEquals(Triple("7991 years, 11 months", "2918987 days", false), diff(d(2008, 1, 31), d(9999, 12, 31)))
        assertEquals(Triple("949 years, 11 months, 4 weeks, 2 days", "346979 days", false),
            diff(DateCalculationEngine.PICKER_MIN_DATE, DateCalculationEngine.PICKER_MAX_DATE))
    }

    @Test fun addClampsAtMonthEndsAndLeapDays() {
        assertEquals("Thursday, February 29, 2024", addSubtract(true, d(2024, 1, 31), 0, 1, 0))
        assertEquals("Sunday, February 28, 2021", addSubtract(true, d(2020, 2, 29), 1, 0, 0))
        assertEquals("Thursday, February 29, 2024", addSubtract(true, d(2020, 2, 29), 4, 0, 0))
        assertEquals("Sunday, March 28, 2021", addSubtract(true, d(2020, 2, 29), 1, 1, 0)) // years, then months
        assertEquals("Thursday, January 1, 2026", addSubtract(true, d(2025, 12, 31), 0, 0, 1))
        assertEquals("Saturday, May 10, 2008", addSubtract(true, d(2008, 3, 31), 0, 1, 10))
        assertEquals("Tuesday, December 25, 3635", addSubtract(true, d(2550, 12, 31), 999, 999, 999))
    }

    @Test fun subtractGoesDaysFirstAndStopsAt1601() {
        assertEquals("Thursday, February 29, 2024", addSubtract(false, d(2024, 3, 31), 0, 1, 0))
        assertEquals("Thursday, February 29, 2024", addSubtract(false, d(2024, 3, 1), 0, 0, 1))
        assertEquals("Friday, February 28, 2020", addSubtract(false, d(2021, 2, 28), 1, 0, 0))
        assertEquals("Tuesday, January 29, 2008", addSubtract(false, d(2008, 3, 10), 0, 1, 10))
        assertEquals("Sunday, January 28, 2007", addSubtract(false, d(2007, 3, 10), 0, 1, 10))
        assertEquals("Wednesday, February 28, 1601", addSubtract(false, d(1601, 3, 31), 0, 1, 0))
        assertEquals("Wednesday, December 31, 2025", addSubtract(false, d(2026, 1, 1), 0, 0, 1))
        assertEquals("Date out of Bound", addSubtract(false, d(1601, 1, 1), 0, 0, 1))
    }

    @Test fun outOfBoundRecovers() {
        val state = DateCalculatorState(d(2026, 9, 23))
        state.isDateDiffMode = false
        state.isAddMode = false
        state.startDate = d(1601, 1, 1)
        state.daysOffset = 1
        assertEquals("Date out of Bound", state.strDateResult)
        state.isAddMode = true
        assertEquals("Tuesday, January 2, 1601", state.strDateResult)
    }

    @Test fun offsetsAreLimitedTo999() {
        val state = DateCalculatorState(d(2026, 9, 23))
        state.daysOffset = DateCalculatorState.MAX_OFFSET_VALUE
        try {
            state.daysOffset = 1000
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertFalse(e.message.isNullOrEmpty())
        }
    }
}

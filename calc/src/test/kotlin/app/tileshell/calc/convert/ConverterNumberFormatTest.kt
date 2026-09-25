package app.tileshell.calc.convert

import org.junit.Assert.assertEquals
import org.junit.Test

/** CalcManager's number strings (UCRT printf semantics; expectations from Python's correctly rounded formatting). */
class ConverterNumberFormatTest {

    @Test fun fixedRoundsTheExactBinaryValueHalfToEven() {
        assertEquals("0.12", ConverterNumberFormat.fixed(0.125, 2))
        assertEquals("0.38", ConverterNumberFormat.fixed(0.375, 2))
        assertEquals("2", ConverterNumberFormat.fixed(2.5, 0))
        assertEquals("4", ConverterNumberFormat.fixed(3.5, 0))
        assertEquals("0.2", ConverterNumberFormat.fixed(0.25, 1))
        assertEquals("-0.00", ConverterNumberFormat.fixed(-0.001, 2))
        assertEquals("212.0000", ConverterNumberFormat.fixed(212.0, 4))
    }

    @Test fun scientificIsPrintfPercentE() {
        assertEquals("8.000000e+24", ConverterNumberFormat.scientific(8e24))
        assertEquals("4.450490e-26", ConverterNumberFormat.scientific(4.4504904583333334e-26))
        assertEquals("1.000000e+15", ConverterNumberFormat.scientific(1.0000005e15))
        assertEquals("1.000000e+16", ConverterNumberFormat.scientific(9.9999995e15))
        assertEquals("-1.500000e-20", ConverterNumberFormat.scientific(-1.5e-20))
    }

    @Test fun trimmingAndDigitCounts() {
        assertEquals("212", ConverterNumberFormat.trimTrailingZeros("212.0000"))
        assertEquals("100", ConverterNumberFormat.trimTrailingZeros("100"))
        assertEquals("3", ConverterNumberFormat.trimTrailingZeros("3."))
        assertEquals("-0", ConverterNumberFormat.trimTrailingZeros("-0.00"))
        assertEquals(2, ConverterNumberFormat.numberDigits("0.5"))
        assertEquals(2, ConverterNumberFormat.numberDigits("-05"))
        assertEquals(2, ConverterNumberFormat.numberDigits("2.50")) // trailing zeros are trimmed first
        assertEquals(1, ConverterNumberFormat.wholeNumberDigits(0.0))
        assertEquals(1, ConverterNumberFormat.wholeNumberDigits(0.001))
        assertEquals(3, ConverterNumberFormat.wholeNumberDigits(999.0))
        assertEquals(4, ConverterNumberFormat.wholeNumberDigits(1000.0))
        assertEquals(16, ConverterNumberFormat.wholeNumberDigits(1e15))
    }

    @Test fun localizedDisplay() {
        assertEquals("523,535", ConverterNumberFormat.localize("523535", true))
        assertEquals("325,338.7", ConverterNumberFormat.localize("325338.7", true))
        assertEquals("1,000,000", ConverterNumberFormat.localize("1000000", false))
        assertEquals("100", ConverterNumberFormat.localize("100", false))
        assertEquals("3.", ConverterNumberFormat.localize("3.", true))
        assertEquals("3", ConverterNumberFormat.localize("3.", false))
        assertEquals("-0", ConverterNumberFormat.localize("-0", true))
        assertEquals("-0.", ConverterNumberFormat.localize("-0.", true))
        assertEquals("-5", ConverterNumberFormat.localize("-05", true))
        assertEquals("-1,234.50", ConverterNumberFormat.localize("-1234.50", true))
        assertEquals("8.000000e+24", ConverterNumberFormat.localize("8.000000e+24", true))
        assertEquals("1.234568e-7", ConverterNumberFormat.localize("1.234568e-07", true))
        assertEquals("0", ConverterNumberFormat.localize("", true))
    }
}

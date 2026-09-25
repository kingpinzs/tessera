package app.tileshell.calc.convert

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Port of microsoft/calculator@4fd3fc5 src/CalcManager/NumberFormattingUtils.cpp (the converter's number strings) and
 * of the en-US path of `UnitConverterViewModel.ConvertToLocalizedString` (how those strings are displayed).
 *
 * CalcManager formats with `wstringstream` (`fixed` / `scientific`), i.e. the Universal CRT's printf, which prints the
 * exact binary value of the double correctly rounded — exact ties to even since Windows 10 2004. `BigDecimal(Double)`
 * is that exact value, so HALF_EVEN on it reproduces the CRT digit for digit.
 */
internal object ConverterNumberFormat {

    /** `TrimTrailingZeros`: only when the string has a '.', strip trailing '0's, then a trailing '.'. */
    fun trimTrailingZeros(number: String): String {
        if (number.indexOf('.') < 0) return number
        var s = number
        val i = s.indexOfLast { it != '0' }
        if (i >= 0) s = s.substring(0, i + 1)
        if (s.endsWith('.')) s = s.substring(0, s.length - 1)
        return s
    }

    /** `GetNumberDigits`: characters after trimming, less one for a '.' and one for a '-'. */
    fun numberDigits(value: String): Int {
        val trimmed = trimTrailingZeros(value)
        var n = trimmed.length
        if (trimmed.indexOf('.') >= 0) n--
        if (trimmed.indexOf('-') >= 0) n--
        return n
    }

    /** `GetNumberDigitsWholeNumberPart`: `value == 0 ? 1 : (unsigned)(1 + max(0.0, log10(abs(value))))`. */
    fun wholeNumberDigits(value: Double): Int =
        if (value == 0.0) 1 else (1.0 + maxOf(0.0, StrictMath.log10(Math.abs(value)))).toInt()

    /** `RoundSignificantDigits`: printf "%.{decimals}f" (despite its name the argument is a count of decimals). */
    fun fixed(value: Double, decimals: Int): String {
        val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
        val digits = BigDecimal(Math.abs(value)).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString()
        return if (negative) "-$digits" else digits
    }

    /** `ToScientificNumber`: printf "%e" — one digit, '.', six digits, 'e', sign, at least two exponent digits. */
    fun scientific(value: Double): String {
        val negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0)
        val magnitude = Math.abs(value)
        val body: String
        if (magnitude == 0.0) {
            body = "0.000000e+00"
        } else {
            val rounded = BigDecimal(magnitude).round(MathContext(7, RoundingMode.HALF_EVEN))
            val unscaled = rounded.unscaledValue().toString()
            val digits = unscaled.padEnd(7, '0').substring(0, 7)
            val exponent = unscaled.length - 1 - rounded.scale()
            val sign = if (exponent < 0) '-' else '+'
            val exp = Math.abs(exponent).toString().padStart(2, '0')
            body = "${digits[0]}.${digits.substring(1)}e$sign$exp"
        }
        return if (negative) "-$body" else body
    }

    /**
     * `ConvertToLocalizedString` for en-US (grouping ",", decimal ".", the default currency USD whose two fraction
     * digits make `allowPartialStrings` keep a trailing '.' while a number is typed). The value is parsed as a double
     * and re-formatted with as many fraction digits as the string has; every string the converter produces has at
     * most 15 significant digits, so that round trip returns the same digits, grouped.
     */
    fun localize(value: String, allowPartialStrings: Boolean): String {
        if (value.isEmpty()) return "0"
        value.toDoubleOrNull() ?: return value
        val posOfE = value.indexOf('e')
        if (posOfE >= 0) {
            val signOfE = value[posOfE + 1]
            return localize(value.substring(0, posOfE), false) + "e" + signOfE + localize(value.substring(posOfE + 2), false)
        }
        val negative = value.startsWith('-')
        val unsigned = if (negative) value.substring(1) else value
        val posOfDecimal = unsigned.indexOf('.')
        val intPart = (if (posOfDecimal >= 0) unsigned.substring(0, posOfDecimal) else unsigned).trimStart('0').ifEmpty { "0" }
        val fraction = if (posOfDecimal >= 0) unsigned.substring(posOfDecimal + 1) else ""
        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(group(intPart))
        if (fraction.isNotEmpty()) {
            sb.append('.').append(fraction)
        } else if (posOfDecimal >= 0 && allowPartialStrings) {
            sb.append('.')
        }
        return sb.toString()
    }

    private fun group(digits: String): String {
        val sb = StringBuilder()
        val first = digits.length % 3
        if (first > 0) sb.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }
}

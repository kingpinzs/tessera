package app.tileshell.calc.engine

/** Header Files/CalcInput.h MAX_STRLEN. */
internal const val MAX_STRLEN = 84
private const val C_NUM_MAX_DIGITS = MAX_STRLEN
private const val C_EXP_MAX_DIGITS = 4

/** Port of CEngine/CalcInput.cpp CalcNumSec: one digit string (mantissa or exponent) with its sign. */
internal class CalcNumSec {
    val value = StringBuilder()
    var isNegative = false

    fun clear() {
        value.setLength(0)
        isNegative = false
    }

    fun isEmpty(): Boolean = value.isEmpty()
}

/**
 * Port of CEngine/CalcInput.cpp: number entry, including the `Exp` exponent entry (four exponent digits at most), the
 * sign toggles, the integer-mode digit limits and backspace.
 */
internal class CalcInput(private var decSymbol: Char = '.') {
    private var hasExponent = false
    private var hasDecimal = false
    private var decPtIndex = 0
    private val base = CalcNumSec()
    private val exponent = CalcNumSec()

    fun clear() {
        base.clear()
        exponent.clear()
        hasExponent = false
        hasDecimal = false
        decPtIndex = 0
    }

    fun tryToggleSign(isIntegerMode: Boolean, maxNumStr: String): Boolean {
        // Zero is always positive
        if (base.isEmpty()) {
            base.isNegative = false
            exponent.isNegative = false
        } else if (hasExponent) {
            exponent.isNegative = !exponent.isNegative
        } else {
            // In integer mode toggling can overflow: in byte -128 is valid but 128 is not.
            if (isIntegerMode && base.isNegative) {
                if (base.value.length >= maxNumStr.length && base.value[base.value.length - 1] > maxNumStr[maxNumStr.length - 1]) {
                    return false
                }
            }
            base.isNegative = !base.isNegative
        }
        return true
    }

    fun tryAddDigit(value: Int, radix: Int, isIntegerMode: Boolean, maxNumStr: String, wordBitWidth: Int, maxDigits: Int): Boolean {
        val chDigit = if (value < 10) ('0' + value) else ('A' + value - 10)
        val numSec: CalcNumSec
        var maxCount: Int
        if (hasExponent) {
            numSec = exponent
            maxCount = C_EXP_MAX_DIGITS
        } else {
            numSec = base
            maxCount = maxDigits
            // The decimal point does not count toward the precision.
            if (hasDecimalPt()) maxCount++
            // A first leading 0 is not counted either.
            if (!numSec.isEmpty() && numSec.value[0] == '0') maxCount++
        }
        // Ignore leading zeros
        if (numSec.isEmpty() && value == 0) return true

        if (numSec.value.length < maxCount) {
            numSec.value.append(chDigit)
            return true
        }

        // Integer mode: one more digit can fit on the last digit in some cases.
        if (isIntegerMode && numSec.value.length == maxCount && !hasExponent) {
            var allowExtraDigit = false
            if (radix == 8) {
                when (wordBitWidth % 3) {
                    // 16 or 64 bit: a first digit of 1 allows 6 (16 bit) or 22 (64 bit) digits
                    1 -> allowExtraDigit = numSec.value[0] == '1'
                    // 8 or 32 bit: a first digit of 3 or less allows 3 (8 bit) or 11 (32 bit) digits
                    2 -> allowExtraDigit = numSec.value[0] <= '3'
                }
            } else if (radix == 10) {
                if (numSec.value.length < maxNumStr.length) {
                    val cmpResult = numSec.value.toString().compareTo(maxNumStr.substring(0, numSec.value.length))
                    if (cmpResult < 0) {
                        allowExtraDigit = true
                    } else if (cmpResult == 0) {
                        val lastChar = maxNumStr[numSec.value.length]
                        if (chDigit <= lastChar) {
                            allowExtraDigit = true
                        } else if (numSec.isNegative && chDigit <= lastChar + 1) {
                            // Negative range is -(max+1)..max.
                            allowExtraDigit = true
                        }
                    }
                }
            }
            if (allowExtraDigit) {
                numSec.value.append(chDigit)
                return true
            }
        }
        return false
    }

    fun tryAddDecimalPt(): Boolean {
        if (hasDecimal || hasExponent) return false
        if (base.isEmpty()) base.value.append('0') // Add a leading zero
        decPtIndex = base.value.length
        base.value.append(decSymbol)
        hasDecimal = true
        return true
    }

    fun hasDecimalPt(): Boolean = hasDecimal

    fun tryBeginExponent(): Boolean {
        // For compatibility, add a trailing dec point to base num if it doesn't have one
        tryAddDecimalPt()
        if (hasExponent) return false
        hasExponent = true
        return true
    }

    fun backspace() {
        if (hasExponent) {
            if (!exponent.isEmpty()) {
                exponent.value.setLength(exponent.value.length - 1)
                if (exponent.isEmpty()) exponent.clear()
            } else {
                hasExponent = false
            }
        } else {
            if (!base.isEmpty()) {
                base.value.setLength(base.value.length - 1)
                if (base.value.toString() == "0") base.value.setLength(0)
            }
            if (base.value.length <= decPtIndex) {
                // Backed up over decimal point
                hasDecimal = false
                decPtIndex = 0
            }
            if (base.isEmpty()) base.clear()
        }
    }

    fun setDecimalSymbol(sym: Char) {
        if (decSymbol != sym) {
            decSymbol = sym
            if (hasDecimal) base.value.setCharAt(decPtIndex, sym)
        }
    }

    fun isEmpty(): Boolean = base.isEmpty() && !hasExponent && exponent.isEmpty() && !hasDecimal

    fun toStringForRadix(radix: Int): String {
        if (base.value.length > MAX_STRLEN || (hasExponent && exponent.value.length > MAX_STRLEN)) return ""
        val result = StringBuilder()
        if (base.isNegative) result.append('-')
        if (base.isEmpty()) result.append('0') else result.append(base.value)
        if (hasExponent) {
            if (!hasDecimal) result.append(decSymbol)
            result.append(if (radix == 10) 'e' else '^')
            result.append(if (exponent.isNegative) '-' else '+')
            if (exponent.isEmpty()) result.append('0') else result.append(exponent.value)
        }
        if (result.length > C_NUM_MAX_DIGITS * 2 + 4) return ""
        return result.toString()
    }

    fun toRational(rp: Ratpak, radix: Int, precision: Int): Rational {
        val rat = rp.stringToRat(base.isNegative, base.value.toString(), exponent.isNegative, exponent.value.toString(), radix, precision)
            ?: return Rational.of(0)
        return Rational.of(rat)
    }
}

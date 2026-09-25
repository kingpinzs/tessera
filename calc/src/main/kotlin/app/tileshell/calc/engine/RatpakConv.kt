package app.tileshell.calc.engine

import java.math.BigInteger

// Port of microsoft/calculator src/CalcManager/Ratpack/conv.cpp: radix conversion, string input (StringToNumber,
// StringToRat) and output (NumberToString, RatToString with its e-notation switch), flatrat and the int conversions.

private const val MAX_ZEROS_AFTER_DECIMAL = 2
private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_@"

/** conv.cpp numtorat: a radix NUMBER to p/q in base 2^31 with p and q integers. */
internal fun Ratpak.numtorat(pin: Num, radix: Int): Rat {
    var pn = pin
    var qn = i32tonum(1, radix.toLong())
    if (pn.exp < 0) {
        qn = qn.withExp(qn.exp - pn.exp)
        pn = pn.withExp(0)
    }
    return Rat(numtonRadixx(pn, radix), numtonRadixx(qn, radix))
}

/** conv.cpp numtonRadixx: the digits of a (in radix) times radix^exp, as a base 2^31 integer. */
internal fun numtonRadixx(a: Num, radix: Int): Num {
    val value = scaleUp(a.mant, radix.toLong(), a.exp)
    return Num.of(a.sign, 0, value, BASEX)
}

/**
 * conv.cpp nRadixxtonum: base 2^31 to [radix], keeping only precision+1 internal digits and scaling by
 * (2^31)^k computed by numpowi32, which trims the power to precision+g_ratio digits as it squares.
 */
internal fun Ratpak.nRadixxtonum(a: Num, radix: Int, precision: Int): Num {
    val r = radix.toLong()
    var cdigits = precision + 1
    if (cdigits > a.cdigit) cdigits = a.cdigit
    // i32tonum(BASEX, radix): BASEX wraps to INT_MIN, so the power carries magnitude 2^31 (its sign is overwritten).
    val base = Num.of(-1, 0, BigInteger.ONE.shiftLeft(BASEXPWR), r)
    val powofnRadix = numpowi32(base, a.exp + (a.cdigit - cdigits), r, precision, gRatio)
    val top = dropLow(a.mant, BASEX, a.cdigit - cdigits)
    var sum = Num.of(1, 0, top, r)
    sum = mulnum(sum, powofnRadix, r)
    return sum.withSign(a.sign)
}

/** conv.cpp RatToNumber: p/q in base 2^31 to a [radix] NUMBER with precision+2 digits of quotient. */
internal fun Ratpak.ratToNumber(prat: Rat, radix: Int, precision: Int): Num {
    var pp = prat.pp
    var pq = prat.pq
    var scaleby = minOf(pp.exp, pq.exp)
    scaleby = maxOf(scaleby, 0)
    pp = pp.withExp(pp.exp - scaleby)
    pq = pq.withExp(pq.exp - scaleby)
    val p = nRadixxtonum(pp, radix, precision)
    val q = nRadixxtonum(pq, radix, precision)
    return divnum(p, q, radix.toLong(), precision)
}

/** conv.cpp flatrat: through a [radix] NUMBER and back, simplifying the rational. */
internal fun Ratpak.flatrat(prat: Rat, radix: Int, precision: Int) {
    val pnum = ratToNumber(prat, radix, precision)
    prat.set(numtorat(pnum, radix))
}

/** conv.cpp stripzeroesnum: strips low zero digits found at the bottom of the top [starting] digits. */
internal fun stripzeroesnum(pnum: Num, starting: Int, radix: Long): Pair<Num, Boolean> {
    var cdigits = pnum.cdigit
    var pos = 0
    if (cdigits > starting) {
        pos += cdigits - starting
        cdigits = starting
    }
    var fstrip = false
    val str = if (pnum.cdigit > 0) pnum.mant.toString(radix.toInt()).padStart(pnum.cdigit, '0') else ""
    while (cdigits > 0 && str[pnum.cdigit - 1 - pos] == '0') {
        pos++
        cdigits--
        fstrip = true
    }
    if (fstrip) {
        return Pair(Num(pnum.sign, pnum.exp + (pnum.cdigit - cdigits), dropLow(pnum.mant, radix, pos), cdigits), true)
    }
    return Pair(pnum, false)
}

/** conv.cpp NumberToString, including the e-notation switch (exponent > precision) and the rounding rule. */
internal fun Ratpak.numberToString(pnumIn: Num, format0: NumberFormat, radix: Int, precision: Int): String {
    val r = radix.toLong()
    var format = format0
    var pnum = stripzeroesnum(pnumIn, precision + 2, r).first
    var length = pnum.cdigit
    var exponent = pnum.exp + length // Actual number of digits to the left of decimal

    val oldFormat = format
    if (exponent > precision && format == NumberFormat.Float) {
        // Force scientific mode to prevent user from assuming 33rd digit is exact.
        format = NumberFormat.Scientific
    }

    if (length > precision) length = precision

    var round: Num? = null
    if (!zernum(pnum) &&
        (pnum.cdigit >= precision || (length - exponent > precision && exponent >= -MAX_ZEROS_AFTER_DECIMAL))
    ) {
        var rnd = i32tonum(radix, r)
        rnd = divnum(rnd, numTwo, r, precision)
        if (exponent > 0 || format == NumberFormat.Float) {
            rnd = rnd.withExp(pnum.exp + pnum.cdigit - rnd.cdigit - precision)
        } else {
            rnd = rnd.withExp(pnum.exp + pnum.cdigit - rnd.cdigit - precision - exponent)
            length = precision + exponent
        }
        round = rnd.withSign(pnum.sign)
    }

    if (format == NumberFormat.Float) {
        if ((length - exponent > precision) || (exponent > precision + 3)) {
            if (exponent >= -MAX_ZEROS_AFTER_DECIMAL) {
                round = round!!.withExp(round.exp - exponent)
                length = precision + exponent
            } else {
                // Too many zeros to the right or left of the decimal point: switch to scientific form.
                format = NumberFormat.Scientific
            }
        } else if (length + kotlin.math.abs(exponent) < precision && round != null) {
            round = round.withExp(round.exp - exponent)
        }
    }

    if (round != null) {
        pnum = addnum(pnum, round, r)
        val offset = (pnum.cdigit + pnum.exp) - (round.cdigit + round.exp)
        val (stripped, didStrip) = stripzeroesnum(pnum, offset, r)
        pnum = stripped
        if (didStrip) {
            // "WARNING: nesting/recursion, too much has been changed, need to re-figure format."
            return numberToString(pnum, oldFormat, radix, precision)
        }
    } else {
        pnum = stripzeroesnum(pnum, precision, r).first
    }

    var useSciForm = false
    var eout = exponent - 1 // Displayed exponent.
    val digitStr = if (pnum.cdigit > 0) pnum.mant.toString(radix).padStart(pnum.cdigit, '0').uppercase() else ""
    var mantIndex = 0 // index into digitStr from the most significant digit
    if (format == NumberFormat.Scientific || format == NumberFormat.Engineering) {
        useSciForm = true
        if (eout != 0) {
            if (format == NumberFormat.Engineering) {
                exponent = eout % 3
                eout -= exponent
                exponent++
                if (exponent < 0) {
                    exponent += 3
                    eout -= 3
                }
            } else {
                exponent = 1
            }
        }
    } else {
        eout = 0
    }

    val result = StringBuilder()
    if (pnum.sign == -1 && length > 0) result.append('-')
    if (exponent <= 0 && !useSciForm) {
        result.append('0')
        result.append(decimalSeparator)
    }
    while (exponent < 0) {
        result.append('0')
        exponent++
    }
    while (length > 0) {
        exponent--
        val d = if (mantIndex < digitStr.length) DIGITS[Character.digit(digitStr[mantIndex], radix)] else '0'
        mantIndex++
        result.append(d)
        length--
        if (exponent == 0) result.append(decimalSeparator)
    }
    while (exponent > 0) {
        result.append('0')
        exponent--
        if (exponent == 0) result.append(decimalSeparator)
    }
    if (useSciForm) {
        result.append(if (radix == 10) 'e' else '^')
        result.append(if (eout < 0) '-' else '+')
        eout = kotlin.math.abs(eout)
        val expString = StringBuilder()
        do {
            expString.append(DIGITS[eout % radix])
            eout /= radix
        } while (eout > 0)
        result.append(expString.reverse())
    }
    if (result.isNotEmpty() && result[result.length - 1] == decimalSeparator) {
        result.setLength(result.length - 1)
    }
    return result.toString()
}

/** conv.cpp RatToString. */
internal fun Ratpak.ratToString(prat: Rat, format: NumberFormat, radix: Int, precision: Int): String {
    val p = ratToNumber(prat, radix, precision)
    return numberToString(p, format, radix, precision)
}

// StringToNumber's state machine (conv.cpp).
private const val DP = 0
private const val ZR = 1
private const val NZ = 2
private const val SG = 3
private const val EX = 4

private const val START = 0
private const val MANTS = 1
private const val LZ = 2
private const val LZDP = 3
private const val LD = 4
private const val DZ = 5
private const val DD = 6
private const val DDP = 7
private const val EXPB = 8
private const val EXPS = 9
private const val EXPD = 10
private const val EXPBZ = 11
private const val EXPSZ = 12
private const val EXPDZ = 13
private const val ERR = 14

private val MACHINE = arrayOf(
    intArrayOf(LZDP, LZ, LD, MANTS, ERR), // START
    intArrayOf(LZDP, LZ, LD, ERR, ERR), // MANTS
    intArrayOf(LZDP, LZ, LD, ERR, EXPBZ), // LZ
    intArrayOf(ERR, DZ, DD, ERR, EXPB), // LZDP
    intArrayOf(DDP, LD, LD, ERR, EXPB), // LD
    intArrayOf(ERR, DZ, DD, ERR, EXPBZ), // DZ
    intArrayOf(ERR, DD, DD, ERR, EXPB), // DD
    intArrayOf(ERR, DD, DD, ERR, EXPB), // DDP
    intArrayOf(ERR, EXPD, EXPD, EXPS, ERR), // EXPB
    intArrayOf(ERR, EXPD, EXPD, ERR, ERR), // EXPS
    intArrayOf(ERR, EXPD, EXPD, ERR, ERR), // EXPD
    intArrayOf(ERR, EXPDZ, EXPDZ, EXPSZ, ERR), // EXPBZ
    intArrayOf(ERR, EXPDZ, EXPDZ, ERR, ERR), // EXPSZ
    intArrayOf(ERR, EXPDZ, EXPDZ, ERR, ERR), // EXPDZ
    intArrayOf(ERR, ERR, ERR, ERR, ERR), // ERR
)

private fun normalizeCharDigit(c: Char, radix: Int): Char =
    if (radix >= DIGITS.indexOf('A') && radix <= DIGITS.indexOf('Z')) c.uppercaseChar() else c

/** conv.cpp StringToNumber: the input state machine; null when no number was scanned. */
internal fun Ratpak.stringToNumber(numberString: String, radix: Int, precision: Int): Num? {
    val r = radix.toLong()
    var expSign = 1
    var expValue = 0
    var sign = 1
    var cdigit = 0
    var exp = 0
    val written = StringBuilder() // digits written from the top, most significant first
    var state = START
    for (c in numberString) {
        var curChar = if (c == decimalSeparator) '.' else c
        state = when (curChar) {
            '-', '+' -> MACHINE[state][SG]
            '.' -> MACHINE[state][DP]
            '0' -> MACHINE[state][ZR]
            '^', 'e' -> if (curChar == '^' || radix == 10) MACHINE[state][EX] else MACHINE[state][NZ]
            else -> MACHINE[state][NZ]
        }
        when (state) {
            MANTS -> sign = if (curChar == '-') -1 else 1
            EXPSZ, EXPS -> expSign = if (curChar == '-') -1 else 1
            EXPDZ, EXPD -> {
                curChar = normalizeCharDigit(curChar, radix)
                val pos = DIGITS.indexOf(curChar)
                if (pos >= 0) {
                    expValue *= radix
                    expValue += pos
                } else {
                    state = ERR
                }
            }
            LD, DD -> {
                if (state == LD) exp++
                curChar = normalizeCharDigit(curChar, radix)
                val pos = DIGITS.indexOf(curChar)
                if (pos >= 0 && pos < radix) {
                    written.append(DIGITS[pos])
                    exp--
                    cdigit++
                } else {
                    state = ERR
                }
            }
            DZ -> exp--
            else -> Unit
        }
    }
    val len = numberString.length
    var mant: BigInteger
    if (state == DZ || state == EXPDZ) {
        cdigit = 1
        exp = 0
        sign = 1
        mant = BigInteger.ZERO
    } else {
        mant = if (written.isEmpty()) BigInteger.ZERO else BigInteger(written.toString(), radix)
        val pad = len - cdigit
        if (pad > 0) {
            mant = scaleUp(mant, r, pad)
            cdigit += pad
            exp -= pad
        }
        exp += expSign * expValue
    }
    if (cdigit == 0) return null
    // All digits zero ("0", "0.", "0.000"): stripzeroesnum strips every one of them, leaving cdigit 0 and the
    // exponent raised by the digits removed, exactly as the C memmove does.
    if (mant.signum() == 0) return Num(sign, exp + cdigit, BigInteger.ZERO, 0)
    return stripzeroesnum(Num(sign, exp, mant, cdigit), precision, r).first
}

/** conv.cpp StringToRat: mantissa and exponent strings to a rational; null when a part fails to scan. */
internal fun Ratpak.stringToRat(
    mantissaIsNegative: Boolean,
    mantissa: String,
    exponentIsNegative: Boolean,
    exponent: String,
    radix: Int,
    precision: Int,
): Rat? {
    val resultRat: Rat
    if (mantissa.isEmpty()) {
        resultRat = if (exponent.isEmpty()) ratZero.copy() else ratOne.copy()
    } else {
        val pnummant = stringToNumber(mantissa, radix, precision) ?: return null
        resultRat = numtorat(pnummant, radix)
    }
    var expt = 0
    if (exponent.isNotEmpty()) {
        val numExp = stringToNumber(exponent, radix, precision) ?: return null
        expt = numtoi32(numExp, radix.toLong())
    }
    val pnumexp = numpowi32x(i32tonum(radix, BASEX), kotlin.math.abs(expt))
    val pratexp = Rat(pnumexp, i32tonum(1, BASEX))
    if (exponentIsNegative) {
        divrat(resultRat, pratexp, precision)
    } else if (expt > 0) {
        mulrat(resultRat, pratexp, precision)
    }
    if (mantissaIsNegative) {
        resultRat.pp = resultRat.pp.withSign(resultRat.pp.sign * -1)
    }
    return resultRat
}

/** conv.cpp rattoi32. */
internal fun Ratpak.rattoi32(prat: Rat, radix: Int, precision: Int): Int {
    if (ratGt(prat, ratMaxI32, precision) || ratLt(prat, ratMinI32, precision)) {
        ratpakError(CalcErr.DOMAIN)
    }
    val pint = prat.copy()
    intrat(pint, radix, precision)
    pint.pp = divnumx(pint.pp, pint.pq, precision, gRatio)
    pint.pq = numOne
    return numtoi32(pint.pp, BASEX)
}

/** conv.cpp rattoUi32 (the signed conversion "happens to work" for the unsigned range). */
internal fun Ratpak.rattoUi32(prat: Rat, radix: Int, precision: Int): Long {
    if (ratGt(prat, ratDword, precision) || ratLt(prat, ratZero, precision)) {
        ratpakError(CalcErr.DOMAIN)
    }
    val pint = prat.copy()
    intrat(pint, radix, precision)
    pint.pp = divnumx(pint.pp, pint.pq, precision, gRatio)
    pint.pq = numOne
    return numtoi32(pint.pp, BASEX).toLong() and 0xFFFFFFFFL
}

/** conv.cpp rattoUi64: low and high 32-bit words through andrat / rshrat. */
internal fun Ratpak.rattoUi64(prat: Rat, radix: Int, precision: Int): Long {
    var pint = prat.copy()
    andrat(pint, ratDword, radix, precision)
    val lo = rattoUi32(pint, radix, precision)
    pint = prat.copy()
    val prat32 = i32torat(32)
    rshrat(pint, prat32, radix, precision)
    intrat(pint, radix, precision)
    andrat(pint, ratDword, radix, precision)
    val hi = rattoUi32(pint, radix, precision)
    return (hi shl 32) or lo
}

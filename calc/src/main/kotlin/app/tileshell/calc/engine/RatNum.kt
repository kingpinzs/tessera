package app.tileshell.calc.engine

import java.math.BigInteger

// Port of microsoft/calculator src/CalcManager/Ratpack/num.cpp and basex.cpp (the NUMBER layer of ratpak) plus the
// NUMBER helpers of conv.cpp (i32tonum, Ui32tonum, numtoi32). A ratpak NUMBER is a digit array in some radix with an
// exponent; here the digit array is held as one non-negative BigInteger whose digits in that radix ARE the array
// (leading digits never zero, low-order zero digits kept exactly as ratpak keeps them), so every ratpak rule that
// reads cdigit, exp or a single digit sees the same values it sees in C.

internal const val BASEXPWR = 31
/** ratpak's internal radix, 2^31 (ratpak.h BASEX). */
internal const val BASEX: Long = 0x80000000L

/** ratpak NUMBER: value = sign * mant * radix^exp, cdigit = the digit count of mant in its radix (zero has one digit). */
internal class Num(val sign: Int, val exp: Int, val mant: BigInteger, val cdigit: Int) {
    val isZero: Boolean get() = mant.signum() == 0

    fun withSign(s: Int): Num = if (s == sign) this else Num(s, exp, mant, cdigit)
    fun withExp(e: Int): Num = if (e == exp) this else Num(sign, e, mant, cdigit)

    /** ratpak's test for the NUMBER 1 (mulnum, divnum): one digit, digit 1, exponent 0, either sign. */
    val isOne: Boolean get() = cdigit == 1 && exp == 0 && mant == BigInteger.ONE

    override fun toString(): String = "Num(sign=$sign, exp=$exp, cdigit=$cdigit, mant=$mant)"

    companion object {
        fun of(sign: Int, exp: Int, mant: BigInteger, radix: Long): Num = Num(sign, exp, mant, digitCount(mant, radix))
    }
}

/** ratpak RAT p/q; a mutable holder (the C code passes PRAT* and rewrites *pa), over immutable NUMBERs. */
internal class Rat(var pp: Num, var pq: Num) {
    fun copy(): Rat = Rat(pp, pq)
    fun set(o: Rat) {
        pp = o.pp
        pq = o.pq
    }
}

internal fun digitCount(m: BigInteger, radix: Long): Int = when {
    m.signum() == 0 -> 1
    radix == BASEX -> (m.bitLength() + BASEXPWR - 1) / BASEXPWR
    radix == 2L -> m.bitLength()
    radix == 16L -> (m.bitLength() + 3) / 4
    radix == 8L -> (m.bitLength() + 2) / 3
    else -> m.toString(radix.toInt()).length
}

internal fun radixPow(radix: Long, k: Int): BigInteger = when {
    k <= 0 -> BigInteger.ONE
    radix == BASEX -> BigInteger.ONE.shiftLeft(BASEXPWR * k)
    radix == 2L -> BigInteger.ONE.shiftLeft(k)
    radix == 16L -> BigInteger.ONE.shiftLeft(4 * k)
    radix == 8L -> BigInteger.ONE.shiftLeft(3 * k)
    else -> BigInteger.valueOf(radix).pow(k)
}

internal fun scaleUp(m: BigInteger, radix: Long, k: Int): BigInteger = when {
    k <= 0 || m.signum() == 0 -> m
    radix == BASEX -> m.shiftLeft(BASEXPWR * k)
    radix == 2L -> m.shiftLeft(k)
    radix == 16L -> m.shiftLeft(4 * k)
    radix == 8L -> m.shiftLeft(3 * k)
    else -> m.multiply(BigInteger.valueOf(radix).pow(k))
}

/** Drops the k lowest digits (a memmove of mant in C). */
internal fun dropLow(m: BigInteger, radix: Long, k: Int): BigInteger = when {
    k <= 0 -> m
    radix == BASEX -> m.shiftRight(BASEXPWR * k)
    radix == 2L -> m.shiftRight(k)
    radix == 16L -> m.shiftRight(4 * k)
    radix == 8L -> m.shiftRight(3 * k)
    else -> m.divide(BigInteger.valueOf(radix).pow(k))
}

/** Digit i (0 = least significant) of the mantissa in [radix]. */
internal fun digitAt(m: BigInteger, radix: Long, i: Int): Long = when (radix) {
    BASEX -> m.shiftRight(BASEXPWR * i).toLong() and (BASEX - 1)
    else -> dropLow(m, radix, i).mod(BigInteger.valueOf(radix)).toLong()
}

internal fun zeroNum(radix: Long): Num = Num(1, 0, BigInteger.ZERO, 1)

/** conv.cpp i32tonum: a 32-bit signed value in [radix] (INT_MIN keeps magnitude 2^31, as the C wrap does). */
internal fun i32tonum(v: Int, radix: Long): Num {
    val sign = if (v < 0) -1 else 1
    val mag = if (v < 0) -(v.toLong()) else v.toLong()
    return Num.of(sign, 0, BigInteger.valueOf(mag), radix)
}

/** conv.cpp Ui32tonum. */
internal fun ui32tonum(v: Long, radix: Long): Num = Num.of(1, 0, BigInteger.valueOf(v and 0xFFFFFFFFL), radix)

/** conv.cpp numtoi32: digits at or above the radix point, 32-bit wrapping arithmetic as in C. */
internal fun numtoi32(pnum: Num, radix: Long): Int {
    var lret = 0
    var expt = pnum.exp
    var length = pnum.cdigit
    while (length > 0 && length + expt > 0) {
        lret = (lret.toLong() * radix).toInt()
        lret = (lret.toLong() + digitAt(pnum.mant, radix, length - 1)).toInt()
        length--
    }
    while (expt-- > 0) {
        lret = (lret.toLong() * radix).toInt()
    }
    return lret * pnum.sign
}

/** num.cpp addnum: *pa += b in [radix]. */
internal fun addnum(a: Num, b: Num, radix: Long): Num {
    if (b.isZero) return a
    if (a.isZero) return b
    val mexp = minOf(a.exp, b.exp)
    var va = scaleUp(a.mant, radix, a.exp - mexp)
    var vb = scaleUp(b.mant, radix, b.exp - mexp)
    if (a.sign < 0) va = va.negate()
    if (b.sign < 0) vb = vb.negate()
    val sum = va.add(vb)
    // A same-signed sum keeps a's sign; a mixed-sign sum is positive unless the complement borrow fails (<0).
    val sign = if (sum.signum() < 0) -1 else 1
    return Num.of(sign, mexp, sum.abs(), radix)
}

/** num.cpp mulnum / basex.cpp mulnumx: *pa *= b (the "one" shortcuts give the same digits as the product). */
internal fun mulnum(a: Num, b: Num, radix: Long): Num =
    Num.of(a.sign * b.sign, a.exp + b.exp, a.mant.multiply(b.mant), radix)

internal fun mulnumx(a: Num, b: Num): Num = mulnum(a, b, BASEX)

/**
 * The shared long division of num.cpp _divnum and basex.cpp _divnumx: at most [thismax] quotient digits, stopping early
 * when the remainder is zero; exponent (a.cdigit+a.exp)-(b.cdigit+b.exp)+1-digits; leading zero digits stripped.
 */
private fun longDivide(a: Num, b: Num, radix: Long, thismax: Int): Num {
    val csign = a.sign * b.sign
    val cexp0 = (a.cdigit + a.exp) - (b.cdigit + b.exp) + 1
    var n = a.mant
    var d = b.mant
    val shift = b.cdigit - a.cdigit
    if (shift > 0) n = scaleUp(n, radix, shift) else if (shift < 0) d = scaleUp(d, radix, -shift)
    val r = BigInteger.valueOf(radix)
    val rMinus1 = BigInteger.valueOf(radix - 1)
    var q = BigInteger.ZERO
    var count = 0
    while (count < thismax && n.signum() != 0) {
        val qr = n.divideAndRemainder(d)
        var digit = qr[0]
        var rem = qr[1]
        if (digit > rMinus1) { // never reached for aligned operands; kept as the C table caps the digit
            rem = n.subtract(d.multiply(rMinus1))
            digit = rMinus1
        }
        q = q.multiply(r).add(digit)
        n = rem.multiply(r)
        count++
    }
    if (count == 0) return Num(csign, 0, BigInteger.ZERO, 1)
    return Num.of(csign, cexp0 - count, q, radix)
}

/** num.cpp divnum: *pa /= b in [radix], precision+2 digits. */
internal fun divnum(a: Num, b: Num, radix: Long, precision: Int): Num {
    if (!b.isOne) {
        var thismax = precision + 2
        if (thismax < a.cdigit) thismax = a.cdigit
        if (thismax < b.cdigit) thismax = b.cdigit
        return longDivide(a, b, radix, thismax)
    }
    return a.withSign(a.sign * b.sign)
}

/** basex.cpp divnumx: *pa /= b in base 2^31; note ratpak's shortcut that 1 / b yields b. */
internal fun divnumx(a: Num, b: Num, precision: Int, gRatio: Int): Num {
    if (!b.isOne) {
        if (!a.isOne) {
            var thismax = precision + gRatio
            if (thismax < a.cdigit) thismax = a.cdigit
            if (thismax < b.cdigit) thismax = b.cdigit
            return longDivide(a, b, BASEX, thismax)
        }
        return b.withSign(b.sign * a.sign)
    }
    return a.withSign(a.sign * b.sign)
}

/** num.cpp lessnum: |a| < |b|, comparing digit positions first (so a zero sits at its own exponent). */
internal fun lessnum(a: Num, b: Num, radix: Long): Boolean {
    val diff = (a.cdigit + a.exp) - (b.cdigit + b.exp)
    if (diff < 0) return true
    if (diff > 0) return false
    val m = minOf(a.exp, b.exp)
    return scaleUp(a.mant, radix, a.exp - m) < scaleUp(b.mant, radix, b.exp - m)
}

/** num.cpp equnum: |a| == |b| with the same positional rule. */
internal fun equnum(a: Num, b: Num, radix: Long): Boolean {
    if ((a.cdigit + a.exp) != (b.cdigit + b.exp)) return false
    val m = minOf(a.exp, b.exp)
    return scaleUp(a.mant, radix, a.exp - m) == scaleUp(b.mant, radix, b.exp - m)
}

internal fun zernum(a: Num): Boolean = a.isZero

private fun msd(x: Num, radix: Long): Long = digitAt(x.mant, radix, x.cdigit - 1)

/** num.cpp remnum: *pa %= b by subtracting doubled, digit-aligned copies of b (keeps ratpak's exponents). */
internal fun remnum(pa: Num, b: Num, radix: Long): Num {
    var a = pa
    while (!lessnum(a, b, radix)) {
        var tmp = b
        if (lessnum(tmp, a, radix)) {
            var e = a.cdigit + a.exp - tmp.cdigit
            if (msd(a, radix) <= msd(tmp, radix)) e--
            tmp = tmp.withExp(e)
        }
        var lasttmp = zeroNum(radix)
        while (lessnum(tmp, a, radix)) {
            lasttmp = tmp
            tmp = addnum(tmp, tmp, radix)
        }
        if (lessnum(a, tmp, radix)) tmp = lasttmp
        tmp = tmp.withSign(-1 * a.sign)
        a = addnum(a, tmp, radix)
    }
    return a
}

/** ratpak.h TRIMNUM (number in [radix] form): keep precision+g_ratio digits when more than one digit would go. */
internal fun trimnum(x: Num, precision: Int, gRatio: Int, radix: Long): Num {
    val trim = x.cdigit - precision - gRatio
    if (trim > 1) {
        return Num(x.sign, x.exp + trim, dropLow(x.mant, radix, trim), x.cdigit - trim)
    }
    return x
}

/** conv.cpp numpowi32: root^power in [radix], trimming the squared root to precision each step. */
internal fun numpowi32(root: Num, power: Int, radix: Long, precision: Int, gRatio: Int): Num {
    var lret = i32tonum(1, radix)
    var r = root
    var p = power
    while (p > 0) {
        if ((p and 1) != 0) lret = mulnum(lret, r, radix)
        r = mulnum(r, r, radix)
        r = trimnum(r, precision, gRatio, radix)
        p = p shr 1
    }
    return lret
}

/** basex.cpp numpowi32x: exact root^power in base 2^31 (a non-positive power gives 1). */
internal fun numpowi32x(root: Num, power: Int): Num {
    var lret = i32tonum(1, BASEX)
    var r = root
    var p = power
    while (p > 0) {
        if ((p and 1) != 0) lret = mulnumx(lret, r)
        p = p shr 1
        if (p > 0) r = mulnumx(r, r)
    }
    return lret
}

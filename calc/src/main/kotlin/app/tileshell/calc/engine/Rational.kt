package app.tileshell.calc.engine

import java.math.BigInteger

/** CEngine/Rational.h RATIONAL_BASE and RATIONAL_PRECISION: every engine operation runs ratpak at 128 digits, base 10. */
internal const val RATIONAL_BASE = 10
internal const val RATIONAL_PRECISION = 128

/**
 * Port of CEngine/Rational.cpp: an immutable p/q over ratpak NUMBERs (the C++ class copies into a fresh PRAT for every
 * operation; here NUMBERs are immutable so the copy is two references).
 */
internal class Rational(val p: Num, val q: Num) {
    fun toRat(): Rat = Rat(p, q)

    /** Rational::operator-(): negates p only. */
    fun negate(): Rational = Rational(p.withSign(-1 * p.sign), q)

    companion object {
        /** Rational(): 0/1. */
        val ZERO = Rational(Num(1, 0, BigInteger.ZERO, 1), Num(1, 0, BigInteger.ONE, 1))

        fun of(r: Rat): Rational = Rational(r.pp, r.pq)

        /** Rational(int32_t) through i32torat. */
        fun of(i: Int): Rational = Rational(i32tonum(i, BASEX), i32tonum(1, BASEX))

        /** Rational(uint32_t) through Ui32torat. */
        fun ofU32(u: Long): Rational = Rational(ui32tonum(u, BASEX), i32tonum(1, BASEX))
    }
}

/**
 * Port of CEngine/Rational.cpp's operators and CEngine/RationalMath.cpp, bound to one ratpak context (the C++ code reads
 * the ratpak globals; here they belong to the Calculator that owns the engine).
 */
internal class RationalMath(val rp: Ratpak) {
    private inline fun op(a: Rational, b: Rational, f: (Rat, Rat) -> Unit): Rational {
        val l = a.toRat()
        val r = b.toRat()
        f(l, r)
        return Rational.of(l)
    }

    private inline fun un(a: Rational, f: (Rat) -> Unit): Rational {
        val l = a.toRat()
        f(l)
        return Rational.of(l)
    }

    fun add(a: Rational, b: Rational) = op(a, b) { l, r -> rp.addrat(l, r, RATIONAL_PRECISION) }
    fun sub(a: Rational, b: Rational) = op(a, b) { l, r -> rp.subrat(l, r, RATIONAL_PRECISION) }
    fun mul(a: Rational, b: Rational) = op(a, b) { l, r -> rp.mulrat(l, r, RATIONAL_PRECISION) }
    fun div(a: Rational, b: Rational) = op(a, b) { l, r -> rp.divrat(l, r, RATIONAL_PRECISION) }
    fun rem(a: Rational, b: Rational) = op(a, b) { l, r -> rp.remrat(l, r) }
    fun shl(a: Rational, b: Rational) = op(a, b) { l, r -> rp.lshrat(l, r, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun shr(a: Rational, b: Rational) = op(a, b) { l, r -> rp.rshrat(l, r, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun and(a: Rational, b: Rational) = op(a, b) { l, r -> rp.andrat(l, r, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun or(a: Rational, b: Rational) = op(a, b) { l, r -> rp.orrat(l, r, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun xor(a: Rational, b: Rational) = op(a, b) { l, r -> rp.xorrat(l, r, RATIONAL_BASE, RATIONAL_PRECISION) }

    fun eq(a: Rational, b: Rational): Boolean = rp.ratEqu(a.toRat(), b.toRat(), RATIONAL_PRECISION)
    fun ne(a: Rational, b: Rational): Boolean = !eq(a, b)
    fun lt(a: Rational, b: Rational): Boolean = rp.ratLt(a.toRat(), b.toRat(), RATIONAL_PRECISION)
    fun gt(a: Rational, b: Rational): Boolean = lt(b, a)
    fun le(a: Rational, b: Rational): Boolean = !gt(a, b)
    fun ge(a: Rational, b: Rational): Boolean = !lt(a, b)

    fun toString(a: Rational, radix: Int, fmt: NumberFormat, precision: Int): String =
        rp.ratToString(a.toRat(), fmt, radix, precision)

    /** Rational::ToUInt64_t. */
    fun toUInt64(a: Rational): Long = rp.rattoUi64(a.toRat(), RATIONAL_BASE, RATIONAL_PRECISION)

    /** Rational(uint64_t): (Rational{hi} << 32) | lo. */
    fun fromUInt64(ui: Long): Rational {
        val hi = (ui ushr 32) and 0xFFFFFFFFL
        val lo = ui and 0xFFFFFFFFL
        return or(shl(Rational.ofU32(hi), Rational.of(32)), Rational.ofU32(lo))
    }

    // RationalMath.cpp
    fun frac(a: Rational) = un(a) { rp.fracrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun integer(a: Rational) = un(a) { rp.intrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun pow(base: Rational, pow: Rational) = op(base, pow) { l, r -> rp.powrat(l, r, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun root(base: Rational, root: Rational) = pow(base, invert(root))
    fun fact(a: Rational) = un(a) { rp.factrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun exp(a: Rational) = un(a) { rp.exprat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun log(a: Rational) = un(a) { rp.lograt(it, RATIONAL_PRECISION) }
    fun log10(a: Rational) = div(log(a), Rational.of(rp.lnTen))
    fun invert(a: Rational) = div(Rational.of(1), a)
    fun abs(a: Rational) = Rational(a.p.withSign(1), a.q.withSign(1))
    fun sin(a: Rational, t: AngleType) = un(a) { rp.sinanglerat(it, t, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun cos(a: Rational, t: AngleType) = un(a) { rp.cosanglerat(it, t, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun tan(a: Rational, t: AngleType) = un(a) { rp.tananglerat(it, t, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun asin(a: Rational, t: AngleType) = un(a) { rp.asinanglerat(it, t, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun acos(a: Rational, t: AngleType) = un(a) { rp.acosanglerat(it, t, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun atan(a: Rational, t: AngleType) = un(a) { rp.atananglerat(it, t, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun sinh(a: Rational) = un(a) { rp.sinhrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun cosh(a: Rational) = un(a) { rp.coshrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun tanh(a: Rational) = un(a) { rp.tanhrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun asinh(a: Rational) = un(a) { rp.asinhrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun acosh(a: Rational) = un(a) { rp.acoshrat(it, RATIONAL_BASE, RATIONAL_PRECISION) }
    fun atanh(a: Rational) = un(a) { rp.atanhrat(it, RATIONAL_PRECISION) }
    fun mod(a: Rational, b: Rational) = op(a, b) { l, r -> rp.modrat(l, r) }
}

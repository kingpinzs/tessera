package app.tileshell.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

/**
 * The ratpak / Rational layer. The first half is Microsoft's own CalculatorUnitTests/RationalTest.cpp (Mod and the %
 * remainder, same inputs and expectations, ChangeConstants(10, 128) as its setup); the rest pins conv.cpp's output
 * rules and the constants.
 */
class RationalTest {
    private val rp = Ratpak().also { it.changeConstants(10, 128) }
    private val m = RationalMath(rp)

    private fun r(i: Int) = Rational.of(i)
    private fun num(sign: Int, v: Long) = Num.of(sign, 0, BigInteger.valueOf(v), BASEX)
    private fun rq(p: Long, q: Long, sign: Int = 1) = Rational(num(sign, p), num(1, q))
    private fun eq(a: Rational, i: Int) = assertTrue("expected $i", m.eq(a, r(i)))
    private fun str(a: Rational) = m.toString(a, 10, NumberFormat.Float, 8)

    @Test fun moduloOperandsNotModified() {
        eq(m.mod(r(25), r(4)), 1)
        eq(m.mod(r(25), r(-4)), -3)
        eq(m.mod(r(-25), r(-4)), -1)
        eq(m.mod(r(-25), r(4)), 3)
    }

    @Test fun moduloInteger() {
        eq(m.mod(r(426), r(56478)), 426)
        eq(m.mod(r(56478), r(426)), 246)
        eq(m.mod(r(-643), r(8756)), 8113)
        eq(m.mod(r(643), r(-8756)), -8113)
        eq(m.mod(r(-643), r(-8756)), -643)
        eq(m.mod(r(1000), r(250)), 0)
        eq(m.mod(r(1000), r(-250)), 0)
    }

    @Test fun moduloZero() {
        eq(m.mod(r(343654332), r(0)), 343654332)
        eq(m.mod(r(0), r(8756)), 0)
        eq(m.mod(r(0), r(-242)), 0)
        eq(m.mod(r(0), r(0)), 0)
        eq(m.mod(rq(23242, 2), Rational(num(1, 0), num(1, 23))), 11621)
    }

    @Test fun moduloRational() {
        assertEquals("2.5", str(m.mod(rq(250, 100), r(89))))
        assertEquals("0.5", str(m.mod(rq(3330, 1332), r(1))))
        assertEquals("2.5", str(m.mod(rq(12250, 100), r(10))))
        assertEquals("7.5", str(m.mod(rq(12250, 100, -1), r(10))))
        assertEquals("-2.5", str(m.mod(rq(12250, 100, -1), r(-10))))
        assertEquals("-7.5", str(m.mod(rq(12250, 100), r(-10))))
        assertEquals("0.33333333", str(m.mod(rq(1000, 3), r(1))))
        assertEquals("-6.6666667", str(m.mod(rq(1000, 3), r(-10))))
        assertEquals("0.71", str(m.mod(r(834345), rq(103, 100))))
        assertEquals("-0.32", str(m.mod(r(834345), rq(103, 100, -1))))
    }

    @Test fun remainderIntegerAndSigns() {
        eq(m.rem(r(25), r(4)), 1)
        eq(m.rem(r(25), r(-4)), 1)
        eq(m.rem(r(-25), r(-4)), -1)
        eq(m.rem(r(-25), r(4)), -1)
        eq(m.rem(r(426), r(56478)), 426)
        eq(m.rem(r(56478), r(426)), 246)
        eq(m.rem(r(-643), r(8756)), -643)
        eq(m.rem(r(643), r(-8756)), 643)
        eq(m.rem(r(-643), r(-8756)), -643)
        eq(m.rem(r(-124), r(-124)), 0)
        eq(m.rem(r(24), r(24)), 0)
    }

    @Test fun remainderZero() {
        eq(m.rem(r(0), r(3654)), 0)
        eq(m.rem(r(0), r(-242)), 0)
        for (n in intArrayOf(343654332, 0, -23423)) {
            try {
                m.rem(r(n), r(0))
                fail("x % 0 must throw")
            } catch (e: RatpakException) {
                assertEquals(CalcErr.INDEFINITE, e.code)
            }
        }
    }

    @Test fun remainderRational() {
        assertEquals("2.5", str(m.rem(rq(250, 100), r(89))))
        assertEquals("0.5", str(m.rem(rq(3330, 1332), r(1))))
        assertEquals("2.5", str(m.rem(rq(12250, 100), r(10))))
        assertEquals("-2.5", str(m.rem(rq(12250, 100, -1), r(10))))
        assertEquals("-2.5", str(m.rem(rq(12250, 100, -1), r(-10))))
        assertEquals("2.5", str(m.rem(rq(12250, 100), r(-10))))
        assertEquals("0.33333333", str(m.rem(rq(1000, 3), r(1))))
        assertEquals("3.3333333", str(m.rem(rq(1000, 3), r(-10))))
        assertEquals("-3.3333333", str(m.rem(rq(1000, 3, -1), r(-10))))
        assertEquals("0.71", str(m.rem(r(834345), rq(103, 100))))
        assertEquals("0.71", str(m.rem(r(834345), rq(103, 100, -1))))
        assertEquals("-0.71", str(m.rem(r(-834345), rq(103, 100))))
    }

    // ---- conv.cpp output rules (NumberToString)

    private val sci = Ratpak().also { it.changeConstants(10, 32) }
    private val sm = RationalMath(sci)
    private fun fmt(v: Rational, precision: Int, format: NumberFormat = NumberFormat.Float) =
        sm.toString(v, 10, format, precision)
    private fun parse(s: String): Rational = Rational.of(sci.stringToRat(false, s, false, "", 10, 32)!!)

    @Test fun exactDecimalArithmetic() {
        assertEquals("0.3", fmt(sm.add(parse("0.1"), parse("0.2")), 16))
        assertEquals("0.3333333333333333", fmt(sm.div(r(1), r(3)), 16))
        assertEquals("0.6666666666666667", fmt(sm.div(r(2), r(3)), 16))
        assertEquals("0.33333333333333333333333333333333", fmt(sm.div(r(1), r(3)), 32))
    }

    @Test fun eNotationSwitchesWhenIntegerDigitsExceedPrecision() {
        val tenTo16 = sm.pow(r(10), r(16))
        assertEquals("1.e+16", fmt(tenTo16, 16))
        assertEquals("10,000,000,000,000,000".replace(",", ""), fmt(tenTo16, 32))
        val big = sm.sub(sm.pow(r(10), r(16)), r(1))
        assertEquals("9999999999999999", fmt(big, 16))
    }

    @Test fun smallNumbersKeepTwoLeadingZerosThenSwitch() {
        assertEquals("0.001", fmt(parse("0.001"), 16))
        assertEquals("0.0001", fmt(parse("0.0001"), 16))
        assertEquals("1.e-5", fmt(sm.div(r(1), r(100000)), 16, NumberFormat.Scientific))
        // 1/7000: exponent -3 (> 2 zeros after the point) and more digits than fit: e-notation.
        assertEquals("1.428571428571429e-4", fmt(sm.div(r(1), r(7000)), 16))
        // 1/700: exponent -2 keeps float form with precision+exponent significant digits.
        assertEquals("0.0014285714285714", fmt(sm.div(r(1), r(700)), 16))
    }

    @Test fun scientificFormatAlwaysUsesExponent() {
        assertEquals("1.2345e+4", fmt(r(12345), 32, NumberFormat.Scientific))
        assertEquals("5.e+0", fmt(r(5), 32, NumberFormat.Scientific))
        assertEquals("-2.5e-1", fmt(sm.div(r(-1), r(4)), 32, NumberFormat.Scientific))
    }

    @Test fun radixOutput() {
        assertEquals("FF", sm.toString(r(255), 16, NumberFormat.Float, 64))
        assertEquals("377", sm.toString(r(255), 8, NumberFormat.Float, 64))
        assertEquals("11111111", sm.toString(r(255), 2, NumberFormat.Float, 64))
    }

    @Test fun tableConstantsAreWindowsOwn() {
        // ratconst.h's pi is good to about 44 digits (3.14159265358979323846264338327950288419716939931...).
        assertEquals("3.1415926535897932384626433832795", fmt(Rational.of(sci.pi), 32))
        assertEquals("2.7182818284590452353602874713527", fmt(Rational.of(sci.ratExp), 32))
        assertEquals("2.3025850929940456840179914546844", fmt(Rational.of(sci.lnTen), 32))
        assertFalse(sci.ratEqu(sci.pi, sci.twoPi, 128))
    }

    @Test fun uint64RoundTrip() {
        val q = RationalMath(Ratpak().also { it.changeConstants(10, 64) })
        val v = q.fromUInt64(-1L)
        assertEquals("18446744073709551615", q.toString(v, 10, NumberFormat.Float, 64))
        assertEquals(-1L, q.toUInt64(v))
        assertEquals(0x123456789ABCDEFL, q.toUInt64(q.fromUInt64(0x123456789ABCDEFL)))
    }
}

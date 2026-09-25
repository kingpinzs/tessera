package app.tileshell.calc.engine

/** The ratpak error codes of Ratpack/CalcErr.h, thrown by the ratpak port and caught by the engine. */
internal class RatpakException(val code: Int) : RuntimeException(null, null, false, false)

internal object CalcErr {
    const val DIVIDEBYZERO = 0x80000000.toInt()
    const val DOMAIN = 0x80000001.toInt()
    const val INDEFINITE = 0x80000002.toInt()
    const val POSINFINITY = 0x80000003.toInt()
    const val NEGINFINITY = 0x80000004.toInt()
    const val INVALIDRANGE = 0x80000006.toInt()
    const val OUTOFMEMORY = 0x80000007.toInt()
    const val OVERFLOW = 0x80000008.toInt()
    const val NORESULT = 0x80000009.toInt()
}

internal fun ratpakError(code: Int): Nothing = throw RatpakException(code)

internal enum class NumberFormat { Float, Scientific, Engineering }

internal enum class AngleType { Degrees, Radians, Gradians }

/**
 * The global state of ratpak (support.cpp and conv.cpp globals: g_ratio, the constants, rat_smallest, the decimal
 * separator, cbitsofprecision) as one object, so each Calculator (and each Tess evaluation) owns its own copy instead
 * of sharing C globals. Port of Ratpack/rat.cpp and Ratpack/support.cpp; conv.cpp is RatpakConv.kt and the
 * transcendental files are RatpakTrans.kt.
 */
internal class Ratpak {
    var gRatio: Int = 0
    var decimalSeparator: Char = '.'
    private var cbitsofprecision = RATIO_FOR_DECIMAL * DECIMAL * CALC_DECIMAL_DIGITS_DEFAULT

    // Number constants.
    lateinit var numOne: Num
    lateinit var numTwo: Num
    lateinit var numFive: Num
    lateinit var numSix: Num
    lateinit var numTen: Num

    // Rational constants (Rat holders; ratpak normalises signs in place on some of them, value-preserving).
    lateinit var lnTen: Rat
    lateinit var lnTwo: Rat
    lateinit var ratZero: Rat
    lateinit var ratNegOne: Rat
    lateinit var ratOne: Rat
    lateinit var ratTwo: Rat
    lateinit var ratSix: Rat
    lateinit var ratHalf: Rat
    lateinit var ratTen: Rat
    lateinit var ptEightFive: Rat
    lateinit var pi: Rat
    lateinit var piOverTwo: Rat
    lateinit var twoPi: Rat
    lateinit var onePtFivePi: Rat
    lateinit var eToOneHalf: Rat
    lateinit var ratExp: Rat
    lateinit var radToDeg: Rat
    lateinit var radToGrad: Rat
    lateinit var ratQword: Rat
    lateinit var ratDword: Rat
    lateinit var ratWord: Rat
    lateinit var ratByte: Rat
    lateinit var rat360: Rat
    lateinit var rat400: Rat
    lateinit var rat180: Rat
    lateinit var rat200: Rat
    lateinit var ratNRadix: Rat
    lateinit var ratSmallest: Rat
    lateinit var ratNegSmallest: Rat
    lateinit var ratMaxExp: Rat
    lateinit var ratMinExp: Rat
    lateinit var ratMaxFact: Rat
    lateinit var ratMinFact: Rat
    lateinit var ratMaxI32: Rat
    lateinit var ratMinI32: Rat

    init {
        // CCalcEngine::InitialOneTimeOnlySetup -> ChangeBaseConstants(DEFAULT_RADIX 10, DEFAULT_MAX_DIGITS 32, 32).
        changeConstants(10, 32)
    }

    // ------------------------------------------------------------------ support.cpp

    /** support.cpp ChangeConstants. */
    fun changeConstants(radix: Int, precision: Int) {
        gRatio = ratioFor(radix)
        ratNRadix = i32torat(radix)
        if (cbitsofprecision < gRatio * radix * precision) {
            // The constants that are only initialised when null in C are already set (from the table) here.
            ratSmallest = ratNRadix.copy()
            ratpowi32(ratSmallest, -precision, precision)
            ratNegSmallest = ratSmallest.copy()
            ratNegSmallest.pp = ratNegSmallest.pp.withSign(-1)

            ratQword = ratTwo.copy()
            ratQword.pp = numpowi32(ratQword.pp, 64, BASEX, precision, gRatio)
            subratRaw(ratQword, ratOne, precision)
            ratDword = ratTwo.copy()
            ratDword.pp = numpowi32(ratDword.pp, 32, BASEX, precision, gRatio)
            subratRaw(ratDword, ratOne, precision)
            ratMaxI32 = ratTwo.copy()
            ratMaxI32.pp = numpowi32(ratMaxI32.pp, 31, BASEX, precision, gRatio)
            ratMinI32 = ratMaxI32.copy()
            subratRaw(ratMaxI32, ratOne, precision)
            ratMinI32.pp = ratMinI32.pp.withSign(ratMinI32.pp.sign * -1)
            ratMinExp = ratMaxExp.copy()
            ratMinExp.pp = ratMinExp.pp.withSign(ratMinExp.pp.sign * -1)

            cbitsofprecision = gRatio * radix * precision

            // "Apparently when dividing 180 by pi, another (internal) digit of precision is needed."
            val extraPrecision = precision + gRatio
            pi = ratHalf.copy()
            asinrat(pi, radix, extraPrecision)
            mulrat(pi, ratSix, extraPrecision)

            twoPi = pi.copy()
            piOverTwo = pi.copy()
            onePtFivePi = pi.copy()
            addratRaw(twoPi, pi, extraPrecision)
            divrat(piOverTwo, ratTwo, extraPrecision)
            addratRaw(onePtFivePi, piOverTwo, extraPrecision)

            eToOneHalf = ratHalf.copy()
            exprat0(eToOneHalf, extraPrecision)
            ratExp = ratOne.copy()
            exprat0(ratExp, extraPrecision)

            lnTen = ratTen.copy()
            lograt0(lnTen, extraPrecision)
            lnTwo = ratTwo.copy()
            lograt0(lnTwo, extraPrecision)

            radToDeg = i32torat(180)
            divrat(radToDeg, pi, extraPrecision)
            radToGrad = i32torat(200)
            divrat(radToGrad, pi, extraPrecision)
        } else {
            readConstants()
            ratSmallest = ratNRadix.copy()
            ratpowi32(ratSmallest, -precision, precision)
            ratNegSmallest = ratSmallest.copy()
            ratNegSmallest.pp = ratNegSmallest.pp.withSign(-1)
        }
    }

    /** support.cpp _readconstants. */
    private fun readConstants() {
        numOne = RatConst.numOne
        numTwo = RatConst.numTwo
        numFive = RatConst.numFive
        numSix = RatConst.numSix
        numTen = RatConst.numTen
        ptEightFive = Rat(RatConst.pPtEightFive, RatConst.qPtEightFive)
        ratSix = Rat(RatConst.pRatSix, RatConst.qRatSix)
        ratTwo = Rat(RatConst.pRatTwo, RatConst.qRatTwo)
        ratZero = Rat(RatConst.pRatZero, RatConst.qRatZero)
        ratOne = Rat(RatConst.pRatOne, RatConst.qRatOne)
        ratNegOne = Rat(RatConst.pRatNegOne, RatConst.qRatNegOne)
        ratHalf = Rat(RatConst.pRatHalf, RatConst.qRatHalf)
        ratTen = Rat(RatConst.pRatTen, RatConst.qRatTen)
        pi = Rat(RatConst.pPi, RatConst.qPi)
        twoPi = Rat(RatConst.pTwoPi, RatConst.qTwoPi)
        piOverTwo = Rat(RatConst.pPiOverTwo, RatConst.qPiOverTwo)
        onePtFivePi = Rat(RatConst.pOnePtFivePi, RatConst.qOnePtFivePi)
        eToOneHalf = Rat(RatConst.pEToOneHalf, RatConst.qEToOneHalf)
        ratExp = Rat(RatConst.pRatExp, RatConst.qRatExp)
        lnTen = Rat(RatConst.pLnTen, RatConst.qLnTen)
        lnTwo = Rat(RatConst.pLnTwo, RatConst.qLnTwo)
        radToDeg = Rat(RatConst.pRadToDeg, RatConst.qRadToDeg)
        radToGrad = Rat(RatConst.pRadToGrad, RatConst.qRadToGrad)
        ratQword = Rat(RatConst.pRatQword, RatConst.qRatQword)
        ratDword = Rat(RatConst.pRatDword, RatConst.qRatDword)
        ratWord = Rat(RatConst.pRatWord, RatConst.qRatWord)
        ratByte = Rat(RatConst.pRatByte, RatConst.qRatByte)
        rat360 = Rat(RatConst.pRat360, RatConst.qRat360)
        rat400 = Rat(RatConst.pRat400, RatConst.qRat400)
        rat180 = Rat(RatConst.pRat180, RatConst.qRat180)
        rat200 = Rat(RatConst.pRat200, RatConst.qRat200)
        ratSmallest = Rat(RatConst.pRatSmallest, RatConst.qRatSmallest)
        ratNegSmallest = Rat(RatConst.pRatNegsmallest, RatConst.qRatNegsmallest)
        ratMaxExp = Rat(RatConst.pRatMaxExp, RatConst.qRatMaxExp)
        ratMinExp = Rat(RatConst.pRatMinExp, RatConst.qRatMinExp)
        ratMaxFact = Rat(RatConst.pRatMaxFact, RatConst.qRatMaxFact)
        ratMinFact = Rat(RatConst.pRatMinFact, RatConst.qRatMinFact)
        ratMinI32 = Rat(RatConst.pRatMinI32, RatConst.qRatMinI32)
        ratMaxI32 = Rat(RatConst.pRatMaxI32, RatConst.qRatMaxI32)
    }

    /** support.cpp intrat: the integer part (toward zero). */
    fun intrat(px: Rat, radix: Int, precision: Int) {
        if (!zernum(px.pp) && !equnum(px.pq, numOne, BASEX)) {
            flatrat(px, radix, precision)
            val pret = px.copy()
            remrat(pret, ratOne)
            if (!equnum(px.pq, pret.pq, BASEX)) {
                flatrat(pret, radix, precision)
            }
            subratRaw(px, pret, precision)
            flatrat(px, radix, precision)
        }
    }

    fun ratEqu(a: Rat, b: Rat, precision: Int): Boolean {
        val rattmp = a.copy()
        rattmp.pp = rattmp.pp.withSign(rattmp.pp.sign * -1)
        addratRaw(rattmp, b, precision)
        return zernum(rattmp.pp)
    }

    fun ratGe(a: Rat, b: Rat, precision: Int): Boolean {
        val rattmp = a.copy()
        addratRaw(rattmp, negated(b), precision)
        return zernum(rattmp.pp) || sign(rattmp) == 1
    }

    fun ratGt(a: Rat, b: Rat, precision: Int): Boolean {
        val rattmp = a.copy()
        addratRaw(rattmp, negated(b), precision)
        return !zernum(rattmp.pp) && sign(rattmp) == 1
    }

    fun ratLe(a: Rat, b: Rat, precision: Int): Boolean {
        val rattmp = a.copy()
        addratRaw(rattmp, negated(b), precision)
        return zernum(rattmp.pp) || sign(rattmp) == -1
    }

    fun ratLt(a: Rat, b: Rat, precision: Int): Boolean {
        val rattmp = a.copy()
        addratRaw(rattmp, negated(b), precision)
        return !zernum(rattmp.pp) && sign(rattmp) == -1
    }

    fun ratNeq(a: Rat, b: Rat, precision: Int): Boolean {
        val rattmp = a.copy()
        rattmp.pp = rattmp.pp.withSign(rattmp.pp.sign * -1)
        addratRaw(rattmp, b, precision)
        return !zernum(rattmp.pp)
    }

    /** support.cpp scale: x - int(x / scalefact) * scalefact. */
    fun scale(px: Rat, scalefact: Rat, radix: Int, precision0: Int) {
        var precision = precision0
        val pret = px.copy()
        val logscale = gRatio * ((pret.pp.cdigit + pret.pp.exp) - (pret.pq.cdigit + pret.pq.exp))
        if (logscale > 0) precision += logscale
        divrat(pret, scalefact, precision)
        intrat(pret, radix, precision)
        mulrat(pret, scalefact, precision)
        pret.pp = pret.pp.withSign(pret.pp.sign * -1)
        addratRaw(px, pret, precision)
    }

    /** support.cpp scale2pi. */
    fun scale2pi(px: Rat, radix: Int, precision0: Int) {
        var precision = precision0
        val pret = px.copy()
        val myTwoPi: Rat
        val logscale = gRatio * ((pret.pp.cdigit + pret.pp.exp) - (pret.pq.cdigit + pret.pq.exp))
        if (logscale > 0) {
            precision += logscale
            myTwoPi = ratHalf.copy()
            asinrat(myTwoPi, radix, precision)
            mulrat(myTwoPi, ratSix, precision)
            mulrat(myTwoPi, ratTwo, precision)
        } else {
            myTwoPi = twoPi.copy()
        }
        divrat(pret, myTwoPi, precision)
        intrat(pret, radix, precision)
        mulrat(pret, myTwoPi, precision)
        pret.pp = pret.pp.withSign(pret.pp.sign * -1)
        addratRaw(px, pret, precision)
    }

    /** support.cpp inbetween: clamp to -range..+range. */
    fun inbetween(px: Rat, range: Rat, precision: Int) {
        if (ratGt(px, range, precision)) {
            px.set(range)
        } else {
            val neg = negated(range)
            if (ratLt(px, neg, precision)) px.set(neg)
        }
    }

    /** support.cpp trimit: keep `precision` digits of the larger of p and q, and drop common exponents. */
    fun trimit(px: Rat, precision: Int) {
        var pp = px.pp
        var pq = px.pq
        var trim = gRatio * (minOf(pp.cdigit + pp.exp, pq.cdigit + pq.exp) - 1) - precision
        if (trim > gRatio) {
            trim /= gRatio
            pp = if (trim <= pp.exp) pp.withExp(pp.exp - trim) else {
                val drop = trim - pp.exp
                Num(pp.sign, 0, dropLow(pp.mant, BASEX, drop), pp.cdigit - drop)
            }
            pq = if (trim <= pq.exp) pq.withExp(pq.exp - trim) else {
                val drop = trim - pq.exp
                Num(pq.sign, 0, dropLow(pq.mant, BASEX, drop), pq.cdigit - drop)
            }
        }
        val t = minOf(pp.exp, pq.exp)
        px.pp = pp.withExp(pp.exp - t)
        px.pq = pq.withExp(pq.exp - t)
    }

    /** ratpak.h TRIMTOP: trim p to precision/g_ratio + 2 internal digits, then drop common exponents. */
    fun trimtop(x: Rat, precision: Int) {
        var pp = x.pp
        val trim = pp.cdigit - (precision / gRatio) - 2
        if (trim > 1) {
            pp = Num(pp.sign, pp.exp + trim, dropLow(pp.mant, BASEX, trim), pp.cdigit - trim)
        }
        val t = minOf(pp.exp, x.pq.exp)
        x.pp = pp.withExp(pp.exp - t)
        x.pq = x.pq.withExp(x.pq.exp - t)
    }

    /** ratpak.h SMALL_ENOUGH_RAT. */
    fun smallEnoughRat(a: Rat, precision: Int): Boolean =
        zernum(a.pp) || ((a.pq.cdigit + a.pq.exp) - (a.pp.cdigit + a.pp.exp) - 1) * gRatio > precision

    // ------------------------------------------------------------------ rat.cpp

    /** rat.cpp fracrat: the fractional part. */
    fun fracrat(pa: Rat, radix: Int, precision: Int) {
        if (!zernum(pa.pp) && !equnum(pa.pq, numOne, BASEX)) {
            flatrat(pa, radix, precision)
        }
        pa.pp = remnum(pa.pp, pa.pq, BASEX)
        renormalize(pa)
    }

    fun mulrat(pa: Rat, b: Rat, precision: Int) {
        if (!zernum(pa.pp)) {
            pa.pp = mulnumx(pa.pp, b.pp)
            pa.pq = mulnumx(pa.pq, b.pq)
            trimit(pa, precision)
        } else {
            pa.pq = numOne
        }
    }

    fun divrat(pa: Rat, b: Rat, precision: Int) {
        if (!zernum(pa.pp)) {
            val bpq = b.pq
            val bpp = b.pp
            pa.pp = mulnumx(pa.pp, bpq)
            pa.pq = mulnumx(pa.pq, bpp)
            if (zernum(pa.pq)) ratpakError(CalcErr.DIVIDEBYZERO)
            trimit(pa, precision)
        } else {
            if (zerrat(b)) ratpakError(CalcErr.INDEFINITE)
            pa.pq = numOne
        }
    }

    /** rat.cpp subrat: subtraction followed by the snap to zero. */
    fun subrat(pa: Rat, b: Rat, precision: Int) {
        val a = pa.copy()
        subratRaw(pa, b, precision)
        snaprat(pa, a, b, precision)
    }

    /** rat.cpp _subrat. */
    fun subratRaw(pa: Rat, b: Rat, precision: Int) {
        addratRaw(pa, negated(b), precision)
    }

    /** rat.cpp addrat: addition followed by the snap to zero. */
    fun addrat(pa: Rat, b: Rat, precision: Int) {
        val a = pa.copy()
        addratRaw(pa, b, precision)
        snaprat(pa, a, b, precision)
    }

    /** rat.cpp _addrat. */
    fun addratRaw(pa: Rat, b: Rat, precision: Int) {
        if (equnum(pa.pq, b.pq, BASEX)) {
            // "Very special case, q's match": fold both q signs into p (b's representation is normalised too).
            pa.pp = pa.pp.withSign(pa.pp.sign * pa.pq.sign)
            pa.pq = pa.pq.withSign(1)
            b.pp = b.pp.withSign(b.pp.sign * b.pq.sign)
            b.pq = b.pq.withSign(1)
            pa.pp = addnum(pa.pp, b.pp, BASEX)
        } else {
            val bot = mulnumx(pa.pq, b.pq)
            val p1 = mulnumx(pa.pp, b.pq)
            val p2 = mulnumx(pa.pq, b.pp)
            pa.pp = addnum(p1, p2, BASEX)
            pa.pq = bot
            trimit(pa, precision)
            pa.pp = pa.pp.withSign(pa.pp.sign * pa.pq.sign)
            pa.pq = pa.pq.withSign(1)
        }
    }

    /** rat.cpp rootrat: y^(1/n). */
    fun rootrat(py: Rat, n: Rat, radix: Int, precision: Int) {
        val oneovern = ratOne.copy()
        divrat(oneovern, n, precision)
        powrat(py, oneovern, radix, precision)
    }

    fun zerrat(a: Rat): Boolean = zernum(a.pp)

    /** rat.cpp _snaprat: a result smaller than max(|a|,|b|) * rat_smallest is snapped to zero. */
    fun snaprat(pr: Rat, a: Rat, b: Rat?, precision: Int) {
        val threshold: Rat
        if (b == null) {
            threshold = abs(a)
        } else {
            val absA = abs(a)
            val absB = abs(b)
            threshold = if (ratLt(absA, absB, precision)) absB.copy() else absA.copy()
        }
        mulrat(threshold, ratSmallest, precision)
        val absR = abs(pr)
        if (ratLt(absR, threshold, precision)) {
            pr.set(ratZero)
        }
    }

    // ------------------------------------------------------------------ small helpers of the C macros

    fun sign(r: Rat): Int = r.pp.sign * r.pq.sign

    fun abs(r: Rat): Rat = Rat(r.pp.withSign(1), r.pq.withSign(1))

    fun negated(r: Rat): Rat = Rat(r.pp.withSign(r.pp.sign * -1), r.pq)

    /** ratpak.h RENORMALIZE: make both exponents non-negative. */
    fun renormalize(x: Rat) {
        if (x.pp.exp < 0) {
            x.pq = x.pq.withExp(x.pq.exp - x.pp.exp)
            x.pp = x.pp.withExp(0)
        }
        if (x.pq.exp < 0) {
            x.pp = x.pp.withExp(x.pp.exp - x.pq.exp)
            x.pq = x.pq.withExp(0)
        }
    }

    fun i32torat(v: Int): Rat = Rat(i32tonum(v, BASEX), i32tonum(1, BASEX))

    fun ui32torat(v: Long): Rat = Rat(ui32tonum(v, BASEX), i32tonum(1, BASEX))

    /** LOGNUMRADIX / LOGRATRADIX. */
    fun logRatRadix(r: Rat): Int = (r.pp.cdigit + r.pp.exp) * gRatio - (r.pq.cdigit + r.pq.exp) * gRatio

    fun logRat2(r: Rat): Int = (r.pp.cdigit + r.pp.exp) - (r.pq.cdigit + r.pq.exp)

    /** conv.cpp ratpowi32: root^power, trimming as it squares; a negative power inverts. */
    fun ratpowi32(proot: Rat, power: Int, precision: Int) {
        if (power < 0) {
            ratpowi32(proot, -power, precision)
            val t = proot.pp
            proot.pp = proot.pq
            proot.pq = t
        } else {
            val lret = i32torat(1)
            var p = power
            while (p > 0) {
                if ((p and 1) != 0) {
                    lret.pp = mulnumx(lret.pp, proot.pp)
                    lret.pq = mulnumx(lret.pq, proot.pq)
                }
                mulrat(proot, proot.copy(), precision)
                trimit(lret, precision)
                trimit(proot, precision)
                p = p shr 1
            }
            proot.set(lret)
        }
    }

    companion object {
        private const val RATIO_FOR_DECIMAL = 9
        private const val DECIMAL = 10
        private const val CALC_DECIMAL_DIGITS_DEFAULT = 32

        /** static_cast<int32_t>(ceil(BASEXPWR / log2(radix))) - 1 for the four radixes the engine uses. */
        fun ratioFor(radix: Int): Int = when (radix) {
            2 -> 30
            8 -> 10
            10 -> 9
            16 -> 7
            else -> Math.ceil(BASEXPWR / (Math.log(radix.toDouble()) / Math.log(2.0))).toInt() - 1
        }
    }
}

package app.tileshell.calc.engine

// Port of microsoft/calculator src/CalcManager/Ratpack/exp.cpp, fact.cpp, trans.cpp, itrans.cpp, transh.cpp,
// itransh.cpp and logic.cpp: ratpak's Taylor series (CREATETAYLOR / NEXTTERM / DESTROYTAYLOR of ratpak.h), its scaling
// and snapping rules, powers, factorial (with the gamma series), and the integer / bitwise helpers.

/** ratpak.h INC for a series counter (a small non-negative base 2^31 integer). */
private fun inc(n: Num): Num {
    val m = n.mant.add(java.math.BigInteger.ONE)
    return Num(n.sign, n.exp, m, digitCount(m, BASEX))
}

/** The squared argument CREATETAYLOR prepares: xx = x * x. */
private fun Ratpak.taylorXX(px: Rat, precision: Int): Rat {
    val xx = px.copy()
    mulrat(xx, px, precision)
    return xx
}

/** DESTROYTAYLOR: trim the sum and hand it back through *px. */
private fun Ratpak.destroyTaylor(px: Rat, pret: Rat, precision: Int) {
    trimit(pret, precision)
    px.set(pret)
}

// ------------------------------------------------------------------ exp.cpp

/** exp.cpp _exprat: the series of e^x (x already small). */
internal fun Ratpak.exprat0(px: Rat, precision: Int) {
    val pret = Rat(numOne, numOne)
    val thisterm = pret.copy()
    var n2 = i32tonum(0, BASEX)
    do {
        mulrat(thisterm, px, precision)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
}

/** exp.cpp exprat: e^x as e^int(x) * e^frac(x). */
internal fun Ratpak.exprat(px: Rat, radix: Int, precision: Int) {
    if (ratGt(px, ratMaxExp, precision) || ratLt(px, ratMinExp, precision)) {
        ratpakError(CalcErr.DOMAIN)
    }
    val pwr = ratExp.copy()
    val pint = px.copy()
    intrat(pint, radix, precision)
    val intpwr = rattoi32(pint, radix, precision)
    ratpowi32(pwr, intpwr, precision)
    subratRaw(px, pint, precision)
    // It just so happens to be an integral power of e.
    if (ratGt(px, ratNegSmallest, precision) && ratLt(px, ratSmallest, precision)) {
        px.set(pwr)
    } else {
        exprat0(px, precision)
        mulrat(px, pwr, precision)
    }
}

/** exp.cpp __lograt: the series of ln(x) for x near 1. */
private fun Ratpak.lograt00(px: Rat, precision: Int) {
    // sub one from x
    px.pp = addnum(px.pp, px.pq.withSign(px.pq.sign * -1), BASEX)
    val pret = px.copy()
    val thisterm = px.copy()
    var n2 = i32tonum(1, BASEX)
    px.pp = px.pp.withSign(px.pp.sign * -1)
    do {
        mulrat(thisterm, px, precision)
        thisterm.pp = mulnumx(thisterm.pp, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
        trimtop(px, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
}

/** exp.cpp _lograt: scale into [1, e^0.5] by powers of 2^31 and e^0.5, then the series. */
internal fun Ratpak.lograt0(px: Rat, precision: Int) {
    if (ratLe(px, ratZero, precision)) ratpakError(CalcErr.DOMAIN)
    val fneglog = ratLt(px, ratOne, precision)
    if (fneglog) {
        val t = px.pp
        px.pp = px.pq
        px.pq = t
    }
    val pwr: Rat
    if (logRat2(px) > 1) {
        val intpwr = logRat2(px) - 1
        px.pq = px.pq.withExp(px.pq.exp + intpwr)
        pwr = i32torat(intpwr * BASEXPWR)
        mulrat(pwr, lnTwo, precision)
        // ln(x+e)-ln(x) looks close to e when x is close to one: trim past precision digits+1.
        trimtop(px, precision)
    } else {
        pwr = ratZero.copy()
    }
    val offset = ratZero.copy()
    while (ratGt(px, eToOneHalf, precision)) {
        divrat(px, eToOneHalf, precision)
        addratRaw(offset, ratOne, precision)
    }
    lograt00(px, precision)
    divrat(offset, ratTwo, precision)
    addratRaw(pwr, offset, precision)
    addratRaw(px, pwr, precision)
    trimit(px, precision)
    if (fneglog) px.pp = px.pp.withSign(px.pp.sign * -1)
}

/** exp.cpp lograt: ln with the snap to zero. */
internal fun Ratpak.lograt(px: Rat, precision: Int) {
    val a = px.copy()
    lograt0(px, precision)
    snaprat(px, a, null, precision)
}

private fun Ratpak.isEven(x: Rat, radix: Int, precision: Int): Boolean {
    val tmp = x.copy()
    divrat(tmp, ratTwo, precision)
    fracrat(tmp, radix, precision)
    addratRaw(tmp, tmp, precision)
    subratRaw(tmp, ratOne, precision)
    return ratLt(tmp, ratZero, precision)
}

/** exp.cpp powrat: x^y through numerator and denominator first, falling back to powratcomp. */
internal fun Ratpak.powrat(px: Rat, y: Rat, radix: Int, precision: Int) {
    if (zerrat(px) || zerrat(y)) {
        powratcomp(px, y, radix, precision)
        return
    }
    if (ratEqu(y, ratOne, precision)) return
    try {
        powratNumeratorDenominator(px, y, radix, precision)
    } catch (e: RatpakException) {
        // "fall back to the less accurate method of passing in the original y"
        powratcomp(px, y, radix, precision)
    }
}

/** exp.cpp powratNumeratorDenominator: x^(n/d) = (x^n)^(1/d), preferring an exact integer root when one exists. */
internal fun Ratpak.powratNumeratorDenominator(px: Rat, y: Rat, radix: Int, precision: Int) {
    val yNumerator = Rat(y.pp, ratZero.pq)
    val yDenominator = Rat(y.pq, ratZero.pq)
    val pxPow = px.copy()
    if (!ratEqu(yNumerator, ratOne, precision)) {
        powratcomp(pxPow, yNumerator, radix, precision)
    }
    if (!ratEqu(yDenominator, ratOne, precision)) {
        val oneoveryDenom = ratOne.copy()
        divrat(oneoveryDenom, yDenominator, precision)
        val originalResult = pxPow.copy()
        powratcomp(originalResult, oneoveryDenom, radix, precision)
        val roundedResult = originalResult.copy()
        if (roundedResult.pp.sign == -1) {
            subratRaw(roundedResult, ratHalf, precision)
        } else {
            addratRaw(roundedResult, ratHalf, precision)
        }
        intrat(roundedResult, radix, precision)
        val roundedPower = roundedResult.copy()
        powratcomp(roundedPower, yDenominator, radix, precision)
        if (ratEqu(roundedPower, pxPow, precision)) {
            px.set(roundedResult)
        } else {
            px.set(originalResult)
        }
    } else {
        px.set(pxPow)
    }
}

/** exp.cpp powratcomp: the general power, with the integer-power path, the odd-root sign rule and the size limits. */
internal fun Ratpak.powratcomp(px: Rat, y: Rat, radix: Int, precision: Int) {
    var sign = sign(px)
    px.pp = px.pp.withSign(1)
    px.pq = px.pq.withSign(1)
    if (zerrat(px)) {
        if (ratLt(y, ratZero, precision)) {
            ratpakError(CalcErr.DOMAIN)
        } else if (zerrat(y)) {
            px.set(ratOne)
            sign = 1
        }
    } else {
        val pxint = px.copy()
        subratRaw(pxint, ratOne, precision)
        if (ratGt(pxint, ratNegSmallest, precision) && ratLt(pxint, ratSmallest, precision) && sign == 1) {
            // *px is one, special case a 1 return.
            px.set(ratOne)
            sign = 1
        } else {
            val podd = y.copy()
            fracrat(podd, radix, precision)
            if (ratGt(podd, ratNegSmallest, precision) && ratLt(podd, ratSmallest, precision)) {
                // If power is an integer let ratpowi32 deal with it.
                val iy = y.copy()
                subratRaw(iy, podd, precision)
                val inty = rattoi32(iy, radix, precision)
                val plnx = px.copy()
                lograt0(plnx, precision)
                mulrat(plnx, iy, precision)
                if (ratGt(plnx, ratMaxExp, precision) || ratLt(plnx, ratMinExp, precision)) {
                    // Don't attempt exp of anything large or small.
                    ratpakError(CalcErr.DOMAIN)
                }
                ratpowi32(px, inty, precision)
                if ((inty and 1) == 0) sign = 1
            } else {
                // power is a fraction
                if (sign == -1) {
                    // An even denominator (after dividing out common factors of 2) has no real root.
                    val pNumerator = Rat(y.pp.withSign(1), ratZero.pq)
                    val pDenominator = Rat(y.pq.withSign(1), ratZero.pq)
                    while (isEven(pNumerator, radix, precision) && isEven(pDenominator, radix, precision)) {
                        divrat(pNumerator, ratTwo, precision)
                        divrat(pDenominator, ratTwo, precision)
                    }
                    val fBadExponent = isEven(pDenominator, radix, precision)
                    if (isEven(pNumerator, radix, precision)) sign = 1
                    if (fBadExponent) ratpakError(CalcErr.DOMAIN)
                } else {
                    sign = 1
                }
                lograt0(px, precision)
                mulrat(px, y, precision)
                exprat(px, radix, precision)
            }
        }
    }
    px.pp = px.pp.withSign(px.pp.sign * sign)
}

// ------------------------------------------------------------------ fact.cpp

/** fact.cpp _gamma: the gamma-function series used for non-integer factorials. */
private fun Ratpak.gamma(pn: Rat, radix: Int, precision0: Int) {
    var precision = precision0
    val ratprec = i32torat(precision)
    // Find the best 'A' for convergence to the required precision.
    val a = i32torat(radix)
    lograt0(a, precision)
    mulrat(a, ratprec, precision)
    // Really is -ln(n)+1, but -ln(n) will be < 1 if we scale n between 0.5 and 1.5
    addratRaw(a, ratTwo, precision)
    var tmp = a.copy()
    lograt0(tmp, precision)
    mulrat(tmp, pn, precision)
    addratRaw(a, tmp, precision)
    addratRaw(a, ratOne, precision)

    // precision += ln(exp(a)*pow(a,n+1.5))-ln(radix)
    tmp = pn.copy()
    val onePtFive = i32torat(3)
    divrat(onePtFive, ratTwo, precision)
    addratRaw(tmp, onePtFive, precision)
    var term = a.copy()
    powratcomp(term, tmp, radix, precision)
    tmp = a.copy()
    exprat(tmp, radix, precision)
    mulrat(term, tmp, precision)
    lograt0(term, precision)
    val ratRadix = i32torat(radix)
    tmp = ratRadix.copy()
    lograt0(tmp, precision)
    subratRaw(term, tmp, precision)
    precision += rattoi32(term, radix, precision)

    val factorial = ratOne.copy()
    var count = i32tonum(0, BASEX)
    val mpy = a.copy()
    powratcomp(mpy, pn, radix, precision)
    val a2 = a.copy()
    mulrat(a2, a, precision)
    // sum=(1/n)-(a/(n+1))
    val sum = ratOne.copy()
    divrat(sum, pn, precision)
    tmp = pn.copy()
    addratRaw(tmp, ratOne, precision)
    term = a.copy()
    divrat(term, tmp, precision)
    subratRaw(sum, term, precision)

    val err = ratRadix.copy()
    ratprec.pp = ratprec.pp.withSign(ratprec.pp.sign * -1)
    powratcomp(err, ratprec, radix, precision)
    divrat(err, ratRadix, precision)

    term = ratTwo.copy()
    while (!zerrat(term) && ratGt(term, err, precision)) {
        addratRaw(pn, ratTwo, precision)
        count = inc(count)
        factorial.pp = mulnumx(factorial.pp, count)
        count = inc(count)
        factorial.pp = mulnumx(factorial.pp, count)
        divrat(factorial, a2, precision)
        tmp = pn.copy()
        addratRaw(tmp, ratOne, precision)
        term = Rat(count, numOne)
        addratRaw(term, ratOne, precision)
        mulrat(term, tmp, precision)
        tmp = a.copy()
        divrat(tmp, term, precision)
        term = ratOne.copy()
        divrat(term, pn, precision)
        subratRaw(term, tmp, precision)
        divrat(term, factorial, precision)
        addratRaw(sum, term, precision)
        term = abs(term)
    }
    mulrat(sum, mpy, precision)
    pn.set(sum)
}

/** fact.cpp factrat: x! for -1000 <= x <= 3248 (integers exactly, others through the gamma series). */
internal fun Ratpak.factrat(px: Rat, radix: Int, precision: Int) {
    if (ratGt(px, ratMaxFact, precision) || ratLt(px, ratMinFact, precision)) {
        // Don't attempt factorial of anything too large or small.
        ratpakError(CalcErr.OVERFLOW)
    }
    val fact = ratOne.copy()
    val negRatOne = ratOne.copy()
    negRatOne.pp = negRatOne.pp.withSign(negRatOne.pp.sign * -1)
    val frac = px.copy()
    fracrat(frac, radix, precision)
    // Check for negative integers and throw an error.
    if ((zerrat(frac) || (logRatRadix(frac) <= -precision)) && (sign(px) == -1)) {
        ratpakError(CalcErr.DOMAIN)
    }
    while (ratGt(px, ratZero, precision) && (logRatRadix(px) > -precision)) {
        mulrat(fact, px, precision)
        subratRaw(px, ratOne, precision)
    }
    // Added to make numbers 'close enough' to integers use integer factorial.
    if (logRatRadix(px) <= -precision) {
        px.set(ratZero)
        intrat(fact, radix, precision)
    }
    while (ratLt(px, negRatOne, precision)) {
        addratRaw(px, ratOne, precision)
        divrat(fact, px, precision)
    }
    if (ratNeq(px, ratZero, precision)) {
        addratRaw(px, ratOne, precision)
        gamma(px, radix, precision)
        mulrat(px, fact, precision)
    } else {
        px.set(fact)
    }
}

// ------------------------------------------------------------------ trans.cpp

private fun Ratpak.scalerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    when (angletype) {
        AngleType.Radians -> scale2pi(pa, radix, precision)
        AngleType.Degrees -> scale(pa, rat360, radix, precision)
        AngleType.Gradians -> scale(pa, rat400, radix, precision)
    }
}

/** Both _sinrat and _cosrat end by clamping to [-1, 1] and snapping |x| <= rat_smallest to zero. */
private fun Ratpak.clampAndSnapUnit(px: Rat, precision: Int) {
    inbetween(px, ratOne, precision)
    if (ratLe(px, ratSmallest, precision) && ratGe(px, ratNegSmallest, precision)) {
        px.set(ratZero)
    }
}

private fun Ratpak.sinrat0(px: Rat, precision: Int) {
    val xx = taylorXX(px, precision)
    val pret = px.copy()
    val thisterm = px.copy()
    var n2 = numOne
    xx.pp = xx.pp.withSign(xx.pp.sign * -1)
    do {
        mulrat(thisterm, xx, precision)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
    clampAndSnapUnit(px, precision)
}

internal fun Ratpak.sinanglerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    scalerat(pa, angletype, radix, precision)
    when (angletype) {
        AngleType.Degrees -> {
            if (ratGt(pa, rat180, precision)) subratRaw(pa, rat360, precision)
            divrat(pa, rat180, precision)
            mulrat(pa, pi, precision)
        }
        AngleType.Gradians -> {
            if (ratGt(pa, rat200, precision)) subratRaw(pa, rat400, precision)
            divrat(pa, rat200, precision)
            mulrat(pa, pi, precision)
        }
        AngleType.Radians -> Unit
    }
    sinrat0(pa, precision)
}

private fun Ratpak.cosrat0(px: Rat, radix: Int, precision: Int) {
    val xx = taylorXX(px, precision)
    val pret = Rat(i32tonum(1, radix.toLong()), i32tonum(1, radix.toLong()))
    val thisterm = pret.copy()
    var n2 = i32tonum(0, radix.toLong())
    xx.pp = xx.pp.withSign(xx.pp.sign * -1)
    do {
        mulrat(thisterm, xx, precision)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
    clampAndSnapUnit(px, precision)
}

internal fun Ratpak.cosanglerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    scalerat(pa, angletype, radix, precision)
    when (angletype) {
        AngleType.Degrees -> {
            if (ratGt(pa, rat180, precision)) {
                val ptmp = rat360.copy()
                subratRaw(ptmp, pa, precision)
                pa.set(ptmp)
            }
            divrat(pa, rat180, precision)
            mulrat(pa, pi, precision)
        }
        AngleType.Gradians -> {
            if (ratGt(pa, rat200, precision)) {
                val ptmp = rat400.copy()
                subratRaw(ptmp, pa, precision)
                pa.set(ptmp)
            }
            divrat(pa, rat200, precision)
            mulrat(pa, pi, precision)
        }
        AngleType.Radians -> Unit
    }
    cosrat0(pa, radix, precision)
}

private fun Ratpak.tanrat0(px: Rat, radix: Int, precision: Int) {
    val ptmp = px.copy()
    sinrat0(px, precision)
    cosrat0(ptmp, radix, precision)
    if (zerrat(ptmp)) ratpakError(CalcErr.DOMAIN)
    divrat(px, ptmp, precision)
}

internal fun Ratpak.tananglerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    scalerat(pa, angletype, radix, precision)
    when (angletype) {
        AngleType.Degrees -> {
            if (ratGt(pa, rat180, precision)) subratRaw(pa, rat180, precision)
            divrat(pa, rat180, precision)
            mulrat(pa, pi, precision)
        }
        AngleType.Gradians -> {
            if (ratGt(pa, rat200, precision)) subratRaw(pa, rat200, precision)
            divrat(pa, rat200, precision)
            mulrat(pa, pi, precision)
        }
        AngleType.Radians -> Unit
    }
    tanrat0(pa, radix, precision)
}

// ------------------------------------------------------------------ itrans.cpp

private fun Ratpak.ascalerat(pa: Rat, angletype: AngleType, precision: Int) {
    when (angletype) {
        AngleType.Radians -> Unit
        AngleType.Degrees -> {
            divrat(pa, twoPi, precision)
            mulrat(pa, rat360, precision)
        }
        AngleType.Gradians -> {
            divrat(pa, twoPi, precision)
            mulrat(pa, rat400, precision)
        }
    }
}

private fun Ratpak.asinrat0(px: Rat, precision: Int) {
    val xx = taylorXX(px, precision)
    val pret = px.copy()
    val thisterm = px.copy()
    var n2 = numOne
    do {
        mulrat(thisterm, xx, precision)
        thisterm.pp = mulnumx(thisterm.pp, n2)
        thisterm.pp = mulnumx(thisterm.pp, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
}

internal fun Ratpak.asinanglerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    asinrat(pa, radix, precision)
    ascalerat(pa, angletype, precision)
}

/** itrans.cpp asinrat: the series below 0.85, pi/2 - asin(sqrt(1-x^2)) above; |x| > 1 is a domain error. */
internal fun Ratpak.asinrat(px: Rat, radix: Int, precision: Int) {
    val sgn = sign(px)
    px.pp = px.pp.withSign(1)
    px.pq = px.pq.withSign(1)
    // Avoid the really bad part of the asin curve near +/-1.
    val phack = px.copy()
    subratRaw(phack, ratOne, precision)
    if (ratLe(phack, ratSmallest, precision) && ratGe(phack, ratNegSmallest, precision)) {
        px.set(piOverTwo)
    } else {
        if (ratGt(px, ptEightFive, precision)) {
            if (ratGt(px, ratOne, precision)) {
                subratRaw(px, ratOne, precision)
                if (ratGt(px, ratSmallest, precision)) {
                    ratpakError(CalcErr.DOMAIN)
                } else {
                    px.set(ratOne)
                }
            }
            val pret = px.copy()
            mulrat(px, pret, precision)
            px.pp = px.pp.withSign(px.pp.sign * -1)
            addratRaw(px, ratOne, precision)
            rootrat(px, ratTwo, radix, precision)
            asinrat0(px, precision)
            px.pp = px.pp.withSign(px.pp.sign * -1)
            addratRaw(px, piOverTwo, precision)
        } else {
            asinrat0(px, precision)
        }
    }
    px.pp = px.pp.withSign(sgn)
    px.pq = px.pq.withSign(1)
}

internal fun Ratpak.acosanglerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    acosrat(pa, radix, precision)
    ascalerat(pa, angletype, precision)
}

internal fun Ratpak.acosrat(px: Rat, radix: Int, precision: Int) {
    val sgn = sign(px)
    px.pp = px.pp.withSign(1)
    px.pq = px.pq.withSign(1)
    if (ratEqu(px, ratOne, precision)) {
        if (sgn == -1) px.set(pi) else px.set(ratZero)
    } else {
        px.pp = px.pp.withSign(sgn)
        asinrat(px, radix, precision)
        px.pp = px.pp.withSign(px.pp.sign * -1)
        addratRaw(px, piOverTwo, precision)
    }
}

internal fun Ratpak.atananglerat(pa: Rat, angletype: AngleType, radix: Int, precision: Int) {
    atanrat(pa, radix, precision)
    ascalerat(pa, angletype, precision)
}

private fun Ratpak.atanrat0(px: Rat, precision: Int) {
    val xx = taylorXX(px, precision)
    val pret = px.copy()
    val thisterm = px.copy()
    var n2 = numOne
    xx.pp = xx.pp.withSign(xx.pp.sign * -1)
    do {
        mulrat(thisterm, xx, precision)
        thisterm.pp = mulnumx(thisterm.pp, n2)
        n2 = inc(n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
}

internal fun Ratpak.atanrat(px: Rat, radix: Int, precision: Int) {
    val sgn = sign(px)
    px.pp = px.pp.withSign(1)
    px.pq = px.pq.withSign(1)
    if (ratGt(px, ptEightFive, precision)) {
        if (ratGt(px, ratTwo, precision)) {
            px.pp = px.pp.withSign(sgn)
            px.pq = px.pq.withSign(1)
            val tmpx = ratOne.copy()
            divrat(tmpx, px, precision)
            atanrat0(tmpx, precision)
            tmpx.pp = tmpx.pp.withSign(sgn)
            tmpx.pq = tmpx.pq.withSign(1)
            px.set(piOverTwo)
            subratRaw(px, tmpx, precision)
        } else {
            px.pp = px.pp.withSign(sgn)
            val tmpx = px.copy()
            mulrat(tmpx, px, precision)
            addratRaw(tmpx, ratOne, precision)
            rootrat(tmpx, ratTwo, radix, precision)
            divrat(px, tmpx, precision)
            asinrat(px, radix, precision)
            px.pp = px.pp.withSign(sgn)
            px.pq = px.pq.withSign(1)
        }
    } else {
        px.pp = px.pp.withSign(sgn)
        px.pq = px.pq.withSign(1)
        atanrat0(px, precision)
    }
    if (ratGt(px, piOverTwo, precision)) {
        subratRaw(px, pi, precision)
    }
}

// ------------------------------------------------------------------ transh.cpp

private fun Ratpak.isValidForHypFunc(px: Rat, precision: Int): Boolean {
    val ptmp = ratMinExp.copy()
    divrat(ptmp, ratTen, precision)
    return !ratLt(px, ptmp, precision)
}

private fun Ratpak.sinhrat0(px: Rat, precision: Int) {
    if (!isValidForHypFunc(px, precision)) ratpakError(CalcErr.DOMAIN)
    val xx = taylorXX(px, precision)
    val pret = px.copy()
    val thisterm = pret.copy()
    var n2 = numOne
    do {
        mulrat(thisterm, xx, precision)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
}

internal fun Ratpak.sinhrat(px: Rat, radix: Int, precision: Int) {
    if (ratGe(px, ratOne, precision)) {
        val tmpx = px.copy()
        exprat(px, radix, precision)
        tmpx.pp = tmpx.pp.withSign(tmpx.pp.sign * -1)
        exprat(tmpx, radix, precision)
        subratRaw(px, tmpx, precision)
        divrat(px, ratTwo, precision)
    } else {
        sinhrat0(px, precision)
    }
}

private fun Ratpak.coshrat0(px: Rat, radix: Int, precision: Int) {
    if (!isValidForHypFunc(px, precision)) ratpakError(CalcErr.DOMAIN)
    val xx = taylorXX(px, precision)
    val pret = Rat(i32tonum(1, radix.toLong()), i32tonum(1, radix.toLong()))
    val thisterm = pret.copy()
    var n2 = i32tonum(0, radix.toLong())
    do {
        mulrat(thisterm, xx, precision)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        n2 = inc(n2)
        thisterm.pq = mulnumx(thisterm.pq, n2)
        addratRaw(pret, thisterm, precision)
    } while (!smallEnoughRat(thisterm, precision))
    destroyTaylor(px, pret, precision)
}

internal fun Ratpak.coshrat(px: Rat, radix: Int, precision: Int) {
    px.pp = px.pp.withSign(1)
    px.pq = px.pq.withSign(1)
    if (ratGe(px, ratOne, precision)) {
        val tmpx = px.copy()
        exprat(px, radix, precision)
        tmpx.pp = tmpx.pp.withSign(tmpx.pp.sign * -1)
        exprat(tmpx, radix, precision)
        addratRaw(px, tmpx, precision)
        divrat(px, ratTwo, precision)
    } else {
        coshrat0(px, radix, precision)
    }
    // Since *px might be epsilon below 1 due to TRIMIT we need this trick here.
    if (ratLt(px, ratOne, precision)) px.set(ratOne)
}

internal fun Ratpak.tanhrat(px: Rat, radix: Int, precision: Int) {
    val ptmp = px.copy()
    sinhrat(px, radix, precision)
    coshrat(ptmp, radix, precision)
    px.pp = mulnumx(px.pp, ptmp.pq)
    px.pq = mulnumx(px.pq, ptmp.pp)
}

// ------------------------------------------------------------------ itransh.cpp

internal fun Ratpak.asinhrat(px: Rat, radix: Int, precision: Int) {
    val negPtEightFive = negated(ptEightFive)
    if (ratGt(px, ptEightFive, precision) || ratLt(px, negPtEightFive, precision)) {
        val ptmp = px.copy()
        mulrat(ptmp, px, precision)
        addratRaw(ptmp, ratOne, precision)
        rootrat(ptmp, ratTwo, radix, precision)
        addratRaw(px, ptmp, precision)
        lograt0(px, precision)
    } else {
        val xx = taylorXX(px, precision)
        xx.pp = xx.pp.withSign(xx.pp.sign * -1)
        val pret = px.copy()
        val thisterm = px.copy()
        var n2 = numOne
        do {
            mulrat(thisterm, xx, precision)
            thisterm.pp = mulnumx(thisterm.pp, n2)
            thisterm.pp = mulnumx(thisterm.pp, n2)
            n2 = inc(n2)
            thisterm.pq = mulnumx(thisterm.pq, n2)
            n2 = inc(n2)
            thisterm.pq = mulnumx(thisterm.pq, n2)
            addratRaw(pret, thisterm, precision)
        } while (!smallEnoughRat(thisterm, precision))
        destroyTaylor(px, pret, precision)
    }
}

internal fun Ratpak.acoshrat(px: Rat, radix: Int, precision: Int) {
    if (ratLt(px, ratOne, precision)) ratpakError(CalcErr.DOMAIN)
    val ptmp = px.copy()
    mulrat(ptmp, px, precision)
    subratRaw(ptmp, ratOne, precision)
    rootrat(ptmp, ratTwo, radix, precision)
    addratRaw(px, ptmp, precision)
    lograt0(px, precision)
}

internal fun Ratpak.atanhrat(px: Rat, precision: Int) {
    val ptmp = px.copy()
    subratRaw(ptmp, ratOne, precision)
    addratRaw(px, ratOne, precision)
    divrat(px, ptmp, precision)
    px.pp = px.pp.withSign(px.pp.sign * -1)
    lograt0(px, precision)
    divrat(px, ratTwo, precision)
}

// ------------------------------------------------------------------ logic.cpp

internal fun Ratpak.lshrat(pa: Rat, b: Rat, radix: Int, precision: Int) {
    intrat(pa, radix, precision)
    if (!zernum(pa.pp)) {
        if (ratGt(b, ratMaxExp, precision)) ratpakError(CalcErr.DOMAIN)
        val intb = rattoi32(b, radix, precision)
        val pwr = ratTwo.copy()
        ratpowi32(pwr, intb, precision)
        mulrat(pa, pwr, precision)
    }
}

internal fun Ratpak.rshrat(pa: Rat, b: Rat, radix: Int, precision: Int) {
    intrat(pa, radix, precision)
    if (!zernum(pa.pp)) {
        if (ratLt(b, ratMinExp, precision)) ratpakError(CalcErr.DOMAIN)
        val intb = rattoi32(b, radix, precision)
        val pwr = ratTwo.copy()
        ratpowi32(pwr, intb, precision)
        divrat(pa, pwr, precision)
    }
}

private enum class BoolFunc { AND, OR, XOR }

private fun Ratpak.boolrat(pa: Rat, b: Rat, func: BoolFunc, radix: Int, precision: Int) {
    intrat(pa, radix, precision)
    val tmp = b.copy()
    intrat(tmp, radix, precision)
    pa.pp = boolnum(pa.pp, tmp.pp, func)
}

/** logic.cpp boolnum: digit-aligned AND / OR / XOR of the magnitudes; the sign is a's. */
private fun boolnum(a: Num, b: Num, func: BoolFunc): Num {
    val mexp = minOf(a.exp, b.exp)
    val va = scaleUp(a.mant, BASEX, a.exp - mexp)
    val vb = scaleUp(b.mant, BASEX, b.exp - mexp)
    val c = when (func) {
        BoolFunc.AND -> va.and(vb)
        BoolFunc.OR -> va.or(vb)
        BoolFunc.XOR -> va.xor(vb)
    }
    return Num.of(a.sign, mexp, c, BASEX)
}

internal fun Ratpak.andrat(pa: Rat, b: Rat, radix: Int, precision: Int) = boolrat(pa, b, BoolFunc.AND, radix, precision)
internal fun Ratpak.orrat(pa: Rat, b: Rat, radix: Int, precision: Int) = boolrat(pa, b, BoolFunc.OR, radix, precision)
internal fun Ratpak.xorrat(pa: Rat, b: Rat, radix: Int, precision: Int) = boolrat(pa, b, BoolFunc.XOR, radix, precision)

/** logic.cpp remrat: the remainder with the dividend's sign (remrat(x, 0) is "Result is undefined"). */
internal fun Ratpak.remrat(pa: Rat, b: Rat) {
    if (zerrat(b)) ratpakError(CalcErr.INDEFINITE)
    val tmp = b.copy()
    pa.pp = mulnumx(pa.pp, tmp.pq)
    tmp.pp = mulnumx(tmp.pp, pa.pq)
    pa.pp = remnum(pa.pp, tmp.pp, BASEX)
    pa.pq = mulnumx(pa.pq, tmp.pq)
    renormalize(pa)
}

/** logic.cpp modrat: the modulus with the divisor's sign (modrat(x, 0) is x). */
internal fun Ratpak.modrat(pa: Rat, b: Rat) {
    if (zerrat(b)) return
    val tmp = b.copy()
    val needAdjust = if (sign(pa) == -1) sign(b) == 1 else sign(b) == -1
    pa.pp = mulnumx(pa.pp, tmp.pq)
    tmp.pp = mulnumx(tmp.pp, pa.pq)
    pa.pp = remnum(pa.pp, tmp.pp, BASEX)
    pa.pq = mulnumx(pa.pq, tmp.pq)
    if (needAdjust && !zerrat(pa)) {
        // The C code passes BASEX (0x80000000) as the precision, i.e. INT32_MIN.
        addratRaw(pa, b, Int.MIN_VALUE)
    }
    renormalize(pa)
}

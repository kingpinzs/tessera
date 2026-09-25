package app.tileshell.calc.engine

/** CalcEngine.h NUM_WIDTH, in IDM_QWORD order. */
internal enum class NumWidth(val bits: Int) { QWORD(64), DWORD(32), WORD(16), BYTE(8) }

/**
 * scidisp.cpp's `static LASTDISP gldPrevious`: DisplayNum only re-sends the display when something changed since the
 * last call. It is a file static in C, i.e. shared by the three engines of one CalculatorManager, so it lives beside
 * the shared ratpak state.
 */
internal class LastDisp {
    var value: Rational = Rational.of(0)
    var precision = -1
    var radix = 0
    var nFE = -1
    var numwidth: NumWidth? = null
    var fIntMath = false
    var bRecord = false
    var bUseSep = false
}

/** What the three engines of one Calculator share: ratpak's globals and DisplayNum's cache. */
internal class EngineGlobals {
    val rp = Ratpak()
    val rm = RationalMath(rp)
    val lastDisp = LastDisp()
}

/**
 * Port of CCalcEngine: CEngine/calc.cpp (construction, chop numbers), scicomm.cpp (command handling, precedence,
 * parentheses, =, memory, history hooks), scioper.cpp (binary operations), scifunc.cpp (unary functions, errors),
 * scidisp.cpp (display, grouping, Overflow check) and sciset.cpp (radix and word size).
 */
internal class CalcEngine(
    private val fPrecedence: Boolean,
    val fIntegerMode: Boolean,
    private val g: EngineGlobals,
    private val calcDisplay: CalcDisplay?,
    historyDisplay: HistoryDisplay?,
) {
    private val rp get() = g.rp
    private val rm get() = g.rm

    private var nOpCode = 0
    private var nPrevOpCode = 0
    private var bChangeOp = false
    private var bRecord = false
    private var bSetCalcState = false
    private val input = CalcInput(DEFAULT_DEC_SEPARATOR)
    var nFE = NumberFormat.Float
        private set
    private var memoryValue: Rational? = Rational.ZERO
    private var holdVal = Rational.ZERO
    var currentVal = Rational.ZERO
        private set
    private var lastVal = Rational.ZERO
    private val parenVals = Array(MAXPRECDEPTH) { Rational.ZERO }
    private val precedenceVals = Array(MAXPRECDEPTH) { Rational.ZERO }
    var bError = false
        private set
    var bInv = false
        private set
    private var bNoPrevEqu = true
    var radix = DEFAULT_RADIX
        private set
    var precision = DEFAULT_PRECISION
        private set
    private var cIntDigitsSav = DEFAULT_MAX_DIGITS
    private var decGrouping: List<Int> = emptyList()
    var numberString = DEFAULT_NUMBER_STR
        private set
    private var nTempCom = 0
    var openParenCount = 0
        private set
    private val nOp = IntArray(MAXPRECDEPTH)
    private val nPrecOp = IntArray(MAXPRECDEPTH)
    private var precedenceOpCount = 0
    private var nLastCom = 0
    var angletype = AngleType.Degrees
        private set
    var numwidth = NumWidth.QWORD
        private set
    var dwWordBitWidth = 64
        private set
    private var carryBit = 0L
    private val historyCollector = HistoryCollector(calcDisplay, historyDisplay, DEFAULT_DEC_SEPARATOR)
    private val chopNumbers = arrayOfNulls<Rational>(4)
    private val maxDecimalValueStrings = arrayOfNulls<String>(4)
    private var decimalSeparator = '\u0000'
    private var groupSeparator = DEFAULT_GRP_SEPARATOR
    private val maxTrigonometricNum: Rational

    /** The ratpak error code of the error on display (for diagnostics), or 0. */
    var lastErrorCode = 0
        private set

    init {
        initChopNumbers()
        dwWordBitWidth = dwWordBitWidthFromNumWidth(numwidth)
        maxTrigonometricNum = rm.pow(Rational.of(10), Rational.of(100))
        setRadixTypeAndNumWidth(RADIX_DECIMAL, numwidth)
        settingsChanged()
        displayNum()
    }

    private fun initChopNumbers() {
        chopNumbers[0] = Rational.of(rp.ratQword)
        chopNumbers[1] = Rational.of(rp.ratDword)
        chopNumbers[2] = Rational.of(rp.ratWord)
        chopNumbers[3] = Rational.of(rp.ratByte)
        for (i in 0 until 4) {
            var maxVal = rm.div(chopNumbers[i]!!, Rational.of(2))
            maxVal = rm.integer(maxVal)
            maxDecimalValueStrings[i] = rm.toString(maxVal, 10, NumberFormat.Float, precision)
        }
    }

    fun getChopNumber(): Rational = chopNumbers[numwidth.ordinal]!!
    private fun getMaxDecimalValueString(): String = maxDecimalValueStrings[numwidth.ordinal]!!

    /** CCalcEngine::PersistedMemObject() getter: moves the value out. */
    fun takePersistedMemObject(): Rational? {
        val v = memoryValue
        memoryValue = null
        return v
    }

    /** CCalcEngine::PersistedMemObject(Rational) setter. */
    fun setPersistedMemObject(v: Rational) {
        memoryValue = v
    }

    fun isInputEmpty(): Boolean = input.isEmpty() && (numberString.isEmpty() || numberString == "0")

    fun inRecordingState(): Boolean = bRecord

    fun changePrecision(p: Int) {
        precision = p
        rp.changeConstants(radix, p)
    }

    private fun settingsChanged() {
        val lastDec = decimalSeparator
        decimalSeparator = DEFAULT_DEC_SEPARATOR
        rp.decimalSeparator = decimalSeparator
        val lastSep = groupSeparator
        groupSeparator = DEFAULT_GRP_SEPARATOR
        val lastDecGrouping = decGrouping
        decGrouping = digitGroupingStringToGroupingVector(DEFAULT_GRP_STR)
        var numChanged = decGrouping != lastDecGrouping || groupSeparator != lastSep
        if (decimalSeparator != lastDec) {
            input.setDecimalSymbol(decimalSeparator)
            historyCollector.setDecimalSymbol(decimalSeparator)
            numChanged = true
        }
        if (numChanged) displayNum()
    }

    // ------------------------------------------------------------------ scicomm.cpp

    private fun handleErrorCommand(idc: Int) {
        if (!Op.isGuiSettingOpCode(idc)) {
            // We would have saved the prev command. Need to forget this state
            nTempCom = nLastCom
        }
    }

    private fun handleMaxDigitsReached() {
        calcDisplay?.maxDigitsReached()
    }

    private fun clearTemporaryValues() {
        bInv = false
        input.clear()
        bRecord = true
        checkAndAddLastBinOpToHistory()
        displayNum()
        bError = false
        lastErrorCode = 0
    }

    private fun clearDisplay() {
        calcDisplay?.setExpressionDisplay(emptyList())
    }

    fun processCommand(wParam0: Int) {
        var wParam = wParam0
        if (wParam == Op.SET_RESULT) {
            wParam = Op.RECALL
            bSetCalcState = true
        }
        processCommandWorker(wParam)
    }

    private fun processCommandWorker(wParam0: Int) {
        var wParam = wParam0
        if (!Op.isGuiSettingOpCode(wParam)) {
            nLastCom = nTempCom
            nTempCom = wParam
        }

        // Clear expression shown after = sign, when user do any action.
        if (!bNoPrevEqu) clearDisplay()

        if (bError) {
            if (wParam == Op.CLEAR) {
                // handle "C" normally
            } else if (wParam == Op.CENTR) {
                // treat "CE" as "C"
                wParam = Op.CLEAR
            } else {
                handleErrorCommand(wParam)
                return
            }
        }

        // Toggle Record/Display mode if appropriate.
        if (bRecord) {
            if (Op.isBinOpCode(wParam) || Op.isUnaryOpCode(wParam) || Op.inRange(wParam, Op.FE, Op.MMINUS) ||
                Op.inRange(wParam, Op.OPENP, Op.CLOSEP) || Op.inRange(wParam, Op.HEX, Op.BIN) ||
                Op.inRange(wParam, Op.QWORD, Op.BYTE) || Op.inRange(wParam, Op.DEG, Op.GRAD) ||
                Op.inRange(wParam, Op.BINEDITSTART, Op.BINEDITEND) || wParam == Op.INV ||
                (wParam == Op.SIGN && radix != 10) || wParam == Op.RAND || wParam == Op.EULER
            ) {
                bRecord = false
                currentVal = input.toRational(rp, radix, precision)
                displayNum() // Causes 3.000 to shrink to 3. on first op.
            }
        } else if (Op.isDigitOpCode(wParam) || wParam == Op.PNT) {
            bRecord = true
            input.clear()
            // An equation with input after a closing parenthesis ("(8)2=16") is not ended here.
            if (nLastCom != Op.CLOSEP) checkAndAddLastBinOpToHistory()
        }

        // Interpret digit keys.
        if (Op.isDigitOpCode(wParam)) {
            val iValue = wParam - Op.D0
            if (iValue >= radix) {
                handleErrorCommand(wParam)
                return
            }
            if (!input.tryAddDigit(iValue, radix, fIntegerMode, getMaxDecimalValueString(), dwWordBitWidth, cIntDigitsSav)) {
                handleErrorCommand(wParam)
                handleMaxDigitsReached()
                return
            }
            if (nLastCom == Op.CLOSEP) implicitMultiplyAfterCloseParen()
            displayNum()
            return
        }

        // BINARY OPERATORS:
        if (Op.isBinOpCode(wParam)) {
            // Change the operation if last input was operation.
            if (Op.isBinOpCode(nLastCom)) {
                var fPrecInvToHigher = false
                nOpCode = wParam
                // "1 * 2 + " changed to "^" must become "(1 * 2) ^".
                if (fPrecedence && nPrevOpCode != 0) {
                    val nPrev = precedenceOfOp(nPrevOpCode)
                    val nx = precedenceOfOp(nLastCom)
                    val ni = precedenceOfOp(nOpCode)
                    if (nx <= nPrev && ni > nPrev) {
                        fPrecInvToHigher = true
                        nPrevOpCode = 0
                    }
                }
                historyCollector.changeLastBinOp(nOpCode, fPrecInvToHigher, fIntegerMode)
                displayAnnounceBinaryOperator()
                return
            }

            if (!historyCollector.fOpndAddedToHistory()) {
                // if the prev command was ) or unop then it is already in history as a opnd form (...)
                historyCollector.addOpndToHistory(numberString, currentVal)
            }

            // bChangeOp: an operation was done and currentVal is its result (3+4+5= gives 7 after the second +).
            if (bChangeOp) {
                while (true) { // DoPrecedenceCheckAgain
                    var nx = precedenceOfOp(wParam)
                    val ni = precedenceOfOp(nOpCode)
                    if (nx > ni && fPrecedence) {
                        if (precedenceOpCount < MAXPRECDEPTH) {
                            precedenceVals[precedenceOpCount] = lastVal
                            nPrecOp[precedenceOpCount] = nOpCode
                            historyCollector.pushLastOpndStart()
                        } else {
                            precedenceOpCount = MAXPRECDEPTH - 1
                            handleErrorCommand(wParam)
                        }
                        precedenceOpCount++
                        break
                    } else {
                        currentVal = doOperation(nOpCode, currentVal, lastVal)
                        nPrevOpCode = nOpCode
                        if (!bError) {
                            displayNum()
                            if (!fPrecedence) {
                                val groupedString = groupDigitsPerRadix(numberString, radix)
                                historyCollector.completeEquation(groupedString)
                                historyCollector.addOpndToHistory(numberString, currentVal)
                            }
                        }
                        if (precedenceOpCount != 0 && nPrecOp[precedenceOpCount - 1] != 0) {
                            precedenceOpCount--
                            nOpCode = nPrecOp[precedenceOpCount]
                            lastVal = precedenceVals[precedenceOpCount]
                            nx = precedenceOfOp(nOpCode)
                            // "1 + 2 * Or 3 Or" needs "1 + (2 Or 3)".
                            if (ni <= nx) historyCollector.enclosePrecInversionBrackets()
                            historyCollector.popLastOpndStart()
                            continue
                        }
                        break
                    }
                }
            }

            displayAnnounceBinaryOperator()
            lastVal = currentVal
            nOpCode = wParam
            historyCollector.addBinOpToHistory(nOpCode, fIntegerMode)
            bNoPrevEqu = true
            bChangeOp = true
            return
        }

        // UNARY OPERATORS:
        if (Op.isUnaryOpCode(wParam) || wParam == Op.DEGREES) {
            // After an operator, use the number before it ("5 + 1/x" is 1/5, "5 + =" gives 10).
            if (Op.isBinOpCode(nLastCom)) currentVal = lastVal

            // % is not added to history; its result is.
            if (wParam != Op.PERCENT) {
                if (!historyCollector.fOpndAddedToHistory()) historyCollector.addOpndToHistory(numberString, currentVal)
                historyCollector.addUnaryOpToHistory(wParam, bInv, angletype)
            }

            if (wParam == Op.SIN || wParam == Op.COS || wParam == Op.TAN || wParam == Op.SINH || wParam == Op.COSH ||
                wParam == Op.TANH || wParam == Op.SEC || wParam == Op.CSC || wParam == Op.COT || wParam == Op.SECH ||
                wParam == Op.CSCH || wParam == Op.COTH
            ) {
                if (isCurrentTooBigForTrig()) {
                    currentVal = Rational.of(0)
                    displayError(CalcErr.DOMAIN)
                    return
                }
            }

            currentVal = sciCalcFunctions(currentVal, wParam)
            if (bError) return

            displayNum()

            if (wParam == Op.PERCENT) {
                checkAndAddLastBinOpToHistory()
                historyCollector.addOpndToHistory(numberString, currentVal, true)
            }

            if (bInv && (wParam == Op.CHOP || wParam == Op.SIN || wParam == Op.COS || wParam == Op.TAN || wParam == Op.LN ||
                    wParam == Op.DMS || wParam == Op.DEGREES || wParam == Op.SINH || wParam == Op.COSH || wParam == Op.TANH ||
                    wParam == Op.SEC || wParam == Op.CSC || wParam == Op.COT || wParam == Op.SECH || wParam == Op.CSCH ||
                    wParam == Op.COTH)
            ) {
                bInv = false
            }
            return
        }

        // Tiny binary edit windows clicked. Toggle that bit and update display
        if (Op.inRange(wParam, Op.BINEDITSTART, Op.BINEDITEND)) {
            if (Op.isBinOpCode(nLastCom)) currentVal = lastVal
            checkAndAddLastBinOpToHistory()
            if (tryToggleBit(wParam - Op.BINEDITSTART)) displayNum()
            return
        }

        when (wParam) {
            Op.CLEAR -> {
                if (!bChangeOp) {
                    // Preserve history, if everything done before was a series of unary operations.
                    checkAndAddLastBinOpToHistory(false)
                }
                lastVal = Rational.of(0)
                bChangeOp = false
                openParenCount = 0
                precedenceOpCount = 0
                nTempCom = 0
                nLastCom = 0
                nOpCode = 0
                nPrevOpCode = 0
                bNoPrevEqu = true
                carryBit = 0
                if (calcDisplay != null) {
                    calcDisplay.setParenthesisNumber(0)
                    clearDisplay()
                }
                historyCollector.clearHistoryLine("")
                clearTemporaryValues()
            }

            Op.CENTR -> clearTemporaryValues()

            Op.BACK -> {
                if (bRecord) {
                    input.backspace()
                    displayNum()
                } else {
                    handleErrorCommand(wParam)
                }
            }

            Op.EQU -> {
                while (openParenCount > 0) {
                    if (bError) break
                    // Close all the parentheses automatically.
                    nTempCom = nLastCom
                    processCommand(Op.CLOSEP)
                    nLastCom = nTempCom
                    nTempCom = wParam
                }
                if (!bNoPrevEqu) {
                    // A unary op may have changed the number on screen without changing lastVal.
                    lastVal = currentVal
                }
                // Last thing keyed in was an operator: do the op on a duplicate of the last entry.
                if (Op.isBinOpCode(nLastCom)) currentVal = lastVal
                if (!historyCollector.fOpndAddedToHistory()) historyCollector.addOpndToHistory(numberString, currentVal)

                resolveHighestPrecedenceOperation()
                while (fPrecedence && precedenceOpCount > 0) {
                    precedenceOpCount--
                    nOpCode = nPrecOp[precedenceOpCount]
                    lastVal = precedenceVals[precedenceOpCount]
                    val ni = precedenceOfOp(nPrevOpCode)
                    val nx = precedenceOfOp(nOpCode)
                    if (ni <= nx) historyCollector.enclosePrecInversionBrackets()
                    historyCollector.popLastOpndStart()
                    bNoPrevEqu = true
                    resolveHighestPrecedenceOperation()
                }
                if (!bError) {
                    val groupedString = groupDigitsPerRadix(numberString, radix)
                    historyCollector.completeEquation(groupedString)
                    lastVal = currentVal
                    nPrevOpCode = 0
                    precedenceOpCount = 0
                }
                bChangeOp = false
            }

            Op.OPENP, Op.CLOSEP -> handleParenthesis(wParam)

            Op.HEX, Op.DEC, Op.OCT, Op.BIN -> {
                setRadixTypeAndNumWidth(wParam - Op.HEX, null)
                historyCollector.updateHistoryExpression(rm, radix, precision)
            }

            Op.QWORD, Op.DWORD, Op.WORD, Op.BYTE -> {
                if (bRecord) {
                    currentVal = input.toRational(rp, radix, precision)
                    bRecord = false
                }
                setRadixTypeAndNumWidth(-1, NumWidth.values()[wParam - Op.QWORD])
            }

            Op.DEG, Op.RAD, Op.GRAD -> angletype = AngleType.values()[wParam - Op.DEG]

            Op.SIGN -> {
                if (bRecord) {
                    if (input.tryToggleSign(fIntegerMode, getMaxDecimalValueString())) {
                        displayNum()
                    } else {
                        handleErrorCommand(wParam)
                    }
                    return
                }
                // Doing +/- while in Record mode is not a unary operation
                if (Op.isBinOpCode(nLastCom)) currentVal = lastVal
                if (!historyCollector.fOpndAddedToHistory()) historyCollector.addOpndToHistory(numberString, currentVal)
                currentVal = currentVal.negate()
                displayNum()
                historyCollector.addUnaryOpToHistory(Op.SIGN, bInv, angletype)
            }

            Op.RECALL -> {
                if (bSetCalcState) {
                    // Not a Memory recall. set the result
                    bSetCalcState = false
                } else {
                    currentVal = memoryValue ?: Rational.ZERO
                }
                checkAndAddLastBinOpToHistory()
                displayNum()
            }

            Op.MPLUS -> {
                val result = rm.add(memoryValue ?: Rational.ZERO, currentVal)
                memoryValue = truncateNumForIntMath(result) // Memory should follow the current int mode
            }

            Op.MMINUS -> {
                val result = rm.sub(memoryValue ?: Rational.ZERO, currentVal)
                memoryValue = truncateNumForIntMath(result)
            }

            Op.STORE, Op.MCLEAR -> {
                memoryValue = if (wParam == Op.STORE) truncateNumForIntMath(currentVal) else Rational.of(0)
            }

            Op.PI -> {
                if (!fIntegerMode) {
                    checkAndAddLastBinOpToHistory() // pi is like entering the number
                    currentVal = Rational.of(if (bInv) rp.twoPi else rp.pi)
                    displayNum()
                    bInv = false
                } else {
                    handleErrorCommand(wParam)
                }
            }

            Op.EULER -> {
                if (!fIntegerMode) {
                    checkAndAddLastBinOpToHistory()
                    currentVal = Rational.of(rp.ratExp)
                    displayNum()
                    bInv = false
                } else {
                    handleErrorCommand(wParam)
                }
            }

            Op.FE -> {
                // Toggle exponential notation display.
                nFE = if (nFE == NumberFormat.Float) NumberFormat.Scientific else NumberFormat.Float
                displayNum()
            }

            Op.EXP -> {
                if (bRecord && !fIntegerMode && input.tryBeginExponent()) {
                    displayNum()
                } else {
                    handleErrorCommand(wParam)
                }
            }

            Op.PNT -> {
                if (nLastCom == Op.CLOSEP) implicitMultiplyAfterCloseParen()
                if (bRecord && !fIntegerMode && input.tryAddDecimalPt()) {
                    displayNum()
                } else {
                    handleErrorCommand(wParam)
                }
            }

            Op.INV -> bInv = !bInv
        }
    }

    /** scicomm.cpp: a digit or '.' right after ')' is an implicit multiplication ("(8)2=16"). */
    private fun implicitMultiplyAfterCloseParen() {
        nOpCode = Op.MUL
        lastVal = currentVal
        holdVal = Rational.of(0)
        bNoPrevEqu = true
        if (!historyCollector.fOpndAddedToHistory()) {
            historyCollector.addOpenBraceToHistory()
            historyCollector.addOpndToHistory(numberString, currentVal)
            historyCollector.addCloseBraceToHistory()
        }
        historyCollector.addBinOpToHistory(nOpCode, fIntegerMode)
        bChangeOp = true
        nPrevOpCode = 0
        while (precedenceOpCount > 0) {
            precedenceOpCount--
            nPrecOp[precedenceOpCount] = 0
        }
    }

    private fun handleParenthesis(wParam: Int) {
        // A full paren array on '(', an empty one on ')', or a full precedence array: ignore the key.
        if ((openParenCount >= MAXPRECDEPTH && wParam == Op.OPENP) || (openParenCount == 0 && wParam != Op.OPENP) ||
            (precedenceOpCount >= MAXPRECDEPTH && nPrecOp[precedenceOpCount - 1] != 0)
        ) {
            if (openParenCount == 0 && wParam != Op.OPENP) calcDisplay?.onNoRightParenAdded()
            handleErrorCommand(wParam)
            return
        }

        if (wParam == Op.OPENP) {
            // an omitted multiplication sign
            if (Op.isDigitOpCode(nLastCom) || Op.isUnaryOpCode(nLastCom) || nLastCom == Op.PNT || nLastCom == Op.CLOSEP) {
                processCommand(Op.MUL)
            }
            checkAndAddLastBinOpToHistory()
            historyCollector.addOpenBraceToHistory()
            // Open level of parentheses, save number and operation.
            parenVals[openParenCount] = lastVal
            nOp[openParenCount++] = if (bChangeOp) nOpCode else 0
            // save a special marker on the precedence array
            if (precedenceOpCount < nPrecOp.size) nPrecOp[precedenceOpCount++] = 0
            lastVal = Rational.of(0)
            if (Op.isBinOpCode(nLastCom)) {
                // "1 + (" starts as "1 + (0"; "1 + 3 (" is "1 + (3".
                currentVal = Rational.of(0)
            }
            nTempCom = 0
            nOpCode = 0
            bChangeOp = false // a ( is like starting a fresh sub equation
        } else {
            if (Op.isBinOpCode(nLastCom)) currentVal = lastVal
            if (!historyCollector.fOpndAddedToHistory()) historyCollector.addOpndToHistory(numberString, currentVal)
            // Get the operation and number and return result.
            currentVal = doOperation(nOpCode, currentVal, lastVal)
            nPrevOpCode = nOpCode
            // Process the precedence stack down to the '(' marker.
            nOpCode = popPrecOp()
            while (nOpCode != 0) {
                val ni = precedenceOfOp(nPrevOpCode)
                val nx = precedenceOfOp(nOpCode)
                if (ni <= nx) historyCollector.enclosePrecInversionBrackets()
                historyCollector.popLastOpndStart()
                lastVal = precedenceVals[precedenceOpCount]
                currentVal = doOperation(nOpCode, currentVal, lastVal)
                nPrevOpCode = nOpCode
                nOpCode = popPrecOp()
            }
            historyCollector.addCloseBraceToHistory()
            // Get back the operation and opcode at the beginning of this parenthesis pair
            openParenCount -= 1
            lastVal = parenVals[openParenCount]
            nOpCode = nOp[openParenCount]
            bChangeOp = nOpCode != 0
        }
        calcDisplay?.setParenthesisNumber(openParenCount)
        if (!bError) displayNum()
    }

    /** m_nPrecOp[--m_precedenceOpCount]; an empty stack (never reached: '(' always pushes a 0 marker) reads 0. */
    private fun popPrecOp(): Int = if (precedenceOpCount > 0) nPrecOp[--precedenceOpCount] else 0

    /** scicomm.cpp ResolveHighestPrecedenceOperation: one = step, repeating the last operation on repeated =. */
    private fun resolveHighestPrecedenceOperation() {
        if (nOpCode != 0) {
            if (bNoPrevEqu) {
                holdVal = currentVal
            } else {
                currentVal = holdVal
                displayNum() // to update the numberString
                historyCollector.addBinOpToHistory(nOpCode, fIntegerMode, false)
                historyCollector.addOpndToHistory(numberString, currentVal) // the repeated last op
            }
            currentVal = doOperation(nOpCode, currentVal, lastVal)
            nPrevOpCode = nOpCode
            lastVal = currentVal
            if (!bError) displayNum()
            bNoPrevEqu = false
        } else if (!bError) {
            displayNum()
        }
    }

    /** scicomm.cpp CheckAndAddLastBinOpToHistory. */
    private fun checkAndAddLastBinOpToHistory(addToHistory: Boolean = true) {
        if (bChangeOp) {
            if (historyCollector.fOpndAddedToHistory()) {
                // "1 + sqrt(4)" whose sqrt(4) is being replaced by a new number: erase the last operand
                historyCollector.removeLastOpndFromHistory()
            }
        } else if (historyCollector.fOpndAddedToHistory() && !bError) {
            // "4 sqrt" then a new number: complete "sqrt(4) = 2" as a history line.
            if ((Op.isUnaryOpCode(nLastCom) || nLastCom == Op.SIGN || nLastCom == Op.CLOSEP) && openParenCount == 0) {
                if (addToHistory) {
                    historyCollector.completeHistoryLine(groupDigitsPerRadix(numberString, radix))
                }
            } else {
                historyCollector.removeLastOpndFromHistory()
            }
        }
    }

    private fun setPrimaryDisplay(text: String, isError: Boolean = false) {
        if (calcDisplay != null) {
            calcDisplay.setPrimaryDisplay(text, isError)
            calcDisplay.setIsInError(isError)
        }
    }

    private fun displayAnnounceBinaryOperator() {
        calcDisplay?.binaryOperatorReceived()
    }

    private fun isCurrentTooBigForTrig(): Boolean = rm.ge(currentVal, maxTrigonometricNum)

    /** scicomm.cpp GetCurrentResultForRadix (the Programmer radix rows). */
    fun getCurrentResultForRadix(radixOut: Int, precisionOut: Int, groupDigits: Boolean): String {
        val rat = if (bRecord) input.toRational(rp, radix, precision) else currentVal
        rp.changeConstants(radix, precisionOut)
        val s = getStringForDisplay(rat, radixOut)
        if (s.isNotEmpty()) {
            // Revert the precision to previously stored precision
            rp.changeConstants(radix, precision)
        }
        return if (groupDigits) groupDigitsPerRadix(s, radixOut) else s
    }

    /** scicomm.cpp GetStringForDisplay: Programmer shows negative decimals in two's complement form. */
    fun getStringForDisplay(rat: Rational, radixOut: Int): String {
        if (!fIntegerMode) {
            return rm.toString(rat, radixOut, nFE, precision)
        }
        var tempRat = truncateNumForIntMath(rat)
        return try {
            val w64Bits = rm.toUInt64(tempRat)
            val fMsb = ((w64Bits ushr (dwWordBitWidth - 1)) and 1L) == 1L
            if (radixOut == 10 && fMsb) {
                tempRat = rm.add(rm.xor(tempRat, getChopNumber()), Rational.of(1)).negate()
            }
            rm.toString(tempRat, radixOut, nFE, precision)
        } catch (e: RatpakException) {
            ""
        }
    }

    /** The value on display as a number (the input while typing), for carrying it across a mode switch. */
    fun displayedValue(): Rational {
        val rat = if (bRecord) input.toRational(rp, radix, precision) else currentVal
        if (!fIntegerMode) return rat
        val t = truncateNumForIntMath(rat)
        return try {
            val w64Bits = rm.toUInt64(t)
            if (((w64Bits ushr (dwWordBitWidth - 1)) and 1L) == 1L) rm.add(rm.xor(t, getChopNumber()), Rational.of(1)).negate() else t
        } catch (e: RatpakException) {
            t
        }
    }

    // ------------------------------------------------------------------ scioper.cpp

    /** scioper.cpp DoOperation: rhs is the left operand (the value before the operator), lhs the right one. */
    private fun doOperation(operation: Int, lhs: Rational, rhs: Rational): Rational {
        // Remove any variance in how 0 could be represented in rat e.g. -0, 0/n, etc.
        var result = if (rm.ne(lhs, Rational.of(0))) lhs else Rational.of(0)
        try {
            when (operation) {
                Op.AND -> result = rm.and(result, rhs)
                Op.OR -> result = rm.or(result, rhs)
                Op.XOR -> result = rm.xor(result, rhs)
                Op.NAND -> result = rm.xor(rm.and(result, rhs), getChopNumber())
                Op.NOR -> result = rm.xor(rm.or(result, rhs), getChopNumber())
                // A shift by the word size or more moves every bit out. Windows throws "Result not defined" here although
                // its own comment says the result "is always 0" (scioper.cpp:41, 65, 74); the shell gives the right answer
                // (INDEX.md Change Log, 2026-09-24): 0, and for an arithmetic right shift of a negative value every bit is
                // a copy of the sign bit, all ones, which is -1.
                Op.RSHF -> {
                    val w64Bits = rm.toUInt64(rhs)
                    val fMsb = ((w64Bits ushr (dwWordBitWidth - 1)) and 1L) == 1L
                    if (fIntegerMode && rm.ge(result, Rational.of(dwWordBitWidth))) {
                        result = if (fMsb) getChopNumber() else Rational.of(0)
                    } else {
                        val holdVal = result
                        result = rm.shr(rhs, holdVal)
                        if (fMsb) {
                            result = rm.integer(result)
                            var tempRat = rm.shr(getChopNumber(), holdVal)
                            tempRat = rm.integer(tempRat)
                            result = rm.or(result, rm.xor(tempRat, getChopNumber()))
                        }
                    }
                }
                Op.RSHFL -> {
                    result = if (fIntegerMode && rm.ge(result, Rational.of(dwWordBitWidth))) Rational.of(0) else rm.shr(rhs, result)
                }
                Op.LSHF -> {
                    result = if (fIntegerMode && rm.ge(result, Rational.of(dwWordBitWidth))) Rational.of(0) else rm.shl(rhs, result)
                }
                Op.ADD -> result = rm.add(result, rhs)
                Op.SUB -> result = rm.sub(rhs, result)
                Op.MUL -> result = rm.mul(result, rhs)
                Op.DIV, Op.MOD -> {
                    var iNumeratorSign = 1
                    var iDenominatorSign = 1
                    var temp = result
                    result = rhs
                    if (fIntegerMode) {
                        var w64Bits = rm.toUInt64(rhs)
                        var fMsb = ((w64Bits ushr (dwWordBitWidth - 1)) and 1L) == 1L
                        if (fMsb) {
                            result = rm.add(rm.xor(rhs, getChopNumber()), Rational.of(1))
                            iNumeratorSign = -1
                        }
                        w64Bits = rm.toUInt64(temp)
                        fMsb = ((w64Bits ushr (dwWordBitWidth - 1)) and 1L) == 1L
                        if (fMsb) {
                            temp = rm.add(rm.xor(temp, getChopNumber()), Rational.of(1))
                            iDenominatorSign = -1
                        }
                    }
                    if (operation == Op.DIV) {
                        result = rm.div(result, temp)
                        if (fIntegerMode && (iNumeratorSign * iDenominatorSign) == -1) {
                            result = rm.integer(result).negate()
                        }
                    } else {
                        if (fIntegerMode) {
                            // Programmer mode, use remrat (remainder after division)
                            result = rm.rem(result, temp)
                            if (iNumeratorSign == -1) result = rm.integer(result).negate()
                        } else {
                            // other modes, use modrat (modulus after division)
                            result = rm.mod(result, temp)
                        }
                    }
                }
                Op.PWR -> result = rm.pow(rhs, result)
                Op.ROOT -> result = rm.root(rhs, result)
                Op.LOGBASEY -> result = rm.div(rm.log(rhs), rm.log(result))
            }
        } catch (e: RatpakException) {
            displayError(e.code)
            // On error, return the original value
            result = lhs
        }
        return result
    }

    // ------------------------------------------------------------------ scifunc.cpp

    private fun sciCalcFunctions(rat: Rational, op: Int): Rational {
        var result = Rational.ZERO
        try {
            when (op) {
                Op.CHOP -> result = if (bInv) rm.frac(rat) else rm.integer(rat)
                Op.COM -> result = if (radix == 10 && !fIntegerMode) {
                    rm.add(rm.integer(rat), Rational.of(1)).negate()
                } else {
                    rm.xor(rat, getChopNumber())
                }
                Op.ROL, Op.ROLC -> if (fIntegerMode) {
                    result = rm.integer(rat)
                    var w64Bits = rm.toUInt64(result)
                    val msb = (w64Bits ushr (dwWordBitWidth - 1)) and 1L
                    w64Bits = w64Bits shl 1
                    if (op == Op.ROL) {
                        w64Bits = w64Bits or msb
                    } else {
                        w64Bits = w64Bits or carryBit
                        carryBit = msb
                    }
                    result = rm.fromUInt64(w64Bits)
                }
                Op.ROR, Op.RORC -> if (fIntegerMode) {
                    result = rm.integer(rat)
                    var w64Bits = rm.toUInt64(result)
                    val lsb = if ((w64Bits and 1L) == 1L) 1L else 0L
                    w64Bits = w64Bits ushr 1
                    if (op == Op.ROR) {
                        w64Bits = w64Bits or (lsb shl (dwWordBitWidth - 1))
                    } else {
                        w64Bits = w64Bits or (carryBit shl (dwWordBitWidth - 1))
                        carryBit = lsb
                    }
                    result = rm.fromUInt64(w64Bits)
                }
                Op.PERCENT -> {
                    // x [*/] y% is x [*/] (y/100); otherwise x [op] (x * y/100).
                    result = if (nOpCode == Op.MUL || nOpCode == Op.DIV) {
                        rm.div(rat, Rational.of(100))
                    } else {
                        rm.mul(rat, rm.div(lastVal, Rational.of(100)))
                    }
                }
                Op.SIN -> if (!fIntegerMode) result = if (bInv) rm.asin(rat, angletype) else rm.sin(rat, angletype)
                Op.SINH -> if (!fIntegerMode) result = if (bInv) rm.asinh(rat) else rm.sinh(rat)
                Op.COS -> if (!fIntegerMode) result = if (bInv) rm.acos(rat, angletype) else rm.cos(rat, angletype)
                Op.COSH -> if (!fIntegerMode) result = if (bInv) rm.acosh(rat) else rm.cosh(rat)
                Op.TAN -> if (!fIntegerMode) result = if (bInv) rm.atan(rat, angletype) else rm.tan(rat, angletype)
                Op.TANH -> if (!fIntegerMode) result = if (bInv) rm.atanh(rat) else rm.tanh(rat)
                Op.SEC -> if (!fIntegerMode) result = if (bInv) rm.acos(rm.invert(rat), angletype) else rm.invert(rm.cos(rat, angletype))
                Op.CSC -> if (!fIntegerMode) result = if (bInv) rm.asin(rm.invert(rat), angletype) else rm.invert(rm.sin(rat, angletype))
                Op.COT -> if (!fIntegerMode) result = if (bInv) rm.atan(rm.invert(rat), angletype) else rm.invert(rm.tan(rat, angletype))
                Op.SECH -> if (!fIntegerMode) result = if (bInv) rm.acosh(rm.invert(rat)) else rm.invert(rm.cosh(rat))
                Op.CSCH -> if (!fIntegerMode) result = if (bInv) rm.asinh(rm.invert(rat)) else rm.invert(rm.sinh(rat))
                Op.COTH -> if (!fIntegerMode) result = if (bInv) rm.atanh(rm.invert(rat)) else rm.invert(rm.tanh(rat))
                Op.REC -> result = rm.invert(rat)
                Op.SQR -> result = rm.pow(rat, Rational.of(2))
                Op.SQRT -> result = rm.root(rat, Rational.of(2))
                Op.CUBEROOT, Op.CUB -> result = if (op == Op.CUBEROOT) rm.root(rat, Rational.of(3)) else rm.pow(rat, Rational.of(3))
                Op.LOG -> result = rm.log10(rat)
                Op.POW10 -> result = rm.pow(Rational.of(10), rat)
                Op.POW2 -> result = rm.pow(Rational.of(2), rat)
                Op.LN -> result = if (bInv) rm.exp(rat) else rm.log(rat)
                Op.FAC -> result = rm.fact(rat)
                Op.DEGREES, Op.DMS -> {
                    if (op == Op.DEGREES) {
                        // the old Win32 Calc's degrees was Inv of dms
                        processCommand(Op.INV)
                    }
                    if (!fIntegerMode) {
                        var shftRat = Rational.of(if (bInv) 100 else 60)
                        val degreeRat = rm.integer(rat)
                        var minuteRat = rm.mul(rm.sub(rat, degreeRat), shftRat)
                        var secondRat = minuteRat
                        minuteRat = rm.integer(minuteRat)
                        secondRat = rm.mul(rm.sub(secondRat, minuteRat), shftRat)
                        shftRat = Rational.of(if (bInv) 60 else 100)
                        secondRat = rm.div(secondRat, shftRat)
                        minuteRat = rm.div(rm.add(minuteRat, secondRat), shftRat)
                        result = rm.add(degreeRat, minuteRat)
                    }
                }
                Op.CEIL -> result = if (rm.gt(rm.frac(rat), Rational.of(0))) rm.integer(rm.add(rat, Rational.of(1))) else rm.integer(rat)
                Op.FLOOR -> result = if (rm.lt(rm.frac(rat), Rational.of(0))) rm.integer(rm.sub(rat, Rational.of(1))) else rm.integer(rat)
                Op.ABS -> result = rm.abs(rat)
            }
        } catch (e: RatpakException) {
            displayError(e.code)
            result = rat
        }
        return result
    }

    /** scifunc.cpp DisplayError: the error string, the error flag, and the history line left as it is. */
    fun displayError(nError: Int) {
        val errorString = EngineStrings.errorString(nError)
        calcDisplay?.onError(nError)
        setPrimaryDisplay(errorString, true)
        bError = true
        lastErrorCode = nError
        historyCollector.clearHistoryLine(errorString)
    }

    // ------------------------------------------------------------------ scidisp.cpp

    /** scidisp.cpp TruncateNumForIntMath: integer part, two's complement of negatives, masked to the word size. */
    fun truncateNumForIntMath(rat: Rational): Rational {
        if (!fIntegerMode) return rat
        var result = rm.integer(rat)
        if (rm.lt(result, Rational.of(0))) {
            result = rm.sub(result.negate(), Rational.of(1))
            result = rm.xor(result, getChopNumber())
        }
        result = rm.and(result, getChopNumber())
        return result
    }

    fun displayNum() {
        val ld = g.lastDisp
        if (bRecord || rm.ne(ld.value, currentVal) || ld.precision != precision || ld.radix != radix ||
            ld.nFE != nFE.ordinal || !ld.bUseSep || ld.numwidth != numwidth || ld.fIntMath != fIntegerMode ||
            ld.bRecord != bRecord
        ) {
            ld.precision = precision
            ld.radix = radix
            ld.nFE = nFE.ordinal
            ld.numwidth = numwidth
            ld.fIntMath = fIntegerMode
            ld.bRecord = bRecord
            ld.bUseSep = true
            if (bRecord) {
                numberString = input.toStringForRadix(radix)
            } else {
                // Programmer mode truncates, so 5 / 2 * 2 gives 4, not 5.
                if (fIntegerMode) currentVal = truncateNumForIntMath(currentVal)
                numberString = getStringForDisplay(currentVal, radix)
            }
            ld.value = currentVal
            if (radix == 10 && isNumberInvalid(numberString, MAX_EXPONENT, precision, radix) != 0) {
                displayError(CalcErr.OVERFLOW)
            } else {
                setPrimaryDisplay(groupDigitsPerRadix(numberString, radix))
            }
        }
    }

    /** scidisp.cpp IsNumberInvalid: more than 4 exponent digits, or more mantissa digits than the precision. */
    fun isNumberInvalid(numberString: String, iMaxExp: Int, iMaxMantissa: Int, radix: Int): Int {
        var iError = 0
        if (radix == 10) {
            val rx = Regex("[+-]?(\\d*)[" + decimalSeparator + "]?(\\d*)(?:e[+-]?(\\d*))?")
            val m = rx.matchEntire(numberString)
            if (m != null) {
                val g3 = m.groups[3]?.value ?: ""
                if (g3.length > iMaxExp) {
                    iError = IDS_ERR_INPUT_OVERFLOW
                } else {
                    val intPart = m.groupValues[1].trimStart('0')
                    val iMantissa = intPart.length + m.groupValues[2].length
                    if (iMantissa > iMaxMantissa) iError = IDS_ERR_INPUT_OVERFLOW
                }
            } else {
                iError = IDS_ERR_UNK_CH
            }
        } else {
            for (c in numberString) {
                if (radix == 16) {
                    if (!(c in '0'..'9' || c in 'A'..'F')) iError = IDS_ERR_UNK_CH
                } else if (c < '0' || c >= '0' + radix) {
                    iError = IDS_ERR_UNK_CH
                }
            }
        }
        return iError
    }

    fun groupDigitsPerRadix(numberString: String, radix: Int): String {
        if (numberString.isEmpty()) return ""
        return when (radix) {
            10 -> groupDigits(groupSeparator.toString(), decGrouping, numberString, numberString[0] == '-')
            8 -> groupDigits(" ", listOf(3, 0), numberString)
            2, 16 -> groupDigits(" ", listOf(4, 0), numberString)
            else -> numberString
        }
    }

    /** scidisp.cpp GroupDigits: separators left of the decimal point (or of the exponent). */
    fun groupDigits(delimiter: String, grouping: List<Int>, displayString: String, isNumNegative: Boolean = false): String {
        if (delimiter.isEmpty() || grouping.isEmpty()) return displayString
        val exp = displayString.indexOf('e')
        val hasExponent = exp >= 0
        val dec = displayString.indexOf(decimalSeparator)
        val hasDecimal = dec >= 0
        // index (exclusive) of the end of the part subject to grouping
        val groupEnd = when {
            hasDecimal -> dec
            hasExponent -> exp
            else -> displayString.length
        }
        val stop = if (isNumNegative) 1 else 0
        val result = StringBuilder()
        var groupingSize = 0
        var groupIdx = 0
        var currGrouping = grouping[0]
        var i = groupEnd - 1
        while (i >= stop) {
            result.append(displayString[i])
            i--
            groupingSize++
            if (currGrouping != 0 && (groupingSize % currGrouping) == 0 && i >= stop) {
                result.append(delimiter.reversed())
                groupingSize = 0
                if (groupIdx < grouping.size) {
                    groupIdx++
                    currGrouping = 0
                    while (groupIdx < grouping.size) {
                        if (grouping[groupIdx] != 0) {
                            currGrouping = grouping[groupIdx]
                            break
                        }
                        currGrouping = grouping[groupIdx - 1]
                        groupIdx++
                    }
                }
            }
        }
        if (isNumNegative) result.append(displayString[0])
        result.reverse()
        if (hasDecimal) {
            result.append(displayString.substring(dec))
        } else if (hasExponent) {
            result.append(displayString.substring(exp))
        }
        return result.toString()
    }

    // ------------------------------------------------------------------ sciset.cpp

    /** sciset.cpp SetRadixTypeAndNumWidth; [radixType] is 0..3 (HEX, DEC, OCT, BIN) or -1 to keep. */
    fun setRadixTypeAndNumWidth(radixType: Int, width: NumWidth?) {
        // Put a two's-complement value back into sign-magnitude form for the new width (DisplayNum redoes it).
        if (fIntegerMode) {
            val w64Bits = rm.toUInt64(currentVal)
            val fMsb = ((w64Bits ushr (dwWordBitWidth - 1)) and 1L) == 1L // the old width
            if (fMsb) {
                val tempResult = rm.xor(currentVal, getChopNumber())
                currentVal = rm.add(tempResult, Rational.of(1)).negate()
            }
        }
        if (radixType in 0..3) radix = nRadixFromRadixType(radixType)
        if (width != null) {
            numwidth = width
            dwWordBitWidth = dwWordBitWidthFromNumWidth(width)
        }
        baseOrPrecisionChanged()
        displayNum()
    }

    private fun dwWordBitWidthFromNumWidth(w: NumWidth): Int = w.bits

    private fun nRadixFromRadixType(t: Int): Int = when (t) {
        0 -> 16
        2 -> 8
        3 -> 2
        else -> 10
    }

    private fun tryToggleBit(wbitno: Int): Boolean {
        if (wbitno >= dwWordBitWidth) return false
        var result = rm.integer(currentVal)
        result = if (rm.ne(result, Rational.of(0))) result else Rational.of(0)
        currentVal = rm.xor(result, rm.pow(Rational.of(2), Rational.of(wbitno)))
        return true
    }

    fun updateMaxIntDigits() {
        cIntDigitsSav = if (radix == 10) {
            if (fIntegerMode) getMaxDecimalValueString().length - 1 else precision
        } else {
            dwWordBitWidth / quickLog2(radix)
        }
    }

    private fun baseOrPrecisionChanged() {
        updateMaxIntDigits()
        changeBaseConstants(radix, cIntDigitsSav, precision)
    }

    private fun changeBaseConstants(radix: Int, maxIntDigits: Int, precision: Int) {
        if (radix == 10) {
            // Base 10 keeps the full precision internally (a qword's high word needs 4294967295.99999...).
            rp.changeConstants(radix, precision)
        } else {
            rp.changeConstants(radix, maxIntDigits + 1)
        }
    }

    companion object {
        private const val DEFAULT_MAX_DIGITS = 32
        private const val DEFAULT_PRECISION = 32
        private const val DEFAULT_RADIX = 10
        const val DEFAULT_DEC_SEPARATOR = '.'
        private const val DEFAULT_GRP_SEPARATOR = ','
        private const val DEFAULT_GRP_STR = "3;0"
        private const val DEFAULT_NUMBER_STR = "0"
        private const val RADIX_DECIMAL = 1
        const val MAX_EXPONENT = 4
        private const val MAX_GROUPING_SIZE = 16
        private const val IDS_ERR_UNK_CH = 111
        private const val IDS_ERR_INPUT_OVERFLOW = 119

        /** scicomm.cpp NPrecedenceOfOp. */
        fun precedenceOfOp(op: Int): Int = when (op) {
            Op.AND, Op.NAND, Op.NOR -> 1
            Op.ADD, Op.SUB -> 2
            Op.LSHF, Op.RSHF, Op.RSHFL, Op.MOD, Op.DIV, Op.MUL -> 3
            Op.PWR, Op.ROOT, Op.LOGBASEY -> 4
            else -> 0
        }

        /** sciset.cpp QuickLog2. */
        fun quickLog2(iNum0: Int): Int {
            var iNum = iNum0
            var iRes = 0
            while ((iNum and 1) == 0) {
                iRes++
                iNum = iNum shr 1
            }
            iNum = iNum shr 1
            if (iNum != 0) {
                iNum = iNum shr 1
                while (iNum != 0) {
                    ++iRes
                    iNum = iNum shr 1
                }
                iRes += 2
            }
            return iRes
        }

        /** scidisp.cpp DigitGroupingStringToGroupingVector. */
        fun digitGroupingStringToGroupingVector(groupingString: String): List<Int> {
            val grouping = ArrayList<Int>()
            for (part in groupingString.split(';')) {
                if (part.isEmpty()) continue
                val currentGroup = part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                if (currentGroup < MAX_GROUPING_SIZE) grouping.add(currentGroup)
            }
            return grouping
        }
    }
}

package app.tileshell.calc.engine

/** Header Files/History.h MAXPRECDEPTH. */
internal const val MAXPRECDEPTH = 25

/** The expression commands History.cpp records beside its tokens; only operands carry data the port needs. */
internal sealed class ExprCommand {
    class Operand(val value: Rational) : ExprCommand()
    object Other : ExprCommand()
}

/** ICalcDisplay (Header Files/ICalcDisplay.h), the engine's callbacks to its owner. */
internal interface CalcDisplay {
    fun setPrimaryDisplay(text: String, isError: Boolean)
    /** DisplayError is about to show the error string of this ratpak code (diagnostics only; not in ICalcDisplay). */
    fun onError(code: Int)
    fun setIsInError(isError: Boolean)
    fun setExpressionDisplay(tokens: List<String>)
    fun setParenthesisNumber(count: Int)
    fun onNoRightParenAdded()
    fun maxDigitsReached()
    fun binaryOperatorReceived()
    fun onHistoryItemAdded(index: Int)
}

/** IHistoryDisplay: where a completed equation goes (CalculatorHistory; none in Programmer). */
internal interface HistoryDisplay {
    fun addToHistory(tokens: List<String>, result: String): Int
}

/**
 * Port of CEngine/History.cpp CHistoryCollector: builds the expression line ("48 ÷ ", "sin₀(30)", "1 + (0 + 3)") token by
 * token as the engine runs, and hands completed equations to the history.
 */
internal class HistoryCollector(
    private val calcDisplay: CalcDisplay?,
    private val historyDisplay: HistoryDisplay?,
    private var decimalSymbol: Char,
) {
    private var tokens: MutableList<Pair<String, Int>>? = null
    private var commands: MutableList<ExprCommand>? = null
    private var lastOpStartIndex = -1
    private var lastBinOpStartIndex = -1
    private val operandIndices = IntArray(MAXPRECDEPTH)
    private var curOperandIndex = 0
    private var bLastOpndBrace = false

    init {
        reinitHistory()
    }

    private fun reinitHistory() {
        lastOpStartIndex = -1
        lastBinOpStartIndex = -1
        curOperandIndex = 0
        bLastOpndBrace = false
        tokens?.clear()
        commands?.clear()
    }

    fun addOpndToHistory(numStr: String, rat: Rational, fRepetition: Boolean = false) {
        val iCommandEnd = addCommand(ExprCommand.Operand(rat))
        lastOpStartIndex = ichAddSzToEquationSz(numStr, iCommandEnd)
        if (fRepetition) setExpressionDisplay()
        bLastOpndBrace = false
        lastBinOpStartIndex = -1
    }

    fun removeLastOpndFromHistory() {
        truncateEquationSzFromIch(lastOpStartIndex)
        setExpressionDisplay()
        lastOpStartIndex = -1
    }

    fun addBinOpToHistory(nOpCode: Int, isIntegerMode: Boolean, fNoRepetition: Boolean = true) {
        val iCommandEnd = addCommand(ExprCommand.Other)
        lastBinOpStartIndex = ichAddSzToEquationSz(" ", -1)
        ichAddSzToEquationSz(EngineStrings.opCodeToBinaryString(nOpCode, isIntegerMode), iCommandEnd)
        ichAddSzToEquationSz(" ", -1)
        if (fNoRepetition) setExpressionDisplay()
        lastOpStartIndex = -1
    }

    fun changeLastBinOp(nOpCode: Int, fPrecInvToHigher: Boolean, isIntegerMode: Boolean) {
        truncateEquationSzFromIch(lastBinOpStartIndex)
        if (fPrecInvToHigher) enclosePrecInversionBrackets()
        addBinOpToHistory(nOpCode, isIntegerMode)
    }

    fun pushLastOpndStart(ichOpndStart: Int = -1) {
        val ich = if (ichOpndStart == -1) lastOpStartIndex else ichOpndStart
        if (curOperandIndex < operandIndices.size) operandIndices[curOperandIndex++] = ich
    }

    fun popLastOpndStart() {
        if (curOperandIndex > 0) lastOpStartIndex = operandIndices[--curOperandIndex]
    }

    fun addOpenBraceToHistory() {
        addCommand(ExprCommand.Other)
        val ichOpndStart = ichAddSzToEquationSz(EngineStrings.opCodeToString(Op.OPENP), -1)
        pushLastOpndStart(ichOpndStart)
        setExpressionDisplay()
        lastBinOpStartIndex = -1
    }

    fun addCloseBraceToHistory() {
        addCommand(ExprCommand.Other)
        ichAddSzToEquationSz(EngineStrings.opCodeToString(Op.CLOSEP), -1)
        setExpressionDisplay()
        popLastOpndStart()
        lastBinOpStartIndex = -1
        bLastOpndBrace = true
    }

    fun enclosePrecInversionBrackets() {
        val ichStart = if (curOperandIndex > 0) operandIndices[curOperandIndex - 1] else 0
        insertSzInEquationSz(EngineStrings.opCodeToString(Op.OPENP), -1, ichStart)
        ichAddSzToEquationSz(EngineStrings.opCodeToString(Op.CLOSEP), -1)
    }

    fun fOpndAddedToHistory(): Boolean = lastOpStartIndex != -1

    fun addUnaryOpToHistory(nOpCode: Int, fInv: Boolean, angletype: AngleType) {
        if (nOpCode == Op.PERCENT) {
            val iCommandEnd = addCommand(ExprCommand.Other)
            ichAddSzToEquationSz(EngineStrings.opCodeToString(nOpCode), iCommandEnd)
        } else {
            val iCommandEnd = addCommand(ExprCommand.Other)
            val operandStr = StringBuilder(EngineStrings.opCodeToUnaryString(nOpCode, fInv, angletype))
            if (!bLastOpndBrace) operandStr.append(EngineStrings.opCodeToString(Op.OPENP))
            insertSzInEquationSz(operandStr.toString(), iCommandEnd, lastOpStartIndex)
            if (!bLastOpndBrace) ichAddSzToEquationSz(EngineStrings.opCodeToString(Op.CLOSEP), -1)
        }
        setExpressionDisplay()
        bLastOpndBrace = false
        // lastOpStartIndex stays: the last operand is replaced by unaryop(lastopnd)
        lastBinOpStartIndex = -1
    }

    fun completeHistoryLine(numStr: String) {
        if (historyDisplay != null) {
            val index = historyDisplay.addToHistory(tokens?.map { it.first } ?: emptyList(), numStr)
            calcDisplay?.onHistoryItemAdded(index)
        }
        tokens = null
        commands = null
        reinitHistory()
    }

    fun completeEquation(numStr: String) {
        // Add only the '=' token (an EQU command would duplicate history entries on reload).
        ichAddSzToEquationSz(EngineStrings.opCodeToString(Op.EQU), -1)
        setExpressionDisplay()
        completeHistoryLine(numStr)
    }

    fun clearHistoryLine(errStr: String) {
        if (errStr.isEmpty()) { // in case of error let the display stay as it is
            calcDisplay?.setExpressionDisplay(emptyList())
            reinitHistory()
        }
    }

    private fun ichAddSzToEquationSz(str: String, icommandIndex: Int): Int {
        val t = tokens ?: mutableListOf<Pair<String, Int>>().also { tokens = it }
        t.add(Pair(str, icommandIndex))
        return t.size - 1
    }

    private fun insertSzInEquationSz(str: String, icommandIndex: Int, ich: Int) {
        val t = tokens ?: mutableListOf<Pair<String, Int>>().also { tokens = it }
        t.add(ich.coerceIn(0, t.size), Pair(str, icommandIndex))
    }

    /**
     * History.cpp TruncateEquationSzFromIch. Its command truncation is guarded by `(minIdx != -1) || (curTokenId <
     * minIdx)` with minIdx starting at -1, which is never true, so only the tokens are truncated; ported as such.
     */
    private fun truncateEquationSzFromIch(ich: Int) {
        val t = tokens ?: return
        if (ich < 0 || ich >= t.size) return
        while (t.size > ich) t.removeAt(t.size - 1)
    }

    private fun setExpressionDisplay() {
        calcDisplay?.setExpressionDisplay(tokens?.map { it.first } ?: emptyList())
    }

    private fun addCommand(c: ExprCommand): Int {
        val cmds = commands ?: mutableListOf<ExprCommand>().also { commands = it }
        cmds.add(c)
        return cmds.size - 1
    }

    /** History.cpp UpdateHistoryExpression: re-render the operands of the expression line in a new radix. */
    fun updateHistoryExpression(rm: RationalMath, radix: Int, precision: Int) {
        val t = tokens ?: return
        val cmds = commands ?: return
        for (i in t.indices) {
            val commandPosition = t[i].second
            if (commandPosition != -1) {
                val c = cmds.getOrNull(commandPosition)
                if (c is ExprCommand.Operand) {
                    t[i] = Pair(rm.toString(c.value, radix, NumberFormat.Float, precision), commandPosition)
                }
            }
        }
        setExpressionDisplay()
    }

    fun setDecimalSymbol(sym: Char) {
        decimalSymbol = sym
    }
}

package app.tileshell.calc.engine

/** The three calculator modes (CalculatorManager.h CalculatorPrecision: 16 / 32 / 64 digits). */
enum class CalcMode(val precision: Int) { STANDARD(16), SCIENTIFIC(32), PROGRAMMER(64) }

/** The Scientific angle unit, cycled by the `angle` key DEG -> RAD -> GRAD. */
enum class AngleUnit { DEG, RAD, GRAD }

/** The Programmer radix rows; [key] is the vocabulary key that selects the row. */
enum class Radix(val base: Int, val key: String) { HEX(16, "radix_hex"), DEC(10, "radix_dec"), OCT(8, "radix_oct"), BIN(2, "radix_bin") }

/** The Programmer word sizes, cycled by the `word` key QWORD -> DWORD -> WORD -> BYTE. */
enum class WordSize(val bits: Int) { QWORD(64), DWORD(32), WORD(16), BYTE(8) }

/**
 * The engine's errors: [message] is the en-US string Windows shows (CEngineStrings.resw ids 99-108 through
 * IDS_ERRORS_FIRST + SCODE_CODE), [logName] the `<kind>` of the `[calc] error <kind>` diagnostics line.
 * POSITIVE_INFINITY, NEGATIVE_INFINITY and OUT_OF_MEMORY have no en-US string (ids 102, 103, 106 are absent) and no
 * reachable source; NOT_ENOUGH_MEMORY is CALC_E_INVALIDRANGE, thrown only when a number would need over a billion
 * digits (conv.cpp _createnum), so none of the four can appear from the keypad.
 */
enum class CalcErrorKind(val message: String, val logName: String) {
    DIVIDE_BY_ZERO("Cannot divide by zero", "divide_by_zero"),
    INVALID_INPUT("Invalid input", "invalid_input"),
    UNDEFINED("Result is undefined", "undefined"),
    POSITIVE_INFINITY("", "positive_infinity"),
    NEGATIVE_INFINITY("", "negative_infinity"),
    NOT_ENOUGH_MEMORY("Not enough memory", "not_enough_memory"),
    OUT_OF_MEMORY("", "out_of_memory"),
    OVERFLOW("Overflow", "overflow"),
    NO_RESULT("Result not defined", "no_result");

    internal companion object {
        fun fromCode(code: Int): CalcErrorKind = when (code) {
            CalcErr.DIVIDEBYZERO -> DIVIDE_BY_ZERO
            CalcErr.DOMAIN -> INVALID_INPUT
            CalcErr.INDEFINITE -> UNDEFINED
            CalcErr.POSINFINITY -> POSITIVE_INFINITY
            CalcErr.NEGINFINITY -> NEGATIVE_INFINITY
            CalcErr.INVALIDRANGE -> NOT_ENOUGH_MEMORY
            CalcErr.OUTOFMEMORY -> OUT_OF_MEMORY
            CalcErr.NORESULT -> NO_RESULT
            else -> OVERFLOW
        }
    }
}

/** One history entry as Windows lists it: the expression (CalculatorHistory.cpp GetGeneratedExpression) and result. */
data class HistoryItem(val expression: String, val result: String)

/**
 * Windows Calculator's engine behind the vocabulary of docs/plan/qa/phase-15/calc-keys.md. Ports
 * CalcManager/CalculatorManager.cpp (modes, memory, history, the combined inverse commands) and the parts of
 * Calculator.ViewModels/StandardCalculatorViewModel.cs that decide what a key press sends (error recovery, F-E reset,
 * paste, radix rows) over the CEngine / Ratpack port in this package. Not thread-safe: one instance per UI (Tess uses
 * [CalcExpression], which builds its own).
 */
class Calculator(initialMode: CalcMode = CalcMode.STANDARD) {
    private val globals = EngineGlobals()
    private val display = Display()
    private val stdHistory = HistoryList(MAX_HISTORY_ITEMS)
    private val sciHistory = HistoryList(MAX_HISTORY_ITEMS)
    private var standardEngine: CalcEngine? = null
    private var scientificEngine: CalcEngine? = null
    private var programmerEngine: CalcEngine? = null
    private lateinit var engine: CalcEngine
    private var currentHistory: HistoryList? = null
    private val memorized = ArrayList<Rational>()

    /** The current mode. */
    var mode: CalcMode = initialMode
        private set

    /** The main display, exactly as Windows shows it (grouped; an error string while an error shows). */
    val displayText: String get() = display.primary

    /** The expression line above the display ("48 ÷"); trailing separator spaces trimmed. */
    val expression: String get() = display.expressionTokens.joinToString("").trimEnd()

    /** The expression line as the engine's tokens concatenated, trailing space included ("48 ÷ "). */
    val expressionRaw: String get() = display.expressionTokens.joinToString("")

    /** True while an error string shows (the ViewModel's IsInError). */
    val isError: Boolean get() = display.inError

    /** The kind of the error on display, or null. */
    val errorKind: CalcErrorKind? get() = if (display.inError) display.errorKind else null

    /** The kind of the most recent error (kept after it clears), for the `[calc] error <kind>` line. */
    var lastErrorKind: CalcErrorKind? = null
        private set

    /** Counts every error shown; a change after a press means a new error appeared. */
    var errorCount: Int = 0
        private set

    /** The `inv` (↑) toggle of Scientific and Programmer. */
    var inv: Boolean = false
        private set

    /** The HYP toggle of Scientific. */
    var hyp: Boolean = false
        private set

    /** F-E (forced e-notation) of Scientific. */
    var fe: Boolean = false
        private set

    var angleUnit: AngleUnit = AngleUnit.DEG
        private set

    var radix: Radix = Radix.DEC
        private set

    val wordSize: WordSize get() = WordSize.values()[(programmerEngine?.numwidth ?: NumWidth.QWORD).ordinal]

    /** Open parentheses (the "(=n" count). */
    val parenthesisCount: Int get() = display.parenCount

    init {
        switchEngine(initialMode)
    }

    // ------------------------------------------------------------------ modes

    /**
     * Switches mode keeping the value on display (phase 15 E12). Windows' CalculatorManager::SetXMode clears the new
     * engine (CalculatorManagerTest "CalculatorManagerTestModeChange" expects "0"); the value is then loaded the way
     * CalculatorManager::LoadPersistedPrimaryValue does (PersistedMemObject + IDC_RECALL). An error on display is not
     * carried. Leaving Scientific turns F-E off, as the ViewModel does.
     */
    fun setMode(newMode: CalcMode) {
        if (newMode == mode) return
        val carry: Rational? = if (!engine.bError && !display.inError) engine.displayedValue() else null
        if (fe && newMode != CalcMode.SCIENTIFIC) {
            fe = false
            sendCommand(Op.FE)
        }
        display.inError = false
        inv = false
        hyp = false
        switchEngine(newMode)
        if (carry != null) {
            engine.setPersistedMemObject(carry)
            engine.processCommand(Op.RECALL)
        }
    }

    private fun programmer(): CalcEngine =
        programmerEngine ?: CalcEngine(true, true, globals, display, null).also { programmerEngine = it }

    private fun switchEngine(newMode: CalcMode) {
        mode = newMode
        when (newMode) {
            CalcMode.STANDARD -> {
                // CalculatorManager::SetStandardMode, then the ViewModel's SetRadix(Decimal) / SetPrecision / UpdateMaxIntDigits.
                engine = standardEngine ?: CalcEngine(false, false, globals, display, stdHistory).also { standardEngine = it }
                engine.processCommand(Op.DEC)
                engine.processCommand(Op.CLEAR)
                engine.changePrecision(CalcMode.STANDARD.precision)
                engine.updateMaxIntDigits()
                currentHistory = stdHistory
            }
            CalcMode.SCIENTIFIC -> {
                engine = scientificEngine ?: CalcEngine(true, false, globals, display, sciHistory).also { scientificEngine = it }
                engine.processCommand(Op.DEC)
                engine.processCommand(Op.CLEAR)
                engine.changePrecision(CalcMode.SCIENTIFIC.precision)
                currentHistory = sciHistory
                angleUnit = AngleUnit.values()[engine.angletype.ordinal]
            }
            CalcMode.PROGRAMMER -> {
                engine = programmer()
                radix = Radix.DEC // before IDC_DEC redraws the display (the BIN padding reads it)
                engine.processCommand(Op.DEC)
                engine.processCommand(Op.CLEAR)
                engine.changePrecision(CalcMode.PROGRAMMER.precision)
                currentHistory = null
            }
        }
    }

    // ------------------------------------------------------------------ keys

    /** The physical keys of the current mode (r11/calculator.md §2.8, §4.11-4.12, §6.1), vocabulary names. */
    val keys: List<String> get() = keysFor(mode)

    /** Whether [key] can be pressed now (exists in the mode and its button is enabled). */
    fun isEnabled(key: String): Boolean {
        if (key == "reciprocal" && mode == CalcMode.SCIENTIFIC) return !display.inError
        if (key !in keysFor(mode)) return false
        if (mode == CalcMode.PROGRAMMER) {
            if (key == "decimal") return false
            val d = digitValue(key)
            if (d != null && d >= radix.base) return false
        }
        if ((key == "mc" || key == "mr") && memorized.isEmpty()) return false
        if (display.inError && key in disabledInError()) return false
        return true
    }

    /** The keys enabled now. */
    val enabledKeys: Set<String> get() = keysFor(mode).filter { isEnabled(it) }.toSet()

    /**
     * Presses one key of the vocabulary. Returns false (and does nothing) for a key the mode does not have or whose
     * button is disabled (a hex digit outside HEX, `decimal` in Programmer, MC/MR with empty memory, an operator while an
     * error shows).
     */
    fun press(key: String): Boolean {
        if (!isEnabled(key)) return false
        when (key) {
            "inv" -> inv = !inv
            "hyp" -> hyp = !hyp
            "mc" -> memoryClearAll()
            "mr" -> memoryRecall(0)
            "mplus" -> memoryAdd(0)
            "mminus" -> memorySubtract(0)
            "ms" -> {
                if (display.inError) {
                    sendCommand(Op.CLEAR)
                } else {
                    memoryStore()
                }
            }
            "radix_hex", "radix_dec", "radix_oct", "radix_bin" -> {
                // SwitchProgrammerModeBase: an error is cleared, the radix still changes.
                if (display.inError) sendCommand(Op.CLEAR)
                val r = Radix.values().first { it.key == key }
                radix = r
                sendCommand(Op.HEX + r.ordinal)
            }
            "word" -> {
                val next = WordSize.values()[(wordSize.ordinal + 1) % 4]
                if (display.inError) sendCommand(Op.CLEAR)
                sendCommand(Op.QWORD + next.ordinal)
            }
            else -> buttonPressed(key)
        }
        return true
    }

    /** StandardCalculatorViewModel.OnButtonPressed for an engine key. */
    private fun buttonPressed(key: String) {
        val commands = commandsFor(key)
        if (display.inError) {
            sendCommand(Op.CLEAR)
            if (!isRecoverable(commands.last())) return
        }
        if ((key == "clear" || key == "clear_entry") && fe) {
            // The ViewModel unchecks F-E on C / CE, which sends FE back to the engine.
            fe = false
            sendCommand(Op.FE)
        }
        if (key == "fe") fe = !fe
        for (c in commands) sendCommand(c)
        if (inv && key in SHIFTED_KEYS) inv = false
        if (hyp && (key == "sin" || key == "cos" || key == "tan")) hyp = false
    }

    private fun commandsFor(key: String): List<Int> {
        digitValue(key)?.let { return listOf(Op.D0 + it) }
        return when (key) {
            "decimal" -> listOf(Op.PNT)
            "add" -> listOf(Op.ADD)
            "subtract" -> listOf(Op.SUB)
            "multiply" -> listOf(Op.MUL)
            "divide" -> listOf(Op.DIV)
            "equals" -> listOf(Op.EQU)
            "negate" -> listOf(Op.SIGN)
            "percent" -> listOf(Op.PERCENT)
            "sqrt" -> listOf(if (inv) Op.REC else Op.SQRT)
            "square" -> listOf(if (inv) Op.CUB else Op.SQR)
            "reciprocal" -> listOf(Op.REC)
            "clear_entry" -> listOf(Op.CENTR)
            "clear" -> listOf(Op.CLEAR)
            "backspace" -> listOf(Op.BACK)
            "pow" -> listOf(if (inv) Op.ROOT else Op.PWR)
            "sin", "cos", "tan" -> {
                val base = when (key) {
                    "sin" -> if (hyp) Op.SINH else Op.SIN
                    "cos" -> if (hyp) Op.COSH else Op.COS
                    else -> if (hyp) Op.TANH else Op.TAN
                }
                // CalculatorManager::SendCommand CommandASIN etc.: INV then the function.
                if (inv) listOf(Op.INV, base) else listOf(base)
            }
            "pow10" -> if (inv) listOf(Op.INV, Op.LN) else listOf(Op.POW10) // CommandPOWE = INV + LN
            "log" -> listOf(if (inv) Op.LN else Op.LOG)
            "exp" -> listOf(if (inv) Op.DMS else Op.EXP)
            "mod" -> listOf(if (inv && mode == CalcMode.SCIENTIFIC) Op.DEGREES else Op.MOD)
            "pi" -> listOf(Op.PI)
            "factorial" -> listOf(Op.FAC)
            "lparen" -> listOf(Op.OPENP)
            "rparen" -> listOf(Op.CLOSEP)
            "angle" -> listOf(Op.DEG + (angleUnit.ordinal + 1) % 3)
            "fe" -> listOf(Op.FE)
            "lsh" -> listOf(if (inv) Op.ROL else Op.LSHF)
            "rsh" -> listOf(if (inv) Op.ROR else Op.RSHF)
            "or" -> listOf(Op.OR)
            "xor" -> listOf(Op.XOR)
            "not" -> listOf(Op.COM)
            "and" -> listOf(Op.AND)
            else -> throw IllegalArgumentException("unknown key $key")
        }
    }

    /** StandardCalculatorViewModel.IsRecoverableCommand: digits, '.', A-F and the bit edits survive an error. */
    private fun isRecoverable(command: Int): Boolean =
        command in Op.D0..(Op.D0 + 9) || command == Op.PNT || command in (Op.D0 + 10)..Op.DF ||
            command in Op.BINEDITSTART..Op.BINEDITEND

    /** CalculatorManager::SendCommand for one engine command. */
    private fun sendCommand(command: Int) {
        try {
            engine.processCommand(command)
        } catch (e: RatpakException) {
            // Windows would let this escape the engine; show it as the engine shows its own errors instead.
            engine.displayError(e.code)
        }
        when (command) {
            Op.DEG, Op.RAD, Op.GRAD -> angleUnit = AngleUnit.values()[command - Op.DEG]
        }
    }

    /**
     * The Programmer bit-toggle keypad (r11/calculator.md 4.7–4.8): flips bit [bit] (0 = least significant) of the
     * value on display, IDC_BINEDITSTART + bit (CalcEngine::ProcessCommand's "tiny binary edit windows"). A bit at
     * or past the word size is refused, as the engine refuses it. An error on display is cleared first, as the
     * ViewModel does for every recoverable command. Returns false when nothing was sent.
     */
    fun toggleBit(bit: Int): Boolean {
        if (mode != CalcMode.PROGRAMMER || bit !in 0 until wordSize.bits) return false
        if (display.inError) sendCommand(Op.CLEAR)
        sendCommand(Op.BINEDITSTART + bit)
        return true
    }

    // ------------------------------------------------------------------ Programmer radix rows

    /**
     * The value in one radix as the Programmer radix rows show it (GetResultForRadix(radix, 64, grouped); BIN padded to
     * the nibble by the ViewModel's AddPadding). Empty outside Programmer and while an error shows.
     */
    fun radixValue(r: Radix): String {
        if (mode != CalcMode.PROGRAMMER || display.inError) return ""
        var s = engine.getCurrentResultForRadix(r.base, 64, true)
        if (r == Radix.BIN) s = addPadding(s)
        return s
    }

    /** All four radix rows. */
    val radixValues: Map<Radix, String> get() = Radix.values().associateWith { radixValue(it) }

    private fun addPadding(binaryString: String): String {
        if (binaryString.isEmpty() || binaryString == "0") return binaryString
        val lengthWithoutPadding = binaryString.count { it != ' ' }
        var pad = 4 - (lengthWithoutPadding % 4)
        if (pad == 4) pad = 0
        return "0".repeat(pad) + binaryString
    }

    // ------------------------------------------------------------------ memory (CalculatorManager.cpp)

    /** The memory list, newest first, formatted in the current mode and radix (SetMemorizedNumbersString). */
    val memory: List<String>
        get() = memorized.mapNotNull { v ->
            val s = engine.getStringForDisplay(v, engine.radix)
            if (s.isEmpty()) null else engine.groupDigitsPerRadix(s, engine.radix)
        }

    /** MS: CalculatorManager::MemorizeNumber (inserted at the top; 100 at most). */
    fun memoryStore() {
        if (engine.bError) return
        sendCommand(Op.STORE)
        engine.takePersistedMemObject()?.let { memorized.add(0, it) }
        while (memorized.size > MAX_MEMORY) memorized.removeAt(memorized.size - 1)
    }

    /** MR on item [index] (MR is index 0). */
    fun memoryRecall(index: Int) {
        if (engine.bError || index !in memorized.indices) return
        engine.setPersistedMemObject(memorized[index])
        sendCommand(Op.RECALL)
    }

    /** M+ on item [index]; with empty memory it stores. */
    fun memoryAdd(index: Int) {
        if (engine.bError) return
        if (memorized.isEmpty()) {
            memoryStore()
        } else if (index in memorized.indices) {
            engine.setPersistedMemObject(memorized[index])
            sendCommand(Op.MPLUS)
            memoryChanged(index)
        }
    }

    /** M- on item [index]; with empty memory it stores -x (x stored, then subtracted twice). */
    fun memorySubtract(index: Int) {
        if (engine.bError) return
        if (memorized.isEmpty()) {
            memoryStore()
            memorySubtract(0)
            memorySubtract(0)
        } else if (index in memorized.indices) {
            engine.setPersistedMemObject(memorized[index])
            sendCommand(Op.MMINUS)
            memoryChanged(index)
        }
    }

    private fun memoryChanged(index: Int) {
        if (engine.bError) return
        engine.takePersistedMemObject()?.let { memorized[index] = it }
    }

    /** Removes one memory item. */
    fun memoryClear(index: Int) {
        if (index in memorized.indices) memorized.removeAt(index)
    }

    /** MC: clears all memory. */
    fun memoryClearAll() {
        memorized.clear()
        sendCommand(Op.MCLEAR)
    }

    // ------------------------------------------------------------------ history (CalculatorHistory.cpp)

    /** The current mode's history, newest first (Standard and Scientific keep separate lists; Programmer has none). */
    val history: List<HistoryItem> get() = currentHistory?.items?.reversed() ?: emptyList()

    /** Clears the current mode's history. */
    fun clearHistory() {
        currentHistory?.items?.clear()
    }

    /** Removes one entry, [index] counted newest first as [history] lists them. */
    fun removeHistoryItem(index: Int) {
        val h = currentHistory ?: return
        val i = h.items.size - 1 - index
        if (i in h.items.indices) h.items.removeAt(i)
    }

    // ------------------------------------------------------------------ persistence

    /** Memory as a string for [restoreMemory] (exact values, not display strings). */
    fun encodeMemory(): String = "m1:" + memorized.joinToString(";") { encodeRational(it) }

    /** Restores what [encodeMemory] produced; returns false (memory unchanged) on malformed input. */
    fun restoreMemory(encoded: String): Boolean {
        if (!encoded.startsWith("m1:")) return false
        val body = encoded.substring(3)
        val list = ArrayList<Rational>()
        if (body.isNotEmpty()) {
            for (part in body.split(';')) list.add(decodeRational(part) ?: return false)
        }
        memorized.clear()
        memorized.addAll(list.take(MAX_MEMORY))
        return true
    }

    /** Both histories (Standard and Scientific) as a string for [restoreHistory]. */
    fun encodeHistory(): String {
        val sb = StringBuilder("h1")
        for ((tag, list) in listOf("S" to stdHistory, "C" to sciHistory)) {
            for (item in list.items) {
                sb.append('\n').append(tag).append('\t').append(escape(item.expression)).append('\t').append(escape(item.result))
            }
        }
        return sb.toString()
    }

    /** Restores what [encodeHistory] produced; returns false (history unchanged) on malformed input. */
    fun restoreHistory(encoded: String): Boolean {
        val lines = encoded.split('\n')
        if (lines.isEmpty() || lines[0] != "h1") return false
        val std = ArrayList<HistoryItem>()
        val sci = ArrayList<HistoryItem>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) continue
            val f = line.split('\t')
            if (f.size != 3) return false
            val item = HistoryItem(unescape(f[1]) ?: return false, unescape(f[2]) ?: return false)
            when (f[0]) {
                "S" -> std.add(item)
                "C" -> sci.add(item)
                else -> return false
            }
        }
        stdHistory.items.clear()
        stdHistory.items.addAll(std.takeLast(MAX_HISTORY_ITEMS))
        sciHistory.items.clear()
        sciHistory.items.addAll(sci.takeLast(MAX_HISTORY_ITEMS))
        return true
    }

    // ------------------------------------------------------------------ paste

    /**
     * Pastes [text] with Windows' rules (CopyPasteManager.cs ValidatePasteExpression, then
     * StandardCalculatorViewModel.cs OnPaste): rejected text shows "Invalid input"; accepted text is typed as keys
     * after a CE, so "12+3" leaves 3 on display with "12 + " pending and "12+3=" shows 15. Returns false when rejected.
     */
    fun paste(text: String): Boolean {
        if (!CopyPaste.validate(text, mode, radix, wordSize)) {
            // DisplayPasteError: the ViewModel shows the engine's "Invalid input" in error state (the engine is untouched).
            display.showUiError(CalcErrorKind.INVALID_INPUT)
            return false
        }
        sendCommand(Op.CENTR)
        var isFirstLegalChar = true
        var sendNegate = false
        var isPreviousOperator = false
        val negateStack = ArrayList<Boolean>()
        var i = 0
        while (i < text.length) {
            var sendCmd = true
            val (mapped, canNegate0) = mapCharacter(text[i])
            var canSendNegate = canNegate0
            if (mapped == null) {
                i++
                continue
            }
            if (isFirstLegalChar || isPreviousOperator) {
                isFirstLegalChar = false
                isPreviousOperator = false
                if (mapped == Op.SUB) {
                    sendNegate = true
                    sendCmd = false
                }
                if (mapped == Op.ADD) sendCmd = false
            }
            when (mapped) {
                Op.OPENP -> {
                    negateStack.add(sendNegate)
                    sendNegate = false
                }
                Op.CLOSEP -> {
                    if (negateStack.isNotEmpty()) {
                        sendNegate = negateStack.removeAt(negateStack.size - 1)
                        canSendNegate = true
                    } else {
                        sendCmd = false
                    }
                }
                Op.ADD, Op.SUB, Op.MUL, Op.DIV -> isPreviousOperator = true
            }
            if (sendCmd) {
                sendCommand(mapped)
                if (sendNegate) {
                    if (canSendNegate) sendCommand(Op.SIGN)
                    if (mapped != Op.D0 && mapped != Op.PNT) sendNegate = false
                }
            }
            if (mapped == Op.EXP && i + 1 < text.length) {
                val next = mapCharacter(text[i + 1]).first
                if (next == Op.SUB) {
                    sendCommand(Op.SIGN)
                    i++
                } else if (next == Op.ADD) {
                    i++
                }
            }
            i++
        }
        return true
    }

    /** MapCharacterToButtonId: the command a pasted character becomes and whether a pending negate may follow it. */
    private fun mapCharacter(ch: Char): Pair<Int?, Boolean> {
        val isSci = mode == CalcMode.SCIENTIFIC
        val isProg = mode == CalcMode.PROGRAMMER
        return when (ch) {
            in '0'..'9' -> Pair(Op.D0 + (ch - '0'), ch != '0')
            '*' -> Pair(Op.MUL, false)
            '+' -> Pair(Op.ADD, false)
            '-' -> Pair(Op.SUB, false)
            '/' -> Pair(Op.DIV, false)
            '^' -> Pair(if (isSci) Op.PWR else null, false)
            '%' -> Pair(if (isSci || isProg) Op.MOD else null, false)
            '=' -> Pair(Op.EQU, false)
            '(' -> Pair(Op.OPENP, false)
            ')' -> Pair(Op.CLOSEP, false)
            'a', 'A' -> Pair(Op.D0 + 10, false)
            'b', 'B' -> Pair(Op.D0 + 11, false)
            'c', 'C' -> Pair(Op.D0 + 12, false)
            'd', 'D' -> Pair(Op.D0 + 13, false)
            'e', 'E' -> Pair(if (isProg) Op.D0 + 14 else Op.EXP, false)
            'f', 'F' -> Pair(Op.D0 + 15, false)
            '.' -> Pair(Op.PNT, false)
            else -> Pair(null, false)
        }
    }

    // ------------------------------------------------------------------ the engine's display callbacks

    private inner class Display : CalcDisplay {
        var primary = "0"
        var inError = false
        var errorKind: CalcErrorKind? = null
        var expressionTokens: List<String> = emptyList()
        var parenCount = 0

        override fun setPrimaryDisplay(text: String, isError: Boolean) {
            // StandardCalculatorViewModel.cs SetPrimaryDisplay -> LocalizeDisplayValue: the BIN main display is padded
            // to the nibble (AddPadding). The pinned source pads whatever string arrives; here an error string is left
            // exact ("Cannot divide by zero", never "00Cannot divide by zero"), the wording E11 asserts.
            primary = if (!isError && mode == CalcMode.PROGRAMMER && radix == Radix.BIN) addPadding(text) else text
        }

        override fun onError(code: Int) {
            val kind = CalcErrorKind.fromCode(code)
            errorKind = kind
            lastErrorKind = kind
            errorCount++
        }

        override fun setIsInError(isError: Boolean) {
            inError = isError
        }

        override fun setExpressionDisplay(tokens: List<String>) {
            expressionTokens = tokens
        }

        override fun setParenthesisNumber(count: Int) {
            parenCount = count
        }

        override fun onNoRightParenAdded() = Unit
        override fun maxDigitsReached() = Unit
        override fun binaryOperatorReceived() = Unit
        override fun onHistoryItemAdded(index: Int) = Unit

        fun showUiError(kind: CalcErrorKind) {
            primary = kind.message
            inError = true
            errorKind = kind
            lastErrorKind = kind
            errorCount++
        }
    }

    private class HistoryList(private val max: Int) : HistoryDisplay {
        val items = ArrayList<HistoryItem>()

        override fun addToHistory(tokens: List<String>, result: String): Int {
            if (items.size >= max) items.removeAt(0)
            items.add(HistoryItem(tokens.joinToString(" "), result))
            return items.size - 1
        }
    }

    private fun disabledInError(): Set<String> = when (mode) {
        CalcMode.STANDARD -> STANDARD_ERROR_DISABLED
        CalcMode.SCIENTIFIC -> SCIENTIFIC_ERROR_DISABLED + (if (inv) setOf("pow10", "log") else emptySet())
        CalcMode.PROGRAMMER -> PROGRAMMER_ERROR_DISABLED
    }

    companion object {
        /** CalculatorManager.cpp MAX_HISTORY_ITEMS. */
        const val MAX_HISTORY_ITEMS = 20

        /** CalculatorManager.h m_maximumMemorySize. */
        const val MAX_MEMORY = 100

        private val DIGIT_KEYS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F")

        internal fun digitValue(key: String): Int? {
            val i = DIGIT_KEYS.indexOf(key)
            return if (i >= 0) i else null
        }

        private val STANDARD_KEYS = listOf(
            "percent", "sqrt", "square", "reciprocal",
            "clear_entry", "clear", "backspace", "divide",
            "7", "8", "9", "multiply", "4", "5", "6", "subtract", "1", "2", "3", "add",
            "negate", "0", "decimal", "equals",
            "mc", "mr", "mplus", "mminus", "ms",
        )
        private val SCIENTIFIC_KEYS = listOf(
            "angle", "hyp", "fe",
            "mc", "mr", "mplus", "mminus", "ms",
            "square", "pow", "sin", "cos", "tan",
            "sqrt", "pow10", "log", "exp", "mod",
            "inv", "clear_entry", "clear", "backspace", "divide",
            "pi", "7", "8", "9", "multiply",
            "factorial", "4", "5", "6", "subtract",
            "negate", "1", "2", "3", "add",
            "lparen", "rparen", "0", "decimal", "equals",
        )
        private val PROGRAMMER_KEYS = listOf(
            "radix_hex", "radix_dec", "radix_oct", "radix_bin", "word", "ms",
            "lsh", "rsh", "or", "xor", "not", "and",
            "inv", "mod", "clear_entry", "clear", "backspace", "divide",
            "A", "B", "7", "8", "9", "multiply",
            "C", "D", "4", "5", "6", "subtract",
            "E", "F", "1", "2", "3", "add",
            "lparen", "rparen", "negate", "0", "decimal", "equals",
        )

        internal fun keysFor(mode: CalcMode): List<String> = when (mode) {
            CalcMode.STANDARD -> STANDARD_KEYS
            CalcMode.SCIENTIFIC -> SCIENTIFIC_KEYS
            CalcMode.PROGRAMMER -> PROGRAMMER_KEYS
        }

        /** Keys whose function `inv` changes; `inv` turns off once one of them is used (ShiftButton_Uncheck). */
        private val SHIFTED_KEYS = setOf("square", "pow", "sin", "cos", "tan", "sqrt", "pow10", "log", "exp", "mod", "lsh", "rsh")

        // The ErrorLayout visual states of CalculatorStandardOperators.xaml, CalculatorScientificOperators.xaml,
        // CalculatorScientificAngleButtons.xaml, CalculatorProgrammerRadixOperators.xaml and Calculator.xaml (memory).
        private val MEMORY_KEYS = setOf("mc", "mr", "mplus", "mminus", "ms")
        private val STANDARD_ERROR_DISABLED =
            setOf("percent", "sqrt", "reciprocal", "divide", "multiply", "subtract", "add", "negate") + MEMORY_KEYS
        private val SCIENTIFIC_ERROR_DISABLED = setOf(
            "pow", "sin", "cos", "tan", "sqrt", "exp", "mod", "inv", "divide", "multiply", "subtract", "add", "pi",
            "factorial", "negate", "lparen", "rparen", "angle", "fe", "hyp",
        ) + MEMORY_KEYS
        private val PROGRAMMER_ERROR_DISABLED = setOf(
            "lsh", "rsh", "or", "xor", "not", "and", "mod", "divide", "multiply", "subtract", "add", "lparen", "rparen",
            "negate", "ms",
        )

        internal fun encodeRational(r: Rational): String = encodeNum(r.p) + "/" + encodeNum(r.q)

        private fun encodeNum(n: Num): String = "${n.sign},${n.exp},${n.mant.toString(16)}"

        internal fun decodeRational(s: String): Rational? {
            val pq = s.split('/')
            if (pq.size != 2) return null
            return Rational(decodeNum(pq[0]) ?: return null, decodeNum(pq[1]) ?: return null)
        }

        private fun decodeNum(s: String): Num? {
            val f = s.split(',')
            if (f.size != 3) return null
            val sign = f[0].toIntOrNull() ?: return null
            val exp = f[1].toIntOrNull() ?: return null
            val mant = runCatching { java.math.BigInteger(f[2], 16) }.getOrNull() ?: return null
            if ((sign != 1 && sign != -1) || mant.signum() < 0) return null
            return Num.of(sign, exp, mant, BASEX)
        }

        private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        private fun unescape(s: String): String? {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\') {
                    if (i + 1 >= s.length) return null
                    when (s[i + 1]) {
                        '\\' -> sb.append('\\')
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        else -> return null
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
    }
}

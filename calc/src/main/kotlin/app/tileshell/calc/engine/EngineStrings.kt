package app.tileshell.calc.engine

/** The engine command ids of Header Files/CCommand.h (and the manager's combined ids of Command.h). */
internal object Op {
    const val HEX = 313
    const val DEC = 314
    const val OCT = 315
    const val BIN = 316
    const val QWORD = 317
    const val DWORD = 318
    const val WORD = 319
    const val BYTE = 320
    const val DEG = 321
    const val RAD = 322
    const val GRAD = 323
    const val DEGREES = 324

    const val SIGN = 80
    const val CLEAR = 81
    const val CENTR = 82
    const val BACK = 83
    const val PNT = 84
    const val AND = 86
    const val OR = 87
    const val XOR = 88
    const val LSHF = 89
    const val RSHF = 90
    const val DIV = 91
    const val MUL = 92
    const val ADD = 93
    const val SUB = 94
    const val MOD = 95
    const val ROOT = 96
    const val PWR = 97
    const val CHOP = 98
    const val ROL = 99
    const val ROR = 100
    const val COM = 101
    const val SIN = 102
    const val COS = 103
    const val TAN = 104
    const val SINH = 105
    const val COSH = 106
    const val TANH = 107
    const val LN = 108
    const val LOG = 109
    const val SQRT = 110
    const val SQR = 111
    const val CUB = 112
    const val FAC = 113
    const val REC = 114
    const val DMS = 115
    const val CUBEROOT = 116
    const val POW10 = 117
    const val PERCENT = 118
    const val FE = 119
    const val PI = 120
    const val EQU = 121
    const val MCLEAR = 122
    const val RECALL = 123
    const val STORE = 124
    const val MPLUS = 125
    const val MMINUS = 126
    const val EXP = 127
    const val OPENP = 128
    const val CLOSEP = 129
    const val D0 = 130
    const val DF = 145
    const val INV = 146
    const val SET_RESULT = 147

    const val UNARYFIRST = CHOP
    const val UNARYLAST = PERCENT
    const val SEC = 400
    const val CSC = 402
    const val COT = 404
    const val SECH = 406
    const val CSCH = 408
    const val COTH = 410
    const val POW2 = 412
    const val ABS = 413
    const val FLOOR = 414
    const val CEIL = 415
    const val ROLC = 416
    const val RORC = 417
    const val UNARYEXTENDEDFIRST = 400
    const val UNARYEXTENDEDLAST = RORC
    const val BINARYEXTENDEDFIRST = 500
    const val LOGBASEY = 500
    const val NAND = 501
    const val NOR = 502
    const val RSHFL = 505
    const val BINARYEXTENDEDLAST = RSHFL
    const val RAND = 600
    const val EULER = 601
    const val BINEDITSTART = 700
    const val BINEDITEND = 763

    const val FIRSTCONTROL = SIGN

    // CalcUtils.cpp
    fun inRange(op: Int, x: Int, y: Int): Boolean = op in x..y
    fun isBinOpCode(op: Int): Boolean = inRange(op, AND, PWR) || inRange(op, BINARYEXTENDEDFIRST, BINARYEXTENDEDLAST)
    fun isUnaryOpCode(op: Int): Boolean = inRange(op, UNARYFIRST, UNARYLAST) || inRange(op, UNARYEXTENDEDFIRST, UNARYEXTENDEDLAST)
    fun isDigitOpCode(op: Int): Boolean = inRange(op, D0, DF)
    fun isGuiSettingOpCode(op: Int): Boolean {
        if (inRange(op, HEX, BIN) || inRange(op, QWORD, BYTE) || inRange(op, DEG, GRAD)) return true
        return when (op) {
            INV, FE, MCLEAR, BACK, EXP, STORE, MPLUS, MMINUS -> true
            else -> false
        }
    }
}

/**
 * The en-US engine strings: src/Calculator/Resources/en-US/CEngineStrings.resw at 4fd3fc5 (only the ids that file
 * defines; a missing id reads as the empty string, as s_engineStrings does), plus the lookup tables of scicomm.cpp
 * (operatorStringTable, OpCodeToUnaryString, OpCodeToBinaryString).
 */
internal object EngineStrings {
    private val table: Map<String, String> = mapOf(
        "2" to "CE", "4" to ".", "6" to "AND", "7" to "OR", "8" to "XOR", "9" to "Lsh", "10" to "Rsh",
        "11" to "÷", "12" to "×", "13" to "+", "14" to "-", "15" to "Mod", "16" to "yroot", "17" to "^",
        "18" to "Int", "19" to "RoL", "20" to "RoR", "21" to "NOT", "22" to "sin", "23" to "cis", "24" to "tan",
        "25" to "sinh", "26" to "cosh", "27" to "tanh", "28" to "ln", "29" to "log", "30" to "√", "35" to "dms",
        "37" to "10^", "38" to "%", "40" to "Pi", "41" to "=", "47" to "Exp", "48" to "(", "49" to ")",
        "66" to "frac", "67" to "sin₀", "68" to "cos₀", "69" to "tan₀", "70" to "sin₀⁻¹", "71" to "cos₀⁻¹",
        "72" to "tan₀⁻¹", "73" to "sinᵣ", "74" to "cosᵣ", "75" to "tanᵣ", "76" to "sinᵣ⁻¹", "77" to "cosᵣ⁻¹",
        "78" to "tanᵣ⁻¹", "79" to "sin₉", "80" to "cos₉", "81" to "tan₉", "82" to "sin₉⁻¹", "83" to "cos₉⁻¹",
        "84" to "tan₉⁻¹", "85" to "sinh⁻¹", "86" to "cosh⁻¹", "87" to "tanh⁻¹", "88" to "e^", "89" to "10^",
        "90" to "√", "91" to "sqr", "92" to "cube", "94" to "fact", "95" to "1/", "96" to "degrees", "97" to "negate",
        "99" to "Cannot divide by zero", "100" to "Invalid input", "101" to "Result is undefined",
        "105" to "Not enough memory", "107" to "Overflow", "108" to "Result not defined", "118" to "Result not defined",
        "119" to "Overflow", "120" to "Overflow",
        "SecDeg" to "sec₀", "SecRad" to "secᵣ", "SecGrad" to "sec₉", "InverseSecDeg" to "sec₀⁻¹",
        "InverseSecRad" to "secᵣ⁻¹", "InverseSecGrad" to "sec₉⁻¹", "CscDeg" to "csc₀", "CscRad" to "cscᵣ",
        "CscGrad" to "csc₉", "InverseCscDeg" to "csc₀⁻¹", "InverseCscRad" to "cscᵣ⁻¹", "InverseCscGrad" to "csc₉⁻¹",
        "CotDeg" to "cot₀", "CotRad" to "cotᵣ", "CotGrad" to "cot₉", "InverseCotDeg" to "cot₀⁻¹",
        "InverseCotRad" to "cotᵣ⁻¹", "InverseCotGrad" to "cot₉⁻¹", "Sech" to "sech", "InverseSech" to "sech⁻¹",
        "Csch" to "csch", "InverseCsch" to "csch⁻¹", "Coth" to "coth", "InverseCoth" to "coth⁻¹", "TwoPowX" to "2^",
        "LogBaseY" to "log base", "Abs" to "abs", "Ceil" to "ceil", "Floor" to "floor", "Nand" to "NAND",
        "Nor" to "NOR", "CubeRoot" to "cuberoot", "ProgrammerMod" to "%",
    )

    fun getString(id: String): String = table[id] ?: ""

    /** IDS_ERRORS_FIRST (99) + SCODE_CODE(error). */
    fun errorString(code: Int): String = getString((99 + (code and 0xFFFF)).toString())

    private fun idStrFromCmdId(id: Int): Int = id - Op.FIRSTCONTROL

    fun opCodeToString(op: Int): String = getString(idStrFromCmdId(op).toString())

    private class FunctionNameElement(
        val degreeString: String,
        val inverseDegreeString: String = "",
        val radString: String = "",
        val inverseRadString: String = "",
        val gradString: String = "",
        val inverseGradString: String = "",
        val programmerModeString: String = "",
    ) {
        val hasAngleStrings = radString.isNotEmpty() || inverseRadString.isNotEmpty() || gradString.isNotEmpty() ||
            inverseGradString.isNotEmpty()
    }

    private val operatorStringTable: Map<Int, FunctionNameElement> = mapOf(
        Op.CHOP to FunctionNameElement("", "66"),
        Op.SIN to FunctionNameElement("67", "70", "73", "76", "79", "82"),
        Op.COS to FunctionNameElement("68", "71", "74", "77", "80", "83"),
        Op.TAN to FunctionNameElement("69", "72", "75", "78", "81", "84"),
        Op.SINH to FunctionNameElement("", "85"),
        Op.COSH to FunctionNameElement("", "86"),
        Op.TANH to FunctionNameElement("", "87"),
        Op.SEC to FunctionNameElement("SecDeg", "InverseSecDeg", "SecRad", "InverseSecRad", "SecGrad", "InverseSecGrad"),
        Op.CSC to FunctionNameElement("CscDeg", "InverseCscDeg", "CscRad", "InverseCscRad", "CscGrad", "InverseCscGrad"),
        Op.COT to FunctionNameElement("CotDeg", "InverseCotDeg", "CotRad", "InverseCotRad", "CotGrad", "InverseCotGrad"),
        Op.SECH to FunctionNameElement("Sech", "InverseSech"),
        Op.CSCH to FunctionNameElement("Csch", "InverseCsch"),
        Op.COTH to FunctionNameElement("Coth", "InverseCoth"),
        Op.LN to FunctionNameElement("", "88"),
        Op.SQR to FunctionNameElement("91"),
        Op.CUB to FunctionNameElement("92"),
        Op.FAC to FunctionNameElement("94"),
        Op.REC to FunctionNameElement("95"),
        Op.DMS to FunctionNameElement("", "96"),
        Op.SIGN to FunctionNameElement("97"),
        Op.DEGREES to FunctionNameElement("96"),
        Op.POW2 to FunctionNameElement("TwoPowX"),
        Op.LOGBASEY to FunctionNameElement("LogBaseY"),
        Op.ABS to FunctionNameElement("Abs"),
        Op.CEIL to FunctionNameElement("Ceil"),
        Op.FLOOR to FunctionNameElement("Floor"),
        Op.NAND to FunctionNameElement("Nand"),
        Op.NOR to FunctionNameElement("Nor"),
        Op.RSHFL to FunctionNameElement("10"),
        Op.RORC to FunctionNameElement("20"),
        Op.ROLC to FunctionNameElement("19"),
        Op.CUBEROOT to FunctionNameElement("CubeRoot"),
        Op.MOD to FunctionNameElement("15", programmerModeString = "ProgrammerMod"),
    )

    fun opCodeToUnaryString(op: Int, fInv: Boolean, angletype: AngleType): String {
        var ids = ""
        val element = operatorStringTable[op]
        if (element != null) {
            if (!element.hasAngleStrings || angletype == AngleType.Degrees) {
                if (fInv) ids = element.inverseDegreeString
                if (ids.isEmpty()) ids = element.degreeString
            } else if (angletype == AngleType.Radians) {
                if (fInv) ids = element.inverseRadString
                if (ids.isEmpty()) ids = element.radString
            } else {
                if (fInv) ids = element.inverseGradString
                if (ids.isEmpty()) ids = element.gradString
            }
        }
        if (ids.isNotEmpty()) return getString(ids)
        return opCodeToString(op)
    }

    fun opCodeToBinaryString(op: Int, isIntegerMode: Boolean): String {
        var ids = ""
        val element = operatorStringTable[op]
        if (element != null) {
            ids = if (isIntegerMode && element.programmerModeString.isNotEmpty()) element.programmerModeString else element.degreeString
        }
        if (ids.isNotEmpty()) return getString(ids)
        return opCodeToString(op)
    }
}

package app.tileshell.calc.engine

import java.util.regex.Pattern

/**
 * Port of src/Calculator.ViewModels/Common/CopyPasteManager.cs (microsoft/calculator 4fd3fc5): which pasted text
 * Windows accepts in each mode (en-US: the localized digits are the English ones and "," is the group separator).
 * The keys the accepted text becomes are StandardCalculatorViewModel.cs OnPaste / MapCharacterToButtonId, ported in
 * [Calculator.paste].
 */
internal object CopyPaste {
    private const val MAX_STANDARD_OPERAND_LENGTH = 16
    private const val MAX_SCIENTIFIC_OPERAND_LENGTH = 32
    private const val MAX_OPERAND_COUNT = 100
    private const val MAX_EXPONENT_LENGTH = 4
    private const val MAX_PROGRAMMER_BIT_LENGTH = 64
    private const val MAX_PASTEABLE_LENGTH = 512

    private const val VALID_BASIC = "0123456789+-.e"
    private const val VALID_STANDARD = "$VALID_BASIC*/"
    private const val VALID_SCIENTIFIC = "$VALID_STANDARD()^%"
    private const val VALID_PROGRAMMER = "$VALID_STANDARD()%abcdfABCDEF"

    private const val WSPC = "[\\s\\x85]*"
    private const val WSPC_LPARENS = "$WSPC[(]*$WSPC"
    private const val WSPC_LPAREN_SIGNED = "$WSPC([-+]?[(])*$WSPC"
    private const val WSPC_RPARENS = "$WSPC[)]*$WSPC"
    private const val SIGNED_DEC_FLOAT = "(?:[-+]?(?:[0-9]+(\\.[0-9]*)?|\\.[0-9]+))"
    private const val OPTIONAL_E_NOTATION = "(?:e[+-]?[0-9]+)?"

    private const val HEX_CHARS = "([a-f]|[A-F]|\\d)+((_|'|`)([a-f]|[A-F]|\\d)+)*"
    private const val DEC_CHARS = "\\d+((_|'|`)\\d+)*"
    private const val OCT_CHARS = "[0-7]+((_|'|`)[0-7]+)*"
    private const val BIN_CHARS = "[0-1]+((_|'|`)[0-1]+)*"
    private const val UINT_SUFFIXES = "[uU]?[lL]{0,2}"

    /** .NET Regex semantics for \s and \d are Unicode-aware; FullMatch wraps in \A(?:...)\z. */
    private fun fullMatch(p: String): Pattern = Pattern.compile("\\A(?:$p)\\z", Pattern.UNICODE_CHARACTER_CLASS)

    private val standardPatterns = listOf(fullMatch(WSPC + SIGNED_DEC_FLOAT + OPTIONAL_E_NOTATION + WSPC))
    private val scientificPatterns = listOf(
        fullMatch("($WSPC[-+]?)|($WSPC_LPAREN_SIGNED)$SIGNED_DEC_FLOAT$OPTIONAL_E_NOTATION$WSPC_RPARENS"),
    )
    private val programmerPatterns: Map<Radix, List<Pattern>> = mapOf(
        Radix.HEX to listOf(
            fullMatch("$WSPC_LPARENS(0[xX])?$HEX_CHARS$UINT_SUFFIXES$WSPC_RPARENS"),
            fullMatch("$WSPC_LPARENS$HEX_CHARS[hH]?$WSPC_RPARENS"),
        ),
        Radix.DEC to listOf(
            fullMatch("$WSPC_LPARENS[-+]?$DEC_CHARS[lL]{0,2}$WSPC_RPARENS"),
            fullMatch("$WSPC_LPARENS(0[nN])?$DEC_CHARS$UINT_SUFFIXES$WSPC_RPARENS"),
        ),
        Radix.OCT to listOf(fullMatch("$WSPC_LPARENS(0[otOT])?$OCT_CHARS$UINT_SUFFIXES$WSPC_RPARENS")),
        Radix.BIN to listOf(
            fullMatch("$WSPC_LPARENS(0[byBY])?$BIN_CHARS$UINT_SUFFIXES$WSPC_RPARENS"),
            fullMatch("$WSPC_LPARENS$BIN_CHARS[bB]?$WSPC_RPARENS"),
        ),
    )

    /** ValidatePasteExpression: true when Windows accepts the text (it then pastes the original text). */
    fun validate(pastedText: String, mode: CalcMode, radix: Radix, word: WordSize): Boolean {
        if (pastedText.length > MAX_PASTEABLE_LENGTH) return false
        var pasteExpression = removeUnwantedCharsFromString(pastedText)
        if (pasteExpression.isNotEmpty() && pasteExpression[pasteExpression.length - 1] == '=') {
            pasteExpression = pasteExpression.substring(0, pasteExpression.length - 1)
        }
        if (mode == CalcMode.SCIENTIFIC && pasteExpression.none { it in '0'..'9' }) return false
        val operands = extractOperands(pasteExpression, mode)
        if (operands.isEmpty()) return false
        return expressionRegExMatch(operands, mode, radix, word)
    }

    fun extractOperands(pasteExpression: String, mode: CalcMode): List<String> {
        val operands = ArrayList<String>()
        var lastIndex = 0
        var haveOperator = false
        var startExpCounting = false
        var startOfExpression = true
        var isPreviousOpenParen = false
        var isPreviousOperator = false
        val validCharacterSet = when (mode) {
            CalcMode.STANDARD -> VALID_STANDARD
            CalcMode.SCIENTIFIC -> VALID_SCIENTIFIC
            CalcMode.PROGRAMMER -> VALID_PROGRAMMER
        }
        var expLength = 0
        for (i in pasteExpression.indices) {
            val currentChar = pasteExpression[i]
            if (validCharacterSet.indexOf(currentChar) < 0) continue
            if (operands.size >= MAX_OPERAND_COUNT) {
                operands.clear()
                return operands
            }
            if (currentChar in '0'..'9') {
                if (startExpCounting) {
                    expLength++
                    if (expLength > MAX_EXPONENT_LENGTH) {
                        operands.clear()
                        return operands
                    }
                }
                isPreviousOperator = false
            } else if (currentChar == 'e') {
                if (mode != CalcMode.PROGRAMMER) startExpCounting = true
                isPreviousOperator = false
            } else if (currentChar == '+' || currentChar == '-' || currentChar == '*' || currentChar == '/' ||
                currentChar == '^' || currentChar == '%'
            ) {
                if (currentChar == '+' || currentChar == '-') {
                    if (isPreviousOpenParen || startOfExpression || isPreviousOperator ||
                        (mode != CalcMode.PROGRAMMER && !(i != 0 && pasteExpression[i - 1] != 'e'))
                    ) {
                        isPreviousOperator = false
                        continue
                    }
                }
                startExpCounting = false
                expLength = 0
                haveOperator = true
                isPreviousOperator = true
                operands.add(pasteExpression.substring(lastIndex, i))
                lastIndex = i + 1
            } else {
                isPreviousOperator = false
            }
            isPreviousOpenParen = currentChar == '('
            startOfExpression = false
        }
        if (!haveOperator) {
            operands.clear()
            operands.add(pasteExpression)
        } else {
            operands.add(pasteExpression.substring(lastIndex))
        }
        return operands
    }

    private fun expressionRegExMatch(operands: List<String>, mode: CalcMode, radix: Radix, word: WordSize): Boolean {
        if (operands.isEmpty()) return false
        val patterns = when (mode) {
            CalcMode.STANDARD -> standardPatterns
            CalcMode.SCIENTIFIC -> scientificPatterns
            CalcMode.PROGRAMMER -> programmerPatterns.getValue(radix)
        }
        val (maxLength, maxValue) = maxOperandLengthAndValue(mode, radix, word)
        var expMatched = true
        for (operand in operands) {
            val operandMatched = patterns.any { it.matcher(operand).matches() }
            if (operandMatched) {
                val isNegativeValue = operand.isNotEmpty() && operand[0] == '-'
                val operandValue = sanitizeOperand(operand)
                if (operandLength(operandValue, mode, radix) > maxLength) {
                    expMatched = false
                    break
                }
                if (maxValue != 0uL) {
                    val v = tryOperandToULL(operandValue, radix)
                    if (v == null) {
                        expMatched = false
                        break
                    }
                    val isOverflow = v > maxValue
                    val isMaxNegativeValue = v - 1uL == maxValue
                    if (isOverflow && !(isNegativeValue && isMaxNegativeValue)) {
                        expMatched = false
                        break
                    }
                }
            }
            expMatched = expMatched && operandMatched
        }
        return expMatched
    }

    private fun maxOperandLengthAndValue(mode: CalcMode, radix: Radix, word: WordSize): Pair<Int, ULong> = when (mode) {
        CalcMode.STANDARD -> Pair(MAX_STANDARD_OPERAND_LENGTH, 0uL)
        CalcMode.SCIENTIFIC -> Pair(MAX_SCIENTIFIC_OPERAND_LENGTH, 0uL)
        CalcMode.PROGRAMMER -> {
            val bitLength = word.bits
            // Math.Log(radix) / Math.Log(2): exact for 2, 8 and 16.
            val bitsPerDigit = when (radix) {
                Radix.BIN -> 1.0
                Radix.OCT -> 3.0
                Radix.HEX -> 4.0
                Radix.DEC -> Math.log(10.0) / Math.log(2.0)
            }
            val signBit = if (radix == Radix.DEC) 1 else 0
            val maxLength = Math.ceil((bitLength - signBit) / bitsPerDigit).toInt()
            val maxValue = ULong.MAX_VALUE shr (MAX_PROGRAMMER_BIT_LENGTH - (bitLength - signBit))
            Pair(maxLength, maxValue)
        }
    }

    private fun sanitizeOperand(operand: String): String = operand.filter { it !in "'_`()-+" }

    private fun tryOperandToULL(operand: String, radix: Radix): ULong? {
        if (operand.isEmpty() || operand[0] == '-') return null
        val intBase = radix.base
        var index = 0
        while (index < operand.length && operand[index].isWhitespace()) index++
        if (index + 1 < operand.length && operand[index] == '0') {
            val prefix = operand[index + 1].uppercaseChar()
            val hasRadixPrefix = (intBase == 16 && prefix == 'X') || (intBase == 10 && prefix == 'N') ||
                (intBase == 8 && (prefix == 'O' || prefix == 'T')) || (intBase == 2 && (prefix == 'B' || prefix == 'Y'))
            val firstDigit = if (index + 2 < operand.length) hexValue(operand[index + 2]) else -1
            if (hasRadixPrefix && firstDigit >= 0 && firstDigit < intBase) index += 2
        }
        var value = 0uL
        var digitCount = 0
        while (index < operand.length) {
            val digit = hexValue(operand[index])
            if (digit < 0 || digit >= intBase) break
            val next = value * intBase.toULong() + digit.toULong()
            // checked((value * base) + digit)
            if (value > (ULong.MAX_VALUE - digit.toULong()) / intBase.toULong()) return null
            value = next
            index++
            digitCount++
        }
        return if (digitCount == 0) null else value
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private fun operandLength(operand: String, mode: CalcMode, radix: Radix): Int = when (mode) {
        CalcMode.STANDARD, CalcMode.SCIENTIFIC -> standardScientificOperandLength(operand)
        CalcMode.PROGRAMMER -> programmerOperandLength(operand, radix)
    }

    private fun standardScientificOperandLength(operand: String): Int {
        val hasDecimal = operand.contains('.')
        var length = operand.length
        if (hasDecimal && length >= 2) {
            length -= if (operand[0] == '0' && operand[1] == '.') 2 else 1
        }
        val exponentPos = operand.indexOf('e')
        if (exponentPos >= 0) length -= operand.length - exponentPos
        return length
    }

    private fun programmerOperandLength(operand: String, radix: Radix): Int {
        val prefixes = ArrayList<String>()
        val suffixes = ArrayList<String>()
        when (radix) {
            Radix.BIN -> {
                prefixes.addAll(listOf("0B", "0Y"))
                suffixes.add("B")
            }
            Radix.DEC -> prefixes.addAll(listOf("-", "0N"))
            Radix.OCT -> prefixes.addAll(listOf("0T", "0O"))
            Radix.HEX -> {
                prefixes.add("0X")
                suffixes.add("H")
            }
        }
        suffixes.addAll(listOf("ULL", "UL", "LL", "U", "L"))
        val operandUpper = operand.uppercase()
        var len = operand.length
        for (suffix in suffixes) {
            if (len < suffix.length) continue
            if (operandUpper.endsWith(suffix)) {
                len -= suffix.length
                break
            }
        }
        for (prefix in prefixes) {
            if (len < prefix.length) continue
            if (operandUpper.startsWith(prefix)) {
                len -= prefix.length
                break
            }
        }
        return len
    }

    /** RemoveUnwantedCharsFromString: group separators, spaces, quotes and currency symbols go. */
    private fun removeUnwantedCharsFromString(input: String): String {
        val unwanted = charArrayOf(
            ' ', ',', '"', 165.toChar(), 164.toChar(), 8373.toChar(), '$', 8353.toChar(), 8361.toChar(), 8362.toChar(),
            8358.toChar(), 8377.toChar(), 163.toChar(), 8364.toChar(), 8234.toChar(), 8235.toChar(), 8236.toChar(),
            8237.toChar(), 160.toChar(),
        )
        return input.filter { it != ' ' && it != ',' }.filter { it !in unwanted }
    }
}

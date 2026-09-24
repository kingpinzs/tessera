package app.tileshell.calc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Calculator state machine against the phase 15 rules (docs/plan/phase-15-inbox-clock-calculator-recorder.md,
 * Decision "Calculator engine", E11 / E12; docs/plan/r11/calculator.md 8.1-8.6; docs/plan/qa/phase-15/calc-keys.md).
 * Every expectation is Windows Calculator's (microsoft/calculator 4fd3fc5), cited per test.
 */
class CalculatorTest {
    private fun calc(mode: CalcMode = CalcMode.STANDARD, keys: String = ""): Calculator {
        val c = Calculator(mode)
        c.pressAll(keys)
        return c
    }

    private fun Calculator.pressAll(keys: String) {
        for (k in keys.split(' ')) if (k.isNotEmpty()) assertTrue("key $k refused", press(k))
    }

    private fun display(mode: CalcMode, keys: String) = calc(mode, keys).displayText

    // ---- E11's named cases (phase doc :1040-1052)

    @Test fun exactRationals() {
        assertEquals("0.3", display(CalcMode.STANDARD, "0 decimal 1 add 0 decimal 2 equals"))
        assertEquals("1", display(CalcMode.STANDARD, "1 divide 3 multiply 3 equals"))
    }

    @Test fun immediateExecutionVersusPrecedence() {
        // CalculatorManager.cpp:164 (Standard: false /* Respect Order of Operations */), :183 and :201 (true).
        assertEquals("20", display(CalcMode.STANDARD, "2 add 3 multiply 4 equals"))
        assertEquals("14", display(CalcMode.SCIENTIFIC, "2 add 3 multiply 4 equals"))
        assertEquals("14", display(CalcMode.PROGRAMMER, "2 add 3 multiply 4 equals"))
    }

    @Test fun percentAndRepeatedEquals() {
        // scifunc.cpp:98-111 (x op y% = x op x*y/100); scicomm.cpp ResolveHighestPrecedenceOperation (= repeats).
        assertEquals("92", display(CalcMode.STANDARD, "8 0 add 1 5 percent equals"))
        assertEquals("8", display(CalcMode.STANDARD, "2 multiply equals equals"))
        assertEquals("14", display(CalcMode.STANDARD, "5 add 3 equals equals equals"))
    }

    @Test fun trigInDegRadGrad() {
        assertEquals("0.5", display(CalcMode.SCIENTIFIC, "3 0 sin"))
        assertEquals("-0.98803162409286178998774890729446", display(CalcMode.SCIENTIFIC, "angle 3 0 sin"))
        assertEquals("0.45399049973954679156040836635787", display(CalcMode.SCIENTIFIC, "angle angle 3 0 sin"))
    }

    @Test fun factorial200AndENotationPerPrecision() {
        // r11/calculator.md 8.3-8.4: e-notation past the precision; 16 significant digits in Standard, 32 in Scientific.
        val c = calc(CalcMode.SCIENTIFIC, "2 0 0 factorial")
        assertEquals("7.8865786736479050355236321393219e+374", c.displayText)
        c.setMode(CalcMode.STANDARD)
        assertEquals("7.886578673647905e+374", c.displayText)
        assertEquals("1.e+16", display(CalcMode.STANDARD, "1 0 0 0 0 0 0 0 0 multiply 1 0 0 0 0 0 0 0 0 equals"))
        assertEquals("9.99999998e+17", display(CalcMode.STANDARD, "9 9 9 9 9 9 9 9 9 multiply 9 9 9 9 9 9 9 9 9 equals"))
        assertEquals("3.333333333333333e-4", display(CalcMode.STANDARD, "1 divide 3 0 0 0 equals"))
        assertEquals("0.0033333333333333", display(CalcMode.STANDARD, "1 divide 3 0 0 equals"))
    }

    @Test fun errorStringsAndKinds() {
        // CEngineStrings.resw ids 99, 101, 100, 107/119/120, 108.
        var c = calc(CalcMode.STANDARD, "1 divide 0 equals")
        assertEquals("Cannot divide by zero", c.displayText)
        assertTrue(c.isError)
        assertEquals(CalcErrorKind.DIVIDE_BY_ZERO, c.errorKind)
        // History.cpp: the expression line stays as it was (the operand is added to the tokens but not shown until
        // the next SetExpressionDisplay, and DisplayError leaves the line alone).
        assertEquals("1 ÷", c.expression)

        c = calc(CalcMode.STANDARD, "0 divide 0 equals")
        assertEquals("Result is undefined", c.displayText)
        assertEquals(CalcErrorKind.UNDEFINED, c.errorKind)

        c = calc(CalcMode.STANDARD, "1 negate sqrt")
        assertEquals("Invalid input", c.displayText)
        assertEquals(CalcErrorKind.INVALID_INPUT, c.errorKind)

        c = calc(CalcMode.SCIENTIFIC, "1 0 pow 1 0 0 0 0 equals")
        assertEquals("Overflow", c.displayText)
        assertEquals(CalcErrorKind.OVERFLOW, c.errorKind)

        // The kind of the last error is kept after it clears (the `[calc] error <kind>` line); the current one is not.
        c.press("clear")
        assertFalse(c.isError)
        assertNull(c.errorKind)
        assertEquals(CalcErrorKind.OVERFLOW, c.lastErrorKind)
        assertEquals("0", c.displayText)
    }

    @Test fun shiftsPastTheWordSizeMoveEveryBitOut() {
        // Windows shows "Result not defined" here; the shell gives the answer its source comment states (2026-09-24).
        assertEquals("0", calc(CalcMode.PROGRAMMER, "1 lsh 6 4 equals").displayText)
        assertEquals("0", calc(CalcMode.PROGRAMMER, "2 5 6 rsh 6 4 equals").displayText)
        assertEquals("-1", calc(CalcMode.PROGRAMMER, "2 5 6 negate rsh 6 4 equals").displayText)
        assertEquals("-9,223,372,036,854,775,808", calc(CalcMode.PROGRAMMER, "1 lsh 6 3 equals").displayText)
    }

    @Test fun errorRecoveryFollowsTheViewModel() {
        // StandardCalculatorViewModel.cs:1411-1420 + IsRecoverableCommand :1997-2012: a digit after an error clears it
        // and starts a new number; an operator is disabled by the ErrorLayout state and does nothing.
        val c = calc(CalcMode.STANDARD, "1 divide 0 equals")
        assertFalse(c.isEnabled("add"))
        assertFalse(c.press("add"))
        assertEquals("Cannot divide by zero", c.displayText)
        assertTrue(c.isEnabled("5"))
        c.press("5")
        assertEquals("5", c.displayText)
        assertFalse(c.isError)
        // CE acts as C in the error state (scicomm.cpp:138-142).
        assertEquals("0", display(CalcMode.STANDARD, "1 divide 0 equals clear_entry"))
    }

    // ---- Programmer (r11/calculator.md 4.3-4.5, 4.11-4.15)

    @Test fun programmerRadixRows() {
        val c = calc(CalcMode.PROGRAMMER, "2 0 2 4 5")
        assertEquals("20,245", c.displayText)
        assertEquals("4F15", c.radixValue(Radix.HEX))
        assertEquals("20,245", c.radixValue(Radix.DEC))
        assertEquals("47 425", c.radixValue(Radix.OCT))
        assertEquals("0100 1111 0001 0101", c.radixValue(Radix.BIN))
        assertEquals(4, c.radixValues.size)
        c.press("radix_bin")
        assertEquals(Radix.BIN, c.radix)
        assertEquals("0100 1111 0001 0101", c.displayText) // the main display is padded to the nibble too
        c.press("radix_hex")
        assertEquals("4F15", c.displayText)
        c.press("radix_oct")
        assertEquals("47 425", c.displayText)
        // Outside Programmer the rows are empty.
        c.setMode(CalcMode.STANDARD)
        assertEquals("", c.radixValue(Radix.HEX))
    }

    @Test fun programmerWrapsToTheWordSizeInTwosComplement() {
        assertEquals("0", display(CalcMode.PROGRAMMER, "word word word radix_hex F F add 1 equals"))
        assertEquals("-128", display(CalcMode.PROGRAMMER, "word word word 1 2 7 add 1 equals"))
        assertEquals("FFFF FFFF FFFF FFFF", display(CalcMode.PROGRAMMER, "1 negate radix_hex"))
        assertEquals("FFFF", display(CalcMode.PROGRAMMER, "word word radix_hex 0 not"))
        assertEquals("-2,147,483,648", display(CalcMode.PROGRAMMER, "word 2 1 4 7 4 8 3 6 4 7 add 1 equals"))
        assertEquals("3", display(CalcMode.PROGRAMMER, "7 divide 2 equals")) // integer division
        val c = calc(CalcMode.PROGRAMMER)
        assertEquals(WordSize.QWORD, c.wordSize)
        c.press("word")
        assertEquals(WordSize.DWORD, c.wordSize)
        c.pressAll("word word word")
        assertEquals(WordSize.QWORD, c.wordSize)
    }

    @Test fun programmerKeyRules() {
        val c = calc(CalcMode.PROGRAMMER)
        assertFalse(c.isEnabled("decimal"))
        assertFalse(c.press("decimal"))
        assertFalse(c.isEnabled("A"))
        assertTrue(c.isEnabled("9"))
        c.press("radix_hex")
        assertTrue(c.isEnabled("A"))
        c.press("radix_oct")
        assertFalse(c.isEnabled("8"))
        assertTrue(c.isEnabled("7"))
        c.press("radix_bin")
        assertFalse(c.isEnabled("2"))
        assertTrue(c.isEnabled("1"))
        assertTrue(c.history.isEmpty())
        c.pressAll("radix_dec 2 add 3 equals")
        assertTrue(c.history.isEmpty())
        // The bitwise rows and shifts.
        assertEquals("8", display(CalcMode.PROGRAMMER, "radix_hex C and A equals"))
        assertEquals("E", display(CalcMode.PROGRAMMER, "radix_hex C or A equals"))
        assertEquals("6", display(CalcMode.PROGRAMMER, "radix_hex C xor A equals"))
        assertEquals("16", display(CalcMode.PROGRAMMER, "1 lsh 4 equals"))
        assertEquals("4", display(CalcMode.PROGRAMMER, "1 6 rsh 2 equals"))
        assertEquals("10", display(CalcMode.PROGRAMMER, "5 inv lsh")) // RoL under inv
    }

    // ---- modes keep the value (E12)

    @Test fun switchingModeKeepsTheDisplayValue() {
        val c = calc(CalcMode.STANDARD, "2 5 5")
        c.setMode(CalcMode.PROGRAMMER)
        assertEquals(CalcMode.PROGRAMMER, c.mode)
        assertEquals("255", c.displayText)
        assertEquals("FF", c.radixValue(Radix.HEX))
        c.setMode(CalcMode.SCIENTIFIC)
        assertEquals("255", c.displayText)
        c.setMode(CalcMode.STANDARD)
        assertEquals("255", c.displayText)
        // The EXACT value is carried (CalculatorManager's PersistedMemObject + IDC_RECALL, the path
        // LoadPersistedPrimaryValue uses), so a fraction re-displays at the new precision. (The pinned source itself
        // clears the display on a mode switch, CalculatorManager.cpp:169/188/206; keeping it is phase 15's E12.)
        val q = calc(CalcMode.STANDARD, "1 divide 3 equals")
        assertEquals("0.3333333333333333", q.displayText)
        q.setMode(CalcMode.SCIENTIFIC)
        assertEquals("0.33333333333333333333333333333333", q.displayText)
        // A value typed in HEX comes back as its decimal.
        val p = calc(CalcMode.PROGRAMMER, "radix_hex F F")
        p.setMode(CalcMode.STANDARD)
        assertEquals("255", p.displayText)
        // An error is not carried; the new mode starts at 0.
        val e = calc(CalcMode.STANDARD, "1 divide 0 equals")
        e.setMode(CalcMode.SCIENTIFIC)
        assertEquals("0", e.displayText)
        assertFalse(e.isError)
        // Leaving Scientific turns F-E off.
        val f = calc(CalcMode.SCIENTIFIC, "1 2 3 fe")
        assertTrue(f.fe)
        f.setMode(CalcMode.STANDARD)
        assertFalse(f.fe)
        assertEquals("123", f.displayText)
    }

    // ---- entry, display and grouping (r11/calculator.md 2.15)

    @Test fun groupingAndEntryLimits() {
        assertEquals("1,234,567", display(CalcMode.STANDARD, "1 2 3 4 5 6 7"))
        assertEquals("1,234.5678", display(CalcMode.STANDARD, "1 2 3 4 decimal 5 6 7 8"))
        assertEquals("-1,234,567", display(CalcMode.STANDARD, "1 2 3 4 5 6 7 negate"))
        // The 17th digit is refused (CalcInput.cpp against the 16-digit precision); press returns true, nothing changes.
        assertEquals("1,234,567,890,123,456", display(CalcMode.STANDARD, "1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7"))
        assertEquals("5", display(CalcMode.STANDARD, "0 0 5"))
        assertEquals("1.23", display(CalcMode.STANDARD, "1 decimal 2 decimal 3"))
        assertEquals("12", display(CalcMode.STANDARD, "1 2 3 backspace"))
        assertEquals("0", display(CalcMode.STANDARD, "1 2 3 backspace backspace backspace"))
        assertEquals("1.", display(CalcMode.STANDARD, "1 decimal 5 backspace"))
        assertEquals("1.e+3", display(CalcMode.SCIENTIFIC, "1 exp 3"))
        assertEquals("1,000", display(CalcMode.SCIENTIFIC, "1 exp 3 equals"))
    }

    @Test fun expressionLine() {
        // History.cpp: "48 ÷ " while the operator is pending; the tokens end with "=" after equals; C clears it.
        val c = calc(CalcMode.STANDARD, "4 8 divide")
        assertEquals("48 ÷", c.expression)
        assertEquals("48 ÷ ", c.expressionRaw)
        c.pressAll("2 equals")
        assertEquals("48 ÷ 2=", c.expression)
        c.press("clear")
        assertEquals("", c.expression)
        val s = calc(CalcMode.SCIENTIFIC, "3 0 sin")
        assertEquals("sin₀(30)", s.expression)
        val p = calc(CalcMode.SCIENTIFIC, "lparen 2 add 3 rparen multiply")
        assertEquals("(2 + 3) ×", p.expression)
        assertEquals(0, p.parenthesisCount)
        p.press("lparen")
        assertEquals(1, p.parenthesisCount)
    }

    // ---- Scientific toggles

    @Test fun scientificToggles() {
        val c = calc(CalcMode.SCIENTIFIC)
        assertEquals(AngleUnit.DEG, c.angleUnit)
        c.press("angle")
        assertEquals(AngleUnit.RAD, c.angleUnit)
        c.press("angle")
        assertEquals(AngleUnit.GRAD, c.angleUnit)
        c.press("angle")
        assertEquals(AngleUnit.DEG, c.angleUnit)
        // inv and hyp turn off once used (CalculatorScientificOperators.xaml.cs ShiftButton_Uncheck / trig pick).
        c.press("inv")
        assertTrue(c.inv)
        c.pressAll("0 decimal 5 sin")
        assertEquals("30", c.displayText)
        assertFalse(c.inv)
        c.press("hyp")
        assertTrue(c.hyp)
        c.pressAll("1 sin")
        assertEquals("1.1752011936438014568823818505956", c.displayText)
        assertFalse(c.hyp)
        // `clear` does not reset inv / hyp (nothing in the ViewModel does; the generator's driver notes).
        c.press("inv")
        c.press("clear")
        assertTrue(c.inv)
        c.press("inv")
        // F-E forces e-notation; C unchecks it (the ToggleButton's Unchecked event sends the command back, so the
        // display returns to float form).
        c.pressAll("1 2 3 fe")
        assertEquals("1.23e+2", c.displayText)
        assertTrue(c.fe)
        c.press("clear")
        assertFalse(c.fe)
        c.pressAll("1 2 3")
        assertEquals("123", c.displayText)
        // The inverse functions of the vocabulary.
        assertEquals("27", display(CalcMode.SCIENTIFIC, "3 inv square"))
        assertEquals("3", display(CalcMode.SCIENTIFIC, "2 7 inv pow 3 equals"))
        assertEquals("2.7182818284590452353602874713527", display(CalcMode.SCIENTIFIC, "1 inv pow10"))
        assertEquals("2.3025850929940456840179914546844", display(CalcMode.SCIENTIFIC, "1 0 inv log"))
        assertEquals("3.1415926535897932384626433832795", display(CalcMode.SCIENTIFIC, "pi"))
    }

    // ---- memory (CalculatorManager.cpp:326-446)

    @Test fun memory() {
        val c = calc(CalcMode.STANDARD)
        assertFalse(c.isEnabled("mr"))
        assertFalse(c.isEnabled("mc"))
        assertTrue(c.memory.isEmpty())
        c.pressAll("5 ms")
        assertEquals(listOf("5"), c.memory)
        assertTrue(c.isEnabled("mr"))
        c.pressAll("3 mplus")
        assertEquals(listOf("8"), c.memory)
        c.pressAll("2 mminus")
        assertEquals(listOf("6"), c.memory)
        c.pressAll("7 ms")
        assertEquals(listOf("7", "6"), c.memory) // MS inserts at the top
        c.press("clear")
        assertEquals(listOf("7", "6"), c.memory) // memory survives C
        c.press("mr")
        assertEquals("7", c.displayText)
        c.memoryRecall(1)
        assertEquals("6", c.displayText)
        c.memoryClear(0)
        assertEquals(listOf("6"), c.memory)
        c.press("mc")
        assertTrue(c.memory.isEmpty())
        // M+ / M- on an empty list store x / -x (CalculatorManager.cpp:378-381, :417-422).
        assertEquals("10", display(CalcMode.STANDARD, "5 mplus mplus mr"))
        assertEquals("-5", display(CalcMode.STANDARD, "5 mminus mr"))
        // The list is shared across modes and formatted per mode / radix.
        val p = calc(CalcMode.STANDARD, "2 5 5 ms")
        p.setMode(CalcMode.PROGRAMMER)
        p.press("radix_hex")
        assertEquals(listOf("FF"), p.memory)
        // A stored fraction is truncated in Programmer (memory follows the current int mode) but keeps its value in Standard.
        val f = calc(CalcMode.STANDARD, "1 divide 3 equals ms")
        f.setMode(CalcMode.PROGRAMMER)
        assertEquals(listOf("0"), f.memory)
        f.setMode(CalcMode.STANDARD)
        assertEquals(listOf("0.3333333333333333"), f.memory)
    }

    @Test fun memoryPersistence() {
        val c = calc(CalcMode.STANDARD, "1 divide 3 equals ms 7 ms")
        val encoded = c.encodeMemory()
        val d = Calculator(CalcMode.STANDARD)
        assertTrue(d.restoreMemory(encoded))
        assertEquals(listOf("7", "0.3333333333333333"), d.memory)
        d.pressAll("mr multiply 3 equals")
        assertEquals("21", d.displayText)
        d.memoryRecall(1)
        d.pressAll("multiply 3 equals")
        assertEquals("1", d.displayText) // the exact rational was kept, not the 16-digit string
        assertFalse(d.restoreMemory("garbage"))
        assertEquals(2, d.memory.size)
        assertTrue(d.restoreMemory(Calculator().encodeMemory()))
        assertTrue(d.memory.isEmpty())
    }

    // ---- history (CalculatorHistory.cpp; none in Programmer, CalculatorManager.cpp:201)

    @Test fun history() {
        val c = calc(CalcMode.STANDARD, "2 add 3 equals 4 multiply 5 equals")
        // CalculatorHistory.cpp GetGeneratedExpression joins the tokens with a space (Calculator.Tests HistoryTests.cs:565).
        assertEquals(listOf(HistoryItem("4   ×   5 =", "20"), HistoryItem("2   +   3 =", "5")), c.history)
        c.pressAll("8 1 sqrt")
        c.pressAll("1")
        // A unary-only line completes when a new number starts (scicomm.cpp CheckAndAddLastBinOpToHistory ->
        // CompleteHistoryLine): no "=" token, and the tokens "√(", "81", ")" joined with spaces.
        assertEquals(HistoryItem("√( 81 )", "9"), c.history[0])
        c.setMode(CalcMode.SCIENTIFIC)
        assertTrue(c.history.isEmpty()) // Scientific has its own list
        c.pressAll("6 divide 4 equals")
        assertEquals(listOf(HistoryItem("6   ÷   4 =", "1.5")), c.history)
        c.setMode(CalcMode.PROGRAMMER)
        c.pressAll("6 divide 4 equals")
        assertTrue(c.history.isEmpty())
        c.setMode(CalcMode.STANDARD)
        assertEquals(3, c.history.size)
        c.removeHistoryItem(0)
        assertEquals(HistoryItem("4   ×   5 =", "20"), c.history[0])
        c.clearHistory()
        assertTrue(c.history.isEmpty())
        c.setMode(CalcMode.SCIENTIFIC)
        assertEquals(1, c.history.size)
        // At most 20 entries; the oldest goes.
        val m = calc(CalcMode.STANDARD)
        for (i in 1..25) m.pressAll(i.toString().toCharArray().joinToString(" ") + " add 0 equals")
        assertEquals(Calculator.MAX_HISTORY_ITEMS, m.history.size)
    }

    @Test fun historyPersistence() {
        val c = calc(CalcMode.STANDARD, "2 add 3 equals")
        c.setMode(CalcMode.SCIENTIFIC)
        c.pressAll("2 pow 1 0 equals")
        val encoded = c.encodeHistory()
        val d = Calculator(CalcMode.SCIENTIFIC)
        assertTrue(d.restoreHistory(encoded))
        assertEquals(listOf(HistoryItem("2   ^   10 =", "1,024")), d.history)
        d.setMode(CalcMode.STANDARD)
        assertEquals(listOf(HistoryItem("2   +   3 =", "5")), d.history)
        assertFalse(d.restoreHistory("nope"))
        assertEquals(1, d.history.size)
    }

    // ---- paste (CopyPasteManager.cs ValidatePasteExpression; StandardCalculatorViewModel.cs OnPaste)

    @Test fun paste() {
        val c = calc(CalcMode.STANDARD)
        assertTrue(c.paste("12+3"))
        assertEquals("3", c.displayText)
        assertEquals("12 +", c.expression)
        c.press("equals")
        assertEquals("15", c.displayText)
        assertTrue(c.paste("12+3="))
        assertEquals("15", c.displayText)
        assertTrue(c.paste("1,234.5"))
        assertEquals("1,234.5", c.displayText)
        assertTrue(c.paste("-5"))
        assertEquals("-5", c.displayText)
        assertTrue(c.paste("2e3"))
        assertEquals("2.e+3", c.displayText)
        // Non-numeric text is rejected: "Invalid input" in error state, the engine untouched; a digit recovers.
        assertFalse(c.paste("hello"))
        assertEquals("Invalid input", c.displayText)
        assertTrue(c.isError)
        assertEquals(CalcErrorKind.INVALID_INPUT, c.errorKind)
        c.press("7")
        assertEquals("7", c.displayText)
        assertFalse(c.isError)
        // Standard has no ^ or parentheses; Scientific does.
        assertFalse(calc(CalcMode.STANDARD).paste("(2+3)*4"))
        val s = calc(CalcMode.SCIENTIFIC)
        assertTrue(s.paste("(2+3)*4="))
        assertEquals("20", s.displayText)
        assertTrue(s.paste("2^10="))
        assertEquals("1,024", s.displayText)
        assertFalse(s.paste("abc")) // no digit at all
        // Programmer accepts the radix's digits with the C prefixes / suffixes and checks the word size.
        val p = calc(CalcMode.PROGRAMMER)
        p.press("radix_hex")
        assertTrue(p.paste("0xFF"))
        assertEquals("FF", p.displayText)
        assertFalse(p.paste("G1"))
        p.press("radix_dec")
        p.pressAll("word word word") // BYTE
        assertFalse(p.paste("300"))
        assertTrue(p.paste("-128"))
        assertEquals("-128", p.displayText)
        assertTrue(p.paste("12+3="))
        assertEquals("15", p.displayText)
        assertFalse(calc(CalcMode.STANDARD).paste("1".repeat(17))) // past the operand length
    }

    // ---- the key lists (r11/calculator.md 2.8, 4.11-4.12, 6.1)

    @Test fun keyLists() {
        val s = calc(CalcMode.STANDARD)
        assertEquals(29, s.keys.size)
        assertTrue("percent" in s.keys && "reciprocal" in s.keys && "mc" in s.keys)
        assertFalse("sin" in s.keys)
        assertFalse(s.press("sin"))
        val sc = calc(CalcMode.SCIENTIFIC)
        assertTrue("angle" in sc.keys && "hyp" in sc.keys && "fe" in sc.keys && "factorial" in sc.keys && "pi" in sc.keys)
        assertFalse("percent" in sc.keys)
        val p = calc(CalcMode.PROGRAMMER)
        assertTrue("radix_hex" in p.keys && "word" in p.keys && "lsh" in p.keys && "not" in p.keys && "ms" in p.keys)
        assertFalse("mr" in p.keys)
        assertEquals(p.keys.filter { p.isEnabled(it) }.toSet(), p.enabledKeys)
        assertFalse("decimal" in p.enabledKeys)
        assertFalse("A" in p.enabledKeys)
    }

    @Test fun errorCountAndUiState() {
        val c = calc(CalcMode.STANDARD, "1 divide 0 equals")
        assertEquals(1, c.errorCount)
        c.pressAll("clear 0 divide 0 equals")
        assertEquals(2, c.errorCount)
        assertEquals(CalcErrorKind.UNDEFINED, c.lastErrorKind)
        assertEquals("undefined", CalcErrorKind.UNDEFINED.logName)
        assertEquals("Cannot divide by zero", CalcErrorKind.DIVIDE_BY_ZERO.message)
        assertEquals("Not enough memory", CalcErrorKind.NOT_ENOUGH_MEMORY.message)
    }
}

package app.tileshell.calc.engine

import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.convert.ConverterState
import app.tileshell.calc.convert.UnitTables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The independent oracle: docs/plan/qa/phase-15/calc-cases.tsv (schema docs/plan/qa/phase-15/calc-keys.md), whose
 * expectations were computed on the host from the Microsoft source, never by this engine. Every standard / scientific /
 * programmer line is pressed key by key on a fresh [Calculator] after `clear`; every tess line's canonical expression
 * goes through [CalcExpression] (the unit rows through the Converter). A mismatch is a defect of this port or of the
 * table, settled against the source — never by editing the table here.
 */
class CalcCasesOracleTest {
    private class Case(val id: String, val mode: String, val setup: String, val keys: String, val expected: String, val utterance: String)

    private fun loadCases(): List<Case> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var tsv: File? = null
        while (dir != null) {
            val f = File(dir, "docs/plan/qa/phase-15/calc-cases.tsv")
            if (f.isFile) {
                tsv = f
                break
            }
            dir = dir.parentFile
        }
        val file = tsv ?: error("calc-cases.tsv not found above ${System.getProperty("user.dir")}")
        val cases = ArrayList<Case>()
        var header = true
        file.forEachLine(Charsets.UTF_8) { line ->
            if (header) {
                header = false
                return@forEachLine
            }
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val f = line.split('\t')
            cases.add(Case(f[0], f[1], f[2], f[3], f[4], f.getOrElse(5) { "" }))
        }
        return cases
    }

    private val setupSplit = Regex("\\s+(?=(?:angle|radix|word|category|from|to|op)=)")

    /** Presses a case on a fresh calculator: mode, setup, `clear`, then the keys. Returns the display. */
    private fun drive(c: Case): String {
        val calc = Calculator(
            when (c.mode) {
                "standard" -> CalcMode.STANDARD
                "scientific" -> CalcMode.SCIENTIFIC
                else -> CalcMode.PROGRAMMER
            },
        )
        applySetup(calc, c.setup)
        calc.press("clear")
        for (k in c.keys.split(' ')) {
            if (k.isEmpty()) continue
            assertTrue("${c.id}: key '$k' is not in mode ${c.mode}", k in calc.keys || k == "reciprocal")
            calc.press(k)
        }
        return calc.displayText
    }

    private fun applySetup(calc: Calculator, setup: String) {
        if (setup.isBlank()) return
        for (part in setup.trim().split(setupSplit)) {
            val (k, v) = part.split('=', limit = 2)
            when (k) {
                "angle" -> {
                    val want = AngleUnit.valueOf(v.uppercase())
                    var n = 0
                    while (calc.angleUnit != want && n++ < 3) calc.press("angle")
                }
                "radix" -> calc.press("radix_$v")
                "word" -> {
                    val want = WordSize.valueOf(v.uppercase())
                    var n = 0
                    while (calc.wordSize != want && n++ < 4) calc.press("word")
                }
                else -> error("unknown setup $k")
            }
            assertEquals("setup $part applied", true, when (k) {
                "angle" -> calc.angleUnit == AngleUnit.valueOf(v.uppercase())
                "radix" -> calc.radix == Radix.valueOf(v.uppercase())
                else -> calc.wordSize == WordSize.valueOf(v.uppercase())
            })
        }
    }

    private fun runMode(mode: String) {
        val cases = loadCases().filter { it.mode == mode }
        assertTrue("no $mode cases", cases.isNotEmpty())
        val failures = ArrayList<String>()
        for (c in cases) {
            val got = try {
                drive(c)
            } catch (e: Throwable) {
                "EXCEPTION ${e::class.simpleName}: ${e.message}"
            }
            if (got != c.expected) failures.add("${c.id} [${c.setup}] ${c.keys}: expected '${c.expected}' got '$got'")
        }
        assertTrue("$mode: ${failures.size} of ${cases.size} mismatch\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun standard() = runMode("standard")
    @Test fun scientific() = runMode("scientific")
    @Test fun programmer() = runMode("programmer")

    /**
     * The driver's way: ONE calculator per mode across every line, `clear` between lines and nothing else reset, as the
     * generator's driver notes describe (memory and F-E survive C; ↑ and HYP are turned off by the driver when a line
     * leaves them on). Proves the state that survives `clear` is only what Windows keeps.
     */
    private fun runModeSequentially(mode: String) {
        val cases = loadCases().filter { it.mode == mode }
        val calc = Calculator(
            when (mode) {
                "standard" -> CalcMode.STANDARD
                "scientific" -> CalcMode.SCIENTIFIC
                else -> CalcMode.PROGRAMMER
            },
        )
        val failures = ArrayList<String>()
        for (c in cases) {
            if (calc.inv) calc.press("inv")
            if (calc.hyp) calc.press("hyp")
            if (calc.isError) calc.press("clear")
            applySetup(calc, c.setup)
            calc.press("clear")
            for (k in c.keys.split(' ')) if (k.isNotEmpty()) calc.press(k)
            val got = calc.displayText
            if (got != c.expected) failures.add("${c.id} [${c.setup}] ${c.keys}: expected '${c.expected}' got '$got'")
        }
        assertTrue("$mode (sequential): ${failures.size} of ${cases.size} mismatch\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun standardSequential() = runModeSequentially("standard")
    @Test fun scientificSequential() = runModeSequentially("scientific")
    @Test fun programmerSequential() = runModeSequentially("programmer")

    /** The canonical infix of each tess utterance (T15-2's grammar output), or the unit conversion it asks for. */
    private val tessExpressions = mapOf(
        "what is fifteen percent of eighty" to "80 * 15 / 100",
        "what is one divided by zero" to "1 / 0",
        "what is the square root of eighty one" to "sqrt(81)",
        "what is 2 plus 2" to "2 + 2",
        "what is 2 plus 3 times 4" to "2 + 3 * 4",
        "what is point five times four" to "0.5 * 4",
        "what is negative three times four" to "-3 * 4",
        "what is 10 to the power of 10000" to "10 ^ 10000",
        "what is 2 to the power of 200" to "2 ^ 200",
        "what is 10 minus 25" to "10 - 25",
        "what is 1 divided by 3" to "1 / 3",
        "calculate 7 times 6" to "7 * 6",
        "how much is 100 divided by 8" to "100 / 8",
        "what is 25 times 40" to "25 * 40",
        "what is the square root of two" to "sqrt(2)",
        "what is zero divided by zero" to "0 / 0",
    )
    private val tessConversions = mapOf(
        "what is five miles in kilometers" to Triple("5", "Miles", "Kilometers"),
        "what is five miles in kilometres" to Triple("5", "Miles", "Kilometers"),
        "what is 3 feet in meters" to Triple("3", "Feet", "Meters"),
    )

    @Test fun tess() {
        val cases = loadCases().filter { it.mode == "tess" }
        assertTrue("no tess cases", cases.isNotEmpty())
        val failures = ArrayList<String>()
        for (c in cases) {
            val got: String = tessExpressions[c.utterance]?.let { CalcExpression.evaluate(it).display }
                ?: tessConversions[c.utterance]?.let { (digits, from, to) -> convert(digits, from, to) }
                ?: "NO MAPPING for '${c.utterance}'"
            if (got != c.expected) failures.add("${c.id} '${c.utterance}': expected '${c.expected}' got '$got'")
        }
        assertTrue("tess: ${failures.size} of ${cases.size} mismatch\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun convert(digits: String, from: String, to: String): String {
        val state = ConverterState()
        val category = ConverterCategory.LENGTH
        state.selectCategory(category)
        val units = UnitTables.orderedUnits(category)
        state.selectUnit1(units.first { it.name == from })
        state.selectUnit2(units.first { it.name == to })
        for (ch in digits) state.press(if (ch == '.') "decimal" else ch.toString())
        return state.toText
    }
}

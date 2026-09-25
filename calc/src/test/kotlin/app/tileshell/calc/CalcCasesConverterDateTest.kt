package app.tileshell.calc

import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.convert.ConverterState
import app.tileshell.calc.date.DateCalculatorState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * The independent oracle's converter and date rows (docs/plan/qa/phase-15/calc-cases.tsv, schema calc-keys.md),
 * driven through the same state classes the Calculator app draws: every `converter` line through [ConverterState]
 * (category, from, to, the typed keys, the "to" text) and every `date` line through [DateCalculatorState] (the
 * difference's one or two result lines joined with " | ", or the add / subtract result). The tsv's expectations are
 * computed on the host from Windows' source and never from this code.
 */
class CalcCasesConverterDateTest {
    private data class Row(val id: String, val mode: String, val setup: String, val keys: String, val expected: String)

    private val rows: List<Row> by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var tsv: File? = null
        while (dir != null && tsv == null) {
            File(dir, "docs/plan/qa/phase-15/calc-cases.tsv").takeIf { it.exists() }?.let { tsv = it }
            dir = dir.parentFile
        }
        val file = tsv ?: error("calc-cases.tsv not found above ${System.getProperty("user.dir")}")
        file.readLines().drop(1).filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val f = line.split('\t')
            Row(f[0], f[1], f[2], f[3], f[4])
        }
    }

    private val setupSplit = Regex("\\s+(?=(?:angle|radix|word|category|from|to|op)=)")
    private fun setup(s: String): Map<String, String> =
        s.trim().split(setupSplit).filter { it.isNotBlank() }.associate { it.split('=', limit = 2).let { (k, v) -> k to v } }

    @Test fun converterRows() {
        val cases = rows.filter { it.mode == "converter" }
        val bad = mutableListOf<String>()
        for (r in cases) {
            val s = setup(r.setup)
            val state = ConverterState()
            val category = ConverterCategory.entries.first { it.label == s.getValue("category") }
            state.selectCategory(category)
            state.selectUnit1(state.units.first { it.name == s.getValue("from") })
            state.selectUnit2(state.units.first { it.name == s.getValue("to") })
            r.keys.split(' ').filter { it.isNotBlank() }.forEach { state.press(it) }
            if (state.toText != r.expected) bad += "${r.id}: expected [${r.expected}] got [${state.toText}]"
        }
        println("converter oracle: ${cases.size - bad.size}/${cases.size}")
        assertEquals(bad.joinToString("\n"), 0, bad.size)
        assertEquals("the table has its converter lines", true, cases.size >= 24)
    }

    @Test fun dateRows() {
        val cases = rows.filter { it.mode == "date" }
        val bad = mutableListOf<String>()
        for (r in cases) {
            val op = setup(r.setup).getValue("op")
            val k = r.keys.trim().split(' ').associate { it.split('=', limit = 2).let { (a, b) -> a to b } }
            val state = DateCalculatorState(LocalDate.of(2026, 9, 23))
            val got = if (op == "difference") {
                state.isDateDiffMode = true
                state.fromDate = LocalDate.parse(k.getValue("from"))
                state.toDate = LocalDate.parse(k.getValue("to"))
                // One line when the difference is days only (or none): the view model puts it in strDateDiffResult.
                if (state.isDiffInDays) state.strDateDiffResult
                else "${state.strDateDiffResult} | ${state.strDateDiffResultInDays}"
            } else {
                state.isDateDiffMode = false
                state.isAddMode = op == "add"
                state.startDate = LocalDate.parse(k.getValue("from"))
                state.yearsOffset = k.getValue("years").toInt()
                state.monthsOffset = k.getValue("months").toInt()
                state.daysOffset = k.getValue("days").toInt()
                state.strDateResult
            }
            if (got != r.expected) bad += "${r.id}: expected [${r.expected}] got [$got]"
        }
        println("date oracle: ${cases.size - bad.size}/${cases.size}")
        assertEquals(bad.joinToString("\n"), 0, bad.size)
        assertEquals("the table has its date lines", true, cases.size >= 10)
    }
}

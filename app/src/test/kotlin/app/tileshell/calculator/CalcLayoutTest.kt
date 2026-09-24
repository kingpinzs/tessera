package app.tileshell.calculator

import app.tileshell.calc.convert.ConverterCategory
import app.tileshell.calc.engine.CalcMode
import app.tileshell.calc.engine.Calculator
import app.tileshell.diag.Diagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure mappings the Calculator UI draws from (phase 15 build task 6): each mode's key layout in r11's order
 * (docs/plan/r11/calculator.md 2.8, 4.11–4.12, 6.1) with every key of docs/plan/qa/phase-15/calc-keys.md's mode,
 * the display-shrink rule (7.5), the pane's order (3.9) and the shortcut → page mapping (build task 9).
 */
class CalcLayoutTest {

    private fun names(rows: List<List<KeySpec>>) = rows.map { row -> row.map { it.name } }

    @Test fun `Standard keys are r11 2_8's six W over 4 rows`() {
        assertEquals(
            listOf(
                listOf("percent", "sqrt", "square", "reciprocal"),
                listOf("clear_entry", "clear", "backspace", "divide"),
                listOf("7", "8", "9", "multiply"),
                listOf("4", "5", "6", "subtract"),
                listOf("1", "2", "3", "add"),
                listOf("negate", "0", "decimal", "equals"),
            ),
            names(CalcLayout.rows(CalcMode.STANDARD)),
        )
        assertEquals(4, CalcLayout.columns(CalcMode.STANDARD))
        assertEquals(1, CalcLayout.blackRows(CalcMode.STANDARD))
    }

    @Test fun `Scientific keys are r11 6_1's seven W over 5 rows`() {
        assertEquals(
            listOf(
                listOf("square", "pow", "sin", "cos", "tan"),
                listOf("sqrt", "pow10", "log", "exp", "mod"),
                listOf("inv", "clear_entry", "clear", "backspace", "divide"),
                listOf("pi", "7", "8", "9", "multiply"),
                listOf("factorial", "4", "5", "6", "subtract"),
                listOf("negate", "1", "2", "3", "add"),
                listOf("lparen", "rparen", "0", "decimal", "equals"),
            ),
            names(CalcLayout.rows(CalcMode.SCIENTIFIC)),
        )
        assertEquals(5, CalcLayout.columns(CalcMode.SCIENTIFIC))
        assertEquals(2, CalcLayout.blackRows(CalcMode.SCIENTIFIC))
        assertEquals(listOf("angle", "hyp", "fe"), CalcLayout.ANGLE_ROW.map { it.first })
    }

    @Test fun `Programmer keys are r11 4_11-4_12's six W over 6 rows`() {
        assertEquals(
            listOf(
                listOf("lsh", "rsh", "or", "xor", "not", "and"),
                listOf("inv", "mod", "clear_entry", "clear", "backspace", "divide"),
                listOf("A", "B", "7", "8", "9", "multiply"),
                listOf("C", "D", "4", "5", "6", "subtract"),
                listOf("E", "F", "1", "2", "3", "add"),
                listOf("lparen", "rparen", "negate", "0", "decimal", "equals"),
            ),
            names(CalcLayout.rows(CalcMode.PROGRAMMER)),
        )
        assertEquals(6, CalcLayout.columns(CalcMode.PROGRAMMER))
        assertEquals(1, CalcLayout.blackRows(CalcMode.PROGRAMMER))
        assertEquals(listOf("radix_hex", "radix_dec", "radix_oct", "radix_bin"), CalcLayout.RADIX_ROWS.map { it.first })
    }

    @Test fun `every mode's page carries exactly the engine's keys for that mode, each once`() {
        for (mode in CalcMode.values()) {
            val page = CalcLayout.keyNames(mode)
            assertEquals("$mode: a key is on the page twice", page.size, page.toSet().size)
            assertEquals("$mode: the page's keys are the engine's", Calculator(mode).keys.toSet(), page.toSet())
        }
        assertEquals(listOf("mc", "mr", "mplus", "mminus", "ms"), CalcLayout.MEMORY_ROW.map { it.first })
        assertEquals("mlist", CalcLayout.MEMORY_LIST_KEY)
    }

    @Test fun `the second-function labels follow the engine's inv mapping`() {
        val sci = CalcLayout.SCIENTIFIC.flatten().associate { it.name to it.second }
        assertEquals("x³", sci["square"])
        assertEquals("ʸ√x", sci["pow"])
        assertEquals("sin⁻¹", sci["sin"])
        assertEquals("¹⁄x", sci["sqrt"])
        assertEquals("eˣ", sci["pow10"])
        assertEquals("ln", sci["log"])
        assertEquals("dms", sci["exp"])
        assertEquals("deg", sci["mod"])
        assertNull(sci["pi"])
        val prog = CalcLayout.PROGRAMMER.flatten().associate { it.name to it.second }
        assertEquals("RoL", prog["lsh"])
        assertEquals("RoR", prog["rsh"])
        assertNull(prog["or"])
    }

    @Test fun `the result shrinks from 46 to a 12 epx floor to fit`() {
        assertEquals(46f, CalcDisplayFit.fontSize(100f, 340f), 0.001f)
        assertEquals(46f, CalcDisplayFit.fontSize(340f, 340f), 0.001f)
        assertEquals(46f * 340f / 510f, CalcDisplayFit.fontSize(510f, 340f), 0.001f)
        assertEquals(12f, CalcDisplayFit.fontSize(3400f, 340f), 0.001f)
        assertEquals(12f, CalcDisplayFit.fontSize(1303.4f, 340f), 0.001f)
        assertEquals(46f, CalcDisplayFit.fontSize(0f, 340f), 0.001f)
    }

    @Test fun `the pane lists the modes, CONVERTER and the twelve categories in r11 3_9's order, no Currency`() {
        assertEquals(
            listOf(
                "Standard", "Scientific", "Programmer", "Date calculation", "CONVERTER",
                "Volume", "Length", "Weight and Mass", "Temperature", "Energy", "Area", "Speed", "Time", "Power", "Data", "Pressure", "Angle",
            ),
            CalcLayout.PANE_ROWS.map { it.label },
        )
        assertFalse(CalcLayout.PANE_ROWS.any { it.label.contains("Currency", ignoreCase = true) })
        assertEquals(12, CalcLayout.PANE_ROWS.count { it is PaneRow.Category })
        assertEquals(ConverterCategory.entries, CalcLayout.PANE_ROWS.filterIsInstance<PaneRow.Category>().map { it.category })
        assertEquals(
            listOf("standard", "scientific", "programmer", "date"),
            CalcLayout.PANE_ROWS.filterIsInstance<PaneRow.Mode>().map { it.page.id },
        )
    }

    @Test fun `the History glyph is Standard's and Scientific's only`() {
        assertTrue(CalcPage.STANDARD.hasHistory)
        assertTrue(CalcPage.SCIENTIFIC.hasHistory)
        assertFalse(CalcPage.PROGRAMMER.hasHistory)
        assertFalse(CalcPage.DATE.hasHistory)
        assertFalse(CalcPage.CONVERTER.hasHistory)
    }

    @Test fun `the shortcut page extra maps to its page and nothing else`() {
        assertEquals(listOf("standard", "scientific", "programmer", "converter"), CalcLayout.SHORTCUT_IDS)
        assertEquals(CalcPage.STANDARD, CalcLayout.pageForShortcut("standard"))
        assertEquals(CalcPage.SCIENTIFIC, CalcLayout.pageForShortcut("scientific"))
        assertEquals(CalcPage.PROGRAMMER, CalcLayout.pageForShortcut("programmer"))
        assertEquals(CalcPage.CONVERTER, CalcLayout.pageForShortcut("converter"))
        assertNull(CalcLayout.pageForShortcut("date"))
        assertNull(CalcLayout.pageForShortcut("STANDARD"))
        assertNull(CalcLayout.pageForShortcut(""))
        assertNull(CalcLayout.pageForShortcut(null))
        assertEquals(CalcPage.STANDARD, CalcLayout.DEFAULT_PAGE)
    }

    @Test fun `page titles and ids are r11's strings`() {
        assertEquals("STANDARD", CalcPage.STANDARD.title.uppercase())
        assertEquals("Date calculation", CalcPage.DATE.title)
        assertEquals(listOf("standard", "scientific", "programmer", "date", "converter"), CalcPage.entries.map { it.id })
    }

    @Test fun `the amount field's text uses the engine's plural forms`() {
        assertEquals("0 years, 1 month, 0 days", amountText(0, 1, 0))
        assertEquals("1 year, 2 months, 1 day", amountText(1, 2, 1))
    }

    @Test fun `the engine line is written once per process`() {
        val before = Diagnostics.snapshot().count { it.tag == "calc" && it.message == CalcDiag.ENGINE_LINE }
        CalcDiag.logEngineOnce()
        CalcDiag.logEngineOnce()
        val after = Diagnostics.snapshot().count { it.tag == "calc" && it.message == CalcDiag.ENGINE_LINE }
        assertTrue("one line at most", after - before <= 1)
        assertEquals("engine port microsoft/calculator@4fd3fc5", CalcDiag.ENGINE_LINE)
    }
}

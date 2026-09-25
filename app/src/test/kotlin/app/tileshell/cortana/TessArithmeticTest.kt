package app.tileshell.cortana

import app.tileshell.cortana.action.TessArithmetic
import app.tileshell.cortana.match.CommandMatcher
import app.tileshell.cortana.match.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tess's arithmetic end to end on the host (phase 15 E26's expectations): every tess line of the independent oracle
 * (docs/plan/qa/phase-15/calc-cases.tsv, computed on the host from Windows' source and T15-52's rule — never by this
 * code) goes through the REAL matcher and the REAL engine, and the reply must equal the column exactly.
 */
class TessArithmeticTest {
    private fun tessRows(): List<Pair<String, String>> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "docs/plan/qa/phase-15/calc-cases.tsv").exists()) dir = dir.parentFile
        val tsv = File(dir ?: error("calc-cases.tsv not found"), "docs/plan/qa/phase-15/calc-cases.tsv")
        return tsv.readLines().drop(1).filter { it.startsWith("tess-") }.map { it.split('\t').let { f -> f[5] to f[6] } }
    }

    @Test fun everyTessLineOfTheOracle() {
        val rows = tessRows()
        val bad = mutableListOf<String>()
        for ((utterance, reply) in rows) {
            val request = CommandMatcher.match(utterance)
            if (request !is Request.Arithmetic) { bad += "\"$utterance\" matched $request"; continue }
            val got = TessArithmetic.answer(request.expr).spoken
            if (got != reply) bad += "\"$utterance\": expected [$reply] got [$got]"
        }
        println("tess oracle: ${rows.size - bad.size}/${rows.size}")
        assertEquals(bad.joinToString("\n"), 0, bad.size)
        assertTrue("the oracle has its tess lines", rows.size >= 10)
    }

    @Test fun theNegativesAreNotArithmetic() {
        for (u in listOf("What is the capital of Peru?", "Calculate my life.", "What's the weather like?",
                "what is 5 parsecs in miles", "what is 5 miles in kilograms")) {
            val r = CommandMatcher.match(u)
            assertTrue("$u -> $r", r !is Request.Arithmetic)
        }
        assertEquals(Request.Weather, CommandMatcher.match("What's the weather like?"))
    }

    @Test fun theSpokenFormsOfE26() {
        fun say(u: String) = TessArithmetic.answer((CommandMatcher.match(u) as Request.Arithmetic).expr)
        assertEquals("15 % of 80 is 12.", say("What's fifteen percent of eighty?").spoken)
        assertEquals("15 % of 80", say("What's fifteen percent of eighty?").logExpr)
        assertEquals("Cannot divide by zero.", say("What's one divided by zero?").spoken)
        assertEquals("1 / 0", say("What's one divided by zero?").logExpr)
        assertEquals("5 miles is 8.04672 kilometers.", say("What is five miles in kilometers?").spoken)
        assertEquals("5 miles is 8.04672 kilometers.", say("What is five miles in kilometres?").spoken)
        assertEquals("√81 is 9.", say("What is the square root of eighty one?").spoken)
    }
}

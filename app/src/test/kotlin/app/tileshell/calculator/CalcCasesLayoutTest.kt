package app.tileshell.calculator

import app.tileshell.calc.engine.CalcMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * E11's driver presses every standard / scientific / programmer case of docs/plan/qa/phase-15/calc-cases.tsv by
 * tapping `calc_key:<name>` (phase 15 E11): so every key a case uses must be a key on that mode's page. This loads
 * the table and checks each line's keys against [CalcLayout.keyNames] for its mode; the setup column's radix / word
 * / angle settings are pressed through `radix_*`, `word` and `angle`, which are on the page too.
 */
class CalcCasesLayoutTest {
    private data class Row(val id: String, val mode: String, val setup: String, val keys: String)

    private fun load(): List<Row> {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var tsv: File? = null
        while (dir != null && tsv == null) {
            File(dir, "docs/plan/qa/phase-15/calc-cases.tsv").takeIf { it.isFile }?.let { tsv = it }
            dir = dir.parentFile
        }
        val file = tsv ?: error("calc-cases.tsv not found above ${System.getProperty("user.dir")}")
        return file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val f = line.split('\t')
            Row(f[0], f[1], f[2], f[3])
        }
    }

    private val setupSplit = Regex("\\s+(?=(?:angle|radix|word|category|from|to|op)=)")

    @Test fun `every key of every calculator case is on its mode's page`() {
        val rows = load()
        val modes = mapOf("standard" to CalcMode.STANDARD, "scientific" to CalcMode.SCIENTIFIC, "programmer" to CalcMode.PROGRAMMER)
        val bad = ArrayList<String>()
        var checked = 0
        for (r in rows) {
            val mode = modes[r.mode] ?: continue
            val page = CalcLayout.keyNames(mode).toSet()
            checked++
            for (k in r.keys.split(' ')) {
                if (k.isEmpty()) continue
                if (k !in page) bad += "${r.id}: key '$k' is not on the ${r.mode} page"
            }
            if (r.setup.isNotBlank()) {
                for (part in r.setup.trim().split(setupSplit)) {
                    val key = when (part.substringBefore('=')) {
                        "radix" -> "radix_" + part.substringAfter('=')
                        "word" -> "word"
                        "angle" -> "angle"
                        else -> continue
                    }
                    if (key !in page) bad += "${r.id}: setup '$part' needs '$key', not on the ${r.mode} page"
                }
            }
        }
        println("calc-cases layout: $checked calculator lines checked, ${bad.size} keys missing")
        assertTrue("no calculator lines found", checked >= 200)
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }
}

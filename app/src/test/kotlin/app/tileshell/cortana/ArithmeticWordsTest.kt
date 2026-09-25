package app.tileshell.cortana

import app.tileshell.cortana.match.ArithmeticWords
import app.tileshell.cortana.match.CalcRequest
import app.tileshell.cortana.match.CalcRequest.Num
import app.tileshell.cortana.match.CalcRequest.Op
import app.tileshell.cortana.match.CalcRequest.Root
import app.tileshell.cortana.match.CommandMatcher
import app.tileshell.cortana.match.NumberWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArithmeticWordsTest {
    private fun parse(raw: String) = ArithmeticWords.parse(CommandMatcher.normalise(raw))
    private fun chain(vararg parts: Any): CalcRequest.Chain {
        val terms = parts.filterIsInstance<CalcRequest.Term>()
        val ops = parts.filterIsInstance<Pair<*, *>>().map { CalcRequest.Said(it.first as Op, it.second as String) }
        return CalcRequest.Chain(terms, ops)
    }

    @Test fun numberWords() {
        fun lit(s: String) = NumberWords.read(s.split(' '), 0)?.literal
        assertEquals("81", lit("eighty one"))
        assertEquals("123405", lit("one hundred twenty three thousand four hundred five"))
        assertEquals("0.5", lit("point five"))
        assertEquals("3.14", lit("three point one four"))
        assertEquals("-3", lit("negative three"))
        assertEquals("1000", lit("1000"))
        assertEquals("2000000", lit("two million"))
        assertNull(NumberWords.read(listOf("hundred"), 0)) // "hundred" alone is not a number
        assertNull(NumberWords.read(listOf("capital"), 0))
    }

    @Test fun theE26Utterances() {
        assertEquals(CalcRequest.Percent(Num("15"), Num("80")), parse("What's fifteen percent of eighty?"))
        // What the recogniser actually writes for calc_percent (E26 run 3's ring: "WHAT'S FIFTEEN PER CENT OF EIGHTY").
        assertEquals(CalcRequest.Percent(Num("15"), Num("80")), parse("WHAT'S FIFTEEN PER CENT OF EIGHTY"))
        assertEquals(chain(Num("1"), Op.DIVIDE to "divided by", Num("0")), parse("What's one divided by zero?"))
        assertEquals(chain(Root(Num("81"))), parse("What is the square root of eighty one?"))
        assertEquals(chain(Num("2"), Op.PLUS to "plus", Num("2")), parse("what is 2 plus 2"))
        assertEquals(chain(Num("2"), Op.PLUS to "plus", Num("3"), Op.TIMES to "times", Num("4")), parse("what is 2 plus 3 times 4"))
    }

    @Test fun theEdgeCases() {
        assertEquals(chain(Num("0.5"), Op.TIMES to "times", Num("4")), parse("what is point five times four"))
        assertEquals(chain(Num("-3"), Op.TIMES to "times", Num("4")), parse("what is negative three times four"))
        assertEquals(chain(Num("10"), Op.POWER to "to the power of", Num("10000")), parse("what is 10 to the power of 10000"))
        assertEquals(chain(Num("100"), Op.DIVIDE to "divided by", Num("8")), parse("how much is 100 divided by 8"))
        assertEquals(chain(Num("7"), Op.TIMES to "times", Num("6")), parse("calculate 7 times 6"))
        assertEquals(chain(Num("7"), Op.TIMES to "multiplied by", Num("6")), parse("what is seven multiplied by six"))
        assertEquals(chain(Num("2"), Op.PLUS to "plus", Num("2")), parse("two plus two"))
        assertEquals(chain(Num("10"), Op.MINUS to "minus", Num("25")), parse("what is 10 minus 25"))
        assertEquals(chain(Num("-4"), Op.PLUS to "plus", Num("1")), parse("minus four plus one"))
    }

    @Test fun anythingNotWhollyArithmeticIsNotArithmetic() {
        assertNull(parse("What is the capital of Peru?"))
        assertNull(parse("Calculate my life."))
        assertNull(parse("What's the weather like?"))
        assertNull(parse("what is five"))
        assertNull(parse("what is 2 plus"))
        assertNull(parse("what time is it"))
        assertNull(parse("set a timer for five minutes"))
        assertNull(parse("what is five percent"))
    }
}

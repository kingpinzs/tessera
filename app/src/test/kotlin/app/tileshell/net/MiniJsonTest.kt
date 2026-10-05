package app.tileshell.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 17: the plain-Kotlin JSON reader the catalogue's, Wikidata's and the media server's parsers stand on. */
class MiniJsonTest {
    @Test fun `objects, arrays, strings, numbers and literals are read`() {
        val v = MiniJson.parse(""" {"a": 1, "b": -2.5, "c": "x\ny \u00e9 \"q\" \\ \/", "d": [true, false, null, {"e": []}], "f": 1e3} """).jsonObject()!!
        assertEquals(1L, v["a"])
        assertEquals(-2.5, v["b"])
        assertEquals("x\ny é \"q\" \\ /", v["c"])
        assertEquals(listOf(true, false, null, mapOf("e" to emptyList<Any?>())), v["d"])
        assertEquals(1000.0, v["f"])
        assertEquals(listOf("a", "b", "c", "d", "f"), v.keys.toList())
    }

    @Test fun `what is not one JSON value is refused, never half-read`() {
        for (bad in listOf("", "{", "[1,", "{\"a\":}", "{\"a\" 1}", "tru", "\"open", "{} x", "[1 2]", "{\"a\":1,}", "01x", "\"\\q\"", "\"\\u12\"")) {
            assertNull(bad, MiniJson.parseOrNull(bad))
        }
        assertNull(MiniJson.parseOrNull("[".repeat(500) + "]".repeat(500)))
    }

    @Test fun `what is written reads back the same`() {
        val value = linkedMapOf("iv" to "AAEC", "ct" to "line\nbreak \"quoted\" \\ \u0001", "n" to 7L, "list" to listOf(1L, "two", null, true))
        assertEquals(value, MiniJson.parse(MiniJson.write(value)))
        assertTrue(MiniJson.write(value).startsWith("{\"iv\":\"AAEC\","))
    }

    @Test fun `the helpers answer null for a wrong shape`() {
        val o = MiniJson.parse("""{"s": 5, "n": "x", "o": [], "id": 78, "f": 7.9}""").jsonObject()!!
        assertNull(o.jsonString("s"))
        assertNull(o.jsonLong("n"))
        assertNull(o["o"].jsonObject())
        assertEquals(78L, o.jsonLong("id"))
        assertEquals(7L, o.jsonLong("f"))
        assertEquals(emptyList<Any?>(), o["s"].jsonArray())
    }
}

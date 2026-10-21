package app.tileshell.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ledger L18-3, the adversarial review's N11: a sound that opens but will not decode (a zero-byte .mp3) made
 * `MediaPlayer.prepare()` throw, the failure was only logged, and the alarm vibrated SILENTLY. The rule: when the
 * chosen sound cannot play — at the start or while playing — the ring plays the default sound, and when that fails
 * too it vibrates only and says so. Never a silent ring with no line.
 */
class RingSoundRulesTest {
    /** What `MediaPlayer.prepare()` throws for a file it cannot decode. */
    private class Undecodable : java.io.IOException("Prepare failed.: status=0x1")

    private val chosen = "content://media/external/audio/media/41"
    private val default = "file:///data/user_de/0/app.tileshell/cache/alarm_sounds/classic.wav"

    /** A platform that starts what [plays] holds and throws [thrown] for anything else, recording each source it was handed. */
    private class Player(val plays: Set<String>, val thrown: () -> Throwable = { Undecodable() }) {
        val opened = mutableListOf<String>()
        fun open(source: String): String {
            opened += source
            if (source !in plays) throw thrown()
            return "playing $source"
        }
    }

    @Test fun `N11 a chosen sound that will not play rings the default sound, and the line names the fallback`() {
        val p = Player(setOf(default))
        val r = RingSoundRules.start("a1", chosen, { default }, p::open)
        assertEquals("playing $default", r.player)
        assertEquals(listOf(chosen, default), p.opened)
        assertEquals(listOf("ring a1 sound could not play (Undecodable) -> default sound", "ring a1 sound=default (fallback)"), r.lines)
    }

    @Test fun `when the default will not play either the ring vibrates only, and says so`() {
        val p = Player(emptySet())
        val r = RingSoundRules.start("a1", chosen, { default }, p::open)
        assertNull(r.player)
        assertEquals(listOf(chosen, default), p.opened)
        assertEquals(listOf("ring a1 sound could not play (Undecodable) -> default sound", "ring a1 default sound could not play (Undecodable) -> vibrate only"), r.lines)
    }

    @Test fun `a sound that plays is the only one opened, and the default is not even made`() {
        val p = Player(setOf(chosen))
        var made = 0
        val r = RingSoundRules.start("a1", chosen, { made++; default }, p::open)
        assertEquals("playing $chosen", r.player)
        assertEquals(listOf(chosen), p.opened)
        assertEquals(0, made)
        assertEquals(emptyList<String>(), r.lines)
    }

    @Test fun `a chosen sound that IS the default is not tried twice - it vibrates only, and says so`() {
        val p = Player(emptySet())
        val r = RingSoundRules.start("t9", default, { default }, p::open)
        assertNull(r.player)
        assertEquals(listOf(default), p.opened)
        assertEquals(listOf("ring t9 default sound could not play (Undecodable) -> vibrate only"), r.lines)
    }

    @Test fun `an error while playing is the same failure - the default sound, or vibrate only when it was the default playing`() {
        val p = Player(setOf(default))
        val r = RingSoundRules.fallback("a1", chosen, "error 1 -1004", { default }, p::open)
        assertEquals("playing $default", r.player)
        assertEquals(listOf("ring a1 sound could not play (error 1 -1004) -> default sound", "ring a1 sound=default (fallback)"), r.lines)
        val q = Player(setOf(default))
        val s = RingSoundRules.fallback("a1", default, "error 1 -1004", { default }, q::open)
        assertNull(s.player)
        assertEquals(emptyList<String>(), q.opened)
        assertEquals(listOf("ring a1 default sound could not play (error 1 -1004) -> vibrate only"), s.lines)
    }

    @Test fun `whatever the player or the default throws - an Error too - never escapes the ring, and the line names only its class`() {
        for (thrown in listOf<() -> Throwable>({ IllegalStateException("a /secret/path") }, { SecurityException("x") }, { OutOfMemoryError() }, { IllegalArgumentException("\n[alarms] forged") })) {
            val r = RingSoundRules.start("a1", chosen, { default }, Player(emptySet(), thrown)::open)
            assertNull(r.player)
            assertEquals(2, r.lines.size)
            for (line in r.lines) {
                assertEquals(line, false, line.contains("secret") || line.contains("forged") || line.contains('\n'))
            }
        }
        val r = RingSoundRules.start("a1", chosen, { throw java.io.IOException("no space") }, Player(setOf(default))::open)
        assertNull(r.player)
        assertEquals(listOf("ring a1 sound could not play (Undecodable) -> default sound", "ring a1 default sound could not play (IOException) -> vibrate only"), r.lines)
    }
}

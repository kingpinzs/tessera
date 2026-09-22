package app.tileshell.qa.imefixture.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GestureScriptTest {

    @Test
    fun parsesEveryCommand() {
        val cmds = GestureScript.parse(
            "down 10 20; move 30 40; up; sleep 150; tap 540 1700; hold 100 200 600; moveto 900 1000 250 10"
        )
        assertEquals(
            listOf(
                Command.Down(10, 20),
                Command.Move(30, 40),
                Command.Up,
                Command.Sleep(150),
                Command.Tap(540, 1700),
                Command.Hold(100, 200, 600),
                Command.MoveTo(900, 1000, 250, 10),
            ),
            cmds,
        )
    }

    @Test
    fun namesAreCaseInsensitiveAndWhitespaceIsLoose() {
        val cmds = GestureScript.parse("  TAP   1 2 ;;Sleep 5;  ; up ;")
        assertEquals(listOf(Command.Tap(1, 2), Command.Sleep(5), Command.Up), cmds)
    }

    @Test
    fun emptyScriptIsEmpty() {
        assertEquals(emptyList<Command>(), GestureScript.parse(""))
        assertEquals(emptyList<Command>(), GestureScript.parse(" ; ; "))
    }

    @Test
    fun canonicalTextRoundTrips() {
        val script = "down 10 20; move 30 40; up; sleep 150; tap 540 1700; hold 100 200 600; moveto 900 1000 250 10"
        val cmds = GestureScript.parse(script)
        assertEquals(script, cmds.joinToString("; ") { it.text })
    }

    @Test
    fun plannedOffsetsFollowTheSequentialTimeline() {
        // Two taps 150 ms apart: the first tap lasts 40 ms, so the sleep is 110.
        val cmds = GestureScript.parse("tap 1 1; sleep 110; tap 1 1; hold 2 2 300; moveto 3 3 200 4; up")
        assertEquals(listOf(0L, 40L, 150L, 190L, 490L, 690L), GestureScript.plannedOffsets(cmds))
    }

    @Test
    fun rejectsBadArity() {
        expectError("tap 1", "command 1 'tap 1': expected 'tap X Y'")
        expectError("up 1", "expected 'up'")
        expectError("hold 1 2", "expected 'hold X Y MS'")
        expectError("moveto 1 2 3", "expected 'moveto X Y MS STEPS'")
    }

    @Test
    fun rejectsBadNumbers() {
        expectError("tap x 2", "X must be an integer, got 'x'")
        expectError("sleep -1", "MS must be >= 0, got -1")
        expectError("sleep abc", "MS must be an integer, got 'abc'")
        expectError("moveto 1 2 3 0", "STEPS must be >= 1, got 0")
    }

    @Test
    fun rejectsUnknownCommandWithItsNumber() {
        expectError("tap 1 2; swipe 3 4", "command 2 'swipe 3 4': unknown command 'swipe'")
    }

    @Test
    fun nothingParsesFromAScriptWithOneBadCommand() {
        try {
            GestureScript.parse("tap 1 2; bogus; tap 3 4")
            fail("expected GestureScriptException")
        } catch (e: GestureScriptException) {
            assertTrue(e.message!!.startsWith("command 2 'bogus'"))
        }
    }

    private fun expectError(script: String, contains: String) {
        try {
            GestureScript.parse(script)
            fail("expected GestureScriptException for '$script'")
        } catch (e: GestureScriptException) {
            assertTrue("'${e.message}' should contain '$contains'", e.message!!.contains(contains))
        }
    }
}

package app.tileshell.qa.imefixture.gesture

/**
 * A timed gesture script, the text form of `op=script`. Plain Kotlin (no Android types) so the JVM
 * unit test in src/test covers the parsing; the androidTest ScriptRunner turns commands into
 * injected MotionEvents.
 *
 * Grammar: commands separated by `;`, tokens separated by whitespace, names case-insensitive.
 *
 *     down X Y            pointer 0 down at X,Y
 *     move X Y            pointer move to X,Y (needs a down)
 *     up                  pointer up at the last position
 *     sleep MS            nothing for MS milliseconds
 *     tap X Y             down at X,Y, up 40 ms later
 *     hold X Y MS         down at X,Y, up MS later
 *     moveto X Y MS STEPS STEPS interpolated moves from the last position to X,Y spread over MS
 *
 * Time is one sequential timeline: each command starts when the previous one ends, and a command's
 * duration is what [Command.durationMs] says (tap 40, hold MS, moveto MS, sleep MS, others 0).
 * Two taps 150 ms apart are therefore `tap X Y; sleep 110; tap X Y`. [plannedOffsets] gives the
 * planned start of every command; the runner sleeps until each one, so injection latency of one
 * command never shifts the next.
 */
sealed class Command {
    abstract val durationMs: Long

    /** The canonical text of the command, as reported back in the status lines. */
    abstract val text: String

    data class Down(val x: Int, val y: Int) : Command() {
        override val durationMs = 0L
        override val text get() = "down $x $y"
    }

    data class Move(val x: Int, val y: Int) : Command() {
        override val durationMs = 0L
        override val text get() = "move $x $y"
    }

    object Up : Command() {
        override val durationMs = 0L
        override val text get() = "up"
        override fun toString() = "Up"
    }

    data class Sleep(val ms: Long) : Command() {
        override val durationMs get() = ms
        override val text get() = "sleep $ms"
    }

    data class Tap(val x: Int, val y: Int) : Command() {
        override val durationMs get() = TAP_MS
        override val text get() = "tap $x $y"
    }

    data class Hold(val x: Int, val y: Int, val ms: Long) : Command() {
        override val durationMs get() = ms
        override val text get() = "hold $x $y $ms"
    }

    data class MoveTo(val x: Int, val y: Int, val ms: Long, val steps: Int) : Command() {
        override val durationMs get() = ms
        override val text get() = "moveto $x $y $ms $steps"
    }

    companion object {
        /** The down-to-up spacing of `tap`. */
        const val TAP_MS = 40L
    }
}

class GestureScriptException(message: String) : IllegalArgumentException(message)

object GestureScript {

    /**
     * Parses [script] into commands. Empty segments (a trailing `;`, `;;`) are skipped. Throws
     * [GestureScriptException] naming the 1-based command number and the offending text on the first
     * error; nothing is executed from a script that does not parse whole.
     */
    fun parse(script: String): List<Command> {
        val out = ArrayList<Command>()
        var number = 0
        for (segment in script.split(';')) {
            val trimmed = segment.trim()
            if (trimmed.isEmpty()) continue
            number++
            out.add(parseOne(trimmed, number))
        }
        return out
    }

    /** Planned start offset in ms of every command, from the script's start, on the sequential timeline. */
    fun plannedOffsets(commands: List<Command>): List<Long> {
        val out = ArrayList<Long>(commands.size)
        var t = 0L
        for (c in commands) {
            out.add(t)
            t += c.durationMs
        }
        return out
    }

    private fun parseOne(text: String, number: Int): Command {
        val tokens = text.split(Regex("\\s+"))
        val name = tokens[0].lowercase()
        val args = tokens.drop(1)
        fun fail(why: String): Nothing =
            throw GestureScriptException("command $number '$text': $why")
        fun arity(n: Int, usage: String) {
            if (args.size != n) fail("expected '$usage'")
        }
        fun int(i: Int, what: String): Int =
            args[i].toIntOrNull() ?: fail("$what must be an integer, got '${args[i]}'")
        fun ms(i: Int): Long {
            val v = args[i].toLongOrNull() ?: fail("MS must be an integer, got '${args[i]}'")
            if (v < 0) fail("MS must be >= 0, got $v")
            return v
        }
        return when (name) {
            "down" -> { arity(2, "down X Y"); Command.Down(int(0, "X"), int(1, "Y")) }
            "move" -> { arity(2, "move X Y"); Command.Move(int(0, "X"), int(1, "Y")) }
            "up" -> { arity(0, "up"); Command.Up }
            "sleep" -> { arity(1, "sleep MS"); Command.Sleep(ms(0)) }
            "tap" -> { arity(2, "tap X Y"); Command.Tap(int(0, "X"), int(1, "Y")) }
            "hold" -> { arity(3, "hold X Y MS"); Command.Hold(int(0, "X"), int(1, "Y"), ms(2)) }
            "moveto" -> {
                arity(4, "moveto X Y MS STEPS")
                val steps = int(3, "STEPS")
                if (steps < 1) fail("STEPS must be >= 1, got $steps")
                Command.MoveTo(int(0, "X"), int(1, "Y"), ms(2), steps)
            }
            else -> fail("unknown command '${tokens[0]}' (down, move, up, sleep, tap, hold, moveto)")
        }
    }
}

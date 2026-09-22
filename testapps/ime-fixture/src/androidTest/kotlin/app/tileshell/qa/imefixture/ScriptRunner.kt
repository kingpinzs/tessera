package app.tileshell.qa.imefixture

import android.app.UiAutomation
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import app.tileshell.qa.imefixture.gesture.Command
import app.tileshell.qa.imefixture.gesture.GestureScript

/**
 * Executes a parsed gesture script by injecting one-pointer MotionEvents through
 * [UiAutomation.injectInputEvent] (sync = true) with real [SystemClock.uptimeMillis] event times.
 *
 * Timing is deadline-based: every command's planned start ([GestureScript.plannedOffsets]) is a
 * deadline on the uptime clock from the script's t0, and the runner sleeps until that deadline
 * before injecting, so the latency of one injection never shifts the next command. Every command's
 * actual start and end uptime are reported so a row can prove the spacing it asked for.
 */
class ScriptRunner(private val automation: UiAutomation) {

    class Report(
        val index: Int,
        val command: Command,
        val plannedOffsetMs: Long,
        val startUptime: Long,
        val endUptime: Long,
    )

    private var downTime = 0L
    private var pointerDown = false
    private var curX = 0f
    private var curY = 0f

    fun run(commands: List<Command>): List<Report> {
        val offsets = GestureScript.plannedOffsets(commands)
        val t0 = SystemClock.uptimeMillis()
        val reports = ArrayList<Report>(commands.size)
        for ((i, cmd) in commands.withIndex()) {
            sleepUntil(t0 + offsets[i])
            val start = SystemClock.uptimeMillis()
            execute(cmd, start, i + 1)
            reports.add(Report(i, cmd, offsets[i], start, SystemClock.uptimeMillis()))
        }
        return reports
    }

    private fun execute(cmd: Command, start: Long, number: Int) {
        when (cmd) {
            is Command.Down -> down(cmd.x.toFloat(), cmd.y.toFloat())
            is Command.Move -> {
                requireDown(cmd, number)
                move(cmd.x.toFloat(), cmd.y.toFloat())
            }
            is Command.Up -> {
                requireDown(cmd, number)
                up()
            }
            is Command.Sleep -> Unit // the deadline of the next command is the sleep
            is Command.Tap -> {
                down(cmd.x.toFloat(), cmd.y.toFloat())
                sleepUntil(start + Command.TAP_MS)
                up()
            }
            is Command.Hold -> {
                down(cmd.x.toFloat(), cmd.y.toFloat())
                sleepUntil(start + cmd.ms)
                up()
            }
            is Command.MoveTo -> {
                requireDown(cmd, number)
                val fromX = curX
                val fromY = curY
                for (s in 1..cmd.steps) {
                    sleepUntil(start + cmd.ms * s / cmd.steps)
                    val f = s.toFloat() / cmd.steps
                    move(fromX + (cmd.x - fromX) * f, fromY + (cmd.y - fromY) * f)
                }
            }
        }
    }

    private fun requireDown(cmd: Command, number: Int) {
        if (!pointerDown) {
            throw IllegalStateException("command $number '${cmd.text}': no pointer is down")
        }
    }

    private fun down(x: Float, y: Float) {
        if (pointerDown) up() // a stray second down would be a second pointer; make it a new touch
        downTime = SystemClock.uptimeMillis()
        curX = x
        curY = y
        inject(MotionEvent.ACTION_DOWN, downTime)
        pointerDown = true
    }

    private fun move(x: Float, y: Float) {
        curX = x
        curY = y
        inject(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
    }

    private fun up() {
        inject(MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
        pointerDown = false
    }

    private fun inject(action: Int, eventTime: Long) {
        val ev = MotionEvent.obtain(downTime, eventTime, action, curX, curY, 0)
        ev.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            if (!automation.injectInputEvent(ev, true)) {
                throw IllegalStateException("injectInputEvent returned false for ${MotionEvent.actionToString(action)}")
            }
        } finally {
            ev.recycle()
        }
    }

    /** Releases a pointer left down by a script that ended without `up`, so the next op starts clean. */
    fun finish(): Boolean {
        if (!pointerDown) return false
        up()
        return true
    }

    companion object {
        fun sleepUntil(deadline: Long) {
            while (true) {
                val remaining = deadline - SystemClock.uptimeMillis()
                if (remaining <= 0) return
                SystemClock.sleep(remaining)
            }
        }
    }
}

package app.tileshell.qa.imefixture

import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import app.tileshell.qa.imefixture.gesture.GestureScript
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/**
 * The gesture driver: one instrumentation run executes exactly one op, chosen by `-e op`, and reports
 * through instrumentation status lines, which `am instrument -r` prints as
 * `INSTRUMENTATION_STATUS: key=value`. Every key this class writes starts with `gesture.`.
 *
 *     am instrument --no-restart -r -w -e op swipe  -e points "x,y;x,y;..." -e steps N   <component>
 *     am instrument --no-restart -r -w -e op drag   -e from x,y -e to x,y -e steps N       <component>
 *     am instrument --no-restart -r -w -e op script -e script "tap X Y; sleep 110; tap X Y" <component>
 *     am instrument --no-restart -r -w -e op dump   -e out /sdcard/Download/<file>.xml      <component>
 *     am instrument --no-restart -r -w -e op wait   -e ms N                                <component>
 *
 * component = app.tileshell.qa.imefixture.test/androidx.test.runner.AndroidJUnitRunner
 * `--no-restart` keeps a running fixture activity alive; without it `am instrument` force-stops the
 * target package before and after the run. See docs/plan/qa/phase-05/TOOLING.md.
 */
@RunWith(AndroidJUnit4::class)
class Gesture {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun run() {
        val args = InstrumentationRegistry.getArguments()
        val op = args.getString("op") ?: throw IllegalArgumentException("missing -e op (swipe|drag|script|dump|wait)")
        val device = UiDevice.getInstance(instrumentation)
        status("op" to op, "uptime" to SystemClock.uptimeMillis().toString())
        try {
            when (op) {
                "swipe" -> swipe(device, args)
                "drag" -> drag(device, args)
                "script" -> script(args)
                "dump" -> dump(device, args)
                "wait" -> wait(args)
                else -> throw IllegalArgumentException("unknown op '$op' (swipe|drag|script|dump|wait)")
            }
            status("ok" to "true")
        } catch (e: Throwable) {
            status("ok" to "false", "error" to "${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    // ---- ops -------------------------------------------------------------------------------------

    /** `UiDevice.swipe(Point[], steps)`: the one continuous gesture E4 (Word Flow) needs. */
    private fun swipe(device: UiDevice, args: Bundle) {
        val points = parsePoints(args.getString("points") ?: throw IllegalArgumentException("missing -e points \"x,y;x,y;...\""))
        if (points.size < 2) throw IllegalArgumentException("swipe needs at least 2 points, got ${points.size}")
        val steps = (args.getString("steps") ?: "20").toInt()
        val t0 = SystemClock.uptimeMillis()
        val result = device.swipe(points.toTypedArray(), steps)
        val t1 = SystemClock.uptimeMillis()
        status(
            "swipe.points" to points.joinToString(";") { "${it.x},${it.y}" },
            "swipe.steps" to steps.toString(),
            "swipe.result" to result.toString(),
            "swipe.start" to t0.toString(),
            "swipe.end" to t1.toString(),
            "swipe.elapsed_ms" to (t1 - t0).toString(),
        )
        if (!result) throw IllegalStateException("UiDevice.swipe returned false")
    }

    /** `UiDevice.drag(...)`: long-press at from, move to to over steps, release. E9's space-bar drag. */
    private fun drag(device: UiDevice, args: Bundle) {
        val from = parsePoint(args.getString("from") ?: throw IllegalArgumentException("missing -e from x,y"))
        val to = parsePoint(args.getString("to") ?: throw IllegalArgumentException("missing -e to x,y"))
        val steps = (args.getString("steps") ?: "40").toInt()
        val t0 = SystemClock.uptimeMillis()
        val result = device.drag(from.x, from.y, to.x, to.y, steps)
        val t1 = SystemClock.uptimeMillis()
        status(
            "drag.from" to "${from.x},${from.y}",
            "drag.to" to "${to.x},${to.y}",
            "drag.steps" to steps.toString(),
            "drag.result" to result.toString(),
            "drag.start" to t0.toString(),
            "drag.end" to t1.toString(),
            "drag.elapsed_ms" to (t1 - t0).toString(),
        )
        if (!result) throw IllegalStateException("UiDevice.drag returned false")
    }

    /** A timed script injected with UiAutomation.injectInputEvent; see GestureScript for the grammar. */
    private fun script(args: Bundle) {
        val text = args.getString("script") ?: throw IllegalArgumentException("missing -e script \"...\"")
        val commands = GestureScript.parse(text)
        if (commands.isEmpty()) throw IllegalArgumentException("empty script")
        status("script.count" to commands.size.toString())
        val runner = ScriptRunner(uiAutomation)
        val reports = try {
            runner.run(commands)
        } finally {
            if (runner.finish()) status("script.note" to "pointer was still down at the end; injected up")
        }
        var prevStart = -1L
        for (r in reports) {
            val gap = if (prevStart < 0) "" else (r.startUptime - prevStart).toString()
            status(
                "script.cmd.${r.index}" to r.command.text,
                "script.planned.${r.index}" to r.plannedOffsetMs.toString(),
                "script.start.${r.index}" to r.startUptime.toString(),
                "script.end.${r.index}" to r.endUptime.toString(),
                "script.gap.${r.index}" to gap,
            )
            prevStart = r.startUptime
        }
        val first = reports.first().startUptime
        status(
            "script.t0" to first.toString(),
            "script.total_ms" to (reports.last().endUptime - first).toString(),
            "script.planned_total_ms" to (reports.last().plannedOffsetMs + reports.last().command.durationMs).toString(),
        )
    }

    /**
     * `Configurator.setWaitForIdleTimeout(0)` then `UiDevice.dumpWindowHierarchy(File)`, and a report
     * of which windows UiAutomation sees against which packages the dump contains, so the row can
     * prove the IME window's nodes are in the file.
     */
    private fun dump(device: UiDevice, args: Bundle) {
        val out = args.getString("out") ?: throw IllegalArgumentException("missing -e out /sdcard/Download/<file>.xml")
        Configurator.getInstance().waitForIdleTimeout = 0
        val file = File(out)
        val t0 = SystemClock.uptimeMillis()
        try {
            device.dumpWindowHierarchy(file)
        } catch (e: java.io.FileNotFoundException) {
            // With --no-restart the process keeps its scoped-storage sandbox: /sdcard/<file> is EPERM,
            // /sdcard/Download/<file> and the app's own external files dir are writable.
            throw java.io.IOException("cannot write $out (${e.message}); use /sdcard/Download/<file>.xml", e)
        }
        val t1 = SystemClock.uptimeMillis()
        val windows = windowsWithRetry()
        val windowLines = windows.map { w ->
            val r = Rect()
            w.getBoundsInScreen(r)
            "${windowType(w.type)}:${w.root?.packageName ?: "-"}:${r.toShortString()}:focused=${w.isFocused}:active=${w.isActive}"
        }
        val stats = DumpStats.of(file)
        val imePackages = windows.filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { it.root?.packageName?.toString() }.toSet()
        val imeNodes = stats.nodesByPackage.filterKeys { it in imePackages }.values.sum()
        status(
            "dump.out" to file.absolutePath,
            "dump.bytes" to file.length().toString(),
            "dump.elapsed_ms" to (t1 - t0).toString(),
            "dump.windows" to windowLines.joinToString(";"),
            "dump.window_count" to windows.size.toString(),
            "dump.roots" to stats.roots.toString(),
            "dump.nodes" to stats.nodes.toString(),
            "dump.packages" to stats.nodesByPackage.entries.joinToString(";") { "${it.key}=${it.value}" },
            "dump.ime_windows" to imePackages.joinToString(";"),
            "dump.ime_nodes" to imeNodes.toString(),
            "dump.all_windows" to (windows.mapNotNull { it.root?.packageName?.toString() }.toSet() - stats.nodesByPackage.keys).isEmpty().toString(),
        )
    }

    /** Sleeps ms and reports how long it really took: the timing sanity check. */
    private fun wait(args: Bundle) {
        val ms = (args.getString("ms") ?: throw IllegalArgumentException("missing -e ms N")).toLong()
        val t0 = SystemClock.uptimeMillis()
        ScriptRunner.sleepUntil(t0 + ms)
        val t1 = SystemClock.uptimeMillis()
        status("wait.ms" to ms.toString(), "wait.start" to t0.toString(), "wait.end" to t1.toString(), "wait.elapsed_ms" to (t1 - t0).toString())
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private fun status(vararg pairs: Pair<String, String>) {
        val b = Bundle()
        for ((k, v) in pairs) b.putString("gesture.$k", v)
        instrumentation.sendStatus(0, b)
    }

    /**
     * The same UiAutomation instance UiDevice uses (same flags): asking the Instrumentation for one
     * with different flags would disconnect UiDevice's, and UiDevice is what set
     * FLAG_RETRIEVE_INTERACTIVE_WINDOWS on it.
     */
    private val uiAutomation get() = instrumentation.getUiAutomation(Configurator.getInstance().uiAutomationFlags)

    /**
     * The window list arrives asynchronously after FLAG_RETRIEVE_INTERACTIVE_WINDOWS is set, so the
     * first calls can return an empty or partial list; poll briefly until it holds the active window.
     */
    private fun windowsWithRetry(): List<AccessibilityWindowInfo> {
        val deadline = SystemClock.uptimeMillis() + 1500
        var windows: List<AccessibilityWindowInfo> = emptyList()
        while (SystemClock.uptimeMillis() < deadline) {
            windows = uiAutomation.windows
            if (windows.any { it.isActive }) break
            SystemClock.sleep(50)
        }
        return windows
    }

    private fun parsePoints(s: String): List<Point> =
        s.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { parsePoint(it) }

    private fun parsePoint(s: String): Point {
        val parts = s.split(',').map { it.trim() }
        if (parts.size != 2) throw IllegalArgumentException("point '$s' is not x,y")
        return Point(parts[0].toInt(), parts[1].toInt())
    }

    private fun windowType(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "MAGNIFICATION_OVERLAY"
        else -> "TYPE_$type"
    }

    /** Node counts per package in a uiautomator-shaped dump file. */
    private class DumpStats(val roots: Int, val nodes: Int, val nodesByPackage: Map<String, Int>) {
        companion object {
            fun of(file: File): DumpStats {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                var roots = 0
                var nodes = 0
                val byPackage = LinkedHashMap<String, Int>()
                file.inputStream().use { input ->
                    parser.setInput(input, "UTF-8")
                    var depth = 0
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "node") {
                            nodes++
                            if (depth == 0) roots++
                            val pkg = parser.getAttributeValue(null, "package") ?: "-"
                            byPackage[pkg] = (byPackage[pkg] ?: 0) + 1
                            depth++
                        } else if (event == XmlPullParser.END_TAG && parser.name == "node") {
                            depth--
                        }
                        event = parser.next()
                    }
                }
                return DumpStats(roots, nodes, byPackage)
            }
        }
    }
}

package app.tileshell.camera

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17, the Camera's edge rows STORAGE and CALLER: `MotionClock.animate` waits on Compose's frame clock, and a
 * coroutine has one only when its context came from the composition. `ViewfinderState.say` took a scope from its caller
 * and animated the toast in it; the two activities handed it a `MainScope()`, which has no frame clock, and `:camera`
 * died with "A MonotonicFrameClock is not available in this CoroutineContext" on every toast of the activity's own
 * ("Storage full", "Recording stopped", a failed capture).
 *
 * No unit test reaches that (it needs a running composition), so the CALL SITES are held to their one safe form here,
 * read from the sources in the style of `video/TrustWiringScanTest`: in camera/, photos/ and video/ every
 * `MotionClock.animate` must be written inside a `@Composable` function, and the coroutine it runs in must be a
 * `LaunchedEffect`'s or one launched on a scope that `rememberCoroutineScope()` made in that file. The sources must
 * pass, and the faulty forms (the one that shipped, and its neighbours) must fail, so a clean result is never an empty
 * one.
 */
class MotionClockScopeScanTest {
    private val root: File = listOf(File("src/main/kotlin/app/tileshell"), File("app/src/main/kotlin/app/tileshell")).first { it.isDirectory }
    private val packages = listOf("camera", "photos", "video")

    /** The source with every comment and every string literal's text blanked (offsets and line breaks kept; a template's code kept). */
    private fun mask(source: String): String {
        val out = StringBuilder(source)
        var i = 0
        fun blank(from: Int, to: Int) { for (k in from until minOf(to, out.length)) if (out[k] != '\n') out[k] = ' ' }
        fun at(s: String) = source.startsWith(s, i)
        fun code(untilBrace: Boolean) {
            var depth = 0
            while (i < source.length) {
                when {
                    at("//") -> { val s = i; while (i < source.length && source[i] != '\n') i++; blank(s, i) }
                    at("/*") -> {
                        val s = i
                        var nested = 0
                        while (i < source.length) {
                            if (at("/*")) { nested++; i += 2 } else if (at("*/")) { nested--; i += 2; if (nested == 0) break } else i++
                        }
                        blank(s, i)
                    }
                    source[i] == '\'' -> { val s = i; i++; while (i < source.length && source[i] != '\'') { if (source[i] == '\\') i++; i++ }; i++; blank(s, i) }
                    source[i] == '"' -> {
                        val raw = at("\"\"\"")
                        i += if (raw) 3 else 1
                        var s = i
                        while (i < source.length) {
                            if (raw && at("\"\"\"")) { blank(s, i); i += 3; break }
                            if (!raw && source[i] == '"') { blank(s, i); i++; break }
                            if (!raw && source[i] == '\\') { i += 2; continue }
                            if (at("\${")) { blank(s, i + 2); i += 2; code(untilBrace = true); s = i; continue }
                            i++
                        }
                    }
                    untilBrace && source[i] == '{' -> { depth++; i++ }
                    untilBrace && source[i] == '}' -> { if (depth == 0) { out[i] = ' '; i++; return }; depth--; i++ }
                    else -> i++
                }
            }
        }
        code(untilBrace = false)
        return out.toString()
    }

    private val call = Regex("""MotionClock\s*\.\s*animate\s*\(""")
    private val function = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\($""")
    private val launch = Regex("""\b(\w+)\s*\.\s*launch\s*(?:\([^{}]*\))?\s*$""")
    private val bareLaunch = Regex("""(?<![\w.])launch\s*(?:\([^{}]*\))?\s*$""")
    private val effect = Regex("""\bLaunchedEffect\s*\([^{}]*\)\s*$""")

    /** What is wrong with the `MotionClock.animate` calls of one source: empty when each is in the safe form. */
    private fun faults(name: String, source: String): List<String> {
        val code = mask(source)
        val found = mutableListOf<String>()
        for (m in call.findAll(code)) {
            val line = code.substring(0, m.range.first).count { it == '\n' } + 1
            // The blocks that enclose the call, innermost first: each as the text that stands before its opening brace.
            val heads = mutableListOf<String>()
            var depth = 0
            var k = m.range.first - 1
            while (k >= 0) {
                when (code[k]) {
                    '}' -> depth++
                    '{' -> if (depth > 0) depth-- else {
                        var s = k - 1
                        var paren = 0
                        // The head runs back to the statement's start, through a parameter list that spans lines.
                        while (s >= 0) {
                            val c = code[s]
                            if (c == ')') paren++
                            if (c == '(') paren--
                            if (paren == 0 && (c == '{' || c == '}' || c == ';')) break
                            if (paren == 0 && c == '\n' && code.substring(s + 1, k).isNotBlank() && !code.substring(0, s).trimEnd().endsWith(",") && !code.substring(0, s).trimEnd().endsWith("@Composable")) break
                            s--
                        }
                        heads += code.substring(s + 1, k)
                    }
                }
                k--
            }
            val composable = heads.any { h -> h.contains("@Composable") && Regex("""\bfun\b""").containsMatchIn(h) }
            if (!composable) { found += "$name:$line MotionClock.animate is not inside a @Composable function"; continue }
            var runner: String? = null
            for (h in heads) {
                val head = h.trimEnd()
                val l = launch.find(head)
                if (effect.containsMatchIn(head)) { runner = "LaunchedEffect"; break }
                if (l != null) { runner = l.groupValues[1]; break }
                if (bareLaunch.containsMatchIn(head)) { runner = "launch"; break }
                if (Regex("""\bfun\b""").containsMatchIn(head) && head.contains("@Composable")) break
            }
            when {
                runner == null -> found += "$name:$line MotionClock.animate is in no LaunchedEffect and no launch of a composition scope"
                runner == "LaunchedEffect" -> Unit
                // A bare launch inside a LaunchedEffect or another launch is a child of that coroutine: judged by its parent.
                runner == "launch" -> if (!heads.any { effect.containsMatchIn(it.trimEnd()) }) found += "$name:$line MotionClock.animate is in a bare launch outside a LaunchedEffect"
                !Regex("""\bval\s+${Regex.escape(runner)}\s*=\s*rememberCoroutineScope\s*\(\s*\)""").containsMatchIn(code) ->
                    found += "$name:$line MotionClock.animate is launched on '$runner', which rememberCoroutineScope() did not make"
            }
        }
        return found
    }

    private fun sources(): List<File> = packages.flatMap { File(root, it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }.sortedBy { it.path }

    @Test
    fun `every MotionClock animate of camera, photos and video runs in a coroutine of the composition`() {
        val files = sources()
        val calls = files.sumOf { call.findAll(mask(it.readText())).count() }
        assertTrue("the scan found the calls it is about ($calls)", calls >= 15)
        assertEquals(emptyList<String>(), files.flatMap { faults(it.relativeTo(root).path, it.readText()) })
    }

    @Test
    fun `the toast is said with its text only, and no scope`() {
        // The form of the fault was a state holder's function that animated in a scope its caller chose. `say` takes
        // none, so there is none to choose wrong; the fade is the viewfinder's own effect.
        val code = mask(File(root, "camera/Viewfinder.kt").readText())
        assertTrue("ViewfinderState.say takes the text only", Regex("""fun say\(text: String\)""").containsMatchIn(code))
        assertTrue("the fade is a LaunchedEffect keyed on the toasts said", Regex("""LaunchedEffect\(state\.toastSaid\)""").containsMatchIn(code))
        val callers = packages.flatMap { File(root, it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
            .flatMap { f -> Regex("""\.say\(\s*\w*[sS]cope\b""").findAll(mask(f.readText())).map { "${f.name}: ${it.value}" } }
        assertEquals(emptyList<String>(), callers)
    }

    @Test
    fun `the scan fails on the forms that crash`() {
        val shipped = """
            class ViewfinderState {
                fun say(scope: CoroutineScope, text: String) {
                    toastJob = scope.launch {
                        MotionClock.animate("camera_toast_in", 150, LinearEasing) { toastAlpha = it }
                    }
                }
            }
        """.trimIndent()
        assertEquals(listOf("a.kt:4 MotionClock.animate is not inside a @Composable function"), faults("a.kt", shipped))
        val activityScope = """
            private val scope = MainScope()
            @Composable
            fun Page() {
                Box(Modifier.tap { scope.launch { MotionClock.animate("x", 1, LinearEasing) { } } })
            }
        """.trimIndent()
        assertEquals(listOf("b.kt:4 MotionClock.animate is launched on 'scope', which rememberCoroutineScope() did not make"), faults("b.kt", activityScope))
        val noRunner = """
            @Composable
            fun Page() {
                Thread { runBlocking { MotionClock.animate("x", 1, LinearEasing) { } } }.start()
            }
        """.trimIndent()
        assertEquals(listOf("c.kt:3 MotionClock.animate is in no LaunchedEffect and no launch of a composition scope"), faults("c.kt", noRunner))
        val good = """
            @Composable
            fun Page(state: S) {
                val scope = rememberCoroutineScope()
                // MotionClock.animate("in a comment", 1, LinearEasing) { }
                LaunchedEffect(state.n) { MotionClock.animate("x", 1, LinearEasing) { } }
                fun fire() {
                    scope.launch {
                        MotionClock.animate("y", 1, LinearEasing) { }
                    }
                }
            }
        """.trimIndent()
        assertEquals(emptyList<String>(), faults("d.kt", good))
    }
}

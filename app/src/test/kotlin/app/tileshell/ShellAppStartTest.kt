package app.tileshell

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trust review's C-L5: the `[net] cleartext permitted for <host>` lines (E13) are written on EVERY launcher
 * start-up — also the one deferred to the first unlock, when `onCreate` returns before its own start-up code. ShellApp
 * is an Application and cannot run here, so this reads its source: the lines are written by `startLauncher`, the one
 * function both paths call, and nowhere else.
 */
class ShellAppStartTest {
    private val source = listOf(File("src/main/kotlin/app/tileshell/ShellApp.kt"), File("app/src/main/kotlin/app/tileshell/ShellApp.kt")).first { it.exists() }.readText()

    /** The body of the function whose header is [header], by matching braces. */
    private fun body(header: String): String {
        val start = source.indexOf(header)
        assertTrue("$header exists", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}' && --depth == 0) return source.substring(open + 1, i)
        }
        error("unbalanced braces after $header")
    }

    private val call = "FixedEndpoints.logPolicy()"

    @Test
    fun `the net lines are written by startLauncher, which both start-up paths call`() {
        assertTrue("startLauncher writes the [net] lines", call in body("private fun startLauncher()"))
        assertEquals("written in one place only", 1, source.split(call).size - 1)
        val onCreate = body("override fun onCreate()")
        // The unlocked path and the deferred one (the ACTION_USER_UNLOCKED receiver) both go through startLauncher.
        assertEquals(2, onCreate.split("startLauncher()").size - 1)
        val receiver = onCreate.substringAfter("override fun onReceive(").substringBefore("IntentFilter(Intent.ACTION_USER_UNLOCKED)")
        assertTrue("the unlock receiver starts the launcher", "startLauncher()" in receiver)
    }
}

package app.tileshell.cortana

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L13-1 (review/2026-09-25-L13-1-fix-plan.md, Q1 "(b)"): Tess's window has a real activity-result registry. A launch
 * goes out through the pass-through, Tess steps aside only once it is out, and the result comes back to the launcher
 * that asked — with Tess back first. A launch that cannot go out is a cancelled result, never a lost one.
 */
class SessionResultRegistryTest {

    /** A contract whose output says which result code it was parsed from. */
    private val contract = object : ActivityResultContract<String, String>() {
        override fun createIntent(context: Context, input: String) = Intent()
        override fun parseResult(resultCode: Int, intent: Intent?) = "code=$resultCode"
    }

    private val events = mutableListOf<String>()
    private var started: Pair<Int, (Int, Intent?) -> Unit>? = null

    private fun registry(canStart: Boolean = true) = SessionResultRegistry(
        context = ContextWrapper(null),
        start = { requestCode, _, onResult ->
            events += "start"
            if (canStart) started = requestCode to onResult
            canStart
        },
        stepAside = { events += "aside" },
        comeBack = { events += "back" },
    )

    @Test
    fun `a launch goes out, then Tess steps aside, and nothing comes back yet`() {
        val launcher = registry().register("pick", contract) { events += "result $it" }
        launcher.launch("photo")
        assertEquals(listOf("start", "aside"), events)
    }

    @Test
    fun `the result brings Tess back, then reaches the launcher that asked`() {
        registry().register("pick", contract) { events += "result $it" }.launch("photo")
        val (_, onResult) = started!!
        onResult(Activity.RESULT_OK, Intent())
        assertEquals(listOf("start", "aside", "back", "result code=${Activity.RESULT_OK}"), events)
    }

    @Test
    fun `Back from the picker is a cancelled result and Tess comes back unchanged`() {
        registry().register("pick", contract) { events += "result $it" }.launch("photo")
        started!!.second(Activity.RESULT_CANCELED, null)
        assertEquals(listOf("start", "aside", "back", "result code=${Activity.RESULT_CANCELED}"), events)
    }

    @Test
    fun `a launch that cannot go out is cancelled at once and Tess never steps aside`() {
        registry(canStart = false).register("pick", contract) { events += "result $it" }.launch("photo")
        assertEquals(listOf("start", "result code=${Activity.RESULT_CANCELED}"), events)
        assertNull(started)
    }

    @Test
    fun `a contract with a synchronous result never leaves the window`() {
        val synchronous = object : ActivityResultContract<String, String>() {
            override fun createIntent(context: Context, input: String) = Intent()
            override fun parseResult(resultCode: Int, intent: Intent?) = "parsed"
            override fun getSynchronousResult(context: Context, input: String) = SynchronousResult("already $input")
        }
        registry().register("pick", synchronous) { events += "result $it" }.launch("granted")
        assertEquals(listOf("result already granted"), events)
    }

    @Test
    fun `the bridge delivers a result once, to the request code that asked`() {
        val got = mutableListOf<Int>()
        CortanaResults.await(41) { code, _ -> got += code }
        assertFalse("another request code gets nothing", CortanaResults.deliver(42, Activity.RESULT_OK, null))
        assertTrue(CortanaResults.deliver(41, Activity.RESULT_OK, null))
        assertFalse("a second delivery is dropped", CortanaResults.deliver(41, Activity.RESULT_OK, null))
        assertEquals(listOf(Activity.RESULT_OK), got)
    }

    @Test
    fun `a closed session forgets what it was waiting for`() {
        CortanaResults.await(7) { _, _ -> error("delivered to a destroyed session") }
        CortanaResults.forgetAll()
        assertFalse(CortanaResults.deliver(7, Activity.RESULT_OK, null))
    }
}

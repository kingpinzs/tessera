package app.tileshell.qa.imefixture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.EditText

/**
 * Shared by both fixture screens: inflates the layout, wires the mirror, and honours the `focus`
 * intent extra (a field id name such as `field_email`): that field is focused on start and the
 * keyboard is requested once the window has focus, so a driver can open a given field with one
 * `am start ... -e focus field_email` and no taps.
 *
 * Both activities are singleTop: a second `am start` of a screen that is already on top would
 * otherwise only bring the old instance forward and drop the new intent (its extras are not part
 * of the intent filter), so the `focus` extra arrives through [onNewIntent] and is applied the
 * same way. A driver that wants a fresh instance adds `-S` (force-stop before start).
 */
abstract class FixtureActivity(private val layout: Int) : Activity() {

    protected lateinit var mirror: Mirror
    private var pendingShow: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout)
        val root = findViewById<ViewGroup>(R.id.root)
        onFieldsReady()
        mirror = Mirror(this, root)
        mirror.attach()
        applyFocusExtra(intent)
        if (intent.getBooleanExtra(EXTRA_TICKER, false)) startTicker()
    }

    /**
     * `-e ticker true`: flip the mirror block's background between two greys one level apart on EVERY
     * frame. The emulator's screenrecord only emits a frame when the screen changes, so a capture of a
     * still screen has no frames to time a touch against; with the ticker every vsync is a frame (phase 05
     * E3 motion, the press-popup timing). Off by default: a screen that never idles breaks plain dumps.
     */
    private fun startTicker() {
        val block = findViewById<View>(R.id.mirror_block) ?: return
        var odd = false
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                odd = !odd
                block.setBackgroundColor(if (odd) 0xFFEEEEEE.toInt() else 0xFFEFEFEF.toInt())
                if (!isFinishing) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(cb)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyFocusExtra(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) showPending()
    }

    private fun applyFocusExtra(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_FOCUS) ?: return
        pendingShow = mirror.focusField(name)
        // showSoftInput before the window is focused is ignored by the IMM ("view not served"); on
        // a fresh start onWindowFocusChanged does it, on a re-delivered intent the window is already
        // focused and it can happen now.
        if (hasWindowFocus()) showPending()
    }

    private fun showPending() {
        val field = pendingShow ?: return
        pendingShow = null
        if (!field.isFocused) field.requestFocus()
        field.post { mirror.showKeyboard(field) }
    }

    /** Per-screen setup that XML cannot express (field_filter's InputFilters). */
    protected open fun onFieldsReady() {}

    companion object {
        const val EXTRA_FOCUS = "focus"
        const val EXTRA_TICKER = "ticker"

        /** Keeps only ASCII lowercase letters from an insertion; the LengthFilter caps at 5. */
        val LOWERCASE_ONLY = InputFilter { source, start, end, _, _, _ ->
            val kept = StringBuilder()
            for (i in start until end) {
                val c = source[i]
                if (c in 'a'..'z') kept.append(c)
            }
            if (kept.length == end - start) null else kept
        }
    }
}

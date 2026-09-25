package app.tileshell.clock

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.tileshell.diag.Diagnostics

/**
 * The ring toast over the app in use (phase 15 T15-14 and T15-32, Q-E A): a full-screen `TYPE_APPLICATION_OVERLAY`
 * window behind "Display over other apps". It dims what is beneath (8.12) and consumes every touch — a touch
 * outside the toast reaches neither the app nor the ring — and it draws the toast below the app's status bar when
 * the app shows one, because an app overlay sits under SystemUI (the recorded seam against 8.1, H5).
 */
class RingOverlay(private val context: Context) {
    private var view: ComposeView? = null
    private var owner: Owner? = null

    fun show() {
        if (view != null) return
        val wm = context.getSystemService(WindowManager::class.java)
        val o = Owner().apply { start() }
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                RingRoot {
                    val ring by RingService.state.collectAsState()
                    val top = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
                    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        ring?.let { RingToastHost(it, top) }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            dimAmount = 0.5f // approximation, H5
            title = "TesseraRing"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { wm.addView(v, params) }
            .onSuccess { view = v; owner = o; Diagnostics.add("alarms", "overlay shown") }
            .onFailure { Diagnostics.add("alarms", "overlay could not be shown: $it"); o.stop() }
    }

    fun hide() {
        val v = view ?: return
        runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
        owner?.stop()
        view = null
        owner = null
        Diagnostics.add("alarms", "overlay removed")
    }

    /** A window added from a service has no activity to own its composition: this is its lifecycle. */
    private class Owner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

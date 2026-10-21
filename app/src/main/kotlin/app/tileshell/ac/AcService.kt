package app.tileshell.ac

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The shell's accessibility service as the rest of the launcher's process sees it (phase 04 build task 4).
 * CONTRACT STUB (docs/plan/qa/phase-04/BUILD-CONTRACT.md): build task 4 owns this file and fills the bodies; the
 * Back hold (task 11), the volume panel (task 8) and the action center (task 5) compile against these signatures.
 */
object AcService {
    private val mutableConnected = MutableStateFlow(false)

    /** True between the service's `onServiceConnected` and its unbind. */
    val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    /** The connected service, or null (the shell's service is off, or a UiAutomation unbound it). */
    @Volatile var instance: AccessibilityService? = null
        private set

    /** Set by the service itself at connect (non-null) and unbind (null). */
    fun bind(service: AccessibilityService?) {
        instance = service
        mutableConnected.value = service != null
    }

    /** `performGlobalAction` through the connected service; false when it is off. */
    fun globalAction(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
}

/** A window-state event the service saw (`TYPE_WINDOW_STATE_CHANGED`): who is in front now. */
data class WindowStateEvent(
    val packageName: String,
    val className: String?,
    /** Whether [className] names an activity (not a dialog or a toast inside one): read by the service at the event. */
    val isActivity: Boolean,
    /** Whether the window is an input method's. */
    val isInputMethod: Boolean,
    val uptimeMs: Long,
)

/**
 * The window-state bus (build task 4 emits; task 5 closes the open panel for another package's activity, round 4 D9;
 * task 8 slides the volume panel away when the page changes and passes the volume keys in a camera app, round 4 D8).
 */
object AcEvents {
    private val mutable = MutableSharedFlow<WindowStateEvent>(extraBufferCapacity = 16)
    val windowState: SharedFlow<WindowStateEvent> = mutable.asSharedFlow()

    /** The last window-state event's package (KeyFilter's `foregroundPkg`), or null before the first. */
    @Volatile var lastPackage: String? = null
        private set

    fun emit(event: WindowStateEvent) {
        lastPackage = event.packageName
        mutable.tryEmit(event)
    }
}

/**
 * The shell's overlay windows, all `TYPE_ACCESSIBILITY_OVERLAY` added through the connected service (Decisions
 * "Queue item 6"). CONTRACT STUB: build task 4 fills the bodies (`PanelWindow.flags` for the params) and prints every
 * added window's tagged nodes from the service's `dump()`; the volume panel (task 8) adds its window here.
 */
object OverlayWindows {
    /**
     * The params every overlay window uses (`PanelWindow.flags`): full width, top-start at y = 0, `FLAG_LAYOUT_IN_SCREEN |
     * FLAG_LAYOUT_NO_LIMITS`, cutout mode ALWAYS, insets not fitted, [title] for `dumpsys window windows`, focusable only
     * when [focusable]. [heightPx] is MATCH_PARENT for the full display.
     */
    fun params(title: String, focusable: Boolean, heightPx: Int = WindowManager.LayoutParams.MATCH_PARENT): WindowManager.LayoutParams =
        WindowManager.LayoutParams()

    /** Adds [view] as the overlay window [name] through the connected service; false when the service is off. */
    fun add(name: String, view: View, params: WindowManager.LayoutParams): Boolean = false

    fun update(name: String, params: WindowManager.LayoutParams) {}

    fun remove(name: String) {}

    /** The service's context for building overlay views (its WindowManager accepts the accessibility overlay type). */
    fun context(): Context? = AcService.instance
}

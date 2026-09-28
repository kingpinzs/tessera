package app.tessera.r4probe

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * P5's window source (review/2026-09-28-r4-kit-review.md B5): phase 04's action center is an accessibility overlay
 * (TYPE_ACCESSIBILITY_OVERLAY), which only a connected accessibility service can add, and which the window manager
 * layers differently from an app overlay. The service does nothing else: no events are read and no window content is
 * retrieved. The host script turns it on for the P5 step (appending to the phone's own enabled services) and restores
 * the exact previous setting after.
 */
class ProbeA11yService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        HelperLink.note("accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        HelperLink.note("accessibility service disconnected")
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile var instance: ProbeA11yService? = null
    }
}

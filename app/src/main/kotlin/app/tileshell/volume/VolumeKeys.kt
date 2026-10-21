package app.tileshell.volume

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent

/**
 * The volume keys the shell's accessibility service consumed (`KeyFilter.consume`, build task 4). CONTRACT STUB
 * (docs/plan/qa/phase-04/BUILD-CONTRACT.md): build task 8 owns this file and fills it — step the target stream through
 * `AudioManager` with no `FLAG_SHOW_UI` and show the W10M volume panel.
 */
object VolumeKeys {
    fun onKey(service: AccessibilityService, event: KeyEvent) {}
}

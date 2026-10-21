package app.tileshell.ac

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The action center's notification list (phase 04 build task 7: groups, items, swipe, expand with the app's buttons and
 * the reply box). CONTRACT STUB (docs/plan/qa/phase-04/BUILD-CONTRACT.md): task 7 owns this file and fills it; the
 * panel (task 5) places it under "Clear all / Expand" and applies the list offset (`ListOffset`) to it.
 */
@Composable
fun NotificationList(control: AcPanelControl, modifier: Modifier = Modifier) {
}

/** "Clear all": cancels every clearable notification (task 7: `ShadeRules.clearAll` through the listener). */
fun clearAllNotifications() {}

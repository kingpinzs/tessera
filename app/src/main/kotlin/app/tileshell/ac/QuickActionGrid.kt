package app.tileshell.ac

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The quick-action grid inside the open action center (phase 04 build task 6: R3 A19 tiles, C2's order filled from the
 * bottom row, the live-state caption Y16). CONTRACT STUB (docs/plan/qa/phase-04/BUILD-CONTRACT.md): task 6 owns this
 * file and fills it; the panel (task 5) places it at the grid's top ([AcGeometry]) and animates expand / collapse.
 * The grid lays out its own cells from [AcGeometry] relative to its own top-left at the grid's first row.
 */
@Composable
fun QuickActionGrid(control: AcPanelControl, modifier: Modifier = Modifier) {
}

/** The number of grid rows for the current quick-action prefs when expanded (task 5 sizes the grid from it). */
fun quickActionRows(): Int = 4

package app.tileshell.photos

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.clock.PressBox
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.ShellType

/** One row of the Edit sheet (1.7.2): a 32-epx accent square with its glyph, and the editor's name. */
data class EditSheetRow(val glyph: String, val label: String, val tag: String, val onPick: () -> Unit)

/**
 * W10M's Edit sheet (r11/photos.md 1.7.2, pass 2 §4.9): bottom-anchored, #1F1F1F, 32-epx icon squares at x 12, the
 * first 15 epx below the sheet's top, labels at x 57.25, rows on the measured 62-epx pitch, 15 epx under the last icon.
 * A tap off it closes it. Tags `edit_sheet`, `edit_sheet_scrim` and each row's own.
 */
@Composable
fun BoxScope.EditSheet(rows: List<EditSheetRow>, isOpen: () -> Boolean, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    OverlayLayer(active = isOpen) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().testTag("edit_sheet_scrim").pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onDismiss() }
            })
            val pitch = 62f
            val height = PhotosMetrics.SHEET_ICON_TOP * 2 + PhotosMetrics.SHEET_ICON.value + pitch * (rows.size - 1)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(height.dp).background(PhotosMetrics.SHEET_FILL).testTag("edit_sheet")) {
                rows.forEachIndexed { i, row ->
                    val top = PhotosMetrics.SHEET_ICON_TOP + pitch * i
                    PressBox(Modifier.offset(y = (top - 8f).dp).fillMaxWidth().height(48.dp).testTag(row.tag), onClick = { onDismiss(); row.onPick() }) {
                        Box(Modifier.offset(x = PhotosMetrics.SHEET_ICON_LEFT.dp, y = 8.dp).size(PhotosMetrics.SHEET_ICON).background(colors.accent)) {
                            PhotoGlyph(row.glyph, 20f, Color.White, Modifier.fillMaxSize())
                        }
                        BasicText(row.label, Modifier.align(Alignment.CenterStart).offset(x = PhotosMetrics.SHEET_LABEL_LEFT.dp), style = ShellType.body.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

/** Starts the editor ([trim] false) or the trim screen for [item], in `:photosedit`, by explicit component (r3 D8). */
fun startEditor(activity: ComponentActivity, item: MediaEntry, trim: Boolean) {
    Diagnostics.add("photosapp", "open ${if (trim) "trim" else "editor"} ${item.id}")
    activity.startActivity(Intent(activity, EditActivity::class.java).setData(PhotoStore.uriOf(item)).putExtra(EditNav.EXTRA_TRIM, trim))
}

/** What lies over the library: the viewer, and a held video tile's Edit sheet. */
@Composable
fun BoxScope.PhotosOverlays(nav: PhotosNav, library: Library, activity: ComponentActivity) {
    val held = nav.sheetFor
    if (held != null) {
        EditSheet(
            rows = listOf(EditSheetRow(Glyph.VIDEO_CLIP, "Trim", "edit_sheet_trim") { startEditor(activity, held, trim = true) }),
            isOpen = { nav.sheetFor != null },
            onDismiss = { dismissOverlay { nav.sheetFor = null } },
        )
        BackHandler { dismissOverlay { nav.sheetFor = null } }
    }
}

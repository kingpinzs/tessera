package app.tileshell.start

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.Slot
import app.tileshell.tiles.SlotResolver
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/**
 * The slot app picker (interview Q1; H6 approximation): a W10M list page of only the apps that handle the
 * slot's category. Choosing one is an explicit assignment that always sticks. The host places it between the drawn
 * W10M status bar and nav bar (bar rule), so Back and the Windows key stay on screen.
 */
@Composable
fun SlotPicker(slot: Slot, onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val catalog = remember { AppCatalog.get(context) }
    val candidates = remember(slot) { SlotResolver(context, catalog).candidates(slot) }
    Column(Modifier.fillMaxSize().background(colors.background).testTag("slot_picker")) {
        BasicText("CHOOSE AN APP", style = ShellType.base.copy(color = colors.text), modifier = Modifier.padding(start = 12.dp, top = 14.dp))
        BasicText(slot.label.lowercase(), style = ShellType.header.copy(color = colors.text), modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        if (candidates.isEmpty()) {
            BasicText("No installed app can be used for ${slot.label}.", style = ShellType.body.copy(color = colors.subtleText), modifier = Modifier.padding(12.dp).testTag("slot_picker_empty"))
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(candidates, key = { it.key }) { entry ->
                PressRow(onClick = {
                    LayoutStore.get(context).assignSlot(slot, entry.component)
                    onDone()
                }, modifier = Modifier.fillMaxWidth().height(44.dp).testTag("slot_candidate:${entry.component.packageName}")) {
                    Row(Modifier.fillMaxSize().padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        catalog.icon(entry, 120)?.let { Image(it, null, Modifier.size(41.dp)) } ?: Box(Modifier.size(41.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicText(entry.label, style = ShellType.body.copy(color = colors.text))
                    }
                }
            }
        }
    }
}

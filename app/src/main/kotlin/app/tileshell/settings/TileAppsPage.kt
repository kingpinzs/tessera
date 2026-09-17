package app.tileshell.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.tileshell.apps.AppCatalog
import app.tileshell.brand.Glyph
import app.tileshell.start.SlotPicker
import app.tileshell.tiles.LayoutStore
import app.tileshell.tiles.Slot
import app.tileshell.tiles.SlotResolver

/** Settings > Tile apps: reassign any slot (interview Q1). Role slots follow Android's default apps. */
@Composable
fun TileAppsPage() {
    val context = LocalContext.current
    val catalog = remember { AppCatalog.get(context) }
    val resolver = remember { SlotResolver(context, catalog) }
    val layout by LayoutStore.get(context).layout.collectAsState()
    catalog.apps.collectAsState().value
    var picking by remember { mutableStateOf<Slot?>(null) }
    val slot = picking
    if (slot != null) {
        Box(Modifier.fillMaxSize()) { SlotPicker(slot) { picking = null } }
        return
    }
    PageHeader(Glyph.APPS, "Tile apps")
    Slot.entries.forEach { s ->
        val entry = resolver.resolve(s, layout.explicitSlots)
        val how = when {
            s.role != null -> "follows your default app"
            layout.explicitSlots.containsKey(s) -> "you chose this"
            entry != null -> "Android's default"
            else -> "not chosen yet"
        }
        TwoLineItem(Glyph.APPS, s.label, "${entry?.label ?: "None"} · $how", "tile_app_slot:${s.name}") {
            if (s.role == null) picking = s
        }
    }
}

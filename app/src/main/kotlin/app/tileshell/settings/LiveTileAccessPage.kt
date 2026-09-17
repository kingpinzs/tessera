package app.tileshell.settings

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.brand.Glyph
import app.tileshell.tiles.api.LiveTileSettings
import app.tileshell.tiles.api.LiveTileStore
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType

/** Settings > Live tile access: the per-app kill switch for the Live Tile API (phase 01 Decisions, R5 §4). */
@Composable
fun LiveTileAccessPage() {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    var tick by remember { mutableIntStateOf(0) }
    val catalog = remember { AppCatalog.get(context) }
    @Suppress("UNUSED_EXPRESSION") tick
    val packages = (LiveTileStore.get(context).callerPackages() + LiveTileSettings.disabledPackages(context)).sorted()
    PageHeader(Glyph.APPS, "Live tile access")
    SectionHeader("All apps")
    ToggleRow("Let apps update their tiles", LiveTileSettings.isApiEnabled(context), "livetile_api_enabled") { on ->
        LiveTileSettings.setApiEnabled(context, on)
        tick++
    }
    ToggleRow("Accept badge counts from senders that do not identify themselves", LiveTileSettings.trustsUnverifiedLegacyBadges(context), "livetile_legacy_trust") { on ->
        LiveTileSettings.setTrustUnverifiedLegacyBadges(context, on)
        tick++
    }
    SectionHeader("Apps")
    if (packages.isEmpty()) {
        BasicText("No app has updated its tile yet.", style = ShellType.body.copy(color = colors.subtleText), modifier = Modifier.padding(12.dp))
    }
    packages.forEach { pkg ->
        val label = catalog.firstForPackage(pkg)?.label ?: pkg
        ToggleRow(label, !LiveTileSettings.isDisabled(context, pkg), "livetile_access:$pkg") { on ->
            LiveTileSettings.setDisabled(context, pkg, !on)
            tick++
        }
    }
}

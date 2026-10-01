package app.tileshell.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.prefs.PodId
import app.tileshell.prefs.ShellSettings

/** The hub item's title: the branded name, capitalised ("Pod bay"). */
internal val podBayTitle: String get() = Brand.POD_BAY_NAME.replaceFirstChar { it.uppercase() }

/**
 * Phase 14 (Decisions "Settings", P4 design, H5): one W10M toggle per pod, default all On, in the fixed order the pod
 * bay draws them. A pod switched off is absent from the page; the order of the rest never changes.
 */
@Composable
fun PodBaySettingsPage() {
    val settings = ShellSettings.get(LocalContext.current)
    val pods by settings.pods.collectAsState()
    Column(Modifier.fillMaxWidth().testTag("settings_page_pod_bay")) {
        PageHeader(Glyph.APPS, podBayTitle)
        PodId.entries.forEach { pod ->
            ToggleRow(pod.title, pod in pods.enabled, "pod_switch:${pod.id}") { on -> settings.setPod(pod, on) }
        }
    }
}

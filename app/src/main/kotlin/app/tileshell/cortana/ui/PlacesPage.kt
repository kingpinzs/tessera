package app.tileshell.cortana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.settings.PageHeader
import app.tileshell.settings.SectionHeader
import app.tileshell.settings.TwoLineItem
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType

/**
 * Cortana's saved places (phase 03 Decisions, "place source ruled C"): rename, delete or move a place,
 * save the one you are standing in, or type an address.
 *
 * **This whole page is a P4 design.** R7 measured no Places page — W10M kept places inside the
 * Notebook, which R7 §3.9.3 never opens — so nothing here is a measurement. It is built out of the
 * shell's own Settings house style ([PageHeader], [TwoLineItem], [SectionHeader]) so it reads as part
 * of the shell rather than inventing a second look, and it uses the Settings theme (the shell's own
 * colours), not the Cortana destination pages' measured (14,19,13).
 *
 * The attribution line is not decoration: a place typed as an address is resolved through Nominatim in
 * the action layer, and OpenStreetMap's licence requires the credit wherever its data is shown.
 *
 * [onSaveCurrentLocation] and [onLookUpAddress] are the lead's — GPS and the Nominatim lookup live in
 * the action layer. This page does no location work and makes no network call.
 */
@Composable
fun PlacesPage(
    prefillName: String? = null,
    onSaveCurrentLocation: (name: String) -> Unit,
    onLookUpAddress: (name: String, address: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore.get(context) }
    val places by store.places.collectAsState()
    val colors = LocalShellColors.current

    // Cortana opens this page pre-filled when a spoken place had no saved place ("I don't know where
    // <place> is yet. Want to save it?").
    var name by remember(prefillName) { mutableStateOf(prefillName.orEmpty()) }
    var address by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())) {
        PageHeader(Glyph.LOCATION, "cortana")
        BasicText(
            "places",
            Modifier.padding(start = 12.dp, bottom = 8.dp).testTag("places_title"),
            style = ShellType.header.copy(color = colors.text),
        )

        // ---- the saved places, each opening rename / delete / move
        if (places.isEmpty()) {
            BasicText(
                "No saved places yet",
                Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp).testTag("places_empty"),
                style = ShellType.body.copy(color = colors.subtleText),
            )
        }
        places.forEach { place ->
            TwoLineItem(
                glyph = Glyph.LOCATION,
                title = place.name,
                subtitle = "${"%.5f".format(place.lat)}, ${"%.5f".format(place.lon)} · ${place.source}",
                tag = "places_row:${place.id}",
            ) {
                renaming = if (renaming == place.id) null else place.id
                renameText = place.name
            }
            if (renaming == place.id) {
                Column(Modifier.fillMaxWidth().padding(start = 55.dp, end = 12.dp, bottom = 8.dp)) {
                    NameField(renameText, colors.text, colors.accent, "places_rename_field") { renameText = it }
                    Spacer(Modifier.height(6.dp))
                    Column {
                        TextAction("rename", "places_rename:${place.id}", colors.text) {
                            if (renameText.isNotBlank()) store.renamePlace(place.id, renameText.trim())
                            renaming = null
                        }
                        // "Move" re-saves the place where the phone is now — the lead's GPS callback.
                        TextAction("move here", "places_move:${place.id}", colors.text) {
                            onSaveCurrentLocation(place.name)
                            renaming = null
                        }
                        TextAction("delete", "places_delete:${place.id}", colors.text) {
                            store.deletePlace(place.id)
                            renaming = null
                        }
                    }
                }
            }
        }

        // ---- adding one
        SectionHeader("add a place")
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp)) {
            NameField(name, colors.text, colors.accent, "places_name_field") { name = it }
            Spacer(Modifier.height(8.dp))
            TextAction("save current location", "places_save_current", colors.text) {
                if (name.isNotBlank()) onSaveCurrentLocation(name.trim())
            }
            Spacer(Modifier.height(8.dp))
            NameField(address, colors.text, colors.accent, "places_address_field", hint = "street, city") { address = it }
            Spacer(Modifier.height(8.dp))
            TextAction("look up this address", "places_type_address", colors.text) {
                if (name.isNotBlank() && address.isNotBlank()) onLookUpAddress(name.trim(), address.trim())
            }
        }

        // ---- the licence credit for every address resolved through Nominatim
        BasicText(
            "© OpenStreetMap contributors",
            Modifier.padding(start = 12.dp, top = 24.dp, bottom = 16.dp).testTag("places_attribution"),
            style = ShellType.caption.copy(color = colors.subtleText),
        )

        TextAction("back", "places_back", colors.text, onBack)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NameField(
    value: String,
    text: Color,
    accent: Color,
    tag: String,
    hint: String = "name",
    onChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .border(1.dp, accent)
            .padding(horizontal = 8.dp)
            .testTag(tag),
    ) {
        if (value.isEmpty()) {
            BasicText(hint, Modifier.padding(top = 7.dp), style = ShellType.body.copy(color = text.copy(alpha = 0.5f)))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = ShellType.body.copy(color = text),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
        )
    }
}

/** The Settings house style's text button: a 1-epx outlined label, as phase 01's pages use. */
@Composable
private fun TextAction(label: String, tag: String, text: Color, onClick: () -> Unit) {
    app.tileshell.ui.components.PressRow(
        onClick,
        Modifier.width(220.dp).height(32.dp).border(1.dp, text).testTag(tag),
    ) {
        BasicText(
            label,
            Modifier.padding(start = 10.dp, top = 6.dp),
            style = ShellType.body.copy(color = text),
        )
    }
}

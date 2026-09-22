package app.tileshell.cortana.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.cortana.CortanaChecklist
import app.tileshell.cortana.CortanaPermissionActivity
import app.tileshell.cortana.CortanaPrefs
import app.tileshell.cortana.CortanaRow
import app.tileshell.cortana.CortanaService
import app.tileshell.cortana.speech.SpeechClient
import app.tileshell.cortana.speech.Voice
import app.tileshell.onboarding.RowState
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/**
 * Cortana's Settings page (phase 03 build task 8).
 *
 * The final release's page is UNMEASURED (R7 §3.9.4). Its structure is a LOW candidate from R7 §3.1.4 —
 * a native dark scrolling page of section titles, description text, toggles, buttons and combo boxes
 * (H27) — and its look is an approximation using the Reminders page's (14,19,13) background and header
 * geometry (H28). Those are the two rows Jeremy judges; everything ON the page is ruled.
 */
object CortanaSettingsValues {
    /** R7 §3.5: the native dark page's background, shared with the Reminders page. */
    val BACKGROUND = Color(0xFF0E130D)
    val SECTION_COLOUR = Color(0xFFE7ECE6)
    val DESCRIPTION_COLOUR = Color(0xFFA8AAA7)

    /** R7 §3.3.2: the title at x 60 epx, cap 14.1 epx (20-epx type), cap centre 26.3 epx from the top. */
    const val TITLE_LEFT_EPX = 60f
    const val TITLE_CAP_CENTRE_EPX = 26.3f
    const val SECTION_LEFT_EPX = 11.9f
}

@Composable
fun CortanaSettingsPage(onOpenPlaces: () -> Unit, onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { CortanaPrefs.get(context) }
    val settings by prefs.settings.collectAsState()
    val colors = LocalShellColors.current
    var picking by remember { mutableStateOf(false) }
    // Coming back from a permission prompt has to re-read the live state, not a remembered one.
    var tick by remember { mutableIntStateOf(0) }
    val rows = remember(tick, settings) { CortanaChecklist.rows(context) }
    val voices = remember(tick) { SpeechClient.voices() }

    if (picking) {
        NotesAppPicker(
            onPicked = { component ->
                prefs.update { it.copy(notesApp = component) }
                picking = false
            },
            onCancel = { picking = false },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(CortanaSettingsValues.BACKGROUND)
            .verticalScroll(rememberScrollState())
            .testTag("cortana_settings"),
    ) {
        CortanaPageTitle("Settings", onOpenMenu, "cortana_settings_title")

        SettingsSection("Voice")
        SettingsDescription("Which voice ${Brand.ASSISTANT_NAME} speaks in. Every voice is on the phone; none of them needs the internet.")
        if (voices.isEmpty()) {
            SettingsDescription("The voices load with the speech engine. Open ${Brand.ASSISTANT_NAME} once and come back.")
        }
        voices.forEach { voice ->
            VoiceRow(voice, settings.voiceId == voice.id) {
                prefs.update { it.copy(voiceId = voice.id) }
                // Speaking the name in the chosen voice is how a voice picker is judged (H2).
                SpeechClient.speak("This is ${voice.name}.", voice.id)
            }
        }

        SettingsSection("Notes")
        SettingsDescription("Where \"take a note\" goes.")
        SettingsRow(
            Glyph.NOTE,
            settings.notesApp?.let { component ->
                AppCatalog.get(context).find(component)?.label ?: component.packageName
            } ?: "Choose an app",
            if (settings.notesApp == null) "Not set" else "Tap to change",
            "cortana_settings_notes",
        ) { picking = true }

        SettingsSection("Places")
        SettingsDescription("Saved places your reminders can use, like \"remind me to take out the trash when I get home\".")
        SettingsRow(Glyph.LOCATION, "Saved places", "Add, rename or remove", "cortana_settings_places", onOpenPlaces)

        // R6 §3.5.1 (MEDIUM, 10586-era): W10M's own wording, with "Search button" adapted to the side key.
        // Microsoft's final-release document calls the option "Lock Screen" (R6 §3.5.2). Default On is a LOW
        // candidate (R6 §3.5.3); H8 judges both the default and the wording.
        SettingsSection("Lock screen options")
        CortanaToggleRow(
            "Open ${Brand.ASSISTANT_NAME} when I press and hold the side key – even when my device is locked",
            settings.lockScreenOption,
            "cortana_settings_lock_screen",
        ) { checked -> prefs.update { it.copy(lockScreenOption = checked) } }

        SettingsSection("Setup")
        rows.forEach { row ->
            ChecklistRow(row) {
                tick++
                when {
                    row.id == "assistant" -> CortanaPermissionActivity.requestAssistantRole(context)
                    row.permissions.isNotEmpty() -> CortanaPermissionActivity.request(context, row.permissions)
                    row.id == "speech_process" -> SpeechClient.preload()
                }
            }
        }
        SettingsDescription(
            "Assistant service: ${if (CortanaService.serviceReady) "running" else "not running"}. " +
                "Tap a red row to fix it, then come back to this page.",
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** R7 §3.3.2's header geometry, shared by every Cortana destination page. */
@Composable
fun CortanaPageTitle(title: String, onOpenMenu: () -> Unit, tag: String) {
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        CortanaMenuButton(Modifier.align(Alignment.CenterStart), onOpenMenu)
        BasicText(
            title,
            style = ShellType.subtitle.copy(color = Color.White),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = CortanaSettingsValues.TITLE_LEFT_EPX.dp)
                .testTag(tag),
        )
    }
}

@Composable
private fun SettingsSection(text: String) {
    BasicText(
        text,
        style = ShellType.body.copy(color = CortanaSettingsValues.SECTION_COLOUR),
        modifier = Modifier
            .padding(start = CortanaSettingsValues.SECTION_LEFT_EPX.dp, top = 18.dp, bottom = 4.dp)
            .testTag("cortana_settings_section:${text.lowercase().replace(' ', '_')}"),
    )
}

@Composable
private fun SettingsDescription(text: String) {
    BasicText(
        text,
        style = ShellType.caption.copy(color = CortanaSettingsValues.DESCRIPTION_COLOUR),
        modifier = Modifier.padding(horizontal = CortanaSettingsValues.SECTION_LEFT_EPX.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsRow(glyph: String, title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    PressRow(onClick, Modifier.fillMaxWidth().height(60.dp).testTag(tag)) {
        Row(Modifier.padding(start = CortanaSettingsValues.SECTION_LEFT_EPX.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp)) { BasicText(glyph, style = iconStyle(Color.White, 24)) }
            Column {
                BasicText(title, style = ShellType.body.copy(color = Color.White))
                BasicText(subtitle, style = ShellType.caption.copy(color = CortanaSettingsValues.DESCRIPTION_COLOUR))
            }
        }
    }
}

@Composable
private fun VoiceRow(voice: Voice, selected: Boolean, onSelect: () -> Unit) {
    val accent = LocalShellColors.current.accent
    PressRow(onSelect, Modifier.fillMaxWidth().height(52.dp).testTag("cortana_voice:${voice.id}")) {
        Row(Modifier.padding(start = CortanaSettingsValues.SECTION_LEFT_EPX.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp)) {
                BasicText(
                    if (selected) Glyph.CHECKMARK else Glyph.MIC,
                    style = iconStyle(if (selected) accent else Color.White, 20),
                )
            }
            Column {
                BasicText(voice.name, style = ShellType.body.copy(color = Color.White))
                BasicText(voice.description, style = ShellType.caption.copy(color = CortanaSettingsValues.DESCRIPTION_COLOUR))
            }
        }
    }
}

@Composable
private fun CortanaToggleRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    PressRow({ onChange(!checked) }, Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            Modifier.padding(horizontal = CortanaSettingsValues.SECTION_LEFT_EPX.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                label,
                style = ShellType.body.copy(color = Color.White),
                modifier = Modifier.weight(1f).testTag("$tag:label"),
            )
            Spacer(Modifier.width(12.dp))
            BasicText(
                if (checked) "On" else "Off",
                style = ShellType.body.copy(color = LocalShellColors.current.accent),
                modifier = Modifier.testTag("$tag:state"),
            )
        }
    }
}

@Composable
private fun ChecklistRow(row: CortanaRow, onClick: () -> Unit) {
    val (glyph, colour) = when (row.state) {
        RowState.GRANTED -> Glyph.CHECKMARK to Color(0xFF10893E)
        RowState.PARTIAL -> Glyph.WARNING to Color(0xFFFFB900)
        RowState.MISSING -> Glyph.DISMISS to Color(0xFFE81123)
    }
    PressRow(
        onClick,
        Modifier.fillMaxWidth().height(60.dp)
            .testTag("cortana_check:${row.id}:${row.state.name.lowercase()}"),
    ) {
        Row(Modifier.padding(start = CortanaSettingsValues.SECTION_LEFT_EPX.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(44.dp)) { BasicText(glyph, style = iconStyle(colour, 24)) }
            Column {
                BasicText(row.title, style = ShellType.body.copy(color = Color.White))
                BasicText(
                    "${if (row.state == RowState.GRANTED) "On" else if (row.state == RowState.PARTIAL) "Partial" else "Off"} · ${row.detail}",
                    style = ShellType.caption.copy(color = CortanaSettingsValues.DESCRIPTION_COLOUR),
                )
            }
        }
    }
}

/**
 * The Notes app picker. Android has no notes category the way it has APP_MUSIC or APP_GALLERY, so the
 * apps that declare `ACTION_CREATE_NOTE` come first and every launchable app follows — the user's notes
 * app is whatever they say it is, which is the same rule phase 01's slot picker follows for a category
 * with no handler.
 */
@Composable
private fun NotesAppPicker(
    onPicked: (android.content.ComponentName) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val catalog = remember { AppCatalog.get(context) }
    val apps by catalog.apps.collectAsState()
    val noteHandlers = remember(apps) {
        runCatching {
            context.packageManager
                .queryIntentActivities(android.content.Intent("android.intent.action.CREATE_NOTE"), 0)
                .map { it.activityInfo.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }
    val ordered = remember(apps, noteHandlers) {
        apps.sortedWith(compareByDescending<app.tileshell.apps.AppEntry> { it.component.packageName in noteHandlers }
            .thenBy { it.label.lowercase() })
    }
    Column(modifier.fillMaxSize().background(CortanaSettingsValues.BACKGROUND).testTag("cortana_notes_picker")) {
        CortanaPageTitle("Notes app", onCancel, "cortana_notes_picker_title")
        LazyColumn(Modifier.fillMaxWidth()) {
            items(ordered, key = { it.key }) { entry ->
                PressRow(
                    { onPicked(entry.component) },
                    Modifier.fillMaxWidth().height(44.dp).testTag("cortana_notes_candidate:${entry.component.packageName}"),
                ) {
                    Row(Modifier.fillMaxSize().padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        catalog.icon(entry, 120)?.let { Image(it, null, Modifier.size(41.dp)) } ?: Box(Modifier.size(41.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicText(entry.label, style = ShellType.body.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

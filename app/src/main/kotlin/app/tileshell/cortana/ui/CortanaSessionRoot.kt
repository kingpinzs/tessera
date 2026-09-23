package app.tileshell.cortana.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.tileshell.bars.BarMetrics
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.cortana.CortanaDestinationKey
import app.tileshell.cortana.CortanaModel
import app.tileshell.cortana.CortanaRoute
import app.tileshell.cortana.PersonaState
import app.tileshell.cortana.reminders.Place
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.tokens.ShellType
import androidx.compose.ui.platform.LocalContext

/**
 * Everything inside Cortana's window (phase 03 build task 1).
 *
 * The bar rule applies here exactly as it does on Start (Decisions "Session bars"): the session hides
 * Samsung's bars and draws phase 01's W10M status bar and a nav bar with Back / Windows / Search. Every
 * measured value in this phase is read against those drawn bars, so "40 epx from the screen top"
 * includes the drawn status bar, as it did on W10M.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CortanaSessionRoot(
    model: CortanaModel,
    onDrawnBack: () -> Unit,
    onWindowsKey: () -> Unit,
    onSavePlaceHere: (String) -> Unit,
    onLookUpAddress: (String, String) -> Unit,
) {
    ShellRoot {
        val state by model.state.collectAsState()
        val route = state.route
        val destination = route is CortanaRoute.Reminders || route is CortanaRoute.Settings ||
            route is CortanaRoute.History || route is CortanaRoute.ReminderDetail ||
            route is CortanaRoute.ReminderNew || route is CortanaRoute.Places

        Box(
            Modifier
                .fillMaxSize()
                // R6 §3.1.14 (MEDIUM, 15063): the page background is black.
                .background(TextBoxValues.PAGE_BACKGROUND)
                .semantics { testTagsAsResourceId = true }
                .testTag("cortana_session"),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (destination) {
                        // R7 §3.1.13: a ≡ destination page hides the drawn status bar and has no text box.
                        CortanaDestinationHost(route) {
                            DestinationPage(model, route, onSavePlaceHere, onLookUpAddress)
                        }
                    } else {
                        HomeOrResult(model)
                    }
                }
                W10mNavBar(
                    onBack = onDrawnBack,
                    onWindows = onWindowsKey,
                    // R6 §4.2.1 / §4.2.3, inside Cortana: a tap goes back to the home page, a hold listens.
                    onSearch = { model.goTo(CortanaDestinationKey.HOME) },
                    onSearchHold = { model.startListening() },
                )
            }
            if (!destination) W10mStatusBar(Modifier.align(Alignment.TopCenter))

            // R7 §3.1.6: the page beside the open pane is NOT dimmed.
            if (state.paneOpen) {
                CortanaNavPane(
                    open = true,
                    current = state.destination.toPaneDestination(),
                    onSelect = { model.goTo(it.toKey()) },
                    onDismiss = { model.openPane(false) },
                )
            }
        }
    }
}

@Composable
private fun HomeOrResult(model: CortanaModel) {
    val state by model.state.collectAsState()
    val accent = LocalShellColors.current.accent
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = BarMetrics.STATUS_EPX.dp)) {
            when (state.route) {
                is CortanaRoute.Result -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // What was asked, one grey line above the answer (Jeremy, 2026-09-23: "(a)"). The text box clears on
                    // send since J1, so without this nothing shows what Tess heard — which matters most when she mishears.
                    // P4 design: W10M kept the query in the bar instead; judged in a NEEDS-HUMAN row.
                    if (state.query.isNotBlank()) {
                        BasicText(
                            askedLine(state.query),
                            style = ShellType.body.copy(color = TextBoxValues.PLACEHOLDER_COLOUR),
                            maxLines = 2,
                            modifier = Modifier
                                .padding(start = CardValues.TITLE_LEFT_EPX.dp, end = CardValues.TITLE_LEFT_EPX.dp, bottom = 8.dp)
                                .testTag("cortana_asked"),
                        )
                    }
                    state.card?.let { card ->
                        ResponseCardView(
                            card, state.persona, state.level, accent,
                            onAction = { action -> model.onCardAction(action) },
                        )
                    }
                }
                else -> HomePage(model)
            }
            if (state.reloading) {
                // E12: the speech process died; the next request works once it is back.
                BasicText(
                    "Just a moment, I'm reloading.",
                    style = ShellType.body.copy(color = Color.White),
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).testTag("cortana_reloading"),
                )
            }
        }
        // The text box is docked directly on the drawn nav bar and is fixed while the page scrolls
        // (R6 §3.3.1-3.3.2).
        if (state.listening) {
            ListeningQueryBox(state.query, state.level, accent, onSubmit = { model.stopListening() })
        } else {
            CortanaTextBox(
                mode = if (state.awaiting != null) TextBoxMode.AWAITING_ANSWER else TextBoxMode.IDLE,
                accent = accent,
                onSubmit = { model.submitTyped(it) },
                onMic = { model.startListening() },
            )
        }
    }
}

@Composable
private fun HomePage(model: CortanaModel) {
    val state by model.state.collectAsState()
    val accent = LocalShellColors.current.accent
    Box(Modifier.fillMaxSize()) {
        // R6 §3.5.5: over the keyguard the page has no ≡ menu at all.
        if (!state.locked) {
            CortanaMenuButton(Modifier.align(Alignment.TopStart)) { model.openPane(true) }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // R6 §3.1.9: the listening persona's centre sits 243.8 epx from the screen top; R3 A22 puts
            // the idle ring at 244 epx. The drawn status bar is above this column, so the spacer measures
            // from the screen top minus that bar.
            val centre = if (state.persona == PersonaState.LISTENING)
                PersonaValues.LISTEN_CENTRE_Y_EPX else PersonaValues.IDLE_CENTRE_Y_EPX
            Spacer(Modifier.height((centre - BarMetrics.STATUS_EPX - PersonaValues.LISTEN_HALO_MAX_EPX / 2f).dp))
            LargePersona(state.persona, state.level, accent)
            Spacer(Modifier.height(24.dp))
            BasicText(
                state.greeting,
                style = ShellType.subtitle.copy(color = Color.White),
                modifier = Modifier.testTag("cortana_greeting"),
            )
        }
    }
}

/**
 * R7 §3.1.5: a 48 × 48 epx button whose glyph runs x 16–32 epx. The three bars are drawn rather than
 * taken from the icon font, because the font's hamburger does not sit on those edges.
 */
@Composable
fun CortanaMenuButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(48.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .testTag("cortana_menu_button")
            .drawBehind {
                val left = 16.dp.toPx()
                val width = 16.dp.toPx()
                val thickness = 2.dp.toPx()
                val gap = 5.dp.toPx()
                val top = (size.height - (thickness * 3 + gap * 2)) / 2f
                for (i in 0..2) {
                    drawRect(
                        Color.White,
                        topLeft = Offset(left, top + i * (thickness + gap)),
                        size = Size(width, thickness),
                    )
                }
            },
    )
}

@Composable
private fun DestinationPage(
    model: CortanaModel,
    route: CortanaRoute,
    onSavePlaceHere: (String) -> Unit,
    onLookUpAddress: (String, String) -> Unit,
) {
    when (route) {
        is CortanaRoute.Reminders -> RemindersPage(
            onOpenReminder = { model.goToRoute(CortanaRoute.ReminderDetail(it)) },
            onNewReminder = { model.goToRoute(CortanaRoute.ReminderNew) },
            onOpenHistory = { model.goToRoute(CortanaRoute.History) },
            onOpenMenu = { model.openPane(true) },
        )
        is CortanaRoute.ReminderDetail -> ReminderDetailPage(
            reminderId = route.id,
            onDone = { model.goTo(CortanaDestinationKey.REMINDERS) },
            onOpenMenu = { model.openPane(true) },
        )
        is CortanaRoute.ReminderNew -> ReminderDetailPage(
            reminderId = null,
            onDone = { model.goTo(CortanaDestinationKey.REMINDERS) },
            onOpenMenu = { model.openPane(true) },
        )
        is CortanaRoute.History -> HistoryPage(
            onOpenReminder = { model.goToRoute(CortanaRoute.ReminderDetail(it)) },
            onOpenMenu = { model.openPane(true) },
        )
        is CortanaRoute.Settings -> CortanaSettingsPage(
            onOpenPlaces = { model.goToRoute(CortanaRoute.Places()) },
            onOpenMenu = { model.openPane(true) },
        )
        is CortanaRoute.Places -> PlacesPage(
            prefillName = route.prefill,
            onSaveCurrentLocation = onSavePlaceHere,
            onLookUpAddress = onLookUpAddress,
            onBack = { model.goTo(CortanaDestinationKey.SETTINGS) },
        )
        else -> Unit
    }
}

/** A saved place, for the callers that only need the name (the Places page keeps the rest). */
@Composable
fun rememberPlaces(): List<Place> {
    val store = ReminderStore.get(LocalContext.current)
    val places by store.places.collectAsState()
    return places
}

private fun CortanaDestinationKey.toPaneDestination(): CortanaDestination = when (this) {
    CortanaDestinationKey.HOME -> CortanaDestination.HOME
    CortanaDestinationKey.REMINDERS -> CortanaDestination.REMINDERS
    CortanaDestinationKey.SETTINGS -> CortanaDestination.SETTINGS
}

private fun CortanaDestination.toKey(): CortanaDestinationKey = when (this) {
    CortanaDestination.HOME -> CortanaDestinationKey.HOME
    CortanaDestination.REMINDERS -> CortanaDestinationKey.REMINDERS
    CortanaDestination.SETTINGS -> CortanaDestinationKey.SETTINGS
}

/**
 * The asked line's text. The recogniser writes UPPER CASE with no punctuation; shown as heard but in sentence case, so a
 * spoken "WHAT'S ON MY CALENDAR" reads "What's on my calendar". Typed text is shown exactly as typed.
 */
internal fun askedLine(query: String): String {
    val t = query.trim()
    if (t.any { it.isLowerCase() }) return t
    val lower = t.lowercase()
    return lower.replaceFirstChar { it.uppercase() }
}

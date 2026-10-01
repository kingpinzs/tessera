package app.tileshell.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockAppBar
import app.tileshell.ui.LocalShellColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A contact's linked profiles (r11/people.md P5.4, guide wording only; U2's approximation — the card's action-row
 * form): one row for each raw contact behind the contact, with its account, and Unlink on each while there is more
 * than one. "+" opens "Select a contact to link". Linking and unlinking write `AggregationExceptions`, which stay on
 * this phone, so both are offered whatever account the contacts are in.
 */
@Composable
fun LinkPage(env: PeopleEnv, page: PeoplePage.Link) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    var contactId by remember(page) { mutableStateOf(page.contactId) }
    var raws by remember(page) { mutableStateOf<List<RawContact>>(emptyList()) }

    LaunchedEffect(page, env.repo.version) {
        val read = withContext(Dispatchers.IO) {
            // After an unlink the contact may carry another id: its lookup key still finds it.
            val id = PeopleData.resolve(context, ContactRef(page.lookup, page.contactId)) ?: page.contactId
            id to PeopleData.rawContacts(context, id)
        }
        contactId = read.first
        raws = read.second
        if (env.repo.loaded && read.second.isEmpty()) nav.pop(PeopleNotices.CONTACT_GONE)
    }

    LeafPage(
        env, "people_page:link", "Linked profiles",
        bar = {
            ClockAppBar(
                buttons = listOf(
                    BarButton(Glyph.ADD, "link", "people_link_add") {
                        // The list as it is now: what the user sees is what a tap links (see LinkPickerPage).
                        nav.push(PeoplePage.LinkPicker(contactId, page.lookup, env.repo.all.filter { it.id != contactId }))
                    },
                ),
                menu = emptyList(),
                isExpanded = { nav.barExpanded },
                onExpand = { nav.barExpanded = it },
                tagPrefix = "people",
            )
        },
    ) {
        PageLine(
            if (raws.size > 1) "These are shown as one contact. Unlink one to show it on its own again." else "This contact isn't linked to another. Tap + to link one.",
            "people_link_line",
        )
        raws.forEach { raw ->
            Box(Modifier.fillMaxWidth().height(PeopleMetrics.ACTION_TWO_LINES).testTag("people_link_row:${raw.id}")) {
                ActionText(raw.name?.takeIf { it.isNotBlank() } ?: PeopleBuckets.NO_NAME, CardRules.accountName(raw.account), colors.text)
                if (raws.size > 1) {
                    Box(
                        Modifier.align(Alignment.CenterEnd).width(56.dp).fillMaxHeight().testTag("people_link_unlink:${raw.id}")
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { PeopleWriter.unlink(context, contactId, raw.id) }
                                    nav.notice = PeopleNotices.of(result, "unlink that contact")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { PeopleGlyph(Glyph.LINK_DISMISS, 20f, colors.text) }
                }
            }
        }
    }
}

/**
 * "Select a contact to link" (r11/people.md §9's string; the list's row form, U2). The page lists the contacts as they
 * were when it opened and does not re-read them: a tap links the contact the user saw, and when that contact has gone
 * in the meantime the link fails with a notice (`[people] link <a>+<b>: failed`) instead of landing on another row.
 */
@Composable
fun LinkPickerPage(env: PeopleEnv, page: PeoplePage.LinkPicker) {
    val context = LocalContext.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    LeafPage(env, "people_page:link_picker", "Select a contact to link") {
        if (page.snapshot.isEmpty()) PageLine("There is no other contact to link.", "people_link_empty")
        page.snapshot.forEach { row ->
            ContactRowView(row, "people_row:${row.lookup}", {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { PeopleWriter.link(context, page.contactId, row.id) }
                    if (result is WriteResult.Ok) nav.pop() else nav.notice = PeopleNotices.of(result, "link those contacts")
                }
            })
        }
    }
}

package app.tileshell.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockAppBar
import app.tileshell.clock.ClockCheckbox
import app.tileshell.clock.ClockMetrics
import app.tileshell.settings.SectionHeader
import app.tileshell.settings.ToggleRow
import app.tileshell.settings.TwoLineItem
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A leaf page of People: its caps header in the pivot's place, and its content under it. */
@Composable
fun LeafPage(
    env: PeopleEnv,
    tag: String,
    title: String,
    bar: (@Composable BoxScope.() -> Unit)? = null,
    notice: PeopleNotice? = env.nav.notice,
    content: @Composable () -> Unit,
) {
    PeopleScaffold(tag, env.host, notice, env.onNoticeAction, bar = bar) {
        CapsHeader(title, PeopleMetrics.PIVOT_CAP_TOP, 12.5f, "people_page_title")
        Column(
            Modifier.fillMaxSize().padding(top = PeopleMetrics.SEARCH_TOP).verticalScroll(rememberScrollState())
                .padding(bottom = ClockMetrics.APP_BAR + 72.dp),
        ) { content() }
    }
}

/**
 * A checkbox row in "Can sync to"'s form (r11/calendar.md K6.4's pane rows: a 48.1-epx pitch, the box before the name
 * at x 62). The box carries [tag] and its checked state; a tap anywhere on the row toggles it.
 */
@Composable
fun CheckRow(title: String, caption: String?, checked: Boolean, tag: String, enabled: Boolean = true, titleTag: String? = null, onChange: (Boolean) -> Unit) {
    val colors = LocalShellColors.current
    Box(
        Modifier.fillMaxWidth().height(CHECK_ROW)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled) { onChange(!checked) },
    ) {
        ClockCheckbox(checked, tag, Modifier.align(Alignment.CenterStart).offset(x = 22.dp))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 62.dp, end = PeopleMetrics.SIDE)) {
            BasicText(
                title, if (titleTag != null) Modifier.testTag(titleTag) else Modifier,
                style = ShellType.body.copy(color = if (enabled) colors.text else colors.subtleText), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) BasicText(caption, style = ShellType.caption.copy(color = colors.subtleText), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val CHECK_ROW = 48.1.dp

/** A page's explaining line. */
@Composable
fun PageLine(text: String, tag: String) {
    BasicText(
        text,
        Modifier.padding(start = PeopleMetrics.SIDE, end = PeopleMetrics.SIDE, top = 8.dp, bottom = 12.dp).testTag(tag),
        style = ShellType.body.copy(color = LocalShellColors.current.subtleText),
    )
}

/** People's settings (r11/people.md P5.1, P5.5: "… > Settings"): Can edit, the list's filter, the SIM import. */
@Composable
fun SettingsPage(env: PeopleEnv) {
    val nav = env.nav
    LeafPage(env, "people_page:settings", "Settings") {
        TwoLineItem(Glyph.EDIT, "Can edit", canEditSummary(env), "people_settings:can_edit") { nav.push(PeoplePage.CanEdit) }
        TwoLineItem(Glyph.FILTER, "Filter contact list", "Choose which contacts are shown", "people_settings:filter") { nav.push(PeoplePage.Filter) }
        TwoLineItem(Glyph.IMPORT, "Import from SIM", "Copy the SIM's contacts to this phone", "people_settings:sim") { nav.push(PeoplePage.Sim) }
    }
}

private fun canEditSummary(env: PeopleEnv): String = when (val n = env.policy.allowed.size) {
    0 -> "Only contacts saved on this phone"
    1 -> "This phone and 1 account"
    else -> "This phone and $n accounts"
}

/**
 * "Can edit" (Q-16-3; H21): the accounts the Contacts provider names, one checkbox each, every one unticked until the
 * user ticks it. The phone has no row — its contacts are always editable. An account ticked here is the only way a
 * write of People's reaches that account.
 */
@Composable
fun CanEditPage(env: PeopleEnv) {
    val context = LocalContext.current
    val rows = CanEditRules.rows(env.repo.accounts, env.policy)
    LeafPage(env, "people_page:can_edit", "Can edit") {
        PageLine("People can always change contacts saved on this phone. Tick an account to let People change its contacts too.", "people_can_edit_line")
        if (rows.isEmpty()) {
            PageLine(if (env.repo.canRead) "No account on this phone holds contacts." else "People can't read your contacts.", "people_can_edit_empty")
        }
        rows.forEach { account ->
            CheckRow(account.name.orEmpty(), account.type, account in env.policy.allowed, "people_can_edit:${account.id}") { on ->
                PeopleEditStore.get(context).set(account, on)
            }
        }
    }
}

/**
 * "Filter contact list" (r11/people.md P5.1, LOW; U4's approximation): W10M's "Hide contacts without phone numbers",
 * then "Show contacts from" with a checkbox for the phone and one per account, then one per group. It is People's own
 * state and takes effect as it is ticked; nothing here writes to the provider.
 */
@Composable
fun FilterPage(env: PeopleEnv) {
    val context = LocalContext.current
    val store = PeopleFilterStore.get(context)
    val filter = env.filter
    val accounts = listOf(env.repo.local) + CanEditRules.rows(env.repo.accounts, env.policy)
    LeafPage(env, "people_page:filter", "Filter contacts") {
        ToggleRow("Hide contacts without phone numbers", filter.hideWithoutPhones, "people_filter_phones") {
            store.set(filter.copy(hideWithoutPhones = it))
        }
        PageLine("Shows only the contacts you can call or text. You can still find others by searching.", "people_filter_line")
        SectionHeader("Show contacts from")
        accounts.forEach { account ->
            val id = if (env.policy.isPhone(account)) "phone" else account.id
            CheckRow(CardRules.accountName(account), account.type, account !in filter.hiddenAccounts, "people_filter_account:$id") { on ->
                store.set(filter.copy(hiddenAccounts = if (on) filter.hiddenAccounts - account else filter.hiddenAccounts + account))
            }
        }
        if (env.repo.groups.isNotEmpty()) {
            SectionHeader("Show groups")
            env.repo.groups.forEach { group ->
                CheckRow(group.title, CardRules.accountName(group.account), group.id !in filter.hiddenGroups, "people_filter_group:${group.id}") { on ->
                    store.set(filter.copy(hiddenGroups = if (on) filter.hiddenGroups - group.id else filter.hiddenGroups + group.id))
                }
            }
        }
    }
}

/**
 * "Import from SIM" (r11/people.md P5.5, guide wording only; U3's approximation): the SIM's entries, each ticked, with
 * "import" and "clear" on the app bar. What is imported goes to the phone's own contacts, never to an account; an
 * entry with no number is skipped. With nothing on the SIM the page says so and an import reads "0 of 0".
 */
@Composable
fun SimPage(env: PeopleEnv) {
    val context = LocalContext.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<SimContact>?>(null) }
    var unticked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entries = withContext(Dispatchers.IO) { PeopleData.sim(context) } }
    val listed = entries.orEmpty()

    LeafPage(
        env, "people_page:sim", "Import from SIM",
        bar = {
            ClockAppBar(
                buttons = listOf(
                    BarButton(Glyph.IMPORT, "import", "people_sim_import", enabled = entries != null && !busy) {
                        busy = true
                        scope.launch {
                            val chosen = listed.filter { it.index !in unticked }
                            val result = withContext(Dispatchers.IO) { PeopleWriter.importSim(context, chosen) }
                            busy = false
                            nav.notice = PeopleNotices.of(result, "import from the SIM")
                                ?: PeopleNotice(if (chosen.isEmpty()) "There was nothing to import." else "Imported what the SIM had with a number.")
                        }
                    },
                    BarButton(Glyph.DISMISS, "clear", "people_sim_clear", enabled = listed.isNotEmpty()) { unticked = listed.map { it.index }.toSet() },
                ),
                menu = emptyList(),
                isExpanded = { nav.barExpanded },
                onExpand = { nav.barExpanded = it },
                tagPrefix = "people",
            )
        },
    ) {
        PageLine("Contacts on the SIM are copied to this phone's own contacts.", "people_sim_line")
        if (entries != null && listed.isEmpty()) PageLine("The SIM has no contacts to import.", "people_sim_empty")
        listed.forEach { entry ->
            // The entry's name carries `people_sim_row:<n>`; its box `people_sim_check:<n>` and whether it is ticked.
            CheckRow(
                entry.name.ifBlank { entry.number ?: PeopleBuckets.NO_NAME },
                entry.number?.takeIf { it.isNotBlank() } ?: "no number — not imported",
                entry.index !in unticked && SimRules.importable(entry), "people_sim_check:${entry.index}", enabled = SimRules.importable(entry),
                titleTag = "people_sim_row:${entry.index}",
            ) { on -> unticked = if (on) unticked - entry.index else unticked + entry.index }
        }
    }
}

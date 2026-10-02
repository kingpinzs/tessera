package app.tileshell.people

import android.content.ContentUris
import android.provider.ContactsContract
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.calculator.rememberInkLayout
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockAppBar
import app.tileshell.clock.ClockMenuEntry
import app.tileshell.clock.ClockMetrics
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * People's front page (r11/people.md §2, §3): the CONTACTS / GROUPS pivot header, and under it the pivot on show — the
 * search box over the A–Z list with People's own jump grid, or the groups. The same list serves a PICK and an
 * INSERT_OR_EDIT, with no pivot and no app bar: there a tap chooses.
 */
@Composable
fun ContactsPage(env: PeopleEnv, mode: ListMode) {
    val context = LocalContext.current
    val nav = env.nav
    val repo = env.repo
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<PeopleData.SearchResult?>(null) }

    // Search asks the provider (the contact filter, the phone lookup, the enterprise filter) once typing pauses.
    LaunchedEffect(query, repo.version) {
        val q = query.trim()
        if (q.isEmpty() || !repo.canRead) { results = null; return@LaunchedEffect }
        delay(SEARCH_PAUSE_MS)
        results = withContext(Dispatchers.IO) { PeopleData.search(context, q) }
    }

    val browse = mode == ListMode.Browse
    // A chooser lists every contact, whatever "filter contact list" hides; a phone PICK only those with a number.
    val listed = when (mode) {
        ListMode.Browse -> repo.rows
        is ListMode.Pick -> if (mode.kind == PickKind.PHONE) repo.all.filter { it.hasPhone } else repo.all
        is ListMode.InsertOrEdit -> repo.all
    }
    val sections = remember(listed) { PeopleBuckets.sections(listed) }

    fun choose(row: ContactRow) {
        when (mode) {
            ListMode.Browse -> nav.push(PeoplePage.Card(row.id, row.lookup, row.takeIf { it.enterprise }))
            is ListMode.Pick -> when (mode.kind) {
                // The one contact tapped, as its lookup URI: the caller is granted a read of that URI and nothing else.
                PickKind.CONTACT -> env.host.finishPick(PeopleWriter.lookupUri(row.id, row.lookup))
                PickKind.PHONE -> scope.launch {
                    val phones = withContext(Dispatchers.IO) { PeopleData.phones(context, row.id) }
                    when (phones.size) {
                        0 -> nav.notice = PeopleNotice("${row.name} has no phone number.")
                        1 -> env.host.finishPick(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, phones[0].dataId))
                        else -> nav.push(PeoplePage.PickNumber(row.id, row.name))
                    }
                }
            }
            is ListMode.InsertOrEdit -> scope.launch {
                val card = withContext(Dispatchers.IO) { PeopleData.card(context, row.id) }
                if (card == null) { nav.notice = PeopleNotices.CONTACT_GONE; return@launch }
                val actions = PeopleWriteGuard.cardActions(card.raws.map { it.ref() }, env.policy)
                if (actions.edit) {
                    nav.push(PeoplePage.Editor(EditorDraft.edit(card, env.policy, mode.prefill)))
                } else {
                    Diagnostics.add("people", "edit ${card.lookup}: refused (account not allowed)")
                    nav.notice = PeopleNotice(CardRules.readOnlyLine(actions.readOnlyAccount, actions.otherProfile), NoticeAction.CAN_EDIT)
                }
            }
        }
    }

    // A search that finds nothing says so in the results' own place (SearchResults), not here: this band sits above
    // the app bar, behind the keyboard that is up while a query is typed (QA defect D-E16-1).
    val notice = if (repo.loaded && !repo.canRead) PeopleNotices.CANNOT_READ else nav.notice

    PeopleScaffold(
        tag = "people_page:list",
        host = env.host,
        notice = notice,
        onNoticeAction = env.onNoticeAction,
        bar = if (!browse) null else {
            {
                // P1.12: "+" and "…" (W10M's multi-select is not part of this phase's People).
                val add = if (nav.pivot == PeoplePivot.CONTACTS) {
                    BarButton(Glyph.ADD, "new", "people_bar:add") { nav.push(PeoplePage.Editor(EditorDraft.new(repo.local, NewContactSource.EDITOR, ContactPrefill()))) }
                } else {
                    BarButton(Glyph.ADD, "new group", "people_group_new") { nav.push(PeoplePage.GroupEditor(null, "")) }
                }
                ClockAppBar(
                    buttons = listOf(add),
                    menu = listOf(ClockMenuEntry("settings", "people_more:settings") { nav.push(PeoplePage.Settings) }),
                    isExpanded = { nav.barExpanded },
                    onExpand = { nav.barExpanded = it },
                    tagPrefix = "people",
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (browse) {
                PivotHeader(nav)
                PivotPager(nav, Modifier.fillMaxSize().padding(top = PeopleMetrics.SEARCH_TOP)) { pivot ->
                    when (pivot) {
                        PeoplePivot.CONTACTS -> ContactsPivot(env, mode, query, { query = it }, results, sections, ::choose)
                        PeoplePivot.GROUPS -> GroupsPivot(env)
                    }
                }
            } else {
                CapsHeader(
                    when (mode) {
                        is ListMode.Pick -> if (mode.kind == PickKind.PHONE) "Choose a phone number" else "Choose a contact"
                        else -> "Add to a contact"
                    },
                    PeopleMetrics.PIVOT_CAP_TOP, PIVOT_FIRST_INK, "people_pick_header",
                )
                Box(Modifier.fillMaxSize().padding(top = PeopleMetrics.SEARCH_TOP)) {
                    ContactsPivot(env, mode, query, { query = it }, results, sections, ::choose)
                }
            }
        }
    }
}

private const val SEARCH_PAUSE_MS = 200L

/**
 * P1.1 / P1.2: "CONTACTS  GROUPS" in the 15-epx semibold caps class (cap 11.0), cap top 19.5 below the status bar, the
 * first word at x 12 and ≈ 24.8 epx between words; the pivot on show white, the other (156,156,156); no underline.
 */
@Composable
private fun PivotHeader(nav: PeopleNav) {
    val colors = LocalShellColors.current
    val density = LocalDensity.current.density
    val style = ShellType.base.copy(fontSize = PeopleMetrics.PIVOT_SP.sp)
    Box(Modifier.fillMaxWidth().height(PeopleMetrics.SEARCH_TOP).testTag("people_pivots")) {
        // Each word's ink follows the one before it by the measured gap; the first starts at x 12.5.
        var inkLeft = PIVOT_FIRST_INK
        PeoplePivot.entries.forEach { pivot ->
            val on = pivot == nav.pivot
            val ink = rememberInkLayout(style, pivot.title).ink
            val inkWidth = (ink.right - ink.left) / density
            // The word's touch target is a box around it; the word itself carries the tag, so a dump gives the text's
            // own box (a tappable text node reports Android's 48-epx touch bounds instead).
            Box(
                Modifier.offset(x = (inkLeft - PeopleMetrics.PIVOT_GAP.value / 2f).dp).width((inkWidth + PeopleMetrics.PIVOT_GAP.value).dp).fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!on) nav.pivotRequest = pivot },
            )
            InkLine(
                pivot.title, style.copy(color = if (on) colors.text else PeopleMetrics.PIVOT_DIM), inkLeft, PeopleMetrics.PIVOT_CAP_TOP,
                Modifier.testTag("people_pivot:${pivot.id}").semantics { role = Role.Tab; selected = on },
            )
            inkLeft += inkWidth + PeopleMetrics.PIVOT_GAP.value
        }
    }
}

/** P1.1: "CONTACTS" begins at x 12.5. */
private const val PIVOT_FIRST_INK = 12.5f

/**
 * The pivot under the header. A swipe drags the page on show with the other following it in; the release — and a tap
 * on the other header — settles on X13's 250 ms through the shell's motion clock as `[motion] people_pivot`
 * (approximation: R11 measured no People motion, U6; H17).
 */
@Composable
private fun PivotPager(nav: PeopleNav, modifier: Modifier, content: @Composable (PeoplePivot) -> Unit) {
    BoxWithConstraints(modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var drag by remember { mutableFloatStateOf(0f) }
        var animating by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val current = nav.pivot
        val prev = PeoplePivot.entries.getOrNull(current.ordinal - 1)
        val next = PeoplePivot.entries.getOrNull(current.ordinal + 1)

        fun settle(forced: PeoplePivot? = null) {
            if (animating) return
            val target = forced ?: when {
                drag < -widthPx / 4f && next != null -> next
                drag > widthPx / 4f && prev != null -> prev
                else -> null
            }
            val end = when (target) { null -> 0f; next -> -widthPx; else -> widthPx }
            val start = drag
            if (start == end) {
                target?.let { nav.pivot = it }
                drag = 0f
                return
            }
            animating = true
            scope.launch {
                MotionClock.animate("people_pivot", Motion.PIVOT_SETTLE_MS, FastOutSlowInEasing) { f -> drag = start + (end - start) * f }
                target?.let { nav.pivot = it; Diagnostics.add("people", "pivot ${it.id}") }
                drag = 0f
                animating = false
            }
        }
        // A tap on the other header slides to it as a swipe's release does.
        LaunchedEffect(nav.pivotRequest) {
            val wanted = nav.pivotRequest ?: return@LaunchedEffect
            nav.pivotRequest = null
            if (wanted != nav.pivot) settle(wanted)
        }

        Box(
            Modifier.fillMaxSize().pointerInput(current, widthPx) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { change, dx ->
                    if (animating) return@detectHorizontalDragGestures
                    drag = (drag + dx).coerceIn(if (next != null) -widthPx else 0f, if (prev != null) widthPx else 0f)
                    change.consume()
                }
            },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag }) { content(current) }
            if (drag > 0f && prev != null) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag - widthPx }) { content(prev) }
            if (drag < 0f && next != null) Box(Modifier.fillMaxSize().graphicsLayer { translationX = drag + widthPx }) { content(next) }
        }
    }
}

/** The CONTACTS pivot: the search box, the filter caption, and the list — or the search's results, or the jump grid over it. */
@Composable
private fun ContactsPivot(
    env: PeopleEnv,
    mode: ListMode,
    query: String,
    onQuery: (String) -> Unit,
    results: PeopleData.SearchResult?,
    sections: List<ContactSection>,
    onChoose: (ContactRow) -> Unit,
) {
    val colors = LocalShellColors.current
    val nav = env.nav
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searching = query.isNotBlank()
    val caption = if (mode == ListMode.Browse && !searching) FilterRules.caption(env.filter) else null
    val listTop = PeopleMetrics.SEARCH_H + if (caption != null) PeopleMetrics.CAPTION_H else 0.dp

    // Where each letter's header sits in the lazy list, for the jump grid.
    val headerIndex = remember(sections, mode) {
        val map = HashMap<String, Int>()
        var i = if (mode is ListMode.InsertOrEdit) 1 else 0
        sections.forEach { s -> map[s.label] = i; i += 1 + s.rows.size }
        map
    }

    Box(Modifier.fillMaxSize().testTag("people_contacts")) {
        SearchBox(query, onQuery)
        if (caption != null) {
            // P1.4: "Showing <the filter>" under the box, the phrase in accent.
            BasicText(
                buildAnnotatedString {
                    append("Showing ")
                    withStyle(SpanStyle(color = colors.accent)) { append(caption) }
                },
                Modifier.offset(x = PeopleMetrics.SIDE, y = PeopleMetrics.CAPTION_TOP - PeopleMetrics.SEARCH_TOP).testTag("people_filter_caption"),
                style = ShellType.caption.copy(color = colors.text),
                maxLines = 1,
            )
        }
        Box(Modifier.fillMaxSize().padding(top = listTop)) {
            when {
                searching -> SearchResults(mode, query, results, onChoose)
                sections.isEmpty() && mode !is ListMode.InsertOrEdit -> {
                    if (env.repo.loaded && env.repo.canRead) {
                        BasicText(
                            if (mode == ListMode.Browse && env.filter.isOn) "No contacts are shown. Change the filter in settings."
                            else if (mode == ListMode.Browse) "No contacts yet. Tap + to add one."
                            else "No contacts to choose from.",
                            Modifier.padding(start = PeopleMetrics.SIDE, top = PeopleMetrics.LIST_PAD + 12.dp, end = PeopleMetrics.SIDE).testTag("people_empty"),
                            style = ShellType.body.copy(color = colors.subtleText),
                        )
                    }
                }
                else -> AzList(mode, sections, list, onChoose, onLetter = { dismissOverlay { nav.gridOpen = true } }, onNew = {
                    nav.push(PeoplePage.Editor(EditorDraft.new(env.repo.local, NewContactSource.INSERT_PREFILL, (mode as ListMode.InsertOrEdit).prefill)))
                })
            }
            // P2.1: the grid replaces the list — a full page on the page's own fill, the header and the search box still above it.
            OverlayLayer(active = { nav.gridOpen }) {
                if (nav.gridOpen && !searching) {
                    JumpGrid(
                        PeopleBuckets.cells(sections),
                        onPick = { cell ->
                            dismissOverlay { nav.gridOpen = false }
                            val index = cell.targetLabel?.let { headerIndex[it] }
                            if (index != null) {
                                scope.launch { list.scrollToItem(index) }
                                Diagnostics.add("people", "jump to ${cell.id} (item $index)")
                            }
                        },
                        onDismiss = { dismissOverlay { nav.gridOpen = false } },
                    )
                }
            }
        }
    }
}

/** The A–Z list: accent letter headers (P1.5, P1.6) over 50-epx rows (P1.7 – P1.10). A tap on a letter opens the jump grid. */
@Composable
private fun AzList(mode: ListMode, sections: List<ContactSection>, state: LazyListState, onChoose: (ContactRow) -> Unit, onLetter: () -> Unit, onNew: () -> Unit) {
    val colors = LocalShellColors.current
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("people_list"),
        contentPadding = PaddingValues(top = PeopleMetrics.LIST_PAD, bottom = ClockMetrics.APP_BAR + 24.dp),
    ) {
        if (mode is ListMode.InsertOrEdit) {
            item(key = "new") {
                // INSERT_OR_EDIT's first choice: a new contact with the fields filled in, on the phone, unsaved.
                PressRow(onNew, Modifier.fillMaxWidth().height(PeopleMetrics.ROW).testTag("people_insert_new")) {
                    Box(Modifier.offset(x = PeopleMetrics.SIDE, y = PeopleMetrics.AVATAR_TOP).size(PeopleMetrics.AVATAR), contentAlignment = Alignment.Center) {
                        PeopleGlyph(Glyph.ADD, 20f, colors.accent)
                    }
                    InkLine(
                        "new contact", ShellType.body.copy(fontSize = PeopleMetrics.NAME_SP.sp, lineHeight = 24.sp, color = colors.accent),
                        PeopleMetrics.NAME_X.value, PeopleMetrics.AVATAR_TOP.value + PeopleMetrics.AVATAR.value / 2f + 1.1f - CapMetrics.capHeight(PeopleMetrics.NAME_SP) / 2f,
                    )
                }
            }
        }
        sections.forEach { section ->
            item(key = "letter:${section.label}", contentType = "letter") {
                Box(
                    Modifier.fillMaxWidth().height(PeopleMetrics.LETTER_H)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onLetter),
                ) {
                    InkLine(
                        section.label,
                        ShellType.body.copy(fontSize = PeopleMetrics.LETTER_SP.sp, lineHeight = 40.sp, color = colors.accent),
                        PeopleMetrics.LETTER_X.value, PeopleMetrics.LETTER_CAP_TOP, Modifier.testTag("people_letter:${section.label}"),
                    )
                }
            }
            items(section.rows.size, key = { "c:${section.rows[it].id}" }, contentType = { "row" }) { i ->
                val row = section.rows[i]
                ContactRowView(row, "people_row:${row.lookup}", { onChoose(row) })
            }
        }
    }
}

/** What a search found: this profile's matches, then the work profile's with the briefcase (H10). */
@Composable
private fun SearchResults(mode: ListMode, query: String, results: PeopleData.SearchResult?, onChoose: (ContactRow) -> Unit) {
    val found = results ?: return
    // A phone PICK chooses only among contacts with a number; another profile's contact cannot be picked or edited.
    val local = if (mode is ListMode.Pick && mode.kind == PickKind.PHONE) found.local.filter { it.hasPhone } else found.local
    val enterprise = if (mode == ListMode.Browse) found.enterprise else emptyList()
    if (local.isEmpty() && enterprise.isEmpty()) {
        // Nothing to list: the line stands where the first result would, above the keyboard (the list's own empty line's place).
        BasicText(
            "No contacts match \"${query.trim()}\".",
            Modifier.padding(start = PeopleMetrics.SIDE, top = PeopleMetrics.LIST_PAD + 12.dp, end = PeopleMetrics.SIDE).testTag("people_empty"),
            style = ShellType.body.copy(color = LocalShellColors.current.subtleText),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("people_results"),
        contentPadding = WindowInsets.ime.asPaddingValues(),
    ) {
        item { Box(Modifier.height(PeopleMetrics.LIST_PAD)) }
        items(local.size, key = { "c:${local[it].id}" }) { i ->
            val row = local[i]
            ContactRowView(row, "people_row:${row.lookup}", { onChoose(row) })
        }
        items(enterprise.size, key = { "e:${enterprise[it].id}" }) { i ->
            val row = enterprise[i]
            ContactRowView(row, "people_row:enterprise:${row.lookup}", { onChoose(row) }) { BriefcaseMark("people_row_briefcase:${row.lookup}") }
        }
        item { Box(Modifier.height(ClockMetrics.APP_BAR + 24.dp)) }
    }
}

/**
 * People's own jump grid (r11/people.md §3; T16-13 — not phase 01's app-list grid): 72-epx cells, as many columns as
 * the width takes with a margin (4 at 360 epx, 5 at 411), "#" first, A–Z, a globe last; letters where contacts file in
 * accent, the others (52,52,52). It opens as the 14393 app list's grid does — fading in while it settles from 1.08x to
 * 1.00x (P2.7; U8's approximation for People) — and closes on a pick or a tap off the cells.
 */
@Composable
private fun JumpGrid(cells: List<JumpCell>, onPick: (JumpCell) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    var open by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { MotionClock.animate("people_jump_grid", PeopleMetrics.GRID_OPEN_MS, ClockMetrics.easeOut) { open = it } }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.background).testTag("people_jump_grid")
            // Modal inside the pivot: a drag on the grid never pages the pivot; a tap off the cells closes it (L13-2's rule).
            .modalOverlay(onTapOff = onDismiss),
    ) {
        val cell = PeopleMetrics.GRID_CELL
        val columns = ((maxWidth.value - 24f) / cell.value).toInt().coerceAtLeast(1)
        val left = ((maxWidth.value - columns * cell.value) / 2f - PeopleMetrics.GRID_LEFT_OF_CENTRE).dp
        // The first row's letter has its cap top 119.5 epx below the status bar; a letter sits centred in its cell.
        val cap = CapMetrics.capHeight(PeopleMetrics.GRID_SP)
        val top = (PeopleMetrics.GRID_FIRST_CAP_TOP - PeopleMetrics.LIST_TOP.value + cap / 2f - cell.value / 2f).dp
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).graphicsLayer {
                alpha = open
                val scale = PeopleMetrics.GRID_OPEN_SCALE - (PeopleMetrics.GRID_OPEN_SCALE - 1f) * open
                scaleX = scale
                scaleY = scale
            },
        ) {
            val rows = (cells.size + columns - 1) / columns
            Box(Modifier.fillMaxWidth().height(top + cell * rows + ClockMetrics.APP_BAR + 24.dp)) {
                cells.forEachIndexed { i, c ->
                    JumpCellView(c, Modifier.offset(x = left + cell * (i % columns), y = top + cell * (i / columns)).size(cell), onPick)
                }
            }
        }
    }
}

@Composable
private fun JumpCellView(cell: JumpCell, modifier: Modifier, onPick: (JumpCell) -> Unit) {
    val colors = LocalShellColors.current
    val live = cell.targetLabel != null
    val color = if (live) colors.accent else PeopleMetrics.GRID_DIM
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.testTag("people_jump_cell:${cell.id}")
            .semantics { selected = live }
            .let { m -> if (live) m.overlayItem(onPressedChange = { pressed = it }, onRun = { onPick(cell) }) else m }
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent),
    ) {
        if (cell.isGlobe) {
            PeopleGlyph(Glyph.GLOBE, 20f, color, Modifier.align(Alignment.Center))
        } else {
            // The letter's CAP is centred on the cell, not its text box (whose centre sits under the cap's).
            val capTop = PeopleMetrics.GRID_CELL.value / 2f - CapMetrics.capHeight(PeopleMetrics.GRID_SP) / 2f
            BasicText(
                cell.text,
                Modifier.align(Alignment.TopCenter).offset(y = CapMetrics.topPaddingForCapTop(capTop, PeopleMetrics.GRID_SP).dp),
                style = ShellType.body.copy(fontSize = PeopleMetrics.GRID_SP.sp, color = color, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}

/** A phone PICK on a contact with several numbers: which one. The result is that one number's data URI. */
@Composable
fun PickNumberPage(env: PeopleEnv, page: PeoplePage.PickNumber) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    var phones by remember { mutableStateOf<List<ContactField>>(emptyList()) }
    LaunchedEffect(page, env.repo.version) { phones = withContext(Dispatchers.IO) { PeopleData.phones(context, page.contactId) } }
    PeopleScaffold("people_page:pick_number", env.host, env.nav.notice, env.onNoticeAction) {
        CapsHeader(page.name, PeopleMetrics.PIVOT_CAP_TOP, PIVOT_FIRST_INK, "people_pick_header")
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(top = PeopleMetrics.SEARCH_TOP).verticalScroll(rememberScrollState())) {
            phones.forEach { phone ->
                PressRow(
                    { env.host.finishPick(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, phone.dataId)) },
                    Modifier.fillMaxWidth().height(PeopleMetrics.ACTION_TWO_LINES).testTag("people_pick_number:${phone.dataId}"),
                ) {
                    ActionText(FieldTypes.word(FieldKind.PHONE, phone.type, phone.customLabel), phone.value, colors.text)
                }
            }
        }
    }
}

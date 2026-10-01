package app.tileshell.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockAppBar
import app.tileshell.clock.ClockCheckbox
import app.tileshell.clock.ClockMetrics
import app.tileshell.recorder.RecorderDialog
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.components.OutlinedFieldMetrics
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The GROUPS pivot (T16-14; r11/people.md P5.2, LOW — one camera photo: a round group avatar, the name and "<n>
 * members", under account headers). A group in an account People may not write is listed and opens read-only.
 */
@Composable
fun GroupsPivot(env: PeopleEnv) {
    val colors = LocalShellColors.current
    val groups = env.repo.groups
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = ClockMetrics.APP_BAR + 24.dp).testTag("people_groups")) {
        if (groups.isEmpty() && env.repo.loaded && env.repo.canRead) {
            BasicText(
                "No groups yet. Tap + to make one.",
                Modifier.padding(start = PeopleMetrics.SIDE, top = 12.dp, end = PeopleMetrics.SIDE).testTag("people_groups_empty"),
                style = ShellType.body.copy(color = colors.subtleText),
            )
        }
        groups.groupBy { it.account }.forEach { (account, list) ->
            BasicText(
                CardRules.accountName(account),
                Modifier.padding(start = PeopleMetrics.SIDE, top = 12.dp, bottom = 4.dp).testTag("people_group_account:${if (env.policy.isPhone(account)) "phone" else account.id}"),
                style = ShellType.caption.copy(color = colors.subtleText),
                maxLines = 1,
            )
            list.forEach { group ->
                PressRow({ env.nav.push(PeoplePage.Group(group.id)) }, Modifier.fillMaxWidth().height(GROUP_ROW).testTag("people_group:${group.id}")) {
                    Box(
                        Modifier.align(Alignment.CenterStart).offset(x = PeopleMetrics.SIDE).size(PeopleMetrics.AVATAR).clip(CircleShape).background(PeopleMetrics.AVATAR_DISC),
                        contentAlignment = Alignment.Center,
                    ) { PeopleGlyph(Glyph.PEOPLE_TEAM, 18f, Color.White) }
                    Column(Modifier.align(Alignment.CenterStart).padding(start = PeopleMetrics.NAME_X, end = PeopleMetrics.SIDE)) {
                        BasicText(
                            group.title, Modifier.testTag("people_group_title:${group.id}"),
                            style = ShellType.body.copy(fontSize = PeopleMetrics.NAME_SP.sp, lineHeight = 24.sp, color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        BasicText(GroupRules.membersLine(group.members), Modifier.testTag("people_group_count:${group.id}"), style = ShellType.caption.copy(color = colors.subtleText), maxLines = 1)
                    }
                }
            }
        }
    }
}

private val GROUP_ROW = 56.dp

/**
 * A group's page (T16-14; H16): its members in the list's row form, and on the app bar "Text the group", add members,
 * rename and delete — the last three only where the group's account is one People may write. A group in any other
 * account says why, as the read-only card does.
 */
@Composable
fun GroupPage(env: PeopleEnv, groupId: Long) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    var members by remember(groupId) { mutableStateOf<List<ContactRow>>(emptyList()) }
    val group = env.repo.groups.firstOrNull { it.id == groupId }

    // The page follows the provider; a group another app deleted closes it with a notice.
    LaunchedEffect(groupId, env.repo.version) {
        if (!env.repo.loaded) return@LaunchedEffect
        if (env.repo.groups.none { it.id == groupId }) {
            nav.pop(PeopleNotices.GROUP_GONE)
            return@LaunchedEffect
        }
        members = withContext(Dispatchers.IO) { PeopleData.groupMembers(context, groupId) }
    }
    val writable = group != null && env.policy.canWrite(group.account)

    fun textGroup() {
        scope.launch {
            val mobiles = withContext(Dispatchers.IO) { PeopleData.firstMobiles(context, members.map { it.id }) }
            nav.notice = if (GroupRules.smsTo(mobiles) == null) PeopleNotice(GroupRules.textNotice(members.size)) else PeopleActions.text(context, mobiles.filterNotNull())
        }
    }

    PeopleScaffold(
        tag = "people_page:group",
        host = env.host,
        notice = nav.notice,
        onNoticeAction = env.onNoticeAction,
        bar = if (group == null) null else {
            {
                val buttons = buildList {
                    add(BarButton(Glyph.CHAT, "text", "people_group_action:text") { textGroup() })
                    if (writable) {
                        add(BarButton(Glyph.ADD, "members", "people_group_add_member") { nav.push(PeoplePage.MemberPicker(groupId)) })
                        add(BarButton(Glyph.EDIT, "rename", "people_group_action:rename") { nav.push(PeoplePage.GroupEditor(groupId, group.title)) })
                        add(BarButton(Glyph.DELETE, "delete", "people_group_action:delete") { nav.overlay = "delete_group" })
                    }
                }
                ClockAppBar(buttons, emptyList(), isExpanded = { nav.barExpanded }, onExpand = { nav.barExpanded = it }, tagPrefix = "people")
            }
        },
    ) {
        if (group == null) return@PeopleScaffold
        Box(Modifier.fillMaxSize().testTag("people_group:$groupId")) {
            CapsHeader(group.title, PeopleMetrics.PIVOT_CAP_TOP, 12.5f, "people_group_title")
            LazyColumn(
                Modifier.fillMaxSize().padding(top = PeopleMetrics.SEARCH_TOP).testTag("people_group_list"),
                contentPadding = PaddingValues(bottom = ClockMetrics.APP_BAR + 72.dp),
            ) {
                item {
                    BasicText(
                        "${GroupRules.membersLine(members.size)} · ${CardRules.accountName(group.account)}",
                        Modifier.padding(start = PeopleMetrics.SIDE, bottom = 8.dp).testTag("people_group_members"),
                        style = ShellType.caption.copy(color = colors.subtleText), maxLines = 1,
                    )
                }
                if (!writable) {
                    item {
                        BasicText(
                            "This group is in ${CardRules.accountName(group.account)}. To change it, allow that account in Can edit.",
                            Modifier.padding(start = PeopleMetrics.SIDE, end = PeopleMetrics.SIDE, bottom = 8.dp).testTag("people_group_readonly")
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { nav.push(PeoplePage.CanEdit) },
                            style = ShellType.body.copy(color = colors.accent),
                        )
                    }
                }
                items(members.size, key = { members[it].id }) { i ->
                    val row = members[i]
                    ContactRowView(row, "people_row:${row.lookup}", { nav.push(PeoplePage.Card(row.id, row.lookup)) })
                }
            }
        }
        OverlayLayer(active = { nav.overlay == "delete_group" }) {
            if (nav.overlay == "delete_group") {
                RecorderDialog(
                    tag = "people_group_delete_dialog",
                    title = "Delete ${group.title}?",
                    confirmLabel = "Delete",
                    confirmTag = "people_group_delete_confirm",
                    cancelTag = "people_group_delete_cancel",
                    onConfirm = {
                        dismissOverlay { nav.overlay = null }
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { PeopleWriter.deleteGroup(context, groupId) }
                            if (result is WriteResult.Ok) nav.pop() else nav.notice = PeopleNotices.of(result, "delete this group")
                        }
                    },
                    onCancel = { dismissOverlay { nav.overlay = null } },
                ) {
                    BasicText("The group goes; its contacts stay.", style = ShellType.body.copy(color = Color.White))
                }
            }
        }
    }
}

/**
 * A new group, or a group's new name (T16-14): the name in the editor's outlined box. A new group is saved to the
 * phone unless an allowed account is chosen — the contact editor's rule and its `people_editor_account` control.
 */
@Composable
fun GroupEditorPage(env: PeopleEnv, page: PeoplePage.GroupEditor) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var title by remember(page) { mutableStateOf(page.title) }
    var account by remember(page) { mutableStateOf(env.repo.local) }
    var saving by remember { mutableStateOf(false) }
    var accountY by remember { mutableFloatStateOf(0f) }
    var pageY by remember { mutableFloatStateOf(0f) }
    val isNew = page.groupId == null
    val choices = remember(env.policy, env.repo.accounts) {
        listOf(env.repo.local) + CanEditRules.rows(env.repo.accounts, env.policy).filter { it in env.policy.allowed }
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (page.groupId == null) PeopleWriter.createGroup(context, title, account) else PeopleWriter.renameGroup(context, page.groupId, title)
            }
            saving = false
            when {
                result !is WriteResult.Ok -> nav.notice = PeopleNotices.of(result, if (isNew) "make this group" else "rename this group")
                isNew && result.id != null -> nav.replace(PeoplePage.Group(result.id))
                else -> nav.pop()
            }
        }
    }

    PeopleScaffold(
        tag = "people_page:group_editor",
        host = env.host,
        notice = nav.notice,
        onNoticeAction = env.onNoticeAction,
        bar = {
            ClockAppBar(
                buttons = listOf(
                    BarButton(Glyph.SAVE, "save", "people_group_save", enabled = title.isNotBlank() && title.trim() != page.title && !saving) { save() },
                    BarButton(Glyph.DISMISS, "cancel", "people_group_cancel") { nav.pop() },
                ),
                menu = emptyList(),
                isExpanded = { nav.barExpanded },
                onExpand = { nav.barExpanded = it },
                tagPrefix = "people",
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pageY = it.positionInRoot().y }) {
            CapsHeader(if (isNew) "New group" else "Rename group", PeopleMetrics.EDITOR_HEADER_CAP_TOP, PeopleMetrics.EDITOR_HEADER_X.value, "people_editor_header")
            Column(Modifier.fillMaxSize().padding(top = PeopleMetrics.SEARCH_TOP)) {
                BasicText("Name", Modifier.padding(start = PeopleMetrics.SIDE).height(27.dp), style = ShellType.body.copy(color = colors.text))
                OutlinedField(
                    value = title, onValueChange = { title = it }, tag = "people_group_name",
                    modifier = Modifier.padding(horizontal = PeopleMetrics.SIDE).fillMaxWidth(), maxLength = 100,
                )
                if (isNew) {
                    BasicText("Save to", Modifier.padding(start = PeopleMetrics.SIDE, top = 18.dp).height(27.dp), style = ShellType.body.copy(color = colors.text))
                    Row(
                        Modifier.padding(start = PeopleMetrics.SIDE).height(OutlinedFieldMetrics.HEIGHT).onGloballyPositioned { accountY = it.positionInRoot().y }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                nav.overlay = "account"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(CardRules.accountName(account), Modifier.testTag("people_editor_account"), style = ShellType.body.copy(color = colors.accent), maxLines = 1)
                        PeopleGlyph(Glyph.CHEVRON_DOWN, 10f, colors.accent, Modifier.padding(start = 6.dp))
                    }
                }
            }
            OverlayLayer(active = { nav.overlay == "account" }) {
                Box(Modifier.fillMaxSize()) {
                    if (nav.overlay == "account") {
                        MenuFlyout(
                            with(density) { (accountY - pageY).toDp() } + OutlinedFieldMetrics.HEIGHT, "people_editor_account_menu",
                            choices.map { a ->
                                MenuItem("people_editor_account_row:${if (env.policy.isPhone(a)) "phone" else a.id}", CardRules.accountName(a)) { account = a }
                            },
                        ) { dismissOverlay { nav.overlay = null } }
                    }
                }
            }
        }
    }
}

/**
 * Choosing a group's members: the contacts that live in the group's own account (a membership is a data row on the
 * member's raw contact there), each with a box that is ticked while it is a member. A tick is written at once.
 */
@Composable
fun MemberPickerPage(env: PeopleEnv, groupId: Long) {
    val context = LocalContext.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    val group = env.repo.groups.firstOrNull { it.id == groupId }
    LaunchedEffect(groupId, env.repo.version) {
        if (env.repo.loaded && env.repo.groups.none { it.id == groupId }) nav.pop(PeopleNotices.GROUP_GONE)
    }
    LeafListPage(env, "people_page:member_picker", "Choose members") {
        if (group == null) return@LeafListPage
        val candidates = env.repo.all.filter { row -> env.repo.memberships[row.id]?.accounts?.contains(group.account) == true }
        if (candidates.isEmpty()) item { PageLine("No contact is saved in ${CardRules.accountName(group.account)}.", "people_member_empty") }
        items(candidates.size, key = { candidates[it].id }) { i ->
            val row = candidates[i]
            val isMember = env.repo.memberships[row.id]?.groups?.contains(groupId) == true
            val toggle = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { PeopleWriter.setMember(context, groupId, row.id, !isMember) }
                    nav.notice = PeopleNotices.of(result, "change this group")
                }
                Unit
            }
            ContactRowView(row, "people_row:${row.lookup}", toggle) {
                Box(Modifier.align(Alignment.CenterEnd).width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    ClockCheckbox(isMember, "people_member:${row.lookup}")
                }
            }
        }
    }
}

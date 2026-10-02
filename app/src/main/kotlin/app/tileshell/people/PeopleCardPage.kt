package app.tileshell.people

import android.graphics.Bitmap
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.calculator.InkText
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockAppBar
import app.tileshell.clock.ClockMenuEntry
import app.tileshell.clock.ClockMetrics
import app.tileshell.recorder.RecorderDialog
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The contact card (r11/people.md §4): a full accent page from the status bar to the nav bar, all text white — the
 * name in caps, "Profile", the 124-epx photo, the account, then titled action rows ("Call Mobile" over the number).
 *
 * What it offers follows the write guard (Decisions point (4)): Edit where at least one raw contact is editable,
 * Delete only where every one is, and for a contact with none the `people_card_readonly` line, whose tap opens "Can
 * edit". Link, Unlink and Share are offered on any contact of this profile.
 */
@Composable
fun CardPage(env: PeopleEnv, page: PeoplePage.Card) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var card by remember(page) { mutableStateOf<ContactCard?>(null) }
    var photo by remember(page) { mutableStateOf<Bitmap?>(null) }

    // The card follows the provider. A contact that is gone closes the card with a notice; one whose id changed when
    // it was linked or unlinked is found again through its lookup key.
    LaunchedEffect(page, env.repo.version) {
        if (!env.repo.loaded) return@LaunchedEffect
        val enterprise = page.enterprise
        val read = withContext(Dispatchers.IO) {
            if (enterprise != null) return@withContext PeopleData.enterpriseCard(context, enterprise)
            PeopleData.card(context, page.contactId)
                ?: PeopleData.resolve(context, ContactRef(page.lookup, null))?.let { PeopleData.card(context, it) }
        }
        if (read == null) {
            if (!nav.pop(PeopleNotices.CONTACT_GONE)) nav.replace(PeoplePage.Contacts(ListMode.Browse), PeopleNotices.CONTACT_GONE)
            return@LaunchedEffect
        }
        card = read
        photo = if (!read.hasPhoto) null else withContext(Dispatchers.IO) {
            val px = with(density) { PeopleMetrics.CARD_PHOTO.roundToPx() }
            if (enterprise != null) enterprise.photoThumb?.let { PeopleData.thumbnail(context, it) } else PeopleData.photo(context, read.id, px)
        }
    }

    val shown = card
    val actions = shown?.let { c -> PeopleWriteGuard.cardActions(c.raws.map { it.ref() }, env.policy) }
    val readOnlyLine = actions?.takeIf { !it.edit }?.let { CardRules.readOnlyLine(it.readOnlyAccount, it.otherProfile) }

    fun delete() {
        val c = shown ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) { PeopleWriter.delete(context, c.id) }
            if (result is WriteResult.Ok) {
                if (!nav.pop()) nav.replace(PeoplePage.Contacts(ListMode.Browse))
            } else {
                nav.notice = PeopleNotices.of(result, "delete this contact")
            }
        }
    }

    PeopleScaffold(
        tag = "people_page:card",
        host = env.host,
        notice = nav.notice,
        onNoticeAction = env.onNoticeAction,
        pageFill = colors.accent,
        bar = if (shown == null || shown.enterprise || actions == null) null else {
            {
                // P3.9: link, edit, "…" on the accent page; Delete sits on the bar so a card shows at once whether it has one.
                val buttons = buildList {
                    add(BarButton(Glyph.LINK, "link", "people_card_link") { nav.push(PeoplePage.Link(shown.id, shown.lookup)) })
                    if (actions.edit) add(BarButton(Glyph.EDIT, "edit", "people_card_edit") { nav.push(PeoplePage.Editor(EditorDraft.edit(shown, env.policy, ContactPrefill()))) })
                    if (actions.delete) add(BarButton(Glyph.DELETE, "delete", "people_card_delete") { nav.overlay = "delete" })
                }
                ClockAppBar(
                    buttons = buttons,
                    menu = listOf(ClockMenuEntry("share contact", "people_card_share") { PeopleActions.share(context, shown)?.let { nav.notice = it } }),
                    isExpanded = { nav.barExpanded },
                    onExpand = { nav.barExpanded = it },
                    fill = colors.accent,
                    tagPrefix = "people",
                )
            }
        },
    ) {
        if (shown == null) return@PeopleScaffold
        val white = Color.White
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("people_card:${shown.lookup}")) {
            Box(Modifier.fillMaxWidth().height((PeopleMetrics.CARD_FIRST_ACTION_CAP_TOP - PeopleMetrics.ACTION_CAP_TOP).dp)) {
                // P3.2: the name in caps, 15-epx semibold class, ink at x 13.25, cap top 26 below the status bar.
                InkLine(shown.name.uppercase(), ShellType.base.copy(color = white), PeopleMetrics.CARD_NAME_X.value, PeopleMetrics.CARD_NAME_CAP_TOP, Modifier.testTag("people_card_name"))
                // P3.3: "Profile", 24-epx class, its ascender top 56 below the status bar.
                InkText("Profile", ShellType.title.copy(color = white), reference = "l", modifier = Modifier.testTag("people_card_profile"), inkLeftEpx = PeopleMetrics.CARD_PROFILE_X.value, inkTopEpx = PeopleMetrics.CARD_PROFILE_TOP.value)
                // P3.4: the photo, a 124-epx circle at x 12; without one, the grey disc with the initial (U9).
                AvatarCircle(shown.name, photo, PeopleMetrics.CARD_PHOTO, Modifier.offset(x = PeopleMetrics.SIDE, y = PeopleMetrics.CARD_PHOTO_TOP).testTag("people_card_photo"))
                // P3.5: where the contact lives, dimmed, under the photo.
                BasicText(
                    if (shown.enterprise) "Work profile" else shown.raws.map { CardRules.accountName(it.account) }.distinct().joinToString(", "),
                    Modifier.offset(x = PeopleMetrics.ACTION_X, y = PeopleMetrics.CARD_ACCOUNT_TOP).padding(end = PeopleMetrics.SIDE * 2).testTag("people_card_account"),
                    style = ShellType.caption.copy(color = white.copy(alpha = 0.7f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CardRules.actions(shown).forEach { action ->
                PressRow(
                    { PeopleActions.run(context, action)?.let { nav.notice = it } },
                    Modifier.fillMaxWidth().height(if (action.detail == null) PeopleMetrics.ACTION_ONE_LINE else PeopleMetrics.ACTION_TWO_LINES)
                        .testTag("people_card_action:${action.kind.id}:${action.index}"),
                ) { ActionText(action.title, action.detail, white) }
            }
            // What a card shows and does nothing with: the organisation, the birthday, the notes.
            listOf(FieldKind.COMPANY to "Company", FieldKind.BIRTHDAY to "Birthday", FieldKind.NOTES to "Notes").forEach { (kind, title) ->
                shown.of(kind).forEachIndexed { i, field ->
                    Box(Modifier.fillMaxWidth().height(PeopleMetrics.ACTION_TWO_LINES).testTag("people_card_info:${kind.id}:$i")) {
                        ActionText(title, if (kind == FieldKind.BIRTHDAY) CardRules.birthday(field.value) else field.value, white)
                    }
                }
            }
            Box(Modifier.height(ClockMetrics.APP_BAR + 72.dp))
        }

        if (readOnlyLine != null) {
            // The read-only card's one line (H21), always on screen above the app bar; its tap opens "Can edit".
            val toCanEdit = !actions.otherProfile
            Box(
                Modifier.align(Alignment.BottomStart).offset(y = if (shown.enterprise) 0.dp else -ClockMetrics.APP_BAR).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = toCanEdit) { nav.push(PeoplePage.CanEdit) }
                    .padding(horizontal = PeopleMetrics.SIDE, vertical = 10.dp),
            ) {
                BasicText(readOnlyLine, Modifier.testTag("people_card_readonly"), style = ShellType.body.copy(color = white))
            }
        }

        OverlayLayer(active = { nav.overlay == "delete" }) {
            if (nav.overlay == "delete") {
                // R7 §1.3.9's dialog: deleting a contact is asked once.
                RecorderDialog(
                    tag = "people_delete_dialog",
                    title = "Delete ${shown.name}?",
                    confirmLabel = "Delete",
                    confirmTag = "people_delete_confirm",
                    cancelTag = "people_delete_cancel",
                    onConfirm = { dismissOverlay { nav.overlay = null }; delete() },
                    onCancel = { dismissOverlay { nav.overlay = null } },
                ) {
                    BasicText("This contact will be removed from this phone.", style = ShellType.body.copy(color = Color.White))
                }
            }
        }
    }
}

/**
 * An action row's text (P3.6): the label in the 20-epx class (cap 13.5) with its ink at x 13, and under it — 22.6 epx
 * lower — the value in the 13-epx class.
 */
@Composable
fun BoxScope.ActionText(title: String, detail: String?, color: Color) {
    InkLine(
        title, ShellType.body.copy(fontSize = PeopleMetrics.ACTION_TITLE_SP.sp, lineHeight = 24.sp, color = color),
        PeopleMetrics.ACTION_X.value, PeopleMetrics.ACTION_CAP_TOP, Modifier.testTag("people_action_title"),
    )
    if (detail != null) {
        InkLine(
            detail.replace('\n', ' '), ShellType.body.copy(fontSize = PeopleMetrics.ACTION_DETAIL_SP.sp, lineHeight = 16.sp, color = color),
            PeopleMetrics.ACTION_X.value, PeopleMetrics.ACTION_CAP_TOP + PeopleMetrics.ACTION_DETAIL_DROP, Modifier.testTag("people_action_detail"),
        )
    }
}

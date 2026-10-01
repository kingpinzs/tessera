package app.tileshell.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.calculator.DatePickerPanel
import app.tileshell.calculator.DatePickerRequest
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockAppBar
import app.tileshell.clock.ClockFlyout
import app.tileshell.clock.ClockMetrics
import app.tileshell.clock.PressBox
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.OutlinedField
import app.tileshell.ui.components.OutlinedFieldMetrics
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** One field of the editor's draft. A field with no [dataId] is new; one with a [dataId] and a blank value is removed on Save. */
class DraftField(
    val key: Int,
    val kind: FieldKind,
    val dataId: Long?,
    val rawId: Long?,
    initialValue: String,
    initialType: Int,
    val customLabel: String?,
) {
    var value by mutableStateOf(initialValue)
    var type by mutableIntStateOf(initialType)
    private val originalValue = initialValue
    private val originalType = initialType

    val changed: Boolean
        get() = if (dataId == null) value.isNotBlank() else value.trim() != originalValue.trim() || type != originalType

    fun toEdit(): FieldEdit = FieldEdit(kind, dataId, rawId, value, type, customLabel)
}

/**
 * What the editor holds until Save: a copy of the contact's editable fields, taken when the editor opened. Nothing in
 * it is in the provider until Save is tapped — an `ACTION_INSERT` prefill and the "New contact" shortcut included.
 *
 * A new contact starts on the phone ([account] is the device's local account) and can be moved to an allowed account
 * on `people_editor_account`. An existing contact's fields stay on the raw contact they came from; a new field goes to
 * [targetRaw], the first raw contact the write guard lets People edit. Fields of a raw contact it does not are
 * [readOnly]: shown, never editable.
 */
class EditorDraft private constructor(
    val contactId: Long?,
    val lookup: String?,
    val contactName: String,
    val source: NewContactSource,
    val targetRaw: Long?,
    val targetAccount: ContactAccount,
    initial: List<DraftField>,
    val readOnly: List<Pair<ContactAccount, ContactField>>,
    val hadPhoto: Boolean,
) {
    val isNew: Boolean get() = contactId == null
    var account by mutableStateOf(targetAccount)
    val fields = mutableStateListOf<DraftField>().also { it.addAll(initial) }
    var newPhoto by mutableStateOf<ByteArray?>(null)
    var newPhotoBitmap by mutableStateOf<Bitmap?>(null)
    var removePhoto by mutableStateOf(false)
    private var nextKey = initial.size

    val changed: Boolean get() = fields.any { it.changed } || newPhoto != null || removePhoto

    fun of(kind: FieldKind): List<DraftField> = fields.filter { it.kind == kind }

    fun add(kind: FieldKind): DraftField =
        DraftField(nextKey++, kind, null, targetRaw, "", FieldTypes.default(kind), null).also { fields += it }

    /** The tag suffix of a field: the kind for the first of it, `<kind>:<n>` for the ones after. */
    fun name(field: DraftField): String {
        val index = of(field.kind).indexOf(field)
        return if (index <= 0) field.kind.id else "${field.kind.id}:$index"
    }

    companion object {
        /** A new contact on the phone, with what an intent suggested filled in. */
        fun new(local: ContactAccount, source: NewContactSource, prefill: ContactPrefill): EditorDraft {
            val fields = ArrayList<DraftField>()
            fun add(kind: FieldKind, value: String?) { fields += DraftField(fields.size, kind, null, null, value.orEmpty(), FieldTypes.default(kind), null) }
            add(FieldKind.NAME, prefill.name)
            add(FieldKind.PHONE, prefill.phone)
            add(FieldKind.EMAIL, prefill.email)
            prefill.postal?.let { add(FieldKind.ADDRESS, it) }
            add(FieldKind.COMPANY, prefill.company)
            prefill.notes?.let { add(FieldKind.NOTES, it) }
            return EditorDraft(null, null, prefill.name.orEmpty(), source, null, local, fields, emptyList(), false)
        }

        /**
         * An existing contact: the fields of every raw contact [policy] lets People edit, the others read-only. The
         * caller has checked that at least one raw contact is editable. [prefill] (an `ACTION_INSERT_OR_EDIT`) adds
         * its values as new fields; nothing existing is replaced.
         */
        fun edit(card: ContactCard, policy: EditPolicy, prefill: ContactPrefill): EditorDraft {
            val editable = card.raws.filter { PeopleWriteGuard.editable(it.ref(card.enterprise), policy) }
            val target = editable.first()
            val editableIds = editable.map { it.id }.toSet()
            val accountOf = card.raws.associate { it.id to it.account }
            val fields = ArrayList<DraftField>()
            fun existing(f: ContactField) { fields += DraftField(fields.size, f.kind, f.dataId, f.rawId, f.value, f.type, f.customLabel) }
            fun blank(kind: FieldKind, value: String? = null) { fields += DraftField(fields.size, kind, null, target.id, value.orEmpty(), FieldTypes.default(kind), null) }
            val mine = card.fields.filter { it.rawId in editableIds }
            // One Name and one Company, as W10M's editor has them: the target raw contact's own, else a new one on it.
            mine.firstOrNull { it.kind == FieldKind.NAME && it.rawId == target.id }?.let { existing(it) } ?: blank(FieldKind.NAME)
            mine.filter { it.kind == FieldKind.PHONE }.forEach { existing(it) }
            prefill.phone?.let { blank(FieldKind.PHONE, it) }
            mine.filter { it.kind == FieldKind.EMAIL }.forEach { existing(it) }
            prefill.email?.let { blank(FieldKind.EMAIL, it) }
            mine.filter { it.kind == FieldKind.ADDRESS }.forEach { existing(it) }
            prefill.postal?.let { blank(FieldKind.ADDRESS, it) }
            (mine.firstOrNull { it.kind == FieldKind.COMPANY }?.let { existing(it) }) ?: blank(FieldKind.COMPANY, prefill.company)
            mine.firstOrNull { it.kind == FieldKind.BIRTHDAY }?.let { existing(it) }
            (mine.firstOrNull { it.kind == FieldKind.NOTES }?.let { existing(it) }) ?: prefill.notes?.let { blank(FieldKind.NOTES, it) }
            val readOnly = card.fields.filter { it.rawId !in editableIds }.map { (accountOf[it.rawId] ?: ContactAccount(null, null)) to it }
            return EditorDraft(card.id, card.lookup, card.name, NewContactSource.EDITOR, target.id, target.account, fields, readOnly, card.hasPhoto)
        }
    }
}

/** The editor's vertical rhythm (r11/people.md §5), in epx. A label's cap top sits [LEAD] below the top of its text box. */
private object EditorRhythm {
    val LEAD = CapMetrics.capTopWithinBox(15f)
    val LABEL_H = (LEAD + PeopleMetrics.LABEL_TO_BOX).dp
    val AFTER_RULE = (PeopleMetrics.RULE_CLEAR - LEAD).dp
    val BETWEEN_FIELDS = (22f - LEAD).dp
    val BEFORE_ADD_ROW = 10.dp
    val BOX_TO_RULE = 21.dp
    val AFTER_ADD_ROW = 11.dp
}

/**
 * The contact editor (r11/people.md §5; build task 6): "EDIT <ACCOUNT> CONTACT", the photo, where it is saved, then a
 * flat list of outlined 32-epx fields under their labels — a typed field's label in accent with a ⌄ — and "+ field"
 * rows, divided by 1-epx rules. Save is the app bar's; nothing reaches the provider before it.
 */
@Composable
fun EditorPage(env: PeopleEnv, draft: EditorDraft) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val nav = env.nav
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var photo by remember(draft) { mutableStateOf<Bitmap?>(null) }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var pageY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // The photo the contact has now, at the circle's size; a newly picked one replaces it in the draft only.
    LaunchedEffect(draft, env.repo.version) {
        val id = draft.contactId
        if (id != null && draft.hadPhoto) photo = withContext(Dispatchers.IO) { PeopleData.photo(context, id, with(density) { PeopleMetrics.CARD_PHOTO.roundToPx() }) }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { EditorPhoto.read(context, uri) }
            if (picked == null) {
                nav.notice = PeopleNotice("People couldn't read that photo.")
            } else {
                draft.newPhoto = picked.first
                draft.newPhotoBitmap = picked.second
                draft.removePhoto = false
            }
        }
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val edits = draft.fields.filter { it.changed }.map { it.toEdit() }
            val result = withContext(Dispatchers.IO) {
                if (draft.isNew) {
                    PeopleWriter.create(context, draft.account, draft.source, draft.fields.map { it.toEdit() }, draft.newPhoto)
                } else {
                    val photoEdit = when {
                        draft.newPhoto != null -> PhotoEdit(draft.targetRaw, draft.newPhoto)
                        draft.removePhoto -> PhotoEdit(draft.targetRaw, null)
                        else -> null
                    }
                    PeopleWriter.update(context, edits, photoEdit)
                }
            }
            saving = false
            if (result !is WriteResult.Ok) {
                nav.notice = PeopleNotices.of(result, "save this contact")
                return@launch
            }
            // The card of what was saved: the contact the new raw contact now belongs to, or the one that was edited.
            val saved = withContext(Dispatchers.IO) {
                val id = result.rawId?.takeIf { draft.isNew }?.let { PeopleWriter.contactOf(context, it) } ?: draft.contactId
                id?.let { PeopleData.card(context, it) }
            }
            when {
                saved == null -> if (!nav.pop()) env.host.finish()
                // The editor was opened from this contact's card: back to it. From anywhere else (the list's "+", an
                // intent, INSERT_OR_EDIT's chooser) the card takes the editor's place.
                nav.under is PeoplePage.Card -> nav.pop()
                else -> nav.replace(PeoplePage.Card(saved.id, saved.lookup))
            }
        }
    }

    fun openOverlay(key: String, yPx: Float) {
        anchorY = yPx - pageY
        nav.overlay = key
    }

    val accountChoices = remember(env.policy, env.repo.accounts) {
        listOf(env.repo.local) + CanEditRules.rows(env.repo.accounts, env.policy).filter { it in env.policy.allowed }
    }
    val header = "${if (draft.isNew) "New" else "Edit"} ${CardRules.accountName(if (draft.isNew) draft.account else draft.targetAccount)} contact"

    PeopleScaffold(
        tag = "people_page:editor",
        host = env.host,
        notice = nav.notice,
        onNoticeAction = env.onNoticeAction,
        bar = {
            // P4.8: save (dim until there is a change), cancel, "…".
            ClockAppBar(
                buttons = listOf(
                    BarButton(Glyph.SAVE, "save", "people_editor_save", enabled = draft.changed && !saving) { save() },
                    BarButton(Glyph.DISMISS, "cancel", "people_editor_cancel") { if (!nav.pop()) env.host.finish() },
                ),
                menu = emptyList(),
                isExpanded = { nav.barExpanded },
                onExpand = { nav.barExpanded = it },
                tagPrefix = "people",
            )
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { pageY = it.positionInRoot().y }) {
            val widthEpx = maxWidth.value
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("people_editor")
                    .padding(bottom = ClockMetrics.APP_BAR + 24.dp).padding(WindowInsets.ime.asPaddingValues()),
            ) {
                Box(Modifier.fillMaxWidth().height(PeopleMetrics.EDITOR_PHOTO_TOP)) {
                    CapsHeader(header, PeopleMetrics.EDITOR_HEADER_CAP_TOP, PeopleMetrics.EDITOR_HEADER_X.value, "people_editor_header")
                }
                // P4.2: the 124-epx circle at x 12. A tap chooses a photo with Android's photo picker, or removes it.
                var photoY by remember { mutableFloatStateOf(0f) }
                val shown = when {
                    draft.newPhotoBitmap != null -> draft.newPhotoBitmap
                    draft.removePhoto -> null
                    else -> photo
                }
                AvatarCircle(
                    draft.of(FieldKind.NAME).firstOrNull()?.value?.takeIf { it.isNotBlank() } ?: draft.contactName,
                    shown, PeopleMetrics.CARD_PHOTO,
                    Modifier.padding(start = PeopleMetrics.SIDE).testTag("people_editor_photo")
                        .onGloballyPositioned { photoY = it.positionInRoot().y }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (shown == null) pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else openOverlay("photo", photoY + with(density) { PeopleMetrics.CARD_PHOTO.toPx() })
                        },
                )
                Spacer(Modifier.height(EditorRhythm.BETWEEN_FIELDS))

                // Where the contact is saved (Q-16-3): "Phone", or an account on "Can edit" — a new contact's choice only.
                var accountY by remember { mutableFloatStateOf(0f) }
                FieldLabel(if (draft.isNew) "Save to" else "Saved to", accent = false, tag = "people_editor_account_label", chevron = false, onTap = null)
                Row(
                    Modifier.padding(start = PeopleMetrics.SIDE).height(OutlinedFieldMetrics.HEIGHT).onGloballyPositioned { accountY = it.positionInRoot().y }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = draft.isNew) {
                            openOverlay("account", accountY + with(density) { OutlinedFieldMetrics.HEIGHT.toPx() })
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        CardRules.accountName(if (draft.isNew) draft.account else draft.targetAccount),
                        Modifier.testTag("people_editor_account"),
                        style = ShellType.body.copy(color = if (draft.isNew) colors.accent else colors.text),
                        maxLines = 1,
                    )
                    if (draft.isNew) PeopleGlyph(Glyph.CHEVRON_DOWN, 10f, colors.accent, Modifier.padding(start = 6.dp))
                }

                Spacer(Modifier.height(EditorRhythm.BEFORE_ADD_ROW))
                GroupRule()
                Spacer(Modifier.height(EditorRhythm.AFTER_RULE))
                draft.of(FieldKind.NAME).forEach { TextField(draft, it, "Name", KeyboardType.Text) }
                // P4.7: a rule sits 20–22 epx under the box above it (G2: the Name box ends at 290, its rule is at 310).
                Spacer(Modifier.height(EditorRhythm.BOX_TO_RULE - EditorRhythm.BEFORE_ADD_ROW))

                TypedGroup(draft, FieldKind.PHONE, "Phone", KeyboardType.Phone) { key, y -> openOverlay(key, y) }
                TypedGroup(draft, FieldKind.EMAIL, "Email", KeyboardType.Email) { key, y -> openOverlay(key, y) }
                TypedGroup(draft, FieldKind.ADDRESS, "Address", KeyboardType.Text) { key, y -> openOverlay(key, y) }

                Spacer(Modifier.height(EditorRhythm.BEFORE_ADD_ROW))
                GroupRule()
                Spacer(Modifier.height(EditorRhythm.AFTER_RULE))
                draft.of(FieldKind.COMPANY).forEach { TextField(draft, it, "Company", KeyboardType.Text) }
                draft.of(FieldKind.BIRTHDAY).forEach { field ->
                    Spacer(Modifier.height(EditorRhythm.BETWEEN_FIELDS))
                    FieldLabel("Birthday", accent = false, tag = "people_field_label:${draft.name(field)}", chevron = false, onTap = null)
                    // A date is chosen on the drawn picker, never typed: the field holds what the provider stores.
                    PressBox(
                        Modifier.padding(horizontal = PeopleMetrics.SIDE).fillMaxWidth().height(OutlinedFieldMetrics.HEIGHT),
                        onClick = { nav.overlay = "birthday" },
                    ) {
                        OutlinedField(
                            value = if (field.value.isBlank()) "" else CardRules.birthday(field.value), onValueChange = {},
                            tag = "people_field:${draft.name(field)}", modifier = Modifier.fillMaxWidth(), enabled = false, placeholder = "choose a date",
                        )
                    }
                }
                draft.of(FieldKind.NOTES).forEach {
                    Spacer(Modifier.height(EditorRhythm.BETWEEN_FIELDS))
                    TextField(draft, it, "Notes", KeyboardType.Text, maxLength = 5000)
                }
                val others = listOf(FieldKind.BIRTHDAY, FieldKind.NOTES).filter { draft.of(it).isEmpty() }
                if (others.isNotEmpty()) {
                    var otherY by remember { mutableFloatStateOf(0f) }
                    Spacer(Modifier.height(EditorRhythm.BEFORE_ADD_ROW))
                    AddRow("Other", "people_add_field:other", Modifier.onGloballyPositioned { otherY = it.positionInRoot().y }) {
                        openOverlay("other", otherY)
                    }
                }

                // A contact joined from an editable and a read-only account: the other part is shown, never editable (H21).
                draft.readOnly.groupBy { it.first }.forEach { (account, fields) ->
                    Spacer(Modifier.height(EditorRhythm.BEFORE_ADD_ROW))
                    GroupRule()
                    Spacer(Modifier.height(EditorRhythm.AFTER_RULE))
                    BasicText(
                        "In ${CardRules.accountName(account)} (read-only)",
                        Modifier.padding(horizontal = PeopleMetrics.SIDE).testTag("people_editor_readonly:${account.id}"),
                        style = ShellType.body.copy(color = colors.subtleText),
                    )
                    fields.map { it.second }.forEach { f ->
                        BasicText(
                            "${FieldTypes.editorLabel(f.kind, f.type, f.customLabel)}: ${if (f.kind == FieldKind.BIRTHDAY) CardRules.birthday(f.value) else f.value}",
                            Modifier.padding(start = PeopleMetrics.SIDE, end = PeopleMetrics.SIDE, top = 6.dp).testTag("people_field_readonly:${f.dataId}"),
                            style = ShellType.body.copy(color = colors.subtleText),
                        )
                    }
                }
            }

            // What opens over the page. Each is placed only while open (L13-3), so a Back that closes it frees the page at once.
            OverlayLayer(active = { nav.overlay != null }) {
                Box(Modifier.fillMaxSize()) {
                    val overlay = nav.overlay
                    val anchor = with(density) { anchorY.toDp() }
                    val close = { dismissOverlay { nav.overlay = null } }
                    when {
                        overlay == null -> Unit
                        overlay == "account" -> MenuFlyout(
                            anchor, "people_editor_account_menu",
                            accountChoices.map { a ->
                                val id = if (env.policy.isPhone(a)) "phone" else a.id
                                MenuItem("people_editor_account_row:$id", CardRules.accountName(a)) { draft.account = a }
                            },
                            close,
                        )
                        overlay == "photo" -> MenuFlyout(
                            anchor, "people_photo_menu",
                            listOf(
                                MenuItem("people_photo_choose", "choose photo") { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                MenuItem("people_photo_remove", "remove photo") { draft.newPhoto = null; draft.newPhotoBitmap = null; draft.removePhoto = draft.hadPhoto; photo = null },
                            ),
                            close,
                        )
                        overlay == "other" -> MenuFlyout(
                            anchor, "people_other_menu",
                            listOf(FieldKind.BIRTHDAY, FieldKind.NOTES).filter { draft.of(it).isEmpty() }.map { kind ->
                                MenuItem("people_other:${kind.id}", FieldTypes.editorLabel(kind, 0, null)) {
                                    draft.add(kind)
                                    if (kind == FieldKind.BIRTHDAY) nav.overlay = "birthday"
                                }
                            },
                            close,
                        )
                        overlay == "birthday" -> draft.of(FieldKind.BIRTHDAY).firstOrNull()?.let { field ->
                            val initial = runCatching { LocalDate.parse(field.value) }.getOrElse { LocalDate.of(2000, 1, 1) }
                            DatePickerPanel(DatePickerRequest.Date(initial) { field.value = it.toString() }, widthEpx, onDone = close)
                        }
                        overlay.startsWith("type:") -> draft.fields.firstOrNull { "type:${it.key}" == overlay }?.let { field ->
                            MenuFlyout(
                                anchor, "people_type_menu",
                                FieldTypes.options(field.kind).map { t ->
                                    MenuItem("people_type_option:${t.type}", FieldTypes.editorLabel(field.kind, t.type, null)) { field.type = t.type }
                                },
                                close,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A fixed field: its white label over an outlined box (P4.3, P4.4). */
@Composable
private fun TextField(draft: EditorDraft, field: DraftField, label: String, keyboard: KeyboardType, maxLength: Int = 500) {
    val name = draft.name(field)
    FieldLabel(label, accent = false, tag = "people_field_label:$name", chevron = false, onTap = null)
    OutlinedField(
        value = field.value, onValueChange = { field.value = it }, tag = "people_field:$name",
        modifier = Modifier.padding(horizontal = PeopleMetrics.SIDE).fillMaxWidth(), maxLength = maxLength, keyboardType = keyboard,
    )
}

/**
 * A group of typed fields (phones, e-mails, addresses): each under its accent type label with a ⌄ that opens the type
 * list, then the "+ field" row (P4.3, P4.6), all under one rule (P4.7).
 */
@Composable
private fun TypedGroup(draft: EditorDraft, kind: FieldKind, word: String, keyboard: KeyboardType, onType: (String, Float) -> Unit) {
    val density = LocalDensity.current
    Spacer(Modifier.height(EditorRhythm.BEFORE_ADD_ROW))
    GroupRule()
    val fields = draft.of(kind)
    fields.forEachIndexed { i, field ->
        Spacer(Modifier.height(if (i == 0) EditorRhythm.AFTER_RULE else EditorRhythm.BETWEEN_FIELDS))
        val name = draft.name(field)
        var labelY by remember(field.key) { mutableFloatStateOf(0f) }
        FieldLabel(
            FieldTypes.editorLabel(kind, field.type, field.customLabel), accent = true, tag = "people_field_type:$name", chevron = true,
            modifier = Modifier.onGloballyPositioned { labelY = it.positionInRoot().y },
            onTap = { onType("type:${field.key}", labelY + with(density) { EditorRhythm.LABEL_H.toPx() }) },
        )
        OutlinedField(
            value = field.value, onValueChange = { field.value = it }, tag = "people_field:$name",
            modifier = Modifier.padding(horizontal = PeopleMetrics.SIDE).fillMaxWidth(), keyboardType = keyboard,
        )
    }
    Spacer(Modifier.height(if (fields.isEmpty()) 6.dp else EditorRhythm.BEFORE_ADD_ROW))
    AddRow(word, "people_add_field:${kind.id}") { draft.add(kind) }
    Spacer(Modifier.height(EditorRhythm.AFTER_ADD_ROW - EditorRhythm.BEFORE_ADD_ROW))
}

/**
 * A field's label (P4.3, P4.5): 15-epx class, its cap top 22.75 epx above the box under it; a typed field's in accent
 * with a ⌄, a fixed field's in the text colour.
 */
@Composable
private fun FieldLabel(text: String, accent: Boolean, tag: String, chevron: Boolean, modifier: Modifier = Modifier, onTap: (() -> Unit)?) {
    val colors = LocalShellColors.current
    val color = if (accent) colors.accent else colors.text
    Row(
        modifier.padding(start = PeopleMetrics.SIDE).height(EditorRhythm.LABEL_H)
            .let { m -> if (onTap != null) m.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap) else m },
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(text, Modifier.testTag(tag), style = ShellType.body.copy(color = color), maxLines = 1)
        if (chevron) PeopleGlyph(Glyph.CHEVRON_DOWN, 10f, color, Modifier.padding(start = 6.dp, top = 5.dp))
    }
}

/** P4.6: a "+ field" row — a 16-epx "+" and a 15-epx label at x 12, 44 epx tall. */
@Composable
private fun AddRow(word: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalShellColors.current
    PressBox(modifier.fillMaxWidth().height(PeopleMetrics.ADD_ROW).testTag(tag), onClick = onClick) {
        Row(Modifier.align(Alignment.CenterStart).padding(start = PeopleMetrics.SIDE), verticalAlignment = Alignment.CenterVertically) {
            PeopleGlyph(Glyph.ADD, 16f, colors.text)
            Spacer(Modifier.width(8.dp))
            BasicText(word, style = ShellType.body.copy(color = colors.text), maxLines = 1)
        }
    }
}

/** One entry of a flyout list. */
class MenuItem(val tag: String, val label: String, val onPick: () -> Unit)

/** R7 §3.6.2's flyout as a list of choices under its anchor: 44-epx items, x 12 → W − 12; a tap outside closes it. */
@Composable
fun BoxScope.MenuFlyout(anchorY: Dp, tag: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth - PeopleMetrics.SIDE * 2
        ClockFlyout(
            x = PeopleMetrics.SIDE, top = anchorY, width = width, height = ClockMetrics.MENU_ROW * items.size + 16.dp,
            tag = tag, onDismiss = onDismiss, bottomInset = ClockMetrics.APP_BAR,
        ) {
            Box(Modifier.height(8.dp))
            items.forEach { item ->
                PressBox(Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).testTag(item.tag), onClick = { onDismiss(); item.onPick() }) {
                    BasicText(item.label, Modifier.align(Alignment.CenterStart).offset(x = 11.7.dp), style = ShellType.body.copy(color = Color.White), maxLines = 1)
                }
            }
        }
    }
}

/** A picked photo made ready for a contact: cropped to its centre square, at most 720 px, as JPEG. */
object EditorPhoto {
    private const val MAX_PX = 720

    fun read(context: Context, uri: Uri): Pair<ByteArray, Bitmap>? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply { inSampleSize = ContactPhotoRules.sampleSize(bounds.outWidth, bounds.outHeight, MAX_PX) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return@runCatching null
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = if (side > MAX_PX) Bitmap.createScaledBitmap(square, MAX_PX, MAX_PX, true) else square
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.toByteArray() to scaled
    }.getOrNull()
}

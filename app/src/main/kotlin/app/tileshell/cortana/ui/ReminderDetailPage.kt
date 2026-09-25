package app.tileshell.cortana.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.cortana.reminders.Place
import app.tileshell.cortana.reminders.Recurrence
import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderPhotos
import app.tileshell.cortana.reminders.ReminderKind
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import java.util.Calendar

/**
 * One reminder, open (R7 §3.7). `reminderId == null` is the empty page the app bar's + opens
 * (approximation, H25: R7 §3.9.5 never taps +).
 *
 * Every y here is R7 §3.7's, measured from the screen top, and the page hides the status bar
 * (§3.1.13), so the composable's own top is the screen top.
 *
 * **Approximations, all H25 unless noted:** save enables after any field changes (and, on the empty
 * page, once the text field has text); camera enables while no photo is attached; delete acts as the
 * long-press menu's Delete; "…" raises the app bar to show a label under each button. A place or
 * person reminder shows a place or contact field where a timed one has its time and date (H26). The
 * time and date **pickers** have no R7 source at all — see [SpinnerPicker].
 */
@Composable
fun ReminderDetailPage(
    reminderId: String?,
    onDone: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore.get(context) }
    val places by store.places.collectAsState()
    val original = remember(reminderId) { reminderId?.let { store.find(it) } }
    val accent = LocalShellColors.current.accent

    var draft by remember(reminderId) { mutableStateOf(original ?: Reminder(id = "", text = "")) }
    var completeChecked by remember(reminderId) { mutableStateOf(original?.completed ?: false) }
    var edited by remember(reminderId) { mutableStateOf(false) }
    var picker by remember(reminderId) { mutableStateOf(DetailPicker.NONE) }
    var moreLabels by remember(reminderId) { mutableStateOf(false) }
    var saved by remember(reminderId) { mutableStateOf(false) }

    fun edit(change: (Reminder) -> Reminder) {
        draft = change(draft)
        edited = true
    }

    // Launched through Tess's window's own registry (L13-1). The picked photo is copied into the launcher's storage
    // here, while the picker's read grant is live, and the reminder keeps the copy (Q2).
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ReminderPhotos.adopt(context, uri)?.let { copy -> edit { it.copy(photoUri = copy) } }
    }
    // A photo picked here and never saved is deleted when the page goes — Back, the ≡ pane, delete, Tess closing.
    DisposableEffect(reminderId) {
        onDispose {
            if (!saved && draft.photoUri != original?.photoUri) ReminderPhotos.release(context, draft.photoUri)
        }
    }

    // H25: save enables after any field changes; on the empty page it also needs text to save.
    val canSave = edited && draft.text.isNotBlank()
    val canAttachPhoto = draft.photoUri == null

    Box(modifier.fillMaxSize().background(CortanaUi.PAGE_BG)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                  val screenWidthEpx = maxWidth.value
                  val photoHeightEpx = (screenWidthEpx - 2 * CortanaUi.DETAIL_PHOTO_SIDE_EPX) / CortanaUi.DETAIL_PHOTO_ASPECT
                  val contentHeightEpx =
                      if (draft.photoUri != null) CortanaUi.DETAIL_PHOTO_TOP_EPX + photoHeightEpx + CortanaUi.FIELD_LEFT_EPX
                      else CortanaUi.COMBO_TOP_EPX + CortanaUi.COMBO_EPX + CortanaUi.FIELD_LEFT_EPX
                  Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.fillMaxWidth().height(contentHeightEpx.dp))

                    // R7 §3.7.2: the checkbox and its label.
                    CompleteCheckbox(completeChecked) { completeChecked = !completeChecked; edited = true }

                    // R7 §3.7.3: the reminder text field, width fitted to its text.
                    ReminderTextField(draft.text) { edit { r -> r.copy(text = it) } }

                    // R7 §3.7.1 / H26: time + date, or the place or contact field that replaces them.
                    when (draft.kind) {
                        ReminderKind.PLACE -> PlaceField(draft, places) { picker = DetailPicker.PLACE }
                        ReminderKind.PERSON -> ContactField(draft)
                        ReminderKind.TIME -> TimeAndDateFields(
                            draft.timeMs,
                            screenWidthEpx,
                            onTime = { picker = DetailPicker.TIME },
                            onDate = { picker = DetailPicker.DATE },
                        )
                    }

                    // R7 §3.7.3: the recurrence combo, 32 epx tall.
                    RecurrenceCombo(draft.recurrence) { picker = DetailPicker.RECURRENCE }

                    // R7 §3.7.4: the attached photo.
                    val photoUri = draft.photoUri
                    if (photoUri != null) {
                        val photo = rememberReminderPhoto(context, photoUri)
                        Box(
                            Modifier
                                .offset(
                                    x = CortanaUi.DETAIL_PHOTO_SIDE_EPX.dp,
                                    y = CortanaUi.DETAIL_PHOTO_TOP_EPX.dp,
                                )
                                .size((screenWidthEpx - 2 * CortanaUi.DETAIL_PHOTO_SIDE_EPX).dp, photoHeightEpx.dp)
                                .background(CortanaUi.FIELD_FILL)
                                .testTag("reminder_page_photo"),
                        ) {
                            if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                  }
                }
                // R7 §3.7.1: the ≡ header, with no title.
                CortanaPageHeader(null, onOpenMenu)
            }

            // R7 §3.5.8: camera / delete / save / "…", the dim ones disabled.
            CortanaAppBar(
                labelled = moreLabels,
                tag = "reminder_appbar",
                buttons = listOf(
                    AppBarButton("reminder_appbar_camera", "photo", canAttachPhoto, {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { c, m -> CortanaIcons.Font(Glyph.CAMERA, c, CortanaUi.APPBAR_GLYPH_EPX, m) },
                    AppBarButton("reminder_appbar_delete", "delete", reminderId != null, {
                        reminderId?.let { store.delete(it) }
                        onDone()
                    }) { c, m -> CortanaIcons.Font(Glyph.DELETE, c, CortanaUi.APPBAR_GLYPH_EPX, m) },
                    AppBarButton("reminder_appbar_save", "save", canSave, {
                        val toSave = draft.copy(completed = completeChecked)
                        if (original == null) store.add(toSave) else store.update(toSave)
                        saved = true
                        onDone()
                    }) { c, m -> CortanaIcons.Font(Glyph.SAVE, c, CortanaUi.APPBAR_GLYPH_EPX, m) },
                    AppBarButton("reminder_appbar_more", "more", true, { moreLabels = !moreLabels }) { c, m ->
                        CortanaIcons.Font(Glyph.MORE_HORIZONTAL, c, CortanaUi.APPBAR_GLYPH_EPX, m)
                    },
                ),
            )
        }

        when (picker) {
            DetailPicker.NONE -> Unit
            DetailPicker.TIME -> TimeSpinner(draft.timeMs) { at ->
                picker = DetailPicker.NONE
                if (at != null) edit { it.copy(timeMs = at) }
            }
            DetailPicker.DATE -> DateSpinner(draft.timeMs) { at ->
                picker = DetailPicker.NONE
                if (at != null) edit { it.copy(timeMs = at) }
            }
            DetailPicker.RECURRENCE -> OptionList(
                Recurrence.entries.map { it.label },
                Recurrence.entries.indexOf(draft.recurrence),
                accent,
            ) { index ->
                picker = DetailPicker.NONE
                if (index != null) edit { it.copy(recurrence = Recurrence.entries[index]) }
            }
            DetailPicker.PLACE -> OptionList(
                places.map { it.name },
                places.indexOfFirst { it.id == draft.placeId },
                accent,
            ) { index ->
                picker = DetailPicker.NONE
                if (index != null) edit { it.copy(placeId = places[index].id) }
            }
        }
    }
}

private enum class DetailPicker { NONE, TIME, DATE, RECURRENCE, PLACE }

// ---------------------------------------------------------------------------------------------
// The fields (R7 §3.7.2–§3.7.4)
// ---------------------------------------------------------------------------------------------

/** R7 §3.7.2: box 20.6 epx with a ≈2-epx light border at x 11.7, top 57.2; label body class at x 40.5. */
@Composable
private fun CompleteCheckbox(checked: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .offset(x = CortanaUi.CHECKBOX_LEFT_EPX.dp, y = CortanaUi.CHECKBOX_TOP_EPX.dp)
            .size(CortanaUi.CHECKBOX_EPX.dp)
            .border(CortanaUi.CHECKBOX_BORDER_EPX.dp, CortanaUi.ROW_TITLE_COLOR)
            .pointerInput(checked) { detectTapGestures { onToggle() } }
            .testTag("reminder_page_checkbox")
            .semantics { role = Role.Checkbox; toggleableState = ToggleableState(checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            CortanaIcons.Font(Glyph.CHECKMARK, CortanaUi.ROW_TITLE_COLOR, CortanaUi.CHECKBOX_EPX * 0.7f)
        }
    }
    // The label's cap is centred on the box (approximation, H24: §3.7.2 gives its left and class only).
    CapCentreText(
        text = CortanaUi.CHECKBOX_LABEL_TEXT,
        style = ShellType.body.copy(color = CortanaUi.ROW_TITLE_COLOR),
        capCentreEpx = CortanaUi.CHECKBOX_TOP_EPX + CortanaUi.CHECKBOX_EPX / 2f,
        leftEpx = CortanaUi.CHECKBOX_LABEL_LEFT_EPX,
    )
}

/**
 * R7 §3.7.3: top 95.9 epx, 43.4 tall, **width fitted to its text** (143.6 epx on the captured
 * reminder), text cap 16.5 epx (the 24-epx ramp step) at left 17.6 epx from the screen.
 */
@Composable
private fun ReminderTextField(text: String, onChange: (String) -> Unit) {
    val style = ShellType.title.copy(color = CortanaUi.ROW_TITLE_COLOR)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 2)
    // Inside ShellDensity 1 dp == 1 epx, so the measured px divided by the density is epx.
    val textWidthEpx = remember(text, style, density) {
        with(density) { measurer.measure(text.ifEmpty { " " }, style, softWrap = false).size.width.toDp().value }
    }
    val inset = CortanaUi.FIELD_TEXT_LEFT_EPX - CortanaUi.FIELD_LEFT_EPX

    FieldBox(
        topEpx = CortanaUi.TEXT_FIELD_TOP_EPX,
        leftEpx = CortanaUi.FIELD_LEFT_EPX,
        widthEpx = (textWidthEpx + 2 * inset).coerceAtLeast(MIN_TEXT_FIELD_EPX),
        heightEpx = CortanaUi.FIELD_EPX,
        tag = "reminder_page_text",
        onClick = null,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(CortanaUi.ROW_TITLE_COLOR),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = inset.dp)
                .fillMaxWidth(),
        )
    }
}

/** Approximation (H24): R7 fits the field to its text but measures no minimum for an empty one. */
private const val MIN_TEXT_FIELD_EPX = 120f

/** R7 §3.7.3: the time and date fields side by side, 11.3 epx apart, top 149.5 epx. */
@Composable
private fun TimeAndDateFields(atMs: Long?, screenWidthEpx: Float, onTime: () -> Unit, onDate: () -> Unit) {
    val context = LocalContext.current
    val usable = screenWidthEpx - 2 * CortanaUi.FIELD_LEFT_EPX - CortanaUi.FIELD_GAP_EPX
    val each = usable / 2f
    val at = atMs ?: System.currentTimeMillis()
    FieldBox(CortanaUi.TIME_DATE_TOP_EPX, CortanaUi.FIELD_LEFT_EPX, each, CortanaUi.FIELD_EPX, "reminder_page_time", onTime) {
        FieldText(
            if (atMs == null) "Set a time" else DateFormat.getTimeFormat(context).format(at),
            Modifier.align(Alignment.CenterStart),
        )
    }
    FieldBox(
        CortanaUi.TIME_DATE_TOP_EPX,
        CortanaUi.FIELD_LEFT_EPX + each + CortanaUi.FIELD_GAP_EPX,
        each,
        CortanaUi.FIELD_EPX,
        "reminder_page_date",
        onDate,
    ) {
        // R7 §3.7.1 captured "Monday, July 25" — the platform's long date format in the user's locale.
        FieldText(
            if (atMs == null) "Set a date" else DateFormat.getLongDateFormat(context).format(at),
            Modifier.align(Alignment.CenterStart),
        )
    }
}

/** H26: a place reminder's field, where a timed reminder has its time and date. */
@Composable
private fun PlaceField(reminder: Reminder, places: List<Place>, onPick: () -> Unit) {
    val name = places.firstOrNull { it.id == reminder.placeId }?.name ?: "Choose a place"
    FieldBox(
        CortanaUi.TIME_DATE_TOP_EPX,
        CortanaUi.FIELD_LEFT_EPX,
        null,
        CortanaUi.FIELD_EPX,
        "reminder_page_place",
        onPick,
        fitToContent = true,
    ) { FieldText("When I arrive at $name", Modifier.align(Alignment.CenterStart)) }
}

/** H26: a person reminder's field. The contact itself is picked in the action layer, not here. */
@Composable
private fun ContactField(reminder: Reminder) {
    FieldBox(
        CortanaUi.TIME_DATE_TOP_EPX,
        CortanaUi.FIELD_LEFT_EPX,
        null,
        CortanaUi.FIELD_EPX,
        "reminder_page_contact",
        null,
        fitToContent = true,
    ) { FieldText("Next time I talk to ${reminder.contactName.orEmpty()}", Modifier.align(Alignment.CenterStart)) }
}

/** R7 §3.7.3: "Only once ▾", 32 epx tall at top 204.2 epx, text cap 11.0 epx (the 15-epx ramp step). */
@Composable
private fun RecurrenceCombo(recurrence: Recurrence, onOpen: () -> Unit) {
    FieldBox(
        CortanaUi.COMBO_TOP_EPX,
        CortanaUi.FIELD_LEFT_EPX,
        null,
        CortanaUi.COMBO_EPX,
        "reminder_page_recurrence",
        onOpen,
        fitToContent = true,
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            FieldText(recurrence.label, Modifier)
            CortanaIcons.Font(
                Glyph.CHEVRON_DOWN,
                CortanaUi.ROW_TITLE_COLOR,
                CortanaUi.APPBAR_GLYPH_EPX * 0.6f,
                Modifier.padding(end = 6.dp).size(12.dp),
            )
        }
    }
}

/** A field's text. R7 §3.7.3 gives no cap top inside a field, so it is centred (approximation, H24). */
@Composable
private fun FieldText(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.padding(start = (CortanaUi.FIELD_TEXT_LEFT_EPX - CortanaUi.FIELD_LEFT_EPX).dp),
        style = ShellType.body.copy(color = CortanaUi.ROW_TITLE_COLOR),
        maxLines = 1,
    )
}

/** R7 §3.7.3: an accent-bordered box, border (55,73,119) over fill (32,37,33). */
@Composable
private fun FieldBox(
    topEpx: Float,
    leftEpx: Float,
    widthEpx: Float?,
    heightEpx: Float,
    tag: String,
    onClick: (() -> Unit)?,
    fitToContent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = Modifier.offset(x = leftEpx.dp, y = topEpx.dp).height(heightEpx.dp)
    val m = when {
        widthEpx != null -> base.width(widthEpx.dp)
        // No width modifier at all: the Box wraps its content, which is R7 §3.7.3's "fitted to its text".
        fitToContent -> base
        else -> base.fillMaxWidth()
    }
    Box(
        m
            .background(CortanaUi.FIELD_FILL)
            .border(CortanaUi.FIELD_BORDER_EPX.dp, CortanaUi.FIELD_BORDER_COLOR)
            .then(if (onClick == null) Modifier else Modifier.pointerInput(tag) { detectTapGestures { onClick() } })
            .testTag(tag)
            .semantics { if (onClick != null) role = Role.Button },
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// The pickers — no R7 source at all (approximation, H25)
// ---------------------------------------------------------------------------------------------

/**
 * A W10M-style spinner: columns of values the user scrolls and taps, with ✓ / ✕ on the app bar. The
 * shell draws its own chrome, so Android's `TimePickerDialog` / `DatePickerDialog` cannot be used —
 * they would bring the platform's Material window into a W10M page. **Nothing in R7 measures this**:
 * the whole look is invented and only the page's own colours and type ramp are reused.
 */
@Composable
private fun SpinnerPicker(
    columns: List<List<String>>,
    selected: List<Int>,
    onDone: (List<Int>?) -> Unit,
) {
    val accent = LocalShellColors.current.accent
    var picks by remember(columns) { mutableStateOf(selected) }
    Box(Modifier.fillMaxSize().background(CortanaUi.PAGE_BG).testTag("cortana_spinner")) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                columns.forEachIndexed { c, values ->
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        values.forEachIndexed { i, value ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(CortanaUi.MENU_ITEM_PITCH_EPX.dp)
                                    .background(if (picks[c] == i) accent else Color.Transparent)
                                    .pointerInput(c, i) {
                                        detectTapGestures { picks = picks.toMutableList().also { it[c] = i } }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(value, style = ShellType.body.copy(color = CortanaUi.ROW_TITLE_COLOR))
                            }
                        }
                    }
                }
            }
            CortanaAppBar(
                labelled = false,
                tag = "cortana_spinner_appbar",
                buttons = listOf(
                    AppBarButton("cortana_spinner_cancel", "cancel", true, { onDone(null) }) { c, m ->
                        CortanaIcons.Font(Glyph.DISMISS, c, CortanaUi.APPBAR_GLYPH_EPX, m)
                    },
                    AppBarButton("cortana_spinner_done", "done", true, { onDone(picks) }) { c, m ->
                        CortanaIcons.Font(Glyph.CHECKMARK, c, CortanaUi.APPBAR_GLYPH_EPX, m)
                    },
                ),
            )
        }
    }
}

@Composable
private fun TimeSpinner(atMs: Long?, onDone: (Long?) -> Unit) {
    val context = LocalContext.current
    val use24 = DateFormat.is24HourFormat(context)
    val cal = remember(atMs) { Calendar.getInstance().apply { timeInMillis = atMs ?: System.currentTimeMillis() } }
    val hours = if (use24) (0..23).map { it.toString() } else (1..12).map { it.toString() }
    val minutes = (0..59).map { it.toString().padStart(2, '0') }
    val meridiem = listOf("AM", "PM")
    val columns = if (use24) listOf(hours, minutes) else listOf(hours, minutes, meridiem)
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val selected = if (use24) {
        listOf(hour24, cal.get(Calendar.MINUTE))
    } else {
        listOf(((hour24 % 12) + 11) % 12, cal.get(Calendar.MINUTE), if (hour24 >= 12) 1 else 0)
    }
    SpinnerPicker(columns, selected) { picks ->
        if (picks == null) { onDone(null); return@SpinnerPicker }
        val h = if (use24) picks[0] else (picks[0] + 1) % 12 + if (picks[2] == 1) 12 else 0
        val out = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, picks[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        onDone(out.timeInMillis)
    }
}

@Composable
private fun DateSpinner(atMs: Long?, onDone: (Long?) -> Unit) {
    val cal = remember(atMs) { Calendar.getInstance().apply { timeInMillis = atMs ?: System.currentTimeMillis() } }
    val thisYear = cal.get(Calendar.YEAR)
    val months = (1..12).map { it.toString() }
    val days = (1..31).map { it.toString() }
    val years = (thisYear..thisYear + 5).map { it.toString() }
    SpinnerPicker(
        listOf(months, days, years),
        listOf(cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH) - 1, 0),
    ) { picks ->
        if (picks == null) { onDone(null); return@SpinnerPicker }
        val out = (cal.clone() as Calendar).apply {
            set(Calendar.YEAR, thisYear + picks[2])
            set(Calendar.MONTH, picks[0])
            set(Calendar.DAY_OF_MONTH, minOf(picks[1] + 1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        onDone(out.timeInMillis)
    }
}

/** The combo's drop-down, in the long-press menu's colours (approximation, H25: R7 never opens one). */
@Composable
private fun OptionList(options: List<String>, selectedIndex: Int, accent: Color, onDone: (Int?) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(options) { detectTapGestures { onDone(null) } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(CortanaUi.MENU_W_EPX.dp)
                .background(CortanaUi.MENU_FILL)
                .border(CortanaUi.MENU_BORDER_EPX.dp, CortanaUi.MENU_BORDER_COLOR)
                .pointerInput(options) { detectTapGestures { } }
                .testTag("cortana_option_list"),
        ) {
            options.forEachIndexed { i, option ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(CortanaUi.MENU_ITEM_PITCH_EPX.dp)
                        .background(if (i == selectedIndex) accent else Color.Transparent)
                        .pointerInput(i) { detectTapGestures { onDone(i) } }
                        .semantics { contentDescription = option },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        option,
                        Modifier.padding(start = CortanaUi.MENU_TEXT_INSET_EPX.dp),
                        style = ShellType.body.copy(color = CortanaUi.ROW_TITLE_COLOR),
                    )
                }
            }
        }
    }
}

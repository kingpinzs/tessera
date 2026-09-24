package app.tileshell.clock

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.AlarmSounds
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.time.ZoneId

/** The top padding that puts a run of text's cap top at [capTop] epx inside its box. */
private fun capPad(capTop: Float, size: Float, line: Float): Dp = CapMetrics.topPaddingForCapTop(capTop, size, line).dp

/** A ticking "now" for the tab's day words, once a minute. */
@Composable
private fun rememberMinuteNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    return now
}

// --- The list (r11/clock.md §2) -------------------------------------------------------------------------------------

/**
 * The Alarm tab: rows at an 88-epx pitch (U3) — time / name / repeat line left, the toggle and its On / Off label
 * right (§2) — or "No alarms" (2.1). Select puts a checkbox on each row and fills the checked rows with accent
 * (R7 §1.3.9); the app bar's Delete removes them (T15-43).
 */
@Composable
fun BoxScope.AlarmTab(nav: ClockNav, store: ClockStore) {
    val alarms by store.alarms.collectAsState()
    val is24h = LocalIs24h.current
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberMinuteNow()
    val today = remember(now) { LocalDate.now(ZoneId.systemDefault()) }
    val sorted = remember(alarms) { alarms.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id })) }
    if (sorted.isEmpty()) {
        EmptyLine("No alarms", "alarm_empty")
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("alarm_list"), contentPadding = PaddingValues(bottom = ClockMetrics.APP_BAR)) {
        items(sorted, key = { it.id }) { a ->
            AlarmRow(
                alarm = a,
                dayLine = ClockText.rowDayLine(a, today, locale),
                timeText = ClockText.time(a.hour, a.minute, is24h, locale),
                select = nav.alarmSelect,
                checked = a.id in nav.alarmSelected,
                onToggle = { on -> store.setAlarmEnabled(a.id, on) },
                onTap = { nav.openAlarmEditor(AlarmDraft.of(a)) },
                onCheck = { c -> nav.alarmSelected = if (c) nav.alarmSelected + a.id else nav.alarmSelected - a.id },
            )
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm, dayLine: String, timeText: String, select: Boolean, checked: Boolean,
    onToggle: (Boolean) -> Unit, onTap: () -> Unit, onCheck: (Boolean) -> Unit,
) {
    val colors = LocalShellColors.current
    // R7 §1.3.9: in Select the content shifts 32 epx right and a checked row is filled with accent, full width.
    val shift = if (select) 32.dp else 0.dp
    Box(
        Modifier.fillMaxWidth().height(88.dp)
            .background(if (select && checked) colors.accent else Color.Transparent)
            .testTag("alarm_row:${alarm.id}")
            .pointerInput(select, checked) { detectTapGestures { if (select) onCheck(!checked) else onTap() } },
    ) {
        if (select) {
            // R7 §1.3.9: the checkbox centred at x 22.2, level with the toggle's centre (2.9: 41.8 below the row top).
            ClockCheckbox(checked, "alarm_check:${alarm.id}", Modifier.offset(x = (22.2f - 10.2f).dp, y = (41.8f - 10.2f).dp)) { onCheck(it) }
        }
        // 2.3: digit height 17.8 epx (≈ 25.4-epx Light), ink x 9.8, cap top 14.3 below the row top.
        BasicText(
            timeText, Modifier.offset(x = 9.8.dp + shift, y = capPad(14.3f, 25.4f, 32f)).testTag("alarm_time:${alarm.id}"),
            style = ShellType.title.copy(fontSize = 25.4.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light, color = colors.text), maxLines = 1,
        )
        // 2.4: the name in semibold body, accent while the alarm is on; cap top 27.5 below the time's.
        BasicText(
            alarm.name, Modifier.offset(x = 9.8.dp + shift, y = capPad(41.8f, 15f, 20f)).testTag("alarm_name:${alarm.id}"),
            style = ShellType.base.copy(color = if (alarm.enabled) colors.accent else colors.text), maxLines = 1,
        )
        // 2.5–2.6: the repeat line in body at ≈ 49 % ink, 20.5 epx under the name.
        BasicText(
            dayLine, Modifier.offset(x = 9.8.dp + shift, y = capPad(62.3f, 15f, 20f)).testTag("alarm_repeat:${alarm.id}"),
            style = ShellType.body.copy(color = colors.text.copy(alpha = 0.49f)), maxLines = 1,
        )
        if (!select) {
            // 2.9: the toggle's right edge 53.1 epx from the screen edge (x 263.1), centre 41.8 below the row top;
            // the state label 13.4 epx after it (x 320.3), cap 10.7 level with the toggle's centre.
            ClockToggle(alarm.enabled, "alarm_toggle:${alarm.id}", Modifier.offset(x = 263.1.dp, y = (41.8f - 9.8f).dp)) { onToggle(it) }
            BasicText(
                if (alarm.enabled) "On" else "Off", Modifier.offset(x = 320.3.dp, y = capPad(41.8f - 5.35f, 15f, 20f)).testTag("alarm_state:${alarm.id}"),
                style = ShellType.body.copy(color = colors.text),
            )
        }
    }
}

// --- The editor (r11/clock.md §3, §4) --------------------------------------------------------------------------------

private enum class EditorFlyout { REPEATS, SOUND, SNOOZE }

/**
 * The 14393 editor (§3): the title, the inline looping time spinner (two columns split at W/2, or three at W/3 with
 * AM / PM under the 12-hour setting — U4), the "In X hours, Y minutes" caption, and four label / value rows at a
 * 64-epx pitch — Alarm name · Repeats · Sound · Snooze time — with the values in accent; the app bar Save, More
 * (+ Delete for an existing alarm, 1.13). Repeats, Sound and Snooze time open §4's flyouts; Sound's links open
 * the music picker and the Sounds page. Every y is the capture's minus its 24-epx status bar, laid under the
 * drawn one (C-17).
 */
@Composable
fun AlarmEditorScreen(nav: ClockNav, store: ClockStore, menu: List<ClockMenuEntry>, onBack: () -> Unit, onWindows: () -> Unit) {
    val draft = nav.alarmDraft
    if (draft == null) {
        nav.page = ClockPage.Tabs
        return
    }
    val colors = LocalShellColors.current
    val context = LocalContext.current
    val is24h = LocalIs24h.current
    val locale = LocalConfiguration.current.locales[0]
    var flyout by remember { mutableStateOf<EditorFlyout?>(null) }
    var editingName by remember { mutableStateOf(false) }
    BackHandler(enabled = flyout != null || editingName) { flyout = null; editingName = false }

    fun update(change: (AlarmDraft) -> AlarmDraft) { nav.alarmDraft = change(draft) }

    fun save() {
        val name = draft.name.trim()
        val existing = draft.id?.let { store.alarm(it) }
        val alarm = if (existing == null) {
            store.newAlarm(draft.hour, draft.minute, name, draft.days, draft.sound, draft.snoozeMinutes)
        } else {
            existing.copy(hour = draft.hour, minute = draft.minute, name = name.ifBlank { Alarm.DEFAULT_NAME }, days = draft.days, sound = draft.sound, snoozeMinutes = draft.snoozeMinutes, enabled = true)
        }
        store.putAlarm(alarm, "alarm ${alarm.id} ${if (existing == null) "created" else "edited"} in the editor")
        Diagnostics.add("clock", "alarm saved ${alarm.id} ${alarm.hour}:${alarm.minute} days=${alarm.days} sound=${alarm.sound.kind} snooze=${alarm.snoozeMinutes}")
        nav.alarmDraft = null
        nav.page = ClockPage.Tabs
    }

    val buttons = buildList {
        add(BarButton(Glyph.SAVE, "Save", "clock_bar:save") { save() })
        if (draft.id != null) add(BarButton(Glyph.DELETE, "Delete", "clock_bar:delete") {
            store.deleteAlarms(listOf(draft.id))
            nav.alarmDraft = null
            nav.page = ClockPage.Tabs
        })
    }

    ClockScaffold(onBack, onWindows, bar = { ClockAppBar(buttons, menu, nav.barExpanded) { nav.barExpanded = it } }) {
        EditorTitle(ClockText.alarmTitle(draft.id != null), "alarm_editor_title")

        // 3.4–3.7: the spinner frame, top rule 40 epx below the status bar, 191 epx tall (5.97 rows), the centre row's
        // accent band at ≈ 0.6 × accent (U13), a divider at W/2 — or two, at W/3, with AM / PM (U4).
        val frameTop = 40.dp
        val frameH = ClockMetrics.SPINNER_ROW * 5.97f
        Box(Modifier.offset(y = frameTop).fillMaxWidth().height(frameH).testTag("alarm_spinner")) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(38, 38, 38)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color(38, 38, 38)))
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(ClockMetrics.SPINNER_ROW).background(colors.accent.copy(alpha = 0.6f)))
            val hours = remember(is24h) { if (is24h) (0..23).map { "%02d".format(it) } else (1..12).map { "%d".format(it) } }
            val minutes = remember { (0..59).map { "%02d".format(it) } }
            val ampm = remember(locale) { DateFormatSymbols.getInstance(locale).amPmStrings.take(2) }
            if (is24h) {
                Row(Modifier.fillMaxSize()) {
                    LoopSpinner(hours, draft.hour, 5.97f, "alarm_spinner:hour", Modifier.weight(1f)) { update { d -> d.copy(hour = it) } }
                    Box(Modifier.width(1.dp).height(frameH).background(Color(40, 40, 40)))
                    LoopSpinner(minutes, draft.minute, 5.97f, "alarm_spinner:minute", Modifier.weight(1f)) { update { d -> d.copy(minute = it) } }
                }
            } else {
                val h12 = if (draft.hour % 12 == 0) 12 else draft.hour % 12
                val pm = draft.hour >= 12
                Row(Modifier.fillMaxSize()) {
                    LoopSpinner(hours, h12 - 1, 5.97f, "alarm_spinner:hour", Modifier.weight(1f)) { i -> update { d -> d.copy(hour = (i + 1) % 12 + if (pm) 12 else 0) } }
                    Box(Modifier.width(1.dp).height(frameH).background(Color(40, 40, 40)))
                    LoopSpinner(minutes, draft.minute, 5.97f, "alarm_spinner:minute", Modifier.weight(1f)) { update { d -> d.copy(minute = it) } }
                    Box(Modifier.width(1.dp).height(frameH).background(Color(40, 40, 40)))
                    LoopSpinner(ampm, if (pm) 1 else 0, 5.97f, "alarm_spinner:ampm", Modifier.weight(1f)) { i -> update { d -> d.copy(hour = d.hour % 12 + if (i == 1) 12 else 0) } }
                }
            }
        }

        // 3.8: "In 5 hours, 57 minutes", centred, body at ≈ 62 % ink, cap top 242.7.
        val now = rememberMinuteNow()
        val caption = remember(draft.hour, draft.minute, draft.days, now) {
            val zone = ZoneId.systemDefault()
            val probe = Alarm("probe", draft.hour, draft.minute, "", draft.days, true, AlarmSound.DEFAULT, 10,
                if (draft.days.isEmpty()) ClockRules.oneShotDate(draft.hour, draft.minute, now, zone) else null, null, null)
            ClockText.countdownCaption((ClockRules.nextTrigger(probe, now, zone) ?: now) - now)
        }
        BasicText(
            caption, Modifier.offset(y = capPad(242.7f, 15f, 20f)).fillMaxWidth().testTag("alarm_editor_caption"),
            style = ShellType.body.copy(color = colors.text.copy(alpha = 0.62f), textAlign = TextAlign.Center),
        )

        // 3.9–3.10: four rows, label cap tops 274.7 · 340.4 · 404.4 · 468.4 (64 pitch), the value in accent 26.6 lower.
        val soundText = when (draft.sound.kind) {
            AlarmSound.Kind.DEFAULT -> "Default"
            AlarmSound.Kind.VIBRATE -> "Vibrate only"
            AlarmSound.Kind.TONE, AlarmSound.Kind.MUSIC -> draft.sound.title ?: AlarmSounds.byUri(draft.sound.uri)?.title ?: "Sound"
        }
        FieldRow(
            "Alarm name", if (editingName) null else draft.name.ifBlank { Alarm.DEFAULT_NAME }, 274.7f, "alarm_editor_field:name",
            extra = {
                if (editingName) {
                    NameField(draft.name, "alarm_editor_name_field", onChange = { update { d -> d.copy(name = it) } }, onDone = { editingName = false })
                }
            },
        ) { editingName = true }
        FieldRow("Repeats", ClockText.repeatSummary(draft.days, locale), 340.4f, "alarm_editor_field:repeats") { flyout = EditorFlyout.REPEATS }
        FieldRow("Sound", soundText, 404.4f, "alarm_editor_field:sound", glyph = if (draft.sound.kind == AlarmSound.Kind.VIBRATE) Glyph.VIBRATE else Glyph.BELL) { flyout = EditorFlyout.SOUND }
        FieldRow("Snooze time", snoozeLabel(draft.snoozeMinutes), 468.4f, "alarm_editor_field:snooze") { flyout = EditorFlyout.SNOOZE }

        when (flyout) {
            EditorFlyout.REPEATS -> DaysFlyout(draft.days, locale, onChange = { update { d -> d.copy(days = it) } }) { flyout = null }
            EditorFlyout.SOUND -> SoundFlyout(
                onVibrate = { update { d -> d.copy(sound = AlarmSound(AlarmSound.Kind.VIBRATE)) }; flyout = null },
                onMusic = { flyout = null; nav.page = ClockPage.MusicPicker },
                onRingtones = { flyout = null; nav.page = ClockPage.Sounds },
            ) { flyout = null }
            EditorFlyout.SNOOZE -> SnoozeFlyout(draft.snoozeMinutes, onPick = { update { d -> d.copy(snoozeMinutes = it) }; flyout = null }) { flyout = null }
            null -> Unit
        }
    }
    @Suppress("UNUSED_EXPRESSION") context
}

/** 3.10: a label in body at ≈ 78 % ink over its value in accent, ink x 10.7; the row (64 epx) is the tap target. */
@Composable
private fun BoxScope.FieldRow(label: String, value: String?, labelCapTop: Float, valueTag: String, glyph: String? = null, extra: @Composable BoxScope.() -> Unit = {}, onTap: () -> Unit) {
    val colors = LocalShellColors.current
    Box(Modifier.offset(y = (labelCapTop - 10f).dp).fillMaxWidth().height(64.dp).pointerInput(onTap) { detectTapGestures { onTap() } }) {
        BasicText(label, Modifier.offset(x = 10.7.dp, y = capPad(10f, 15f, 20f)), style = ShellType.body.copy(color = colors.text.copy(alpha = 0.78f)))
        if (value != null) {
            Row(Modifier.offset(x = 10.7.dp, y = capPad(10f + 26.6f, 15f, 20f))) {
                if (glyph != null) {
                    BasicText(glyph, Modifier.padding(end = 6.dp), style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 15.sp, color = colors.accent))
                }
                BasicText(value, Modifier.testTag(valueTag), style = ShellType.body.copy(color = colors.accent), maxLines = 1)
            }
        }
        extra()
    }
}

/** The name field in place of the value: accent text, done on the keyboard's Done (64 characters at most — the tile face and the API's ceiling). */
@Composable
private fun BoxScope.NameField(value: String, tag: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = LocalShellColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(64).replace("\n", "")) },
        modifier = Modifier.offset(x = 10.7.dp, y = capPad(10f + 26.6f, 15f, 20f)).fillMaxWidth().padding(end = 12.dp).focusRequester(focus).testTag(tag),
        textStyle = ShellType.body.copy(color = colors.accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        cursorBrush = SolidColor(colors.accent),
    )
}

/** 4.1: the days flyout — x 11.6 → 347.1, y 113.8 → 472 (below the status bar); a ✕ top-right; seven 44-epx rows with a 20.4 checkbox 15.2 in. */
@Composable
private fun BoxScope.DaysFlyout(days: Set<java.time.DayOfWeek>, locale: java.util.Locale, onChange: (Set<java.time.DayOfWeek>) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    ClockFlyout(x = 11.6.dp, top = 113.8.dp, width = 335.5.dp, height = 358.2.dp, tag = "alarm_days_flyout", onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            // The close ✕ (11.6-epx glyph) centred at (325.2, 158.7) in the capture → 313.6 in, 20.9 down.
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-8).dp).width(44.dp).height(44.dp).testTag("alarm_days_close").pointerInput(Unit) { detectTapGestures { onDismiss() } }, contentAlignment = Alignment.Center) {
                BasicText(Glyph.DISMISS, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 12.sp, color = Color.White))
            }
        }
        ClockText.weekOrder(locale).forEach { day ->
            val on = day in days
            val id = day.name.lowercase().take(3)
            PressBox(Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).testTag("alarm_day:$id"), onClick = { onChange(if (on) days - day else days + day) }) {
                ClockCheckbox(on, "alarm_day_check:$id", Modifier.align(Alignment.CenterStart).offset(x = 15.2.dp))
                BasicText(ClockText.dayLong(day, locale), Modifier.align(Alignment.CenterStart).offset(x = (15.2f + 20.4f + 12f).dp), style = ShellType.body.copy(color = Color.White))
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") colors
}

/** 4.3: the Sound flyout — x 11.6 → 346.2, y 331.6 → 506.7; "Vibrate only", a separator, then the two accent links at a 48 pitch, ink x 12.6 in. */
@Composable
private fun BoxScope.SoundFlyout(onVibrate: () -> Unit, onMusic: () -> Unit, onRingtones: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    ClockFlyout(x = 11.6.dp, top = 331.6.dp, width = 334.6.dp, height = 175.1.dp, tag = "alarm_sound_flyout", onDismiss = onDismiss) {
        PressBox(Modifier.fillMaxWidth().height(56.8.dp).testTag("alarm_sound:vibrate"), onClick = onVibrate) {
            Row(Modifier.align(Alignment.CenterStart).offset(x = 12.6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(Glyph.VIBRATE, Modifier.padding(end = 8.dp), style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 16.sp, color = Color.White))
                BasicText("Vibrate only", style = ShellType.body.copy(color = Color.White))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ClockMetrics.FLYOUT_BORDER))
        Box(Modifier.height(4.5.dp))
        PressBox(Modifier.fillMaxWidth().height(48.dp).testTag("alarm_sound:music"), onClick = onMusic) {
            BasicText("Pick from my music ›", Modifier.align(Alignment.CenterStart).offset(x = 12.6.dp), style = ShellType.body.copy(color = colors.accent))
        }
        PressBox(Modifier.fillMaxWidth().height(48.dp).testTag("alarm_sound:ringtones"), onClick = onRingtones) {
            BasicText("Pick from ringtones ›", Modifier.align(Alignment.CenterStart).offset(x = 12.6.dp), style = ShellType.body.copy(color = colors.accent))
        }
    }
}

/** 4.4: the Snooze dropdown — x 11.6 → 346.2, y 291.6 → 527.1; five 44-epx items, ink 11.7 in, the selected one at ≈ 0.68 × accent. */
@Composable
private fun BoxScope.SnoozeFlyout(selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    ClockFlyout(x = 11.6.dp, top = 291.6.dp, width = 334.6.dp, height = 235.5.dp, tag = "alarm_snooze_flyout", onDismiss = onDismiss) {
        Box(Modifier.height(8.dp))
        Alarm.SNOOZE_CHOICES.forEach { m ->
            PressBox(
                Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).background(if (m == selected) colors.accent.copy(alpha = 0.68f) else Color.Transparent).testTag("alarm_snooze:$m"),
                onClick = { onPick(m) },
            ) {
                BasicText(snoozeLabel(m), Modifier.align(Alignment.CenterStart).offset(x = 11.7.dp), style = ShellType.body.copy(color = Color.White))
            }
        }
    }
}

// --- The Sounds page (4.6) and the music picker (T15-16) ---------------------------------------------------------------

private data class SoundRow(val id: String, val title: String, val uri: String, val brand: AlarmSounds.Sound?)

/**
 * "Pick from ringtones" (4.6, MEDIUM; T15-43): a shell-drawn page — title "Sounds" in Light at x 24.2, "Use default",
 * a 1-epx rule, then ▷ rows at a 60.4-epx pitch listing the branding module's sound-alikes and the device's
 * `RingtoneManager.TYPE_ALARM` tones, each previewed on the alarm stream while the page shows. A name picks it.
 */
@Composable
fun SoundsScreen(nav: ClockNav, onBack: () -> Unit, onWindows: () -> Unit) {
    val draft = nav.alarmDraft
    if (draft == null) {
        nav.page = ClockPage.Tabs
        return
    }
    val colors = LocalShellColors.current
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<SoundRow>>(AlarmSounds.all.map { SoundRow(it.id, it.title, AlarmSounds.uri(it.id), it) }) }
    LaunchedEffect(Unit) {
        val tones = withContext(Dispatchers.IO) {
            runCatching {
                val rm = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
                val cursor = rm.cursor
                buildList {
                    var i = 0
                    while (cursor.moveToNext()) {
                        val uri = rm.getRingtoneUri(i)
                        add(SoundRow("tone.${uri.lastPathSegment}", cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX), uri.toString(), null))
                        i++
                    }
                }
            }.onFailure { Diagnostics.add("clock", "sounds: ringtones unreadable: $it") }.getOrDefault(emptyList())
        }
        rows = rows + tones
        Diagnostics.add("clock", "sounds page: ${AlarmSounds.all.size} brand sounds + ${tones.size} alarm tones")
    }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.stop() }; player.release() } }
    var playing by remember { mutableStateOf<String?>(null) }
    fun preview(row: SoundRow) {
        runCatching {
            player.reset()
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val uri = row.brand?.let { Uri.fromFile(AlarmSounds.file(context, it)) } ?: Uri.parse(row.uri)
            player.setDataSource(context, uri)
            player.isLooping = false
            player.prepare()
            player.start()
            playing = row.id
            Diagnostics.add("clock", "sounds preview ${row.id}")
        }.onFailure { Diagnostics.add("clock", "sounds preview ${row.id} failed: $it") }
    }
    fun pick(sound: AlarmSound) {
        nav.alarmDraft = draft.copy(sound = sound)
        nav.page = ClockPage.AlarmEditor
    }
    val selectedUri = draft.sound.uri
    ClockScaffold(onBack, onWindows) {
        // 4.6: the title's ascender top 36.4 below the status bar (subheader 34 Light), "Use default" ≈ 24-epx at 109.3, the rule at 161.8.
        BasicText("Sounds", Modifier.offset(x = 24.2.dp, y = 33.8.dp).testTag("sounds_title"), style = ShellType.subheader.copy(color = colors.text))
        PressBox(Modifier.offset(y = 97.dp).fillMaxWidth().height(48.dp).testTag("sounds_default"), onClick = { pick(AlarmSound.DEFAULT) }) {
            BasicText("Use default", Modifier.align(Alignment.CenterStart).offset(x = 24.2.dp), style = ShellType.title.copy(color = if (draft.sound.kind == AlarmSound.Kind.DEFAULT) colors.accent else colors.text))
        }
        Box(Modifier.offset(x = 22.4.dp, y = 161.8.dp).width(313.1.dp).height(1.dp).background(Color(129, 129, 129)))
        LazyColumn(Modifier.offset(y = 170.dp).fillMaxSize().testTag("sounds_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows, key = { it.id }) { row ->
                val selected = row.uri == selectedUri && draft.sound.kind != AlarmSound.Kind.DEFAULT
                Box(Modifier.fillMaxWidth().height(60.4.dp)) {
                    // The outline ▷ at x 28.6 → 41.2; a tap on it previews the sound.
                    Box(Modifier.align(Alignment.CenterStart).offset(x = 22.dp).width(30.dp).height(60.4.dp).testTag("sounds_play:${row.id}").pointerInput(row) { detectTapGestures { preview(row) } }, contentAlignment = Alignment.Center) {
                        BasicText(if (playing == row.id) Glyph.PAUSE else Glyph.PLAY, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 16.sp, color = colors.text))
                    }
                    PressBox(Modifier.align(Alignment.CenterStart).offset(x = 52.dp).fillMaxWidth().height(60.4.dp), onClick = { pick(AlarmSound(AlarmSound.Kind.TONE, row.uri, row.title)) }) {
                        BasicText(row.title, Modifier.align(Alignment.CenterStart).testTag("sounds_row:${row.id}"), style = ShellType.title.copy(color = if (selected) colors.accent else colors.text), maxLines = 1)
                    }
                }
            }
        }
    }
}

private data class MusicRow(val id: Long, val title: String, val artist: String)

/**
 * "Pick from my music" (T15-16, H24 [accept]): a shell-drawn list of MediaStore's music (READ_MEDIA_AUDIO — the
 * Setup `music` row's grant; asked for in place when missing, as Music does), two-line rows at R3 A15's 64 pitch.
 * A row picks the file; a file that later disappears rings the default sound (the ring service's fallback).
 */
@Composable
fun MusicPickerScreen(nav: ClockNav, onBack: () -> Unit, onWindows: () -> Unit) {
    val draft = nav.alarmDraft
    if (draft == null) {
        nav.page = ClockPage.Tabs
        return
    }
    val colors = LocalShellColors.current
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access = granted
        Diagnostics.add("clock", "music picker: audio permission ${if (granted) "granted" else "denied"}")
    }
    var rows by remember { mutableStateOf<List<MusicRow>?>(null) }
    LaunchedEffect(access) {
        if (!access) { rows = emptyList(); return@LaunchedEffect }
        rows = withContext(Dispatchers.IO) {
            runCatching {
                val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
                context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                    buildList { while (c.moveToNext()) add(MusicRow(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")) }
                }.orEmpty()
            }.onFailure { Diagnostics.add("clock", "music picker: query failed: $it") }.getOrDefault(emptyList())
        }
        Diagnostics.add("clock", "music picker: ${rows?.size ?: 0} tracks")
    }
    ClockScaffold(onBack, onWindows) {
        BasicText("Pick from my music", Modifier.offset(x = 24.2.dp, y = 33.8.dp).testTag("alarm_sound_pick_title"), style = ShellType.subheader.copy(color = colors.text))
        Box(Modifier.offset(x = 22.4.dp, y = 97.dp).width(313.1.dp).height(1.dp).background(Color(129, 129, 129)))
        when {
            !access -> Column(Modifier.offset(y = 110.dp).fillMaxWidth().padding(horizontal = 12.dp)) {
                BasicText("Alarms & Clock needs access to your audio files to pick a song.", style = ShellType.body.copy(color = colors.text))
                PressBox(Modifier.fillMaxWidth().height(44.dp).testTag("alarm_sound_grant"), onClick = { grant.launch(Manifest.permission.READ_MEDIA_AUDIO) }) {
                    BasicText("Allow access", Modifier.align(Alignment.CenterStart), style = ShellType.body.copy(color = colors.accent))
                }
            }
            rows?.isEmpty() == true -> EmptyLineR7("No music on this phone", "alarm_sound_pick_empty", capTop = 140f)
            else -> LazyColumn(Modifier.offset(y = 106.dp).fillMaxSize().testTag("alarm_sound_pick_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(rows.orEmpty(), key = { it.id }) { row ->
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, row.id).toString()
                    PressBox(Modifier.fillMaxWidth().height(64.dp), onClick = {
                        nav.alarmDraft = draft.copy(sound = AlarmSound(AlarmSound.Kind.MUSIC, uri, row.title))
                        Diagnostics.add("clock", "music picker: picked ${row.id} \"${row.title}\"")
                        nav.page = ClockPage.AlarmEditor
                    }) {
                        Column(Modifier.align(Alignment.CenterStart).padding(start = 12.dp, end = 12.dp)) {
                            BasicText(row.title, Modifier.testTag("alarm_sound_pick:${row.id}"), style = ShellType.body.copy(color = if (uri == draft.sound.uri) colors.accent else colors.text), maxLines = 1)
                            BasicText(row.artist, style = ShellType.caption.copy(color = colors.subtleText), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

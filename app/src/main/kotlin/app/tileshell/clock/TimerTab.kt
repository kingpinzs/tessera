package app.tileshell.clock

import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay

private fun capPad(capTop: Float, size: Float): Dp = CapMetrics.topPaddingForCapTop(capTop, size).dp

/** `[timer] <id> remaining=<ms> uptime=<ms>` (T15-9): on start, stop, pause, resume, the tab's resume and every 5 s on screen. */
fun logTimer(store: ClockStore, t: ClockTimer) {
    Diagnostics.add("timer", "${t.id} remaining=${store.timerRemaining(t)} uptime=${SystemClock.uptimeMillis()}")
}

/** The timer's digits (6.2): "hh:mm:" and the colons grey (≈ 36 % ink), the seconds in the text colour; heavy weight. */
@Composable
fun timerDigits(text: String, dim: Color, bright: Color): AnnotatedString {
    val (head, tail) = ClockText.splitSeconds(text)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = dim)) { append(head) }
        withStyle(SpanStyle(color = bright)) { append(tail) }
    }
}

/** A "now" on the elapsed clock, refreshed every 200 ms while [running] (the seconds change on time). */
@Composable
fun rememberElapsedNow(running: Boolean): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        now = SystemClock.elapsedRealtime()
        while (running) {
            delay(200L)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

/** The 5-s log loop for every running timer on screen; its first pass is the tab's resume line. */
@Composable
private fun TimerLogLoop(store: ClockStore, timers: List<ClockTimer>) {
    val runningIds = timers.filter { it.state == ClockTimer.State.RUNNING }.map { it.id }
    LaunchedEffect(runningIds) {
        while (true) {
            store.timers.value.filter { it.id in runningIds }.forEach { logTimer(store, it) }
            delay(5_000L)
        }
    }
}

/**
 * The Timer tab (§6): several timers, each a block ≈ 175 epx tall (6.8) — big digits, the control row reset ·
 * ring play/pause · expand at ± 104 epx from the centre (6.3–6.5), the name and the original duration (6.6);
 * idle reset dim, finished play dim (6.7). Select puts a checkbox on each block (T15-43); the app bar's Pin pins the
 * focused timer (T15-16); "No timers" when there is none (approximation).
 */
@Composable
fun BoxScope.TimerTab(nav: ClockNav, store: ClockStore) {
    val timers by store.timers.collectAsState()
    val anyRunning = timers.any { it.state == ClockTimer.State.RUNNING }
    val now = rememberElapsedNow(anyRunning)
    TimerLogLoop(store, timers)
    val list = rememberLazyListState()
    LaunchedEffect(nav.focusedTimer, timers.size) {
        val i = timers.indexOfFirst { it.id == nav.focusedTimer }
        if (i >= 0) list.scrollToItem(i)
    }
    if (timers.isEmpty()) {
        EmptyLine("No timers", "timer_empty")
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("timer_list"), state = list, contentPadding = PaddingValues(bottom = ClockMetrics.APP_BAR)) {
        items(timers, key = { it.id }) { t ->
            @Suppress("UNUSED_VARIABLE") val tick = now
            TimerBlock(
                t = t, remaining = store.timerRemaining(t), select = nav.timerSelect, checked = t.id in nav.timerSelected,
                onCheck = { c -> nav.timerSelected = if (c) nav.timerSelected + t.id else nav.timerSelected - t.id },
                onPlay = { nav.focusedTimer = t.id; togglePlay(store, t) },
                onReset = { nav.focusedTimer = t.id; store.resetTimer(t.id); store.timer(t.id)?.let { logTimer(store, it) }; Diagnostics.add("clock", "timer ${t.id} stop") },
                onExpand = { nav.focusedTimer = t.id; nav.page = ClockPage.TimerExpanded(t.id) },
                onEdit = { nav.focusedTimer = t.id; nav.openTimerEditor(TimerDraft.of(t)) },
            )
        }
    }
}

private fun togglePlay(store: ClockStore, t: ClockTimer) {
    if (t.state == ClockTimer.State.RUNNING) store.pauseTimer(t.id) else store.startTimer(t.id)
    store.timer(t.id)?.let { logTimer(store, it) }
}

@Composable
private fun TimerBlock(
    t: ClockTimer, remaining: Long, select: Boolean, checked: Boolean,
    onCheck: (Boolean) -> Unit, onPlay: () -> Unit, onReset: () -> Unit, onExpand: () -> Unit, onEdit: () -> Unit,
) {
    val colors = LocalShellColors.current
    val running = t.state == ClockTimer.State.RUNNING
    val finished = t.state == ClockTimer.State.IDLE && remaining == t.lengthMs
    Box(
        Modifier.fillMaxWidth().height(175.dp).background(if (select && checked) colors.accent else Color.Transparent).testTag("timer_block:${t.id}")
            .let { m -> if (select) m.pointerInput(checked) { detectTapGestures { onCheck(!checked) } } else m },
    ) {
        if (select) ClockCheckbox(checked, "timer_check:${t.id}", Modifier.offset(x = 12.dp, y = 21.dp))
        // 6.2: digit height 30.2 epx (≈ 43-epx heavy), cap top 21.4 below the block top, centred on the screen.
        BasicText(
            timerDigits(ClockText.hms(remaining), colors.text.copy(alpha = 0.36f), colors.text),
            Modifier.offset(y = capPad(21.4f, 43f)).fillMaxWidth().testTag("timer_remaining:${t.id}"),
            style = ShellType.body.copy(fontSize = 43.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1,
        )
        // 6.3–6.4: the control row's centre 104.5 below the block top; reset at x 75.1, the ring at 179, expand at 282.7.
        GlyphButton(Glyph.RESET, "timer_reset:${t.id}", enabled = !finished && !select, Modifier.offset(x = (75.1f - 22f).dp, y = (104.5f - 22f).dp)) { onReset() }
        RingButton(
            glyph = if (running) Glyph.PAUSE else Glyph.PLAY, tag = "timer_play:${t.id}",
            arcFraction = if (running && t.lengthMs > 0) 1f - remaining.toFloat() / t.lengthMs else null,
            enabled = !select, modifier = Modifier.offset(x = (179f - 29.8f).dp, y = (104.5f - 29.8f).dp),
        ) { onPlay() }
        GlyphButton(Glyph.EXPAND, "timer_expand:${t.id}", enabled = !select, Modifier.offset(x = (282.7f - 22f).dp, y = (104.5f - 22f).dp)) { onExpand() }
        // 6.6: the name (cap 11.5 ≈ 16.4-epx, ≈ 45 % ink) and the duration (semibold, ≈ 58 %), centred; a tap edits.
        Column(
            Modifier.offset(y = capPad(148.5f, 16.4f)).fillMaxWidth().let { m -> if (select) m else m.pointerInput(t.id) { detectTapGestures { onEdit() } } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(t.name.ifBlank { "Timer" }, Modifier.testTag("timer_name:${t.id}"), style = ShellType.body.copy(fontSize = 16.4.sp, color = colors.text.copy(alpha = 0.45f), textAlign = TextAlign.Center), maxLines = 1)
            BasicText(ClockText.hms(t.lengthMs), Modifier.testTag("timer_length:${t.id}"), style = ShellType.base.copy(color = colors.text.copy(alpha = 0.58f), textAlign = TextAlign.Center), maxLines = 1)
        }
    }
}

/**
 * The timer editor (4.8, H27): "NEW TIMER" / "EDIT TIMER" (U2), the spinner over 40 → 295.1 epx below the status
 * bar (eight 32-epx rows) in three W/3 columns with their captions Hours · Minutes · Seconds, then "Timer name";
 * the app bar Save (dim until something changed), Delete for an existing timer, More. A 0:00:00 length is refused
 * (Save stays dim — the phase's edge case).
 */
@Composable
fun TimerEditorScreen(nav: ClockNav, store: ClockStore, menu: List<ClockMenuEntry>, onBack: () -> Unit, onWindows: () -> Unit) {
    val draft = nav.timerDraft
    if (draft == null) {
        nav.page = ClockPage.Tabs
        return
    }
    val colors = LocalShellColors.current
    var editingName by remember { mutableStateOf(false) }
    BackHandler(enabled = editingName) { editingName = false }
    fun update(change: (TimerDraft) -> TimerDraft) { nav.timerDraft = change(draft) }
    val changed = draft.id == null || draft != nav.timerDraftOriginal
    val canSave = changed && draft.lengthMs > 0

    fun save() {
        val name = draft.name.trim()
        if (draft.id == null) {
            val t = store.addTimer(name, draft.lengthMs, start = false)
            Diagnostics.add("clock", "timer created ${t?.id} length=${draft.lengthMs} name=\"$name\"")
        } else {
            store.updateTimer(draft.id, "timer ${draft.id} edited") { ClockRules.resetTimer(it.copy(name = name, lengthMs = draft.lengthMs)) }
            Diagnostics.add("clock", "timer edited ${draft.id} length=${draft.lengthMs} name=\"$name\"")
        }
        nav.timerDraft = null
        nav.page = ClockPage.Tabs
    }

    val buttons = buildList {
        add(BarButton(Glyph.SAVE, "Save", "clock_bar:save", enabled = canSave) { save() })
        if (draft.id != null) add(BarButton(Glyph.DELETE, "Delete", "clock_bar:delete") {
            store.deleteTimers(listOf(draft.id))
            nav.timerDraft = null
            nav.page = ClockPage.Tabs
        })
    }
    ClockScaffold(onBack, onWindows, bar = { ClockAppBar(buttons, menu, nav.barExpanded) { nav.barExpanded = it } }) {
        EditorTitle(ClockText.timerTitle(draft.id != null), "timer_editor_title")
        val frameTop = 40.dp
        val frameH = ClockMetrics.SPINNER_ROW * 7.97f
        Box(Modifier.offset(y = frameTop).fillMaxWidth().height(frameH).testTag("timer_spinner")) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(38, 38, 38)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(Color(38, 38, 38)))
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(ClockMetrics.SPINNER_ROW).background(colors.accent.copy(alpha = 0.6f)))
            val hours = remember { (0..99).map { "%02d".format(it) } }
            val sixty = remember { (0..59).map { "%02d".format(it) } }
            // 4.8: r11 measured the splits at 117.7 and 239.4 epx (columns 117.7 / 121.7 / 120.6), not thirds; each 1-epx
            // divider is centred on its split.
            Row(Modifier.fillMaxSize()) {
                LoopSpinner(hours, draft.hours, 7.97f, "timer_editor_field:hours", Modifier.weight(117.2f)) { update { d -> d.copy(hours = it) } }
                Box(Modifier.width(1.dp).height(frameH).background(Color(40, 40, 40)))
                LoopSpinner(sixty, draft.minutes, 7.97f, "timer_editor_field:minutes", Modifier.weight(120.7f)) { update { d -> d.copy(minutes = it) } }
                Box(Modifier.width(1.dp).height(frameH).background(Color(40, 40, 40)))
                LoopSpinner(sixty, draft.seconds, 7.97f, "timer_editor_field:seconds", Modifier.weight(120.1f)) { update { d -> d.copy(seconds = it) } }
            }
        }
        // 4.8: the column captions at cap top 306.7, ≈ 65 % ink, centred per column.
        Row(Modifier.offset(y = capPad(306.7f, 15f)).fillMaxWidth()) {
            // Each caption centred on its column's span, split to split.
            listOf("Hours" to 117.7f, "Minutes" to 121.7f, "Seconds" to 120.6f).forEach { (c, w) ->
                BasicText(c, Modifier.weight(w), style = ShellType.body.copy(color = colors.text.copy(alpha = 0.65f), textAlign = TextAlign.Center))
            }
        }
        // 4.8: "Timer name" at cap top 350.2, the value in accent at 383.1.
        Box(Modifier.offset(y = (350.2f - 10f).dp).fillMaxWidth().height(64.dp).pointerInput(Unit) { detectTapGestures { editingName = true } }) {
            BasicText("Timer name", Modifier.offset(x = 10.7.dp, y = capPad(10f, 15f)), style = ShellType.body.copy(color = colors.text.copy(alpha = 0.78f)))
            if (editingName) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    value = draft.name,
                    onValueChange = { v -> update { d -> d.copy(name = v.take(64).replace("\n", "")) } },
                    modifier = Modifier.offset(x = 10.7.dp, y = capPad(10f + 32.9f, 15f)).fillMaxWidth().padding(end = 12.dp).focusRequester(focus).testTag("timer_editor_name_field"),
                    textStyle = ShellType.body.copy(color = colors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { editingName = false }),
                    cursorBrush = SolidColor(colors.accent),
                )
            } else {
                BasicText(draft.name.ifBlank { "Timer" }, Modifier.offset(x = 10.7.dp, y = capPad(10f + 32.9f, 15f)).testTag("timer_editor_field:name"), style = ShellType.body.copy(color = colors.accent), maxLines = 1)
            }
        }
    }
}

/**
 * The expanded timer (6.9, U7; H23 [accept]): a full-screen page in the accent colour — the status bar on accent, no
 * tab band, no app bar — larger digits centred at mid-screen (hh dimmed), the name above a bottom control row
 * reset · ring pause · collapse. Its `[timer]` lines keep falling while it shows (E31).
 */
@Composable
fun TimerExpandedScreen(nav: ClockNav, store: ClockStore, id: String, onBack: () -> Unit, onWindows: () -> Unit) {
    val colors = LocalShellColors.current
    val timers by store.timers.collectAsState()
    val t = timers.firstOrNull { it.id == id }
    if (t == null) {
        nav.page = ClockPage.Tabs
        return
    }
    val running = t.state == ClockTimer.State.RUNNING
    val now = rememberElapsedNow(running)
    @Suppress("UNUSED_VARIABLE") val tick = now
    val remaining = store.timerRemaining(t)
    TimerLogLoop(store, listOf(t))
    val finished = t.state == ClockTimer.State.IDLE && remaining == t.lengthMs
    ClockScaffold(onBack, onWindows, background = colors.accent) {
        Box(Modifier.fillMaxSize().testTag("timer_expanded:${t.id}")) {
            BasicText(
                timerDigits(ClockText.hms(remaining), Color.White.copy(alpha = 0.5f), Color.White),
                Modifier.align(Alignment.Center).offset(y = (-40).dp).fillMaxWidth().testTag("timer_remaining:${t.id}"),
                style = ShellType.body.copy(fontSize = 64.5.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1,
            )
            BasicText(t.name.ifBlank { "Timer" }, Modifier.align(Alignment.BottomCenter).offset(y = (-124).dp).testTag("timer_name:${t.id}"),
                style = ShellType.subtitle.copy(color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center), maxLines = 1)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)) {
                GlyphButton(Glyph.RESET, "timer_reset:${t.id}", enabled = !finished, Modifier.align(Alignment.Center).offset(x = (-104).dp), size = 24f) {
                    store.resetTimer(t.id); store.timer(t.id)?.let { logTimer(store, it) }; Diagnostics.add("clock", "timer ${t.id} stop")
                }
                RingButton(if (running) Glyph.PAUSE else Glyph.PLAY, "timer_play:${t.id}", if (running && t.lengthMs > 0) 1f - remaining.toFloat() / t.lengthMs else null, true, Modifier.align(Alignment.Center), scale = 1.5f) { togglePlay(store, t) }
                GlyphButton(Glyph.COLLAPSE, "timer_collapse", true, Modifier.align(Alignment.Center).offset(x = 104.dp), size = 24f) { nav.page = ClockPage.Tabs }
            }
        }
    }
}

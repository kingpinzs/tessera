package app.tileshell.calculator

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calc.convert.ConverterUnit
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType

/** The ease-out every Calculator motion runs on (the shell's flyout / toast curve; r11 measured no curve). */
val CalcEaseOut = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

/** A flyout open over the page: what it lists and where it hangs. */
class CalcFlyoutState(val tag: String, val items: List<CalcFlyoutItem>, val topEpx: Float, val centreXEpx: Float)

data class CalcFlyoutItem(val label: String, val tag: String, val onPick: () -> Unit)

/**
 * The whole app between the drawn bars: the ≡ header (§1), the page showing, the pane (§3) and every flyout,
 * with the drawn W10M bars around them (Bars). The tag `calc_mode:<id>` with `selected="true"` sits on the page
 * showing (Harness contracts); while the pane is open it moves to the pane's rows (E12 reads the pane's order from
 * those nodes), so a dump never holds one id twice.
 */
@Composable
fun CalculatorScreen(
    model: CalcModel,
    requestedPage: CalcPage?,
    onPageShown: () -> Unit,
    onFinish: () -> Unit,
    onHome: () -> Unit,
    clipboardText: () -> String?,
) {
    val colors = LocalShellColors.current
    val density = LocalDensity.current
    LaunchedEffect(requestedPage) {
        if (requestedPage != null) {
            if (requestedPage == CalcPage.CONVERTER) model.showConverterLastUsed() else model.showPage(requestedPage)
            onPageShown()
        }
    }
    var flyout by remember { mutableStateOf<CalcFlyoutState?>(null) }
    var unitPicker by remember { mutableStateOf<Boolean?>(null) } // true: the top row's unit
    var datePicker by remember { mutableStateOf<DatePickerRequest?>(null) }
    var areaTopPx by remember { mutableFloatStateOf(0f) }
    fun toAreaEpx(windowPx: Float): Float = with(density) { (windowPx - areaTopPx).toDp().value }

    fun back() {
        when {
            datePicker != null -> datePicker = null
            unitPicker != null -> unitPicker = null
            flyout != null -> flyout = null
            model.paneOpen -> model.paneOpen = false
            model.aboutOpen -> model.aboutOpen = false
            model.historyOpen -> model.historyOpen = false
            model.memoryOpen -> model.memoryOpen = false
            else -> onFinish()
        }
    }
    BackHandler(enabled = true) { back() }

    val page = model.page
    val tick = model.tick
    val title = when {
        model.aboutOpen -> "About"
        page == CalcPage.CONVERTER -> model.converter.category.label
        else -> page.title
    }.uppercase()

    Box(Modifier.fillMaxSize().background(colors.background).testTag("calc_root")) {
        Column(Modifier.fillMaxSize()) {
            W10mStatusBar()
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { areaTopPx = it.boundsInWindow().top },
            ) {
                val areaW = maxWidth.value
                Column(Modifier.fillMaxSize()) {
                    CalcHeader(
                        title = title,
                        showHistory = !model.aboutOpen && page.hasHistory,
                        historyOn = model.historyOpen,
                        onMenu = { model.paneOpen = true },
                        onHistory = {
                            model.memoryOpen = false
                            model.historyOpen = !model.historyOpen
                        },
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        // The page's own tag, unless the pane is showing (its rows carry the ids then).
                        val pageModifier = if (model.paneOpen || model.aboutOpen) Modifier.fillMaxSize()
                        else Modifier.fillMaxSize().testTag("calc_mode:${page.id}").semantics { selected = true }
                        val host = remember(model) {
                            CalcPageHost(
                                onDisplayHold = { bottomPx, centreXPx ->
                                    flyout = CalcFlyoutState(
                                        tag = "calc_paste_menu",
                                        items = listOf(CalcFlyoutItem("Paste", "calc_paste") { flyout = null; model.paste(clipboardText()) }),
                                        topEpx = toAreaEpx(bottomPx),
                                        centreXEpx = with(density) { centreXPx.toDp().value },
                                    )
                                },
                                onUnitTap = { top -> unitPicker = top },
                                onDatePick = { request -> datePicker = request },
                            )
                        }
                        when {
                            model.aboutOpen -> CalcAboutPage()
                            page == CalcPage.STANDARD || page == CalcPage.SCIENTIFIC -> StandardScientificPage(model, host, pageModifier)
                            page == CalcPage.PROGRAMMER -> ProgrammerPage(model, host, pageModifier)
                            page == CalcPage.CONVERTER -> ConverterPage(model, host, pageModifier)
                            page == CalcPage.DATE -> DatePage(model, host, pageModifier)
                        }
                    }
                }
                if (model.paneOpen) {
                    CalcPane(
                        model = model,
                        onSettings = {
                            model.paneOpen = false
                            model.aboutOpen = true
                        },
                        onDismiss = { model.paneOpen = false },
                    )
                }
                flyout?.let { f ->
                    CalcFlyout(tag = f.tag, items = f.items, topEpx = f.topEpx, centreXEpx = f.centreXEpx, onDismiss = { flyout = null })
                }
                unitPicker?.let { top ->
                    val units = model.converter.units
                    val current = if (top) model.converter.unit1 else model.converter.unit2
                    UnitPickerFlyout(
                        units = units,
                        current = current,
                        maxHeightEpx = maxHeight.value - CalcMetrics.HEADER,
                        onPick = { unit ->
                            unitPicker = null
                            model.converterSelectUnit(top, unit)
                        },
                        onDismiss = { unitPicker = null },
                    )
                }
                datePicker?.let { request ->
                    DatePickerPanel(
                        request = request,
                        widthEpx = areaW,
                        onDone = { datePicker = null },
                    )
                }
            }
            W10mNavBar(onBack = { back() }, onWindows = onHome)
        }
    }
}

/** What a page needs from its host: the paste flyout, the unit pickers and the date pickers. */
class CalcPageHost(
    val onDisplayHold: (bottomWindowPx: Float, centreXWindowPx: Float) -> Unit,
    val onUnitTap: (top: Boolean) -> Unit,
    val onDatePick: (DatePickerRequest) -> Unit,
)

/**
 * §1: the 48-epx header — the ≡ (1.3) at x 13.5–33.5, the title in semibold caps at x 60.5 with an 11-epx cap
 * (1.5) and, in Standard and Scientific only (1.7, T15-42), the 16-epx History glyph 15 epx from the right edge.
 */
@Composable
fun CalcHeader(title: String, showHistory: Boolean, historyOn: Boolean, onMenu: () -> Unit, onHistory: () -> Unit) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(CalcMetrics.HEADER.dp).testTag("calc_header")) {
        MenuGlyphButton(onMenu, Modifier.testTag("calc_menu"))
        CalcTitle(title, Modifier.testTag("calc_title"))
        if (showHistory) {
            // A 48-epx touch cell flush right; the glyph's INK lands 16 × 16 with its right edge 15 epx from the screen's,
            // centred on the header (1.7).
            val cell = CalcMetrics.HEADER
            PressRow(
                onHistory,
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(cell.dp)
                    .testTag("calc_history_toggle")
                    .semantics { role = Role.Button; selected = historyOn },
            ) {
                InkGlyph(
                    code = Glyph.HISTORY,
                    color = if (historyOn) colors.accent else colors.text,
                    inkHeightEpx = CalcMetrics.HISTORY_GLYPH,
                    inkCentreEpx = cell / 2f,
                    inkRightEpx = cell - CalcMetrics.HISTORY_RIGHT_INSET,
                )
            }
        }
    }
}

/**
 * 1.5 / 3.8: the title style — semibold caps at the size that gives r11's "STANDARD" 11.0 epx of ink in the shell's
 * font — placed by ink: the text's own left edge at 60.5, "STANDARD"'s centre 26 epx into the header, or its top at
 * [inkTopEpx] (the pane's CONVERTER row).
 */
@Composable
fun CalcTitle(text: String, modifier: Modifier = Modifier, inkTopEpx: Float? = null) {
    val colors = LocalShellColors.current
    val size = rememberInkFontSize(ShellType.base.fontFamily, FontWeight.SemiBold, CalcMetrics.TITLE_INK_REFERENCE, CalcMetrics.TITLE_INK, colors.text)
    InkText(
        text = text,
        style = ShellType.base.copy(fontSize = size.sp, color = colors.text),
        reference = CalcMetrics.TITLE_INK_REFERENCE,
        modifier = modifier,
        inkLeftEpx = CalcMetrics.TITLE_LEFT,
        inkCentreEpx = if (inkTopEpx == null) CalcMetrics.TITLE_CAP_CENTRE else null,
        inkTopEpx = inkTopEpx,
    )
}

/** 1.3: three bars 20 epx long and 1.25 thick at a 5-epx pitch, x 13.5–33.5, the top bar 19 epx into the header, in a 48-epx cell. */
@Composable
fun MenuGlyphButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalShellColors.current
    PressRow(onClick, modifier.size(CalcMetrics.MENU_TOUCH.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val thick = CalcMetrics.MENU_BAR_THICK * d
            repeat(3) { i ->
                drawRect(
                    colors.text,
                    Offset(CalcMetrics.MENU_LEFT * d, (CalcMetrics.MENU_BARS_TOP + i * CalcMetrics.MENU_BAR_PITCH) * d - thick / 2f),
                    Size(CalcMetrics.MENU_BAR_LENGTH * d, thick),
                )
            }
        }
    }
}

/**
 * §3: the hamburger pane. 256 epx wide (3.1), #2B2B2B (3.2), from the status bar's bottom to the nav bar's top,
 * covering the header with its own ≡ and "CALCULATOR"; 48-epx rows from the header's bottom (3.4–3.5) — Standard,
 * Scientific, Programmer, Date calculation, CONVERTER, the twelve categories (3.9) — scrolling under the bottom
 * rule (3.10–3.11) with Settings pinned in the last 48 epx (3.12). The selected row is the accent at 60 % over the
 * pane (3.7); the page behind is not dimmed (3.3). It slides in from the left in ≈167 ms (M.1) on the shell's
 * clock: `[motion] calc_pane`.
 */
@Composable
fun CalcPane(model: CalcModel, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    var slide by remember { mutableFloatStateOf(-CalcMetrics.PANE_WIDTH) }
    LaunchedEffect(Unit) {
        MotionClock.animate("calc_pane", CalcMetrics.PANE_SLIDE_MS, CalcEaseOut) { slide = -CalcMetrics.PANE_WIDTH * (1f - it) }
    }
    val selectedFill = colors.accent.copy(alpha = CalcMetrics.PANE_SELECTED_ALPHA).compositeOverPane()
    Box(Modifier.fillMaxSize()) {
        // 3.3: the page beside the pane is not dimmed; a tap on it closes the pane (light dismiss).
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
        BoxWithConstraints(
            Modifier
                .offset(x = slide.dp)
                .width(CalcMetrics.PANE_WIDTH.dp)
                .fillMaxHeight()
                .background(CalcMetrics.PANE_FILL)
                .pointerInput(Unit) { detectTapGestures { } }
                .testTag("calc_pane"),
        ) {
            val h = maxHeight.value
            MenuGlyphButton(onDismiss, Modifier.testTag("calc_pane_menu"))
            CalcTitle("CALCULATOR", Modifier.testTag("calc_pane_title"))
            val listHeight = (h - CalcMetrics.HEADER - CalcMetrics.PANE_ROW).coerceAtLeast(0f)
            Column(
                Modifier
                    .offset(y = CalcMetrics.HEADER.dp)
                    .fillMaxWidth()
                    .height(listHeight.dp)
                    .clipToBounds()
                    .verticalScroll(rememberScrollState())
                    .testTag("calc_pane_list"),
            ) {
                var categoryIndex = 0
                CalcLayout.PANE_ROWS.forEach { row ->
                    when (row) {
                        is PaneRow.Mode -> PaneItem(
                            label = row.label,
                            tag = "calc_mode:${row.page.id}",
                            selected = model.page == row.page,
                            selectedFill = selectedFill,
                            onClick = { model.showPage(row.page) },
                        )
                        PaneRow.ConverterGroup -> Box(
                            Modifier
                                .fillMaxWidth()
                                .height(CalcMetrics.PANE_ROW.dp)
                                .testTag("calc_mode:converter")
                                .semantics { selected = model.page == CalcPage.CONVERTER; text = AnnotatedString(row.label) },
                        ) {
                            // 3.8: the group header in the title style — semibold caps, cap 11, at x 60.5.
                            CalcTitle(row.label, inkTopEpx = CalcMetrics.PANE_LABEL_CAP_TOP)
                        }
                        is PaneRow.Category -> {
                            categoryIndex++
                            PaneItem(
                                label = row.label,
                                tag = "calc_converter_category:$categoryIndex",
                                selected = model.page == CalcPage.CONVERTER && model.converter.category == row.category,
                                selectedFill = selectedFill,
                                onClick = { model.showConverter(row.category) },
                            )
                        }
                    }
                }
            }
            // 3.11–3.12: the rule 48 epx above the nav bar, inset 12; Settings under it, the gear at x 14 centred on the row.
            val settingsTop = h - CalcMetrics.PANE_ROW
            Box(
                Modifier
                    .offset(x = CalcMetrics.PANE_RULE_INSET.dp, y = settingsTop.dp)
                    .width((CalcMetrics.PANE_WIDTH - 2 * CalcMetrics.PANE_RULE_INSET).dp)
                    .height(CalcMetrics.RULE.dp)
                    .background(CalcMetrics.PANE_RULE_COLOR),
            )
            PressRow(
                onSettings,
                Modifier
                    .offset(y = settingsTop.dp)
                    .fillMaxWidth()
                    .height(CalcMetrics.PANE_ROW.dp)
                    .testTag("calc_pane_settings")
                    .semantics { role = Role.Button },
            ) {
                // 3.12: the gear's ink 20 tall, centred in r11's 20-wide slot at x 14–34 (the shipped glyph is narrower than square).
                InkGlyph(
                    code = Glyph.SETTINGS,
                    color = colors.text,
                    inkHeightEpx = CalcMetrics.PANE_GEAR,
                    inkCentreEpx = CalcMetrics.PANE_ROW / 2f,
                    inkCentreXEpx = CalcMetrics.PANE_GEAR_LEFT + CalcMetrics.PANE_GEAR / 2f,
                )
                PaneLabel("Settings", CalcMetrics.PANE_SETTINGS_LABEL_LEFT)
            }
        }
    }
}

/** 3.7: 0.6 × accent + 0.4 × (43,43,43), which reproduces both measured fills. */
private fun Color.compositeOverPane(): Color {
    val a = alpha
    val p = CalcMetrics.PANE_FILL
    return Color(red * a + p.red * (1 - a), green * a + p.green * (1 - a), blue * a + p.blue * (1 - a), 1f)
}

/**
 * One pane row (3.4–3.7): a 48-epx fill across the pane, the label at x 60 with its cap top 19 epx below the row top.
 * The tagged row carries the label as its own text (Harness contracts: never a parent whose text lives in its children).
 */
@Composable
private fun PaneItem(label: String, tag: String, selected: Boolean, selectedFill: Color, onClick: () -> Unit) {
    PressRow(
        onClick,
        Modifier
            .fillMaxWidth()
            .height(CalcMetrics.PANE_ROW.dp)
            .background(if (selected) selectedFill else Color.Transparent)
            .testTag(tag)
            .semantics { role = Role.Tab; this.selected = selected; text = AnnotatedString(label) },
    ) {
        PaneLabel(label, CalcMetrics.PANE_LABEL_LEFT)
    }
}

/** 3.6: a label in body type, its box at [leftEpx] (r11's origin) and its cap top — a flat capital's measured ink — 19 epx below the row top. */
@Composable
private fun PaneLabel(text: String, leftEpx: Float) {
    val colors = LocalShellColors.current
    InkText(
        text = text,
        style = ShellType.body.copy(color = colors.text),
        reference = CalcMetrics.PANE_LABEL_CAP_REFERENCE,
        leftEpx = leftEpx,
        inkTopEpx = CalcMetrics.PANE_LABEL_CAP_TOP,
    )
}

/**
 * The pane's Settings (3.12, T15-43): an About page (U13's block — name, version, privacy), with no Feedback
 * (Microsoft's online service, nothing offline to send it to). Back returns to the page that was showing.
 */
@Composable
fun CalcAboutPage() {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    Column(Modifier.fillMaxSize().testTag("about_page").padding(12.dp)) {
        BasicText("Calculator", style = ShellType.subtitle.copy(color = colors.text), modifier = Modifier.testTag("about_name"))
        BasicText(Brand.PRODUCT_NAME, style = ShellType.body.copy(color = colors.subtleText))
        BasicText("Version ${version ?: "unknown"}", style = ShellType.body.copy(color = colors.subtleText), modifier = Modifier.testTag("about_version"))
        Spacer(Modifier.height(18.dp))
        BasicText("Privacy", style = ShellType.base.copy(color = colors.text))
        BasicText(
            "Every answer is computed on this phone. Calculator sends nothing anywhere and makes no network request; its memory and history stay in the app's own storage until you clear them.",
            style = ShellType.body.copy(color = colors.subtleText),
            modifier = Modifier.testTag("about_privacy"),
        )
        Spacer(Modifier.height(18.dp))
        BasicText("Engine", style = ShellType.base.copy(color = colors.text))
        BasicText(
            "Windows Calculator's engine (microsoft/calculator, MIT), ported.",
            style = ShellType.body.copy(color = colors.subtleText),
            modifier = Modifier.testTag("about_engine"),
        )
    }
}

/**
 * The W10M flyout (R7 §2.2.5 width, items and inset; §3.6.2 fill, border and padding) over a scrim that closes it
 * on any tap outside, growing down from [topEpx] over 233 ms ease-out (§3.6.4) on the shell's clock: `[motion] flyout`.
 */
@Composable
fun CalcFlyout(tag: String, items: List<CalcFlyoutItem>, topEpx: Float, centreXEpx: Float, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    val height = CalcMetrics.FLYOUT_PAD * 2 + items.size * CalcMetrics.FLYOUT_ITEM + 2f
    var grow by remember(tag) { mutableFloatStateOf(0.5f) }
    LaunchedEffect(tag) { MotionClock.animate("flyout", CalcMetrics.FLYOUT_GROW_MS, CalcEaseOut) { grow = 0.5f + 0.5f * it } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("${tag}_scrim")
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onDismiss()
                }
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val left = (centreXEpx - CalcMetrics.FLYOUT_W / 2f).coerceIn(0f, (w - CalcMetrics.FLYOUT_W).coerceAtLeast(0f))
        val top = topEpx.coerceIn(0f, (h - height).coerceAtLeast(0f))
        Box(
            Modifier
                .offset(x = left.dp, y = top.dp)
                .width(CalcMetrics.FLYOUT_W.dp)
                .height((height * grow).dp)
                .clipToBounds()
                .background(CalcMetrics.FLYOUT_FILL)
                .border(1.dp, CalcMetrics.FLYOUT_BORDER)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                .testTag(tag),
        ) {
            Column(Modifier.padding(top = (1f + CalcMetrics.FLYOUT_PAD).dp)) {
                items.forEach { item ->
                    PressRow(
                        onClick = item.onPick,
                        modifier = Modifier.fillMaxWidth().height(CalcMetrics.FLYOUT_ITEM.dp).testTag(item.tag).semantics { role = Role.Button },
                    ) {
                        BasicText(
                            item.label,
                            style = ShellType.body.copy(color = colors.text),
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = CalcMetrics.FLYOUT_INSET.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5.7: the unit picker behind a unit label — the category's units (whimsicals excluded, `ConverterState.units`)
 * as a scrolling flyout list in the same form, the current unit in accent. Each row carries `calc_unit:<id>`
 * (Windows' unit id) and reads the unit's en-US name.
 */
@Composable
fun UnitPickerFlyout(units: List<ConverterUnit>, current: ConverterUnit, maxHeightEpx: Float, onPick: (ConverterUnit) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalShellColors.current
    val full = CalcMetrics.FLYOUT_PAD * 2 + units.size * CalcMetrics.FLYOUT_ITEM + 2f
    val height = full.coerceAtMost(maxHeightEpx)
    var grow by remember { mutableFloatStateOf(0.5f) }
    LaunchedEffect(Unit) { MotionClock.animate("flyout", CalcMetrics.FLYOUT_GROW_MS, CalcEaseOut) { grow = 0.5f + 0.5f * it } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("calc_unit_picker_scrim")
            .pointerInput(onDismiss) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (waitForUpOrCancellation() != null) onDismiss()
                }
            },
    ) {
        val w = maxWidth.value
        Box(
            Modifier
                .offset(x = ((w - CalcMetrics.FLYOUT_W) / 2f).coerceAtLeast(0f).dp, y = CalcMetrics.HEADER.dp)
                .width(CalcMetrics.FLYOUT_W.dp)
                .height((height * grow).dp)
                .clipToBounds()
                .background(CalcMetrics.FLYOUT_FILL)
                .border(1.dp, CalcMetrics.FLYOUT_BORDER)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
                .testTag("calc_unit_picker"),
        ) {
            Column(Modifier.fillMaxSize().padding(top = (1f + CalcMetrics.FLYOUT_PAD).dp).verticalScroll(rememberScrollState())) {
                units.forEach { unit ->
                    PressRow(
                        onClick = { onPick(unit) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CalcMetrics.FLYOUT_ITEM.dp)
                            .testTag("calc_unit:${unit.id}")
                            .semantics { role = Role.Button; selected = unit == current; text = AnnotatedString(unit.name) },
                    ) {
                        BasicText(
                            unit.name,
                            style = ShellType.body.copy(color = if (unit == current) colors.accent else colors.text, fontWeight = if (unit == current) FontWeight.SemiBold else FontWeight.Normal),
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = CalcMetrics.FLYOUT_INSET.dp),
                        )
                    }
                }
            }
        }
    }
}

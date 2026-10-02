package app.tileshell.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.clock.BarButton
import app.tileshell.clock.ClockMetrics
import app.tileshell.clock.DIM_INK
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Calendar app's measured values (r11/calendar.md; each cites its row). Vertical values R11 measured from the
 * screen top under a 24-epx status bar are written here from the drawn status bar's bottom edge (the Acceptance
 * preamble: asserted relative to it), so `BarMetrics.STATUS_EPX` is never written as a number.
 */
object CalMetrics {
    /** K1.3: the page is #1A1A1A, not black. */
    val PAGE = Color(0xFF1A1A1A)
    /** K2.2 / K3.4 / K4.2: the grey of day names, "No events today" and week-cell labels. */
    val GREY = Color(151, 151, 151)
    /** K1.4: week-view cell rules, the month panel's border, the app bar's top edge. */
    val RULE = Color(80, 80, 80)
    /** K5.3: other-month dates. */
    val OTHER_MONTH = Color(110, 110, 110)

    /** K1.2: the header band, 40 epx under the status bar. */
    val HEADER = 40.dp
    /** K1.1: ≡ — three 1-epx bars at a 5-epx pitch, x 16–36, centred 20 epx below the status bar. */
    const val MENU_X = 16f
    const val MENU_W = 20f
    const val MENU_CY = 20f
    /** K1.1: the month and year, semibold caps, cap 11.0 (a 15.7-epx size on the shell's font), left 51.0, cap top 15.5. */
    const val TITLE_X = 51f
    const val TITLE_CAP_TOP = 15.5f
    const val TITLE_SIZE = 15.7f

    /** K1.5: the app bar, 47 epx, fill (33,33,33), a 1-epx (80,80,80) top edge. */
    val BAR = 47.dp
    val BAR_FILL = Color(33, 33, 33)
    /** K1.6, the 15063 form: the standard CommandBar — buttons at a 68-epx pitch beside a 48-epx "…" flush right. */
    val BUTTON_PITCH = 68.dp
    val MORE_W = 48.dp
    /** The bar with its labels showing while "…" is open (R7 §3.5.8's expanded bar). */
    val BAR_EXPANDED = 68.dp
    /** K6.2 / K6.3: the View list and the "…" menu — rows ≈ 45 epx, an 18-epx glyph at x 23, text at x 60, fill (70,70,70). */
    val MENU_ROW = 44.8.dp
    val MENU_FILL = Color(70, 70, 70)
    const val MENU_GLYPH_X = 23.2f
    const val MENU_TEXT_X = 60f

    /**
     * K2.1: the week strip's columns, proportional to the width — centres 25.0 … 331.3 on 360 (a 51.05-epx pitch from
     * 25.0). The month panel shares them (K5.2).
     */
    const val COLUMN_FIRST_CENTRE = 25.0f
    const val COLUMN_PITCH = 51.05f
    fun columnCentre(i: Int): Float = COLUMN_FIRST_CENTRE + COLUMN_PITCH * i

    /** K2.2: day names, cap top 75.3 from the screen top under a 24-epx bar → 51.3 below the status bar. */
    const val DAY_NAMES_CAP_TOP = 51.3f
    /** K2.4 / K2.5, the 15063 form (LOW): two week rows, the selected day a 32-epx accent square; the strip ends ≈ 164 below the bar. */
    const val STRIP_ROW_CENTRE = 97.4f
    const val STRIP_ROW_PITCH = 41f
    const val STRIP_BOTTOM = 164f
    val SELECTED = 32.dp

    /** K3.2–K3.8: Agenda. */
    const val HEADING_X = 24f
    const val LABEL_X = 24.3f
    const val EVENT_TITLE_X = 92.5f

    /**
     * r11/calendar.md's x values are where the INK of its sample text starts (half-level crossings in its captures); a
     * glyph's box starts one side bearing earlier. These are the bearings of R11's own samples at these sizes, read on
     * this build (QA defect D-E19-1): the box goes that much left, so the sample's ink lands on R11's x.
     */
    const val EVENT_TITLE_BEARING = 1.7f   // the "P" of "Pay rent" at the title's size
    const val PANE_CHEVRON_BEARING = 2.3f  // the collapse chevron glyph at 12 epx
    const val PANE_NAME_BEARING = 1.3f     // the "M" of "MoNa Events" / the "m" of "mark guim" at the name's size
    val EVENT_BAR_W = 8.dp
    val ALL_DAY_BAR = 40.dp
    val ALL_DAY_PITCH = 44.dp
    val TIMED_BAR = 56.dp
    val TIMED_PITCH = 60.dp
    /** C1 083: a group's heading sits 16 epx (cap top) under its rule and its first bar 40 epx under it; 12 epx clear under the last bar. */
    val GROUP_HEAD = 40.dp
    val GROUP_FOOT = 8.dp

    /** K4.1–K4.3: the Week view. */
    val WEEK_CELL = 120.dp
    const val WEEK_LABEL_X = 10f
    const val WEEK_LABEL_CAP_TOP = 11f
    const val WEEK_LINE_PITCH = 19f
    const val WEEK_FIRST_LINE = 29f

    /** K5.1–K5.2: the month panel — x 5–355, 235 epx from the header's bottom, six rows at a 34.25-epx pitch. */
    val MONTH_X = 5.dp
    val MONTH_W = 350.dp
    val MONTH_H = 235.dp
    /** K5.2's rows are cap tops from the screen top (76.25 the day names, 106.25 the first dates) under the header's bottom at 64: 12.25, and a first row centred 47 below the panel's top. */
    const val MONTH_NAMES_CAP_TOP = 12.25f
    const val MONTH_FIRST_ROW_CENTRE = 47f
    const val MONTH_ROW_PITCH = 34.25f
    /** r11 U8 → R7 §2.2.6: the panel grows from its top edge in 200 ms, ease-out (tagged approximation, H17). */
    const val MONTH_MS = 200

    /** K6.4 on U10's dark chrome: the ≡ pane — rows 48.1 epx, account headers at x 14, calendar names at x 62. */
    val PANE_FILL = Color(0xFF1F1F1F)
    val PANE_ROW = 48.1.dp
    const val PANE_ACCOUNT_X = 14f
    const val PANE_NAME_X = 62f

    /** U1: the Day view's hour rows (settings-front.md 3.3's 48-epx row). */
    val HOUR = 48.dp
    val HOUR_GUTTER = 56.dp

    /** r11/people.md P4.x, which U3 proposes for the editor: x 12 → W − 12, label cap top → box top 22.75, 1-epx (103,103,103) group rules. */
    val FIELD_X = 12.dp
    const val FIELD_LABEL_TO_BOX = 22.75f
    val GROUP_RULE = Color(103, 103, 103)
}

/**
 * Code points of the shell's icon font (Fluent UI System Icons resizable, MIT; read from the shipped TTF's cmap) that
 * only the Calendar app draws. Each stands in for the Segoe MDL2 glyph r11/calendar.md §9 names.
 */
object CalGlyph {
    const val CHEVRON_UP = "\ue490"        // ChevronUp E70E — the header while the month panel is open, a pane group
    const val TODAY = "\ue388"             // "Today" — a calendar with an arrow (MDL2: CalendarReply E8F5 the nearest name)
    const val AGENDA = "\ue334"            // BulletedList E8FD — View > Agenda
    const val DAY = "\ue350"               // CalendarDay E8BF — View > Day
    const val WEEK = "\ue330"              // CalendarWeek E8C0 — the View button, View > Week (the font's day-columns calendar)
}

/**
 * Text placed by its cap top, [capTop] epx below the parent's top, at [x]. The offset comes first, so the caller's tag
 * reports the box the text is drawn in, not where it would sit unplaced.
 */
@Composable
fun CapText(text: String, x: Float, capTop: Float, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(
        text,
        Modifier.offset(x = x.dp, y = CapMetrics.topPaddingForCapTop(capTop, style.fontSize.value).dp).then(modifier),
        style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

fun glyphStyle(size: Float, color: Color) = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = size.sp, color = color, textAlign = TextAlign.Center)

/** A calendar's colour as it is drawn: its own, or the accent when the provider holds none (Edge cases). */
@Composable
fun colorOf(argb: Int?): Color = if (argb == null) LocalShellColors.current.accent else Color(argb or (0xFF shl 24))

@Composable
fun tintOf(argb: Int?): Color {
    val accent = LocalShellColors.current.accent
    val base = argb ?: (0xFF shl 24 or ((accent.red * 255).toInt() shl 16) or ((accent.green * 255).toInt() shl 8) or (accent.blue * 255).toInt())
    return Color(EventRules.tint(base))
}

/** Times and dates as the views write them (r11/calendar.md §10), in the phone's 12 / 24-hour form and locale. */
object CalText {
    fun time(t: LocalDateTime, is24h: Boolean, locale: Locale): String =
        DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a", locale).format(t)

    /** "NOVEMBER 2015". */
    fun monthTitle(day: LocalDate, locale: Locale): String = DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(day).uppercase(locale)

    /** "Saturday 12". */
    fun dayHeading(day: LocalDate, locale: Locale): String = DateTimeFormatter.ofPattern("EEEE d", locale).format(day)

    /** "23 MON". */
    fun weekLabel(day: LocalDate, locale: Locale): String = "${day.dayOfMonth} ${shortDay(day.dayOfWeek, locale).uppercase(locale)}"

    fun shortDay(day: DayOfWeek, locale: Locale): String = day.getDisplayName(java.time.format.TextStyle.SHORT, locale).trimEnd('.')

    /** "Thu 1 Oct 2026". */
    fun date(day: LocalDate, locale: Locale): String = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale).format(day)

    fun iso(day: LocalDate): String = day.toString()
}

/** K1.1's ≡: three 1-epx bars at a 5-epx pitch, 20 epx wide. */
@Composable
fun MenuBars(modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(modifier.size(CalMetrics.MENU_W.dp, 11.dp).drawBehind {
        val bar = 1.dp.toPx()
        for (i in 0..2) drawRect(color, topLeft = Offset(0f, i * 5.dp.toPx()), size = Size(size.width, bar))
    })
}

/**
 * The header band (K1.1–K1.2): ≡ opening the calendar pane (`cal_menu`), then the month and year in semibold caps
 * with ⌄ — ⌃ while the month panel is open — opening that panel (`cal_header`; the text is `cal_month_title`).
 */
@Composable
fun CalHeader(title: String, monthOpen: Boolean, onMenu: () -> Unit, onTitle: () -> Unit) {
    val style = ShellType.base.copy(fontSize = CalMetrics.TITLE_SIZE.sp, color = Color.White)
    Box(Modifier.fillMaxWidth().height(CalMetrics.HEADER).background(CalMetrics.PAGE).testTag("cal_header_band")) {
        Box(
            Modifier.width(48.dp).fillMaxHeight().testTag("cal_menu")
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onMenu),
        ) {
            MenuBars(Modifier.offset(x = CalMetrics.MENU_X.dp, y = (CalMetrics.MENU_CY - 5.5f).dp).testTag("cal_menu_glyph"))
        }
        Row(
            Modifier.offset(x = CalMetrics.TITLE_X.dp).fillMaxHeight().testTag("cal_header")
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTitle),
        ) {
            BasicText(
                title,
                Modifier.padding(top = CapMetrics.topPaddingForCapTop(CalMetrics.TITLE_CAP_TOP, CalMetrics.TITLE_SIZE).dp).testTag("cal_month_title"),
                style = style, maxLines = 1,
            )
            BasicText(
                if (monthOpen) CalGlyph.CHEVRON_UP else Glyph.CHEVRON_DOWN,
                Modifier.padding(start = 6.dp, top = 14.dp).testTag("cal_header_chevron"),
                style = glyphStyle(12f, Color.White),
            )
        }
    }
}

/**
 * A row or a button that lightens while pressed (X19's press, [ROW_PRESS_ALPHA]) and fires on the up — the Clock app's
 * PressBox in look. Its gesture is keyed on nothing and reads the callback as it is at the up: a page that recomposes
 * under a finger (the provider changed, a list was read again) hands a row a new callback, and a gesture keyed on the
 * callback would restart there and lose the tap.
 */
@Composable
fun CalPress(modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown().consume()
                pressed = true
                val up = waitForUpOrCancellation()
                pressed = false
                if (up != null) { up.consume(); click() }
            }
        }.background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent),
        content = content,
    )
}

/** One row of the View list or the "…" menu. */
data class CalMenuEntry(val glyph: String?, val label: String, val tag: String, val selected: Boolean = false, val onPick: () -> Unit)

/** Which list stands open above the app bar. */
enum class BarMenu { NONE, VIEW, MORE }

/**
 * The app bar (K1.5–K1.6): 47 epx in (33,33,33) with a 1-epx (80,80,80) top edge, its buttons on the CommandBar's 68-epx
 * pitch beside "…" flush right. "…" expands the bar — the labels show — and raises the overflow menu; the View button
 * raises the View list (K6.2: "the app bar expands upward into a list"). Either list grows from the bar on the motion
 * clock, and a tap anywhere else, or Back, closes it.
 */
@Composable
fun BoxScope.CalAppBar(
    buttons: List<BarButton>,
    more: List<CalMenuEntry>,
    view: List<CalMenuEntry>,
    menu: () -> BarMenu,
    onMenu: (BarMenu) -> Unit,
) {
    val open = menu()
    val expanded = open == BarMenu.MORE
    val height = if (expanded) CalMetrics.BAR_EXPANDED else CalMetrics.BAR
    OverlayLayer(active = { menu() != BarMenu.NONE }) {
        Box(Modifier.fillMaxSize()) {
            if (open != BarMenu.NONE) {
                Box(Modifier.fillMaxSize().testTag("cal_bar_scrim").pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(); if (waitForUpOrCancellation() != null) onMenu(BarMenu.NONE) }
                })
                val entries = if (open == BarMenu.VIEW) view else more
                var grow by remember(open) { mutableFloatStateOf(0f) }
                LaunchedEffect(open) { MotionClock.animate("appbar_menu", ClockMetrics.FLYOUT_MS, ClockMetrics.easeOut) { grow = it } }
                Column(
                    Modifier.align(Alignment.BottomStart).offset(y = -height).fillMaxWidth().height(CalMetrics.MENU_ROW * entries.size * grow)
                        .clipToBounds().background(CalMetrics.MENU_FILL).testTag(if (open == BarMenu.VIEW) "cal_view_menu" else "cal_more_menu"),
                ) {
                    entries.forEach { entry ->
                        CalPress(
                            Modifier.fillMaxWidth().height(CalMetrics.MENU_ROW).testTag(entry.tag).semantics { role = Role.Tab; selected = entry.selected },
                            onClick = { onMenu(BarMenu.NONE); entry.onPick() },
                        ) {
                            if (entry.glyph != null) {
                                BasicText(entry.glyph, Modifier.align(Alignment.CenterStart).offset(x = CalMetrics.MENU_GLYPH_X.dp), style = glyphStyle(18f, Color.White))
                            }
                            BasicText(
                                entry.label, Modifier.align(Alignment.CenterStart).offset(x = CalMetrics.MENU_TEXT_X.dp),
                                style = ShellType.body.copy(color = Color.White, fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal),
                            )
                        }
                    }
                }
            }
        }
    }
    Box(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(height).background(CalMetrics.BAR_FILL)
            .drawBehind { drawRect(CalMetrics.RULE, size = Size(size.width, 1.dp.toPx())) }
            .testTag("cal_app_bar"),
    ) {
        // The bar takes the touches that land on its blank parts, so a row under it is never tapped through it (L13-14).
        OverlayLayer(active = { menu() == BarMenu.NONE }, Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } })
        }
        Row(Modifier.align(Alignment.TopEnd).height(height)) {
            buttons.forEach { b ->
                CalBarButton(b.glyph, if (expanded) b.label else null, b.tag, b.enabled, CalMetrics.BUTTON_PITCH) { b.onClick() }
            }
            CalBarButton(Glyph.MORE_HORIZONTAL, null, "cal_bar:more", true, CalMetrics.MORE_W) { onMenu(if (open == BarMenu.MORE) BarMenu.NONE else BarMenu.MORE) }
        }
    }
}

@Composable
private fun CalBarButton(glyph: String, label: String?, tag: String, enabled: Boolean, width: Dp, onClick: () -> Unit) {
    val color = if (enabled) Color.White else Color.White.copy(alpha = DIM_INK)
    Box(
        Modifier.width(width).fillMaxHeight().testTag(tag)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
    ) {
        // The glyph keeps its place in the 47-epx bar whether or not the labels show under it.
        Box(Modifier.fillMaxWidth().height(CalMetrics.BAR), contentAlignment = Alignment.Center) { BasicText(glyph, style = glyphStyle(20f, color)) }
        if (label != null) {
            BasicText(label, Modifier.align(Alignment.TopCenter).offset(y = 40.dp), style = ShellType.caption.copy(color = color, textAlign = TextAlign.Center), maxLines = 1)
        }
    }
}

/**
 * A checkbox filled with its calendar's colour when checked (K6.4), the size of the shell's other checkboxes (R7
 * §1.3.9's 20.4 epx). Drawn only: the row that holds it carries the tag, the tap and the checked state.
 */
@Composable
fun ColorCheckbox(checked: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(ClockMetrics.CHECKBOX).background(if (checked) color else Color.Transparent).border(1.5.dp, if (checked) color else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) BasicText(Glyph.CHECKMARK, style = glyphStyle(14f, Color.White))
    }
}

/** A calendar row of the ≡ pane, "Can sync to" and the Sync picker: 48.1 epx, the box at x 22, the name at x 62 (K6.4). */
@Composable
fun CalendarRow(name: String, color: Color, checked: Boolean?, tag: String, onClick: () -> Unit) {
    CalPress(
        Modifier.fillMaxWidth().height(CalMetrics.PANE_ROW).testTag(tag).semantics {
            if (checked != null) {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
        },
        onClick = onClick,
    ) {
        if (checked != null) {
            ColorCheckbox(checked, color, Modifier.align(Alignment.CenterStart).offset(x = 22.dp))
        } else {
            // The Sync picker's rows pick, they do not tick: the calendar's colour as a swatch in the box's place.
            Box(Modifier.align(Alignment.CenterStart).offset(x = 22.dp).size(ClockMetrics.CHECKBOX).background(color))
        }
        BasicText(
            name, Modifier.align(Alignment.CenterStart).offset(x = (CalMetrics.PANE_NAME_X - CalMetrics.PANE_NAME_BEARING).dp).padding(end = 70.dp),
            style = ShellType.body.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An account's header over its calendars (K6.4; r3 D5: display names repeat, so every list is grouped by account). */
@Composable
fun AccountHeader(name: String, collapsed: Boolean? = null, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth().height(CalMetrics.PANE_ROW).testTag("cal_account:$name")
            .let { if (onClick != null) it.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick) else it },
    ) {
        val textX = if (collapsed != null) {
            BasicText(
                CalGlyph.CHEVRON_UP,
                Modifier.align(Alignment.CenterStart).offset(x = (CalMetrics.PANE_ACCOUNT_X - CalMetrics.PANE_CHEVRON_BEARING).dp)
                    .graphicsLayer { rotationZ = if (collapsed) 180f else 0f },
                style = glyphStyle(12f, Color.White),
            )
            CalMetrics.PANE_ACCOUNT_X + 20f
        } else {
            CalMetrics.PANE_ACCOUNT_X
        }
        BasicText(
            name, Modifier.align(Alignment.CenterStart).offset(x = textX.dp).padding(end = 40.dp),
            style = ShellType.base.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The page's notice line (`cal_notice`; r3 V8): a node that stays until the page changes, never a toast. With
 * [onGrant] it offers the permission in place (`cal_grant`, phase 10 E18's form: the reason, then the grant right there).
 */
@Composable
fun CalNotice(text: String, modifier: Modifier = Modifier, onGrant: (() -> Unit)? = null) {
    Column(modifier) {
        BasicText(text, Modifier.testTag("cal_notice"), style = ShellType.body.copy(color = Color.White))
        if (onGrant != null) {
            BasicText(
                "allow access",
                Modifier.padding(top = 12.dp).clickable(onClick = onGrant).testTag("cal_grant"),
                style = ShellType.body.copy(color = LocalShellColors.current.accent),
            )
        }
    }
}

/** A leaf page's caps title in the editor header's form (r11/people.md P4.1: cap top 45.5 − 24 below the status bar, cap 11.0, left 13). */
@Composable
fun PageTitle(text: String, tag: String) {
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        CapText(text.uppercase(), 13f, 21.5f, ShellType.base.copy(fontSize = CalMetrics.TITLE_SIZE.sp, color = Color.White), Modifier.testTag(tag))
    }
}

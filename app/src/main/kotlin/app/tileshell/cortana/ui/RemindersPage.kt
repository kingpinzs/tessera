package app.tileshell.cortana.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.tileshell.brand.Glyph
import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderGroup
import app.tileshell.cortana.reminders.ReminderKind
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.cortana.reminders.ReminderText
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Pure layout — every number here is R7 §3.5, and every position is epx from the screen top
// ---------------------------------------------------------------------------------------------

/** All the list layout needs to know about a reminder: whether it has a second line, and a photo. */
data class RowShape(val hasSubline: Boolean, val hasPhoto: Boolean)

/** One laid-out row block, in epx from the screen top. The pressed fill (R7 §3.5.7) covers exactly this. */
data class RowBox(val topEpx: Float, val heightEpx: Float) {
    val bottomEpx: Float get() = topEpx + heightEpx
}

/** One laid-out group: its header's **cap top** and its rows. */
data class GroupBox(val group: ReminderGroup, val headerCapTopEpx: Float, val rows: List<RowBox>)

/**
 * The Reminders list's geometry (R7 §3.5.2–§3.5.5), as pure arithmetic so it can be checked off a device.
 *
 * Chained from two measured anchors: a group header's cap top sits 26.3 epx above its first row's top,
 * and 13.9 epx below the previous row's bottom. `RemindersLayoutTest` replays R7's captured frame
 * (`frames/n_s1/s1_full_t875.0.png`) through it and asserts every edge that frame measured.
 */
object RemindersLayout {

    /** R7 §3.5.5: the photo runs from x 55.4 to a 12.4-epx right margin, aspect 1.97. */
    fun photoWidthEpx(screenWidthEpx: Float): Float =
        screenWidthEpx - CortanaUi.PHOTO_LEFT_EPX - CortanaUi.PHOTO_RIGHT_MARGIN_EPX

    fun photoHeightEpx(screenWidthEpx: Float): Float = photoWidthEpx(screenWidthEpx) / CortanaUi.PHOTO_ASPECT

    /** R7 §3.5.5: the photo's top, 20.1 epx below the title's cap top. */
    fun photoTopEpx(rowTopEpx: Float): Float =
        rowTopEpx + CortanaUi.ROW_TITLE_CAP_TOP_EPX + CortanaUi.PHOTO_BELOW_TITLE_CAP_EPX

    /** R7 §3.5.5: the grey time line's cap top, 5.4 epx under the photo. */
    fun photoTimeCapTopEpx(rowTopEpx: Float, screenWidthEpx: Float): Float =
        photoTopEpx(rowTopEpx) + photoHeightEpx(screenWidthEpx) + CortanaUi.PHOTO_TIME_CAP_BELOW_PHOTO_EPX

    fun rowHeightEpx(shape: RowShape, screenWidthEpx: Float): Float = when {
        shape.hasPhoto -> photoTimeCapTopEpx(0f, screenWidthEpx) + CortanaUi.PHOTO_ROW_BOTTOM_BELOW_TIME_CAP_EPX
        shape.hasSubline -> CortanaUi.ROW_TWO_LINE_EPX
        else -> CortanaUi.ROW_ONE_LINE_EPX
    }

    /**
     * Lay the groups out top to bottom. A group with no rows is **dropped** — R7 §3.5.1 and §3.5.9–10:
     * a group is shown only while it holds a reminder, and a header its last row leaves empty goes with it.
     */
    fun layout(groups: List<Pair<ReminderGroup, List<RowShape>>>, screenWidthEpx: Float): List<GroupBox> {
        val out = mutableListOf<GroupBox>()
        var headerCapTop = CortanaUi.FIRST_HEADER_CAP_TOP_EPX
        for ((group, shapes) in groups) {
            if (shapes.isEmpty()) continue
            var top = headerCapTop + CortanaUi.HEADER_CAP_TO_ROW_TOP_EPX
            val rows = ArrayList<RowBox>(shapes.size)
            for (shape in shapes) {
                val box = RowBox(top, rowHeightEpx(shape, screenWidthEpx))
                rows += box
                top = box.bottomEpx
            }
            out += GroupBox(group, headerCapTop, rows)
            headerCapTop = top + CortanaUi.ROW_BOTTOM_TO_HEADER_CAP_EPX
        }
        return out
    }

    /**
     * How tall the scrolling content is. The bottom margin reuses §3.5.2's 13.9-epx row-bottom gap;
     * R7 measures no margin under the last row (approximation, H24).
     */
    fun contentHeightEpx(blocks: List<GroupBox>): Float {
        val last = blocks.lastOrNull()?.rows?.lastOrNull() ?: return CortanaUi.FIRST_HEADER_CAP_TOP_EPX
        return last.bottomEpx + CortanaUi.ROW_BOTTOM_TO_HEADER_CAP_EPX
    }

    /**
     * R7 §3.5.1 / §3.5.9–10: Today / Coming up / Whenever, in that order, each present only while it
     * holds a reminder. Completed reminders belong to History (R7 §3.6), not this list.
     *
     * Order inside a group is not measured (approximation, H24): soonest first, then oldest first.
     */
    fun groupReminders(
        reminders: List<Reminder>,
        nowMs: Long,
        endOfTodayMs: Long,
    ): List<Pair<ReminderGroup, List<Reminder>>> {
        val byGroup = reminders.filterNot { it.completed }.groupBy { it.group(nowMs, endOfTodayMs) }
        return ReminderGroup.entries.mapNotNull { group ->
            val items = byGroup[group].orEmpty()
                .sortedWith(compareBy({ it.timeMs ?: Long.MAX_VALUE }, { it.createdMs }, { it.id }))
            if (items.isEmpty()) null else group to items
        }
    }

    /**
     * R7 §3.6.2: the long-press menu is centred horizontally on the touch point with its bottom edge
     * 23.5 epx above it. Clamped to the screen's side edges so a touch near an edge cannot push it off
     * (approximation, H24: R7's one capture was touched near the centre, so nothing measures a clamp).
     * The bottom edge is never clamped — R7 §3.6.4 grows the menu from a *fixed* bottom edge.
     */
    fun longPressMenuRect(touchXEpx: Float, touchYEpx: Float, screenWidthEpx: Float): EpxRect {
        val bottom = touchYEpx - CortanaUi.MENU_BOTTOM_ABOVE_TOUCH_EPX
        val maxLeft = (screenWidthEpx - CortanaUi.MENU_W_EPX).coerceAtLeast(0f)
        val left = (touchXEpx - CortanaUi.MENU_W_EPX / 2f).coerceIn(0f, maxLeft)
        return EpxRect(left, bottom - CortanaUi.MENU_H_EPX, left + CortanaUi.MENU_W_EPX, bottom)
    }
}

/** R7 §3.5.6: a timed reminder carries the clock-with-check; everything else the lightbulb-with-check. */
internal fun Reminder.isTimed(): Boolean = kind == ReminderKind.TIME && timeMs != null

/** R7 §3.5.4 / H26: which rows carry a grey second line above the fold (a photo row puts its time below). */
internal fun Reminder.hasSubline(): Boolean =
    photoUri == null && (isTimed() || kind == ReminderKind.PLACE || kind == ReminderKind.PERSON)

// ---------------------------------------------------------------------------------------------
// The pages
// ---------------------------------------------------------------------------------------------

/** What the long-press menu is open over, and where. */
private data class MenuState(val reminderId: String, val rect: EpxRect)

@Composable
fun RemindersPage(
    onOpenReminder: (String) -> Unit,
    onNewReminder: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore.get(context) }
    val all by store.reminders.collectAsState()
    val scope = rememberCoroutineScope()

    val groups = remember(all) {
        val now = System.currentTimeMillis()
        RemindersLayout.groupReminders(all, now, ReminderText.endOfToday(now))
    }

    var pressedRowId by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }

    ReminderListScaffold(
        title = "Reminders",
        titleTag = "reminders_title",
        groups = groups,
        emptyTag = "reminders_empty",
        pressedRowId = pressedRowId,
        onOpenMenu = onOpenMenu,
        onRowPressed = { id, on -> pressedRowId = if (on) id else if (pressedRowId == id) null else pressedRowId },
        onRowTap = { id -> pressedRowId = null; onOpenReminder(id) },
        onRowLongPress = { id, rect -> menu = MenuState(id, rect) },
        menuReminderId = menu?.reminderId,
        menuRect = menu?.rect,
        onMenuChoice = { id, complete ->
            // R7 §3.5.10 / §3.6.1: the row (and a header it empties) go in ONE frame 333 ms after the
            // tap, with no confirmation card — a touch here is not a spoken or typed request.
            scope.launch {
                delay(CortanaUi.DELETE_REMOVE_MS)
                if (complete) store.complete(id) else store.delete(id)
                pressedRowId = null
            }
        },
        onMenuGone = { menu = null },
        modifier = modifier,
    ) { labelled, onMore ->
        CortanaAppBar(
            labelled = labelled,
            tag = "reminders_appbar",
            buttons = listOf(
                AppBarButton("reminders_appbar_list", "history", true, onOpenHistory) { c, m ->
                    CortanaIcons.Font(Glyph.LIST, c, CortanaUi.APPBAR_GLYPH_EPX, m)
                },
                AppBarButton("reminders_appbar_add", "new", true, onNewReminder) { c, m ->
                    CortanaIcons.Font(Glyph.ADD, c, CortanaUi.APPBAR_GLYPH_EPX, m)
                },
                AppBarButton("reminders_appbar_more", "more", true, onMore) { c, m ->
                    CortanaIcons.Font(Glyph.MORE_HORIZONTAL, c, CortanaUi.APPBAR_GLYPH_EPX, m)
                },
            ),
        )
    }
}

/**
 * Completed reminders (approximation, H25: R7 §3.9.5 never opens History). Same header, group and row
 * styles as the list, which is what the Decisions' "+ / History / …" line rules.
 */
@Composable
fun HistoryPage(onOpenReminder: (String) -> Unit, onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore.get(context) }
    val all by store.reminders.collectAsState()

    val groups = remember(all) {
        val now = System.currentTimeMillis()
        // groupReminders drops completed reminders by design, so History hands it the same rows uncompleted.
        val done = all.filter { it.completed }.map { it.copy(completed = false) }
        RemindersLayout.groupReminders(done, now, ReminderText.endOfToday(now))
    }
    var pressedRowId by remember { mutableStateOf<String?>(null) }

    ReminderListScaffold(
        title = "History",
        titleTag = "history_title",
        groups = groups,
        emptyTag = "history_empty",
        emptyText = "No completed reminders",
        pressedRowId = pressedRowId,
        onOpenMenu = onOpenMenu,
        onRowPressed = { id, on -> pressedRowId = if (on) id else if (pressedRowId == id) null else pressedRowId },
        onRowTap = { id -> pressedRowId = null; onOpenReminder(id) },
        onRowLongPress = { _, _ -> },
        menuReminderId = null,
        menuRect = null,
        onMenuChoice = { _, _ -> },
        onMenuGone = {},
        longPressEnabled = false,
        modifier = modifier,
    )
}

/**
 * The shared body of the Reminders and History pages: the (14,19,13) page, the ≡ header drawn over a
 * scrolling list positioned in **screen** coordinates, an optional app bar, and the long-press menu.
 *
 * The scroll container starts at the page's top, so every epx in [RemindersLayout] is literally the y
 * R7 measured from the screen top (R7 §3.1.13: a destination page hides the status bar and runs from
 * the screen top to the nav bar). The header band is drawn last, over it, filled with the page
 * background so scrolled content is occluded rather than showing through.
 */
@Composable
private fun ReminderListScaffold(
    title: String,
    titleTag: String,
    groups: List<Pair<ReminderGroup, List<Reminder>>>,
    emptyTag: String,
    pressedRowId: String?,
    onOpenMenu: () -> Unit,
    onRowPressed: (String, Boolean) -> Unit,
    onRowTap: (String) -> Unit,
    onRowLongPress: (String, EpxRect) -> Unit,
    menuReminderId: String?,
    menuRect: EpxRect?,
    onMenuChoice: (String, Boolean) -> Unit,
    onMenuGone: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = CortanaUi.EMPTY_TEXT,
    longPressEnabled: Boolean = true,
    appBar: (@Composable (labelled: Boolean, onMore: () -> Unit) -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { ReminderStore.get(context) }
    val density = LocalDensity.current
    val revealed = LocalCortanaPageRevealed.current
    var pageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var moreLabels by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(CortanaUi.PAGE_BG)
            .onGloballyPositioned { pageCoords = it },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val screenWidthEpx = maxWidth.value
                    when {
                        // R7 §3.5.9: white subtitle type at x 11.7, cap top 70.4, and no group headers.
                        groups.isEmpty() -> CapTopText(
                            text = emptyText,
                            style = ShellType.subtitle.copy(color = Color.White),
                            capTopEpx = CortanaUi.EMPTY_CAP_TOP_EPX,
                            leftEpx = CortanaUi.EMPTY_LEFT_EPX,
                            modifier = Modifier.testTag(emptyTag),
                        )
                        // R7 §3.2.3: during the fade the group headers show stacked, the rows are absent.
                        !revealed -> groups.forEachIndexed { i, (group, _) ->
                            GroupHeader(group, CortanaUi.FIRST_HEADER_CAP_TOP_EPX + i * CortanaUi.FADE_HEADER_STACK_PITCH_EPX)
                        }
                        else -> {
                            val blocks = remember(groups, screenWidthEpx) {
                                RemindersLayout.layout(
                                    groups.map { (g, items) ->
                                        g to items.map { RowShape(it.hasSubline(), it.photoUri != null) }
                                    },
                                    screenWidthEpx,
                                )
                            }
                            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Spacer(Modifier.fillMaxWidth().height(RemindersLayout.contentHeightEpx(blocks).dp))
                                blocks.forEach { block ->
                                    GroupHeader(block.group, block.headerCapTopEpx)
                                    val items = groups.first { it.first == block.group }.second
                                    block.rows.forEachIndexed { r, box ->
                                        val reminder = items[r]
                                        key(reminder.id) {
                                            ReminderRow(
                                                reminder = reminder,
                                                box = box,
                                                screenWidthEpx = screenWidthEpx,
                                                subline = ReminderText.listSubline(context, reminder, store),
                                                pressed = pressedRowId == reminder.id,
                                                gestures = rememberRowGestures(
                                                    key = reminder.id,
                                                    longPressEnabled = longPressEnabled,
                                                    onPressed = { on -> onRowPressed(reminder.id, on) },
                                                    onTap = { onRowTap(reminder.id) },
                                                    onLongPress = { rowCoords, local ->
                                                        val page = pageCoords
                                                        if (page != null && rowCoords != null) {
                                                            val p = page.localPositionOf(rowCoords, local)
                                                            onRowLongPress(
                                                                reminder.id,
                                                                RemindersLayout.longPressMenuRect(
                                                                    p.x.toEpx(density), p.y.toEpx(density), screenWidthEpx,
                                                                ),
                                                            )
                                                        }
                                                    },
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                CortanaPageHeader(title, onOpenMenu, titleTag = titleTag)
            }
            appBar?.invoke(moreLabels) { moreLabels = !moreLabels }
        }

        if (menuRect != null && menuReminderId != null) {
            LongPressMenu(
                rect = menuRect,
                onChoose = { complete -> onMenuChoice(menuReminderId, complete) },
                onGone = onMenuGone,
            )
        }
    }
}

@Composable
private fun GroupHeader(group: ReminderGroup, capTopEpx: Float) {
    CapTopText(
        text = group.header,
        style = ShellType.body.copy(color = CortanaUi.GROUP_HEADER_COLOR),
        capTopEpx = capTopEpx,
        leftEpx = CortanaUi.GROUP_HEADER_LEFT_EPX,
        modifier = Modifier.testTag(
            when (group) {
                ReminderGroup.TODAY -> "reminders_group_today"
                ReminderGroup.COMING_UP -> "reminders_group_coming_up"
                ReminderGroup.WHENEVER -> "reminders_group_whenever"
            },
        ),
    )
}

/**
 * One reminder row (R7 §3.5.3–§3.5.5). Everything inside is placed against the **row's** top, so the
 * row's own y is the only thing the list layout has to get right.
 */
@Composable
private fun ReminderRow(
    reminder: Reminder,
    box: RowBox,
    screenWidthEpx: Float,
    subline: String?,
    pressed: Boolean,
    gestures: Modifier,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .offset(y = box.topEpx.dp)
            .fillMaxWidth()
            .height(box.heightEpx.dp)
            // The gesture node sits INSIDE the offset so its bounds are the drawn ones (hit testing
            // and the long-press menu's anchor both depend on that).
            .then(gestures)
            // R7 §3.5.7: a pressed row fills its whole block, full width, with (63,68,64).
            .background(if (pressed) CortanaUi.PRESSED_FILL else Color.Transparent)
            .testTag("reminder_row:${reminder.id}")
            .semantics { role = Role.Button },
    ) {
        // R7 §3.5.3 / §3.5.6: the icon box is 32 epx tall with its top 14 epx below the row top.
        if (reminder.isTimed()) {
            CortanaIcons.GlyphWithCheck(
                Glyph.CLOCK,
                CortanaUi.ROW_TITLE_COLOR,
                Modifier
                    .offset(x = CortanaUi.CLOCK_ICON_LEFT_EPX.dp, y = iconTopEpx(CortanaUi.CLOCK_ICON_H_EPX).dp)
                    .size(CortanaUi.CLOCK_ICON_W_EPX.dp, CortanaUi.CLOCK_ICON_H_EPX.dp),
            )
        } else {
            CortanaIcons.GlyphWithCheck(
                Glyph.LIGHTBULB,
                CortanaUi.ROW_TITLE_COLOR,
                Modifier
                    .offset(x = CortanaUi.BULB_ICON_LEFT_EPX.dp, y = iconTopEpx(CortanaUi.BULB_ICON_H_EPX).dp)
                    .size(CortanaUi.BULB_ICON_W_EPX.dp, CortanaUi.BULB_ICON_H_EPX.dp),
            )
        }

        CapTopText(
            text = reminder.text,
            style = ShellType.body.copy(color = CortanaUi.ROW_TITLE_COLOR),
            capTopEpx = CortanaUi.ROW_TITLE_CAP_TOP_EPX,
            leftEpx = CortanaUi.ROW_TITLE_LEFT_EPX,
            modifier = Modifier.testTag("reminder_row_title:${reminder.id}"),
        )

        val photoUri = reminder.photoUri
        if (photoUri != null) {
            // R7 §3.5.5: the photo, then its time as a grey line whose cap top is 5.4 epx under it.
            val photo = rememberReminderPhoto(context, photoUri)
            Box(
                Modifier
                    .offset(x = CortanaUi.PHOTO_LEFT_EPX.dp, y = RemindersLayout.photoTopEpx(0f).dp)
                    .size(
                        RemindersLayout.photoWidthEpx(screenWidthEpx).dp,
                        RemindersLayout.photoHeightEpx(screenWidthEpx).dp,
                    )
                    .background(CortanaUi.FIELD_FILL)
                    .testTag("reminder_row_photo:${reminder.id}"),
            ) {
                if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            reminder.timeMs?.let { at ->
                CapTopText(
                    text = ReminderText.photoTimeLine(context, at),
                    style = ShellType.body.copy(color = CortanaUi.PHOTO_TIME_COLOR),
                    capTopEpx = RemindersLayout.photoTimeCapTopEpx(0f, screenWidthEpx),
                    leftEpx = CortanaUi.PHOTO_LEFT_EPX,
                    modifier = Modifier.testTag("reminder_row_subline:${reminder.id}"),
                )
            }
        } else if (subline != null) {
            // R7 §3.5.4: grey (168,170,167) at x 55.9, cap top 20.1 epx below the title's.
            CapTopText(
                text = subline,
                style = ShellType.body.copy(color = CortanaUi.SUBLINE_COLOR),
                capTopEpx = CortanaUi.ROW_TITLE_CAP_TOP_EPX + CortanaUi.SUBLINE_CAP_PITCH_EPX,
                leftEpx = CortanaUi.SUBLINE_LEFT_EPX,
                modifier = Modifier.testTag("reminder_row_subline:${reminder.id}"),
            )
        }
    }
}

/** R7 §3.5.3: the 32-epx icon box's top is 14 epx below the row top; a shorter glyph centres in it. */
internal fun iconTopEpx(glyphHeightEpx: Float): Float =
    CortanaUi.ROW_ICON_TOP_EPX + (CortanaUi.ROW_ICON_BOX_EPX - glyphHeightEpx) / 2f

/**
 * R7 §3.6.5 and §3.2.6's row timings in one gesture:
 * - the row takes the pressed fill 300 ms after touch-down, or on an earlier lift so that a quick tap
 *   still shows the fill §3.5.7 and §3.2.6 both see (reconciling the two is an approximation, H24);
 * - a long press opens the menu at 700 ms;
 * - a tap opens the reminder page 550 ms after touch-down.
 */
@Composable
private fun rememberRowGestures(
    key: Any,
    longPressEnabled: Boolean,
    onPressed: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: (LayoutCoordinates?, Offset) -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return Modifier
        .onGloballyPositioned { coords = it }
        .pointerInput(key, longPressEnabled) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downAt = System.currentTimeMillis()
                var menuOpened = false
                val fill = scope.launch { delay(CortanaUi.LONG_PRESS_FILL_MS); onPressed(true) }
                val longPress = if (!longPressEnabled) null else scope.launch {
                    delay(CortanaUi.LONG_PRESS_MENU_MS)
                    menuOpened = true
                    onPressed(true)
                    onLongPress(coords, down.position)
                }
                val up = waitForUpOrCancellation()
                fill.cancel()
                if (menuOpened) return@awaitEachGesture
                longPress?.cancel()
                if (up == null) {
                    onPressed(false)
                } else {
                    onPressed(true)
                    scope.launch {
                        val left = CortanaUi.ROW_TAP_TO_PAGE_MS - (System.currentTimeMillis() - downAt)
                        if (left > 0) delay(left)
                        onTap()
                    }
                }
            }
        }
}

// ---------------------------------------------------------------------------------------------
// The long-press menu (R7 §3.6)
// ---------------------------------------------------------------------------------------------

@Composable
private fun LongPressMenu(rect: EpxRect, onChoose: (complete: Boolean) -> Unit, onGone: () -> Unit) {
    var chosen by remember(rect) { mutableStateOf<Boolean?>(null) }
    val grow = remember(rect) { Animatable(CortanaUi.MENU_FIRST_FRAME_HEIGHT) }
    val fade = remember(rect) { Animatable(1f) }

    // R7 §3.6.4: grows upward from a fixed bottom edge, first frame at half height, ease-out over 233 ms.
    LaunchedEffect(rect) {
        grow.animateTo(1f, tween(CortanaUi.MENU_GROW_MS, easing = CortanaEaseOut))
    }

    // R7 §3.6.6: the pressed item shows for 5 frames (≈83 ms), then the menu fades out over 67–83 ms.
    LaunchedEffect(chosen) {
        if (chosen == null) return@LaunchedEffect
        delay(CortanaUi.MENU_CHOICE_HOLD_MS)
        fade.animateTo(0f, tween(CortanaUi.MENU_FADE_OUT_MS, easing = LinearEasing))
        onGone()
    }

    Box(Modifier.fillMaxSize().pointerInput(rect) { detectTapGestures { if (chosen == null) onGone() } }) {
        val height = CortanaUi.MENU_H_EPX * grow.value
        // R7 §3.6.4: the item text rises 6.4 epx over the same 233 ms as the growth.
        val progress = (grow.value - CortanaUi.MENU_FIRST_FRAME_HEIGHT) / (1f - CortanaUi.MENU_FIRST_FRAME_HEIGHT)
        val rise = CortanaUi.MENU_TEXT_RISE_EPX * (1f - progress)
        Box(
            Modifier
                .offset(x = rect.left.dp, y = (rect.bottom - height).dp)
                .width(CortanaUi.MENU_W_EPX.dp)
                .height(height.dp)
                .alpha(fade.value)
                .background(CortanaUi.MENU_FILL)
                .border(CortanaUi.MENU_BORDER_EPX.dp, CortanaUi.MENU_BORDER_COLOR)
                .pointerInput(rect) { detectTapGestures { } }
                .testTag("reminder_menu"),
        ) {
            MenuItem("Complete", "reminder_menu_complete", 0, rise, chosen == true) { chosen = true; onChoose(true) }
            MenuItem("Delete", "reminder_menu_delete", 1, rise, chosen == false) { chosen = false; onChoose(false) }
        }
    }
}

/** R7 §3.6.2: two items at a 44-epx pitch inside the menu's padding, text body class inset ≈11 epx. */
@Composable
private fun MenuItem(label: String, tag: String, index: Int, riseEpx: Float, pressed: Boolean, onClick: () -> Unit) {
    val top = CortanaUi.MENU_BORDER_EPX + CortanaUi.MENU_PAD_EPX + index * CortanaUi.MENU_ITEM_PITCH_EPX
    Box(
        Modifier
            .offset(x = CortanaUi.MENU_BORDER_EPX.dp, y = (top + riseEpx).dp)
            .width((CortanaUi.MENU_W_EPX - 2 * CortanaUi.MENU_BORDER_EPX).dp)
            .height(CortanaUi.MENU_ITEM_PITCH_EPX.dp)
            .background(if (pressed) CortanaUi.MENU_PRESSED_FILL else Color.Transparent)
            .pointerInput(tag) { detectTapGestures { onClick() } }
            .testTag(tag)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            label,
            Modifier.offset(x = CortanaUi.MENU_TEXT_INSET_EPX.dp),
            style = ShellType.body.copy(color = CortanaUi.ROW_TITLE_COLOR),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The app bar (R7 §3.5.8)
// ---------------------------------------------------------------------------------------------

internal data class AppBarButton(
    val tag: String,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
    val icon: @Composable (Color, Modifier) -> Unit,
)

/**
 * R7 §3.5.8: 48.2 epx directly above the nav bar, fill (22,27,21), **no labels**; buttons at a 68-epx
 * pitch with the 48-epx "…" flush right (its dots centred 24.7 epx from the right edge). Raising a
 * label under each button is H25's approximation for what "…" does, so the wording is invented.
 */
@Composable
internal fun CortanaAppBar(labelled: Boolean, tag: String, buttons: List<AppBarButton>, modifier: Modifier = Modifier) {
    val height = if (labelled) CortanaUi.APPBAR_EPX + CortanaUi.APPBAR_LABEL_BAND_EPX else CortanaUi.APPBAR_EPX
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(CortanaUi.APPBAR_FILL)
            .testTag(tag),
    ) {
        val widthEpx = maxWidth.value
        buttons.forEachIndexed { i, button ->
            val fromRight = (buttons.size - 1 - i) * CortanaUi.APPBAR_BUTTON_PITCH_EPX
            AppBarButtonBox(button, widthEpx - CortanaUi.APPBAR_MORE_EPX - fromRight, labelled)
        }
    }
}

@Composable
private fun AppBarButtonBox(button: AppBarButton, leftEpx: Float, labelled: Boolean) {
    val ink = if (button.enabled) CortanaUi.ROW_TITLE_COLOR
    else CortanaUi.ROW_TITLE_COLOR.copy(alpha = CortanaUi.APPBAR_DISABLED_ALPHA)
    Box(
        Modifier
            .offset(x = leftEpx.dp)
            .width(CortanaUi.APPBAR_MORE_EPX.dp)
            .fillMaxHeight()
            .pointerInput(button.tag, button.enabled) { detectTapGestures { if (button.enabled) button.onClick() } }
            .testTag(button.tag)
            .semantics { role = Role.Button; contentDescription = button.label },
    ) {
        button.icon(
            ink,
            Modifier
                .offset(
                    x = ((CortanaUi.APPBAR_MORE_EPX - CortanaUi.APPBAR_GLYPH_EPX) / 2f).dp,
                    y = ((CortanaUi.APPBAR_EPX - CortanaUi.APPBAR_GLYPH_EPX) / 2f).dp,
                )
                .size(CortanaUi.APPBAR_GLYPH_EPX.dp),
        )
        if (labelled) {
            BasicText(
                button.label,
                Modifier.offset(y = CortanaUi.APPBAR_EPX.dp).fillMaxWidth(),
                style = ShellType.caption.copy(color = ink),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------

/** px → epx. Inside `ShellDensity` 1 dp == 1 epx, so this is the density division and nothing else. */
internal fun Float.toEpx(density: Density): Float = with(density) { this@toEpx.toDp().value }

@Composable
internal fun rememberReminderPhoto(context: Context, uri: String?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / sample > 1440) sample *= 2
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

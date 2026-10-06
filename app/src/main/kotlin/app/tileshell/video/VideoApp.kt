package app.tileshell.video

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.calculator.InkText
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.MotionClock
import app.tileshell.ui.components.OverlayLayer
import app.tileshell.ui.components.ROW_PRESS_ALPHA
import app.tileshell.ui.components.dismissOverlay
import app.tileshell.ui.components.modalOverlay
import app.tileshell.ui.components.overlayItem
import app.tileshell.ui.motion.Motion
import app.tileshell.ui.tokens.ShellType

/** The pane's pages (T17-16; tags `hub_pane:<id>`). The App Shortcuts' `page` extra names one (build task 15). */
enum class HubPage(val id: String, val title: String, val glyph: String) {
    MY_VIDEOS("myvideos", "My videos", Glyph.VIDEO),
    BROWSE("browse", "Browse", Glyph.MOVIES_TV),
    MEDIA_SERVER("mediaserver", "Media server", Glyph.SERVER),
    SETTINGS("settings", "Settings", Glyph.SETTINGS);

    companion object {
        fun byId(id: String?): HubPage? = entries.firstOrNull { it.id == id }
    }
}

/** A page opened from a pane page; Back returns to the pane page under it. */
sealed interface HubSub {
    val title: String
}

/** The hub's navigation, held above the composition so an intent can route while the activity is already running. */
class VideoNav {
    /** The pane page that is current (its pane row is the accent one). */
    var page by mutableStateOf(HubPage.MY_VIDEOS)
        private set

    /** A page opened over [page] (a title, a settings page), or null. */
    var sub by mutableStateOf<HubSub?>(null)
        private set
    var paneOpen by mutableStateOf(false)

    /** True while a media server is set up: only then the pane has its Media server row (Q-B). */
    var serverSetUp by mutableStateOf(false)

    /** False until the activity has read whether a server is set up, so the first page drawn is the one asked for. */
    var ready by mutableStateOf(false)

    /** Bumped on every `onResume`, so a page re-reads what may have changed while the app was away. */
    var resumes by mutableIntStateOf(0)

    /** Bumped when Browse's search box should take the focus (the header's search glyph). */
    var searchRequests by mutableIntStateOf(0)

    fun open(intent: Intent?) {
        val extra = runCatching { intent?.getStringExtra(EXTRA_PAGE) }.getOrNull()
        val asked = HubPage.byId(extra) ?: HubPage.MY_VIDEOS
        Diagnostics.add("video", "open page=${asked.id}")
        go(asked)
    }

    /** Opens a pane page; Media server falls back to My videos while no server is set up. */
    fun go(target: HubPage) {
        page = if (target == HubPage.MEDIA_SERVER && !serverSetUp) HubPage.MY_VIDEOS else target
        sub = null
        paneOpen = false
    }

    fun openSub(target: HubSub) {
        sub = target
        paneOpen = false
    }

    /** Back: the pane closes, then a sub page closes; false when the activity should take it. */
    fun back(): Boolean = when {
        paneOpen -> { paneOpen = false; true }
        sub != null -> { sub = null; true }
        else -> false
    }

    val title: String get() = sub?.title ?: page.title

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

/** The library pages' chrome (r11/movies-tv.md 1.1–1.3, HIGH; E19): every value in epx. */
object HubMetrics {
    val CHROME = Color(0xFF171717)
    const val HEADER = 48f
    const val MENU_CENTRE_X = 24f
    const val MENU_GLYPH_WIDTH = 20f
    const val TITLE_X = 60.25f
    /** 1.2.3: cap 11.0 epx in a bold face — 15-epx type, whose capitals draw 11 epx tall. */
    const val TITLE_FONT = 15f
    const val SEARCH_FROM_RIGHT = 24f
    const val SEARCH_GLYPH = 18f

    const val PANE_WIDTH = 256f
    const val PANE_ROW = 48f
    const val PANE_GLYPH_X = 24f
    const val PANE_LABEL_X = 48f
    const val PANE_BAR = 4f
    val PANE_RULE = Color(0xFF404040)
    const val PANE_RULE_INSET = 12f

    /** 1.3.11: the rule sits 15 epx above the bottom group's first row. */
    const val PANE_RULE_ABOVE_GROUP = 15f

    /** Y6 (r11/movies-tv-pass2.md §5): the pane's edge per 33-ms frame — 23 / 46 / 78 / 87 / 93 / 99 / 100 %, settled at 167–200 ms. */
    const val PANE_OPEN_MS = 183
    val PANE_OPEN_KEYS = listOf(0 to 0f, 33 to 0.23f, 67 to 0.46f, 100 to 0.78f, 133 to 0.87f, 167 to 0.96f, 183 to 1f)

    /** Y6: the pane slides out in 2 frames of the 30-fps source, 67–100 ms. */
    const val PANE_CLOSE_MS = 75

    val paneOpenEasing = Easing { f -> Motion.sampleKeyframes(PANE_OPEN_KEYS, f * PANE_OPEN_MS) }
}

/**
 * The hub's pages inside the frame [VideoActivity] sets: phase 01's status bar over the 48-epx header in one #171717
 * band (the frame's background is the chrome colour — Y7), the page on black under it, and the ≡ pane over the page.
 */
@Composable
fun VideoApp(nav: VideoNav, activity: ComponentActivity, browse: BrowseModel) {
    BackHandler(enabled = nav.paneOpen || nav.sub != null) { nav.back() }
    Column(Modifier.fillMaxSize().testTag("hub_root")) {
        HubHeader(nav)
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black).clipToBounds()) {
            HubPages(nav, activity, browse)
            HubPane(nav)
        }
    }
}

/** 1.2: ≡ at x 24, the ALL-CAPS title at x 60.25 (hidden while the pane is open, 1.2.4), search at W − 24. */
@Composable
private fun HubHeader(nav: VideoNav) {
    Box(Modifier.fillMaxWidth().height(HubMetrics.HEADER.dp).background(HubMetrics.CHROME).testTag("hub_header")) {
        HeaderGlyph(
            Glyph.NAVIGATION, HubMetrics.MENU_GLYPH_WIDTH, "hub_menu", "Menu",
            Modifier.align(Alignment.CenterStart).offset((HubMetrics.MENU_CENTRE_X - 24f).dp),
        ) { nav.paneOpen = !nav.paneOpen }
        if (!nav.paneOpen) {
            // 1.2.3: the capitals' ink starts at x 60.25 and their centre sits 1 epx below the header's (cy 49.0 in the
            // 24 → 72 band); r11 measured ink, so the text is placed by its ink.
            InkText(
                nav.title.uppercase(),
                ShellType.base.copy(fontSize = HubMetrics.TITLE_FONT.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
                reference = "V", modifier = Modifier.testTag("hub_header_title"),
                inkLeftEpx = HubMetrics.TITLE_X, inkCentreEpx = HubMetrics.HEADER / 2f + 1f,
            )
        }
        HeaderGlyph(
            Glyph.SEARCH, HubMetrics.SEARCH_GLYPH, "hub_search", "Search",
            Modifier.align(Alignment.CenterEnd).offset((24f - HubMetrics.SEARCH_FROM_RIGHT).dp),
        ) {
            if (HubPage.BROWSE in HubPages.PANE_TOP) {
                nav.go(HubPage.BROWSE)
                nav.searchRequests++
            }
        }
    }
}

@Composable
private fun HeaderGlyph(glyph: String, inkEpx: Float, tag: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).testTag(tag).semantics { contentDescription = description }.pointerInput(tag) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        // The icon font's glyphs fill 0.8 of their em box.
        BasicText(glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = (inkEpx / 0.8f).sp, lineHeight = (inkEpx / 0.8f).sp, color = Color.White))
    }
}

/**
 * 1.3: the ≡ pane — a 256-epx overlay from the chrome's bottom to the nav bar, #171717, with NO scrim (the page right
 * of it is drawn unchanged), 48-epx rows with the glyph at x 24 and the label at x 48, the current row's glyph and
 * label in the accent with a 4 × 48 accent bar at x 0, and the bottom group (Settings) under a 1-epx rule. A tap off
 * it closes it. It slides in over [HubMetrics.PANE_OPEN_MS] and out over [HubMetrics.PANE_CLOSE_MS] on the motion
 * clock (`[motion] pane_open`, `[motion] pane_close`).
 */
@Composable
private fun HubPane(nav: VideoNav) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(nav.paneOpen) {
        val from = progress
        if (nav.paneOpen) {
            if (from < 1f) MotionClock.animate("pane_open", HubMetrics.PANE_OPEN_MS, HubMetrics.paneOpenEasing) { progress = from + (1f - from) * it }
        } else if (from > 0f) {
            MotionClock.animate("pane_close", HubMetrics.PANE_CLOSE_MS, LinearEasing) { progress = from * (1f - it) }
        }
    }
    if (!nav.paneOpen && progress <= 0f) return
    OverlayLayer(active = { nav.paneOpen || progress > 0f }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().modalOverlay(onTapOff = { dismissOverlay { nav.paneOpen = false } })) {
            Column(
                Modifier.offset((-HubMetrics.PANE_WIDTH * (1f - progress)).dp).width(HubMetrics.PANE_WIDTH.dp).fillMaxHeight()
                    .background(HubMetrics.CHROME).testTag("hub_pane")
                    // The pane's own surface is not "off the pane".
                    .overlayItem(onPressedChange = {}, onRun = {}),
            ) {
                for (p in HubPages.PANE_TOP) {
                    if (p == HubPage.MEDIA_SERVER && !nav.serverSetUp) continue
                    PaneRow(p, current = nav.page == p) { dismissOverlay { nav.go(p) } }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(HubMetrics.PANE_RULE_ABOVE_GROUP.dp).fillMaxWidth()) {
                    Box(
                        Modifier.padding(horizontal = HubMetrics.PANE_RULE_INSET.dp).fillMaxWidth().height(1.dp)
                            .background(HubMetrics.PANE_RULE).testTag("hub_pane_rule"),
                    )
                }
                PaneRow(HubPage.SETTINGS, current = nav.page == HubPage.SETTINGS) { dismissOverlay { nav.go(HubPage.SETTINGS) } }
            }
        }
    }
}

@Composable
private fun PaneRow(page: HubPage, current: Boolean, onRun: () -> Unit) {
    val colors = LocalShellColors.current
    var pressed by remember { mutableStateOf(false) }
    val ink = if (current) colors.accent else Color.White
    Box(
        Modifier.fillMaxWidth().height(HubMetrics.PANE_ROW.dp)
            .background(if (pressed) Color.White.copy(alpha = ROW_PRESS_ALPHA) else Color.Transparent)
            .testTag("hub_pane:${page.id}")
            // Role.Tab, as the clock's tabs: without it Compose reports `selected` as a checkbox state, not as selected.
            .semantics { role = Role.Tab; selected = current }
            .overlayItem(onPressedChange = { pressed = it }, onRun = onRun),
    ) {
        if (current) Box(Modifier.size(HubMetrics.PANE_BAR.dp, HubMetrics.PANE_ROW.dp).background(colors.accent).testTag("hub_pane_bar"))
        Box(Modifier.size((HubMetrics.PANE_GLYPH_X * 2).dp, HubMetrics.PANE_ROW.dp), contentAlignment = Alignment.Center) {
            BasicText(page.glyph, style = ShellType.body.copy(fontFamily = Brand.iconFont, fontSize = 20.sp, lineHeight = 20.sp, color = ink))
        }
        BasicText(
            page.title,
            Modifier.align(Alignment.CenterStart).padding(start = HubMetrics.PANE_LABEL_X.dp).testTag("hub_pane_label:${page.id}"),
            style = ShellType.body.copy(color = ink), maxLines = 1,
        )
    }
}

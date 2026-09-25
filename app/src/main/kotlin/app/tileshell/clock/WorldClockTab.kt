package app.tileshell.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.TimeZoneNames
import android.icu.util.TimeZone as IcuTimeZone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.start.Edit
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.CapMetrics
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

private fun capPad(capTop: Float, size: Float): Dp = CapMetrics.topPaddingForCapTop(capTop, size).dp

/**
 * The device's own zone list with ICU's exemplar location names (Decisions "World clock": no bundled asset, no
 * network) — the canonical tz ids, so aliases such as US/Eastern do not double every city; a zone ICU has no
 * name for keeps its id's last segment (WorldClockRules.name).
 */
private fun loadZones(locale: Locale): List<WorldClockRules.Zone> {
    val names = TimeZoneNames.getInstance(android.icu.util.ULocale.forLocale(locale))
    val ids = IcuTimeZone.getAvailableIDs(IcuTimeZone.SystemTimeZoneType.CANONICAL, null, null)
    return ids.map { id -> WorldClockRules.Zone(id, names.getExemplarLocationName(id)) }
}

/**
 * The World Clock tab (§5; H4 [accept]: no map): the accent-filled "Local time" row (5.3), then one row per city at
 * a 96.9-epx pitch (5.4–5.5) with W10M's difference line (5.6). Add opens the city search over the list (5.8, U5);
 * a hold on a city offers Remove; compare mode (5.7) shifts every time by the strip's hour and shows full dates.
 */
@Composable
fun BoxScope.WorldClockTab(nav: ClockNav, world: WorldClockStore) {
    val colors = LocalShellColors.current
    val context = LocalContext.current
    val is24h = LocalIs24h.current
    val locale = LocalConfiguration.current.locales[0]
    val zones by world.zones.collectAsState()

    // The process caches the default zone; a zone change re-reads it (the status bar does the same).
    var zoneGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { TimeZone.setDefault(null); zoneGeneration++ }
        }
        context.registerReceiver(receiver, IntentFilter().apply { addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED) }, Context.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val local = remember(zoneGeneration) { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    var entries by remember { mutableStateOf<List<WorldClockRules.Entry>>(emptyList()) }
    LaunchedEffect(locale) {
        entries = withContext(Dispatchers.Default) { WorldClockRules.entries(loadZones(locale)) }
        Diagnostics.add("clock", "world clock: ${entries.size} zones from ICU (${locale.toLanguageTag()})")
    }
    val labels = remember(entries) { entries.associate { it.id to it.label } }
    val displayMs = if (nav.compare) WorldClockRules.compareInstant(now, nav.compareOffset) else now

    var menuZone by remember { mutableStateOf<String?>(null) }
    var menuAnchor by remember { mutableFloatStateOf(0f) }
    var rootTop by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize().background(if (nav.compare) Color(32, 34, 34) else Color.Transparent).onGloballyPositioned { rootTop = it.positionInRoot().y }) {
        LazyColumn(Modifier.fillMaxSize().testTag("clock_list"), contentPadding = PaddingValues(bottom = ClockMetrics.APP_BAR)) {
            item(key = "local") { LocalRow(displayMs, local, is24h, locale) }
            items(zones, key = { it }) { id ->
                val zone = remember(id) { runCatching { ZoneId.of(id) }.getOrNull() }
                if (zone != null) {
                    CityRow(
                        id = id, zone = zone, label = labels[id] ?: WorldClockRules.name(WorldClockRules.Zone(id, null)),
                        displayMs = displayMs, local = local, compare = nav.compare, is24h = is24h, locale = locale,
                        onHold = { y -> menuAnchor = y - rootTop; menuZone = id },
                    )
                }
            }
        }
        menuZone?.let { id ->
            // The hold menu: R7 §3.6.2's flyout with the one verb, under the held row.
            val top = with(density) { menuAnchor.toDp() } + 60.dp
            ClockFlyout(x = 11.6.dp, top = top, width = 335.5.dp, height = 60.dp, tag = "clock_row_menu", onDismiss = { menuZone = null }) {
                Box(Modifier.height(8.dp))
                PressBox(Modifier.fillMaxWidth().height(ClockMetrics.MENU_ROW).testTag("clock_remove:$id"), onClick = { world.remove(id); menuZone = null }) {
                    BasicText("Remove", Modifier.align(Alignment.CenterStart).offset(x = 11.7.dp), style = ShellType.body.copy(color = Color.White))
                }
            }
        }
        if (nav.worldSearch) CitySearch(entries, onPick = { world.add(it.id); nav.worldSearch = false })
    }
    @Suppress("UNUSED_EXPRESSION") colors
}

/** 5.3: full width at ≈ 0.6 × accent, 84.9 epx tall; white text at x 18.8 — the time (cap top 15.6), "Local time" semibold, the date. */
@Composable
private fun LocalRow(ms: Long, local: ZoneId, is24h: Boolean, locale: Locale) {
    val colors = LocalShellColors.current
    Box(Modifier.fillMaxWidth().height(84.9.dp).background(colors.accent.copy(alpha = 0.6f)).testTag("clock_local_row")) {
        BasicText(ClockText.timeAt(ms, local, is24h, locale), Modifier.offset(x = 18.8.dp, y = capPad(15.6f, 25.4f)).testTag("clock_local_time"),
            style = ShellType.title.copy(fontSize = 25.4.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light, color = Color.White), maxLines = 1)
        BasicText("Local time", Modifier.offset(x = 18.8.dp, y = capPad(15.6f + 30.3f, 15f)).testTag("clock_local_label"), style = ShellType.base.copy(color = Color.White))
        BasicText(WorldClockRules.localDate(ms, local, locale), Modifier.offset(x = 18.8.dp, y = capPad(15.6f + 30.3f + 19.5f, 15f)).testTag("clock_local_date"),
            style = ShellType.body.copy(color = Color.White), maxLines = 1)
    }
}

/** 5.4–5.6: time (Light, cap top 27.5), the city in semibold 30.3 lower, the difference line (or the full date in compare) 19.5 lower; ink x 18.8. */
@Composable
private fun CityRow(id: String, zone: ZoneId, label: String, displayMs: Long, local: ZoneId, compare: Boolean, is24h: Boolean, locale: Locale, onHold: (Float) -> Unit) {
    val colors = LocalShellColors.current
    var top by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(96.9.dp).testTag("clock_row:$id")
            .onGloballyPositioned { top = it.positionInRoot().y }
            .pointerInput(id) {
                awaitEachGesture {
                    awaitFirstDown()
                    // A hold (Edit.HOLD_MS, the shell's one hold) opens the row's menu; a scroll opens nothing.
                    val up = withTimeoutOrNull(Edit.HOLD_MS) { waitForUpOrCancellation() }
                    if (up == null && currentEvent.changes.any { it.pressed }) onHold(top)
                }
            },
    ) {
        BasicText(ClockText.timeAt(displayMs, zone, is24h, locale), Modifier.offset(x = 18.8.dp, y = capPad(27.5f, 25.4f)).testTag("clock_time:$id"),
            style = ShellType.title.copy(fontSize = 25.4.sp, lineHeight = 32.sp, fontWeight = FontWeight.Light, color = colors.text), maxLines = 1)
        BasicText(label, Modifier.offset(x = 18.8.dp, y = capPad(27.5f + 30.3f, 15f)).testTag("clock_name:$id"), style = ShellType.base.copy(color = colors.text), maxLines = 1)
        if (compare) {
            BasicText(WorldClockRules.fullDate(displayMs, zone, locale), Modifier.offset(x = 18.8.dp, y = capPad(27.5f + 30.3f + 19.5f, 15f)).testTag("clock_date:$id"),
                style = ShellType.body.copy(color = colors.text.copy(alpha = 0.51f)), maxLines = 1)
        } else {
            BasicText(WorldClockRules.difference(zone, local, displayMs, locale), Modifier.offset(x = 18.8.dp, y = capPad(27.5f + 30.3f + 19.5f, 15f)).testTag("clock_diff:$id"),
                style = ShellType.body.copy(color = colors.text.copy(alpha = 0.51f)), maxLines = 1)
        }
    }
}

/**
 * The city search (5.8, LOW; U5): R7 §3.7.3's field — 43.4 epx tall, accent border, fill (32,37,33) — with
 * 12-epx side margins at the top of the tab, suggestions in 44-epx rows under it (R3 C2), and "No results" in
 * R7 §3.5.9's style for a query nothing matches (approximation, H16).
 */
@Composable
private fun BoxScope.CitySearch(entries: List<WorldClockRules.Entry>, onPick: (WorldClockRules.Entry) -> Unit) {
    val colors = LocalShellColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(query, entries) { WorldClockRules.search(entries, query) }
    Box(Modifier.fillMaxSize().background(colors.background).testTag("clock_search_page")) {
        Box(
            Modifier.offset(y = 8.dp).padding(horizontal = 12.dp).fillMaxWidth().height(43.4.dp)
                .background(Color(32, 37, 33)).border(2.dp, colors.accent),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it.replace("\n", "") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 17.6.dp).focusRequester(focus).testTag("clock_search"),
                textStyle = ShellType.title.copy(color = colors.text),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
            )
        }
        if (query.isNotBlank() && results.isEmpty()) {
            EmptyLineR7("No results", "clock_search_empty", capTop = 8f + 43.4f + 40f)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 60.dp).testTag("clock_search_results"), contentPadding = PaddingValues(bottom = ClockMetrics.APP_BAR)) {
                items(results, key = { it.id }) { e ->
                    PressBox(Modifier.fillMaxWidth().height(44.dp), onClick = { onPick(e) }) {
                        BasicText(e.label, Modifier.align(Alignment.CenterStart).offset(x = 12.dp).testTag("clock_search_result:${e.id}"), style = ShellType.body.copy(color = colors.text), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Compare mode's strip (5.7, H22): a 48-epx accent strip where the app bar was, "‹ 23 · 00 · 01 · 02 · 03 ›" with
 * the compare hour in the middle and a centred up-notch on its top edge; ‹ › step the hour.
 */
@Composable
fun BoxScope.CompareStrip(nav: ClockNav) {
    val colors = LocalShellColors.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000L) } }
    val hours = WorldClockRules.stripHours(WorldClockRules.compareInstant(now, nav.compareOffset), ZoneId.systemDefault())
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(48.dp + 6.dp)) {
        // The notch: a small accent triangle rising from the strip's top edge.
        androidx.compose.foundation.Canvas(Modifier.align(Alignment.TopCenter).size(14.dp, 6.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height); close() }
            drawPath(path, colors.accent)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(48.dp).background(colors.accent).testTag("clock_compare_strip")) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlyphButton(Glyph.CHEVRON_LEFT, "clock_compare_prev", true, Modifier, size = 16f) { nav.compareOffset-- }
                Box(Modifier.weight(1f)) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        hours.forEachIndexed { i, h ->
                            if (i > 0) BasicText(" · ", style = ShellType.body.copy(color = Color.White.copy(alpha = 0.7f)))
                            BasicText(
                                h,
                                if (i == 2) Modifier.testTag("clock_compare_hour") else Modifier,
                                style = ShellType.body.copy(color = Color.White, fontWeight = if (i == 2) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center),
                            )
                        }
                    }
                }
                GlyphButton(Glyph.CHEVRON_RIGHT, "clock_compare_next", true, Modifier, size = 16f) { nav.compareOffset++ }
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") Brand.iconFont
}

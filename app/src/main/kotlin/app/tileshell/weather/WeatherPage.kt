package app.tileshell.weather

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.components.PressRow
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Forecast page modelled on MSN Weather (R3 C5, 2015 geometry) with S11's 15063 differences applied (approximation X1,
 * H9): dark header bar, condition icon left of the temperature, "Updated at" line, barometer / humidity line and a
 * "Daily" header. Values are epx (1.dp == 1 epx inside ShellRoot).
 */
private object WeatherColors {
    /** C5: page navy (16,32,57). */
    val page = Color(0xFF102039)
    /** S11: dark header bar on 15063; the shade is the dark theme's chrome medium (agent pick inside X1). */
    val header = Color(0xFF1F1F1F)
    /** C5: 1-epx rule (37,37,37) under a section header. */
    val rule = Color(0xFF252525)
    /** C5: selected day cell = page colour + ≈20 % white. */
    val selectedTint = Color.White.copy(alpha = 0.20f)
    val dim = Color.White.copy(alpha = 0.60f)

    /**
     * Approximation X2 (H10): the current-conditions block's full-bleed background keyed to condition group +
     * day/night, drawn as a gradient in code (no bundled photos) that settles into the page navy.
     */
    fun background(group: ConditionGroup, isDay: Boolean): Pair<Color, Color> = when (group) {
        ConditionGroup.CLEAR -> if (isDay) Color(0xFF165EA8) to Color(0xFF3A88CC) else Color(0xFF081028) to Color(0xFF192850)
        ConditionGroup.PARTLY_CLOUDY -> if (isDay) Color(0xFF2E5C91) to Color(0xFF5C84B0) else Color(0xFF0F1830) to Color(0xFF283452)
        ConditionGroup.CLOUDY -> if (isDay) Color(0xFF485462) to Color(0xFF6E7A86) else Color(0xFF1C2028) to Color(0xFF343A44)
        ConditionGroup.FOG -> if (isDay) Color(0xFF5C6268) to Color(0xFF80868A) else Color(0xFF282A2E) to Color(0xFF46484C)
        ConditionGroup.RAIN -> if (isDay) Color(0xFF2C3A4C) to Color(0xFF4E5C6C) else Color(0xFF121822) to Color(0xFF28303C)
        ConditionGroup.SNOW -> if (isDay) Color(0xFF566880) to Color(0xFF7E8EA0) else Color(0xFF283246) to Color(0xFF4B586E)
        ConditionGroup.THUNDER -> if (isDay) Color(0xFF262438) to Color(0xFF48425C) else Color(0xFF0F0C1C) to Color(0xFF28233C)
    }
}

/** C5 day strip: three cells across the 360-epx canvas (measured 119 × 148 epx). */
private const val DAY_CELL_W = 120
private const val DAY_CELL_H = 148
/** C5 day-strip tap: the strip scrolls so the tapped day sits in the selected slot in ≈167 ms. */
private const val DAY_SCROLL_MS = 167
private const val HOUR_CELL_W = 72

private fun icon(size: Int, color: Color = Color.White) = TextStyle(fontFamily = Brand.iconFont, fontSize = size.sp, lineHeight = size.sp, color = color)

@Composable
fun WeatherPage(onBack: () -> Unit, onWindows: () -> Unit) {
    val state by WeatherFeed.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val colors = LocalShellColors.current.copy(background = WeatherColors.page, text = Color.White, subtleText = WeatherColors.dim, chrome = WeatherColors.header, isDark = true)
    CompositionLocalProvider(LocalShellColors provides colors) {
        Column(Modifier.fillMaxSize().background(WeatherColors.page)) {
            W10mStatusBar(Modifier.background(WeatherColors.header))
            HeaderBar()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("weather_scroll")) {
                state.problems.forEach { ProblemRow(it, hasReport = state.report != null) }
                val report = state.report
                if (report != null) {
                    ReportContent(report, now = maxOf(now, report.fetchedAtMs))
                } else {
                    BasicText(
                        if (state.refreshing || state.problems.isEmpty()) "Getting your weather…" else "No weather to show yet",
                        style = ShellType.body.copy(color = Color.White),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp).testTag("weather_loading"),
                    )
                }
                BasicText(
                    WeatherFeed.provider.attribution,
                    style = ShellType.caption.copy(color = WeatherColors.dim),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp).testTag("weather_attribution"),
                )
            }
            W10mNavBar(onBack = onBack, onWindows = onWindows)
        }
    }
}

/** S11 (15063): dark header bar. C5: 41 epx tall, title SemiBold ≈18 epx. */
@Composable
private fun HeaderBar() {
    Box(Modifier.fillMaxWidth().height(41.dp).background(WeatherColors.header).testTag("weather_header")) {
        BasicText(
            "Forecast",
            style = ShellType.base.copy(fontSize = 18.sp, lineHeight = 24.sp, color = Color.White),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
        )
    }
}

@Composable
private fun ReportContent(report: WeatherReport, now: Long) {
    val days = report.upcomingDays(now)
    var selected by remember(report.fetchedAtMs) { mutableIntStateOf(0) }
    val sel = if (days.isEmpty()) -1 else selected.coerceIn(0, days.size - 1)

    CurrentBlock(report, now)
    SectionHeader("Daily", "weather_daily_header")
    DayStrip(days, report.timeZone, sel) { selected = it }
    SectionHeader("Hourly", "weather_hourly_header")
    key(sel) {
        HourStrip(report.hoursFor(days.getOrNull(sel)?.takeIf { sel > 0 }, now), report.timeZone)
    }
}

@Composable
private fun CurrentBlock(report: WeatherReport, now: Long) {
    val context = LocalContext.current
    val c = report.current
    val group = WmoCodes.group(c.code)
    val (top, mid) = WeatherColors.background(group, c.isDay)
    val backgroundKey = "${group.name.lowercase()}_${if (c.isDay) "day" else "night"}"
    LaunchedEffect(backgroundKey) { Diagnostics.add("weather", "app background key=$backgroundKey") }
    val white = Color.White
    val imperial = report.units == WeatherUnits.IMPERIAL

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to top, 0.75f to mid, 1f to WeatherColors.page))
            .testTag("weather_background"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        // C5: location line cap 18 (≈26-epx Light), centred.
        BasicText(
            report.place ?: WeatherFormat.coordinates(report.latitude, report.longitude),
            style = ShellType.subheader.copy(fontSize = 26.sp, lineHeight = 32.sp, color = white),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp).testTag("weather_location"),
        )
        Spacer(Modifier.height(8.dp))
        // S11: condition icon left of the temperature. C5: digits 61 epx tall (≈87-epx Light), "°" then a C/F stack.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(WmoCodes.glyph(c.code, c.isDay), style = icon(56), modifier = Modifier.testTag("weather_current_icon"))
            Spacer(Modifier.width(10.dp))
            BasicText(
                WeatherFormat.degrees(c.temperature),
                style = ShellType.header.copy(fontSize = 87.sp, lineHeight = 100.sp, color = white),
                modifier = Modifier.testTag("weather_current_temp"),
            )
            Column(Modifier.padding(start = 2.dp).testTag("weather_units")) {
                BasicText("C", style = ShellType.subtitle.copy(fontSize = 22.sp, lineHeight = 26.sp, color = if (imperial) WeatherColors.dim else white))
                BasicText("F", style = ShellType.subtitle.copy(fontSize = 22.sp, lineHeight = 26.sp, color = if (imperial) white else WeatherColors.dim))
            }
        }
        // C5: condition text cap 14 (≈20-epx Subtitle).
        BasicText(WmoCodes.word(c.code, c.isDay), style = ShellType.subtitle.copy(color = white), modifier = Modifier.testTag("weather_current_condition"))
        Spacer(Modifier.height(4.dp))
        // S11: "Updated at" line (X22: the same time the stale tile shows).
        BasicText(
            "Updated at ${WeatherFormat.clock(context, report.fetchedAtMs)}",
            style = ShellType.caption.copy(color = white),
            modifier = Modifier.testTag("weather_updated"),
        )
        Spacer(Modifier.height(10.dp))
        // C5 / S11: two ≈15-epx body lines: feels like + wind, barometer + humidity.
        BasicText(
            "Feels Like ${WeatherFormat.degrees(c.feelsLike)}    Wind ${WeatherFormat.wind(report.units, c.windSpeed, c.windDirectionDeg)}",
            style = ShellType.body.copy(color = white),
            modifier = Modifier.testTag("weather_feels_wind"),
        )
        BasicText(
            "Barometer ${WeatherFormat.pressure(report.units, c.pressureHpa)}    Humidity ${c.humidityPct}%",
            style = ShellType.body.copy(color = white),
            modifier = Modifier.testTag("weather_barometer_humidity"),
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** C5: section header ≈18–20-epx at x = 12 with a 1-epx rule under it ("Hourly"; S11 adds "Daily"). */
@Composable
private fun SectionHeader(title: String, tag: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        BasicText(title, style = ShellType.subtitle.copy(color = Color.White), modifier = Modifier.padding(start = 12.dp, bottom = 6.dp).testTag(tag))
        Box(Modifier.fillMaxWidth().height(1.dp).background(WeatherColors.rule))
    }
}

@Composable
private fun DayStrip(days: List<DayPoint>, zone: TimeZone, selected: Int, onSelect: (Int) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val cellPx = with(LocalDensity.current) { DAY_CELL_W.dp.roundToPx() }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).testTag("weather_daily")) {
        days.forEachIndexed { i, day ->
            DayCell(day, zone, i == selected, Modifier.testTag("weather_day_$i")) {
                onSelect(i)
                scope.launch { scroll.animateScrollTo((i * cellPx).coerceAtMost(scroll.maxValue), tween(DAY_SCROLL_MS)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: DayPoint, zone: TimeZone, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Column(modifier.width(DAY_CELL_W.dp)) {
        Box(
            Modifier.size(DAY_CELL_W.dp, DAY_CELL_H.dp)
                .background(if (selected) WeatherColors.selectedTint else Color.Transparent)
                // C5: a 1-epx light outline while pressed.
                .border(1.dp, if (pressed) WeatherColors.dim else Color.Transparent)
                .pointerInput(onClick) {
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up != null) onClick()
                    }
                },
        ) {
            // C5 cell content: day name (≈15-epx) at 12, icon 22 at 34, hi (≈22-epx) + dimmed lo at 67, caption (12-epx) at 95.
            BasicText(WeatherFormat.dayCell(day.dateMs, zone), style = ShellType.body.copy(color = Color.White), maxLines = 1,
                modifier = Modifier.padding(start = 13.dp, top = 8.dp))
            BasicText(WmoCodes.glyph(day.code, true), style = icon(22), modifier = Modifier.padding(start = 13.dp, top = 34.dp))
            Row(Modifier.padding(start = 13.dp, top = 62.dp), verticalAlignment = Alignment.Bottom) {
                BasicText(WeatherFormat.degrees(day.high), style = ShellType.subtitle.copy(fontSize = 22.sp, lineHeight = 28.sp, color = Color.White))
                Spacer(Modifier.width(6.dp))
                BasicText(WeatherFormat.degrees(day.low), style = ShellType.body.copy(fontSize = 18.sp, lineHeight = 24.sp, color = WeatherColors.dim))
            }
            BasicText(WmoCodes.word(day.code, true), style = ShellType.caption.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 13.dp, top = 95.dp, end = 8.dp))
        }
        // C5: an 8-epx caret under the selected cell.
        Canvas(Modifier.width(DAY_CELL_W.dp).height(8.dp)) {
            if (selected) {
                val path = Path().apply {
                    moveTo(size.width / 2f - size.height, 0f)
                    lineTo(size.width / 2f + size.height, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, WeatherColors.selectedTint)
            }
        }
    }
}

@Composable
private fun HourStrip(hours: List<HourPoint>, zone: TimeZone) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("weather_hourly")) {
        hours.forEachIndexed { i, h ->
            // C5 hourly list cell (icon 22, temp ≈25-epx, precipitation, hour label at the bottom) as a strip.
            Box(Modifier.size(HOUR_CELL_W.dp, 124.dp).testTag("weather_hour_$i")) {
                BasicText(WmoCodes.glyph(h.code, h.isDay), style = icon(22), modifier = Modifier.padding(start = 12.dp, top = 12.dp))
                BasicText(WeatherFormat.degrees(h.temperature), style = ShellType.subtitle.copy(fontSize = 25.sp, lineHeight = 32.sp, color = Color.White),
                    modifier = Modifier.padding(start = 12.dp, top = 40.dp))
                h.precipPct?.let {
                    BasicText("$it%", style = ShellType.caption.copy(color = WeatherColors.dim), modifier = Modifier.padding(start = 12.dp, top = 76.dp))
                }
                BasicText(WeatherFormat.hour(context, h.timeMs, zone), style = ShellType.caption.copy(color = Color.White), maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp, top = 98.dp))
            }
        }
    }
}

/** W10M-style message row (C1 two-line list item: 64 epx, glyph at x 12, text at x 55) with a tap target. */
@Composable
private fun ProblemRow(problem: WeatherFeed.Problem, hasReport: Boolean) {
    val context = LocalContext.current
    val (glyph, title, subtitle) = when (problem) {
        WeatherFeed.Problem.NO_PERMISSION -> Triple(Glyph.LOCATION, "Weather can't use your location", "Tap to allow location in app permissions")
        WeatherFeed.Problem.LOCATION_OFF -> Triple(Glyph.LOCATION, "Location is turned off", "Tap to open location settings")
        WeatherFeed.Problem.NO_LOCATION -> Triple(Glyph.LOCATION, "We couldn't find your location", "Tap to try again")
        WeatherFeed.Problem.NO_NETWORK -> Triple(Glyph.WARNING, "No internet connection", if (hasReport) "Showing the last update. Tap to try again" else "Tap to try again")
        WeatherFeed.Problem.PROVIDER_ERROR -> Triple(Glyph.WARNING, "Couldn't get the weather", "Tap to try again")
    }
    val tag = when (problem) {
        WeatherFeed.Problem.NO_PERMISSION -> "weather_permission_row"
        WeatherFeed.Problem.LOCATION_OFF -> "weather_location_off_row"
        WeatherFeed.Problem.NO_LOCATION -> "weather_no_location_row"
        WeatherFeed.Problem.NO_NETWORK -> "weather_offline_row"
        WeatherFeed.Problem.PROVIDER_ERROR -> "weather_error_row"
    }
    val onClick: () -> Unit = {
        Diagnostics.add("weather", "tap $tag")
        when (problem) {
            WeatherFeed.Problem.NO_PERMISSION -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
            WeatherFeed.Problem.LOCATION_OFF -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> WeatherFeed.refreshNow("tap $tag")
        }
    }
    PressRow(onClick = onClick, modifier = Modifier.fillMaxWidth().height(64.dp).testTag(tag)) {
        BasicText(glyph, style = icon(24), modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 55.dp, end = 12.dp)) {
            BasicText(title, style = ShellType.body.copy(color = Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(subtitle, style = ShellType.caption.copy(color = WeatherColors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

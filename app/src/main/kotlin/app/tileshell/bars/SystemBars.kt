package app.tileshell.bars

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tileshell.brand.Brand
import app.tileshell.brand.Glyph
import app.tileshell.cortana.CortanaMode
import app.tileshell.cortana.CortanaService
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.tokens.ShellType
import kotlinx.coroutines.delay
import java.util.TimeZone
import android.text.format.DateFormat as AndroidDateFormat
import java.util.Date

/** Bar rule (phase 01 Decisions): every shell-owned screen hides Samsung's bars; an edge swipe reveals them transiently. */
fun Activity.hideSystemBars() {
    // The insets controller lives on the decor view; touching decorView creates it when onCreate runs before setContent.
    window.decorView
    window.setDecorFitsSystemWindows(false)
    window.insetsController?.let {
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    }
    Diagnostics.add("bars", "${javaClass.simpleName}: system bars hidden (transient by swipe)")
}

object BarMetrics {
    /** R3 C4: status bar 28 epx (S1 360-epx canvas). */
    const val STATUS_EPX = 28
    /** X6 approximation: nav bar 48 epx (derived 47-48 epx from R6 S1 frames). */
    const val NAV_EPX = 48
}

private fun glyphStyle(color: Color, size: Int) = TextStyle(fontFamily = Brand.iconFont, fontSize = size.sp, color = color)

/** Drawn W10M status bar (X16 approximation for the glyph layout). */
@Composable
fun W10mStatusBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalShellColors.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableStateOf(batteryPercent(context)) }
    var charging by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf(wifiConnected(context)) }
    var cell by remember { mutableStateOf(hasCellService(context)) }
    // The process caches the default time zone and the 12/24-hour format, so the clock follows the system's
    // time zone, time and locale broadcasts.
    var clockGeneration by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ignored: Context?, intent: Intent?) {
                TimeZone.setDefault(null)
                clockGeneration++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        // The 12/24-hour setting changes with no broadcast, so it is observed directly.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { clockGeneration++ }
        }
        runCatching {
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }
    // android.text.format.DateFormat honours the user's 12/24-hour setting; java.text.DateFormat only the locale.
    val timeFormat = remember(clockGeneration) { AndroidDateFormat.getTimeFormat(context) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = batteryPercent(context)
            charging = isCharging(context)
            wifi = wifiConnected(context)
            cell = hasCellService(context)
            delay(1000)
        }
    }
    Row(
        modifier.fillMaxWidth().height(BarMetrics.STATUS_EPX.dp).padding(horizontal = 12.dp).testTag("w10m_status_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cell) BasicText(Glyph.CELL_0, style = glyphStyle(colors.text, 15))
        Spacer(Modifier.width(6.dp))
        BasicText(if (wifi) Glyph.WIFI_1 else Glyph.WIFI_OFF, style = glyphStyle(colors.text, 15))
        Spacer(Modifier.weight(1f))
        BasicText(batteryGlyph(battery, charging), style = glyphStyle(colors.text, 15))
        Spacer(Modifier.width(6.dp))
        BasicText(timeFormat.format(now), style = ShellType.caption.copy(color = colors.text))
    }
}

private fun batteryGlyph(percent: Int, charging: Boolean): String = when {
    charging -> Glyph.BATTERY_CHARGE
    else -> when ((percent + 5) / 10) {
        0 -> Glyph.BATTERY_LEVEL_0; 1 -> Glyph.BATTERY_LEVEL_1; 2 -> Glyph.BATTERY_LEVEL_2; 3 -> Glyph.BATTERY_LEVEL_3
        4 -> Glyph.BATTERY_LEVEL_4; 5 -> Glyph.BATTERY_LEVEL_5; 6 -> Glyph.BATTERY_LEVEL_6; 7 -> Glyph.BATTERY_LEVEL_7
        8 -> Glyph.BATTERY_LEVEL_8; 9 -> Glyph.BATTERY_LEVEL_9; else -> Glyph.BATTERY_LEVEL_10
    }
}

private fun batteryPercent(context: Context): Int =
    context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0

private fun isCharging(context: Context): Boolean {
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

private fun wifiConnected(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun hasCellService(context: Context): Boolean =
    context.getSystemService(TelephonyManager::class.java)?.simState == TelephonyManager.SIM_STATE_READY

/**
 * Drawn W10M nav bar: three equal slots (X17). Phase 01 drew Back (left) and Windows (centre) and left
 * the right slot empty; phase 03 ADDs Search there and wires it to Cortana (R6 §4.2).
 *
 * Phase 01's bar rule makes this one component shared by every shell screen, so the Search key shows
 * wherever the drawn nav bar does (review R3D-04). [onSearch] defaults to opening Cortana on its home
 * page and [onSearchHold] to opening it already listening; Cortana's own session passes its own, since
 * inside Cortana the key goes back to the home page rather than opening a second session.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun W10mNavBar(
    onBack: () -> Unit,
    onWindows: () -> Unit,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onSearchHold: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val search: () -> Unit = onSearch ?: { CortanaService.open(context, CortanaMode.HOME) }
    // R6 §4.2.3: press-and-hold opens Cortana already listening. W10M's hold time is not in R6, so the
    // build uses Android's own long-press timeout (approximation, H20).
    val searchHold: () -> Unit = onSearchHold ?: { CortanaService.open(context, CortanaMode.LISTENING) }
    Row(modifier.fillMaxWidth().height(BarMetrics.NAV_EPX.dp).background(Color.Black).testTag("w10m_nav_bar"), horizontalArrangement = Arrangement.SpaceEvenly) {
        NavSlot("nav_back", onBack) { BasicText(Glyph.ARROW_LEFT, style = glyphStyle(Color.White, 20)) }
        NavSlot("nav_windows", onWindows) { WindowsGlyph() }
        NavSlot("nav_search", search, searchHold) { BasicText(Glyph.SEARCH, style = glyphStyle(Color.White, 20)) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavSlot(
    tag: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    glyph: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.weight(1f).fillMaxHeight().testTag(tag)
            .let {
                when {
                    onClick == null -> it
                    onLongClick == null -> it.clickable(interaction, indication = null, onClick = onClick)
                    else -> it.combinedClickable(
                        interaction, indication = null, onClick = onClick, onLongClick = onLongClick,
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/** Windows key glyph: four panes (branding module asset, A10). 20 epx (X17). */
@Composable
fun WindowsGlyph(sizeEpx: Int = 20, color: Color = Color.White) {
    Box(Modifier.size(sizeEpx.dp).drawBehind {
        val gap = size.width * 0.08f
        val cell = (size.width - gap) / 2f
        for (r in 0..1) for (c in 0..1) {
            drawRect(color, topLeft = Offset(c * (cell + gap), r * (cell + gap)), size = Size(cell, cell))
        }
    })
}

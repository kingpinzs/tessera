package app.tileshell.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.tileshell.bars.BarMetrics
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.bars.hideSystemBars
import app.tileshell.brand.Glyph
import app.tileshell.onboarding.ChecklistPage
import app.tileshell.start.SlotPicker
import app.tileshell.tiles.Slot
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.motion.Motion

enum class SettingsPage { HOME, START_THEME, TILE_APPS, LIVE_TILE_ACCESS, CHECKLIST, DIAGNOSTICS, ABOUT }

/** An entry on the Settings page stack: a page, or the slot app picker opened from Tile apps (its own list, not scrolled by the page). */
private sealed interface Route {
    data class Page(val page: SettingsPage) : Route
    data class Picker(val slot: Slot) : Route
}

/** The W10M Settings hub (build task 13). Every shell screen follows the bar rule. */
class SettingsActivity : ComponentActivity() {
    private val stack = mutableStateListOf<Route>(Route.Page(SettingsPage.HOME))

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        intent?.getStringExtra(EXTRA_PAGE)?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }?.let { if (it != SettingsPage.HOME) stack += Route.Page(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        setContent {
            ShellRoot {
                val colors = LocalShellColors.current
                Box(Modifier.fillMaxSize().background(colors.background).semantics { testTagsAsResourceId = true }) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth().padding(top = BarMetrics.STATUS_EPX.dp)) {
                            val route = stack.last()
                            PageTransition(route) {
                                when (route) {
                                    is Route.Picker -> SlotPicker(route.slot) { goBack() }
                                    is Route.Page -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                        when (route.page) {
                                            SettingsPage.HOME -> HomePage { stack += Route.Page(it) }
                                            SettingsPage.START_THEME -> StartThemePage()
                                            SettingsPage.TILE_APPS -> TileAppsPage { stack += Route.Picker(it) }
                                            SettingsPage.LIVE_TILE_ACCESS -> LiveTileAccessPage()
                                            SettingsPage.CHECKLIST -> ChecklistPage()
                                            SettingsPage.DIAGNOSTICS -> DiagnosticsPage()
                                            SettingsPage.ABOUT -> AboutPage()
                                        }
                                    }
                                }
                            }
                        }
                        W10mNavBar(onBack = { goBack() }, onWindows = {
                            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                    }
                    W10mStatusBar()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Opening a specific page while Settings is already running (e.g. the checklist from another shell screen).
        intent.getStringExtra(EXTRA_PAGE)?.let { runCatching { SettingsPage.valueOf(it) }.getOrNull() }?.let { page ->
            stack.clear()
            stack += Route.Page(SettingsPage.HOME)
            if (page != SettingsPage.HOME) stack += Route.Page(page)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun goBack() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else finish()
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

/** X7 approximation: page-to-page = the Start entrance form (scale 0.78 -> 1 with fade over ≈217 ms). */
@Composable
private fun PageTransition(page: Any, content: @Composable () -> Unit) {
    val progress = remember(page) { Animatable(0f) }
    LaunchedEffect(page) { progress.animateTo(1f, tween(Motion.PAGE_ENTER_MS, easing = LinearEasing)) }
    val elapsed = progress.value * Motion.PAGE_ENTER_MS
    Box(Modifier.fillMaxSize().graphicsLayer {
        val s = Motion.sampleFrames(Motion.entranceScaleFrames, elapsed)
        scaleX = s; scaleY = s
        alpha = Motion.sampleFrames(Motion.entranceAlphaFrames, elapsed)
    }) { content() }
}

@Composable
private fun HomePage(open: (SettingsPage) -> Unit) {
    PageHeader(Glyph.SETTINGS, "Start settings")
    TwoLineItem(Glyph.PALETTE, "Start + theme", "Background, accent colour, tiles, press effect", "settings_start_theme") { open(SettingsPage.START_THEME) }
    TwoLineItem(Glyph.APPS, "Tile apps", "Choose the apps behind Mail, Music, Maps and more", "settings_tile_apps") { open(SettingsPage.TILE_APPS) }
    TwoLineItem(Glyph.APPS, "Live tile access", "Apps that update their own tiles", "settings_live_tile_access") { open(SettingsPage.LIVE_TILE_ACCESS) }
    TwoLineItem(Glyph.CHECKMARK, "Setup checklist", "Home, permissions and live tile health", "settings_checklist") { open(SettingsPage.CHECKLIST) }
    TwoLineItem(Glyph.DOCUMENT, "Diagnostics", "What the shell recorded", "settings_diagnostics") { open(SettingsPage.DIAGNOSTICS) }
    TwoLineItem(Glyph.INFO, "About", "Version and licences", "settings_about") { open(SettingsPage.ABOUT) }
}

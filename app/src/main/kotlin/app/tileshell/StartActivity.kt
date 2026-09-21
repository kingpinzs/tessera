package app.tileshell

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.window.SplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.tileshell.apps.AppCatalog
import app.tileshell.applist.AppListPage
import app.tileshell.bars.BarMetrics
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.start.BackHistory
import app.tileshell.start.PlacedTile
import app.tileshell.start.SlotPicker
import app.tileshell.start.StartAnimation
import app.tileshell.start.SecondaryPinPrompt
import app.tileshell.start.StartEditState
import app.tileshell.start.StartPage
import app.tileshell.start.TileTarget
import app.tileshell.tiles.ShellTiles
import app.tileshell.tiles.api.SecondaryTiles
import app.tileshell.tiles.Slot
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.motion.Motion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** The HOME activity: Start and the app list on one pivot, the drawn W10M bars, launch / return motion. */
class StartActivity : ComponentActivity() {
    /** true = Home pressed while Start was already in front (scroll to top); false = Home from elsewhere (page 0 only). */
    private val homeEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    // In front means resumed and not stopped since; Android pauses Start before delivering a Home intent, so
    // RESUMED can't be read in onNewIntent.
    private var inFront = false
    private var animation by mutableStateOf(StartAnimation())
    // Each run of the exit or entrance animation has its own token, so updating the animation state every frame
    // never restarts (and freezes) the effect that drives it.
    private var exitToken by mutableStateOf(0)
    private var entranceToken by mutableStateOf(0)
    private var pickerSlot by mutableStateOf<Slot?>(null)
    private var returningFromLaunch = false
    private var page by mutableStateOf(0)
    private val backEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Phase 02 edit mode: held here so Back, Home and the pivot can see it. */
    private val edit = StartEditState()

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        AppCatalog.get(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { backEvents.tryEmit(Unit) }
        })
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    StartHost()
                }
            }
        }
        Diagnostics.add("start", "StartActivity created")
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun StartHost() {
        val scroll = rememberScrollState()
        val pager = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            homeEvents.collect { alreadyInFront ->
                pickerSlot = null
                // Windows leaves edit mode when Start is re-entered (X20's sibling; agent).
                if (edit.active) edit.requestExit()
                pager.animateScrollToPage(0, animationSpec = tween(Motion.PIVOT_SETTLE_MS))
                if (alreadyInFront) {
                    // X20 approximation: Home while Start is showing scrolls Start to the top.
                    scroll.animateScrollTo(0)
                }
                Diagnostics.add("start", "home: page 0" + if (alreadyInFront) ", scrolled to top" else "")
            }
        }
        LaunchedEffect(Unit) {
            backEvents.collect {
                when {
                    // R6 §4.1.7 (H9): Back exits edit mode like a tap, and an expanded folder stays expanded.
                    edit.active -> edit.requestExit()
                    edit.expandedFolder != null -> edit.expandedFolder = null
                    pickerSlot != null -> pickerSlot = null
                    pager.currentPage == 1 -> pager.animateScrollToPage(0, animationSpec = tween(Motion.PIVOT_SETTLE_MS))
                    else -> backOnStart()
                }
            }
        }
        LaunchedEffect(pager.currentPage) { page = pager.currentPage }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // X13 approximation: follows the finger 1:1, settles with an ease-out over 250 ms.
                        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(Motion.PIVOT_SETTLE_MS)),
                        snapPosition = SnapPosition.Start,
                        beyondViewportPageCount = 1,
                        // Edit mode holds Start still: a drag across the screen moves a tile, not the pivot (agent).
                        userScrollEnabled = !edit.active,
                    ) { index ->
                        if (index == 0) {
                            StartPage(scroll, animation, edit) { tile -> onTileTap(tile) }
                        } else {
                            AppListPage(onLaunch = { entry, bounds -> launchApp(TileTarget.App(entry), bounds, null) })
                        }
                    }
                    // The pin confirmation band sits at the top of the screen, under the drawn status bar (H22).
                    val pinRequest by SecondaryTiles.pending.collectAsState()
                    pinRequest?.let { request ->
                        SecondaryPinPrompt(request, Modifier.align(Alignment.TopCenter).padding(top = BarMetrics.STATUS_EPX.dp))
                    }
                    // The picker is a page between the drawn bars (bar rule): the status bar draws over its top inset.
                    pickerSlot?.let { slot ->
                        Box(Modifier.fillMaxSize().background(LocalShellColors.current.background).padding(top = BarMetrics.STATUS_EPX.dp)) {
                            SlotPicker(slot, onDone = { pickerSlot = null })
                        }
                    }
                }
                W10mNavBar(
                    onBack = { backEvents.tryEmit(Unit) },
                    onWindows = { homeEvents.tryEmit(true) },
                )
            }
            W10mStatusBar(Modifier.align(Alignment.TopCenter))
        }

        // Drive exit / entrance animations frame by frame.
        LaunchedEffect(exitToken) {
            if (exitToken == 0) return@LaunchedEffect
            val tapped = animation.exitTappedId
            val start = withFrameMillis { it }
            while (true) {
                val t = withFrameMillis { it } - start
                animation = animation.copy(exitElapsedMs = t.toFloat())
                if (t >= Motion.EXIT_TOTAL_MS + Motion.EXIT_TAPPED_EXTRA_MS) break
            }
            pendingLaunch?.invoke()
            pendingLaunch = null
            Diagnostics.add("motion", "start exit finished tile=$tapped")
        }
        LaunchedEffect(entranceToken) {
            if (entranceToken == 0) return@LaunchedEffect
            val start = withFrameMillis { it }
            while (true) {
                val t = withFrameMillis { it } - start
                animation = StartAnimation(entranceElapsedMs = t.toFloat())
                if (t >= Motion.ENTRANCE_FADE_MS) break
            }
            animation = StartAnimation()
            Diagnostics.add("motion", "start entrance finished")
        }
    }

    private var pendingLaunch: (() -> Unit)? = null

    private fun onTileTap(tile: PlacedTile) {
        when (val target = tile.target) {
            is TileTarget.Unassigned -> pickerSlot = target.slot
            // R6 §1.6.6 / §1.6.7 (H15 / H16): a tap opens the folder's band, another tap folds it away.
            is TileTarget.Folder -> {
                edit.expandedFolder = if (edit.expandedFolder == target.folderId) null else target.folderId
                edit.naming = false
                Diagnostics.add("start", "folder ${target.folderId} " + if (edit.expandedFolder == null) "collapsed" else "expanded")
            }
            else -> {
                val bounds = Rect(tile.xPx.toInt(), tile.yPx.toInt(), (tile.xPx + tile.wPx).toInt(), (tile.yPx + tile.hPx).toInt())
                pendingLaunch = { launchApp(target, bounds, tile.model.id) }
                animation = StartAnimation(exitElapsedMs = 0f, exitTappedId = tile.model.id)
                exitToken++
            }
        }
    }

    private fun launchApp(target: TileTarget, bounds: Rect?, tileId: String?) {
        // Splash style request (N-09): the launcher can only ask for the solid-colour splash.
        val options = ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR).toBundle()
        returningFromLaunch = true
        when (target) {
            is TileTarget.App -> AppCatalog.get(this).launch(target.entry, bounds, options)
            is TileTarget.Shell -> when (target.name) {
                ShellTiles.WEATHER -> startActivity(Intent(this, app.tileshell.weather.WeatherActivity::class.java), options)
                ShellTiles.SETTINGS -> startActivity(Intent(this, app.tileshell.settings.SettingsActivity::class.java), options)
                else -> Diagnostics.add("launch", "shell tile ${target.name} has no target")
            }
            is TileTarget.Unassigned, is TileTarget.Folder -> Unit
            // The seam starts the OWNER's launcher activity with the tile's TILE_ID / ARGUMENTS extras (R5 §4b).
            is TileTarget.Secondary -> SecondaryTiles.launch(this, target.owner, target.tileId, bounds, options)
        }
        Diagnostics.add("launch", "tile=$tileId target=$target")
    }

    private fun backOnStart() {
        val entry = BackHistory.findTarget(this, AppCatalog.get(this))
        if (entry == null) return // X12: nothing happens
        pendingLaunch = { launchApp(TileTarget.App(entry), null, "back") }
        animation = StartAnimation(exitElapsedMs = 0f, exitTappedId = "back")
        exitToken++
    }

    override fun onPause() {
        super.onPause()
        SecondaryTiles.setStartVisible(false)
    }

    override fun onStop() {
        super.onStop()
        inFront = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeEvents.tryEmit(inFront)
        }
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        // A pin request that arrived while Start was away is shown now, never over another app (R5 §1.9).
        SecondaryTiles.setStartVisible(true)
        hideSystemBars()
        // A default app (dialer, SMS, browser) may have been changed elsewhere while Start was away.
        app.tileshell.tiles.SlotDefaults.refresh()
        if (returningFromLaunch) {
            returningFromLaunch = false
            animation = StartAnimation(entranceElapsedMs = 0f)
            entranceToken++
            Diagnostics.add("motion", "start entrance")
        }
    }
}

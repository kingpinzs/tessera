package app.tileshell

import androidx.compose.foundation.interaction.DragInteraction
import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import app.tileshell.ui.MotionTrace
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusProperties
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
import app.tileshell.start.FolderNameBox
import app.tileshell.start.SecondaryPinPrompt
import app.tileshell.start.StartEditState
import app.tileshell.start.StartPage
import app.tileshell.start.StartPages
import app.tileshell.start.TileTarget
import app.tileshell.start.podbay.PodBayPage
import app.tileshell.start.podbay.PodBayRequests
import app.tileshell.start.podbay.PodTarget
import app.tileshell.tiles.ShellTiles
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.UseCounts
import app.tileshell.tiles.api.SecondaryTiles
import app.tileshell.tiles.Slot
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.motion.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the one pager-moving coroutine for keys takes (L13-4): Home, or phase 14's look at a pending pod-bay request. */
private sealed interface HomeEvent {
    /** true = Home pressed while Start was already in front (scroll to top); false = Home from elsewhere (Start only). */
    data class Home(val alreadyInFront: Boolean) : HomeEvent

    /** Phase 14 (r3 D1): consult [PodBayRequests]; open or close the pod bay if a request is pending. */
    data object PodBayCheck : HomeEvent
}

/** The HOME activity: the pod bay, Start and the app list on one pivot, the drawn W10M bars, launch / return motion. */
class StartActivity : ComponentActivity() {
    // Room for a Home and a pod-bay check in the same instant (the session's HOME intent and the focus coming back).
    private val homeEvents = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)

    /**
     * The page a key or voice scroll is taking the pager to, and why ("back", "home", "voice", "voice (doors)"), consumed
     * by the settle that lands on that page; a settle anywhere else is a swipe. Cleared when the scroll is interrupted or
     * moves nothing, so a later swipe is never credited to a key.
     */
    private var pendingCause: Pair<Int, String>? = null
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
    /** The tile that launched something, promoted above the bottom row when Start comes back. */
    private var pendingRecent: app.tileshell.tiles.TileKey? = null
    private var page by mutableStateOf(0)
    private val backEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Phase 02 edit mode: held here so Back, Home and the pivot can see it. */
    private val edit = StartEditState()
    /** Phase 11: runs a satellite's App Shortcut. */
    private val quickSource by lazy { app.tileshell.start.QuickSource(this) }
    /** Phase 12: the wizard was evaluated in onCreate, so the first onResume does not evaluate (and log) it again. */
    private var wizardEvaluatedAtCreate = false
    /** Phase 12 (T12-6): Start composes under the X7 page motion when the wizard ends, never behind it. */
    private var cameFromWizard by mutableStateOf(false)

    /**
     * Phase 11: `onShortcutsChanged` for the held app while its burst is open closes the burst — `removed` when the
     * app is gone (an uninstall sends this too), else `shortcuts changed`.
     */
    private val shortcutsChanged: (String, android.os.UserHandle) -> Unit = { pkg, user ->
        val open = edit.quick.burst
        val held = open?.satellites?.firstOrNull()
        if (held != null && held.pkg == pkg && held.user == user) {
            val gone = AppCatalog.get(this).packageState(pkg) == AppCatalog.PackageState.GONE
            edit.quick.close(if (gone) app.tileshell.start.CloseReason.REMOVED else app.tileshell.start.CloseReason.SHORTCUTS_CHANGED)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        AppCatalog.get(this).addShortcutsChangedListener(shortcutsChanged)
        edit.quick.onSatelliteTap = { burst, index, square -> launchSatellite(burst, index, square) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { backEvents.tryEmit(Unit) }
        })
        // Phase 12: the Visibility rule is evaluated before Start's first frame, so a phone missing a grant never sees
        // Start drawn behind the wizard.
        if (!app.tileshell.onboarding.SetupWizard.showing) app.tileshell.onboarding.SetupWizard.evaluate(this)
        wizardEvaluatedAtCreate = true
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
        val pager = rememberPagerState(initialPage = StartPages.START, pageCount = { StartPages.COUNT })
        val scope = rememberCoroutineScope()

        // L13-4: a touch that lands during the pivot (or Home's scroll to the top) interrupts the animation, which throws a
        // CancellationException. It used to end the collect with it, so every later Back and Home, the drawn keys
        // included, did nothing until the activity was recreated. The collectors now catch that interruption and go on,
        // and they still take one key at a time: a second Back during the pivot runs when it ends (Back on Start).
        LaunchedEffect(Unit) {
            homeEvents.collect { event ->
                // Phase 12: the Windows key and Home do nothing while the wizard shows — Start is behind it, and Home is
                // where you are (Decisions "Bars"). A pod-bay request is not taken then either: it waits for the wizard
                // to end (phase 14 T14-7), when onWizardEnded() asks again.
                if (app.tileshell.onboarding.SetupWizard.showing) {
                    // The wizard holds a pending request for as long as it shows; without this it would lapse (gate r1 S2).
                    if (event is HomeEvent.PodBayCheck) PodBayRequests.waitForWizard()
                    return@collect
                }
                when (event) {
                    is HomeEvent.Home -> home(pager, scroll, event.alreadyInFront)
                    HomeEvent.PodBayCheck -> podBayCheck(pager)
                }
            }
        }
        LaunchedEffect(Unit) {
            backEvents.collect {
                when {
                    // Phase 12: Back = the wizard's previous step (nothing on the first).
                    app.tileshell.onboarding.SetupWizard.showing -> app.tileshell.onboarding.SetupWizard.back(this@StartActivity)
                    // Phase 11: Back closes an open burst only; the next Back exits edit mode (R6 §4.1.7).
                    edit.quick.active -> edit.quick.close(app.tileshell.start.CloseReason.BACK)
                    // R6 §4.1.7 (H9): Back exits edit mode like a tap, and an expanded folder stays expanded.
                    edit.active -> edit.requestExit()
                    edit.expandedFolder != null -> edit.expandedFolder = null
                    pickerSlot != null -> pickerSlot = null
                    // Phase 14: Back from the pod bay is Start, as it is from the app list; the Back history rule
                    // (backOnStart) applies only when Start is the current page (an ADD to phase 01's rule, H8).
                    pager.currentPage == StartPages.APP_LIST || pager.currentPage == StartPages.POD_BAY ->
                        scrollForKey(pager, StartPages.START, "back")
                    else -> backOnStart()
                }
            }
        }
        LaunchedEffect(Unit) {
            // A ring ends edit mode (phase 15 Edge cases: "while Start is in edit mode (edit mode ends)"). The ring toast
            // is an overlay, so Start stays resumed beneath it and hears no Home or Back to leave by.
            app.tileshell.clock.RingService.state.collect { ring ->
                if (ring != null && edit.active) {
                    edit.requestExit()
                    Diagnostics.add("start", "edit mode ends: a ring started")
                }
            }
        }
        LaunchedEffect(pager.currentPage) { page = pager.currentPage }
        // Phase 14 (T14-8): `[start] page=<name>` each time the pager settles on a page, and the pod bay's
        // `[podbay] opened by …` / `closed by …` with the cause a key or voice scroll left in [pendingCause].
        LaunchedEffect(pager) {
            var last: Int? = null
            snapshotFlow { pager.settledPage }.collect { now ->
                if (now == last) return@collect
                val from = last
                last = now
                Diagnostics.add("start", "page=${StartPages.name(now)}")
                if (from == null) return@collect
                val cause = pendingCause?.takeIf { it.first == now }?.second ?: "swipe"
                pendingCause = null
                when {
                    now == StartPages.POD_BAY -> Diagnostics.add("podbay", "opened by $cause")
                    from == StartPages.POD_BAY -> Diagnostics.add("podbay", "closed by $cause")
                }
            }
        }
        // Phase 13 (C-5, T13-27, E8): the pivot's settle after a swipe is released, on the shell's own clock —
        // `[motion] pivot`, value = how far the pager has gone from where the finger let go to where it settles.
        LaunchedEffect(pager) {
            pager.interactionSource.interactions.collect { interaction ->
                if (interaction !is DragInteraction.Stop && interaction !is DragInteraction.Cancel) return@collect
                val t0 = SystemClock.uptimeMillis()
                val from = pager.currentPage + pager.currentPageOffsetFraction
                val frames = ArrayList<Pair<Long, Float>>()
                do {
                    val nanos = withFrameNanos { it }
                    frames += nanos to (pager.currentPage + pager.currentPageOffsetFraction)
                } while (pager.isScrollInProgress)
                val to = frames.last().second
                val trace = MotionTrace("pivot", t0)
                frames.forEach { (nanos, at) ->
                    trace.frame(nanos, if (to == from) 1f else ((at - from) / (to - from)).coerceAtMost(1f))
                }
                Diagnostics.add("motion", trace.message())
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (app.tileshell.onboarding.SetupWizard.showing) {
                        // Phase 12 (T12-6, T12-8): the wizard sits above everything Start draws, and while it shows the
                        // pager, the burst layer, the pin band and the picker are not composed at all; the feeds keep running.
                        app.tileshell.onboarding.WizardHost(onEnd = { onWizardEnded() })
                    } else if (cameFromWizard) {
                        app.tileshell.onboarding.WizardPageTransition("start") { StartPivot(pager, scroll) }
                    } else {
                        StartPivot(pager, scroll)
                    }
                }
                W10mNavBar(
                    onBack = { backEvents.tryEmit(Unit) },
                    onWindows = { homeEvents.tryEmit(HomeEvent.Home(true)) },
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

    /** Start and the app list on one pivot, with what is drawn above it (folder name box, pin band, slot picker). */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun StartPivot(pager: androidx.compose.foundation.pager.PagerState, scroll: androidx.compose.foundation.ScrollState) {
        val view = androidx.compose.ui.platform.LocalView.current
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                // Phase 14 (Decisions "Gesture navigation"): a swipe from Android's left gesture inset is never the pod bay.
                modifier = Modifier.fillMaxSize().pointerInput(edit.active) { if (!edit.active) edgeGuard(view) },
                // X13 approximation: follows the finger 1:1, settles with an ease-out over 250 ms.
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(Motion.PIVOT_SETTLE_MS)),
                snapPosition = SnapPosition.Start,
                beyondViewportPageCount = 1,
                // Edit mode holds Start still: a drag across the screen moves a tile, not the pivot (agent).
                userScrollEnabled = !edit.active,
            ) { index ->
                when (index) {
                    StartPages.START -> StartPage(scroll, animation, edit) { tile -> onTileTap(tile) }
                    // A pivot page that is not showing takes no focus. Without this, dismissing a text
                    // field on Start (the folder name box) handed focus to the app list's search field,
                    // and the pager dutifully scrolled that page into view — Start swung away on its own.
                    // Phase 14: the pod bay carries the same guard, so nothing on it can swing the pager either.
                    StartPages.POD_BAY -> Box(
                        Modifier
                            .fillMaxSize()
                            .focusGroup()
                            .focusProperties { canFocus = pager.currentPage == StartPages.POD_BAY },
                    ) {
                        PodBayPage(onLaunch = { pod, target, bounds -> podLaunch(pod, target, bounds) })
                    }
                    else -> Box(
                        Modifier
                            .fillMaxSize()
                            .focusGroup()
                            .focusProperties { canFocus = pager.currentPage == StartPages.APP_LIST },
                    ) {
                        AppListPage(onLaunch = { entry, bounds -> launchApp(TileTarget.App(entry), bounds, null) })
                    }
                }
            }
            // The folder name box (R6 §1.7.2): drawn here, above the pivot, so focusing it cannot make
            // the pager bring it into view and swing Start over to the app list.
            if (edit.naming) {
                val folderId = edit.expandedFolder
                val store = app.tileshell.tiles.LayoutStore.get(this@StartActivity)
                if (folderId != null) {
                    FolderNameBox(
                        initial = store.layout.value.folders[folderId]?.name.orEmpty(),
                        yPx = edit.nameBoxYPx,
                    ) { name ->
                        store.renameFolder(folderId, name)
                        edit.naming = false
                    }
                } else {
                    edit.naming = false
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
    }

    private var pendingLaunch: (() -> Unit)? = null

    /**
     * Phase 14 (Decisions "Gesture navigation"): a horizontal pan that STARTS inside Android's left system-gesture inset
     * is not the pager's. Every shell screen hides the system bars (phase 01's bar rule), and on an immersive window
     * Android answers the first edge swipe by revealing the bars and still hands the drag to the app — found by E12 on
     * the AVD's gestural overlay, where it opened the pod bay (qa/phase-14/BUILD-NOTES/e12-probe). The guard takes the
     * pan in the Initial pass once it is horizontal past the touch slop, so the pager (a child in the Main pass) sees it
     * consumed and never starts; a tap or a vertical scroll from that strip is untouched. No
     * systemGestureExclusionRects: the edge stays Android's. With 3-button navigation the inset is 0 and nothing happens.
     */
    private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.edgeGuard(view: android.view.View) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val inset = view.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemGestures())?.left ?: 0
            if (inset <= 0 || down.position.x >= inset) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            var taken = false
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!taken) {
                    val d = change.position - change.previousPosition
                    dx += d.x
                    dy += d.y
                    if (kotlin.math.abs(dx) > viewConfiguration.touchSlop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        taken = true
                        Diagnostics.add("start", "edge pan from x=${down.position.x.toInt()} inside the gesture inset ($inset px): not the pager's")
                    }
                }
                if (taken) change.consume()
            }
        }
    }

    /** Home: Start, the burst and edit mode closed, and X20's scroll to the top when Start was already in front. */
    private suspend fun home(
        pager: androidx.compose.foundation.pager.PagerState,
        scroll: androidx.compose.foundation.ScrollState,
        alreadyInFront: Boolean,
    ) {
        pickerSlot = null
        // Phase 11: Home closes an open burst and edit mode with it (the HOME intent and the drawn
        // Windows key both arrive here, T11-16).
        edit.quick.close(app.tileshell.start.CloseReason.HOME)
        // Windows leaves edit mode when Start is re-entered (X20's sibling; agent).
        if (edit.active) edit.requestExit()
        val done = scrollForKey(pager, StartPages.START, "home") {
            if (alreadyInFront) {
                // X20 approximation: Home while Start is showing scrolls Start to the top.
                scroll.animateScrollTo(0)
            }
        }
        if (done) Diagnostics.add("start", "home: page ${StartPages.name(StartPages.START)}" + if (alreadyInFront) ", scrolled to top" else "")
    }

    /**
     * Phase 14 (r3 D1): takes the pending pod-bay request, if any — none does nothing and never scrolls. Otherwise Home's
     * housekeeping (the picker, the burst, edit mode), then the pivot's own settle to the pod bay (open) or to Start
     * (close); on the page already, the request is taken and nothing moves. No scroll-to-top and no home line.
     */
    private suspend fun podBayCheck(pager: androidx.compose.foundation.pager.PagerState) {
        val request = PodBayRequests.take() ?: return
        pickerSlot = null
        edit.quick.close(app.tileshell.start.CloseReason.HOME)
        if (edit.active) edit.requestExit()
        // Right after the wizard ends the pager is composed on the next frame; wait for its first layout.
        withTimeoutOrNull(2_000) { snapshotFlow { pager.layoutInfo.visiblePagesInfo.isNotEmpty() }.first { it } }
        val target = if (request.open) StartPages.POD_BAY else StartPages.START
        if (pager.currentPage == target && !pager.isScrollInProgress) {
            Diagnostics.add("podbay", "voice ${if (request.open) "open" else "close"}: already on ${StartPages.name(target)}, nothing moves")
            return
        }
        scrollForKey(pager, target, if (!request.open) "voice" else if (request.doors) "voice (doors)" else "voice")
    }

    /**
     * A key's (or the voice request's) scroll to [target] with the pivot's settle, recording why for the settle line;
     * [then] runs after the scroll (Home's scroll to the top). Returns false when interrupted (L13-4).
     */
    private suspend fun scrollForKey(
        pager: androidx.compose.foundation.pager.PagerState,
        target: Int,
        cause: String,
        then: suspend () -> Unit = {},
    ): Boolean {
        val from = pager.currentPage
        pendingCause = if (from != target) target to cause else null
        val done = untilInterrupted(cause.substringBefore(' ')) {
            pager.animateScrollToPage(target, animationSpec = tween(Motion.PIVOT_SETTLE_MS))
            then()
        }
        if (!done) pendingCause = null
        return done
    }

    /**
     * Phase 14 (Decisions "The pods", r3 D8 / D9, T14-13): a pod's tap opens its app or page as the app list's do —
     * [launchApp] with no tile key, so no Start exit and nothing promoted — and a tap that opens nothing says why.
     */
    private fun podLaunch(pod: app.tileshell.prefs.PodId, target: PodTarget, bounds: Rect?) {
        when (target) {
            is PodTarget.SlotApp -> {
                val entry = app.tileshell.tiles.SlotResolver(this, AppCatalog.get(this))
                    .resolve(target.slot, app.tileshell.tiles.LayoutStore.get(this).layout.value.explicitSlots)
                if (entry == null) {
                    Diagnostics.add("podbay", "launch ${pod.id} failed: slot unassigned")
                    // The tap follows the tile: an unassigned slot opens its picker (StartActivity onTileTap).
                    pickerSlot = target.slot
                    return
                }
                guardedLaunch(pod, entry.component.flattenToShortString()) { launchApp(TileTarget.App(entry), bounds, null) }
            }
            PodTarget.Weather -> guardedLaunch(pod, "app.tileshell/.weather.WeatherActivity") {
                launchApp(TileTarget.Shell(ShellTiles.WEATHER), bounds, null)
            }
            PodTarget.TessReminders -> {
                val why = app.tileshell.cortana.CortanaService.whyNoSession(this)
                if (app.tileshell.cortana.CortanaService.open(
                        this, app.tileshell.cortana.CortanaMode.HOME, app.tileshell.cortana.CortanaDestinationKey.REMINDERS,
                    )
                ) {
                    Diagnostics.add("podbay", "launch ${pod.id} -> app.tileshell/.cortana.CortanaService (Reminders)")
                } else {
                    Diagnostics.add("podbay", "launch ${pod.id} failed: no session: ${why ?: "not shown"}")
                }
            }
        }
    }

    /** Runs a pod's launch; an app that cannot be started is logged, and Start does not wait for a return. */
    private fun guardedLaunch(pod: app.tileshell.prefs.PodId, component: String, launch: () -> Unit) {
        try {
            launch()
            Diagnostics.add("podbay", "launch ${pod.id} -> $component")
        } catch (e: android.content.ActivityNotFoundException) {
            returningFromLaunch = false
            Diagnostics.add("podbay", "launch ${pod.id} failed: ActivityNotFoundException")
        }
    }

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
                pendingLaunch = { launchApp(target, bounds, tile.model.id, tile.key) }
                animation = StartAnimation(exitElapsedMs = 0f, exitTappedId = tile.model.id)
                exitToken++
            }
        }
    }

    private fun launchApp(target: TileTarget, bounds: Rect?, tileId: String?, key: TileKey? = null) {
        // Splash style request (N-09): the launcher can only ask for the solid-colour splash.
        val options = ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR).toBundle()
        returningFromLaunch = true
        // Auto-sizing counts opens, and this is the one place a tile opens anything (INDEX Change Log
        // 2026-09-21 item 1). Counted here rather than in AppCatalog.launch so that a shell tile and a
        // secondary tile count too, and so that an app opened from the app list — which is not a tile
        // being used — does not.
        key?.let {
            UseCounts.get(this).record(it)
            // The last app you opened is shown in the row above the bottom tile row (INDEX Change Log
            // 2026-09-21 item 7). A dock tile is never found in the grid order, so opening one of the
            // three bottom apps leaves the grid alone on its own, with nothing to special-case.
            //
            // Recorded here but APPLIED on the way back, in onResume, for the same reason auto-sizing is:
            // the tile must not leave the grid under the finger that just tapped it. It now moves to a
            // fixed row at the bottom of the screen rather than to the end of the grid, so doing it at
            // tap time would pull the tile out from under the launch animation that is scaling it.
            pendingRecent = it
        }
        when (target) {
            is TileTarget.App -> AppCatalog.get(this).launch(target.entry, bounds, options)
            is TileTarget.Shell -> when (target.name) {
                ShellTiles.WEATHER -> startActivity(Intent(this, app.tileshell.weather.WeatherActivity::class.java), options)
                ShellTiles.SETTINGS -> startActivity(Intent(this, app.tileshell.settings.SettingsActivity::class.java), options)
                // Phase 03: the tile opens the same session the Search key, the assist gesture and the
                // side key open — there is no second Cortana screen.
                ShellTiles.CORTANA -> app.tileshell.cortana.CortanaService.open(this, app.tileshell.cortana.CortanaMode.HOME)
                else -> Diagnostics.add("launch", "shell tile ${target.name} has no target")
            }
            is TileTarget.Unassigned, is TileTarget.Folder -> Unit
            // The seam starts the OWNER's launcher activity with the tile's TILE_ID / ARGUMENTS extras (R5 §4b).
            is TileTarget.Secondary -> SecondaryTiles.launch(this, target.owner, target.tileId, bounds, options)
        }
        Diagnostics.add("launch", "tile=$tileId target=$target")
    }

    /**
     * Phase 11 (Decisions "Satellite tap", T11-24): a satellite runs where a tile's launch runs. The burst goes in
     * the Start exit's first frame, edit mode exits with R6 §1.5's exit, the Start exit plays as for a tile tap, and
     * `startShortcut` runs from [pendingLaunch] after the exit's last frame.
     */
    private fun launchSatellite(burst: app.tileshell.start.OpenBurst, index: Int, square: app.tileshell.start.QRect) {
        val sat = burst.satellites[index]
        edit.quick.close(app.tileshell.start.CloseReason.LAUNCH, animate = false)
        edit.requestExit()
        val bounds = Rect(square.l.toInt(), square.t.toInt(), square.r.toInt(), square.b.toInt())
        pendingLaunch = { launchShortcut(burst.key, sat, bounds) }
        animation = StartAnimation(exitElapsedMs = 0f, exitTappedId = burst.drawnId)
        exitToken++
    }

    /** launchApp's options and bookkeeping for a shortcut; a shortcut that cannot start plays the entrance at once. */
    private fun launchShortcut(key: app.tileshell.tiles.TileKey, sat: app.tileshell.start.SatelliteSpec, bounds: Rect) {
        val options = ActivityOptions.makeBasic().setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR).toBundle()
        when (val outcome = app.tileshell.start.QuickLaunch.run { quickSource.start(sat, bounds, options) }) {
            app.tileshell.start.QuickLaunchOutcome.Ok -> {
                Diagnostics.add("quick", "tap satellite ${sat.index} ${sat.pkg}/${sat.id}: startShortcut ok")
                returningFromLaunch = true
                UseCounts.get(this).record(key)
                // Applied on the way back, in onResume, never now: the tile must not leave the grid under the exit.
                pendingRecent = key
                // The app was opened by the shell: its "New" caption clears as on any shell launch (X11; G-D4).
                AppCatalog.get(this).notifyLaunched(sat.pkg, sat.user)
            }
            is app.tileshell.start.QuickLaunchOutcome.Failed -> {
                Diagnostics.add("quick", "tap satellite ${sat.index} ${sat.pkg}/${sat.id}: startShortcut failed ${outcome.error}")
                // Nothing opened, so nothing pauses Start: it would stay on the exit's last frame. Come back now.
                returningFromLaunch = false
                animation = StartAnimation(entranceElapsedMs = 0f)
                entranceToken++
                Diagnostics.add("motion", "start entrance")
            }
        }
    }

    /**
     * L13-4: runs a key's scroll, and returns false when something else took the pager or the scroll first (a touch, or
     * another key's pivot), which interrupts the animation with a CancellationException; a diagnostics line says so.
     * When the collector itself is cancelled (the activity going away), the exception is rethrown.
     */
    private suspend fun untilInterrupted(key: String, scroll: suspend () -> Unit): Boolean = try {
        scroll()
        true
    } catch (interrupted: CancellationException) {
        currentCoroutineContext().ensureActive()
        Diagnostics.add("start", "$key: animation interrupted")
        false
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
        // Phase 11: Start stopping (screen off, a call, any window in front) closes an open burst at once.
        edit.quick.close(app.tileshell.start.CloseReason.STOP, animate = false)
        inFront = false
    }

    override fun onDestroy() {
        AppCatalog.get(this).removeShortcutsChangedListener(shortcutsChanged)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Phase 14: Tess's session sends Home with a POD_BAY_CHECK signal when a pod-bay request is pending. The
            // extra carries no request — anyone may send it; it only makes Start consult PodBayRequests.
            homeEvents.tryEmit(if (intent.getBooleanExtra(EXTRA_POD_BAY_CHECK, false)) HomeEvent.PodBayCheck else HomeEvent.Home(inFront))
        }
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        // Phase 12: the Visibility rule on every resume while no run is in progress; a run in progress re-derives its steps
        // from live grant state (the dialogs and Settings pages all return through here).
        val wizard = app.tileshell.onboarding.SetupWizard
        when {
            wizardEvaluatedAtCreate -> wizardEvaluatedAtCreate = false
            wizard.showing -> reconcileWizard()
            else -> wizard.evaluate(this)
        }
        // A pin request that arrived while Start was away is shown now, never over another app (R5 §1.9) — and never
        // behind the wizard: it waits until Start is visible (T12-14).
        if (!wizard.showing) SecondaryTiles.setStartVisible(true)
        hideSystemBars()
        // A default app (dialer, SMS, browser) may have been changed elsewhere while Start was away.
        app.tileshell.tiles.SlotDefaults.refresh()
        comeBack("start entrance")
    }

    /**
     * L13-7: the Tess tile opens her session, a window over Start rather than an activity, so Start is never paused
     * and onResume never brings it back: once the session hid, Start stayed on its exit's last frame, with no tiles.
     * Start gets its window focus back when that window goes, and comes back then. A launch that did pause Start came
     * back in onResume, which cleared [returningFromLaunch] before the focus returned.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && returningFromLaunch) comeBack("start entrance (focus back)")
        // Phase 14 (r3 D1): Tess's session is a window over Start, so Start is never paused under it; when it goes —
        // after her reply, or dismissed mid-line — the focus comes back, and a pending pod-bay request is taken here.
        if (hasFocus) homeEvents.tryEmit(HomeEvent.PodBayCheck)
        // Phase 12: the keyboard picker is a system window, not an activity, so Start is never paused under it; the step
        // re-derives when the focus comes back instead.
        if (hasFocus && app.tileshell.onboarding.SetupWizard.showing) reconcileWizard()
    }

    private fun reconcileWizard() {
        app.tileshell.onboarding.SetupWizard.reconcile(
            this,
            rationale = { shouldShowRequestPermissionRationale(it) },
            granted = { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED },
        )
    }

    /** "Skip setup" or "Done": Start composes under the page motion, and a pin request that waited is shown now. */
    private fun onWizardEnded() {
        cameFromWizard = true
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) SecondaryTiles.setStartVisible(true)
        // Phase 14 (T14-7): a pod-bay request made while the wizard showed waited for this.
        homeEvents.tryEmit(HomeEvent.PodBayCheck)
    }

    /**
     * Phase 12 (build task 2, r3 V4): `adb shell dumpsys activity app.tileshell/.StartActivity` prints the diagnostics
     * ring through this already-exported activity — no new exported component — so a row can read the wizard's lines while
     * notification access (the listener's own dump) is revoked.
     */
    override fun dump(prefix: String, fd: java.io.FileDescriptor?, writer: java.io.PrintWriter, args: Array<out String>?) {
        super.dump(prefix, fd, writer, args)
        Diagnostics.dump(writer)
    }

    companion object {
        /** Phase 14: the signal on the HOME intent Tess's session sends when a pod-bay request is pending. */
        const val EXTRA_POD_BAY_CHECK = "app.tileshell.extra.POD_BAY_CHECK"
    }

    /**
     * Start comes back: from onResume, or from the focus returning after a launch that never paused it (L13-7). The
     * tile sizes and the recent row are applied here, while the tiles are still off screen, and then the entrance plays.
     */
    private fun comeBack(entranceLine: String) {
        // Tile size follows use (INDEX Change Log 2026-09-21 item 1). Applied as Start COMES BACK,
        // never while it is on screen: a tile must not change size under the finger that just tapped
        // it, and coming back from the app you just opened is exactly when the new count lands.
        if (app.tileshell.prefs.ShellSettings.get(this).theme.value.autoSizeTiles) {
            app.tileshell.tiles.LayoutStore.get(this).applyAutoSize(UseCounts.get(this).scores())
        }
        pendingRecent?.let {
            pendingRecent = null
            app.tileshell.tiles.RecentApp.opened(it)
        }
        if (returningFromLaunch) {
            returningFromLaunch = false
            animation = StartAnimation(entranceElapsedMs = 0f)
            entranceToken++
            Diagnostics.add("motion", entranceLine)
        }
    }
}

package app.tileshell.cortana

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.tileshell.apps.AppEntry
import app.tileshell.apps.AppCatalog
import app.tileshell.cortana.action.ActionHost
import app.tileshell.cortana.ui.CortanaSessionRoot
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Cortana's window (phase 03 build task 1).
 *
 * The session hides the system status and nav bars and draws phase 01's W10M status bar and a nav bar
 * with Back / Windows / Search, so every measured value in this phase is read against the DRAWN bars
 * (Decisions "Session bars"). Whether the system bars really can be hidden over a voice-interaction
 * window was UNVERIFIED when the doc was written; [barsHidden] records what actually happened, which is
 * what E11 and H1 read.
 */
class CortanaSession(context: Context) : VoiceInteractionSession(context),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val host = object : ActionHost {
        override fun startVoiceActivity(intent: Intent): Boolean = runCatching {
            this@CortanaSession.startVoiceActivity(intent)
            Diagnostics.add("cortana", "startVoiceActivity ${intent.action}")
            true
        }.getOrElse {
            Diagnostics.add("cortana", "startVoiceActivity refused: $it")
            false
        }

        override fun launch(intent: Intent) {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Diagnostics.add("cortana", "launch failed: $it") }
        }

        override fun launchApp(entry: AppEntry) {
            AppCatalog.get(context).launch(entry, null, null)
        }

        override fun requestUnlock() {
            UnlockBridge.await { unlocked -> if (unlocked) model.onUnlocked() else model.onUnlockCancelled() }
            UnlockBridge.start(context)
        }
    }

    val model: CortanaModel = CortanaModel(context, scope, host)

    /** E11 / H1: whether the system bars really went away over this window. */
    @Volatile
    var barsHidden: Boolean = false
        private set

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        // RESUMED, not CREATED: onCreateContentView runs right after this, and the ComposeView it
        // returns takes its pointer-input pipeline from the lifecycle owner it is attached with. With a
        // merely-CREATED owner the content composes and draws but never reacts to touch.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        setUiEnabled(true)
        scope.launch { model.closeRequests.collect { hide() } }
        Diagnostics.add("cortana", "session created")
    }

    override fun onCreateContentView(): View {
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CortanaSession)
            setViewTreeViewModelStoreOwner(this@CortanaSession)
            setViewTreeSavedStateRegistryOwner(this@CortanaSession)
            setContent {
                CortanaSessionRoot(
                    model = model,
                    onDrawnBack = { onDrawnBack() },
                    onWindowsKey = { goHome() },
                    onSavePlaceHere = { name -> savePlaceHere(name) },
                    onLookUpAddress = { name, address -> lookUpAddress(name, address) },
                )
            }
        }
        // Instrumentation, kept because it is the only thing that showed where a touch stops. Without
        // it "the button is not tappable" and "the event never arrives" look identical from outside.
        val host = object : android.widget.FrameLayout(context) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                val handled = super.dispatchTouchEvent(event)
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN ||
                    event.actionMasked == android.view.MotionEvent.ACTION_UP
                ) {
                    Diagnostics.add(
                        "cortana",
                        "touch ${event.actionMasked} at ${event.x.toInt()},${event.y.toInt()} handled=$handled " +
                            "childCount=$childCount composeAttached=${compose.isAttachedToWindow}",
                    )
                }
                return handled
            }
        }
        host.addView(
            compose,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return host
    }

    /** Place source C: saved at the spot from GPS — no internet, no Google. */
    private fun savePlaceHere(name: String) {
        scope.launch { PlaceSaver.saveCurrentLocation(context, name) }
    }

    /** Place source C: a typed address, looked up once through OpenStreetMap's Nominatim. */
    private fun lookUpAddress(name: String, address: String) {
        scope.launch { PlaceSaver.saveTypedAddress(context, name, address) }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // Re-asserted per show: the window is recreated around a reused session, and a window whose UI
        // is not enabled is not touchable.
        setUiEnabled(true)
        makeTouchable()
        // The session object outlives a hide, so the engines are bound per SHOW, not per session.
        model.start()
        hideSystemBars()
        val mode = args?.getString(CortanaService.EXTRA_MODE)
            ?.let { runCatching { CortanaMode.valueOf(it) }.getOrNull() }
            // The assist gesture, the side key and KEYCODE_ASSIST carry no args of ours. W10M's Search
            // key opened the home page on a tap, so a session with no mode opens on Home too.
            ?: CortanaMode.HOME
        model.open(mode)
    }

    override fun onHide() {
        model.stop()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        Diagnostics.add("cortana", "session hidden")
        super.onHide()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        scope.cancel()
        Diagnostics.add("cortana", "session destroyed")
        super.onDestroy()
    }

    /**
     * The whole window is touchable.
     *
     * A VoiceInteractionSession computes its touchable region from the content frame by default, and on
     * this build that region came out empty: every tap inside Cortana — the microphone, the ≡ button,
     * a card's Remind and Cancel — was treated as a touch OUTSIDE the session and dismissed it instead.
     * The page drew perfectly and reported `clickable="true"` on every node, so nothing short of tapping
     * one showed it. Nothing in Cortana is tappable without this.
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentInsets.set(0, 0, 0, 0)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_FRAME
        outInsets.touchableRegion.setEmpty()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!model.onBack()) hide()
    }

    /** The drawn Windows key: Start comes back and the session goes away. */
    fun goHome() {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        hide()
    }

    fun onDrawnBack() {
        if (!model.onBack()) hide()
    }

    /**
     * Give the session window a real input channel.
     *
     * `dumpsys input` listed NO window for this session at all — only a focus request answered
     * `result='NO_WINDOW'` — so the page rendered and reported every node clickable while the input
     * dispatcher had nothing to deliver a touch to. A window with `INPUT_FEATURE_NO_INPUT_CHANNEL`, or
     * with FLAG_NOT_FOCUSABLE / FLAG_NOT_TOUCHABLE, is exactly that: visible and deaf.
     *
     * The flags are logged before and after, because the useful fact is which bit was set, not that
     * something was cleared.
     */
    private fun makeTouchable() {
        val dialogWindow = window?.window ?: return
        val before = dialogWindow.attributes
        Diagnostics.add(
            "cortana",
            "session window type=${before.type} flags=0x${Integer.toHexString(before.flags)} " +
                "softInputMode=0x${Integer.toHexString(before.softInputMode)}",
        )
        dialogWindow.clearFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        // INPUT_FEATURE_NO_INPUT_CHANNEL is @hide, so an app cannot read or clear it. If that is what
        // the framework set on this window, nothing here can undo it — which would make the missing
        // input channel the platform's, not the shell's, and a phone row rather than a bug to fix.
        val after = dialogWindow.attributes
        Diagnostics.add(
            "cortana",
            "session window now flags=0x${Integer.toHexString(after.flags)} " +
                "softInputMode=0x${Integer.toHexString(after.softInputMode)}",
        )
    }

    private fun hideSystemBars() {
        val dialogWindow = window?.window
        if (dialogWindow == null) {
            Diagnostics.add("cortana", "no session window to hide bars on")
            return
        }
        runCatching {
            dialogWindow.setDecorFitsSystemWindows(false)
            dialogWindow.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
            barsHidden = true
        }.onFailure {
            barsHidden = false
            Diagnostics.add("cortana", "hiding the system bars over the session failed: $it")
        }
        Diagnostics.add("cortana", "session bars: system bars hidden=$barsHidden (E11 / H1)")
    }
}

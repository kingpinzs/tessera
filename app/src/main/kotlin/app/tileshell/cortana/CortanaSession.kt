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
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        setUiEnabled(true)
        model.start()
        scope.launch { model.closeRequests.collect { hide() } }
        Diagnostics.add("cortana", "session created")
    }

    override fun onCreateContentView(): View {
        val view = ComposeView(context).apply {
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
        return view
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

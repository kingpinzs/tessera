package app.tileshell.ime

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Process
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.tileshell.R
import app.tileshell.brand.Brand
import app.tileshell.cortana.CortanaPermissionActivity
import app.tileshell.cortana.speech.SpeechClient
import app.tileshell.cortana.speech.SpeechError
import app.tileshell.cortana.speech.SpeechEvent
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.tokens.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.concurrent.Executors

/**
 * Phase 05: the W10M keyboard, as an input method inside the shell's one APK (Decisions: "Shell-owned
 * IME in the one APK"). It runs in its own `:ime` process — a keyboard crash cannot take Start with it,
 * Start's crash cannot take the keyboard, and the speech process sees the keyboard as a client of its own
 * when the voice key and Cortana both want the one microphone.
 *
 * `adb shell dumpsys activity service app.tileshell/.ime.KeyboardService` prints this process's state
 * and its own diagnostics ring (the launcher's dump cannot see it), which is what the QA rows read.
 */
class KeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, KeyboardController.Host, KeyboardActions {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store0 = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store0
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "ime-dictionary") }

    private val state = KeyboardState()
    private lateinit var editor: Editor
    private lateinit var controller: KeyboardController
    private lateinit var feedback: KeyFeedback
    private lateinit var store: ImeStore
    private lateinit var fonts: KeyFonts
    private lateinit var emoji: EmojiCatalog
    private var host: KeyboardHost? = null

    @Volatile private var brain: TextBrain? = null
    private var config = KeyboardConfig()
    private var metrics by mutableStateOf(KeyboardMetrics(1080f, 2340f, 3f))
    private var bottomInset = 0f
    private var pendingListen = false

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        // RESUMED at once: the strip and the emoji panel take touch through Compose, whose pointer input
        // needs a resumed owner (phase 03's lesson: a merely-CREATED owner draws but never reacts).
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        store = ImeStore(this)
        editor = Editor { currentInputConnection }
        feedback = KeyFeedback(this) { host }
        fonts = KeyFonts(resources.getFont(R.font.selawik_regular), resources.getFont(R.font.fluent_icons))
        emoji = EmojiCatalog(assets)
        controller = KeyboardController(state, editor, { brain }, feedback, store, this)
        controller.configure(ViewConfiguration.get(this))
        state.dock = store.dock
        state.recentEmoji = store.recentEmoji
        loader.execute { loadBrain() }
        scope.launch { SpeechClient.events.collect { onSpeech(it) } }
        scope.launch {
            SpeechClient.connection.collect { c ->
                if (c == SpeechClient.Connection.BOUND && pendingListen) {
                    pendingListen = false
                    SpeechClient.listen("")
                }
            }
        }
        Diagnostics.add("ime", "keyboard service created pid=${Process.myPid()} process=${android.app.Application.getProcessName()}")
    }

    private fun loadBrain() {
        val started = SystemClock.elapsedRealtime()
        brain = runCatching { EngineBrain.load(this) }
            .onFailure { Diagnostics.add("ime", "dictionary failed to load: $it") }
            .getOrNull()
        Diagnostics.add("ime", "dictionary ready=${brain != null} in ${SystemClock.elapsedRealtime() - started} ms")
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        scope.cancel()
        loader.shutdownNow()
        feedback.release()
        if (SpeechClient.connection.value != SpeechClient.Connection.UNBOUND) SpeechClient.unbind(this)
        Diagnostics.add("ime", "keyboard service destroyed")
        super.onDestroy()
    }

    // ---- the window ---------------------------------------------------------------------------------

    override fun onCreateInputView(): View {
        val compose = ComposeView(this).apply {
            setContent {
                KeyboardView(state, metrics, fonts, emoji, this@KeyboardService) { controller.labelFor(it) }
            }
        }
        val h = KeyboardHost(this, controller) { inset ->
            if (inset != bottomInset) {
                bottomInset = inset
                recomputeMetrics("nav inset $inset")
            }
        }
        h.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        // Compose builds its recomposer from the window's ROOT view, and the root of an IME window is the
        // window's decor view, not the view returned here — so the owners go on both (phase 03's lesson:
        // with them only on a child every open died with "ViewTreeLifecycleOwner not found").
        listOfNotNull(h, window?.window?.decorView).forEach {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        // The headroom above the panel (for popups and the raised keyboard) must show the app through it.
        window?.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        host = h
        recomputeMetrics("input view created")
        return h
    }

    /** RV10: sizes from the display (maximum window metrics), never from this window's own bounds. */
    private fun recomputeMetrics(why: String) {
        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        metrics = KeyboardMetrics(
            screenWidthPx = bounds.width().toFloat(),
            screenHeightPx = bounds.height().toFloat(),
            pxPerEpx = Scale.pxPerEpx(this),
            dock = state.dock,
            bottomInsetPx = bottomInset,
        )
        controller.metrics = metrics
        state.raise = (store.raise * metrics.sy).coerceIn(0f, metrics.maxRaise)
        Diagnostics.add(
            "ime",
            "metrics ($why): screen ${bounds.width()}x${bounds.height()} sx=${metrics.sx} sy=${metrics.sy} " +
                "epx=${metrics.pxPerEpx} strip=${metrics.stripH} panel=${metrics.panelH} view=${metrics.viewH} " +
                "dock=${state.dock} raise=${state.raise} inset=$bottomInset",
        )
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * The panel is what covers the app; the headroom above it (popups, the room to drag the keyboard up)
     * and the band below a raised panel are see-through and pass touches to the app (Decisions "Moving the
     * keyboard": "a transparent, non-touchable band set through onComputeInsets").
     */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = host ?: return
        if (!isInputViewShown) return
        val top = metrics.panelTop(state.raise).toInt()
        val bottom = (metrics.panelTop(state.raise) + metrics.panelH).toInt()
        val viewTop = (view.height - metrics.viewH).toInt().coerceAtLeast(0)
        outInsets.contentTopInsets = viewTop + top
        outInsets.visibleTopInsets = viewTop + top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, viewTop + top, view.width, viewTop + bottom)
        if (metrics.bottomInsetPx > 0f) {
            // The nav bar's strip at the very bottom stays touchable so the system's own nav buttons,
            // drawn over this window, keep working.
            outInsets.touchableRegion.union(android.graphics.Rect(0, view.height - metrics.bottomInsetPx.toInt(), view.width, view.height))
        }
    }

    override fun dimensionsChanged() {
        recomputeMetrics("dimensions")
    }

    // ---- input lifecycle ----------------------------------------------------------------------------

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        KeyboardConfigProvider.read(this)?.let { config = it }
        state.accent = config.accent
        state.handedness = config.handedness
        feedback.sounds = config.sounds
        feedback.vibration = config.vibration
        val field = FieldInfo.from(info.inputType, info.imeOptions)
        state.field = field
        controller.reset()
        editor.reset(info)
        state.emojiOpen = false
        state.voice = VoiceState.Idle
        state.notice = null
        state.layout = Layouts.build(Layouts.initialLayer(field), field)
        recomputeMetrics("start input")
        controller.autoShift()
        controller.refreshStrip(typing = true)
        Diagnostics.add(
            "ime",
            "start input pkg=${info.packageName} type=0x${Integer.toHexString(info.inputType)} " +
                "options=0x${Integer.toHexString(info.imeOptions)} field=$field layer=${state.layout.layer} config=$config",
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        controller.cancelAll()
        stopVoice("input finished")
        Diagnostics.add("ime", "finish input view")
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val own = editor.selectionChanged(newSelStart, newSelEnd)
        controller.selectionMoved(own)
    }

    override val editorInfo: EditorInfo? get() = currentInputEditorInfo

    // ---- the strip, the emoji panel, the one-handed band --------------------------------------------

    override fun stripTapped(item: StripItem) {
        feedback.press()
        controller.stripTapped(item)
    }

    override fun restoreFullWidth() {
        feedback.press()
        controller.setDock(Dock.FULL)
    }

    override fun openEmoji() {
        state.emojiOpen = true
        if (state.recentEmoji.isNotEmpty() && state.emojiCategory == EmojiCategory.SMILEYS && emojiOpenedOnce) state.emojiCategory = EmojiCategory.RECENT
        emojiOpenedOnce = true
        Diagnostics.add("ime", "emoji panel open (${state.emojiCategory})")
    }

    private var emojiOpenedOnce = false

    override fun emoji(action: EmojiAction) {
        feedback.press()
        when (action) {
            is EmojiAction.Insert -> {
                editor.commit(action.text, "emoji")
                store.addRecent(action.text)
                state.recentEmoji = store.recentEmoji
                val cps = action.text.codePoints().toArray().joinToString(" ") { "U+" + Integer.toHexString(it).uppercase() }
                Diagnostics.add("ime", "emoji inserted $cps")
                // Decisions: "Switch back to letters after I type an emoticon" (R6 §2.6.5, LOW, H18).
                if (config.switchBackAfterEmoji) {
                    state.emojiOpen = false
                    controller.switchLayer(Layer.LETTERS)
                }
            }
            is EmojiAction.Category -> {
                state.emojiCategory = action.category
                Diagnostics.add("ime", "emoji category ${action.category}")
            }
            EmojiAction.Letters -> {
                state.emojiOpen = false
                controller.switchLayer(Layer.LETTERS)
            }
            EmojiAction.Backspace -> editor.backspace()
        }
    }

    // ---- voice typing (build task 8) ----------------------------------------------------------------

    override fun micTapped() {
        feedback.press()
        if (state.voice is VoiceState.Listening) {
            SpeechClient.stopListening()
            return
        }
        startVoice()
    }

    /**
     * The voice key binds phase 03's `app.tileshell:speech` process (Decisions): the keyboard never loads
     * a speech model itself, and the assistant role is not needed — the engine is ours.
     */
    override fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Edge case "Microphone permission denied": say so, and raise Android's own grant prompt
            // through the shell's existing permission page rather than doing nothing.
            state.notice = "Microphone access is off."
            Diagnostics.add("ime", "voice refused: no RECORD_AUDIO; asking")
            CortanaPermissionActivity.request(this, listOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        state.voice = VoiceState.Listening("")
        state.notice = null
        if (SpeechClient.bound) {
            SpeechClient.listen("")
        } else {
            pendingListen = true
            SpeechClient.bind(this, includeCapabilities = true)
        }
        Diagnostics.add("ime", "voice typing started (bound=${SpeechClient.bound})")
    }

    private fun stopVoice(why: String) {
        pendingListen = false
        if (state.voice is VoiceState.Listening) SpeechClient.stopListening()
        state.voice = VoiceState.Idle
        if (SpeechClient.connection.value != SpeechClient.Connection.UNBOUND) {
            SpeechClient.unbind(this)
            Diagnostics.add("ime", "voice: unbound ($why)")
        }
    }

    private fun onSpeech(event: SpeechEvent) {
        val listening = state.voice is VoiceState.Listening
        when (event) {
            is SpeechEvent.Listening -> if (listening) state.voice = VoiceState.Listening("")
            is SpeechEvent.Partial -> if (listening) state.voice = VoiceState.Listening(sentenceCase(event.text))
            is SpeechEvent.Final -> if (listening) {
                state.voice = VoiceState.Idle
                val text = sentenceCase(event.open.trim())
                Diagnostics.add("ime", "voice final \"$text\" (${event.audioMs} ms)")
                if (text.isEmpty()) {
                    state.notice = "Didn't catch that."
                } else {
                    val before = editor.before(1)
                    val lead = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
                    editor.commit("$lead$text ", "voice")
                    controller.refreshStrip(typing = true)
                    controller.autoShift()
                }
            }
            is SpeechEvent.Error -> if (listening || pendingListen) {
                pendingListen = false
                state.voice = VoiceState.Idle
                state.notice = when (event.code) {
                    SpeechError.MICROPHONE_BUSY -> "${Brand.ASSISTANT_NAME} is using the microphone."
                    SpeechError.NO_MICROPHONE_PERMISSION -> "Microphone access is off."
                    SpeechError.AUDIO_UNAVAILABLE -> "The microphone is in use (a call?)."
                    else -> "Voice typing isn't available right now."
                }
                Diagnostics.add("ime", "voice error ${event.code}: ${event.detail}")
            }
            SpeechEvent.ProcessGone -> if (listening) {
                state.voice = VoiceState.Idle
                state.notice = "Speech is restarting. Try again."
            }
            else -> Unit
        }
    }

    /**
     * The recogniser writes capitals (sherpa-onnx's BPE vocabulary is upper case). Typed text is sentence
     * case: lower-case it, restore each word's dictionary casing ("I", "Monday"), and capitalise the first
     * letter where the field's own caps rule says a sentence starts.
     */
    private fun sentenceCase(raw: String): String {
        if (raw.isBlank()) return ""
        val b = brain
        val words = raw.lowercase().split(' ').filter { it.isNotEmpty() }.map { w -> b?.properCase(w) ?: if (w == "i") "I" else w }
        val joined = words.joinToString(" ")
        val caps = currentInputEditorInfo?.let { editor.capsMode(it.inputType) } ?: false
        return if (caps) joined.replaceFirstChar { it.uppercaseChar() } else joined
    }

    // ---- diagnostics ------------------------------------------------------------------------------

    override fun dump(fd: FileDescriptor, fout: PrintWriter, args: Array<out String>?) {
        fout.println("tileshell keyboard process pid=${Process.myPid()} process=${android.app.Application.getProcessName()}")
        fout.println("field=${state.field}")
        fout.println("layer=${state.layout.layer} shift=${state.shift} emoji=${state.emojiOpen} dock=${state.dock} raise=${state.raise}")
        fout.println("metrics sx=${metrics.sx} sy=${metrics.sy} strip=${metrics.stripH} panel=${metrics.panelH} view=${metrics.viewH} inset=${metrics.bottomInsetPx}")
        fout.println("config=$config")
        fout.println("feedback clicks=${feedback.clicks} buzzes=${feedback.buzzes}")
        fout.println("dictionary=${brain != null} voice=${state.voice} speech=${SpeechClient.connection.value}")
        fout.println("strip=${state.strip.joinToString(" | ") { (if (it.bold) "*" else "") + it.shown }}")
        fout.println("--- diagnostics ---")
        Diagnostics.dump(fout)
    }
}

/**
 * The input view's root. A touch that starts in the key block belongs to the controller for its whole
 * gesture (all its pointers); anything else — the strip, the emoji panel, the one-handed band — goes to
 * Compose. It also reports how much of the window the system nav bar covers.
 */
class KeyboardHost(
    context: android.content.Context,
    private val controller: KeyboardController,
    private val onNavInset: (Float) -> Unit,
) : FrameLayout(context) {
    private var ownGesture = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ownGesture = controller.inKeyBlock(ev.x, ev.y)
        }
        val result = if (ownGesture) controller.onTouch(ev) else super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) ownGesture = false
        return result
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        onNavInset(nav.toFloat())
        return super.onApplyWindowInsets(insets)
    }
}

package app.tileshell.cortana

import android.content.Context
import app.tileshell.apps.AppCatalog
import app.tileshell.brand.Brand
import app.tileshell.cortana.action.ActionHost
import app.tileshell.cortana.action.ActionLayer
import app.tileshell.cortana.action.Contacts
import app.tileshell.cortana.action.LockGate
import app.tileshell.cortana.action.Outcome
import app.tileshell.cortana.action.Pending
import app.tileshell.cortana.match.CommandMatcher
import app.tileshell.cortana.match.Request
import app.tileshell.cortana.speech.MicHolders
import app.tileshell.cortana.speech.SpeechClient
import app.tileshell.cortana.speech.SpeechError
import app.tileshell.cortana.speech.SpeechEvent
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The persona's states (R3 A22 for idle / thinking, R6 §3.1 for listening, R6 §3.2 for speaking). */
enum class PersonaState { IDLE, THINKING, LISTENING, SPEAKING, IDLE_AFTER_SPEAKING }

/** Where the session is. Home keeps the text box; the ≡ destinations do not (R7 §3.1.13). */
sealed interface CortanaRoute {
    data object Home : CortanaRoute
    data object Result : CortanaRoute
    data object Reminders : CortanaRoute
    data object ReminderNew : CortanaRoute
    data class ReminderDetail(val id: String) : CortanaRoute
    data object History : CortanaRoute
    data object Settings : CortanaRoute
    data class Places(val prefill: String? = null) : CortanaRoute
}

data class CortanaState(
    val route: CortanaRoute = CortanaRoute.Home,
    val persona: PersonaState = PersonaState.IDLE,
    /** What the query box or the text bar shows: the partial transcript, then the submitted request. */
    val query: String = "",
    /** R6 §3.1.11's white listening box, which replaces the grey text bar while listening. */
    val listening: Boolean = false,
    val card: Card? = null,
    val pending: Pending? = null,
    val awaiting: CommandMatcher.Awaiting? = null,
    /** The non-personalised greeting over the keyguard is R6 §3.5.5's; the open one is Home's. */
    val greeting: String = "What's on your mind?",
    /** Microphone level while listening, or the voice's output level while speaking (0..1). */
    val level: Float = 0f,
    val locked: Boolean = false,
    val paneOpen: Boolean = false,
    /** The speech process died and is coming back (E12). */
    val reloading: Boolean = false,
    val destination: CortanaDestinationKey = CortanaDestinationKey.HOME,
)

/** What [CortanaRoute] a ≡ pane item stands for, so the pane can highlight the current one. */
enum class CortanaDestinationKey { HOME, REMINDERS, SETTINGS }

/**
 * Cortana's state machine: microphone or text box in, a [Request], an action, a card, a spoken reply.
 *
 * It holds no Android view and no session, so the whole flow — including every confirmation branch and
 * the locked gate — is testable without a device. The session only renders [state] and forwards taps.
 */
class CortanaModel(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: ActionHost,
) {
    private val actions = ActionLayer(context, host)
    private val mutable = MutableStateFlow(CortanaState())
    val state: StateFlow<CortanaState> = mutable.asStateFlow()

    private var speakingUtteranceId: String? = null
    private var eventJob: Job? = null

    /**
     * The utterance the session is waiting to finish before it hides.
     *
     * A command that opens an app ("Opening Clock.") both speaks and closes. Emitting the close at the
     * same moment hid the session, which stopped the speech that had just started, so the reply was
     * never heard — E2 caught it as a reply captured at -118 dBFS while every other command's was
     * around -32. Cortana says it, THEN gets out of the way.
     */
    private var closeAfterUtterance: String? = null

    /**
     * Idempotent: the framework REUSES a VoiceInteractionSession across show and hide, so this runs on
     * every show, not once per session object. Binding only in the session's onCreate left the second
     * and every later open with no engine at all ("startListening dropped: not bound") — found on the
     * device, not in review.
     */
    fun start() {
        if (eventJob != null) return
        eventJob = scope.launch {
            SpeechClient.events.collect { onSpeechEvent(it) }
        }
        SpeechClient.bind(context)
        SpeechClient.preload()
    }

    fun stop() {
        if (eventJob == null) return
        eventJob?.cancel()
        eventJob = null
        SpeechClient.stopListening()
        SpeechClient.stopSpeaking()
        SpeechClient.unbind(context)
        closeAfterUtterance = null
        // "closing the session drops it too" (H12): a pending request never survives the session.
        mutable.value = CortanaState()
    }

    // ---------------- opening ----------------

    fun open(mode: CortanaMode) {
        val locked = LockGate.locked(context)
        mutable.value = CortanaState(
            locked = locked,
            // R6 §3.5.5: a session over the keyguard opens on the black page with the non-personalised
            // greeting, no ≡ menu and no way into Settings, Reminders or the Notebook.
            greeting = if (locked) "What's on your mind?" else "What's on your mind?",
            persona = PersonaState.IDLE,
        )
        Diagnostics.add("cortana", "session opened mode=$mode locked=$locked")
        // R6 §4.2.3: a press-and-hold opens Cortana already listening. Over the keyguard it always does
        // (R6 §3.5.5), because the tap rule does not apply there.
        if (mode == CortanaMode.LISTENING || locked) startListening()
    }

    // ---------------- listening ----------------

    fun startListening() {
        // Ask BEFORE listening, at the tap: the speech process can only report that the permission is
        // missing, and reporting it was all that ever happened (Jeremy, 2026-09-22: "it is saying it needs
        // permission to use the microphone but it never popped up the prompt to grant it").
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            SpeechClient.stopSpeaking()
            Diagnostics.add("cortana", "listen: no RECORD_AUDIO; asking")
            mutable.value = mutable.value.copy(listening = false, level = 0f)
            reply(Outcome("", Card(CardKind.NOT_UNDERSTOOD, "I need permission to use the microphone. Allow it, then tap the microphone again.")))
            host.requestMicrophone()
            return
        }
        SpeechClient.stopSpeaking()
        mutable.value = mutable.value.copy(
            persona = PersonaState.LISTENING, listening = true, query = "", reloading = false,
        )
        SpeechClient.listen(hotwords())
    }

    fun stopListening() {
        SpeechClient.stopListening()
    }

    /**
     * The grammar pass's boosted phrases. Contact and app names come from the device, so the boost
     * covers the words that actually follow a command on THIS phone.
     */
    private fun hotwords(): String {
        val contacts = if (Contacts.granted(context)) {
            // Cheap and bounded: the names of the contacts a command could name.
            Contacts.allNames(context, limit = 200)
        } else emptyList()
        val apps = AppCatalog.get(context).apps.value.map { it.label }.take(120)
        return CommandMatcher.hotwords(contacts, apps)
    }

    // ---------------- typed requests (fidelity A4) ----------------

    /**
     * A request typed into the real text box runs the SAME matcher and reply path as speech. There is
     * no other text entry point: no debug intent, broadcast or provider verb takes a request (E5).
     */
    fun submitTyped(text: String) {
        if (text.isBlank()) return
        Diagnostics.add("cortana", "typed request: \"$text\"")
        mutable.value = mutable.value.copy(query = text, listening = false)
        handle(text)
    }

    // ---------------- speech events ----------------

    private fun onSpeechEvent(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.Listening ->
                mutable.value = mutable.value.copy(persona = PersonaState.LISTENING, listening = true)
            is SpeechEvent.Partial ->
                mutable.value = mutable.value.copy(query = event.text)
            is SpeechEvent.Level ->
                if (mutable.value.persona == PersonaState.LISTENING) {
                    mutable.value = mutable.value.copy(level = event.level)
                }
            is SpeechEvent.Final -> onFinal(event)
            is SpeechEvent.SpeakingLevel ->
                if (event.utteranceId == speakingUtteranceId) {
                    mutable.value = mutable.value.copy(persona = PersonaState.SPEAKING, level = event.level)
                }
            is SpeechEvent.SpeakingDone -> {
                if (event.utteranceId == speakingUtteranceId) {
                    speakingUtteranceId = null
                    // R6 §3.2.4: after speaking, the small persona breathes on the result page.
                    mutable.value = mutable.value.copy(persona = PersonaState.IDLE_AFTER_SPEAKING, level = 0f)
                }
                if (event.utteranceId == listenAfterUtterance) {
                    listenAfterUtterance = null
                    if (!event.cancelled) {
                        Diagnostics.add("cortana", "question spoken; listening for the answer")
                        startListening()
                    }
                }
                if (event.utteranceId == closeAfterUtterance) {
                    closeAfterUtterance = null
                    Diagnostics.add("cortana", "reply finished; the session closes now")
                    closeRequests.tryEmit(Unit)
                }
            }
            is SpeechEvent.ProcessGone -> {
                speakingUtteranceId = null
                mutable.value = mutable.value.copy(
                    persona = PersonaState.IDLE, listening = false, reloading = true, level = 0f,
                )
            }
            is SpeechEvent.Error -> onError(event)
        }
    }

    private fun onFinal(event: SpeechEvent.Final) {
        mutable.value = mutable.value.copy(listening = false, level = 0f, persona = PersonaState.THINKING)
        // The grammar pass wins when it produced something, because it is the same audio decoded with
        // the command vocabulary boosted; the open pass is what an unmatched utterance is judged on and
        // what phase 08 is handed.
        val text = event.grammar.ifBlank { event.open }
        if (text.isBlank()) {
            // Silence while a card waits for yes / no is not an answer: the card and its buttons stay (phase 03
            // edge case "silence at a reminder or calendar card"). It used to be replaced by "I didn't catch that".
            if (mutable.value.pending != null) {
                Diagnostics.add("cortana", "silence at a pending card (audioMs=${event.audioMs}): the card stays")
                mutable.value = mutable.value.copy(persona = PersonaState.IDLE_AFTER_SPEAKING)
                return
            }
            Diagnostics.add("cortana", "final was silence (audioMs=${event.audioMs})")
            reply(actions.run(Request.Silence))
            return
        }
        mutable.value = mutable.value.copy(query = text)
        handle(text, openText = event.open)
    }

    /**
     * One sentence per CAUSE, not one for the group.
     *
     * "My speech files are missing" used to cover three different faults — an asset that is not in the
     * APK, a model sherpa-onnx refuses, and espeak data that failed its checksum — and on a phone that
     * is the whole of what anyone can see. Three causes behind one sentence means the sentence tells
     * nobody anything, including the person who has to fix it. The detail the speech process sent is
     * put on the card too, where it can be read and copied; the spoken half stays short, because it is
     * spoken.
     */
    private fun onError(event: SpeechEvent.Error) {
        val settings = "Open ${Brand.ASSISTANT_NAME}'s settings, then Diagnostics."
        val spoken = when (event.code) {
            SpeechError.NO_MICROPHONE_PERMISSION -> "I need permission to use the microphone."
            SpeechError.MODEL_MISSING ->
                "My speech files did not load. $settings"
            SpeechError.MODEL_CORRUPT ->
                "My speech files are here but the engine refused them. $settings"
            SpeechError.ESPEAK_DATA_BAD ->
                "My pronunciation data is damaged. $settings"
            SpeechError.AUDIO_UNAVAILABLE -> "I can't get to the microphone right now."
            // Worded from the holder the speech process named (T15-28): the keyboard or the voice recorder.
            SpeechError.MICROPHONE_BUSY -> MicHolders.busySentence(MicHolders.holderOf(event.detail), Brand.ASSISTANT_NAME)
            else -> "Something went wrong."
        }
        val shown = if (event.detail.isBlank()) spoken else "$spoken\n\n${event.detail}"
        val speakable = SpeechError.isSpeakable(event.code)
        Diagnostics.add(
            "cortana",
            "speech error ${event.code}: ${event.detail}" + if (speakable) "" else " (shown, not spoken)",
        )
        mutable.value = mutable.value.copy(listening = false, level = 0f)
        // An error that says the voice is broken cannot be delivered BY the voice: speaking it fails
        // the same way and comes straight back here. See [SpeechError.isSpeakable].
        reply(Outcome(if (speakable) spoken else "", Card(CardKind.NOT_UNDERSTOOD, shown)))
    }

    // ---------------- the request path (speech and text share it) ----------------

    private fun handle(text: String, openText: String = text) {
        val current = mutable.value
        val pending = current.pending

        // "What do you want to say?" and "What would you like to add?": the whole utterance is the
        // message, so it is never run through the matcher.
        if (pending is Pending.AwaitMessage || pending is ActionLayer.AddingTo) {
            reply(actions.supplyMessage(pending, text.trim()))
            return
        }

        val request = CommandMatcher.match(text, CommandMatcher.Context(current.awaiting))

        if (pending != null && request is Request.Answer) {
            reply(answerPending(pending, request))
            return
        }

        // A second command spoken while a confirmation is pending replaces it (edge case; H12's rule for
        // the Unlock card, applied to every card so there is one rule rather than two).
        if (pending != null) {
            Diagnostics.add("cortana", "a new request replaced the pending ${pending.javaClass.simpleName}")
            mutable.value = mutable.value.copy(pending = null, awaiting = null)
        }

        val actual = if (request is Request.NotUnderstood) Request.NotUnderstood(openText) else request
        reply(actions.run(actual))
    }

    private fun answerPending(pending: Pending, answer: Request.Answer): Outcome = when (answer) {
        is Request.Confirm -> actions.confirm(pending)
        is Request.Cancel -> actions.cancel(pending)
        is Request.AddMore -> (pending as? Pending.SendText)?.let { actions.addMore(it) }
            ?: actions.cancel(pending)
        is Request.TryAgain -> actions.tryAgain(pending)
        is Request.Whenever -> (pending as? Pending.AwaitReminderTime)
            ?.let { actions.supplyReminderTime(it, null) } ?: actions.cancel(pending)
    }

    // ---------------- card buttons ----------------

    fun onCardAction(action: CardAction) {
        val pending = mutable.value.pending
        Diagnostics.add("cortana", "card action $action pending=${pending?.javaClass?.simpleName}")
        when (action) {
            CardAction.CONFIRM -> {
                // Tapping "Remind" with the time and day fields empty stores a Whenever reminder (H16).
                if (pending is Pending.AwaitReminderTime) reply(actions.supplyReminderTime(pending, null))
                else pending?.let { reply(actions.confirm(it)) }
            }
            CardAction.CANCEL -> pending?.let { reply(actions.cancel(it)) }
            CardAction.ADD_MORE -> (pending as? Pending.SendText)?.let { reply(actions.addMore(it)) }
            CardAction.TRY_AGAIN -> pending?.let { reply(actions.tryAgain(it)) }
            CardAction.UNLOCK -> pending?.let { reply(actions.confirm(it)) }
            CardAction.SET_DEFAULT_ASSISTANT, CardAction.PICK_PHOTO -> Unit // the session handles these
        }
    }

    /** The gated request the "Unlock" button was raised for, run now that the keyguard is gone. */
    fun onUnlocked() {
        val pending = mutable.value.pending as? Pending.Locked
        if (pending == null) {
            Diagnostics.add("cortana", "unlocked with nothing pending")
            mutable.value = mutable.value.copy(locked = false)
            return
        }
        Diagnostics.add("cortana", "unlocked; running the pending ${pending.request}")
        mutable.value = mutable.value.copy(locked = false, pending = null)
        reply(actions.run(pending.request))
    }

    fun onUnlockCancelled() {
        Diagnostics.add("cortana", "unlock cancelled; the pending request stays on the card")
    }

    // ---------------- replies ----------------

    private fun reply(outcome: Outcome) {
        val voice = CortanaPrefs.get(context).settings.value.voiceId
        mutable.value = mutable.value.copy(
            card = outcome.card,
            pending = outcome.pending,
            awaiting = outcome.awaiting,
            route = when {
                outcome.openPlaces != null -> CortanaRoute.Places(outcome.openPlaces)
                outcome.card != null -> CortanaRoute.Result
                else -> mutable.value.route
            },
            destination = if (outcome.openPlaces != null) CortanaDestinationKey.SETTINGS else mutable.value.destination,
            persona = if (outcome.spoken.isBlank()) PersonaState.IDLE_AFTER_SPEAKING else PersonaState.SPEAKING,
            listening = false,
        )
        val utterance = if (outcome.spoken.isNotBlank()) SpeechClient.speak(outcome.spoken, voice) else null
        speakingUtteranceId = utterance
        if (!outcome.close) {
            // Cortana asked a question, so Cortana listens for the answer.
            //
            // The card's own callout says "you can say Yes, No, or Cancel", and R6 §3.3.7 has the text
            // bar go EMPTY with a grey mic and no accent fill while a confirm card waits — i.e. there is
            // no microphone to press, because it is already open. Without this a confirmation can only
            // be answered by its buttons, which is not what the row requires and not what W10M did.
            if (outcome.awaiting != null) listenAfter(utterance)
            return
        }
        if (utterance == null) {
            closeRequests.tryEmit(Unit)
            return
        }
        closeAfterUtterance = utterance
        // A speech process that dies mid-reply would otherwise strand the session on screen, so the
        // close has a deadline as well as a trigger.
        scope.launch {
            kotlinx.coroutines.delay(CLOSE_DEADLINE_MS)
            if (closeAfterUtterance == utterance) {
                closeAfterUtterance = null
                Diagnostics.add("cortana", "reply did not finish within ${CLOSE_DEADLINE_MS} ms; closing anyway")
                closeRequests.tryEmit(Unit)
            }
        }
    }

    /**
     * Start listening once the question has finished being spoken, so the answer is not swallowed by
     * Cortana's own voice. With no utterance to wait for, listening starts at once.
     */
    private fun listenAfter(utterance: String?) {
        if (utterance == null) {
            startListening()
            return
        }
        listenAfterUtterance = utterance
    }

    private var listenAfterUtterance: String? = null

    /** The session collects this and hides itself: an app has taken the screen. */
    val closeRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private companion object {
        /** Long enough for any reply this phase produces, short enough not to strand the session. */
        const val CLOSE_DEADLINE_MS = 8000L
    }

    // ---------------- navigation ----------------

    fun openPane(open: Boolean) {
        // R6 §3.5.5: over the keyguard there is no ≡ menu and no entry to Settings or Reminders.
        if (open && mutable.value.locked) return
        mutable.value = mutable.value.copy(paneOpen = open)
    }

    fun goTo(destination: CortanaDestinationKey) {
        // R7 §3.1.11: re-selecting Home leaves the pane open.
        if (destination == CortanaDestinationKey.HOME && mutable.value.destination == CortanaDestinationKey.HOME) return
        mutable.value = mutable.value.copy(
            destination = destination,
            paneOpen = false,
            route = when (destination) {
                CortanaDestinationKey.HOME -> CortanaRoute.Home
                CortanaDestinationKey.REMINDERS -> CortanaRoute.Reminders
                CortanaDestinationKey.SETTINGS -> CortanaRoute.Settings
            },
            card = if (destination == CortanaDestinationKey.HOME) mutable.value.card else null,
        )
        Diagnostics.add("cortana", "destination $destination")
    }

    fun goToRoute(route: CortanaRoute) {
        mutable.value = mutable.value.copy(route = route, paneOpen = false)
    }

    /** @return true when Back was consumed inside Cortana; false means the session should close. */
    fun onBack(): Boolean {
        val current = mutable.value
        return when {
            current.paneOpen -> { openPane(false); true }
            current.route is CortanaRoute.ReminderDetail || current.route is CortanaRoute.ReminderNew ||
                current.route is CortanaRoute.History -> { goTo(CortanaDestinationKey.REMINDERS); true }
            current.route is CortanaRoute.Places -> { goTo(CortanaDestinationKey.SETTINGS); true }
            current.route is CortanaRoute.Reminders || current.route is CortanaRoute.Settings ->
                { goTo(CortanaDestinationKey.HOME); true }
            current.route is CortanaRoute.Result -> { clearResult(); true }
            else -> false
        }
    }

    /** Back from an answer: take the card off the page and return to Home. */
    fun clearResult() {
        mutable.value = mutable.value.copy(route = CortanaRoute.Home, card = null, pending = null, awaiting = null)
        Diagnostics.add("cortana", "result cleared")
    }
}

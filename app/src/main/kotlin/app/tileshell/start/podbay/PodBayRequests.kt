package app.tileshell.start.podbay

import android.os.SystemClock
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A voice (or typed) request to move the pager: open the pod bay, or close it. */
data class PodBayRequest(val open: Boolean, val doors: Boolean = false)

/**
 * Phase 14 (Decisions "Route to Start with no new exported surface"): the one pending pod-bay request, in the main
 * process, between Tess's action and Start's pager. The action only records it; Start takes it in its `homeEvents`
 * collector on a `PodBayCheck` (the session's close-time HOME intent, window focus back, the wizard ending). A new
 * request replaces the pending one. Nothing outside this process can reach it — the HOME intent's `POD_BAY_CHECK`
 * extra is a signal to look, never the request.
 *
 * A request does not wait for ever (owner ruling 2026-09-30, gate review r1 S2): Tess dismissed over another app left it
 * pending until some later Home slid the pod bay open, or until a later request's close pulled Start over that request's
 * own result. It is dropped when any other request is made, and it lapses [EXPIRES_AFTER_MS] after it was made — except
 * while the setup wizard holds it, which lasts as long as the wizard does (T14-7).
 */
object PodBayRequests {
    const val EXPIRES_AFTER_MS = 30_000L

    /** The clock the expiry reads; a test sets its own. */
    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    private data class Held(val request: PodBayRequest, val atMs: Long, val waitingForWizard: Boolean = false)

    private var held: Held? = null
    private val mutable = MutableStateFlow<PodBayRequest?>(null)
    val pending: StateFlow<PodBayRequest?> = mutable.asStateFlow()

    @Synchronized
    fun record(request: PodBayRequest) {
        val replaced = live()?.request
        held = Held(request, clock())
        mutable.value = request
        Diagnostics.add(
            "podbay",
            "request recorded: ${request.describe()}" + (replaced?.let { " (replaces ${it.describe()})" } ?: ""),
        )
    }

    /** Whether a request is waiting that has not lapsed. */
    @Synchronized
    fun hasPending(): Boolean = live() != null

    /** Takes the pending request, leaving none; null when there is nothing to take (or it has lapsed). */
    @Synchronized
    fun take(): PodBayRequest? {
        val current = live() ?: return null
        clear()
        return current.request
    }

    /** Start looked while the setup wizard was showing: the request waits for the wizard, however long that takes. */
    @Synchronized
    fun waitForWizard() {
        val current = live() ?: return
        if (!current.waitingForWizard) {
            held = current.copy(waitingForWizard = true)
            Diagnostics.add("podbay", "request waits for the wizard: ${current.request.describe()}")
        }
    }

    /** Another request was made: the pending one is no longer what the user asked for last. */
    @Synchronized
    fun dropFor(reason: String) {
        val current = held ?: return
        clear()
        Diagnostics.add("podbay", "request dropped: ${current.request.describe()} ($reason)")
    }

    private fun live(): Held? {
        val current = held ?: return null
        val ageMs = clock() - current.atMs
        if (!current.waitingForWizard && ageMs > EXPIRES_AFTER_MS) {
            clear()
            Diagnostics.add("podbay", "request lapsed: ${current.request.describe()} after ${ageMs / 1000} s")
            return null
        }
        return current
    }

    private fun clear() {
        held = null
        mutable.value = null
    }

    private fun PodBayRequest.describe() = if (open) "open" + if (doors) " (doors)" else "" else "close"
}

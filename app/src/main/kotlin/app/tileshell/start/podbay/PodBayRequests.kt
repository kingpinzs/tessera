package app.tileshell.start.podbay

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
 */
object PodBayRequests {
    private val mutable = MutableStateFlow<PodBayRequest?>(null)
    val pending: StateFlow<PodBayRequest?> = mutable.asStateFlow()

    fun record(request: PodBayRequest) {
        val replaced = mutable.value
        mutable.value = request
        Diagnostics.add(
            "podbay",
            "request recorded: ${request.describe()}" + (replaced?.let { " (replaces ${it.describe()})" } ?: ""),
        )
    }

    /** Takes the pending request, leaving none; null when there is nothing to take. */
    @Synchronized
    fun take(): PodBayRequest? {
        val request = mutable.value ?: return null
        mutable.value = null
        return request
    }

    private fun PodBayRequest.describe() = if (open) "open" + if (doors) " (doors)" else "" else "close"
}

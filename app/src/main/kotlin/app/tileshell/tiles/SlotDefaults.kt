package app.tileshell.tiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Role slots (Phone, Messaging, Browser) follow Android's default app, which can change while the shell runs without
 * any package, layout or content change, so Start would otherwise keep the old app. Android gives a launcher no signal
 * for it: the defaults live in RoleManager (its listener needs a system permission), the Secure keys that held them
 * before Android 10 are empty, and ACTION_DEFAULT_SMS_PACKAGE_CHANGED is sent only to the old and new default app.
 * Start therefore re-resolves its slots whenever it resumes - a default is changed in Settings, and the user comes
 * back to Start - by bumping this counter, which is one of the keys the tile list is remembered by.
 */
object SlotDefaults {
    private val state = MutableStateFlow(0)
    val generation: StateFlow<Int> = state

    fun refresh() {
        state.value = state.value + 1
    }
}

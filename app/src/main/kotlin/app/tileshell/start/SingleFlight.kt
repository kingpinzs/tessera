package app.tileshell.start

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * One in-progress run per key: concurrent callers for the same key share it and get the same value (phase 13 gate
 * round 2, A: Start's page and the app list's backdrop asked [BackgroundDecoder] for the picture in the same frame, both
 * missed its finished-decode cache, and each kept its own decode — the picture held twice). The run belongs to [scope],
 * not to the caller that started it, so a caller that is cancelled stops waiting without cancelling it for the others.
 */
internal class SingleFlight<K, V>(private val scope: CoroutineScope) {
    private val running = HashMap<K, Deferred<V>>()

    suspend fun run(key: K, make: suspend () -> V): V {
        val flight = synchronized(running) {
            running[key] ?: scope.async { make() }.also { started ->
                running[key] = started
                started.invokeOnCompletion { synchronized(running) { if (running[key] === started) running.remove(key) } }
            }
        }
        return flight.await()
    }
}

package app.tileshell.cortana.speech

/**
 * One engine, one microphone (phase 05 edge case: "the second caller waits or is refused with a notice,
 * never a crash"). The speech process keeps one of these; the rules live here, free of Binder, so the host
 * JVM tests every one of them — the device can only rarely put two clients on the microphone at once.
 *
 * [K] is the client's identity: the callback's IBinder in the service, a plain value in the tests.
 *
 * - A client acquires the microphone only when nobody holds it, or when it already does (starting again
 *   replaces its own capture). Anyone else is refused: never queued, never allowed to take it over.
 * - Only the holder can release it (a stop from anyone else does nothing).
 * - A capture that ends frees the microphone only if it is still the holder's CURRENT capture: a holder
 *   that started again already has a newer generation, and the old capture ending must not free that.
 */
class MicArbiter<K : Any> {
    private var owner: K? = null
    private var generation = -1

    /** The holder's process id, for the status dump and the diagnostics; -1 when free. */
    var ownerPid = -1
        private set

    /**
     * Try to take the microphone for [who] (process [pid]). [nextGeneration] is called only on success and
     * returns the capture generation the new capture will carry.
     *
     * @return that generation, or null when another client holds the microphone
     */
    @Synchronized
    fun acquire(who: K, pid: Int, nextGeneration: () -> Int): Int? {
        val holder = owner
        if (holder != null && holder != who) return null
        val g = nextGeneration()
        owner = who
        generation = g
        ownerPid = pid
        return g
    }

    @Synchronized
    fun isHolder(who: K): Boolean = owner == who

    /** [who] lets go (it stopped, unregistered or died). @return true when it was the holder. */
    @Synchronized
    fun release(who: K): Boolean {
        if (owner != who) return false
        clear()
        return true
    }

    /** The capture carrying generation [g] has ended; frees the microphone only if that is still current. */
    @Synchronized
    fun finished(g: Int) {
        if (generation == g) clear()
    }

    @Synchronized
    fun holder(): K? = owner

    private fun clear() {
        owner = null
        generation = -1
        ownerPid = -1
    }
}

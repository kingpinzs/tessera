package app.tileshell.tiles.api

/**
 * Sliding-window limiter: at most [limit] accepted calls per key within any [windowMs] span
 * (Live Tile API: 60 verbs/min per caller, excess answers error "rate"). Rejected calls do not consume
 * budget, so a caller that backs off is served again as soon as its oldest accepted call ages out.
 * The clock is passed in (monotonic elapsedRealtime in the provider; explicit values in tests).
 */
class RateLimiter(private val limit: Int = 60, private val windowMs: Long = 60_000L) {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun tryAcquire(key: String, nowMs: Long): Boolean {
        val q = hits.getOrPut(key) { ArrayDeque() }
        while (q.isNotEmpty() && nowMs - q.first() >= windowMs) q.removeFirst()
        if (q.size >= limit) return false
        q.addLast(nowMs)
        return true
    }

    /** Milliseconds until [key] may call again (0 when it may call now). */
    @Synchronized
    fun retryAfterMs(key: String, nowMs: Long): Long {
        val q = hits[key] ?: return 0
        while (q.isNotEmpty() && nowMs - q.first() >= windowMs) q.removeFirst()
        return if (q.size < limit) 0 else windowMs - (nowMs - q.first())
    }

    @Synchronized
    fun forget(key: String) {
        hits.remove(key)
    }
}

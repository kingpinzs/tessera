package app.tileshell.music.catalogue

/**
 * MusicBrainz's etiquette as a rule (phase 20, r3 D17): on average one request a second per IP, so the shell starts
 * its requests at least [MIN_GAP_MS] apart. Pure.
 */
object RateSpacing {
    const val MIN_GAP_MS = 1000L

    /**
     * How long to wait before the next request may start. [lastStartMs] is when the last one started, null when none
     * has; both are readings of one clock. A clock that went backwards waits the whole gap — never less, and never more.
     */
    fun waitMs(lastStartMs: Long?, nowMs: Long): Long {
        if (lastStartMs == null) return 0L
        if (nowMs < lastStartMs) return MIN_GAP_MS
        val since = nowMs - lastStartMs
        // A difference too large for a Long wraps below zero: that is a very long time ago, not a wait.
        return if (since < 0 || since >= MIN_GAP_MS) 0L else MIN_GAP_MS - since
    }
}

/**
 * One request in flight, and [RateSpacing] between starts: a caller that submits while a request is running waits for
 * it to end and then for the gap. [now] and [sleep] are the clock's two halves, so a unit test runs it with no time passing.
 */
class RequestGate(private val now: () -> Long, private val sleep: (Long) -> Unit) {
    private var lastStart: Long? = null

    @Synchronized fun <T> run(request: () -> T): T {
        val wait = RateSpacing.waitMs(lastStart, now())
        if (wait > 0) sleep(wait)
        lastStart = now()
        return request()
    }
}

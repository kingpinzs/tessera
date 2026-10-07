package app.tileshell.music.radio

/**
 * The ONE reconnect clock of a live item (phase 20 Decisions "reconnect", r3 D8; Y4). T0 is the first load error of
 * a live item — ExoPlayer has not given up yet, it retries the load itself six times first — and that is when
 * `[music] stream: lost, retrying` is written, once. On each player error after it the service prepares again after
 * 2 / 4 / 8 / 16 / 30 s; at T0 + [WINDOW_MS] it pauses and writes `stream: gave up after 60000 ms`. From T0 until
 * either end the service holds its own partial wake lock ([WAKE_LOCK_MS]). Pure (`StreamRetryTest`).
 */
object StreamRetry {
    const val WINDOW_MS = 60_000L

    /** The service's own wake lock's timeout: the window and a margin, so a lost release cannot hold the phone awake. */
    const val WAKE_LOCK_MS = 65_000L

    private val DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

    const val RECONNECTING = "Reconnecting…"
    const val NOT_ANSWERING = "This station isn't answering"

    sealed interface Step {
        /** Prepare again after [delayMs]. */
        data class Retry(val delayMs: Long) : Step

        /** No further attempt fits the window: give up [inMs] from now (0 = now). */
        data class GiveUp(val inMs: Long) : Step
    }

    /**
     * What the service does on a player error [sinceLostMs] after T0, having prepared again [attempt] times already.
     */
    fun next(sinceLostMs: Long, attempt: Int): Step {
        val since = sinceLostMs.coerceAtLeast(0L)
        if (since >= WINDOW_MS) return Step.GiveUp(0L)
        val delay = DELAYS_MS[attempt.coerceIn(0, DELAYS_MS.size - 1)]
        return if (since + delay < WINDOW_MS) Step.Retry(delay) else Step.GiveUp(WINDOW_MS - since)
    }

    /** The clock itself: when the stream was lost (elapsed-realtime ms; null = not lost) and the attempts since. */
    data class Clock(val lostAtMs: Long? = null, val attempt: Int = 0) {
        val lost: Boolean get() = lostAtMs != null
    }

    /** A load error at [nowMs]: the clock, and whether `lost, retrying` is written now — on the first, once. */
    fun onLoadError(clock: Clock, nowMs: Long): Pair<Clock, Boolean> =
        if (clock.lost) clock to false else Clock(nowMs, 0) to true

    /** A player error at [nowMs] (after [onLoadError] has had it): the step, and the clock with the attempt counted. */
    fun onPlayerError(clock: Clock, nowMs: Long): Pair<Clock, Step> {
        val t0 = clock.lostAtMs ?: nowMs
        val step = next(nowMs - t0, clock.attempt)
        return Clock(t0, if (step is Step.Retry) clock.attempt + 1 else clock.attempt) to step
    }

    /** The stream plays again at [nowMs]: how long it was lost (for `reconnected after <ms> ms`), null when it was not. */
    fun reconnectedAfter(clock: Clock, nowMs: Long): Long? = clock.lostAtMs?.let { (nowMs - it).coerceAtLeast(0L) }
}

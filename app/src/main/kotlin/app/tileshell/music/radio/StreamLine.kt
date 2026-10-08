package app.tileshell.music.radio

import app.tileshell.video.server.ServerRules
import java.util.Locale

/**
 * Every `[music] stream:` and `[music] sleep:` line of phase 20, built in one place and without its tag (the caller
 * is `Diagnostics.add(StreamLine.TAG, …)`). Pure (`StreamLineTest` holds each line's exact text).
 *
 * TRUST-TOUCHING: an address in a line goes through [url] — its query (a token's place) and its userinfo (a
 * password's) removed, and what is left through [RadioText.shown] — and a scheme or a codec is cut to a plain token,
 * so nothing a directory row or a stream sends can forge a line or carry a secret into one (T20-13, C-32).
 */
object StreamLine {
    const val TAG = "music"

    /** The longest address a line carries. */
    const val URL_MAX = 300

    /** T0 of the reconnect clock: the first load error of a live item. */
    const val LOST = "stream: lost, retrying"
    const val CAPTIVE_PORTAL = "stream: captive portal"
    /**
     * A station whose address names a private host — and (review R20-1) one whose connection was refused where it was
     * made: a name that resolved to a private address, or a redirect to one. The address is not in the line.
     */
    const val UNSUPPORTED_HOST = "stream: unsupported host"
    const val UNSUPPORTED_PLAYLIST = "stream: unsupported playlist"

    /** A redirect between https and http: Media3's kept default refuses it, and it is not retried. */
    const val REDIRECT_REFUSED = "stream: redirect refused"

    /** A station tapped with no network at all: refused at once ([StreamGate]). */
    const val NO_NETWORK = "stream: no network"

    /** An armed end-of-track timer cleared because a station queue replaced the track queue. */
    const val SLEEP_CLEARED = "sleep: end-of-track cleared (live item)"

    fun gaveUp(afterMs: Long = StreamRetry.WINDOW_MS): String = "stream: gave up after $afterMs ms"

    fun reconnected(afterMs: Long): String = "stream: reconnected after $afterMs ms"

    fun unsupportedScheme(scheme: String?): String = "stream: unsupported scheme=${token(scheme).ifEmpty { StationUrl.NO_SCHEME }}"

    /** `stream: connected <address> codec=<codec>` — the address as [url] gives it, the codec in lower case. */
    fun connected(address: String, codec: String?): String = "stream: connected ${url(address)} codec=${token(codec).ifEmpty { "unknown" }}"

    /** An address as a line may show it: no query, no fragment, no userinfo, no character that could break the line. */
    fun url(u: String?): String {
        val bare = ServerRules.withoutQuery(u.orEmpty())
        val colon = bare.indexOf(':')
        val slashes = if (colon >= 0 && bare.startsWith("//", colon + 1)) colon + 3 else -1
        val clean = if (slashes < 0) bare else {
            val end = bare.indexOf('/', slashes).let { if (it < 0) bare.length else it }
            val at = bare.lastIndexOf('@', end - 1)
            if (at >= slashes) bare.substring(0, slashes) + bare.substring(at + 1) else bare
        }
        return RadioText.shown(clean, URL_MAX)
    }

    /** Letters, digits, `+`, `-`, `.` in lower case, at most 16: the form of a scheme and of a codec's name. */
    private fun token(text: String?): String =
        text.orEmpty().lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' || it in "+-." }.take(16)
}

/**
 * The `[music] radio:` lines (phase 20 build task 5), without their tag. Pure (`StreamLineTest`).
 */
object RadioLine {
    /** A directory answer over the 4 MiB response cap: nothing of it is used and the cache is kept. */
    const val TOO_LARGE = "radio: directory too large"

    /** The first open with no network and nothing cached. */
    const val NO_CACHE_OFFLINE = "radio: no cache yet (offline)"

    fun fetched(stations: Int): String = "radio: directory fetched $stations stations"

    /** A fetch that failed, or no network: the cache stays, and the line says how old it is ([date] as `yyyy-MM-dd`). */
    fun offline(date: String): String = "radio: directory offline, cache from ${RadioText.shown(date, 32)}"

    /** The process-start line of Q3's negative: whether the phone exposes a broadcast-radio receiver to apps. */
    fun fm(feature: Boolean): String = "radio: fm feature=$feature"
}

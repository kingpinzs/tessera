package app.tileshell.music.radio

/**
 * What a tap on something streamed does on the network the phone is on (phase 20 Decisions "metered data", "captive
 * portal"; r3 D14; Q4 A). Pure (`StreamGateTest`); [RadioNet.gate] reads the four facts off `ConnectivityManager`.
 *
 *  - no network: a station is refused at once with the gave-up text — not after the 60-s reconnect window;
 *  - a network Android has FLAGGED as a captive portal: a station is refused with "Sign in to this Wi-Fi network
 *    first" and no stream starts. A network that is merely not validated (a just-joined Wi-Fi, a LAN with no internet)
 *    is not captive: the station plays into the reconnect path;
 *  - a metered network: it plays — the person pressed play — under the line "Streaming over mobile data"; with Data
 *    Saver on the line says so too (the foreground service keeps its stream);
 *  - a home-server track is exempt from both refusals: a server on a LAN with no internet must play.
 */
object StreamGate {
    const val SIGN_IN_FIRST = "Sign in to this Wi-Fi network first"
    const val METERED = "Streaming over mobile data"
    const val METERED_DATA_SAVER = "Streaming over mobile data — Data Saver is on"

    /**
     * @param play whether a stream is started
     * @param refusal what the page says instead when it is not
     * @param meteredLine the caption under the metadata block while it plays, or null
     * @param line the `[music]` diagnostics line of a refusal, or null
     */
    data class Decision(val play: Boolean, val refusal: String? = null, val meteredLine: String? = null, val line: String? = null)

    fun decide(hasNetwork: Boolean, captive: Boolean, metered: Boolean, dataSaver: Boolean, isStation: Boolean): Decision {
        if (isStation && !hasNetwork) return Decision(play = false, refusal = StreamRetry.NOT_ANSWERING, line = StreamLine.NO_NETWORK)
        if (isStation && captive) return Decision(play = false, refusal = SIGN_IN_FIRST, line = StreamLine.CAPTIVE_PORTAL)
        val caption = when {
            !hasNetwork || !metered -> null
            dataSaver -> METERED_DATA_SAVER
            else -> METERED
        }
        return Decision(play = true, meteredLine = caption)
    }
}

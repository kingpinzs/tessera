package app.tileshell.music

import app.tileshell.music.radio.StationUrl
import app.tileshell.music.radio.StreamFacts
import java.net.InetAddress
import java.util.Locale

/**
 * Which address a request of the music player may CONNECT to (phase 20, the review's R20-1; the owner's ruling
 * 2026-10-08). Pure: every case is in `MusicConnectRuleTest`; [MusicHttp] carries the answers out.
 *
 * `StationUrl.accept` and [MusicSourceRule] read an address's text. A station comes from a community directory, and
 * text is not where a request goes: `192.168.1.1.nip.io`, `nas` and `router.lan` are names like any other, and a
 * public station can answer with a redirect. So the question is asked again of the address itself, at the moment of
 * connecting, for a station, for everything its playlist names, and for every redirect of any of them:
 *
 *  - [mayConnect]: EVERY address the name resolved to must be public — the ranges `StationUrl.accept` refuses as
 *    literals ([StationUrl.publicAddress]): loopback, link-local, RFC 1918, carrier-grade NAT, unspecified, multicast,
 *    reserved, and every IPv6 address that is not global unicast. One private answer refuses the name, and so does
 *    no answer at all. The socket is then opened to an address that was judged and to no other ([MusicHttp] asks
 *    this again of the one address it is about to connect to), so a name that answers differently a moment later
 *    (DNS rebinding) gets nothing.
 *  - [hop]: a redirect is judged before it is followed — its scheme must be the request's (a cross-protocol redirect
 *    stays refused, with Media3's own words, so its line stays `stream: redirect refused`), at most [MAX_HOPS], its
 *    address through `StationUrl.accept`, and then [mayConnect] when it connects.
 *
 * Two exceptions, and no third: an address the shell itself queued for the HOME SERVER (`homeServer`; the user's own
 * server is on his private network by design) — that address, and a redirect that stays on its host; a redirect to
 * any other host is judged like a station's. And `qaHost`, the debug-only fixture host (null in a release build).
 */
object MusicConnectRule {
    /** As many redirects as Media3's own http source followed. */
    const val MAX_HOPS = 20

    /** The refusal's whole message: it names no address. */
    const val REFUSED = "the music player refused to connect to an address"
    const val TOO_MANY = "Too many redirects"
    const val NO_LOCATION = "Redirect to no http or https address"

    /**
     * @param host the request's host as the HTTP stack reads it: a name, or an address literal
     * @param resolved every address [host] resolved to — or the one address about to be connected to
     * @param homeServer the request is for an address the shell queued for the home server, or a redirect still on its host
     * @param qaHost the debug-only fixture host, null in a release build
     */
    fun mayConnect(host: String, resolved: List<InetAddress>, homeServer: Boolean, qaHost: String?): Boolean {
        if (homeServer) return true
        if (qaHost != null && qaHost.isNotEmpty() && host.lowercase(Locale.ROOT) == qaHost.lowercase(Locale.ROOT)) return true
        return resolved.isNotEmpty() && resolved.all { StationUrl.publicAddress(it.address) }
    }

    sealed interface Hop {
        /** Follow it; [homeServer] is whether the home server's exception still holds for the next request. */
        data class Follow(val homeServer: Boolean) : Hop

        /** Do not: [message] is the error's ([REFUSED], [TOO_MANY], [NO_LOCATION], or Media3's cross-protocol words). */
        data class Refused(val message: String) : Hop
    }

    /**
     * A redirect answered to a request of scheme [fromScheme] for [fromHost], after [followed] redirects already.
     *
     * @param to the address it points to, resolved against the request's and in lower-case scheme and host; null when
     *   the answer names none, or one that is not `http` / `https`
     * @param homeServer [mayConnect]'s, for the request that was answered
     */
    fun hop(fromScheme: String, fromHost: String, to: Target?, followed: Int, homeServer: Boolean, qaHost: String?): Hop {
        if (to == null) return Hop.Refused(NO_LOCATION)
        if (followed >= MAX_HOPS) return Hop.Refused(TOO_MANY)
        if (to.scheme != fromScheme) return Hop.Refused(StreamFacts.CROSS_PROTOCOL)
        if (homeServer && to.host == fromHost) return Hop.Follow(homeServer = true)
        return if (StationUrl.accept(to.url, qaHost) == StationUrl.Accept.Ok) Hop.Follow(homeServer = false) else Hop.Refused(REFUSED)
    }

    /** Where a redirect points: the whole address, and its scheme and host as the HTTP stack reads them. */
    data class Target(val url: String, val scheme: String, val host: String)
}

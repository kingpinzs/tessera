package app.tileshell.music.radio

import androidx.media3.common.MimeTypes
import app.tileshell.video.server.ServerRules
import java.util.Locale

/**
 * Which address of a station may reach the player (phase 20, r3 D11 / D12; a trust rule). Station addresses come from
 * a community directory, and the service's data source also opens `file://`, `content://`, `asset://`,
 * `rawresource://` and `data:` — with the SHELL's identity. So:
 *
 *  - [playable] picks the one address: the directory's `url_resolved`, else `url`. No playlist is fetched or parsed
 *    by the shell; a leftover `.pls` / `.m3u` / `.asx` is refused.
 *  - [accept] lets only `http` / `https` through, and refuses an empty host, `localhost`, and an IP literal that is
 *    loopback, link-local, RFC 1918, unique-local or unspecified ([ServerRules.isPrivate]) — a station must not be a
 *    way to make the phone call a device on the network it is on. An address in a form only an address parser would
 *    read as one (`127.1`, `0x7f.0.0.1`, a bare number) is refused with them, as are a backslash, white space and a
 *    `%` in the host: what this rule reads and what the platform's parser reads must be the same host.
 *
 * NOT checked, by design and stated for the review: a NAME that resolves to a private address. Nothing is resolved
 * here.
 *
 * `qaHost` is the host of the debug-only radio base override (null in a release build): the one private host let
 * through, so the fixtures at `10.0.2.2` play in a debug build and nowhere else. Pure (`StationUrlTest`); the address
 * is read by hand, so no parser's leniency is borrowed.
 */
object StationUrl {
    sealed interface Accept {
        data object Ok : Accept

        /** Not `http` / `https`: [scheme] is the scheme in lower case, `none` when the address has none. */
        data class UnsupportedScheme(val scheme: String) : Accept

        data object UnsupportedHost : Accept
    }

    sealed interface Playable {
        /** The address to play, and the MIME type the item is given (HLS) or null (the player's own sniffing). */
        data class Stream(val url: String, val mimeType: String?) : Playable

        /** The address is a playlist file the shell does not fetch. */
        data object Playlist : Playable

        /** The row has no address at all. */
        data object None : Playable
    }

    private val PLAYLISTS = listOf(".pls", ".m3u", ".asx")

    fun playable(urlResolved: String?, url: String?, hls: Boolean): Playable {
        val chosen = urlResolved?.trim().orEmpty().ifEmpty { url?.trim().orEmpty() }
        if (chosen.isEmpty()) return Playable.None
        val path = chosen.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
        if (PLAYLISTS.any { path.endsWith(it) }) return Playable.Playlist
        return Playable.Stream(chosen, if (hls) MimeTypes.APPLICATION_M3U8 else null)
    }

    fun accept(url: String?, qaHost: String?): Accept {
        val text = url.orEmpty()
        val scheme = schemeOf(text) ?: return Accept.UnsupportedScheme(NO_SCHEME)
        if (scheme != "http" && scheme != "https") return Accept.UnsupportedScheme(scheme)
        val host = hostOf(text) ?: return Accept.UnsupportedHost
        if (qaHost != null && qaHost.isNotEmpty() && host == qaHost.lowercase(Locale.ROOT)) return Accept.Ok
        return if (isRefusedHost(host)) Accept.UnsupportedHost else Accept.Ok
    }

    /** The host of the debug-only override's base, for [accept]; null when there is no override. */
    fun qaHost(qaBase: String?): String? = qaBase?.let { hostOf(it) }

    const val NO_SCHEME = "none"

    /** The scheme in lower case — a letter, then letters, digits, `+`, `-`, `.`, before the first `:` — else null. */
    private fun schemeOf(text: String): String? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val s = text.substring(0, colon)
        if (!s[0].isAsciiLetter() || !s.all { it.isAsciiLetter() || it in '0'..'9' || it in "+-." }) return null
        return s.lowercase(Locale.ROOT)
    }

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

    /**
     * The host of an `http(s)://…` address in lower case, without brackets, port or a trailing dot; null when the
     * address has none or is in a form two parsers could read differently.
     */
    private fun hostOf(text: String): String? {
        // White space, a control character or a backslash anywhere: parsers disagree on where the host then ends.
        if (text.any { it <= ' ' || it == '\\' || it == '\u007f' }) return null
        val rest = text.substringAfter(':')
        if (!rest.startsWith("//")) return null
        val authority = rest.substring(2).takeWhile { it != '/' && it != '?' && it != '#' }
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            val after = hostPort.substring(close + 1)
            if (after.isNotEmpty() && !(after.startsWith(":") && isPort(after.substring(1)))) return null
            hostPort.substring(1, close).also { if (!it.contains(':')) return null }
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0 && !isPort(hostPort.substring(colon + 1))) return null
            if (colon >= 0) hostPort.substring(0, colon) else hostPort
        }
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        if (h.isEmpty() || h.contains('%') || h.contains('[') || h.contains(']')) return null
        return h
    }

    private fun isPort(text: String): Boolean = text.length <= 5 && text.all { it in '0'..'9' }

    private fun isRefusedHost(h: String): Boolean {
        if (ServerRules.isPrivate(h)) return true
        if (h.contains(':')) {
            // An IPv6 literal whose first group is zero or missing: unspecified, loopback, IPv4-mapped or -compatible.
            val first = h.substringBefore(':')
            return first.isEmpty() || first.all { it == '0' }
        }
        val labels = h.split('.')
        if (labels.any { it.isEmpty() }) return true
        // A numeric last label is an address, not a name. A strict dotted quad that is public may play; 0.0.0.0/8 and
        // every other numeric spelling (fewer parts, octal, hex, one big number) may not.
        val last = labels.last()
        val numeric = last.all { it in '0'..'9' } || (last.startsWith("0x") && last.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
        if (!numeric) return false
        val quad = labels.size == 4 && labels.all { p -> p.length in 1..3 && p.all { it in '0'..'9' } && !(p.length > 1 && p[0] == '0') && p.toInt() <= 255 }
        return !quad || labels[0] == "0"
    }
}

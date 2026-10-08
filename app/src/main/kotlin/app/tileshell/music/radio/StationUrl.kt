package app.tileshell.music.radio

import androidx.media3.common.MimeTypes
import app.tileshell.video.server.ServerRules
import java.net.IDN
import java.util.Locale

/**
 * Which address of a station may reach the player (phase 20, r3 D11 / D12; a trust rule). Station addresses come from
 * a community directory, and the service's data source also opens `file://`, `content://`, `asset://`,
 * `rawresource://` and `data:` — with the SHELL's identity. So:
 *
 *  - [playable] picks the one address: the directory's `url_resolved`, else `url`. No playlist is fetched or parsed
 *    by the shell; a leftover `.pls` / `.m3u` / `.asx` is refused. So is an address Media3 would read as DASH or
 *    SmoothStreaming (review R20-3): neither is linked, and ONE such item makes the player refuse the whole queue it
 *    is handed — silently — so one favourite would stop every favourite.
 *  - [accept] lets only `http` / `https` through, and refuses an empty host, `localhost`, and an IP literal that is
 *    loopback, link-local, RFC 1918, unique-local or unspecified ([ServerRules.isPrivate]) — a station must not be a
 *    way to make the phone call a device on the network it is on. An address in a form only an address parser would
 *    read as one (`127.1`, `0x7f.0.0.1`, a bare number) is refused with them, as are a backslash, white space and a
 *    `%` in the host: what this rule reads and what the platform's parser reads must be the same host.
 *  - (review R20-5 / R20-6) The platform's HTTP stack maps a host to ASCII (IDNA) before it connects — fullwidth and
 *    circled digits become digits, an ideographic full stop a dot, a fullwidth `localhost` the word — so the host is
 *    JUDGED AFTER THAT MAPPING, and one that is then anything but letters, digits, `.`, `-` and `_` is refused. An
 *    IPv6 literal is hex groups in brackets, and only a global unicast one (2000::/3, less 6to4's 2002::/16) may
 *    play: site-local, multicast, NAT64 and every unallocated block go with loopback and link-local. An IPv4 literal
 *    in carrier-grade NAT (100.64/10), the benchmarking and protocol blocks (198.18/15, 192.0.0/24), multicast or
 *    the reserved top (224/3) is refused. An authority with two ports, or a bare IPv6 address, has no host.
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
        // With no MIME type on the item the player infers the source type from the address; with one, it goes by that.
        if (!hls && isUnlinkedType(chosen)) return Playable.Playlist
        return Playable.Stream(chosen, if (hls) MimeTypes.APPLICATION_M3U8 else null)
    }

    /** Media3's own pattern for a SmoothStreaming address (`Util.ISM_PATH_PATTERN`, 1.9.0), case-insensitive as there. */
    private val ISM_PATH = Regex("(?:.*\\.)?isml?(?:/(manifest(.*))?)?", RegexOption.IGNORE_CASE)
    private val ESCAPE = Regex("%([0-9A-Fa-f]{2})")

    /**
     * Whether Media3 1.9.0 would infer DASH or SmoothStreaming for [address] when its item has no MIME type —
     * `Util.inferContentType(Uri)`, read from the bytecode and followed step by step: the extension of the last
     * non-empty path segment, DECODED (`mpd` → DASH, `ism` / `isml` → SmoothStreaming, `m3u8` → HLS, which is linked);
     * else the whole decoded path against [ISM_PATH] (`….ism`, `….isml`, with `/` or `/manifest…` after it) — DASH
     * when the manifest asks `format=mpd-time-csf`, HLS when it asks `format=m3u8-aapl`, SmoothStreaming otherwise.
     * (RTSP is inferred from the scheme alone, and [accept] refuses every scheme but `http` / `https`.)
     */
    private fun isUnlinkedType(address: String): Boolean {
        val path = pathOf(address)
        val last = path.split('/').lastOrNull { it.isNotEmpty() }?.let(::unescaped) ?: return false
        val dot = last.lastIndexOf('.')
        if (dot >= 0) {
            when (last.substring(dot + 1).lowercase(Locale.ROOT)) {
                "mpd", "ism", "isml" -> return true
                "m3u8" -> return false
            }
        }
        val manifest = ISM_PATH.matchEntire(unescaped(path))?.groupValues?.get(2) ?: return false
        return manifest.contains("format=mpd-time-csf") || !manifest.contains("format=m3u8-aapl")
    }

    /** The path as the player's parser reads it: after the authority, to the first `?` or `#`; a bare path is all path. */
    private fun pathOf(address: String): String {
        val bare = address.substringBefore('#').substringBefore('?')
        val rest = if (schemeOf(bare) != null) bare.substringAfter(':') else bare
        if (!rest.startsWith("//")) return rest
        val slash = rest.indexOf('/', 2)
        return if (slash < 0) "" else rest.substring(slash)
    }

    /** Percent-escapes decoded once, as the player's parser decodes a path. Only an ASCII byte can become a letter. */
    private fun unescaped(text: String): String =
        ESCAPE.replace(text) { m -> m.groupValues[1].toInt(16).let { if (it < 0x80) it.toChar().toString() else m.value } }

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
        val h = if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return null
            val after = hostPort.substring(close + 1)
            if (after.isNotEmpty() && !(after.startsWith(":") && isPort(after.substring(1)))) return null
            val literal = hostPort.substring(1, close).lowercase(Locale.ROOT)
            // An IPv6 literal: hex groups, and the dotted quad one may end in. A zone id, or any other character, is not one.
            if (!literal.contains(':') || !literal.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }) return null
            literal
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0 && !isPort(hostPort.substring(colon + 1))) return null
            val typed = if (colon >= 0) hostPort.substring(0, colon) else hostPort
            // The host as the platform's HTTP stack will read it: mapped to ASCII first (IDNA). What cannot be mapped
            // has no host; what is mapped must be a name's or a literal's characters and no others — so a second colon
            // (two ports, a bare IPv6 address), a `%`, a bracket, or a `/` `?` `#` `@` the mapping produced, ends here.
            val mapped = runCatching { IDN.toASCII(typed) }.getOrNull() ?: return null
            val name = mapped.lowercase(Locale.ROOT).trimEnd('.')
            if (!name.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }) return null
            name
        }
        return h.ifEmpty { null }
    }

    private fun isPort(text: String): Boolean = text.length <= 5 && text.all { it in '0'..'9' }

    private fun isRefusedHost(h: String): Boolean {
        if (ServerRules.isPrivate(h)) return true
        if (h.contains(':')) {
            // An IPv6 literal may play only when it is global unicast (2000::/3) and not 6to4 (2002::/16, an IPv4
            // address in disguise). So a first group that is zero or missing — unspecified, loopback, IPv4-mapped or
            // -compatible — is refused, and with it NAT64 (64:ff9b::/96), site-local, multicast and the unallocated rest.
            val first = h.substringBefore(':').takeIf { it.length in 1..4 }?.toIntOrNull(16) ?: return true
            return first !in 0x2000..0x3fff || first == 0x2002
        }
        val labels = h.split('.')
        if (labels.any { it.isEmpty() }) return true
        // A numeric last label is an address, not a name. A strict dotted quad that is public may play; 0.0.0.0/8 and
        // every other numeric spelling (fewer parts, octal, hex, one big number) may not.
        val last = labels.last()
        val numeric = last.all { it in '0'..'9' } || (last.startsWith("0x") && last.drop(2).all { it in '0'..'9' || it in 'a'..'f' })
        if (!numeric) return false
        val quad = labels.size == 4 && labels.all { p -> p.length in 1..3 && p.all { it in '0'..'9' } && !(p.length > 1 && p[0] == '0') && p.toInt() <= 255 }
        if (!quad) return true
        // 0/8, carrier-grade NAT (100.64/10), benchmarking (198.18/15), the protocol block (192.0.0/24), and
        // multicast, the reserved block and broadcast (224/3): nobody's station, and some of them somebody's network.
        val (a, b, c) = labels.map { it.toInt() }
        return a == 0 || (a == 100 && b in 64..127) || (a == 198 && b in 18..19) || (a == 192 && b == 0 && c == 0) || a >= 224
    }
}

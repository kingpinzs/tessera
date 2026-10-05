package app.tileshell.video.server

import app.tileshell.net.MiniJson
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import java.util.Locale

/**
 * A media server's address as the user typed it, read into its parts.
 * @param base `scheme://host[:port]` with no trailing slash — what requests are built on
 * @param label `host[:port]` — what a diagnostics line names (never a path, a query or a user part)
 */
data class ServerAddress(val scheme: String, val host: String, val port: Int?, val base: String, val label: String)

/** One video of the server's library. */
data class ServerItem(val id: String, val name: String, val type: String)

/** How a sign-in or a library read ended (`[video] server <host>: connected | unreachable | unauthorised`). */
enum class ServerState(val word: String) { CONNECTED("connected"), UNREACHABLE("unreachable"), UNAUTHORISED("unauthorised") }

/**
 * The media server's rules that need no network (build task 13; C-16 (5)). TRUST-TOUCHING: [needsInsecurePrompt] is
 * what stands between a typed password and a plain-http request to a host outside the home network.
 */
object ServerRules {
    /** Jellyfin's own default port for a plain-http address typed without one. */
    const val DEFAULT_HTTP_PORT = 8096

    const val TEXT_INSECURE = "This server isn't secure — your password would be sent unencrypted"
    const val TEXT_BAD_PASSWORD = "That password isn't right"
    const val TEXT_UNREACHABLE = "Can't reach your media server"

    private val HOST_NAME = Regex("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?")
    private val IPV6_CHARS = Regex("[0-9A-Fa-f:.]{2,45}")

    /**
     * Reads "host", "host:port", "http://host:port" or "https://host[:port]". With no scheme the address is plain http
     * (Jellyfin's default), and plain http with no port is port 8096. Null when it is not an address: any other
     * scheme, a user part, a path, a query, spaces.
     */
    fun parse(typed: String): ServerAddress? {
        var text = typed.trim().trimEnd('/')
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val scheme = when {
            text.startsWith("http://", ignoreCase = true) -> "http".also { text = text.substring(7) }
            text.startsWith("https://", ignoreCase = true) -> "https".also { text = text.substring(8) }
            text.contains("://") -> return null
            else -> "http"
        }
        if (text.isEmpty() || text.contains('/') || text.contains('?') || text.contains('#') || text.contains('@')) return null
        val host: String
        val portText: String?
        if (text.startsWith("[")) {
            val close = text.indexOf(']')
            if (close < 0) return null
            host = text.substring(1, close)
            if (!IPV6_CHARS.matches(host) || !host.contains(':')) return null
            val rest = text.substring(close + 1)
            portText = if (rest.isEmpty()) null else if (rest.startsWith(":")) rest.substring(1) else return null
        } else {
            val colon = text.lastIndexOf(':')
            if (colon >= 0 && text.indexOf(':') != colon) return null   // an IPv6 literal must be in brackets
            host = if (colon >= 0) text.substring(0, colon) else text
            portText = if (colon >= 0) text.substring(colon + 1) else null
            if (!HOST_NAME.matches(host)) return null
        }
        val typedPort = portText?.let { p -> p.toIntOrNull()?.takeIf { it in 1..65535 && p.all(Char::isDigit) } ?: return null }
        val port = typedPort ?: if (scheme == "http") DEFAULT_HTTP_PORT else null
        val shownHost = if (host.contains(':')) "[$host]" else host.lowercase(Locale.ROOT)
        val label = if (port != null) "$shownHost:$port" else shownHost
        return ServerAddress(scheme, host.lowercase(Locale.ROOT), port, "$scheme://$label", label)
    }

    /**
     * Whether [host] is on the home network by its NAME OR LITERAL alone (nothing is resolved): RFC 1918 addresses,
     * link-local (169.254/16, fe80::/10), loopback (127/8, ::1, "localhost"), IPv6 unique-local (fc00::/7), `.local`
     * names — and so the emulator's host 10.0.2.2. A public name, a public IPv4 address and a global IPv6 address are
     * not. Anything that is not a strict dotted quad or a recognised form counts as a public name, so an odd spelling
     * of an address ("127.1", "0x7f.0.0.1", a decimal integer) is asked about rather than trusted.
     */
    fun isPrivate(host: String): Boolean {
        val h = host.trim().trim('[', ']').lowercase(Locale.ROOT).trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h.endsWith(".local") && h.length > ".local".length) return true
        ipv4(h)?.let { (a, b) ->
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (h.contains(':')) {
            if (h == "::1") return true
            val first = h.substringBefore(':').toIntOrNull(16) ?: return false
            if (h.startsWith("::")) return false
            return (first and 0xffc0) == 0xfe80 || (first and 0xfe00) == 0xfc00
        }
        return false
    }

    /** The first two octets of a strict dotted quad (four decimal parts, 0–255, no leading zeros), else null. */
    private fun ipv4(h: String): Pair<Int, Int>? {
        val parts = h.split('.')
        if (parts.size != 4) return null
        val n = parts.map { p -> if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit) || (p.length > 1 && p[0] == '0')) return null else p.toInt() }
        if (n.any { it > 255 }) return null
        return n[0] to n[1]
    }

    /** C-16 (5): a sign-in over plain http to an address that is not private asks first, and sends nothing until Continue. */
    fun needsInsecurePrompt(address: ServerAddress): Boolean = address.scheme == "http" && !isPrivate(address.host)

    /** `[video] server <host>: <state>` — the host and the state only. */
    fun line(label: String, state: String): String = "server $label: $state"

    /** A sign-in's or a read's HTTP status as a state: 401 and 403 are the server refusing who we are. */
    fun stateOf(status: Int): ServerState = when (status) {
        in 200..299 -> ServerState.CONNECTED
        401, 403 -> ServerState.UNAUTHORISED
        else -> ServerState.UNREACHABLE
    }

    /** Jellyfin's sign-in header (BS-5): who is calling; with a token, the same line carries it. Values are quoted. */
    fun authorization(deviceName: String, deviceId: String, version: String, token: String?): String {
        fun q(v: String) = v.replace("\"", "").replace("\\", "").replace("\n", " ")
        val base = "MediaBrowser Client=\"$CLIENT\", Device=\"${q(deviceName)}\", DeviceId=\"${q(deviceId)}\", Version=\"${q(version)}\""
        return if (token == null) base else "$base, Token=\"${q(token)}\""
    }

    /** The name the server lists the shell under (`AppName`). */
    const val CLIENT = "Tessera"

    /** `POST /Users/AuthenticateByName`'s answer → the access token and the user's id. */
    fun parseSignIn(body: String): Pair<String, String>? {
        val o = MiniJson.parseOrNull(body).jsonObject() ?: return null
        val token = o.jsonString("AccessToken")?.takeIf { it.isNotBlank() } ?: return null
        val user = o["User"].jsonObject()?.jsonString("Id")?.takeIf { it.isNotBlank() } ?: return null
        return token to user
    }

    /** `GET /Items?userId=…` → the library's videos, in the server's order. */
    fun parseItems(body: String): List<ServerItem>? {
        val o = MiniJson.parseOrNull(body).jsonObject() ?: return null
        if (!o.containsKey("Items")) return null
        return o["Items"].jsonArray().mapNotNull { row ->
            val item = row.jsonObject() ?: return@mapNotNull null
            val id = item.jsonString("Id")?.takeIf(SAFE_ID::matches) ?: return@mapNotNull null
            ServerItem(id, item.jsonString("Name").orEmpty().ifBlank { id }, item.jsonString("Type").orEmpty())
        }
    }

    /** An item id is what Jellyfin makes (32 hex digits, or a GUID): it becomes part of a path. */
    private val SAFE_ID = Regex("[0-9A-Fa-f-]{1,64}")

    fun libraryPath(userId: String): String =
        "/Items?userId=$userId&recursive=true&includeItemTypes=Movie,Episode,Video&sortBy=SortName"

    /** Direct play (BS-5): the address the player is given. The token is NOT in it — the player's data source adds `ApiKey`. */
    fun streamUrl(base: String, itemId: String): String = "$base/Videos/$itemId/stream?static=true"

    private val STREAM_PATH = Regex("/Videos/[0-9A-Fa-f-]{1,64}/stream")

    /**
     * Whether the player may add the server's token to a request: only for the direct-play path, only on the saved
     * server's own scheme, host and port, and only when the address carries no `ApiKey` of its own. So the token can
     * never be attached to an address another app made up for another host.
     */
    fun mayCarryToken(url: String, savedBase: String): Boolean {
        val (origin, rest) = split(url) ?: return false
        val saved = split(savedBase)?.first ?: return false
        if (origin != saved) return false
        val path = rest.substringBefore('?').substringBefore('#')
        val query = rest.substringAfter('?', "").substringBefore('#')
        if (!STREAM_PATH.matches(path)) return false
        return query.split('&').none { it.substringBefore('=').equals("ApiKey", ignoreCase = true) || it.substringBefore('=').equals("api_key", ignoreCase = true) }
    }

    /** `scheme://authority` in lower case with the scheme's default port made explicit, and what follows it. */
    private fun split(url: String): Pair<String, String>? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val after = url.substring(schemeEnd + 3)
        val cut = after.indexOfFirst { it == '/' || it == '?' || it == '#' }
        var authority = (if (cut < 0) after else after.substring(0, cut)).lowercase(Locale.ROOT)
        if (authority.isEmpty() || authority.contains('@')) return null
        val hasPort = if (authority.startsWith("[")) authority.substringAfter(']').startsWith(":") else authority.contains(':')
        if (!hasPort) authority += if (scheme == "http") ":80" else ":443"
        return "$scheme://$authority" to (if (cut < 0) "" else after.substring(cut))
    }

    /** A stream address as a line may show it: the query string (the token's place) removed (C-32). */
    fun withoutQuery(url: String): String = url.substringBefore('?').substringBefore('#')
}

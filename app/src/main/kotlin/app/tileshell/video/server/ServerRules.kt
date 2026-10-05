package app.tileshell.video.server

import app.tileshell.net.HeaderText
import app.tileshell.net.MiniJson
import app.tileshell.video.catalogue.CatalogueRules
import app.tileshell.net.jsonArray
import app.tileshell.net.jsonObject
import app.tileshell.net.jsonString
import app.tileshell.video.VideoLines
import java.util.Locale

/**
 * A media server's address as the user typed it, read into its parts.
 * @param base `scheme://host[:port]` with no trailing slash — what requests are built on
 * @param label `host[:port]` — what a diagnostics line names (never a path, a query or a user part)
 */
data class ServerAddress(val scheme: String, val host: String, val port: Int?, val base: String, val label: String)

/** One video of the server's library. */
data class ServerItem(val id: String, val name: String, val type: String)

/** What the sign-in form may do with an address: see [ServerRules.signInAction]. */
enum class SignInAction { ASK, SEND, NOTHING }

/** The user's answer to "This server isn't secure". */
enum class PromptAnswer { CONTINUE, CANCEL }

/** A sign-in's answer, read: the token and the user's id. [toString] holds neither (B2-L1). */
data class SignIn(val token: String, val userId: String) {
    override fun toString(): String = "SignIn"
}

/** What the Media server setting shows: see [ServerRules.settingsView]. */
enum class ServerSettingsView { SERVER, LEFT_OVER, FORM }

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

    /** The state word of a sign-in whose answer cannot be used (no token, or one that is not a plain token). */
    const val WORD_BAD_ANSWER = "sign-in answer not usable"

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

    /**
     * What the sign-in form does next (C-16 (5); B-3). [answer] is null when the user has just pressed Connect, else
     * what he chose on the "isn't secure" prompt. Only [SignInAction.SEND] lets a request of any kind leave the phone:
     * an address that needs the prompt is ASKED about first, Cancel sends NOTHING, and only Continue sends.
     */
    fun signInAction(address: ServerAddress, answer: PromptAnswer?): SignInAction = when {
        !needsInsecurePrompt(address) -> SignInAction.SEND
        answer == null -> SignInAction.ASK
        answer == PromptAnswer.CONTINUE -> SignInAction.SEND
        else -> SignInAction.NOTHING
    }

    /**
     * Where the sign-in is sent: the address the user typed, or — in a DEBUG build only — the `qa_server_base` pref's
     * value. The same gate as the catalogue's and Wikidata's QA prefs ([CatalogueRules.base]): a release build cannot
     * be redirected, whatever the pref holds.
     */
    fun signInBase(debug: Boolean, qaPref: String?, typedBase: String): String =
        CatalogueRules.base(debug, qaPref, typedBase).trimEnd('/')

    /**
     * What "Sign in again" puts in the address field (B-2): the saved server's whole address — scheme and port kept —
     * so a server saved as https is signed in to over https again. [parse] reads it back to the same server.
     */
    fun signInAgainPrefill(saved: ServerConfig?): String = saved?.address?.takeIf { it.isNotBlank() } ?: saved?.label.orEmpty()

    /** `[video] server <host>: <state>` — the host and the state only. */
    fun line(label: String, state: String): String = "server ${VideoLines.text(label)}: $state"

    /** `[video] server stream <address>`: the address with its query string — the token's place — removed (C-32). */
    fun streamLine(url: String): String = "server stream ${VideoLines.text(withoutQuery(url), VideoLines.ADDRESS_MAX)}"

    /** A sign-in's or a read's HTTP status as a state: 401 and 403 are the server refusing who we are. */
    fun stateOf(status: Int): ServerState = when (status) {
        in 200..299 -> ServerState.CONNECTED
        401, 403 -> ServerState.UNAUTHORISED
        else -> ServerState.UNREACHABLE
    }

    /**
     * Jellyfin's sign-in header (BS-5): who is calling; with a token, the same line carries it. Values are quoted, and
     * what comes back is always a value a header can carry ([HeaderText.isHeaderSafe]).
     */
    fun authorization(deviceName: String, deviceId: String, version: String, token: String?): String {
        // Every control character, the quote and the backslash are dropped from every field (B-1): a value the
        // platform would refuse as a header is one whose exception quotes the token.
        fun q(v: String) = HeaderText.quoted(v)
        val base = "MediaBrowser Client=\"$CLIENT\", Device=\"${q(deviceName)}\", DeviceId=\"${q(deviceId)}\", Version=\"${q(version)}\""
        return if (token == null) base else "$base, Token=\"${q(token)}\""
    }

    /** The name the server lists the shell under (`AppName`). */
    const val CLIENT = "Tessera"

    /**
     * `POST /Users/AuthenticateByName`'s answer → the access token and the user's id. Null — a failed sign-in — when
     * either is missing, when the token is anything but a plain token (letters, digits, `.`, `_`, `-`: it is sent in a
     * header and in a stream's query), or when the user's id is not an id (it becomes part of an address). B-1.
     */
    fun parseSignIn(body: String): SignIn? {
        val o = MiniJson.parseOrNull(body).jsonObject() ?: return null
        val token = o.jsonString("AccessToken")?.takeIf(HeaderText::isSafeToken) ?: return null
        val user = o["User"].jsonObject()?.jsonString("Id")?.takeIf(SAFE_ID::matches) ?: return null
        return SignIn(token, user)
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

    /**
     * Whether a source could be a media server's direct-play address at all: http(s), and a path that ends `/stream`.
     * Only such a source is ever looked at for the token ([mayCarryToken] then decides, per request).
     */
    fun couldBeStream(scheme: String?, path: String?): Boolean =
        (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) && path?.endsWith("/stream") == true

    private val STREAM_PATH = Regex("/Videos/[0-9A-Fa-f-]{1,64}/stream")

    /**
     * Whether the player may add the server's token to a request: only for the direct-play path, only on the saved
     * server's own scheme, host and port, and only when the address carries no `ApiKey` / `api_key` of its own — the
     * key's name read as the server would read it: percent-decoded, in any case, separated by `&` or `;` (B2-L6). So
     * the token can never be attached to an address another app made up for another host.
     */
    fun mayCarryToken(url: String, savedBase: String): Boolean {
        val (origin, rest) = split(url) ?: return false
        val saved = split(savedBase)?.first ?: return false
        if (origin != saved) return false
        val path = rest.substringBefore('?').substringBefore('#')
        val query = rest.substringAfter('?', "").substringBefore('#')
        if (!STREAM_PATH.matches(path)) return false
        // The caller's own key, however it is spelled (B2-L6): percent-encoded, in any case, after `&` or `;`.
        return query.split('&', ';').none { isKeyParameter(it.substringBefore('=')) }
    }

    /**
     * Whether [url] is an address of the server at [base]: the same scheme, host and port (a scheme's default port
     * made explicit), with no user part. What every request that carries the token is checked with (B-4).
     */
    fun sameServer(url: String, base: String): Boolean {
        val origin = split(url)?.first ?: return false
        return origin == split(base)?.first
    }

    /**
     * A sealed server's parts as one text, and back. [pair] is the save's own id, written into the pages' file too
     * ([isPair]): it is what says the two belong to one save (B2-M1). Null from [openCredential] when any part is not
     * what it must be — so the bare token an earlier build stored, and an entry sealed before the pair id was kept,
     * are not sealed servers.
     */
    fun sealCredential(credential: ServerCredential, pair: String): String =
        MiniJson.write(linkedMapOf("token" to credential.token, "base" to credential.base, "userId" to credential.userId, "pair" to pair))

    fun openCredential(text: String): SealedServer? {
        val o = MiniJson.parseOrNull(text).jsonObject() ?: return null
        val token = o.jsonString("token")?.takeIf(HeaderText::isSafeToken) ?: return null
        // A server's address and nothing more: http(s), a host, a port — no user part, no path, no query.
        val base = o.jsonString("base")?.takeIf { parse(it) != null && it.contains("://") } ?: return null
        val userId = o.jsonString("userId")?.takeIf(SAFE_ID::matches) ?: return null
        val pair = o.jsonString("pair")?.takeIf(::isPair) ?: return null
        return SealedServer(ServerCredential(token, base, userId), pair)
    }

    private val PAIR = Regex("[0-9a-f]{32}")

    /** A save's id: 32 hex digits, made new for every save. Not a secret — it names nothing and opens nothing. */
    fun isPair(value: String): Boolean = PAIR.matches(value)

    /**
     * Whether the sealed server and the pages' file are ONE saved server (B2-M1): both are there and carry the same
     * save's id. Anything else — no file (a save cut in half, a removal that could not clear the entry), a file from
     * another save — is not a set-up server, and no token is given for it.
     */
    fun paired(sealedPair: String?, filePair: String?): Boolean = sealedPair != null && isPair(sealedPair) && sealedPair == filePair

    const val TEXT_LEFT_OVER = "A saved sign-in that isn't in use is still on this phone. Remove it, or sign in again."

    /**
     * What the Media server setting shows (B2-M1). Whenever an entry is stored under the server's name — whether or
     * not it is a whole saved server — the page offers "Remove this server" ([offersRemove]), so a half-saved or
     * half-removed state can always be cleared by hand: the server itself while one is set up, else [TEXT_LEFT_OVER]
     * above the sign-in form, else the form alone.
     */
    fun settingsView(setUp: Boolean, entryHeld: Boolean): ServerSettingsView = when {
        setUp -> ServerSettingsView.SERVER
        entryHeld -> ServerSettingsView.LEFT_OVER
        else -> ServerSettingsView.FORM
    }

    fun offersRemove(view: ServerSettingsView): Boolean = view != ServerSettingsView.FORM

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

    /**
     * The address the player REPORTS for a request it opened (B-5): the same address with any `ApiKey` / `api_key`
     * parameter taken out — after `&` or after `;` — so the token the data source added is in nothing the player hands on — an error, a load
     * event, a listener.
     */
    fun reportedUrl(url: String): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val hash = url.indexOf('#', q).let { if (it < 0) url.length else it }
        // Each parameter with the separator before it, `&` or `;` (B2-L6); what is kept keeps its own separator.
        val kept = mutableListOf<String>()
        var start = q + 1
        var separator = ""
        while (true) {
            val end = (start until hash).firstOrNull { url[it] == '&' || url[it] == ';' } ?: hash
            val parameter = url.substring(start, end)
            if (!isKeyParameter(parameter.substringBefore('='))) kept += if (kept.isEmpty()) parameter else separator + parameter
            if (end >= hash) break
            separator = url[end].toString()
            start = end + 1
        }
        return url.substring(0, q) + (if (kept.isEmpty()) "" else "?" + kept.joinToString("")) + url.substring(hash)
    }

    /** `ApiKey` or `api_key` in any case, as written or percent-encoded. */
    private fun isKeyParameter(name: String): Boolean {
        val plain = runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
        return plain.equals("ApiKey", ignoreCase = true) || plain.equals("api_key", ignoreCase = true)
    }

    /** A stream address as a line may show it: the query string (the token's place) removed (C-32). */
    fun withoutQuery(url: String): String = url.substringBefore('?').substringBefore('#')
}

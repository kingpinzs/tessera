package app.tileshell.net

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trust review's C-M2: "the list of fixed endpoints is complete" as a test. Every `http://` and `https://` inside a
 * STRING LITERAL of every Kotlin file under `app/src/main` is read (comments are not literals and are skipped), and each
 * must be one of:
 *  1. an `https://` URL of a host in [FixedEndpoints.HOSTS], or of a subdomain of one — the hosts the network security
 *     config denies cleartext to;
 *  2. a host on [NOT_ENDPOINTS], in the file named there: text that is never requested by the shell;
 *  3. a literal with no host of its own (a bare `http://` prefix, a `${…}` host), in a file on [DYNAMIC]: a URL whose
 *     host comes from the user or from another app.
 * Anything else fails with its file and line: a new endpoint goes into `FixedEndpoints` and both configs, not here.
 */
class EndpointLiteralScanTest {
    /** Hosts that are in a literal and are NOT requested by the shell: host to (why, the one file that may hold it). */
    private val NOT_ENDPOINTS: Map<String, Pair<String, String>> = buildMap {
        // XML namespace and schema URIs: identifiers inside a document, never fetched.
        for (ns in listOf("ns.google.com", "ns.adobe.com", "www.w3.org")) put(ns, "an XMP namespace URI of the Motion Photo packet" to "media/MotionPhoto.kt")
        // The XML parser's feature identifiers (they switch DTDs and external entities OFF).
        for (id in listOf("apache.org", "xml.org")) put(id, "an XML parser feature id" to "tiles/api/TileXml.kt")
        // A VIEW intent the package manager resolves to find the browser; it is never started.
        put("example.com", "the probe URL the BROWSER slot is resolved with" to "tiles/SlotResolver.kt")
        // "Watch on": links handed to ANOTHER app with a VIEW intent. The shell requests none of them.
        for (service in listOf(
            "www.netflix.com", "app.primevideo.com", "www.primevideo.com", "www.disneyplus.com", "www.hulu.com", "play.hbomax.com",
            "tv.apple.com", "www.paramountplus.com", "www.peacocktv.com", "www.youtube.com", "watch.plex.tv",
        )) put(service, "a streaming service's link, handed to that service's app" to "video/handoff/ServicesTable.kt")
        // The QA fixture service of the same table: offered in a debug build only, and a reserved name (.test) that resolves nowhere.
        put("qa-flix.test", "the debug-only fixture service's link" to "video/handoff/ServicesTable.kt")
    }

    /** Files that hold a scheme prefix or a URL whose host is not in the literal: file to why. */
    private val DYNAMIC: Map<String, String> = mapOf(
        "video/server/ServerRules.kt" to "reads the scheme off the media server address the user typed (Q-D A: the user's own server)",
        "video/catalogue/CatalogueRules.kt" to "accepts a QA base URL pref by its scheme, in a debug build only",
    )

    private data class Literal(val line: Int, val text: String)
    private data class Finding(val file: String, val line: Int, val url: String, val why: String) {
        override fun toString() = "$file:$line $url — $why"
    }

    /**
     * The string literals of a Kotlin source, each with the line it starts on. Line and block comments (nested, as
     * Kotlin nests them) are skipped; a `${…}` template is code, read for literals of its own, and stands in the
     * enclosing literal's text as `${}`.
     */
    private fun literals(source: String): List<Literal> {
        val out = mutableListOf<Literal>()
        var i = 0
        var line = 1
        fun at(s: String) = source.startsWith(s, i)

        fun code(untilBrace: Boolean) {
            var depth = 0
            while (i < source.length) {
                val c = source[i]
                when {
                    at("//") -> while (i < source.length && source[i] != '\n') i++
                    at("/*") -> {
                        var nested = 0
                        while (i < source.length) {
                            if (at("/*")) { nested++; i += 2 } else if (at("*/")) { nested--; i += 2; if (nested == 0) break } else { if (source[i] == '\n') line++; i++ }
                        }
                    }
                    at("\"\"\"") || c == '"' -> {
                        val raw = at("\"\"\"")
                        val start = line
                        val text = StringBuilder()
                        i += if (raw) 3 else 1
                        while (i < source.length) {
                            if (raw && at("\"\"\"")) { i += 3; while (i < source.length && source[i] == '"') { text.append('"'); i++ }; break }
                            if (!raw && source[i] == '"') { i++; break }
                            if (!raw && source[i] == '\\') { text.append(source[i]).append(source.getOrElse(i + 1) { ' ' }); i += 2; continue }
                            if (at("\${")) { i += 2; text.append("\${}"); code(untilBrace = true); continue }
                            if (source[i] == '\n') line++
                            text.append(source[i]); i++
                        }
                        out += Literal(start, text.toString())
                    }
                    c == '\'' -> {
                        // A character literal: 'x', '\n', 'A', '"'.
                        i++
                        if (i < source.length && source[i] == '\\') i += 2
                        while (i < source.length && source[i] != '\'' && source[i] != '\n') i++
                        i++
                    }
                    c == '{' -> { depth++; i++ }
                    c == '}' -> { if (untilBrace && depth == 0) { i++; return }; depth--; i++ }
                    else -> { if (c == '\n') line++; i++ }
                }
            }
        }
        code(untilBrace = false)
        return out
    }

    private val URL = Regex("(?i)(https?)://([^/\\s\"'?#\\\\]*)")

    /** What is wrong with the URL literals of one file ([file] is its path under the source root); empty when nothing is. */
    private fun check(file: String, source: String): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (literal in literals(source)) for (match in URL.findAll(literal.text)) {
            val scheme = match.groupValues[1].lowercase()
            val host = match.groupValues[2].substringAfterLast('@').substringBefore(':').lowercase()
            val url = match.value
            val fixed = FixedEndpoints.HOSTS.any { host == it || host.endsWith(".$it") }
            val why = when {
                fixed && scheme == "https" -> null
                fixed -> "a fixed endpoint must be https"
                host.isEmpty() || '$' in host || '%' in host || '[' in host ->
                    if (file in DYNAMIC) null else "a URL with no host of its own: name this file in DYNAMIC with its reason, or use a FixedEndpoints constant"
                NOT_ENDPOINTS[host]?.second == file -> null
                host in NOT_ENDPOINTS -> "$host is listed as not an endpoint only for ${NOT_ENDPOINTS.getValue(host).second}"
                else -> "$host is not in FixedEndpoints.HOSTS: add the endpoint there and to both network security configs"
            }
            if (why != null) findings += Finding(file, literal.line, url, why)
        }
        return findings
    }

    private val root: File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }

    private fun sources(): List<Pair<String, String>> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(root).invariantSeparatorsPath.removePrefix("kotlin/app/tileshell/") to it.readText() }.toList()

    @Test
    fun `every URL literal in the shell's sources is a fixed endpoint, or is listed as not one`() {
        val all = sources()
        assertTrue("the scan read the sources (${all.size} files)", all.size > 200)
        val findings = all.flatMap { (file, text) -> check(file, text) }
        assertEquals("unlisted URL literals:\n" + findings.joinToString("\n"), emptyList<Finding>(), findings)
    }

    @Test
    fun `the scan sees the literals it is there for, so a clean result is not an empty one`() {
        val seen = sources().flatMap { (file, text) -> literals(text).flatMap { l -> URL.findAll(l.text).map { file to it.groupValues[2].lowercase() } } }
        // Each fixed endpoint's constant, and each kind of listed literal, is found where it lives.
        for (host in FixedEndpoints.HOSTS) assertTrue(host, ("net/FixedEndpoints.kt" to host) in seen)
        assertTrue(("media/MotionPhoto.kt" to "ns.google.com") in seen)
        assertTrue(("video/handoff/ServicesTable.kt" to "www.netflix.com") in seen)
        assertTrue(("video/server/ServerRules.kt" to "") in seen)
        // Every entry of the two lists is still needed: a stale entry is removed, not left as a standing exception.
        for ((host, entry) in NOT_ENDPOINTS) assertTrue("$host in ${entry.second} (${entry.first})", (entry.second to host) in seen)
        for (file in DYNAMIC.keys) assertTrue(file, seen.any { it.first == file && (it.second.isEmpty() || '$' in it.second) })
        assertTrue("no listed host is also a fixed endpoint", NOT_ENDPOINTS.keys.none { h -> FixedEndpoints.HOSTS.any { h == it || h.endsWith(".$it") } })
    }

    @Test
    fun `an unlisted literal is caught - by host, by scheme, by file and by shape`() {
        fun whys(file: String, source: String) = check(file, source).map { "${it.line} ${it.url}" }
        assertEquals(listOf("2 http://telemetry.example.net"), whys("video/New.kt", "class A {\n  val u = \"http://telemetry.example.net/v1/ping\"\n}"))
        assertEquals(listOf("1 https://api.unlisted.org"), whys("weather/W.kt", "const val E = \"https://api.unlisted.org/x\""))
        // A fixed host over cleartext, and a look-alike of one.
        assertEquals(listOf("1 http://api.themoviedb.org"), whys("video/V.kt", "val a = \"http://api.themoviedb.org/3/x\""))
        assertEquals(listOf("1 https://api.themoviedb.org.evil.net"), whys("video/V.kt", "val a = \"https://api.themoviedb.org.evil.net/3\""))
        assertEquals(listOf("1 https://evilmusicbrainz.org"), whys("music/M.kt", "val a = \"https://evilmusicbrainz.org/ws\""))
        assertEquals(listOf("1 https://musicbrainz.org@evil.net"), whys("music/M.kt", "val a = \"https://musicbrainz.org@evil.net/ws\""))
        // A listed non-endpoint in a file it is not listed for; a host-less URL in a file not named for it.
        assertEquals(listOf("1 https://www.netflix.com"), whys("video/Other.kt", "val a = \"https://www.netflix.com/title/1\""))
        assertEquals(listOf("1 https://\${}"), whys("video/Other.kt", "val a = \"https://\${host}/x\""))
        assertEquals(listOf("1 http://"), whys("music/M.kt", "val a = \"http://\" + host"))
        // Raw strings, a literal inside a template, upper case, and several on one line.
        assertEquals(listOf("1 HTTPS://API.UNLISTED.ORG"), whys("a/A.kt", "val a = \"\"\"HTTPS://API.UNLISTED.ORG/x\"\"\""))
        assertEquals(listOf("1 http://inner.example.net"), whys("a/A.kt", "val a = \"x \${f(\"http://inner.example.net\")} y\""))
        assertEquals(listOf("1 http://a.example.net", "1 http://b.example.net"), whys("a/A.kt", "val a = \"http://a.example.net http://b.example.net\""))
    }

    @Test
    fun `what is allowed is allowed, and a comment is not a literal`() {
        fun clean(file: String, source: String) = assertEquals(source, emptyList<Finding>(), check(file, source))
        clean("video/V.kt", "val a = \"https://api.themoviedb.org/3/configuration\"")
        clean("music/M.kt", "val a = \"https://ia800.us.archive.org/1/items/x.jpg\"")
        clean("media/MotionPhoto.kt", "val ns = \"http://ns.google.com/photos/1.0/camera/\"")
        clean("video/server/ServerRules.kt", "val s = text.startsWith(\"http://\")")
        clean("a/A.kt", "// see http://docs.example.net/x\nval a = 1 /* http://block.example.net /* nested http://n.example.net */ still */\n/** KDoc http://kdoc.example.net */")
        clean("a/A.kt", "val c = '\"' // http://after-a-quote-char.example.net\nval d = \"no url\"")
        clean("a/A.kt", "val a = \"an escaped quote \\\" then // not a comment\" // http://real-comment.example.net")
        // And a literal after such lines is still read.
        assertEquals(1, check("a/A.kt", "val c = '\"'\nval a = \"escaped \\\" quote\"\nval u = \"http://caught.example.net\"").size)
        assertEquals(1, check("a/A.kt", "/* c */ val t = \"\${if (x) { \"a\" } else { \"b\" }}\"; val u = \"http://caught.example.net\"").size)
    }
}

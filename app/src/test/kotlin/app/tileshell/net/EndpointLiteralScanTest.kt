package app.tileshell.net

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trust review's C-M2 and C2-L7: "the list of fixed endpoints is complete" as a test.
 *
 * THIS IS A TRIPWIRE AGAINST AN HONEST MISTAKE, NOT A GUARD. It catches a developer who writes a new address the plain
 * way and forgets `FixedEndpoints` and the network security config. It does not stop anyone who means to hide one: a
 * scheme or a host assembled from pieces, a host read from a file or a resource at run time, a native library, or
 * reflection all pass it. What actually binds the shell's traffic is the network security config and the code review.
 *
 * What it reads: every Kotlin and Java source under `app/src/main` and under the `:calc` module's `src/main`; the
 * manifests under `app/src`; and every `res/values*` XML file. In sources, every `http://` and `https://` inside a
 * STRING LITERAL (comments are not literals and are skipped); also a literal that holds a bare `://` with no scheme
 * before it, and a `URL(…)`, `URI(…)` or `Uri.Builder()` / `.scheme(…)` in code — the plain ways an address is built
 * from pieces — outside `net/`, unless the file is listed with its reason. Each URL literal must be one of:
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
        "music/catalogue/CoverArt.kt" to "reads the scheme off a Cover Art redirect's Location, to refuse every host but https archive.org (phase 20, r3 D13)",
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

    /** The `:calc` module's sources (it is compiled into the same APK). */
    private val calcRoot: File = listOf(File("../calc/src/main"), File("calc/src/main")).first { it.isDirectory }

    /** Kotlin and Java sources: the shell's under their path below `app/tileshell/`, `:calc`'s under `calc:<path>`. */
    private fun sources(): List<Pair<String, String>> =
        root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .map { it.relativeTo(root).invariantSeparatorsPath.removePrefix("kotlin/app/tileshell/").removePrefix("java/app/tileshell/") to it.readText() }.toList() +
            calcRoot.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .map { "calc:" + it.relativeTo(calcRoot).invariantSeparatorsPath to it.readText() }.toList()

    /** The manifests of every source set, and every `res/values*` XML file of every source set: path under `app/src` to text. */
    private fun resources(): List<Pair<String, String>> {
        val src = root.parentFile
        return src.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.flatMap { set ->
            listOf(File(set, "AndroidManifest.xml")).filter { it.isFile } +
                File(set, "res").listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }.flatMap { it.listFiles().orEmpty().filter { f -> f.extension == "xml" } }
        }.map { it.relativeTo(src).invariantSeparatorsPath to it.readText() }
    }

    /** The one host an XML resource or manifest may name: Android's own namespace URIs, which are never fetched. */
    private val XML_NAMESPACE_HOST = "schemas.android.com"

    /** What is wrong with the URLs anywhere in one XML resource or manifest (its comments included: it is all read). */
    private fun checkXml(file: String, text: String): List<Finding> = URL.findAll(text).mapNotNull { match ->
        val host = match.groupValues[2].substringAfterLast('@').substringBefore(':').lowercase()
        val line = text.substring(0, match.range.first).count { it == '\n' } + 1
        when {
            host == XML_NAMESPACE_HOST && match.groupValues[1] == "http" -> null
            else -> Finding(file, line, match.value, "a URL in a resource or a manifest: an endpoint is a FixedEndpoints constant, never a resource")
        }
    }.toList()

    /**
     * Files outside `net/` that build an address in code, each with how many times and why that is not a new endpoint.
     * A new `URL(…)` anywhere else, or one more in a listed file, fails: its address must be a FixedEndpoints constant
     * (then list the file here with that reason) or go through `net/`.
     */
    private val BUILDERS: Map<String, Pair<Int, String>> = mapOf(
        "cortana/PlaceSaver.kt" to (1 to "URL(…) over FixedEndpoints.NOMINATIM, the place search"),
        "weather/WeatherLocation.kt" to (1 to "URL(…) over FixedEndpoints.NOMINATIM, the reverse lookup"),
        "weather/WeatherProvider.kt" to (1 to "URL(url) of an address built from FixedEndpoints' weather constants"),
        "video/catalogue/Catalogue.kt" to (2 to "URL(url) of a catalogue address: FixedEndpoints, or the debug-only QA base (CatalogueRules.base)"),
        "ime/engine/FieldKind.kt" to (1 to "an enum constant named URL (a kind of text field), not java.net.URL"),
    )

    private val BUILDER = Regex("\\bURL\\(|\\bURI\\(|\\bUri\\.Builder\\(|\\.scheme\\(|\\bURI\\.create\\(")

    /** A source with its comments and the insides of its string literals blanked: what is left is code. */
    private fun codeOnly(source: String): String {
        val out = StringBuilder(source)
        // Blank every literal's text (found by the reader above, by position), then drop comments.
        var i = 0
        var inLine = false
        var block = 0
        var quote = 0
        while (i < source.length) {
            val c = source[i]
            when {
                inLine -> { if (c == '\n') inLine = false else out.setCharAt(i, ' ') }
                block > 0 -> {
                    if (source.startsWith("/*", i)) { block++; out.setCharAt(i, ' '); out.setCharAt(i + 1, ' '); i++ }
                    else if (source.startsWith("*/", i)) { block--; out.setCharAt(i, ' '); out.setCharAt(i + 1, ' '); i++ }
                    else if (c != '\n') out.setCharAt(i, ' ')
                }
                quote == 3 -> { if (source.startsWith("\"\"\"", i)) { quote = 0; i += 2 } else if (c != '\n') out.setCharAt(i, ' ') }
                quote == 1 -> { if (c == '\\') { out.setCharAt(i, ' '); if (i + 1 < source.length) out.setCharAt(i + 1, ' '); i++ } else if (c == '"') quote = 0 else out.setCharAt(i, ' ') }
                source.startsWith("//", i) -> { inLine = true; out.setCharAt(i, ' ') }
                source.startsWith("/*", i) -> { block = 1; out.setCharAt(i, ' '); out.setCharAt(i + 1, ' '); i++ }
                source.startsWith("\"\"\"", i) -> { quote = 3; i += 2 }
                c == '"' -> quote = 1
                c == '\'' -> { val end = if (source.getOrNull(i + 1) == '\\') i + 3 else i + 2; i = minOf(end, source.length - 1) }
            }
            i++
        }
        return out.toString()
    }

    /**
     * The plain ways an address is assembled from pieces, in one source: a `URL(` / `URI(` / `Uri.Builder()` /
     * `.scheme(` in code, and a string literal that holds `://` with no scheme of its own before it (`"://"`,
     * `scheme + "://" + host`). `net/` may hold them; any other file must be on [BUILDERS] with that many (a bare `://` literal: on [DYNAMIC]).
     */
    private fun checkBuilders(file: String, source: String): List<Finding> {
        if (file.startsWith("net/")) return emptyList()
        val findings = mutableListOf<Finding>()
        val code = codeOnly(source)
        val built = BUILDER.findAll(code).toList()
        val allowed = BUILDERS[file]?.first ?: 0
        if (built.size != allowed) {
            for (m in built) findings += Finding(file, code.substring(0, m.range.first).count { it == '\n' } + 1, m.value, "an address built in code outside net/ (${built.size} here, $allowed listed): use net/, or list the file in BUILDERS with its reason")
            if (built.isEmpty()) findings += Finding(file, 0, "", "listed in BUILDERS for $allowed address builders and holds none: remove the stale entry")
        }
        // A file on DYNAMIC handles an address whose host is the user's own (the media server): it may split one.
        if (file !in DYNAMIC) for (literal in literals(source)) for (m in Regex("://").findAll(literal.text)) {
            val before = literal.text.getOrNull(m.range.first - 1)
            if (before == null || !(before.isLetterOrDigit() || before in "+-.}")) findings += Finding(file, literal.line, "://", "a literal that holds :// with no scheme before it: a scheme joined on in code hides an address from this scan")
        }
        return findings
    }

    @Test
    fun `every URL literal in the shell's sources is a fixed endpoint, or is listed as not one`() {
        val all = sources()
        assertTrue("the scan read the sources (${all.size} files)", all.size > 200)
        val findings = all.flatMap { (file, text) -> check(file, text) }
        assertEquals("unlisted URL literals:\n" + findings.joinToString("\n"), emptyList<Finding>(), findings)
    }

    @Test
    fun `C2-L7 the scan also reads Java sources, the calc module, the manifests and the values resources`() {
        val all = sources()
        assertTrue("the calc module's sources are read (${all.count { it.first.startsWith("calc:") }})", all.count { it.first.startsWith("calc:") } >= 10)
        val xml = resources()
        assertTrue("the main manifest is read", xml.any { it.first == "main/AndroidManifest.xml" })
        assertTrue("the values resources are read (${xml.size} files)", xml.count { it.first.startsWith("main/res/values") } >= 5)
        val findings = xml.flatMap { (file, text) -> checkXml(file, text) }
        assertEquals("URLs in resources or manifests:\n" + findings.joinToString("\n"), emptyList<Finding>(), findings)
        // The manifest's own namespace URIs are seen and allowed, so the XML scan is not an empty one.
        assertTrue(URL.findAll(xml.first { it.first == "main/AndroidManifest.xml" }.second).any { it.groupValues[2] == XML_NAMESPACE_HOST })
    }

    @Test
    fun `C2-L7 a URL in a resource, a manifest, a Java source or the calc module is caught`() {
        assertEquals(1, checkXml("main/res/values/strings.xml", "<resources>\n<string name=\"x\">https://telemetry.example.net/v1</string>\n</resources>").size)
        assertEquals(2, checkXml("main/res/values/strings.xml", "<resources>\n<string name=\"x\">https://telemetry.example.net/v1</string>\n</resources>").single().line)
        assertEquals(1, checkXml("main/res/values-night/x.xml", "<resources><string name=\"x\">http://api.themoviedb.org/3</string></resources>").size)
        assertEquals(1, checkXml("main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><meta-data android:value=\"https://x.example.net\"/></manifest>").size)
        assertEquals("a look-alike of the namespace host", 1, checkXml("main/AndroidManifest.xml", "<manifest xmlns:a=\"http://schemas.android.com.evil.net/x\"/>").size)
        assertEquals("the namespace host over https is no namespace", 1, checkXml("main/AndroidManifest.xml", "<manifest xmlns:a=\"https://schemas.android.com/x\"/>").size)
        assertEquals(0, checkXml("main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" xmlns:tools=\"http://schemas.android.com/tools\"/>").size)
        // A Java source and a calc source go through the same literal reader as Kotlin.
        assertEquals(1, check("java:Legacy.java", "class Legacy {\n  // http://comment.example.net\n  static final String U = \"http://telemetry.example.net/v1\";\n}").size)
        assertEquals(1, check("calc:kotlin/app/tileshell/calc/CalcModule.kt", "val rates = \"https://rates.example.net/latest\"").size)
    }

    @Test
    fun `C2-L7 no address is built in code outside net - but for the listed files, each with its count and reason`() {
        val findings = sources().flatMap { (file, text) -> checkBuilders(file, text) }
        assertEquals("addresses built in code:\n" + findings.joinToString("\n"), emptyList<Finding>(), findings)
        // Every listed file still holds what it is listed for.
        val all = sources().toMap()
        for ((file, entry) in BUILDERS) assertEquals("$file (${entry.second})", entry.first, BUILDER.findAll(codeOnly(all.getValue(file))).count())
    }

    @Test
    fun `C2-L7 the plain hiding forms are caught - a built scheme, URL with parts, Uri Builder scheme - and it stays a tripwire`() {
        fun whys(file: String, source: String) = checkBuilders(file, source).map { it.url }
        assertEquals(listOf("://"), whys("video/New.kt", "val u = \"http\" + \"://\" + host"))
        assertEquals(listOf("://"), whys("video/New.kt", "val u = scheme + \"://telemetry.example.net/v1\""))
        assertEquals(listOf("URL("), whys("video/New.kt", "val u = URL(\"http\", host, 80, \"/v1\")"))
        assertEquals(listOf("URL("), whys("video/New.kt", "val u = URL(text)"))
        assertEquals("spelled in full", listOf("URL("), whys("video/New.kt", "val u = java.net.URL(\"http\", host, 80, \"/v1\")"))
        assertEquals(listOf("Uri.Builder(", ".scheme("), whys("video/New.kt", "val u = Uri.Builder().scheme(\"http\").authority(host).build()"))
        assertEquals(listOf("URI("), whys("calc:kotlin/app/tileshell/calc/CalcModule.kt", "val u = URI(\"http\", host, \"/x\", null)"))
        // One more in a listed file than it is listed for; and a listed file that no longer holds any.
        assertEquals(2, whys("weather/WeatherProvider.kt", "val a = URL(url)\nval b = URL(other)").size)
        assertEquals(1, checkBuilders("weather/WeatherProvider.kt", "val a = 1").size)
        // Allowed: net/ itself; an ordinary scheme literal; a word that only ends in URL; text in a comment or a literal.
        assertEquals(emptyList<String>(), whys("net/VideoHttp.kt", "val c = URL(url).openConnection(); val s = \"://\""))
        assertEquals(emptyList<String>(), whys("video/New.kt", "val a = \"content://media/external\"; val b = \"https://\${host}/x\"; val c = \"x-scheme+v1.a://y\""))
        assertEquals(emptyList<String>(), whys("video/New.kt", "val a = posterURL(x); val b = item.imageUrl(1) // URL(text)\n/* Uri.Builder().scheme(\"http\") */ val c = \"URL(text) .scheme(x)\""))
        assertEquals(emptyList<String>(), whys("video/server/ServerRules.kt", "val rest = text.substringAfter(\"://\")"))
        // What it does NOT catch, by design (the KDoc says so): pieces that never spell `://` or URL( in one place.
        assertEquals(emptyList<String>(), whys("video/New.kt", "val u = listOf(\"http\", \":\", \"//\", host).joinToString(\"\")"))
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

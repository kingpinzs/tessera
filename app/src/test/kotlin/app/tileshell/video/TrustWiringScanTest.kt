package app.tileshell.video

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17 trust review round 2, B2-M3 (the JVM half): the CALL SITES of the trust rules, read from the sources. The
 * rules themselves are pure and unit-tested; what no unit test reaches is the Android code that calls them — a key page
 * that stored the paste instead of the validator's result, a QA pref read with the debug gate left out, a secret field
 * drawn in the clear, a cipher with no AAD would each pass every rule's test. So each such site is held here to the one
 * form it must have, in the style of `net/EndpointLiteralScanTest`: the real source must pass, and the same source with
 * the site changed (the reviewer's surviving mutations, applied to a copy in memory) must fail, so a clean result is
 * never an empty one. That the device does what the source says is still the device legs'.
 *
 * Every check reads CODE ONLY: comments are removed first and white space is collapsed, so a comment cannot satisfy a
 * check and a reformatted line does not break one.
 */
class TrustWiringScanTest {
    private val root: File = listOf(File("src/main/kotlin/app/tileshell"), File("app/src/main/kotlin/app/tileshell")).first { it.isDirectory }

    /** Kotlin source with its comments removed (nested block comments too) and its string literals kept, white space collapsed. */
    private fun code(source: String): String {
        val out = StringBuilder()
        var i = 0
        fun at(s: String) = source.startsWith(s, i)
        fun string() {
            val raw = at("\"\"\"")
            val quote = if (raw) 3 else 1
            out.append(source, i, i + quote); i += quote
            while (i < source.length) {
                if (raw && at("\"\"\"")) { out.append("\"\"\""); i += 3; return }
                if (!raw && source[i] == '"') { out.append('"'); i++; return }
                if (!raw && source[i] == '\\') { out.append(source, i, minOf(i + 2, source.length)); i += 2; continue }
                if (at("\${")) {
                    // A template is code: read to its closing brace, strings inside it included.
                    out.append("\${"); i += 2
                    var depth = 0
                    while (i < source.length) {
                        val c = source[i]
                        if (c == '"') { string(); continue }
                        if (c == '{') depth++
                        if (c == '}') { if (depth == 0) { out.append('}'); i++; break }; depth-- }
                        out.append(c); i++
                    }
                    continue
                }
                out.append(source[i]); i++
            }
        }
        while (i < source.length) {
            when {
                at("//") -> while (i < source.length && source[i] != '\n') i++
                at("/*") -> {
                    var nested = 0
                    while (i < source.length) {
                        if (at("/*")) { nested++; i += 2 } else if (at("*/")) { nested--; i += 2; if (nested == 0) break } else i++
                    }
                    out.append(' ')
                }
                source[i] == '"' -> string()
                source[i] == '\'' -> {
                    val end = if (source.getOrNull(i + 1) == '\\') source.indexOf('\'', i + 3) else source.indexOf('\'', i + 1)
                    val stop = if (end < 0) source.length else end + 1
                    out.append(source, i, stop); i = stop
                }
                else -> { out.append(source[i]); i++ }
            }
        }
        return out.toString().replace(Regex("\\s+"), " ")
    }

    private fun read(path: String): String = code(File(root, path).readText())

    private fun all(): Map<String, String> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
        .associate { it.relativeTo(root).invariantSeparatorsPath to code(it.readText()) }

    /** [text] with [old] — which must be there exactly once — turned into [new]: a mutation applied to a copy. */
    private fun mutate(text: String, old: String, new: String): String {
        assertEquals("the mutation's site is in the source once: $old", 1, Regex(Regex.escape(old)).findAll(text).count())
        return text.replace(old, new)
    }

    /** The call that starts at [start] (the index of its name), through its closing parenthesis; strings are skipped. */
    private fun call(text: String, start: Int): String {
        var i = text.indexOf('(', start)
        var depth = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (inString) { if (c == '\\') i++ else if (c == '"') inString = false }
            else if (c == '"') inString = true
            else if (c == '(') depth++
            else if (c == ')') { depth--; if (depth == 0) return text.substring(start, i + 1) }
            i++
        }
        return text.substring(start)
    }

    /** The body of the function whose declaration holds [declaration], to the brace that closes it. */
    private fun body(text: String, declaration: String): String {
        val at = text.indexOf(declaration)
        if (at < 0) return ""
        var i = text.indexOf('{', at)
        val start = i
        var depth = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (inString) { if (c == '\\') i++ else if (c == '"') inString = false }
            else if (c == '"') inString = true
            else if (c == '{') depth++
            else if (c == '}') { depth--; if (depth == 0) return text.substring(start, i + 1) }
            i++
        }
        return text.substring(start)
    }

    private fun count(text: String, piece: String): Int = Regex(Regex.escape(piece)).findAll(text).count()

    // ---- the QA prefs (phase 17's three redirects, phase 18's two paces, phase 20's three bases): read only behind BuildConfig.DEBUG

    /**
     * Each pref's ONE read: the file it is in and the rule it is the second argument of. Phase 18 added the two paces
     * (Q-18-3, Q-18-5), and with them the pairing: a pref read behind another pref's rule, or in another file, is not
     * its site.
     */
    private val SITES = mapOf(
        "CATALOGUE" to ("video/catalogue/Catalogue.kt" to "CatalogueRules.base(BuildConfig.DEBUG, "),
        "WIKIDATA" to ("video/handoff/StreamingHandoff.kt" to "CatalogueRules.base(BuildConfig.DEBUG, "),
        "SERVER" to ("video/server/MediaServer.kt" to "ServerRules.signInBase(BuildConfig.DEBUG, "),
        "FILES_RATE" to ("files/FileOpsService.kt" to "FilePace.rate(BuildConfig.DEBUG, "),
        "FILES_SEARCH_RATE" to ("files/FilesState.kt" to "FilePace.searchRate(BuildConfig.DEBUG, "),
        // Phase 20 (T20-6, r3 D16): the radio directory's base, and the music catalogue's and the cover art's (build task 8).
        "RADIO" to ("music/radio/RadioNet.kt" to "CatalogueRules.base(BuildConfig.DEBUG, "),
        "MUSIC_CATALOGUE" to ("music/catalogue/MusicCatalogueFetcher.kt" to "CatalogueRules.base(BuildConfig.DEBUG, "),
        "COVERART" to ("music/catalogue/MusicCatalogueFetcher.kt" to "CatalogueRules.base(BuildConfig.DEBUG, "),
    )
    private val GATED = listOf("CatalogueRules.base(BuildConfig.DEBUG, ", "ServerRules.signInBase(BuildConfig.DEBUG, ", "FilePace.rate(BuildConfig.DEBUG, ", "FilePace.searchRate(BuildConfig.DEBUG, ")
    private val PREF_NAMES = Regex("QaBases\\.(CATALOGUE|WIKIDATA|SERVER|FILES_RATE|FILES_SEARCH_RATE|RADIO|MUSIC_CATALOGUE|COVERART)\\b")
    private val PREF_READ = Regex("QaBases\\.read\\(\\w+, QaBases\\.(CATALOGUE|WIKIDATA|SERVER|FILES_RATE|FILES_SEARCH_RATE|RADIO|MUSIC_CATALOGUE|COVERART)\\)")

    /** What is wrong with how the sources read the QA prefs; empty when nothing is. */
    private fun qaPrefProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        var reads = 0
        val prefsRead = mutableSetOf<String>()
        for ((file, text) in sources) {
            for (m in Regex("QaBases\\.read\\(").findAll(text)) {
                reads++
                val gate = GATED.firstOrNull { text.startsWith(it, m.range.first - it.length) }
                if (gate == null) problems += "$file: QaBases.read is not the second argument of a rule that is given BuildConfig.DEBUG"
                else {
                    val pref = PREF_READ.matchEntire(call(text, m.range.first))?.groupValues?.get(1)
                    if (pref == null || SITES[pref] != (file to gate)) problems += "$file: QaBases.read($pref) behind $gate… is not that pref's one site, behind its own rule"
                }
            }
            val named = PREF_NAMES.findAll(text).count()
            val readProperly = PREF_READ.findAll(text).onEach { prefsRead += it.groupValues[1] }.count()
            if (named != readProperly) problems += "$file: a QA pref's name is used outside QaBases.read(context, QaBases.X)"
            // The prefs' names as text: only the three constants, in the one file.
            for (literal in Regex("\"qa_[a-z_]*").findAll(text)) if (file != "video/catalogue/Catalogue.kt") problems += "$file: ${literal.value}\" is spelled outside QaBases"
        }
        if (reads != 8 || prefsRead != setOf("CATALOGUE", "WIKIDATA", "SERVER", "FILES_RATE", "FILES_SEARCH_RATE", "RADIO", "MUSIC_CATALOGUE", "COVERART")) problems += "the eight QA prefs are each read once (reads=$reads, prefs=$prefsRead)"
        val qa = sources["video/catalogue/Catalogue.kt"].orEmpty()
        if (Regex("\"qa_[a-z_]*\"").findAll(qa).map { it.value }.toList() != listOf("\"qa_catalogue_base\"", "\"qa_wikidata_base\"", "\"qa_server_base\"", "\"qa_files_rate_bps\"", "\"qa_files_search_eps\"", "\"qa_radio_base\"", "\"qa_music_catalogue_base\"", "\"qa_coverart_base\"")) problems += "QaBases names exactly the eight prefs"
        if (!body(qa, "fun read(context: Context, key: String): String?").startsWith("{ if (!BuildConfig.DEBUG) return null ")) problems += "QaBases.read does not begin by returning null outside a debug build"
        return problems
    }

    @Test fun `every read of a QA pref passes through a BuildConfig DEBUG gate`() {
        val sources = all()
        assertTrue("the scan read the sources (${sources.size} files)", sources.size > 200)
        assertEquals(emptyList<String>(), qaPrefProblems(sources))
    }

    @Test fun `a QA pref read with the gate left out, constant, or made elsewhere is caught`() {
        val sources = all()
        fun with(file: String, old: String, new: String) = qaPrefProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        // The reviewer's mutations: the debug flag replaced by a constant at each of the three sites …
        assertEquals(1, with("video/server/MediaServer.kt", "ServerRules.signInBase(BuildConfig.DEBUG, QaBases.read(", "ServerRules.signInBase(true, QaBases.read(").size)
        assertEquals(1, with("video/catalogue/Catalogue.kt", "CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.CATALOGUE)", "CatalogueRules.base(true, QaBases.read(app, QaBases.CATALOGUE)").size)
        assertEquals(1, with("video/handoff/StreamingHandoff.kt", "CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(", "CatalogueRules.base(!BuildConfig.DEBUG, QaBases.read(").size)
        // … the pref used with no rule at all, the reader's own gate removed, and the pref read by its name elsewhere.
        assertTrue(with("video/server/MediaServer.kt", "ServerRules.signInBase(BuildConfig.DEBUG, QaBases.read(app, QaBases.SERVER), address.base)", "(QaBases.read(app, QaBases.SERVER) ?: address.base)").isNotEmpty())
        assertTrue(with("video/catalogue/Catalogue.kt", "if (!BuildConfig.DEBUG) return null ", "").isNotEmpty())
        assertTrue(with("video/VideoActivity.kt", "nav.ready = true", "nav.ready = getSharedPreferences(\"start_theme\", 0).getString(\"qa_server_base\", null) == null").isNotEmpty())
        assertTrue(with("video/VideoActivity.kt", "nav.ready = true", "nav.ready = prefs.getString(QaBases.SERVER, null) == null").isNotEmpty())
        // Phase 18's two paces, the same mutations: the debug flag a constant or negated at each new site …
        assertEquals(1, with("files/FileOpsService.kt", "FilePace.rate(BuildConfig.DEBUG, QaBases.read(", "FilePace.rate(true, QaBases.read(").size)
        assertEquals(1, with("files/FileOpsService.kt", "FilePace.rate(BuildConfig.DEBUG, QaBases.read(", "FilePace.rate(!BuildConfig.DEBUG, QaBases.read(").size)
        assertEquals(1, with("files/FilesState.kt", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(", "FilePace.searchRate(true, QaBases.read(").size)
        assertEquals(1, with("files/FilesState.kt", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(", "FilePace.searchRate(!BuildConfig.DEBUG, QaBases.read(").size)
        // … the pace pref used with no rule at all (the form phase 18 first wrote, which this scan caught) …
        assertTrue(with("files/FileOpsService.kt", "FilePace.rate(BuildConfig.DEBUG, QaBases.read(this, QaBases.FILES_RATE))", "QaBases.read(this, QaBases.FILES_RATE)?.trim()?.toLongOrNull()").isNotEmpty())
        assertTrue(with("files/FilesState.kt", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(app, QaBases.FILES_SEARCH_RATE))", "QaBases.read(app, QaBases.FILES_SEARCH_RATE)?.trim()?.toLongOrNull()?.takeIf { it > 0 }").isNotEmpty())
        // … read straight from SharedPreferences, by its text or by its constant, and spelled outside Catalogue.kt …
        assertTrue(with("files/FileOpsService.kt", "FilePace.rate(BuildConfig.DEBUG, QaBases.read(this, QaBases.FILES_RATE))", "getSharedPreferences(\"start_theme\", 0).getString(\"qa_files_rate_bps\", null)?.toLongOrNull()").isNotEmpty())
        assertTrue(with("files/FilesState.kt", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(app, QaBases.FILES_SEARCH_RATE))", "app.getSharedPreferences(\"start_theme\", 0).getString(QaBases.FILES_SEARCH_RATE, null)?.toLongOrNull()").isNotEmpty())
        assertTrue(with("files/FilesSearch.kt", "package app.tileshell.files", "package app.tileshell.files const val PACE = \"qa_files_search_eps\"").isNotEmpty())
        // … a second read of a pace beside its one site, a pace behind the other pace's rule or behind a redirect's
        // rule, one pace's pref at the other's site, and a sixth pref.
        assertTrue(with("files/FilesState.kt", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(app, QaBases.FILES_SEARCH_RATE))", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(app, QaBases.FILES_SEARCH_RATE)) ?: FilePace.rate(BuildConfig.DEBUG, QaBases.read(app, QaBases.FILES_RATE))").isNotEmpty())
        assertEquals(1, with("files/FileOpsService.kt", "FilePace.rate(BuildConfig.DEBUG, QaBases.read(", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(").size)
        assertEquals(1, with("video/server/MediaServer.kt", "ServerRules.signInBase(BuildConfig.DEBUG, QaBases.read(app, QaBases.SERVER), address.base)", "CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.SERVER), address.base)").size)
        assertTrue(with("files/FileOpsService.kt", "QaBases.read(this, QaBases.FILES_RATE)", "QaBases.read(this, QaBases.FILES_SEARCH_RATE)").isNotEmpty())
        assertTrue(with("video/catalogue/Catalogue.kt", "const val FILES_RATE = \"qa_files_rate_bps\"", "const val FILES_RATE = \"qa_files_rate_bps\" const val FILES_FLOOR = \"qa_files_floor\"").isNotEmpty())
        assertTrue(with("video/catalogue/Catalogue.kt", "const val FILES_SEARCH_RATE = \"qa_files_search_eps\"", "").isNotEmpty())
        // Phase 20's radio base, the same mutations: the debug flag a constant or negated, the pref with no rule, read
        // by its constant elsewhere, read a second time, another phase-20 pref at its site, and its name removed.
        val radio = "CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.RADIO), FixedEndpoints.RADIO_BROWSER)"
        assertEquals(1, with("music/radio/RadioNet.kt", radio, radio.replace("BuildConfig.DEBUG", "true")).size)
        assertEquals(1, with("music/radio/RadioNet.kt", radio, radio.replace("BuildConfig.DEBUG", "!BuildConfig.DEBUG")).size)
        assertTrue(with("music/radio/RadioNet.kt", radio, "(QaBases.read(app, QaBases.RADIO) ?: FixedEndpoints.RADIO_BROWSER)").isNotEmpty())
        assertTrue(with("music/radio/RadioNet.kt", radio, "app.getSharedPreferences(\"start_theme\", 0).getString(QaBases.RADIO, null).orEmpty()").isNotEmpty())
        assertTrue(with("music/radio/RadioNet.kt", radio, "$radio + $radio").isNotEmpty())
        assertTrue(with("music/radio/RadioNet.kt", "QaBases.read(app, QaBases.RADIO)", "QaBases.read(app, QaBases.COVERART)").isNotEmpty())
        // … and the catalogue's two bases (build task 8): the gate a constant at each, and one pref read at the other's place twice.
        assertEquals(1, with("music/catalogue/MusicCatalogueFetcher.kt", "CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.MUSIC_CATALOGUE)", "CatalogueRules.base(true, QaBases.read(app, QaBases.MUSIC_CATALOGUE)").size)
        assertEquals(1, with("music/catalogue/MusicCatalogueFetcher.kt", "CatalogueRules.base(BuildConfig.DEBUG, QaBases.read(app, QaBases.COVERART)", "CatalogueRules.base(!BuildConfig.DEBUG, QaBases.read(app, QaBases.COVERART)").size)
        assertTrue(with("music/catalogue/MusicCatalogueFetcher.kt", "QaBases.read(app, QaBases.COVERART)", "QaBases.read(app, QaBases.MUSIC_CATALOGUE)").isNotEmpty())
        assertTrue(with("music/radio/StationUrl.kt", "const val NO_SCHEME = \"none\"", "const val NO_SCHEME = \"none\" const val BASE = \"qa_radio_base\"").isNotEmpty())
        assertTrue(with("video/catalogue/Catalogue.kt", "const val RADIO = \"qa_radio_base\"", "").isNotEmpty())
    }

    // ---- the two paces (phase 18): what paces a copy or a search is the rule's answer and nothing else

    /**
     * `qaPrefProblems` holds each pace's pref to its rule and its debug flag; `FilePaceRulesTest` holds the rules (null
     * in a release build whatever the pref says). Held here is the rest of the way: the rule's answer, untouched, is
     * the only thing a `FilePace` is made from and the only thing the search walk is paced by — so a release build,
     * where the answer is null, has no pace to apply.
     */
    private fun paceProblems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val service = sources["files/FileOpsService.kt"].orEmpty()
        val state = sources["files/FilesState.kt"].orEmpty()
        val paths = sources["files/FilePaths.kt"].orEmpty()
        // The copy's pace: FilePace.of(the rule's answer), the one FilePace any process makes.
        if (!service.contains("val rate = FilePace.rate(BuildConfig.DEBUG, QaBases.read(this, QaBases.FILES_RATE)) val pace = FilePace.of(rate) if (pace != null && rate != null) say(FileOpsText.paceLine(rate)) ")) {
            problems += "the copy's pace is not FilePace.of(FilePace.rate(BuildConfig.DEBUG, the pref))"
        }
        if (Regex("\\brate\\s*=[^=]").findAll(service).count() != 1 || Regex("\\bpace\\s*=[^=]").findAll(service).count() != 2 || !service.contains("stopped = run::stopped, pace = pace, )")) problems += "the operation is run with a pace other than the rule's"
        val made = sources.mapValues { (_, text) -> Regex("(?<!class )\\bFilePace\\(").findAll(text).count() }.filterValues { it > 0 }
        val asked = sources.mapValues { (_, text) -> count(text, "FilePace.of(") }.filterValues { it > 0 }
        if (made != mapOf("files/FilePaths.kt" to 1) || asked != mapOf("files/FileOpsService.kt" to 1) ||
            !paths.contains("fun of(bytesPerSecond: Long?): FilePace? = if (bytesPerSecond == null || bytesPerSecond <= 0) null else FilePace(bytesPerSecond)")
        ) problems += "a FilePace is made other than by FilePace.of at the copy service's one site (made=$made, of=$asked)"
        val paced = sources.mapValues { (_, text) -> Regex("\\bpace\\s*=[^=]").findAll(text).count() }.filterValues { it > 0 }
        if (paced != mapOf("files/FileOpsService.kt" to 2)) problems += "an OpControl is given a pace somewhere else: $paced"
        // The search walk's pace: the rule's answer, handed to the one walk.
        if (!state.contains("val eps = FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(app, QaBases.FILES_SEARCH_RATE)) searchJob = scope.launch {") || Regex("\\beps\\s*=[^=]").findAll(state).count() != 1) {
            problems += "the search's pace is not FilePace.searchRate(BuildConfig.DEBUG, the pref)"
        }
        val walks = sources.mapValues { (_, text) -> Regex("\\bentriesPerSecond\\s*=[^=]").findAll(text).count() }.filterValues { it > 0 }
        if (walks != mapOf("files/FilesState.kt" to 1) || !state.contains("onFolder = { folders.set(it) }, entriesPerSecond = eps, onHit = ")) problems += "a search walk is paced by something other than the rule's answer: $walks"
        // The rules give no pace outside a debug build (their unit tests run them; this is the form they must keep).
        for (rule in listOf("fun rate(debug: Boolean, pref: String?): Long?", "fun searchRate(debug: Boolean, pref: String?): Long?")) {
            if (!paths.contains("$rule = if (debug) pref?.trim()?.toLongOrNull()?.takeIf { it > 0 } else null")) problems += "FilePace.${rule.substringAfter("fun ").substringBefore('(')} gives a pace outside a debug build"
        }
        return problems
    }

    @Test fun `a copy and a search are paced by the rule's answer and nothing else`() {
        assertEquals(emptyList<String>(), paceProblems(all()))
    }

    @Test fun `a pace with a fallback, made by hand, or handed to the walk as a constant is caught`() {
        val sources = all()
        fun with(file: String, old: String, new: String) = paceProblems(sources + (file to mutate(sources.getValue(file), old, new)))
        // The debug flag a constant or negated at each site (qaPrefProblems' mutation, seen from this side too).
        assertTrue(with("files/FileOpsService.kt", "FilePace.rate(BuildConfig.DEBUG, QaBases.read(", "FilePace.rate(true, QaBases.read(").isNotEmpty())
        assertTrue(with("files/FilesState.kt", "FilePace.searchRate(BuildConfig.DEBUG, QaBases.read(", "FilePace.searchRate(!BuildConfig.DEBUG, QaBases.read(").isNotEmpty())
        // A release build paced all the same: a fallback after the rule, a pace made by hand, a constant to the walk.
        assertTrue(with("files/FileOpsService.kt", "QaBases.read(this, QaBases.FILES_RATE)) val pace", "QaBases.read(this, QaBases.FILES_RATE)) ?: 1_000_000L val pace").isNotEmpty())
        assertTrue(with("files/FileOpsService.kt", "val pace = FilePace.of(rate)", "val pace = FilePace.of(rate ?: 1_000_000L)").isNotEmpty())
        assertTrue(with("files/FileOpsService.kt", "val pace = FilePace.of(rate)", "val pace = FilePace(1_000_000L)").isNotEmpty())
        assertTrue(with("files/FileOpsService.kt", "stopped = run::stopped, pace = pace,", "stopped = run::stopped, pace = FilePace.of(1_000_000L),").isNotEmpty())
        assertTrue(with("files/FileOps.kt", "package app.tileshell.files", "package app.tileshell.files val always = OpControl(pace = FilePace.of(1L))").isNotEmpty())
        assertTrue(with("files/FilesState.kt", "QaBases.read(app, QaBases.FILES_SEARCH_RATE)) searchJob", "QaBases.read(app, QaBases.FILES_SEARCH_RATE)) ?: 50L searchJob").isNotEmpty())
        assertTrue(with("files/FilesState.kt", "entriesPerSecond = eps,", "entriesPerSecond = 50L,").isNotEmpty())
        assertTrue(with("files/FilesState.kt", "entriesPerSecond = eps,", "entriesPerSecond = eps ?: 50L,").isNotEmpty())
        // The rule itself opened to a release build.
        assertTrue(with("files/FilePaths.kt", "fun rate(debug: Boolean, pref: String?): Long? = if (debug) pref", "fun rate(debug: Boolean, pref: String?): Long? = if (true) pref").isNotEmpty())
        assertTrue(with("files/FilePaths.kt", "fun searchRate(debug: Boolean, pref: String?): Long? = if (debug) pref", "fun searchRate(debug: Boolean, pref: String?): Long? = if (!debug) pref").isNotEmpty())
    }

    // ---- the key page: only the validator's result is stored; the field is a secret one that does not cut a key

    private fun keyPageProblems(page: String, sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val validated = page.indexOf("val token = HeaderText.pastedSecret(typed)")
        val refusal = page.indexOf("if (token == null) {")
        val store = page.indexOf("CredentialStore.of(context).set(CredentialStore.TMDB, token)")
        if (validated < 0 || count(page, "val token =") != 1) problems += "the stored value is not HeaderText.pastedSecret(typed)"
        if (count(page, ".set(") != 1 || store < 0) problems += "the page stores something other than the validator's result"
        if (!(validated in 0 until refusal && refusal < store)) problems += "a refused paste is not turned away before the store is reached"
        if (!body(page.substring(maxOf(refusal, 0)), "if (token == null)").trimEnd().endsWith("return }")) problems += "the refusal does not return"
        val elsewhere = sources.filter { (file, text) -> file != "video/TmdbKeyPage.kt" && Regex("\\.set\\(\\s*CredentialStore\\.TMDB").containsMatchIn(text) }.keys
        if (elsewhere.isNotEmpty()) problems += "the TMDB key is also stored by $elsewhere"
        val field = page.indexOf("OutlinedField(").let { if (it < 0) "" else call(page, it) }
        if (!field.contains("tag = \"tmdb_key_field\"")) problems += "the key's field is not found"
        if (!field.contains("secret = true")) problems += "the key's field is not a secret one"
        if (!field.contains("maxLength = HeaderText.FIELD_MAX,")) problems += "the key's field cuts a paste before the validator sees that it is too long"
        if (!page.contains("val gone = VideoCalls.io(\"TMDB key remove\", false) { CredentialStore.of(context).clear(CredentialStore.TMDB) } Diagnostics.add(\"video\", TmdbKeyLines.removal(gone))")) {
            problems += "the removal's line is not written from whether the key is gone"
        }
        return problems
    }

    @Test fun `the key page hands the credential store only what HeaderText pastedSecret returned`() {
        assertEquals(emptyList<String>(), keyPageProblems(read("video/TmdbKeyPage.kt"), all()))
    }

    @Test fun `a key page that stores the paste, shows it, cuts it or says removed regardless is caught`() {
        val page = read("video/TmdbKeyPage.kt")
        val sources = all()
        fun with(old: String, new: String) = keyPageProblems(mutate(page, old, new), sources)
        assertTrue(with("CredentialStore.of(context).set(CredentialStore.TMDB, token)", "CredentialStore.of(context).set(CredentialStore.TMDB, typed)").isNotEmpty())
        assertTrue(with("val token = HeaderText.pastedSecret(typed)", "val token: String? = typed.trim()").isNotEmpty())
        assertTrue(with("Diagnostics.add(\"video\", \"TMDB key refused: not a key's characters\") return }", "Diagnostics.add(\"video\", \"TMDB key refused: not a key's characters\") }").isNotEmpty())
        assertTrue(with("onImeAction = { save() }, secret = true,", "onImeAction = { save() },").isNotEmpty())
        assertTrue(with("maxLength = HeaderText.FIELD_MAX,", "maxLength = HeaderText.MAX_SECRET,").isNotEmpty())
        assertTrue(with("Diagnostics.add(\"video\", TmdbKeyLines.removal(gone))", "Diagnostics.add(\"video\", TmdbKeyLines.removal(true))").isNotEmpty())
        // And another file storing the key is seen.
        val other = sources + ("video/BrowsePage.kt" to sources.getValue("video/BrowsePage.kt") + " fun x(c: Context, s: String) = CredentialStore.of(c).set(CredentialStore.TMDB, s)")
        assertTrue(keyPageProblems(page, other).isNotEmpty())
    }

    // ---- the secret fields

    private fun secretFieldProblems(serverPages: String, field: String): List<String> {
        val problems = mutableListOf<String>()
        val password = Regex("OutlinedField\\(").findAll(serverPages).map { call(serverPages, it.range.first) }.filter { it.contains("\"server_password\"") }.toList()
        if (password.size != 1) problems += "the password field is not found once"
        if (password.any { !it.contains("secret = true") }) problems += "the password field is not a secret one"
        // What `secret` does, in the one text box: dots on screen, the password keyboard, and dots to accessibility.
        for (piece in listOf(
            "visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None",
            ".then(if (secret) Modifier.semantics { password() } else Modifier)",
            "keyboardType = if (secret) KeyboardType.Password else keyboardType",
            "secret: Boolean = false,",
        )) if (!field.contains(piece)) problems += "OutlinedField no longer has: $piece"
        return problems
    }

    @Test fun `the password field and the key field are created as secret fields, and a secret field is drawn as dots`() {
        assertEquals(emptyList<String>(), secretFieldProblems(read("video/MediaServerPages.kt"), read("ui/components/OutlinedField.kt")))
    }

    @Test fun `a secret field in the clear is caught`() {
        val pages = read("video/MediaServerPages.kt")
        val field = read("ui/components/OutlinedField.kt")
        assertTrue(secretFieldProblems(mutate(pages, "onImeAction = { submit() }, secret = true)", "onImeAction = { submit() })"), field).isNotEmpty())
        assertTrue(secretFieldProblems(mutate(pages, "onImeAction = { submit() }, secret = true)", "onImeAction = { submit() }, secret = false)"), field).isNotEmpty())
        assertTrue(secretFieldProblems(pages, mutate(field, "if (secret) PasswordVisualTransformation() else VisualTransformation.None", "VisualTransformation.None")).isNotEmpty())
        assertTrue(secretFieldProblems(pages, mutate(field, "secret: Boolean = false,", "secret: Boolean = true,")).isNotEmpty())
    }

    // ---- the Keystore cipher

    private fun cipherProblems(store: String): List<String> {
        val problems = mutableListOf<String>()
        val key = body(store, "private fun key(create: Boolean): SecretKey")
        for (piece in listOf(
            ".setKeySize(256)", ".setBlockModes(KeyProperties.BLOCK_MODE_GCM)", ".setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)",
            ".setUnlockedDeviceRequired(true)", "KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)",
        )) if (count(key, piece) != 1) problems += "the key is not made with $piece"
        if (count(store, ".setKeySize(") != 1 || count(store, ".setUnlockedDeviceRequired(") != 1) problems += "the key's size or its unlocked-device rule is set more than once"
        val aad = "cipher.updateAAD(name.toByteArray(Charsets.UTF_8))"
        for (function in listOf("override fun seal(name: String, plain: ByteArray): CredentialCipher.Sealed", "override fun open(name: String, sealed: CredentialCipher.Sealed): ByteArray")) {
            val text = body(store, function)
            val name = function.substringAfter("fun ").substringBefore('(')
            if (count(text, aad) != 1) problems += "$name does not bind the entry's name as AAD"
            if (text.indexOf(aad) !in (text.indexOf("cipher.init(") + 1) until text.indexOf("cipher.doFinal(")) problems += "$name: the AAD is not given between init and doFinal"
        }
        if (!store.contains("const val TRANSFORMATION = \"AES/GCM/NoPadding\"") || !store.contains("const val TAG_BITS = 128") || !store.contains("const val PROVIDER = \"AndroidKeyStore\"")) {
            problems += "the cipher is not AES/GCM/NoPadding with a 128-bit tag in the Android Keystore"
        }
        return problems
    }

    @Test fun `the Keystore cipher makes a 256-bit key that needs an unlocked phone, and binds the entry's name on seal and on open`() {
        assertEquals(emptyList<String>(), cipherProblems(read("net/CredentialStore.kt")))
    }

    @Test fun `a cipher with a smaller key, no AAD on either side, or a key usable while locked is caught`() {
        val store = read("net/CredentialStore.kt")
        fun with(old: String, new: String) = cipherProblems(mutate(store, old, new))
        assertTrue(with(".setKeySize(256)", ".setKeySize(128)").isNotEmpty())
        assertTrue(with(".setKeySize(256)", "").isNotEmpty())
        assertTrue(with(".setUnlockedDeviceRequired(true)", ".setUnlockedDeviceRequired(false)").isNotEmpty())
        assertTrue(with(".setUnlockedDeviceRequired(true)", "").isNotEmpty())
        // The AAD dropped from seal, from open, or given after the bytes are already through.
        assertTrue(with("cipher.updateAAD(name.toByteArray(Charsets.UTF_8)) val ct = cipher.doFinal(plain)", "val ct = cipher.doFinal(plain)").isNotEmpty())
        assertTrue(with("cipher.updateAAD(name.toByteArray(Charsets.UTF_8)) return cipher.doFinal(sealed.ciphertext)", "return cipher.doFinal(sealed.ciphertext)").isNotEmpty())
        assertTrue(with("cipher.updateAAD(name.toByteArray(Charsets.UTF_8)) val ct = cipher.doFinal(plain)", "val ct = cipher.doFinal(plain) cipher.updateAAD(name.toByteArray(Charsets.UTF_8))").isNotEmpty())
        assertTrue(with("const val TAG_BITS = 128", "const val TAG_BITS = 96").isNotEmpty())
    }

    // ---- the player, the sign-in form and the hub's start: the rules are the only way through

    private fun callSiteProblems(player: String, playback: String, server: String, pages: String, hub: String): List<String> {
        val problems = mutableListOf<String>()
        // Who started the player is read once, from Android's port, through the rule (`media/UriAccessWiringScanTest`
        // holds the port and the rest of the player's wiring).
        val assignments = Regex("\\blaunch\\s*=[^=]").findAll(player).count()
        if (assignments != 2 || !player.contains("private var launch = PlayerLaunch.OTHER") ||
            !player.contains("launch = PlayerAccess.launch(AndroidUriAccess(this), { intent?.hasExtra(EXTRA_QUEUE) == true }, { intent?.getLongArrayExtra(EXTRA_QUEUE) })")
        ) problems += "the launch is not read once, from the platform port through PlayerAccess.launch"
        // The token's resolver exists only on the rule's RESOLVE (B2-M2).
        if (count(player, "MediaServer.streamResolver(") != 1 || !player.contains("ServerTokenUse.RESOLVE -> MediaServer.streamResolver(this)") ||
            !player.contains("when (PlayerAccess.serverToken(launch.own, ServerRules.couldBeStream(source.scheme, source.path))) {") ||
            count(player, "VideoPlayback.acquire(") != 1 || !player.contains("VideoPlayback.acquire(this, resolver) {")
        ) problems += "the player makes the token's resolver other than on PlayerAccess.serverToken's RESOLVE"
        if (!player.contains("queue = launch.queue") || count(player, "getLongArrayExtra(") != 1) problems += "the queue is not the one PlayerAccess.launch honoured"
        if (!player.contains("PlayerRules.source(source?.toString(), source?.scheme, source?.encodedAuthority, source?.path, ownRoots(), launch.own, AndroidUriAccess(this))")) problems += "the source is not decided by PlayerRules.source with who launched"
        // The resolver asks for the STREAM token, and what the player reports has no key.
        val resolver = body(server, "fun streamResolver(context: Context): (DataSpec) -> DataSpec")
        if (!resolver.contains("storeOf(app).streamToken(url)") || resolver.contains("tokenFor(") || resolver.contains("credential()")) problems += "the resolver takes the token other than from ServerStore.streamToken"
        if (!playback.contains("override fun resolveReportedUri(uri: Uri): Uri = Uri.parse(ServerRules.reportedUrl(uri.toString()))")) problems += "the reported address is not ServerRules.reportedUrl's"
        // The sign-in is sent only on the rule's SEND (B-3).
        val form = body(pages, "fun AddServerForm(")
        if (count(form, "signIn(address)") != 2 || !form.contains("SignInAction.SEND -> signIn(address)") ||
            !form.contains("if (ServerRules.signInAction(address, answer) == SignInAction.SEND) signIn(address) else password = \"\"") ||
            count(form, ".connect(") != 1
        ) problems += "the form sends a sign-in other than on ServerRules.signInAction's SEND"
        // The hub's start clears a left-over entry before it asks whether a server is set up (B2-M1).
        val route = body(hub, "private fun route(intent: Intent?)")
        if (route.indexOf("MediaServer(this).sweep()") !in 0 until route.indexOf("MediaServer(this).isSetUp()")) problems += "the hub's start does not sweep before it reads whether a server is set up"
        val settings = body(pages, "fun ServerSettingsPage(nav: VideoNav)")
        if (!settings.contains("view = ServerRules.settingsView(setUp, held)") || count(settings, "if (ServerRules.offersRemove(view))") != 2) problems += "the setting does not offer Remove by ServerRules.settingsView"
        return problems
    }

    @Test fun `the player, the sign-in form and the hub's start go through the rules`() {
        assertEquals(
            emptyList<String>(),
            callSiteProblems(read("video/PlayerActivity.kt"), read("video/VideoPlayback.kt"), read("video/server/MediaServer.kt"), read("video/MediaServerPages.kt"), read("video/VideoActivity.kt")),
        )
    }

    @Test fun `a call site that goes round its rule is caught`() {
        val player = read("video/PlayerActivity.kt")
        val playback = read("video/VideoPlayback.kt")
        val server = read("video/server/MediaServer.kt")
        val pages = read("video/MediaServerPages.kt")
        val hub = read("video/VideoActivity.kt")
        fun player(old: String, new: String) = callSiteProblems(mutate(player, old, new), playback, server, pages, hub)
        assertTrue(player("launch = PlayerAccess.launch(AndroidUriAccess(this), { intent?.hasExtra(EXTRA_QUEUE) == true }, { intent?.getLongArrayExtra(EXTRA_QUEUE) })", "launch = PlayerLaunch(true, intent?.getLongArrayExtra(EXTRA_QUEUE), emptyList())").isNotEmpty())
        assertTrue(player("PlayerAccess.serverToken(launch.own, ServerRules", "PlayerAccess.serverToken(true, ServerRules").isNotEmpty())
        assertTrue(player("ServerTokenUse.NONE -> null", "ServerTokenUse.NONE -> MediaServer.streamResolver(this)").isNotEmpty())
        assertTrue(player("VideoPlayback.acquire(this, resolver) {", "VideoPlayback.acquire(this, MediaServer.streamResolver(this)) {").isNotEmpty())
        assertTrue(player("queue = launch.queue", "queue = intent?.getLongArrayExtra(EXTRA_QUEUE)").isNotEmpty())
        assertTrue(player("ownRoots(), launch.own, AndroidUriAccess(this))", "ownRoots(), true, AndroidUriAccess(this))").isNotEmpty())
        assertTrue(callSiteProblems(player, mutate(playback, "Uri.parse(ServerRules.reportedUrl(uri.toString()))", "uri"), server, pages, hub).isNotEmpty())
        assertTrue(callSiteProblems(player, playback, mutate(server, "storeOf(app).streamToken(url)", "storeOf(app).tokenFor(url)"), pages, hub).isNotEmpty())
        // The form sending at once: on the ASK branch, or whatever the prompt's answer.
        assertTrue(callSiteProblems(player, playback, server, mutate(pages, "asking = address }", "asking = address; signIn(address) }"), hub).isNotEmpty())
        assertTrue(callSiteProblems(player, playback, server, mutate(pages, "if (ServerRules.signInAction(address, answer) == SignInAction.SEND) signIn(address)", "if (true) signIn(address)"), hub).isNotEmpty())
        assertTrue(callSiteProblems(player, playback, server, pages, mutate(hub, "runCatching { MediaServer(this).sweep() }", "")).isNotEmpty())
    }

    @Test fun `the reader skips comments and keeps strings, so a comment cannot stand in for code`() {
        assertEquals("val a = 1 val b = \"// not a comment\" val c = 2", code("val a = 1 // secret = true\nval b = \"// not a comment\" /* .setKeySize(256) /* nested */ still */\nval c = 2").trim())
        assertEquals("val q = '\"' val t = \"\${f(\"x\")} y\" val u = 1", code("val q = '\"' // c\nval t = \"\${f(\"x\")} y\"\n/** KDoc */ val u = 1").replace("  ", " ").trim())
        // The real files are still read: each check's anchor is found in code, not in a comment.
        assertTrue(read("net/CredentialStore.kt").contains(".setUnlockedDeviceRequired(true)"))
        assertTrue(!read("net/CredentialStore.kt").contains("B2-L8"))
    }
}

package app.tileshell.net

import app.tileshell.media.SourceScan
import app.tileshell.media.SourceScan.body
import app.tileshell.media.SourceScan.call
import app.tileshell.media.SourceScan.count
import app.tileshell.media.SourceScan.mutate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 17, the owner's ruling D2 (a): the Android side of keeping a credential off the screen's copies and off the
 * clipboard, read from the SOURCES in the form of `video/TrustWiringScanTest`. The rules are pure and unit-tested
 * (`SecureWindowsTest`, `PastedCredentialTest`); what no unit test executes is the code that hands them the real window
 * and the real clipboard. So that code holds no logic, and each piece of it is held here to the one form it must have:
 *  - every secret field (`OutlinedField(…, secret = true)`) is on a page whose FIRST statement is `SecurePage(…)`, so a
 *    future credential page cannot forget the window flag, and every field that looks like it takes a credential is a
 *    secret one; password-style input exists only in OutlinedField's secret form;
 *  - the window's FLAG_SECURE is touched only by the secure page's two steps, which follow the count's answer, and the
 *    page is counted in as it appears and out as it leaves;
 *  - only a secret field reaches the clipboard, its RAW edit is weighed by the rule, the clip is cleared only by the
 *    port's one call (which only the rule makes), and what is written is the rule's line and nothing else.
 * Each check has its twin: the same source with the site changed must be caught, so a clean result is never an empty
 * one. That the device does what the source says is the final device pass's.
 */
class CredentialScreenWiringScanTest {
    private class Fn(val name: String, val body: String, val start: Int, val end: Int)

    /** The index of the bracket that closes the one opened at [open]; strings are skipped. */
    private fun closing(text: String, open: Int, opens: Char, closes: Char): Int? {
        var depth = 0
        var inString = false
        var i = open
        while (i < text.length) {
            val c = text[i]
            if (inString) { if (c == '\\') i++ else if (c == '"') inString = false }
            else if (c == '"') inString = true
            else if (c == opens) depth++
            else if (c == closes) { depth--; if (depth == 0) return i }
            i++
        }
        return null
    }

    /** Every function in [text] with a block body (an expression body is not one: it cannot call SecurePage first). */
    private fun functions(text: String): List<Fn> = Regex("\\bfun (?:<[^>]*> )?(?:[\\w.]+\\.)?(\\w+)\\(").findAll(text).mapNotNull { m ->
        val close = closing(text, m.range.last, '(', ')') ?: return@mapNotNull null
        val brace = text.indexOf('{', close)
        val equals = text.indexOf('=', close)
        if (brace < 0 || equals in 0 until brace) return@mapNotNull null
        val end = closing(text, brace, '{', '}') ?: return@mapNotNull null
        Fn(m.groupValues[1], text.substring(brace, end + 1), brace, end)
    }.toList()

    /** The innermost function whose body holds [index]. */
    private fun enclosing(text: String, index: Int): Fn? = functions(text).filter { index in it.start..it.end }.maxByOrNull { it.start }

    private val FIELD = Regex("(?<![\\w.])OutlinedField\\(")
    private val CREDENTIAL_WORDS = Regex("(?i)password|passcode|passphrase|token|secret|api_?key|_key\\b|key_field")

    /** What is wrong with the credential pages' wiring; empty when nothing is. */
    private fun problems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()

        // ---- every secret field is on a page that calls SecurePage first; every credential-looking field is secret
        val secretPages = mutableSetOf<String>()
        for ((file, text) in sources) {
            for (m in FIELD.findAll(text)) {
                if (text.startsWith("fun ", m.range.first - 4)) continue
                val field = call(text, m.range.first)
                if (!field.contains("secret = true")) {
                    if (CREDENTIAL_WORDS.containsMatchIn(field)) problems += "$file: a field that looks like it takes a credential is not a secret one"
                    continue
                }
                val page = enclosing(text, m.range.first)
                if (page == null) { problems += "$file: a secret field outside any function's block body"; continue }
                secretPages += "$file#${page.name}"
                if (!page.body.startsWith("{ SecurePage(")) problems += "$file: ${page.name} holds a secret field but does not call SecurePage first"
                if (!field.contains("linesTag = ")) problems += "$file: ${page.name}'s secret field does not name its page's diagnostics tag"
            }
        }
        if (!secretPages.containsAll(setOf("video/TmdbKeyPage.kt#TmdbKeyPage", "video/MediaServerPages.kt#AddServerForm"))) {
            problems += "the two credential pages' secret fields were not both found: $secretPages"
        }
        val keyPage = sources["video/TmdbKeyPage.kt"].orEmpty()
        val form = sources["video/MediaServerPages.kt"].orEmpty()
        if (!body(keyPage, "fun TmdbKeyPage()").startsWith("{ SecurePage(\"video\") ")) problems += "the key page is not secured under its own tag"
        if (!body(form, "fun AddServerForm(").startsWith("{ SecurePage(tag) ")) problems += "the sign-in form is not secured under the tag it was given (video or music)"
        val keyField = FIELD.findAll(keyPage).map { call(keyPage, it.range.first) }.toList()
        if (keyField.size != 1 || !keyField.single().contains("linesTag = \"video\"")) problems += "the key's field does not write its lines under video"
        val formFields = FIELD.findAll(form).map { call(form, it.range.first) }.toList()
        if (formFields.count { it.contains("\"server_password\"") && it.contains("linesTag = tag") } != 1) problems += "the password field does not write its lines under the form's tag"
        // The user name and the host are plain fields: they never read, and so never clear, the clipboard.
        for (tag in listOf("\"server_host\"", "\"server_user\"")) {
            val calls = formFields.filter { it.contains(tag) }
            if (calls.size != 1 || calls.single().contains("secret")) problems += "the form's $tag field is not one plain field"
        }
        for (piece in listOf("PasswordVisualTransformation(", "KeyboardType.Password", "password()")) {
            val where = sources.filterValues { it.contains(piece) }.keys
            if (where != setOf("ui/components/OutlinedField.kt")) problems += "$piece is used outside OutlinedField's secret form: $where"
        }

        // ---- the window flag: the secure page's two steps, following the count
        val android = sources["net/AndroidCredentialScreen.kt"].orEmpty()
        val flags = sources.mapValues { (_, text) -> count(text, "FLAG_SECURE") }.filterValues { it > 0 }
        if (flags != mapOf("net/AndroidCredentialScreen.kt" to 2)) problems += "FLAG_SECURE is touched other than by the secure page's two steps: $flags"
        val secure = body(android, "private fun secure(window: Window, step: SecureStep, tag: String)")
        for (piece in listOf(
            "SecureStep.SET -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)",
            "SecureStep.CLEAR -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)",
            "SecureStep.KEEP -> Unit",
            "Diagnostics.add(tag, SecureWindows.line(step))",
        )) if (!secure.contains(piece)) problems += "the flag does not follow the count's step: $piece"
        val page = body(android, "fun SecurePage(tag: String)")
        if (!page.contains("val window = LocalActivity.current?.window DisposableEffect(window) {") ||
            !page.contains("secure(window, secureWindows.shown(window), tag) onDispose { secure(window, secureWindows.left(window), tag) }")
        ) problems += "the page's window is not counted in as it appears and out as it leaves"
        if (count(android, "secureWindows.") != 2 || !android.contains("private val secureWindows = SecureWindows<Window>()")) problems += "the count is changed other than by a page appearing and leaving"

        // ---- the clipboard: only a secret field, its raw edit, the rule's one clear, the rule's line
        val field = sources["ui/components/OutlinedField.kt"].orEmpty()
        if (count(field, "val clip = if (secret) AndroidClip(LocalContext.current) else null") != 1) problems += "the clipboard is reached for a field that is not a secret one"
        if (!field.contains("onValueChange = { typed -> val kept = if (singleLine) typed.replace(\"\\n\", \"\") else typed onValueChange(kept.take(maxLength)) if (clip != null) afterSecretEdit(linesTag, value, typed, clip) }")) {
            problems += "a secret field's edit is not weighed, raw, against the clipboard"
        }
        val made = sources.mapValues { (_, text) -> Regex("(?<!class )\\bAndroidClip\\(").findAll(text).count() }.filterValues { it > 0 }
        if (made != mapOf("ui/components/OutlinedField.kt" to 1)) problems += "the clipboard port is made outside the secret field: $made"
        val edits = sources.mapValues { (_, text) -> Regex("(?<!fun )\\bafterSecretEdit\\(").findAll(text).count() }.filterValues { it > 0 }
        if (edits != mapOf("ui/components/OutlinedField.kt" to 1)) problems += "a secret field's edit is weighed somewhere else: $edits"
        val clears = sources.mapValues { (_, text) -> count(text, "clearPrimaryClip(") }.filterValues { it > 0 }
        if (clears != mapOf("net/AndroidCredentialScreen.kt" to 1) || !android.contains("override fun clear() = manager().clearPrimaryClip()")) {
            problems += "the clipboard is cleared other than by the port's clear, which only the rule calls: $clears"
        }
        if (!android.contains("override fun text(): String? = manager().primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).text?.toString() else null }")) {
            problems += "the port does not read the clip as Compose's paste reads it"
        }
        if (body(android, "fun afterSecretEdit(") != "{ PastedCredential.line(PastedCredential.afterEdit(before, after, clip))?.let { Diagnostics.add(tag, it) } }") {
            problems += "a secret field's edit writes something other than the rule's line"
        }
        return problems
    }

    @Test fun `every credential page is secured first, its fields clear a paste through the rule, and nothing else touches either`() {
        val sources = SourceScan.all()
        assertTrue("the scan read the sources (${sources.size} files)", sources.size > 200)
        assertEquals(emptyList<String>(), problems(sources))
    }

    @Test fun `a credential page or field that goes round the wiring is caught`() {
        val sources = SourceScan.all()
        fun with(file: String, old: String, new: String) = problems(sources + (file to mutate(sources.getValue(file), old, new)))
        fun plus(file: String, code: String) = problems(sources + (file to sources.getValue(file) + " " + code))
        // The window flag forgotten: SecurePage removed from either page, or moved after the form's early return.
        assertTrue(with("video/TmdbKeyPage.kt", "SecurePage(\"video\") ", "").isNotEmpty())
        assertTrue(with("video/MediaServerPages.kt", "SecurePage(tag) ", "").isNotEmpty())
        assertTrue(with("video/MediaServerPages.kt", "SecurePage(tag) val context", "val context = LocalContext.current SecurePage(tag) val unused").isNotEmpty())
        assertTrue(with("video/MediaServerPages.kt", "SecurePage(tag) ", "if (prefillHost.isEmpty()) SecurePage(tag) ").isNotEmpty())
        // A NEW credential page that forgets it, a field that looks like a credential but is not secret, a raw password box.
        assertTrue(plus("video/HubPages.kt", "@Composable fun ApiTokenPage() { var t by remember { mutableStateOf(\"\") } OutlinedField(t, { t = it }, \"api_token\", secret = true, linesTag = \"video\") }").isNotEmpty())
        assertTrue(plus("video/HubPages.kt", "@Composable fun ApiTokenPage() { var t by remember { mutableStateOf(\"\") } OutlinedField(value = t, onValueChange = { t = it }, tag = \"api_token_field\") }").isNotEmpty())
        assertTrue(plus("music/MusicServerPages.kt", "@Composable fun Pin() { BasicTextField(\"\", {}, visualTransformation = PasswordVisualTransformation()) }").isNotEmpty())
        // The user name or the host made a secret field (it would read the clipboard), a field's tag dropped.
        assertTrue(with("video/MediaServerPages.kt", "imeAction = ImeAction.Next) BasicText(\"Password\"", "imeAction = ImeAction.Next, secret = true, linesTag = tag) BasicText(\"Password\"").isNotEmpty())
        assertTrue(with("video/MediaServerPages.kt", "linesTag = tag, ", "").isNotEmpty())
        assertTrue(with("video/TmdbKeyPage.kt", "linesTag = \"video\",", "").isNotEmpty())
        // The flag toggled instead of counted, never cleared, set or cleared somewhere else.
        assertTrue(with("net/AndroidCredentialScreen.kt", "onDispose { secure(window, secureWindows.left(window), tag) }", "onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }").isNotEmpty())
        assertTrue(with("net/AndroidCredentialScreen.kt", "onDispose { secure(window, secureWindows.left(window), tag) }", "onDispose { }").isNotEmpty())
        assertTrue(with("net/AndroidCredentialScreen.kt", "SecureStep.CLEAR -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)", "SecureStep.CLEAR -> Unit").isNotEmpty())
        assertTrue(with("net/AndroidCredentialScreen.kt", "SecureStep.KEEP -> Unit", "SecureStep.KEEP -> window.clearFlags(0)").isNotEmpty())
        assertTrue(plus("video/VideoActivity.kt", "fun open(w: android.view.Window) { w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }").isNotEmpty())
        // The clipboard reached for every field, weighed on the filtered text, not weighed, cleared elsewhere, read otherwise.
        assertTrue(with("ui/components/OutlinedField.kt", "val clip = if (secret) AndroidClip(LocalContext.current) else null", "val clip = AndroidClip(LocalContext.current)").isNotEmpty())
        assertTrue(with("ui/components/OutlinedField.kt", "afterSecretEdit(linesTag, value, typed, clip)", "afterSecretEdit(linesTag, value, kept, clip)").isNotEmpty())
        assertTrue(with("ui/components/OutlinedField.kt", "if (clip != null) afterSecretEdit(linesTag, value, typed, clip)", "").isNotEmpty())
        assertTrue(plus("video/TmdbKeyPage.kt", "fun wipe(c: Context) { c.getSystemService(ClipboardManager::class.java).clearPrimaryClip() }").isNotEmpty())
        assertTrue(with("net/AndroidCredentialScreen.kt", "override fun clear() = manager().clearPrimaryClip()", "override fun clear() = manager().setPrimaryClip(ClipData.newPlainText(\"\", \"\"))").isNotEmpty())
        assertTrue(with("net/AndroidCredentialScreen.kt", "it.getItemAt(0).text?.toString()", "it.getItemAt(0).coerceToText(context).toString()").isNotEmpty())
        // What was pasted written to a line.
        assertTrue(with("net/AndroidCredentialScreen.kt", "?.let { Diagnostics.add(tag, it) }", "?.let { Diagnostics.add(tag, \"\$it: \${clip.text()}\") }").isNotEmpty())
    }
}

package app.tileshell.media

import java.io.File
import org.junit.Assert.assertEquals

/**
 * Reading the shell's own sources as CODE, for the tests that hold a trust rule's call site to its one form (the form
 * `video/TrustWiringScanTest` set: comments removed, string literals kept, white space collapsed — so a comment cannot
 * satisfy a check and a reformatted line does not break one).
 */
object SourceScan {
    val root: File = listOf(File("src/main/kotlin/app/tileshell"), File("app/src/main/kotlin/app/tileshell")).first { it.isDirectory }

    fun read(path: String): String = code(File(root, path).readText())

    fun all(): Map<String, String> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
        .associate { it.relativeTo(root).invariantSeparatorsPath to code(it.readText()) }

    /** Kotlin source with its comments removed (nested block comments too) and its string literals kept, white space collapsed. */
    fun code(source: String): String {
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

    /** [text] with [old] — which must be there exactly once — turned into [new]: a mutation applied to a copy. */
    fun mutate(text: String, old: String, new: String): String {
        assertEquals("the mutation's site is in the source once: $old", 1, Regex(Regex.escape(old)).findAll(text).count())
        return text.replace(old, new)
    }

    /** The call that starts at [start] (the index of its name), through its closing parenthesis; strings are skipped. */
    fun call(text: String, start: Int): String {
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
    fun body(text: String, declaration: String): String {
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

    fun count(text: String, piece: String): Int = Regex(Regex.escape(piece)).findAll(text).count()
}

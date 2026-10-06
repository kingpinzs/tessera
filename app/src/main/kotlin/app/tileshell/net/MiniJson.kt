package app.tileshell.net

/**
 * A small JSON reader and writer in plain Kotlin (phase 17). The catalogue's, Wikidata's and the media server's
 * answers are parsed by rules the unit tests run on the JVM, where Android's `org.json` is a stub; so those rules read
 * JSON through this instead. Objects are `Map<String, Any?>` (insertion order kept), arrays `List<Any?>`, numbers
 * `Long` or `Double`, plus `String`, `Boolean` and null.
 */
object MiniJson {
    class Malformed(message: String) : Exception(message)

    /** The value [text] holds; [Malformed] when it is not one JSON value. Depth is bounded, so no input overflows the stack. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipSpace()
        val v = p.value(0)
        p.skipSpace()
        if (p.pos != text.length) throw Malformed("trailing text at ${p.pos}")
        return v
    }

    /** As [parse], null instead of an exception. */
    fun parseOrNull(text: String): Any? = try { parse(text) } catch (e: Malformed) { null }

    fun write(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(v: Any?, out: StringBuilder) {
        when (v) {
            null -> out.append("null")
            is Boolean -> out.append(v)
            is Number -> out.append(v)
            is String -> quote(v, out)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) out.append(',')
                    first = false
                    quote(k.toString(), out)
                    out.append(':')
                    write(x, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (x in v) {
                    if (!first) out.append(',')
                    first = false
                    write(x, out)
                }
                out.append(']')
            }
            else -> quote(v.toString(), out)
        }
    }

    private fun quote(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append(String.format("\\u%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private const val MAX_DEPTH = 64

    private class Parser(val s: String) {
        var pos = 0

        fun skipSpace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw Malformed("nested too deep")
            if (pos >= s.length) throw Malformed("unexpected end")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw Malformed("unexpected '$c' at $pos")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, pos)) throw Malformed("bad literal at $pos")
            pos += w.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            pos++
            skipSpace()
            if (pos < s.length && s[pos] == '}') { pos++; return out }
            while (true) {
                skipSpace()
                if (pos >= s.length || s[pos] != '"') throw Malformed("expected a key at $pos")
                val k = str()
                skipSpace()
                if (pos >= s.length || s[pos] != ':') throw Malformed("expected ':' at $pos")
                pos++
                skipSpace()
                out[k] = value(depth + 1)
                skipSpace()
                if (pos >= s.length) throw Malformed("unexpected end")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw Malformed("expected ',' or '}' at ${pos - 1}")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val out = ArrayList<Any?>()
            pos++
            skipSpace()
            if (pos < s.length && s[pos] == ']') { pos++; return out }
            while (true) {
                skipSpace()
                out += value(depth + 1)
                skipSpace()
                if (pos >= s.length) throw Malformed("unexpected end")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw Malformed("expected ',' or ']' at ${pos - 1}")
                }
            }
        }

        private fun str(): String {
            val out = StringBuilder()
            pos++
            while (true) {
                if (pos >= s.length) throw Malformed("unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (pos >= s.length) throw Malformed("unterminated escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw Malformed("short \\u escape")
                                out.append((s.substring(pos, pos + 4).toIntOrNull(16) ?: throw Malformed("bad \\u escape")).toChar())
                                pos += 4
                            }
                            else -> throw Malformed("bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' || s[pos] == '+' || s[pos] == '-')) pos++
            val t = s.substring(start, pos)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw Malformed("bad number '$t'")
        }
    }
}

/** Reading helpers: each returns null (or empty) when the shape is not what was asked for, so a parser never throws on a field. */
@Suppress("UNCHECKED_CAST")
fun Any?.jsonObject(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.jsonArray(): List<Any?> = this as? List<Any?> ?: emptyList()

fun Map<String, Any?>.jsonString(key: String): String? = this[key] as? String

fun Map<String, Any?>.jsonLong(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Double -> v.toLong()
    else -> null
}

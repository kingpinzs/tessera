package app.tileshell.calendar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A JSON reader and writer for the JVM tests only: Android's `org.json` is not on a unit test's classpath, and the
 * store's file handling and its section-by-section reading are written over [SyncCodec] so they can run here. It reads
 * the JSON the store writes and the inputs of the trust review's probe; the phone's codec is `JsonSyncCodec`.
 */
object TestJson : SyncCodec {
    override fun decode(text: String): Map<String, Any?> {
        val p = Parser(text)
        val v = p.value()
        p.end()
        @Suppress("UNCHECKED_CAST")
        return v as? Map<String, Any?> ?: error("not a JSON object")
    }

    override fun encode(tree: Map<String, Any?>): String = StringBuilder().also { write(it, tree) }.toString()

    private fun write(out: StringBuilder, v: Any?) {
        when (v) {
            null -> out.append("null")
            is String -> quote(out, v)
            is Number, is Boolean -> out.append(v.toString())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, e) in v) {
                    if (!first) out.append(',')
                    first = false
                    quote(out, k.toString())
                    out.append(':')
                    write(out, e)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                v.forEachIndexed { i, e ->
                    if (i > 0) out.append(',')
                    write(out, e)
                }
                out.append(']')
            }
            else -> error("not a JSON value: $v")
        }
    }

    private fun quote(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            else -> out.append(c)
        }
        out.append('"')
    }

    private class Parser(private val s: String) {
        private var i = 0

        private fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun expect(c: Char) {
            skip()
            if (i >= s.length || s[i] != c) error("expected $c at $i")
            i++
        }

        fun end() {
            skip()
            if (i != s.length) error("text after the value at $i")
        }

        fun value(): Any? {
            skip()
            if (i >= s.length) error("no value")
            return when (val c = s[i]) {
                '{' -> {
                    i++
                    val map = LinkedHashMap<String, Any?>()
                    skip()
                    if (i < s.length && s[i] == '}') {
                        i++
                        return map
                    }
                    while (true) {
                        skip()
                        val key = string()
                        expect(':')
                        map[key] = value()
                        skip()
                        if (i < s.length && s[i] == ',') {
                            i++
                            continue
                        }
                        expect('}')
                        return map
                    }
                    @Suppress("UNREACHABLE_CODE")
                    map
                }
                '[' -> {
                    i++
                    val list = ArrayList<Any?>()
                    skip()
                    if (i < s.length && s[i] == ']') {
                        i++
                        return list
                    }
                    while (true) {
                        list += value()
                        skip()
                        if (i < s.length && s[i] == ',') {
                            i++
                            continue
                        }
                        expect(']')
                        return list
                    }
                    @Suppress("UNREACHABLE_CODE")
                    list
                }
                '"' -> string()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> {
                    if (c != '-' && !c.isDigit()) error("unexpected $c at $i")
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] in "-+.eE")) i++
                    val text = s.substring(start, i)
                    text.toIntOrNull() ?: text.toLongOrNull() ?: text.toDouble()
                }
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) error("unexpected text at $i")
            i += w.length
            return v
        }

        private fun string(): String {
            if (i >= s.length || s[i] != '"') error("expected a string at $i")
            i++
            val out = StringBuilder()
            while (true) {
                if (i >= s.length) error("an unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (i >= s.length) error("an unterminated escape")
                        when (val e = s[i++]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'u' -> {
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> out.append(e)
                        }
                    }
                    else -> out.append(c)
                }
            }
        }
    }
}

/**
 * Phase 16 fix round F17 and F21 (trust review A-F3, A-F8), with F16's file shape: `calendar_sync.json` as the product
 * store reads and writes it, against files in a temp dir — the inputs of the reviewer's probe, each with the answer the
 * fix must give.
 */
class CalendarSyncStoreTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "cal-store-${System.nanoTime()}").apply { mkdirs() }
    private val file = File(dir, CalendarSyncStore.FILE)

    @After fun cleanUp() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    private val me = CalendarKey(5, "me@example.com", "com.google", "personal-cal")
    private val workKey = CalendarKey(6, "work@example.com", "com.google", "work-cal")

    private val allowedEntry = """{"id":5,"accountName":"me@example.com","accountType":"com.google","name":"personal-cal"}"""
    private val mappingEntry = """{"id":5,"accountName":"me@example.com","accountType":"com.google","name":"personal-cal","local":10,"copy":77}"""

    /** The probe's good file, in this build's shape (the key carries the name). */
    private val good = """{"version":1,"allowed":[$allowedEntry],"mappings":[$mappingEntry],"hidden":[],"notifiedAlerts":["10:1:2"],"remindersSince":1790000000000}"""

    private val goodState = SyncState(
        allowed = listOf(me), mappings = mapOf(10L to SyncMapping(10, me, 77)), notifiedAlerts = setOf("10:1:2"), remindersSince = 1_790_000_000_000L,
    )

    /** A store over [content] (null: no file), as a fresh process start reads it. */
    private fun storeOver(content: String?): CalendarSyncStore {
        if (content == null) file.delete() else file.writeText(content)
        return CalendarSyncStore(file, TestJson)
    }

    private fun asides(): List<File> = dir.listFiles { f -> f.name.startsWith("${CalendarSyncStore.FILE}.unreadable-") }.orEmpty().toList()

    // ---------------------------------------------------------------- the shape

    @Test fun aMissingFileIsAnEmptyStoreAndAValidOneReadsAsWritten() {
        assertEquals(SyncState(), storeOver(null).current)
        assertEquals(goodState, storeOver(good).current)
        assertEquals(emptyList<File>(), asides())
    }

    @Test fun whatTheStoreWritesItReadsBack() {
        val state = SyncState(
            allowed = listOf(me, workKey),
            mappings = mapOf(10L to SyncMapping(10, me, 77), 11L to SyncMapping(11, workKey.copy(name = null), 78)),
            hidden = listOf(CalendarKey(8, "", "LOCAL", "")), firstDayOfWeek = 7, notifiedAlerts = setOf("10:1:2", "11:3:4"), remindersSince = 5,
        )
        assertEquals(state, SyncStateFile.read(SyncStateFile.write(state)).state)
        assertEquals(emptyList<String>(), SyncStateFile.read(SyncStateFile.write(state)).notes)
        // Through the file, and a restart.
        val store = storeOver(null)
        assertEquals(state, store.update { state })
        assertEquals(state, CalendarSyncStore(file, TestJson).current)
        // A calendar is named by id, accountName, accountType and name, in that order (F16); a mapping adds local and copy.
        assertTrue(file.readText(), file.readText().startsWith("""{"version":1,"allowed":[$allowedEntry,{"id":6,"accountName":"work@example.com","accountType":"com.google","name":"work-cal"}],"mappings":[$mappingEntry,"""))
    }

    // ---------------------------------------------------------------- F21: a file that cannot be read at all

    @Test fun anUnreadableFileAllowsNothingAndIsKeptAsideNotOverwritten() {
        val inputs = mapOf(
            "truncated-half" to good.substring(0, good.length / 2),
            "truncated-last-brace" to good.dropLast(1),
            "empty-file" to "",
            "garbage" to "\u0000\u0001not json at all",
            "top-level-array" to "[]",
        )
        for ((name, text) in inputs) {
            asides().forEach { it.delete() }
            val store = storeOver(text)
            assertEquals(name, SyncState(), store.current)
            // The file is out of the next save's way, byte for byte.
            assertFalse(name, file.exists())
            assertEquals(name, listOf(text), asides().map { it.readText() })
            // Any update — the receiver's record, a prune — then writes a fresh file, and the old one is still there.
            store.update { SyncStateRules.keepNotified(it, emptySet(), setOf("1:2:3")) }
            assertEquals(name, SyncState(notifiedAlerts = setOf("1:2:3")), CalendarSyncStore(file, TestJson).current)
            assertEquals(name, listOf(text), asides().map { it.readText() })
        }
    }

    // ---------------------------------------------------------------- F21: one bad entry, one bad section

    @Test fun aBadAllowedSectionAllowsNothingAndTheRestSurvives() {
        for (bad in listOf(""""yes"""", "true", "5", allowedEntry, "null")) {
            val state = storeOver(good.replace("[$allowedEntry]", bad)).current
            assertEquals(bad, goodState.copy(allowed = emptyList()), state)
        }
        // The section missing altogether: nothing allowed, the rest as it was.
        assertEquals(goodState.copy(allowed = emptyList()), storeOver(good.replace(""""allowed":[$allowedEntry],""", "")).current)
        assertEquals(emptyList<File>(), asides())
    }

    @Test fun aBadAllowedEntryIsDroppedAloneAndAllowsNothing() {
        val entries = mapOf(
            "missing accountType" to """{"id":5,"accountName":"me@example.com","name":"personal-cal"}""",
            "missing accountName" to """{"id":5,"accountType":"com.google","name":"personal-cal"}""",
            "missing id" to """{"accountName":"me@example.com","accountType":"com.google","name":"personal-cal"}""",
            "id as a string" to """{"id":"5","accountName":"me@example.com","accountType":"com.google","name":"personal-cal"}""",
            "id as a fraction" to """{"id":5.5,"accountName":"me@example.com","accountType":"com.google","name":"personal-cal"}""",
            "the entry is a number" to "5",
            "the entry is a list" to "[5]",
            "the entry is null" to "null",
            "an empty account name" to """{"id":5,"accountName":"","accountType":"com.google","name":"personal-cal"}""",
            "an account type of another type" to """{"id":5,"accountName":"me@example.com","accountType":7,"name":"personal-cal"}""",
            "a name of another type" to """{"id":5,"accountName":"me@example.com","accountType":"com.google","name":7}""",
            // F16: a file an earlier build wrote names no calendar inside the account — it is no tick.
            "no name (an earlier build's entry)" to """{"id":5,"accountName":"me@example.com","accountType":"com.google"}""",
            "a null name" to """{"id":5,"accountName":"me@example.com","accountType":"com.google","name":null}""",
        )
        val workEntry = """{"id":6,"accountName":"work@example.com","accountType":"com.google","name":"work-cal"}"""
        for ((name, entry) in entries) {
            // Alone: nothing allowed; the mappings, the notified set and the cut-off survive.
            assertEquals(name, goodState.copy(allowed = emptyList()), storeOver(good.replace("[$allowedEntry]", "[$entry]")).current)
            // Beside a good entry: that one is kept, in its place, and the bad one allows nothing.
            assertEquals(name, goodState.copy(allowed = listOf(workKey)), storeOver(good.replace("[$allowedEntry]", "[$entry,$workEntry]")).current)
            assertEquals(name, goodState.copy(allowed = listOf(workKey)), storeOver(good.replace("[$allowedEntry]", "[$workEntry,$entry]")).current)
        }
        assertEquals(emptyList<File>(), asides())
    }

    @Test fun aHandEditedEntryIsHonouredAsTheGateItIs() {
        // The file is the gate: only the app's uid can write it, and an entry that is whole is a tick.
        val edited = good.replace(""""allowed":[""", """"allowed":[{"id":6,"accountName":"work@example.com","accountType":"com.google","name":"work-cal"},""")
        assertEquals(listOf(workKey, me), storeOver(edited).current.allowed)
    }

    @Test fun aBadMappingIsDroppedAloneAndAnOldOneKeepsItsCopyHidden() {
        val other = """{"id":5,"accountName":"me@example.com","accountType":"com.google","name":"personal-cal","local":11,"copy":78}"""
        val kept = mapOf(11L to SyncMapping(11, me, 78))
        for (bad in listOf(
            mappingEntry.replace(""","copy":77""", ""), mappingEntry.replace(""","local":10""", ""), mappingEntry.replace(""""copy":77""", """"copy":"77""""),
            mappingEntry.replace(""""id":5,""", ""), mappingEntry.replace(""""accountName":"me@example.com",""", ""), "7", "null",
        )) {
            assertEquals(bad, goodState.copy(mappings = kept), storeOver(good.replace("[$mappingEntry]", "[$bad,$other]")).current)
        }
        // The section itself unreadable: no mappings; what is allowed, the notified set and the cut-off survive.
        assertEquals(goodState.copy(mappings = emptyMap()), storeOver(good.replace("[$mappingEntry]", """{"10":77}""")).current)
        // F16: a mapping an earlier build wrote (no name) is kept, its target's name unknown — its copy stays hidden.
        val old = storeOver(good.replace("[$mappingEntry]", "[" + mappingEntry.replace(""","name":"personal-cal"""", "") + "]")).current
        assertEquals(mapOf(10L to SyncMapping(10, me.copy(name = null), 77)), old.mappings)
        assertEquals(setOf(77L), SyncStateRules.copyIds(old))
        assertEquals(listOf(me), old.allowed)
    }

    @Test fun theOtherSectionsAreReadOnTheirOwnToo() {
        // A bad hidden entry, a bad first day, a notified set with other things in it, a cut-off of another type:
        // each reads as its own default and takes nothing else with it.
        val state = storeOver(
            """{"version":1,"allowed":[$allowedEntry],"mappings":[$mappingEntry],"hidden":[7,{"id":8,"accountName":"","accountType":"LOCAL","name":""},{"id":9,"accountName":"x","accountType":"y"}],""" +
                """"firstDayOfWeek":9,"notifiedAlerts":["10:1:2",5,"281",null,"11:3:4"],"remindersSince":"soon"}""",
        ).current
        assertEquals(
            SyncState(
                allowed = listOf(me), mappings = mapOf(10L to SyncMapping(10, me, 77)), hidden = listOf(CalendarKey(8, "", "LOCAL", "")),
                firstDayOfWeek = null, notifiedAlerts = setOf("10:1:2", "11:3:4"), remindersSince = null,
            ),
            state,
        )
        assertEquals(goodState.copy(notifiedAlerts = emptySet(), hidden = emptyList()), storeOver(good.replace("""["10:1:2"]""", "7").replace(""""hidden":[]""", """"hidden":"none"""")).current)
        assertEquals(3, storeOver(good.replace(""""hidden":[]""", """"hidden":[],"firstDayOfWeek":3""")).current.firstDayOfWeek)
    }

    @Test fun theNotesSayWhatWasDropped() {
        val tree = TestJson.decode(good.replace("[$allowedEntry]", "[5,$allowedEntry]").replace("[$mappingEntry]", "7"))
        assertEquals(
            listOf("allowed: 1 of 2 entries dropped (unreadable, or written before the key carried the calendar's name)", "mappings is unreadable: read as empty"),
            SyncStateFile.read(tree).notes,
        )
    }

    // ---------------------------------------------------------------- the file under a running store

    @Test fun aFileRemovedOrReplacedUnderARunningStoreIsReadAgain() {
        val store = storeOver(good)
        assertEquals(listOf(me), store.current.allowed)
        file.delete()
        assertEquals(SyncState(), store.current)
        file.writeText(good.replace("[$allowedEntry]", "[]"))
        assertEquals(goodState.copy(allowed = emptyList()), store.current)
    }

    // ---------------------------------------------------------------- F17: a change that could not be saved

    /** The temp file's place is taken by a directory: every save fails, whoever runs the test. */
    private fun breakSaves() = File(dir, "${CalendarSyncStore.FILE}.tmp").mkdirs()

    @Test fun anUnTickWhoseSaveFailsStaysTickedInMemoryAsItIsOnDisk() {
        val store = storeOver(good)
        assertEquals(listOf(me), store.current.allowed)
        assertTrue(breakSaves())
        // The un-tick could not be written: the store answers with what the file holds, and holds it.
        val after = store.update { SyncStateRules.setAllowed(it, me, false) }
        assertEquals(listOf(me), after.allowed)
        assertEquals(goodState, after)
        assertEquals(goodState, store.current)
        assertEquals(goodState, store.state.value)
        // The next start of the process reads the same thing: there is no state the page showed and the file did not.
        assertEquals(goodState, CalendarSyncStore(file, TestJson).current)
        assertEquals(good, file.readText())
    }

    @Test fun aTickWhoseSaveFailsIsNotATick() {
        val store = storeOver(null)
        assertTrue(breakSaves())
        val after = store.update { SyncStateRules.setAllowed(it, me, true) }
        assertEquals(emptyList<CalendarKey>(), after.allowed)
        assertEquals(SyncState(), store.current)
        assertEquals(SyncState(), store.state.value)
        assertFalse(file.exists())
        // Every other change that could not be saved is not held either: a mapping, a notified alert.
        assertEquals(SyncState(), store.update { SyncStateRules.map(it, SyncMapping(10, me, 77)) })
        assertEquals(SyncState(), store.update { SyncStateRules.keepNotified(it, emptySet(), setOf("1:2:3")) })
        assertEquals(SyncState(), store.current)
    }

    @Test fun aSaveThatWorksAgainIsKept() {
        val store = storeOver(good)
        val tmp = File(dir, "${CalendarSyncStore.FILE}.tmp")
        assertTrue(breakSaves())
        assertEquals(listOf(me), store.update { SyncStateRules.setAllowed(it, me, false) }.allowed)
        assertTrue(tmp.deleteRecursively())
        assertEquals(emptyList<CalendarKey>(), store.update { SyncStateRules.setAllowed(it, me, false) }.allowed)
        assertEquals(emptyList<CalendarKey>(), CalendarSyncStore(file, TestJson).current.allowed)
    }
}

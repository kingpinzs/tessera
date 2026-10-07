package app.tileshell.files

import app.tileshell.media.SourceScan
import app.tileshell.media.SourceScan.count
import app.tileshell.media.SourceScan.mutate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fixes L8 and L9, the Android side, read from the SOURCE (the form of `FilesProviderWiringScanTest`):
 * [FilesIntents.request] and [FilesIntents.lineSafe] are pure and tested; what no unit test runs is the activity that
 * hands them the real intent. So: FilesActivity reads an intent's extras in ONE expression — through the reader, never
 * by a typed getter that can throw or a text it then writes itself — and the two lines that carry a caller's text are
 * the reader's own (`createdLine`, `openAtLine`). Each check has its mutated twin.
 */
class FilesExtrasWiringScanTest {
    private val sources = mapOf("files/FilesActivity.kt" to SourceScan.read("files/FilesActivity.kt"), "files/FilesState.kt" to SourceScan.read("files/FilesState.kt"))

    private val reader = "private fun requestOf(intent: Intent?): FilesRequest = FilesIntents.request({ key -> intent?.extras?.get(key) }) { Diagnostics.add(\"files\", it) }"

    /** An extra read by a typed getter, or the intent's bundle taken apart, anywhere but the one reader. */
    private val extraReads = Regex("\\.get(String|CharSequence|Parcelable|Serializable|Int|Long|Boolean|Bundle|StringArray|StringArrayList|ParcelableArray|ParcelableArrayList)?Extra\\(|\\.extras\\b|getExtras\\(")

    /** A request's own text in a string template: a caller's bytes on their way into a line. */
    private val rawText = Regex("\\$\\{?\\s*(request|asked|pending)\\b[^}\"]*\\.(page|path|name|from)\\b|\\$\\{?\\s*request\\.")

    private fun problems(sources: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val activity = sources.getValue("files/FilesActivity.kt")
        val state = sources.getValue("files/FilesState.kt")
        if (count(activity, reader) != 1) problems += "FilesActivity's extras are not read by the one reader, in its one form"
        val reads = extraReads.findAll(activity).map { it.value }.toList()
        if (reads != listOf(".extras")) problems += "FilesActivity reads an intent's extras outside the reader: $reads"
        if (count(activity, "requestOf(intent)") != 2) problems += "onCreate and onNewIntent do not both take their request from requestOf(intent)"
        if (count(activity, "Diagnostics.add(\"files\", FilesIntents.createdLine(request))") != 1) problems += "the created line is not FilesIntents.createdLine's"
        if (count(state, "say(FilesIntents.openAtLine(asked.location.path, request.from))") != 1) problems += "the open-at line is not FilesIntents.openAtLine's"
        for ((file, text) in sources) rawText.findAll(text).forEach { problems += "$file puts a request's own text into a string: ${it.value}" }
        return problems
    }

    private fun with(file: String, old: String, new: String) = problems(sources + (file to mutate(sources.getValue(file), old, new)))

    @Test
    fun `the activity reads its extras through the one reader and writes a caller's text through the line rule`() {
        assertEquals(emptyList<String>(), problems(sources))
    }

    @Test
    fun `a changed read or a changed line is caught`() {
        val activity = "files/FilesActivity.kt"
        // The extras read by the typed getter again (L9), for one extra or for all.
        assertTrue(with(activity, "FilesIntents.request({ key -> intent?.extras?.get(key) })", "FilesIntents.request({ key -> intent?.getStringExtra(key) })").isNotEmpty())
        assertTrue(with(activity, reader, "private fun requestOf(intent: Intent?): FilesRequest = FilesRequest(intent?.getStringExtra(\"page\"), intent?.getStringExtra(\"path\"))").isNotEmpty())
        // The page written raw again (L8).
        assertTrue(with(activity, "Diagnostics.add(\"files\", FilesIntents.createdLine(request))", "Diagnostics.add(\"files\", \"FilesActivity created page=\${request.page ?: \"none\"}\")").isNotEmpty())
        assertTrue(with(activity, "Diagnostics.add(\"files\", FilesIntents.createdLine(request))", "Diagnostics.add(\"files\", \"FilesActivity created page=\" + request.page)").isNotEmpty())
        // A second place that takes the launch apart.
        assertTrue(with(activity, "pending = requestOf(intent) to false", "pending = FilesRequest(intent.getStringExtra(\"page\"), null) to false").isNotEmpty())
        // The caller's name written raw into the open-at line.
        assertTrue(with("files/FilesState.kt", "say(FilesIntents.openAtLine(asked.location.path, request.from))", "say(\"open at \${asked.location.path} (from \${request.from})\")").isNotEmpty())
    }
}

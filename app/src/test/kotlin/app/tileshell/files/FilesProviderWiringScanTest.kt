package app.tileshell.files

import app.tileshell.media.SourceScan
import app.tileshell.media.SourceScan.body
import app.tileshell.media.SourceScan.count
import app.tileshell.media.SourceScan.mutate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 18 GATE fix M6: the FileProvider's wiring, read from the SOURCE in the form of `media/UriAccessWiringScanTest`.
 * The scope rule and the serving rules are pure and unit-tested ([FilesProviderRules]); what no unit test executes is
 * the Android class that hands them the real URI — the review rebuilt `query` without its scope check and all 1,922
 * tests passed. So that class holds no logic, and it is held here to its one form:
 *  - every entry that can reveal a file (`openFile`, `query`, `getType`) is ONE expression: the URI through [gate], the
 *    answer straight into its rule; nothing else in the class reads a URI's path;
 *  - `insert`, `update` and `delete` only throw;
 *  - nothing calls up to FileProvider's own resolution, and the class overrides nothing else;
 *  - `getUriForFile` is called once, in `uriFor`, behind `FileShareGuard.check`, and `fileFor` answers by the same rule.
 * Each check has its twin: the source with the site changed (the review's surviving mutant among them), in memory, must
 * be caught — so a clean result is never an empty one.
 */
class FilesProviderWiringScanTest {
    private val source: String = SourceScan.read("files/FilesProvider.kt")

    /** The whole of the provider class's instance side, as code. A change to it is a change to this text, made knowingly. */
    private val form = listOf(
        "class FilesProvider : FileProvider() {",
        "private fun gate(uri: Uri): String? = FilesProviderRules.gate(uri.encodedPath, context?.let { roots(it) }.orEmpty(), CANONICAL, SAY)",
        "override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = FilesProviderRules.open(gate(uri), mode, OPENER, SAY).descriptor",
        "override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =",
        "FilesProviderRules.row(gate(uri), projection, OPENER, SAY).let { row -> MatrixCursor(row.columns.toTypedArray(), 1).apply { row.values?.let { addRow(it) } } }",
        "override fun getType(uri: Uri): String? = FilesProviderRules.type(gate(uri), MIME)",
        "override fun insert(uri: Uri, values: ContentValues): Uri = throw UnsupportedOperationException(\"No external inserts\")",
        "override fun update(uri: Uri, values: ContentValues, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException(\"No external updates\")",
        "override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException(\"No external deletes\")",
        "companion object {",
    ).joinToString(" ")

    /** The two companion functions that hand a URI out or take one in, as code. */
    private val uriForForm =
        "fun uriFor(context: Context, file: File): Uri? { val canonical = CANONICAL(file.path) " +
            "if (!FileShareGuard.check(canonical, roots(context), CANONICAL, SAY)) return null " +
            "return getUriForFile(context, AUTHORITY, File(canonical!!)) }"
    private val fileForForm =
        "fun fileFor(context: Context, uri: Uri): File? { " +
            "val path = if (uri.scheme == \"content\" && uri.authority == AUTHORITY) FilesProviderRules.gate(uri.encodedPath, roots(context), CANONICAL, SAY) else null.also { SAY(FileShareGuard.LINE_REFUSED) } " +
            "return path?.let { File(it) } }"

    private fun problems(text: String): List<String> {
        val problems = mutableListOf<String>()
        val at = text.indexOf("class FilesProvider : FileProvider() {")
        if (at < 0) return listOf("there is no FilesProvider class")
        val cls = text.substring(at)
        val instance = cls.substringBefore("companion object {") + "companion object {"
        if (instance != form) problems += "FilesProvider is not its one form:\n  is:      $instance\n  must be: $form"

        // Each revealing entry: the URI through the gate, the answer into its own rule, and nothing else.
        for ((entry, rule) in listOf("openFile" to "FilesProviderRules.open(gate(uri), ", "query" to "FilesProviderRules.row(gate(uri), ", "getType" to "FilesProviderRules.type(gate(uri), ")) {
            val declared = Regex("override fun $entry\\(uri: Uri[^=]*= ?(.*?)(?= override fun | companion object)").find(cls)?.groupValues?.get(1).orEmpty()
            if (!declared.startsWith(rule)) problems += "$entry does not hand the gate's answer to its rule: $declared"
            if (count(declared, "gate(uri)") != 1) problems += "$entry does not pass through the gate exactly once"
        }
        if (count(cls, "gate(uri)") != 3) problems += "the gate is called ${count(cls, "gate(uri)")} times, not once by each of the three entries"
        // A URI's path is read in two places: the gate, and fileFor's own call of the gate rule.
        val reads = Regex("\\buri\\.(encodedPath|path|pathSegments|lastPathSegment|toString\\(\\))|\\bpathOf\\(|\\bserve\\(").findAll(cls).map { it.value }.toList()
        if (reads != listOf("uri.encodedPath", "uri.encodedPath")) problems += "a URI's path is read outside the gate: $reads"
        if (count(cls, "FilesProviderRules.gate(uri.encodedPath, ") != 2) problems += "the gate rule is not asked with the URI's encoded path at both places"
        if (cls.contains("super.") || cls.contains("super<")) problems += "the provider calls up to FileProvider's own resolution"
        val overrides = Regex("override fun (\\w+)").findAll(cls).map { it.groupValues[1] }.toList()
        if (overrides != listOf("openFile", "query", "getType", "insert", "update", "delete")) problems += "the provider's entries are not the six: $overrides"
        for (write in listOf("insert", "update", "delete")) {
            val declared = Regex("override fun $write\\([^=]*= ?(.*?)(?= override fun | companion object)").find(cls)?.groupValues?.get(1).orEmpty()
            if (!Regex("throw UnsupportedOperationException\\(\"[^\"]*\"\\)").matches(declared)) problems += "$write does something: $declared"
        }

        // Handing a URI out, and resolving one for the shell's own readers.
        if (count(text, "getUriForFile(") != 1) problems += "getUriForFile is called ${count(text, "getUriForFile(")} times, not once"
        if ("fun uriFor(context: Context, file: File): Uri? " + body(cls, "fun uriFor(") != uriForForm) problems += "uriFor is not its one form: ${body(cls, "fun uriFor(")}"
        if ("fun fileFor(context: Context, uri: Uri): File? " + body(cls, "fun fileFor(") != fileForForm) problems += "fileFor is not its one form: ${body(cls, "fun fileFor(")}"
        return problems
    }

    @Test
    fun `the provider is its one form - every entry through the gate, into its rule`() {
        assertEquals(emptyList<String>(), problems(source))
    }

    @Test
    fun `a changed entry is caught - the scope call gone from query, from openFile, from getType`() {
        // The review's surviving mutant M13: query rebuilt from the URI's own path, with no scope check.
        assertTrue(problems(mutate(source, "FilesProviderRules.row(gate(uri), projection", "FilesProviderRules.row(FilesProviderRules.pathOf(uri.encodedPath), projection")).isNotEmpty())
        assertTrue(problems(mutate(source, "FilesProviderRules.open(gate(uri), mode", "FilesProviderRules.open(FilesProviderRules.pathOf(uri.encodedPath), mode")).isNotEmpty())
        assertTrue(problems(mutate(source, "FilesProviderRules.type(gate(uri), MIME)", "FilesProviderRules.type(uri.path?.removePrefix(\"/root\"), MIME)")).isNotEmpty())
        // The gate itself answering without the rule.
        assertTrue(problems(mutate(source, "private fun gate(uri: Uri): String? = FilesProviderRules.gate(uri.encodedPath, context?.let { roots(it) }.orEmpty(), CANONICAL, SAY)", "private fun gate(uri: Uri): String? = FilesProviderRules.pathOf(uri.encodedPath)")).isNotEmpty())
        // An entry that asks the gate and then opens something else.
        assertTrue(problems(mutate(source, "FilesProviderRules.open(gate(uri), mode, OPENER, SAY).descriptor", "FilesProviderRules.open(gate(uri), mode, OPENER, SAY).let { ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY) }")).isNotEmpty())
        // Falling through to FileProvider's own resolution.
        assertTrue(problems(mutate(source, "override fun getType(uri: Uri): String? = FilesProviderRules.type(gate(uri), MIME)", "override fun getType(uri: Uri): String? = FilesProviderRules.type(gate(uri), MIME) ?: super.getType(uri)")).isNotEmpty())
    }

    @Test
    fun `a changed write entry is caught - delete, insert or update made to do something`() {
        assertTrue(problems(mutate(source, "Int = throw UnsupportedOperationException(\"No external deletes\")", "Int = if (File(uri.path!!).delete()) 1 else 0")).isNotEmpty())
        assertTrue(problems(mutate(source, "Int = throw UnsupportedOperationException(\"No external deletes\")", "Int = super.delete(uri, selection, selectionArgs)")).isNotEmpty())
        assertTrue(problems(mutate(source, "override fun insert(uri: Uri, values: ContentValues): Uri = throw UnsupportedOperationException(\"No external inserts\") ", "")).isNotEmpty())
        assertTrue(problems(mutate(source, "Int = throw UnsupportedOperationException(\"No external updates\")", "Int = 0")).isNotEmpty())
    }

    @Test
    fun `a changed hand-out is caught - a URI made without the check, or resolved without the rule`() {
        assertTrue(problems(mutate(source, "if (!FileShareGuard.check(canonical, roots(context), CANONICAL, SAY)) return null ", "")).isNotEmpty())
        assertTrue(problems(mutate(source, "return getUriForFile(context, AUTHORITY, File(canonical!!))", "return getUriForFile(context, AUTHORITY, file)")).isNotEmpty())
        assertTrue(problems(mutate(source, "FilesProviderRules.gate(uri.encodedPath, roots(context), CANONICAL, SAY) else null", "FilesProviderRules.pathOf(uri.encodedPath) else null")).isNotEmpty())
        assertTrue(problems(mutate(source, "uri.scheme == \"content\" && uri.authority == AUTHORITY", "uri.scheme == \"content\"")).isNotEmpty())
    }
}

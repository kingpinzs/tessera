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
 * The WHOLE file is read (round 3; the scan once stopped at `companion object {` and so never saw the opener, the
 * canonicaliser, the roots or [AndroidOpenedFile] — the GATE review's W1-W3 survived): its top-level declarations are the
 * four it has, the companion and [AndroidOpenedFile] are held to their one form, the canonicaliser is `canonicalPath`
 * (W1) and the roots are the storage volumes' directories and nothing else (W3).
 * And the ENTRY AUDIT: every way into a content provider — each public `ContentProvider` method that takes a URI, and
 * `call` / `applyBatch` — is either one of this class's overrides, each of which passes the gate into its rule or only
 * throws, or an inherited default reviewed here that answers through such an override or without reading a file. A new
 * override without the gate, or an inherited entry nobody reviewed, fails.
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

    /** W1: the canonicaliser resolves links and `..` (`canonicalPath`), never only makes a path absolute. */
    private val canonicalForm = "private val CANONICAL: (String) -> String? = { runCatching { File(it).canonicalPath }.getOrNull() }"

    /** W3: the roots are the mounted storage volumes' directories, and nothing else — never `/data`, never a literal. */
    private val rootsForm = "fun roots(context: Context): List<String> = context.getSystemService(StorageManager::class.java)?.storageVolumes.orEmpty().mapNotNull { it.directory?.path }"

    /** The companion's members, as code: what the instance side hands the gate and the opener. */
    private val companionForm = listOf(
        "companion object {",
        "const val AUTHORITY = \"app.tileshell.files\"",
        canonicalForm,
        "private val SAY: (String) -> Unit = { Diagnostics.add(\"files\", it) }",
        "private val MIME: (String) -> String? = { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }",
        "private val OPENER: (String) -> AndroidOpenedFile? = FilesProviderRules.opener { file, mode -> AndroidOpenedFile(ParcelFileDescriptor.open(file, mode)) }",
        rootsForm,
        uriForForm,
        fileForForm,
        "}",
    ).joinToString(" ")

    /** The device's opened file: the descriptor's real place from `/proc/self/fd`, its kind and size from `fstat`. */
    private val openedForm = "class AndroidOpenedFile(val descriptor: ParcelFileDescriptor) : OpenedFile { " +
        "private val stat = runCatching { Os.fstat(descriptor.fileDescriptor) }.getOrNull() " +
        "override val realPath: String? get() = runCatching { Os.readlink(\"/proc/self/fd/\${descriptor.fd}\") }.getOrNull() " +
        "override val isRegularFile: Boolean get() = stat != null && OsConstants.S_ISREG(stat.st_mode) " +
        "override val size: Long get() = stat?.st_size ?: 0L " +
        "override fun close() { runCatching { descriptor.close() } } }"

    /** The file's top-level declarations, in order: the port, the pure rules, the device's port, the provider. */
    private val declarations = listOf("interface OpenedFile", "object FilesProviderRules", "class AndroidOpenedFile", "class FilesProvider")

    /** Each top-level `interface | object | class | fun | val | var | typealias <name>` of [text]: at brace and parenthesis depth 0. */
    private fun topLevel(text: String): List<String> {
        val depth = IntArray(text.length)
        var d = 0
        var inString = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            depth[i] = d
            if (inString) { if (c == '\\') { i++; if (i < text.length) depth[i] = d } else if (c == '"') inString = false }
            else if (c == '"') inString = true
            else if (c == '{' || c == '(') d++
            else if (c == '}' || c == ')') d--
            i++
        }
        return Regex("\\b(interface|object|class|fun|val|var|typealias) (\\w+)").findAll(text)
            .filter { depth[it.range.first] == 0 && !text.substring(0, it.range.first).trimEnd().endsWith("::") }
            .map { "${it.groupValues[1]} ${it.groupValues[2]}" }.toList()
    }

    private fun problems(text: String): List<String> {
        val problems = mutableListOf<String>()
        val at = text.indexOf("class FilesProvider : FileProvider() {")
        if (at < 0) return listOf("there is no FilesProvider class")
        val cls = "class FilesProvider : FileProvider() " + body(text, "class FilesProvider : FileProvider() {")

        // The whole file: its four declarations, each of the device's parts in its one form.
        if (topLevel(text) != declarations) problems += "the file's declarations are not the four: ${topLevel(text)}"
        val instance = cls.substringBefore("companion object {") + "companion object {"
        if (instance != form) problems += "FilesProvider is not its one form:\n  is:      $instance\n  must be: $form"
        val companion = "companion object {" + cls.substringAfter("companion object {")
        if (companion != "$companionForm }") problems += "FilesProvider's companion is not its one form:\n  is:      $companion\n  must be: $companionForm }"
        if (count(text, canonicalForm) != 1) problems += "the canonicaliser is not canonicalPath (W1)"
        if (count(text, rootsForm) != 1) problems += "the roots are not the storage volumes' directories (W3)"
        val rootsBody = cls.substringAfter("fun roots(").substringBefore(" fun ")
        if (rootsBody.contains('"') || Regex("\\+|plus\\(|listOf\\(").containsMatchIn(rootsBody)) problems += "the roots hold something besides the volumes (W3): $rootsBody"
        val opened = "class AndroidOpenedFile(val descriptor: ParcelFileDescriptor) : OpenedFile " + body(text, "class AndroidOpenedFile(")
        if (opened != openedForm) problems += "AndroidOpenedFile is not its one form:\n  is:      $opened\n  must be: $openedForm"
        problems += audit(cls)

        // A URI's path is read in two places: the gate, and fileFor's own call of the gate rule.
        val reads = Regex("\\buri\\.(encodedPath|path|pathSegments|lastPathSegment|toString\\(\\))|\\bpathOf\\(|\\bserve\\(").findAll(cls).map { it.value }.toList()
        if (reads != listOf("uri.encodedPath", "uri.encodedPath")) problems += "a URI's path is read outside the gate: $reads"
        if (count(cls, "FilesProviderRules.gate(uri.encodedPath, ") != 2) problems += "the gate rule is not asked with the URI's encoded path at both places"
        if (cls.contains("super.") || cls.contains("super<")) problems += "the provider calls up to FileProvider's own resolution"

        // Handing a URI out, and resolving one for the shell's own readers.
        if (count(text, "getUriForFile(") != 1) problems += "getUriForFile is called ${count(text, "getUriForFile(")} times, not once"
        if ("fun uriFor(context: Context, file: File): Uri? " + body(cls, "fun uriFor(") != uriForForm) problems += "uriFor is not its one form: ${body(cls, "fun uriFor(")}"
        if ("fun fileFor(context: Context, uri: Uri): File? " + body(cls, "fun fileFor(") != fileForForm) problems += "fileFor is not its one form: ${body(cls, "fun fileFor(")}"
        return problems
    }

    /**
     * The entry audit over the class's source [cls]: each `override fun` is ONE expression that either hands the gate's
     * answer to its rule (`FilesProviderRules.<rule>(gate(uri), …`, the gate asked exactly once, the URI read nowhere
     * else) or only throws. Anything else — a block body, a second way to the file, a rule asked without the gate — is
     * named. The three revealing entries must be among the gated ones.
     */
    private fun audit(cls: String): List<String> {
        val problems = mutableListOf<String>()
        val gated = mutableListOf<String>()
        for (m in Regex("override fun (\\w+)\\(").findAll(cls)) {
            val name = m.groupValues[1]
            val signature = SourceScan.call(cls, m.range.first + "override fun ".length)
            val after = cls.substring(m.range.first + "override fun ".length + signature.length)
            val eq = after.indexOf('=')
            val brace = after.indexOf('{')
            if (eq < 0 || (brace in 0 until eq)) { problems += "$name is not one expression"; continue }
            val declared = after.substring(eq + 1).split(MEMBER, limit = 2)[0].trim()
            when {
                Regex("throw (UnsupportedOperationException|SecurityException)\\(\"[^\"]*\"\\)").matches(declared) -> {}
                Regex("FilesProviderRules\\.(open|row|type)\\(gate\\(uri\\), .*").matches(declared) && count(declared, "gate(uri)") == 1 &&
                    !Regex("\\buri\\.|\\bFile\\(|ParcelFileDescriptor\\.open|pathOf\\(|serve\\(").containsMatchIn(declared) -> gated += name
                else -> problems += "$name neither passes the gate into its rule nor refuses: $declared"
            }
        }
        if (!gated.containsAll(REVEALING)) problems += "the revealing entries ${REVEALING - gated.toSet()} do not pass the gate"
        return problems
    }

    /** Where the next member of the class begins. */
    private val MEMBER = Regex(" (?=(override |private |protected |internal |public )?(fun|val|var) |companion object)")

    /** The entries that can reveal a file: each must pass the gate. */
    private val REVEALING = listOf("openFile", "query", "getType")

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

    // ---- the whole file (round 3: W1, W3 and the parts the scan never saw)

    @Test
    fun `the canonicaliser, the roots, the opener and the device's opened file are each caught when changed`() {
        // W1: a canonicaliser that only makes the path absolute (no link, no `..` resolved).
        assertTrue(problems(mutate(source, "File(it).canonicalPath", "File(it).absolutePath")).any { "W1" in it })
        // W3: the roots gaining a folder that is not a storage volume, or losing the volumes.
        assertTrue(problems(mutate(source, ".mapNotNull { it.directory?.path }", ".mapNotNull { it.directory?.path } + \"/data\"")).any { "W3" in it })
        assertTrue(problems(mutate(source, ".mapNotNull { it.directory?.path }", ".mapNotNull { it.directory?.path }.plus(\"/\")")).any { "W3" in it })
        // The opener around the rule's own (read-only) opener.
        assertTrue(problems(mutate(source, "FilesProviderRules.opener { file, mode -> AndroidOpenedFile(ParcelFileDescriptor.open(file, mode)) }", "{ AndroidOpenedFile(ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_WRITE)) }")).isNotEmpty())
        // The opened file's real place no longer read from the kernel; its kind no longer from fstat.
        assertTrue(problems(mutate(source, "Os.readlink(\"/proc/self/fd/\${descriptor.fd}\")", "descriptor.toString()")).isNotEmpty())
        assertTrue(problems(mutate(source, "stat != null && OsConstants.S_ISREG(stat.st_mode)", "true")).isNotEmpty())
        // Another declaration in the file: a second provider beside this one.
        assertTrue(problems(source + " class Open : FileProvider() { }").any { "declarations" in it })
    }

    @Test
    fun `the entry audit - an entry added without the gate, or the gate taken from one, is named`() {
        val cls = { text: String -> "class FilesProvider : FileProvider() " + body(text, "class FilesProvider : FileProvider() {") }
        val getType = "override fun getType(uri: Uri): String? = FilesProviderRules.type(gate(uri), MIME)"
        assertEquals(emptyList<String>(), audit(cls(source)))
        // A new way in that opens the URI's own path.
        val unchecked = "$getType override fun openAssetFile(uri: Uri, mode: String): android.content.res.AssetFileDescriptor? = " +
            "android.content.res.AssetFileDescriptor(ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY), 0, -1)"
        assertTrue(audit(cls(mutate(source, getType, unchecked))).any { it.startsWith("openAssetFile neither passes the gate") })
        // The same entry through the gate passes the audit (the class's form, held above, still has to change knowingly).
        val checked = "$getType override fun openAssetFile(uri: Uri, mode: String): android.content.res.AssetFileDescriptor? = " +
            "FilesProviderRules.open(gate(uri), mode, OPENER, SAY).let { android.content.res.AssetFileDescriptor(it.descriptor, 0, -1) }"
        assertEquals(emptyList<String>(), audit(cls(mutate(source, getType, checked))))
        // A block body, and a call that reads a file.
        assertTrue(audit(cls(mutate(source, getType, "$getType override fun call(method: String, arg: String?, extras: Bundle?): Bundle? { return null }"))).any { it == "call is not one expression" })
        assertTrue(audit(cls(mutate(source, getType, "$getType override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = Bundle().apply { putString(\"x\", File(arg!!).readText()) }"))).any { it.startsWith("call neither") })
        // The check removed from an entry that has one.
        assertTrue(audit(cls(mutate(source, "FilesProviderRules.type(gate(uri), MIME)", "FilesProviderRules.type(FilesProviderRules.pathOf(uri.encodedPath), MIME)"))).any { it.startsWith("getType neither") })
        assertTrue(audit(cls(mutate(source, getType, ""))).any { "[getType]" in it })
    }

    // ---- the entry audit's other half: what the class does NOT override

    /** The answer of an inherited entry that reads no file at all. */
    private val noFile = "no file"

    /**
     * Each way into a content provider this class does not override, as `ContentProvider` (API 36) answers it: through
     * which of this class's overrides — each audited above — or with no file at all (`call` and `getStreamTypes` answer
     * null, `canonicalize` null, `uncanonicalize` the URI itself, `refresh` false).
     */
    private val contentProviderDefaults = mapOf(
        "openFile(Uri,String,CancellationSignal)" to "openFile",
        "openAssetFile(Uri,String)" to "openFile",
        "openAssetFile(Uri,String,CancellationSignal)" to "openFile",
        "openTypedAssetFile(Uri,String,Bundle)" to "getType,openFile",
        "openTypedAssetFile(Uri,String,Bundle,CancellationSignal)" to "getType,openFile",
        "query(Uri,String[],Bundle,CancellationSignal)" to "query",
        "query(Uri,String[],String,String[],String,CancellationSignal)" to "query",
        "insert(Uri,ContentValues,Bundle)" to "insert",
        "bulkInsert(Uri,ContentValues[])" to "insert",
        "update(Uri,ContentValues,Bundle)" to "update",
        "delete(Uri,Bundle)" to "delete",
        "applyBatch(ArrayList)" to "insert,update,delete,query",
        "applyBatch(String,ArrayList)" to "insert,update,delete,query",
        "call(String,String,Bundle)" to noFile,
        "call(String,String,String,Bundle)" to noFile,
        "canonicalize(Uri)" to noFile,
        "uncanonicalize(Uri)" to noFile,
        "refresh(Uri,Bundle,CancellationSignal)" to noFile,
        "getStreamTypes(Uri,String)" to noFile,
        // A helper the provider's own code calls to stream a pipe; no caller reaches it.
        "openPipeHelper(Uri,String,Bundle,Object,PipeDataWriter)" to noFile,
    )

    /** FileProvider's own answers to an entry this class does not override (androidx.core 1.18, read from its bytecode). */
    private val fileProviderOwn = mapOf(
        // `return "application/octet-stream"`: a constant, no path is resolved.
        "getTypeAnonymous(Uri)" to noFile,
    )

    private fun key(m: java.lang.reflect.Method) = "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }})"

    /**
     * Every public way into a [android.content.ContentProvider] — a method taking a URI, and `call` / `applyBatch` —
     * answered: by one of [ours] (the class's own overrides, audited by source), by [fileProvider]'s own code only where
     * reviewed in [fileProviderOwn], or by ContentProvider's default only where reviewed in [contentProviderDefaults],
     * whose answer goes through overrides that are [ours].
     */
    private fun inheritedProblems(entries: Set<String>, ours: Set<String>, fileProvider: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        val ourNames = ours.map { it.substringBefore('(') }.toSet()
        for (e in entries.sorted()) {
            val via = when {
                e in ours -> continue
                e in fileProvider -> fileProviderOwn[e] ?: run { problems += "$e is answered by FileProvider's own code, unreviewed"; continue }
                else -> contentProviderDefaults[e] ?: run { problems += "$e is a way in nobody reviewed"; continue }
            }
            if (via != noFile) for (d in via.split(',')) if (d !in ourNames) problems += "$e answers through $d, which this class does not override"
        }
        return problems
    }

    private val entries: Set<String> = android.content.ContentProvider::class.java.methods
        .filter { m -> !java.lang.reflect.Modifier.isStatic(m.modifiers) && m.declaringClass != Any::class.java &&
            (m.parameterTypes.any { it == android.net.Uri::class.java } || m.name == "call" || m.name == "applyBatch") }
        .map(::key).toSet()
    private val ours: Set<String> = FilesProvider::class.java.declaredMethods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }.map(::key).toSet()
    private val fileProvider: Set<String> = androidx.core.content.FileProvider::class.java.declaredMethods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }.map(::key).toSet()

    @Test
    fun `every way into the provider is an audited override, or a reviewed default that answers through one or reads nothing`() {
        assertTrue(entries.toString(), entries.size >= 20)
        assertEquals(setOf("openFile(Uri,String)", "query(Uri,String[],String,String[],String)", "getType(Uri)", "insert(Uri,ContentValues)", "update(Uri,ContentValues,String,String[])", "delete(Uri,String,String[])"), ours.filter { it in entries }.toSet())
        assertEquals(emptyList<String>(), inheritedProblems(entries, ours, fileProvider))
    }

    @Test
    fun `the inherited half is caught - an entry left to FileProvider, a library that starts serving one, a new way in`() {
        assertTrue(inheritedProblems(entries, ours - "getType(Uri)", fileProvider).any { it.startsWith("getType(Uri) is answered by FileProvider's own code") })
        assertTrue(inheritedProblems(entries, ours - "openFile(Uri,String)", fileProvider - "openFile(Uri,String)").any { "openAssetFile(Uri,String) answers through openFile" in it })
        assertTrue(inheritedProblems(entries, ours, fileProvider + "openAssetFile(Uri,String)").any { it.startsWith("openAssetFile(Uri,String) is answered by FileProvider") })
        assertTrue(inheritedProblems(entries + "openStream(Uri)", ours, fileProvider).any { it == "openStream(Uri) is a way in nobody reviewed" })
    }
}

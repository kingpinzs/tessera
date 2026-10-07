package app.tileshell.files

/**
 * The FileProvider's scope (T18-11, r3 D10; a trust rule): Files holds All-files access and its provider needs a
 * `root-path` entry to reach `/storage/<UUID>`, which also covers the shell's private dirs — so a URI is handed out, and
 * served, only for a file whose CANONICAL path (symlinks and `..` resolved) lies under one of
 * `StorageManager.getStorageVolumes()`' `StorageVolume.getDirectory()` — and NOT in one of the shell's own folders
 * there ([own]): `<volume>/.Tessera/` (the Recycle Bin, the nested-zip copies) and `<volume>/Android/data|obb/<the
 * shell's package>/`. Nothing ever hands those out, so nothing is ever served from them (the GATE review's M6).
 *
 * Pure: the roots and the canonicaliser are parameters (`File.canonicalPath` on a host cannot see `/storage`;
 * `PlayerRules.sourcePath` takes `canonical:` the same way). The SAME rule is asked twice on the device — before
 * `FileProvider.getUriForFile` ([check], which writes the refusal line) and again where the provider serves, in its
 * subclass's `openFile` / `query` ([allowed]) — so a grant holder gets nothing outside a volume and check-then-use is
 * closed. `FileShareGuardTest` holds E7's five cases.
 */
object FileShareGuard {
    /** `[files] share refused: outside shared storage`, without its tag. */
    const val LINE_REFUSED = "share refused: outside shared storage"

    /** The provider opened a file and it was not the file the rule had checked (`FilesProviderRules.open`). */
    const val LINE_SWAPPED = "share refused: not the file that was checked"

    /** The shell's application id (`app/build.gradle.kts`; `FilesProviderScopeTest` holds the two together). */
    const val OWN_PACKAGE = "app.tileshell"

    private val OWN_APP_DIRS = listOf("data", "obb")

    /**
     * [path]'s canonical form lies strictly under one of [roots] and outside the shell's [own] folders there. A path
     * that cannot be canonicalised, a canonicaliser that throws, and a root that is empty or only slashes all refuse.
     */
    fun allowed(path: String?, roots: List<String>, canonical: (String) -> String?): Boolean {
        if (path.isNullOrEmpty() || path.contains('\u0000')) return false
        val p = runCatching { canonical(path) }.getOrNull() ?: return false
        return roots.any { root ->
            // The root in the same form as the path: a volume directory is canonical already on the device.
            val r = runCatching { canonical(root) }.getOrNull() ?: return@any false
            FilePaths.under(p, r) && !own(p, r)
        }
    }

    /**
     * [path] (canonical, under the canonical [root]) is the shell's own: `.Tessera` or inside it, or
     * `Android/data|obb/<the shell's package>` or inside that. Names are compared ignoring case (FAT folds it).
     */
    internal fun own(path: String, root: String): Boolean {
        val parts = path.substring(root.trimEnd('/').length + 1).split('/')
        if (parts[0].equals(FilePaths.SHELL_DIR, ignoreCase = true)) return true
        return parts.size >= 3 && parts[0].equals(RecycleBin.ANDROID_DIR, ignoreCase = true) &&
            OWN_APP_DIRS.any { parts[1].equals(it, ignoreCase = true) } && parts[2].equals(OWN_PACKAGE, ignoreCase = true)
    }

    /** As [allowed], writing [LINE_REFUSED] through [say] when the answer is no — the share then does not start. */
    fun check(path: String?, roots: List<String>, canonical: (String) -> String?, say: (String) -> Unit): Boolean {
        val ok = allowed(path, roots, canonical)
        if (!ok) say(LINE_REFUSED)
        return ok
    }
}

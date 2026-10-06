package app.tileshell.files

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * One mounted storage volume as Files sees it: [uuid] is `StorageVolume.getUuid()` ([FilePaths.PRIMARY] for the primary
 * shared storage, whose uuid is null) and [root] is `StorageVolume.getDirectory()`'s path (`/storage/emulated/0`,
 * `/storage/3842-1609`). No Android type: the JVM tests name two temp dirs so.
 */
data class FileVolume(val uuid: String, val root: String)

/** The three answers of Files' conflict dialog (`files_dialog:replace|keep_both|skip`). */
enum class Conflict { REPLACE, KEEP_BOTH, SKIP }

/** Why a running operation was told to stop: the notification's Cancel, or the service's `onTimeout` (r3 D8). */
enum class StopReason { CANCEL, TIME_LIMIT }

/** How one write ended. A [Failed.reason] is the text after `failed ` in the operation's `[files]` line. */
sealed interface OpResult {
    /** [outputs] are the top-level paths the operation made; [skipped] counts the conflicts answered "skip". */
    data class Done(val outputs: List<String> = emptyList(), val skipped: Int = 0) : OpResult
    data object Cancelled : OpResult
    data class Failed(val reason: String) : OpResult
}

/**
 * What the copy service hands a long operation (r3 D7): [progress] is called with (bytes so far, total) from every byte
 * loop — the service writes `[files] copy progress <bytes>/<total>` from it at least once a second (r3 V7); [stopped] is
 * asked before every chunk; [pace] is Q-18-3's debug-only rate cap, null when unpaced.
 */
class OpControl(
    val progress: (Long, Long) -> Unit = { _, _ -> },
    val stopped: () -> StopReason? = { null },
    val pace: FilePace? = null,
)

/**
 * Q-18-3's pacing switch as a function of the bytes written: [wrote] sleeps until the operation's average rate is at or
 * under [bytesPerSecond]. A rate cap, not a per-chunk sleep, so a paced run takes bytes / rate on any volume. One object
 * per operation (the clock starts at its first chunk). The clock and the sleep are injected so a JVM test runs it without
 * waiting.
 */
class FilePace(
    private val bytesPerSecond: Long,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private var startNs = Long.MIN_VALUE

    /** Called after each chunk with the operation's total so far. */
    fun wrote(totalBytes: Long) {
        val now = nanoTime()
        if (startNs == Long.MIN_VALUE) startNs = now
        // When this many bytes are due at the cap, measured from the first chunk.
        val dueNs = totalBytes.toDouble() / bytesPerSecond * 1e9
        val waitMs = ((dueNs - (now - startNs)) / 1e6).toLong()
        if (waitMs > 0) sleep(waitMs)
    }

    companion object {
        /**
         * Q-18-3's QA pace as a rule (the form of `CatalogueRules.base`): [pref] as bytes a second, and ONLY in a debug
         * build — a release build is unpaced whatever the pref holds. Null (unpaced) too for a pref that is not a
         * number above 0. The one site that reads the pref hands it here with `BuildConfig.DEBUG`
         * (`video/TrustWiringScanTest`).
         */
        fun rate(debug: Boolean, pref: String?): Long? = if (debug) pref?.trim()?.toLongOrNull()?.takeIf { it > 0 } else null

        /** Q-18-5's QA pace for the search walk, as entries a second: the same rule as [rate], for its own pref. */
        fun searchRate(debug: Boolean, pref: String?): Long? = if (debug) pref?.trim()?.toLongOrNull()?.takeIf { it > 0 } else null

        /** The pref's value as a pace: null or 0 (or less) is unpaced. */
        fun of(bytesPerSecond: Long?): FilePace? = if (bytesPerSecond == null || bytesPerSecond <= 0) null else FilePace(bytesPerSecond)
    }
}

/** Path, name and small-file rules shared by every part of Files' pure layer. */
object FilePaths {
    /** [FileVolume.uuid] of the primary shared storage. */
    const val PRIMARY = "primary"

    /** The longest file name, in UTF-8 bytes, that ext4, f2fs and the FUSE layer over them take. */
    const val NAME_MAX = 255

    /** The shell's own folder at a volume's root: the Recycle Bin and the nested-zip copies live under it. */
    const val SHELL_DIR = ".Tessera"

    private const val EXT_MAX = 32

    /** Text fit for one diagnostics line: control characters (a newline in a file name) become `?`, the length is capped. */
    fun lineText(text: String?, max: Int = 400): String {
        val t = text.orEmpty()
        val out = StringBuilder(minOf(t.length, max))
        for (c in t) {
            if (out.length >= max) break
            out.append(if (c < ' ' || c == '\u007f' || c == ' ' || c == ' ') '?' else c)
        }
        return out.toString()
    }

    /** [path] lies strictly under [root]; both already canonical. An empty or all-slash root names the whole disk and matches nothing. */
    fun under(path: String, root: String): Boolean {
        val r = root.trimEnd('/')
        return r.isNotBlank() && path.length > r.length + 1 && path.startsWith("$r/")
    }

    /** The volume whose root [path]'s canonical form lies under (or is), or null: a path outside every volume. */
    fun volumeOf(path: String, volumes: List<FileVolume>, canonical: (String) -> String?): FileVolume? {
        val p = runCatching { canonical(path) }.getOrNull() ?: return null
        return volumes.firstOrNull { v ->
            val r = runCatching { canonical(v.root) }.getOrNull()?.trimEnd('/') ?: return@firstOrNull false
            r.isNotBlank() && (p == r || under(p, r))
        }
    }

    /** [path] is the shell's folder at [volume]'s root, or inside it (the bin, the nested-zip copies). */
    fun inShellDir(path: String, volume: FileVolume, canonical: (String) -> String?): Boolean {
        val p = runCatching { canonical(path) }.getOrNull() ?: return false
        val r = runCatching { canonical(volume.root) }.getOrNull()?.trimEnd('/') ?: return false
        if (!under(p, r)) return false
        // FAT ignores case, so `.tessera` is the same folder there.
        return p.substring(r.length + 1).substringBefore('/').equals(SHELL_DIR, ignoreCase = true)
    }

    /** A name the user may give a file or folder: one path segment that fits. */
    fun validName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\u0000' } && utf8(name) <= NAME_MAX

    fun utf8(s: String): Int = s.toByteArray(Charsets.UTF_8).size

    /** `b.bin` → (`b`, `.bin`); `.hidden`, `README` and a folder have no extension. An over-long tail is not an extension. */
    fun splitExtension(name: String, isDirectory: Boolean = false): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        if (isDirectory || dot <= 0 || dot == name.length - 1 || utf8(name.substring(dot)) > EXT_MAX) return name to ""
        return name.substring(0, dot) to name.substring(dot)
    }

    /**
     * `prefix + name + suffix` cut to [NAME_MAX] bytes by shortening only [name]'s base — its extension is kept and no
     * character is split (r3 D3: the bin name of a 255-byte file; the same rule fits a temp name, r3 D4).
     */
    fun fitName(prefix: String, name: String, suffix: String = "", isDirectory: Boolean = false): String {
        val (base, ext) = splitExtension(name, isDirectory)
        var room = NAME_MAX - utf8(prefix) - utf8(ext) - utf8(suffix)
        val kept = StringBuilder()
        var i = 0
        while (i < base.length) {
            val cp = base.codePointAt(i)
            val n = Character.charCount(cp)
            val bytes = utf8(base.substring(i, i + n))
            if (bytes > room) break
            kept.append(base, i, i + n)
            room -= bytes
            i += n
        }
        return prefix + kept + ext + suffix
    }

    /** r3 D4's temp name for a file being written: `.<name>.<opid>.part`, dot-hidden, in the destination folder. */
    fun partName(name: String, opId: String): String = fitName(".", name, ".$opId.part", isDirectory = true)

    /** r3 D4's temp name for a zip being extracted: `.<zip>.<opid>.extract`. */
    fun extractName(zipName: String, opId: String): String = fitName(".", zipName, ".$opId.extract", isDirectory = true)

    /** A journalled temp by its name alone: what the sweep is willing to delete. */
    fun isTempName(name: String): Boolean = name.startsWith(".") && (name.endsWith(".part") || name.endsWith(".extract"))

    /** "Keep both" (r3 D6): `name (2).ext`, then `(3)`…; a folder takes the number at its end. */
    fun keepBothName(name: String, n: Int, isDirectory: Boolean): String {
        val (base, ext) = splitExtension(name, isDirectory)
        return fitName("", base, " ($n)$ext", isDirectory = true)
    }

    /** The first "keep both" name in [dir] that nothing holds. */
    fun keepBoth(dir: File, name: String, isDirectory: Boolean, taken: (File) -> Boolean = ::existsNoFollow): File {
        var n = 2
        while (true) {
            val f = File(dir, keepBothName(name, n, isDirectory))
            if (!taken(f)) return f
            n++
        }
    }

    /** Present as anything at all — a dangling symlink included, so nothing is ever renamed onto one. */
    fun existsNoFollow(f: File): Boolean = Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    fun isSymlink(f: File): Boolean = Files.isSymbolicLink(f.toPath())

    /** A real directory, not a link to one: the only kind a walk descends into. */
    fun isRealDirectory(f: File): Boolean = Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    /** Deletes [f] and everything in it, never following a symlink out of it. True when nothing of it is left. */
    fun deleteTree(f: File): Boolean {
        if (!existsNoFollow(f)) return true
        var ok = true
        if (isRealDirectory(f)) f.listFiles()?.forEach { if (!deleteTree(it)) ok = false }
        return f.delete() && ok
    }

    /** The bytes of the regular files in [f] (itself, for a file); links are not followed and count nothing. */
    fun treeBytes(f: File): Long = when {
        isSymlink(f) -> 0L
        isRealDirectory(f) -> f.listFiles()?.sumOf { treeBytes(it) } ?: 0L
        else -> f.length()
    }

    /** [from] becomes [to]; never onto something that is there ([replace] = false) — `rename(2)` alone would replace it. */
    @Throws(IOException::class)
    fun rename(from: File, to: File, replace: Boolean) {
        if (replace) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } else {
            if (existsNoFollow(to)) throw java.nio.file.FileAlreadyExistsException(to.path)
            Files.move(from.toPath(), to.toPath())
        }
    }

    /**
     * Temp-and-rename (LayoutStore's shape): [text] is written to `<name>.tmp` beside [file], synced, and renamed over it,
     * so a reader sees the old file or the new one and never half of either. Throws when any step fails; no temp is left.
     */
    @Throws(IOException::class)
    fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            tmp.delete()
            throw if (e is IOException) e else IOException(e)
        }
    }
}

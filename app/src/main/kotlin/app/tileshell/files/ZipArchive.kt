package app.tileshell.files

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** One entry as the zip's central directory declares it. Every value here is the archive's CLAIM (a bomb lies about [size]). */
class ZipEntryInfo(
    /** Its place in the central directory. */
    val index: Int,
    /** Decoded by the UTF-8 flag (general-purpose bit 11), else as CP437 (T18-2). */
    val name: String,
    val flags: Int,
    val method: Int,
    val crc: Long,
    val compressedSize: Long,
    /** The declared uncompressed size. */
    val size: Long,
    val modifiedMs: Long,
    /** A Unix symlink entry: its data is the link text. It is extracted as a regular file holding that text (r3 D6). */
    val isSymlink: Boolean,
) {
    /** General-purpose flag bit 0 (r3 D6): `java.util.zip.ZipEntry` exposes no flag, so Files reads it itself. */
    val encrypted: Boolean get() = flags and 1 != 0
    val isDirectory: Boolean get() = name.endsWith("/")
}

/** The file is not a zip Files can read; [message] is the `<why>` of `zip open <path>: failed <why>`. */
class ZipFormatException(message: String) : Exception(message)

/**
 * Files' own central-directory reader (r3 D6): the end record (zip64's when there is one), then every entry's flags,
 * sizes, name bytes and Unix mode. It reads the directory only — no entry's data — so it is the same cost for a 3 GB
 * entry as for an empty one.
 *
 * What the end record CLAIMS is bounded before anything is allocated for it (the GATE review's L10): at most
 * [MAX_ENTRIES] entries and [MAX_DIRECTORY_BYTES] of directory. The directory is then read through one [WINDOW]-byte
 * buffer, an entry at a time — never into one array of its own size — and an entry that would lie past the
 * directory's end is damage.
 */
object ZipCentral {
    private const val SIG_END = 0x06054b50L
    private const val SIG_END64 = 0x06064b50L
    private const val SIG_LOCATOR64 = 0x07064b50L
    private const val SIG_ENTRY = 0x02014b50L
    private const val END_SIZE = 22
    private const val FLAG_UTF8 = 0x800

    private const val ENTRY_SIZE = 46

    /** More entries than this is "too many entries": no list of them is ever built (the fixtures' largest is 70,000). */
    const val MAX_ENTRIES = 100_000

    /** A directory bigger than this is "too many entries" too (70,000 entries take about 4 MB). */
    const val MAX_DIRECTORY_BYTES = 16L * 1024 * 1024

    /** The reader's one buffer: bigger than the biggest entry (46 bytes and three fields of at most 65,535). */
    private const val WINDOW = 256 * 1024

    @Throws(ZipFormatException::class)
    fun read(file: File): List<ZipEntryInfo> = try {
        RandomAccessFile(file, "r").use(::read)
    } catch (e: ZipFormatException) {
        throw e
    } catch (e: Exception) {
        throw ZipFormatException("unreadable (${e.javaClass.simpleName})")
    }

    private fun read(raf: RandomAccessFile): List<ZipEntryInfo> {
        val length = raf.length()
        if (length < END_SIZE) throw ZipFormatException("not a zip (too short)")
        // The end record is the last thing in the file, before a comment of at most 65,535 bytes.
        val tail = ByteArray(minOf(length, (END_SIZE + 0xFFFF).toLong()).toInt())
        raf.seek(length - tail.size)
        raf.readFully(tail)
        var end = tail.size - END_SIZE
        while (end >= 0 && !(u32(tail, end) == SIG_END && end + END_SIZE + u16(tail, end + 20) <= tail.size)) end--
        if (end < 0) throw ZipFormatException("not a zip (no end record)")
        var count = u16(tail, end + 10).toLong()
        var size = u32(tail, end + 12)
        var offset = u32(tail, end + 16)

        // zip64: a locator sits just before the end record and points at the 64-bit one.
        val endAt = length - tail.size + end
        if (endAt >= 20) {
            val loc = ByteArray(20)
            raf.seek(endAt - 20)
            raf.readFully(loc)
            if (u32(loc, 0) == SIG_LOCATOR64) {
                val at = u64(loc, 8)
                if (at < 0 || at + 56 > length) throw ZipFormatException("damaged (zip64 end record)")
                val rec = ByteArray(56)
                raf.seek(at)
                raf.readFully(rec)
                if (u32(rec, 0) != SIG_END64) throw ZipFormatException("damaged (zip64 end record)")
                count = u64(rec, 32)
                size = u64(rec, 40)
                offset = u64(rec, 48)
            }
        }
        if (size < 0 || offset < 0 || count < 0 || size > length || offset > length - size) throw ZipFormatException("damaged (central directory)")
        // The claim is weighed before a byte of the directory is read or a list is made for it.
        if (count > MAX_ENTRIES || size > MAX_DIRECTORY_BYTES) throw ZipFormatException("too many entries")

        val zone = ZoneId.systemDefault()
        val out = ArrayList<ZipEntryInfo>(count.toInt())
        // One window over the directory: `dir[p]` is the byte at `windowAt + p` of the file.
        val dir = ByteArray(minOf(size, WINDOW.toLong()).toInt())
        var windowAt = offset
        var held = 0
        val dirEnd = offset + size
        var at = offset
        /** Makes the [n] bytes at [at] readable and returns their index in [dir]; past the directory's end is damage. */
        fun need(n: Int): Int {
            if (n > dir.size || at + n > dirEnd) throw ZipFormatException("damaged (central directory)")
            if (at < windowAt || at + n > windowAt + held) {
                windowAt = at
                held = minOf(dir.size.toLong(), dirEnd - at).toInt()
                raf.seek(at)
                raf.readFully(dir, 0, held)
            }
            return (at - windowAt).toInt()
        }
        while (out.size < count) {
            var p = need(ENTRY_SIZE)
            if (u32(dir, p) != SIG_ENTRY) throw ZipFormatException("damaged (central directory)")
            val nameLen = u16(dir, p + 28)
            val extraLen = u16(dir, p + 30)
            val commentLen = u16(dir, p + 32)
            val whole = ENTRY_SIZE + nameLen + extraLen + commentLen
            p = need(whole)
            val madeBy = u16(dir, p + 4)
            val flags = u16(dir, p + 8)
            val mode = (u32(dir, p + 38) ushr 16).toInt()
            var compressed = u32(dir, p + 20)
            var plain = u32(dir, p + 24)
            // The zip64 extra holds, in this order, only the values whose 32-bit field is all ones.
            var x = p + ENTRY_SIZE + nameLen
            val extraEnd = x + extraLen
            while (x + 4 <= extraEnd) {
                val id = u16(dir, x)
                val len = u16(dir, x + 2)
                if (id == 1) {
                    var q = x + 4
                    if (plain == 0xFFFFFFFFL && q + 8 <= extraEnd) { plain = u64(dir, q); q += 8 }
                    if (compressed == 0xFFFFFFFFL && q + 8 <= extraEnd) { compressed = u64(dir, q) }
                }
                x += 4 + len
            }
            if (plain < 0 || compressed < 0) throw ZipFormatException("damaged (entry size)")
            val raw = dir.copyOfRange(p + ENTRY_SIZE, p + ENTRY_SIZE + nameLen)
            out += ZipEntryInfo(
                index = out.size,
                name = if (flags and FLAG_UTF8 != 0) String(raw, Charsets.UTF_8) else Cp437.decode(raw),
                flags = flags,
                method = u16(dir, p + 10),
                crc = u32(dir, p + 16),
                compressedSize = compressed,
                size = plain,
                modifiedMs = dosTime(u16(dir, p + 14), u16(dir, p + 12), zone),
                // Host 3 = Unix; S_IFMT 0170000, S_IFLNK 0120000.
                isSymlink = madeBy ushr 8 == 3 && mode and 0xF000 == 0xA000,
            )
            at += whole
        }
        return out
    }

    private fun dosTime(date: Int, time: Int, zone: ZoneId): Long = runCatching {
        LocalDateTime.of(1980 + (date ushr 9), date ushr 5 and 0xF, date and 0x1F, time ushr 11, time ushr 5 and 0x3F, (time and 0x1F) * 2)
            .atZone(zone).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8)
    private fun u32(b: ByteArray, at: Int): Long = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)
    private fun u64(b: ByteArray, at: Int): Long = u32(b, at) or (u32(b, at + 4) shl 32)
}

/** IBM code page 437, the zip format's name encoding when the UTF-8 flag is clear (T18-2). A table: no charset provider is needed. */
object Cp437 {
    private const val HIGH =
        "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■ "

    fun decode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(if (v < 0x80) v.toChar() else HIGH[v - 0x80])
        }
        return out.toString()
    }
}

/** The entry-name guard (T18-2's Security): an entry is written only under the extract root. */
object ZipNames {
    /**
     * [name] as a path relative to the extract root, or null when it is refused: an absolute path, any `..` segment, a
     * NUL, or nothing at all. Empty and `.` segments are dropped. (The extractor ALSO checks the canonical path of what
     * it is about to write against the root — this is the first of the two.)
     */
    fun normalise(name: String): String? {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\u0000')) return null
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /**
     * Creating an entry's file or folder failed because of its NAME, not because of the volume: `ENAMETOOLONG` (a
     * segment over the volume's limit) or `EINVAL` (a character the volume's filesystem rejects — a backslash, a colon
     * or a trailing dot on FAT). That entry is refused by itself. Anything else — no space, the volume gone, no access
     * — is the extract's failure and is not answered here.
     */
    fun unwritable(e: java.io.IOException): Boolean {
        val why = (e.message.orEmpty() + " " + ((e as? java.nio.file.FileSystemException)?.reason.orEmpty())).lowercase()
        return listOf("enametoolong", "file name too long", "einval", "invalid argument").any { it in why }
    }

    /** A zip by its name: what opens as a folder (and, inside a zip, the one kind of entry that opens — r3 D6). */
    fun isZipName(name: String): Boolean = name.endsWith(".zip", ignoreCase = true) && name.length > 4
}

/** One row of a zip's virtual folder. [entry] is null for a folder the archive only implies (`dir/two.bin` with no `dir/`). */
data class ZipRow(val name: String, val path: String, val isDirectory: Boolean, val size: Long, val modifiedMs: Long, val entry: ZipEntryInfo?)

/** How opening a zip went. */
sealed interface ZipOpen {
    data class Opened(val archive: ZipArchive) : ZipOpen

    /** "This zip is password-protected" — unsupported, stated (T18-2). */
    data object Encrypted : ZipOpen

    /** "This zip can't be opened" (T18-12). */
    data class Failed(val why: String) : ZipOpen
}

/**
 * A zip opened as a folder (T18-2, r3 D6): a virtual root over `java.util.zip.ZipFile` (which reads zip64), listed from
 * Files' own reading of the central directory ([ZipCentral]) — the sizes the rows show, the encrypted bit and the names
 * (UTF-8 flag, else CP437) all come from there. `ZipFile` supplies the entries' data and is paired with the directory
 * by position, checked entry by entry, so no name ever has to survive a round trip through a charset.
 *
 * Read-only; close it when its page is left. Entries inside it do not open or share ("Extract first"); a nested zip is
 * the one exception and goes through [FileOps.zipOpenNested].
 */
class ZipArchive private constructor(
    val file: File,
    private val zip: ZipFile,
    /** Every central-directory entry, in its order. */
    val entries: List<ZipEntryInfo>,
    private val handles: List<ZipEntry>,
) : Closeable {
    private val tree: Map<String, List<ZipRow>> by lazy(::buildTree)

    /** The rows of the virtual folder [dir] (`""` is the root, `"dir"` a folder in it), in the archive's order. */
    fun list(dir: String = ""): List<ZipRow> = tree[dir.trim('/')].orEmpty()

    /** The entry at [path] as a row, or null. */
    fun row(path: String): ZipRow? = path.trim('/').let { p -> list(p.substringBeforeLast('/', "")).firstOrNull { it.path == p } }

    /** [entry]'s data, inflated. It may run past the declared size: the reader counts (the bomb guard). */
    fun input(entry: ZipEntryInfo): InputStream = zip.getInputStream(handles[entry.index]) ?: throw java.util.zip.ZipException("entry not readable")

    override fun close() = zip.close()

    private fun buildTree(): Map<String, List<ZipRow>> {
        val rows = LinkedHashMap<String, LinkedHashMap<String, ZipRow>>()
        fun folder(path: String): LinkedHashMap<String, ZipRow> = rows.getOrPut(path) { LinkedHashMap() }
        folder("")
        for (e in entries) {
            // A refused name has no place under the root, so it has no row either.
            val path = ZipNames.normalise(e.name) ?: continue
            val parts = path.split('/')
            var parent = ""
            for (i in parts.indices) {
                val here = if (parent.isEmpty()) parts[i] else "$parent/${parts[i]}"
                val last = i == parts.lastIndex
                val siblings = folder(parent)
                if (last && !e.isDirectory) {
                    siblings[parts[i]] = ZipRow(parts[i], here, false, e.size, e.modifiedMs, e)
                } else {
                    val had = siblings[parts[i]]
                    if (had == null || !had.isDirectory || (last && had.entry == null)) {
                        siblings[parts[i]] = ZipRow(parts[i], here, true, 0, if (last) e.modifiedMs else 0, if (last) e else null)
                    }
                    folder(here)
                }
                parent = here
            }
        }
        return rows.mapValues { it.value.values.toList() }
    }

    companion object {
        /**
         * Opens [file] as a zip, writing its line through [say]: `zip open <path>: <n> entries` (n = the central
         * directory's count), `zip: encrypted <path>`, or `zip open <path>: failed <why>`.
         *
         * [openZip] is the platform's `ZipFile`; ISO-8859-1 is asked for only so that no name can fail to decode — the
         * names shown are [ZipCentral]'s ([central] is its reader). Whatever either of them throws — an Error too
         * (`OutOfMemoryError` on a hostile file) — is this zip's failure with its line, never the process's (the GATE
         * review's L10).
         */
        fun open(
            file: File,
            say: (String) -> Unit,
            openZip: (File) -> ZipFile = { ZipFile(it, Charsets.ISO_8859_1) },
            central: (File) -> List<ZipEntryInfo> = ZipCentral::read,
        ): ZipOpen {
            val path = FilePaths.lineText(file.path)
            fun failed(why: String): ZipOpen {
                say("zip open $path: failed $why")
                return ZipOpen.Failed(why)
            }
            val entries = try {
                central(file)
            } catch (e: ZipFormatException) {
                return failed(e.message ?: "unreadable")
            } catch (e: Throwable) {
                return failed("unreadable (${e.javaClass.simpleName})")
            }
            if (entries.any { it.encrypted }) {
                say("zip: encrypted $path")
                return ZipOpen.Encrypted
            }
            var zip: ZipFile? = null
            return try {
                zip = openZip(file)
                // No more handles are ever held than Files' own reading of the directory found entries.
                val handles = ArrayList<ZipEntry>(entries.size)
                val all = zip.entries()
                var same = true
                while (same && all.hasMoreElements()) {
                    val handle = all.nextElement()
                    // The pairing by position holds only if the platform read the same directory Files did.
                    val mine = entries.getOrNull(handles.size)
                    same = mine != null && handle.crc == mine.crc && handle.size == mine.size && handle.compressedSize == mine.compressedSize
                    if (same) handles += handle
                }
                if (!same || handles.size != entries.size) {
                    zip.close()
                    failed("unreadable (directory mismatch)")
                } else {
                    say("zip open $path: ${entries.size} entries")
                    ZipOpen.Opened(ZipArchive(file, zip, entries, handles))
                }
            } catch (e: Throwable) {
                runCatching { zip?.close() }
                failed("unreadable (${e.javaClass.simpleName})")
            }
        }
    }
}

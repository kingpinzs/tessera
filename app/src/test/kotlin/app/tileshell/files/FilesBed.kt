package app.tileshell.files

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * The phone as the `files` JVM tests see it: a temp dir holding two volumes (`storage/emulated/0` and a card), the app's
 * private files, and a recording stand-in for every port [FileOps] takes — the lines it wrote, the paths it scanned.
 */
class FilesBed {
    val base: File = Files.createTempDirectory("files-bed").toFile().canonicalFile
    val primary = FileVolume(FilePaths.PRIMARY, File(base, "storage/emulated/0").apply { mkdirs() }.path)
    val card = FileVolume("3842-1609", File(base, "storage/3842-1609").apply { mkdirs() }.path)
    val privateDir = File(base, "data/data/app.tileshell/files").apply { mkdirs() }

    val mounted = mutableListOf(primary, card)
    val lines = mutableListOf<String>()
    val scanned = mutableListOf<String>()
    var now = 1_000L
    var granted = true

    /** The free space the phone reports; null = the temp dir's own. */
    var free: Long? = null

    /** Whether a cross-volume move's delete of the original works. */
    var removeWorks = true

    val canonical: (String) -> String? = { File(it).canonicalPath }
    val recent = RecentStore(privateDir, { now }, { lines += it })

    /** A journal over the same private dir: a second one is "the process after a restart". */
    fun newJournal() = OpsJournal(privateDir, { now }, canonical, { lines += it })
    val journal = newJournal()

    val ops = FileOps(
        volumes = { mounted.toList() },
        canonical = canonical,
        clock = { now },
        scan = { scanned += it },
        say = { lines += it },
        journal = journal,
        recent = recent,
        access = { granted },
        freeSpace = { free ?: it.usableSpace },
        removeSource = { removeWorks && it.delete() },
    )

    val root: File get() = File(primary.root)
    val cardRoot: File get() = File(card.root)

    /** Makes `<volume root>/<relative>` holding [bytes]. */
    fun file(relative: String, bytes: ByteArray = relative.toByteArray(), volume: FileVolume = primary): File =
        File(volume.root, relative).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    fun dir(relative: String, volume: FileVolume = primary): File = File(volume.root, relative).apply { mkdirs() }

    /** Every name in [dir], dot-files included (`ls -a`). */
    fun names(dir: File): List<String> = dir.list()?.sorted().orEmpty()

    fun close() {
        // A test may have taken a folder's write bit away.
        base.walkBottomUp().forEach { it.setWritable(true) }
        base.deleteRecursively()
    }

    companion object {
        fun md5(file: File): String = md5(file.readBytes())
        fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

        /** [n] bytes that are not all alike. */
        fun bytes(n: Int, seed: Int = 7): ByteArray = ByteArray(n) { ((it * 31 + seed) % 251).toByte() }

        /** A file's folder (`parentFile`, which is never null for the paths these tests make). */
        val File.folder: File get() = parentFile!!

        val always: (Conflict) -> (File) -> Conflict = { answer -> { answer } }
        val never: (File) -> Conflict = { throw AssertionError("no conflict was expected, asked about $it") }
    }
}

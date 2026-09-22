package app.tileshell.cortana.speech

import android.content.Context
import android.content.res.AssetManager
import app.tileshell.diag.Diagnostics
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Where the two models live in the APK, and how espeak-ng's data gets out of it.
 *
 * The .onnx / .bin / .zip assets are stored uncompressed (`noCompress` in app/build.gradle.kts), so
 * sherpa-onnx's native asset loader reads the models straight out of the APK and nothing is copied to
 * /data. espeak-ng is the exception: it opens its data by *file path*, so the zip has to be unpacked.
 */
object SpeechAssets {
    const val ASR_ENCODER = "speech/asr/encoder.int8.onnx"
    const val ASR_DECODER = "speech/asr/decoder.int8.onnx"
    const val ASR_JOINER = "speech/asr/joiner.int8.onnx"
    const val ASR_TOKENS = "speech/asr/tokens.txt"

    /**
     * The BPE vocabulary. It is what turns a plain-text hotword — a contact name the shell cannot know
     * in advance — into the tokens the grammar stream boosts, so the grammar pass does not exist without
     * it. The 20M model this phase started on shipped none, which is why the model was swapped.
     *
     * It is the TEXT vocabulary ("<piece> <score>" per line), NOT the sentencepiece `bpe.model` binary:
     * sherpa-onnx parses this file itself and refuses the binary with "Each line in vocab should contain
     * two items". tools/fetch-speech.sh derives it from bpe.model with sentencepiece.
     */
    const val ASR_BPE = "speech/asr/bpe.vocab"

    /** sha256 of bpe.vocab as derived by tools/fetch-speech.sh. */
    const val ASR_BPE_SHA256 = "010f69344848b004b3a9c7fe2456c56b300ce381745cfadea6056ccc3bdf47f0"

    const val TTS_MODEL = "speech/tts/model.int8.onnx"
    const val TTS_VOICES = "speech/tts/voices.bin"
    const val TTS_TOKENS = "speech/tts/tokens.txt"
    const val ESPEAK_ZIP = "speech/tts/espeak-ng-data.zip"

    /** The directory the zip contains at its root, and therefore the name of the extracted directory. */
    const val ESPEAK_DIR_NAME = "espeak-ng-data"

    /**
     * sha256 of app/src/main/assets/speech/tts/espeak-ng-data.zip as built by tools/fetch-speech.sh.
     * Checked before the zip is unpacked and recorded in a stamp next to the extracted directory, so a
     * truncated fetch or a half-written extraction reports [SpeechError.ESPEAK_DATA_BAD] instead of
     * producing a TTS engine that mispronounces everything.
     *
     * This pin is only worth checking while the zip is byte-identical on every machine that builds it,
     * which until 2026-09-22 it was not: the script used `zip -X`, whose DOS timestamps are written in
     * the builder's local timezone, so CI's archive hashed differently from this one and every CI APK
     * shipped with speech dead. fetch-speech.sh now writes the archive with fixed metadata AND verifies
     * it against this constant, so the two can no longer drift apart silently.
     */
    const val ESPEAK_ZIP_SHA256 = "efc829fc33ff93f44e1938b58f7725c8973ccb491c1985226454cad63b1f0e7c"

    /** Everything the recognizer needs, `bpe.model` included: without it there is no grammar pass. */
    val ASR_FILES = listOf(ASR_ENCODER, ASR_DECODER, ASR_JOINER, ASR_TOKENS, ASR_BPE)

    /** The TTS model proper. espeak-ng's data is tracked separately, by [EspeakData]. */
    val TTS_MODEL_FILES = listOf(TTS_MODEL, TTS_VOICES, TTS_TOKENS)

    val TTS_FILES = TTS_MODEL_FILES + ESPEAK_ZIP

    /** `filesDir/speech/v<longVersionCode>`: everything this process has to put on the filesystem. */
    fun versionedDir(context: Context): File = File(context.filesDir, "speech/v${versionCode(context)}")

    fun versionCode(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    } catch (e: Throwable) {
        Diagnostics.add("speech", "version code unavailable ($e); using 0")
        0L
    }

    /**
     * The asset's length in bytes, or -1 when it cannot be read. `openFd` works for the uncompressed
     * assets (the models); the compressed ones fall back to counting the stream.
     */
    fun bytes(assets: AssetManager, path: String): Long {
        try {
            assets.openFd(path).use { return it.declaredLength }
        } catch (_: Throwable) {
            // not stored uncompressed, or not present: fall through
        }
        return try {
            assets.open(path).use { stream ->
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    /** The first asset in [paths] that cannot be opened, or null when they are all present. */
    fun firstMissing(assets: AssetManager, paths: List<String>): String? = paths.firstOrNull { path ->
        try {
            assets.open(path).close()
            false
        } catch (_: IOException) {
            true
        }
    }
}

/**
 * A model could not be made ready. [code] is one of [SpeechError]'s constants, so the service can hand
 * the caller the right message without inspecting the exception.
 */
class SpeechModelException(
    val code: Int,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * The zip-traversal guard, kept pure so it is unit-tested on the host.
 *
 * A zip entry may name anything at all, including `../evil` or a path that walks out through a symlink.
 * Every entry is resolved against the target directory and rejected unless its canonical path is inside it.
 */
object ZipSafety {

    /** The file [entryName] resolves to inside [targetDir], or null when it escapes (reject the entry). */
    fun resolve(targetDir: File, entryName: String): File? {
        if (entryName.isEmpty()) return null
        val root = targetDir.canonicalFile
        val rootPath = root.path
        val candidate = File(root, entryName).canonicalFile
        val path = candidate.path
        val inside = path == rootPath || path.startsWith(rootPath + File.separator)
        return if (inside) candidate else null
    }

    fun isSafe(targetDir: File, entryName: String): Boolean = resolve(targetDir, entryName) != null
}

/** sha256 of a stream, lower-case hex. */
fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    val bytes = digest.digest()
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) out.append(String.format(Locale.ROOT, "%02x", b))
    return out.toString()
}

/**
 * Unpacks `speech/tts/espeak-ng-data.zip` into the app's files directory, once per app version.
 *
 * Layout: `filesDir/speech/v<longVersionCode>/espeak-ng-data/...`, with the zip's sha256 recorded in
 * `filesDir/speech/v<longVersionCode>/espeak-ng-data.sha256`. A missing or different stamp, or an empty
 * extracted directory, re-extracts. The version in the path is what makes an app update re-extract.
 */
object EspeakData {

    private const val STAMP_NAME = "espeak-ng-data.sha256"

    /**
     * @return the extracted `espeak-ng-data` directory, ready to hand to sherpa-onnx as `dataDir`
     * @throws SpeechModelException with [SpeechError.ESPEAK_DATA_BAD] when the zip's hash is wrong or the
     *         extraction did not produce a usable directory. There is no fallback: a broken espeak data
     *         directory makes Kokoro mispronounce silently, so the engine is not built at all.
     */
    fun ensure(context: Context): File {
        val versionCode = SpeechAssets.versionCode(context)
        val base = SpeechAssets.versionedDir(context)
        val dataDir = File(base, SpeechAssets.ESPEAK_DIR_NAME)
        val stamp = File(base, STAMP_NAME)
        val assets = context.assets

        // The checksum is taken BEFORE anything is unpacked, so a bad zip never reaches the filesystem.
        val actual = try {
            assets.open(SpeechAssets.ESPEAK_ZIP).use { sha256(it) }
        } catch (e: IOException) {
            Diagnostics.add("speech", "espeak: ${SpeechAssets.ESPEAK_ZIP} unreadable: $e")
            throw SpeechModelException(SpeechError.ESPEAK_DATA_BAD, "espeak-ng-data.zip unreadable: $e", e)
        }
        if (actual != SpeechAssets.ESPEAK_ZIP_SHA256) {
            Diagnostics.add("speech", "espeak: zip sha256 $actual != expected ${SpeechAssets.ESPEAK_ZIP_SHA256}")
            throw SpeechModelException(
                SpeechError.ESPEAK_DATA_BAD,
                "espeak-ng-data.zip sha256 $actual != expected ${SpeechAssets.ESPEAK_ZIP_SHA256}",
            )
        }
        Diagnostics.add("speech", "espeak: zip sha256 verified $actual")

        val stamped = if (stamp.isFile) runCatching { stamp.readText().trim() }.getOrNull() else null
        val populated = dataDir.isDirectory && (dataDir.list()?.isNotEmpty() == true)
        if (stamped == actual && populated) {
            Diagnostics.add("speech", "espeak: reusing ${dataDir.absolutePath} (stamp matches)")
            return dataDir
        }
        Diagnostics.add(
            "speech",
            "espeak: extracting v$versionCode (stamp=${stamped ?: "none"} populated=$populated)",
        )

        // Only espeak's own directory and stamp are cleared: the versioned directory is shared with the
        // extracted BPE vocabulary, which has its own checksum and its own lifetime.
        dataDir.deleteRecursively()
        stamp.delete()
        if (!base.mkdirs() && !base.isDirectory) {
            throw SpeechModelException(SpeechError.ESPEAK_DATA_BAD, "cannot create ${base.absolutePath}")
        }
        val entries = try {
            assets.open(SpeechAssets.ESPEAK_ZIP).use { extract(it, base) }
        } catch (e: SpeechModelException) {
            throw e
        } catch (e: IOException) {
            Diagnostics.add("speech", "espeak: extraction failed: $e")
            throw SpeechModelException(SpeechError.ESPEAK_DATA_BAD, "espeak extraction failed: $e", e)
        }

        // Verified after the fact as well: an extraction that wrote nothing useful is as bad as a bad zip.
        val files = dataDir.list()
        if (!dataDir.isDirectory || files == null || files.isEmpty()) {
            Diagnostics.add("speech", "espeak: ${dataDir.absolutePath} missing or empty after $entries entries")
            throw SpeechModelException(
                SpeechError.ESPEAK_DATA_BAD,
                "espeak-ng-data missing or empty after extracting $entries entries",
            )
        }
        stamp.writeText(actual)
        val readBack = runCatching { stamp.readText().trim() }.getOrNull()
        if (readBack != actual) {
            Diagnostics.add("speech", "espeak: stamp read back as ${readBack ?: "null"}")
            throw SpeechModelException(SpeechError.ESPEAK_DATA_BAD, "espeak stamp did not persist")
        }
        Diagnostics.add(
            "speech",
            "espeak: extracted $entries entries -> ${dataDir.absolutePath} (${files.size} files)",
        )
        return dataDir
    }

    /** @return how many entries were written */
    private fun extract(input: InputStream, targetDir: File): Int {
        var written = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val destination = ZipSafety.resolve(targetDir, name)
                if (destination == null) {
                    Diagnostics.add("speech", "espeak: rejected traversal entry \"$name\"")
                    throw SpeechModelException(
                        SpeechError.ESPEAK_DATA_BAD,
                        "zip entry \"$name\" escapes ${targetDir.absolutePath}",
                    )
                }
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { out -> zip.copyTo(out, 64 * 1024) }
                    written++
                }
                zip.closeEntry()
            }
        }
        return written
    }
}

/**
 * Copies one asset into the app's files directory, once per app version, under the same checksum
 * discipline as [EspeakData].
 *
 * Only `bpe.model` needs this, and only if sherpa-onnx turns out to want a filesystem path for
 * `bpeVocab` — see [SherpaAsr.load], which tries the asset path first.
 */
object ExtractedAsset {

    /**
     * @return the extracted file
     * @throws SpeechModelException [SpeechError.MODEL_CORRUPT] when the asset's sha256 is not [expectedSha256]
     *         or the copy does not survive a read-back
     */
    fun ensure(context: Context, assetPath: String, fileName: String, expectedSha256: String): File {
        val base = SpeechAssets.versionedDir(context)
        val target = File(base, fileName)
        val stamp = File(base, "$fileName.sha256")
        val assets = context.assets

        val actual = try {
            assets.open(assetPath).use { sha256(it) }
        } catch (e: IOException) {
            throw SpeechModelException(SpeechError.MODEL_MISSING, "$assetPath unreadable: $e", e)
        }
        if (actual != expectedSha256) {
            Diagnostics.add("speech", "extract: $assetPath sha256 $actual != expected $expectedSha256")
            throw SpeechModelException(
                SpeechError.MODEL_CORRUPT,
                "$assetPath sha256 $actual != expected $expectedSha256",
            )
        }

        val stamped = if (stamp.isFile) runCatching { stamp.readText().trim() }.getOrNull() else null
        if (stamped == actual && target.isFile && target.length() > 0) {
            Diagnostics.add("speech", "extract: reusing ${target.absolutePath}")
            return target
        }

        base.mkdirs()
        target.delete()
        try {
            assets.open(assetPath).use { input ->
                target.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            }
        } catch (e: IOException) {
            throw SpeechModelException(SpeechError.MODEL_CORRUPT, "copying $assetPath failed: $e", e)
        }
        val written = runCatching { target.inputStream().use { sha256(it) } }.getOrNull()
        if (written != actual) {
            Diagnostics.add("speech", "extract: ${target.absolutePath} read back as ${written ?: "null"}")
            throw SpeechModelException(SpeechError.MODEL_CORRUPT, "$fileName did not survive extraction")
        }
        stamp.writeText(actual)
        Diagnostics.add("speech", "extract: wrote ${target.absolutePath} (${target.length()} bytes, sha256 verified)")
        return target
    }
}

/**
 * A one-line trail on disk, written before each native call that could take the process down with it.
 *
 * The speech process has its own [Diagnostics] ring, and a native abort inside onnxruntime takes that
 * ring with it — the launcher's dump then shows only "process gone" and nothing about WHY. This file
 * survives, so the next start (and any QA driver) can read exactly which model was being constructed
 * when the process died. It is the same rule the rest of the shell follows: nothing that matters is
 * left only in a log the next run cannot read.
 */
object SpeechBreadcrumb {

    private const val NAME = "speech-breadcrumb.txt"

    @Volatile private var file: File? = null

    fun attach(context: Context) {
        file = File(context.filesDir, NAME)
    }

    /** What is about to happen. Cleared by [done] when it survives. */
    fun enter(step: String) {
        write("ENTER $step at ${System.currentTimeMillis()}")
        Diagnostics.add("speech", "breadcrumb: $step")
    }

    fun done(step: String) {
        write("DONE  $step at ${System.currentTimeMillis()}")
    }

    /** What the previous run was doing when it stopped, or null when it finished cleanly. */
    fun previous(): String? = runCatching {
        file?.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.startsWith("ENTER") }
    }.getOrNull()

    fun read(): String = runCatching { file?.takeIf { it.exists() }?.readText()?.trim() }.getOrNull() ?: "(none)"

    private fun write(line: String) {
        val target = file ?: return
        runCatching { target.writeText(line + "\n") }
    }
}

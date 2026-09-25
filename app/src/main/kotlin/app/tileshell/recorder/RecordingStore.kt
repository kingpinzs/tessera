package app.tileshell.recorder

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.tileshell.diag.Diagnostics

/**
 * Recordings in MediaStore (phase 15 Q3 A): `.m4a` in the shared `Recordings/` folder, marked `IS_RECORDING`,
 * owned by the shell — so other apps, a PC over USB and file managers see them, and they outlive the shell.
 *
 * Both processes use this: `:recorder` publishes a take, the page lists, renames and deletes. It decides
 * nothing: which rows may be edited is [RecordingCaps], and every edit here is refused unless the row is the
 * shell's own — the T15-3 write guard is checked again at the MediaStore call, not only in the page, so no
 * path can raise Android's consent dialog for another app's file (T15-33 (e)).
 */
object RecordingStore {

    /** Every external volume: a recording on an SD card is still a recording. */
    val COLLECTION: Uri get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /** Where a take is written: the primary volume, as `Recordings/` there is the phone's folder. */
    private val PRIMARY: Uri get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** `Recordings/` — Environment.DIRECTORY_RECORDINGS, the folder MediaStore marks IS_RECORDING (API 31+). */
    val RELATIVE_PATH: String get() = Environment.DIRECTORY_RECORDINGS + "/"

    private val PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.OWNER_PACKAGE_NAME,
        MediaStore.Audio.Media.RELATIVE_PATH,
        MediaStore.Audio.Media.SIZE,
    )

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(COLLECTION, id)

    /** Whether other apps' recordings can be listed at all (T15-19): without it MediaStore returns only the shell's own. */
    fun canSeeOthers(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Every row MediaStore marks `IS_RECORDING`, whoever made it (Q2 A); the recorded-at time comes from
     * the shell's own [RecordingMetaStore] where it has one. Pending and trashed rows are left out by
     * MediaStore itself.
     */
    fun query(context: Context): List<Recording> = runCatching {
        val meta = RecordingMetaStore.readAll(context)
        val out = ArrayList<Recording>()
        context.contentResolver.query(COLLECTION, PROJECTION, "${MediaStore.Audio.Media.IS_RECORDING} = 1", null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val owner = c.getColumnIndexOrThrow(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
            val path = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val own = meta[rowId]
                val scanned = if (c.isNull(duration)) 0L else c.getLong(duration)
                out += Recording(
                    id = rowId,
                    displayName = c.getString(name) ?: "",
                    // The scanner fills DURATION a moment after a publish; until it does, the take's own count stands.
                    durationMs = if (scanned > 0L) scanned else own?.durationMs ?: 0L,
                    // DATE_ADDED is seconds since the epoch, unlike almost everything else on Android.
                    recordedAtMs = own?.recordedAtMs ?: c.getLong(added) * 1000L,
                    ownerPackage = if (c.isNull(owner)) null else c.getString(owner),
                    relativePath = c.getString(path),
                    sizeBytes = c.getLong(size),
                )
            }
        }
        out
    }.onFailure { Diagnostics.add("recorder", "list query failed: $it") }.getOrDefault(emptyList())

    /** The display names already in `Recordings/` (every one this process can see), for the next take's name. */
    fun namesInRecordings(context: Context): List<String> = runCatching {
        val out = ArrayList<String>()
        context.contentResolver.query(
            COLLECTION,
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
            arrayOf(RELATIVE_PATH),
            null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out += it } }
        out
    }.onFailure { Diagnostics.add("recorder", "name query failed: $it") }.getOrDefault(emptyList())

    /**
     * A new, PENDING `.m4a` in `Recordings/`: invisible to other apps until [publish], and written through the
     * descriptor MediaStore opens. MediaProvider fixes the final name at publish, appending " (1)" if another
     * file took it meanwhile.
     */
    fun createPending(context: Context, name: String): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, RecordingNames.displayName(name))
            put(MediaStore.Audio.Media.MIME_TYPE, RecorderAudio.FILE_MIME)
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        context.contentResolver.insert(PRIMARY, values)
    }.onFailure { Diagnostics.add("recorder", "could not create a file for $name: $it") }.getOrNull()

    /** Make a pending file visible; MediaStore scans it (duration, IS_RECORDING) as it does. */
    fun publish(context: Context, uri: Uri): Boolean = runCatching {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null) > 0
    }.onFailure { Diagnostics.add("recorder", "publish of $uri failed: $it") }.getOrDefault(false)

    /** Throw away a pending file that never got its audio (a take with no whole frame, a failed mux). */
    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
            .onFailure { Diagnostics.add("recorder", "discard of $uri failed: $it") }
    }

    /** What MediaStore says about one row now: its display name, size and whether it is marked a recording. */
    data class Row(val id: Long, val displayName: String, val sizeBytes: Long, val isRecording: Boolean, val ownerPackage: String?)

    fun row(context: Context, uri: Uri): Row? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.IS_RECORDING,
                MediaStore.Audio.Media.OWNER_PACKAGE_NAME,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) null
            else Row(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getInt(3) == 1, if (c.isNull(4)) null else c.getString(4))
        }
    }.onFailure { Diagnostics.add("recorder", "row query of $uri failed: $it") }.getOrNull()

    /**
     * Rename an OWN recording to [name] (`<name>.m4a`). A clash is MediaProvider's to resolve (" (1)").
     * @return the display name MediaStore now holds, or null when refused or failed
     */
    fun rename(context: Context, id: Long, name: String): String? {
        if (!guard(context, id, "rename")) return null
        val uri = uriOf(id)
        return runCatching {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, RecordingNames.displayName(name)) }
            context.contentResolver.update(uri, values, null, null)
            row(context, uri)?.displayName
        }.onFailure { Diagnostics.add("recorder", "rename of $id failed: $it") }.getOrNull()
    }

    /** Delete an OWN recording: the MediaStore row and the file go together. */
    fun delete(context: Context, id: Long): Boolean {
        if (!guard(context, id, "delete")) return false
        return runCatching { context.contentResolver.delete(uriOf(id), null, null) > 0 }
            .onFailure { Diagnostics.add("recorder", "delete of $id failed: $it") }
            .getOrDefault(false)
    }

    /**
     * The T15-3 write guard at the point of writing: only a row MediaStore says the shell owns, re-read now
     * rather than trusted from a list that may be stale (an uninstall-and-reinstall orphans a take while it is
     * on screen, T15-26).
     */
    fun guard(context: Context, id: Long, what: String): Boolean {
        val owner = row(context, uriOf(id))?.ownerPackage
        if (!RecordingCaps.of(owner, context.packageName).delete) {
            Diagnostics.add("recorder", "$what refused for $id: owner ${owner ?: "none"} is not the shell")
            return false
        }
        return true
    }
}

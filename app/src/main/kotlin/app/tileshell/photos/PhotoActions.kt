package app.tileshell.photos

import android.app.Activity
import android.app.WallpaperManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import app.tileshell.diag.Diagnostics
import app.tileshell.media.AndroidMediaStorePort
import app.tileshell.media.MediaWrites
import app.tileshell.prefs.ShellSettings
import app.tileshell.ui.tokens.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The viewer's actions (build task 5): share, delete, set as. Each failure has its line — nothing here ends silently.
 */
object PhotoActions {
    /** The shell's one MediaStore write layer (r3 D7), as Photos' main process uses it: the delete consent. */
    fun writes(context: Context): MediaWrites<IntentSender> = MediaWrites(AndroidMediaStorePort(context), context.packageName)

    /**
     * Share: `ACTION_SEND` with the content URI and a read grant, through the system chooser. The ClipData carries the
     * same URI, which is what the grant flag is applied to.
     */
    fun share(activity: Activity, uri: Uri, mime: String?, id: String) {
        val send = Intent(Intent.ACTION_SEND).setType(mime?.takeIf { it.isNotEmpty() } ?: "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        runCatching { activity.startActivity(Intent.createChooser(send, null)) }
            .onSuccess { Diagnostics.add("photosapp", "share $id -> chooser") }
            .onFailure { Diagnostics.add("photosapp", "share $id failed: ${it.javaClass.simpleName}") }
    }

    /** The system's consent to delete [uri] (`MediaStore.createDeleteRequest` through [MediaWrites]); null when it cannot be asked. */
    fun deleteConsent(context: Context, uri: Uri, id: String): IntentSender? =
        runCatching { writes(context).deleteRequest(listOf(uri.toString())) }
            .onFailure { Diagnostics.add("photosapp", "delete $id failed: ${it.javaClass.simpleName}") }
            .getOrNull()

    /**
     * Set as Start background (r3 D9): a MediaStore URI Photos found by its own query has no grant to persist, so the
     * picture is decoded at screen size and COPIED to `files/backgrounds/<id>.jpg`, and the theme's background points
     * at that file — it survives a revoked or partial photo grant. Returns the failure, or null.
     */
    suspend fun setAsBackground(context: Context, uri: Uri, id: Long): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val relative = "files/backgrounds/$id.jpg"
        val result = runCatching {
            val shown = PhotoDecode.screen(app, uri, Scale.portraitWidthPx(app), Scale.portraitHeightPx(app), cover = true, software = true).getOrThrow()
            val dir = File(app.filesDir, "backgrounds").apply { mkdirs() }
            val file = File(dir, "$id.jpg")
            val temp = File(dir, "$id.jpg.tmp")
            temp.outputStream().use { check(shown.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "could not be written" } }
            check(temp.renameTo(file)) { "could not be renamed" }
            file
        }
        result.fold(
            onSuccess = { file ->
                // The theme store's own setter (phase 01 / 12): nothing is added to it.
                ShellSettings.get(app).update { it.copy(backgroundUri = Uri.fromFile(file).toString()) }
                Diagnostics.add("photosapp", "set as background $id -> $relative")
                null
            },
            onFailure = { e ->
                val why = e.message?.takeIf { it.length < 60 } ?: e.javaClass.simpleName
                Diagnostics.add("photosapp", "set as background $id failed: $why")
                why
            },
        )
    }

    /** Set as lock screen: Android's lock wallpaper (`WallpaperManager`, FLAG_LOCK; the install-time SET_WALLPAPER). */
    suspend fun setAsLockScreen(context: Context, uri: Uri, id: Long): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val result = runCatching {
            val shown = PhotoDecode.screen(app, uri, Scale.portraitWidthPx(app), Scale.portraitHeightPx(app), cover = true, software = true).getOrThrow()
            val wallpapers = WallpaperManager.getInstance(app)
            val wallpaperId = wallpapers.setBitmap(shown.bitmap, null, true, WallpaperManager.FLAG_LOCK)
            check(wallpaperId != 0) { "the system refused it" }
            wallpaperId
        }
        result.fold(
            onSuccess = { Diagnostics.add("photosapp", "set as lock screen $id -> wallpaper $it"); null },
            onFailure = { e ->
                val why = e.message?.takeIf { it.length < 60 } ?: e.javaClass.simpleName
                Diagnostics.add("photosapp", "set as lock screen $id failed: $why")
                why
            },
        )
    }

    /** A size as File information shows it. */
    fun sizeText(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes bytes"
    }
}

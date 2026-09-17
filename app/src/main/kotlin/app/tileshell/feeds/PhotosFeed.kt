package app.tileshell.feeds

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Photos tile: cycles the newest images from MediaStore with a crossfade (R3 A7 image tiles, R3 C3 Photos:
 * full-bleed photo, no text). Works with full or partial ("Select photos") media access.
 */
object PhotosFeed {
    private const val MAX_PHOTOS = 8
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null

    enum class Access { GRANTED, PARTIAL, DENIED }

    fun access(context: Context): Access = when {
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED -> Access.GRANTED
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED -> Access.PARTIAL
        else -> Access.DENIED
    }

    fun start(context: Context) {
        val app = context.applicationContext
        if (observer == null) {
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(app, "mediastore change")
            }.also { app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, it) }
        }
        refresh(app, "start")
    }

    fun refresh(context: Context, reason: String) {
        scope.launch {
            val access = access(context)
            if (access == Access.DENIED) {
                LiveTileEngine.publish(LiveTileEngine.PHOTOS, null)
                Diagnostics.add("photos", "no media access ($reason)")
                return@launch
            }
            val faces = mutableListOf<TileFace>()
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
            runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (c.moveToNext() && faces.size < MAX_PHOTOS) {
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
                        val thumb: Bitmap? = runCatching { context.contentResolver.loadThumbnail(uri, Size(640, 640), null) }.getOrNull()
                        if (thumb != null) faces += TileFace.Photo(thumb.asImageBitmap())
                    }
                }
            }.onFailure { Diagnostics.add("photos", "query failed: $it") }
            LiveTileEngine.publish(
                LiveTileEngine.PHOTOS,
                if (faces.isEmpty()) null else TileContent(faces, FaceTransition.CROSSFADE, System.currentTimeMillis(), "photos"),
            )
            Diagnostics.add("photos", "refresh ($reason): access=$access photos=${faces.size}")
        }
    }
}

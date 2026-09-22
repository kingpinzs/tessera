package app.tileshell.feeds

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import app.tileshell.prefs.ShellSettings
import app.tileshell.tiles.ActiveTiles
import app.tileshell.tiles.Slot
import app.tileshell.tiles.TileKey
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Photos tile: cycles the newest images from MediaStore with a crossfade (R3 A7 image tiles, R3 C3 Photos:
 * full-bleed photo, no text). Works with full or partial ("Select photos") media access.
 *
 * INDEX Change Log 2026-09-21 item 4 adds two modes on top, both ruled by [PhotoRules]: a SLIDESHOW (more
 * photos, a fixed 3-second cadence, and the tile drawn one size bigger while it runs) and a PICTURE FRAME
 * (one chosen photo that replaces the logo, so the tile cannot flip at all). Both are settings, and both
 * are undone by turning them off — nothing about them touches the stored layout.
 */
object PhotosFeed {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var watchingSettings = false

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
        if (!watchingSettings) {
            watchingSettings = true
            // Turning the slideshow on, or choosing a main photo, has to reach the tile at once: nothing
            // else would change until the next photo was taken. Only these two fields are watched, so
            // changing the accent colour does not re-read the gallery.
            scope.launch {
                ShellSettings.get(app).theme
                    .map { it.photosSlideshow to it.photoFrameUri }
                    .distinctUntilChanged()
                    // The first emission is what the settings ALREADY say, and the refresh below covers
                    // that; without this the tile would read the gallery twice on every process start.
                    .drop(1)
                    .collect { refresh(app, "photos tile setting changed") }
            }
        }
        refresh(app, "start")
    }

    fun refresh(context: Context, reason: String) {
        scope.launch {
            val settings = ShellSettings.get(context).theme.value

            // The frame comes FIRST, and before the media-access gate, because it is read through the
            // persisted grant the picker gave the shell rather than through gallery access: a main photo
            // keeps showing even when the Photos permission is denied or has been revoked.
            val frame = settings.photoFrameUri?.let { loadFrame(context, it) }
            if (settings.photoFrameUri != null && frame == null) {
                Diagnostics.add("photos", "main photo unreadable, falling back to the normal tile ($reason)")
            }
            val framePlan = PhotoRules.plan(frameSet = frame != null, slideshow = settings.photosSlideshow, photos = 0)
            if (frame != null && framePlan.frame) {
                ActiveTiles.set(PHOTOS_TILE, framePlan.grow, "picture frame")
                LiveTileEngine.publish(
                    LiveTileEngine.PHOTOS,
                    TileContent(
                        faces = emptyList(),
                        transition = FaceTransition.CROSSFADE,
                        sourceTimeMs = System.currentTimeMillis(),
                        sourceTag = "photos:frame",
                        front = frame,
                    ),
                )
                Diagnostics.add("photos", "picture frame ($reason): the tile shows one photo and does not flip")
                return@launch
            }

            val access = access(context)
            if (access == Access.DENIED) {
                clear("no media access ($reason)")
                return@launch
            }
            val wanted = PhotoRules.photosToRead(frameSet = false, slideshow = settings.photosSlideshow)
            val photos = readPhotos(context, wanted)
            val plan = PhotoRules.plan(frameSet = false, slideshow = settings.photosSlideshow, photos = photos.size)
            ActiveTiles.set(PHOTOS_TILE, plan.grow, if (plan.grow) "slideshow running" else "slideshow stopped")

            val faces = photos.take(plan.faces)
            LiveTileEngine.publish(
                LiveTileEngine.PHOTOS,
                if (faces.isEmpty()) null
                else TileContent(
                    faces = faces,
                    transition = FaceTransition.CROSSFADE,
                    sourceTimeMs = System.currentTimeMillis(),
                    sourceTag = "photos",
                    slideshowMs = plan.slideshowMs,
                ),
            )
            Diagnostics.add(
                "photos",
                "refresh ($reason): access=$access photos=${faces.size} slideshow=${plan.slideshowMs} grow=${plan.grow}",
            )
        }
    }

    /** The newest [limit] images as full-bleed faces. */
    private fun readPhotos(context: Context, limit: Int): List<TileFace> {
        val faces = mutableListOf<TileFace>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && faces.size < limit) {
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol))
                    val thumb: Bitmap? = runCatching { context.contentResolver.loadThumbnail(uri, Size(640, 640), null) }.getOrNull()
                    if (thumb != null) faces += TileFace.Photo(thumb.asImageBitmap())
                }
            }
        }.onFailure { Diagnostics.add("photos", "query failed: $it") }
        return faces
    }

    /**
     * The chosen photo, decoded at about tile size.
     *
     * It goes through the content resolver rather than MediaStore, because the picker hands back a URI
     * the shell holds a persisted read grant on — the same mechanism the Start background already uses —
     * so a main photo keeps working whether or not the shell has gallery access at all.
     */
    private fun loadFrame(context: Context, uri: String): TileFace? = runCatching {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        // A tile is never more than the screen wide; 1024 px of source is more than a wide tile can show.
        while (bounds.outWidth / sample > FRAME_TARGET_PX * 2) sample *= 2
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.let { TileFace.Photo(it.asImageBitmap()) }
    }.getOrNull()

    private fun clear(reason: String) {
        ActiveTiles.set(PHOTOS_TILE, false, reason)
        LiveTileEngine.publish(LiveTileEngine.PHOTOS, null)
        Diagnostics.add("photos", reason)
    }

    private const val FRAME_TARGET_PX = 1024

    /** The tile that grows while a slideshow runs; like Music, it is one slot tile. */
    private val PHOTOS_TILE: TileKey = TileKey.SlotTile(Slot.PHOTOS)
}

package app.tileshell.video

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import java.util.Locale

/** One video of the phone as My videos lists it. */
data class VideoRow(val id: Long, val displayName: String, val relativePath: String)

/** A folder's videos under its header (r11/movies-tv.md 1.4.1: "browse by folders"). */
data class VideoGroup(val path: String, val name: String, val items: List<VideoRow>) {
    /** The group's ids in the order shown: what Autoplay walks. */
    val queue: LongArray get() = LongArray(items.size) { items[it].id }
}

/** The My videos page's rules, free of Android types (build task 7; Y5). */
object VideoGroups {
    const val TILE = 112f
    const val PITCH = 124f
    const val MARGIN = 12f

    /** The header of videos that sit in no folder (the root of shared storage). */
    const val ROOT_NAME = "Videos"

    /** 1.4.8: the caption is the file name without its extension. A name that is only an extension is kept whole. */
    fun caption(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        return if (dot > 0) displayName.substring(0, dot) else displayName
    }

    /** The last folder of a MediaStore RELATIVE_PATH ("Movies/Trip/" → "Trip"). */
    fun folderName(relativePath: String): String =
        relativePath.trim('/').substringAfterLast('/').ifEmpty { ROOT_NAME }

    /** A group per folder, folders by name and then path, each folder's videos by caption (ties by id). */
    fun group(rows: List<VideoRow>): List<VideoGroup> = rows
        .groupBy { it.relativePath.trim('/') }
        .map { (path, items) ->
            VideoGroup(path, folderName(path), items.sortedWith(compareBy<VideoRow> { caption(it.displayName).lowercase(Locale.ROOT) }.thenBy { it.id }))
        }
        .sortedWith(compareBy<VideoGroup> { it.name.lowercase(Locale.ROOT) }.thenBy { it.path })

    /** 1.4.4: as many fixed 112-epx tiles on the 124 pitch as fit inside the 12-epx margins — 2 at 360 epx, 3 at 411. */
    fun columns(canvasWidth: Float): Int = (((canvasWidth - 2 * MARGIN + (PITCH - TILE)) / PITCH).toInt()).coerceAtLeast(1)
}

/** The phone's videos, read from MediaStore under READ_MEDIA_VIDEO. */
object VideoLibrary {
    fun hasAccess(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

    /** Every video row MediaStore lets the shell see. Blocking: call it off the main thread. */
    fun load(context: Context): List<VideoRow> {
        val out = ArrayList<VideoRow>()
        val columns = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.RELATIVE_PATH)
        context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) out += VideoRow(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())
        }
        return out
    }
}

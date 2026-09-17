package app.tileshell.start

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.apps.AppEntry

/**
 * Tile glyphs. W10M tiles show a flat white glyph on the accent. The closest Android source is the
 * adaptive icon's monochrome layer (themed icons, API 33+), tinted white; apps without one show their
 * full-colour icon (agent decision, P4; recorded in the INDEX change log with a NEEDS-HUMAN row).
 */
object TileIcons {
    data class Icon(val bitmap: ImageBitmap, val monochrome: Boolean)

    private val cache = HashMap<String, Icon?>()

    fun load(context: Context, entry: AppEntry, sizePx: Int): Icon? = synchronized(cache) {
        cache.getOrPut("${entry.key}@$sizePx") {
            val la = context.getSystemService(LauncherApps::class.java)
            val info = runCatching { la.getActivityList(entry.component.packageName, entry.user).firstOrNull { it.componentName == entry.component } }.getOrNull()
                ?: return@getOrPut null
            val drawable = info.getIcon(0) ?: return@getOrPut null
            val mono = (drawable as? AdaptiveIconDrawable)?.monochrome
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            if (mono != null) {
                // Monochrome layers are drawn inside the adaptive icon's 108-unit canvas; crop to the central 72 units.
                val inset = (sizePx * 0.25f).toInt()
                mono.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
                mono.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
                mono.draw(canvas)
                Icon(bmp.asImageBitmap(), true)
            } else {
                drawable.setBounds(0, 0, sizePx, sizePx)
                drawable.draw(canvas)
                Icon(bmp.asImageBitmap(), false)
            }
        }
    }

    fun clear() = synchronized(cache) { cache.clear() }
}

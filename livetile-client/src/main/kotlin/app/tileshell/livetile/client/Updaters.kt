package app.tileshell.livetile.client

import android.content.Context
import android.net.Uri
import android.os.Bundle

/** Windows TileUpdater for the app's own (primary) tile. The shell decides the owner from the caller's identity. */
class TileUpdater private constructor(private val context: Context) {

    /** TileUpdater.Update: shows [content]; with the queue enabled it joins the 5-deep queue ([tag] replaces a match). */
    fun update(content: TileContent, tag: String? = null, expiresAtMs: Long? = null): LiveTileResult =
        update(content.toXml(), content.imageUris(), tag, expiresAtMs)

    /** Raw adaptive-tile XML. content:// image sources are granted to the shell for the duration of the call. */
    fun update(xml: String, images: Collection<Uri> = emptyList(), tag: String? = null, expiresAtMs: Long? = null): LiveTileResult {
        val extras = Bundle().apply {
            putString("xml", xml)
            tag?.let { putString("tag", it) }
            expiresAtMs?.let { putLong("expiresAt", it) }
        }
        return LiveTile.call(context, "tile.update", extras, images.toSet() + LiveTile.contentSources(xml))
    }

    /** TileUpdater.Clear: empties the queue; scheduled notifications stay scheduled. */
    fun clear(): LiveTileResult = LiveTile.call(context, "tile.clear", Bundle())

    /** TileUpdater.EnableNotificationQueue (one flag for all phone sizes). */
    fun enableNotificationQueue(enabled: Boolean): LiveTileResult =
        LiveTile.call(context, "tile.enableQueue", Bundle().apply { putBoolean("enabled", enabled) })

    /** TileUpdater.AddToSchedule: [id] is 1-16 alphanumeric characters; the same id replaces an earlier schedule. */
    fun schedule(id: String, deliveryAtMs: Long, content: TileContent, tag: String? = null, expiresAtMs: Long? = null): LiveTileResult =
        schedule(id, deliveryAtMs, content.toXml(), content.imageUris(), tag, expiresAtMs)

    fun schedule(id: String, deliveryAtMs: Long, xml: String, images: Collection<Uri> = emptyList(), tag: String? = null, expiresAtMs: Long? = null): LiveTileResult {
        val extras = Bundle().apply {
            putString("id", id)
            putLong("deliveryAt", deliveryAtMs)
            putString("xml", xml)
            tag?.let { putString("tag", it) }
            expiresAtMs?.let { putLong("expiresAt", it) }
        }
        return LiveTile.call(context, "tile.schedule", extras, images.toSet() + LiveTile.contentSources(xml))
    }

    /** TileUpdater.Setting. */
    fun setting(): TileSetting {
        val r = LiveTile.call(context, "tile.setting", Bundle())
        if (!r.ok) return TileSetting.UNAVAILABLE
        return TileSetting.entries.firstOrNull { it.name == r.setting } ?: TileSetting.UNAVAILABLE
    }

    companion object {
        @JvmStatic
        fun forApplication(context: Context): TileUpdater = TileUpdater(context.applicationContext ?: context)
    }
}

/** Windows BadgeUpdater for the app's own tile: 1-99 as digits, 100+ as 99+, 0 clears. */
class BadgeUpdater private constructor(private val context: Context) {

    fun update(count: Int, expiresAtMs: Long? = null): LiveTileResult =
        LiveTile.call(context, "badge.update", Bundle().apply {
            putInt("value", count)
            expiresAtMs?.let { putLong("expiresAt", it) }
        })

    fun update(glyph: BadgeGlyph, expiresAtMs: Long? = null): LiveTileResult =
        LiveTile.call(context, "badge.update", Bundle().apply {
            putString("value", glyph.value)
            expiresAtMs?.let { putLong("expiresAt", it) }
        })

    fun clear(): LiveTileResult = LiveTile.call(context, "badge.clear", Bundle())

    companion object {
        @JvmStatic
        fun forApplication(context: Context): BadgeUpdater = BadgeUpdater(context.applicationContext ?: context)
    }
}

/** The Windows badge glyph set. The shell answers "glyph-unsupported" for glyphs it does not render yet. */
enum class BadgeGlyph(val value: String) {
    NONE("none"), ACTIVITY("activity"), ALARM("alarm"), ALERT("alert"), ATTENTION("attention"), AVAILABLE("available"),
    AWAY("away"), BUSY("busy"), ERROR("error"), NEW_MESSAGE("newMessage"), PAUSED("paused"), PLAYING("playing"),
    UNAVAILABLE("unavailable"),
}

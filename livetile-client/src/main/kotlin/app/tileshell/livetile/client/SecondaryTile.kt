package app.tileshell.livetile.client

import android.content.Context
import android.net.Uri
import android.os.Bundle

/** The size a secondary tile asks to be pinned at. Windows' TileSize, minus the sizes W10M phones never drew. */
enum class TileSize(val value: String) { SMALL("small"), MEDIUM("medium"), WIDE("wide") }

/**
 * One of this app's secondary tiles (Windows' `SecondaryTile`), reached with
 * `LiveTile.forSecondaryTile(context, tileId)`.
 *
 * ```
 * val tile = LiveTile.forSecondaryTile(context, "chat.42")
 * // Only the user can pin a tile: this ASKS, and the shell confirms with the user on Start.
 * tile.requestCreate(displayName = "Jen", arguments = "chat=42", logo = avatarUri, size = TileSize.MEDIUM)
 * // …later, once tile.exists() is true:
 * tile.update(tileContent { medium { text("Jen"); text("Lunch?") } })
 * tile.badge(3)
 * ```
 *
 * Tapping the tile starts this app's launcher activity with `LiveTile.tileId(intent)`,
 * `LiveTile.arguments(intent)` and `LiveTile.activatedArguments(intent)` filled in.
 *
 * Every call is synchronous and returns [LiveTileResult.UNAVAILABLE] when no tile shell is installed.
 */
class SecondaryTile internal constructor(private val context: Context, val tileId: String) {

    /** The tile / queue verbs, addressed to this tile. */
    val tiles: TileUpdater = TileUpdater.forSecondaryTile(context, tileId)

    /** The badge verbs, addressed to this tile. */
    val badges: BadgeUpdater = BadgeUpdater.forSecondaryTile(context, tileId)

    /**
     * Windows' `RequestCreateAsync`: asks the shell to pin this tile. The user confirms it on Start, so a successful
     * result with [LiveTileResult.pending] true means "asked", not "pinned" — poll [exists] or watch
     * [LiveTile.changeUri]. Asking again for a tileId that is already pinned UPDATES it instead, with no dialog
     * (the result then carries pending = false).
     *
     * [logo] must be a content:// URI from this app's own provider (grantUriPermissions="true") or
     * android.resource://<this package>/…; the shell copies it during the call.
     */
    fun requestCreate(
        displayName: String,
        arguments: String = "",
        logo: Uri? = null,
        size: TileSize = TileSize.MEDIUM,
        showName: Boolean = false,
    ): LiveTileResult = LiveTile.call(context, "secondary.requestCreate", fields(displayName, arguments, logo, size, showName), listOfNotNull(logo))

    /**
     * Windows' `UpdateAsync` — "Assign ALL properties, including ones you aren't changing". The tile must already be
     * pinned; if it is not, the result carries error "not-found".
     */
    fun update(
        displayName: String,
        arguments: String = "",
        logo: Uri? = null,
        size: TileSize = TileSize.MEDIUM,
        showName: Boolean = false,
    ): LiveTileResult = LiveTile.call(context, "secondary.update", fields(displayName, arguments, logo, size, showName), listOfNotNull(logo))

    /** Windows' `RequestDeleteAsync`: unpins this tile and drops everything stored under it. */
    fun requestDelete(): LiveTileResult = LiveTile.call(context, "secondary.requestDelete", idOnly())

    /** Windows' `SecondaryTile.Exists`: true when this tile is pinned to Start right now. */
    fun exists(): Boolean = LiveTile.call(context, "secondary.exists", idOnly()).exists == true

    // ---- the tile and badge verbs, routed to this tile ----

    fun update(content: TileContent, tag: String? = null, expiresAtMs: Long? = null): LiveTileResult =
        tiles.update(content, tag, expiresAtMs)

    fun update(xml: String, images: Collection<Uri> = emptyList(), tag: String? = null, expiresAtMs: Long? = null): LiveTileResult =
        tiles.update(xml, images, tag, expiresAtMs)

    fun clear(): LiveTileResult = tiles.clear()

    fun enableNotificationQueue(enabled: Boolean): LiveTileResult = tiles.enableNotificationQueue(enabled)

    fun schedule(id: String, deliveryAtMs: Long, content: TileContent, tag: String? = null, expiresAtMs: Long? = null): LiveTileResult =
        tiles.schedule(id, deliveryAtMs, content, tag, expiresAtMs)

    fun badge(count: Int, expiresAtMs: Long? = null): LiveTileResult = badges.update(count, expiresAtMs)

    fun badge(glyph: BadgeGlyph, expiresAtMs: Long? = null): LiveTileResult = badges.update(glyph, expiresAtMs)

    fun clearBadge(): LiveTileResult = badges.clear()

    fun setting(): TileSetting = tiles.setting()

    private fun idOnly() = Bundle().apply { putString("tileId", tileId) }

    private fun fields(displayName: String, arguments: String, logo: Uri?, size: TileSize, showName: Boolean) = idOnly().apply {
        putString("displayName", displayName)
        putString("arguments", arguments)
        logo?.let { putString("logo", it.toString()) }
        putString("size", size.value)
        putBoolean("showName", showName)
    }

    companion object {
        /** Windows' `SecondaryTile.FindAllAsync`: the ids of this app's pinned tiles, in the order they were pinned. */
        @JvmStatic
        fun findAll(context: Context): List<String> =
            LiveTile.call(context.applicationContext ?: context, "secondary.findAll", Bundle()).tileIds.orEmpty()
    }
}

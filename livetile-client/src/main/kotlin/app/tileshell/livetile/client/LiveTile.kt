package app.tileshell.livetile.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Entry point of the Live Tile API client, protocol version 1.
 *
 * Every call is a no-op returning [LiveTileResult.UNAVAILABLE] when the tile shell is not installed (the ShortcutBadger
 * `applyCount` contract). Calls are synchronous binder calls; make them off the main thread when they carry images.
 */
object LiveTile {
    const val AUTHORITY = "app.tileshell.livetile"
    const val PROTOCOL_VERSION = 1
    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")
    private val CONTENT_SRC = Regex("src\\s*=\\s*\"(content://[^\"]+)\"")

    /**
     * Extras the shell puts on the intent it starts your CATEGORY_LAUNCHER activity with when one of your tiles is
     * tapped: the secondary tile's id, that tile's `arguments`, and the `arguments` of the notifications on the tile
     * at that moment (Windows' chaseable tiles). Read them with [tileId], [arguments] and [activatedArguments].
     */
    const val EXTRA_TILE_ID = "app.tileshell.extra.TILE_ID"
    const val EXTRA_ARGUMENTS = "app.tileshell.extra.ARGUMENTS"
    const val EXTRA_TILE_ACTIVATED_ARGS = "app.tileshell.extra.TILE_ACTIVATED_ARGS"

    /** True when a tile shell exposing the Live Tile API is installed. */
    @JvmStatic
    fun isAvailable(context: Context): Boolean = shellPackage(context) != null

    /** The secondary tile the user tapped, or null when the app was started some other way. */
    @JvmStatic
    fun tileId(intent: Intent?): String? = intent?.getStringExtra(EXTRA_TILE_ID)

    /** The tapped secondary tile's `arguments`, or null. */
    @JvmStatic
    fun arguments(intent: Intent?): String? = intent?.getStringExtra(EXTRA_ARGUMENTS)

    /** The `arguments` of the notifications that were on the tapped tile, newest first. */
    @JvmStatic
    fun activatedArguments(intent: Intent?): List<String> = intent?.getStringArrayExtra(EXTRA_TILE_ACTIVATED_ARGS)?.toList().orEmpty()

    /**
     * One of this app's secondary tiles (Windows' SecondaryTile): pin requests, updates, deletion, and the tile /
     * badge verbs routed to that tile instead of the app's own tile.
     */
    @JvmStatic
    fun forSecondaryTile(context: Context, tileId: String): SecondaryTile = SecondaryTile(context.applicationContext ?: context, tileId)

    /**
     * Uri an app can observe (ContentObserver) to learn that the shell accepted a change to a tile. It is shared by
     * every app, so an observer cannot tell which app changed; re-read your own state when it fires.
     */
    @JvmStatic
    fun changeUri(context: Context): Uri = Uri.parse("content://$AUTHORITY/tiles")

    internal fun shellPackage(context: Context): String? =
        runCatching { context.packageManager.resolveContentProvider(AUTHORITY, 0)?.packageName }.getOrNull()

    /** content:// image sources in an XML payload, so the caller's read grant can be handed to the shell for the call. */
    internal fun contentSources(xml: String): Set<Uri> =
        CONTENT_SRC.findAll(xml).map { Uri.parse(it.groupValues[1].replace("&amp;", "&")) }.toSet()

    internal fun call(context: Context, method: String, extras: Bundle, images: Collection<Uri> = emptyList()): LiveTileResult {
        val shell = shellPackage(context) ?: return LiveTileResult.UNAVAILABLE
        extras.putInt("v", PROTOCOL_VERSION)
        val granted = mutableListOf<Uri>()
        return try {
            for (uri in images) {
                if (uri.scheme != "content") continue
                context.grantUriPermission(shell, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                granted += uri
            }
            val out = context.contentResolver.call(AUTHORITY_URI, method, null, extras)
                ?: return LiveTileResult(false, "transport", "provider returned no result")
            LiveTileResult(
                ok = out.getBoolean("ok"),
                error = out.getString("error"),
                detail = out.getString("detail"),
                setting = out.getString("setting"),
                exists = if (out.containsKey("exists")) out.getBoolean("exists") else null,
                tileIds = out.getStringArray("tileIds")?.toList(),
                pending = if (out.containsKey("pending")) out.getBoolean("pending") else null,
            )
        } catch (e: SecurityException) {
            LiveTileResult(false, if (granted.size < images.count { it.scheme == "content" }) "grant" else "transport", e.message)
        } catch (e: IllegalArgumentException) {
            LiveTileResult(false, "transport", e.message)
        } finally {
            // The shell copies images during the call, so the grant is withdrawn as soon as it returns.
            granted.forEach { runCatching { context.revokeUriPermission(shell, it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        }
    }
}

/** Outcome of one API call. [error] is the shell's error code ("identity", "rate", "xml-dtd", ...) when [ok] is false. */
data class LiveTileResult(
    val ok: Boolean,
    val error: String? = null,
    val detail: String? = null,
    val setting: String? = null,
    /** secondary.exists. */
    val exists: Boolean? = null,
    /** secondary.findAll: this app's own tile ids. */
    val tileIds: List<String>? = null,
    /** secondary.requestCreate: true = the user still has to confirm; false = an existing tile was updated. */
    val pending: Boolean? = null,
) {
    companion object {
        @JvmField val UNAVAILABLE = LiveTileResult(false, "unavailable", "no tile shell with the Live Tile API is installed")
    }
}

/** Windows NotificationSetting subset answered by tile.setting, plus UNAVAILABLE when no shell is installed. */
enum class TileSetting { ENABLED, DISABLED_FOR_APPLICATION, NOT_PINNED, UNAVAILABLE }

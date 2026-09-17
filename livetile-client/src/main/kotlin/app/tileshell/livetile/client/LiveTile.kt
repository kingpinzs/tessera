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

    /** True when a tile shell exposing the Live Tile API is installed. */
    @JvmStatic
    fun isAvailable(context: Context): Boolean = shellPackage(context) != null

    /** Uri an app can observe (ContentObserver) to learn that the shell accepted a change to its tile. */
    @JvmStatic
    fun changeUri(context: Context): Uri = Uri.parse("content://$AUTHORITY/tiles/${context.packageName}/primary")

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
) {
    companion object {
        @JvmField val UNAVAILABLE = LiveTileResult(false, "unavailable", "no tile shell with the Live Tile API is installed")
    }
}

/** Windows NotificationSetting subset answered by tile.setting, plus UNAVAILABLE when no shell is installed. */
enum class TileSetting { ENABLED, DISABLED_FOR_APPLICATION, NOT_PINNED, UNAVAILABLE }

package app.tileshell.tiles.api

import app.tileshell.tiles.TileSize

/**
 * The secondary-tile verbs' validation and the pin prompt's hold rule (R5 §1.9 / §4b, phase 02 Decisions).
 * Pure Kotlin, no Android classes, so the provider and the unit tests run exactly the same code.
 */

/** The fields of one `secondary.requestCreate` / `secondary.update` call, after validation. */
data class SecondaryFields(
    val tileId: String,
    val displayName: String,
    val arguments: String,
    /** The logo's image URI as the caller wrote it; the authority is checked against the caller by [ImageIngest]. */
    val logo: TileElement.Image?,
    val size: TileSize,
    val showName: Boolean,
)

sealed interface SecondaryFieldsResult {
    data class Ok(val fields: SecondaryFields) : SecondaryFieldsResult
    data class Invalid(val reason: String, val detail: String) : SecondaryFieldsResult
}

object SecondaryTileRules {

    /**
     * Windows' `new SecondaryTile(tileId, displayName, arguments, logo, size)`: "You MUST provide initialized values
     * for all of the above properties" (R5 §1.9). Here displayName is required, arguments defaults to the empty
     * string, the logo is optional (the tile then shows the owner's own icon) and showName defaults to false
     * ("By default the display name will NOT be shown").
     *
     * Every argument is taken as `Any?` because it comes out of a Bundle an app filled in: a wrong type is a
     * rejection with a named reason, never a silent default.
     */
    fun validate(tileId: Any?, displayName: Any?, arguments: Any?, logo: Any?, size: Any?, showName: Any?): SecondaryFieldsResult {
        val id = tileId as? String
            ?: return invalid(LiveTileProtocol.Error.TILE_ID, "tileId (String) is required")
        if (!LiveTileProtocol.isValidTileId(id)) {
            return invalid(LiveTileProtocol.Error.TILE_ID, "tileId must be 1-64 characters of letters, digits, '.', '_' or '-', and not only dots")
        }
        val name = when (displayName) {
            null -> return invalid(LiveTileProtocol.Error.DISPLAY_NAME, "displayName (String) is required")
            is String -> displayName.trim()
            else -> return invalid(LiveTileProtocol.Error.DISPLAY_NAME, "displayName must be a String")
        }
        if (name.isEmpty()) return invalid(LiveTileProtocol.Error.DISPLAY_NAME, "displayName is empty")
        if (name.length > LiveTileProtocol.MAX_DISPLAY_NAME) {
            return invalid(LiveTileProtocol.Error.DISPLAY_NAME, "displayName is ${name.length} characters (max ${LiveTileProtocol.MAX_DISPLAY_NAME})")
        }
        if (name.any { it.isISOControl() }) return invalid(LiveTileProtocol.Error.DISPLAY_NAME, "displayName holds a control character")
        val args = when (arguments) {
            null -> ""
            is String -> arguments
            else -> return invalid(LiveTileProtocol.Error.ARGUMENTS, "arguments must be a String")
        }
        if (args.length > LiveTileProtocol.MAX_ARGUMENTS) {
            return invalid(LiveTileProtocol.Error.ARGUMENTS, "arguments is ${args.length} characters (max ${LiveTileProtocol.MAX_ARGUMENTS})")
        }
        val tileSize = when (size) {
            null -> TileSize.MEDIUM // Windows' TileSize.Default for a square tile
            LiveTileProtocol.SIZE_SMALL -> TileSize.SMALL
            LiveTileProtocol.SIZE_MEDIUM -> TileSize.MEDIUM
            LiveTileProtocol.SIZE_WIDE -> TileSize.WIDE
            else -> return invalid(LiveTileProtocol.Error.SIZE, "size must be one of ${LiveTileProtocol.SIZES}")
        }
        val show = when (showName) {
            null -> false
            is Boolean -> showName
            else -> return invalid(LiveTileProtocol.Error.SHOW_NAME, "showName must be a boolean")
        }
        val image = when (logo) {
            null -> null
            is String -> when (val r = TileXmlValidator.imageSource(logo)) {
                is ImageSourceResult.Ok -> r.image
                is ImageSourceResult.Invalid -> return invalid(LiveTileProtocol.Error.LOGO, "logo: ${r.detail}")
            }
            else -> return invalid(LiveTileProtocol.Error.LOGO, "logo must be a content:// or android.resource:// URI string")
        }
        return SecondaryFieldsResult.Ok(SecondaryFields(id, name, args, image, tileSize, show))
    }

    private fun invalid(reason: String, detail: String) = SecondaryFieldsResult.Invalid(reason, detail)
}

/**
 * Holds pin requests until Start is next shown (phase 02 Decisions: "a request while Start is not visible or the
 * requester is in the background is held until Start is next shown"; Edge cases: "request while Start is not visible
 * or the requester is in the background").
 *
 * The rule is one line: a request is presented at the NEXT showing of Start, never the one it arrived during. An app
 * that asks while the user is in that app (Start not visible) is answered when the user goes Home; an app that asks
 * from the background while the user is already on Start cannot interrupt the showing it did not cause. A prompt is
 * therefore never drawn over another app, and a request is never dropped.
 *
 * Pure: the caller tells it when Start becomes visible; it holds no Android state.
 */
class SecondaryPinQueue<T>(private val idOf: (T) -> String, private val capacity: Int = MAX_PENDING) {
    private class Held<T>(val id: String, var request: T, val showAtShowing: Long)

    private val held = ArrayList<Held<T>>()
    private var showings = 0L
    private var visible = false

    sealed interface Offer<out T> {
        /** Queued. [presentable] is what the prompt should show now (null: nothing to show yet). */
        data class Queued<T>(val presentable: T?) : Offer<T>
        /** The queue is full: the request was NOT taken, and the caller is told so. */
        data object Full : Offer<Nothing>
    }

    @Synchronized
    fun offer(request: T): Offer<T> {
        val id = idOf(request)
        val existing = held.firstOrNull { it.id == id }
        if (existing != null) {
            // A second request for the same tile replaces the first and keeps its place in the queue.
            existing.request = request
            return Offer.Queued(presentable())
        }
        if (held.size >= capacity) return Offer.Full
        held += Held(id, request, showings + 1)
        return Offer.Queued(presentable())
    }

    /** Start became visible (true) or left the screen (false); returns what the prompt should show now. */
    @Synchronized
    fun onStartVisible(nowVisible: Boolean): T? {
        if (nowVisible && !visible) showings++
        visible = nowVisible
        return presentable()
    }

    /** What the prompt should be showing, or null. */
    @Synchronized
    fun presentable(): T? {
        if (!visible) return null
        return held.firstOrNull { it.showAtShowing <= showings }?.request
    }

    /** Drops [id] (accepted or declined) and returns what the prompt should show next. */
    @Synchronized
    fun remove(id: String): T? {
        held.removeAll { it.id == id }
        return presentable()
    }

    /** Drops every request matching [predicate] (e.g. an owner that went away); returns the dropped ones and what to show next. */
    @Synchronized
    fun removeIf(predicate: (T) -> Boolean): Pair<List<T>, T?> {
        val dropped = held.filter { predicate(it.request) }.map { it.request }
        held.removeAll { predicate(it.request) }
        return dropped to presentable()
    }

    @Synchronized
    fun waiting(): List<T> = held.map { it.request }

    companion object {
        /** At most this many requests wait at once; a further request is refused rather than silently dropped. */
        const val MAX_PENDING = 8
    }
}

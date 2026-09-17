package app.tileshell.tiles.api

/**
 * Wire contract of the public Live Tile API, protocol version 1 (R5 §4b; phase 01 Decisions).
 * Verbs are appended in later versions; a name or extra listed here never changes meaning.
 * Secondary-tile verbs belong to phase 02 and are deliberately absent.
 */
object LiveTileProtocol {
    const val AUTHORITY = "app.tileshell.livetile"
    const val VERSION = 1

    const val TILE_UPDATE = "tile.update"
    const val TILE_CLEAR = "tile.clear"
    const val TILE_ENABLE_QUEUE = "tile.enableQueue"
    const val TILE_SCHEDULE = "tile.schedule"
    const val BADGE_UPDATE = "badge.update"
    const val BADGE_CLEAR = "badge.clear"
    const val TILE_SETTING = "tile.setting"
    val VERBS = setOf(TILE_UPDATE, TILE_CLEAR, TILE_ENABLE_QUEUE, TILE_SCHEDULE, BADGE_UPDATE, BADGE_CLEAR, TILE_SETTING)

    // Request extras
    const val EXTRA_VERSION = "v"
    const val EXTRA_TILE_ID = "tileId"
    const val EXTRA_OWNER = "owner"
    const val EXTRA_XML = "xml"
    const val EXTRA_TAG = "tag"
    const val EXTRA_EXPIRES_AT = "expiresAt"
    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_ID = "id"
    const val EXTRA_DELIVERY_AT = "deliveryAt"
    const val EXTRA_VALUE = "value"

    // Response extras
    const val RESULT_OK = "ok"
    const val RESULT_ERROR = "error"
    const val RESULT_DETAIL = "detail"
    const val RESULT_SETTING = "setting"

    // tile.setting values (Windows NotificationSetting subset + NOT_PINNED)
    const val SETTING_ENABLED = "ENABLED"
    const val SETTING_DISABLED_FOR_APPLICATION = "DISABLED_FOR_APPLICATION"
    const val SETTING_NOT_PINNED = "NOT_PINNED"

    /** The 13 Windows badge glyph names (R5 §1.6). */
    val GLYPHS = setOf("none", "activity", "alarm", "alert", "attention", "available", "away", "busy", "error", "newMessage", "paused", "playing", "unavailable")

    /** Error codes returned in [RESULT_ERROR]. */
    object Error {
        const val IDENTITY = "identity"
        const val SHARED_UID = "shared-uid"
        const val RATE = "rate"
        const val DISABLED = "disabled"
        const val UNKNOWN_VERB = "unknown-verb"
        const val SECONDARY_UNSUPPORTED = "secondary-unsupported"
        const val TAG = "tag"
        const val EXPIRES_AT = "expiresAt"
        const val ID = "id"
        const val DELIVERY_AT = "deliveryAt"
        const val ENABLED = "enabled"
        const val VALUE = "value"
        const val GLYPH_UNSUPPORTED = "glyph-unsupported"
        const val QUOTA = "quota"
        const val IMAGE_AUTHORITY = "image-authority"
        const val IMAGE_READ = "image-read"
        const val IMAGE_SIZE = "image-size"
        const val IMAGE_DECODE = "image-decode"
        const val INTERNAL = "internal"
    }

    /** Tag and scheduled id: Windows' "case-insensitive string of up to 16 alphanumeric characters" (R5 §1.3). */
    val TAG_PATTERN = Regex("^[A-Za-z0-9]{1,16}$")
}

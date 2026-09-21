package app.tileshell.tiles.api

/**
 * Wire contract of the public Live Tile API, protocol version 1 (R5 §4b; phase 01 Decisions).
 * Verbs are appended in later versions; a name or extra listed here never changes meaning.
 *
 * Phase 02 build task 6 appends the secondary-tile verbs and lets every tile.* / badge.* verb carry a [EXTRA_TILE_ID]
 * that addresses one of the CALLER'S OWN secondary tiles. Nothing already in version 1 changed meaning: a call with
 * no tileId still addresses the caller's primary tile.
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

    // Secondary tiles (R5 §1.9 / §4b, phase 02 build task 6).
    const val SECONDARY_REQUEST_CREATE = "secondary.requestCreate"
    const val SECONDARY_REQUEST_DELETE = "secondary.requestDelete"
    const val SECONDARY_UPDATE = "secondary.update"
    const val SECONDARY_EXISTS = "secondary.exists"
    const val SECONDARY_FIND_ALL = "secondary.findAll"

    val VERBS = setOf(
        TILE_UPDATE, TILE_CLEAR, TILE_ENABLE_QUEUE, TILE_SCHEDULE, BADGE_UPDATE, BADGE_CLEAR, TILE_SETTING,
        SECONDARY_REQUEST_CREATE, SECONDARY_REQUEST_DELETE, SECONDARY_UPDATE, SECONDARY_EXISTS, SECONDARY_FIND_ALL,
    )

    /** The verbs whose subject is the secondary tile named by [EXTRA_TILE_ID] rather than a tile's content. */
    val SECONDARY_VERBS = setOf(SECONDARY_REQUEST_CREATE, SECONDARY_REQUEST_DELETE, SECONDARY_UPDATE, SECONDARY_EXISTS, SECONDARY_FIND_ALL)

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
    const val EXTRA_DISPLAY_NAME = "displayName"
    const val EXTRA_ARGUMENTS = "arguments"
    const val EXTRA_LOGO = "logo"
    const val EXTRA_SIZE = "size"
    const val EXTRA_SHOW_NAME = "showName"

    // Response extras
    const val RESULT_OK = "ok"
    const val RESULT_ERROR = "error"
    const val RESULT_DETAIL = "detail"
    const val RESULT_SETTING = "setting"
    /** secondary.exists. */
    const val RESULT_EXISTS = "exists"
    /** secondary.findAll: the caller's own tile ids, never anyone else's. */
    const val RESULT_TILE_IDS = "tileIds"
    /** secondary.requestCreate: true = the user has still to confirm; false = an existing tile was updated in place. */
    const val RESULT_PENDING = "pending"

    // tile.setting values (Windows NotificationSetting subset + NOT_PINNED)
    const val SETTING_ENABLED = "ENABLED"
    const val SETTING_DISABLED_FOR_APPLICATION = "DISABLED_FOR_APPLICATION"
    const val SETTING_NOT_PINNED = "NOT_PINNED"

    /** Tile sizes a secondary tile may ask to be pinned at (W10M phones had no large tile, R5 §1.12). */
    const val SIZE_SMALL = "small"
    const val SIZE_MEDIUM = "medium"
    const val SIZE_WIDE = "wide"
    val SIZES = setOf(SIZE_SMALL, SIZE_MEDIUM, SIZE_WIDE)

    /**
     * Extras on the intent that a tile tap starts the owner's CATEGORY_LAUNCHER activity with (R5 §4b
     * "Launch arguments"). The shell never starts an arbitrary intent.
     */
    const val EXTRA_LAUNCH_TILE_ID = "app.tileshell.extra.TILE_ID"
    const val EXTRA_LAUNCH_ARGUMENTS = "app.tileshell.extra.ARGUMENTS"
    const val EXTRA_LAUNCH_ACTIVATED_ARGS = "app.tileshell.extra.TILE_ACTIVATED_ARGS"

    /** The 13 Windows badge glyph names (R5 §1.6). */
    val GLYPHS = setOf("none", "activity", "alarm", "alert", "attention", "available", "away", "busy", "error", "newMessage", "paused", "playing", "unavailable")

    /** Error codes returned in [RESULT_ERROR]. */
    object Error {
        const val IDENTITY = "identity"
        const val SHARED_UID = "shared-uid"
        const val RATE = "rate"
        const val DISABLED = "disabled"
        const val UNKNOWN_VERB = "unknown-verb"

        /**
         * Phase 01 answered this to every call carrying a tileId. Phase 02 serves the caller's own secondary tiles, so
         * nothing returns it any more; the name stays reserved (protocol v1 never reuses a name for something else).
         */
        @Suppress("unused")
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

        // Secondary tiles
        const val TILE_ID = "tileId"
        const val DISPLAY_NAME = "displayName"
        const val ARGUMENTS = "arguments"
        const val SIZE = "size"
        const val SHOW_NAME = "showName"
        const val LOGO = "logo"
        /** The caller addressed one of its own secondary tiles that is not pinned (Windows throws for this, R5 §1.9). */
        const val NOT_FOUND = "not-found"
    }

    /** Tag and scheduled id: Windows' "case-insensitive string of up to 16 alphanumeric characters" (R5 §1.3). */
    val TAG_PATTERN = Regex("^[A-Za-z0-9]{1,16}$")

    /**
     * Secondary tile id: Windows allows alphanumerics, period and underscore, up to 64 characters
     * (learn.microsoft.com secondary-tiles-pinning). A hyphen is allowed too; a path separator, a colon (the tile key's
     * own separator) and a whitespace character are not, and an id of nothing but dots is refused so a stored id can
     * never read as a path.
     */
    val TILE_ID_PATTERN = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun isValidTileId(tileId: String): Boolean = TILE_ID_PATTERN.matches(tileId) && tileId.any { it != '.' }

    /** Windows: "You MUST provide initialized values" for displayName; the caps keep one record small. */
    const val MAX_DISPLAY_NAME = 64
    const val MAX_ARGUMENTS = 2048
}

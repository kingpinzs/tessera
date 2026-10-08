package app.tileshell.music

/**
 * The two kinds of item that are not of the library (phase 20, r3 D1 / D10): a radio station and a home-server track,
 * each known by its media id's prefix. Neither id is ever a decimal number, so [MusicItemRule.libraryId] cannot match
 * one and no library row, playlist or album lookup can either.
 *
 * "Live" is the item's MARK alone, never its duration: an HLS station's live window reports a duration above 0, and
 * a progressive one reports none. Pure (`MusicLiveTest`).
 */
object MusicLive {
    /** A station's media id is `station:<uuid>`; built only by `music/radio/StationItem.kt`. */
    const val STATION_PREFIX = "station:"

    /** A home-server track's media id is `server:<id>`; built only by `music/server/ServerTrackItem.kt`. */
    const val SERVER_PREFIX = "server:"

    fun stationId(uuid: String): String = STATION_PREFIX + uuid

    /** The station's uuid, or null when [mediaId] is not a station's. */
    fun stationUuid(mediaId: String?): String? = mediaId?.takeIf { isLive(it) }?.removePrefix(STATION_PREFIX)

    fun serverId(id: String): String = SERVER_PREFIX + id

    /** A station: live whatever the player says its duration is. A server track has a known length and is not. */
    fun isLive(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(STATION_PREFIX)

    fun isServer(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(SERVER_PREFIX)

    /**
     * Whether an armed end-of-track sleep timer is cleared because the item now playing is live (r3 D15): a stream
     * never reaches its end, so the timer could never fire. The caller then writes
     * [app.tileshell.music.radio.StreamLine.SLEEP_CLEARED].
     */
    fun clearsEndOfTrack(armed: Boolean, mediaId: String?): Boolean = armed && isLive(mediaId)
}

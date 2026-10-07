package app.tileshell.music

/**
 * What the session does with ONE media item a controller hands it (ledger L18-1; a trust rule).
 *
 * `MusicService` is exported, so any app can connect a controller — Media3's own, a legacy `MediaBrowser`, the
 * platform's `MediaController` — and a Media3 controller can hand the session an item that carries a URI of the
 * controller's choosing (`MediaItem.toBundleIncludeLocalConfiguration`). The player opens what an item names with the
 * SHELL's identity: any file on any volume, the shell's own providers, the network. So:
 *
 *  - an item keeps the URI it arrived with ONLY when the controller runs as the shell itself (Music's own queue, whose
 *    items are built by `MusicService.mediaItem` from the library);
 *  - for every other controller the URI — and with it the metadata and everything else the item carried — is never
 *    used: the item is REBUILT from the library by its media id (as an item that arrives without a URI always was), a
 *    search is answered from the library, and anything else is dropped;
 *  - (phase 20, r3 D1) a station (`station:<uuid>`) or a home-server track (`server:<id>`) is such an item of the
 *    shell's own: kept from the shell's controller, and dropped outright from anyone else's.
 *
 * The rule never looks at the URI itself, only at whether there is one: what a stranger's URI says cannot change the
 * answer, so the answer cannot tell a stranger anything about the file it names.
 *
 * Pure: `MusicItemRuleTest` holds every combination; `media/UriAccessWiringScanTest` holds the service to asking it
 * for every item of every controller and doing exactly what it says.
 */
object MusicItemRule {
    sealed interface Decision {
        /** The shell's own item: used as it arrived. */
        data object Keep : Decision

        /** A search: the queue `MusicSearch` resolves from the library. */
        data object Search : Decision

        /** The library track with this MediaStore audio id, built by the service; dropped when the library has none. */
        data class Rebuild(val id: Long) : Decision

        /** Nothing of the library is named: the item is not played. */
        data object Drop : Decision
    }

    /**
     * @param controllerUid the uid of the controller that sent the item, as the session reports it
     * @param myUid `Process.myUid()`
     * @param mediaId the item's media id
     * @param hasUri whether the item arrived carrying a URI (a `localConfiguration`)
     * @param hasQuery whether the item is a search (`requestMetadata.searchQuery`)
     */
    fun decide(controllerUid: Int, myUid: Int, mediaId: String?, hasUri: Boolean, hasQuery: Boolean): Decision {
        // The one way an item keeps what it arrived with: the shell's own controller. A uid below 0 is nobody's.
        if (hasUri && myUid >= 0 && controllerUid == myUid) return Decision.Keep
        // Phase 20 (r3 D1): a station or a home-server track is the shell's own to build (`StationItem`,
        // `ServerTrackItem`) and reaches the player only by the line above. Anyone else naming one gets nothing —
        // not a search, and never a library row: neither id is a library id.
        if (MusicLive.isLive(mediaId) || MusicLive.isServer(mediaId)) return Decision.Drop
        if (hasQuery) return Decision.Search
        val id = libraryId(mediaId) ?: return Decision.Drop
        return Decision.Rebuild(id)
    }

    /** A MediaStore audio id exactly as the library writes one: a positive decimal number and nothing else. */
    fun libraryId(mediaId: String?): Long? = mediaId?.toLongOrNull()?.takeIf { it > 0 && it.toString() == mediaId }
}

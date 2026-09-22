package app.tileshell.tiles

/**
 * A tile grows while the thing it stands for is HAPPENING, and returns when it stops (Jeremy,
 * 2026-09-21: "if there is music playing that tile should grow bigger", and "be able to make it a bit
 * bigger when its playing the slide show"). INDEX Change Log items 3 and 4.
 *
 * **This never touches the stored layout, and that is the whole design.** It is a transform applied to
 * the grid order on the way to the screen, exactly like [RecentPromotion]: "music is playing right now"
 * is a fact about this moment, not a durable fact about the tile, so it must not be written down.
 * [AutoSize] writes to the store because "this app is used a lot" IS durable; this deliberately does not.
 *
 * What that buys, and why it is the only safe shape:
 *  - **Returning is the ABSENCE of the transform.** There is no saved original size to restore, so there
 *    is no second write that could fail, and no window in which a crash could strand a tile at the size
 *    it grew to. A tile that grew because a song started is back at its stored size the moment the song
 *    stops — including when "stops" means the process died mid-song, because the set of grown tiles
 *    ([ActiveTiles]) lives only in memory and comes back empty.
 *  - **A size set by hand is never at risk.** See below.
 *
 * ### Hand-set sizes ([LayoutStore.Layout.manualSizes])
 *
 * A growth applies to a hand-set tile, and it does NOT write to `manualSizes`. Both halves are
 * deliberate:
 *
 *  - It is not BLOCKED by a hand-set size, because blocking would mean the Music tile someone
 *    deliberately made small is the one tile that never shows its controls — a tile behaving
 *    differently from its neighbours for a reason set weeks ago and invisible today. That is the
 *    surprise this rule exists to avoid.
 *  - It never WRITES, so the hand-set size cannot be lost: the tile is drawn one step bigger while the
 *    music plays and is drawn at exactly the size that person chose the moment it stops.
 *
 * `manualSizes` means "the auto-sizer may not change this", and the auto-sizer still may not — [AutoSize]
 * is the thing that writes, and it still honours it. This only changes a picture.
 *
 * ### Who is left alone
 *  - **The bottom tile row.** Its tiles are not in the grid order at all (they live in `Layout.dock`),
 *    and the row is one fixed-height row by construction, so a dock key is simply never found here.
 *  - **Folder members.** Same rule as [RecentPromotion]: what is inside a folder is what someone built.
 *  - **Edit mode.** The caller does not apply this there. [app.tileshell.start.EditGestures] reads the
 *    grid AS DRAWN and writes back by index, so a displayed size that differs from the stored one would
 *    resize the wrong tile.
 */
object TileGrowth {

    /**
     * One step up W10M's own size ladder — the only sizing vocabulary the grid has, so "grow bigger" and
     * "a bit bigger" are the same step. A wide tile is already the widest a phone drew, so it stays.
     */
    fun grown(size: TileSize): TileSize = when (size) {
        TileSize.SMALL -> TileSize.MEDIUM
        TileSize.MEDIUM -> TileSize.WIDE
        TileSize.WIDE -> TileSize.WIDE
    }

    /**
     * [order] with every tile in [growing] drawn one size bigger. Tiles not in [growing], and tiles already
     * wide, come back untouched — the same list instance when nothing changes, so a quiet Start allocates
     * nothing on every recomposition.
     */
    fun apply(order: List<Sized>, growing: Set<TileKey>): List<Sized> {
        if (growing.isEmpty() || order.isEmpty()) return order
        if (order.none { it.key in growing && it.size != grown(it.size) }) return order
        return order.map { sized ->
            if (sized.key !in growing) sized else sized.copy(size = grown(sized.size))
        }
    }
}

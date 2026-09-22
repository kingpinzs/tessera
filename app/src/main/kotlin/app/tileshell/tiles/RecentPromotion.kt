package app.tileshell.tiles

/**
 * The app you last opened sits right above the bottom tile row (Jeremy, 2026-09-21: "The last open app
 * should be in the section right above the phone bar unless the last open app is the 3 bottom apps and
 * if its one of the others on the home screen rearrange so the tile goes right above the phone bar and
 * goes back to where it was after closing it").
 *
 * **"Right above the bottom row" is a place on the SCREEN, not a place in the grid** (Jeremy,
 * 2026-09-22: "the last active app goes right above the bottom row in this black space", with a photo
 * of the empty band between the last grid row and the bottom row). This first moved the tile to the end
 * of the grid order on the assumption that the end of the grid is the row above the bottom row. It is
 * not: the grid ends wherever the tiles happen to end, so on a Start that does not fill the screen the
 * promoted tile landed part-way up and the band above the bottom row stayed black — the exact space the
 * promotion exists to use. So the tile is LIFTED OUT of the grid and handed back separately, for the
 * caller to draw in a fixed row above the bottom row, pinned the same way the bottom row itself is.
 *
 * **This never touches the stored layout.** It is a transform applied to the order on the way to the
 * screen, so "goes back to where it was" costs nothing and cannot go wrong: there is no saved original
 * position to restore, no window in which a crash could strand a tile somewhere it does not belong, and
 * a person's own arrangement is exactly what it was the moment the promotion stops. Auto-sizing
 * ([AutoSize]) writes to the store; this deliberately does not.
 *
 * Who is left alone, and why:
 *  - **The bottom row's own apps.** Jeremy's "unless the last open app is the 3 bottom apps": they are
 *    already within reach, and promoting one would take it out of the row it was put in.
 *  - **An app with no tile on Start.** "if its one of the others on the home screen" — something opened
 *    from the app list has nothing to move, and inventing a tile for it is not what was asked.
 *  - **A tile inside a folder.** Pulling a member out of a folder to the grid would be a real edit to
 *    what someone built, not a temporary promotion, and it could not be undone by simply not applying
 *    this any more.
 */
object RecentPromotion {

    /**
     * The grid to pack, and the one tile to draw above the bottom row.
     *
     * [tile] is null whenever nothing is promoted or the promoted app is one of the three left alone
     * above, and [grid] is then the caller's own list instance — a quiet Start allocates nothing.
     */
    data class Promotion(val grid: List<Sized>, val tile: Sized?)

    /**
     * [order] with [promoted] lifted out of it. The tile keeps its own size: what is promoted is where a
     * tile is drawn, never how big it is, so a small tile promoted stays small and [TileGrowth] remains
     * the only thing that resizes on the way to the screen.
     */
    fun apply(order: List<Sized>, promoted: TileKey?): Promotion {
        if (promoted == null) return Promotion(order, null)
        val index = order.indexOfFirst { it.key == promoted }
        // Not on the grid: a bottom-row app, a folder member, or an app with no tile at all.
        if (index < 0) return Promotion(order, null)
        val lifted = order[index]
        val rest = buildList(order.size - 1) {
            addAll(order.subList(0, index))
            addAll(order.subList(index + 1, order.size))
        }
        return Promotion(rest, lifted)
    }
}

package app.tileshell.tiles

/**
 * The app you last opened sits right above the bottom tile row (Jeremy, 2026-09-21: "The last open app
 * should be in the section right above the phone bar unless the last open app is the 3 bottom apps and
 * if its one of the others on the home screen rearrange so the tile goes right above the phone bar and
 * goes back to where it was after closing it").
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
     * [order] with [promoted] moved to the end, which is where [GridPack] packs the last row — the
     * section directly above the bottom tile row. Everything else keeps its relative position, so the
     * grid a person knows only shifts by the one tile that left it.
     */
    fun apply(order: List<Sized>, promoted: TileKey?): List<Sized> {
        if (promoted == null) return order
        val index = order.indexOfFirst { it.key == promoted }
        // Not on the grid: a bottom-row app, a folder member, or an app with no tile at all.
        if (index < 0) return order
        // Already the last tile: moving it would be a no-op that still allocated a new list.
        if (index == order.lastIndex) return order
        val moved = order[index]
        return buildList(order.size) {
            addAll(order.subList(0, index))
            addAll(order.subList(index + 1, order.size))
            add(moved)
        }
    }
}

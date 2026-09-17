package app.tileshell.tiles

/**
 * The Start grid's reflow (phase 02 build task 2).
 *
 * The layout's truth is an ORDER of tiles, not a set of coordinates: positions are derived by packing the
 * order into the grid, first fit, reading order. That is what makes every reflow in this phase lossless —
 * unpinning fills the gap from the tiles after it (R6 §1.2.8, H3), a drop after the dwell pushes the tiles
 * there down the grid (R6 §1.3.3, H5), and changing "show more tiles" re-packs the same order into the
 * narrower grid instead of leaving tiles outside it (phase 02 edge case; the defect phase 01 recorded and
 * this phase owns, INDEX Change Log 2026-09-17).
 *
 * Packing the phase 01 default order into the 6-unit grid reproduces DefaultLayout's coordinates exactly,
 * so phase 01's Start is unchanged (GridPackTest).
 */
object GridPack {

    /** First-fit pack of [items] into a grid [unitsAcross] small-tile units wide. Never drops an item. */
    fun pack(items: List<Sized>, unitsAcross: Int): List<Placement> {
        if (unitsAcross <= 0) return emptyList()
        val rows = ArrayList<BooleanArray>()
        fun row(y: Int): BooleanArray {
            while (rows.size <= y) rows.add(BooleanArray(unitsAcross))
            return rows[y]
        }
        val out = ArrayList<Placement>(items.size)
        for (item in items) {
            // A tile wider than the grid (a wide tile in a 2-column grid is exactly the width) is clamped
            // rather than dropped: losing a tile is never an option.
            val w = item.size.spanX.coerceAtMost(unitsAcross)
            val h = item.size.spanY
            var y = 0
            var placed = false
            while (!placed) {
                for (x in 0..(unitsAcross - w)) {
                    if (free(::row, x, y, w, h)) {
                        for (dy in 0 until h) for (dx in 0 until w) row(y + dy)[x + dx] = true
                        out.add(Placement(item.key, x, y, item.size))
                        placed = true
                        break
                    }
                }
                if (!placed) y++
            }
        }
        return out
    }

    private inline fun free(row: (Int) -> BooleanArray, x: Int, y: Int, w: Int, h: Int): Boolean {
        for (dy in 0 until h) for (dx in 0 until w) if (row(y + dy)[x + dx]) return false
        return true
    }

    /** Index in [placements] of the tile covering cell ([x], [y]), or null when the cell is empty. */
    fun indexAt(placements: List<Placement>, x: Int, y: Int): Int? {
        placements.forEachIndexed { i, p ->
            val w = p.size.spanX
            if (x >= p.x && x < p.x + w && y >= p.y && y < p.y + p.size.spanY) return i
        }
        return null
    }

    /**
     * Where a tile dropped on the empty cell ([x], [y]) lands in the order: before the first tile that starts
     * after that cell in reading order, i.e. appended when the cell is past the end of the grid.
     */
    fun insertIndexForEmptyCell(placements: List<Placement>, x: Int, y: Int): Int {
        placements.forEachIndexed { i, p ->
            if (p.y > y || (p.y == y && p.x > x)) return i
        }
        return placements.size
    }

    /** Rows the packed [placements] fill (0 when empty). */
    fun rowCount(placements: List<Placement>): Int = placements.maxOfOrNull { it.y + it.size.spanY } ?: 0
}

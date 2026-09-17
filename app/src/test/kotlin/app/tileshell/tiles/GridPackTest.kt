package app.tileshell.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPackTest {
    private fun key(n: String) = TileKey.ShellTile(n)
    private fun at(ps: List<Placement>, id: String) = ps.first { it.key.id == "shell:$id" }

    @Test
    fun `the phase 01 default order packs to the phase 01 coordinates`() {
        val packed = GridPack.pack(DefaultLayout.order(), 6)
        val byId = packed.associateBy { it.key.id }
        assertEquals(0 to 0, byId["slot:PEOPLE"]!!.let { it.x to it.y })
        assertEquals(2 to 0, byId["slot:BROWSER"]!!.let { it.x to it.y })
        assertEquals(4 to 0, byId["slot:MAIL"]!!.let { it.x to it.y })
        assertEquals(0 to 2, byId["slot:CALENDAR"]!!.let { it.x to it.y })
        assertEquals(4 to 2, byId["slot:PHOTOS"]!!.let { it.x to it.y })
        assertEquals(0 to 4, byId["shell:weather"]!!.let { it.x to it.y })
        assertEquals(4 to 4, byId["slot:STORE"]!!.let { it.x to it.y })
        assertEquals(5 to 4, byId["slot:MAPS"]!!.let { it.x to it.y })
        assertEquals(4 to 5, byId["slot:MUSIC"]!!.let { it.x to it.y })
        assertEquals(5 to 5, byId["shell:settings"]!!.let { it.x to it.y })
    }

    /** The defect phase 01 recorded and this phase owns: nothing may fall outside the 2-column grid. */
    @Test
    fun `the default order re-packs inside a 2-column grid with no tile lost`() {
        val packed = GridPack.pack(DefaultLayout.order(), 4)
        assertEquals(DefaultLayout.order().size, packed.size)
        packed.forEach { p ->
            assertTrue("${p.key.id} at x=${p.x} w=${p.size.spanX} is outside a 4-unit grid", p.x >= 0 && p.x + p.size.spanX <= 4)
        }
        // and no two tiles overlap
        val cells = HashSet<Pair<Int, Int>>()
        packed.forEach { p ->
            for (dy in 0 until p.size.spanY) for (dx in 0 until p.size.spanX) {
                assertTrue("overlap at ${p.x + dx},${p.y + dy}", cells.add(p.x + dx to p.y + dy))
            }
        }
    }

    @Test
    fun `unpinning pulls the tiles after the gap up`() {
        val order = listOf(
            Sized(key("a"), TileSize.MEDIUM), Sized(key("b"), TileSize.MEDIUM), Sized(key("c"), TileSize.MEDIUM),
            Sized(key("d"), TileSize.MEDIUM),
        )
        val before = GridPack.pack(order, 6)
        assertEquals(0 to 2, at(before, "d").let { it.x to it.y })
        val after = GridPack.pack(order.filterNot { it.key == key("b") }, 6)
        assertEquals(2 to 0, at(after, "c").let { it.x to it.y })
        assertEquals(4 to 0, at(after, "d").let { it.x to it.y })
    }

    @Test
    fun `a small tile flows into the gap a wide tile leaves`() {
        val order = listOf(Sized(key("wide"), TileSize.WIDE), Sized(key("s"), TileSize.SMALL))
        val packed = GridPack.pack(order, 6)
        assertEquals(0 to 0, at(packed, "wide").let { it.x to it.y })
        assertEquals(4 to 0, at(packed, "s").let { it.x to it.y })
    }

    @Test
    fun `indexAt finds the tile under a cell and nothing under an empty one`() {
        val order = listOf(Sized(key("a"), TileSize.MEDIUM), Sized(key("b"), TileSize.SMALL))
        val packed = GridPack.pack(order, 6)
        assertEquals(0, GridPack.indexAt(packed, 1, 1))
        assertEquals(1, GridPack.indexAt(packed, 2, 0))
        assertNull(GridPack.indexAt(packed, 5, 5))
    }

    @Test
    fun `an empty cell past the end of the grid appends`() {
        val order = listOf(Sized(key("a"), TileSize.MEDIUM), Sized(key("b"), TileSize.MEDIUM))
        val packed = GridPack.pack(order, 6)
        assertEquals(2, GridPack.insertIndexForEmptyCell(packed, 0, 9))
        assertEquals(1, GridPack.insertIndexForEmptyCell(packed, 1, 0).let { if (it == 1) 1 else it })
    }

    @Test
    fun `a tile wider than the grid is clamped, never dropped`() {
        val packed = GridPack.pack(listOf(Sized(key("wide"), TileSize.WIDE)), 2)
        assertEquals(1, packed.size)
        assertEquals(0, packed[0].x)
    }
}

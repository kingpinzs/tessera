package app.tileshell.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The last opened app sits above the bottom tile row (INDEX Change Log 2026-09-21 item 7, amended
 * 2026-09-22: it is LIFTED OUT of the grid for the caller to draw in a fixed row there, because the end
 * of the grid is not the bottom of the screen).
 *
 * The promise this has to keep is that a person's Start screen comes back EXACTLY as they left it, so
 * most of these tests are about what the transform refuses to touch.
 *
 * Keys are SecondaryTile, not AppTile: with returnDefaultValues on, ComponentName.flattenToString()
 * returns null in a JVM test and every AppTile.id collapses to one string.
 */
class RecentPromotionTest {

    private fun tile(name: String) = TileKey.SecondaryTile("p.$name", "t")
    private fun sized(name: String) = Sized(tile(name), TileSize.MEDIUM)
    private val order = listOf(sized("a"), sized("b"), sized("c"), sized("d"))

    @Test
    fun `the opened app is handed back on its own, to be drawn above the bottom tile row`() {
        val out = RecentPromotion.apply(order, tile("b"))
        assertEquals(tile("b"), out.tile?.key)
    }

    @Test
    fun `and it leaves the grid, so the grid does not draw it twice`() {
        val out = RecentPromotion.apply(order, tile("b"))
        assertEquals(listOf("a", "c", "d").map { tile(it) }, out.grid.map { it.key })
    }

    @Test
    fun `the last tile is lifted out like any other, not left where it is`() {
        // The bug this replaces: "already at the end" was treated as already promoted, which it was not
        // — the end of the grid is wherever the tiles end, not the row above the bottom tile row.
        val out = RecentPromotion.apply(order, tile("d"))
        assertEquals(tile("d"), out.tile?.key)
        assertEquals(listOf("a", "b", "c").map { tile(it) }, out.grid.map { it.key })
    }

    @Test
    fun `every other tile keeps its order, so only the promoted one appears to move`() {
        val out = RecentPromotion.apply(order, tile("a"))
        assertEquals(listOf("b", "c", "d").map { tile(it) }, out.grid.map { it.key })
    }

    @Test
    fun `the promoted tile keeps its own size`() {
        val mixed = listOf(Sized(tile("a"), TileSize.WIDE), Sized(tile("b"), TileSize.SMALL))
        assertEquals(TileSize.WIDE, RecentPromotion.apply(mixed, tile("a")).tile?.size)
        assertEquals(TileSize.SMALL, RecentPromotion.apply(mixed, tile("b")).tile?.size)
    }

    @Test
    fun `nothing promoted leaves the order untouched`() {
        val out = RecentPromotion.apply(order, null)
        assertSame(order, out.grid)
        assertNull(out.tile)
    }

    @Test
    fun `an app with no tile on Start leaves the order untouched`() {
        val out = RecentPromotion.apply(order, tile("not-on-start"))
        assertSame(order, out.grid)
        assertNull(out.tile)
    }

    @Test
    fun `a bottom-row app leaves the order untouched, because the row is not in the grid order`() {
        // The dock lives in Layout.dock, never in the grid order, so a dock key simply is not found.
        val out = RecentPromotion.apply(order, TileKey.SlotTile(Slot.PHONE))
        assertSame(order, out.grid)
        assertNull(out.tile)
    }

    @Test
    fun `an empty grid is not a special case`() {
        val empty = emptyList<Sized>()
        val out = RecentPromotion.apply(empty, tile("a"))
        assertSame(empty, out.grid)
        assertNull(out.tile)
    }

    @Test
    fun `promoting is undone by simply not applying it any more`() {
        // The whole point of doing this on the way to the screen: "goes back to where it was" is the
        // absence of the transform, not a second edit that could fail or be interrupted.
        val promoted = RecentPromotion.apply(order, tile("b"))
        assertEquals(order, RecentPromotion.apply(order, null).grid)
        assertEquals(order.map { it.key }.toSet(), (promoted.grid + promoted.tile!!).map { it.key }.toSet())
    }

    @Test
    fun `promoting one tile then another leaves no trace of the first`() {
        val first = RecentPromotion.apply(order, tile("a"))
        val second = RecentPromotion.apply(order, tile("c"))
        assertEquals(listOf("a", "b", "d").map { tile(it) }, second.grid.map { it.key })
        assertEquals(tile("c"), second.tile?.key)
        assertEquals(3, first.grid.size)
    }
}

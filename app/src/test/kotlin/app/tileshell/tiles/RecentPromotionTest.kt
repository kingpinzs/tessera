package app.tileshell.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The last opened app sits above the bottom tile row (INDEX Change Log 2026-09-21 item 7).
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
    fun `the opened app moves to the end, which is the row above the bottom tile row`() {
        val out = RecentPromotion.apply(order, tile("b"))
        assertEquals(listOf("a", "c", "d", "b").map { tile(it) }, out.map { it.key })
    }

    @Test
    fun `every other tile keeps its order, so only the promoted one appears to move`() {
        val out = RecentPromotion.apply(order, tile("a"))
        assertEquals(listOf("b", "c", "d", "a").map { tile(it) }, out.map { it.key })
    }

    @Test
    fun `the promoted tile keeps its own size`() {
        val mixed = listOf(Sized(tile("a"), TileSize.WIDE), Sized(tile("b"), TileSize.SMALL))
        val out = RecentPromotion.apply(mixed, tile("a"))
        assertEquals(TileSize.WIDE, out.last().size)
    }

    @Test
    fun `nothing promoted leaves the order untouched`() {
        assertSame(order, RecentPromotion.apply(order, null))
    }

    @Test
    fun `an app with no tile on Start leaves the order untouched`() {
        assertSame(order, RecentPromotion.apply(order, tile("not-on-start")))
    }

    @Test
    fun `a bottom-row app leaves the order untouched, because the row is not in the grid order`() {
        // The dock lives in Layout.dock, never in the grid order, so a dock key simply is not found.
        assertSame(order, RecentPromotion.apply(order, TileKey.SlotTile(Slot.PHONE)))
    }

    @Test
    fun `a tile already last is left exactly as it is`() {
        assertSame(order, RecentPromotion.apply(order, tile("d")))
    }

    @Test
    fun `an empty grid is not a special case`() {
        val empty = emptyList<Sized>()
        assertSame(empty, RecentPromotion.apply(empty, tile("a")))
    }

    @Test
    fun `promoting is undone by simply not applying it any more`() {
        // The whole point of doing this on the way to the screen: "goes back to where it was" is the
        // absence of the transform, not a second edit that could fail or be interrupted.
        val promoted = RecentPromotion.apply(order, tile("b"))
        assertEquals(order, RecentPromotion.apply(order, null))
        assertEquals(order.map { it.key }.toSet(), promoted.map { it.key }.toSet())
    }

    @Test
    fun `promoting one tile then another leaves no trace of the first`() {
        val first = RecentPromotion.apply(order, tile("a"))
        val second = RecentPromotion.apply(order, tile("c"))
        assertEquals(listOf("a", "b", "d", "c").map { tile(it) }, second.map { it.key })
        assertEquals(4, first.size)
    }
}

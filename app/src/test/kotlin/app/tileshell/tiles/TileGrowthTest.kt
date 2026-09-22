package app.tileshell.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * A tile that grows while something is happening on it and returns when it stops (INDEX Change Log
 * 2026-09-21 items 3 and 4).
 *
 * The promise these tests hold to is the same one [RecentPromotionTest] holds to: the stored layout is
 * never touched, so "returns to its size" is the ABSENCE of the transform and cannot half-happen.
 *
 * Keys are SecondaryTile / SlotTile, never AppTile: with returnDefaultValues on,
 * ComponentName.flattenToString() returns null in a JVM test and every AppTile.id collapses to one string.
 */
class TileGrowthTest {

    private fun tile(name: String) = TileKey.SecondaryTile("p.$name", "t")
    private fun sized(name: String, size: TileSize = TileSize.MEDIUM) = Sized(tile(name), size)
    private val order = listOf(sized("a"), sized("b"), sized("c"))

    @Test
    fun `a small tile grows to medium and a medium one to wide`() {
        assertEquals(TileSize.MEDIUM, TileGrowth.grown(TileSize.SMALL))
        assertEquals(TileSize.WIDE, TileGrowth.grown(TileSize.MEDIUM))
    }

    @Test
    fun `a wide tile is already the widest a phone drew, so it stays wide`() {
        assertEquals(TileSize.WIDE, TileGrowth.grown(TileSize.WIDE))
    }

    @Test
    fun `the busy tile is drawn one size bigger`() {
        val out = TileGrowth.apply(order, setOf(tile("b")))
        assertEquals(listOf(TileSize.MEDIUM, TileSize.WIDE, TileSize.MEDIUM), out.map { it.size })
    }

    @Test
    fun `every other tile keeps its size and its place`() {
        val out = TileGrowth.apply(order, setOf(tile("b")))
        assertEquals(order.map { it.key }, out.map { it.key })
    }

    @Test
    fun `nothing busy leaves the order exactly as it is`() {
        assertSame(order, TileGrowth.apply(order, emptySet()))
    }

    @Test
    fun `a busy tile that is not on the grid changes nothing`() {
        // A bottom-row tile, a folder member, or a tile that was unpinned while the music played.
        assertSame(order, TileGrowth.apply(order, setOf(tile("not-on-start"))))
    }

    @Test
    fun `a busy tile that is already wide costs no new list`() {
        val wide = listOf(sized("a", TileSize.WIDE))
        assertSame(wide, TileGrowth.apply(wide, setOf(tile("a"))))
    }

    @Test
    fun `growing is undone by simply not applying it any more`() {
        // The whole point of doing this on the way to the screen: the stored sizes are still the stored
        // sizes, so the moment the music stops the tile is back at exactly the size it was.
        val grown = TileGrowth.apply(order, setOf(tile("b")))
        assertEquals(TileSize.WIDE, grown[1].size)
        assertEquals(order, TileGrowth.apply(order, emptySet()))
    }

    @Test
    fun `a hand-set size is not a reason to refuse to grow, and is not written to`() {
        // TileGrowth never sees manualSizes at all: it is a picture, and the size someone set by hand is
        // still in the store, untouched, waiting for the music to stop. (AutoSize is the thing that
        // writes, and it is the thing manualSizes protects against.)
        val handSet = listOf(sized("a", TileSize.SMALL))
        val out = TileGrowth.apply(handSet, setOf(tile("a")))
        assertEquals(TileSize.MEDIUM, out.single().size)
        assertEquals(TileSize.SMALL, handSet.single().size)
    }

    @Test
    fun `two tiles can be busy at once`() {
        // Music playing while a slideshow runs is two grown tiles, not a fight over one slot.
        val out = TileGrowth.apply(order, setOf(tile("a"), tile("c")))
        assertEquals(listOf(TileSize.WIDE, TileSize.MEDIUM, TileSize.WIDE), out.map { it.size })
    }

    @Test
    fun `the bottom row is left alone because its tiles are not in the grid order`() {
        val dockKey: TileKey = TileKey.SlotTile(Slot.PHONE)
        assertSame(order, TileGrowth.apply(order, setOf(dockKey)))
    }

    @Test
    fun `an empty grid is not a special case`() {
        val empty = emptyList<Sized>()
        assertSame(empty, TileGrowth.apply(empty, setOf(tile("a"))))
    }

    @Test
    fun `a process that died mid-song leaves nothing behind, because growth is not stored`() {
        // ActiveTiles comes back empty after a process death (it is in memory only), and an empty set is
        // the identity of this transform: the first frame Start draws is the stored layout.
        assertSame(order, TileGrowth.apply(order, emptySet()))
    }
}

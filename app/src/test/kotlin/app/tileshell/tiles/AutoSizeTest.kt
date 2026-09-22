package app.tileshell.tiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tile size follows use (INDEX Change Log 2026-09-21 item 1).
 *
 * The policy rearranges someone's Start screen on its own, so it is tested before it is trusted: that
 * it forgets, that it does nothing without evidence, that it never overrides a size set by hand, and
 * that it does not oscillate.
 */
class AutoSizeTest {

    private val day = 24L * 60 * 60 * 1000

    /**
     * A tile that participates, built WITHOUT [ComponentName]. In a plain JVM unit test the android.jar
     * is the stub, so `ComponentName.flattenToString()` returns null and every `AppTile.id` collapses to
     * the same string — which silently made three of these tests pass the wrong thing before this
     * comment existed. [TileKey.SecondaryTile]'s id is pure Kotlin and it participates the same way.
     */
    private fun app(name: String) = TileKey.SecondaryTile("p.$name", "t")
    private fun sized(name: String, size: TileSize) = Sized(app(name), size)

    // ---- forgetting ----

    @Test
    fun `a score halves over one half-life`() {
        assertEquals(5f, AutoSize.decay(10f, AutoSize.HALF_LIFE_MS), 0.01f)
        assertEquals(2.5f, AutoSize.decay(10f, 2 * AutoSize.HALF_LIFE_MS), 0.01f)
        assertEquals(10f, AutoSize.decay(10f, 0L), 0.001f)
    }

    @Test
    fun `an unused tile decays towards nothing, so it shrinks on its own`() {
        val after = AutoSize.decay(40f, 10 * AutoSize.HALF_LIFE_MS)
        assertTrue("$after should be tiny", after < 0.05f)
    }

    @Test
    fun `a negative or zero score never goes anywhere`() {
        assertEquals(0f, AutoSize.decay(0f, 5 * day), 0.001f)
        assertEquals(0f, AutoSize.decay(-3f, 5 * day), 0.001f)
    }

    // ---- evidence ----

    @Test
    fun `a fresh install is left exactly as the default layout put it`() {
        val order = listOf(sized("a", TileSize.MEDIUM), sized("b", TileSize.WIDE), sized("c", TileSize.SMALL))
        val scores = mapOf(app("a").id to 2f, app("b").id to 1f)
        assertSame(order, AutoSize.apply(order, scores, emptySet()))
    }

    @Test
    fun `it starts working once the busiest tile has real use`() {
        val order = listOf(sized("a", TileSize.SMALL), sized("b", TileSize.MEDIUM))
        val scores = mapOf(app("a").id to 20f, app("b").id to 0f)
        val out = AutoSize.apply(order, scores, emptySet())
        assertEquals(TileSize.WIDE, out[0].size)
        assertEquals(TileSize.SMALL, out[1].size)
    }

    // ---- a size set by hand wins ----

    @Test
    fun `a manually sized tile is never touched, however much or little it is used`() {
        val order = listOf(sized("a", TileSize.SMALL), sized("b", TileSize.WIDE))
        val scores = mapOf(app("a").id to 100f, app("b").id to 0f)
        val out = AutoSize.apply(order, scores, setOf(app("a").id, app("b").id))
        assertEquals(TileSize.SMALL, out[0].size)
        assertEquals(TileSize.WIDE, out[1].size)
    }

    @Test
    fun `a manually sized tile does not even set the scale others are measured against`() {
        // 'big' is pinned and by far the most used. If it counted, everything else would look tiny.
        val order = listOf(sized("big", TileSize.SMALL), sized("a", TileSize.SMALL), sized("b", TileSize.SMALL))
        val scores = mapOf(app("big").id to 1000f, app("a").id to 20f, app("b").id to 19f)
        val out = AutoSize.apply(order, scores, setOf(app("big").id))
        assertEquals(TileSize.SMALL, out[0].size)
        assertEquals(TileSize.WIDE, out[1].size)
        assertEquals(TileSize.WIDE, out[2].size)
    }

    // ---- shell tiles, folders and the dock keep their own sizes ----

    @Test
    fun `only tiles that stand for something you open take part`() {
        assertTrue(AutoSize.participates(app("a")))
        assertTrue(AutoSize.participates(TileKey.SlotTile(Slot.PHONE)))
        assertTrue(AutoSize.participates(TileKey.SecondaryTile("p", "t")))
        assertTrue(!AutoSize.participates(TileKey.ShellTile("weather")))
        assertTrue(!AutoSize.participates(TileKey.FolderTile("f1")))
    }

    @Test
    fun `a shell tile keeps its size even when everything around it is resized`() {
        val order = listOf(
            Sized(TileKey.ShellTile("weather"), TileSize.WIDE),
            sized("a", TileSize.SMALL),
        )
        val out = AutoSize.apply(order, mapOf(app("a").id to 30f), emptySet())
        assertEquals(TileSize.WIDE, out[0].size)
        assertEquals(TileSize.WIDE, out[1].size)
    }

    // ---- hysteresis ----

    @Test
    fun `a tile on a boundary keeps the size it has`() {
        // Between SHRINK_WIDE and GROW_WIDE: a wide tile stays wide, a medium one stays medium.
        val between = (AutoSize.SHRINK_WIDE + AutoSize.GROW_WIDE) / 2f
        assertEquals(TileSize.WIDE, AutoSize.sizeFor(between, TileSize.WIDE))
        assertEquals(TileSize.MEDIUM, AutoSize.sizeFor(between, TileSize.MEDIUM))
        val small = (AutoSize.SHRINK_MEDIUM + AutoSize.GROW_MEDIUM) / 2f
        assertEquals(TileSize.MEDIUM, AutoSize.sizeFor(small, TileSize.MEDIUM))
        assertEquals(TileSize.SMALL, AutoSize.sizeFor(small, TileSize.SMALL))
    }

    @Test
    fun `a share that keeps wobbling across one threshold never flips the tile`() {
        var size = TileSize.MEDIUM
        val jitter = listOf(0.59f, 0.61f, 0.58f, 0.62f, 0.57f)
        // Crossing GROW_WIDE once is enough to grow, and it then STAYS wide through the same wobble,
        // because coming back down needs SHRINK_WIDE, not GROW_WIDE.
        for (share in jitter) size = AutoSize.sizeFor(share, size)
        assertEquals(TileSize.WIDE, size)
        for (share in jitter) size = AutoSize.sizeFor(share, size)
        assertEquals(TileSize.WIDE, size)
    }

    @Test
    fun `a tile that really stops being used falls all the way back to small`() {
        var size = TileSize.WIDE
        for (share in listOf(0.40f, 0.20f, 0.05f)) size = AutoSize.sizeFor(share, size)
        assertEquals(TileSize.SMALL, size)
    }

    // ---- the wide cap ----

    @Test
    fun `no more than MAX_WIDE tiles go wide, and the busiest keep it`() {
        val order = (1..5).map { sized("a$it", TileSize.SMALL) }
        val scores = (1..5).associate { app("a$it").id to (100f - it) }
        val out = AutoSize.apply(order, scores, emptySet())
        assertEquals(AutoSize.MAX_WIDE, out.count { it.size == TileSize.WIDE })
        assertEquals(TileSize.WIDE, out[0].size)
        assertEquals(TileSize.WIDE, out[1].size)
        assertEquals(TileSize.MEDIUM, out[2].size)
    }

    @Test
    fun `the order itself is never changed, only the sizes`() {
        val order = (1..6).map { sized("a$it", TileSize.SMALL) }
        val scores = (1..6).associate { app("a$it").id to (60f - it * 5f) }
        val out = AutoSize.apply(order, scores, emptySet())
        assertEquals(order.map { it.key }, out.map { it.key })
    }
}

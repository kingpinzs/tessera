package app.tileshell.start

import app.tileshell.tiles.Slot
import app.tileshell.tiles.TileKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jeremy 2026-09-25, "(a)": a burst hides while its own tile is dragged and comes back where the tile is dropped. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickBurstDragTest {
    private val held: TileKey = TileKey.SlotTile(Slot.BROWSER)
    private val other: TileKey = TileKey.SlotTile(Slot.PHONE)
    private val rect = QRect(0f, 0f, 100f, 100f)

    private fun opened(key: TileKey = held) = QuickBurstState().apply {
        open(OpenBurst(key, emptyList(), BurstLayout(Arrangement.values().first(), emptyList()), rect))
    }

    @Test fun theHeldTilesOwnDragHidesTheBurstAndTheDropBringsItBack() {
        val q = opened()
        q.hideForDrag(held)
        assertNull("nothing drawn during the drag", q.burst)
        assertNotNull(q.hiddenForDrag)
        assertFalse("hidden is not open", q.active)
        q.showAfterDrop(held)
        assertNull(q.hiddenForDrag)
        val p = q.pending
        assertNotNull("the drop reopens it through the opener", p)
        assertEquals(held, p!!.key)
        assertTrue("with the satellites it already had", p.load.isCompleted && p.load.getCompleted() is QuickLoad.Ready)
    }

    @Test fun aDragOfAnotherTileClosesTheBurst() {
        val q = opened()
        q.hideForDrag(other)
        assertEquals("drag", q.closing)
        assertNull(q.hiddenForDrag)
    }

    @Test fun aDropThatLeavesAnotherTileHeldDoesNotBringItBack() {
        val q = opened()
        q.hideForDrag(held)
        q.showAfterDrop(other)
        assertNull(q.pending)
        assertNull(q.hiddenForDrag)
        assertNull(q.burst)
    }

    @Test fun aCloseWhileHiddenForgetsIt() {
        val q = opened()
        q.hideForDrag(held)
        q.close(CloseReason.HOME)
        assertNull(q.hiddenForDrag)
        q.showAfterDrop(held)
        assertNull("nothing comes back after it was closed", q.pending)
    }

    @Test fun aLoadStillInFlightIsCarriedAcrossTheDrag() {
        val q = QuickBurstState()
        val load = CompletableDeferred<QuickLoad>()
        q.pending = PendingBurst(held, load)
        q.hideForDrag(held)
        assertNull(q.pending)
        assertFalse("the load is not cancelled", load.isCancelled)
        q.showAfterDrop(held)
        assertSame("the same load, answered later, opens it", load, q.pending!!.load)
    }
}

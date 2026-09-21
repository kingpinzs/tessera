package app.tileshell.tiles.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The hold rule of the pin confirmation (phase 02 Decisions: "a request while Start is not visible or the requester
 * is in the background is held until Start is next shown"; edge case "request while Start is not visible or the
 * requester is in the background").
 */
class SecondaryPinQueueTest {
    private data class Req(val id: String, val what: String = "")

    private fun queue(capacity: Int = SecondaryPinQueue.MAX_PENDING) = SecondaryPinQueue<Req>({ it.id }, capacity)

    private fun presentable(offer: SecondaryPinQueue.Offer<Req>) = (offer as SecondaryPinQueue.Offer.Queued).presentable

    @Test fun aRequestWhileStartIsNotVisibleIsShownWhenStartIsNextShown() {
        val q = queue()
        assertNull(presentable(q.offer(Req("a")))) // the user is in the app that asked
        assertEquals(Req("a"), q.onStartVisible(true)) // they press Home
    }

    @Test fun aRequestFromTheBackgroundDoesNotInterruptTheShowingItDidNotCause() {
        val q = queue()
        q.onStartVisible(true)
        assertNull(presentable(q.offer(Req("a")))) // arrives while the user is already on Start: held
        assertNull(q.presentable())
        q.onStartVisible(false)
        assertNull(q.presentable()) // never drawn over another app
        assertEquals(Req("a"), q.onStartVisible(true)) // the next showing of Start
    }

    @Test fun nothingIsEverPresentedWhileStartIsNotVisible() {
        val q = queue()
        q.offer(Req("a"))
        assertNull(q.presentable())
        q.onStartVisible(true)
        q.onStartVisible(false)
        assertNull(q.presentable())
    }

    @Test fun requestsAreAnsweredOneAtATimeInOrder() {
        val q = queue()
        q.offer(Req("a"))
        q.offer(Req("b"))
        assertEquals(Req("a"), q.onStartVisible(true))
        assertEquals(Req("b"), q.remove("a")) // "a" answered: "b" is next, in the same showing
        assertNull(q.remove("b"))
    }

    /** A duplicate tileId replaces the waiting request and keeps its place, so one tile never queues twice. */
    @Test fun aSecondRequestForTheSameTileReplacesTheFirst() {
        val q = queue()
        q.offer(Req("a", "first"))
        q.offer(Req("b"))
        q.offer(Req("a", "second"))
        assertEquals(listOf(Req("a", "second"), Req("b")), q.waiting())
        assertEquals(Req("a", "second"), q.onStartVisible(true))
    }

    @Test fun aFullQueueRefusesRatherThanDroppingSomething() {
        val q = queue(capacity = 2)
        q.offer(Req("a"))
        q.offer(Req("b"))
        assertSame(SecondaryPinQueue.Offer.Full, q.offer(Req("c")))
        assertEquals(listOf(Req("a"), Req("b")), q.waiting())
        // A replacement of something already waiting still goes through.
        q.offer(Req("b", "again"))
        assertEquals(listOf(Req("a"), Req("b", "again")), q.waiting())
    }

    @Test fun removeIfDropsAnOwnersRequestsAndMovesOn() {
        val q = queue()
        q.offer(Req("gone.1"))
        q.offer(Req("gone.2"))
        q.offer(Req("here.1"))
        q.onStartVisible(true)
        val (dropped, next) = q.removeIf { it.id.startsWith("gone.") }
        assertEquals(listOf(Req("gone.1"), Req("gone.2")), dropped)
        assertEquals(Req("here.1"), next)
    }

    @Test fun aRequestSurvivesStartComingAndGoingUntilItIsAnswered() {
        val q = queue()
        q.offer(Req("a"))
        assertEquals(Req("a"), q.onStartVisible(true))
        q.onStartVisible(false)
        assertNull(q.presentable())
        assertEquals(Req("a"), q.onStartVisible(true)) // still waiting: nothing is dropped
        assertNull(q.remove("a"))
    }
}

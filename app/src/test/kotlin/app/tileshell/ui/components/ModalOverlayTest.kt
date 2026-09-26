package app.tileshell.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L13-2: the two decisions an overlay inside a pivot page makes about a gesture that starts on it — an item's press
 * (held while the finger stays on it, ended for good when it leaves, run only by a lift on it; E7(d)) and the root's
 * tap off the items (only a tap, never a drag, dismisses). The pointer plumbing is proved on the device
 * (qa/phase-13/scripts/l13_2_row.sh); these pin the rules it runs.
 */
class ModalOverlayTest {

    // ---- an item's press

    @Test
    fun aLiftOnTheItemRunsIt() {
        val press = OverlayItemPress()
        assertTrue(press.pressed)
        assertTrue(press.lift(onItem = true))
        assertFalse(press.pressed)
    }

    @Test
    fun aMoveWithinTheItemKeepsThePressAndTheLiftRunsIt() {
        val press = OverlayItemPress()
        press.move(onItem = true)
        press.move(onItem = true)
        assertTrue(press.pressed)
        assertTrue(press.lift(onItem = true))
    }

    @Test
    fun leavingTheItemEndsThePressForGood() {
        val press = OverlayItemPress()
        press.move(onItem = false)
        assertFalse(press.pressed)
        press.move(onItem = true)
        assertFalse("coming back does not press it again", press.pressed)
        assertFalse("and a lift on it runs nothing", press.lift(onItem = true))
    }

    @Test
    fun aLiftOffTheItemRunsNothing() {
        val press = OverlayItemPress()
        assertFalse(press.lift(onItem = false))
    }

    @Test
    fun aCancelledPressRunsNothing() {
        val press = OverlayItemPress()
        press.cancel()
        assertFalse(press.pressed)
        assertFalse(press.lift(onItem = true))
    }

    // ---- the root's tap off the items

    @Test
    fun aTapOnTheRootDismisses() {
        val tap = OverlayRootTap(downTakenByItem = false, slopPx = 20f)
        assertTrue(tap.lift())
    }

    @Test
    fun aJitterWithinTouchSlopIsStillATap() {
        val tap = OverlayRootTap(downTakenByItem = false, slopPx = 20f)
        tap.move(dx = 12f, dy = -9f)
        assertTrue(tap.lift())
    }

    @Test
    fun aDragOnTheRootNeverDismisses() {
        val tap = OverlayRootTap(downTakenByItem = false, slopPx = 20f)
        tap.move(dx = 60f, dy = 0f)
        tap.move(dx = 0f, dy = 0f)
        assertFalse("a drag that comes back is still a drag", tap.lift())
    }

    @Test
    fun aGestureAnItemTookNeverDismisses() {
        val tap = OverlayRootTap(downTakenByItem = true, slopPx = 20f)
        assertFalse(tap.lift())
    }
}

package app.tileshell.tiles

import app.tileshell.tiles.SlotSeed.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 16 build task 1: the one-time slot seed's guard (a pick made by hand is kept) and the take-over the owner ruled
 * for the shell's Calendar and People (Q-16-1, 2026-09-30: once, even over a hand pick, naming what was replaced).
 */
class SlotSeedTest {
    private val shell = "app.tileshell/.calendar.CalendarActivity"
    private val picked = "com.android.calendar/.AllInOneActivity"

    @Test
    fun `a marker that has run writes nothing, whatever else is true`() {
        assertEquals(Outcome.AlreadyRun, SlotSeed.decide(markerHasRun = true, current = null, component = shell, takeOver = false))
        assertEquals(Outcome.AlreadyRun, SlotSeed.decide(markerHasRun = true, current = picked, component = shell, takeOver = true))
        assertFalse(SlotSeed.writesSlot(Outcome.AlreadyRun))
    }

    @Test
    fun `an unassigned slot is seeded`() {
        val outcome = SlotSeed.decide(markerHasRun = false, current = null, component = shell, takeOver = false)
        assertEquals(Outcome.Assigned, outcome)
        assertTrue(SlotSeed.writesSlot(outcome))
    }

    @Test
    fun `the guard keeps a pick made by hand`() {
        // The hole phase 16 closes: the seed used to write over this (LayoutStore.assignSlotOnce checked only the marker).
        val outcome = SlotSeed.decide(markerHasRun = false, current = picked, component = shell, takeOver = false)
        assertEquals(Outcome.KeptUsers(picked), outcome)
        assertFalse(SlotSeed.writesSlot(outcome))
    }

    @Test
    fun `take-over writes over a hand pick once and names what it replaced`() {
        val outcome = SlotSeed.decide(markerHasRun = false, current = picked, component = shell, takeOver = true)
        assertEquals(Outcome.AssignedReplacing(picked), outcome)
        assertTrue(SlotSeed.writesSlot(outcome))
        // Pointed back by hand afterwards, the marker has run: the pick stays.
        assertEquals(Outcome.AlreadyRun, SlotSeed.decide(markerHasRun = true, current = picked, component = shell, takeOver = true))
    }

    @Test
    fun `take-over of a slot with no pick is a plain assignment`() {
        assertEquals(Outcome.Assigned, SlotSeed.decide(markerHasRun = false, current = null, component = shell, takeOver = true))
    }

    @Test
    fun `a slot already pointed at the shell's app is a plain assignment under either rule`() {
        assertEquals(Outcome.Assigned, SlotSeed.decide(markerHasRun = false, current = shell, component = shell, takeOver = false))
        assertEquals(Outcome.Assigned, SlotSeed.decide(markerHasRun = false, current = shell, component = shell, takeOver = true))
    }
}

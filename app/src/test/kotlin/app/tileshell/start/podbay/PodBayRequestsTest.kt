package app.tileshell.start.podbay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 14, owner ruling 2026-09-30 (gate review r1 S2): a pending pod-bay request is dropped when any other request is
 * made and lapses 30 s after it was made — except while the setup wizard holds it (T14-7).
 */
class PodBayRequestsTest {
    private var now = 1_000L
    private val open = PodBayRequest(open = true, doors = true)

    @Before
    fun setUp() {
        PodBayRequests.clock = { now }
        PodBayRequests.dropFor("test setup")
    }

    @After
    fun tearDown() {
        PodBayRequests.dropFor("test teardown")
    }

    @Test
    fun `a request is taken once, within its time`() {
        PodBayRequests.record(open)
        now += 7_000
        assertTrue(PodBayRequests.hasPending())
        assertEquals(open, PodBayRequests.take())
        assertNull(PodBayRequests.take())
        assertNull(PodBayRequests.pending.value)
    }

    @Test
    fun `a request lapses thirty seconds after it was made`() {
        PodBayRequests.record(open)
        now += PodBayRequests.EXPIRES_AFTER_MS
        assertTrue("still live at exactly 30 s", PodBayRequests.hasPending())
        now += 1
        assertFalse(PodBayRequests.hasPending())
        assertNull(PodBayRequests.take())
        assertNull(PodBayRequests.pending.value)
    }

    @Test
    fun `the wizard holds a request for as long as it shows`() {
        PodBayRequests.record(open)
        now += 3_000
        PodBayRequests.waitForWizard()
        now += 20 * 60_000
        assertTrue(PodBayRequests.hasPending())
        assertEquals(open, PodBayRequests.take())
    }

    @Test
    fun `the wizard cannot revive a request that has already lapsed`() {
        PodBayRequests.record(open)
        now += PodBayRequests.EXPIRES_AFTER_MS + 1
        PodBayRequests.waitForWizard()
        assertNull(PodBayRequests.take())
    }

    @Test
    fun `another request drops the pending one`() {
        PodBayRequests.record(open)
        PodBayRequests.dropFor("another request")
        assertFalse(PodBayRequests.hasPending())
        assertNull(PodBayRequests.take())
    }

    @Test
    fun `a new pod-bay request replaces the pending one and restarts its time`() {
        PodBayRequests.record(open)
        now += 25_000
        val close = PodBayRequest(open = false)
        PodBayRequests.record(close)
        now += 25_000
        assertEquals(close, PodBayRequests.take())
    }
}

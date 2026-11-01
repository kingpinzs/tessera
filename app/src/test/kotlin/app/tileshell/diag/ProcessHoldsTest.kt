package app.tileshell.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3 (a): a ring held for its process's whole life ([RemoteRings.holdForProcess]) is bound once — the viewer opened again
 * in a warm `:photosedit` binds nothing more — and a bind the platform refused is tried again at the next open.
 */
class ProcessHoldsTest {
    @Test fun `the first open binds, every later open binds nothing`() {
        val holds = ProcessHolds()
        var binds = 0
        assertTrue(holds.hold("app.tileshell.photos.PhotosEditDumpService") { binds++; true })
        assertFalse(holds.hold("app.tileshell.photos.PhotosEditDumpService") { binds++; true })
        assertFalse(holds.hold("app.tileshell.photos.PhotosEditDumpService") { binds++; true })
        assertEquals(1, binds)
    }

    @Test fun `a refused bind is not counted as held, so the next open binds again`() {
        val holds = ProcessHolds()
        var binds = 0
        assertFalse(holds.hold("ring") { binds++; false })
        assertTrue(holds.hold("ring") { binds++; true })
        assertFalse(holds.hold("ring") { binds++; true })
        assertEquals(2, binds)
    }

    @Test fun `each service is held on its own`() {
        val holds = ProcessHolds()
        assertTrue(holds.hold("a") { true })
        assertTrue(holds.hold("b") { true })
        assertFalse(holds.hold("a") { true })
    }
}

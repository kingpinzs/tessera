package app.tileshell.cortana.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 05's "one engine, one microphone" rules, which the device rarely gets to exercise (review M5). */
class MicArbiterTest {
    private var gen = 0
    private fun next() = ++gen

    @Test fun `a free microphone goes to whoever asks`() {
        val m = MicArbiter<String>()
        assertEquals(1, m.acquire("keyboard", 100, ::next))
        assertEquals("keyboard", m.holder())
        assertEquals(100, m.ownerPid)
    }

    @Test fun `a second client is refused while the first listens, and the first keeps it`() {
        val m = MicArbiter<String>()
        m.acquire("keyboard", 100, ::next)
        assertNull(m.acquire("cortana", 200, ::next))
        assertEquals("keyboard", m.holder())
        assertEquals("the refusal claims no generation (no capture is interrupted)", 1, gen)
    }

    @Test fun `the holder starting again replaces its own capture`() {
        val m = MicArbiter<String>()
        m.acquire("cortana", 200, ::next)
        assertEquals(2, m.acquire("cortana", 200, ::next))
        assertEquals("cortana", m.holder())
    }

    @Test fun `only the holder can release it`() {
        val m = MicArbiter<String>()
        m.acquire("keyboard", 100, ::next)
        assertFalse(m.release("cortana"))
        assertEquals("keyboard", m.holder())
        assertTrue(m.release("keyboard"))
        assertNull(m.holder())
        assertEquals(-1, m.ownerPid)
    }

    @Test fun `a finished capture frees it only if it is the current one`() {
        val m = MicArbiter<String>()
        val first = m.acquire("cortana", 200, ::next)!!
        val second = m.acquire("cortana", 200, ::next)!!
        m.finished(first)
        assertEquals("an old capture ending must not free the newer one", "cortana", m.holder())
        m.finished(second)
        assertNull(m.holder())
    }

    @Test fun `after release the other client gets it`() {
        val m = MicArbiter<String>()
        m.acquire("keyboard", 100, ::next)
        m.release("keyboard")
        assertEquals(2, m.acquire("cortana", 200, ::next))
        assertEquals(200, m.ownerPid)
    }
}

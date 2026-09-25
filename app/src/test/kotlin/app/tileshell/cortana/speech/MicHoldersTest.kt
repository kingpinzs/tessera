package app.tileshell.cortana.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 15's third microphone owner, named across the binder (T15-4 / T15-28): the arbiter keeps each
 * holder's name, every refusal reports it, and both refused sides word their notice from it.
 */
class MicHoldersTest {
    private var gen = 0
    private fun next() = ++gen

    @Test fun `the holder's name is kept beside its key`() {
        val m = MicArbiter<String>()
        m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next)
        assertEquals(MicHolders.RECORDER, m.holderName())
        assertEquals(300, m.ownerPid)
    }

    @Test fun `a free microphone has no holder name`() {
        assertNull(MicArbiter<String>().holderName())
    }

    @Test fun `Tess is refused while the recorder holds it, and the refusal names the recorder`() {
        val m = MicArbiter<String>()
        m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next)
        assertNull(m.acquire("tess-binder", 100, MicHolders.CORTANA, ::next))
        assertEquals(MicHolders.RECORDER, m.holderName())
        assertEquals("rec-binder", m.holder())
    }

    @Test fun `the keyboard is refused while the recorder holds it`() {
        val m = MicArbiter<String>()
        m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next)
        assertNull(m.acquire("ime-binder", 200, MicHolders.KEYBOARD, ::next))
        assertEquals(MicHolders.RECORDER, m.holderName())
    }

    @Test fun `the recorder is refused while Tess listens, and the refusal names Tess`() {
        val m = MicArbiter<String>()
        m.acquire("tess-binder", 100, MicHolders.CORTANA, ::next)
        assertNull(m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next))
        assertEquals(MicHolders.CORTANA, m.holderName())
    }

    @Test fun `the recorder is refused while the keyboard listens`() {
        val m = MicArbiter<String>()
        m.acquire("ime-binder", 200, MicHolders.KEYBOARD, ::next)
        assertNull(m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next))
        assertEquals(MicHolders.KEYBOARD, m.holderName())
    }

    @Test fun `the recorder's hold survives Tess's refused attempt and frees on its release`() {
        val m = MicArbiter<String>()
        m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next)
        m.acquire("tess-binder", 100, MicHolders.CORTANA, ::next)
        assertEquals(true, m.release("rec-binder"))
        assertNull(m.holderName())
        assertEquals("the refused attempt claimed no generation", 2, m.acquire("tess-binder", 100, MicHolders.CORTANA, ::next))
        assertEquals(MicHolders.CORTANA, m.holderName())
    }

    @Test fun `a dead holder's release (onCallbackDied) frees the name too`() {
        val m = MicArbiter<String>()
        m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next)
        m.release("rec-binder")
        assertNull(m.holder())
        assertNull(m.holderName())
        assertEquals(-1, m.ownerPid)
    }

    @Test fun `a hold is never freed by a capture generation finishing`() {
        val m = MicArbiter<String>()
        val tess = m.acquire("tess-binder", 100, MicHolders.CORTANA, ::next)!!
        m.finished(tess)
        val hold = m.acquire("rec-binder", 300, MicHolders.RECORDER, ::next)!!
        m.finished(tess) // the old capture's late finish
        assertEquals(MicHolders.RECORDER, m.holderName())
        assertEquals(hold, gen)
    }

    @Test fun `the unnamed acquire still works and names nobody`() {
        val m = MicArbiter<String>()
        m.acquire("keyboard", 100, ::next)
        assertEquals("", m.holderName())
    }

    @Test fun `the owner name follows the process`() {
        assertEquals(MicHolders.KEYBOARD, MicHolders.forProcess("app.tileshell:ime"))
        assertEquals(MicHolders.CORTANA, MicHolders.forProcess("app.tileshell"))
        assertEquals(MicHolders.CORTANA, MicHolders.forProcess(null))
    }

    @Test fun `the busy detail carries the holder and reads back`() {
        assertEquals("held by recorder", MicHolders.busyDetail(MicHolders.RECORDER))
        assertEquals(MicHolders.RECORDER, MicHolders.holderOf(MicHolders.busyDetail(MicHolders.RECORDER)))
        assertEquals(MicHolders.KEYBOARD, MicHolders.holderOf("held by keyboard"))
        assertEquals("unknown", MicHolders.holderOf(MicHolders.busyDetail(null)))
        assertNull(MicHolders.holderOf("the microphone is in use by another part of the shell"))
        assertNull(MicHolders.holderOf(null))
    }

    @Test fun `each refused side words its notice from the holder`() {
        assertEquals("The keyboard is using the microphone right now.", MicHolders.busySentence("keyboard", "Tess"))
        assertEquals("The voice recorder is using the microphone right now.", MicHolders.busySentence("recorder", "Tess"))
        assertEquals("Tess is using the microphone right now.", MicHolders.busySentence("cortana", "Tess"))
        assertEquals("Something else is using the microphone right now.", MicHolders.busySentence(null, "Tess"))
    }

    @Test fun `the recorder's own notice says Tess is listening`() {
        assertEquals("Tess is listening", MicHolders.recorderNotice("cortana", "Tess"))
        assertEquals("The keyboard is using the microphone right now.", MicHolders.recorderNotice("keyboard", "Tess"))
    }
}

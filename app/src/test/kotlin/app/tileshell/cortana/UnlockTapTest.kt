package app.tileshell.cortana

import android.content.ContextWrapper
import android.content.Intent
import app.tileshell.apps.AppEntry
import app.tileshell.cortana.action.ActionHost
import app.tileshell.cortana.action.ActionLayer
import app.tileshell.cortana.action.LockGate
import app.tileshell.cortana.action.Pending
import app.tileshell.cortana.match.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * L14-1 (Jeremy's Q2 (a), 2026-09-30): the "Unlock to continue" card's Unlock button keeps the card on screen and says
 * nothing new — the line was said when the card appeared. The card must still be there when a cancelled unlock brings
 * Tess back (phase 03 Decisions: "a cancelled unlock does nothing"), and phase 14 E8 reads the first reply after the tap
 * as the doors line.
 */
class UnlockTapTest {

    private var unlockRequests = 0

    private val host = object : ActionHost {
        override fun launch(intent: Intent) = Unit
        override fun launchApp(entry: AppEntry) = Unit
        override fun requestUnlock() {
            unlockRequests++
        }
        override fun requestMicrophone() = Unit
    }

    private val actions = ActionLayer(ContextWrapper(null), host)

    private val request = Request.OpenPodBay(doors = true)

    @Test
    fun `the tap keeps the unlock card on screen`() {
        val outcome = actions.confirm(Pending.Locked(request))
        assertEquals(
            Card(
                kind = CardKind.UNLOCK,
                title = "Unlock to continue",
                caption = LockGate.restate(request),
                buttons = listOf(CardButton("Unlock", CardAction.UNLOCK)),
            ),
            outcome.card,
        )
    }

    @Test
    fun `the tap says nothing new`() {
        assertEquals("", actions.confirm(Pending.Locked(request)).spoken)
    }

    @Test
    fun `the tap raises the unlock prompt once and keeps the request pending`() {
        val pending = Pending.Locked(request)
        val outcome = actions.confirm(pending)
        assertEquals(1, unlockRequests)
        assertEquals(pending, outcome.pending)
        assertNull(outcome.awaiting)
        assertFalse(outcome.close)
    }
}

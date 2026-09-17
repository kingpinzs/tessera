package app.tileshell.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppRulesTest {
    private val baseline = 1_000_000L

    @Test
    fun appsPresentAtBaselineCarryNoCaption() {
        assertEquals(NewState.SEEN, NewAppRules.atBaseline(baseline + 50).state)
        assertEquals(NewState.SEEN, NewAppRules.atBaseline(5).state)
    }

    @Test
    fun appInstalledAfterBaselineIsNew() {
        val rec = NewAppRules.decide(null, isSystem = false, firstInstallTime = baseline + 10, baselineMs = baseline)
        assertEquals(NewState.NEW, rec.state)
    }

    @Test
    fun systemAppsNeverCarryIt() {
        assertEquals(NewState.SEEN, NewAppRules.decide(null, true, baseline + 10, baseline).state)
        val stale = InstallRecord(baseline + 10, NewState.NEW)
        assertEquals(NewState.SEEN, NewAppRules.decide(stale, true, baseline + 10, baseline).state)
    }

    @Test
    fun appInstalledBeforeBaselineButFirstSeenLaterIsNotNew() {
        // e.g. a private-space app that was hidden when the baseline was taken
        assertEquals(NewState.SEEN, NewAppRules.decide(null, false, baseline - 1, baseline).state)
        assertEquals(NewState.SEEN, NewAppRules.decide(null, false, 0L, baseline).state)
    }

    @Test
    fun updateKeepsState() {
        val newRec = InstallRecord(baseline + 10, NewState.NEW)
        assertSame(newRec, NewAppRules.decide(newRec, false, baseline + 10, baseline))
        val seenRec = InstallRecord(baseline + 10, NewState.SEEN)
        assertSame(seenRec, NewAppRules.decide(seenRec, false, baseline + 10, baseline))
    }

    @Test
    fun reinstallIsANewInstall() {
        val launched = InstallRecord(baseline + 10, NewState.SEEN)
        assertEquals(InstallRecord(baseline + 99, NewState.NEW), NewAppRules.decide(launched, false, baseline + 99, baseline))
    }

    @Test
    fun launchClearsAndNoTimerClears() {
        val rec = InstallRecord(baseline + 10, NewState.NEW)
        assertEquals(NewState.SEEN, NewAppRules.afterLaunch(rec).state)
        // Three days later, with no launch, reconciling keeps the caption.
        val threeDays = 3L * 24 * 60 * 60 * 1000
        assertEquals(NewState.NEW, NewAppRules.decide(rec, false, baseline + 10, baseline).state)
        assertFalse(NewAppRules.clearedByUsage(rec, baseline + 5))
        assertTrue(NewAppRules.clearedByUsage(rec, baseline + 10 + threeDays))
        assertFalse(NewAppRules.clearedByUsage(rec.copy(state = NewState.SEEN), baseline + 20))
    }

    @Test
    fun recordsRoundTripAndKeysParse() {
        val rec = InstallRecord(1234567890123L, NewState.NEW)
        assertEquals(rec, InstallRecord.decode(rec.encode()))
        assertEquals(InstallRecord(7, NewState.SEEN), InstallRecord.decode(InstallRecord(7, NewState.SEEN).encode()))
        assertNull(InstallRecord.decode("garbage"))
        assertNull(InstallRecord.decode(null))
        val key = NewAppRules.key("com.example.app", 11)
        assertEquals(11L, NewAppRules.serialOf(key))
        assertEquals("com.example.app", NewAppRules.packageOf(key))
    }
}

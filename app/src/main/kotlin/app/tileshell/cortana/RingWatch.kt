package app.tileshell.cortana

import app.tileshell.clock.RingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull

/**
 * L13-10: the rings that make Tess step aside — each ring that STARTS after she opened. RingService re-publishes a ring
 * that is already up whenever its surface changes (the screen going off, the keyguard going away), so a new value is not
 * a new ring: a ring is new when its ids are. The first value is what was ringing when she opened, and is skipped.
 */
internal fun Flow<RingService.Ring?>.ringsStartingAfterFirst(): Flow<RingService.Ring> =
    distinctUntilChangedBy { it?.ids }.drop(1).filterNotNull()

package app.tileshell.cortana

import app.tileshell.clock.RingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull

/**
 * L13-10: the rings that make Tess step aside — every ring while she is shown, including one already ringing when she
 * opens (Jeremy, 2026-09-27: "The alarm should go over top"). RingService re-publishes a ring that is already up whenever
 * its surface changes (the screen going off, the keyguard going away), so a new value is not a new ring: a ring is new
 * when its ids are.
 */
internal fun Flow<RingService.Ring?>.ringsToYieldTo(): Flow<RingService.Ring> =
    distinctUntilChangedBy { it?.ids }.filterNotNull()

package app.tileshell.tiles

/**
 * What a one-time slot seed does (phase 16 build task 1), kept free of Android types so the rule is unit-tested.
 *
 * A seed points a slot at one of the shell's own apps the first time a build runs, under a marker, and never again.
 * - **The guard (the default, for every marker):** a slot the user has already pointed at an app by hand is kept. The
 *   marker is still recorded, so the seed does not come back for it on a later start. Before phase 16 the seed wrote
 *   over that pick.
 * - **Take-over (Q-16-1, the owner's ruling of 2026-09-30):** on the update that brings them, the shell's Calendar and
 *   People take their slots once, even over a pick made by hand — as Music took its slot — and the outcome names what was
 *   replaced so it can be pointed back in Settings > Tile apps. The marker is recorded in the same write, so it
 *   happens once: a slot pointed back afterwards is a hand pick like any other. Four markers pass `takeOver`: those
 *   two, and phase 17's Photos and Camera (`slot:photos:v1`, `slot:camera:v1`; the owner's ruling Q-17-3 (a)).
 */
object SlotSeed {
    sealed interface Outcome<out C> {
        /** The marker has run before: nothing is written. */
        data object AlreadyRun : Outcome<Nothing>

        /** The slot was written; it held no explicit assignment (or already held this component). */
        data object Assigned : Outcome<Nothing>

        /** The slot was written over the user's own pick, [earlier] (take-over only). */
        data class AssignedReplacing<C>(val earlier: C) : Outcome<C>

        /** The user's own pick, [kept], stays; only the marker is recorded. */
        data class KeptUsers<C>(val kept: C) : Outcome<C>
    }

    /** @param current the slot's explicit assignment now, null when it has none */
    fun <C : Any> decide(markerHasRun: Boolean, current: C?, component: C, takeOver: Boolean): Outcome<C> = when {
        markerHasRun -> Outcome.AlreadyRun
        current == null || current == component -> Outcome.Assigned
        takeOver -> Outcome.AssignedReplacing(current)
        else -> Outcome.KeptUsers(current)
    }

    /** Whether [outcome] writes the slot (the marker is recorded for every outcome but [Outcome.AlreadyRun]). */
    fun writesSlot(outcome: Outcome<*>): Boolean = outcome is Outcome.Assigned || outcome is Outcome.AssignedReplacing<*>
}

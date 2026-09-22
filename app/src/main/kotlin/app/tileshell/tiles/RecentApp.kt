package app.tileshell.tiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.diag.Diagnostics

/**
 * Which tile is currently promoted to the row above the bottom tile row ([RecentPromotion]).
 *
 * Deliberately IN MEMORY only. "The last open app" is a fact about this run of the shell: after a
 * reboot nothing is open, so a promotion read back off disk would be a lie the first time Start drew.
 * It is Compose state, so setting it redraws Start without anything having to watch a file.
 *
 * The bottom row's own apps never take the slot — Jeremy's "unless the last open app is the 3 bottom
 * apps" — and [RecentPromotion] enforces that structurally: a dock key is not in the grid order, so it
 * is simply never found there. This holds the key anyway rather than filtering it out, so the
 * diagnostics say what was actually opened.
 */
object RecentApp {
    var promoted by mutableStateOf<TileKey?>(null)
        private set

    /** A tile opened something. It takes the slot, and whatever held it goes back where it was. */
    fun opened(key: TileKey) {
        if (promoted == key) return
        promoted = key
        Diagnostics.add("start", "last open app: ${key.id} promoted above the bottom row")
    }

    /** Nothing is promoted: the grid is exactly what the layout says. */
    fun clear(reason: String) {
        if (promoted == null) return
        Diagnostics.add("start", "last open app cleared ($reason)")
        promoted = null
    }
}

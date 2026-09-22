package app.tileshell.tiles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.diag.Diagnostics

/**
 * Which tiles are BUSY right now — a song playing, a slideshow running — and therefore drawn one size
 * bigger by [TileGrowth] (INDEX Change Log 2026-09-21 items 3 and 4).
 *
 * Deliberately IN MEMORY only, for the same reason [RecentApp] is. "Music is playing" and "a slideshow
 * is running" are facts about this run of the shell, not about the layout:
 *
 *  - **If the process dies mid-song, nothing is stranded.** This comes back empty, so the first frame
 *    Start draws has every tile at its stored size. The feeds then re-read what is actually happening as
 *    they start ([app.tileshell.feeds.MusicFeed.start] reads the live sessions), so a tile grows again
 *    within a moment if the song really is still playing — and simply does not if it is not. There is no
 *    state on disk that could disagree with the world.
 *  - **Nothing to clean up.** A stored "grown" flag would need an owner to clear it, and the one moment
 *    it could not be cleared is the moment the process is killed — which is exactly when it would be wrong.
 *
 * It is Compose state, so growing or shrinking a tile redraws Start with nothing watching a file.
 */
object ActiveTiles {

    /** The tiles drawn one size bigger right now. */
    var grown by mutableStateOf<Set<TileKey>>(emptySet())
        private set

    /** [key] is busy (or is not). Only a real change redraws Start or reaches the diagnostics. */
    fun set(key: TileKey, active: Boolean, reason: String) {
        val has = key in grown
        if (has == active) return
        grown = if (active) grown + key else grown - key
        Diagnostics.add("tile_size", "${key.id} ${if (active) "grew" else "returned to its stored size"} ($reason)")
    }

    /** Everything returns to its stored size (a feed losing access, or a test starting clean). */
    fun clear(reason: String) {
        if (grown.isEmpty()) return
        Diagnostics.add("tile_size", "every grown tile returned to its stored size ($reason)")
        grown = emptySet()
    }
}

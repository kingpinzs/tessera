package app.tileshell.tiles.engine

import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live tile content, keyed by a content key: "pkg:<packageName>" for app tiles, or a shell feed key
 * ("feed:photos", "feed:calendar", "feed:music", "feed:weather"). Feeds publish; Start renders.
 * Each tile runs its own timer in the UI (R3 A8: no global scheduler).
 */
object LiveTileEngine {
    private val state = MutableStateFlow<Map<String, TileContent>>(emptyMap())
    val content: StateFlow<Map<String, TileContent>> = state.asStateFlow()

    fun packageKey(pkg: String) = "pkg:$pkg"
    const val PHOTOS = "feed:photos"
    const val CALENDAR = "feed:calendar"
    const val MUSIC = "feed:music"
    const val WEATHER = "feed:weather"

    @Synchronized
    fun publish(key: String, content: TileContent?) {
        val next = state.value.toMutableMap()
        if (content == null || content.faces.isEmpty()) next.remove(key) else next[key] = content
        state.value = next
        Diagnostics.add("engine", "publish $key faces=${content?.faces?.size ?: 0} source=${content?.sourceTag} sourceTime=${content?.sourceTimeMs}")
    }
}

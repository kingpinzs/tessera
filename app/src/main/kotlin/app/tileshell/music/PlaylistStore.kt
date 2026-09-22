package app.tileshell.music

import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where playlists live (phase 10 build task 8, the build-time call the doc asked for).
 *
 * `music_playlists.json` in the app's private files directory, written with a temp file and a rename —
 * the same shape as [app.tileshell.tiles.LayoutStore]'s `start_layout.json`, so a kill mid-write
 * leaves the previous file whole rather than half a new one. [PlaylistRules] holds every rule about
 * what may be written; this file only knows how to keep it.
 */
class PlaylistStore private constructor(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val state = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = state.asStateFlow()

    fun create(name: String = ""): String {
        val id = PlaylistRules.newId(System.currentTimeMillis(), state.value)
        mutate("create") { PlaylistRules.create(it, id, name) }
        return id
    }

    fun rename(id: String, name: String) = mutate("rename") { PlaylistRules.rename(it, id, name) }

    fun delete(id: String) = mutate("delete") { PlaylistRules.delete(it, id) }

    fun add(id: String, trackId: Long) = mutate("add") { PlaylistRules.add(it, id, trackId) }

    fun removeAt(id: String, index: Int) = mutate("remove") { PlaylistRules.removeAt(it, id, index) }

    fun move(id: String, from: Int, to: Int) = mutate("move") { PlaylistRules.move(it, id, from, to) }

    private fun mutate(what: String, block: (List<Playlist>) -> List<Playlist>) {
        val next = block(state.value)
        if (next == state.value) return
        save(next)
        state.value = next
        Diagnostics.add("music", "playlists $what: ${next.size} playlist(s), ${next.sumOf { it.size }} track(s)")
    }

    private fun save(playlists: List<Playlist>) {
        val json = JSONObject()
            .put("version", VERSION)
            .put("playlists", JSONArray().apply {
                playlists.forEach { p ->
                    put(
                        JSONObject()
                            .put("id", p.id)
                            .put("name", p.name)
                            .put("tracks", JSONArray().apply { p.trackIds.forEach { put(it) } }),
                    )
                }
            })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) error("playlist store rename failed")
    }

    private fun load(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONObject(file.readText()).optJSONArray("playlists") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("tracks") ?: JSONArray()
                Playlist(
                    id = o.getString("id"),
                    name = o.optString("name", PlaylistRules.DEFAULT_NAME),
                    trackIds = (0 until ids.length()).map { ids.getLong(it) },
                )
            }
        }.onFailure {
            // Said, not swallowed: a library that draws no playlists and a file that would not parse
            // look identical on screen, and only one of them is a fault.
            Diagnostics.add("music", "playlist store would not parse, starting empty: $it")
        }.getOrDefault(emptyList())
    }

    companion object {
        const val FILE_NAME = "music_playlists.json"
        private const val VERSION = 1

        @Volatile
        private var instance: PlaylistStore? = null

        fun get(context: Context): PlaylistStore =
            instance ?: synchronized(this) {
                instance ?: PlaylistStore(context.applicationContext).also { instance = it }
            }
    }
}

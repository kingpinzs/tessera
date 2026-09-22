package app.tileshell.tiles

import android.content.Context
import app.tileshell.diag.Diagnostics
import org.json.JSONObject
import java.io.File

/**
 * How much each tile actually gets used (INDEX Change Log 2026-09-21 item 1). One JSON file in
 * app-private storage, written atomically, the same shape [LayoutStore] uses.
 *
 * A score is not a count: it is a count that forgets. Each recorded use adds 1, and the stored value
 * is decayed to "now" whenever it is read or written, so the file holds a score and the moment it was
 * last touched rather than a running total. [AutoSize] owns the maths; this owns the storage.
 *
 * Keyed by [TileKey.id], so a slot tile, a pinned app and a secondary tile are all counted the same
 * way and a tile that moves between the grid, a folder and the bottom row keeps its history.
 */
class UseCounts private constructor(context: Context) {

    private data class Entry(val score: Float, val atMs: Long)

    private val file = File(context.filesDir, "tile_use.json")
    private val entries = HashMap<String, Entry>()

    init {
        runCatching {
            if (file.exists()) {
                val json = JSONObject(file.readText())
                val scores = json.optJSONObject("scores") ?: JSONObject()
                scores.keys().forEach { id ->
                    val e = scores.getJSONObject(id)
                    entries[id] = Entry(e.optDouble("score", 0.0).toFloat(), e.optLong("at", 0L))
                }
            }
        }.onFailure { Diagnostics.add("layout", "use counts unreadable, starting fresh: $it") }
    }

    /** One more use of [key], counted at [nowMs]. */
    @Synchronized
    fun record(key: TileKey, nowMs: Long = System.currentTimeMillis()) {
        val current = entries[key.id]
        val decayed = if (current == null) 0f else AutoSize.decay(current.score, nowMs - current.atMs)
        entries[key.id] = Entry(decayed + 1f, nowMs)
        save()
        Diagnostics.add("layout", "use ${key.id} -> ${entries[key.id]?.score}")
    }

    /** Every score, decayed to [nowMs]. */
    @Synchronized
    fun scores(nowMs: Long = System.currentTimeMillis()): Map<String, Float> =
        entries.mapValues { (_, e) -> AutoSize.decay(e.score, nowMs - e.atMs) }

    /** Drops the history of tiles that are gone, so an uninstalled app stops holding a share. */
    @Synchronized
    fun forget(ids: Set<String>) {
        if (ids.none { entries.containsKey(it) }) return
        ids.forEach { entries.remove(it) }
        save()
    }

    private fun save() {
        runCatching {
            val scores = JSONObject()
            entries.forEach { (id, e) ->
                scores.put(id, JSONObject().put("score", e.score.toDouble()).put("at", e.atMs))
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("scores", scores).toString())
            if (!tmp.renameTo(file)) error("use counts rename failed")
        }.onFailure { Diagnostics.add("layout", "use counts not saved: $it") }
    }

    companion object {
        @Volatile private var instance: UseCounts? = null

        fun get(context: Context): UseCounts =
            instance ?: synchronized(this) {
                instance ?: UseCounts(context.applicationContext).also { instance = it }
            }
    }
}

package app.tileshell.recorder

import android.content.Context
import app.tileshell.diag.Diagnostics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * `recordings.json` in the app's files directory (T15-16): what the shell knows about ITS OWN recordings that
 * the audio file does not carry — the markers, when the take was made, and how long it is before MediaStore's
 * scan says so — keyed by the MediaStore id. Markers live here and never in the file, so adding one never
 * rewrites a recording, and other apps' files carry none.
 *
 * Two processes write it: `:recorder` when a take is saved or recovered, the page when a marker is added on
 * playback, a take is trimmed, renamed or deleted. Every change is a read-modify-write under an exclusive lock
 * on a lock file beside it (a file lock holds across processes) and lands with a temp file and a rename, the
 * shape [app.tileshell.tiles.LayoutStore] uses, so a kill mid-write leaves the previous file whole.
 *
 * A Clear storage takes this file with it, which is one half of an orphaned take (T15-26): its markers go.
 */
object RecordingMetaStore {

    const val FILE_NAME = "recordings.json"
    private const val VERSION = 1

    data class Meta(val recordedAtMs: Long, val durationMs: Long, val markers: List<Long>)

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun readAll(context: Context): Map<Long, Meta> = locked(context) { load(context) }

    fun get(context: Context, id: Long): Meta? = readAll(context)[id]

    fun put(context: Context, id: Long, meta: Meta) = mutate(context, "put $id") { it[id] = meta }

    fun remove(context: Context, id: Long) = mutate(context, "remove $id") { it.remove(id) }

    /** Add a marker at [atMs] to recording [id]; @return the markers now, oldest first. */
    fun addMarker(context: Context, id: Long, atMs: Long, fallback: Meta): List<Long> {
        var result = emptyList<Long>()
        mutate(context, "marker $id") { all ->
            val current = all[id] ?: fallback
            val markers = (current.markers + atMs).sorted()
            all[id] = current.copy(markers = markers)
            result = markers
        }
        return result
    }

    private fun mutate(context: Context, what: String, change: (MutableMap<Long, Meta>) -> Unit) {
        runCatching {
            locked(context) {
                val all = load(context).toMutableMap()
                change(all)
                save(context, all)
            }
        }.onFailure { Diagnostics.add("recorder", "recordings.json $what failed: $it") }
    }

    /**
     * Exclusive across processes (the file lock) AND across this process's threads (the monitor): Java refuses
     * a second lock on one file from the same process with OverlappingFileLockException rather than waiting.
     */
    @Synchronized
    private fun <T> locked(context: Context, block: () -> T): T {
        val lockFile = File(context.filesDir, "$FILE_NAME.lock")
        RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.lock().use { return block() }
        }
    }

    private fun load(context: Context): Map<Long, Meta> {
        val f = file(context)
        if (!f.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(f.readText())
            val recs = root.optJSONObject("recordings") ?: JSONObject()
            val out = HashMap<Long, Meta>()
            for (key in recs.keys()) {
                val o = recs.getJSONObject(key)
                val arr = o.optJSONArray("markers") ?: JSONArray()
                out[key.toLong()] = Meta(
                    recordedAtMs = o.optLong("recordedAt", 0L),
                    durationMs = o.optLong("durationMs", 0L),
                    markers = (0 until arr.length()).map { arr.getLong(it) },
                )
            }
            out
        }.onFailure {
            // Said, not swallowed: markers that vanished and a file that would not parse look the same.
            Diagnostics.add("recorder", "recordings.json would not parse, reading it as empty: $it")
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, all: Map<Long, Meta>) {
        val recs = JSONObject()
        for ((id, m) in all.toSortedMap()) {
            recs.put(
                id.toString(),
                JSONObject()
                    .put("recordedAt", m.recordedAtMs)
                    .put("durationMs", m.durationMs)
                    .put("markers", JSONArray().apply { m.markers.forEach { put(it) } }),
            )
        }
        val json = JSONObject().put("version", VERSION).put("recordings", recs)
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(f)) error("recordings.json rename failed")
    }
}

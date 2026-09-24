package app.tileshell.recorder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A take on disk while it records (T15-27): `files/recorder/take-<started>.aac`, the raw ADTS stream, and
 * `take-<started>.json` beside it holding what the stream cannot — the take's name, when it started and its
 * markers — rewritten with a temp file and a rename whenever a marker is added. Both are deleted once the take
 * is a published `.m4a`; a pair still here when the recorder process starts is a take whose process died, and
 * [RecorderService] recovers it into a normal recording with its name and markers (E18).
 */
class TakeFiles(private val dir: File, val started: Long) {

    val audio = File(dir, "$PREFIX$started$AUDIO")
    private val sidecar = File(dir, "$PREFIX$started$SIDECAR")

    data class Sidecar(val name: String, val recordedAtMs: Long, val markers: List<Long>)

    fun writeSidecar(s: Sidecar) {
        val json = JSONObject()
            .put("name", s.name)
            .put("recordedAt", s.recordedAtMs)
            .put("markers", JSONArray().apply { s.markers.forEach { put(it) } })
        val tmp = File(dir, sidecar.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(sidecar)) error("take sidecar rename failed")
    }

    fun readSidecar(): Sidecar? = runCatching {
        val o = JSONObject(sidecar.readText())
        val arr = o.optJSONArray("markers") ?: JSONArray()
        Sidecar(o.getString("name"), o.optLong("recordedAt", started), (0 until arr.length()).map { arr.getLong(it) })
    }.getOrNull()

    fun delete() {
        audio.delete()
        sidecar.delete()
        File(dir, sidecar.name + ".tmp").delete()
    }

    companion object {
        private const val PREFIX = "take-"
        private const val AUDIO = ".aac"
        private const val SIDECAR = ".json"

        fun dir(context: Context): File = File(context.filesDir, "recorder").apply { mkdirs() }

        /** Every take left on disk, oldest first: each one's process died before it was saved. */
        fun leftovers(dir: File): List<TakeFiles> =
            dir.listFiles().orEmpty()
                .filter { it.name.startsWith(PREFIX) && it.name.endsWith(AUDIO) }
                .mapNotNull { it.name.removePrefix(PREFIX).removeSuffix(AUDIO).toLongOrNull() }
                .sorted()
                .map { TakeFiles(dir, it) }
    }
}

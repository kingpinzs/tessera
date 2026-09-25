package app.tileshell.clock

import android.content.Context
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * The World Clock tab's city list (phase 15 build task 4): the zone ids the user added, in order, as
 * world_clock.json in the app's files dir — credential storage, because the world clock never runs before an
 * unlock — written with a temp file and a rename like the clock store's files.
 */
class WorldClockStore private constructor(context: Context) {
    private val file = File(context.filesDir, "world_clock.json")
    private val _zones = MutableStateFlow(load())
    val zones: StateFlow<List<String>> = _zones.asStateFlow()

    @Synchronized
    fun add(zoneId: String) {
        if (zoneId in _zones.value) return
        _zones.value = _zones.value + zoneId
        save()
        Diagnostics.add("clock", "world clock: added $zoneId (${_zones.value.size} cities)")
    }

    @Synchronized
    fun remove(zoneId: String) {
        if (zoneId !in _zones.value) return
        _zones.value = _zones.value - zoneId
        save()
        Diagnostics.add("clock", "world clock: removed $zoneId (${_zones.value.size} cities)")
    }

    private fun load(): List<String> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { arr.getString(it) }
    }.onFailure { Diagnostics.add("clock", "world clock: unreadable ${file.name}: $it") }.getOrDefault(emptyList())

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONArray().apply { _zones.value.forEach { put(it) } }.toString())
        if (!tmp.renameTo(file)) Diagnostics.add("clock", "world clock: could not write ${file.name}")
    }

    companion object {
        @Volatile private var instance: WorldClockStore? = null

        fun get(context: Context): WorldClockStore = instance ?: synchronized(this) {
            instance ?: WorldClockStore(context.applicationContext).also { instance = it }
        }
    }
}

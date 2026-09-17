package app.tileshell.tiles.engine

import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Badge counts per package with the R5 §4 precedence (phase 01 Decisions):
 * Live Tile API badge > legacy badge broadcast (3-day implicit expiry) > Samsung badge provider (if readable)
 * > notification-derived (AOSP Launcher3: sum of max(1, number) over badge-eligible notifications).
 * Sources are never summed; an explicit 0 from a source clears that source and falls through.
 */
object BadgeStore {
    enum class Source { API, LEGACY_BROADCAST, SAMSUNG_PROVIDER, NOTIFICATIONS }

    private data class Value(val count: Int, val atMs: Long)

    const val LEGACY_EXPIRY_MS = 3L * 24 * 60 * 60 * 1000

    private val values = HashMap<String, MutableMap<Source, Value>>()
    private val state = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = state.asStateFlow()

    @Synchronized
    fun set(pkg: String, source: Source, count: Int, nowMs: Long = System.currentTimeMillis()) {
        val bySource = values.getOrPut(pkg) { mutableMapOf() }
        if (count <= 0) bySource.remove(source) else bySource[source] = Value(count, nowMs)
        publish(nowMs)
        Diagnostics.add("badge", "set $pkg $source=$count -> effective ${state.value[pkg] ?: 0}")
    }

    @Synchronized
    fun clearPackage(pkg: String) {
        values.remove(pkg)
        publish(System.currentTimeMillis())
    }

    @Synchronized
    fun effective(pkg: String, nowMs: Long = System.currentTimeMillis()): Int {
        val bySource = values[pkg] ?: return 0
        for (source in Source.entries) {
            val v = bySource[source] ?: continue
            if (source == Source.LEGACY_BROADCAST && nowMs - v.atMs > LEGACY_EXPIRY_MS) continue
            return v.count
        }
        return 0
    }

    private fun publish(nowMs: Long) {
        state.value = values.keys.associateWith { effective(it, nowMs) }.filterValues { it > 0 }
    }
}

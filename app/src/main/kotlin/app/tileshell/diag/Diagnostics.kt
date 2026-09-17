package app.tileshell.diag

import android.os.SystemClock
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One diagnostics ring buffer for the whole shell (phase 01 Decisions: "Diagnostics").
 * Read on any build type through the notification listener's Service.dump() and the
 * Settings > Diagnostics page. Later phases add entries; nothing is written to logcat.
 */
object Diagnostics {
    private const val CAPACITY = 4000

    data class Entry(val wallMs: Long, val uptimeMs: Long, val tag: String, val message: String)

    private val ring = ArrayDeque<Entry>(CAPACITY)
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun add(tag: String, message: String) {
        if (ring.size == CAPACITY) ring.removeFirst()
        ring.addLast(Entry(System.currentTimeMillis(), SystemClock.uptimeMillis(), tag, message))
        listeners.forEach { it() }
    }

    @Synchronized
    fun snapshot(): List<Entry> = ring.toList()

    @Synchronized
    fun onChange(listener: () -> Unit) { listeners += listener }

    fun dump(out: PrintWriter) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val entries = snapshot()
        out.println("tileshell diagnostics: ${entries.size} entries")
        for (e in entries) {
            out.println("${fmt.format(Date(e.wallMs))} wall=${e.wallMs} [${e.tag}] ${e.message}")
        }
    }
}

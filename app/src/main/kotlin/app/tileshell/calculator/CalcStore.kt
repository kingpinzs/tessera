package app.tileshell.calculator

import android.content.Context
import app.tileshell.diag.Diagnostics
import java.io.File

/**
 * What the Calculator keeps across process death (phase 15 Decisions "Calculator engine": memory and history persist
 * until cleared, H11; T15-43: the Converter's last-used category): three small text files in the app's private files
 * directory, each written with a temp file and a rename as `LayoutStore` and `PlaylistStore` do, so a kill mid-write
 * leaves the previous file whole. The strings are the engine's own encodings (`Calculator.encodeMemory` /
 * `encodeHistory`, `ConverterState.saveUserPreferences`); this class only keeps them.
 */
class CalcStore(context: Context) {
    private val dir: File = context.filesDir
    private val memoryFile = File(dir, MEMORY_FILE)
    private val historyFile = File(dir, HISTORY_FILE)
    private val converterFile = File(dir, CONVERTER_FILE)

    private var lastMemory: String? = null
    private var lastHistory: String? = null
    private var lastConverter: String? = null

    fun readMemory(): String? = read(memoryFile).also { lastMemory = it }
    fun readHistory(): String? = read(historyFile).also { lastHistory = it }
    fun readConverter(): String? = read(converterFile).also { lastConverter = it }

    /** Writes [memory] and [history] when either differs from what was last read or written. */
    fun save(memory: String, history: String) {
        if (memory != lastMemory) {
            write(memoryFile, memory)
            lastMemory = memory
        }
        if (history != lastHistory) {
            write(historyFile, history)
            lastHistory = history
        }
    }

    fun saveConverter(preferences: String) {
        if (preferences == lastConverter) return
        write(converterFile, preferences)
        lastConverter = preferences
    }

    private fun read(file: File): String? = if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    private fun write(file: File, text: String) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) error("rename failed")
        }.onFailure { Diagnostics.add("calc", "store write ${file.name} failed: $it") }
    }

    companion object {
        const val MEMORY_FILE = "calc_memory.txt"
        const val HISTORY_FILE = "calc_history.txt"
        const val CONVERTER_FILE = "calc_converter.txt"
    }
}

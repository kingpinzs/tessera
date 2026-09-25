package app.tileshell.calculator

import app.tileshell.diag.Diagnostics

/** The once-per-process diagnostics line naming the engine's port (Harness contracts: `[calc] engine <port|ndk> <version>`). */
object CalcDiag {
    const val ENGINE_LINE = "engine port microsoft/calculator@4fd3fc5"

    @Volatile private var logged = false

    @Synchronized
    fun logEngineOnce() {
        if (logged) return
        logged = true
        Diagnostics.add("calc", ENGINE_LINE)
    }
}

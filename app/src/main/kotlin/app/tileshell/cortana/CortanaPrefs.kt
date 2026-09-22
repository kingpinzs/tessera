package app.tileshell.cortana

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cortana's own settings (phase 03 build task 8). Separate from [app.tileshell.prefs.ShellSettings],
 * which is Start's; nothing here changes how Start draws.
 */
data class CortanaPrefsState(
    /** The bundled voice, by sherpa-onnx speaker id. The default is the closest to Cortana's tone (Q3, H2). */
    val voiceId: Int = DEFAULT_VOICE_ID,
    /** Where "take a note" goes. Same slot picker as phase 01; unassigned until chosen (Decisions). */
    val notesApp: ComponentName? = null,
    /**
     * R6 §3.5.1's W10M wording, with "Search button" adapted to the S25 Ultra's side key. On by default
     * is a LOW candidate from R6 §3.5.3 (H8).
     */
    val lockScreenOption: Boolean = true,
) {
    companion object {
        /**
         * Kokoro en v0.19 speaker 2 = `af_bella` (build-start agent call, H2): of the eleven bundled
         * voices it is the warm, clear American female closest to Cortana's tone. Jeremy judges it in H2.
         */
        const val DEFAULT_VOICE_ID = 2
    }
}

class CortanaPrefs private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cortana", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<CortanaPrefsState> = state.asStateFlow()

    private fun read() = CortanaPrefsState(
        voiceId = prefs.getInt("voice", CortanaPrefsState.DEFAULT_VOICE_ID),
        notesApp = prefs.getString("notes", null)?.let { ComponentName.unflattenFromString(it) },
        lockScreenOption = prefs.getBoolean("lock_screen", true),
    )

    fun update(change: (CortanaPrefsState) -> CortanaPrefsState) {
        val next = change(state.value)
        prefs.edit()
            .putInt("voice", next.voiceId)
            .putString("notes", next.notesApp?.flattenToString())
            .putBoolean("lock_screen", next.lockScreenOption)
            .apply()
        state.value = next
        Diagnostics.add("cortana", "settings changed: $next")
    }

    companion object {
        @Volatile private var instance: CortanaPrefs? = null
        fun get(context: Context): CortanaPrefs =
            instance ?: synchronized(this) { instance ?: CortanaPrefs(context.applicationContext).also { instance = it } }
    }
}

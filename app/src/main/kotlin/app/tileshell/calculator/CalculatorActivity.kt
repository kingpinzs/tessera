package app.tileshell.calculator

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot
import java.time.LocalDate

/**
 * Calculator (phase 15 build task 6): an app in the shell APK with its own task, in the app list like Music (Scope).
 * Standard, Scientific, Programmer and Date calculation modes and the Converter behind a ≡ pane (r11/calculator.md
 * §1–§5); every page hides Samsung's bars and draws the W10M bars (phase 01's bar rule), the drawn Back is Back for
 * the page and Windows goes Home. The engine, converter and date calculation are the `:calc` module's ports of
 * microsoft/calculator@4fd3fc5; this activity only shows what they answer.
 *
 * The App Shortcuts' `page` extra (build task 9: `standard` / `scientific` / `programmer` / `converter`) picks the
 * page shown; `converter` opens the last-used category, Volume the first time (T15-43).
 *
 * `testTagsAsResourceId` on the root is the QA contract every shell window signs (MusicActivity's lesson).
 */
class CalculatorActivity : ComponentActivity() {

    private val requested = mutableStateOf<CalcPage?>(null)
    private lateinit var model: CalcModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        CalcDiag.logEngineOnce()
        requested.value = CalcLayout.pageForShortcut(intent?.getStringExtra(EXTRA_PAGE))
        Diagnostics.add("calc", "CalculatorActivity created page=${intent?.getStringExtra(EXTRA_PAGE) ?: "default"}")
        // Held by the activity: configuration changes are handled in the manifest, so this survives them; process
        // death restores memory, history and the converter's preferences from CalcStore.
        model = CalcModel(CalcStore(this), LocalDate.now())
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    CalculatorScreen(
                        model = model,
                        requestedPage = requested.value,
                        onPageShown = { requested.value = null },
                        onFinish = { finish() },
                        onHome = { goHome() },
                        clipboardText = { clipboardText() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CalcLayout.pageForShortcut(intent.getStringExtra(EXTRA_PAGE))?.let { requested.value = it }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The clipboard's primary text, for the paste route (T15-57); null when there is none. */
    private fun clipboardText(): String? {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    companion object {
        /** The shortcuts' extra — the key SettingsActivity.EXTRA_PAGE already uses (build task 9). */
        const val EXTRA_PAGE = "page"
    }
}

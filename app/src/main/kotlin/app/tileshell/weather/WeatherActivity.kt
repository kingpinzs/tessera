package app.tileshell.weather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot

/**
 * The shell's W10M-style Weather app (build task 12), opened from the Weather tile. Bar rule: Samsung's bars are
 * hidden and the drawn W10M bars are shown; the drawn Back key finishes, the Windows key goes Home.
 */
class WeatherActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        WeatherFeed.start(this)
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    WeatherPage(onBack = { finish() }, onWindows = { goHome() })
                }
            }
        }
        Diagnostics.add("weather", "WeatherActivity created")
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        WeatherFeed.onAppResumed()
    }

    private fun goHome() {
        Diagnostics.add("weather", "Windows key: go Home")
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

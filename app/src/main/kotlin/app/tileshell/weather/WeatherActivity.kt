package app.tileshell.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.tileshell.bars.hideSystemBars
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.ShellRoot

/** W10M-style Weather app (build task 12 fills this in). */
class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent { ShellRoot { Box(Modifier.fillMaxSize().background(LocalShellColors.current.background)) } }
    }
}

package app.tileshell.ui.tokens

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.min

/**
 * RV10 / Q11: the shell lays out on a 360-epx canvas mapped to the display's portrait width.
 * Screen width comes from the maximum window metrics (never an overlay's or the IME window's own
 * bounds), so Samsung Screen zoom (density) and Font size (fontScale) never resize the shell.
 * Inside [ShellDensity], 1.dp == 1 epx and 1.sp == 1 epx.
 */
object Scale {
    const val CANVAS_EPX = 360f

    fun portraitWidthPx(context: Context): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm.maximumWindowMetrics.bounds
        return min(bounds.width(), bounds.height())
    }

    /** The panel's long side: R6 measures the edit-mode fixed point against the whole screen. */
    fun portraitHeightPx(context: Context): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm.maximumWindowMetrics.bounds
        return kotlin.math.max(bounds.width(), bounds.height())
    }

    fun pxPerEpx(context: Context): Float = portraitWidthPx(context) / CANVAS_EPX
}

@Composable
fun ShellDensity(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Re-read on every configuration change (wm size / density / font scale) so the mapping follows the panel.
    val configuration = LocalConfiguration.current
    val density = remember(configuration, context) { Density(Scale.pxPerEpx(context), fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

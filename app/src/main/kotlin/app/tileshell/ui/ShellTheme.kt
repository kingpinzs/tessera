package app.tileshell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.tileshell.prefs.ShellSettings
import app.tileshell.prefs.StartTheme
import app.tileshell.prefs.ThemeMode
import app.tileshell.ui.tokens.Palette
import app.tileshell.ui.tokens.ShellDensity

@Immutable
data class ShellColors(
    val accent: Color,
    val background: Color,
    val text: Color,
    val subtleText: Color,
    val chrome: Color,
    val isDark: Boolean,
)

val LocalShellColors = staticCompositionLocalOf { colorsFor(StartTheme()) }
val LocalStartTheme = staticCompositionLocalOf { StartTheme() }

fun colorsFor(theme: StartTheme): ShellColors {
    val dark = theme.theme == ThemeMode.DARK
    return ShellColors(
        accent = Color(theme.accent),
        background = if (dark) Palette.darkBackground else Palette.lightBackground,
        text = if (dark) Palette.darkText else Palette.lightText,
        subtleText = if (dark) Color(0x99FFFFFF) else Color(0x99000000),
        chrome = if (dark) Palette.darkChromeMedium else Palette.lightChromeMedium,
        isDark = dark,
    )
}

/** Density mapping (RV10) + the Start + theme settings for every shell-owned window root. */
@Composable
fun ShellRoot(content: @Composable () -> Unit) {
    val settings = ShellSettings.get(LocalContext.current)
    val theme by settings.theme.collectAsState()
    ShellDensity {
        CompositionLocalProvider(LocalStartTheme provides theme, LocalShellColors provides colorsFor(theme), content = content)
    }
}

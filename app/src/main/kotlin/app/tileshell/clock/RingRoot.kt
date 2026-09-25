package app.tileshell.clock

import android.content.Context
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import app.tileshell.prefs.ShellSettings
import app.tileshell.prefs.StartTheme
import app.tileshell.prefs.ThemeMode
import app.tileshell.ui.LocalShellColors
import app.tileshell.ui.LocalStartTheme
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.colorsFor
import app.tileshell.ui.tokens.ShellDensity

/**
 * The ring toast's window root. Once the user is unlocked it is [ShellRoot]. Before the first unlock after a reboot
 * (T15-22) the theme's SharedPreferences are in credential storage and cannot be read, so the toast takes the
 * accent and Dark / Light last mirrored into device-protected storage ([mirror], called whenever the clock store
 * changes or a ring starts on an unlocked phone).
 */
@Composable
fun RingRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
        ShellRoot(content)
    } else {
        val theme = locked(context)
        ShellDensity {
            CompositionLocalProvider(LocalStartTheme provides theme, LocalShellColors provides colorsFor(theme), content = content)
        }
    }
}

private const val PREFS = "ring_theme"

/** Copies the accent and Dark / Light into device-protected storage; a no-op before the first unlock. */
fun mirrorRingTheme(context: Context) {
    if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
    val theme = ShellSettings.get(context).theme.value
    context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putLong("accent", theme.accent).putString("mode", theme.theme.name).apply()
}

private fun locked(context: Context): StartTheme {
    val p = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val d = StartTheme()
    return d.copy(
        accent = p.getLong("accent", d.accent),
        theme = runCatching { ThemeMode.valueOf(p.getString("mode", d.theme.name)!!) }.getOrDefault(d.theme),
    )
}

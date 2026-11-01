package app.tileshell.prefs

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * D3 (a): the launcher's process hands its theme, as it is now, to a window opening in another of the shell's processes
 * (`process/LauncherCalls.refreshTheme`) — the [StartTheme] itself, across the Bundle. Every item arrives as it left.
 */
class StartThemeTransferTest {
    /** Every item set away from its default, so an item that did not cross would show. */
    private val changed = StartTheme(
        accent = 0xFF60A917,
        theme = ThemeMode.LIGHT,
        backgroundUri = "file:///data/user/0/app.tileshell/files/backgrounds/42.jpg",
        transparency = 0.25f,
        mediumColumns = 6,
        pressStyle = PressStyle.WP8_TILT,
        showWorkAndPrivateApps = false,
        autoSizeTiles = false,
        photosSlideshow = true,
        photoFrameUri = "content://media/external/images/media/7",
        transparencyEffects = false,
        themePreset = "custom",
        themePresetVariant = "streaks",
        tessLens = "accent",
        keyboardPalette = ThemeMode.LIGHT,
    )

    @Test fun `the sample changes every item of the theme`() {
        val defaults = StartTheme()
        val fields = StartTheme::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }
        assertEquals(15, fields.size)
        for (f in fields) assertNotEquals(f.name, f.get(defaults), f.get(changed))
    }

    @Test fun `the theme crosses the process boundary whole`() {
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(changed) } }.toByteArray()
        val back = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertEquals(changed, back)
    }
}

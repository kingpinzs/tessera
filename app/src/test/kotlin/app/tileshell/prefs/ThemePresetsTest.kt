package app.tileshell.prefs

import androidx.compose.ui.graphics.Color
import app.tileshell.brand.Brand
import app.tileshell.cortana.ui.LensTones
import app.tileshell.ui.tokens.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Phase 12 build task 3: the preset table as the doc writes it, the Custom rule, Cobalt the 49th, and the lens tones. */
class ThemePresetsTest {

    @Test fun `six presets, in the page's order`() {
        assertEquals(listOf("Default", "Windows 10 Mobile (original)", "HAL", "Soft", "Lumia", "Midnight"), ThemePreset.entries.map { it.displayName })
        assertEquals(listOf("default", "w10m", "hal", "soft", "lumia", "midnight"), ThemePreset.entries.map { it.id })
    }

    @Test fun `the preset table`() {
        fun check(p: ThemePreset, accent: Long, theme: ThemeMode, transparency: Float, press: PressStyle, lens: String, kb: ThemeMode, fx: Boolean) {
            assertEquals(p.name, accent, p.accent)
            assertEquals(p.name, theme, p.theme)
            assertEquals(p.name, transparency, p.transparency, 0f)
            assertEquals(p.name, press, p.pressStyle)
            assertEquals(p.name, lens, p.tessLens)
            assertEquals(p.name, kb, p.keyboardPalette)
            assertEquals(p.name, fx, p.transparencyEffects)
        }
        check(ThemePreset.DEFAULT, 0xFF0078D7, ThemeMode.DARK, 0.5f, PressStyle.NONE, "hal", ThemeMode.DARK, true)
        check(ThemePreset.W10M, 0xFF3E65FF, ThemeMode.DARK, 0.75f, PressStyle.NONE, "accent", ThemeMode.DARK, false)
        check(ThemePreset.HAL, 0xFFE81123, ThemeMode.DARK, 0.35f, PressStyle.NONE, "hal", ThemeMode.DARK, true)
        check(ThemePreset.SOFT, 0xFF8E8CD8, ThemeMode.LIGHT, 0.6f, PressStyle.P4_PRESS, "accent", ThemeMode.LIGHT, true)
        check(ThemePreset.LUMIA, 0xFF00B7C3, ThemeMode.DARK, 0.5f, PressStyle.WP8_TILT, "accent", ThemeMode.DARK, true)
        check(ThemePreset.MIDNIGHT, 0xFF6B69D6, ThemeMode.DARK, 0.0f, PressStyle.NONE, "hal_dim", ThemeMode.DARK, false)
        // The accents the rows read back as start_theme.xml longs.
        assertEquals(4293398819L, ThemePreset.HAL.accent)
        assertEquals(4282279423L, ThemePreset.W10M.accent)
    }

    @Test fun `tile alpha over each picture - 1 minus 0_8 times transparency`() {
        val alpha = ThemePreset.entries.associate { it.id to 1f - 0.8f * it.transparency }
        assertEquals(0.40f, alpha.getValue("w10m"), 1e-6f)
        assertEquals(0.72f, alpha.getValue("hal"), 1e-6f)
        assertEquals(0.52f, alpha.getValue("soft"), 1e-6f)
        assertEquals(0.60f, alpha.getValue("lumia"), 1e-6f)
        assertEquals(1.00f, alpha.getValue("midnight"), 1e-6f)
    }

    @Test fun `pictures - none for Default, the variant for the original, one each for the rest`() {
        assertNull(ThemePreset.DEFAULT.pictureName("hero"))
        assertEquals("preset_w10m_hero", ThemePreset.W10M.pictureName("hero"))
        assertEquals("preset_w10m_streaks", ThemePreset.W10M.pictureName("streaks"))
        assertEquals("preset_hal", ThemePreset.HAL.pictureName("streaks"))
        assertEquals("preset_soft", ThemePreset.SOFT.pictureName("hero"))
        assertEquals("preset_lumia", ThemePreset.LUMIA.pictureName("hero"))
        assertEquals("preset_midnight", ThemePreset.MIDNIGHT.pictureName("hero"))
        assertEquals("android.resource://app.tileshell/drawable/preset_hal", ThemePresets.pictureUri("app.tileshell", "preset_hal"))
    }

    @Test fun `Custom rule - an item change reads custom`() {
        val lumia = ThemePresets.withItems(StartTheme(), ThemePresets.itemsFor(ThemePreset.LUMIA, "android.resource://app.tileshell/drawable/preset_lumia")).copy(themePreset = "lumia")
        val red = ThemePresets.afterUserChange(lumia, lumia.copy(accent = 0xFFE81123))
        assertEquals("custom", red.themePreset)
        assertEquals(0xFFE81123, red.accent)
        assertEquals(ThemePresets.itemsOf(lumia).copy(accent = 0xFFE81123), ThemePresets.itemsOf(red)) // every OTHER item unchanged
        listOf<(StartTheme) -> StartTheme>(
            { it.copy(theme = ThemeMode.LIGHT) }, { it.copy(backgroundUri = null) }, { it.copy(transparency = 0.1f) },
            { it.copy(pressStyle = PressStyle.NONE) }, { it.copy(transparencyEffects = false) }, { it.copy(tessLens = "hal") },
            { it.copy(keyboardPalette = ThemeMode.LIGHT) },
        ).forEach { change -> assertEquals("custom", ThemePresets.afterUserChange(lumia, change(lumia)).themePreset) }
    }

    @Test fun `Custom rule - the non-preset keys and the variant never read custom`() {
        val w10m = StartTheme(themePreset = "w10m")
        listOf<(StartTheme) -> StartTheme>(
            { it.copy(mediumColumns = 2) }, { it.copy(showWorkAndPrivateApps = false) }, { it.copy(autoSizeTiles = false) },
            { it.copy(photosSlideshow = true) }, { it.copy(photoFrameUri = "content://media/x") }, { it.copy(themePresetVariant = "streaks") },
        ).forEach { change -> assertEquals("w10m", ThemePresets.afterUserChange(w10m, change(w10m)).themePreset) }
    }

    @Test fun `an absent preset key reads as Default`() {
        assertEquals("default", ThemePresets.selectedId(StartTheme()))
        assertEquals("custom", ThemePresets.selectedId(StartTheme(themePreset = "custom")))
    }

    @Test fun `a stored set with no theme_preset key reads as Custom when its items are not Default's (B1)`() {
        assertNull("a fresh install reads as Default", ThemePresets.presetOnRead(null, StartTheme()))
        assertEquals("custom", ThemePresets.presetOnRead(null, StartTheme(backgroundUri = "content://media/picker/0/x/media/1")))
        assertEquals("custom", ThemePresets.presetOnRead(null, StartTheme(accent = 0xFFE81123)))
        assertNull("columns is not an item", ThemePresets.presetOnRead(null, StartTheme(mediumColumns = 2)))
        assertEquals("a stored id wins", "hal", ThemePresets.presetOnRead("hal", StartTheme(accent = 0xFF00B7C3)))
    }

    @Test fun `the variant reads as one of the two, else the Hero`() {
        assertEquals("hero", ThemePresets.variantOnRead(null))
        assertEquals("streaks", ThemePresets.variantOnRead("streaks"))
        assertEquals("hero", ThemePresets.variantOnRead("garbage"))
    }

    @Test fun `a replaced snapshot's grant goes only when it is a held content grant nothing references`() {
        val a = "content://media/picker/0/p/media/2290"
        assertTrue(ThemePresets.releasable(a, photoFrameUri = null, snapshotBackgroundUri = "content://other", held = true))
        assertFalse("never an android.resource URI", ThemePresets.releasable("android.resource://app.tileshell/drawable/preset_hal", null, null, true))
        assertFalse("kept while the photo frame uses it", ThemePresets.releasable(a, photoFrameUri = a, snapshotBackgroundUri = null, held = true))
        assertFalse("kept while the new snapshot uses it", ThemePresets.releasable(a, photoFrameUri = null, snapshotBackgroundUri = a, held = true))
        assertFalse("never one the shell does not hold", ThemePresets.releasable(a, null, null, held = false))
    }

    @Test fun `Cobalt is the 49th swatch - last row, first column`() {
        assertEquals(49, Palette.accents.size)
        assertEquals("Cobalt" to 0xFF3E65FF, Palette.accents[48])
        assertEquals(listOf("Cobalt"), Palette.accents.chunked(6).last().map { it.first })
        assertEquals(49, Palette.accents.map { it.first }.distinct().size)
    }

    @Test fun `HAL's accent Red is an A16 swatch nearest the lens red`() {
        assertTrue(Palette.accents.any { it.second == ThemePreset.HAL.accent })
    }

    // ------------------------------------------------------------------ tess_lens

    private fun close(a: Float, b: Float, tol: Float = 0.6f) = abs(a - b) <= tol

    @Test fun `hal is the lens as built`() {
        assertEquals(LensTones.HAL, LensTones.of("hal", Color(0xFF3E65FF)))
        assertEquals(Brand.LENS_IRIS, LensTones.of("hal", Color.White).iris)
    }

    @Test fun `hal_dim halves R, G and B of rim, iris and glow, and keeps the core`() {
        val dim = LensTones.of("hal_dim", Color(0xFF6B69D6))
        listOf(Brand.LENS_RIM to dim.rim, Brand.LENS_IRIS to dim.iris, Brand.LENS_GLOW to dim.glow).forEach { (hal, d) ->
            // An sRGB Color is stored in 8 bits a channel, so a halved channel is within one step (1/255) of half.
            assertEquals(hal.red * 0.5f, d.red, 1f / 255f)
            assertEquals(hal.green * 0.5f, d.green, 1f / 255f)
            assertEquals(hal.blue * 0.5f, d.blue, 1f / 255f)
        }
        assertEquals(Brand.LENS_CORE, dim.core)
    }

    @Test fun `accent re-hues rim, iris and glow to the accent's hue, keeping each tone's S and V`() {
        val cobalt = Color(0xFF3E65FF)
        val hue = LensTones.hsv(cobalt)[0]
        val tones = LensTones.of("accent", cobalt)
        listOf(Brand.LENS_RIM to tones.rim, Brand.LENS_IRIS to tones.iris, Brand.LENS_GLOW to tones.glow).forEach { (hal, t) ->
            val (h, s, v) = LensTones.hsv(t).toList()
            val (_, hs, hv) = LensTones.hsv(hal).toList()
            assertTrue("hue $h vs $hue", close(h, hue))
            assertEquals(hs, s, 0.01f)
            assertEquals(hv, v, 0.01f)
        }
        assertEquals(Brand.LENS_CORE, tones.core)
        assertFalse(tones == LensTones.HAL)
    }

    @Test fun `hsv round trip`() {
        listOf(Color(0xFF3E65FF), Color(0xFF8A1008), Color(0xFF00B7C3), Color(0xFF8E8CD8)).forEach { c ->
            val (h, s, v) = LensTones.hsv(c).toList()
            val back = LensTones.fromHsv(h, s, v)
            assertEquals(c.red, back.red, 0.005f); assertEquals(c.green, back.green, 0.005f); assertEquals(c.blue, back.blue, 0.005f)
        }
    }
}

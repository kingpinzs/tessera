package app.tileshell.ui.fluent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** Phase 13 build task 1: the tint derivation, the on / off rule and its reason precedence, the noise's range. */
class FluentMaterialTest {

    // ---- the tint derivation, one test per surface-table row (phase 13 Decisions "The surface table")

    @Test
    fun appListBackdropTintIsTheThemeBackground() {
        // App-list backdrop: T = theme background (0,0,0); its B_m is the wallpaper, so the row states T directly.
        assertEquals(Rgb(0, 0, 0), FluentMaterial.deriveTint(Rgb(0, 0, 0), Rgb(0, 0, 0)))
    }

    @Test
    fun appListMenuBandTint() {
        // H21's band: F = theme background (0,0,0) over the app list, black in H21's setup.
        assertEquals(Rgb(0, 0, 0), FluentMaterial.deriveTint(Rgb(0, 0, 0), Rgb(0, 0, 0)))
    }

    @Test
    fun musicMenuTint() {
        // MUSIC8's band: F = theme background over the page under it (black).
        assertEquals(Rgb(0, 0, 0), FluentMaterial.deriveTint(Rgb(0, 0, 0), Rgb(0, 0, 0)))
    }

    @Test
    fun cortanaPaneTintReadsItsMeasuredFillOverHome() {
        // R7 §3.1.6 (14,19,13) over Cortana's Home page (0,0,0), R6 §3.1.14 → T = (18,24,16).
        val t = FluentMaterial.deriveTint(Rgb(14, 19, 13), Rgb(0, 0, 0))
        assertEquals(Rgb(18, 24, 16), t)
        assertEquals(Rgb(14, 19, 13), FluentMaterial.over(t!!, Rgb(0, 0, 0)))
        // The table's note: over the pane's other pages ((14,19,13), R7 §3.5) it reads (17,23,15).
        assertEquals(Rgb(17, 23, 15), FluentMaterial.over(t, Rgb(14, 19, 13)))
    }

    @Test
    fun reminderMenuTintReadsItsMeasuredFillOverTheRemindersPage() {
        // R7 §3.6.2 (40,40,40) over the Reminders page (14,19,13) → T = (47,45,47).
        val t = FluentMaterial.deriveTint(Rgb(40, 40, 40), Rgb(14, 19, 13))
        assertEquals(Rgb(47, 45, 47), t)
        assertEquals(Rgb(40, 40, 40), FluentMaterial.over(t!!, Rgb(14, 19, 13)))
    }

    @Test
    fun aFillDarkerThanTwentyPercentOfItsBackdropStaysSolid() {
        // The negative branch: phase 04's black action center over a bright app cannot be acrylic at α 0.8.
        assertNull(FluentMaterial.deriveTint(Rgb(0, 0, 0), Rgb(200, 200, 200)))
        // One channel is enough to rule it out.
        assertNull(FluentMaterial.deriveTint(Rgb(40, 40, 10), Rgb(14, 19, 60)))
        // Exactly 20 % of the backdrop is the boundary: T = 0, still acrylic.
        assertEquals(Rgb(0, 0, 0), FluentMaterial.deriveTint(Rgb(20, 20, 20), Rgb(100, 100, 100)))
    }

    @Test
    fun sigmaFollowsHwuisRadiusConversion() {
        // 30 epx at 3 px/epx = 90 px → σ = 0.57735·90 + 0.5 = 52.46 px; the S layer's margin is 3σ, 158 px.
        assertEquals(52.46f, FluentMaterial.sigmaPx(90f), 0.01f)
        assertEquals(158f, FluentMaterial.marginPx(90f), 0f)
    }

    // ---- the on / off rule and its reason precedence (T13-20), all 8 combinations

    @Test
    fun ruleAndPrecedenceOnAllEightCombinations() {
        for (setting in listOf(true, false)) for (saver in listOf(true, false)) for (lowRam in listOf(true, false)) {
            val state = FluentRule.decide(transparencyEffects = setting, powerSave = saver, lowRam = lowRam)
            val expectedOn = setting && !saver && !lowRam
            val expectedReason = when {
                lowRam -> FluentReason.LOW_RAM
                saver -> FluentReason.BATTERY_SAVER
                !setting -> FluentReason.SETTING
                else -> FluentReason.NONE
            }
            val label = "setting=$setting saver=$saver lowRam=$lowRam"
            assertEquals(label, expectedOn, state.on)
            assertEquals(label, expectedReason, state.reason)
        }
    }

    @Test
    fun theDiagnosticsLineNamesTheReason() {
        assertEquals("acrylic=on reason=none", FluentRule.decide(true, false, false).line())
        assertEquals("acrylic=off reason=battery-saver", FluentRule.decide(false, true, false).line())
        assertEquals("acrylic=off reason=setting", FluentRule.decide(false, false, false).line())
        assertEquals("acrylic=off reason=low-ram", FluentRule.decide(false, true, true).line())
    }

    // ---- the noise

    @Test
    fun noiseLevelsStayInRangeAndSpreadAsAUniformElevenLevelNoise() {
        val levels = IntArray(11)
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 0 until 400) for (x in 0 until 400) {
            val level = FluentNoise.level(x, y)
            assertTrue("level $level at ($x,$y)", level in -5..5)
            levels[level + 5]++
            sum += level
            sumSq += level * level
            n++
        }
        val mean = sum / n
        val std = sqrt(sumSq / n - mean * mean)
        // A uniform −5..5 has mean 0 and σ = √10 ≈ 3.16; E6 gates the device at 1.5–4 levels.
        assertEquals(0.0, mean, 0.1)
        assertEquals(sqrt(10.0), std, 0.15)
        levels.forEachIndexed { i, count -> assertTrue("level ${i - 5} occurs $count times", count > n / 11 / 2) }
    }

    @Test
    fun noiseIsDeterministicPerPixel() {
        for (y in 0 until 50) for (x in 0 until 50) assertEquals(FluentNoise.level(x, y), FluentNoise.level(x, y))
    }
}

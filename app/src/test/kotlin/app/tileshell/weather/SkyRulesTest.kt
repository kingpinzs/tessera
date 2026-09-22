package app.tileshell.weather

import app.tileshell.tiles.engine.SkyScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Weather tile's animated main face (INDEX Change Log 2026-09-21 item 2), weather half.
 *
 * This is tested here rather than on the device because the device cannot produce the inputs: the rainbow
 * needs the sun out with rain still on the ground, and E9 runs against whatever the real sky is doing that
 * morning. The mapping is checked over the whole WMO table the provider can return, not a sample, so a code
 * the feed can report can never land on a scene nobody drew.
 */
class SkyRulesTest {

    /** Every code [WmoCodes.word] names, i.e. everything Open-Meteo documents it can return. */
    private val allCodes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)

    private fun report(
        code: Int,
        isDay: Boolean = true,
        nowMs: Long = NOW,
        hourly: List<HourPoint> = emptyList(),
    ) = WeatherReport(
        provider = "test",
        latitude = 40.44,
        longitude = -80.0,
        place = "Pittsburgh",
        timeZoneId = "America/New_York",
        units = WeatherUnits.IMPERIAL,
        fetchedAtMs = nowMs,
        current = CurrentConditions(
            timeMs = nowMs, temperature = 62.0, feelsLike = 60.0, humidityPct = 70, pressureHpa = 1012.0,
            windSpeed = 7.0, windDirectionDeg = 270, code = code, isDay = isDay,
        ),
        hourly = hourly,
        daily = emptyList(),
    )

    private fun hour(agoMs: Long, code: Int) = HourPoint(NOW - agoMs, 60.0, code, 40, true)

    // ---- the scene a condition draws as ----

    @Test
    fun `every WMO code the provider can return has a scene`() {
        for (code in allCodes) {
            val scene = SkyRules.scene(code, rainbow = false)
            val expected = when (WmoCodes.group(code)) {
                ConditionGroup.CLEAR -> SkyScene.CLEAR
                ConditionGroup.PARTLY_CLOUDY -> SkyScene.PARTLY
                ConditionGroup.CLOUDY -> SkyScene.CLOUDY
                ConditionGroup.FOG -> SkyScene.FOG
                ConditionGroup.RAIN -> SkyScene.RAIN
                ConditionGroup.SNOW -> SkyScene.SNOW
                ConditionGroup.THUNDER -> SkyScene.THUNDER
            }
            assertEquals("code $code", expected, scene)
        }
    }

    @Test
    fun `the eight conditions Jeremy named are all reachable`() {
        assertEquals(SkyScene.CLEAR, SkyRules.scene(0, false))      // sunny / clear
        assertEquals(SkyScene.PARTLY, SkyRules.scene(2, false))     // partly cloudy
        assertEquals(SkyScene.CLOUDY, SkyRules.scene(3, false))     // cloudy
        assertEquals(SkyScene.FOG, SkyRules.scene(45, false))       // fog
        assertEquals(SkyScene.RAIN, SkyRules.scene(63, false))      // raining
        assertEquals(SkyScene.SNOW, SkyRules.scene(73, false))      // snowing
        assertEquals(SkyScene.THUNDER, SkyRules.scene(95, false))   // thunderstorm
        assertEquals(SkyScene.RAINBOW, SkyRules.scene(0, true))     // the fun one
    }

    @Test
    fun `an unknown code falls through to the same scene its group falls through to`() {
        assertEquals(ConditionGroup.CLOUDY, WmoCodes.group(4242))
        assertEquals(SkyScene.CLOUDY, SkyRules.scene(4242, false))
    }

    @Test
    fun `the rainbow overrides whatever the code would have drawn`() {
        for (code in allCodes) assertEquals("code $code", SkyScene.RAINBOW, SkyRules.scene(code, rainbow = true))
    }

    // ---- intensity: how much of it is falling ----

    @Test
    fun `intensity is a fraction for every code`() {
        for (code in allCodes + listOf(4242)) {
            val value = SkyRules.intensity(code)
            assertTrue("code $code gave $value", value in 0f..1f)
        }
    }

    @Test
    fun `light is lighter than moderate is lighter than heavy`() {
        // drizzle, rain, snow, showers: the WMO table's own three steps.
        for ((light, moderate, heavy) in listOf(
            Triple(51, 53, 55), Triple(61, 63, 65), Triple(71, 73, 75), Triple(80, 81, 82),
        )) {
            assertTrue("$light < $moderate", SkyRules.intensity(light) < SkyRules.intensity(moderate))
            assertTrue("$moderate < $heavy", SkyRules.intensity(moderate) < SkyRules.intensity(heavy))
        }
    }

    @Test
    fun `a clear sky has nothing in it and overcast is nearly solid`() {
        assertEquals(0f, SkyRules.intensity(0), 0f)
        assertTrue(SkyRules.intensity(1) < SkyRules.intensity(2))
        assertTrue(SkyRules.intensity(2) < SkyRules.intensity(3))
    }

    // ---- the rainbow trigger ----

    @Test
    fun `sun out after a shower makes a rainbow`() {
        assertTrue(SkyRules.rainbow(report(0, hourly = listOf(hour(HOUR, 63))), NOW))
        assertTrue(SkyRules.rainbow(report(2, hourly = listOf(hour(HOUR / 2, 80))), NOW))
    }

    @Test
    fun `a sun shower is a rainbow on its own - the drops are in the air now`() {
        assertTrue(SkyRules.rainbow(report(80), NOW))
        assertTrue(SkyRules.rainbow(report(81), NOW))
        assertTrue(SkyRules.rainbow(report(82), NOW))
    }

    @Test
    fun `no sun, no rainbow`() {
        // Night, however recently it rained.
        assertFalse(SkyRules.rainbow(report(0, isDay = false, hourly = listOf(hour(HOUR, 63))), NOW))
        assertFalse(SkyRules.rainbow(report(80, isDay = false), NOW))
        // Overcast, fog and an active thunderstorm all hide the sun.
        assertFalse(SkyRules.rainbow(report(3, hourly = listOf(hour(HOUR, 63))), NOW))
        assertFalse(SkyRules.rainbow(report(45, hourly = listOf(hour(HOUR, 63))), NOW))
        assertFalse(SkyRules.rainbow(report(95, hourly = listOf(hour(HOUR, 63))), NOW))
    }

    @Test
    fun `no recent rain, no rainbow`() {
        assertFalse("a dry sunny day", SkyRules.rainbow(report(0), NOW))
        assertFalse(
            "rain older than the window",
            SkyRules.rainbow(report(0, hourly = listOf(hour(SkyRules.RAINBOW_WINDOW_MS + HOUR, 63))), NOW),
        )
        assertFalse("snow is not rain", SkyRules.rainbow(report(0, hourly = listOf(hour(HOUR, 73))), NOW))
    }

    @Test
    fun `a forecast of rain later today is not a rainbow now`() {
        val later = HourPoint(NOW + HOUR, 60.0, 63, 80, true)
        assertFalse(SkyRules.rainbow(report(0, hourly = listOf(later)), NOW))
    }

    @Test
    fun `rain exactly at the edge of the window still counts`() {
        assertTrue(SkyRules.rainbow(report(0, hourly = listOf(hour(SkyRules.RAINBOW_WINDOW_MS, 63))), NOW))
    }

    // ---- the whole decision ----

    @Test
    fun `of carries the scene, the light and the intensity of the report`() {
        val rainy = SkyRules.of(report(65), NOW)
        assertEquals(SkyScene.RAIN, rainy.scene)
        assertTrue(rainy.isDay)
        assertEquals(SkyRules.intensity(65), rainy.intensity, 0f)

        val night = SkyRules.of(report(0, isDay = false), NOW)
        assertEquals(SkyScene.CLEAR, night.scene)
        assertFalse(night.isDay)

        val bow = SkyRules.of(report(1, hourly = listOf(hour(HOUR, 81))), NOW)
        assertEquals(SkyScene.RAINBOW, bow.scene)
        assertEquals(SkyRules.intensity(1), bow.intensity, 0f)
    }

    private companion object {
        const val HOUR = WeatherReport.HOUR_MS
        /** A fixed "now"; nothing here reads the clock. */
        const val NOW = 1_758_470_400_000L
    }
}

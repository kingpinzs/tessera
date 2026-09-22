package app.tileshell.weather

import app.tileshell.tiles.engine.SkyScene
import app.tileshell.tiles.engine.WeatherSky

/**
 * Which sky the Weather tile's main face animates, decided from the report the feed already has
 * (Jeremy, INDEX Change Log 2026-09-21 item 2: "animation of sunny, raining, snowing, rainbow ect.").
 *
 * This is the weather half of the feature and it lives here, beside [WmoCodes], for the same reason the
 * words and the glyphs do: the WMO table is provider knowledge, and Start should not have to know that a
 * 3 is overcast and an 82 is a violent shower. Start is handed a [WeatherSky] and paints it.
 *
 * Everything here is pure and takes its "now" as an argument, so the mapping and the rainbow trigger are
 * unit-testable without a device — which matters because the rainbow cannot be summoned on demand in QA:
 * it needs real weather, and E9 runs against whatever the sky is doing that morning.
 */
object SkyRules {
    /**
     * How far back a shower still counts as "it has just rained" for the rainbow.
     *
     * Two hours is the trailing edge of the hourly series the feed already holds — Open-Meteo returns the
     * whole local day hour by hour, so the hours BEFORE now are in the response we already made. No new
     * request, no new field, no new provider capability.
     */
    const val RAINBOW_WINDOW_MS = 2 * WeatherReport.HOUR_MS

    /** WMO 80-82: rain SHOWERS. A shower is convective — broken cloud with sun between the cells. */
    private val SHOWERS = 80..82

    /** The sun is out enough to light a rainbow: clear, part cloud, or a shower's own gaps. */
    private fun sunIsOut(code: Int): Boolean =
        WmoCodes.group(code) == ConditionGroup.CLEAR || WmoCodes.group(code) == ConditionGroup.PARTLY_CLOUDY || code in SHOWERS

    /**
     * The "something fun" case, and it is not invented: a rainbow is sunlight refracted through raindrops
     * that are still in the air, so the real-world precondition is exactly **sun out, and rain falling or
     * just finished**. That is the picture this returns true for:
     *
     *  - it is day (`current.is_day`) — no sun, no bow;
     *  - the sun is out NOW (clear / partly cloudy, or a shower, which by definition has gaps); and
     *  - rain is falling now or fell inside [RAINBOW_WINDOW_MS], read off the hourly codes already fetched.
     *
     * Every input is a field the provider fills today. Nothing here needs a second call, a radar image or a
     * sun-altitude ephemeris, and the trigger stays rare enough to be a treat: it takes a shower to clear.
     */
    fun rainbow(report: WeatherReport, nowMs: Long): Boolean {
        val current = report.current
        if (!current.isDay) return false
        if (!sunIsOut(current.code)) return false
        if (current.code in SHOWERS) return true // a sun shower: the drops are in the air right now
        return report.hourly.any { hour ->
            hour.timeMs <= nowMs && nowMs - hour.timeMs <= RAINBOW_WINDOW_MS && WmoCodes.group(hour.code) == ConditionGroup.RAIN
        }
    }

    /** The condition group's picture, with the rainbow overriding the plain sun it would otherwise be. */
    fun scene(code: Int, rainbow: Boolean): SkyScene {
        if (rainbow) return SkyScene.RAINBOW
        return when (WmoCodes.group(code)) {
            ConditionGroup.CLEAR -> SkyScene.CLEAR
            ConditionGroup.PARTLY_CLOUDY -> SkyScene.PARTLY
            ConditionGroup.CLOUDY -> SkyScene.CLOUDY
            ConditionGroup.FOG -> SkyScene.FOG
            ConditionGroup.RAIN -> SkyScene.RAIN
            ConditionGroup.SNOW -> SkyScene.SNOW
            ConditionGroup.THUNDER -> SkyScene.THUNDER
        }
    }

    /**
     * 0..1 "how much of it there is": the WMO code's own light / moderate / heavy step. It drives how many
     * drops or flakes fall and how fast, so a drizzle does not look like a downpour. For the dry scenes it
     * reads as cloud cover, which is the same axis: 0 is a bare sky, 1 is solid overcast.
     */
    fun intensity(code: Int): Float = when (code) {
        0 -> 0f
        1 -> 0.25f
        2 -> 0.5f
        3 -> 0.85f
        45 -> 0.6f
        48 -> 0.85f
        // light: drizzle, freezing drizzle, light rain, light snow, slight showers
        51, 56, 61, 66, 71, 80, 85 -> 0.35f
        // moderate: drizzle, rain, snow, snow grains, moderate showers, a plain thunderstorm
        53, 63, 73, 77, 81, 86, 95 -> 0.65f
        // heavy: dense drizzle, heavy rain, freezing rain, heavy snow, violent showers, hail
        55, 57, 65, 67, 75, 82, 96, 99 -> 1f
        // An unmapped code renders as CLOUDY (WmoCodes.group's own fallback); give it the same middle cover.
        else -> 0.5f
    }

    /** The whole decision for one report: what to draw, in which light, and how hard. */
    fun of(report: WeatherReport, nowMs: Long): WeatherSky {
        val current = report.current
        return WeatherSky(
            scene = scene(current.code, rainbow(report, nowMs)),
            isDay = current.isDay,
            intensity = intensity(current.code),
        )
    }
}

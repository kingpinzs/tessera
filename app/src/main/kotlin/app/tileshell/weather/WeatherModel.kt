package app.tileshell.weather

import android.content.Context
import app.tileshell.brand.Glyph
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Units follow the device region (phase 01 Decisions: "units = device region"): °F for the US, Liberia and Myanmar,
 * °C everywhere else. An explicit temperature unit in the locale's Unicode extension (Android 14 regional
 * preferences, `-u-mu-fahrenhe` / `-u-mu-celsius`) is the device's own setting, so it wins over the region.
 */
enum class WeatherUnits {
    IMPERIAL, METRIC;

    companion object {
        private val FAHRENHEIT_REGIONS = setOf("US", "LR", "MM")

        fun current(locale: Locale = Locale.getDefault(Locale.Category.FORMAT)): WeatherUnits {
            val preference: String? = locale.getUnicodeLocaleType("mu")
            return when {
                preference == "fahrenhe" -> IMPERIAL
                preference == "celsius" -> METRIC
                locale.country.uppercase(Locale.ROOT) in FAHRENHEIT_REGIONS -> IMPERIAL
                else -> METRIC
            }
        }
    }
}

/** Condition groups key the Weather app's background (approximation X2) and the glyph choice. */
enum class ConditionGroup { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, THUNDER }

/** WMO weather interpretation codes (as Open-Meteo returns them) → MSN Weather-style words and the Fluent glyphs in [Glyph]. */
object WmoCodes {
    fun group(code: Int): ConditionGroup = when (code) {
        0 -> ConditionGroup.CLEAR
        1, 2 -> ConditionGroup.PARTLY_CLOUDY
        3 -> ConditionGroup.CLOUDY
        45, 48 -> ConditionGroup.FOG
        in 51..67, in 80..82 -> ConditionGroup.RAIN
        in 71..77, 85, 86 -> ConditionGroup.SNOW
        in 95..99 -> ConditionGroup.THUNDER
        else -> ConditionGroup.CLOUDY
    }

    fun word(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "Sunny" else "Clear"
        1 -> if (isDay) "Mostly Sunny" else "Mostly Clear"
        2 -> if (isDay) "Partly Sunny" else "Partly Cloudy"
        3 -> "Cloudy"
        45 -> "Fog"
        48 -> "Freezing Fog"
        51 -> "Light Drizzle"
        53 -> "Drizzle"
        55 -> "Heavy Drizzle"
        56, 57 -> "Freezing Drizzle"
        61 -> "Light Rain"
        63 -> "Rain"
        65 -> "Heavy Rain"
        66, 67 -> "Freezing Rain"
        71 -> "Light Snow"
        73 -> "Snow"
        75 -> "Heavy Snow"
        77 -> "Snow Grains"
        80 -> "Light Rain Showers"
        81 -> "Rain Showers"
        82 -> "Heavy Rain Showers"
        85 -> "Light Snow Showers"
        86 -> "Snow Showers"
        95 -> "Thunderstorms"
        96, 99 -> "Thunderstorms with Hail"
        else -> "Weather code $code"
    }

    fun glyph(code: Int, isDay: Boolean): String = when (group(code)) {
        ConditionGroup.CLEAR -> if (isDay) Glyph.WEATHER_SUNNY else Glyph.WEATHER_MOON
        ConditionGroup.PARTLY_CLOUDY -> if (isDay) Glyph.WEATHER_PARTLY else Glyph.WEATHER_MOON
        ConditionGroup.CLOUDY -> Glyph.WEATHER_CLOUDY
        ConditionGroup.FOG -> Glyph.WEATHER_FOG
        ConditionGroup.RAIN -> Glyph.WEATHER_RAIN
        ConditionGroup.SNOW -> Glyph.WEATHER_SNOW
        ConditionGroup.THUNDER -> Glyph.WEATHER_THUNDER
    }
}

data class CurrentConditions(
    val timeMs: Long,
    val temperature: Double,
    val feelsLike: Double,
    val humidityPct: Int,
    val pressureHpa: Double,
    val windSpeed: Double,
    val windDirectionDeg: Int,
    val code: Int,
    val isDay: Boolean,
)

data class HourPoint(val timeMs: Long, val temperature: Double, val code: Int, val precipPct: Int?, val isDay: Boolean)

data class DayPoint(val dateMs: Long, val code: Int, val high: Double, val low: Double, val precipPct: Int?, val sunriseMs: Long?, val sunsetMs: Long?)

/** Provider-neutral weather data. Temperatures and wind are in [units]; pressure is always hPa. */
data class WeatherReport(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val place: String?,
    val timeZoneId: String,
    val units: WeatherUnits,
    val fetchedAtMs: Long,
    val current: CurrentConditions,
    val hourly: List<HourPoint>,
    val daily: List<DayPoint>,
) {
    val timeZone: TimeZone get() = TimeZone.getTimeZone(timeZoneId)

    /** Days from today (in the location's zone) onward. */
    fun upcomingDays(nowMs: Long): List<DayPoint> = daily.filter { it.dateMs + DAY_MS > nowMs }

    /** Today: the next 24 hours from the current hour. Any other day: that day's hours. */
    fun hoursFor(day: DayPoint?, nowMs: Long): List<HourPoint> {
        if (day == null || day.dateMs <= nowMs) return hourly.filter { it.timeMs + HOUR_MS > nowMs }.take(24)
        val next = daily.firstOrNull { it.dateMs > day.dateMs }?.dateMs ?: (day.dateMs + DAY_MS)
        return hourly.filter { it.timeMs >= day.dateMs && it.timeMs < next }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("provider", provider).put("latitude", latitude).put("longitude", longitude)
        .put("place", place ?: JSONObject.NULL).put("timeZoneId", timeZoneId).put("units", units.name)
        .put("fetchedAtMs", fetchedAtMs)
        .put("current", JSONObject()
            .put("timeMs", current.timeMs).put("temperature", current.temperature).put("feelsLike", current.feelsLike)
            .put("humidityPct", current.humidityPct).put("pressureHpa", current.pressureHpa).put("windSpeed", current.windSpeed)
            .put("windDirectionDeg", current.windDirectionDeg).put("code", current.code).put("isDay", current.isDay))
        .put("hourly", JSONArray().also { arr ->
            hourly.forEach { h ->
                arr.put(JSONObject().put("timeMs", h.timeMs).put("temperature", h.temperature).put("code", h.code)
                    .put("precipPct", h.precipPct ?: JSONObject.NULL).put("isDay", h.isDay))
            }
        })
        .put("daily", JSONArray().also { arr ->
            daily.forEach { d ->
                arr.put(JSONObject().put("dateMs", d.dateMs).put("code", d.code).put("high", d.high).put("low", d.low)
                    .put("precipPct", d.precipPct ?: JSONObject.NULL).put("sunriseMs", d.sunriseMs ?: JSONObject.NULL)
                    .put("sunsetMs", d.sunsetMs ?: JSONObject.NULL))
            }
        })

    companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS

        fun fromJson(o: JSONObject): WeatherReport {
            val c = o.getJSONObject("current")
            val hourlyArr = o.getJSONArray("hourly")
            val dailyArr = o.getJSONArray("daily")
            return WeatherReport(
                provider = o.getString("provider"),
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
                place = if (o.isNull("place")) null else o.getString("place"),
                timeZoneId = o.getString("timeZoneId"),
                units = WeatherUnits.valueOf(o.getString("units")),
                fetchedAtMs = o.getLong("fetchedAtMs"),
                current = CurrentConditions(
                    c.getLong("timeMs"), c.getDouble("temperature"), c.getDouble("feelsLike"), c.getInt("humidityPct"),
                    c.getDouble("pressureHpa"), c.getDouble("windSpeed"), c.getInt("windDirectionDeg"), c.getInt("code"), c.getBoolean("isDay"),
                ),
                hourly = (0 until hourlyArr.length()).map { i ->
                    val h = hourlyArr.getJSONObject(i)
                    HourPoint(h.getLong("timeMs"), h.getDouble("temperature"), h.getInt("code"), h.optIntOrNull("precipPct"), h.getBoolean("isDay"))
                },
                daily = (0 until dailyArr.length()).map { i ->
                    val d = dailyArr.getJSONObject(i)
                    DayPoint(d.getLong("dateMs"), d.getInt("code"), d.getDouble("high"), d.getDouble("low"), d.optIntOrNull("precipPct"),
                        d.optLongOrNull("sunriseMs"), d.optLongOrNull("sunsetMs"))
                },
            )
        }
    }
}

internal fun JSONObject.optIntOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
internal fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)

/** Display strings shared by the Weather tile and the Weather app. */
object WeatherFormat {
    private val COMPASS = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    fun degrees(value: Double): String = "${value.roundToInt()}°"

    fun compass(deg: Int): String = COMPASS[(((deg % 360) + 360) % 360 * 16 + 180) / 360 % 16]

    fun wind(units: WeatherUnits, speed: Double, directionDeg: Int): String =
        "${compass(directionDeg)} ${speed.roundToInt()} ${if (units == WeatherUnits.IMPERIAL) "mph" else "km/h"}"

    fun pressure(units: WeatherUnits, hPa: Double): String =
        if (units == WeatherUnits.IMPERIAL) String.format(Locale.getDefault(), "%.2f in", hPa * 0.0295300)
        else "${hPa.roundToInt()} mb"

    /** "h:mm" (or "H:mm" when the device uses 24-hour time); X22's "Updated h:mm" text uses it. */
    fun clock(context: Context, ms: Long): String =
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", Locale.getDefault()).format(Date(ms))

    fun dayName(ms: Long, zone: TimeZone): String = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = zone }.format(Date(ms))

    fun dayCell(ms: Long, zone: TimeZone): String = SimpleDateFormat("EEE d", Locale.getDefault()).apply { timeZone = zone }.format(Date(ms))

    fun hour(context: Context, ms: Long, zone: TimeZone): String =
        SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h a", Locale.getDefault())
            .apply { timeZone = zone }.format(Date(ms))

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.getDefault(), "%.2f°%s, %.2f°%s", abs(lat), if (lat >= 0) "N" else "S", abs(lon), if (lon >= 0) "E" else "W")
}

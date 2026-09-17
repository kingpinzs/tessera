package app.tileshell.weather

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * The swappable weather source (phase 01 Decisions: "the provider sits behind a swappable interface"; a provider's
 * commercial licence matters if the project is ever sold, A10). [fetch] is a blocking network call: it throws
 * [IOException] when the network fails and [WeatherProviderException] when the provider answers with an error or
 * a payload the parser cannot read.
 */
interface WeatherProvider {
    val id: String

    /** Attribution the provider's licence requires, shown in the Weather app. */
    val attribution: String

    /** A replayable description of the request (URL for HTTP providers), logged to diagnostics so QA can capture the same response. */
    fun describeRequest(latitude: Double, longitude: Double, units: WeatherUnits): String

    fun fetch(latitude: Double, longitude: Double, units: WeatherUnits): WeatherReport
}

class WeatherProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Open-Meteo forecast API (https://open-meteo.com, no key; data CC BY 4.0, so the app shows [attribution]).
 * One request carries current conditions, hourly values for the 10 days and the 10-day daily summary; times are
 * requested as unix seconds and the location's IANA zone comes back in `timezone`.
 */
class OpenMeteoProvider : WeatherProvider {
    override val id = "open-meteo"
    override val attribution = "Weather data by Open-Meteo.com (CC BY 4.0)"

    override fun describeRequest(latitude: Double, longitude: Double, units: WeatherUnits): String {
        val imperial = units == WeatherUnits.IMPERIAL
        return buildString {
            append(ENDPOINT)
            append("?latitude=").append(String.format(Locale.ROOT, "%.4f", latitude))
            append("&longitude=").append(String.format(Locale.ROOT, "%.4f", longitude))
            append("&current=").append(CURRENT_FIELDS)
            append("&hourly=").append(HOURLY_FIELDS)
            append("&daily=").append(DAILY_FIELDS)
            append("&forecast_days=10&timezone=auto&timeformat=unixtime")
            append("&temperature_unit=").append(if (imperial) "fahrenheit" else "celsius")
            append("&wind_speed_unit=").append(if (imperial) "mph" else "kmh")
        }
    }

    override fun fetch(latitude: Double, longitude: Double, units: WeatherUnits): WeatherReport {
        val url = describeRequest(latitude, longitude, units)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val reason = runCatching { JSONObject(body).optString("reason") }.getOrNull().orEmpty()
                throw WeatherProviderException("HTTP $code $reason".trim())
            }
            return parse(body, units, System.currentTimeMillis())
        } finally {
            conn.disconnect()
        }
    }

    /** Parses an Open-Meteo forecast body requested with [describeRequest]'s fields. Field names and types are checked against docs/plan/qa/phase-01/weather/open-meteo-sample.json. */
    fun parse(body: String, units: WeatherUnits, fetchedAtMs: Long): WeatherReport {
        try {
            val root = JSONObject(body)
            if (root.optBoolean("error", false)) throw WeatherProviderException("provider error: ${root.optString("reason")}")
            val c = root.getJSONObject("current")
            val current = CurrentConditions(
                timeMs = c.getLong("time") * 1000,
                temperature = c.getDouble("temperature_2m"),
                feelsLike = c.getDouble("apparent_temperature"),
                humidityPct = c.getInt("relative_humidity_2m"),
                pressureHpa = c.getDouble("pressure_msl"),
                windSpeed = c.getDouble("wind_speed_10m"),
                windDirectionDeg = c.getInt("wind_direction_10m"),
                code = c.getInt("weather_code"),
                isDay = c.getInt("is_day") == 1,
            )

            val h = root.getJSONObject("hourly")
            val hTime = h.getJSONArray("time")
            val hTemp = h.getJSONArray("temperature_2m")
            val hCode = h.getJSONArray("weather_code")
            val hPrecip = h.getJSONArray("precipitation_probability")
            val hDay = h.getJSONArray("is_day")
            val hourly = (0 until hTime.length()).mapNotNull { i ->
                if (hTemp.isNull(i) || hCode.isNull(i)) return@mapNotNull null
                HourPoint(hTime.getLong(i) * 1000, hTemp.getDouble(i), hCode.getInt(i), hPrecip.intOrNull(i), hDay.optInt(i, 1) == 1)
            }

            val d = root.getJSONObject("daily")
            val dTime = d.getJSONArray("time")
            val dCode = d.getJSONArray("weather_code")
            val dMax = d.getJSONArray("temperature_2m_max")
            val dMin = d.getJSONArray("temperature_2m_min")
            val dPrecip = d.getJSONArray("precipitation_probability_max")
            val dRise = d.getJSONArray("sunrise")
            val dSet = d.getJSONArray("sunset")
            val daily = (0 until dTime.length()).mapNotNull { i ->
                if (dCode.isNull(i) || dMax.isNull(i) || dMin.isNull(i)) return@mapNotNull null
                DayPoint(dTime.getLong(i) * 1000, dCode.getInt(i), dMax.getDouble(i), dMin.getDouble(i), dPrecip.intOrNull(i),
                    dRise.longOrNull(i)?.times(1000), dSet.longOrNull(i)?.times(1000))
            }

            return WeatherReport(
                provider = id,
                latitude = root.getDouble("latitude"),
                longitude = root.getDouble("longitude"),
                place = null,
                timeZoneId = root.optString("timezone", "UTC"),
                units = units,
                fetchedAtMs = fetchedAtMs,
                current = current,
                hourly = hourly,
                daily = daily,
            )
        } catch (e: JSONException) {
            throw WeatherProviderException("unreadable payload: ${e.message}", e)
        }
    }

    private fun JSONArray.intOrNull(i: Int): Int? = if (i >= length() || isNull(i)) null else getInt(i)
    private fun JSONArray.longOrNull(i: Int): Long? = if (i >= length() || isNull(i)) null else getLong(i)

    private companion object {
        const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        const val CURRENT_FIELDS = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m,wind_direction_10m,pressure_msl"
        const val HOURLY_FIELDS = "temperature_2m,weather_code,precipitation_probability,is_day"
        const val DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
        const val TIMEOUT_MS = 15_000
    }
}

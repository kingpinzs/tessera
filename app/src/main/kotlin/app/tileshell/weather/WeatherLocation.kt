package app.tileshell.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationOutcome {
    data class Found(val latitude: Double, val longitude: Double, val source: String, val fixTimeMs: Long) : LocationOutcome
    data object NoPermission : LocationOutcome
    data object LocationOff : LocationOutcome
    data object NoFix : LocationOutcome
}

/** Device location through the platform [LocationManager] only (no Google Play services, P5): last known + one fresh fix. */
object WeatherLocation {
    private const val FIX_TIMEOUT_MS = 15_000L
    private const val GEOCODE_TIMEOUT_MS = 10_000L

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true

    suspend fun locate(context: Context): LocationOutcome {
        if (!hasPermission(context)) return LocationOutcome.NoPermission
        val lm = context.getSystemService(LocationManager::class.java) ?: return LocationOutcome.NoFix
        if (!lm.isLocationEnabled) return LocationOutcome.LocationOff
        val enabled = lm.getProviders(true)

        val lastKnown = enabled.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
        val freshProvider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in enabled }
        val fresh = freshProvider?.let { provider ->
            withTimeoutOrNull(FIX_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                    } catch (e: SecurityException) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        Diagnostics.add("weather", "location providers=$enabled lastKnown=${lastKnown?.provider} fresh=${fresh?.provider ?: "none"} via=$freshProvider")
        val chosen = fresh ?: lastKnown ?: return LocationOutcome.NoFix
        return LocationOutcome.Found(chosen.latitude, chosen.longitude, if (chosen === fresh) "fresh:${chosen.provider}" else "lastKnown:${chosen.provider}", chosen.time)
    }

    /**
     * Place name by reverse lookup through OpenStreetMap's Nominatim (P5: no Google; Android's platform Geocoder
     * is backed by Google services on the phone). It is part of the Weather feature's internet use (A11), runs
     * only when the location moved, and follows Nominatim's usage policy (identifying User-Agent, one request).
     * Null when the lookup fails; the app then shows coordinates.
     */
    suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&lat=$latitude&lon=$longitude&accept-language=" + java.util.Locale.getDefault().toLanguageTag())
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = GEOCODE_TIMEOUT_MS.toInt()
            conn.readTimeout = GEOCODE_TIMEOUT_MS.toInt()
            conn.setRequestProperty("User-Agent", "tileshell-launcher/" + (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"))
            try {
                if (conn.responseCode != 200) error("HTTP " + conn.responseCode)
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val address = json.optJSONObject("address")
                val name = address?.let { a ->
                    listOf("city", "town", "village", "municipality", "county", "state", "country").firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.isNotBlank() } }
                }
                Diagnostics.add("weather", "place lookup (nominatim) -> $name")
                name
            } finally {
                conn.disconnect()
            }
        }.onFailure { Diagnostics.add("weather", "place lookup failed: $it") }.getOrNull()
    }
}

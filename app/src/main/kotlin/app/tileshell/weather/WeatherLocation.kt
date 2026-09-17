package app.tileshell.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.suspendCancellableCoroutine
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

    /** Place name from the platform [Geocoder] when the device has a geocoder backend; null otherwise (the app then shows coordinates). */
    suspend fun placeName(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val addresses = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            suspendCancellableCoroutine<List<Address>?> { cont ->
                try {
                    Geocoder(context).getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                    })
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        val a = addresses?.firstOrNull() ?: return null
        return a.locality ?: a.subAdminArea ?: a.adminArea ?: a.countryName
    }
}

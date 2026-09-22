package app.tileshell.cortana

import android.content.Context
import app.tileshell.cortana.reminders.Place
import app.tileshell.cortana.reminders.ReminderStore
import app.tileshell.diag.Diagnostics
import app.tileshell.weather.LocationOutcome
import app.tileshell.weather.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * The two ways a place is saved (place source ruled C, 2026-09-17, Jeremy):
 *
 *  1. **at the spot** — "Save current location" on the Places page, or saying "this is home". GPS only:
 *     no internet, no Google.
 *  2. **by typing an address** — looked up ONCE through OpenStreetMap's Nominatim (non-Google, P5). That
 *     single request at the moment of saving is the only network call a place ever makes; the result is
 *     cached with the place, and the Places page carries "© OpenStreetMap contributors".
 *
 * This amends A11 with that one lookup, and with nothing else.
 */
object PlaceSaver {

    /** What the Places page shows after a save attempt. */
    sealed interface Result {
        data class Saved(val place: Place) : Result
        data object NoPermission : Result
        data object LocationOff : Result
        data object NoFix : Result
        /** Nominatim unreachable, rate-limited, or offline — nothing is saved (edge case). */
        data object LookupFailed : Result
        data object NotFound : Result
        /** Several matches: the user picks (edge case "several matches"). */
        data class Choices(val name: String, val options: List<Candidate>) : Result
    }

    data class Candidate(val label: String, val lat: Double, val lon: Double)

    private val lastResult = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = lastResult.asStateFlow()

    fun clearResult() {
        lastResult.value = null
    }

    suspend fun saveCurrentLocation(context: Context, name: String): Result {
        val outcome = WeatherLocation.locate(context)
        val result = when (outcome) {
            is LocationOutcome.Found -> Result.Saved(
                ReminderStore.get(context).savePlace(
                    Place(id = "", name = name, lat = outcome.latitude, lon = outcome.longitude, source = "current")
                )
            )
            is LocationOutcome.NoPermission -> Result.NoPermission
            is LocationOutcome.LocationOff -> Result.LocationOff
            is LocationOutcome.NoFix -> Result.NoFix
        }
        Diagnostics.add("places", "save current location \"$name\" -> ${result.javaClass.simpleName}")
        lastResult.value = result
        return result
    }

    suspend fun saveTypedAddress(context: Context, name: String, address: String): Result {
        val candidates = search(context, address)
        val result = when {
            candidates == null -> Result.LookupFailed
            candidates.isEmpty() -> Result.NotFound
            candidates.size > 1 -> Result.Choices(name, candidates)
            else -> Result.Saved(
                ReminderStore.get(context).savePlace(
                    Place(id = "", name = name, lat = candidates[0].lat, lon = candidates[0].lon, source = "typed")
                )
            )
        }
        Diagnostics.add("places", "typed address \"$address\" as \"$name\" -> ${result.javaClass.simpleName}")
        lastResult.value = result
        return result
    }

    /** The user picked one of the several matches. */
    fun saveChoice(context: Context, name: String, candidate: Candidate): Place =
        ReminderStore.get(context).savePlace(
            Place(id = "", name = name, lat = candidate.lat, lon = candidate.lon, source = "typed")
        ).also { lastResult.value = Result.Saved(it) }

    /** null = the lookup itself failed (unreachable, rate-limited, offline); empty = no match. */
    private suspend fun search(context: Context, address: String): List<Candidate>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" +
                    java.net.URLEncoder.encode(address, "UTF-8") +
                    "&accept-language=" + Locale.getDefault().toLanguageTag()
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                // Nominatim's usage policy: an identifying User-Agent, and one request per save.
                setRequestProperty(
                    "User-Agent",
                    "tileshell-launcher/" + (
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull() ?: "0"
                        ),
                )
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Diagnostics.add("places", "nominatim search HTTP ${connection.responseCode}")
                    return@withContext null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.getJSONObject(index)
                    val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                    val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                    Candidate(item.optString("display_name").ifBlank { "$lat, $lon" }, lat, lon)
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Diagnostics.add("places", "nominatim search failed: $it") }.getOrNull()
    }

    private const val TIMEOUT_MS = 8000
}

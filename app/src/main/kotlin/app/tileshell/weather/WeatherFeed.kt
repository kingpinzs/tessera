package app.tileshell.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.AtomicFile
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.engine.FaceTransition
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Weather feed: fetches on start and every 30 minutes while the process lives, caches the last report in
 * app-private storage, and publishes the Weather tile ([LiveTileEngine.WEATHER]: current-conditions face + days face, FLIP).
 * Stale rule X22 (H30): a report older than 60 minutes keeps its conditions and gains an "Updated h:mm" line.
 */
object WeatherFeed {
    const val REFRESH_INTERVAL_MS = 30 * 60_000L
    const val STALE_AFTER_MS = 60 * 60_000L
    private const val CHECK_EVERY_MS = 60_000L
    private const val CACHE_FILE = "weather/last-report.json"

    enum class Problem { NO_PERMISSION, LOCATION_OFF, NO_LOCATION, NO_NETWORK, PROVIDER_ERROR }

    data class State(val report: WeatherReport? = null, val problems: List<Problem> = emptyList(), val refreshing: Boolean = false)

    /** Swappable provider (phase 01 Decisions); Open-Meteo is the recorded pick. */
    @Volatile var provider: WeatherProvider = OpenMeteoProvider()

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private var appContext: Context? = null
    @Volatile private var lastAttemptWallMs = Long.MIN_VALUE / 2
    @Volatile private var lastAttemptElapsedMs = Long.MIN_VALUE / 2
    @Volatile private var publishedStale: Boolean? = null

    /** Call once per process (repeat calls are no-ops). */
    @Synchronized
    fun start(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        Diagnostics.add("weather", "feed start provider=${provider.id}")

        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { scope.launch { check("broadcast ${intent.action}") } }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }, Context.RECEIVER_NOT_EXPORTED)

        app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (Problem.NO_NETWORK in mutableState.value.problems) scope.launch { refresh("network available") }
            }
        })

        scope.launch {
            loadCache(app)?.let { cached ->
                mutableState.update { it.copy(report = cached) }
                Diagnostics.add("weather", "cache loaded fetchedAt=${cached.fetchedAtMs} provider=${cached.provider} units=${cached.units}")
                publishTile(app, cached)
            }
            refresh("start")
            while (true) {
                delay(CHECK_EVERY_MS)
                check("minute")
            }
        }
    }

    /** The Weather app resumed: retry when something was wrong (a permission or location setting may have changed) or the data is due. */
    fun onAppResumed() {
        scope.launch { if (mutableState.value.problems.isNotEmpty()) refresh("app resumed") else check("app resumed") }
    }

    /** A user tap on a retry row. */
    fun refreshNow(reason: String) {
        scope.launch { refresh(reason) }
    }

    private suspend fun check(reason: String) {
        val app = appContext ?: return
        val report = mutableState.value.report
        val due = System.currentTimeMillis() - lastAttemptWallMs >= REFRESH_INTERVAL_MS ||
            SystemClock.elapsedRealtime() - lastAttemptElapsedMs >= REFRESH_INTERVAL_MS
        val unitsChanged = report != null && report.units != WeatherUnits.current()
        when {
            unitsChanged -> refresh("units changed ($reason)")
            due -> refresh("interval ($reason)")
            report != null && isStale(report) != publishedStale -> publishTile(app, report)
        }
    }

    private suspend fun refresh(reason: String) {
        val app = appContext ?: return
        if (!refreshLock.tryLock()) {
            Diagnostics.add("weather", "refresh skipped reason=$reason: one is already running")
            return
        }
        try {
            lastAttemptWallMs = System.currentTimeMillis()
            lastAttemptElapsedMs = SystemClock.elapsedRealtime()
            mutableState.update { it.copy(refreshing = true) }
            val units = WeatherUnits.current()
            val cached = mutableState.value.report
            Diagnostics.add("weather", "refresh start reason=$reason units=$units")

            val problems = mutableListOf<Problem>()
            // Location off / no fix: keep serving the last place (the tile stays current), with the problem shown in the app.
            val coords: Pair<Double, Double>? = when (val loc = WeatherLocation.locate(app)) {
                is LocationOutcome.Found -> loc.latitude to loc.longitude
                LocationOutcome.NoPermission -> { problems += Problem.NO_PERMISSION; null }
                LocationOutcome.LocationOff -> { problems += Problem.LOCATION_OFF; cached?.let { it.latitude to it.longitude } }
                LocationOutcome.NoFix -> { problems += Problem.NO_LOCATION; cached?.let { it.latitude to it.longitude } }
            }
            if (coords == null) return finish(app, problems, "no coordinates")
            if (!networkAvailable(app)) return finish(app, problems + Problem.NO_NETWORK, "no network")

            // Two decimals (about 1 km) is all a forecast needs; nothing finer leaves the device.
            val lat = (coords.first * 100).roundToInt() / 100.0
            val lon = (coords.second * 100).roundToInt() / 100.0
            val p = provider
            Diagnostics.add("weather", "fetch provider=${p.id} request=${p.describeRequest(lat, lon, units)}")
            val started = SystemClock.elapsedRealtime()
            val report = try {
                p.fetch(lat, lon, units)
            } catch (e: IOException) {
                Diagnostics.add("weather", "fetch error network ${e.javaClass.simpleName}: ${e.message}")
                return finish(app, problems + Problem.NO_NETWORK, "network error")
            } catch (e: Exception) {
                Diagnostics.add("weather", "fetch error provider ${e.javaClass.simpleName}: ${e.message}")
                return finish(app, problems + Problem.PROVIDER_ERROR, "provider error")
            }

            val samePlace = cached != null && abs(cached.latitude - report.latitude) < 0.05 && abs(cached.longitude - report.longitude) < 0.05
            val place = cached?.place?.takeIf { samePlace } ?: WeatherLocation.placeName(app, lat, lon)
            val full = report.copy(place = place)
            Diagnostics.add("weather", "fetch ok provider=${p.id} ms=${SystemClock.elapsedRealtime() - started} grid=${full.latitude},${full.longitude} " +
                "tz=${full.timeZoneId} current=${full.current.code}/${full.current.temperature} hourly=${full.hourly.size} daily=${full.daily.size} place=${place != null}")
            saveCache(app, full)
            mutableState.value = State(report = full, problems = problems, refreshing = false)
            publishTile(app, full)
        } finally {
            mutableState.update { it.copy(refreshing = false) }
            refreshLock.unlock()
        }
    }

    private fun finish(app: Context, problems: List<Problem>, why: String) {
        Diagnostics.add("weather", "refresh ended without new data: $why problems=$problems")
        mutableState.update { it.copy(problems = problems, refreshing = false) }
        mutableState.value.report?.let { publishTile(app, it) }
    }

    fun isStale(report: WeatherReport, nowMs: Long = System.currentTimeMillis()): Boolean = nowMs - report.fetchedAtMs > STALE_AFTER_MS

    private fun publishTile(app: Context, report: WeatherReport) {
        val now = System.currentTimeMillis()
        val stale = isStale(report, now)
        val staleText = if (stale) "Updated ${WeatherFormat.clock(app, report.fetchedAtMs)}" else null
        val c = report.current
        val days = report.upcomingDays(now)
        val today = days.firstOrNull()
        val details = buildList {
            today?.let { add("${WeatherFormat.degrees(it.high)}/${WeatherFormat.degrees(it.low)}") }
            today?.precipPct?.let { add("Precip $it%") }
            add("Wind ${WeatherFormat.wind(report.units, c.windSpeed, c.windDirectionDeg)}")
        }
        // The tile's MAIN face is the day's weather, animated (Jeremy, INDEX Change Log 2026-09-21 item 2), so
        // the current conditions go in TileContent.front — the place a face takes over the logo front — and
        // only the 3-day face takes a turn behind it. Publishing conditions in both would flip the tile
        // between two identical pictures every 5 s.
        val sky = SkyRules.of(report, now)
        val front = TileFace.WeatherNow(
            condition = WmoCodes.word(c.code, c.isDay),
            temperature = WeatherFormat.degrees(c.temperature),
            details = details,
            stale = staleText,
            place = report.place,
            sky = sky,
        )
        val faces = buildList<TileFace> {
            if (days.isNotEmpty()) {
                add(TileFace.WeatherDays(days.take(3).map { d ->
                    Triple(WeatherFormat.dayName(d.dateMs, report.timeZone), WmoCodes.glyph(d.code, true), "${WeatherFormat.degrees(d.high)}/${WeatherFormat.degrees(d.low)}")
                }, staleText))
            }
        }
        LiveTileEngine.publish(
            LiveTileEngine.WEATHER,
            TileContent(faces, FaceTransition.FLIP, sourceTimeMs = report.fetchedAtMs, sourceTag = "weather:${report.provider}", front = front),
        )
        publishedStale = stale
        Diagnostics.add("weather", "publish tile faces=${faces.size} temp=${WeatherFormat.degrees(c.temperature)} condition=${c.code} " +
            "sky=${sky.scene}/${if (sky.isDay) "day" else "night"}/${sky.intensity} stale=$stale fetchedAt=${report.fetchedAtMs}")
    }

    private fun networkAvailable(app: Context): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun cacheFile(app: Context) = AtomicFile(File(app.filesDir, CACHE_FILE).also { it.parentFile?.mkdirs() })

    private fun loadCache(app: Context): WeatherReport? = try {
        val file = cacheFile(app)
        if (!file.baseFile.exists()) null else WeatherReport.fromJson(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    } catch (e: Exception) {
        Diagnostics.add("weather", "cache unreadable ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private fun saveCache(app: Context, report: WeatherReport) {
        val file = cacheFile(app)
        val out = file.startWrite()
        try {
            out.write(report.toJson().toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Diagnostics.add("weather", "cache write failed ${e.message}")
        }
    }
}

package app.tileshell.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.tiles.TileSize
import app.tileshell.tiles.api.LiveTileStore
import app.tileshell.tiles.api.SecondaryTiles
import app.tileshell.tiles.engine.LiveTileEngine
import app.tileshell.tiles.engine.TileContent
import app.tileshell.tiles.engine.TileFace
import app.tileshell.tiles.engine.TileRouting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Alarms & Clock's tile faces (phase 15 build task 4 / Decisions "Tiles"; r11/clock.md §9, U10, H14):
 *
 *  - the next alarm on the app's own tile, published under `TileRouting.componentKey(<pkg>, ClockActivity)` —
 *    the COMPONENT's key, never the package's (build task 0's routing) — whenever the alarms change, and cleared
 *    when none is on;
 *  - a pinned timer's or the stopwatch's face on its secondary tile under `SecondaryTiles.contentKey`, kept
 *    current: the face carries a [TileFace.Clock.Tick] the tile itself runs, so it is published once per state
 *    change and never every second. `LiveTileStore` republishes a secondary tile's key from its own (empty) queue
 *    when the record is stored, which clears the face; the publisher watches the engine and puts it back.
 *
 * Started once per launcher process from ShellApp (after the unlock, like every feed).
 */
object ClockTiles {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    private val generation = MutableStateFlow(0)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        val store = ClockStore.get(app)
        val key = TileRouting.componentKey(app.packageName, TileRouting.CLOCK_ACTIVITY)
        // The face's text follows the 12/24-hour setting and the day words follow the date.
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { generation.value++ }
        }, IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) }, Context.RECEIVER_EXPORTED)
        runCatching {
            app.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { generation.value++ }
            })
        }
        scope.launch {
            combine(store.alarms, generation) { alarms, _ -> alarms }.collect { alarms -> publishNextAlarm(app, store, key, alarms) }
        }
        scope.launch {
            combine(SecondaryTiles.tiles, store.timers, store.stopwatch, LiveTileEngine.content) { tiles, timers, sw, content -> Pinned(tiles, timers, sw, content) }
                .collect { publishPinned(app, store, it) }
        }
        Diagnostics.add("clock", "tiles: publisher started under $key")
    }

    private class Pinned(
        val tiles: Map<String, SecondaryTiles.SecondaryTileInfo>,
        val timers: List<ClockTimer>,
        val stopwatch: Stopwatch,
        val content: Map<String, TileContent>,
    )

    private fun publishNextAlarm(app: Context, store: ClockStore, key: String, alarms: List<Alarm>) {
        val now = System.currentTimeMillis()
        val zone = store.zone()
        val next = alarms.filter { it.enabled }.mapNotNull { a -> ClockRules.nextTrigger(a, now, zone)?.let { a to it } }.minByOrNull { it.second }
        if (next == null) {
            LiveTileEngine.publish(key, null)
            Diagnostics.add("clock", "tile: no next alarm, face cleared")
            return
        }
        val (alarm, at) = next
        val is24h = DateFormat.is24HourFormat(app)
        val locale = app.resources.configuration.locales[0]
        val (headline, lines) = ClockText.tileLines(alarm, LocalDate.now(zone), is24h, locale)
        // The front face (TileContent.front): the tile's face IS the next alarm, as Weather's is its conditions.
        LiveTileEngine.publish(key, TileContent(faces = emptyList(), sourceTimeMs = at, sourceTag = "clock", front = TileFace.Clock(headline, lines, Glyph.BELL)))
        Diagnostics.add("clock", "tile: next alarm ${alarm.id} at $at -> \"$headline\" ${lines}")
    }

    private fun publishPinned(app: Context, store: ClockStore, p: Pinned) {
        val pkg = app.packageName
        for (info in p.tiles.values) {
            if (info.owner != pkg) continue
            val key = SecondaryTiles.contentKey(pkg, info.tileId)
            val desired: TileContent? = when {
                info.tileId == TileRouting.STOPWATCH_TILE_ID -> stopwatchContent(store, p.stopwatch)
                info.tileId.startsWith(TileRouting.TIMER_TILE_PREFIX) -> {
                    val id = info.tileId.removePrefix(TileRouting.TIMER_TILE_PREFIX)
                    val t = p.timers.firstOrNull { it.id == id }
                    if (t == null) {
                        // The timer was deleted: its tile goes with it (a tile for nothing is not a tile).
                        Diagnostics.add("clock", "tile: timer $id gone, unpinning ${info.tileId}")
                        SecondaryTiles.delete(app, pkg, info.tileId)
                        continue
                    }
                    timerContent(store, t)
                }
                else -> continue
            }
            if (p.content[key] != desired) {
                LiveTileEngine.publish(key, desired)
                Diagnostics.add("clock", "tile: ${info.tileId} face -> ${(desired?.front as? TileFace.Clock)?.headline} tick=${(desired?.front as? TileFace.Clock)?.tick != null}")
            }
        }
    }

    /** Deterministic (the same state gives an equal face), so the engine watch never republishes what is already there. */
    private fun timerContent(store: ClockStore, t: ClockTimer): TileContent {
        val boot = store.bootCount()
        val tick = if (t.state == ClockTimer.State.RUNNING) {
            if (t.bootCount == boot && t.deadlineElapsedMs != null) TileFace.Clock.Tick(t.deadlineElapsedMs, 0L, countDown = true)
            else TileFace.Clock.Tick(t.deadlineWallMs ?: 0L, 0L, countDown = true, wall = true)
        } else null
        val headline = TileFace.Clock.hms(t.remainingMs)
        return TileContent(faces = emptyList(), sourceTag = "clock", front = TileFace.Clock(headline, listOf(t.name.ifBlank { "Timer" }), Glyph.HOURGLASS, tick))
    }

    private fun stopwatchContent(store: ClockStore, s: Stopwatch): TileContent {
        val boot = store.bootCount()
        val tick = if (s.running) {
            if (s.bootCount == boot && s.startElapsedMs != null) TileFace.Clock.Tick(s.accumulatedMs, s.startElapsedMs, countDown = false, rolloverMs = Stopwatch.ROLLOVER_MS)
            else TileFace.Clock.Tick(s.accumulatedMs, s.startWallMs ?: 0L, countDown = false, rolloverMs = Stopwatch.ROLLOVER_MS, wall = true)
        } else null
        return TileContent(faces = emptyList(), sourceTag = "clock", front = TileFace.Clock(TileFace.Clock.hms(s.accumulatedMs), listOf("Stopwatch"), Glyph.TIMER, tick))
    }

    // --- Pinning (T15-16, T15-40) ---------------------------------------------------------------------------------

    fun pinTimer(context: Context, t: ClockTimer) = request(context, TileRouting.TIMER_TILE_PREFIX + t.id, t.name.ifBlank { "Timer" })

    fun pinStopwatch(context: Context) = request(context, TileRouting.STOPWATCH_TILE_ID, "Stopwatch")

    /**
     * The shell's own pin request through phase 01's model: `SecondaryTiles.request`, which applies the provider's
     * checks itself (the tile-id pattern, the name, the size). The request waits for Start's pin band; a tile
     * already pinned needs nothing (its face is live).
     */
    private fun request(context: Context, tileId: String, name: String) {
        val app = context.applicationContext
        val pkg = app.packageName
        if (LiveTileStore.get(app).secondary(pkg, tileId) != null) {
            Diagnostics.add("clock", "pin $tileId -> already pinned")
            return
        }
        val record = LiveTileStore.SecondaryRecord(pkg, tileId, name.take(64), "", null, TileSize.MEDIUM, false)
        val result = when (val r = SecondaryTiles.request(app, record)) {
            is SecondaryTiles.RequestResult.Queued -> "queued (${r.waiting} waiting for Start)"
            SecondaryTiles.RequestResult.Full -> "refused: too many requests waiting"
            is SecondaryTiles.RequestResult.Invalid -> "refused: ${r.reason} ${r.detail}"
        }
        Diagnostics.add("clock", "pin $tileId \"$name\" -> $result")
    }
}

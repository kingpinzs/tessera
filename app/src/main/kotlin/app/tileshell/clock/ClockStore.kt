package app.tileshell.clock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.tileshell.cortana.reminders.ReminderScheduler
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Alarms, timers and the stopwatch (phase 15 build task 2): three JSON files — alarms.json, timers.json,
 * stopwatch.json — written with a temp file and a rename like LayoutStore's start_layout.json.
 *
 * They live in DEVICE-PROTECTED storage (T15-22): with a PIN set, credential storage stays locked after a reboot
 * until the first unlock, and an alarm that could only be read after the unlock would not ring before it. On an
 * AVD or phone the files are at /data/user_de/0/app.tileshell/files/.
 *
 * Every change re-arms the scheduler (Decisions "One exact-alarm scheduler": re-arm on every store change).
 */
class ClockStore private constructor(private val app: Context) {
    private val dir: File = app.createDeviceProtectedStorageContext().filesDir
    private val alarmsFile = File(dir, "alarms.json")
    private val timersFile = File(dir, "timers.json")
    private val stopwatchFile = File(dir, "stopwatch.json")

    private val _alarms = MutableStateFlow(loadAlarms())
    private val _timers = MutableStateFlow(loadTimers())
    private val _stopwatch = MutableStateFlow(loadStopwatch())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    val timers: StateFlow<List<ClockTimer>> = _timers.asStateFlow()
    val stopwatch: StateFlow<Stopwatch> = _stopwatch.asStateFlow()

    fun alarm(id: String): Alarm? = _alarms.value.firstOrNull { it.id == id }
    fun timer(id: String): ClockTimer? = _timers.value.firstOrNull { it.id == id }

    // --- Alarms -----------------------------------------------------------------------------------------------

    /** A new alarm, switched on: its "only once" date and its arming floor are fixed now. */
    fun newAlarm(hour: Int, minute: Int, name: String, days: Set<DayOfWeek>, sound: AlarmSound, snoozeMinutes: Int): Alarm {
        val now = System.currentTimeMillis()
        return Alarm(
            id = "a" + UUID.randomUUID().toString().take(8),
            hour = hour, minute = minute, name = name.ifBlank { Alarm.DEFAULT_NAME }, days = days, enabled = true,
            sound = sound, snoozeMinutes = snoozeMinutes,
            date = if (days.isEmpty()) ClockRules.oneShotDate(hour, minute, now, zone()) else null,
            snoozedUntilMs = null, lastHandledMs = now,
        )
    }

    /** Adds or replaces [alarm]; an edit re-fixes a one-shot date and clears a pending snooze (the time changed). */
    @Synchronized
    fun putAlarm(alarm: Alarm, why: String) {
        val now = System.currentTimeMillis()
        val fixed = if (alarm.enabled && !alarm.repeating) alarm.copy(date = ClockRules.oneShotDate(alarm.hour, alarm.minute, now, zone())) else alarm
        val existing = alarm(alarm.id)
        val next = if (existing == null) fixed.copy(lastHandledMs = now)
        else fixed.copy(snoozedUntilMs = null, lastHandledMs = now)
        _alarms.value = _alarms.value.filter { it.id != alarm.id } + next
        saveAlarms()
        changed(why)
    }

    @Synchronized
    fun setAlarmEnabled(id: String, on: Boolean) {
        val a = alarm(id) ?: return
        putAlarm(a.copy(enabled = on, snoozedUntilMs = null), if (on) "alarm $id on" else "alarm $id off")
    }

    @Synchronized
    fun deleteAlarms(ids: Collection<String>) {
        ids.forEach { ReminderScheduler.cancelClock(app, ReminderScheduler.CLOCK_ALARM, it) }
        _alarms.value = _alarms.value.filter { it.id !in ids }
        saveAlarms()
        changed("deleted ${ids.size} alarm(s)")
    }

    /**
     * The ring for [occurrenceMs] was answered or ran out: nothing re-rings it. A snooze sets the next ring.
     * [rearm] is false only when the scheduler itself records a missed occurrence mid-re-arm.
     */
    @Synchronized
    fun alarmHandled(id: String, occurrenceMs: Long, outcome: Outcome, snoozeMinutes: Int? = null, rearm: Boolean = true) {
        val a = alarm(id) ?: return
        val now = System.currentTimeMillis()
        val next = when (outcome) {
            Outcome.SNOOZED -> a.copy(snoozedUntilMs = now + (snoozeMinutes ?: a.snoozeMinutes) * 60_000L, lastHandledMs = maxOf(occurrenceMs, now))
            // A one-shot alarm is finished once it is dismissed or missed; a repeating one waits for its next day.
            Outcome.DISMISSED, Outcome.MISSED -> a.copy(
                snoozedUntilMs = null,
                lastHandledMs = maxOf(occurrenceMs, a.lastHandledMs ?: 0),
                enabled = a.repeating && a.enabled,
            )
            // Ringing now: the occurrence is taken, so a re-arm while it rings does not fire it again.
            Outcome.RINGING -> a.copy(snoozedUntilMs = null, lastHandledMs = maxOf(occurrenceMs, a.lastHandledMs ?: 0))
        }
        _alarms.value = _alarms.value.map { if (it.id == id) next else it }
        saveAlarms()
        if (rearm) changed("alarm $id ${outcome.name.lowercase()}")
    }

    enum class Outcome { RINGING, SNOOZED, DISMISSED, MISSED }

    // --- Timers -----------------------------------------------------------------------------------------------

    @Synchronized
    fun addTimer(name: String, lengthMs: Long, start: Boolean): ClockTimer? {
        if (lengthMs <= 0 || lengthMs > ClockTimer.MAX_LENGTH_MS) return null
        var t = ClockTimer("t" + UUID.randomUUID().toString().take(8), name, lengthMs, ClockTimer.State.IDLE, lengthMs, null, null, null)
        if (start) t = ClockRules.startTimer(t, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount())
        _timers.value = _timers.value + t
        saveTimers()
        changed("timer ${t.id} added")
        return t
    }

    @Synchronized
    fun updateTimer(id: String, why: String, change: (ClockTimer) -> ClockTimer) {
        val t = timer(id) ?: return
        _timers.value = _timers.value.map { if (it.id == id) change(t) else it }
        saveTimers()
        changed(why)
    }

    fun startTimer(id: String) = updateTimer(id, "timer $id start") { ClockRules.startTimer(it, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount()) }
    fun pauseTimer(id: String) = updateTimer(id, "timer $id pause") { ClockRules.pauseTimer(it, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount()) }
    fun resetTimer(id: String) = updateTimer(id, "timer $id reset") { ClockRules.resetTimer(it) }

    @Synchronized
    fun deleteTimers(ids: Collection<String>) {
        ids.forEach { ReminderScheduler.cancelClock(app, ReminderScheduler.CLOCK_TIMER, it) }
        _timers.value = _timers.value.filter { it.id !in ids }
        saveTimers()
        changed("deleted ${ids.size} timer(s)")
    }

    fun timerRemaining(t: ClockTimer): Long = ClockRules.timerRemaining(t, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount())

    // --- The stopwatch ------------------------------------------------------------------------------------------

    @Synchronized
    /**
     * Applies [change] at one instant and logs the elapsed time at that same instant, so the line names exactly what
     * was stored: a second clock read after the save could log a lap 1 ms later than the lap kept (E7).
     */
    fun updateStopwatch(why: String, change: (Stopwatch, Long, Long, Int) -> Stopwatch) {
        val real = SystemClock.elapsedRealtime()
        val uptime = SystemClock.uptimeMillis()
        val wall = System.currentTimeMillis()
        val boot = bootCount()
        _stopwatch.value = change(_stopwatch.value, real, wall, boot)
        saveStopwatch()
        Diagnostics.add("stopwatch", "$why elapsed=${ClockRules.stopwatchElapsed(_stopwatch.value, real, wall, boot)} uptime=$uptime")
    }

    fun startStopwatch() = updateStopwatch("start") { s, real, wall, boot -> ClockRules.startStopwatch(s, real, wall, boot) }
    fun stopStopwatch() = updateStopwatch("stop") { s, real, wall, boot -> ClockRules.stopStopwatch(s, real, wall, boot) }
    fun lapStopwatch() = updateStopwatch("lap") { s, real, wall, boot -> ClockRules.lap(s, real, wall, boot) }
    fun resetStopwatch() = updateStopwatch("reset") { _, _, _, _ -> Stopwatch.RESET }
    fun stopwatchElapsed(): Long = ClockRules.stopwatchElapsed(_stopwatch.value, SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount())

    // --- Plumbing -------------------------------------------------------------------------------------------------

    fun zone(): ZoneId = ZoneId.systemDefault()

    /** The boot a running timer or stopwatch started in; a reboot changes it, and the elapsed clock with it. */
    fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun changed(why: String) {
        Diagnostics.add("alarms", "store: $why")
        mirrorRingTheme(app)
        ReminderScheduler.rearmClock(app, "store change")
    }

    private fun loadAlarms(): List<Alarm> = read(alarmsFile)?.let { arr ->
        (0 until arr.length()).mapNotNull { runCatching { alarmFrom(arr.getJSONObject(it)) }.getOrNull() }
    }.orEmpty()

    private fun loadTimers(): List<ClockTimer> = read(timersFile)?.let { arr ->
        (0 until arr.length()).mapNotNull { runCatching { timerFrom(arr.getJSONObject(it)) }.getOrNull() }
    }.orEmpty()

    private fun loadStopwatch(): Stopwatch = runCatching {
        val o = JSONObject(stopwatchFile.readText())
        Stopwatch(
            running = o.getBoolean("running"),
            accumulatedMs = o.getLong("accumulatedMs"),
            startElapsedMs = o.optLongOrNull("startElapsedMs"),
            startWallMs = o.optLongOrNull("startWallMs"),
            bootCount = o.optIntOrNull("bootCount"),
            laps = o.getJSONArray("laps").let { a -> (0 until a.length()).map { a.getLong(it) } },
        )
    }.getOrDefault(Stopwatch.RESET)

    private fun read(file: File): JSONArray? = runCatching { if (file.exists()) JSONArray(file.readText()) else null }
        .onFailure { Diagnostics.add("alarms", "unreadable ${file.name}: $it") }.getOrNull()

    private fun saveAlarms() = write(alarmsFile, JSONArray().apply { _alarms.value.forEach { put(alarmJson(it)) } }.toString())
    private fun saveTimers() = write(timersFile, JSONArray().apply { _timers.value.forEach { put(timerJson(it)) } }.toString())
    private fun saveStopwatch() = _stopwatch.value.let { s ->
        write(stopwatchFile, JSONObject()
            .put("running", s.running).put("accumulatedMs", s.accumulatedMs)
            .put("startElapsedMs", s.startElapsedMs ?: JSONObject.NULL).put("startWallMs", s.startWallMs ?: JSONObject.NULL)
            .put("bootCount", s.bootCount ?: JSONObject.NULL)
            .put("laps", JSONArray().apply { s.laps.forEach { put(it) } }).toString())
    }

    private fun write(file: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) Diagnostics.add("alarms", "could not write ${file.name}")
    }

    private fun alarmJson(a: Alarm) = JSONObject()
        .put("id", a.id).put("hour", a.hour).put("minute", a.minute).put("name", a.name)
        .put("days", JSONArray().apply { a.days.sorted().forEach { put(it.name) } })
        .put("enabled", a.enabled)
        .put("sound", JSONObject().put("kind", a.sound.kind.name).put("uri", a.sound.uri ?: JSONObject.NULL).put("title", a.sound.title ?: JSONObject.NULL))
        .put("snoozeMinutes", a.snoozeMinutes)
        .put("date", a.date?.toString() ?: JSONObject.NULL)
        .put("snoozedUntilMs", a.snoozedUntilMs ?: JSONObject.NULL)
        .put("lastHandledMs", a.lastHandledMs ?: JSONObject.NULL)

    private fun alarmFrom(o: JSONObject): Alarm {
        val s = o.getJSONObject("sound")
        return Alarm(
            id = o.getString("id"), hour = o.getInt("hour"), minute = o.getInt("minute"), name = o.getString("name"),
            days = o.getJSONArray("days").let { a -> (0 until a.length()).map { DayOfWeek.valueOf(a.getString(it)) }.toSet() },
            enabled = o.getBoolean("enabled"),
            sound = AlarmSound(AlarmSound.Kind.valueOf(s.getString("kind")), s.optStringOrNull("uri"), s.optStringOrNull("title")),
            snoozeMinutes = o.getInt("snoozeMinutes"),
            date = o.optStringOrNull("date")?.let { LocalDate.parse(it) },
            snoozedUntilMs = o.optLongOrNull("snoozedUntilMs"),
            lastHandledMs = o.optLongOrNull("lastHandledMs"),
        )
    }

    private fun timerJson(t: ClockTimer) = JSONObject()
        .put("id", t.id).put("name", t.name).put("lengthMs", t.lengthMs).put("state", t.state.name).put("remainingMs", t.remainingMs)
        .put("deadlineElapsedMs", t.deadlineElapsedMs ?: JSONObject.NULL).put("deadlineWallMs", t.deadlineWallMs ?: JSONObject.NULL)
        .put("bootCount", t.bootCount ?: JSONObject.NULL)

    private fun timerFrom(o: JSONObject) = ClockTimer(
        id = o.getString("id"), name = o.getString("name"), lengthMs = o.getLong("lengthMs"),
        state = ClockTimer.State.valueOf(o.getString("state")), remainingMs = o.getLong("remainingMs"),
        deadlineElapsedMs = o.optLongOrNull("deadlineElapsedMs"), deadlineWallMs = o.optLongOrNull("deadlineWallMs"),
        bootCount = o.optIntOrNull("bootCount"),
    )

    companion object {
        @Volatile private var instance: ClockStore? = null

        fun get(context: Context): ClockStore = instance ?: synchronized(this) {
            instance ?: ClockStore(context.applicationContext).also { instance = it }
        }
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)

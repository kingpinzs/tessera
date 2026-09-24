package app.tileshell.clock

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import app.tileshell.brand.AlarmSounds
import app.tileshell.cortana.reminders.ReminderScheduler
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * The ring (phase 15 Decisions "Ringing", T15-14, T15-23, T15-41, T15-54): a foreground service started only from
 * an alarm's broadcast, which plays the sound on the ALARM stream (so a silent ringer and Do not disturb do not
 * silence it), vibrates, holds a wake lock, and shows W10M's toast — over the keyguard through a full-screen
 * intent that starts [RingActivity], over the app in use as an overlay window ([RingOverlay]). An alarm nobody
 * answers stops after [ClockRules.RING_TIMEOUT_MS] and is missed.
 *
 * It runs in the main process and is direct-boot aware, so an alarm rings before the first unlock after a
 * reboot (T15-22).
 */
class RingService : Service() {

    /** What is ringing: one timer, or the alarms due in one wall-clock minute (T15-41). */
    data class Ring(
        val timer: Boolean,
        val ids: List<String>,
        val occurrences: Map<String, Long>,
        val names: List<String>,
        val timeText: String,
        val snoozeMinutes: Int,
        val surface: Surface,
    ) {
        val title: String get() = if (timer) "Timer finished" else "Alarm"
        val body: String get() = (names + timeText).filter { it.isNotBlank() }.joinToString(" · ")
        val logId: String get() = ids.joinToString(",")
    }

    /** Where the toast is: over the keyguard (the activity), over the app in use (the overlay), or not drawn by the shell. */
    enum class Surface { LOCKED, OVERLAY, NOTIFICATION }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: RingOverlay? = null
    private var notificationId = ClockNotifications.RING_ID
    private var screenReceiver: BroadcastReceiver? = null
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private val timeout = Runnable { end(Outcome.TIMEOUT) }

    private enum class Outcome(val log: String) { DISMISS("dismiss"), SNOOZE("snooze"), TIMEOUT("timeout"), SUPERSEDED("superseded") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        live = this
    }

    override fun onDestroy() {
        stopSound()
        overlay?.hide()
        overlay = null
        unregisterScreen()
        handler.removeCallbacks(timeout)
        wakeLock?.let { if (it.isHeld) it.release() }
        if (live === this) live = null
        _state.value = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(intent)
            ACTION_SNOOZE -> snooze(intent.getIntExtra(EXTRA_MINUTES, -1).takeIf { it > 0 })
            ACTION_DISMISS -> end(Outcome.DISMISS)
            ACTION_UNSNOOZE -> intent.getStringExtra(ReminderScheduler.EXTRA_CLOCK_ID)?.let { unsnooze(it) }
        }
        if (_state.value == null) {
            // Nothing is ringing (a late action from a notification, a snooze cancelled): nothing to keep alive.
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    // --- Ringing --------------------------------------------------------------------------------------------------

    private fun ring(intent: Intent) {
        val store = ClockStore.get(this)
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_CLOCK_KIND) ?: return
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_CLOCK_ID) ?: return
        val at = intent.getLongExtra(ReminderScheduler.EXTRA_CLOCK_AT, 0L)
        val timer = kind == ReminderScheduler.CLOCK_TIMER
        val late = if (timer) SystemClock.elapsedRealtime() - at else System.currentTimeMillis() - at
        Diagnostics.add("alarms", "fired $id kind=$kind late=$late")

        val current = _state.value
        val next: Ring = if (timer) {
            val t = store.timer(id) ?: return
            if (intent.getBooleanExtra(ReminderScheduler.EXTRA_CLOCK_ENDED_OFF, false)) {
                Diagnostics.add("alarms", "timer $id ended while the phone was off")
            }
            // A finished timer goes back to its full length, so a re-arm never rings it twice.
            store.updateTimer(id, "timer $id finished") { ClockRules.resetTimer(it) }
            current?.let { superseded(it) }
            Ring(true, listOf(id), mapOf(id to at), listOf(t.name.ifBlank { "Timer" }), length(t.lengthMs), 0, Surface.NOTIFICATION)
        } else {
            val a = store.alarm(id) ?: return
            store.alarmHandled(id, at, ClockStore.Outcome.RINGING)
            val time = ClockNotifications.time(this, at)
            if (current != null && !current.timer && current.occurrences.values.any { ClockRules.sameMinute(it, at, store.zone()) }) {
                // Due in the same minute: one toast lists both, and Snooze / Dismiss act on both (T15-41).
                current.copy(ids = current.ids + id, occurrences = current.occurrences + (id to at), names = current.names + a.name)
            } else {
                current?.let { superseded(it) }
                Ring(false, listOf(id), mapOf(id to at), listOf(a.name), time, a.snoozeMinutes, Surface.NOTIFICATION)
            }
        }
        mirrorRingTheme(this)
        val joined = current != null && next.ids.containsAll(current.ids) && next.ids.size > current.ids.size
        if (!joined) startSound(next, store)
        present(next)
        acquireWakeLock()
        registerScreen()
        if (!joined) {
            handler.removeCallbacks(timeout)
            handler.postDelayed(timeout, ClockRules.RING_TIMEOUT_MS)
        }
    }

    /**
     * Chooses the notification form and the surface from the phone's state now (T15-23) and shows them. In use
     * with the overlay: the quiet notification and the overlay toast, so no heads-up covers it. Otherwise: the
     * full-screen form — the locked toast when the keyguard shows or the screen is off, Android's own heads-up
     * (in use) or lock-screen notification (locked) when full-screen intents are not allowed.
     */
    private fun present(ring: Ring) {
        val km = getSystemService(KeyguardManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        val locked = km.isKeyguardLocked || !pm.isInteractive
        val overlayOk = Settings.canDrawOverlays(this)
        val fsiOk = nm.canUseFullScreenIntent()
        Diagnostics.add("alarms", "full-screen intent: ${if (fsiOk) "allow" else "deny"}")
        val surface = when {
            !locked && overlayOk -> Surface.OVERLAY
            locked && fsiOk -> Surface.LOCKED
            else -> Surface.NOTIFICATION
        }
        val shown = ring.copy(surface = surface)
        _state.value = shown
        val fullScreen = surface != Surface.OVERLAY
        // A new id each time the form changes: a full-screen intent launches when a notification is POSTED.
        val previous = notificationId
        notificationId = if (previous == ClockNotifications.RING_ID) ClockNotifications.RING_ID + 2 else ClockNotifications.RING_ID
        startForeground(notificationId, ClockNotifications.ring(this, shown, fullScreen), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        nm.cancel(previous)
        Diagnostics.add("alarms", "notification ${shown.logId}: ${if (fullScreen) "fullscreen" else "quiet"}")
        if (surface == Surface.OVERLAY) {
            (overlay ?: RingOverlay(this).also { overlay = it }).show()
        } else {
            overlay?.hide()
            overlay = null
        }
        val form = when (surface) {
            Surface.OVERLAY -> "toast-overlay"
            Surface.LOCKED -> "toast-locked"
            Surface.NOTIFICATION -> if (locked) "lockscreen-notification" else "heads-up"
        }
        Diagnostics.add("alarms", "surface: $form ${shown.logId}")
    }

    /** While ringing: the screen going off moves the toast onto the keyguard; unlocking moves it over the app. */
    private fun registerScreen() {
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val ring = _state.value ?: return
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> if (ring.surface == Surface.OVERLAY) present(ring)
                    Intent.ACTION_USER_PRESENT -> if (ring.surface != Surface.OVERLAY) present(ring)
                }
            }
        }
        registerReceiver(r, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }, RECEIVER_NOT_EXPORTED)
        screenReceiver = r
    }

    private fun unregisterScreen() {
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
    }

    // --- Sound and vibration --------------------------------------------------------------------------------------

    private fun startSound(ring: Ring, store: ClockStore) {
        stopSound()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val sound = if (ring.timer) AlarmSound.DEFAULT else store.alarm(ring.ids.first())?.sound ?: AlarmSound.DEFAULT
        val source = source(ring, sound)
        if (source != null) {
            val audio = getSystemService(AudioManager::class.java)
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build().also { audio.requestAudioFocus(it) }
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@RingService, source)
                    isLooping = true
                    prepare()
                    start()
                }
            }.onFailure { Diagnostics.add("alarms", "ring ${ring.logId} could not play $source: $it") }.getOrNull()
            applyCallLevel()
            val listener = AudioManager.OnModeChangedListener { applyCallLevel() }
            audio.addOnModeChangedListener(mainExecutor, listener)
            modeListener = listener
        }
        val vibrator = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    /** The sound's URI, or null for Vibrate only; a file that cannot be read rings the default and says so. */
    private fun source(ring: Ring, sound: AlarmSound): Uri? {
        val id = ring.logId
        fun default(): Uri = Uri.fromFile(AlarmSounds.file(this, AlarmSounds.default))
        return when (sound.kind) {
            AlarmSound.Kind.VIBRATE -> null.also { Diagnostics.add("alarms", "ring $id sound=vibrate") }
            AlarmSound.Kind.DEFAULT -> default().also { Diagnostics.add("alarms", "ring $id sound=default") }
            AlarmSound.Kind.TONE, AlarmSound.Kind.MUSIC -> {
                val uri = sound.uri ?: return default().also { Diagnostics.add("alarms", "ring $id sound=default") }
                AlarmSounds.byUri(uri)?.let { brand ->
                    return Uri.fromFile(AlarmSounds.file(this, brand)).also { Diagnostics.add("alarms", "ring $id sound=$uri") }
                }
                if (sound.kind == AlarmSound.Kind.MUSIC && !getSystemService(UserManager::class.java).isUserUnlocked) {
                    // Before the first unlock the music on shared storage cannot be read (T15-22).
                    Diagnostics.add("alarms", "sound $uri locked -> default")
                    return default()
                }
                val readable = runCatching { contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
                if (readable) {
                    Diagnostics.add("alarms", "ring $id sound=$uri")
                    Uri.parse(uri)
                } else {
                    Diagnostics.add("alarms", "sound $uri missing -> default")
                    default()
                }
            }
        }
    }

    /** During a call the ring plays at one eighth of its level, vibration unchanged (approximation, H7; T15-36). */
    private fun applyCallLevel() {
        val mode = getSystemService(AudioManager::class.java).mode
        val inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        val level = if (inCall) 0.125f else 1f
        player?.setVolume(level, level)
        Diagnostics.add("alarms", "ring level ${if (inCall) "1/8 (in call, mode $mode)" else "full"}")
    }

    private fun stopSound() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        val audio = getSystemService(AudioManager::class.java)
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        modeListener?.let { audio.removeOnModeChangedListener(it) }
        modeListener = null
        getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tessera:ring")
            .apply { acquire(ClockRules.RING_TIMEOUT_MS + 60_000) }
    }

    // --- Ending ---------------------------------------------------------------------------------------------------

    private fun snooze(minutes: Int?) {
        val ring = _state.value ?: return
        if (ring.timer) return end(Outcome.DISMISS)
        val store = ClockStore.get(this)
        ring.ids.forEach { id ->
            store.alarmHandled(id, ring.occurrences[id] ?: 0L, ClockStore.Outcome.SNOOZED, minutes ?: ring.snoozeMinutes)
            store.alarm(id)?.let { ClockNotifications.snoozed(this, it) }
        }
        finish(ring, Outcome.SNOOZE)
    }

    private fun end(outcome: Outcome) {
        val ring = _state.value ?: return
        val store = ClockStore.get(this)
        if (!ring.timer) {
            ring.ids.forEach { id ->
                val occurrence = ring.occurrences[id] ?: 0L
                when (outcome) {
                    Outcome.DISMISS -> store.alarmHandled(id, occurrence, ClockStore.Outcome.DISMISSED)
                    else -> {
                        store.alarmHandled(id, occurrence, ClockStore.Outcome.MISSED)
                        store.alarm(id)?.let { ClockNotifications.missed(this, it, occurrence) }
                    }
                }
                ClockNotifications.clearSnoozed(this, id)
            }
        }
        finish(ring, outcome)
    }

    /** A later alarm (or a timer) took the toast: the one ringing is ended and counts as missed (Decisions "Ringing"). */
    private fun superseded(ring: Ring) {
        val store = ClockStore.get(this)
        ring.ids.forEach { id ->
            Diagnostics.add("alarms", "ring ended $id: superseded")
            if (!ring.timer) {
                val occurrence = ring.occurrences[id] ?: 0L
                store.alarmHandled(id, occurrence, ClockStore.Outcome.MISSED)
                store.alarm(id)?.let { ClockNotifications.missed(this, it, occurrence) }
                Diagnostics.add("alarms", "ring ended $id: missed")
            }
        }
        stopSound()
    }

    private fun unsnooze(id: String) {
        val store = ClockStore.get(this)
        val a = store.alarm(id) ?: return
        store.alarmHandled(id, a.snoozedUntilMs ?: System.currentTimeMillis(), ClockStore.Outcome.DISMISSED)
        ClockNotifications.clearSnoozed(this, id)
        Diagnostics.add("alarms", "snooze cancelled $id")
    }

    private fun finish(ring: Ring, outcome: Outcome) {
        ring.ids.forEach { id ->
            Diagnostics.add("alarms", "ring ended $id: ${outcome.log}")
            if (outcome == Outcome.TIMEOUT && !ring.timer) Diagnostics.add("alarms", "ring ended $id: missed")
        }
        handler.removeCallbacks(timeout)
        stopSound()
        overlay?.hide()
        overlay = null
        unregisterScreen()
        _state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf()
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        writer.println("ringing=${_state.value}")
        Diagnostics.dump(writer)
    }

    companion object {
        const val ACTION_RING = "app.tileshell.clock.RING"
        const val ACTION_SNOOZE = "app.tileshell.clock.SNOOZE"
        const val ACTION_DISMISS = "app.tileshell.clock.DISMISS"
        const val ACTION_UNSNOOZE = "app.tileshell.clock.UNSNOOZE"
        const val EXTRA_MINUTES = "minutes"

        private val _state = MutableStateFlow<Ring?>(null)
        /** What is ringing now, for the toast wherever it is drawn; null when nothing rings. */
        val state: StateFlow<Ring?> = _state.asStateFlow()

        @Volatile private var live: RingService? = null

        /**
         * The alarm's broadcast (ReminderReceiver). The item is checked here, before the foreground service
         * starts: a stale broadcast — an alarm switched off, deleted or already handled — rings nothing.
         */
        fun fire(context: Context, intent: Intent) {
            val store = ClockStore.get(context)
            val kind = intent.getStringExtra(ReminderScheduler.EXTRA_CLOCK_KIND)
            val id = intent.getStringExtra(ReminderScheduler.EXTRA_CLOCK_ID) ?: return
            val at = intent.getLongExtra(ReminderScheduler.EXTRA_CLOCK_AT, 0L)
            val stale = when (kind) {
                ReminderScheduler.CLOCK_ALARM -> store.alarm(id).let { it == null || !it.enabled || (it.lastHandledMs ?: 0L) >= at && it.snoozedUntilMs != at }
                ReminderScheduler.CLOCK_TIMER -> store.timer(id)?.state != ClockTimer.State.RUNNING
                else -> true
            }
            if (stale) {
                Diagnostics.add("alarms", "fire $kind $id at=$at ignored: no longer due")
                return
            }
            context.startForegroundService(Intent(context, RingService::class.java).setAction(ACTION_RING).putExtras(intent))
        }

        /** A button on the toast: sent straight to the running service. */
        fun act(context: Context, action: String, minutes: Int? = null) {
            context.startService(Intent(context, RingService::class.java).setAction(action).apply { minutes?.let { putExtra(EXTRA_MINUTES, it) } })
        }

        fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(), Intent(context, RingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun length(ms: Long): String {
            val s = ms / 1000
            return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        }
    }
}

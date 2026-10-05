package app.tileshell.camera

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.diag.RemoteRings
import app.tileshell.ui.setShellAppContent
import kotlinx.coroutines.MainScope
import java.util.concurrent.Executors

/**
 * Camera (phase 17): W10M's Camera, an app inside the shell APK — a launcher activity with its own task, answering
 * `STILL_IMAGE_CAMERA` and `VIDEO_CAMERA`, so the CAMERA slot (the bottom-row tile, Tess's "take a photo") can point at
 * it. `singleTask`, in its own process `:camera` (capture must not be able to take Start down). Another app's capture
 * request never reaches this activity: `IMAGE_CAPTURE` / `VIDEO_CAPTURE` are [CaptureActivity]'s (r3 D6).
 *
 * Every capture goes to `DCIM/Camera/` through [CameraSaver] (the one MediaStore write layer). A recording or a
 * panorama sweep in progress is ended when the screen goes off, a call comes in or the activity is left — there is no
 * camera foreground service (T17-19).
 */
class CameraActivity : ComponentActivity() {
    private val nav = CameraNav()
    private var ring: android.content.ServiceConnection? = null
    private lateinit var engine: CameraEngine
    private lateinit var saver: CameraSaver
    private val state = ViewfinderState()
    private val scope = MainScope()
    private val saving = Executors.newSingleThreadExecutor()
    private val interruptions = CameraInterruptions(this) { interrupt(it) }
    private val locations = CameraLocation(this)

    private val sink = object : CaptureSink {
        override val keepsLocation = true

        override fun photo(shot: PhotoShot, clip: LivingClip.Encoded?, panorama: Boolean) {
            state.busySaving = true
            saving.execute {
                val thumb = thumbnailOf(shot.file, shot.rotationDegrees)
                val uri = saver.savePhoto(shot, clip, if (panorama) "PANO" else "IMG")
                runOnUiThread {
                    state.busySaving = false
                    if (uri != null) state.lastThumb = thumb else state.say(scope, "Couldn't save")
                }
            }
        }

        override fun video(take: VideoTake) {
            state.busySaving = true
            if (take.error == "storage full") state.say(scope, "Storage full")
            saving.execute {
                val uri = saver.saveVideo(take)
                runOnUiThread {
                    state.busySaving = false
                    if (uri == null && take.error != "storage full") state.say(scope, "Couldn't save")
                }
            }
        }

        override fun failed(why: String) {
            Diagnostics.add("camera", "capture failed: $why")
            state.say(scope, why)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("camera", "CameraActivity created")
        hideSystemBars()
        ring = RemoteRings.hold(this, CameraDumpService::class.java)
        CameraDumpService.linger(this)
        saver = CameraSaver(this)
        CameraProcess.startOnce(this, saver)
        engine = CameraEngine(this)
        nav.open(intent)
        engine.openMode(nav.asked.id)
        setShellAppContent(statusBar = false, onBack = { if (!state.back()) finish() }) {
            CameraApp(engine, state, sink, onRoll = ::openRoll)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nav.open(intent)
        state.back()
        engine.openMode(nav.asked.id)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        engine.start()
        interruptions.start()
        locations.start { engine.location = it }
    }

    override fun onPause() {
        CameraDumpService.linger(this)
        val interactive = getSystemService(PowerManager::class.java)?.isInteractive != false
        interrupt(if (interactive) "left the camera" else "screen off")
        interruptions.stop()
        locations.stop()
        engine.location = null
        super.onPause()
    }

    /** Ends what cannot go on unattended: a recording is stopped and finalised, a sweep or a countdown is dropped. */
    private fun interrupt(reason: String) {
        if (engine.isRecording) engine.stopVideo(reason)
        state.panorama?.cancel(reason)
        state.cancelCountdown()
    }

    /**
     * The roll opens Photos on the last capture (r11/camera.md 1.3.9). Until Photos' viewer takes a picture to open,
     * this starts the Photos app itself.
     */
    private fun openRoll() {
        Diagnostics.add("camera", "roll -> Photos")
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).setClassName(packageName, "app.tileshell.photos.PhotosActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Diagnostics.add("camera", "roll: Photos did not open (${it.javaClass.simpleName})") }
    }

    override fun onDestroy() {
        engine.release()
        RemoteRings.release(this, ring)
        super.onDestroy()
    }
}

/** What runs once per start of the `:camera` process, whichever of its activities comes first. */
object CameraProcess {
    private var started = false

    @Synchronized
    fun startOnce(context: Context, saver: CameraSaver) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread {
            // Build task 6 clause 7: the shell's own pending rows a killed process left are removed; so is its capture cache.
            saver.cleanUpPending()
            java.io.File(app.cacheDir, "camera").listFiles()?.forEach { it.delete() }
        }.start()
    }
}

/**
 * The events that end a recording or a sweep (build task 6 clause 3; Edge cases): the screen going off, and a call —
 * read from the audio mode, which needs no permission and no microphone.
 */
class CameraInterruptions(private val activity: ComponentActivity, private val onInterrupt: (String) -> Unit) {
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onInterrupt("screen off")
    }
    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        if (mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) onInterrupt("a call")
    }
    private var on = false

    fun start() {
        if (on) return
        on = true
        activity.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        activity.getSystemService(AudioManager::class.java)?.addOnModeChangedListener(activity.mainExecutor, modeListener)
    }

    fun stop() {
        if (!on) return
        on = false
        runCatching { activity.unregisterReceiver(screenOff) }
        activity.getSystemService(AudioManager::class.java)?.removeOnModeChangedListener(modeListener)
    }
}

/**
 * The fix a capture carries in its EXIF (build task 6 clause 1): only while the shell holds a location permission, and
 * only while the viewfinder is in front. No permission is asked for here — Location is the Setup checklist's row.
 */
class CameraLocation(private val activity: ComponentActivity) {
    private var listener: LocationListener? = null

    fun start(onFix: (Location) -> Unit) {
        val fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val manager = activity.getSystemService(LocationManager::class.java) ?: return
        val providers = (if (fine) listOf(LocationManager.GPS_PROVIDER) else emptyList()) + LocationManager.NETWORK_PROVIDER
        val usable = providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        try {
            usable.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let(onFix)
            val l = LocationListener { onFix(it) }
            usable.forEach { manager.requestLocationUpdates(it, 2000L, 0f, l, activity.mainLooper) }
            listener = l
        } catch (e: SecurityException) {
            listener = null
        }
    }

    fun stop() {
        val l = listener ?: return
        listener = null
        runCatching { activity.getSystemService(LocationManager::class.java)?.removeUpdates(l) }
    }
}

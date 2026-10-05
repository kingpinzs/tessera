package app.tileshell.camera

import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tileshell.diag.Diagnostics

/**
 * The modes an intent or an App Shortcut can name (build task 15): the `mode` extra's ids. A mode the phone's camera
 * cannot do opens Photo (the dynamic shortcuts of such a mode are never published — `CameraGates.dynamicShortcuts`).
 */
enum class CameraMode(val id: String, val title: String) {
    PHOTO("photo", "Photo"),
    VIDEO("video", "Video"),
    PANORAMA("panorama", "Panorama"),
    SLOWMO("slowmo", "Slow motion");

    companion object {
        fun byId(id: String?): CameraMode? = entries.firstOrNull { it.id == id }
    }
}

/** Camera's navigation, held above the composition so an intent can route while the activity is already running. */
class CameraNav {
    /** The mode the last intent asked for; the viewfinder falls back to Photo when the camera cannot do it. */
    var asked by mutableStateOf(CameraMode.PHOTO)
        private set
    var routeToken by mutableIntStateOf(0)
        private set

    fun open(intent: Intent?) {
        val extra = runCatching { intent?.getStringExtra(EXTRA_MODE) }.getOrNull()
        asked = CameraMode.byId(extra)
            ?: if (intent?.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA) CameraMode.VIDEO else CameraMode.PHOTO
        routeToken++
        Diagnostics.add("camera", "open mode=${asked.id}")
    }

    companion object {
        const val EXTRA_MODE = "mode"
    }
}

/** The viewfinder and its pages inside the frame [CameraActivity] sets (no status bar, the nav bar drawn — Y7). */
@Composable
fun CameraApp(engine: CameraEngine, state: ViewfinderState, sink: CaptureSink, onRoll: () -> Unit) {
    val context = engine.context
    val caps = engine.caps
    val env = engine.env
    // Build task 15 (C-9): the dynamic shortcuts follow the gates of the camera that is open now.
    LaunchedEffect(caps, env) { if (caps != null) CameraShortcuts.publish(context, caps, env) }
    Viewfinder(engine, state, sink, captureKind = null, onRoll = onRoll)
}

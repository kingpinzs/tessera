package app.tileshell.camera

import android.content.Intent
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.tileshell.diag.Diagnostics

/**
 * The capture modes (Q2 C). A mode the phone's camera cannot do is not shown and logs why; the App Shortcuts' `mode`
 * extra names one (build task 15) and a mode that is not available opens Photo.
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
fun CameraApp(nav: CameraNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("camera_root"))
}

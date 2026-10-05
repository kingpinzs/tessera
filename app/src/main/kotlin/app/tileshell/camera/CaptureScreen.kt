package app.tileshell.camera

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** What [CaptureActivity] was asked for. The request is decided by the output guard before anything is shown. */
class CaptureNav {
    var request: Intent? = null
        private set

    fun open(intent: Intent?) {
        request = intent
    }
}

@Composable
fun CaptureScreen(nav: CaptureNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("capture_root"))
}

package app.tileshell.photos

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** What [ViewerActivity] was asked to show: one `content://` picture, or nothing it can show. */
class ViewerNav {
    var uri by mutableStateOf<Uri?>(null)
        private set

    fun open(intent: Intent?) {
        uri = intent?.data?.takeIf { it.scheme == "content" }
    }
}

@Composable
fun ViewerScreen(nav: ViewerNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("viewer_root"))
}

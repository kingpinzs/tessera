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

/** What [EditActivity] was started for: the MediaStore item to edit (an image) or trim (a video). */
class EditNav {
    var uri by mutableStateOf<Uri?>(null)
        private set
    var trim by mutableStateOf(false)
        private set

    fun open(intent: Intent?) {
        uri = intent?.data?.takeIf { it.scheme == "content" && it.authority == "media" }
        trim = intent?.getBooleanExtra(EXTRA_TRIM, false) == true
    }

    companion object {
        const val EXTRA_TRIM = "trim"
    }
}

@Composable
fun EditScreen(nav: EditNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("edit_root"))
}

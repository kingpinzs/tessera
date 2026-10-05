package app.tileshell.video

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

/** What [PlayerActivity] was asked to play. */
class PlayerNav {
    var source by mutableStateOf<Uri?>(null)
        private set

    fun open(intent: Intent?) {
        source = intent?.data
    }
}

@Composable
fun PlayerScreen(nav: PlayerNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("player_root"))
}

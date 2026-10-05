package app.tileshell.video

import android.content.Intent
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

/** The pane's pages (T17-16; tags `hub_pane:<id>`). The App Shortcuts' `page` extra names one (build task 15). */
enum class HubPage(val id: String, val title: String) {
    MY_VIDEOS("myvideos", "My videos"),
    BROWSE("browse", "Browse"),
    MEDIA_SERVER("mediaserver", "Media server"),
    SETTINGS("settings", "Settings");

    companion object {
        fun byId(id: String?): HubPage? = entries.firstOrNull { it.id == id }
    }
}

/** The hub's navigation, held above the composition so an intent can route while the activity is already running. */
class VideoNav {
    /** The page the last intent asked for; Media server falls back to My videos while no server is set up. */
    var asked by mutableStateOf(HubPage.MY_VIDEOS)
        private set
    var routeToken by mutableIntStateOf(0)
        private set

    fun open(intent: Intent?) {
        val extra = runCatching { intent?.getStringExtra(EXTRA_PAGE) }.getOrNull()
        asked = HubPage.byId(extra) ?: HubPage.MY_VIDEOS
        routeToken++
        Diagnostics.add("video", "open page=${asked.id}")
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

/** The hub's pages inside the frame [VideoActivity] sets (phase 01's status bar and the nav bar drawn — Y7). */
@Composable
fun VideoApp(nav: VideoNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("hub_root"))
}

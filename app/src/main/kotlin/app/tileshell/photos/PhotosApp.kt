package app.tileshell.photos

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

/** Photos' two pivots (Y1): the App Shortcuts' `page` extra names one (build task 15). */
enum class PhotosPivot(val id: String, val title: String) {
    COLLECTION("collection", "Collection"),
    ALBUMS("albums", "Albums");

    companion object {
        fun byId(id: String?): PhotosPivot? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Photos' navigation, held above the composition so an intent can route while the activity is already running.
 * [routeToken] changes on every intent, so the same shortcut twice is two opens.
 */
class PhotosNav {
    var pivot by mutableStateOf(PhotosPivot.COLLECTION)
    var routeToken by mutableIntStateOf(0)
        private set

    fun open(intent: Intent?) {
        // The extra is another app's text when it is not a shortcut's: anything unknown opens the collection.
        val asked = runCatching { intent?.getStringExtra(EXTRA_PAGE) }.getOrNull()
        pivot = PhotosPivot.byId(asked) ?: PhotosPivot.COLLECTION
        routeToken++
        Diagnostics.add("photosapp", "open page=${pivot.id}")
    }

    companion object {
        /** The same key SettingsActivity.EXTRA_PAGE uses (phase 11's shortcut form). */
        const val EXTRA_PAGE = "page"
    }
}

/** The app's pages inside the frame [PhotosActivity] sets (no status bar, the nav bar drawn — Y7). */
@Composable
fun PhotosApp(nav: PhotosNav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().testTag("photos_root"))
}

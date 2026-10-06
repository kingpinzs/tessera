package app.tileshell.photos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.ShellApp
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.PhotosFeed
import app.tileshell.ui.components.dismissOverlay

/** Photos' two pivots (Y1): the App Shortcuts' `page` extra names one (build task 15). */
enum class PhotosPivot(val id: String, val title: String) {
    COLLECTION("collection", "Collection"),
    ALBUMS("albums", "Albums");

    companion object {
        fun byId(id: String?): PhotosPivot? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What the viewer was opened on: the item [startId] of the collection ([albumId] null) or of one album, expanding from
 * its tile's place [origin] (Y6); [slideshow] starts the slideshow at once (the library bar's button).
 */
data class ViewerRequest(val startId: Long, val albumId: Long?, val origin: Rect?, val slideshow: Boolean, val token: Int)

/**
 * Photos' navigation, held above the composition so an intent can route while the activity is already running.
 * [routeToken] changes on every intent, so the same shortcut twice is two opens.
 */
class PhotosNav {
    var pivot by mutableStateOf(PhotosPivot.COLLECTION)
    var routeToken by mutableIntStateOf(0)
        private set

    /** The album page that is open over the Albums pivot (a bucket id), or null. */
    var album by mutableStateOf<Long?>(null)

    /** The viewer over the library, or null. */
    var viewer by mutableStateOf<ViewerRequest?>(null)
        private set

    /** The item whose Edit sheet is up over the library (a held video tile: Trim), or null. */
    var sheetFor by mutableStateOf<MediaEntry?>(null)

    /** Where each laid-out tile is, in the window: what the viewer expands from and shrinks back to (Y6). Not state. */
    val tileBounds = HashMap<Long, Rect>()

    private var viewerToken = 0

    fun open(intent: Intent?) {
        // The extra is another app's text when it is not a shortcut's: anything unknown opens the collection.
        val asked = runCatching { intent?.getStringExtra(EXTRA_PAGE) }.getOrNull()
        pivot = PhotosPivot.byId(asked) ?: PhotosPivot.COLLECTION
        // A shortcut or the tile opens a pivot itself, not what was left over it.
        album = null
        viewer = null
        sheetFor = null
        routeToken++
        Diagnostics.add("photosapp", "open page=${pivot.id}")
    }

    fun openViewer(item: MediaEntry, albumId: Long?, slideshow: Boolean = false) {
        viewer = ViewerRequest(item.id, albumId, tileBounds[item.id], slideshow, ++viewerToken)
    }

    fun closeViewer() = dismissOverlay { viewer = null }

    companion object {
        /** The same key SettingsActivity.EXTRA_PAGE uses (phase 11's shortcut form). */
        const val EXTRA_PAGE = "page"
    }
}

/** The permissions the in-place grant asks for: the Photos row's two and the Videos row's one, one system dialog. */
private val MEDIA_PERMISSIONS = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    Manifest.permission.READ_MEDIA_VIDEO,
)

/** The app's pages inside the frame [PhotosActivity] sets (no status bar, the nav bar drawn — Y7). */
@Composable
fun PhotosApp(nav: PhotosNav, activity: ComponentActivity) {
    val library by PhotoStore.library.collectAsState()
    LaunchedEffect(Unit) { PhotoStore.start(activity) }

    // A grant changed while Photos was away (Settings, the checklist): the page follows it on return.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val access = PhotosFeed.access(activity)
        val video = activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        if (library.loaded && (access != library.access || video != library.videoAccess)) PhotoStore.refresh(activity, "access changed while away")
    }

    // Phase 10 task 10's pattern: Android's real dialog from the empty state; when Android will not ask again, the
    // app's own settings page is the only place left where the permission can be turned on.
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val access = PhotosFeed.access(activity)
        val video = activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        Diagnostics.add("photosapp", "permission request: access=$access videos=${if (video) "granted" else "denied"}")
        if (access != PhotosFeed.Access.DENIED || video) {
            // r3 D6: the tile's feeds restart after a grant made in place — from PhotosActivity only (main process).
            (activity.applicationContext as ShellApp).startFeeds("photos grant")
            PhotoStore.refresh(activity, "permission granted")
        }
        val refused = result.filterValues { !it }.keys - Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        if (access == PhotosFeed.Access.DENIED && refused.isNotEmpty() && refused.none { activity.shouldShowRequestPermissionRationale(it) }) {
            Diagnostics.add("photosapp", "permission will not be asked again: opening the app's settings")
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", activity.packageName, null)))
        }
    }

    Box(Modifier.fillMaxSize().testTag("photos_root")) {
        LibraryPages(
            nav = nav,
            library = library,
            onGrant = { grant.launch(MEDIA_PERMISSIONS) },
            onOpen = { item, albumId -> openItem(activity, nav, item, albumId) },
        )
        PhotosOverlays(nav, library, activity)
    }
    BackHandler(enabled = nav.viewer == null && nav.album != null) { nav.album = null }
}

/** A tile's tap: a picture opens the viewer; a video opens the shell's one player, by explicit component (Q4 A, r3 D6). */
fun openItem(activity: ComponentActivity, nav: PhotosNav, item: MediaEntry, albumId: Long?) {
    if (!item.video) {
        nav.openViewer(item, albumId)
        return
    }
    val uri = PhotoStore.uriOf(item)
    Diagnostics.add("photosapp", "open video ${item.id} -> .video.PlayerActivity")
    runCatching {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).setClassName(activity.packageName, PLAYER_ACTIVITY).setDataAndType(uri, item.mime.ifEmpty { "video/*" }),
        )
    }.onFailure { Diagnostics.add("photosapp", "open video ${item.id} failed: ${it.javaClass.simpleName}") }
}

/** Movies & TV's player: named, never linked, so Photos holds no player code (E3: one player surface). */
const val PLAYER_ACTIVITY = "app.tileshell.video.PlayerActivity"

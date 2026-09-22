package app.tileshell.music

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.Manifest
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot

/**
 * The music player's own activity (phase 10 build tasks 1, 6 and 7).
 *
 * It is a real app inside the shell APK, not an internal page: it declares a launcher activity and
 * `android.intent.category.APP_MUSIC`, so it appears in the app list beside every other app and the
 * MUSIC slot can point at it the way it points at any music app (phase 10 Q5). Groove was an app in
 * W10M's list too.
 *
 * [ShellRoot] wraps it, so it takes the phone's accent, theme and the shell's density mapping (RV10)
 * exactly as every other shell-owned window does — an app inside the shell that themed itself would be
 * the one screen that did not follow the accent someone chose.
 *
 * `testTagsAsResourceId` is the QA contract every shell-owned window signs: without it a test tag is a
 * semantics property uiautomator never dumps, and a device row reads a correctly drawn screen as a
 * blank one. This activity shipped without it and MUSIC6's first run failed 27 of 35 assertions
 * against a collection that was on screen the whole time.
 *
 * **The route to now-playing is a build-time call, stated because R8 does not cover it.** R8 measured
 * the now-playing screen and nothing about how Groove reached it — Groove had a mini-player strip at
 * the bottom of the collection, and that strip is not in any measurement. So a tap on a track plays it
 * AND opens now-playing, and Back returns to the collection with the music still going. That invents
 * no unmeasured control, and it makes the screen reachable the moment there is something to show on it.
 */
class MusicActivity : ComponentActivity() {

    private enum class Screen { COLLECTION, NOW_PLAYING }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("music", "MusicActivity created")
        hideSystemBars()
        MusicStore.start(this)
        MusicPlayer.connect(this)
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    var screen by remember { mutableStateOf(Screen.COLLECTION) }
                    BackHandler(enabled = screen == Screen.NOW_PLAYING) { screen = Screen.COLLECTION }
                    when (screen) {
                        Screen.COLLECTION -> {
                            val context = LocalContext.current
                            val tracks by MusicStore.library.collectAsState()
                            // Held as state and re-read on every resume — NOT derived from the track
                            // list. A phone with no music has an empty list before AND after the grant,
                            // so an access flag keyed on the list would stay "denied" forever there.
                            var access by remember { mutableStateOf(MusicStore.hasAccess(context)) }
                            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                                val now = MusicStore.hasAccess(context)
                                if (now != access) {
                                    access = now
                                    MusicStore.refresh(context, "access changed while away")
                                }
                            }
                            val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                                access = granted
                                Diagnostics.add("music", "audio permission request: ${if (granted) "granted" else "denied"}")
                                if (granted) {
                                    MusicStore.refresh(context, "permission granted")
                                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO)) {
                                    // Denied with no rationale to show right after asking means Android will
                                    // not ask again ("don't ask again", or a second denial). The app's own
                                    // settings page is the only place left where it can be turned on, so the
                                    // tap goes there rather than doing nothing.
                                    Diagnostics.add("music", "audio permission will not be asked again: opening the app's settings")
                                    startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(Uri.fromParts("package", packageName, null)),
                                    )
                                }
                            }
                            val store = remember(context) { PlaylistStore.get(context) }
                            val playlists by store.playlists.collectAsState()
                            MusicCollectionPage(
                                tracks = tracks,
                                playlists = playlists,
                                hasAccess = access,
                                store = store,
                                onPlay = { queue, index ->
                                    MusicPlayer.play(queue, index)
                                    screen = Screen.NOW_PLAYING
                                },
                                onBack = { finish() },
                                onWindows = { goHome() },
                                onGrant = { grant.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                            )
                        }
                        Screen.NOW_PLAYING -> NowPlayingPage(
                            onBack = { screen = Screen.COLLECTION },
                            onWindows = { goHome() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onDestroy() {
        // The SESSION outlives this (E7) — only this activity's handle on it goes.
        if (isFinishing) MusicPlayer.release()
        super.onDestroy()
    }
}

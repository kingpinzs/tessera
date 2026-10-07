package app.tileshell.music

import android.app.ComponentCaller
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.LaunchedEffect
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
import app.tileshell.files.FilesProvider
import app.tileshell.media.AndroidUriAccess
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

    /**
     * Phase 11 (interview Q1 A, an ADD to phase 10): the pivot a Music App Shortcut asked for. Each request is a new
     * object, so the same pivot asked for twice still swings the pager.
     */
    private var pivotRequest by mutableStateOf<PivotRequest?>(null)

    private class PivotRequest(val pivot: MusicPivot)

    /** Phase 20: the request that brings Back from now-playing to the radio pivot after a station was started there. */
    private var radioReturn: PivotRequest? = null

    private fun takePivot(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_PIVOT) ?: return
        val pivot = runCatching { MusicPivot.valueOf(name) }.getOrNull() ?: return
        Diagnostics.add("music", "opened on pivot ${pivot.name.lowercase()} (shortcut)")
        pivotRequest = PivotRequest(pivot)
    }

    /**
     * Phase 18 (an ADD to phase 10; r3 D5, "below Q-18-2"): a play asked for by the launch. Each is a new object, and
     * each opens now-playing, as a tap on a song does.
     */
    private var nowPlayingRequest by mutableStateOf<Any?>(null)

    /**
     * The play extra ([MusicPlayExtra]; a trust rule — this activity is exported). [callerUid] is the uid that sent
     * THIS intent as the platform reports it, which it does only for a launch from the shell's own uid (or a caller
     * that chose to share its identity): anyone else is -1, and the extra is ignored with its line.
     *
     * An id plays that one library track through [MusicPlayer.play], the path a tap on a song takes; a URI of the
     * shell's FileProvider goes to the service, which plays the file as a one-item queue outside the library.
     */
    private fun takePlay(intent: Intent?, callerUid: Int) {
        if (intent == null) return
        // A launch replayed from Recents is not someone asking again.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        // The extras are whatever the sender put there: one of the wrong type reads as a value the rule refuses.
        val id = runCatching { if (intent.hasExtra(MusicPlayExtra.EXTRA_PLAY_ID)) intent.getLongExtra(MusicPlayExtra.EXTRA_PLAY_ID, 0L) else null }.getOrNull()
        val uri = runCatching { if (intent.hasExtra(MusicPlayExtra.EXTRA_PLAY_URI)) intent.getStringExtra(MusicPlayExtra.EXTRA_PLAY_URI).orEmpty() else null }.getOrNull()
        when (val decision = MusicPlayExtra.decide(callerUid, Process.myUid(), id, uri, FilesProvider.AUTHORITY)) {
            MusicPlayExtra.Decision.None -> Unit
            is MusicPlayExtra.Decision.Ignored -> Diagnostics.add("music", decision.line)
            is MusicPlayExtra.Decision.PlayId -> {
                val track = MusicStore.library.value.firstOrNull { it.id == decision.id }
                if (track == null) {
                    Diagnostics.add("music", "play extra ignored: not in the library")
                } else {
                    MusicPlayer.play(listOf(track), 0)
                    nowPlayingRequest = Any()
                }
            }
            is MusicPlayExtra.Decision.PlayUri -> {
                MusicPlayer.playFile(decision.uri)
                nowPlayingRequest = Any()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takePivot(intent)
        // Before Android 15 the platform does not say who sent a NEW intent to a running activity
        // (getLaunchedFromUid is the caller that CREATED it), so its play extra has no shell to be from.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) takePlay(intent, Process.INVALID_UID)
    }

    /** Android 15+: the new intent comes with its own caller, so the own-launch rule is asked of THIS intent's sender. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        takePlay(intent, caller.uid)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("music", "MusicActivity created")
        hideSystemBars()
        MusicStore.start(this)
        MusicPlayer.connect(this)
        takePivot(intent)
        // Only a fresh launch: a re-creation carries the same intent, and replaying it would restart the track.
        if (savedInstanceState == null) takePlay(intent, MusicPlayExtra.launchCaller(AndroidUriAccess(this)))
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    var screen by remember { mutableStateOf(Screen.COLLECTION) }
                    // Phase 20: the streaming side's pages, kept here so they are still open after now-playing.
                    val online = remember { OnlineNav() }
                    BackHandler(enabled = screen == Screen.NOW_PLAYING) { screen = Screen.COLLECTION }
                    val asked = nowPlayingRequest
                    LaunchedEffect(asked) { if (asked != null) screen = Screen.NOW_PLAYING }
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
                                    // A song of the phone's comes back where it always did, not to the radio pivot.
                                    if (pivotRequest != null && pivotRequest === radioReturn) pivotRequest = null
                                    screen = Screen.NOW_PLAYING
                                },
                                // L13-8: the drawn Back is the system Back — the same dispatcher, so the same callbacks
                                // in the same order (the jump grid, then the page's overlays). With nothing open it ends in
                                // the platform's own Back, as the system Back always did: Music's task moves to the back
                                // rather than finishing (it used to call finish()).
                                onBack = { onBackPressedDispatcher.onBackPressed() },
                                onWindows = { goHome() },
                                onGrant = { grant.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                                openPivot = pivotRequest?.pivot,
                                openPivotToken = pivotRequest,
                                online = online,
                                onNowPlaying = {
                                    // Phase 20: a station or a server song was started from the radio pivot's side, so
                                    // Back from now-playing returns to that pivot — and to the page the tap was made on.
                                    val back = PivotRequest(MusicPivot.RADIO)
                                    online.seenPivotToken = back
                                    radioReturn = back
                                    pivotRequest = back
                                    screen = Screen.NOW_PLAYING
                                },
                            )
                        }
                        Screen.NOW_PLAYING -> NowPlayingPage(
                            // L13-8: as above — the more menu closes first, then the BackHandler above returns to the list.
                            onBack = { onBackPressedDispatcher.onBackPressed() },
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

    companion object {
        /** Phase 11: a [MusicPivot] name; the pivot the activity opens on (Music's App Shortcuts carry it). */
        const val EXTRA_PIVOT = "pivot"
    }
}

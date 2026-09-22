package app.tileshell.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot

/**
 * The music player's own activity (phase 10 build tasks 1 and 6).
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
 * The now-playing screen (build task 7) is gated on R8 and replaces nothing here: it opens ON TOP of
 * this, as Groove's did.
 *
 * `testTagsAsResourceId` is the QA contract every shell-owned window signs: without it a test tag is a
 * semantics property uiautomator never dumps, and a device row reads a correctly drawn screen as a
 * blank one. This activity shipped without it and MUSIC6's first run failed 27 of 35 assertions
 * against a collection that was on screen the whole time.
 */
class MusicActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("music", "MusicActivity created")
        MusicStore.start(this)
        MusicPlayer.connect(this)
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    val context = LocalContext.current
                    val tracks by MusicStore.library.collectAsState()
                    val access = remember(tracks) { MusicStore.hasAccess(context) }
                    MusicCollectionPage(tracks, access)
                }
            }
        }
    }

    override fun onDestroy() {
        // The SESSION outlives this (E7) — only this activity's handle on it goes.
        if (isFinishing) MusicPlayer.release()
        super.onDestroy()
    }
}

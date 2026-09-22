package app.tileshell.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import app.tileshell.diag.Diagnostics

/**
 * The music player's own activity (phase 10 build task 1).
 *
 * It is a real app inside the shell APK, not an internal page: it declares a launcher activity and
 * `android.intent.category.APP_MUSIC`, so it appears in the app list beside every other app and the
 * MUSIC slot can point at it the way it points at any music app (phase 10 Q5). Groove was an app in
 * W10M's list too.
 *
 * The collection (build task 6) and the now-playing screen (build task 7, gated on R8) replace what is
 * drawn here. This is the entry point and the app identity, which the slot assignment and the app-list
 * row both need to exist before anything else can be wired to them.
 */
class MusicActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("music", "MusicActivity created")
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("music_root"), Alignment.Center) {
                BasicText("Music", style = androidx.compose.ui.text.TextStyle(color = Color.White))
            }
        }
    }
}

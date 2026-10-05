package app.tileshell.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar

/**
 * The window root every phase 17 activity sets (Decisions "harness contracts"): [ShellRoot] for the theme and the
 * 360-epx canvas, `testTagsAsResourceId` so the QA rows read each node's tag, the page's own background, then the
 * drawn bars. [statusBar] draws phase 01's status bar above the content (Movies & TV's library pages); Photos, Camera
 * and the player pass false (r11: those pages hide it). The nav bar is always drawn.
 */
fun ComponentActivity.setShellAppContent(
    statusBar: Boolean,
    background: Color = Color.Black,
    onBack: () -> Unit = { onBackPressedDispatcher.onBackPressed() },
    content: @Composable () -> Unit,
) {
    setContent {
        ShellRoot {
            Column(Modifier.fillMaxSize().background(background).semantics { testTagsAsResourceId = true }) {
                if (statusBar) W10mStatusBar()
                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
                W10mNavBar(onBack = onBack, onWindows = { goHome() })
            }
        }
    }
}

/** The drawn Start key: Home. */
fun Activity.goHome() {
    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

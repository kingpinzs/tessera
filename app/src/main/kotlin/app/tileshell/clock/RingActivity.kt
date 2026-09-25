package app.tileshell.clock

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import app.tileshell.diag.Diagnostics

/**
 * The ring toast over the keyguard (phase 15 T15-14, Q-E A). The ring's full-screen intent starts it while the
 * phone is locked or asleep: a translucent `showWhenLocked` + `turnScreenOn` activity that draws only the toast
 * at the top, so the lock screen (or, per the build-start check, the wallpaper) stays visible below it
 * (r11/clock.md 8.1, 8.7). It never dismisses the keyguard, opens nothing and is not exported; Back and touches
 * below the toast do nothing, so a stray touch cannot end a ringing alarm (E23). It finishes itself when the ring
 * ends or moves over an app (the phone was unlocked, T15-23).
 */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Drawn from y 0: the toast covers the status bar (8.1).
        window.setDecorFitsSystemWindows(false)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        Diagnostics.add("alarms", "ring activity shown for ${RingService.state.value?.logId}")
        // The toast covers the status bar (8.1): SystemUI's own bar, opaque over the keyguard, would hide its top
        // 45 epx — the app icon and the title. The status bar is hidden while the toast shows (a swipe shows it
        // transiently); the lock screen's nav bar stays below, as in the 15254 capture (8.7).
        window.decorView.post {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        setContent {
            RingRoot {
                val ring by RingService.state.collectAsState()
                LaunchedEffect(ring) {
                    val r = ring
                    if (r == null || r.surface != RingService.Surface.LOCKED) finish()
                }
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    ring?.let { RingToastHost(it, 0.dp) }
                }
            }
        }
    }
}

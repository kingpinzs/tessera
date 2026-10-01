package app.tileshell.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar
import app.tileshell.ui.LocalShellColors

/**
 * People's navigation, held above the composition so an intent can route while the activity is already running
 * (onNewIntent). The pages and their modes are build task 6's; this is what the activity hands them.
 */
class PeopleNav {
    /** What the last intent asked for; [routeToken] changes on every intent, so the same route twice is two opens. */
    var route by mutableStateOf<PeopleRoute>(PeopleRoute.Open(null))
        private set
    var routeToken by mutableIntStateOf(0)
        private set

    fun open(route: PeopleRoute) {
        this.route = route
        routeToken++
    }

    /** Back: the innermost thing first. False when there was nothing left to unwind (the activity finishes). */
    fun back(): Boolean = false
}

/**
 * Every People page hides Samsung's bars and draws the W10M status bar and the Back / Windows / Search nav bar
 * (phase 01's bar rule); Back is Back for the page, Windows goes Home.
 */
@Composable
fun PeopleApp(nav: PeopleNav, onBack: () -> Unit, onWindows: () -> Unit) {
    val colors = LocalShellColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        W10mStatusBar()
        Box(Modifier.fillMaxWidth().weight(1f))
        W10mNavBar(onBack = onBack, onWindows = onWindows)
    }
}

package app.tileshell.calendar

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
import androidx.compose.ui.graphics.Color
import app.tileshell.bars.W10mNavBar
import app.tileshell.bars.W10mStatusBar

/**
 * The Calendar app's navigation, held above the composition so an intent can route while the activity is already
 * running (onNewIntent). The pages and their modes are build task 4's; this is what the activity hands them.
 */
class CalendarNav {
    /** What the last intent asked for; [routeToken] changes on every intent, so the same route twice is two opens. */
    var route by mutableStateOf<CalendarRoute>(CalendarRoute.Open(null))
        private set
    var routeToken by mutableIntStateOf(0)
        private set

    fun open(route: CalendarRoute) {
        this.route = route
        routeToken++
    }

    /** Back: the innermost thing first. False when there was nothing left to unwind (the activity finishes). */
    fun back(): Boolean = false
}

/** The page behind every Calendar view (r11/calendar.md K1.3: #1A1A1A, with the status and nav bars black). */
val CalendarPageBackground = Color(0xFF1A1A1A)

/**
 * Every Calendar page hides Samsung's bars and draws the W10M status bar and the Back / Windows / Search nav bar
 * (phase 01's bar rule); Back is Back for the page, Windows goes Home.
 */
@Composable
fun CalendarApp(nav: CalendarNav, onBack: () -> Unit, onWindows: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        W10mStatusBar()
        Box(Modifier.fillMaxWidth().weight(1f).background(CalendarPageBackground))
        W10mNavBar(onBack = onBack, onWindows = onWindows)
    }
}

package app.tileshell.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.hideSystemBars
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.components.dismissOverlay

/**
 * Calendar (phase 16): an app inside the shell APK as Music and Alarms & Clock are — a launcher activity with its own
 * task, `CATEGORY_APP_CALENDAR`, so the CALENDAR slot can point at it. One activity hosts every page (r3 D11):
 * `singleTask`, so the Calendar tile, the Agenda pod, an App Shortcut or another app's intent re-routes the running app
 * ([onNewIntent]) instead of stacking a second one.
 *
 * It is exported with VIEW / EDIT / INSERT handlers, so any app can start it. What an intent may do is
 * [CalendarIntents]' and nothing more (Decisions "Trust" (c)): it chooses what is shown. An INSERT fills the editor in
 * and saves nothing without the user's tap, into the Tessera calendar whatever `calendar_id` it carried; an EDIT on an
 * event outside Tessera opens that event read-only.
 *
 * `testTagsAsResourceId` on the root is the QA contract every shell-owned window signs.
 */
class CalendarActivity : ComponentActivity() {
    private val nav = CalendarNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("calendar", "CalendarActivity created")
        hideSystemBars()
        route(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // A Back that closes an overlay is applied at once, so a touch right after it is not hit-tested against
            // what it closed (L13-3's rule).
            override fun handleOnBackPressed() {
                var handled = false
                dismissOverlay { handled = nav.back() }
                if (!handled) finish()
            }
        })
        setContent {
            ShellRoot {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    CalendarApp(
                        nav = nav,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        onWindows = { goHome() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    private fun route(intent: Intent?) {
        val route = if (intent == null) CalendarRoute.Open(null) else CalendarIntents.route(intent.action, intent.dataString, resolvedType(intent), IntentExtras(intent))
        Diagnostics.add("calendar", "open ${intent?.action ?: "no action"} -> ${describe(route)}")
        nav.open(route)
    }

    /** The intent's own type, else what the calendar provider says its data is; never another provider's answer. */
    private fun resolvedType(intent: Intent): String? = intent.type
        ?: intent.data?.takeIf { it.scheme == "content" && it.authority == CalendarIntents.AUTHORITY }?.let { runCatching { contentResolver.getType(it) }.getOrNull() }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The route without what the caller typed into it: a title or a note never reaches the diagnostics ring. */
    private fun describe(route: CalendarRoute): String = when (route) {
        is CalendarRoute.Open -> "open page=${route.page?.id ?: "default"}"
        is CalendarRoute.Time -> "time ${route.millis}"
        is CalendarRoute.Event -> "event ${route.id}"
        is CalendarRoute.Edit -> "edit ${route.id}"
        is CalendarRoute.Insert -> "insert (prefilled, unsaved)"
    }

    /**
     * The extras of an intent any app may have sent. Reading a Bundle that holds a class this app cannot load throws,
     * so every read is guarded and a value of the wrong type is no value.
     */
    private class IntentExtras(private val intent: Intent) : CalendarIntents.Extras {
        @Suppress("DEPRECATION")
        private fun value(key: String): Any? = runCatching { intent.extras?.get(key) }.getOrNull()
        override fun string(key: String): String? = (value(key) as? CharSequence)?.toString()
        override fun long(key: String): Long? = when (val v = value(key)) {
            is Long -> v
            is Int -> v.toLong()
            else -> null
        }
        override fun boolean(key: String): Boolean? = value(key) as? Boolean
    }

    companion object {
        /** The same key SettingsActivity.EXTRA_PAGE uses (build task 9's rule). */
        const val EXTRA_PAGE = CalendarIntents.EXTRA_PAGE
    }
}

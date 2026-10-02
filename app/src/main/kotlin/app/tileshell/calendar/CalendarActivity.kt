package app.tileshell.calendar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.hideSystemBars
import app.tileshell.clock.LocalIs24h
import app.tileshell.diag.Diagnostics
import app.tileshell.ui.ShellRoot
import app.tileshell.ui.components.dismissOverlay
import java.util.TimeZone

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
    private lateinit var model: CalendarModel
    private var is24h by mutableStateOf(false)

    /** Bumped when the date, the time or the zone changes under the app, so "today" and every shown time are read again. */
    private var clock by mutableIntStateOf(0)
    private var observer: ContentObserver? = null
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // The process caches its default zone; the broadcast is when it is read again (SystemBars does the same).
            TimeZone.setDefault(null)
            clock++
            model.reload()
        }
    }

    /**
     * A calendar permission asked for in place (r3 D7; phase 10 E18's form): READ for the views, WRITE for a save or a
     * Sync. Refused with no rationale left to show, Android will not ask again, so the tap opens the app's own settings.
     */
    private var asked: String? = null
    private val grant = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val permission = asked ?: return@registerForActivityResult
        Diagnostics.add("calendar", "permission request ${permission.substringAfterLast('.')}: ${if (granted) "granted" else "denied"}")
        if (granted) {
            model.checkPermissions("permission granted")
            // The Calendar tile's observer registers only once the read is held: a grant made here starts it, as the
            // Setup checklist's does (gate review A, finding 10).
            (applicationContext as? app.tileshell.ShellApp)?.startFeeds("calendar grant")
        } else if (!shouldShowRequestPermissionRationale(permission)) {
            Diagnostics.add("calendar", "the permission will not be asked again: opening the app's settings")
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("calendar", "CalendarActivity created")
        hideSystemBars()
        model = CalendarModel(this)
        route(intent)
        is24h = DateFormat.is24HourFormat(this)
        // The 12/24-hour setting changes with no broadcast.
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { is24h = DateFormat.is24HourFormat(this@CalendarActivity) }
        }.also { runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, it) } }
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
                CompositionLocalProvider(LocalIs24h provides is24h) {
                    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        CalendarApp(
                            nav = nav,
                            model = model,
                            clock = clock,
                            onBack = { onBackPressedDispatcher.onBackPressed() },
                            onWindows = { goHome() },
                            onGrant = { write ->
                                val permission = if (write) Manifest.permission.WRITE_CALENDAR else Manifest.permission.READ_CALENDAR
                                asked = permission
                                grant.launch(permission)
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            clockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
            // The three are broadcasts only the system sends; nothing outside it needs to reach this receiver (F14).
            Context.RECEIVER_NOT_EXPORTED,
        )
        TimeZone.setDefault(null)
        clock++
        model.start()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(clockReceiver) }
        model.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        is24h = DateFormat.is24HourFormat(this)
        model.checkPermissions("resume")
    }

    override fun onDestroy() {
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onDestroy()
    }

    private fun route(intent: Intent?) {
        val route = if (intent == null) CalendarRoute.Open(null) else CalendarIntents.route(intent.action, intent.dataString, resolvedType(intent), IntentExtras(intent))
        // Any app can start this activity with any action string and any extras: the line is built from the handled
        // action's own name (else the word "other") and the route's numbers, never from what a caller typed (F4).
        Diagnostics.add("calendar", CalendarIntents.openLine(intent?.action, route))
        nav.open(route)
    }

    /** The intent's own type, else what the calendar provider says its data is; never another provider's answer. */
    private fun resolvedType(intent: Intent): String? = intent.type
        ?: intent.data?.takeIf { it.scheme == "content" && it.authority == CalendarIntents.AUTHORITY }?.let { runCatching { contentResolver.getType(it) }.getOrNull() }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

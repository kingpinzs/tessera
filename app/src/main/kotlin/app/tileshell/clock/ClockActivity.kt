package app.tileshell.clock

import android.app.ComponentCaller
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.tileshell.bars.hideSystemBars
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.media.AndroidUriAccess
import app.tileshell.settings.SettingsActivity
import app.tileshell.settings.SettingsPage
import app.tileshell.ui.ShellRoot
import java.time.DayOfWeek
import app.tileshell.ui.components.dismissOverlay

/** The four tabs (r11/clock.md 1.3; "Strings as shipped": Alarm · World Clock · Timer · Stopwatch). */
enum class ClockTab(val id: String, val label: String, val glyph: String) {
    ALARM("alarm", "Alarm", Glyph.CLOCK_ALARM),
    WORLD_CLOCK("world_clock", "World Clock", Glyph.GLOBE_CLOCK),
    TIMER("timer", "Timer", Glyph.HOURGLASS),
    STOPWATCH("stopwatch", "Stopwatch", Glyph.TIMER);

    companion object {
        fun byId(id: String?): ClockTab? = entries.firstOrNull { it.id == id }
    }
}

/** The alarm being edited (§3): a new one, or an existing alarm's fields until Save. */
data class AlarmDraft(
    val id: String?,
    val hour: Int,
    val minute: Int,
    val name: String,
    val days: Set<DayOfWeek>,
    val sound: AlarmSound,
    val snoozeMinutes: Int,
) {
    companion object {
        /** A new alarm: 7:00, no name yet (the store names it "Alarm"), only once, the default sound, 10 minutes (4.5). */
        fun new() = AlarmDraft(null, 7, 0, "", emptySet(), AlarmSound.DEFAULT, Alarm.DEFAULT_SNOOZE)
        fun of(a: Alarm) = AlarmDraft(a.id, a.hour, a.minute, a.name, a.days, a.sound, a.snoozeMinutes)
    }
}

/** The timer being edited (4.8). */
data class TimerDraft(val id: String?, val hours: Int, val minutes: Int, val seconds: Int, val name: String) {
    val lengthMs: Long get() = ClockText.lengthMs(hours, minutes, seconds)

    companion object {
        fun new() = TimerDraft(null, 0, 5, 0, "")
        fun of(t: ClockTimer): TimerDraft {
            val (h, m, s) = ClockText.hmsParts(t.lengthMs)
            return TimerDraft(t.id, h, m, s, t.name)
        }
    }
}

/** Which screen the activity shows; the tabs are one screen, every editor and sub-page another. */
sealed interface ClockPage {
    data object Tabs : ClockPage
    data object AlarmEditor : ClockPage
    data object Sounds : ClockPage
    data object MusicPicker : ClockPage
    data object TimerEditor : ClockPage
    data object About : ClockPage
    data class TimerExpanded(val id: String) : ClockPage
    data object StopwatchExpanded : ClockPage
}

/**
 * The app's navigation and per-tab modes, held above the composition so an intent (`page`, a tile id, an
 * `AlarmClock` request) can route while the activity is already running, and so Back unwinds the innermost mode
 * first: the "…" menu, a sub-page, Select, the city search, compare mode.
 */
class ClockNav {
    var tab by mutableStateOf(ClockTab.ALARM)
    var page by mutableStateOf<ClockPage>(ClockPage.Tabs)
    var barExpanded by mutableStateOf(false)

    var alarmDraft by mutableStateOf<AlarmDraft?>(null)
    var timerDraft by mutableStateOf<TimerDraft?>(null)
    /** What the timer editor opened with, so Save stays dim until something changed (4.8). */
    var timerDraftOriginal: TimerDraft? = null

    var alarmSelect by mutableStateOf(false)
    var alarmSelected by mutableStateOf<Set<String>>(emptySet())
    var timerSelect by mutableStateOf(false)
    var timerSelected by mutableStateOf<Set<String>>(emptySet())
    /** The timer the app bar's Pin means and a `timer.<id>` tile opens onto: the last one touched, else the first. */
    var focusedTimer by mutableStateOf<String?>(null)

    var worldSearch by mutableStateOf(false)
    var compare by mutableStateOf(false)
    var compareOffset by mutableIntStateOf(0)

    /** A tab tap is a JUMP (M1): the input's uptime and a token the header's frame logger keys on. */
    var jumpToken by mutableIntStateOf(0)
    var jumpInputUptime = 0L

    fun tapTab(t: ClockTab) {
        if (t == tab) return
        jumpInputUptime = SystemClock.uptimeMillis()
        leaveTabModes()
        tab = t
        jumpToken++
    }

    /** A swipe settled on [t] (its motion was logged by the pager). */
    fun settleTab(t: ClockTab) {
        if (t == tab) return
        leaveTabModes()
        tab = t
    }

    /** An external open (a notification, a tile, a shortcut, the API): no motion to log. */
    fun show(t: ClockTab) {
        leaveTabModes()
        tab = t
        page = ClockPage.Tabs
    }

    private fun leaveTabModes() {
        barExpanded = false
        alarmSelect = false
        alarmSelected = emptySet()
        timerSelect = false
        timerSelected = emptySet()
        worldSearch = false
        compare = false
    }

    fun openAlarmEditor(draft: AlarmDraft) {
        alarmDraft = draft
        page = ClockPage.AlarmEditor
    }

    fun openTimerEditor(draft: TimerDraft) {
        timerDraft = draft
        timerDraftOriginal = draft
        page = ClockPage.TimerEditor
    }

    /** L13-18: the page About was opened from — the tabs or an editor, whose draft is still held here — where Back returns. */
    private var aboutFrom: ClockPage = ClockPage.Tabs

    fun openAbout() {
        barExpanded = false
        aboutFrom = page
        page = ClockPage.About
    }

    /** Back: the innermost thing first. False when there was nothing left to unwind (the activity finishes). */
    fun back(): Boolean {
        if (barExpanded) { barExpanded = false; return true }
        when (page) {
            ClockPage.Tabs -> Unit
            ClockPage.Sounds, ClockPage.MusicPicker -> { page = ClockPage.AlarmEditor; return true }
            ClockPage.About -> { page = aboutFrom; return true }
            else -> { page = ClockPage.Tabs; return true }
        }
        when {
            alarmSelect -> { alarmSelect = false; alarmSelected = emptySet() }
            timerSelect -> { timerSelect = false; timerSelected = emptySet() }
            worldSearch -> worldSearch = false
            compare -> compare = false
            else -> return false
        }
        return true
    }
}

/**
 * Alarms & Clock (phase 15 build task 4): a real app inside the shell APK — a launcher activity with its own task,
 * exactly as Music is — drawing W10M's four tabs on the back end of tasks 2 and 3. Every value comes from
 * r11/clock.md (14393, unchanged-since not proven — U1) or is a tagged approximation with its H row.
 *
 * Opened with the extra `page` (alarm / world_clock / timer / stopwatch — notifications, the lock screen's next
 * alarm, the App Shortcuts) or a secondary tile's `EXTRA_LAUNCH_TILE_ID` (`timer.<id>` → the Timer tab with that
 * timer; `stopwatch` → the Stopwatch tab; T15-40), and by [AlarmApiActivity] with an alarm or timer to edit.
 *
 * `testTagsAsResourceId` is the QA contract every shell-owned window signs (MusicActivity's lesson).
 */
class ClockActivity : ComponentActivity() {
    private val nav = ClockNav()
    private var is24h by mutableStateOf(false)
    private var observer: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diagnostics.add("clock", "ClockActivity created")
        hideSystemBars()
        ClockStore.get(this)
        route(intent, ClockIntents.launchCaller(AndroidUriAccess(this)))
        is24h = DateFormat.is24HourFormat(this)
        // The 12/24-hour setting changes with no broadcast (SystemBars does the same).
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { is24h = DateFormat.is24HourFormat(this@ClockActivity) }
        }.also { runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, it) } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // L13-11: a Back that closes the "…" bar or the city search is applied at once, so a touch right after it is
            // not hit-tested against what it closed (their OverlayLayers stop placing them; L13-3's rule).
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
                        ClockApp(
                            nav = nav,
                            onBack = { onBackPressedDispatcher.onBackPressed() },
                            onWindows = { goHome() },
                            onNotificationSettings = { openNotificationSettings() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Before Android 15 the platform does not say who sent a NEW intent to a running activity, so its edit bundle
        // has no shell to be from (as MusicActivity's play extra).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) route(intent, ClockIntents.NO_CALLER)
    }

    /** Android 15+: the new intent comes with its own caller, so the edit bundle is weighed against THIS intent's sender. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        route(intent, try { caller.uid } catch (e: Throwable) { ClockIntents.NO_CALLER })
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        is24h = DateFormat.is24HourFormat(this)
    }

    override fun onDestroy() {
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        super.onDestroy()
    }

    /**
     * The `page` extra, a secondary tile's id, or an `AlarmClock` request to edit — all decided by [ClockIntents.open]
     * (ledger L18-3): this activity is exported, so every extra is read there through the reader that cannot throw, and
     * the edit bundle is honoured only when [callerUid], the uid that sent this intent, is the shell's own.
     */
    private fun route(intent: Intent?, callerUid: Int) {
        val open = ClockIntents.open(intent?.let { AndroidExtras(it) }, callerUid, Process.myUid())
        open.focusedTimer?.let { nav.focusedTimer = it }
        open.tab?.let { nav.show(it) }
        open.timer?.let { nav.openTimerEditor(it) }
        open.alarm?.let { nav.openAlarmEditor(it) }
        open.lines.forEach { Diagnostics.add("clock", it) }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** More → Notification settings: the shell's Setup page, where "Full-screen alarms" and "Display over other apps" live (T15-43). */
    private fun openNotificationSettings() {
        Diagnostics.add("clock", "notification settings -> the Setup checklist")
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, SettingsPage.CHECKLIST.name))
    }

    companion object {
        /** The same key SettingsActivity.EXTRA_PAGE uses (build task 9's rule). */
        const val EXTRA_PAGE = ClockIntents.EXTRA_PAGE

        /** An `AlarmClock` request without SKIP_UI: the editor opens filled in with these (AlarmApiActivity). */
        const val EXTRA_API_EDIT = ClockIntents.EXTRA_API_EDIT
        const val API_KIND = ClockIntents.API_KIND
        const val API_HOUR = ClockIntents.API_HOUR
        const val API_MINUTE = ClockIntents.API_MINUTE
        const val API_DAYS = ClockIntents.API_DAYS
        const val API_MESSAGE = ClockIntents.API_MESSAGE
        const val API_SOUND_KIND = ClockIntents.API_SOUND_KIND
        const val API_SOUND_URI = ClockIntents.API_SOUND_URI
        const val API_TIMER_LENGTH_MS = ClockIntents.API_TIMER_LENGTH_MS
    }
}

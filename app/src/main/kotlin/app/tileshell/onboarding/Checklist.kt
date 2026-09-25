package app.tileshell.onboarding

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tileshell.ShellApp
import app.tileshell.brand.Glyph
import app.tileshell.diag.Diagnostics
import app.tileshell.feeds.PhotosFeed
import app.tileshell.feeds.TileNotificationListener
import app.tileshell.settings.PageHeader
import app.tileshell.settings.TwoLineItem
import app.tileshell.tiles.api.LegacyBadgeReceiver
import app.tileshell.tiles.api.SamsungBadgeReader

/** One checklist row's state. Later phases ADD rows (phase 01 Decisions). */
enum class RowState { GRANTED, PARTIAL, MISSING }

data class ChecklistRow(val id: String, val title: String, val state: RowState, val detail: String, val action: () -> Unit)

/**
 * The onboarding / health checklist (P4 design, H33): a W10M Settings page of rows, each with a status glyph,
 * its name, a one-line state and a tap target that opens the grant.
 */
object Checklist {
    fun homeHeld(context: Context) = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    fun notificationAccess(context: Context) = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context, TileNotificationListener::class.java))

    fun usageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun granted(context: Context, permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Phase 05: the keyboard's input-method id, as `ime list` and Settings.Secure spell it. */
    fun keyboardId(context: Context) = ComponentName(context, app.tileshell.ime.KeyboardService::class.java).flattenToShortString()

    /** Phase 05 E1: the keyboard is in Android's ENABLED input methods (`ime enable`). */
    fun keyboardEnabled(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.id == keyboardId(context) }

    /**
     * Phase 05 E1: the keyboard is the SELECTED input method (`ime set`). Asked of the input-method
     * manager (API 34, the shell's minSdk), not read out of Settings.Secure: E1 caught the secure
     * setting coming back empty to the app while `ime set` had plainly selected the keyboard.
     */
    /** Phase 15: the special app access behind ringing over the keyguard (USE_FULL_SCREEN_INTENT, API 34+). */
    fun fullScreenAlarms(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun keyboardSelected(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java).currentInputMethodInfo?.id == keyboardId(context)
}

@Composable
fun ChecklistPage() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { tick++; (context.applicationContext as ShellApp).startFeeds("checklist resume") } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val requestPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    @Suppress("UNUSED_EXPRESSION") tick
    val rows = listOf(
        ChecklistRow("home", "Default Home", if (Checklist.homeHeld(context)) RowState.GRANTED else RowState.MISSING, "Start shows when you press Home") {
            requestRole.launch(context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME))
        },
        ChecklistRow("notifications", "Notification access", if (Checklist.notificationAccess(context)) RowState.GRANTED else RowState.MISSING, "Live tiles show your notifications") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, TileNotificationListener::class.java).flattenToString()))
        },
        ChecklistRow("photos", "Photos", when (PhotosFeed.access(context)) { PhotosFeed.Access.GRANTED -> RowState.GRANTED; PhotosFeed.Access.PARTIAL -> RowState.PARTIAL; else -> RowState.MISSING }, "The Photos tile cycles your pictures") {
            requestPermissions.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        },
        // Phase 10 task 10 (E18): without this the music player's library is simply empty, which looks
        // exactly like a phone with no music on it — only one of those is fixable from here.
        ChecklistRow("music", "Music", if (Checklist.granted(context, Manifest.permission.READ_MEDIA_AUDIO)) RowState.GRANTED else RowState.MISSING, "The music player plays the songs on this phone") {
            requestPermissions.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
        },
        ChecklistRow("calendar", "Calendar", if (Checklist.granted(context, Manifest.permission.READ_CALENDAR)) RowState.GRANTED else RowState.MISSING, "The Calendar tile shows what's next") {
            requestPermissions.launch(arrayOf(Manifest.permission.READ_CALENDAR))
        },
        ChecklistRow("location", "Location", if (Checklist.granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) RowState.GRANTED else RowState.MISSING, "Weather for where you are") {
            requestPermissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        ChecklistRow("usage", "Usage access", if (Checklist.usageAccess(context)) RowState.GRANTED else RowState.MISSING, "Back on Start returns to your last app") {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
        },
        SamsungBadgeReader.probe(context).let { probe ->
            ChecklistRow("samsung_badges", "Samsung badge counts", if (probe == "readable") RowState.GRANTED else RowState.MISSING, "Real unread counts from Samsung: $probe") {}
        },
        LegacyBadgeReceiver.lastSeen.let { seen ->
            ChecklistRow("legacy_badges", "App badge messages", if (seen != null) RowState.GRANTED else RowState.MISSING,
                seen?.let { "Last from ${it.sender ?: it.pkg}: ${it.count}" } ?: "None received yet") {}
        },
        // Phase 05 build task 9: the keyboard is enabled, then selected — two steps, because Android asks
        // for them separately (the enable page, then the switcher).
        ChecklistRow("keyboard_enabled", "Keyboard enabled", if (Checklist.keyboardEnabled(context)) RowState.GRANTED else RowState.MISSING, "Turn on the Windows-style keyboard") {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        },
        ChecklistRow("keyboard_selected", "Keyboard selected", if (Checklist.keyboardSelected(context)) RowState.GRANTED else RowState.MISSING, "Use it for typing everywhere") {
            context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        },
        // Phase 15 (Q-E A, T15-14): alarms ring as W10M's toast — over the lock screen through a full-screen intent
        // (special app access on API 34+), over the app in use through "Display over other apps". Without either the
        // alarm still sounds and shows as Android's own notification, never silently.
        ChecklistRow("full_screen_alarms", "Full-screen alarms", if (Checklist.fullScreenAlarms(context)) RowState.GRANTED else RowState.MISSING, "Alarms ring over the lock screen") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:${context.packageName}")))
        },
        ChecklistRow("overlay", "Display over other apps", if (Settings.canDrawOverlays(context)) RowState.GRANTED else RowState.MISSING, "Alarms ring over the app you're using") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:${context.packageName}")))
        },
        ChecklistRow("listener", "Live tiles running", if (TileNotificationListener.connected) RowState.GRANTED else RowState.MISSING, if (TileNotificationListener.connected) "Connected" else "Not connected") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
    )
    Diagnostics.add("checklist", rows.joinToString { "${it.id}=${it.state}" })

    PageHeader(Glyph.CHECKMARK, "Setup checklist")
    rows.forEach { row ->
        val (glyph, color) = when (row.state) {
            RowState.GRANTED -> Glyph.CHECKMARK to Color(0xFF10893E)
            RowState.PARTIAL -> Glyph.WARNING to Color(0xFFFFB900)
            RowState.MISSING -> Glyph.DISMISS to Color(0xFFE81123)
        }
        val stateText = when (row.state) { RowState.GRANTED -> "On"; RowState.PARTIAL -> "Partial"; RowState.MISSING -> "Off" }
        TwoLineItem(glyph, row.title, "$stateText · ${row.detail}", "checklist:${row.id}:${row.state.name.lowercase()}", glyphColor = color, onClick = row.action)
    }
}

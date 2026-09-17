package app.tileshell.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Profile kinds the shell groups in the app list (P4 design). */
enum class ProfileKind { MAIN, WORK, PRIVATE }

data class AppEntry(
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val profile: ProfileKind,
    val isSystem: Boolean,
    val firstInstallTime: Long,
) {
    val key: String get() = "${component.flattenToString()}#${user.hashCode()}"
}

/**
 * Every launchable activity for every profile the default Home may see, kept live through
 * LauncherApps callbacks (package add / remove / change, profile availability).
 */
class AppCatalog private constructor(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val state = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = state.asStateFlow()
    private val iconCache = HashMap<String, ImageBitmap>()

    init {
        refresh("init")
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh("removed $packageName")
            override fun onPackageAdded(packageName: String, user: UserHandle) = refresh("added $packageName")
            override fun onPackageChanged(packageName: String, user: UserHandle) = refresh("changed $packageName")
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh("available")
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh("unavailable")
        }, Handler(Looper.getMainLooper()))
    }

    fun profileKind(user: UserHandle): ProfileKind {
        if (user == android.os.Process.myUserHandle()) return ProfileKind.MAIN
        return runCatching {
            val info = launcherApps.getLauncherUserInfo(user)
            when (info?.userType) {
                UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.PRIVATE
                else -> ProfileKind.WORK
            }
        }.getOrDefault(ProfileKind.WORK)
    }

    fun isQuietMode(user: UserHandle): Boolean = runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)

    /** Profiles the default Home may see (LauncherApps also returns private space when the platform allows it). */
    fun profiles(): List<UserHandle> = runCatching { launcherApps.profiles }.getOrNull() ?: userManager.userProfiles

    @Synchronized
    fun refresh(reason: String) {
        val list = mutableListOf<AppEntry>()
        for (user in profiles()) {
            val activities: List<LauncherActivityInfo> = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            val kind = profileKind(user)
            for (a in activities) {
                if (a.componentName.packageName == context.packageName && a.componentName.className.endsWith("StartActivity")) continue
                val info = a.applicationInfo
                list += AppEntry(
                    component = a.componentName,
                    user = user,
                    label = a.label?.toString() ?: a.componentName.packageName,
                    profile = kind,
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    // Per-user install time ("New" caption, X14); an update keeps it, a reinstall changes it.
                    firstInstallTime = a.firstInstallTime,
                )
            }
        }
        state.value = list.sortedWith(compareBy({ it.profile.ordinal }, { it.label.lowercase() }))
        iconCache.clear()
        Diagnostics.add("apps", "catalog refreshed ($reason): ${list.size} activities")
    }

    fun find(component: ComponentName?, user: UserHandle? = null): AppEntry? =
        component?.let { cn -> state.value.firstOrNull { it.component == cn && (user == null || it.user == user) } }

    fun firstForPackage(packageName: String): AppEntry? =
        state.value.firstOrNull { it.component.packageName == packageName && it.profile == ProfileKind.MAIN }

    /** [badged] = false returns the icon without Android's profile badge (the app list draws its own P4 glyph). */
    fun icon(entry: AppEntry, sizePx: Int, badged: Boolean = true): ImageBitmap? = synchronized(iconCache) {
        iconCache.getOrPut("${entry.key}@$sizePx${if (badged) "" else "u"}") {
            val drawable = runCatching {
                launcherApps.getActivityList(entry.component.packageName, entry.user)
                    .firstOrNull { it.componentName == entry.component }?.let { if (badged) it.getBadgedIcon(0) else it.getIcon(0) }
            }.getOrNull() ?: return null
            drawable.toBitmap(sizePx).asImageBitmap()
        }
    }

    private val launchListeners = java.util.concurrent.CopyOnWriteArrayList<(AppEntry) -> Unit>()

    /** Called after every shell launch of an app (the app list's "New" caption clears on first launch, X11). */
    fun addLaunchListener(listener: (AppEntry) -> Unit) { launchListeners += listener }

    fun launch(entry: AppEntry, sourceBounds: android.graphics.Rect?, options: android.os.Bundle?) {
        launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options)
        Diagnostics.add("launch", "startMainActivity ${entry.component.flattenToShortString()} user=${entry.user.hashCode()}")
        launchListeners.forEach { it(entry) }
    }

    companion object {
        @Volatile private var instance: AppCatalog? = null
        fun get(context: Context): AppCatalog =
            instance ?: synchronized(this) { instance ?: AppCatalog(context.applicationContext).also { instance = it } }
    }
}

fun Drawable.toBitmap(sizePx: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bmp
}

@Suppress("unused")
private fun PackageManager.safeLabel(pkg: String) = runCatching { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() }.getOrNull()

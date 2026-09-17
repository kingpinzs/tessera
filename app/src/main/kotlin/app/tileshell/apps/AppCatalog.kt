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

    @Synchronized
    fun refresh(reason: String) {
        val pm = context.packageManager
        val list = mutableListOf<AppEntry>()
        for (user in userManager.userProfiles) {
            val activities: List<LauncherActivityInfo> = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            for (a in activities) {
                if (a.componentName.packageName == context.packageName && a.componentName.className.endsWith("StartActivity")) continue
                val info = a.applicationInfo
                val installTime = runCatching { pm.getPackageInfo(a.componentName.packageName, 0).firstInstallTime }.getOrDefault(0L)
                list += AppEntry(
                    component = a.componentName,
                    user = user,
                    label = a.label?.toString() ?: a.componentName.packageName,
                    profile = profileKind(user),
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    firstInstallTime = installTime,
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

    fun icon(entry: AppEntry, sizePx: Int): ImageBitmap? = synchronized(iconCache) {
        iconCache.getOrPut("${entry.key}@$sizePx") {
            val drawable = runCatching {
                launcherApps.getActivityList(entry.component.packageName, entry.user)
                    .firstOrNull { it.componentName == entry.component }?.getBadgedIcon(0)
            }.getOrNull() ?: return null
            drawable.toBitmap(sizePx).asImageBitmap()
        }
    }

    fun launch(entry: AppEntry, sourceBounds: android.graphics.Rect?, options: android.os.Bundle?) {
        launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options)
        Diagnostics.add("launch", "startMainActivity ${entry.component.flattenToShortString()} user=${entry.user.hashCode()}")
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

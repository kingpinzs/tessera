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
import android.os.Process
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
        // One LauncherApps callback for the whole shell (phase 02 build task 5): it refreshes the list AND tells the
        // layout which packages are gone. Every callback is logged by name so which one Android delivers for an
        // uninstall, an update, a disable and an unavailable package is a matter of record, not of assumption.
        launcherApps.registerCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                Diagnostics.add("apps", "LauncherApps.onPackageRemoved $packageName user=${user.hashCode()}")
                refresh("removed $packageName")
                // The only callback that drops tiles. An update (`adb install -r`) does not come through here:
                // the platform's PackageMonitor routes the remove half of a replace to onPackageUpdateStarted,
                // which LauncherApps does not forward, so an update keeps its tiles (E6).
                notifyPackagesRemoved(setOf(packageName), user)
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                Diagnostics.add("apps", "LauncherApps.onPackageAdded $packageName user=${user.hashCode()}")
                refresh("added $packageName")
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                // An update, an enable and a disable all land here: the tiles stay (phase 02 build task 5).
                Diagnostics.add("apps", "LauncherApps.onPackageChanged $packageName user=${user.hashCode()}")
                refresh("changed $packageName")
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                Diagnostics.add("apps", "LauncherApps.onPackagesAvailable ${packageNames.toList()} replacing=$replacing")
                refresh("available")
            }

            override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<android.content.pm.ShortcutInfo>, user: UserHandle) {
                // Phase 11: an open burst for this package closes (Decisions "Satellite tap").
                Diagnostics.add("apps", "LauncherApps.onShortcutsChanged $packageName user=${user.hashCode()} n=${shortcuts.size}")
                shortcutsChangedListeners.forEach { it(packageName, user) }
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                // Unavailable is not gone (external storage, a stopped profile): the tiles stay.
                Diagnostics.add("apps", "LauncherApps.onPackagesUnavailable ${packageNames.toList()} replacing=$replacing")
                refresh("unavailable")
            }
        }, Handler(Looper.getMainLooper()))
    }

    private val packageRemovedListeners = java.util.concurrent.CopyOnWriteArrayList<(Set<String>, UserHandle) -> Unit>()
    private val shortcutsChangedListeners = java.util.concurrent.CopyOnWriteArrayList<(String, UserHandle) -> Unit>()

    /** Phase 11: LauncherApps' onShortcutsChanged, forwarded (the one callback lives here). */
    fun addShortcutsChangedListener(listener: (String, UserHandle) -> Unit) { shortcutsChangedListeners += listener }
    fun removeShortcutsChangedListener(listener: (String, UserHandle) -> Unit) { shortcutsChangedListeners -= listener }

    /** Called for an uninstall only (never for an update, a disable or an unavailable package). */
    fun addPackageRemovedListener(listener: (Set<String>, UserHandle) -> Unit) { packageRemovedListeners += listener }

    private fun notifyPackagesRemoved(packages: Set<String>, user: UserHandle) {
        packageRemovedListeners.forEach { it(packages, user) }
    }

    /** What the shell can tell about a package across every profile it sees. */
    enum class PackageState { INSTALLED, GONE, UNKNOWN }

    /**
     * Whether [packageName] is still installed anywhere the shell can see, for the uninstall the shell slept
     * through (no process, no callback). Disabled and unavailable packages are still installed and answer
     * INSTALLED, so they keep their tiles; anything the shell cannot resolve answers UNKNOWN and keeps them too.
     */
    fun packageState(packageName: String): PackageState {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        // This profile answers through PackageManager, which needs no Home role (QUERY_ALL_PACKAGES is held).
        try {
            context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            return PackageState.INSTALLED
        } catch (e: PackageManager.NameNotFoundException) {
            // Not in this profile. The other profiles answer below.
        } catch (e: Exception) {
            Diagnostics.add("apps", "package state of $packageName unknown: $e")
            return PackageState.UNKNOWN
        }
        for (user in profiles()) {
            if (user == Process.myUserHandle()) continue
            try {
                if (launcherApps.getApplicationInfo(packageName, flags, user) != null) return PackageState.INSTALLED
            } catch (e: PackageManager.NameNotFoundException) {
                continue
            } catch (e: Exception) {
                Diagnostics.add("apps", "package state of $packageName unknown for user ${user.hashCode()}: $e")
                return PackageState.UNKNOWN
            }
        }
        return PackageState.GONE
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

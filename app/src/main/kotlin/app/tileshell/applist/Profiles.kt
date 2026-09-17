package app.tileshell.applist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.ProfileKind
import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One separate profile the default Home sees (P4 design: work profile, private space). */
data class ShellProfile(val user: UserHandle, val kind: ProfileKind, val quiet: Boolean, val serial: Long) {
    /** "work" or "private", used in test tags ("profile_group:<work|private>"). */
    val tagName: String get() = kind.name.lowercase()
}

/**
 * Tracks the non-main profiles and their quiet-mode (locked) state, live through the profile broadcasts.
 * Also refreshes the catalog on locale change (app labels and the "#" group follow the locale).
 */
class ProfileTracker private constructor(private val context: Context) {
    private val catalog = AppCatalog.get(context)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val state = MutableStateFlow(read())
    val profiles: StateFlow<List<ShellProfile>> = state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val action = intent.action ?: return
            Diagnostics.add("applist", "broadcast $action")
            catalog.refresh(action.substringAfterLast('.'))
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
            addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
            if (Build.VERSION.SDK_INT >= 35) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun read(): List<ShellProfile> {
        val me = Process.myUserHandle()
        return catalog.profiles().filter { it != me }.map { user ->
            ShellProfile(
                user = user,
                kind = catalog.profileKind(user),
                quiet = catalog.isQuietMode(user),
                serial = runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(user.hashCode().toLong()),
            )
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.serial }))
    }

    fun refresh() {
        val next = read()
        if (next != state.value) {
            state.value = next
            Diagnostics.add("applist", "profiles: ${next.joinToString { "${it.tagName}#${it.serial}${if (it.quiet) "(locked)" else ""}" }}")
        }
    }

    /**
     * Lock ([enable] = true) or unlock a profile through quiet mode. The default Home may call this; unlocking shows
     * Android's own credential prompt when the profile has one (P4 design, E18).
     */
    fun requestQuietMode(profile: ShellProfile, enable: Boolean) {
        val result = runCatching { userManager.requestQuietModeEnabled(enable, profile.user) }
        Diagnostics.add("applist", "requestQuietModeEnabled($enable, ${profile.tagName}#${profile.serial}) -> ${result.getOrElse { "error ${it.javaClass.simpleName}: ${it.message}" }}")
        refresh()
    }

    companion object {
        @Volatile private var instance: ProfileTracker? = null
        fun get(context: Context): ProfileTracker =
            instance ?: synchronized(this) { instance ?: ProfileTracker(context.applicationContext).also { instance = it } }
    }
}

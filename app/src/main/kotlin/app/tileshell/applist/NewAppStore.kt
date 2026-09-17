package app.tileshell.applist

import android.app.role.RoleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.diag.Diagnostics
import app.tileshell.start.BackHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent "New" caption state per package per profile (rules in [NewAppRules]), in app-private SharedPreferences.
 * The baseline is taken the first time the store sees the shell holding the Home role.
 */
class NewAppStore private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("applist_new", Context.MODE_PRIVATE)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val serials = HashMap<UserHandle, Long>()
    private val records = HashMap<String, InstallRecord>()
    private var baselineMs: Long = prefs.getLong(KEY_BASELINE, -1L)
    private val state = MutableStateFlow<Set<String>>(emptySet())
    private val scannedUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Keys ([NewAppRules.key]) of apps currently carrying the caption. */
    val newKeys: StateFlow<Set<String>> = state.asStateFlow()

    init {
        for ((k, v) in prefs.all) {
            if (k.startsWith(RECORD_PREFIX)) InstallRecord.decode(v as? String)?.let { records[k.removePrefix(RECORD_PREFIX)] = it }
        }
        publish()
        AppCatalog.get(context).addLaunchListener { markLaunched(it) }
    }

    @Synchronized
    fun serialOf(user: UserHandle): Long = serials.getOrPut(user) {
        runCatching { userManager.getSerialNumberForUser(user) }.getOrDefault(user.hashCode().toLong())
    }

    fun keyOf(entry: AppEntry): String = NewAppRules.key(entry.component.packageName, serialOf(entry.user))

    private fun isHome(): Boolean =
        runCatching { context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(false)

    /** Bring the records in line with the installed apps. Safe off the main thread. */
    fun reconcile(apps: List<AppEntry>) {
        if (apps.isEmpty()) return
        val home = baselineMs < 0 && isHome()
        synchronized(this) {
            val editor = prefs.edit()
            var changed = false
            if (baselineMs < 0) {
                if (!home) return
                baselineMs = System.currentTimeMillis()
                editor.putLong(KEY_BASELINE, baselineMs)
                for (entry in apps) {
                    val key = keyOf(entry)
                    val rec = NewAppRules.atBaseline(entry.firstInstallTime)
                    records[key] = rec
                    editor.putString(RECORD_PREFIX + key, rec.encode())
                }
                editor.apply()
                Diagnostics.add("applist", "new-caption baseline taken at $baselineMs: ${records.size} installed apps carry no caption")
                publish()
                return
            }
            for (entry in apps) {
                val key = keyOf(entry)
                val existing = records[key]
                val next = NewAppRules.decide(existing, entry.isSystem, entry.firstInstallTime, baselineMs)
                if (next != existing) {
                    records[key] = next
                    editor.putString(RECORD_PREFIX + key, next.encode())
                    changed = true
                    if (next.state == NewState.NEW) Diagnostics.add("applist", "new caption on $key (installed ${next.firstInstallTime})")
                }
            }
            if (changed) {
                editor.apply()
                publish()
            }
        }
    }

    /** A launch the shell saw clears the caption (X11). */
    fun markLaunched(entry: AppEntry) {
        val key = keyOf(entry)
        synchronized(this) {
            val rec = records[key] ?: return
            if (rec.state != NewState.NEW) return
            clear(key, rec, "launched from the shell")
        }
    }

    /**
     * With Usage access, an ACTIVITY_RESUMED event since install clears the caption (X11). UsageStats only reports
     * this profile's events, so a launch inside another profile the shell did not start clears nothing (X14).
     */
    fun scanUsage() {
        if (!BackHistory.hasUsageAccess(context)) return
        val mySerial = serialOf(Process.myUserHandle())
        val pending = synchronized(this) {
            records.filter { (k, r) -> r.state == NewState.NEW && NewAppRules.serialOf(k) == mySerial }
        }
        if (pending.isEmpty()) return
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        // Each key's events are read once per process: from its install time, then from the end of its last scan.
        val from = pending.entries.minOf { (k, r) -> maxOf(r.firstInstallTime, scannedUntil[k] ?: 0L) - SCAN_OVERLAP_MS }
        val events = runCatching { usm.queryEvents(from, now) }.getOrNull() ?: return
        pending.keys.forEach { scannedUntil[it] = now }
        val seen = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val key = NewAppRules.key(e.packageName, mySerial)
            val rec = pending[key] ?: continue
            if (NewAppRules.clearedByUsage(rec, e.timeStamp)) seen[key] = e.timeStamp
        }
        if (seen.isEmpty()) return
        synchronized(this) {
            for ((key, at) in seen) {
                val rec = records[key] ?: continue
                if (rec.state == NewState.NEW && rec == pending[key]) clear(key, rec, "resumed at $at (UsageStats)")
            }
        }
    }

    private fun clear(key: String, rec: InstallRecord, why: String) {
        val next = NewAppRules.afterLaunch(rec)
        records[key] = next
        prefs.edit().putString(RECORD_PREFIX + key, next.encode()).apply()
        Diagnostics.add("applist", "new caption cleared on $key: $why")
        publish()
    }

    private fun publish() {
        state.value = records.filterValues { it.state == NewState.NEW }.keys.toSet()
    }

    companion object {
        private const val KEY_BASELINE = "baseline_ms"
        private const val RECORD_PREFIX = "r:"
        private const val SCAN_OVERLAP_MS = 60_000L

        @Volatile private var instance: NewAppStore? = null
        fun get(context: Context): NewAppStore =
            instance ?: synchronized(this) { instance ?: NewAppStore(context.applicationContext).also { instance = it } }
    }
}

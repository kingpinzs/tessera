package app.tileshell.applist

import android.app.usage.UsageStatsManager
import android.content.Context
import app.tileshell.apps.AppEntry
import app.tileshell.diag.Diagnostics
import app.tileshell.start.BackHistory

/**
 * When each app was last run, for the app list's Running section (INDEX Change Log 2026-09-21 item 8, amended 2026-09-22).
 *
 * Read from [UsageStatsManager], not from the shell's own launches, because "the last 5 ran programs"
 * means what it says: an app opened from a notification, from a link, or from another launcher was
 * still run. The shell already declares PACKAGE_USAGE_STATS and already reads usage events for Back on
 * Start and for the "New" caption, so this costs no new permission and no new checklist row.
 *
 * Without Usage access there is no Running section at all, and the diagnostics say so. That is the same
 * thing Back on Start does rather than guess, and a section built only from launches the shell itself
 * happened to see would be wrong in a way nobody could spot.
 */
object RecentRuns {

    /** How far back to look. A week is long enough that the section is populated on any real phone. */
    private const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Package name to the last time it was used, for every package with a record in the window.
     * Empty when Usage access is not granted.
     */
    fun lastUsed(context: Context, nowMs: Long = System.currentTimeMillis()): Map<String, Long> {
        if (!BackHistory.hasUsageAccess(context)) {
            Diagnostics.add("applist", "no Usage access: the app list has no recent section")
            return emptyMap()
        }
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, nowMs - WINDOW_MS, nowMs)
        }.getOrNull().orEmpty()
        // queryUsageStats returns several buckets per package; the latest one wins.
        val out = HashMap<String, Long>(stats.size)
        for (s in stats) {
            val at = s.lastTimeUsed
            if (at > 0 && at > (out[s.packageName] ?: 0L)) out[s.packageName] = at
        }
        return out
    }

    /** The shell's own package never appears in its own recent list. */
    fun isSelf(entry: AppEntry, context: Context): Boolean = entry.component.packageName == context.packageName
}

package app.tileshell.start

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import app.tileshell.apps.AppCatalog
import app.tileshell.apps.AppEntry
import app.tileshell.diag.Diagnostics

/**
 * Back on Start (R6 §4.1.1): resume the most recently used eligible app inside the Back history window,
 * which starts at the later of the last keyguard shown and boot (R6 §4.1.2; phase 01 Decisions).
 */
object BackHistory {
    private val alwaysExcluded = setOf(
        "com.android.systemui", "android", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.intentresolver",
    )

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun findTarget(context: Context, catalog: AppCatalog): AppEntry? {
        if (!hasUsageAccess(context)) {
            Diagnostics.add("back", "no Usage access: Back on Start does nothing")
            return null
        }
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val bootWall = now - SystemClock.elapsedRealtime()
        val events = usm.queryEvents(bootWall, now)
        val homePackages = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val imePackages = context.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
        var windowStart = bootWall
        var candidate: Pair<String, String>? = null
        var candidateTime = 0L
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.KEYGUARD_SHOWN -> if (e.timeStamp > windowStart) windowStart = e.timeStamp
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = e.packageName
                    val cls = e.className ?: ""
                    val excluded = pkg in alwaysExcluded || pkg in imePackages ||
                        (pkg != context.packageName && pkg in homePackages) ||
                        (pkg == context.packageName && cls.endsWith("StartActivity"))
                    if (!excluded && e.timeStamp >= candidateTime) {
                        candidate = pkg to cls
                        candidateTime = e.timeStamp
                    }
                }
            }
        }
        val found = candidate?.takeIf { candidateTime > windowStart }
        val entry = found?.let { (pkg, cls) ->
            catalog.apps.value.firstOrNull { it.component.packageName == pkg && it.component.className == cls }
                ?: catalog.firstForPackage(pkg)
        }
        Diagnostics.add("back", "window start=$windowStart candidate=$candidate at=$candidateTime -> ${entry?.component?.flattenToShortString()}")
        return entry
    }
}

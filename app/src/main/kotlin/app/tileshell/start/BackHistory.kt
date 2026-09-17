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
 * Back on Start (R6 §4.1.1): resume the most recently used eligible app since the last keyguard or boot
 * (R6 §4.1.2; phase 01 Decisions). The choice itself is [BackRules].
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
        val homeComponents = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .flatMap { r -> listOfNotNull(r.activityInfo.name, r.activityInfo.targetActivity).map { r.activityInfo.packageName to it } }
            .toSet()
        val imePackages = context.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
        val rules = BackRules(context.packageName, homeComponents, alwaysExcluded + imePackages)
        val stream = mutableListOf<BackRules.Event>()
        var keyguards = 0
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.KEYGUARD_SHOWN -> { stream += BackRules.KeyguardShown; keyguards++ }
                UsageEvents.Event.ACTIVITY_RESUMED -> stream += BackRules.Resumed(e.packageName, e.className ?: "")
            }
        }
        var picked: BackRules.Resumed? = null
        val entry = rules.pick(stream) { r ->
            (catalog.apps.value.firstOrNull { it.component.packageName == r.pkg && it.component.className == r.cls }
                ?: catalog.firstForPackage(r.pkg))?.also { picked = r }
        }
        Diagnostics.add("back", "events=${stream.size} keyguards=$keyguards candidate=$picked -> ${entry?.component?.flattenToShortString()}")
        return entry
    }
}

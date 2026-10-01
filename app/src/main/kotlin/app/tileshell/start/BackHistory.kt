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

    /**
     * The packages [BackRules] treats as system surfaces: they neither count nor end an unlock's continuation.
     *
     * Keyboards are among them — but not the shell, which has been a keyboard package itself since phase 05 (L14-2).
     * With the shell in this set every resume of Start was skipped: Start never ended the continuation, and the last
     * app used before a lock could not be reached by Back until some other app had been opened. Start is a HOME
     * activity, and [BackRules] already leaves those out by activity.
     */
    internal fun systemSurfaces(shellPackage: String, imePackages: Set<String>): Set<String> =
        alwaysExcluded + (imePackages - shellPackage)

    /**
     * Of the shell's own activities only Start (a HOME activity, which ends an unlock's continuation) and the shell's
     * apps in the catalog (Weather, Music, ...) are pages the user opens. The rest — the unlock prompt, a ring, a
     * permission page — are plumbing: they neither count nor end a continuation, and must not become a Back target
     * through the package fallback (gate review of L14-2: a ring over the lock screen made Back open an unrelated shell app).
     */
    internal fun shellPageCounts(cls: String, shellAppClasses: Set<String>): Boolean =
        cls.endsWith(".StartActivity") || cls in shellAppClasses

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
        val rules = BackRules(context.packageName, homeComponents, systemSurfaces(context.packageName, imePackages))
        val shellAppClasses = catalog.apps.value
            .filter { it.component.packageName == context.packageName }.map { it.component.className }.toSet()
        val stream = mutableListOf<BackRules.Event>()
        var keyguards = 0
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.KEYGUARD_SHOWN -> { stream += BackRules.KeyguardShown; keyguards++ }
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val cls = e.className ?: ""
                    if (e.packageName != context.packageName || shellPageCounts(cls, shellAppClasses)) {
                        stream += BackRules.Resumed(e.packageName, cls)
                    }
                }
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

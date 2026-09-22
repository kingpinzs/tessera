package app.tileshell.applist

/**
 * Which apps head the app list (Jeremy, 2026-09-21: "the program list should have the last 5 ran
 * programs and the last 3 installed programs"; amended 2026-09-22: "it has the Recently added which is
 * perfect but it also needs the last 3 running programs unless there is no running programs").
 * INDEX Change Log item 8.
 *
 * ### What "running" can honestly mean here
 *
 * No public API tells a launcher which apps have a live task: `getRunningAppProcesses` and
 * `getRunningTasks` return only the caller's own since Android 5, `getAppTasks` is the caller's own by
 * definition, and /proc is hidden. The one usable signal is [android.app.usage.UsageStatsManager], and
 * tracking ACTIVITY_RESUMED against ACTIVITY_STOPPED does NOT give "running" either — pressing Home
 * stops the activity you just left, so that definition empties the section the moment you look at it.
 *
 * So running means **run since the phone last booted**, which is the strongest claim the platform
 * actually supports and is what makes Jeremy's "unless there is no running programs" literally true: a
 * freshly booted phone has none, and the section is not drawn. The seven-day window this replaces could
 * never be empty on a phone anyone uses.
 *
 * Generic over the row type and holding no Android types at all, so the choosing is tested with plain
 * values — no device, no clock, no package manager, and no android.jar stub returning null for the
 * UserHandle and the ComponentName an AppEntry is made of.
 */
object AppSections {

    const val RUNNING_COUNT = 3
    const val ADDED_COUNT = 3

    /**
     * The [RUNNING_COUNT] apps run most recently since [sinceMs], most recent first.
     *
     * An app with no usage record, or one whose last run predates [sinceMs], is left out entirely
     * rather than sorted to the bottom: the section is a claim about what is running now, so it shows
     * two rows on a phone that has run two apps and nothing at all on one that has run none. Ties break
     * on the label so the order is stable between redraws — two apps can genuinely share a millisecond.
     */
    fun <T> running(
        apps: List<T>,
        lastUsed: Map<String, Long>,
        sinceMs: Long,
        packageOf: (T) -> String,
        labelOf: (T) -> String,
    ): List<T> =
        apps.mapNotNull { app -> lastUsed[packageOf(app)]?.takeIf { it >= sinceMs }?.let { app to it } }
            .sortedWith(compareByDescending<Pair<T, Long>> { it.second }.thenBy { labelOf(it.first) })
            .take(RUNNING_COUNT)
            .map { it.first }

    /**
     * The [ADDED_COUNT] most recently installed apps, newest first.
     *
     * Read off the install time the app catalog already carries, so this needs no store, no permission
     * and no scan, and is right the first time Start ever draws. An app with no install time (0 — the
     * platform reports that for some preinstalled entries) is left out: "recently installed" is a
     * claim, and a zero timestamp would make the oldest thing on the phone look like the newest.
     */
    fun <T> added(apps: List<T>, installedAt: (T) -> Long, labelOf: (T) -> String): List<T> =
        apps.filter { installedAt(it) > 0L }
            .sortedWith(compareByDescending<T> { installedAt(it) }.thenBy { labelOf(it) })
            .take(ADDED_COUNT)
}

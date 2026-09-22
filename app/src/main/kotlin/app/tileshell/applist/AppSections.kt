package app.tileshell.applist

/**
 * Which apps head the app list (Jeremy, 2026-09-21: "the program list should have the last 5 ran
 * programs and the last 3 installed programs"). INDEX Change Log item 8.
 *
 * Generic over the row type and holding no Android types at all, so the choosing is tested with plain
 * values — no device, no clock, no package manager, and no android.jar stub returning null for the
 * UserHandle and the ComponentName an AppEntry is made of.
 */
object AppSections {

    const val RECENT_COUNT = 5
    const val ADDED_COUNT = 3

    /**
     * The [RECENT_COUNT] most recently run apps, most recent first.
     *
     * An app with no usage record is not "recently run" and is left out entirely rather than sorted to
     * the bottom, so a phone with two apps ever opened shows two rows, not five padded out with
     * whatever happened to be installed. Ties break on the label so the order is stable between
     * redraws — two apps can genuinely share a millisecond.
     */
    fun <T> recent(
        apps: List<T>,
        lastUsed: Map<String, Long>,
        packageOf: (T) -> String,
        labelOf: (T) -> String,
    ): List<T> =
        apps.mapNotNull { app -> lastUsed[packageOf(app)]?.let { app to it } }
            .sortedWith(compareByDescending<Pair<T, Long>> { it.second }.thenBy { labelOf(it.first) })
            .take(RECENT_COUNT)
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

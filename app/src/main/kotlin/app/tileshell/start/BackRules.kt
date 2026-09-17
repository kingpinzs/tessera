package app.tileshell.start

/**
 * Back on Start's choice over the usage event stream (phase 01 Decisions, R6 §4.1.1 / §4.1.2), kept free of Android
 * types so the rules are unit-tested.
 *
 * - A keyguard clears the history ("since the last time your screen was locked").
 * - The activity that was showing when the keyguard appeared resumes again when the keyguard is dismissed; that is
 *   the same page coming back, not an app the user opened, so it doesn't count until another activity has resumed
 *   in between (Start, another app). System surfaces (SystemUI, permission controller, installer, resolver, IMEs)
 *   neither count nor end that continuation.
 * - HOME activities (Start, One UI Home, Settings' FallbackHome) are excluded by activity, not by package, so a
 *   package's other activities (Settings' pages) still count.
 * - The target is the most recent counted activity whose app is still launchable.
 */
class BackRules(
    private val shellPackage: String,
    private val homeComponents: Set<Pair<String, String>>,
    private val systemSurfacePackages: Set<String>,
) {
    sealed interface Event
    data class Resumed(val pkg: String, val cls: String) : Event
    data object KeyguardShown : Event

    fun <T : Any> pick(events: List<Event>, launch: (Resumed) -> T?): T? {
        val counted = mutableListOf<Resumed>()
        var last: Resumed? = null
        var showingAtLock: Resumed? = null
        for (e in events) {
            when (e) {
                KeyguardShown -> {
                    counted.clear()
                    showingAtLock = last
                }
                is Resumed -> {
                    if (e.pkg in systemSurfacePackages) continue
                    if (e == showingAtLock) continue
                    showingAtLock = null
                    last = e
                    if (!isHome(e)) counted += e
                }
            }
        }
        return counted.asReversed().firstNotNullOfOrNull(launch)
    }

    private fun isHome(e: Resumed): Boolean =
        (e.pkg to e.cls) in homeComponents || (e.pkg == shellPackage && e.cls.endsWith(".StartActivity"))
}

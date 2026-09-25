package app.tileshell.tiles.engine

/**
 * Which live-tile content, growth and count belong to which tile (phase 15 build task 0; C-1, T15-34, T15-35).
 *
 * Every app outside the shell is one package, and a tile standing for any of its activities stands for all of
 * it: a media session or a notification tells the shell its PACKAGE and nothing else, so package-keyed content
 * is exactly right there (phase 10 Q4). The shell is different. Music, Alarms & Clock, Calculator, Voice
 * Recorder and the rest are separate apps in the app list that all live in one package, so "the tiles of the
 * package that owns the session" would be every one of them — a pinned Calculator tile growing and flipping
 * with Music's now-playing face, and every in-APK tile carrying a count for a running timer's notification.
 *
 * So for the shell's own package the key is the COMPONENT, never the package:
 *  - a shell-owned media session is attributed by its Media3 session id (the platform tag Media3 writes is
 *    [SESSION_ID_PREFIX] + the id): `music` goes to Music's component; any other id (Voice Recorder's
 *    `recorder`, phase 17's `video`) goes to no tile at all, because those apps' tiles carry no now-playing
 *    face by their own Decisions (W10M's Voice Recorder and Movies & TV tiles showed none);
 *  - a tile standing for a shell component reads only content published under that component's key, and no
 *    notification count — the shell's own notifications (a running timer, a take, a missed alarm, a reminder)
 *    are recorded under the package as the listener sees them, and no in-APK tile shows them.
 *
 * Pure functions over strings, so the rules are proven by JVM tests (TileRoutingTest).
 */
object TileRouting {
    /** What media3-session 1.8.0 puts before the session id in the platform session's tag. */
    const val SESSION_ID_PREFIX = "androidx.media3.session.id."

    /** Music's activity: the one shell app whose tile carries a now-playing face. */
    const val MUSIC_ACTIVITY = "app.tileshell.music.MusicActivity"

    /** The Media3 id MusicService gives its session. */
    const val MUSIC_SESSION_ID = "music"

    /** The session id inside a platform session tag, or null for a tag Media3 did not write. */
    fun sessionId(tag: String?): String? = tag?.takeIf { it.startsWith(SESSION_ID_PREFIX) }?.removePrefix(SESSION_ID_PREFIX)

    /** Content key of a component: what a shell-owned app's tile reads and what a shell feed publishes under. */
    fun componentKey(pkg: String, cls: String): String = "cmp:$pkg/$cls"

    /**
     * Where a media session's face goes: a content key, or null when it goes to no tile. [shellPackage] is the
     * shell's own package name; a session of any other package keeps the package rule.
     */
    fun sessionContentKey(sessionPackage: String, tag: String?, shellPackage: String): String? =
        if (sessionPackage != shellPackage) {
            LiveTileEngine.packageKey(sessionPackage)
        } else if (sessionId(tag) == MUSIC_SESSION_ID) {
            componentKey(shellPackage, MUSIC_ACTIVITY)
        } else {
            null
        }

    /**
     * What grows while that session plays: a package name for another app (every tile of that package), the
     * component key for the shell's Music, or null.
     */
    fun sessionGrowthKey(sessionPackage: String, tag: String?, shellPackage: String): String? =
        if (sessionPackage != shellPackage) sessionPackage else sessionContentKey(sessionPackage, tag, shellPackage)

    /** The content key a tile standing for component [pkg]/[cls] reads. */
    fun tileContentKey(pkg: String, cls: String, shellPackage: String): String =
        if (pkg == shellPackage) componentKey(pkg, cls) else LiveTileEngine.packageKey(pkg)

    /** The growth key a tile standing for component [pkg]/[cls] answers to (see [sessionGrowthKey]). */
    fun tileGrowthKey(pkg: String, cls: String, shellPackage: String): String =
        if (pkg == shellPackage) componentKey(pkg, cls) else pkg

    /** The badge-store key whose count the tile shows, or null for a shell app's tile (no count from the shell's own notifications). */
    fun tileBadgeKey(pkg: String, shellPackage: String): String? = pkg.takeIf { it != shellPackage }

    // --- Phase 15 build task 4 (T15-40): the shell's own secondary tiles ------------------------------------------

    /** Alarms & Clock's activity: the owner of the shell's `timer.<id>` and `stopwatch` secondary tiles. */
    const val CLOCK_ACTIVITY = "app.tileshell.clock.ClockActivity"

    /** A pinned timer's tile id, and the stopwatch's (`LiveTileProtocol.TILE_ID_PATTERN` fits both). */
    const val TIMER_TILE_PREFIX = "timer."
    const val STOPWATCH_TILE_ID = "stopwatch"

    /**
     * The activity a tap on one of the SHELL'S OWN secondary tiles opens, by tile id — never "whichever in-APK app
     * sorts first" (`AppCatalog.firstForPackage`, the rule every other owner keeps: one package, one launcher
     * activity). Null for a shell tile id no shell app claims; the caller then falls back to the package rule.
     */
    fun shellSecondaryActivity(tileId: String): String? =
        if (tileId.startsWith(TIMER_TILE_PREFIX) || tileId == STOPWATCH_TILE_ID) CLOCK_ACTIVITY else null
}

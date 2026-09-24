package app.tileshell.tiles.engine

import app.tileshell.diag.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The producers of an app's OWN tile content (L11-1): each keeps its own slot, and [TileSourcePrecedence] picks one. */
enum class PackageSource(val tag: String) { MUSIC("music"), API("api"), NOTIFICATIONS("notifications") }

/**
 * Which producer an app's tile shows (L11-1; Jeremy, 2026-09-24: "(a)" — one place, in the engine): a playing media
 * session's face, then the Live Tile API queue, then notifications, then a paused track's face. This is phase 01's
 * Decision "preview = the API queue if the app uses it, else notifications" with the two music ends added, so nothing
 * changes for an app that plays nothing. It replaces the notification feed's one-off `previewOwners` check, and it is
 * why a notification update can no longer wipe a playing tile's face and its transport strip.
 */
object TileSourcePrecedence {
    /** A playing session: a front NowPlaying face whose `playing` is true (MusicRules.plan sets both from one state). */
    fun playing(content: TileContent?): Boolean = (content?.front as? TileFace.NowPlaying)?.playing == true

    fun resolve(sources: Map<PackageSource, TileContent>): Pair<PackageSource, TileContent>? {
        val music = sources[PackageSource.MUSIC]
        if (playing(music)) return PackageSource.MUSIC to music!!
        sources[PackageSource.API]?.let { return PackageSource.API to it }
        sources[PackageSource.NOTIFICATIONS]?.let { return PackageSource.NOTIFICATIONS to it }
        return music?.let { PackageSource.MUSIC to it }
    }
}

/**
 * Live tile content, keyed by a content key: "pkg:<packageName>" for app tiles, or a shell feed key
 * ("feed:photos", "feed:calendar", "feed:music", "feed:weather"). Feeds publish; Start renders.
 * Each tile runs its own timer in the UI (R3 A8: no global scheduler).
 *
 * An app's own "pkg:" content has three producers, so they publish through [publishPackage] into their own slots and
 * the entry Start renders is the one [TileSourcePrecedence] picks (L11-1). Every other key has one producer.
 */
object LiveTileEngine {
    private val state = MutableStateFlow<Map<String, TileContent>>(emptyMap())
    val content: StateFlow<Map<String, TileContent>> = state.asStateFlow()

    fun packageKey(pkg: String) = "pkg:$pkg"
    const val PHOTOS = "feed:photos"
    const val CALENDAR = "feed:calendar"
    const val MUSIC = "feed:music"
    const val WEATHER = "feed:weather"

    /**
     * Content with neither live faces nor a [TileContent.front] is nothing to show, so it clears the tile.
     * A front face on its own IS content, and has to survive: the Music tile while a song is playing and
     * the Photos tile in picture-frame mode both publish exactly one face — the one that REPLACES the
     * logo — and no flip faces at all, because neither of them may flip (INDEX Change Log 2026-09-21
     * items 3 and 4). Before this, a faces-less publish silently cleared the tile back to its logo.
     */
    private val packageSources = HashMap<String, MutableMap<PackageSource, TileContent>>()

    private fun empty(content: TileContent?) = content == null || (content.faces.isEmpty() && content.front == null)

    /** The packages that have content from any producer — what a producer that rescans may revisit (F-1). */
    @Synchronized
    fun packages(): Set<String> = packageSources.keys.toSet()

    private val forgetListeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    /** Told when a package is forgotten, so a producer that remembers its content drops it too (MusicFeed; F-2). */
    fun addForgetListener(listener: (String) -> Unit) { forgetListeners += listener }

    /**
     * The package as the shell knew it is gone (uninstalled, or reinstalled under another identity): every producer's
     * content for it goes, so a reinstall inherits nothing (phase 01's trust edge; the L11-1 fix review, F-2).
     */
    fun forgetPackage(pkg: String) {
        synchronized(this) {
            packageSources.remove(pkg)
            val key = packageKey(pkg)
            if (key in state.value) state.value = state.value - key
            Diagnostics.add("engine", "forget $key (every source)")
        }
        forgetListeners.forEach { it(pkg) }
    }

    /** One producer's content for an app's own tile; what shows is decided by [TileSourcePrecedence] (L11-1). */
    @Synchronized
    fun publishPackage(pkg: String, source: PackageSource, content: TileContent?) {
        // A key this arbiter never held (a secondary tile's `pkg:<owner>#<id>`, written by publish()) is never
        // removed by it: only a package that HAD slots loses its entry when they empty (the fix review, F-1).
        val had = packageSources.containsKey(pkg)
        val slots = packageSources.getOrPut(pkg) { mutableMapOf() }
        if (empty(content)) slots.remove(source) else slots[source] = content!!
        if (slots.isEmpty()) packageSources.remove(pkg)
        val winner = TileSourcePrecedence.resolve(slots)
        val key = packageKey(pkg)
        val next = state.value.toMutableMap()
        if (winner == null) { if (had) next.remove(key) } else next[key] = winner.second
        state.value = next
        val shows = winner?.let { (s, c) -> s.tag + if (s == PackageSource.MUSIC) (if (TileSourcePrecedence.playing(c)) " (playing)" else " (paused)") else "" } ?: "nothing"
        Diagnostics.add(
            "engine",
            "publish $key from=${source.tag} faces=${content?.faces?.size ?: 0} front=${content?.front != null} " +
                "source=${content?.sourceTag} sourceTime=${content?.sourceTimeMs} -> shows $shows",
        )
    }

    @Synchronized
    fun publish(key: String, content: TileContent?) {
        val next = state.value.toMutableMap()
        if (content == null || (content.faces.isEmpty() && content.front == null)) next.remove(key) else next[key] = content
        state.value = next
        Diagnostics.add(
            "engine",
            "publish $key faces=${content?.faces?.size ?: 0} front=${content?.front != null} " +
                "source=${content?.sourceTag} sourceTime=${content?.sourceTimeMs}",
        )
    }
}

package app.tileshell.start

/**
 * Phase 11's pure rules: which shortcuts become satellites, why a hold opens no burst, where the satellites
 * rest, and what a failed launch means. No Android types here, so every rule runs on the JVM (build tasks 1, 3
 * and 4; E3 asserts the exact ids [QuickSelection] predicts).
 */

/** One App Shortcut as the selection rule sees it. */
data class ShortcutCandidate(val id: String, val manifest: Boolean, val rank: Int, val enabled: Boolean)

/**
 * The selection rule (Decisions, Android's own launcher convention): enabled only; manifest before dynamic
 * (a screen that exists only conditionally is a dynamic shortcut, so the four a user learns stay put — C-9);
 * then rank ascending, then id; the first four.
 */
object QuickSelection {
    const val MAX_SATELLITES = 4

    fun select(all: List<ShortcutCandidate>): List<ShortcutCandidate> =
        all.filter { it.enabled }
            .sortedWith(compareBy<ShortcutCandidate>({ if (it.manifest) 0 else 1 }, { it.rank }, { it.id }))
            .take(MAX_SATELLITES)
}

/** The `[quick] no burst on <tileId>: <reason>` reasons, exactly as Decisions lists them (T11-21 / T11-22). */
object NoBurstReason {
    const val FOLDER = "folder"
    const val NO_APP = "no app"
    const val SECONDARY = "secondary tile"
    const val OFF_SCREEN = "off screen"
    const val NOT_HOST = "not the shortcut host"
    const val QUIET = "profile quiet"
    const val NO_SHORTCUTS = "no shortcuts"
    fun queryFailed(e: Throwable) = "query failed $e"
}

/** The `[quick] burst closed: <reason>` reasons (Decisions "Taps and events"). */
object CloseReason {
    const val TAP_ELSEWHERE = "tap elsewhere"
    const val DRAG = "drag"
    const val BACK = "back"
    const val HOME = "home"
    const val STOP = "stop"
    const val UNPIN = "unpin"
    const val RESIZE = "resize"
    const val REMOVED = "removed"
    const val LAUNCH = "launch"
    const val SHORTCUTS_CHANGED = "shortcuts changed"
}

/** Everything the decision asks Android, behind one seam so a JVM test can make it throw (E14's `query failed`). */
interface ShortcutPlatform<Q> {
    fun isShortcutHost(): Boolean
    fun isQuiet(query: Q): Boolean
    fun shortcuts(query: Q): List<ShortcutCandidate>
    /** `<pkg>/<activity>/<userId>`, the activity as `ComponentName.flattenToShortString()` (T11-12). */
    fun describe(query: Q): String
}

sealed interface QuickDecision {
    data class Show(val shown: List<ShortcutCandidate>, val total: Int) : QuickDecision
    data class None(val reason: String) : QuickDecision
}

object QuickRule {
    /**
     * The whole decision for one tile that has an activity to ask about. The host check comes first, so
     * `getShortcuts`' SecurityException never fires; a quiet profile is never queried; anything else that
     * throws is caught and becomes the reason. Writes `shortcuts for …` through [log] whenever a query ran.
     */
    fun <Q> decide(platform: ShortcutPlatform<Q>, query: Q, log: (String) -> Unit): QuickDecision {
        val all = try {
            if (!platform.isShortcutHost()) return QuickDecision.None(NoBurstReason.NOT_HOST)
            if (platform.isQuiet(query)) return QuickDecision.None(NoBurstReason.QUIET)
            platform.shortcuts(query)
        } catch (e: Exception) {
            return QuickDecision.None(NoBurstReason.queryFailed(e))
        }
        val shown = QuickSelection.select(all)
        log(shortcutsLine(platform.describe(query), all.size, shown.map { it.id }))
        return if (shown.isEmpty()) QuickDecision.None(NoBurstReason.NO_SHORTCUTS) else QuickDecision.Show(shown, all.size)
    }

    fun shortcutsLine(described: String, total: Int, shownIds: List<String>): String =
        "shortcuts for $described: $total (${shownIds.size} shown" +
            (if (shownIds.isEmpty()) ")" else ": ${shownIds.joinToString(",")})")
}

/** A rectangle in page pixels (left, top, right, bottom). */
data class QRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) / 2f
    val cy: Float get() = (t + b) / 2f
    fun intersects(o: QRect): Boolean = l < o.r && o.l < r && t < o.b && o.t < b
    fun offset(dx: Float, dy: Float) = QRect(l + dx, t + dy, r + dx, b + dy)
    fun union(o: QRect) = QRect(minOf(l, o.l), minOf(t, o.t), maxOf(r, o.r), maxOf(b, o.b))
}

enum class Arrangement { CORNER, LINE_ABOVE, LINE_BELOW }

/** One satellite at rest: its square and its label's box, both in page px. */
data class SatelliteRest(val square: QRect, val label: QRect) {
    /** A tap on the label counts as the satellite (T11-26). */
    val hit: QRect get() = square.union(label)
}

data class BurstLayout(val arrangement: Arrangement, val satellites: List<SatelliteRest>)

/**
 * Where the satellites rest (Decisions "Arrangement", P4 design, H4 / H5), decided ONCE when the burst opens,
 * against the held tile's edit-mode rest rectangle (T11-27).
 *
 * @param tile the held tile's drawn rectangle at rest in edit mode
 * @param page the area satellites and labels must stay inside: below the drawn status bar, above the bottom
 *   tile row's top minus one gutter, inside the grid margins
 * @param inBottomRow the held tile is in the bottom tile row, which always takes the line above it
 */
object QuickGeometry {
    fun layout(
        tile: QRect,
        page: QRect,
        count: Int,
        satellitePx: Float,
        standOffPx: Float,
        gutterPx: Float,
        labelHeightPx: Float,
        inBottomRow: Boolean,
    ): BurstLayout {
        val k = count.coerceIn(0, QuickSelection.MAX_SATELLITES)
        if (!inBottomRow) {
            val corner = corner(tile, page, k, satellitePx, standOffPx, gutterPx, labelHeightPx)
            if (corner.none { it.square.intersects(tile) || it.label.intersects(tile) }) return BurstLayout(Arrangement.CORNER, corner)
        }
        val above = line(tile, page, k, satellitePx, gutterPx, labelHeightPx, above = true)
        val aboveFits = above.all { it.label.t >= page.t - 0.5f } && above.none { it.square.intersects(tile) || it.label.intersects(tile) }
        if (inBottomRow || aboveFits) return BurstLayout(Arrangement.LINE_ABOVE, clampLine(above, page, tile, above = true))
        val below = line(tile, page, k, satellitePx, gutterPx, labelHeightPx, above = false)
        return BurstLayout(Arrangement.LINE_BELOW, clampLine(below, page, tile, above = false))
    }

    /** Label box: the satellite's width plus HALF a gutter each side (T11-26), on the satellite's outer side. */
    private fun labelBox(sq: QRect, gutterPx: Float, labelHeightPx: Float, above: Boolean): QRect =
        if (above) QRect(sq.l - gutterPx / 2f, sq.t - labelHeightPx, sq.r + gutterPx / 2f, sq.t)
        else QRect(sq.l - gutterPx / 2f, sq.b, sq.r + gutterPx / 2f, sq.b + labelHeightPx)

    /** Corner i sits diagonally outside tile corner i: top-left, top-right, bottom-left, bottom-right. */
    private fun corner(tile: QRect, page: QRect, k: Int, s: Float, d: Float, g: Float, lh: Float): List<SatelliteRest> =
        (0 until k).map { i ->
            val left = i % 2 == 0
            val top = i < 2
            val l = if (left) tile.l - d - s else tile.r + d
            val t = if (top) tile.t - d - s else tile.b + d
            var sq = QRect(l, t, l + s, t + s)
            var label = labelBox(sq, g, lh, above = top)
            // Moved inward along the offending axis until square and label fit (Decisions "Clamping").
            val both = sq.union(label)
            val dx = when {
                both.l < page.l -> page.l - both.l
                both.r > page.r -> page.r - both.r
                else -> 0f
            }
            val dy = when {
                both.t < page.t -> page.t - both.t
                both.b > page.b -> page.b - both.b
                else -> 0f
            }
            sq = sq.offset(dx, dy)
            label = label.offset(dx, dy)
            SatelliteRest(sq, label)
        }

    /** Four slots in one row, one gutter apart, centred on the tile; fewer than four take the first k slots. */
    private fun line(tile: QRect, page: QRect, k: Int, s: Float, g: Float, lh: Float, above: Boolean): List<SatelliteRest> {
        val rowW = QuickSelection.MAX_SATELLITES * s + (QuickSelection.MAX_SATELLITES - 1) * g
        val left0 = tile.cx - rowW / 2f
        val t = if (above) tile.t - g - s else tile.b + g
        return (0 until k).map { i ->
            val l = left0 + i * (s + g)
            val sq = QRect(l, t, l + s, t + s)
            SatelliteRest(sq, labelBox(sq, g, lh, above))
        }
    }

    /** The whole row moves inward together, so its one-gutter spacing survives the clamp. */
    private fun clampLine(row: List<SatelliteRest>, page: QRect, tile: QRect, above: Boolean): List<SatelliteRest> {
        if (row.isEmpty()) return row
        val all = row.map { it.hit }.reduce { a, b -> a.union(b) }
        val dx = when {
            all.l < page.l -> page.l - all.l
            all.r > page.r -> page.r - all.r
            else -> 0f
        }
        // Vertically a line only ever moves away from the tile, never onto it.
        val dy = when {
            above && all.b > page.b -> page.b - all.b
            !above && all.t < page.t -> page.t - all.t
            else -> 0f
        }.let { if (above) minOf(it, 0f) else maxOf(it, 0f) }
        return row.map { SatelliteRest(it.square.offset(dx, dy), it.label.offset(dx, dy)) }
    }
}

/**
 * The satellites' motion (Decisions "Motion", Jeremy's dampingRatio 0.65; stiffness 1500 agent, H2): what the
 * shell logs on its own clock (T11-5, T11-29, C-31) and what E6 asserts.
 */
object QuickMotion {
    const val DAMPING = 0.65f
    const val STIFFNESS = 1500f

    /** Scale 0.5 -> 1 and alpha 0 -> 1 ride the first half of the travel. */
    fun openAlpha(progress: Float): Float = (2f * progress).coerceIn(0f, 1f)
    fun openScale(progress: Float): Float = 0.5f + 0.5f * (2f * progress).coerceIn(0f, 1f)

    /** Closing: the same spring back; alpha reaches 0 at the halfway point. [back] is the fraction travelled back. */
    fun closeAlpha(back: Float): Float = (1f - 2f * back).coerceIn(0f, 1f)

    data class Sample(val tMs: Float, val progress: Float)

    data class OpenStats(val peakMs: Float, val overshootPct: Float, val settleMs: Float, val frames: Int, val maxGapMs: Float)

    /**
     * From the frames the open actually drew: peak = the frame of the largest progress, overshoot = how far
     * past rest it went, settle = the first frame from which every later frame is within 1 px of rest for the
     * longest travel (T11-29: the spring passes THROUGH rest at ≈77 and ≈184 ms before it settles).
     */
    fun openStats(samples: List<Sample>, travelPx: Float): OpenStats {
        val peak = samples.maxByOrNull { it.progress } ?: return OpenStats(0f, 0f, 0f, 0, 0f)
        val tolerance = if (travelPx > 0f) 1f / travelPx else 0f
        var settle = samples.last().tMs
        for (i in samples.indices.reversed()) {
            if (kotlin.math.abs(1f - samples[i].progress) <= tolerance) settle = samples[i].tMs else break
        }
        return OpenStats(peak.tMs, (peak.progress - 1f) * 100f, settle, samples.size, maxGap(samples))
    }

    fun maxGap(samples: List<Sample>): Float =
        samples.zipWithNext { a, b -> b.tMs - a.tMs }.maxOrNull() ?: 0f

    /** Closing: alpha0 = the first frame whose alpha is 0 (the satellites are gone), which is also its settle. */
    fun closeAlpha0(samples: List<Sample>): Float =
        samples.firstOrNull { closeAlpha(it.progress) <= 0f }?.tMs ?: (samples.lastOrNull()?.tMs ?: 0f)
}

/** How a satellite's `startShortcut` ended (Decisions "Satellite tap", T11-24). */
sealed interface QuickLaunchOutcome {
    data object Ok : QuickLaunchOutcome
    data class Failed(val error: Throwable) : QuickLaunchOutcome
}

object QuickLaunch {
    /**
     * A shortcut removed, disabled or unstartable between open and tap launches nothing: these three are
     * what `LauncherApps.startShortcut` throws for it, and each becomes [QuickLaunchOutcome.Failed] so the
     * caller plays Start's entrance instead of leaving Start on the exit's last frame.
     */
    fun run(start: () -> Unit): QuickLaunchOutcome = try {
        start()
        QuickLaunchOutcome.Ok
    } catch (e: android.content.ActivityNotFoundException) {
        QuickLaunchOutcome.Failed(e)
    } catch (e: IllegalStateException) {
        QuickLaunchOutcome.Failed(e)
    } catch (e: SecurityException) {
        QuickLaunchOutcome.Failed(e)
    }
}

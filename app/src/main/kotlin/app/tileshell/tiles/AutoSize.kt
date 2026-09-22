package app.tileshell.tiles

import kotlin.math.pow

/**
 * Tile size follows use (Jeremy, 2026-09-21: "the more something gets used the bigger the tile is the
 * less it gets used the smaller the tile gets"). INDEX Change Log item 1.
 *
 * Everything here is pure, so the policy is testable without a device and without a clock. The store
 * that counts the uses is [UseCounts]; this file only decides what the sizes should be.
 *
 * Four things the policy has to get right, and how:
 *
 *  - **"less used gets smaller" needs forgetting, not just counting.** A raw count only ever grows, so
 *    an app used daily a year ago would outrank one used daily this week for ever. Every score decays
 *    by half every [HALF_LIFE_MS]; a tile nobody opens shrinks on its own.
 *  - **A quiet phone must not end up covered in wide tiles.** Sizes come from each tile's SHARE of the
 *    busiest tile's score, not from an absolute number of launches.
 *  - **A tile must not flip size every time the numbers wobble.** Growing and shrinking use different
 *    thresholds ([GROW_WIDE] vs [SHRINK_WIDE]), so a tile sitting on a boundary keeps the size it has.
 *  - **It must not touch a fresh install.** Until the busiest tile has [MIN_EVIDENCE] worth of use,
 *    the layout is left exactly as the default put it.
 */
object AutoSize {

    /** A score halves every fortnight, so "stopped using it" shows up in weeks, not months. */
    const val HALF_LIFE_MS: Long = 14L * 24 * 60 * 60 * 1000

    /** Below this much decayed use on the busiest tile, the policy does nothing at all. */
    const val MIN_EVIDENCE = 5f

    /** Share of the busiest tile's score at which a tile grows to, or shrinks from, each size. */
    const val GROW_WIDE = 0.60f
    const val SHRINK_WIDE = 0.45f
    const val GROW_MEDIUM = 0.20f
    const val SHRINK_MEDIUM = 0.12f

    /** W10M's own Start never had a wall of wide tiles; the widest are the few you really live in. */
    const val MAX_WIDE = 2

    /** [score] as it stands [ageMs] after it was last written. */
    fun decay(score: Float, ageMs: Long, halfLifeMs: Long = HALF_LIFE_MS): Float {
        if (score <= 0f || ageMs <= 0L) return score.coerceAtLeast(0f)
        return score * 0.5f.toDouble().pow(ageMs.toDouble() / halfLifeMs).toFloat()
    }

    /**
     * The size [share] argues for, given the size the tile already has. The two thresholds per step are
     * the hysteresis: a tile only grows once it is clearly above, and only shrinks once clearly below.
     */
    fun sizeFor(share: Float, current: TileSize): TileSize = when (current) {
        TileSize.WIDE -> if (share < SHRINK_WIDE) sizeFor(share, TileSize.MEDIUM) else TileSize.WIDE
        TileSize.MEDIUM -> when {
            share >= GROW_WIDE -> TileSize.WIDE
            share < SHRINK_MEDIUM -> TileSize.SMALL
            else -> TileSize.MEDIUM
        }
        TileSize.SMALL -> when {
            share >= GROW_WIDE -> TileSize.WIDE
            share >= GROW_MEDIUM -> TileSize.MEDIUM
            else -> TileSize.SMALL
        }
    }

    /** Only tiles that stand for something a person opens take part; the rest keep their own size. */
    fun participates(key: TileKey): Boolean =
        key is TileKey.SlotTile || key is TileKey.AppTile || key is TileKey.SecondaryTile

    /**
     * The grid order with every participating, non-[manual] tile resized to what its use argues for.
     * Returns [order] unchanged when there is not enough evidence yet.
     */
    fun apply(order: List<Sized>, scores: Map<String, Float>, manual: Set<String>): List<Sized> {
        val eligible = order.filter { participates(it.key) && it.key.id !in manual }
        if (eligible.isEmpty()) return order
        val top = eligible.maxOf { scores[it.key.id] ?: 0f }
        if (top < MIN_EVIDENCE) return order

        val wanted = eligible.associate { sized ->
            sized.key.id to sizeFor((scores[sized.key.id] ?: 0f) / top, sized.size)
        }
        // The wide cap: when more tiles earn WIDE than the cap allows, the highest-scoring ones keep it.
        val wide = wanted.filterValues { it == TileSize.WIDE }.keys
            .sortedByDescending { scores[it] ?: 0f }
        val demoted = wide.drop(MAX_WIDE).toSet()

        return order.map { sized ->
            val size = wanted[sized.key.id] ?: return@map sized
            val capped = if (sized.key.id in demoted) TileSize.MEDIUM else size
            if (capped == sized.size) sized else sized.copy(size = capped)
        }
    }
}

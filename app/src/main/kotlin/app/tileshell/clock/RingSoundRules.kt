package app.tileshell.clock

import app.tileshell.media.UriAccessRules

/**
 * What the ring plays when a sound will not play (ledger L18-3, the adversarial review's N11). A sound that opens but
 * will not decode — a zero-byte .mp3 — makes the player throw as it starts, and one can also fail while it plays; the
 * alarm then vibrated SILENTLY with only a log line. Now, whatever the sound was:
 *  - the chosen sound cannot play → the DEFAULT sound plays, and the line says so (`… -> default sound`, then
 *    `ring <id> sound=default (fallback)`);
 *  - the default cannot play either (or it was the one that failed) → the ring vibrates only, and the line says so
 *    (`… -> vibrate only`).
 * Pure, over any source and player types: `RingSoundRulesTest`. RingService hands it the platform's player
 * (`AlarmSoundWiringScanTest` holds that form). A line carries the id and the class of what failed, never a path.
 */
object RingSoundRules {
    /** [player] is what started, or null: nothing plays and the ring only vibrates. [lines] are the diagnostics lines, in order. */
    data class Started<P>(val player: P?, val lines: List<String>)

    /**
     * Starts [chosen] with [open] (which throws when the source will not play); when it throws, [fallback]. [default]
     * is asked only then.
     */
    fun <S, P : Any> start(id: String, chosen: S, default: () -> S, open: (S) -> P): Started<P> {
        val why = try {
            return Started(open(chosen), emptyList())
        } catch (e: Throwable) {
            UriAccessRules.className(e.javaClass.simpleName.orEmpty())
        }
        return fallback(id, chosen, why, default, open)
    }

    /**
     * [failed] could not play — it threw a [why] (a class name) as it started, or reported `error <what> <extra>` while
     * playing: the default sound, unless [failed] was the default; then nothing, said.
     */
    fun <S, P : Any> fallback(id: String, failed: S, why: String, default: () -> S, open: (S) -> P): Started<P> {
        val lines = mutableListOf<String>()
        val fallback = try {
            default()
        } catch (e: Throwable) {
            lines += "ring $id sound could not play ($why) -> default sound"
            return Started(null, lines + vibrateOnly(id, e))
        }
        if (fallback == failed) return Started(null, listOf("ring $id default sound could not play ($why) -> vibrate only"))
        lines += "ring $id sound could not play ($why) -> default sound"
        return try {
            Started(open(fallback), lines + "ring $id sound=default (fallback)")
        } catch (e: Throwable) {
            Started(null, lines + vibrateOnly(id, e))
        }
    }

    private fun vibrateOnly(id: String, e: Throwable) = "ring $id default sound could not play (${UriAccessRules.className(e.javaClass.simpleName.orEmpty())}) -> vibrate only"
}

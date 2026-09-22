package app.tileshell.ime.engine

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Word Flow (phase 05 build task 3; D1 l.1503–1506: "Swipe from the first letter of the word, and draw
 * a path from letter to letter. Lift your finger after the last letter."): the finger's path in pixels
 * in, ranked candidate words out, best first, for the strip to show on lift (R6 §2.4.5, bold first).
 *
 * The method is SHARK2 (Kristensson & Zhai, UIST 2004), written here against [LetterLayout] — no
 * FlorisBoard code is carried over. Each candidate word has an IDEAL path: its key centres in order,
 * a doubled letter being one visit, since the finger does not loop for "hello". The user's path and
 * every ideal path are resampled to [SAMPLES] points at equal arc-length steps and compared on two
 * channels:
 *
 * - location — mean point-to-point distance in key pitches, which says whether the finger actually
 *   passed over the word's keys;
 * - shape — the same after both paths are scaled into a unit box about their centre, which says
 *   whether the finger drew the word's figure regardless of where or how large.
 *
 * The score is a log-likelihood, `(location/σ)² + (shape/σ)² + w·log10(rank+1)`, so among words the
 * finger fits equally well (a doubled-letter word and its single-letter twin draw the same path) the
 * commoner wins, while a rarer word that fits much better still wins.
 *
 * 150k words are far too many to score per lift, so three prunes run first, all cheap: a word must
 * start on one of the keys nearest the path's first sample and end on one nearest its last (that
 * pair indexes into [byEnds], built once per layout); its ideal path length must be near the finger's;
 * and each start/end bucket is walked commonest first and capped, so a hot pair like s…s still costs
 * bounded time.
 */
class SwipeDecoder(
    private val lexicon: Lexicon,
    private val layout: LetterLayout,
    private val userDictionary: UserDictionary? = null,
) {

    /** Ideal path length in pixels per word rank; NaN where the word has no key on this layout. */
    private val idealLength = FloatArray(lexicon.size)

    /** Ranks, ascending, of the words with a given first/last key: index `first.code * 128 + last.code`. */
    private val byEnds = arrayOfNulls<IntArray>(128 * 128)

    // Scratch, reused per decode: the engine runs on every lift and must not churn the heap.
    private val userX = FloatArray(SAMPLES)
    private val userY = FloatArray(SAMPLES)
    private val userShapeX = FloatArray(SAMPLES)
    private val userShapeY = FloatArray(SAMPLES)
    private val wordX = FloatArray(SAMPLES)
    private val wordY = FloatArray(SAMPLES)
    private val wordShapeX = FloatArray(SAMPLES)
    private val wordShapeY = FloatArray(SAMPLES)
    private var visitX = FloatArray(64)
    private var visitY = FloatArray(64)

    init {
        buildIndex()
    }

    /**
     * Candidate words for [path], best first, at most [maxResults]. Empty when the path is empty or
     * starts or ends nowhere near a key; a path of one point is a tap, and comes back with whatever
     * one-letter words sit under it.
     */
    fun decode(path: List<KeyPoint>, maxResults: Int = 8): List<String> {
        if (path.isEmpty() || maxResults <= 0) return emptyList()
        val n = path.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var count = 0
        for (p in path) {
            if (count > 0 && p.x == xs[count - 1] && p.y == ys[count - 1]) continue
            xs[count] = p.x
            ys[count] = p.y
            count++
        }
        val length = pathLength(xs, ys, count)
        val starts = layout.nearest(xs[0], ys[0], EXTREMITY_KEYS, EXTREMITY_MAX_PITCHES)
        val ends = layout.nearest(xs[count - 1], ys[count - 1], EXTREMITY_KEYS, EXTREMITY_MAX_PITCHES)
        if (starts.isEmpty() || ends.isEmpty()) return emptyList()

        resample(xs, ys, count, userX, userY)
        normalise(userX, userY, userShapeX, userShapeY)

        val bestScore = FloatArray(maxResults) { Float.MAX_VALUE }
        val bestWord = arrayOfNulls<String>(maxResults)
        var kept = 0
        fun offer(word: String, score: Float) {
            if (kept == maxResults && score >= bestScore[kept - 1]) return
            var i = if (kept < maxResults) kept++ else kept - 1
            while (i > 0 && bestScore[i - 1] > score) {
                bestScore[i] = bestScore[i - 1]
                bestWord[i] = bestWord[i - 1]
                i--
            }
            bestScore[i] = score
            bestWord[i] = word
        }

        for (s in starts) for (e in ends) {
            if (s.code >= 128 || e.code >= 128) continue
            val bucket = byEnds[s.code * 128 + e.code] ?: continue
            var considered = 0
            for (rank in bucket) {
                if (!lengthFits(idealLength[rank], length)) continue
                if (++considered > BUCKET_CAP) break
                val visits = visits(lexicon.wordAt(rank))
                offer(lexicon.wordAt(rank), score(visits, rank))
            }
        }
        userDictionary?.let { dict ->
            for (word in dict.words) {
                val visits = visits(word)
                if (visits == 0) continue
                val first = layout.nearest(visitX[0], visitY[0], 1).firstOrNull()
                val last = layout.nearest(visitX[visits - 1], visitY[visits - 1], 1).firstOrNull()
                if (first !in starts || last !in ends) continue
                if (!lengthFits(pathLength(visitX, visitY, visits), length)) continue
                offer(word, score(visits, Suggester.USER_WORD_RANK))
            }
        }
        return List(kept) { bestWord[it]!! }
    }

    // ---- scoring ----

    /** Score for the word whose [count] ideal visits are in [visitX]/[visitY]; lower is better. */
    private fun score(count: Int, rank: Int): Float {
        resample(visitX, visitY, count, wordX, wordY)
        var location = 0f
        for (i in 0 until SAMPLES) {
            val dx = (wordX[i] - userX[i]) / layout.pitchX
            val dy = (wordY[i] - userY[i]) / layout.pitchY
            location += sqrt(dx * dx + dy * dy)
        }
        location /= SAMPLES
        normalise(wordX, wordY, wordShapeX, wordShapeY)
        var shape = 0f
        for (i in 0 until SAMPLES) {
            val dx = wordShapeX[i] - userShapeX[i]
            val dy = wordShapeY[i] - userShapeY[i]
            shape += sqrt(dx * dx + dy * dy)
        }
        shape /= SAMPLES
        val l = location / LOCATION_SIGMA
        val s = shape / SHAPE_SIGMA
        return l * l + s * s + FREQUENCY_WEIGHT * log10(rank + 1f)
    }

    /**
     * The word's key visits into [visitX]/[visitY]: one per letter that has a key, a run of the same
     * letter collapsed to one. Returns the visit count (0 when no letter is on the layout).
     */
    private fun visits(word: String): Int {
        var count = 0
        var prev = '\u0000'
        for (ch in word) {
            val c = Character.toLowerCase(ch)
            if (c == prev) continue
            val centre = layout.centres[c] ?: continue
            if (count == visitX.size) {
                visitX = visitX.copyOf(count * 2)
                visitY = visitY.copyOf(count * 2)
            }
            visitX[count] = centre.x
            visitY[count] = centre.y
            count++
            prev = c
        }
        return count
    }

    private fun lengthFits(ideal: Float, user: Float): Boolean =
        !ideal.isNaN() && abs(ideal - user) <= max(LENGTH_SLACK_PITCHES * layout.pitchX, LENGTH_SLACK_RATIO * max(ideal, user))

    // ---- the index ----

    private fun buildIndex() {
        val first = CharArray(lexicon.size)
        val last = CharArray(lexicon.size)
        val perPair = IntArray(128 * 128)
        for (rank in 0 until lexicon.size) {
            val count = visits(lexicon.wordAt(rank))
            if (count == 0) {
                idealLength[rank] = Float.NaN
                continue
            }
            idealLength[rank] = pathLength(visitX, visitY, count)
            val f = layout.nearest(visitX[0], visitY[0], 1)[0]
            val l = layout.nearest(visitX[count - 1], visitY[count - 1], 1)[0]
            if (f.code >= 128 || l.code >= 128) {
                idealLength[rank] = Float.NaN
                continue
            }
            first[rank] = f
            last[rank] = l
            perPair[f.code * 128 + l.code]++
        }
        val fill = IntArray(128 * 128)
        for (rank in 0 until lexicon.size) {
            if (idealLength[rank].isNaN()) continue
            val pair = first[rank].code * 128 + last[rank].code
            val bucket = byEnds[pair] ?: IntArray(perPair[pair]).also { byEnds[pair] = it }
            bucket[fill[pair]++] = rank
        }
    }

    companion object {
        /** Points per resampled path. Enough to keep a five-key word's corners; cheap enough for thousands of candidates. */
        const val SAMPLES = 40

        /** How many keys around the path's first and last sample may start or end the word. */
        const val EXTREMITY_KEYS = 3
        const val EXTREMITY_MAX_PITCHES = 1.5f

        /** Candidates scored per start/end bucket, commonest first. */
        const val BUCKET_CAP = 2500

        /** A finger cuts corners and wobbles: its path may differ from the ideal by this much. */
        const val LENGTH_SLACK_PITCHES = 1f
        const val LENGTH_SLACK_RATIO = 0.3f

        /** Channel widths: the typical deviation of a finger that meant this word. */
        const val LOCATION_SIGMA = 0.35f
        const val SHAPE_SIGMA = 0.1f
        const val FREQUENCY_WEIGHT = 0.5f

        internal fun pathLength(xs: FloatArray, ys: FloatArray, count: Int): Float {
            var length = 0f
            for (i in 1 until count) {
                val dx = xs[i] - xs[i - 1]
                val dy = ys[i] - ys[i - 1]
                length += sqrt(dx * dx + dy * dy)
            }
            return length
        }

        /** [count] points of a polyline into exactly `outX.size` points at equal arc-length steps. */
        internal fun resample(xs: FloatArray, ys: FloatArray, count: Int, outX: FloatArray, outY: FloatArray) {
            val n = outX.size
            val total = pathLength(xs, ys, count)
            if (count == 1 || total <= 0f) {
                outX.fill(xs[0])
                outY.fill(ys[0])
                return
            }
            val step = total / (n - 1)
            outX[0] = xs[0]
            outY[0] = ys[0]
            var k = 1
            var walked = 0f
            for (i in 1 until count) {
                val dx = xs[i] - xs[i - 1]
                val dy = ys[i] - ys[i - 1]
                val seg = sqrt(dx * dx + dy * dy)
                if (seg <= 0f) continue
                while (k < n - 1 && walked + seg >= k * step) {
                    val t = (k * step - walked) / seg
                    outX[k] = xs[i - 1] + dx * t
                    outY[k] = ys[i - 1] + dy * t
                    k++
                }
                walked += seg
            }
            // Rounding can leave the last point or two unfilled; they belong at the path's end anyway.
            while (k < n) {
                outX[k] = xs[count - 1]
                outY[k] = ys[count - 1]
                k++
            }
        }

        /** Scale into a unit box (longest side 1) centred on the box's middle. */
        internal fun normalise(xs: FloatArray, ys: FloatArray, outX: FloatArray, outY: FloatArray) {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in xs.indices) {
                minX = min(minX, xs[i]); maxX = max(maxX, xs[i])
                minY = min(minY, ys[i]); maxY = max(maxY, ys[i])
            }
            val side = max(max(maxX - minX, maxY - minY), 1e-3f)
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            for (i in xs.indices) {
                outX[i] = (xs[i] - cx) / side
                outY[i] = (ys[i] - cy) / side
            }
        }
    }
}

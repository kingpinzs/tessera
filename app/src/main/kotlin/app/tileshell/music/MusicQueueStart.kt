package app.tileshell.music

import app.tileshell.files.FilesIntents

/**
 * Where a controller's queue starts once [MusicItemRule] has decided its items (ledger L18-4; a trust rule).
 *
 * A controller's "set these items, start at index i, position p" counts the list IT sent. The session then drops an
 * item the library does not have, rebuilds one from its id, or answers a search with a whole queue — so the list the
 * player is handed is not the list the index was for. Media3's own default (`MediaSession.Callback.onSetMediaItems`,
 * read from the 1.9.0 bytecode) hands the request's index and position on untouched beside whatever `onAddMediaItems`
 * returned; and for a set without a reset Media3 fills in the player's CURRENT index, which a new, shorter list need not
 * have either. ExoPlayer refuses an index its list does not have (`IllegalSeekPositionException`) only AFTER it has
 * taken the new list and counted an operation it will never acknowledge: the player is wedged, for the shell's own
 * Music screens too. `[<a real library id>, "x"]` with index 1, from any app, did it.
 *
 * So the session never hands the player a start it did not work out here:
 *  - nothing left of the request (every item dropped, or no item at all): [Start.Refuse] — the request fails BEFORE the
 *    player is touched, and what is playing goes on playing;
 *  - no index asked ([INDEX_UNSET]): the default start stays the default start;
 *  - the item the caller pointed at survived: its place in the FINAL list (what came before it may have been dropped or
 *    expanded), with the caller's position when it became exactly one item, else from its beginning;
 *  - anything else — the pointed-at item dropped, an index the request's own list does not have, a negative one: the
 *    first item, position 0.
 * Whatever is asked, the answer is a refusal, the default start, or an index the final list has (`MusicQueueStartTest`).
 *
 * Pure. `media/UriAccessWiringScanTest` holds the service to making every list-with-a-start from this rule's answer.
 */
object MusicQueueStart {
    /** `C.INDEX_UNSET` and `C.TIME_UNSET` (pinned to Media3's by a test). */
    const val INDEX_UNSET = -1
    const val TIME_UNSET = Long.MIN_VALUE + 1

    /** The longest a search query is in a diagnostics line. */
    const val MAX_LINE_QUERY = 80

    sealed interface Start {
        /** Hand the player the final list with this index (in range, or [INDEX_UNSET]) and position (0 or more, or [TIME_UNSET]). */
        data class At(val index: Int, val positionMs: Long) : Start

        /** Nothing to play: fail the request; the player is not touched. */
        data object Refuse : Start
    }

    /**
     * A set (`setMediaItem(s)` of a Media3 controller, a play-from request of a legacy one).
     *
     * @param counts for each item of the request, in order, how many items it became: 0 dropped, 1 kept or rebuilt,
     *   more a search's queue
     * @param startIndex the index the session was handed, counted in the REQUEST's list, or [INDEX_UNSET]
     * @param startPositionMs the position the session was handed, or [TIME_UNSET]
     */
    fun set(counts: List<Int>, startIndex: Int, startPositionMs: Long): Start {
        if (total(counts) <= 0L) return Start.Refuse
        // Media3 sets a list that has no index with a reset, and reads no position for it.
        if (startIndex == INDEX_UNSET) return Start.At(INDEX_UNSET, TIME_UNSET)
        val first = Start.At(0, 0L)
        if (startIndex !in counts.indices || counts[startIndex] == 0) return first
        var before = 0L
        for (i in 0 until startIndex) before += counts[i]
        if (before > Int.MAX_VALUE) return first
        val position = when {
            counts[startIndex] != 1 -> 0L
            startPositionMs == TIME_UNSET -> TIME_UNSET
            startPositionMs < 0L -> 0L
            else -> startPositionMs
        }
        return Start.At(before.toInt(), position)
    }

    /**
     * A search's own queue — the library's, or (phase 20) a station queue of the favourites: it starts at its match
     * when the queue has that index, else at its first item.
     */
    fun search(queueSize: Int, matchIndex: Int): Start {
        if (queueSize <= 0) return Start.Refuse
        return Start.At(if (matchIndex in 0 until queueSize) matchIndex else 0, 0L)
    }

    /**
     * A queue of the shell's OWN making (phase 20: a station queue, a home-server queue), some of whose items the
     * item's own builder refused ([kept] is false for those): where it starts in the list of the kept ones, or null
     * when the item the person asked for is itself not kept — nothing is played in its place.
     */
    fun own(kept: List<Boolean>, startIndex: Int): Int? {
        if (startIndex !in kept.indices || !kept[startIndex]) return null
        return (0 until startIndex).count { kept[it] }
    }

    /**
     * An add or a replace (index-less or indexed — the callback is not told the index, and ExoPlayer bounds an add's
     * index by its own list): allowed only when something is left to add. [counts] as for [set].
     */
    fun mayAdd(counts: List<Int>): Boolean = total(counts) > 0L

    /** The line the session writes when it moved a controller's start or refused its list; null when it did neither. */
    fun line(uid: Int, counts: List<Int>, startIndex: Int, start: Start): String? = when (start) {
        Start.Refuse -> "controller uid $uid: none of its ${counts.size} item(s) is in the library, the queue is left as it was"
        is Start.At -> if (start.index == startIndex) null else "controller uid $uid: start index $startIndex -> ${start.index} (${total(counts)} of its ${counts.size} item(s) from the library)"
    }

    /**
     * A search query as a diagnostics line may carry it (the review's N10): another app's text, so no line break, no
     * control or format character (`FilesIntents.lineSafe`'s rule), at most [MAX_LINE_QUERY] characters, and no double
     * quote — the line puts the query between two.
     */
    fun lineQuery(query: String?): String = FilesIntents.lineSafe(query, MAX_LINE_QUERY).replace('"', '\'')

    /** How many items the final list has; below zero when a count is (no list at all). */
    private fun total(counts: List<Int>): Long {
        var sum = 0L
        for (c in counts) { if (c < 0) return -1L; sum += c }
        return if (sum > Int.MAX_VALUE) -1L else sum
    }
}

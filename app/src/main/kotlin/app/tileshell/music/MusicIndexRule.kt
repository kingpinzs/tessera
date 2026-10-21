package app.tileshell.music

/**
 * Which index a controller may hand Music's player (ledger L18-5; the adversarial review's pass 3, P3-1; a trust rule).
 *
 * MusicService is exported, so any app's controller reaches the session, and several of its commands carry an index
 * into the queue. Media3 1.9.0 hands them on with no more than a `< 0` check of its own (read from the bytecode), and
 * the player then throws — on the main thread, the launcher's — for an index it cannot take:
 *  - a legacy `skipToQueueItem(queueId)`: the legacy stub refuses only `queueId < 0`, casts the long to an int and calls
 *    `seekToDefaultPosition`. 2^31 becomes Int.MIN_VALUE and 2^32 − 2 becomes −2, and ExoPlayer's
 *    `checkArgument(index >= 0)` throws: RUN on the emulator (2026-10-20), the launcher died. 2^32 − 1 becomes −1
 *    (`C.INDEX_UNSET`), which ExoPlayer itself ignores;
 *  - a Media3 `removeMediaItem`, `replaceMediaItem` or `moveMediaItem` of Int.MAX_VALUE: `BasePlayer` adds one to it for
 *    the end of its range, the sum wraps below zero, and ExoPlayer's range check throws (READ).
 * So the session's player ([BoundedIndexPlayer]) asks this rule before it hands an index on, and refuses, with [line],
 * whatever the list as it is NOW does not have:
 *  - [item]: an index that must name an item (a seek's target; what a single remove, replace or move acts on): from 0
 *    up to the count. An empty list has none, so nothing is sought in it;
 *  - [place]: where items go (an add's index, a move's destination): from 0 on; past the end is the end, as the Player
 *    contract says;
 *  - [range]: from `from` up to `to`: it starts in the list or at its end and does not run backwards; a `to` past the
 *    end is the end, as the Player contract says (a Media3 controller's `clearMediaItems` sends Int.MAX_VALUE).
 * Whatever the rule lets through, ExoPlayer takes without throwing (`MusicIndexRuleTest`). Pure.
 */
object MusicIndexRule {
    fun item(index: Int, count: Int): Boolean = index in 0 until count

    fun place(index: Int): Boolean = index >= 0

    fun range(from: Int, to: Int, count: Int): Boolean = from in 0..count && to >= from

    /**
     * The line the session's player writes for a refused command: the controller's uid as the session reports it (none:
     * "unknown"), the command with the indices it came with, and the queue's length. Numbers only, so nothing the caller
     * sends can put text in the line.
     */
    fun line(uid: Int?, call: String, count: Int): String = "controller uid ${uid ?: "unknown"}: $call refused, the queue has $count item(s)"
}

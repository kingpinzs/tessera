package app.tileshell.tiles.api

/**
 * Windows tile notification queue rules (R5 §1.3): up to five notifications; a new one whose tag matches a
 * queued one (case-insensitive) replaces it; otherwise it is appended and, past capacity, the oldest is dropped.
 * With the queue disabled a tile holds only the latest notification. Entries are ordered oldest first.
 */
object TileQueuePolicy {
    const val CAPACITY = 5

    data class Insert<T>(val queue: List<T>, val evicted: List<T>)
    data class Prune<T>(val kept: List<T>, val expired: List<T>)

    fun <T> insert(queue: List<T>, entry: T, queueEnabled: Boolean, tagOf: (T) -> String?): Insert<T> {
        if (!queueEnabled) return Insert(listOf(entry), queue)
        val tag = tagOf(entry)
        val next = queue.toMutableList()
        val evicted = mutableListOf<T>()
        if (tag != null) {
            val i = next.indexOfFirst { tagOf(it)?.equals(tag, ignoreCase = true) == true }
            if (i >= 0) evicted += next.removeAt(i)
        }
        next += entry
        while (next.size > CAPACITY) evicted += next.removeAt(0)
        return Insert(next, evicted)
    }

    /** Disabling the queue keeps only the newest entry (Windows shows the latest update). */
    fun <T> disable(queue: List<T>): Insert<T> =
        if (queue.size <= 1) Insert(queue, emptyList()) else Insert(listOf(queue.last()), queue.dropLast(1))

    fun <T> prune(queue: List<T>, nowMs: Long, expiresAtOf: (T) -> Long?): Prune<T> {
        val (expired, kept) = queue.partition { e -> expiresAtOf(e)?.let { it <= nowMs } == true }
        return Prune(kept, expired)
    }
}

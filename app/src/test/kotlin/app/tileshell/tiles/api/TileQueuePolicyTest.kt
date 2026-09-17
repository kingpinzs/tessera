package app.tileshell.tiles.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TileQueuePolicyTest {
    private data class E(val id: Int, val tag: String? = null, val expiresAt: Long? = null)

    private fun insert(q: List<E>, e: E, enabled: Boolean = true) = TileQueuePolicy.insert(q, e, enabled) { it.tag }

    @Test fun fiveDeepOldestOut() {
        var q = emptyList<E>()
        for (i in 1..5) q = insert(q, E(i)).queue
        val r = insert(q, E(6))
        assertEquals(listOf(2, 3, 4, 5, 6), r.queue.map { it.id })
        assertEquals(listOf(1), r.evicted.map { it.id })
    }

    @Test fun tagMatchReplacesCaseInsensitively() {
        var q = emptyList<E>()
        q = insert(q, E(1, "Mail")).queue
        q = insert(q, E(2, "news")).queue
        val r = insert(q, E(3, "MAIL"))
        assertEquals(listOf(2, 3), r.queue.map { it.id })
        assertEquals(listOf(1), r.evicted.map { it.id })
    }

    @Test fun disabledQueueHoldsOnlyTheLatest() {
        val q = listOf(E(1), E(2))
        val r = insert(q, E(3), enabled = false)
        assertEquals(listOf(3), r.queue.map { it.id })
        assertEquals(listOf(1, 2), r.evicted.map { it.id })
    }

    @Test fun disablingKeepsTheNewest() {
        val r = TileQueuePolicy.disable(listOf(E(1), E(2), E(3)))
        assertEquals(listOf(3), r.queue.map { it.id })
        assertEquals(listOf(1, 2), r.evicted.map { it.id })
    }

    @Test fun expiredEntriesArePruned() {
        val q = listOf(E(1, expiresAt = 100), E(2), E(3, expiresAt = 200), E(4, expiresAt = 101))
        val r = TileQueuePolicy.prune(q, 101) { it.expiresAt }
        assertEquals(listOf(2, 3), r.kept.map { it.id })
        assertEquals(listOf(1, 4), r.expired.map { it.id })
    }
}

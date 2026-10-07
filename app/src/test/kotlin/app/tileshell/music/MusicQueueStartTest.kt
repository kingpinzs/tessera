package app.tileshell.music

import androidx.media3.common.C
import app.tileshell.music.MusicQueueStart.Start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ledger L18-4: where a controller's queue starts once the item rule has dropped, rebuilt or expanded its items. The
 * player must never be handed an index its final list does not have (ExoPlayer throws AFTER it has taken the list).
 * `counts[i]` is how many items the request's item i became: 0 dropped, 1 kept or rebuilt, n a search's queue.
 */
class MusicQueueStartTest {
    private fun set(counts: List<Int>, index: Int, position: Long = 0L) = MusicQueueStart.set(counts, index, position)

    /** What the guarantee is: whatever comes back is a refusal, the default start, or an index the final list has. */
    private fun assertInRange(counts: List<Int>, index: Int, position: Long) {
        val total = counts.sumOf { it.coerceAtLeast(0).toLong() }
        when (val start = MusicQueueStart.set(counts, index, position)) {
            // A list longer than a list can be is no list either.
            Start.Refuse -> assertTrue("refused though $total item(s) are left: $counts @$index", total == 0L || total > Int.MAX_VALUE || counts.any { it < 0 })
            is Start.At -> {
                assertTrue("an empty list is never started: $counts @$index", total > 0)
                assertTrue("index ${start.index} of $total: $counts @$index", start.index == MusicQueueStart.INDEX_UNSET || start.index in 0 until total)
                assertTrue("position ${start.positionMs}: $counts @$index +$position", start.positionMs >= 0 || start.positionMs == MusicQueueStart.TIME_UNSET)
            }
        }
    }

    @Test fun `the two unset values are Media3's own`() {
        assertEquals(C.INDEX_UNSET, MusicQueueStart.INDEX_UNSET)
        assertEquals(C.TIME_UNSET, MusicQueueStart.TIME_UNSET)
    }

    @Test fun `all kept - the index and the position are the caller's own`() {
        assertEquals(Start.At(0, 0L), set(listOf(1, 1, 1), 0))
        assertEquals(Start.At(2, 41_000L), set(listOf(1, 1, 1), 2, 41_000L))
        assertEquals(Start.At(1, MusicQueueStart.TIME_UNSET), set(listOf(1, 1), 1, MusicQueueStart.TIME_UNSET))
    }

    @Test fun `first dropped - the item the caller pointed at keeps its place in the shorter list`() {
        // The review's second call: [x, <real id>] with index 0 points at the dropped one -> the first item, from 0.
        assertEquals(Start.At(0, 0L), set(listOf(0, 1), 0, 30_000L))
        // Pointing at the survivor: it is now item 0, and its position is kept.
        assertEquals(Start.At(0, 30_000L), set(listOf(0, 1), 1, 30_000L))
        assertEquals(Start.At(1, 5L), set(listOf(0, 1, 0, 1, 1), 3, 5L))
    }

    @Test fun `pointed-at dropped - the first item, position 0 (the review's wedge - a real id and x, index 1)`() {
        assertEquals(Start.At(0, 0L), set(listOf(1, 0), 1, 0L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 0), 1, 90_000L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 0, 1), 1, 90_000L))
    }

    @Test fun `all dropped - refused, whatever the index (the current queue is left as it was)`() {
        for (index in listOf(0, 1, 3, 99, -1, -7, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals("index $index", Start.Refuse, set(listOf(0, 0, 0), index))
            assertEquals("index $index", Start.Refuse, set(emptyList(), index))
        }
        // The review's third call: all unknown, index 3.
        assertEquals(Start.Refuse, set(listOf(0, 0), 3, 0L))
        // A count below zero is no list at all.
        assertEquals(Start.Refuse, set(listOf(1, -1), 0))
        assertEquals(Start.Refuse, set(listOf(-1, 2), 0))
    }

    @Test fun `expansion before the index - the pointed-at item moves by what came before it`() {
        // A search that became three tracks, then the item the caller pointed at.
        assertEquals(Start.At(3, 12L), set(listOf(3, 1), 1, 12L))
        assertEquals(Start.At(5, 12L), set(listOf(3, 0, 2, 1, 1), 3, 12L))
        // Pointing AT an expansion: its first track, from the start — the caller's position was for one item.
        assertEquals(Start.At(1, 0L), set(listOf(1, 4, 1), 1, 12L))
    }

    @Test fun `index already out of range - the first item, position 0, with or without a drop`() {
        // Media3 hands a set without a reset the player's CURRENT index, which the new list need not have.
        assertEquals(Start.At(0, 0L), set(listOf(1), 5, 77_000L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 1), 2, 77_000L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 0), 2, 77_000L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 1), Int.MAX_VALUE, 1L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 1), -2, 1L))
        assertEquals(Start.At(0, 0L), set(listOf(1, 1), Int.MIN_VALUE, 1L))
    }

    @Test fun `INDEX_UNSET and TIME_UNSET - the default start stays the default start`() {
        // No index: Media3 then sets the list with a reset and reads no position at all.
        assertEquals(Start.At(MusicQueueStart.INDEX_UNSET, MusicQueueStart.TIME_UNSET), set(listOf(1, 1), MusicQueueStart.INDEX_UNSET, MusicQueueStart.TIME_UNSET))
        assertEquals(Start.At(MusicQueueStart.INDEX_UNSET, MusicQueueStart.TIME_UNSET), set(listOf(0, 1), MusicQueueStart.INDEX_UNSET, 9_000L))
        assertEquals(Start.Refuse, set(listOf(0, 0), MusicQueueStart.INDEX_UNSET, MusicQueueStart.TIME_UNSET))
        // An index with no position: the item's default position, kept only while the item is the one pointed at.
        assertEquals(Start.At(0, MusicQueueStart.TIME_UNSET), set(listOf(0, 1), 1, MusicQueueStart.TIME_UNSET))
        assertEquals(Start.At(0, 0L), set(listOf(1, 0), 1, MusicQueueStart.TIME_UNSET))
        // A position below zero that is not "unset" is nobody's position.
        assertEquals(Start.At(1, 0L), set(listOf(1, 1), 1, -5L))
        assertEquals(Start.At(1, 0L), set(listOf(1, 1), 1, Long.MIN_VALUE))
    }

    @Test fun `whatever is asked, the answer is a refusal, the default start or an index the final list has`() {
        val shapes = listOf(
            emptyList(), listOf(0), listOf(1), listOf(3), listOf(0, 0), listOf(1, 0), listOf(0, 1), listOf(1, 1), listOf(0, 3), listOf(3, 0),
            listOf(2, 0, 1), listOf(0, 0, 1), listOf(1, 0, 0), listOf(5, 5, 5), listOf(0, 1, 0, 1, 0), listOf(1, -1), listOf(Int.MAX_VALUE, Int.MAX_VALUE),
        )
        val indexes = listOf(Int.MIN_VALUE, -7, -2, -1, 0, 1, 2, 3, 4, 5, 14, 15, 99, Int.MAX_VALUE)
        val positions = listOf(Long.MIN_VALUE, MusicQueueStart.TIME_UNSET, -1L, 0L, 1L, 60_000L, Long.MAX_VALUE)
        for (counts in shapes) for (index in indexes) for (position in positions) assertInRange(counts, index, position)
    }

    @Test fun `a library search starts at its own match when the queue has it, else at the first track, and never on nothing`() {
        assertEquals(Start.At(2, 0L), MusicQueueStart.search(5, 2))
        assertEquals(Start.At(0, 0L), MusicQueueStart.search(5, 0))
        assertEquals(Start.At(0, 0L), MusicQueueStart.search(5, 5))
        assertEquals(Start.At(0, 0L), MusicQueueStart.search(5, -1))
        assertEquals(Start.At(0, 0L), MusicQueueStart.search(1, Int.MAX_VALUE))
        assertEquals(Start.Refuse, MusicQueueStart.search(0, 0))
        assertEquals(Start.Refuse, MusicQueueStart.search(-3, 0))
    }

    @Test fun `an add is refused when nothing is left to add - an indexed add too, whose index the player bounds itself`() {
        assertTrue(MusicQueueStart.mayAdd(listOf(1)))
        assertTrue(MusicQueueStart.mayAdd(listOf(0, 1, 0)))
        assertTrue(MusicQueueStart.mayAdd(listOf(0, 4)))
        assertFalse(MusicQueueStart.mayAdd(emptyList()))
        assertFalse(MusicQueueStart.mayAdd(listOf(0)))
        assertFalse(MusicQueueStart.mayAdd(listOf(0, 0, 0)))
        assertFalse(MusicQueueStart.mayAdd(listOf(2, -2)))
        assertFalse(MusicQueueStart.mayAdd(listOf(-1, 3)))
    }

    @Test fun `N10 a search query reaches a diagnostics line with no line break, no control or format character, and capped`() {
        assertEquals("Bloom", MusicQueueStart.lineQuery("Bloom"))
        assertEquals("a[music] play file /x", MusicQueueStart.lineQuery("a\n[music] play file /x"))
        assertEquals("ab", MusicQueueStart.lineQuery("a\r\u0000\u001b\u007f\u0085  ‮​﻿b"))
        assertEquals("", MusicQueueStart.lineQuery(null))
        assertEquals(MusicQueueStart.MAX_LINE_QUERY, MusicQueueStart.lineQuery("x".repeat(5_000)).length)
        assertTrue(MusicQueueStart.MAX_LINE_QUERY in 40..200)
        // A quote would close the line's own quotes round the query.
        assertEquals("a'b", MusicQueueStart.lineQuery("a\"b"))
    }

    @Test fun `the session says when it moved a controller's start or refused its list, and nothing when it did not`() {
        assertEquals(null, MusicQueueStart.line(10123, listOf(1, 1), 1, Start.At(1, 0L)))
        assertEquals(null, MusicQueueStart.line(10123, listOf(1, 1), -1, Start.At(-1, MusicQueueStart.TIME_UNSET)))
        assertEquals("controller uid 10123: start index 1 -> 0 (1 of its 2 item(s) from the library)", MusicQueueStart.line(10123, listOf(1, 0), 1, MusicQueueStart.set(listOf(1, 0), 1, 0L)))
        assertEquals("controller uid 10123: start index 3 -> 0 (4 of its 2 item(s) from the library)", MusicQueueStart.line(10123, listOf(3, 1), 3, Start.At(0, 0L)))
        assertEquals("controller uid 10123: none of its 2 item(s) is in the library, the queue is left as it was", MusicQueueStart.line(10123, listOf(0, 0), 3, Start.Refuse))
    }
}

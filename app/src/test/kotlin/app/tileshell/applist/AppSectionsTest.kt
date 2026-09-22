package app.tileshell.applist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app list's two top sections (INDEX Change Log 2026-09-21 item 8).
 *
 * Plain values, not AppEntry: an AppEntry is made of a ComponentName and a UserHandle, and in a JVM
 * test with returnDefaultValues both come back null. That trap has now cost this build three tests that
 * passed the wrong thing and one that failed for the wrong reason, which is why AppSections takes
 * accessors instead of the type.
 */
class AppSectionsTest {

    private data class Row(val pkg: String, val label: String = pkg, val installed: Long = 1_000L)

    private fun recent(apps: List<Row>, used: Map<String, Long>) =
        AppSections.recent(apps, used, { it.pkg }, { it.label })

    private fun added(apps: List<Row>) = AppSections.added(apps, { it.installed }, { it.label })

    // ---- recent ----

    @Test
    fun `the five most recently run come back, most recent first`() {
        val apps = (1..8).map { Row("p$it") }
        val used = (1..8).associate { "p$it" to it * 100L }
        assertEquals(listOf("p8", "p7", "p6", "p5", "p4"), recent(apps, used).map { it.pkg })
    }

    @Test
    fun `an app that has never been run is left out, not sorted to the bottom`() {
        val apps = listOf(Row("a"), Row("b"), Row("c"))
        assertEquals(listOf("a"), recent(apps, mapOf("a" to 5L)).map { it.pkg })
    }

    @Test
    fun `no usage records at all means no section`() {
        assertTrue(recent(listOf(Row("a"), Row("b")), emptyMap()).isEmpty())
    }

    @Test
    fun `fewer than five run apps gives a shorter section, never padding`() {
        assertEquals(2, recent(listOf(Row("a"), Row("b"), Row("c")), mapOf("a" to 2L, "b" to 1L)).size)
    }

    @Test
    fun `two apps used in the same millisecond keep a stable order`() {
        val apps = listOf(Row("z", "Zebra"), Row("a", "Apple"))
        val used = mapOf("z" to 7L, "a" to 7L)
        assertEquals(listOf("Apple", "Zebra"), recent(apps, used).map { it.label })
        assertEquals(recent(apps, used).map { it.label }, recent(apps.reversed(), used).map { it.label })
    }

    @Test
    fun `a usage record for an app that is not installed is ignored`() {
        assertEquals(listOf("a"), recent(listOf(Row("a")), mapOf("a" to 1L, "gone" to 99L)).map { it.pkg })
    }

    // ---- recently added ----

    @Test
    fun `the three most recently installed come back, newest first`() {
        val apps = (1..6).map { Row("p$it", installed = it * 10L) }
        assertEquals(listOf("p6", "p5", "p4"), added(apps).map { it.pkg })
    }

    @Test
    fun `an app with no install time is left out rather than looking newest or oldest`() {
        val apps = listOf(Row("old", installed = 0L), Row("a", installed = 5L), Row("b", installed = 9L))
        assertEquals(listOf("b", "a"), added(apps).map { it.pkg })
    }

    @Test
    fun `two apps installed in the same millisecond keep a stable order`() {
        assertEquals(listOf("Apple", "Zebra"), added(listOf(Row("z", "Zebra", 4L), Row("a", "Apple", 4L))).map { it.label })
    }

    @Test
    fun `an empty list is not a special case`() {
        assertTrue(recent(emptyList(), mapOf("a" to 1L)).isEmpty())
        assertTrue(added(emptyList()).isEmpty())
    }

    @Test
    fun `the counts are the ones Jeremy asked for`() {
        assertEquals(5, AppSections.RECENT_COUNT)
        assertEquals(3, AppSections.ADDED_COUNT)
    }
}

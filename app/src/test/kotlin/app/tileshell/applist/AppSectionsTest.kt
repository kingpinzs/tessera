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

    /** BOOT is the cut-off; a usage time below it is an app that last ran before this boot. */
    private val BOOT = 100L

    private fun running(apps: List<Row>, used: Map<String, Long>, since: Long = BOOT) =
        AppSections.running(apps, used, since, { it.pkg }, { it.label })

    private fun added(apps: List<Row>) = AppSections.added(apps, { it.installed }, { it.label })

    // ---- running ----

    @Test
    fun `the three most recently run since boot come back, most recent first`() {
        val apps = (1..8).map { Row("p$it") }
        val used = (1..8).associate { "p$it" to BOOT + it * 100L }
        assertEquals(listOf("p8", "p7", "p6"), running(apps, used).map { it.pkg })
    }

    @Test
    fun `an app last run BEFORE this boot is not running`() {
        // The whole point of the 2026-09-22 amendment: a seven-day window can never be empty on a phone
        // anyone uses, so "unless there is no running programs" could never happen.
        val apps = listOf(Row("before"), Row("after"))
        val used = mapOf("before" to BOOT - 1L, "after" to BOOT + 1L)
        assertEquals(listOf("after"), running(apps, used).map { it.pkg })
    }

    @Test
    fun `an app run at the very instant of boot counts`() {
        assertEquals(listOf("a"), running(listOf(Row("a")), mapOf("a" to BOOT)).map { it.pkg })
    }

    @Test
    fun `nothing run since boot means no section at all`() {
        val used = mapOf("a" to BOOT - 5L, "b" to BOOT - 9L)
        assertTrue(running(listOf(Row("a"), Row("b")), used).isEmpty())
    }

    @Test
    fun `an app that has never been run is left out, not sorted to the bottom`() {
        val apps = listOf(Row("a"), Row("b"), Row("c"))
        assertEquals(listOf("a"), running(apps, mapOf("a" to BOOT + 5L)).map { it.pkg })
    }

    @Test
    fun `no usage records at all means no section`() {
        assertTrue(running(listOf(Row("a"), Row("b")), emptyMap()).isEmpty())
    }

    @Test
    fun `fewer than three running apps gives a shorter section, never padding`() {
        val used = mapOf("a" to BOOT + 2L, "b" to BOOT + 1L)
        assertEquals(2, running(listOf(Row("a"), Row("b"), Row("c")), used).size)
    }

    @Test
    fun `two apps used in the same millisecond keep a stable order`() {
        val apps = listOf(Row("z", "Zebra"), Row("a", "Apple"))
        val used = mapOf("z" to BOOT + 7L, "a" to BOOT + 7L)
        assertEquals(listOf("Apple", "Zebra"), running(apps, used).map { it.label })
        assertEquals(running(apps, used).map { it.label }, running(apps.reversed(), used).map { it.label })
    }

    @Test
    fun `a usage record for an app that is not installed is ignored`() {
        assertEquals(listOf("a"), running(listOf(Row("a")), mapOf("a" to BOOT + 1L, "gone" to BOOT + 99L)).map { it.pkg })
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
        assertTrue(running(emptyList(), mapOf("a" to BOOT + 1L)).isEmpty())
        assertTrue(added(emptyList()).isEmpty())
    }

    @Test
    fun `the counts are the ones Jeremy asked for`() {
        assertEquals(3, AppSections.RUNNING_COUNT)
        assertEquals(3, AppSections.ADDED_COUNT)
    }
}

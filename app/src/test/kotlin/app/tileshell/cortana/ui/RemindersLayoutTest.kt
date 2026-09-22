package app.tileshell.cortana.ui

import app.tileshell.cortana.reminders.Reminder
import app.tileshell.cortana.reminders.ReminderGroup
import app.tileshell.cortana.reminders.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reminders list's grouping and geometry.
 *
 * The geometry tests replay R7's own captured frame — `frames/n_s1/s1_full_t875.0.png`, three
 * reminders on N2's 360-epx canvas: a photo reminder under Today, a two-line one under Coming up and a
 * one-line one under Whenever — and assert every edge R7 §3.5.2–§3.5.5 read off it. If the layout
 * drifts, these turn red against the measurement rather than against another of my numbers.
 */
class RemindersLayoutTest {

    private val screen = 360f

    /** R7's captured frame, as shapes. */
    private val capturedFrame = listOf(
        ReminderGroup.TODAY to listOf(RowShape(hasSubline = false, hasPhoto = true)),
        ReminderGroup.COMING_UP to listOf(RowShape(hasSubline = true, hasPhoto = false)),
        ReminderGroup.WHENEVER to listOf(RowShape(hasSubline = false, hasPhoto = false)),
    )

    // ------------------------------------------------------------------ geometry

    @Test
    fun `the photo box reproduces R7 §3_5_5`() {
        assertEquals(292.6f, RemindersLayout.photoWidthEpx(screen), 0.5f)
        assertEquals(148.6f, RemindersLayout.photoHeightEpx(screen), 0.5f)
    }

    @Test
    fun `the captured frame's group headers land where R7 measured them`() {
        val blocks = RemindersLayout.layout(capturedFrame, screen)
        assertEquals(3, blocks.size)
        assertEquals(67.4f, blocks[0].headerCapTopEpx, 0.5f)   // §3.5.2 "Today"
        assertEquals(326.2f, blocks[1].headerCapTopEpx, 0.5f)  // §3.5.2 "Coming up"
        assertEquals(431.7f, blocks[2].headerCapTopEpx, 0.5f)  // §3.5.2 "Whenever"
    }

    @Test
    fun `the captured frame's rows land where R7 measured them`() {
        val blocks = RemindersLayout.layout(capturedFrame, screen)
        val photoRow = blocks[0].rows[0]
        assertEquals(93.5f, photoRow.topEpx, 0.5f)             // §3.5.5 pressed fill 93.5-312.3
        assertEquals(312.3f, photoRow.bottomEpx, 0.5f)
        assertEquals(218.8f, photoRow.heightEpx, 0.5f)

        val oneLine = blocks[2].rows[0]
        assertEquals(458.2f, oneLine.topEpx, 0.5f)             // §3.5.3 pressed fill 458.2-518.2
        assertEquals(518.2f, oneLine.bottomEpx, 0.5f)
        assertEquals(60f, oneLine.heightEpx, 0.5f)
    }

    @Test
    fun `the photo and its time line land where R7 measured them`() {
        val blocks = RemindersLayout.layout(capturedFrame, screen)
        val rowTop = blocks[0].rows[0].topEpx
        assertEquals(129.4f, RemindersLayout.photoTopEpx(rowTop), 0.5f)                  // §3.5.5
        assertEquals(283.4f, RemindersLayout.photoTimeCapTopEpx(rowTop, screen), 0.5f)   // §3.5.5
    }

    @Test
    fun `row heights are the table R7 gives, plus the one derived from its anchors`() {
        assertEquals(60f, RemindersLayout.rowHeightEpx(RowShape(false, false), screen), 1e-3f)
        assertEquals(65.3f, RemindersLayout.rowHeightEpx(RowShape(true, false), screen), 1e-3f)
        assertEquals(218.8f, RemindersLayout.rowHeightEpx(RowShape(false, true), screen), 0.5f)
    }

    @Test
    fun `a narrower canvas keeps the photo's measured aspect and margins`() {
        val narrow = 320f
        assertEquals(narrow - 55.4f - 12.4f, RemindersLayout.photoWidthEpx(narrow), 1e-3f)
        assertEquals(
            RemindersLayout.photoWidthEpx(narrow) / 1.97f,
            RemindersLayout.photoHeightEpx(narrow),
            1e-3f,
        )
    }

    @Test
    fun `content height clears the last row`() {
        val blocks = RemindersLayout.layout(capturedFrame, screen)
        assertTrue(RemindersLayout.contentHeightEpx(blocks) > blocks.last().rows.last().bottomEpx)
    }

    // ------------------------------------------------------------------ grouping

    private val now = 1_700_000_000_000L          // an arbitrary instant
    private val endOfToday = now + 6 * 3_600_000L // six hours later

    private fun timed(id: String, at: Long?) =
        Reminder(id = id, text = id, kind = ReminderKind.TIME, timeMs = at)

    @Test
    fun `reminders fall into Today, Coming up and Whenever`() {
        val groups = RemindersLayout.groupReminders(
            listOf(
                timed("today", now + 3_600_000L),
                timed("tomorrow", endOfToday + 3_600_000L),
                timed("whenever", null),
            ),
            now, endOfToday,
        )
        assertEquals(listOf(ReminderGroup.TODAY, ReminderGroup.COMING_UP, ReminderGroup.WHENEVER), groups.map { it.first })
        assertEquals("today", groups[0].second.single().id)
        assertEquals("tomorrow", groups[1].second.single().id)
        assertEquals("whenever", groups[2].second.single().id)
    }

    @Test
    fun `a group with no reminder is absent`() {
        val groups = RemindersLayout.groupReminders(
            listOf(timed("today", now + 60_000L), timed("whenever", null)),
            now, endOfToday,
        )
        assertEquals(listOf(ReminderGroup.TODAY, ReminderGroup.WHENEVER), groups.map { it.first })
        assertFalse(groups.any { it.first == ReminderGroup.COMING_UP })
    }

    @Test
    fun `an empty list groups to nothing at all`() {
        assertTrue(RemindersLayout.groupReminders(emptyList(), now, endOfToday).isEmpty())
        // And nothing laid out, which is what drives §3.5.9's empty state.
        assertTrue(RemindersLayout.layout(emptyList(), screen).isEmpty())
    }

    @Test
    fun `a completed reminder leaves the list`() {
        val groups = RemindersLayout.groupReminders(
            listOf(timed("done", now + 60_000L).copy(completed = true), timed("live", now + 60_000L)),
            now, endOfToday,
        )
        assertEquals("live", groups.single().second.single().id)
    }

    @Test
    fun `place and person reminders list under Whenever`() {
        val groups = RemindersLayout.groupReminders(
            listOf(
                Reminder(id = "p", text = "p", kind = ReminderKind.PLACE, placeId = "x"),
                Reminder(id = "c", text = "c", kind = ReminderKind.PERSON, contactName = "Sam"),
            ),
            now, endOfToday,
        )
        assertEquals(ReminderGroup.WHENEVER, groups.single().first)
        assertEquals(2, groups.single().second.size)
    }

    @Test
    fun `a reminder due exactly at the end of today is still Today`() {
        val groups = RemindersLayout.groupReminders(listOf(timed("edge", endOfToday)), now, endOfToday)
        assertEquals(ReminderGroup.TODAY, groups.single().first)
    }

    @Test
    fun `reminders inside a group are soonest first`() {
        val groups = RemindersLayout.groupReminders(
            listOf(timed("late", now + 5_000_000L), timed("soon", now + 1_000L)),
            now, endOfToday + 10_000_000L,
        )
        assertEquals(listOf("soon", "late"), groups.single().second.map { it.id })
    }

    // ------------------------------------------------------------------ long-press menu geometry

    @Test
    fun `the menu centres on the touch with its bottom 23_5 epx above it`() {
        // R7 §3.6.2: menu centre 180.8 epx against a touch at 181.
        val rect = RemindersLayout.longPressMenuRect(181f, 900f, screen)
        assertEquals(181f, (rect.left + rect.right) / 2f, 0.5f)
        assertEquals(876.5f, rect.bottom, 1e-3f)
        assertEquals(243.3f, rect.width, 1e-3f)
        assertEquals(107f, rect.height, 1e-3f)
        assertEquals(rect.bottom - 107f, rect.top, 1e-3f)
    }

    @Test
    fun `the menu clamps at the left edge`() {
        val rect = RemindersLayout.longPressMenuRect(10f, 500f, screen)
        assertEquals(0f, rect.left, 1e-3f)
        assertEquals(243.3f, rect.width, 1e-3f)
    }

    @Test
    fun `the menu clamps at the right edge`() {
        val rect = RemindersLayout.longPressMenuRect(350f, 500f, screen)
        assertEquals(screen - 243.3f, rect.left, 1e-3f)
        assertEquals(screen, rect.right, 1e-3f)
    }

    @Test
    fun `a canvas narrower than the menu pins it to the left edge`() {
        val rect = RemindersLayout.longPressMenuRect(100f, 500f, 200f)
        assertEquals(0f, rect.left, 1e-3f)
        assertEquals(243.3f, rect.width, 1e-3f)
    }

    /** §3.6.4 grows the menu from a FIXED bottom edge, so the bottom is never clamped. */
    @Test
    fun `the bottom edge tracks the touch even high on the screen`() {
        assertEquals(6.5f, RemindersLayout.longPressMenuRect(180f, 30f, screen).bottom, 1e-3f)
    }
}

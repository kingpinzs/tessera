package app.tileshell.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Voice Recorder's pure rules (phase 15 build task 7): names, T15-3 capabilities, filter, groups, formats. */
class RecorderRulesTest {

    private val shell = "app.tileshell"

    private fun rec(id: Long, name: String, owner: String?, at: Long = id) =
        Recording(id, "$name.m4a", 5_000, at, owner, "Recordings/", 40_000)

    // ---- names (U2) -------------------------------------------------------------------------------

    @Test fun `the first take is Recording`() {
        assertEquals("Recording", RecordingNames.next(emptyList()))
        assertEquals("Recording", RecordingNames.next(listOf("other.m4a", "Standup.m4a")))
    }

    @Test fun `then Recording (2), Recording (3)`() {
        assertEquals("Recording (2)", RecordingNames.next(listOf("Recording.m4a")))
        assertEquals("Recording (3)", RecordingNames.next(listOf("Recording.m4a", "Recording (2).m4a")))
    }

    @Test fun `the smallest free number wins, so a deleted name comes back`() {
        assertEquals("Recording", RecordingNames.next(listOf("Recording (2).m4a", "Recording (3).m4a")))
        assertEquals("Recording (2)", RecordingNames.next(listOf("Recording.m4a", "Recording (3).m4a")))
    }

    @Test fun `names match whatever their case and with or without the extension`() {
        assertEquals("Recording (2)", RecordingNames.next(listOf("recording.M4A")))
        assertEquals("Recording (3)", RecordingNames.next(listOf("Recording", "RECORDING (2).m4a")))
    }

    @Test fun `a provider-suffixed clash is not mistaken for the numbering`() {
        // " (1)" is MediaProvider's own clash suffix, not this numbering; Recording (2) is still free.
        assertEquals("Recording (2)", RecordingNames.next(listOf("Recording.m4a", "Recording (1).m4a")))
    }

    @Test fun `stem strips only a trailing m4a`() {
        assertEquals("Standup", RecordingNames.stem("Standup.m4a"))
        assertEquals("a.m4a.b", RecordingNames.stem("a.m4a.b"))
        assertEquals(".m4a", RecordingNames.stem(".m4a"))
        assertEquals("Recording.m4a", RecordingNames.displayName("Recording"))
    }

    // ---- T15-3 / T15-26 capabilities ---------------------------------------------------------------

    @Test fun `the shell's own recording can do everything`() {
        val c = RecordingCaps.of(shell, shell)
        assertTrue(c.share && c.rename && c.delete && c.trim && c.markers)
    }

    @Test fun `another app's recording plays and shares only`() {
        val c = RecordingCaps.of("com.sec.android.app.voicenote", shell)
        assertTrue(c.share)
        assertFalse(c.rename)
        assertFalse(c.delete)
        assertFalse(c.trim)
        assertFalse(c.markers)
    }

    @Test fun `an orphaned take (owner cleared by uninstall or Clear storage) is another app's`() {
        val c = RecordingCaps.of(null, shell)
        assertTrue(c.share)
        assertFalse(c.rename || c.delete || c.trim || c.markers)
    }

    @Test fun `ownership is the exact package, not a prefix`() {
        assertFalse(RecordingCaps.of("app.tileshell.evil", shell).delete)
        assertFalse(RecordingCaps.of("", shell).rename)
    }

    // ---- filter and search (T15-16) ---------------------------------------------------------------

    private val list = listOf(
        rec(1, "Standup", shell),
        rec(2, "Recording", shell),
        rec(3, "other", "com.example.recorder"),
        rec(4, "Orphan", null),
    )

    @Test fun `All shows everything`() {
        assertEquals(listOf(1L, 2L, 3L, 4L), RecordingFilter.apply(list, ShowingKind.ALL, "", shell).map { it.id })
    }

    @Test fun `My recordings shows only the shell's own`() {
        assertEquals(listOf(1L, 2L), RecordingFilter.apply(list, ShowingKind.MINE, "", shell).map { it.id })
    }

    @Test fun `Other apps' recordings includes orphans`() {
        assertEquals(listOf(3L, 4L), RecordingFilter.apply(list, ShowingKind.OTHERS, "", shell).map { it.id })
    }

    @Test fun `search matches a part of the name, any case, trimmed`() {
        assertEquals(listOf(1L), RecordingFilter.apply(list, ShowingKind.ALL, "Stand", shell).map { it.id })
        assertEquals(listOf(1L), RecordingFilter.apply(list, ShowingKind.ALL, "  stAND ", shell).map { it.id })
        assertEquals(emptyList<Long>(), RecordingFilter.apply(list, ShowingKind.ALL, "m4a", shell).map { it.id })
    }

    @Test fun `search and the filter combine`() {
        assertEquals(listOf(4L), RecordingFilter.apply(list, ShowingKind.OTHERS, "or", shell).map { it.id })
        assertEquals(listOf(2L), RecordingFilter.apply(list, ShowingKind.MINE, "or", shell).map { it.id })
    }

    @Test fun `the other-apps count for the list line counts orphans`() {
        assertEquals(2, RecordingFilter.othersCount(list, shell))
    }

    @Test fun `the Showing kinds, their ids and their words`() {
        assertEquals(listOf("all", "mine", "others"), ShowingKind.entries.map { it.id })
        assertEquals(listOf("All recordings", "My recordings", "Other apps' recordings"), ShowingKind.entries.map { it.label })
        assertEquals(ShowingKind.MINE, ShowingKind.byId("mine"))
        assertEquals(ShowingKind.ALL, ShowingKind.byId("nonsense"))
        assertEquals(ShowingKind.ALL, ShowingKind.byId(null))
    }

    // ---- date groups (3.3, U7) --------------------------------------------------------------------

    private val zone = ZoneId.of("America/Denver")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, mo, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun `groups are Today, Yesterday, This week, Last week, then months`() {
        // Wednesday 2026-09-23; en-US weeks start on Sunday (the 20th).
        val now = at(2026, 9, 23, 18)
        val l = Locale.US
        assertEquals("Today", RecordingGroups.label(at(2026, 9, 23, 0), now, zone, l))
        assertEquals("Yesterday", RecordingGroups.label(at(2026, 9, 22), now, zone, l))
        assertEquals("This week", RecordingGroups.label(at(2026, 9, 20), now, zone, l))
        assertEquals("Last week", RecordingGroups.label(at(2026, 9, 19), now, zone, l))
        assertEquals("Last week", RecordingGroups.label(at(2026, 9, 13), now, zone, l))
        assertEquals("September", RecordingGroups.label(at(2026, 9, 12), now, zone, l))
        assertEquals("March", RecordingGroups.label(at(2026, 3, 1), now, zone, l))
        assertEquals("December 2025", RecordingGroups.label(at(2025, 12, 31), now, zone, l))
    }

    @Test fun `a date in the future is Today`() {
        val now = at(2026, 9, 23)
        assertEquals("Today", RecordingGroups.label(at(2026, 9, 25), now, zone, Locale.US))
    }

    @Test fun `grouping is newest first inside and across groups`() {
        val now = at(2026, 9, 23, 18)
        val items = listOf(
            rec(1, "a", shell, at(2026, 9, 22)),
            rec(2, "b", shell, at(2026, 9, 23, 9)),
            rec(3, "c", shell, at(2026, 9, 23, 11)),
            rec(4, "d", shell, at(2025, 1, 1)),
        )
        val groups = RecordingGroups.group(items, now, zone, Locale.US)
        assertEquals(listOf("Today", "Yesterday", "January 2025"), groups.map { it.first })
        assertEquals(listOf(3L, 2L), groups[0].second.map { it.id })
    }

    // ---- formats (2.8, 3.7) -------------------------------------------------------------------------

    @Test fun `row durations are m_ss and h_mm_ss from one hour`() {
        assertEquals("0:13", RecorderFormat.duration(13_000))
        assertEquals("4:24", RecorderFormat.duration(264_000))
        assertEquals("1:07:03", RecorderFormat.duration(4_023_000))
        assertEquals("0:05", RecorderFormat.duration(4_600))
        assertEquals("0:00", RecorderFormat.duration(-5))
    }

    @Test fun `the timer is eight characters with its leading zero fields dimmed`() {
        assertEquals("00:00:" to "03", RecorderFormat.timer(3_400))
        assertEquals("00:" to "37:44", RecorderFormat.timer((37 * 60 + 44) * 1000L))
        assertEquals("" to "01:00:00", RecorderFormat.timer(3_600_000))
        assertEquals("00:00:" to "00", RecorderFormat.timer(0))
        val (dim, live) = RecorderFormat.timer(59_999)
        assertEquals(8, (dim + live).length)
    }

    @Test fun `markers read 00_07 while recording and 1_08 on playback`() {
        assertEquals("00:07", RecorderFormat.recordingMarker(7_900))
        assertEquals("1:08", RecorderFormat.playbackMarker(68_000))
    }

    @Test fun `free space is whole mebibytes`() {
        assertEquals(50L, RecorderFormat.mebibytes(50L * 1024 * 1024 + 1000))
        assertEquals(0L, RecorderFormat.mebibytes(-1))
    }
}

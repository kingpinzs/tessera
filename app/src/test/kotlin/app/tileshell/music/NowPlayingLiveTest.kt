package app.tileshell.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live now-playing form's rules (phase 20 build task 4; Y1): what the `•••` sleep list offers while a station
 * plays, and what the two metadata lines say. What is drawn — the bar, the caption, the absent scrubber — is the
 * device row's (A2).
 */
class NowPlayingLiveTest {

    private fun sleepTags(live: Boolean) = moreEntries(MoreMenu.SLEEP, live = live, now = 0L) {}.map { it.tag }

    private val minutes = listOf("music_menu_sleep:15", "music_menu_sleep:30", "music_menu_sleep:45", "music_menu_sleep:60")

    // ---- moreEntries (the live case) ----

    @Test
    fun `a track's sleep list ends with the end-of-track entry, as built`() {
        assertEquals(minutes + "music_menu_sleep:eot", sleepTags(live = false))
    }

    @Test
    fun `a station's sleep list has no end-of-track entry, and every minute choice`() {
        val tags = sleepTags(live = true)
        assertFalse("music_menu_sleep:eot" in tags)
        assertEquals(minutes, tags)
    }

    @Test
    fun `the root menu is the same for a station and a track`() {
        val track = moreEntries(MoreMenu.ROOT, live = false, now = 0L) {}.map { it.tag }
        assertEquals(track, moreEntries(MoreMenu.ROOT, live = true, now = 0L) {}.map { it.tag })
        assertTrue("music_menu_sleep" in track)
    }

    // ---- the metadata lines ----

    @Test
    fun `a track's two lines are R8's whatever the stream state says`() {
        assertEquals("Bloom", metaText(true, "Bloom", "Radiohead", "The King of Limbs", live = false, streamState = "Reconnecting…"))
        assertEquals("Radiohead • The King of Limbs", metaText(false, "Bloom", "Radiohead", "The King of Limbs", live = false, streamState = null))
        // A self-titled album still says both: only a station's doubled name is folded.
        assertEquals("Weezer • Weezer", metaText(false, "Buddy Holly", "Weezer", "Weezer", live = false, streamState = null))
        assertEquals("Radiohead", metaText(false, "Bloom", "Radiohead", "", live = false, streamState = null))
    }

    @Test
    fun `a station's first line is the song on air, or the reconnect state while there is one`() {
        assertEquals("QA Song 1", metaText(true, "QA Song 1", "QA Jazz One", "QA Jazz One", live = true, streamState = null))
        assertEquals("Reconnecting…", metaText(true, "QA Song 1", "QA Jazz One", "QA Jazz One", live = true, streamState = "Reconnecting…"))
    }

    @Test
    fun `a station's second line is its name once`() {
        assertEquals("QA Jazz One", metaText(false, "QA Song 1", "QA Jazz One", "QA Jazz One", live = true, streamState = null))
        assertEquals("QA Jazz One", metaText(false, "", "", "QA Jazz One", live = true, streamState = null))
        assertEquals("QA Jazz One", metaText(false, "", "QA Jazz One", "", live = true, streamState = null))
    }
}

package app.tileshell.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase 17 build task 7: My videos' captions, folder groups and column count (Y5; r11/movies-tv.md 1.4). */
class VideoGroupsTest {
    @Test fun `the caption is the file name without its extension`() {
        assertEquals("qa-steps", VideoGroups.caption("qa-steps.mp4"))
        assertEquals("Hall Pass 720p Xvid", VideoGroups.caption("Hall Pass 720p Xvid.avi"))
        assertEquals("a.b", VideoGroups.caption("a.b.mkv"))
        assertEquals("noextension", VideoGroups.caption("noextension"))
        assertEquals(".hidden", VideoGroups.caption(".hidden"))
    }

    @Test fun `a folder's name is the last part of its path`() {
        assertEquals("Trip", VideoGroups.folderName("Movies/Trip/"))
        assertEquals("Movies", VideoGroups.folderName("Movies/"))
        assertEquals("Videos", VideoGroups.folderName("/"))
        assertEquals("Videos", VideoGroups.folderName(""))
    }

    @Test fun `one group per folder, folders and videos in name order`() {
        val groups = VideoGroups.group(
            listOf(
                VideoRow(5, "zeta.mp4", "Movies/"),
                VideoRow(2, "Alpha.mp4", "Movies/"),
                VideoRow(9, "b.mp4", "DCIM/Camera/"),
                VideoRow(1, "root.mp4", "/"),
                VideoRow(7, "beta.mp4", "Movies/"),
                VideoRow(8, "c.mp4", "Download/Camera/"),
            ),
        )
        assertEquals(listOf("Camera", "Camera", "Movies", "Videos"), groups.map { it.name })
        assertEquals(listOf("DCIM/Camera", "Download/Camera", "Movies", ""), groups.map { it.path })
        assertEquals(listOf("Alpha.mp4", "beta.mp4", "zeta.mp4"), groups[2].items.map { it.displayName })
        assertArrayEquals(longArrayOf(2, 7, 5), groups[2].queue)
    }

    @Test fun `two fixed tiles fit at 360 epx and three at 411`() {
        assertEquals(2, VideoGroups.columns(360f))
        assertEquals(3, VideoGroups.columns(411.43f))
        assertEquals(1, VideoGroups.columns(100f))
        // The second tile ends at 12 + 124 + 112 = 248, inside the 348 margin; a third would end at 372.
        assertEquals(248f, VideoGroups.MARGIN + VideoGroups.PITCH + VideoGroups.TILE, 0f)
    }
}

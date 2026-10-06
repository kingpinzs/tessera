package app.tileshell.files

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The words and numbers of build tasks 3, 8 and 9 that need no phone: Properties' formats, a zip row's detail, the dialogs' sentences, the share's type, the hold menu's curve. */
class FilesScreensTextTest {
    @Test
    fun `Properties' value formats are r11 1_10_5's`() {
        assertEquals("MOV File", FileFacts.typeText("clip.mov", false))
        assertEquals("File", FileFacts.typeText("README", false))
        assertEquals("File folder", FileFacts.typeText("sub.d", true))
        assertEquals("00:07:52", FileFacts.lengthText(472_000))
        assertEquals("20619kbps", FileFacts.rateText(20_619_400))
    }

    @Test
    fun `a zip row's detail is size then date, a folder's the date, and no date when the archive gives none`() {
        val utc = ZoneId.of("UTC")
        val day = 1_767_312_000_000L // 2026-01-02
        assertEquals("1.00 KB 1/2/2026", ZipSessions.detail(ZipRow("one.txt", "one.txt", false, 1024, day, null), utc))
        assertEquals("1/2/2026", ZipSessions.detail(ZipRow("dir", "dir", true, 0, day, null), utc))
        assertEquals("", ZipSessions.detail(ZipRow("dir", "dir", true, 0, 0, null), utc))
        assertEquals("15 bytes", ZipSessions.detail(ZipRow("a", "a", false, 15, 0, null), utc))
    }

    @Test
    fun `the dialogs count in the singular and turn a reason into a sentence`() {
        assertEquals("Delete this item?", DialogText.deleteTitle(1))
        assertEquals("Delete these 3 items?", DialogText.deleteTitle(3))
        assertEquals("1 item will be permanently deleted. You can't undo this.", DialogText.emptyBody(1))
        assertEquals("The name is taken.", DialogText.sentence("the name is taken"))
    }

    @Test
    fun `a share has one common type or any`() {
        assertEquals("image/png", FilesShare.commonType(listOf("image/png", "image/png")))
        assertEquals("*/*", FilesShare.commonType(listOf("image/png", "audio/mpeg")))
    }

    @Test
    fun `the hold menu grows from 76 percent through the measured points`() {
        fun height(ms: Float) = HoldMetrics.FIRST_HEIGHT + (1f - HoldMetrics.FIRST_HEIGHT) * HoldMetrics.Grow.transform(ms / 283f)
        assertEquals(0.76f, height(0f), 0.001f)
        assertEquals(0.90f, height(100f), 0.001f)
        assertEquals(0.95f, height(133f), 0.001f)
        assertEquals(0.98f, height(167f), 0.001f)
        assertEquals(1f, height(283f), 0.001f)
    }
}

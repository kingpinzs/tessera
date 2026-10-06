package app.tileshell.photos

import app.tileshell.media.MotionPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Living Images in Photos (phase 17 build task 6d): what the check is remembered by, which rows are read at all, the
 * bounds on a clip's offset and length — numbers out of a file another app may have written — the player's read
 * window, and the two lines.
 */
class LivingRulesTest {
    private fun entry(id: Long, mime: String = "image/jpeg", video: Boolean = false, size: Long = 1000, modified: Long = 50) = MediaEntry(
        id = id, video = video, dateMs = 1, bucketId = 1, bucket = "Camera", mime = mime, name = "f$id", width = 640, height = 480,
        size = size, relativePath = "DCIM/Camera/", durationMs = 0, dateTakenMs = null, dateModifiedS = modified,
    )

    // ---- the cache key -----------------------------------------------------------------------------------------------

    @Test fun `the key is the row, its modified time and its size`() {
        assertEquals(LivingRules.Key(7, 50, 1000), LivingRules.key(entry(7)))
        assertEquals(LivingRules.key(entry(7)), LivingRules.key(entry(7).copy(dateMs = 99, name = "renamed", bucket = "Other")))
    }

    @Test fun `a file edited or replaced under the same row is another key, and so is another row`() {
        val k = LivingRules.key(entry(7))
        assertNotEquals(k, LivingRules.key(entry(7, modified = 51)))
        assertNotEquals(k, LivingRules.key(entry(7, size = 1001)))
        assertNotEquals(k, LivingRules.key(entry(8)))
    }

    // ---- which rows are read -----------------------------------------------------------------------------------------

    @Test fun `only a jpeg still is opened for the check`() {
        assertTrue(LivingRules.mayBeLiving(false, "image/jpeg"))
        assertTrue(LivingRules.mayBeLiving(false, "IMAGE/JPEG"))
        for (mime in listOf("image/png", "image/heic", "image/gif", "image/webp", "image/x-adobe-dng", "video/mp4")) {
            assertFalse(mime, LivingRules.mayBeLiving(false, mime))
        }
        assertFalse("a video row, whatever its type says", LivingRules.mayBeLiving(true, "image/jpeg"))
    }

    @Test fun `a picture with no stated type is read - the file's bytes decide`() {
        assertTrue(LivingRules.mayBeLiving(false, null))
        assertTrue(LivingRules.mayBeLiving(false, ""))
        assertTrue(LivingRules.mayBeLiving(false, "image/*"))
    }

    // ---- the bounds --------------------------------------------------------------------------------------------------

    @Test fun `a clip inside the file is taken as stated`() {
        assertEquals(LivingRules.Bounded(LivingRules.Clip(600, 400), null), LivingRules.clip(600, 400, 1000))
        // Ending exactly on the file's last byte, and ending before it (an item after the clip).
        assertNotNull(LivingRules.clip(1, 999, 1000).clip)
        assertNotNull(LivingRules.clip(600, 100, 1000).clip)
        assertNotNull(LivingRules.clip(992, 8, 1000).clip)
    }

    @Test fun `a clip that runs past the file's end is refused`() {
        assertEquals("clip runs past the file's end", LivingRules.clip(600, 401, 1000).why)
        assertNull(LivingRules.clip(600, 401, 1000).clip)
        assertNull(LivingRules.clip(999, 8, 1000).clip)
    }

    @Test fun `an offset before the file's second byte or at its end is refused`() {
        for (offset in listOf(0L, -1L, Long.MIN_VALUE, 1000L, 1001L, Long.MAX_VALUE)) {
            assertEquals("offset $offset", "clip offset outside the file", LivingRules.clip(offset, 100, 1000).why)
        }
    }

    @Test fun `a length that is too short, negative or over the cap is refused`() {
        assertEquals("clip too short", LivingRules.clip(600, 7, 1000).why)
        assertEquals("clip too short", LivingRules.clip(600, 0, 1000).why)
        assertEquals("clip too short", LivingRules.clip(600, -400, 1000).why)
        assertEquals("clip too short", LivingRules.clip(600, Long.MIN_VALUE, 1000).why)
        val big = 200L * 1024 * 1024
        assertEquals("clip over 64 MB", LivingRules.clip(1000, LivingRules.MAX_CLIP_BYTES + 1, big).why)
        assertNotNull(LivingRules.clip(1000, LivingRules.MAX_CLIP_BYTES, big).clip)
        assertEquals(64L * 1024 * 1024, LivingRules.MAX_CLIP_BYTES)
    }

    @Test fun `numbers chosen to overflow a sum are refused, not wrapped`() {
        // offset + length wraps to a small positive number in 64-bit arithmetic.
        assertNull(LivingRules.clip(600, Long.MAX_VALUE, 1000).clip)
        assertNull(LivingRules.clip(Long.MAX_VALUE - 3, 8, Long.MAX_VALUE).clip)
        assertNull(LivingRules.clip(Long.MAX_VALUE, Long.MAX_VALUE, 1000).clip)
    }

    @Test fun `a file with no size has no clip`() {
        assertEquals("no file size", LivingRules.clip(600, 400, 0).why)
        assertEquals("no file size", LivingRules.clip(600, 400, -1).why)
    }

    @Test fun `every accepted clip lies inside the file`() {
        val sizes = listOf(1L, 8L, 9L, 1000L, 5_000_000L)
        val numbers = listOf(Long.MIN_VALUE, -1L, 0L, 1L, 7L, 8L, 9L, 500L, 999L, 1000L, 4_999_992L, 5_000_000L, Long.MAX_VALUE)
        var accepted = 0
        for (size in sizes) for (offset in numbers) for (length in numbers) {
            val clip = LivingRules.clip(offset, length, size).clip ?: continue
            accepted++
            assertTrue("$offset+$length in $size", clip.offset > 0 && clip.length >= 8 && clip.length <= LivingRules.MAX_CLIP_BYTES && clip.offset <= size - clip.length)
        }
        assertTrue(accepted > 0)
    }

    // ---- the player's reads ------------------------------------------------------------------------------------------

    @Test fun `a read is the clip's bytes in the file's coordinates`() {
        val clip = LivingRules.Clip(600, 400)
        assertEquals(LivingRules.Window(600, 400), LivingRules.window(clip, 0, -1))
        assertEquals(LivingRules.Window(700, 300), LivingRules.window(clip, 100, -1))
        assertEquals(LivingRules.Window(700, 50), LivingRules.window(clip, 100, 50))
    }

    @Test fun `a read never reaches past the clip's last byte`() {
        val clip = LivingRules.Clip(600, 400)
        assertEquals(LivingRules.Window(700, 300), LivingRules.window(clip, 100, 5000))
        assertEquals(LivingRules.Window(700, 300), LivingRules.window(clip, 100, Long.MAX_VALUE))
        assertEquals(LivingRules.Window(1000, 0), LivingRules.window(clip, 400, -1))
        for (position in 0L..400L) for (requested in listOf(-1L, 0L, 1L, 399L, 400L, 401L, Long.MAX_VALUE)) {
            val w = LivingRules.window(clip, position, requested)!!
            assertTrue(w.start >= 600 && w.count >= 0 && w.start + w.count <= 1000)
        }
    }

    @Test fun `a position outside the clip is no read at all`() {
        val clip = LivingRules.Clip(600, 400)
        assertNull(LivingRules.window(clip, -1, -1))
        assertNull(LivingRules.window(clip, 401, -1))
        assertNull(LivingRules.window(clip, Long.MAX_VALUE, 10))
    }

    // ---- the reader's answer through the bounds (what LivingImages does with a file) ---------------------------------

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun segment(marker: Int, payload: ByteArray) = bytes(0xFF, marker, (payload.size + 2) ushr 8, (payload.size + 2) and 0xFF) + payload
    private val scan = segment(0xDA, ByteArray(10)) + ByteArray(200) { (it % 251).toByte() } + bytes(0xFF, 0xD9)
    private val jpeg = bytes(0xFF, 0xD8) + segment(0xE0, "JFIF\u0000".toByteArray() + ByteArray(9)) + scan
    private val mp4 = bytes(0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(12) + bytes(0, 0, 1, 0) + "mdat".toByteArray() + ByteArray(248)

    /** What `LivingImages.read` does, on a byte array: the reader on the head and the size, then the bounds. */
    private fun living(file: ByteArray): LivingRules.Clip? {
        val head = file.copyOf(minOf(file.size, MotionPhoto.HEAD_BYTES))
        val info = MotionPhoto.read(head, file.size.toLong()) { offset, count ->
            if (offset < 0 || offset + count > file.size) null else file.copyOfRange(offset.toInt(), offset.toInt() + count)
        } ?: return null
        return LivingRules.clip(info.videoOffset, info.videoLength, file.size.toLong()).clip
    }

    @Test fun `a motion photo's bytes are a living image, with the clip where the file's tail is`() {
        val file = MotionPhoto.write(jpeg, mp4, 500_000L)
        assertEquals(LivingRules.Clip((file.size - mp4.size).toLong(), mp4.size.toLong()), living(file))
    }

    @Test fun `a plain jpeg, a png's bytes and a jpeg with a stray mp4 on its end are plain stills`() {
        assertNull(living(jpeg))
        assertNull(living(jpeg + mp4))
        assertNull(living(bytes(0x89, 0x50, 0x4E, 0x47) + ByteArray(64)))
        assertNull(living(ByteArray(0)))
    }

    @Test fun `a motion photo whose stated length lies about the file is a plain still`() {
        fun lying(length: String, padding: String = "0") = bytes(0xFF, 0xD8) + segment(
            0xE1,
            ("http://ns.adobe.com/xap/1.0/\u0000" + MotionPhoto.xmp(mp4.size.toLong(), 0L)
                .replace("Item:Length=\"${mp4.size}\" Item:Padding=\"0\"", "Item:Length=\"$length\" Item:Padding=\"$padding\"")).toByteArray(),
        ) + scan + mp4
        assertNotNull(living(lying(mp4.size.toString())))
        for (length in listOf("0", "-${mp4.size}", "99999999", "9223372036854775807", "-9223372036854775808", "abc", "")) {
            assertNull("length $length", living(lying(length)))
        }
    }

    // ---- the lines ---------------------------------------------------------------------------------------------------

    @Test fun `the play line and the four stop forms`() {
        assertEquals("living 1234: play 987654 bytes", LivingRules.playLine("1234", 987_654))
        assertEquals("living 1234: stop (released)", LivingRules.stopLine("1234", LivingRules.Stop.RELEASED))
        assertEquals("living 1234: stop (ended)", LivingRules.stopLine("1234", LivingRules.Stop.ENDED))
        assertEquals("living 1234: stop (left)", LivingRules.stopLine("1234", LivingRules.Stop.LEFT))
        assertEquals("living external: stop (error ERROR_CODE_PARSING_CONTAINER_MALFORMED)", LivingRules.errorLine("external", "ERROR_CODE_PARSING_CONTAINER_MALFORMED"))
    }

    @Test fun `an error's reason stays one short line`() {
        val line = LivingRules.errorLine("5", "a\nb" + "x".repeat(200))
        assertFalse(line.contains('\n'))
        assertEquals("living 5: stop (error ".length + 80 + 1, line.length)
    }

    // ---- the clip's picture over the still ---------------------------------------------------------------------------

    @Test fun `the clip covers the still's box with its own proportions`() {
        // A 4:3 still's box, a 16:9 clip: the clip is as tall as the box and wider than it.
        assertEquals(1440f to 810f, LivingRules.cover(1080f, 810f, 1920, 1080))
        // A portrait box, a portrait clip of the same shape: exactly the box.
        assertEquals(1080f to 1440f, LivingRules.cover(1080f, 1440f, 720, 960))
        // A narrower clip than the box: as wide as the box and taller.
        assertEquals(1080f to 1920f, LivingRules.cover(1080f, 1440f, 1080, 1920))
    }

    @Test fun `before the clip's size is known the box itself is used`() {
        assertEquals(1080f to 810f, LivingRules.cover(1080f, 810f, 0, 0))
        assertEquals(1080f to 810f, LivingRules.cover(1080f, 810f, -1, 720))
    }
}

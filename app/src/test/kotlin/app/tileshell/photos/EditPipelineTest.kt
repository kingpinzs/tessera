package app.tileshell.photos

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** The editor's pure parts: geometry, red-eye, the strip encoders, the EXIF block, the MP4 date, the trim arithmetic. */
class EditPipelineTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun parts(p: Int) = listOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

    /** Renders [plan] from a whole in-memory source, the way the save renders a tile. */
    private fun render(plan: EditPlan, src: IntArray): IntArray {
        val out = IntArray(plan.outW * plan.outH)
        EditGeometry.sampleTile(plan, src, PxRect(0, 0, plan.srcW, plan.srcH), 0, 0, plan.outW, plan.outH, out, 0, plan.outW)
        return out
    }

    // ---- geometry

    @Test
    fun `straighten keeps the largest rectangle inside the turned picture, the gate's numbers`() {
        // docs/plan/qa/phase-17/scripts/edit_expect.py straighten 640 480 -10 prints 582 384.
        assertEquals(582 to 384, EditGeometry.straightenSize(640, 480, -10.0))
        assertEquals(582 to 384, EditGeometry.straightenSize(640, 480, 10.0))
        assertEquals(384 to 582, EditGeometry.straightenSize(480, 640, 10.0))
        assertEquals(640 to 480, EditGeometry.straightenSize(640, 480, 0.0))
        // A long thin picture is half-constrained: its short side decides.
        val (w, h) = EditGeometry.straightenSize(1000, 100, 30.0)
        assertEquals(100, w)
        assertEquals(57, h)
        // 45 degrees of a square: the inner square of half the area.
        assertEquals(707 to 707, EditGeometry.straightenSize(1000, 1000, 45.0))
    }

    @Test
    fun `no edit is the picture itself`() {
        val src = IntArray(12) { rgb(it, it * 2, it * 3) }
        val plan = EditPlan(4, 3)
        assertEquals(4 to 3, plan.outW to plan.outH)
        assertArrayEquals(src, render(plan, src))
    }

    @Test
    fun `rotate turns clockwise and swaps the sides`() {
        // 3 x 2:  0 1 2        clockwise:  3 0
        //         3 4 5                    4 1
        //                                  5 2
        val src = IntArray(6) { it }
        val once = EditPlan(3, 2, quarterTurns = 1)
        assertEquals(2 to 3, once.outW to once.outH)
        assertArrayEquals(intArrayOf(3, 0, 4, 1, 5, 2), render(once, src))
        assertArrayEquals(intArrayOf(5, 4, 3, 2, 1, 0), render(EditPlan(3, 2, quarterTurns = 2), src))
        assertArrayEquals(intArrayOf(2, 5, 1, 4, 0, 3), render(EditPlan(3, 2, quarterTurns = 3), src))
        assertArrayEquals(src, render(EditPlan(3, 2, quarterTurns = 4), src))
        assertArrayEquals(render(EditPlan(3, 2, quarterTurns = 3), src), render(EditPlan(3, 2, quarterTurns = -1), src))
    }

    @Test
    fun `the centre half of 640 x 480 is 320 x 240 from 160, 120`() {
        val plan = EditPlan(640, 480, crop = NormRect(0.25, 0.25, 0.75, 0.75))
        assertEquals(320 to 240, plan.outW to plan.outH)
        val p = DoubleArray(2)
        plan.source(0.5, 0.5, p)
        assertArrayEquals(doubleArrayOf(160.5, 120.5), p, 1e-9)
        assertEquals(PxRect(160, 120, 480, 360), plan.sourceBounds(0, 0, 320, 240, 0))
    }

    @Test
    fun `a crop after a rotate is a crop of the turned picture`() {
        val src = IntArray(6) { it }
        // Turned: 3 0 / 4 1 / 5 2; its bottom two rows.
        val plan = EditPlan(3, 2, quarterTurns = 1, crop = NormRect(0.0, 1.0 / 3, 1.0, 1.0))
        assertEquals(2 to 2, plan.outW to plan.outH)
        assertArrayEquals(intArrayOf(4, 1, 5, 2), render(plan, src))
    }

    @Test
    fun `straightening by minus ten degrees lays a line that rises at ten degrees flat`() {
        // A black line through the centre of a white 400 x 300 picture, rising to the right at 10 degrees.
        val w = 400
        val h = 300
        val tan = Math.tan(Math.toRadians(10.0))
        val src = IntArray(w * h) { i ->
            val x = i % w - w / 2.0
            val y = i / w - h / 2.0
            if (abs(y + x * tan) <= 2.0) rgb(0, 0, 0) else rgb(255, 255, 255)
        }
        val plan = EditPlan(w, h, straighten = -10.0)
        assertEquals(EditGeometry.straightenSize(w, h, -10.0), plan.outW to plan.outH)
        assertFalse(plan.exact)
        val out = render(plan, src)
        val mid = plan.outH / 2
        // The line's row is dark from edge to edge, and ten rows above and below it are white from edge to edge.
        for (x in 0 until plan.outW) {
            assertTrue("dark at $x", parts(out[mid * plan.outW + x])[0] < 60)
            assertEquals("white above at $x", 255, parts(out[(mid - 10) * plan.outW + x])[0])
            assertEquals("white below at $x", 255, parts(out[(mid + 10) * plan.outW + x])[0])
        }
    }

    @Test
    fun `a tile reads only the source rectangle its bounds name`() {
        val plan = EditPlan(640, 480, quarterTurns = 1)
        // The output's top-left 100 x 50 tile of a clockwise turn comes from the source's bottom-left 50 x 100.
        assertEquals(PxRect(0, 380, 50, 480), plan.sourceBounds(0, 0, 100, 50, 0))
        val tilted = EditPlan(640, 480, straighten = 5.0)
        val b = tilted.sourceBounds(0, 0, 64, 64, 2)
        assertTrue(b.width in 64..90 && b.height in 64..90)
        // Rendering by tiles equals rendering whole.
        val src = IntArray(640 * 480) { rgb(it % 251, it % 241, it % 239) }
        val whole = render(tilted, src)
        val tiled = IntArray(whole.size)
        var y = 0
        while (y < tilted.outH) {
            var x = 0
            val th = minOf(64, tilted.outH - y)
            while (x < tilted.outW) {
                val tw = minOf(96, tilted.outW - x)
                val r = tilted.sourceBounds(x, y, x + tw, y + th, 2)
                val part = IntArray(r.width * r.height) { i -> src[(r.top + i / r.width) * 640 + r.left + i % r.width] }
                EditGeometry.sampleTile(tilted, part, r, x, y, tw, th, tiled, y * tilted.outW + x, tilted.outW)
                x += tw
            }
            y += th
        }
        assertArrayEquals(whole, tiled)
    }

    // ---- the crop rectangle

    @Test
    fun `dragging the crop's corners from 70 percent to the centre half`() {
        var r = NormRect(0.15, 0.15, 0.85, 0.85)
        r = CropMath.dragCorner(r, left = true, top = true, dx = 0.10, dy = 0.10, aspect = null, frameAspect = 4.0 / 3)
        r = CropMath.dragCorner(r, left = false, top = false, dx = -0.10, dy = -0.10, aspect = null, frameAspect = 4.0 / 3)
        assertEquals(0.25, r.left, 1e-9); assertEquals(0.25, r.top, 1e-9); assertEquals(0.75, r.right, 1e-9); assertEquals(0.75, r.bottom, 1e-9)
        assertEquals(320 to 240, EditPlan(640, 480, crop = r).let { it.outW to it.outH })
    }

    @Test
    fun `a corner never crosses its opposite or leaves the picture`() {
        val r = NormRect(0.2, 0.2, 0.8, 0.8)
        val crushed = CropMath.dragCorner(r, left = true, top = true, dx = 5.0, dy = 5.0, aspect = null, frameAspect = 1.0)
        assertEquals(0.8 - CropMath.MIN, crushed.left, 1e-9)
        assertEquals(0.8 - CropMath.MIN, crushed.top, 1e-9)
        val out = CropMath.dragCorner(r, left = false, top = false, dx = 5.0, dy = 5.0, aspect = null, frameAspect = 1.0)
        assertEquals(1.0, out.right, 1e-9); assertEquals(1.0, out.bottom, 1e-9)
    }

    @Test
    fun `a locked aspect is kept by a drag and applied on the centre`() {
        // A 4:3 frame; a square crop is three quarters as wide as it is tall, in fractions.
        val square = CropMath.withAspect(NormRect(0.0, 0.0, 1.0, 1.0), 1.0, 4.0 / 3)
        assertEquals(0.75, square.right - square.left, 1e-9)
        assertEquals(1.0, square.bottom - square.top, 1e-9)
        assertEquals(0.5, (square.left + square.right) / 2, 1e-9)
        val dragged = CropMath.dragCorner(NormRect(0.2, 0.2, 0.5, 0.6), left = false, top = false, dx = 0.15, dy = 0.0, aspect = 1.0, frameAspect = 4.0 / 3)
        val wPx = (dragged.right - dragged.left) * 4
        val hPx = (dragged.bottom - dragged.top) * 3
        assertEquals(wPx, hPx, 1e-9)
        assertEquals(0.2, dragged.left, 1e-9); assertEquals(0.2, dragged.top, 1e-9)
        assertTrue(dragged.bottom <= 1.0 && dragged.right <= 1.0)
    }

    @Test
    fun `the edit's tool name lists what is set, in a fixed order`() {
        assertEquals("", EditState().toolName)
        assertEquals("enhance", EditState(recipe = EditMatrices.Recipe(enhance = true)).toolName)
        assertEquals("filter:sepia", EditState(recipe = EditMatrices.Recipe(filter = "sepia")).toolName)
        assertEquals("rotate", EditState(quarterTurns = 5).toolName)
        assertEquals("", EditState(quarterTurns = 4).toolName)
        assertEquals("crop+rotate+straighten+filter:mono+light+colour+enhance",
            EditState(quarterTurns = 1, straighten = -3, crop = NormRect(0.1, 0.1, 0.9, 0.9), recipe = EditMatrices.Recipe("mono", 1, -1, true)).toolName)
    }

    @Test
    fun `the preview's render is the plan, red-eye first, then the colour steps`() {
        // 4 x 2, red-eye blob over the left 6 x 6 of a larger found picture is not needed here: geometry and colour only.
        val src = IntArray(8) { rgb(40 + it, 180, 80) }
        val state = EditState(quarterTurns = 2, recipe = EditMatrices.Recipe(light = 1))
        val (out, w, h) = EditRender.renderInto(state, src, 4, 2, applyCrop = true)
        assertEquals(4 to 2, w to h)
        assertEquals(listOf(40 + 7 + 20, 200, 100), parts(out[0]))
        assertEquals(listOf(40 + 0 + 20, 200, 100), parts(out[7]))
        // The crop is left out while the crop page shows the whole picture.
        val cropped = EditState(crop = NormRect(0.0, 0.0, 0.5, 1.0))
        assertEquals(2, EditRender.renderInto(cropped, src, 4, 2, applyCrop = true).second)
        assertEquals(4, EditRender.renderInto(cropped, src, 4, 2, applyCrop = false).second)
    }

    // ---- red-eye

    private fun redeyePicture(w: Int, h: Int, cx: Int, cy: Int, radius: Int): IntArray = IntArray(w * h) { i ->
        val dx = i % w - cx
        val dy = i / w - cy
        if (dx * dx + dy * dy <= radius * radius) rgb(255, 0, 0) else rgb(128, 128, 128)
    }

    @Test
    fun `red dominance is r over one and a half times the larger of g and b`() {
        assertTrue(RedEye.isRed(rgb(255, 0, 0)))
        assertTrue(RedEye.isRed(rgb(151, 100, 20)))
        assertFalse(RedEye.isRed(rgb(150, 100, 20)))
        assertFalse(RedEye.isRed(rgb(128, 128, 128)))
        assertFalse(RedEye.isRed(rgb(220, 40, 200)))
    }

    @Test
    fun `a red disc is one blob and its red is halved, nothing outside changes`() {
        val w = 120
        val h = 90
        val pixels = redeyePicture(w, h, 60, 45, 20)
        val blobs = RedEye.detect(pixels, w, h)
        assertEquals(1, blobs.size)
        assertEquals(41, blobs[0].width)
        assertEquals(41, blobs[0].height)
        val fixed = IntArray(pixels.size) { i -> if (RedEye.inBlobs(blobs, i % w, i / w, w, h, w, h)) RedEye.fix(pixels[i]) else pixels[i] }
        assertEquals(listOf(127, 0, 0), parts(fixed[45 * w + 60]))
        assertTrue("the red channel is at most half", parts(fixed[45 * w + 60])[0] * 2 <= 255)
        // 30 px outside the disc's edge, and right beside it: untouched.
        assertEquals(pixels[45 * w + 110], fixed[45 * w + 110])
        assertEquals(pixels[45 * w + 81], fixed[45 * w + 81])
        assertEquals(blobs[0].pixelCount, fixed.indices.count { fixed[it] != pixels[it] })
    }

    @Test
    fun `a blob under six pixels across is left alone`() {
        val w = 40
        val h = 40
        val pixels = IntArray(w * h) { i -> if (i % w in 10..14 && i / w in 10..30) rgb(255, 0, 0) else rgb(90, 90, 90) }
        assertEquals("5 wide, 21 tall", 0, RedEye.detect(pixels, w, h).size)
        val six = IntArray(w * h) { i -> if (i % w in 10..15 && i / w in 10..15) rgb(255, 0, 0) else rgb(90, 90, 90) }
        assertEquals(1, RedEye.detect(six, w, h).size)
    }

    @Test
    fun `a tap finds only the blob it touches, whole`() {
        val w = 200
        val h = 80
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            val a = (x - 40) * (x - 40) + (y - 40) * (y - 40) <= 15 * 15
            val b = (x - 150) * (x - 150) + (y - 40) * (y - 40) <= 15 * 15
            if (a || b) rgb(250, 20, 20) else rgb(100, 100, 100)
        }
        assertEquals(2, RedEye.detect(pixels, w, h).size)
        // A small window on the first disc's edge: that disc, all of it, and not the other.
        val near = RedEye.detect(pixels, w, h, PxRect(50, 38, 56, 44))
        assertEquals(1, near.size)
        assertEquals(31, near[0].width)
        assertTrue(near[0].contains(26, 40))
        assertFalse(near[0].contains(150, 40))
        assertEquals(0, RedEye.detect(pixels, w, h, PxRect(90, 30, 100, 50)).size)
    }

    @Test
    fun `the preview's blobs apply at full resolution`() {
        val blobs = RedEye.detect(redeyePicture(120, 90, 60, 45, 20), 120, 90)
        // The same picture ten times larger: the disc's centre is at 600, 450 and its edge at radius 200.
        assertTrue(RedEye.inBlobs(blobs, 600, 450, 1200, 900, 120, 90))
        assertTrue(RedEye.inBlobs(blobs, 600 + 195, 450, 1200, 900, 120, 90))
        assertFalse(RedEye.inBlobs(blobs, 600 + 215, 450, 1200, 900, 120, 90))
        assertFalse(RedEye.inBlobs(emptyList(), 600, 450, 1200, 900, 120, 90))
    }

    // ---- the strip encoders

    private fun encodeJpeg(w: Int, h: Int, exif: ByteArray?, strip: Int, pixel: (Int, Int) -> Int): ByteArray {
        val out = ByteArrayOutputStream()
        val enc = JpegStripEncoder(out, w, h, 95, exif)
        var y = 0
        while (y < h) {
            val rows = minOf(strip, h - y)
            enc.writeRows(IntArray(w * rows) { i -> pixel(i % w, y + i / w) }, rows)
            y += rows
        }
        enc.finish()
        return out.toByteArray()
    }

    @Test
    fun `a flat colour survives the JPEG encoder within two per channel, at any size and strip height`() {
        for ((w, h, strip) in listOf(Triple(64, 48, 16), Triple(37, 21, 5), Triple(16, 16, 16), Triple(1, 1, 1), Triple(130, 70, 64))) {
            for (colour in listOf(listOf(220, 40, 40), listOf(40, 180, 80), listOf(40, 90, 220), listOf(240, 240, 240), listOf(0, 0, 0))) {
                val bytes = encodeJpeg(w, h, null, strip) { _, _ -> rgb(colour[0], colour[1], colour[2]) }
                val image = ImageIO.read(ByteArrayInputStream(bytes))
                assertEquals(w to h, image.width to image.height)
                for ((x, y) in listOf(0 to 0, w - 1 to h - 1, w / 2 to h / 2)) {
                    val got = parts(image.getRGB(x, y))
                    for (c in 0..2) assertTrue("$colour at $x,$y of ${w}x$h: $got", abs(got[c] - colour[c]) <= 2)
                }
            }
        }
    }

    @Test
    fun `a picture with detail decodes close to what went in`() {
        val w = 96
        val h = 80
        val pixel = { x: Int, y: Int -> rgb((x * 255) / (w - 1), (y * 255) / (h - 1), 128 + ((x / 8 + y / 8) % 2) * 60) }
        val image = ImageIO.read(ByteArrayInputStream(encodeJpeg(w, h, null, 16, pixel)))
        var worst = 0
        var total = 0L
        for (y in 0 until h) for (x in 0 until w) {
            val got = parts(image.getRGB(x, y))
            val want = parts(pixel(x, y))
            for (c in 0..2) { worst = maxOf(worst, abs(got[c] - want[c])); total += abs(got[c] - want[c]) }
        }
        // Quality 95 with halved chroma: small on average, bounded at the checker's edges.
        assertTrue("mean error ${total.toDouble() / (w * h * 3)}", total.toDouble() / (w * h * 3) < 4.0)
        assertTrue("worst error $worst", worst < 70)
    }

    @Test(expected = IllegalStateException::class)
    fun `the JPEG encoder refuses to finish a picture with rows missing`() {
        val enc = JpegStripEncoder(ByteArrayOutputStream(), 8, 8, 95, null)
        enc.writeRows(IntArray(8 * 4), 4)
        enc.finish()
    }

    @Test
    fun `the JPEG carries the EXIF block in APP1 and still decodes`() {
        val exif = ExifBlock.build("2024:07:04 09:08:07", "-06:00", "123")
        val bytes = encodeJpeg(32, 32, exif, 16) { _, _ -> rgb(10, 200, 30) }
        // SOI, APP0 (JFIF, 16 bytes of payload and length), then APP1 "Exif\0\0".
        assertEquals(0xFF, bytes[0].toInt() and 0xFF); assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        assertEquals(0xE1, bytes[21].toInt() and 0xFF)
        assertEquals("Exif", String(bytes, 24, 4, Charsets.US_ASCII))
        assertArrayEquals(exif, bytes.copyOfRange(30, 30 + exif.size))
        assertEquals(32, ImageIO.read(ByteArrayInputStream(bytes)).width)
    }

    /** A minimal TIFF reader: tag -> value, for IFD0 and the Exif IFD it points at. */
    private fun readExif(tiff: ByteArray): Map<Int, Any> {
        fun u16(at: Int) = ((tiff[at].toInt() and 0xFF) shl 8) or (tiff[at + 1].toInt() and 0xFF)
        fun u32(at: Int) = (u16(at) shl 16) or u16(at + 2)
        val found = HashMap<Int, Any>()
        fun ifd(at: Int) {
            val n = u16(at)
            for (i in 0 until n) {
                val e = at + 2 + 12 * i
                val tag = u16(e)
                val type = u16(e + 2)
                val count = u32(e + 4)
                found[tag] = when (type) {
                    3 -> u16(e + 8)
                    4 -> u32(e + 8)
                    else -> {
                        val from = if (count <= 4) e + 8 else u32(e + 8)
                        String(tiff, from, count - 1, Charsets.US_ASCII)
                    }
                }
            }
            assertEquals("no further IFD", 0, u32(at + 2 + 12 * n))
        }
        assertEquals("MM", String(tiff, 0, 2, Charsets.US_ASCII))
        assertEquals(42, u16(2))
        ifd(u32(4))
        (found[0x8769] as? Int)?.let { ifd(it) }
        return found
    }

    @Test
    fun `the EXIF block says upright and carries the original's capture time`() {
        val tags = readExif(ExifBlock.build("2024:07:04 09:08:07", "-06:00", "123"))
        assertEquals(1, tags[0x0112])
        assertEquals("2024:07:04 09:08:07", tags[0x0132])
        assertEquals("2024:07:04 09:08:07", tags[0x9003])
        assertEquals("-06:00", tags[0x9011])
        assertEquals("123", tags[0x9291])
        val bare = readExif(ExifBlock.build(null, "-06:00", null))
        assertEquals(mapOf<Int, Any>(0x0112 to 1), bare)
        val dateOnly = readExif(ExifBlock.build("2020:01:02 03:04:05", null, null))
        assertEquals("2020:01:02 03:04:05", dateOnly[0x9003])
        assertNull(dateOnly[0x9011])
    }

    @Test
    fun `the PNG writer is lossless, with and without alpha, in strips`() {
        val w = 33
        val h = 19
        for (alpha in listOf(false, true)) {
            val pixel = { x: Int, y: Int -> ((if (alpha) (x * 7) and 0xFF else 0xFF) shl 24) or ((x * 5 and 0xFF) shl 16) or ((y * 11 and 0xFF) shl 8) or ((x + y) and 0xFF) }
            val out = ByteArrayOutputStream()
            val png = PngStripWriter(out, w, h, alpha, ExifBlock.build("2024:07:04 09:08:07", null, null))
            var y = 0
            while (y < h) {
                val rows = minOf(4, h - y)
                png.writeRows(IntArray(w * rows) { i -> pixel(i % w, y + i / w) }, rows)
                y += rows
            }
            png.finish()
            val bytes = out.toByteArray()
            assertTrue("an eXIf chunk", String(bytes, Charsets.ISO_8859_1).contains("eXIf"))
            val image = ImageIO.read(ByteArrayInputStream(bytes))
            assertEquals(w to h, image.width to image.height)
            for (yy in 0 until h) for (x in 0 until w) {
                val want = pixel(x, yy)
                val got = image.getRGB(x, yy)
                // A fully transparent pixel's colour is not carried by getRGB on every reader: compare alpha, and colour where visible.
                assertEquals(want ushr 24, got ushr 24)
                if (want ushr 24 != 0) assertEquals("at $x,$yy", want and 0xFFFFFF, got and 0xFFFFFF)
            }
        }
    }

    @Test
    fun `a large flat PNG is many IDAT chunks and still one picture`() {
        val w = 1500
        val h = 600
        val out = ByteArrayOutputStream()
        val png = PngStripWriter(out, w, h, false, null)
        val rnd = java.util.Random(3)
        val rows = IntArray(w * 100) { rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }
        repeat(6) { png.writeRows(rows, 100) }
        png.finish()
        val bytes = out.toByteArray()
        assertTrue(Regex("IDAT").findAll(String(bytes, Charsets.ISO_8859_1)).count() > 5)
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(rows[5 * w + 7] and 0xFFFFFF, image.getRGB(7, 505) and 0xFFFFFF)
    }

    // ---- the MP4 date

    private fun box(type: String, payload: ByteArray): ByteArray {
        val n = payload.size + 8
        return byteArrayOf((n ushr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()) + type.toByteArray(Charsets.US_ASCII) + payload
    }

    @Test
    fun `the movie header's creation time is set in place, both header versions`() {
        val want = 1_720_105_687_000L // 2024-07-04T15:08:07Z
        for (version in 0..1) {
            val mvhd = ByteArray(if (version == 1) 112 else 100).also { it[0] = version.toByte() }
            val file = File.createTempFile("photos-mp4", ".mp4")
            try {
                file.writeBytes(box("ftyp", "isom0000".toByteArray()) + box("free", ByteArray(5)) + box("moov", box("udta", ByteArray(3)) + box("mvhd", mvhd) + box("trak", ByteArray(40))) + box("mdat", ByteArray(64)))
                val size = file.length()
                assertEquals(-2_082_844_800_000L, Mp4Dates.creationTime(file))
                assertTrue(Mp4Dates.setCreationTime(file, want))
                assertEquals(want, Mp4Dates.creationTime(file))
                assertEquals("nothing moved", size, file.length())
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun `a file with no movie header is left alone`() {
        val file = File.createTempFile("photos-mp4", ".mp4")
        try {
            val bytes = box("ftyp", "isom0000".toByteArray()) + box("mdat", ByteArray(64)) + byteArrayOf(0, 0, 0, 3, 1)
            file.writeBytes(bytes)
            assertFalse(Mp4Dates.setCreationTime(file, 1L))
            assertNull(Mp4Dates.creationTime(file))
            assertArrayEquals(bytes, file.readBytes())
        } finally {
            file.delete()
        }
    }

    // ---- trim

    @Test
    fun `the trim handles snap to tenths and keep half a second apart`() {
        assertEquals(2000L, TrimMath.snap(2049, 10_000))
        assertEquals(2100L, TrimMath.snap(2050, 10_000))
        assertEquals(10_000L, TrimMath.snap(10_400, 10_000))
        // A 678-px track for 10 s from x = 201: 2 s is at 336.6, 5 s at 540.
        assertEquals(2000L, TrimMath.timeAt(337f, 201f, 678f, 10_000))
        assertEquals(5000L, TrimMath.timeAt(540f, 201f, 678f, 10_000))
        assertEquals(0L, TrimMath.timeAt(0f, 201f, 678f, 10_000))
        assertEquals(10_000L, TrimMath.timeAt(2000f, 201f, 678f, 10_000))
        assertEquals(540f, TrimMath.xAt(5000, 201f, 678f, 10_000), 0.01f)
        assertEquals(4500L, TrimMath.moveStart(4900, 5000))
        assertEquals(0L, TrimMath.moveStart(-5, 5000))
        assertEquals(2500L, TrimMath.moveEnd(2100, 2000, 10_000))
        assertEquals(10_000L, TrimMath.moveEnd(12_000, 2000, 10_000))
        assertEquals("0:03", TrimMath.label(3000))
        assertEquals("0:14", TrimMath.label(14_900))
        assertEquals("1:02:03", TrimMath.label(3_723_000))
    }
}

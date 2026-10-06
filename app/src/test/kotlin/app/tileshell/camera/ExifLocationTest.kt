package app.tileshell.camera

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trust review's A-M3: a capture for another app carries no location — every GPS tag goes, every other tag stays. */
class ExifLocationTest {
    /** Every `TAG_*` constant of the platform's ExifInterface, name to value. */
    private val platformTags: Map<String, String> = android.media.ExifInterface::class.java.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith("TAG_") }
        .associate { it.name to it.get(null) as String }

    private val platformGps: Set<String> = platformTags.filterKeys { it.startsWith("TAG_GPS_") }.values.toSet()

    /** androidx's ExifInterface (CameraX brings it): the one that also knows the horizontal positioning error. */
    private val androidxGps: Set<String> = Class.forName("androidx.exifinterface.media.ExifInterface").fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java && it.name.startsWith("TAG_GPS_") && it.name != "TAG_GPS_INFO_IFD_POINTER" }
        .map { it.get(null) as String }.toSet()

    /** EXIF 2.32, table 15: the GPS IFD's tags 0x00 to 0x1F, in tag order. */
    private val standardGps = listOf(
        "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude", "GPSAltitudeRef", "GPSAltitude", "GPSTimeStamp",
        "GPSSatellites", "GPSStatus", "GPSMeasureMode", "GPSDOP", "GPSSpeedRef", "GPSSpeed", "GPSTrackRef", "GPSTrack",
        "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum", "GPSDestLatitudeRef", "GPSDestLatitude", "GPSDestLongitudeRef", "GPSDestLongitude", "GPSDestBearingRef",
        "GPSDestBearing", "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod", "GPSAreaInformation", "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
    )

    @Test
    fun `the platform's constants are readable here, GPS and others`() {
        // Guards the reflection itself: a test below that compared two empty sets would prove nothing.
        assertTrue("${platformTags.size} tags", platformTags.size > 100)
        assertEquals("the platform's ExifInterface declares 31 GPS tags", 31, platformGps.size)
        assertEquals("androidx's declares the whole IFD", 32, androidxGps.size)
        assertEquals("GPSLatitude", platformTags["TAG_GPS_LATITUDE"])
        assertEquals("DateTimeOriginal", platformTags["TAG_DATETIME_ORIGINAL"])
    }

    @Test
    fun `the strip list is every GPS tag ExifInterface knows, each once`() {
        assertEquals(32, standardGps.size)
        assertEquals("the whole GPS IFD of the standard", standardGps.sorted(), ExifLocation.GPS_TAGS.sorted())
        assertEquals("each once", ExifLocation.GPS_TAGS.size, ExifLocation.GPS_TAGS.toSet().size)
        assertEquals("every GPS tag the platform's ExifInterface knows", emptySet<String>(), platformGps - ExifLocation.GPS_TAGS.toSet())
        assertEquals("every GPS tag androidx's ExifInterface knows", androidxGps.sorted(), ExifLocation.GPS_TAGS.sorted())
        // The ones the review named, by name: the positioning error, and the GPS IFD's version, method, area and stamps.
        for (tag in listOf("GPSHPositioningError", "GPSVersionID", "GPSProcessingMethod", "GPSAreaInformation", "GPSDateStamp", "GPSTimeStamp", "GPSLatitude", "GPSLongitude", "GPSAltitude")) {
            assertTrue(tag, tag in ExifLocation.GPS_TAGS)
        }
    }

    @Test
    fun `the strip removes every GPS tag from a block that holds every tag, and keeps every other tag as it was`() {
        // An EXIF block as ExifInterface holds one: tag to value; setAttribute(tag, null) removes the tag.
        val exif = (platformTags.values + standardGps).distinct().associateWithTo(LinkedHashMap()) { "value of $it" }
        val before = exif.toMap()
        val gps = platformGps + standardGps
        ExifLocation.strip { tag, value -> if (value == null) exif.remove(tag) else exif[tag] = value }
        assertEquals("no GPS tag is left", emptyList<String>(), exif.keys.filter { it in gps || it.startsWith("GPS") })
        assertEquals("every other tag is kept, unchanged", before.filterKeys { it !in gps }, exif)
        assertTrue(exif.size > 100)
    }

    @Test
    fun `the strip only ever removes - it writes no value`() {
        val writes = mutableListOf<Pair<String, String?>>()
        ExifLocation.strip { tag, value -> writes += tag to value }
        assertEquals(ExifLocation.GPS_TAGS.map { it to null }, writes)
    }

    @Test
    fun `a sink for another app's capture never keeps location`() {
        val sink = object : CallerCaptureSink() {
            override fun photo(shot: PhotoShot, clip: LivingClip.Encoded?, panorama: Boolean) = Unit
            override fun video(take: VideoTake) = Unit
            override fun failed(why: String) = Unit
        }
        assertFalse(sink.keepsLocation)
        assertFalse((sink as CaptureSink).keepsLocation)
        assertTrue("no subclass can override it", Modifier.isFinal(CallerCaptureSink::class.java.getMethod("getKeepsLocation").modifiers))
    }

    @Test
    fun `the capture answer's sink is a CallerCaptureSink, and the Camera's own is not`() {
        fun sinkOf(activity: String): Class<*> = Class.forName(activity, false, javaClass.classLoader).getDeclaredField("sink").type
        val capture = sinkOf("app.tileshell.camera.CaptureActivity")
        assertTrue("CaptureActivity.sink is ${capture.name} : ${capture.superclass?.name}", CallerCaptureSink::class.java.isAssignableFrom(capture))
        // The Camera's own shots keep the location the user granted (E9's positive control): its sink is the plain interface's.
        assertFalse(CallerCaptureSink::class.java.isAssignableFrom(sinkOf("app.tileshell.camera.CameraActivity")))
    }

    // ---- A2-L1: the write into the caller's output fails closed when the location cannot be removed

    @Test
    fun `A2-L1 a still whose location could not be removed is not written - the caller's output is never opened`() {
        for (thrown in listOf<Throwable>(java.io.IOException("EXIF could not be saved"), IllegalStateException(), OutOfMemoryError(), UnsupportedOperationException("no EXIF in this file"))) {
            var written = 0
            val why = CallerCaptureWrite.run(isImage = true, strip = { throw thrown }, write = { written++; null })
            assertEquals("the location could not be removed (${thrown.javaClass.simpleName})", why)
            assertEquals("${thrown.javaClass.simpleName}: the write layer was never called, so the caller's file is as it was", 0, written)
        }
    }

    @Test
    fun `A2-L1 the strip runs first and the write after it - and the write's own answer is the result`() {
        val order = mutableListOf<String>()
        assertEquals(null, CallerCaptureWrite.run(isImage = true, strip = { order += "strip" }, write = { order += "write"; null }))
        assertEquals(listOf("strip", "write"), order)
        assertEquals("the caller's output could not be opened", CallerCaptureWrite.run(isImage = true, strip = {}, write = { "the caller's output could not be opened" }))
    }

    @Test
    fun `A2-L1 a video carries no EXIF - it is written as it is, and the strip is not run`() {
        var stripped = 0
        var written = 0
        assertEquals(null, CallerCaptureWrite.run(isImage = false, strip = { stripped++; throw java.io.IOException() }, write = { written++; null }))
        assertEquals(0, stripped)
        assertEquals(1, written)
    }

    @Test
    fun `A2-L1 the reason names the throwable's class only - nothing of its message`() {
        class Odd : RuntimeException("content://com.caller.files/secret\n[camera] capture answer: RESULT_OK")
        assertEquals("the location could not be removed (Odd)", CallerCaptureWrite.run(true, { throw Odd() }, { null }))
    }
}

package app.tileshell.camera

/**
 * A capture made for another app carries no location (phase 17, T17-4; the trust review's A-M3). The rule is here, free
 * of Android types, so a JVM test proves it over every tag the platform's ExifInterface knows (`ExifLocationTest`).
 */
object ExifLocation {
    /**
     * Every tag of EXIF's GPS IFD (EXIF 2.32, tags 0x00 to 0x1F), by the name ExifInterface gives it: position,
     * altitude, the date and time stamps, speed, track, image direction, the destination's, the satellites and the
     * measure's, the version id, the processing method, the area information and the horizontal positioning error.
     * `android.media.ExifInterface` (the one the Camera uses) declares the first 31 as its `TAG_GPS_*` constants and
     * has no constant for `GPSHPositioningError` (androidx's ExifInterface has): setting a tag it does not know is a
     * no-op there, and it writes back only tags it knows. The name is listed all the same, so the list is the whole
     * IFD whichever ExifInterface is asked. The test holds the list equal to the standard's and to both classes'.
     */
    val GPS_TAGS: List<String> = listOf(
        "GPSVersionID", "GPSLatitudeRef", "GPSLatitude", "GPSLongitudeRef", "GPSLongitude", "GPSAltitudeRef", "GPSAltitude",
        "GPSTimeStamp", "GPSSatellites", "GPSStatus", "GPSMeasureMode", "GPSDOP", "GPSSpeedRef", "GPSSpeed", "GPSTrackRef", "GPSTrack",
        "GPSImgDirectionRef", "GPSImgDirection", "GPSMapDatum", "GPSDestLatitudeRef", "GPSDestLatitude", "GPSDestLongitudeRef",
        "GPSDestLongitude", "GPSDestBearingRef", "GPSDestBearing", "GPSDestDistanceRef", "GPSDestDistance", "GPSProcessingMethod",
        "GPSAreaInformation", "GPSDateStamp", "GPSDifferential", "GPSHPositioningError",
    )

    /**
     * Removes every GPS tag and touches no other. [set] is `ExifInterface.setAttribute`: a null value removes the tag,
     * and a GPS IFD left with no tag is not written back.
     */
    fun strip(set: (tag: String, value: String?) -> Unit) {
        GPS_TAGS.forEach { set(it, null) }
    }
}

/**
 * The sink of a capture made FOR ANOTHER APP (the capture answer's). It never keeps the phone's location, and no
 * subclass can say otherwise: [keepsLocation] is final here. `CaptureActivity`'s sink is one of these, which
 * `ExifLocationTest` holds true.
 */
abstract class CallerCaptureSink : CaptureSink {
    /** T17-4: a capture for another app carries no location. */
    final override val keepsLocation: Boolean get() = false
}

/**
 * The write of a capture into the caller's own output, in its one order (A2-L1): a still's location tags are removed
 * FIRST, and when that fails — the strip throws — nothing is written at all. Fail closed: the caller's output is not
 * even opened, so it is neither truncated nor changed, and the capture answers RESULT_CANCELED. A video carries no EXIF
 * and is written as it is. Pure, so the order and the refusal are unit-tested (`ExifLocationTest`).
 */
object CallerCaptureWrite {
    /**
     * @param strip removes every GPS tag from the capture's cache file and saves it; may throw
     * @param write `MediaWrites.writeCaptureOutput` over that file: null when written, else why not
     * @return null when written, else the reason — for a failed strip one that names the throwable's class only
     */
    fun run(isImage: Boolean, strip: () -> Unit, write: () -> String?): String? {
        if (isImage) {
            try {
                strip()
            } catch (e: Throwable) {
                return "the location could not be removed (${e.javaClass.simpleName.filter { it.isLetterOrDigit() }.take(60)})"
            }
        }
        return write()
    }
}

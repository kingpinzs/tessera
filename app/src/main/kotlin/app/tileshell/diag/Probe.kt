package app.tileshell.diag

import android.app.ActivityManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns

/**
 * The probe (phase 17, r3 V15): facts a phone row needs, read on the phone and shown as text on Settings > Diagnostics >
 * Probe, so no row uses adb, exiftool or ffprobe. Three reads: the cameras the public API exposes with the
 * characteristics the Camera's mode gates use (the same keys E7 reads on the emulator from `dumpsys media.camera`); the
 * shell's own processes with each one's PSS; and, for a picture or video the user picks, its EXIF tags, its Motion
 * Photo marker, its tracks, duration, dimensions and size. Read-only: nothing here opens a camera or writes a file.
 */
object Probe {
    /** The last probe text of each kind, so Diagnostics' "Copy everything" carries it. */
    @Volatile var lastCameras: String = ""
    @Volatile var lastProcesses: String = ""
    @Volatile var lastFile: String = ""

    fun cameras(context: Context): String = buildString {
        val manager = context.getSystemService(CameraManager::class.java)
        val ids = runCatching { manager.cameraIdList.toList() }.getOrElse { append("cameras: read failed (${it.javaClass.simpleName})\n"); emptyList() }
        append("cameras: ${ids.size} id(s) ${ids.joinToString(",")}\n")
        for (id in ids) {
            val c = runCatching { manager.getCameraCharacteristics(id) }.getOrNull()
            if (c == null) { append("camera $id: characteristics unreadable\n"); continue }
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "back"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                else -> "unknown"
            }
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
            val map: StreamConfigurationMap? = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            append("camera $id: facing=$facing\n")
            append("camera $id: hardwareLevel=${c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}\n")
            append("camera $id: capabilities=${caps.sorted().joinToString(",")} (${caps.sorted().joinToString(",") { capabilityName(it) }})\n")
            append("camera $id: physicalIds=${runCatching { c.physicalCameraIds.sorted().joinToString(",") }.getOrDefault("")}\n")
            append("camera $id: aeModes=${c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.joinToString(",")}\n")
            append("camera $id: aeCompensationRange=${c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)} step=${c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)}\n")
            append("camera $id: afModes=${c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.joinToString(",")}\n")
            append("camera $id: minimumFocusDistance=${c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)}\n")
            append("camera $id: awbModes=${c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.joinToString(",")}\n")
            append("camera $id: sensitivityRange=${c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}\n")
            append("camera $id: exposureTimeRangeNs=${c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}\n")
            append("camera $id: maxDigitalZoom=${c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}\n")
            append("camera $id: flash=${c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)}\n")
            append("camera $id: pixelArraySize=${c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}\n")
            append("camera $id: sensorOrientation=${c.get(CameraCharacteristics.SENSOR_ORIENTATION)}\n")
            append("camera $id: highSpeedFpsRanges=${runCatching { map?.highSpeedVideoFpsRanges?.joinToString(",") }.getOrNull().orEmpty().ifEmpty { "none" }}\n")
            append("camera $id: highSpeedSizes=${runCatching { map?.highSpeedVideoSizes?.joinToString(",") }.getOrNull().orEmpty().ifEmpty { "none" }}\n")
        }
    }.also { lastCameras = it }

    private fun capabilityName(value: Int): String = when (value) {
        0 -> "BACKWARD_COMPATIBLE"; 1 -> "MANUAL_SENSOR"; 2 -> "MANUAL_POST_PROCESSING"; 3 -> "RAW"
        4 -> "PRIVATE_REPROCESSING"; 5 -> "READ_SENSOR_SETTINGS"; 6 -> "BURST_CAPTURE"; 7 -> "YUV_REPROCESSING"
        8 -> "DEPTH_OUTPUT"; 9 -> "CONSTRAINED_HIGH_SPEED_VIDEO"; 10 -> "MOTION_TRACKING"; 11 -> "LOGICAL_MULTI_CAMERA"
        12 -> "MONOCHROME"; 13 -> "SECURE_IMAGE_DATA"; 14 -> "SYSTEM_CAMERA"; 15 -> "OFFLINE_PROCESSING"
        16 -> "ULTRA_HIGH_RESOLUTION_SENSOR"; 17 -> "REMOSAIC_REPROCESSING"; 18 -> "DYNAMIC_RANGE_TEN_BIT"
        19 -> "STREAM_USE_CASE"; 20 -> "COLOR_SPACE_PROFILES"
        else -> "cap$value"
    }

    /** The shell's own processes with each one's PSS in kB. Android may answer a repeated read from a recent sample. */
    fun processes(context: Context): String = buildString {
        val am = context.getSystemService(ActivityManager::class.java)
        val own = am.runningAppProcesses.orEmpty().filter { it.uid == android.os.Process.myUid() }.sortedBy { it.processName }
        append("processes: ${own.size}\n")
        if (own.isNotEmpty()) {
            val info = runCatching { am.getProcessMemoryInfo(own.map { it.pid }.toIntArray()) }.getOrNull()
            own.forEachIndexed { i, p -> append("process ${p.processName} pid=${p.pid} pssKb=${info?.getOrNull(i)?.totalPss ?: "unreadable"}\n") }
        }
    }.also { lastProcesses = it }

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH, ExifInterface.TAG_PIXEL_X_DIMENSION, ExifInterface.TAG_PIXEL_Y_DIMENSION,
        ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_SUBJECT_DISTANCE, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    )

    /**
     * The facts of one picked picture or video. [motionPhoto] reads the Motion Photo marker from the file's bytes (the
     * Camera's own reader, passed in so this object needs nothing of the Camera's). GPS is reported as present or
     * absent, never its value.
     */
    fun file(context: Context, uri: Uri, motionPhoto: (ByteArray) -> String): String = buildString {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0); size = if (c.isNull(1)) null else c.getLong(1) }
            }
        }
        append("file: name=$name mime=$mime sizeBytes=$size\n")
        if (mime?.startsWith("image/") == true) {
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    for (tag in EXIF_TAGS) exif.getAttribute(tag)?.let { append("exif $tag=$it\n") }
                    append("exif GPS=${if (exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null) "present" else "absent"}\n")
                }
            }.onFailure { append("exif: unreadable (${it.javaClass.simpleName})\n") }
            runCatching {
                // The marker and the directory sit in the first segment and the clip at the end: the whole file is read
                // only up to a bound, which a still with a short clip stays under.
                val bytes = resolver.openInputStream(uri)?.use { it.readNBytes(MOTION_PHOTO_READ_LIMIT) } ?: ByteArray(0)
                append("motionPhoto: ${motionPhoto(bytes)}\n")
            }.onFailure { append("motionPhoto: unreadable (${it.javaClass.simpleName})\n") }
        }
        // Tracks: a video's, and a Motion Photo's none (its clip is inside the still) — an image simply lists no track.
        val extractor = MediaExtractor()
        runCatching {
            extractor.setDataSource(context, uri, null)
            append("tracks: ${extractor.trackCount}\n")
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                fun int(key: String) = if (f.containsKey(key)) runCatching { f.getInteger(key).toString() }.getOrElse { runCatching { f.getFloat(key).toString() }.getOrDefault("?") } else "-"
                fun long(key: String) = if (f.containsKey(key)) runCatching { f.getLong(key).toString() }.getOrDefault("?") else "-"
                append("track $i: mime=${f.getString(MediaFormat.KEY_MIME)} width=${int(MediaFormat.KEY_WIDTH)} height=${int(MediaFormat.KEY_HEIGHT)} ")
                append("frameRate=${int(MediaFormat.KEY_FRAME_RATE)} captureRate=${int(MediaFormat.KEY_CAPTURE_RATE)} rotation=${int(MediaFormat.KEY_ROTATION)} ")
                append("durationUs=${long(MediaFormat.KEY_DURATION)} sampleRate=${int(MediaFormat.KEY_SAMPLE_RATE)} channels=${int(MediaFormat.KEY_CHANNEL_COUNT)}\n")
            }
        }.onFailure { if (mime?.startsWith("image/") != true) append("tracks: unreadable (${it.javaClass.simpleName})\n") }
        runCatching { extractor.release() }
    }.also { lastFile = it }

    /** 64 MB: more than any still with a one-second clip; a larger file reads as its first 64 MB. */
    private const val MOTION_PHOTO_READ_LIMIT = 64 * 1024 * 1024
}

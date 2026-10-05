package app.tileshell.camera

/**
 * What one camera can do, as Camera2's characteristics say it (phase 17 build task 6; Decisions T17-2 as r3 D10
 * replaces its gates). A small value free of Android types, so the gates below are JVM-tested and the QA row (E7) and
 * the phone's probe page derive the same list from the same keys.
 *
 * The integer codes are Camera2's own (`CameraMetadata`), written here as constants because this file imports nothing
 * of Android.
 */
data class CameraCaps(
    /** `INFO_SUPPORTED_HARDWARE_LEVEL`. */
    val hardwareLevel: Int,
    /** `REQUEST_AVAILABLE_CAPABILITIES`. */
    val capabilities: Set<Int>,
    /** `CONTROL_AE_AVAILABLE_MODES`. */
    val aeModes: Set<Int>,
    /** `CONTROL_AE_COMPENSATION_RANGE`, in steps. */
    val evLow: Int,
    val evHigh: Int,
    /** `CONTROL_AE_COMPENSATION_STEP` as a fraction of one EV. */
    val evStep: Float,
    /** `LENS_INFO_MINIMUM_FOCUS_DISTANCE` in dioptres; 0 = fixed focus. */
    val minFocusDistance: Float,
    /** `CONTROL_AWB_AVAILABLE_MODES`. */
    val awbModes: Set<Int>,
    /** `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM` (or the zoom-ratio range's upper end). */
    val maxZoom: Float,
    /** `SENSOR_INFO_SENSITIVITY_RANGE`, null when the camera does not report one. */
    val isoLow: Int? = null,
    val isoHigh: Int? = null,
    /** `SENSOR_INFO_EXPOSURE_TIME_RANGE` in nanoseconds, null when not reported. */
    val exposureLowNs: Long? = null,
    val exposureHighNs: Long? = null,
    /** `FLASH_INFO_AVAILABLE`. */
    val hasFlash: Boolean = false,
) {
    companion object {
        const val CAPABILITY_MANUAL_SENSOR = 1
        const val CAPABILITY_CONSTRAINED_HIGH_SPEED_VIDEO = 9
        const val AE_MODE_OFF = 0
        const val AWB_AUTO = 1
    }
}

/** What is known outside the characteristics: CameraX's own answers and the APK's native library. */
data class CameraEnv(
    /** CameraX Extensions report HDR for this camera (`ExtensionsManager.isExtensionAvailable`). */
    val hdrExtension: Boolean,
    /** `Recorder.getHighSpeedVideoCapabilities(cameraInfo)` is not null and lists a quality (BS-2). */
    val highSpeedSession: Boolean,
    /** `Build.SUPPORTED_ABIS[0]`. */
    val primaryAbi: String,
    /** `libopencv_pano.so` loaded in this process. */
    val panoramaLibrary: Boolean,
)

/** One mode or control and whether the camera admits it; [reason] is what the `unavailable (<reason>)` line says. */
data class Gate(val id: String, val available: Boolean, val reason: String = "") {
    /** The `[camera]` line of a hidden mode. */
    val line: String get() = "mode $id: unavailable ($reason)"
}

/** The pro dial's five controls, inner ring → outer (Y4). */
enum class ProControl(val id: String) {
    EXPOSURE("exposure"), SHUTTER("shutter"), ISO("iso"), FOCUS("focus"), WB("wb")
}

/** A white-balance preset the dial offers; [awbMode] is Camera2's `CONTROL_AWB_MODE_*`. */
enum class WbPreset(val awbMode: Int, val label: String) {
    INCANDESCENT(2, "incandescent"),
    FLUORESCENT(3, "fluorescent"),
    WARM_FLUORESCENT(4, "warm fluorescent"),
    DAYLIGHT(5, "daylight"),
    CLOUDY(6, "cloudy"),
    TWILIGHT(7, "twilight"),
    SHADE(8, "shade"),
}

/** The gates (pure). Every mode id the viewfinder tags comes from [modes]; every hidden one has its line. */
object CameraGates {
    /** The capture-mode ids in the order the doc's rows list them. */
    val MODE_IDS = listOf("photo", "video", "pro", "hdr", "slowmo", "panorama", "livingimages")

    /** r3 D10: EV compensation works with auto-exposure on, so it needs only a range other than [0, 0]. */
    fun exposure(c: CameraCaps): Gate =
        if (c.evLow != 0 || c.evHigh != 0) Gate("pro.exposure", true)
        else Gate("pro.exposure", false, "no exposure compensation")

    private fun manualSensor(c: CameraCaps, id: String, rangeKnown: Boolean): Gate = when {
        CameraCaps.AE_MODE_OFF !in c.aeModes -> Gate(id, false, "auto-exposure cannot be turned off")
        CameraCaps.CAPABILITY_MANUAL_SENSOR !in c.capabilities -> Gate(id, false, "no manual sensor")
        !rangeKnown -> Gate(id, false, "no range reported")
        else -> Gate(id, true)
    }

    /** r3 D10: ISO and shutter need AE OFF and MANUAL_SENSOR — in Camera2 AE OFF makes both manual together. */
    fun iso(c: CameraCaps): Gate = manualSensor(c, "pro.iso", c.isoLow != null && c.isoHigh != null && c.isoHigh > c.isoLow)
    fun shutter(c: CameraCaps): Gate =
        manualSensor(c, "pro.shutter", c.exposureLowNs != null && c.exposureHighNs != null && c.exposureHighNs > c.exposureLowNs)

    /** T17-2, unchanged by r3 D10: manual focus needs a minimum focus distance above 0. */
    fun focus(c: CameraCaps): Gate =
        if (c.minFocusDistance > 0f) Gate("pro.focus", true) else Gate("pro.focus", false, "fixed focus")

    /** r3 D10: each preset is offered only if `CONTROL_AWB_AVAILABLE_MODES` lists it. */
    fun wbPresets(c: CameraCaps): List<WbPreset> = WbPreset.entries.filter { it.awbMode in c.awbModes }

    fun wb(c: CameraCaps): Gate =
        if (wbPresets(c).isNotEmpty()) Gate("pro.wb", true) else Gate("pro.wb", false, "no white balance presets")

    /** The five controls' gates, inner ring → outer. */
    fun proControls(c: CameraCaps): List<Pair<ProControl, Gate>> = listOf(
        ProControl.EXPOSURE to exposure(c),
        ProControl.SHUTTER to shutter(c),
        ProControl.ISO to iso(c),
        ProControl.FOCUS to focus(c),
        ProControl.WB to wb(c),
    )

    fun pro(c: CameraCaps): Gate =
        if (proControls(c).any { it.second.available }) Gate("pro", true) else Gate("pro", false, "no manual control")

    fun hdr(env: CameraEnv): Gate = if (env.hdrExtension) Gate("hdr", true) else Gate("hdr", false, "no HDR extension")

    fun slowmo(env: CameraEnv): Gate =
        if (env.highSpeedSession) Gate("slowmo", true) else Gate("slowmo", false, "no high-speed session")

    /** BS-1: the stitcher is built for arm64-v8a only. */
    fun panorama(env: CameraEnv): Gate = when {
        env.primaryAbi != "arm64-v8a" -> Gate("panorama", false, "no native library for ${env.primaryAbi}")
        !env.panoramaLibrary -> Gate("panorama", false, "the native library did not load")
        else -> Gate("panorama", true)
    }

    fun zoom(c: CameraCaps): Gate =
        if (c.maxZoom > 1f) Gate("zoom", true) else Gate("zoom", false, "max zoom ${"%.1f".format(java.util.Locale.US, c.maxZoom)}")

    fun flash(c: CameraCaps): Gate = if (c.hasFlash) Gate("flash", true) else Gate("flash", false, "no flash unit")

    /** Every mode id with its gate, in [MODE_IDS]' order: Photo, Video and Living Images always. */
    fun modes(c: CameraCaps, env: CameraEnv): List<Gate> = listOf(
        Gate("photo", true),
        Gate("video", true),
        pro(c),
        hdr(env),
        slowmo(env),
        panorama(env),
        Gate("livingimages", true),
    )

    /** The ids of the modes shown — what the viewfinder tags `camera_mode:<id>`. */
    fun shownModeIds(c: CameraCaps, env: CameraEnv): List<String> = modes(c, env).filter { it.available }.map { it.id }

    /**
     * Every `[camera] mode <x>: unavailable (<reason>)` line this camera owes: the hidden modes, the hidden dial
     * controls (only while the dial itself is shown — a hidden dial says so once), zoom and flash.
     */
    fun hiddenLines(c: CameraCaps, env: CameraEnv): List<String> {
        val out = modes(c, env).filterNot { it.available }.map { it.line }.toMutableList()
        if (pro(c).available) out += proControls(c).map { it.second }.filterNot { it.available }.map { it.line }
        listOf(zoom(c), flash(c)).filterNot { it.available }.forEach { out += it.line }
        return out
    }

    /**
     * The capture modes that cycle through the shutter and its two discs (Y3): photo → video → panorama, a mode the
     * camera cannot do left out.
     */
    fun cycle(c: CameraCaps, env: CameraEnv): List<String> =
        listOf("photo", "video") + if (panorama(env).available) listOf("panorama") else emptyList()

    /**
     * The dynamic App Shortcuts CameraActivity publishes (build task 15, C-9): Panorama rank 2 and Slow motion rank 3,
     * each only where its gate admits the mode; a shortcut not in this list is removed.
     */
    fun dynamicShortcuts(c: CameraCaps?, env: CameraEnv): List<Pair<String, Int>> = buildList {
        if (c != null && panorama(env).available) add("camera_panorama" to 2)
        if (c != null && slowmo(env).available) add("camera_slowmo" to 3)
    }
}

/**
 * The discs beside the shutter (Y3): the right disc is the next mode of [cycle] and the left the previous. With two
 * modes the next and the previous are the same mode, so only one disc is drawn — on the right from the first mode and
 * on the left from the second, as V-2015 drew its one other mode (r11/camera.md 1.2.2–1.2.3).
 */
data class ModeDiscs(val left: String?, val right: String?) {
    companion object {
        fun of(cycle: List<String>, current: String): ModeDiscs {
            val i = cycle.indexOf(current)
            if (i < 0 || cycle.size < 2) return ModeDiscs(null, null)
            val next = cycle[(i + 1) % cycle.size]
            val prev = cycle[(i - 1 + cycle.size) % cycle.size]
            return when {
                cycle.size > 2 -> ModeDiscs(prev, next)
                i == 0 -> ModeDiscs(null, next)
                else -> ModeDiscs(prev, null)
            }
        }
    }
}

/** The values each dial ring steps through (pure; the labels are what the ring's value label shows). */
object ProValues {
    /** Standard shutter speeds in nanoseconds, fastest first. */
    private val SPEEDS_NS: List<Pair<Long, String>> = listOf(
        16000, 8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2,
    ).map { (1_000_000_000L / it) to "1/$it" } + listOf(1, 2, 4).map { (it * 1_000_000_000L) to "$it s" }

    private val ISOS = listOf(50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 6400, 12800)

    /** The shutter speeds inside the sensor's range. */
    fun shutterSpeeds(c: CameraCaps): List<Pair<Long, String>> {
        val lo = c.exposureLowNs ?: return emptyList()
        val hi = c.exposureHighNs ?: return emptyList()
        return SPEEDS_NS.filter { it.first in lo..hi }
    }

    fun isos(c: CameraCaps): List<Int> {
        val lo = c.isoLow ?: return emptyList()
        val hi = c.isoHigh ?: return emptyList()
        return ISOS.filter { it in lo..hi }
    }

    /** Every compensation index of the range. */
    fun evIndices(c: CameraCaps): List<Int> = (c.evLow..c.evHigh).toList()

    /** "0.0", "+0.3", "-1.0": the EV of [index] to one decimal, as W10M wrote it. */
    fun evLabel(index: Int, step: Float): String {
        val ev = index * step
        val text = "%.1f".format(java.util.Locale.US, kotlin.math.abs(ev))
        return if (text == "0.0") text else (if (ev > 0) "+" else "-") + text
    }

    /** Whether a ring's first value is "auto" (every control but exposure, whose values are the EV steps). */
    fun hasAuto(control: ProControl): Boolean = control != ProControl.EXPOSURE

    /** The labels of a ring's values in dial order — "auto" first where the control has one. */
    fun labels(c: CameraCaps, control: ProControl): List<String> = when (control) {
        ProControl.EXPOSURE -> evIndices(c).map { evLabel(it, c.evStep) }
        ProControl.SHUTTER -> listOf("auto") + shutterSpeeds(c).map { it.second }
        ProControl.ISO -> listOf("auto") + isos(c).map { it.toString() }
        ProControl.FOCUS -> listOf("auto") + focusStops(c).map { focusLabel(it) }
        ProControl.WB -> listOf("auto") + CameraGates.wbPresets(c).map { it.label }
    }

    /**
     * The dial position of a control whose engine value is [engineIndex] (null = automatic): exposure's automatic is
     * 0 EV, the others' is their first position.
     */
    fun dialIndex(c: CameraCaps, control: ProControl, engineIndex: Int?): Int =
        if (control == ProControl.EXPOSURE) engineIndex ?: evIndices(c).indexOf(0).coerceAtLeast(0) else (engineIndex ?: -1) + 1

    /** The inverse of [dialIndex]: the engine value of dial position [dialIndex] (null = automatic). */
    fun engineIndex(control: ProControl, dialIndex: Int): Int? =
        if (control == ProControl.EXPOSURE) dialIndex else if (dialIndex <= 0) null else dialIndex - 1

    /** Manual focus stops in dioptres: infinity (0) first, the near stop ([CameraCaps.minFocusDistance]) last. */
    fun focusStops(c: CameraCaps, steps: Int = 10): List<Float> =
        if (c.minFocusDistance <= 0f) emptyList() else (0..steps).map { c.minFocusDistance * it / steps }

    /** "infinity", else the distance in metres or centimetres. */
    fun focusLabel(dioptres: Float): String = when {
        dioptres <= 0f -> "infinity"
        1f / dioptres >= 1f -> "%.1f m".format(java.util.Locale.US, 1f / dioptres)
        else -> "${Math.round(100f / dioptres)} cm"
    }
}

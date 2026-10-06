package app.tileshell.camera

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * The Camera's own settings (phase 17 build task 6: the settings page per r11/camera.md 1.7 / §2). One JSON file in
 * the app's files dir, written temp-and-rename and re-read on every use, as the shell's stores are — both of
 * `:camera`'s activities read it.
 */
data class CameraSettings(
    /** "Framing grid": off, or the rule of thirds (two lines each way at thirds of the preview). */
    val grid: Boolean = false,
    /** "Capture living images" (build task 6d). */
    val livingImages: Boolean = false,
    /** "Time lapse": with the timer on, keep taking photos until the shutter is pressed again. */
    val timeLapse: Boolean = false,
    /** "Aspect ratio": true = 16:9, false = 4:3. */
    val wide: Boolean = false,
    /** "Image size for main camera" as "<w>x<h>"; empty = the largest the camera offers at the aspect ratio. */
    val photoSize: String = "",
    /** "Video recording": a CameraX quality name (UHD, FHD, HD, SD); empty = the highest the camera offers. */
    val videoQuality: String = "",
    /** "Digital video stabilization". */
    val stabilization: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("grid", grid).put("livingImages", livingImages).put("timeLapse", timeLapse).put("wide", wide)
        .put("photoSize", photoSize).put("videoQuality", videoQuality).put("stabilization", stabilization).toString()

    companion object {
        private const val FILE = "camera_settings_v1.json"

        fun fromJson(text: String): CameraSettings = runCatching {
            val o = JSONObject(text)
            CameraSettings(
                grid = o.optBoolean("grid"), livingImages = o.optBoolean("livingImages"), timeLapse = o.optBoolean("timeLapse"),
                wide = o.optBoolean("wide"), photoSize = o.optString("photoSize"), videoQuality = o.optString("videoQuality"),
                stabilization = o.optBoolean("stabilization"),
            )
        }.getOrDefault(CameraSettings())

        fun load(context: Context): CameraSettings {
            val f = File(context.filesDir, FILE)
            return if (f.isFile) fromJson(runCatching { f.readText() }.getOrDefault("")) else CameraSettings()
        }

        fun save(context: Context, settings: CameraSettings) {
            val f = File(context.filesDir, FILE)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(settings.toJson())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}

package app.tileshell.camera

import android.graphics.Bitmap

/**
 * The panorama stitcher (phase 17 build task 6b; BS-1): OpenCV 4.14.0's `cv::Stitcher`, linked statically into ONE
 * JNI library, `libopencv_pano.so`, built for arm64-v8a only (`app/src/main/cpp/`). The JNI surface is three calls:
 * frames in, one stitched image out, a status code.
 *
 * On a device whose ABI has no library (the x86_64 emulator) [available] is false and the mode is hidden with its
 * reason (`CameraGates.panorama`); nothing else here is ever called there.
 */
object PanoramaStitcher {
    /** `cv::Stitcher::Status`, and this shim's own negative codes. */
    const val OK = 0
    const val ERR_NEED_MORE_IMGS = 1
    const val ERR_HOMOGRAPHY_EST_FAIL = 2
    const val ERR_CAMERA_PARAMS_ADJUST_FAIL = 3
    const val ERR_BAD_INPUT = -1
    const val ERR_EXCEPTION = -2

    /** Whether the native library is in this APK for this device's ABI and loaded. */
    val available: Boolean by lazy {
        try {
            System.loadLibrary("opencv_pano")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    /** Stitches [frames] (ARGB_8888, in sweep order); on [OK] writes the result's width and height to [outSize]. */
    private external fun nativeStitch(frames: Array<Bitmap>, outSize: IntArray): Int

    /** Copies the stitched image into [into] (ARGB_8888 of the size [nativeStitch] reported). */
    private external fun nativeResult(into: Bitmap): Int

    /** Frees the stitched image. */
    private external fun nativeRelease()

    sealed interface Result {
        class Stitched(val image: Bitmap) : Result
        data class Failed(val status: Int) : Result {
            val reason: String
                get() = when (status) {
                    ERR_NEED_MORE_IMGS -> "the frames do not overlap enough"
                    ERR_HOMOGRAPHY_EST_FAIL -> "the frames could not be matched"
                    ERR_CAMERA_PARAMS_ADJUST_FAIL -> "the sweep could not be solved"
                    ERR_BAD_INPUT -> "bad frames"
                    else -> "the stitcher failed ($status)"
                }
        }
    }

    /** Blocking and heavy: call it off the main thread. One stitch at a time. */
    @Synchronized
    fun stitch(frames: List<Bitmap>): Result {
        if (!available) return Result.Failed(ERR_EXCEPTION)
        if (frames.size < 2) return Result.Failed(ERR_NEED_MORE_IMGS)
        val size = IntArray(2)
        val status = nativeStitch(frames.toTypedArray(), size)
        if (status != OK) return Result.Failed(status)
        return try {
            val out = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888)
            val copied = nativeResult(out)
            if (copied == OK) Result.Stitched(out) else Result.Failed(copied)
        } catch (e: OutOfMemoryError) {
            Result.Failed(ERR_EXCEPTION)
        } finally {
            nativeRelease()
        }
    }
}

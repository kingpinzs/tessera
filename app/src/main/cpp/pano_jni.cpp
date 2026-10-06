// Phase 17 build task 6b (BS-1): the whole JNI surface of libopencv_pano.so — frames in, one stitched image out, a
// status code. Called only from app.tileshell.camera.PanoramaStitcher, one stitch at a time (its stitch() is
// synchronized), so the result is held in one static Mat between nativeStitch and nativeResult.
#include <jni.h>
#include <android/bitmap.h>
#include <exception>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/stitching.hpp>

namespace {
const jint ERR_BAD_INPUT = -1;
const jint ERR_EXCEPTION = -2;
cv::Mat g_result;   // BGR, valid between nativeStitch == 0 and nativeRelease

// Copies an ARGB_8888 bitmap (RGBA bytes in memory) into a BGR Mat. False when it is not that format or cannot be locked.
bool toBgr(JNIEnv* env, jobject bitmap, cv::Mat& out) {
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return false;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || info.width == 0 || info.height == 0) return false;
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) return false;
    cv::Mat rgba(static_cast<int>(info.height), static_cast<int>(info.width), CV_8UC4, pixels, info.stride);
    cv::cvtColor(rgba, out, cv::COLOR_RGBA2BGR);
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}
}  // namespace

extern "C" {

// private external fun nativeStitch(frames: Array<Bitmap>, outSize: IntArray): Int
JNIEXPORT jint JNICALL
Java_app_tileshell_camera_PanoramaStitcher_nativeStitch(JNIEnv* env, jobject, jobjectArray frames, jintArray outSize) {
    try {
        g_result.release();
        if (frames == nullptr || outSize == nullptr || env->GetArrayLength(outSize) < 2) return ERR_BAD_INPUT;
        const jsize n = env->GetArrayLength(frames);
        if (n < 2) return static_cast<jint>(cv::Stitcher::ERR_NEED_MORE_IMGS);
        std::vector<cv::Mat> images;
        images.reserve(static_cast<size_t>(n));
        for (jsize i = 0; i < n; i++) {
            jobject bitmap = env->GetObjectArrayElement(frames, i);
            cv::Mat bgr;
            const bool ok = bitmap != nullptr && toBgr(env, bitmap, bgr);
            if (bitmap != nullptr) env->DeleteLocalRef(bitmap);
            if (!ok) return ERR_BAD_INPUT;
            images.push_back(bgr);
        }
        cv::Ptr<cv::Stitcher> stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);
        cv::Mat pano;
        const cv::Stitcher::Status status = stitcher->stitch(images, pano);
        if (status != cv::Stitcher::OK) return static_cast<jint>(status);
        if (pano.empty() || pano.type() != CV_8UC3) return ERR_EXCEPTION;
        g_result = pano;
        const jint size[2] = {pano.cols, pano.rows};
        env->SetIntArrayRegion(outSize, 0, 2, size);
        return 0;
    } catch (const std::exception&) {
        g_result.release();
        return ERR_EXCEPTION;
    } catch (...) {
        g_result.release();
        return ERR_EXCEPTION;
    }
}

// private external fun nativeResult(into: Bitmap): Int
JNIEXPORT jint JNICALL
Java_app_tileshell_camera_PanoramaStitcher_nativeResult(JNIEnv* env, jobject, jobject into) {
    try {
        if (into == nullptr || g_result.empty()) return ERR_BAD_INPUT;
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, into, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return ERR_BAD_INPUT;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || static_cast<int>(info.width) != g_result.cols || static_cast<int>(info.height) != g_result.rows) return ERR_BAD_INPUT;
        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, into, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || pixels == nullptr) return ERR_BAD_INPUT;
        cv::Mat rgba(g_result.rows, g_result.cols, CV_8UC4, pixels, info.stride);
        cv::cvtColor(g_result, rgba, cv::COLOR_BGR2RGBA);
        AndroidBitmap_unlockPixels(env, into);
        return 0;
    } catch (...) {
        return ERR_EXCEPTION;
    }
}

// private external fun nativeRelease()
JNIEXPORT void JNICALL
Java_app_tileshell_camera_PanoramaStitcher_nativeRelease(JNIEnv*, jobject) {
    g_result.release();
}

}  // extern "C"

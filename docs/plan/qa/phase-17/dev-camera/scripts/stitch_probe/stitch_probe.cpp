// Development proof only: frames in, one stitched image out, a status code — the same calls app/src/main/cpp/pano_jni.cpp
// makes (RGBA -> BGR, cv::Stitcher::create(PANORAMA)->stitch), on frames cut from one synthetic textured scene the way a
// sweep to the right sees it. Prints `status=<n> frames=<n> frame=<w>x<h> pano=<w>x<h>`; exit 0 only when the stitch
// succeeded and the panorama is wider than one frame.
#include <cstdio>
#include <vector>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/stitching.hpp>

int main() {
    // A scene with plenty of corners: deterministic random rectangles, circles and lines over a gradient.
    const int W = 2400, H = 960;
    cv::Mat scene(H, W, CV_8UC3);
    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) scene.at<cv::Vec3b>(y, x) = cv::Vec3b(60 + x * 120 / W, 80 + y * 100 / H, 120);
    cv::RNG rng(17);
    for (int i = 0; i < 900; i++) {
        const cv::Scalar color(rng.uniform(0, 256), rng.uniform(0, 256), rng.uniform(0, 256));
        const cv::Point p(rng.uniform(0, W), rng.uniform(0, H));
        switch (i % 3) {
            case 0: cv::rectangle(scene, p, p + cv::Point(rng.uniform(12, 90), rng.uniform(12, 90)), color, cv::FILLED); break;
            case 1: cv::circle(scene, p, rng.uniform(6, 40), color, cv::FILLED); break;
            default: cv::line(scene, p, p + cv::Point(rng.uniform(-120, 120), rng.uniform(-120, 120)), color, rng.uniform(1, 5)); break;
        }
    }
    // Six upright frames 720 x 960, each 300 px further right: 58 % overlap, as RGBA (what a Bitmap holds).
    std::vector<cv::Mat> frames;
    for (int i = 0; i < 6; i++) {
        cv::Mat rgba, bgr;
        cv::cvtColor(scene(cv::Rect(i * 300, 0, 720, H)), rgba, cv::COLOR_BGR2RGBA);
        cv::cvtColor(rgba, bgr, cv::COLOR_RGBA2BGR);
        frames.push_back(bgr);
    }
    cv::Mat pano;
    int status = -2;
    try {
        status = static_cast<int>(cv::Stitcher::create(cv::Stitcher::PANORAMA)->stitch(frames, pano));
    } catch (const std::exception& e) {
        std::printf("exception=%s\n", e.what());
    }
    std::printf("status=%d frames=%zu frame=%dx%d pano=%dx%d\n", status, frames.size(), frames[0].cols, frames[0].rows, pano.cols, pano.rows);
    return (status == 0 && pano.cols > frames[0].cols) ? 0 : 1;
}

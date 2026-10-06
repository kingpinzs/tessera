# Development proof only (never shipped, never in the APK): the same OpenCV module set libopencv_pano.so links, built
# as a command-line program for the emulator's ABI, to show the static link is complete and cv::Stitcher runs on
# Android. See d1_native.sh.
cmake_minimum_required(VERSION 3.22.1)
project(stitch_probe LANGUAGES CXX)
set(OpenCV_DIR "${OPENCV_SDK}/sdk/native/jni")
find_package(OpenCV 4.14.0 EXACT REQUIRED COMPONENTS core imgproc features2d flann calib3d stitching)
add_executable(stitch_probe stitch_probe.cpp)
target_compile_features(stitch_probe PRIVATE cxx_std_17)
target_compile_options(stitch_probe PRIVATE -O2)
target_include_directories(stitch_probe PRIVATE ${OpenCV_INCLUDE_DIRS})
target_link_libraries(stitch_probe PRIVATE ${OpenCV_LIBS} log)
target_link_options(stitch_probe PRIVATE -Wl,--gc-sections)

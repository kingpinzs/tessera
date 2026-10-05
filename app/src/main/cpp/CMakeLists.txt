# Phase 17 build task 6b (BS-1): the panorama stitcher. ONE JNI library, libopencv_pano.so, that links the static
# libraries of the official OpenCV 4.14.0 Android SDK — core imgproc features2d flann calib3d stitching — with the
# static C++ runtime, for arm64-v8a ONLY (app/build.gradle.kts restricts the native build to that ABI, so the APK
# holds no lib/x86_64/libopencv*; on the emulator Panorama is hidden with its reason).
#
# The SDK is a git-ignored build input fetched by tools/fetch-opencv.sh into .opencvsrc/ (pinned by sha256 there).
cmake_minimum_required(VERSION 3.22.1)
project(opencv_pano LANGUAGES CXX)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "libopencv_pano is built for arm64-v8a only (BS-1); got ${ANDROID_ABI}")
endif()

set(OPENCV_SDK "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.opencvsrc/OpenCV-android-sdk" CACHE PATH "The OpenCV Android SDK (tools/fetch-opencv.sh)")
if(NOT EXISTS "${OPENCV_SDK}/sdk/native/jni/OpenCVConfig.cmake")
    message(FATAL_ERROR "The OpenCV Android SDK is not at ${OPENCV_SDK}: run tools/fetch-opencv.sh")
endif()
set(OpenCV_DIR "${OPENCV_SDK}/sdk/native/jni")
# The module set BS-1 fixed; the SDK's config adds each module's own third-party archives (TBB, KleidiCV, the Tegra
# HAL, cpufeatures, ittnotify) and system libraries.
find_package(OpenCV 4.14.0 EXACT REQUIRED COMPONENTS core imgproc features2d flann calib3d stitching)

add_library(opencv_pano SHARED pano_jni.cpp)
target_compile_features(opencv_pano PRIVATE cxx_std_17)
target_compile_options(opencv_pano PRIVATE -O2 -fvisibility=hidden -ffunction-sections -fdata-sections)
target_include_directories(opencv_pano PRIVATE ${OpenCV_INCLUDE_DIRS})
target_link_libraries(opencv_pano PRIVATE ${OpenCV_LIBS} jnigraphics log)
# Only the three JNI entry points are exported; everything unreached is dropped; 16 KB pages (Android 15+ devices).
target_link_options(opencv_pano PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    -Wl,--strip-all
    -Wl,-z,max-page-size=16384
)

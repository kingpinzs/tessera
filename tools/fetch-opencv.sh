#!/usr/bin/env bash
# Phase 17 (build task 6b, BS-1): the OpenCV Android SDK the panorama stitcher is linked from.
#
# It is not in git: the official SDK zip is 319 MB of third-party binaries and an immutable GitHub release asset. This
# script pins it by URL and sha256, as tools/fetch-speech.sh pins the speech runtime, so the build is reproducible
# without carrying the bytes. Run it once after a clone; it is a no-op afterwards.
#
#   tools/fetch-opencv.sh
#
# What it produces (git-ignored):
#   .opencvsrc/opencv-4.14.0-android-sdk.zip      the download, kept so a re-run verifies instead of fetching
#   .opencvsrc/OpenCV-android-sdk/                 only what the native build reads: sdk/native/jni (the CMake config
#                                                  and the headers), the arm64-v8a static libraries and their
#                                                  third-party archives, and the licence texts — plus the SDK's own
#                                                  libopencv_java4.so, which is NOT linked or shipped: the SDK's CMake
#                                                  config refuses to load unless every target it names exists on disk
#   app/src/main/assets/licenses/opencv-*.txt      OpenCV's licence and those of the third parties linked into
#                                                  libopencv_pano.so, shown on Settings > About (the files are small
#                                                  and committed; this step only proves they equal the SDK's own)
#
# The native build itself is app/src/main/cpp/CMakeLists.txt: ONE library, libopencv_pano.so, arm64-v8a only.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$root/.opencvsrc"
zip="$work/opencv-4.14.0-android-sdk.zip"
sdk="$work/OpenCV-android-sdk"

SDK_URL="https://github.com/opencv/opencv/releases/download/4.14.0/opencv-4.14.0-android-sdk.zip"
SDK_SHA="e8cfaf2e51f2e2127a6ede91718d1ef7587f8b6e62db922816e7c33a1f1116a7"

mkdir -p "$work"
if [ -f "$zip" ] && [ "$(sha256sum "$zip" | cut -d' ' -f1)" = "$SDK_SHA" ]; then
  echo "have  $(basename "$zip")"
else
  echo "fetch $(basename "$zip")"
  curl -fsSL -o "$zip.part" "$SDK_URL"
  got="$(sha256sum "$zip.part" | cut -d' ' -f1)"
  [ "$got" = "$SDK_SHA" ] || { echo "sha256 mismatch for $SDK_URL: got $got want $SDK_SHA" >&2; exit 1; }
  mv "$zip.part" "$zip"
fi

# Extract only what the arm64 link reads. The marker names the zip's hash, so a new pin re-extracts.
marker="$sdk/.extracted-$SDK_SHA"
if [ ! -f "$marker" ]; then
  rm -rf "$sdk"
  unzip -q -o "$zip" \
    'OpenCV-android-sdk/LICENSE' \
    'OpenCV-android-sdk/sdk/etc/licenses/*' \
    'OpenCV-android-sdk/sdk/native/jni/*' \
    'OpenCV-android-sdk/sdk/native/staticlibs/arm64-v8a/*' \
    'OpenCV-android-sdk/sdk/native/libs/arm64-v8a/*' \
    'OpenCV-android-sdk/sdk/native/3rdparty/libs/arm64-v8a/*' \
    -d "$work"
  touch "$marker"
fi
for f in sdk/native/jni/OpenCVConfig.cmake sdk/native/staticlibs/arm64-v8a/libopencv_stitching.a sdk/native/3rdparty/libs/arm64-v8a/libtbb.a; do
  [ -f "$sdk/$f" ] || { echo "the SDK is missing $f after extraction" >&2; exit 1; }
done

# The licence texts on the app's licence page are the SDK's own, byte for byte.
licenses="$root/app/src/main/assets/licenses"
check() { # committed-name sdk-path
  cmp -s "$licenses/$1" "$sdk/$2" || { echo "$licenses/$1 differs from the SDK's $2" >&2; exit 1; }
}
check opencv-4.14.0-LICENSE.txt LICENSE
check opencv-tbb-LICENSE.txt sdk/etc/licenses/tbb-LICENSE.txt
check opencv-cpufeatures-LICENSE.txt sdk/etc/licenses/cpufeatures-LICENSE
check opencv-ittnotify-BSD-3-Clause.txt sdk/etc/licenses/ittnotify-BSD-3-Clause.txt
check opencv-features2d-AKAZE-LICENSE.txt sdk/etc/licenses/features2d-LICENSE.AKAZE
check opencv-features2d-KAZE-LICENSE.txt sdk/etc/licenses/features2d-LICENSE.KAZE
check opencv-mscr-chi_table-LICENSE.txt sdk/etc/licenses/mscr-chi_table_LICENSE.txt
check opencv-SoftFloat-COPYING.txt sdk/etc/licenses/SoftFloat-COPYING.txt
echo "ok    OpenCV 4.14.0 Android SDK at $sdk"

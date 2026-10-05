#!/usr/bin/env bash
# Development proof, build task 6b (D): the native build. Host half: E17's OpenCV listing on the debug APK (>= 1
# lib/arm64-v8a/libopencv* entry, their sum <= 20,971,520 bytes, none under lib/x86_64), the APK's other native
# libraries unchanged in count and ABI, and libopencv_pano.so's own facts (three JNI exports, system libraries only,
# 16 KB pages). Device half: `stitch_probe` — the SAME OpenCV module set and the same calls as pano_jni.cpp, built as a
# program for the emulator's ABI (it is never in the APK) — is pushed to /data/local/tmp, run, and removed: it shows the
# static link is complete and cv::Stitcher stitches on Android. It does NOT prove the arm64 library or its JNI glue on
# a device: that is the phone's row (P10).
#   usage: d1_native.sh <path to the built stitch_probe for x86_64>
. "$(dirname "$0")/head.sh"
row_begin D1 "native build: E17's listing, the library's facts, the stitcher on Android"
PROBE="${1:-}"
LIST="$(unzip -l "$APK" 'lib/arm64-v8a/libopencv*' | grep 'lib/')"; echo "$LIST" | sed 's/^/      /' >> "$LOG"
record "unzip -l app-debug.apk 'lib/arm64-v8a/libopencv*'" "$(echo "$LIST" | xargs)"
N="$(echo "$LIST" | grep -c 'lib/arm64-v8a/libopencv')"; SUM="$(echo "$LIST" | awk '{s+=$1} END {print s+0}')"
if [ "$N" -ge 1 ]; then _verdict PASS ">= 1 lib/arm64-v8a/libopencv* entry" "$N"; else _verdict FAIL ">= 1 lib/arm64-v8a/libopencv* entry" "$N"; fi
if [ "$SUM" -gt 0 ] && [ "$SUM" -le 20971520 ]; then _verdict PASS "OpenCV sum <= 20,971,520 bytes" "$SUM"; else _verdict FAIL "OpenCV sum <= 20,971,520 bytes" "$SUM"; fi
assert_eq "no lib/x86_64/libopencv*" "0" "$(unzip -l "$APK" 'lib/x86_64/libopencv*' 2>/dev/null | grep -c 'lib/x86_64/')"
ALL="$(unzip -l "$APK" 'lib/*' | grep 'lib/' | awk '{print $4}' | grep -v libopencv | sort | xargs)"
assert_eq "the other native libraries: the same seven for each ABI, as before this phase" \
  "lib/arm64-v8a/libandroidx.graphics.path.so lib/arm64-v8a/libimage_processing_util_jni.so lib/arm64-v8a/libonnxruntime.so lib/arm64-v8a/libsherpa-onnx-c-api.so lib/arm64-v8a/libsherpa-onnx-cxx-api.so lib/arm64-v8a/libsherpa-onnx-jni.so lib/arm64-v8a/libsurface_util_jni.so lib/x86_64/libandroidx.graphics.path.so lib/x86_64/libimage_processing_util_jni.so lib/x86_64/libonnxruntime.so lib/x86_64/libsherpa-onnx-c-api.so lib/x86_64/libsherpa-onnx-cxx-api.so lib/x86_64/libsherpa-onnx-jni.so lib/x86_64/libsurface_util_jni.so" "$ALL"
assert_eq "no libc++_shared.so (the C++ runtime is static)" "0" "$(unzip -l "$APK" | grep -c 'libc++_shared')"
TOOLS="$HOME/Android/Sdk/ndk/30.0.16248370/toolchains/llvm/prebuilt/linux-x86_64/bin"
unzip -o -q "$APK" 'lib/arm64-v8a/libopencv_pano.so' -d "$ROW_DIR"
SO="$ROW_DIR/lib/arm64-v8a/libopencv_pano.so"
assert_contains "an arm64 shared object" "ARM aarch64" "$(file "$SO")"
assert_eq "exports: the three JNI entry points and nothing else" \
  "Java_app_tileshell_camera_PanoramaStitcher_nativeRelease Java_app_tileshell_camera_PanoramaStitcher_nativeResult Java_app_tileshell_camera_PanoramaStitcher_nativeStitch" \
  "$("$TOOLS/llvm-nm" -D --defined-only "$SO" | awk '{print $3}' | sort | xargs)"
assert_eq "needs system libraries only" "libc.so libdl.so libjnigraphics.so liblog.so libm.so libz.so" "$("$TOOLS/llvm-readelf" -d "$SO" | grep NEEDED | sed 's/.*\[\(.*\)\]/\1/' | sort | xargs)"
assert_eq "every LOAD segment aligned to 16 KB" "0x4000" "$("$TOOLS/llvm-readelf" -lW "$SO" | awk '/LOAD/{print $NF}' | sort -u | xargs)"
if [ -n "$PROBE" ] && [ -f "$PROBE" ]; then
  record "stitch_probe" "$(file "$PROBE" | sed 's/.*: //' | cut -c1-70), $(stat -c%s "$PROBE") bytes"
  adb push "$PROBE" /data/local/tmp/camera_stitch_probe >/dev/null
  adb shell chmod 755 /data/local/tmp/camera_stitch_probe
  OUT="$(adb shell '/data/local/tmp/camera_stitch_probe; echo rc=$?' | tr -d '\r')"; record "stitch_probe on the emulator" "$(echo "$OUT" | xargs)"
  adb shell rm -f /data/local/tmp/camera_stitch_probe
  assert_contains "cv::Stitcher status OK on Android" "status=0 frames=6 frame=720x960" "$OUT"
  assert_contains "the program's own verdict" "rc=0" "$OUT"
  PW="$(echo "$OUT" | sed -n 's/.*pano=\([0-9]*\)x.*/\1/p')"
  if [ "${PW:-0}" -gt 720 ]; then _verdict PASS "the panorama is wider than one frame" "$PW > 720"; else _verdict FAIL "the panorama is wider than one frame" "${PW:-none}"; fi
  assert_eq "the probe is removed from the device" "" "$(adb shell ls /data/local/tmp/camera_stitch_probe 2>/dev/null | tr -d '\r')"
else
  record "stitch_probe" "not given: the device half did not run"
fi
row_end

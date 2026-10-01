#!/usr/bin/env bash
# Phase 14 E15 — RV10: `wm size 1440x3120` / `720x1560`, `wm density 560` and `font_scale 1.3` leave every pod node's
# bounds in epx unchanged +-1 epx (phase 01 E3's method, one dump each: scripts/pod_epx.py); restore. StartActivity
# takes these as configuration changes (manifest configChanges), so the pod bay stays the page across them; each dump
# asserts it did.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E15 "RV10: display size, density and font scale leave the pod nodes' epx bounds"
restore() {
  adb shell wm size reset >/dev/null 2>&1
  adb shell wm density reset >/dev/null 2>&1
  adb shell settings put system font_scale 1.0 >/dev/null 2>&1
}
trap restore EXIT

open_pod_bay "$ROW_DIR/base.xml"
assert_eq "base: the pod bay" "yes" "$(has_node "$ROW_DIR/base.xml" pod_bay)"
screencap "$ROW_DIR/base.png"

one() { # tag width
  local tag="$1" w="$2"
  sleep 4
  dump_ui "$ROW_DIR/$tag.xml"; screencap "$ROW_DIR/$tag.png"
  assert_eq "$tag: still the pod bay" "yes" "$(has_node "$ROW_DIR/$tag.xml" pod_bay)"
  python3 "$HERE/pod_epx.py" "$ROW_DIR/base.xml" 1080 "$ROW_DIR/$tag.xml:$w" > "$ROW_DIR/$tag-epx.txt" 2>&1
  echo $? > "$ROW_DIR/$tag-epx.rc"
  note "$tag: $(tail -1 "$ROW_DIR/$tag-epx.txt")"
  assert_eq "$tag: every pod node within 1 epx of the base" "0" "$(cat "$ROW_DIR/$tag-epx.rc")"
}

adb shell wm size 1440x3120
assert_contains "1440x3120 applied" "Override size: 1440x3120" "$(adb shell wm size)"
adb shell dumpsys display | grep -m1 -oE 'mOverrideDisplayInfo=DisplayInfo\{[^,]*, [^,]*, real [0-9]+ x [0-9]+' > "$ROW_DIR/size1440-display.txt"
note "size1440: $(cat "$ROW_DIR/size1440-display.txt")"
one size1440 1440
adb shell wm size 720x1560
assert_contains "720x1560 applied" "Override size: 720x1560" "$(adb shell wm size)"
one size720 720
adb shell wm size reset
sleep 3
adb shell wm density 560
assert_contains "density 560 applied" "Override density: 560" "$(adb shell wm density)"
one d560 1080
adb shell wm density reset
sleep 3
adb shell settings put system font_scale 1.3
assert_eq "font_scale 1.3 applied" "1.3" "$(adb shell settings get system font_scale | tr -d '\r')"
one font13 1080

restore
trap - EXIT
sleep 3
assert_contains "restore: wm size" "Physical size: 1080x2340" "$(adb shell wm size)"
assert_absent "restore: no size override" "Override size" "$(adb shell wm size)"
assert_absent "restore: no density override" "Override density" "$(adb shell wm density)"
assert_eq "restore: font_scale 1.0" "1.0" "$(adb shell settings get system font_scale | tr -d '\r')"
ensure_start
RINGS="launcher" row_end

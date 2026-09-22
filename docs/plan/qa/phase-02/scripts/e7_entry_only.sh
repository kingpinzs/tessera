#!/usr/bin/env bash
# One more entry recording for E7's median (usage: e7_entry_only.sh <out dir> <tile id> <run number>).
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/gestures.sh"
OUT=$1; TILE=$2; RUN=$3
ensure_start
adb shell input swipe 540 700 540 1900 200; sleep 1
XY=$(center "$OUT/normal.xml" "tile:$TILE") || exit 1
adb shell rm -f /sdcard/e7_entry.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 6 /sdcard/e7_entry.mp4 &
REC=$!
sleep 1.5
down ${XY% *} ${XY#* }
sleep 1.3
up ${XY% *} ${XY#* }
sleep 2
wait $REC
adb pull /sdcard/e7_entry.mp4 "$OUT/e7_entry_$RUN.mp4" >/dev/null
adb shell input keyevent KEYCODE_BACK; sleep 1
echo "entry recording $RUN captured"

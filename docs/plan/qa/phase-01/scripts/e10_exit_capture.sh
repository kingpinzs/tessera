#!/usr/bin/env bash
# E10 Start exit / return: tap a tile (Start exit, R3 A11), wait for the app, press Home (Start entrance). One recording per run.
# usage: e10_exit_capture.sh <out dir> <run name> <tile id>
source "$(dirname "$0")/ui.sh"
OUT=$1; RUN=$2; TILE=$3; LOG=$OUT/exit_capture.txt
adb shell input keyevent KEYCODE_HOME; sleep 3
for i in 1 2 3 4; do adb shell input swipe 540 700 540 1900 200; sleep 0.4; done; sleep 2
dump "$OUT/${RUN}_start.xml"
XY=$(center "$OUT/${RUN}_start.xml" "tile:$TILE") || { echo "$RUN: no tile $TILE" >> "$LOG"; exit 1; }
adb shell rm -f /sdcard/e10_$RUN.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 7 /sdcard/e10_$RUN.mp4 &
REC=$!
sleep 1.5
adb shell "input tap $XY"
sleep 3
adb shell input keyevent KEYCODE_HOME
wait $REC
adb pull /sdcard/e10_$RUN.mp4 "$OUT/e10_$RUN.mp4" >/dev/null
echo "$RUN: tapped tile:$TILE at $XY; $(adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E 'start exit finished|start entrance' | tail -3 | tr -s ' ' | tr '\n' ';')" >> "$LOG"

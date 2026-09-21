#!/usr/bin/env bash
# E7 capture: the settled edit-mode geometry, the entry motion, the exit motion and the hold bracket.
# usage: e7_capture.sh <out dir> <held tile id>        e.g. e7_capture.sh qa/phase-02/E07 slot:PEOPLE
# Leaves the device as it found it (RV12): show_touches and stayon are restored.
set -u
source "$(dirname "$0")/gestures.sh"
OUT=$1; TILE=$2; LOG=$OUT/e7_capture.txt
mkdir -p "$OUT"
echo "# E7 capture $(date -Iseconds) held=$TILE" > "$LOG"
adb shell svc power stayon true
ensure_start
adb shell input swipe 540 700 540 1900 200; sleep 1   # scroll to the top so both captures share a scroll

# --- 1. Start at rest -----------------------------------------------------------------------------------------
dump "$OUT/normal.xml"
adb exec-out screencap -p > "$OUT/normal.png"
XY=$(center "$OUT/normal.xml" "tile:$TILE") || { echo "no tile $TILE" | tee -a "$LOG"; exit 1; }
echo "held tile centre: $XY" >> "$LOG"

# --- 2. the entry, recorded ----------------------------------------------------------------------------------
adb shell rm -f /sdcard/e7_entry.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 6 /sdcard/e7_entry.mp4 &
REC=$!
sleep 1.5
down ${XY% *} ${XY#* }
sleep 1.3            # past the 783 ms hold, then let the entry settle
up ${XY% *} ${XY#* }
sleep 2
wait $REC
adb pull /sdcard/e7_entry.mp4 "$OUT/e7_entry.mp4" >/dev/null

# --- 3. edit mode, settled ------------------------------------------------------------------------------------
sleep 1
adb exec-out screencap -p > "$OUT/edit.png"
dump "$OUT/edit.xml"

# --- 4. the exit, recorded, with the touch indicator marking the touch-up --------------------------------------
TOUCHES_BEFORE=$(adb shell settings get system show_touches | tr -d '\r')
echo "show_touches before: $TOUCHES_BEFORE" >> "$LOG"
adb shell settings put system show_touches 1
# An empty spot low on the grid: the page background there makes the indicator unmistakable, and tapping empty
# space is one of the three exits R6 §1.5.1 lists.
EMPTY_X=540; EMPTY_Y=$(python3 -c "
import re,sys
s=open('$OUT/normal.xml').read()
b=[tuple(map(int,m.groups())) for m in re.finditer(r'resource-id=\"tile:[^\"]*\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', s)]
grid=[r for r in b if r[3] < 1900]
print(min(1700, max(r[3] for r in grid) + 120))")
echo "exit tap at $EMPTY_X $EMPTY_Y (empty grid space)" >> "$LOG"
adb shell rm -f /sdcard/e7_exit.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 6 /sdcard/e7_exit.mp4 &
REC=$!
sleep 1.5
adb shell input tap $EMPTY_X $EMPTY_Y
sleep 3
wait $REC
adb pull /sdcard/e7_exit.mp4 "$OUT/e7_exit.mp4" >/dev/null
# "null" means the setting was never set: deleting restores that exactly, rather than writing a 0 that was
# not there before (RV12).
if [ -z "$TOUCHES_BEFORE" ] || [ "$TOUCHES_BEFORE" = "null" ]; then
  adb shell settings delete system show_touches
else
  adb shell settings put system show_touches "$TOUCHES_BEFORE"
fi
adb exec-out screencap -p > "$OUT/after_exit.png"

# --- 5. the hold bracket (E7: 740 ms must not enter edit mode, 830 ms must) -------------------------------------
# `input swipe x y x y <ms>` presses for exactly that long on the device, so the bracket does not depend on host
# timing. The discriminator is the shell's own behaviour: under the threshold the press is a TAP and the tile
# launches its app; over it, nothing launches and the grid contracts.
top() { adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//'; }
for MS in 740 830; do
  ensure_start
  adb shell input swipe ${XY% *} ${XY#* } ${XY% *} ${XY#* } $MS
  sleep 1.4
  adb exec-out screencap -p > "$OUT/hold_$MS.png"
  echo "hold ${MS}ms: top activity $(top)" >> "$LOG"
  ensure_start
  ensure_start
done

adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep "\[edit\]" | tail -40 > "$OUT/e7_edit_diag.txt"
adb shell svc power stayon false
echo "done $(date -Iseconds)" >> "$LOG"
echo "captured into $OUT"

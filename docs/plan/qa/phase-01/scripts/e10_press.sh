#!/usr/bin/env bash
# E10 press feedback (Q6): for each press style chosen in Settings > Start, screencaps of a static tile before, while held
# (input motionevent DOWN) and after CANCEL (no launch). usage: e10_press.sh <out dir> <tile id>
source "$(dirname "$0")/ui.sh"
OUT=$1; TILE=$2; LOG=$OUT/press.txt
echo "# press feedback $(date -Iseconds), tile $TILE" > "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 3
dump "$OUT/press_theme_before.xml"
PRE=$(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml | tr -d '\n' | grep -o '<string name="[a-zA-Z_]*[Pp]ress[a-zA-Z_]*">[^<]*</string>')
echo "press style before (start_theme prefs; the radio rows carry no selected state in the dump): ${PRE:-default (key absent)}" >> "$LOG"
for style in none tilt p4; do
  adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 2
  scroll_to_id "$OUT/press_theme_$style.xml" "press_$style" && tap_id "$OUT/press_theme_$style.xml" "press_$style"; sleep 1
  dump "$OUT/press_theme_${style}_after.xml"
  echo "$style chosen: $(grep -o 'resource-id="press_[a-z0-9]*"[^>]*selected="[a-z]*"' "$OUT/press_theme_${style}_after.xml" | sed 's/resource-id="//; s/".*selected=/ selected=/' | tr '\n' ' '); prefs $(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml | tr -d '\n' | grep -o '<string name="press">[^<]*</string>')" >> "$LOG"
  adb shell input keyevent KEYCODE_HOME; sleep 3
  for i in 1 2 3 4; do adb shell input swipe 540 700 540 1900 200; sleep 0.4; done; sleep 2
  dump "$OUT/press_start_$style.xml"
  read X1 Y1 X2 Y2 < <(bounds "$OUT/press_start_$style.xml" "tile:$TILE")
  for spot in centre corner; do
    if [ $spot = centre ]; then X=$(( (X1 + X2) / 2 )); Y=$(( (Y1 + Y2) / 2 )); else X=$(( X1 + (X2 - X1) / 8 )); Y=$(( Y1 + (Y2 - Y1) / 8 )); fi
    adb exec-out screencap -p > "$OUT/press_${style}_${spot}_before.png"
    adb shell input motionevent DOWN $X $Y; sleep 0.35
    adb exec-out screencap -p > "$OUT/press_${style}_${spot}_down.png"
    adb shell input motionevent CANCEL $X $Y; sleep 0.8
    adb exec-out screencap -p > "$OUT/press_${style}_${spot}_after.png"
    echo "$style $spot: tile [$X1,$Y1][$X2,$Y2] touch $X,$Y; resumed after: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' ')" >> "$LOG"
  done
done
# restore the starting style
case "$PRE" in *WP8_TILT*) R=press_tilt;; *P4_PRESS*) R=press_p4;; *) R=press_none;; esac
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 2
scroll_to_id "$OUT/press_theme_restore.xml" "$R" && tap_id "$OUT/press_theme_restore.xml" "$R"; sleep 1
echo "restored $R; prefs now: $(adb shell run-as app.tileshell cat shared_prefs/start_theme.xml | tr -d '\n' | grep -o '<string name="[a-zA-Z_]*[Pp]ress[a-zA-Z_]*">[^<]*</string>')" >> "$LOG"
adb shell input keyevent KEYCODE_HOME

#!/usr/bin/env bash
# e13_step.sh <name> <resource-id> [fraction]: open Settings > Start, scroll to the control, tap it (slider: at the fraction),
# then Home and capture Start (dump + screencap) and its colours / geometry.
source "$(dirname "$0")/ui.sh"
NAME=$1; ID=$2; FRAC=$3; LOG=E13/E13.txt
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page HOME >/dev/null 2>&1; sleep 1
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 3
scroll_to_id "E13/${NAME}_settings.xml" "$ID" || { echo "$NAME: no control $ID" >> "$LOG"; exit 1; }
if [ -n "$FRAC" ]; then
  read X1 Y1 X2 Y2 < <(bounds "E13/${NAME}_settings.xml" "$ID")
  adb shell input tap $(python3 -c "print(int($X1 + $FRAC * ($X2 - $X1)), ($Y1 + $Y2) // 2)")
else
  tap_id "E13/${NAME}_settings.xml" "$ID"
fi
sleep 1.5; dump "E13/${NAME}_settings_after.xml"
echo "## $NAME: tapped $ID ${FRAC:+at $FRAC}; control after: $(grep -o "<node [^>]*resource-id=\"$ID\"[^>]*>" "E13/${NAME}_settings_after.xml" | grep -o 'checked="[a-z]*"\|content-desc="[^"]*"' | tr '\n' ' ')" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3
for i in 1 2 3; do adb shell input swipe 540 700 540 1900 200; sleep 0.4; done; sleep 1.5
dump "E13/${NAME}_start.xml"; adb exec-out screencap -p > "E13/${NAME}_start.png"
python3 "$(dirname "$0")/e13_pixels.py" "E13/${NAME}_start.xml" "E13/${NAME}_start.png" >> "$LOG"

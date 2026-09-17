#!/usr/bin/env bash
# E9 offline + stale line, E12 caption after 3 days, launch clears it, uninstall; then the RV12 clock restore and network back on.
source "$(dirname "$0")/ui.sh"
E9=$1; E12=$2; L9=$E9/E09.txt; L12=$E12/E12.txt; PKG=com.ashkanrafiee.librecontactsbackup
N="python3 $(dirname "$0")/nodes.py"
diag() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener; }
setclock() { # setclock <epoch seconds>: sets the device clock (root) in UTC
  adb shell "date -u $(date -u -d @$1 +%m%d%H%M%Y.%S)" >/dev/null
}
tile_samples() { # tile_samples <prefix> <count>
  for s in $(seq 1 $2); do dump "$E9/$1_$s.xml"; echo "tile sample $s: $($N "$E9/$1_$s.xml" tile:shell:weather)" >> "$L9"; sleep 2.5; done
}
echo "## E9 offline" >> "$L9"
adb shell input keyevent KEYCODE_HOME; sleep 2
echo '$ adb shell cmd connectivity airplane-mode enable' >> "$L9"; adb shell cmd connectivity airplane-mode enable; sleep 5
echo "dumpsys connectivity: $(adb shell dumpsys connectivity | grep -m1 'Active default network')" >> "$L9"
tile_samples offline 4
echo "## clock +61 min" >> "$L9"
T0=$(adb shell date +%s | tr -d '\r'); echo "device epoch before: $T0 ($(adb shell date))" >> "$L9"
adb root >/dev/null; adb wait-for-device; sleep 2
adb shell settings put global auto_time 0
setclock $((T0 + 61*60)); echo "\$ auto_time 0; date -u set to +61 min: $(adb shell date)" >> "$L9"
for i in $(seq 1 60); do diag | grep -q "\[weather\] publish tile.*stale=true" && break; sleep 3; done
diag | grep "\[weather\]" | tail -3 >> "$L9"
tile_samples stale 6
adb exec-out screencap -p > "$E9/stale_tile.png"
echo "## E12: clock +3 days" >> "$L12"
setclock $((T0 + 3*24*3600 + 61*60)); echo "\$ date -u set to +3 days: $(adb shell date)" >> "$L12"
sleep 5
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$E12/c1.xml"; tap_id "$E12/c1.xml" "$(grep -o 'resource-id="applist_header:[^"]*"' "$E12/c1.xml" | head -1 | sed 's/resource-id="//; s/"$//')"; sleep 1.5
dump "$E12/c2.xml"; tap_id "$E12/c2.xml" jump_cell:L; sleep 2
dump "$E12/after_3_days.xml"; adb exec-out screencap -p > "$E12/after_3_days.png"
echo "after 3 days: $($N "$E12/after_3_days.xml" applist_row:$PKG)" >> "$L12"
echo "## E12: launch once" >> "$L12"
tap_id "$E12/after_3_days.xml" applist_row:$PKG; sleep 5
echo "resumed: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity)" >> "$L12"
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$E12/c3.xml"; tap_id "$E12/c3.xml" "$(grep -o 'resource-id="applist_header:[^"]*"' "$E12/c3.xml" | head -1 | sed 's/resource-id="//; s/"$//')"; sleep 1.5
dump "$E12/c4.xml"; tap_id "$E12/c4.xml" jump_cell:L; sleep 2
dump "$E12/after_launch.xml"; echo "after launch: $($N "$E12/after_launch.xml" applist_row:$PKG)" >> "$L12"
diag | grep "\[applist\]" | grep -i "$PKG\|caption\|launch" | tail -2 >> "$L12"
echo "## E12: uninstall" >> "$L12"
echo "\$ adb uninstall $PKG: $(adb uninstall $PKG)" >> "$L12"; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2; adb shell input swipe 900 1200 150 1200 250; sleep 2
dump "$E12/c5.xml"; tap_id "$E12/c5.xml" "$(grep -o 'resource-id="applist_header:[^"]*"' "$E12/c5.xml" | head -1 | sed 's/resource-id="//; s/"$//')"; sleep 1.5
dump "$E12/jump_after_uninstall.xml"
echo "after uninstall: L cell $(grep -o 'resource-id="jump_cell:L"[^>]*' "$E12/jump_after_uninstall.xml" | grep -o 'enabled="[a-z]*"')" >> "$L12"
adb shell input keyevent KEYCODE_BACK; sleep 1
rows=0; for i in 1 2 3 4 5 6 7 8; do dump "$E12/w.xml"; grep -q "applist_row:$PKG" "$E12/w.xml" && rows=1; adb shell input swipe 540 1900 540 700 300; sleep 1; done
echo "row present anywhere in the list after uninstall: $rows" >> "$L12"
rm -f "$E12"/c[1-5].xml "$E12/w.xml"
echo "## restore (RV12 clock restore, then network)" | tee -a "$L9" >> "$L12"
adb shell "date -u $(date -u +%m%d%H%M%Y.%S)" >/dev/null
adb shell settings put global auto_time 1
adb unroot >/dev/null; adb wait-for-device; sleep 3
R="host $(date +%s) device $(adb shell date +%s | tr -d '\r') auto_time=$(adb shell settings get global auto_time) root=$(adb shell id -u)"
echo "$R" | tee -a "$L9" >> "$L12"
adb shell cmd connectivity airplane-mode disable; sleep 8
echo "airplane mode disabled: $(adb shell dumpsys connectivity | grep -m1 'Active default network')" | tee -a "$L9" >> "$L12"

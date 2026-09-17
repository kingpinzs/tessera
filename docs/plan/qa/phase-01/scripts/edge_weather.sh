#!/usr/bin/env bash
# Edge cases: weather (location denied, location services off, units change, a new location).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/edge_weather.txt
wlog() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep '\[weather\]' | tail -2 | sed 's/.*\[weather\] //' | tr '\n' ' '; }
echo "# Edge cases: weather $(date -Iseconds)" > "$LOG"
echo '## location permission revoked' >> "$LOG"
adb shell pm revoke app.tileshell android.permission.ACCESS_COARSE_LOCATION
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 10
echo "feed: $(wlog)" >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_location_revoked.xml"
echo "checklist location row: $(grep -o 'checklist:location:[a-z_]*' "$OUT/checklist_location_revoked.xml" | head -1)" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 2
dump "$OUT/weather_tile_no_location.xml"
echo "weather tile: $(python3 "$(dirname "$0")/nodes.py" "$OUT/weather_tile_no_location.xml" tile:shell:weather)" >> "$LOG"
echo '## location services off (permission granted again)' >> "$LOG"
adb shell pm grant app.tileshell android.permission.ACCESS_COARSE_LOCATION
adb shell settings put secure location_mode 0
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 12
echo "feed: $(wlog)" >> "$LOG"
adb shell settings put secure location_mode 3
echo '## a new location (emulator geo fix to Denver)' >> "$LOG"
adb emu geo fix -104.9903 39.7392 >/dev/null 2>&1
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 14
echo "feed: $(wlog)" >> "$LOG"
adb shell am start -n app.tileshell/.weather.WeatherActivity >/dev/null 2>&1; sleep 6
dump "$OUT/weather_denver.xml"; adb exec-out screencap -p > "$OUT/weather_denver.png"
echo "weather page: location $(python3 "$(dirname "$0")/nodes.py" "$OUT/weather_denver.xml" weather_location), temp $(python3 "$(dirname "$0")/nodes.py" "$OUT/weather_denver.xml" weather_current_temp)" >> "$LOG"
echo '## units change (F -> C) on the Weather page' >> "$LOG"
read X1 Y1 X2 Y2 < <(bounds "$OUT/weather_denver.xml" weather_units)
adb shell "input tap $(( (X1 + X2) / 2 )) $(( Y1 + (Y2 - Y1) / 4 ))"
sleep 6; dump "$OUT/weather_celsius.xml"; adb exec-out screencap -p > "$OUT/weather_celsius.png"
echo "after tapping C: temp $(python3 "$(dirname "$0")/nodes.py" "$OUT/weather_celsius.xml" weather_current_temp), units row $(python3 "$(dirname "$0")/nodes.py" "$OUT/weather_celsius.xml" weather_units)" >> "$LOG"
echo "feed: $(wlog)" >> "$LOG"

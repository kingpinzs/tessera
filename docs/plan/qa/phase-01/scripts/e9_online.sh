#!/usr/bin/env bash
# E9 / E9b online part: fresh device fetch, the same request from the host at the same time, tile and Weather app dumps.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E09.txt
diag() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener; }
echo "# E9 $(date -Iseconds)" > "$LOG"
before=$(diag | grep -c "\[weather\] fetch ok")
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME
for i in $(seq 1 60); do n=$(diag | grep -c "\[weather\] fetch ok"); [ "$n" -gt 0 ] && diag | grep -q "\[weather\] fetch ok" && break; sleep 2; done
URL=$(diag | grep "\[weather\] fetch provider" | tail -1 | sed 's/.*request=//' | tr -d '\r')
curl -s "$URL" -o "$OUT/open_meteo_response.json"; echo "host fetch at $(date -Iseconds) rc=$?" >> "$LOG"
diag | grep "\[weather\]" | tail -6 > "$OUT/device_weather_diag.txt"
cat "$OUT/device_weather_diag.txt" >> "$LOG"
echo "request: $URL" >> "$LOG"
sleep 3
for s in 1 2 3 4 5 6; do dump "$OUT/start_weather_$s.xml"; echo "tile sample $s: $(python3 "$(dirname "$0")/nodes.py" "$OUT/start_weather_$s.xml" tile:shell:weather)" >> "$LOG"; sleep 2.5; done
adb exec-out screencap -p > "$OUT/start_weather.png"
dump "$OUT/start_for_tap.xml"; tap_id "$OUT/start_for_tap.xml" tile:shell:weather; sleep 4
dump "$OUT/weather_app_top.xml"; adb exec-out screencap -p > "$OUT/weather_app_top.png"
adb shell input swipe 540 1700 540 700 400; sleep 2
dump "$OUT/weather_app_mid.xml"; adb exec-out screencap -p > "$OUT/weather_app_mid.png"
adb shell input swipe 540 1700 540 500 400; sleep 2
dump "$OUT/weather_app_bottom.xml"; adb exec-out screencap -p > "$OUT/weather_app_bottom.png"
adb shell input keyevent KEYCODE_HOME

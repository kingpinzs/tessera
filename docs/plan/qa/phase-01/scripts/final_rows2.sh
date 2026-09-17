#!/usr/bin/env bash
# Final pass, second batch: E3, E5, E6, E7, E9, E13 core, E19, E21 and a short E10 against the same APK.
source "$(dirname "$0")/ui.sh"
Q=$1; D=$(dirname "$0"); OUT=$Q/FINAL; LOG=$OUT/final_pass.txt
say() { echo "$*" >> "$LOG"; }
diag() { adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener; }

say "## E3 epx scaling (1440x3120, 720x1560, density 420 and 560, font 1.3)"
mkdir -p "$OUT/E03"
adb shell input keyevent KEYCODE_HOME; sleep 4; dump "$OUT/E03/base.xml"
for spec in "wm size 1440x3120:size1440" "wm size 720x1560:size720" "wm density 420:d420" "wm density 560:d560"; do
  cmd=${spec%%:*}; name=${spec##*:}
  adb shell $cmd >/dev/null 2>&1; sleep 6; adb shell input keyevent KEYCODE_HOME; sleep 5
  dump "$OUT/E03/$name.xml"
  adb shell wm size reset >/dev/null 2>&1; adb shell wm density reset >/dev/null 2>&1; sleep 5
done
adb shell settings put system font_scale 1.3; sleep 5; adb shell input keyevent KEYCODE_HOME; sleep 5
dump "$OUT/E03/font13.xml"; adb shell settings put system font_scale 1.0; sleep 4
for f in size1440 size720 d420 d560 font13; do
  say "E3 $f: $(python3 "$D/epx_compare.py" "$OUT/E03/base.xml" "$OUT/E03/$f.xml" 2>&1 | tail -1)"
done
say "E3 activity created count: $(diag | grep -c 'StartActivity created')"

say "## E5 the Messaging row tile shows a new message"
mkdir -p "$OUT/E05"; adb shell input keyevent KEYCODE_HOME; sleep 4
adb emu sms send 5551234 "Final pass message" >/dev/null 2>&1; sleep 4
say "E5: $(diag | grep -E '\[notif\] posted org.fossify.messages|\[render\] tile=dock:slot:MESSAGING' | tail -2 | sed 's/.*wall=//' | tr '\n' ' ')"

say "## E6 photos, E7 calendar, E9 weather feeds"
say "E6: $(diag | grep '\[engine\] publish feed:photos' | tail -1 | sed 's/.*publish //')"
say "E7: $(diag | grep '\[calendar\]' | tail -1 | sed 's/.*\[calendar\] //')"
say "E9: $(diag | grep '\[weather\] publish' | tail -1 | sed 's/.*\[weather\] //')"

say "## E19 bars on Start, the app list, a Settings page and Weather"
mkdir -p "$OUT/E19"
bars() { # bars <label>
  dump "$OUT/E19/$1.xml"
  local ins; ins=$(adb shell dumpsys window | grep -m2 -E "type=statusBars|type=navigationBars" | tr -s ' ' | tr '\n' ' ')
  say "  $1: status bar $(grep -c 'resource-id=\"w10m_status_bar\"' "$OUT/E19/$1.xml") nav bar $(grep -c 'resource-id=\"w10m_nav_bar\"' "$OUT/E19/$1.xml"); insets $ins"
}
adb shell input keyevent KEYCODE_HOME; sleep 4; bars start
adb shell input swipe 900 1200 150 1200 250; sleep 3; bars applist
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page START_THEME >/dev/null 2>&1; sleep 4; bars settings
adb shell am start -n app.tileshell/.weather.WeatherActivity >/dev/null 2>&1; sleep 5; bars weather
adb shell input keyevent KEYCODE_HOME; sleep 4

say "## E21 bottom tile row geometry"
mkdir -p "$OUT/E21"; dump "$OUT/E21/start.xml"
say "E21: $(python3 "$D/e21_row.py" "$OUT/E21/start.xml" 2>&1 | tail -3 | tr '\n' ' ')"

say "## E10 short: one 170 s timing segment plus an exit and entrance capture"
mkdir -p "$OUT/E10"
adb shell input keyevent KEYCODE_HOME; sleep 3
for i in 1 2 3 4; do adb shell input swipe 540 700 540 1900 200; sleep 0.4; done; sleep 10
dump "$OUT/E10/timing_start.xml"
adb shell rm -f /sdcard/final_timing.mp4
adb shell screenrecord --bit-rate 20000000 --time-limit 170 /sdcard/final_timing.mp4
adb pull /sdcard/final_timing.mp4 "$OUT/E10/e10_timing_1.mp4" >/dev/null
cp "$OUT/E10/e10_timing_1.mp4" "$OUT/E10/e10_timing_2.mp4"; cp "$OUT/E10/e10_timing_1.mp4" "$OUT/E10/e10_timing_3.mp4"
diag | grep '\[tile_anim\]' > "$OUT/E10/timing_tile_anim_diag.txt"
python3 "$D/e10_timing.py" "$OUT/E10" 1 > "$OUT/E10/timing_analysis.txt" 2>/dev/null
say "E10 timing (one segment): $(grep -E '^(slot:CALENDAR|slot:PHOTOS|shell:weather)' "$OUT/E10/timing_analysis.txt" | tr '\n' ' ')"
bash "$D/e10_exit_capture.sh" "$OUT/E10" final_exit slot:CALENDAR >/dev/null 2>&1
say "E10 exit: $(python3 "$D/e10_exit.py" "$OUT/E10/final_exit_start.xml" "$OUT/E10/e10_final_exit.mp4" slot:CALENDAR "$OUT/E10/timing_tile_anim_diag.txt" 2>/dev/null | grep -E 'every static band|scale per frame' | head -2 | tr '\n' ' ')"

#!/usr/bin/env bash
# E4b: picker lists only category handlers; choosing assigns; force-stop and reboot keep it; reassign from Settings > Tile apps.
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E04.txt; N="python3 $(dirname "$0")/nodes.py"
slotdesc() { dump "$OUT/$1.xml"; $N "$OUT/$1.xml" "tile:slot:MAIL"; }
echo "# E4b $(date -Iseconds)" >> "$LOG"
bars() { echo "status_bar=$(grep -c 'resource-id="w10m_status_bar"' "$1") nav_back=$(grep -c 'resource-id="nav_back"' "$1") nav_windows=$(grep -c 'resource-id="nav_windows"' "$1")"; }
adb shell am force-stop app.tileshell
adb shell "run-as app.tileshell sh -c 'cat > files/start_layout.json'" < "$OUT/layout_no_slots.json"
echo "reset to no explicit slots: $(adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"])')" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3
dump "$OUT/e4b_start.xml"
echo '## tap unassigned Mail tile' >> "$LOG"
tap_id "$OUT/e4b_start.xml" tile:slot:MAIL; sleep 2
dump "$OUT/e4b_picker_mail.xml"; adb exec-out screencap -p > "$OUT/e4b_picker_mail.png"
echo "picker shown: $(grep -c 'resource-id="slot_picker"' "$OUT/e4b_picker_mail.xml"); bars on the picker: $(bars "$OUT/e4b_picker_mail.xml")" >> "$LOG"
echo "picker candidates: $(grep -o 'slot_candidate:[^"]*' "$OUT/e4b_picker_mail.xml" | sort | tr '\n' ' ')" >> "$LOG"
echo "APP_EMAIL handlers: $(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.APP_EMAIL | grep '/' | cut -d/ -f1 | tr -d ' ' | sort | tr '\n' ' ')" >> "$LOG"
tap_id "$OUT/e4b_picker_mail.xml" slot_candidate:com.fsck.k9; sleep 2
echo "after choosing com.fsck.k9: $(slotdesc e4b_after_choose)" >> "$LOG"
echo "layout slots: $(adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"])')" >> "$LOG"
echo '## tap unassigned Camera row tile, then Back closes the picker' >> "$LOG"
tap_id "$OUT/e4b_after_choose.xml" tile:dock:slot:CAMERA; sleep 2
dump "$OUT/e4b_picker_camera.xml"
echo "camera picker candidates: $(grep -o 'slot_candidate:[^"]*' "$OUT/e4b_picker_camera.xml" | sort | tr '\n' ' ')" >> "$LOG"
adb shell input keyevent KEYCODE_BACK; sleep 2; dump "$OUT/e4b_camera_back.xml"
echo "picker after Back: $(grep -c 'resource-id="slot_picker"' "$OUT/e4b_camera_back.xml"); camera tile: $($N "$OUT/e4b_camera_back.xml" tile:dock:slot:CAMERA)" >> "$LOG"
echo '## Camera picker again, drawn Windows key closes it' >> "$LOG"
tap_id "$OUT/e4b_camera_back.xml" tile:dock:slot:CAMERA; sleep 2; dump "$OUT/e4b_picker_camera2.xml"
tap_id "$OUT/e4b_picker_camera2.xml" nav_windows; sleep 2; dump "$OUT/e4b_camera_windows.xml"
echo "picker before Windows: $(grep -c 'resource-id="slot_picker"' "$OUT/e4b_picker_camera2.xml"); after Windows: $(grep -c 'resource-id="slot_picker"' "$OUT/e4b_camera_windows.xml"); start_page: $(grep -c 'resource-id="start_page"' "$OUT/e4b_camera_windows.xml")" >> "$LOG"
echo '## force-stop' >> "$LOG"
echo '$ adb shell am force-stop app.tileshell' >> "$LOG"; adb shell am force-stop app.tileshell
adb shell input keyevent KEYCODE_HOME; sleep 4
echo "after force-stop: $(slotdesc e4b_after_forcestop)" >> "$LOG"
echo '$ adb shell am force-stop com.fsck.k9' >> "$LOG"; adb shell am force-stop com.fsck.k9; sleep 2
echo "after force-stop of the assigned app: $(slotdesc e4b_after_forcestop_k9)" >> "$LOG"
echo '## reboot' >> "$LOG"
adb reboot; adb wait-for-device
for i in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break; sleep 2; done
sleep 10; adb shell wm dismiss-keyguard; adb shell input keyevent KEYCODE_HOME; sleep 4
echo "after reboot: $(slotdesc e4b_after_reboot)" >> "$LOG"
echo '## reassign from Settings > Tile apps' >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page TILE_APPS >/dev/null 2>&1; sleep 3
dump "$OUT/e4b_tile_apps.xml"; echo "before: $($N "$OUT/e4b_tile_apps.xml" tile_app_slot:MAIL)" >> "$LOG"
tap_id "$OUT/e4b_tile_apps.xml" tile_app_slot:MAIL; sleep 2
dump "$OUT/e4b_tile_apps_picker.xml"
echo "picker candidates: $(grep -o 'slot_candidate:[^"]*' "$OUT/e4b_tile_apps_picker.xml" | sort | tr '\n' ' '); bars: $(bars "$OUT/e4b_tile_apps_picker.xml")" >> "$LOG"
adb exec-out screencap -p > "$OUT/e4b_tile_apps_picker.png"
adb shell input keyevent KEYCODE_BACK; sleep 2; dump "$OUT/e4b_tile_apps_picker_back.xml"
echo "Back on the Settings picker: picker=$(grep -c 'resource-id="slot_picker"' "$OUT/e4b_tile_apps_picker_back.xml") tile_apps_rows=$(grep -c 'resource-id="tile_app_slot:MAIL"' "$OUT/e4b_tile_apps_picker_back.xml")" >> "$LOG"
tap_id "$OUT/e4b_tile_apps_picker_back.xml" tile_app_slot:MAIL; sleep 2; dump "$OUT/e4b_tile_apps_picker.xml"
tap_id "$OUT/e4b_tile_apps_picker.xml" slot_candidate:eu.faircode.email; sleep 2
dump "$OUT/e4b_tile_apps_after.xml"; echo "after: $($N "$OUT/e4b_tile_apps_after.xml" tile_app_slot:MAIL)" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 3
echo "Start after reassign: $(slotdesc e4b_start_after_reassign)" >> "$LOG"
adb exec-out screencap -p > "$OUT/e4b_start_after_reassign.png"
echo "layout slots: $(adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"])')" >> "$LOG"
echo '$ crash buffer since this run' >> "$LOG"; adb logcat -d -b crash | grep -c "FATAL" >> "$LOG"

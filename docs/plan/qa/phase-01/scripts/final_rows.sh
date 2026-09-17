#!/usr/bin/env bash
# Final pass: re-runs the scripted acceptance rows against the freshly installed APK, into FINAL/<row>/.
# usage: final_rows.sh <qa dir> <fixtures dir>
source "$(dirname "$0")/ui.sh"
Q=$1; F=$2; D=$(dirname "$0"); OUT=$Q/FINAL; LOG=$OUT/final_pass.txt
say() { echo "$*" >> "$LOG"; }
say "## grants"
adb shell cmd notification allow_listener app.tileshell/app.tileshell.feeds.TileNotificationListener
for p in android.permission.READ_MEDIA_IMAGES android.permission.READ_CALENDAR android.permission.ACCESS_COARSE_LOCATION; do adb shell pm grant app.tileshell $p; done
adb shell appops set app.tileshell GET_USAGE_STATS allow
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 8
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page CHECKLIST >/dev/null 2>&1; sleep 3
dump "$OUT/checklist_granted.xml"
say "checklist after granting: $(grep -o 'checklist:[a-z_]*:[a-z_]*' "$OUT/checklist_granted.xml" | tr '\n' ' ')"
adb shell input keyevent KEYCODE_HOME; sleep 4

say "## E4 and E4b slots"
mkdir -p "$OUT/E04"; cp "$Q/E04/layout_before_E4.json" "$OUT/E04/" 2>/dev/null
bash "$D/e4_part1.sh" "$OUT/E04" >/dev/null 2>&1
say "$(grep -E '^(APP_EMAIL|tile:slot:MAIL|tile_app_slot:MAIL)' "$OUT/E04/E04.txt" | tail -3 | tr '\n' ' ')"
bash "$D/e4_part2.sh" "$OUT/E04" >/dev/null 2>&1
say "E4b: $(grep -E 'after choosing|after reboot|Start after reassign' "$OUT/E04/E04.txt" | tail -3 | tr '\n' ' ')"

say "## E11 tile launch"
mkdir -p "$OUT/E11"; bash "$D/e11.sh" "$OUT/E11" >/dev/null 2>&1
say "E11: $(grep -m1 'topResumedActivity' "$OUT/E11/E11.txt" | tr -s ' ')"

say "## E15, E16, E17 Live Tile API"
adb install -r "$F/tileclient-a-debug.apk" >/dev/null 2>&1
adb shell pm grant app.tileshell.testclient.a android.permission.POST_NOTIFICATIONS
mkdir -p "$OUT/E15" "$OUT/E16" "$OUT/E17"
python3 - "$Q" "$OUT" <<'PY'
import json, sys, subprocess
q, out = sys.argv[1], sys.argv[2]
layout = json.loads(subprocess.run(["adb", "shell", "run-as", "app.tileshell", "cat", "files/start_layout.json"], capture_output=True, text=True).stdout)
layout["placements"].append({"key": "app:app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity:0", "x": 0, "y": 6, "size": "MEDIUM"})
open(f"{out}/layout_with_client.json", "w").write(json.dumps(layout))
PY
adb shell am force-stop app.tileshell
adb shell "run-as app.tileshell sh -c 'cat > files/start_layout.json'" < "$OUT/layout_with_client.json"
adb shell input keyevent KEYCODE_HOME; sleep 6
bash "$D/e15.sh" "$OUT/E15" >/dev/null 2>&1
say "E15: $(grep -E 'update faces seen|badge node:' -A1 "$OUT/E15/E15.txt" | tail -3 | tr '\n' ' ')"
bash "$D/e16.sh" "$OUT/E16" >/dev/null 2>&1
say "E16: $(grep -c 'ok=false' "$OUT/E16/E16.txt") refusals recorded"
adb install -r "$F/tileclient-b-debug.apk" >/dev/null 2>&1
bash "$D/e17.sh" "$OUT/E17" >/dev/null 2>&1
say "E17: $(grep -E '^(notification_7|legacy_3|api_5|after_api_clear|after_legacy_clear|after_notification_cancel)' "$OUT/E17/E17.txt" | sed 's/:.*badge\] //' | tr '\n' ' ')"

say "## E20 Back on Start"
mkdir -p "$OUT/E20"; bash "$D/e20_part1.sh" "$OUT/E20" >/dev/null 2>&1
say "E20 part 1: $(grep -E 'Timer tab selected after' "$OUT/E20/E20.txt" | tr '\n' ' ')"
bash "$D/e20_part2.sh" "$OUT/E20" >/dev/null 2>&1
say "E20 part 2: $(grep -A1 'after drawn Back' "$OUT/E20/E20.txt" | grep topResumed | tail -2 | tr -s ' ' | tr '\n' ' ')"

say "## E12 a newly installed app carries the caption"
mkdir -p "$OUT/E12"; cp "$Q/E12/test_app_librecontactsbackup_25.apk" "$OUT/E12/" 2>/dev/null
bash "$D/e12_part2.sh" "$OUT/E12" >/dev/null 2>&1
say "E12: $(grep -E 'row:|caption node:' "$OUT/E12/E12.txt" | tail -2 | tr '\n' ' ')"

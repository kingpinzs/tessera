#!/usr/bin/env bash
# E4: default slot resolution with no explicit assignments (role holders, one handler, 2+ handlers).
source "$(dirname "$0")/ui.sh"
OUT=$1; LOG=$OUT/E04.txt; N="python3 $(dirname "$0")/nodes.py"
echo "# E4 run 2 (fixture swaps per INDEX Change Log 2026-09-17 QA-gate agent calls) $(date -Iseconds)" >> "$LOG"
adb shell run-as app.tileshell cat files/start_layout.json > "$OUT/layout_before_E4.json"
echo "saved layout before E4 (restored at the end): $(cat "$OUT/layout_before_E4.json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"])')" >> "$LOG"
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); d["slots"]={}; open(sys.argv[2],"w").write(json.dumps(d))' "$OUT/layout_before_E4.json" "$OUT/layout_no_slots.json"
adb shell am force-stop app.tileshell
adb shell "run-as app.tileshell sh -c 'cat > files/start_layout.json'" < "$OUT/layout_no_slots.json"
echo "wrote layout with no explicit slots: $(adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["slots"])')" >> "$LOG"
echo "## role holders" >> "$LOG"
for r in DIALER SMS BROWSER; do echo "$r: $(adb shell cmd role get-role-holders android.app.role.$r)" >> "$LOG"; done
echo "## handlers per category after fixture swaps" >> "$LOG"
for c in APP_EMAIL APP_MUSIC APP_MAPS APP_MARKET APP_GALLERY APP_CONTACTS APP_CALENDAR; do
  echo "$c: $(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.$c | grep '/' | tr -d ' ' | tr '\n' ' ')" >> "$LOG"
done
echo "STILL_IMAGE_CAMERA: $(adb shell cmd package query-activities --brief -a android.media.action.STILL_IMAGE_CAMERA | grep '/' | tr -d ' ' | tr '\n' ' ')" >> "$LOG"
echo "VIEW https BROWSABLE: $(adb shell cmd package query-activities --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://example.com | grep '/' | tr -d ' ' | tr '\n' ' ')" >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 4
dump "$OUT/start_default_slots.xml"
echo "## Start tiles (content-desc = app label when assigned, slot label when unassigned; grid labels add 'Tap to choose')" >> "$LOG"
$N "$OUT/start_default_slots.xml" tile: >> "$LOG"
adb shell am start -n app.tileshell/.settings.SettingsActivity --activity-single-top --es page TILE_APPS >/dev/null; sleep 3
dump "$OUT/tile_apps_default.xml"
echo "## Settings > Tile apps" >> "$LOG"
$N "$OUT/tile_apps_default.xml" tile_app_slot: >> "$LOG"
adb shell dumpsys activity service app.tileshell/.feeds.TileNotificationListener | grep -E "\[slots\]" | tail -3 >> "$LOG"
adb shell input keyevent KEYCODE_HOME; sleep 2

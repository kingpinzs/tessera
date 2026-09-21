#!/usr/bin/env bash
# E6, the folder half: `adb uninstall` of a pinned app removes its tile from INSIDE a folder too, and the
# folder left with one tile dissolves into that tile (H19). The folder is seeded into the layout file rather
# than built by hand with a drag, so the uninstall - not the gesture - is what this row measures.
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E6d.txt
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
A=app.tileshell.testclient.a
B=app.tileshell.testclient.b
WORK=/tmp/claude-1000/-home-jeremyking/277bd9d2-7817-47e9-bc8f-0d5611a2abef/scratchpad/a35
TILE_A="tile:app:$A/app.tileshell.testclient.VerbActivity:0"
TILE_B="tile:app:$B/app.tileshell.testclient.VerbActivity:0"

echo "# E6d $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "\$ adb install -r tileclient-a: $(adb install -r "$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk" | tail -1)" >> "$LOG"
echo "\$ adb install -r tileclient-b: $(adb install -r "$REPO/testapps/tileclient-b/build/outputs/apk/debug/tileclient-b-debug.apk" | tail -1)" >> "$LOG"
sleep 4

adb shell run-as app.tileshell cat files/start_layout.json > "$WORK/e6d_before.json"
python3 - "$WORK/e6d_before.json" "$WORK/e6d_seeded.json" "$A" "$B" <<'PY'
import json, sys
src, dst, a, b = sys.argv[1:5]
d = json.load(open(src))
d["order"] = [o for o in d["order"] if a not in o["key"] and b not in o["key"] and o["key"] != "folder:f1"]
d["order"].append({"key": "folder:f1", "size": "MEDIUM"})
d["folders"] = [{"id": "f1", "name": "QA", "members": [
    {"key": f"app:{a}/app.tileshell.testclient.VerbActivity:0", "size": "MEDIUM"},
    {"key": f"app:{b}/app.tileshell.testclient.VerbActivity:0", "size": "MEDIUM"}]}]
json.dump(d, open(dst, "w"))
print("seeded folder f1 with two members")
PY
adb shell am force-stop app.tileshell
sleep 1
adb push "$WORK/e6d_seeded.json" /data/local/tmp/seed.json > /dev/null
adb shell chmod 666 /data/local/tmp/seed.json
adb shell run-as app.tileshell cp /data/local/tmp/seed.json /data/data/app.tileshell/files/start_layout.json
adb shell am force-stop app.tileshell
sleep 2
go_start
sleep 3
scroll_to_id "$OUT/e6d_folder.xml" "tile:folder:f1" || echo "SEEDED FOLDER NOT DRAWN" >> "$LOG"
adb exec-out screencap -p > "$OUT/e6d_1_folder.png"
echo "folder tile on Start: $(grep -o 'resource-id="tile:folder:f1"' "$OUT/e6d_folder.xml" | wc -l); members in the layout: $(adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys;print([m["key"] for m in json.load(sys.stdin)["folders"][0]["members"]])')" >> "$LOG"

echo '' >> "$LOG"
echo "\$ adb uninstall $A   # a tile that lives inside the folder" >> "$LOG"
echo "result: $(adb uninstall $A | tail -1)" >> "$LOG"
sleep 4
go_start
sleep 2
dump "$OUT/e6d_after.xml"
adb exec-out screencap -p > "$OUT/e6d_2_after_uninstall.png"
adb shell run-as app.tileshell cat files/start_layout.json > "$OUT/e6d_layout_after.json"
echo "layout mentions A: $(grep -c "$A" "$OUT/e6d_layout_after.json") (0 = removed from inside the folder)" >> "$LOG"
echo "folders left: $(python3 -c 'import json;print(json.load(open("'"$OUT"'/e6d_layout_after.json"))["folders"])') (empty = the one-tile folder dissolved, H19)" >> "$LOG"
echo "B took the folder's place in the order: $(python3 -c 'import json;print([o["key"] for o in json.load(open("'"$OUT"'/e6d_layout_after.json"))["order"]][-2:])')" >> "$LOG"
echo "tile B drawn on Start: $(grep -o "resource-id=\"$TILE_B\"" "$OUT/e6d_after.xml" | wc -l); tile A: $(grep -o "resource-id=\"$TILE_A\"" "$OUT/e6d_after.xml" | wc -l); folder tile: $(grep -o 'resource-id="tile:folder:f1"' "$OUT/e6d_after.xml" | wc -l)" >> "$LOG"
echo "diagnostics:" >> "$LOG"
diag | grep -E "LauncherApps\.onPackageRemoved|\[layout\] (packages removed|folder)" | tail -4 >> "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"

#!/usr/bin/env bash
# Build task 5, the hole no callback can cover: a package that went away while the shell process was not
# running delivers no LauncherApps callback at all, so the next process start reconciles the layout against
# the package manager. Proved deterministically (a plain force-stop does not hold: the bound notification
# listener respawns the process within a second, and then the live callback handles the uninstall instead).
#
# The layout is written with two tiles the shell has never seen before it starts:
#   - a package that does not exist at all -> must be reconciled away
#   - a package that is installed but DISABLED -> must be kept
# The layout file is backed up first and restored by restore.sh.
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E6c.txt
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
APK_B=$REPO/testapps/tileclient-b/build/outputs/apk/debug/tileclient-b-debug.apk
B=app.tileshell.testclient.b
GHOST=app.tileshell.testclient.ghost
TILE_B="tile:app:$B/app.tileshell.testclient.VerbActivity:0"
TILE_GHOST="tile:app:$GHOST/app.tileshell.testclient.VerbActivity:0"
WORK=/tmp/claude-1000/-home-jeremyking/277bd9d2-7817-47e9-bc8f-0d5611a2abef/scratchpad/a35

echo "# E6c $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "\$ adb install -r tileclient-b: $(adb install -r "$APK_B" | tail -1)" >> "$LOG"
sleep 3
echo "\$ adb shell pm disable-user --user 0 $B: $(adb shell pm disable-user --user 0 $B | tail -1)" >> "$LOG"
echo "\$ adb shell pm list packages -d | grep $B: $(adb shell pm list packages -d | grep $B | tr -d '\r') (installed, disabled)" >> "$LOG"
echo "\$ adb shell pm list packages | grep $GHOST: '$(adb shell pm list packages | grep $GHOST | tr -d '\r')' (never installed)" >> "$LOG"

adb shell run-as app.tileshell cat files/start_layout.json > "$WORK/e6c_layout_before.json"
python3 - "$WORK/e6c_layout_before.json" "$WORK/e6c_layout_seeded.json" "$B" "$GHOST" <<'PY'
import json, sys
src, dst, b, ghost = sys.argv[1:5]
d = json.load(open(src))
d["order"] = [o for o in d["order"] if b not in o["key"] and ghost not in o["key"]]
d["order"].append({"key": f"app:{b}/app.tileshell.testclient.VerbActivity:0", "size": "MEDIUM"})
d["order"].append({"key": f"app:{ghost}/app.tileshell.testclient.VerbActivity:0", "size": "MEDIUM"})
json.dump(d, open(dst, "w"))
print("seeded order tail:", [o["key"] for o in d["order"]][-2:])
PY
adb shell am force-stop app.tileshell
sleep 1
# run-as cannot create the file through a redirect (the shell's own write is refused), so the seed is pushed
# and copied by a process running as the app.
adb push "$WORK/e6c_layout_seeded.json" /data/local/tmp/seed.json > /dev/null
adb shell chmod 666 /data/local/tmp/seed.json
adb shell run-as app.tileshell cp /data/local/tmp/seed.json /data/data/app.tileshell/files/start_layout.json
sleep 1
echo "seed landed on the device: $(adb shell run-as app.tileshell cat files/start_layout.json | grep -c "$GHOST") ghost entry" >> "$LOG"
echo "layout seeded with a disabled package's tile and a never-installed package's tile while the shell was stopped" >> "$LOG"
adb shell am force-stop app.tileshell
sleep 2
go_start
sleep 3
dump "$OUT/e6c_after.xml"
adb exec-out screencap -p > "$OUT/e6c_1_after_restart.png"
echo "ghost tile on Start after the process start: $(grep -c "resource-id=\"$TILE_GHOST\"" "$OUT/e6c_after.xml") (0 = reconciled away)" >> "$LOG"
echo "disabled package's tile on Start: $(grep -c "resource-id=\"$TILE_B\"" "$OUT/e6c_after.xml") (1 = kept)" >> "$LOG"
adb shell run-as app.tileshell cat files/start_layout.json > "$OUT/e6c_layout_after.json"
echo "layout file mentions the ghost: $(grep -c "$GHOST" "$OUT/e6c_layout_after.json"); mentions the disabled package: $(grep -c "$B" "$OUT/e6c_layout_after.json")" >> "$LOG"
echo "diagnostics:" >> "$LOG"
diag | grep -E "\[app\] packages gone|\[layout\] packages removed|LauncherApps\." | tail -4 >> "$LOG"
echo "\$ adb shell pm enable $B: $(adb shell pm enable $B | tail -1)" >> "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"

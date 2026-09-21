#!/usr/bin/env bash
# E6 (phase 02 build task 5): `adb install -r` of an update KEEPS a pinned app's tile; `adb uninstall` removes
# it. The LauncherApps callbacks Android actually delivered for each are quoted from the shell's ring.
# Precondition: tileclient-a is installed and pinned (e2_pin.sh leaves it that way).
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E6.txt
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
APK=$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
PKG=app.tileshell.testclient.a
TILE="tile:app:$PKG/app.tileshell.testclient.VerbActivity:0"

echo "# E6 $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"

go_start
scroll_to_id "$OUT/e6_before.xml" "$TILE" || echo "PRECONDITION FAILED: no pinned tile to test" >> "$LOG"
echo "tile on Start before: $(grep -c "resource-id=\"$TILE\"" "$OUT/e6_before.xml")" >> "$LOG"

echo '' >> "$LOG"
echo '## adb install -r (update): the tile must stay' >> "$LOG"
echo "\$ adb install -r tileclient-a-debug.apk: $(adb install -r "$APK" | tail -1)" >> "$LOG"
sleep 4
echo "LauncherApps callbacks delivered:" >> "$LOG"
diag | grep "LauncherApps\." | tail -6 >> "$LOG"
go_start
scroll_to_id "$OUT/e6_after_update.xml" "$TILE" || echo "TILE LOST ON UPDATE" >> "$LOG"
adb exec-out screencap -p > "$OUT/e6_1_after_update.png"
echo "tile on Start after the update: $(grep -c "resource-id=\"$TILE\"" "$OUT/e6_after_update.xml") (1 = kept)" >> "$LOG"
echo "layout order tail: $(adb shell run-as app.tileshell cat files/start_layout.json | python3 -c 'import json,sys;print([o["key"] for o in json.load(sys.stdin)["order"]][-2:])')" >> "$LOG"

echo '' >> "$LOG"
echo '## adb uninstall: the tile must go' >> "$LOG"
echo "\$ adb uninstall $PKG: $(adb uninstall $PKG | tail -1)" >> "$LOG"
sleep 4
echo "LauncherApps callbacks delivered:" >> "$LOG"
diag | grep "LauncherApps\." | tail -6 >> "$LOG"
echo "layout diagnostics:" >> "$LOG"
diag | grep -E "\[layout\] packages removed|\[app\] packages gone" | tail -3 >> "$LOG"
go_start
dump "$OUT/e6_after_uninstall.xml"
adb exec-out screencap -p > "$OUT/e6_2_after_uninstall.png"
echo "tile on Start after the uninstall (top screen): $(grep -c "resource-id=\"$TILE\"" "$OUT/e6_after_uninstall.xml") (0 = removed)" >> "$LOG"
echo "layout file mentions the package: $(adb shell run-as app.tileshell cat files/start_layout.json | grep -c "$PKG") (0 = gone from the layout, folders and row included)" >> "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"

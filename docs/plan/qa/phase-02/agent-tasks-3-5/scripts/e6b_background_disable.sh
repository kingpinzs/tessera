#!/usr/bin/env bash
# Build task 5, the two cases E6 does not name: an uninstall while Start is NOT in the foreground still drops
# the tile, and a package that is only DISABLED keeps its tiles. Fixtures: tileclient-a and tileclient-b.
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E6b.txt
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
APK_A=$REPO/testapps/tileclient-a/build/outputs/apk/debug/tileclient-a-debug.apk
APK_B=$REPO/testapps/tileclient-b/build/outputs/apk/debug/tileclient-b-debug.apk
A=app.tileshell.testclient.a
B=app.tileshell.testclient.b
TILE_A="tile:app:$A/app.tileshell.testclient.VerbActivity:0"
TILE_B="tile:app:$B/app.tileshell.testclient.VerbActivity:0"

echo "# E6b $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "\$ adb install -r tileclient-a: $(adb install -r "$APK_A" | tail -1)" >> "$LOG"
echo "\$ adb install -r tileclient-b: $(adb install -r "$APK_B" | tail -1)" >> "$LOG"
sleep 4
pin_app "$A" "$OUT/e6b_pin_a.xml" || echo "PIN A FAILED" >> "$LOG"
pin_app "$B" "$OUT/e6b_pin_b.xml" || echo "PIN B FAILED" >> "$LOG"
echo "pinned: $(diag | grep '\[applist\] pin to Start' | tail -2 | sed 's/^ *//')" >> "$LOG"

echo '' >> "$LOG"
echo '## uninstall while Start is NOT in the foreground' >> "$LOG"
adb shell am start -n "$B/app.tileshell.testclient.VerbActivity" > /dev/null 2>&1
sleep 3
echo "top resumed activity: $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' ')" >> "$LOG"
echo "\$ adb uninstall $A: $(adb uninstall $A | tail -1)" >> "$LOG"
sleep 4
echo "callbacks + layout while Start was in the background:" >> "$LOG"
diag | grep -E "LauncherApps\.|\[layout\] packages removed" | tail -4 >> "$LOG"
go_start
dump "$OUT/e6b_after_bg_uninstall.xml"
adb exec-out screencap -p > "$OUT/e6b_1_after_background_uninstall.png"
echo "tile A on Start: $(grep -c "resource-id=\"$TILE_A\"" "$OUT/e6b_after_bg_uninstall.xml") (0 = removed); tile B: $(grep -c "resource-id=\"$TILE_B\"" "$OUT/e6b_after_bg_uninstall.xml") (1 = untouched)" >> "$LOG"

echo '' >> "$LOG"
echo '## disable (pm disable-user): the tile must stay' >> "$LOG"
echo "\$ adb shell pm disable-user --user 0 $B: $(adb shell pm disable-user --user 0 $B | tail -1)" >> "$LOG"
sleep 4
echo "callbacks:" >> "$LOG"
diag | grep -E "LauncherApps\.|\[layout\] packages removed" | tail -4 >> "$LOG"
go_start
dump "$OUT/e6b_after_disable.xml"
adb exec-out screencap -p > "$OUT/e6b_2_after_disable.png"
echo "tile B on Start while disabled: $(grep -c "resource-id=\"$TILE_B\"" "$OUT/e6b_after_disable.xml") (1 = kept)" >> "$LOG"
echo "layout file mentions B: $(adb shell run-as app.tileshell cat files/start_layout.json | grep -c "$B")" >> "$LOG"
echo "app list row for B while disabled: $(adb shell pm list packages -d | grep -c $B) (1 = the package is disabled, not gone)" >> "$LOG"
echo "\$ adb shell pm enable $B: $(adb shell pm enable $B | tail -1)" >> "$LOG"
sleep 3
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"

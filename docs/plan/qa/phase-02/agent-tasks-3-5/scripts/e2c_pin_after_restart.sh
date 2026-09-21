#!/usr/bin/env bash
# Edge case "pinning an app already on Start", across a process restart: the layout is re-read from disk, so
# the key the store holds for a pinned app comes from LayoutStore.parseKey rather than from the app list.
# Precondition: tileclient-b is installed and pinned (e6d leaves it that way).
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/E2c.txt
B=app.tileshell.testclient.b
TILE_B="tile:app:$B/app.tileshell.testclient.VerbActivity:0"

echo "# E2c $(date -Iseconds)" > "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo "tiles for B in the layout before: $(adb shell run-as app.tileshell cat files/start_layout.json | grep -o "app:$B[^\"]*" | wc -l)" >> "$LOG"
echo '$ adb shell am force-stop app.tileshell   # the next start re-parses the layout from disk' >> "$LOG"
adb shell am force-stop app.tileshell
sleep 3
go_start
sleep 3
pin_app "$B" "$OUT/e2c_pin.xml" || echo "PIN ATTEMPT FAILED" >> "$LOG"
echo "pin diagnostics:" >> "$LOG"
diag | grep -E "\[layout\] pin app:$B|\[applist\] pin to Start $B" | tail -2 >> "$LOG"
echo "tiles for B in the layout after: $(adb shell run-as app.tileshell cat files/start_layout.json | grep -o "app:$B[^\"]*" | wc -l) (1 = still one tile)" >> "$LOG"
go_start
scroll_to_id "$OUT/e2c_start.xml" "$TILE_B" > /dev/null
adb exec-out screencap -p > "$OUT/e2c_1_start.png"
echo "tiles for B drawn on Start: $(grep -o "resource-id=\"$TILE_B\"" "$OUT/e2c_start.xml" | wc -l)" >> "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"

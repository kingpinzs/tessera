#!/usr/bin/env bash
# Put the emulator back the way this pass found it: the layout file from the backup taken before the first
# check, and the two test fixtures uninstalled. The shell itself is left installed (this worktree's build).
source "$(dirname "$0")/p2.sh"
set -u
OUT=$1; LOG=$OUT/RESTORE.txt
WORK=/tmp/claude-1000/-home-jeremyking/277bd9d2-7817-47e9-bc8f-0d5611a2abef/scratchpad/a35
BACKUP=$WORK/layout_backup_run2.json

echo "# restore $(date -Iseconds)" > "$LOG"
echo "backup taken before the pass: $(wc -c < "$BACKUP") bytes" >> "$LOG"
echo "\$ adb uninstall app.tileshell.testclient.a: $(adb uninstall app.tileshell.testclient.a 2>&1 | tail -1)" >> "$LOG"
echo "\$ adb uninstall app.tileshell.testclient.b: $(adb uninstall app.tileshell.testclient.b 2>&1 | tail -1)" >> "$LOG"
adb shell am force-stop app.tileshell
sleep 1
adb push "$BACKUP" /data/local/tmp/seed.json > /dev/null
adb shell chmod 666 /data/local/tmp/seed.json
adb shell run-as app.tileshell cp /data/local/tmp/seed.json /data/data/app.tileshell/files/start_layout.json
adb shell rm /data/local/tmp/seed.json
adb shell am force-stop app.tileshell
sleep 2
go_start
sleep 3
adb shell run-as app.tileshell cat files/start_layout.json > "$OUT/restore_layout.json"
echo "layout restored byte-for-byte: $(cmp -s "$BACKUP" "$OUT/restore_layout.json" && echo yes || echo no)" >> "$LOG"
dump "$OUT/restore_start.xml"
adb exec-out screencap -p > "$OUT/restore_1_start.png"
echo "tiles drawn after the restore: $(grep -o 'resource-id="tile:[^"]*"' "$OUT/restore_start.xml" | sed 's/resource-id="//; s/"$//' | tr '\n' ' ')" >> "$LOG"
echo "fixtures still installed: '$(adb shell pm list packages | grep testclient | tr -d '\r')' (empty = both gone)" >> "$LOG"
echo "shell package untouched: $(adb shell pm list packages | grep -c app.tileshell$) ; HOME role holder: $(adb shell cmd shortcut get-default-launcher 2>/dev/null | tail -1)" >> "$LOG"
bash "$(dirname "$0")/guard.sh" >> "$LOG"
echo done >> "$LOG"

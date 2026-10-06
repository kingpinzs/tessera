#!/usr/bin/env bash
# E4 (r3 D4): force-stop mid-copy, then relaunch -> the sweep removes the journalled temp.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg f-big
adb logcat -c; ensure_start; files_at $QF; M=$(ring_mark)
pick_op copy big.bin sub
mid_progress copy "$M" 20 | tail -1; sleep 3
ring_since $M > $ROW_DIR/ring-kill-before.txt
adb shell am force-stop app.tileshell; sleep 1
echo "after the kill: $(q "find /sdcard/QA-Files -name '.*.part'")"
ensure_start; M2=$(ring_mark); files_open; sleep 3
ring_since $M2 | grep -E "sweep" | tee $ROW_DIR/ring-sweep.txt
ring_since 0 | grep "sweep" | tail -2
echo "after the sweep: [$(q "find /sdcard/ -name '.*.part' 2>/dev/null")]"; q "md5sum /sdcard/QA-Files/big.bin"; grep " big.bin" $ROW_DIR/fixtures.md5; q "ls -a /sdcard/QA-Files/sub | xargs"
D 20-relaunched; echo "relaunch: pane=$(H 20-relaunched files_pane) crash=$(crash)"

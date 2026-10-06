#!/usr/bin/env bash
# E4: cancel mid-copy from the notification's action; then force-stop mid-copy and the sweep at the next start.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg f-big
bin_it $QF/sub big.bin
adb logcat -c; ensure_start; files_at $QF; M=$(ring_mark)
pick_op copy big.bin sub
mid_progress copy "$M" 20 | tail -1
adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell cmd statusbar expand-notifications; sleep 2; gdump $ROW_DIR/10-shade0.xml
xy="$(python3 "$(dirname "$0")/shade_xy.py" $ROW_DIR/10-shade0.xml expand)"; echo "expander at: $xy"; [ -n "$xy" ] && adb shell input tap $xy; sleep 1.5; gdump $ROW_DIR/10-shade.xml; S 10-shade
xy="$(python3 "$(dirname "$0")/shade_xy.py" $ROW_DIR/10-shade.xml cancel)"; echo "Cancel at: $xy"; [ -n "$xy" ] && adb shell input tap $xy
sleep 2; adb shell cmd statusbar collapse; sleep 1; files_open
ring_since $M | grep -E "copy 1 files|cancel" ; q "ls -a /sdcard/QA-Files/sub | xargs; find /sdcard/QA-Files -name '.*.part'"
D 11-after-cancel; echo "after cancel: progress=$(H 11-after-cancel files_progress) error=[$(X 11-after-cancel files_error)] crumb1=[$(X 11-after-cancel files_crumb:1)] top=$(top_activity)"
ring_since $M > $ROW_DIR/ring-cancel.txt

#!/usr/bin/env bash
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg dbg
pubvol_up; sleep 2
adb shell "cp /sdcard/QA-Files/img-2.png $PUBVOL_PATH/pv.png"; sleep 2
L=$(echo $PUBVOL_UUID | tr A-Z a-z)
echo "--- external:"; q "content query --uri content://media/external/images/media --projection _id:_data:volume_name --where \"_data='$PUBVOL_PATH/pv.png'\""
echo "--- $L:"; q "content query --uri content://media/$L/images/media --projection _id:_data:volume_name --where \"_data='$PUBVOL_PATH/pv.png'\""
echo "--- $L all:"; q "content query --uri content://media/$L/images/media --projection _id:_data:volume_name"
echo "--- volumes:"; q "content query --uri content://media/external/file --projection volume_name" | sort | uniq -c
M=$(ring_mark); tap_row $PUBVOL_PATH pv.png 3; ring_since $M | grep -E "via provider|viewer open|media query|TEMPDBG"
adb logcat -d | grep -iE "MediaProvider|volume" | grep -iE "$L|not found|Exception" | tail -8
adb shell input keyevent KEYCODE_BACK; sleep 1
hold_remove() { files_open --es page recent; D r; hold r files_recent_row:pv.png; D r2; T r2 files_hold:remove_recent 1.5; }
hold_remove
adb shell input keyevent KEYCODE_HOME; sleep 1; pubvol_down

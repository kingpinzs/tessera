#!/usr/bin/env bash
# E14 "Volume": a file opened on the row's own public volume shows a row; unmount → hidden; mount → back.
. "$(dirname "$0")/t4.sh"; take_device_lock; KEEP_LEG=1 leg b-e14
adb logcat -c
recent_page() { files_open --es page recent; D "$1"; S "$1"; }
ensure_start
pubvol_up
echo "volume: $PUBVOL_UUID $PUBVOL_ID"; sleep 2
adb shell "cp /sdcard/QA-Files/img-2.png $PUBVOL_PATH/pv.png"; sleep 1; q "ls -la $PUBVOL_PATH/pv.png"
record "MediaStore rows for pv.png on the public volume before the tap" "$(q "content query --uri content://media/external/images/media --projection _id:_data:volume_name" | grep -c 'pv.png') $(q "content query --uri content://media/external/images/media --projection _id:_data:volume_name" | grep 'pv.png' | xargs)"
BEFORE="$(recent_rows 26-after-c6)"
M=$(ring_mark); tap_row $PUBVOL_PATH pv.png 3; S 30-pv-viewer
assert_eq "public volume: the viewer" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
SL="$(ring_since $M)"; echo "$SL" | grep -E "\[(files|photosapp)\]" | grep -E "open|recent|viewer" | tee "$ROW_DIR/30-ring.txt"
assert_contains "public volume: [files] recent add" "[files] recent add $PUBVOL_PATH/pv.png" "$SL"
C="$(px "$ROW_DIR/30-pv-viewer.png" 540 1170)"; assert_eq "public volume: the picture is shown (centre $C = 40,90,220 ± 4)" "yes" "$(near "$C" 40,90,220 4)"
record "public volume: opened by" "$(echo "$SL" | grep -c 'via provider') 'via provider' line(s); viewer line: $(echo "$SL" | grep -o 'viewer open [a-z0-9]*')"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
recent_page 31-pv-row
assert_eq "public volume: its row is on top" "pv.png r1b.png" "$(recent_rows 31-pv-row)"
echo "store: $(recent_json)"
M=$(ring_mark); adb shell sm unmount "$PUBVOL_ID"; sleep 3; D 32-unmounted; S 32-unmounted
echo "$(ring_since $M | grep -F '[files]')"
assert_eq "unmounted: the row is hidden (the page re-read by itself)" "r1b.png" "$(recent_rows 32-unmounted)"
assert_contains "unmounted: the entry is kept in the store" "pv.png" "$(recent_json)"
M=$(ring_mark); adb shell sm mount "$PUBVOL_ID"; sleep 4; D 33-mounted; S 33-mounted
echo "$(ring_since $M | grep -F '[files]')"
assert_eq "mounted again: the row is back" "pv.png r1b.png" "$(recent_rows 33-mounted)"
# The entry leaves with its file (the volume is the row's own and is removed below).
hold 33-mounted files_recent_row:pv.png; D 34; T 34 files_hold:remove_recent 1.5
adb shell input keyevent KEYCODE_HOME; sleep 1
pubvol_down
echo "store at the end: $(recent_json)"
leg_end

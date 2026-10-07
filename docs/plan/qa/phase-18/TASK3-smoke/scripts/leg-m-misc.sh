#!/usr/bin/env bash
# Icons view selection and hold, the overflow's Select all / Clear, a hold on Recent (the next builder's: nothing), the
# 70,000-entry zip's time and last row, qa-big.zip's extract cancelled from the notification.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg m-misc
adb logcat -c; ensure_start; files_at $QF; M=$(ring_mark)
D 01; T 01 files_bar:view 1.5; D 02-icons; hold 02-icons files_row:a.txt; D 03-icons-hold; S 03-icons-hold
echo "icons hold: $(epx $(B 03-icons-hold files_hold)) items=$(ids 03-icons-hold | tr ' ' '\n' | grep -c 'files_hold:')"; adb shell input keyevent KEYCODE_BACK; sleep 0.8
D 04; T 04 files_bar:more 1; D 05-more; T 05-more files_more:select_all 1.2; D 06-all; S 06-all; echo "select all (icons): [$(X 06-all files_sort)] checks=$(grep -c 'files_check:' $ROW_DIR/06-all.xml) checked=$(grep -o '<node[^>]*files_check:[^>]*>' $ROW_DIR/06-all.xml | grep -c 'checked="true"')"
T 06-all files_bar:more 1; D 07; echo "overflow in selection: $(ids 07 | tr ' ' '\n' | grep -E 'files_more:|files_zip' | xargs)"; T 07 files_more:clear 1; D 08; echo "after clear: [$(X 08 files_sort)]"
adb shell input keyevent KEYCODE_BACK; sleep 1; D 09; echo "back: [$(X 09 files_sort)] top=$(top_activity)"; T 09 files_bar:view 1.2
# Recent: a hold opens nothing here
files_open --es page recent; D 10-recent; echo "recent rows: $(grep -c files_recent_row $ROW_DIR/10-recent.xml)"
# zip64
files_at $ZD; scroll_to_node "$ROW_DIR/z0.xml" files_row:qa-zip64.zip >/dev/null; M2=$(ring_mark); tap_node $ROW_DIR/z0.xml files_row:qa-zip64.zip
for i in $(seq 1 60); do L="$(ring_since $M2 | grep 'zip open .*: 70000 entries')"; [ -n "$L" ] && break; sleep 0.1; done
python3 -c "import re,sys; w=int(re.search(r'wall=(\d+)',sys.argv[1]).group(1)); print('zip open line %d ms after the MARK before the tap' % (w-int(sys.argv[2])))" "$L" "$M2"
sleep 2; D 11; T 11 files_sort 1; D 12; T 12 files_sort_item:date 2; D 13
for i in $(seq 1 6); do adb shell input swipe 540 1800 540 200 40; done; sleep 2; D 14-zip64; echo "rows after flings: $(rows 14-zip64 | cut -c1-120)"
echo "anr=$(adb logcat -d | grep -c 'ANR in app.tileshell')"
# qa-big.zip: extract, cancel from the notification
zopen qa-big.zip; D 20; M3=$(ring_mark); T 20 files_extract 0.3; mid_progress "zip extract" "$M3" 20 | tail -1
G 21-extracting; echo "box text=[$(X 21-extracting files_progress_text)]"; q "ls -a /sdcard/QA-Files/zips | grep extract"
adb shell input keyevent KEYCODE_HOME; sleep 1; adb shell cmd statusbar expand-notifications; sleep 2; gdump $ROW_DIR/22-shade0.xml
xy="$(python3 "$(dirname "$0")/shade_xy.py" $ROW_DIR/22-shade0.xml expand)"; [ -n "$xy" ] && adb shell input tap $xy; sleep 1.5; gdump $ROW_DIR/22-shade.xml
xy="$(python3 "$(dirname "$0")/shade_xy.py" $ROW_DIR/22-shade.xml cancel)"; echo "Cancel at: $xy"; [ -n "$xy" ] && adb shell input tap $xy; sleep 2; adb shell cmd statusbar collapse; sleep 1
ring_since $M3 | grep "zip extract" | grep -v progress; echo "left: [$(q "ls -a /sdcard/QA-Files/zips | grep -E 'extract|^qa-big$' | xargs")]"
files_open; sleep 1; D 23; echo "back in Files: progress=$(H 23 files_progress) error=[$(X 23 files_error)]"
ring_since $M > $ROW_DIR/ring.txt; echo crash=$(crash)

#!/usr/bin/env bash
# E4's paced big.bin copy: the progress box, the notification, Home mid-copy, completion and landing.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg f-big
adb logcat -c; ensure_start; files_at $QF; S 01-before; M=$(ring_mark)
pick_op copy big.bin sub
mid_progress copy "$M" 20 > $ROW_DIR/mid1.txt; cat $ROW_DIR/mid1.txt | tail -1
ring_since $M | grep -E "qa pace" | tee $ROW_DIR/pace.txt
G 02-progress; S 02-progress
echo "progress=$(B 02-progress files_progress) -> $(epx $(B 02-progress files_progress)) text=[$(X 02-progress files_progress_text)] textbox=$(epx $(B 02-progress files_progress_text))"
echo "nodes in app during progress: $(ids 02-progress | tr " " "\n" | grep -E "progress|cancel|percent|wash" | xargs)"; grep -c "%" $ROW_DIR/02-progress.xml
echo "pixel (540,1500) before=$(px $ROW_DIR/01-before.png 540 1500) during=$(px $ROW_DIR/02-progress.png 540 1500); status bar (300,40) before=$(px $ROW_DIR/01-before.png 300 40) during=$(px $ROW_DIR/02-progress.png 300 40); nav (300,2300) before=$(px $ROW_DIR/01-before.png 300 2300) during=$(px $ROW_DIR/02-progress.png 300 2300); box (540,150)=$(px $ROW_DIR/02-progress.png 540 150)"
adb shell dumpsys notification --noredact > $ROW_DIR/03-notification.txt; grep -n "app.tileshell" $ROW_DIR/03-notification.txt | head -3; grep -n "Copying files\|actions=\|\"Cancel\"\|Cancel" $ROW_DIR/03-notification.txt | head -8
adb shell input keyevent KEYCODE_HOME; sleep 1.5; M2=$(ring_mark)
adb shell dumpsys activity services app.tileshell > $ROW_DIR/04-services.txt; grep -n "FileOpsService\|isForeground\|foregroundServiceType\|types=" $ROW_DIR/04-services.txt | head -8
mid_progress copy "$M2" 10 | tail -1; echo "top while home=$(top_activity)"
for i in $(seq 1 60); do ring_since $M | grep -q "copy 1 files .* done" && break; sleep 1; done
ring_since $M | grep "copy 1 files" ; q "md5sum /sdcard/QA-Files/big.bin /sdcard/QA-Files/sub/big.bin; ls -a /sdcard/QA-Files/sub"
files_open; sleep 1; D 05-reopened; S 05-reopened; echo "reopened: crumbs [$(X 05-reopened files_crumb:1)] [$(X 05-reopened files_crumb:2)] row=$(H 05-reopened files_row:big.bin) progress=$(H 05-reopened files_progress)"
ring_since $M > $ROW_DIR/ring-copy.txt; grep -c "copy progress" $ROW_DIR/ring-copy.txt; echo crash=$(crash)

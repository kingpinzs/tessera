#!/usr/bin/env bash
# The final build's regression pass over tasks 3 / 8 / 9 (the numbers E11 names, and one of each write).
. "$(dirname "$0")/t3.sh"; take_device_lock; leg z-final
echo "apk $(md5sum "$APK" | cut -d' ' -f1) installed $(installed_apk_id)"
adb logcat -c; ensure_start; files_at $QF; M=$(ring_mark)
D 01; T 01 files_bar:select 1.2; D 02; echo "0: [$(X 02 files_sort)] dim: $(grep -o '<node[^>]*files_sel:[^>]*>' $ROW_DIR/02.xml | grep -c 'enabled="false"')/4"; S 02
echo "dim glyph pixel delete (552,2124)=$(px $ROW_DIR/02.png 666 2124) "
T 02 files_row:a.txt 0.6; D 03; echo "1: [$(X 03 files_sort)]"; T 03 files_row:b.bin 0.6; D 04; S 04; echo "2: [$(X 04 files_sort)]"
echo "check $(epx $(B 04 files_check:a.txt)) icon $(epx $(B 04 files_icon:a.txt)) name $(epx $(B 04 files_row:a.txt))"
set -- $(B 04 files_icon:a.txt); echo "row fill left (6,$2)=$(px $ROW_DIR/04.png 6 $(( ($2+$4)/2 ))) right (1070)=$(px $ROW_DIR/04.png 1070 $(( ($2+$4)/2 ))) top edge=$(px $ROW_DIR/04.png 700 $(( $2 - 34 ))) bottom edge=$(px $ROW_DIR/04.png 700 $(( $4 + 34 )))"
T 04 files_row:sub 0.6; D 05; echo "with sub: share enabled=$(grep -o '<node[^>]*files_sel:share[^>]*>' $ROW_DIR/05.xml | grep -o 'enabled="[a-z]*"')"; T 05 files_row:sub 0.6
D 06; T 06 files_bar:view 1.5; D 07-icons; S 07-icons; echo "icons check a.txt $(epx $(B 07-icons files_check:a.txt))"; T 07-icons files_bar:view 1.5
adb shell input keyevent KEYCODE_BACK; sleep 1; D 08; echo "back: [$(X 08 files_sort)]"
hold 08 files_row:b.bin; D 09; echo "hold: $(epx $(B 09 files_hold)) $(ids 09 | tr ' ' '\n' | grep 'files_hold:' | xargs)"; T 09 files_hold:rename 1; D 10; echo "dialog $(epx $(B 10 files_dialog)) fill=$(px <(adb exec-out screencap -p) 540 100 2>/dev/null)"; S 10; echo "dialog fill (540,100)=$(px $ROW_DIR/10.png 540 100)"
adb shell input text bb.bin; sleep 0.4; D 11; T 11 files_dialog:ok 1.5; q "ls /sdcard/QA-Files | grep bin"
files_at $QF; pick_op move bb.bin sub; sleep 2; D 12; echo "moved: crumb2=[$(X 12 files_crumb:2)] row=$(H 12 files_row:bb.bin)"; q "ls /sdcard/QA-Files/sub | xargs"
hold 12 files_row:bb.bin; D 13; T 13 files_hold:delete 1; D 14; T 14 files_dialog:ok 1.5; binsel bb.bin; T b2 files_bin_restore 1.5; q "md5sum /sdcard/QA-Files/sub/bb.bin"
zopen qa.zip; D 15; echo "zip: root=$(H 15 files_zip_root) extract=$(H 15 files_extract)"; T 15 files_row:one.txt 1; D 16; echo "[$(X 16 files_error)]"
ring_since $M > $ROW_DIR/ring.txt; grep -E "\[motion\] files_(select|deselect|hold)|rename|move 1|bin (delete|restore)" $ROW_DIR/ring.txt; echo "crash=$(crash) anr=$(adb logcat -d | grep -c 'ANR in app.tileshell')"

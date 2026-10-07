#!/usr/bin/env bash
# E13: the nested zip and its copy, the 70,000-entry zip, create a zip from a selection.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg k-zip
adb logcat -c; ensure_start; M=$(ring_mark)
( cd "$T3/../gen/zips" 2>/dev/null && unzip -p qa.zip one.txt | md5sum; unzip -p qa.zip dir/two.bin | md5sum ) 2>&1 | xargs echo "host md5 of one.txt, dir/two.bin:"
zopen qa-nested.zip; D 20-nested; echo "nested outer rows:"; rows 20-nested
M1=$(ring_mark); T 20-nested files_row:qa.zip 2; D 21-inner; echo "inner: zip root=$(H 21-inner files_zip_root) crumbs=[$(X 21-inner files_crumb:2)][$(X 21-inner files_crumb:3)][$(X 21-inner files_crumb:4)] extract enabled: $(grep -o '<node[^>]*files_extract[^>]*>' $ROW_DIR/21-inner.xml | grep -o 'enabled="[a-z]*"')"; rows 21-inner
echo "tmp while open: $(q 'ls -a /sdcard/.Tessera/tmp | xargs')"; ring_since $M1 | grep zip
adb shell input keyevent KEYCODE_BACK; sleep 1.5; adb shell input keyevent KEYCODE_BACK; sleep 1.5; D 22-left; echo "left: crumb last=[$(X 22-left files_crumb:2)] zip root=$(H 22-left files_zip_root)"; echo "tmp after leaving: [$(q 'ls /sdcard/.Tessera/tmp | xargs')]"
# 70,000 entries
M2=$(ring_mark); T0=$(date +%s.%N); zopen qa-zip64.zip 0.2
for i in $(seq 1 50); do ring_since $M2 | grep -q "zip open .*: 70000 entries" && break; sleep 0.2; done; T1=$(date +%s.%N)
ring_since $M2 | grep "zip open"; python3 -c "print('tap -> line: %.1f s (includes the dump-and-tap)' % ($T1-$T0))"
sleep 2; D 23-zip64; S 23-zip64; echo "first rows: $(rows 23-zip64 | cut -c1-200)"
for i in 1 2 3; do adb shell input swipe 540 1800 540 300 60; done; sleep 2; D 24-zip64-scrolled; echo "after flings: $(rows 24-zip64-scrolled | cut -c1-160)"; echo "anr: $(adb logcat -d | grep -c 'ANR in app.tileshell')"
# create: a.txt + b.bin -> Archive.zip ; a.txt alone -> a.txt.zip
files_at $QF; D c1; T c1 files_bar:select 1; D c2; T c2 files_row:a.txt 0.6; D c3; T c3 files_row:b.bin 0.6; D c4; T c4 files_bar:more 1; D 25-sel-more; S 25-sel-more
echo "selection overflow: $(ids 25-sel-more | tr ' ' '\n' | grep -E 'files_more|files_zip' | xargs)"; M3=$(ring_mark); T 25-sel-more files_zip_create 0.3; wait_op $M3
ring_since $M3 | grep "zip create" | grep -v progress
files_at $QF; D c1; T c1 files_bar:select 1; D c2; T c2 files_row:a.txt 0.6; D c4; T c4 files_bar:more 1; D c5; M4=$(ring_mark); T c5 files_zip_create 0.3; wait_op $M4
ring_since $M4 | grep "zip create" | grep -v progress; q "ls /sdcard/QA-Files | grep zip"
adb pull /sdcard/QA-Files/Archive.zip $ROW_DIR/Archive.zip >/dev/null; unzip -l $ROW_DIR/Archive.zip; unzip -t $ROW_DIR/Archive.zip | tail -1
ring_since $M > $ROW_DIR/ring2.txt; echo crash=$(crash)

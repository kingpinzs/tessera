#!/usr/bin/env bash
# E13: a zip as a folder, "Extract first", extract and md5s, extract again -> keep both, the bad / corrupt / encrypted / bomb zips.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg k-zip
adb logcat -c; ensure_start; M=$(ring_mark)
zopen qa.zip; D 01-qa; S 01-qa
echo "zip root=$(H 01-qa files_zip_root) crumbs=[$(X 01-qa files_crumb:1)][$(X 01-qa files_crumb:2)][$(X 01-qa files_crumb:3)] bar: $(ids 01-qa | tr ' ' '\n' | grep -E 'files_bar|files_extract' | xargs)"; rows 01-qa
T 01-qa files_row:one.txt 1; D 02-first; echo "tap one.txt: files_error=[$(X 02-first files_error)] top=$(top_activity)"
T 02-first files_row:dir 1.5; D 03-dir; echo "inside dir:"; rows 03-dir; T 03-dir files_up 1.5; D 04-up; echo "up -> zip root rows: $(grep -c 'files_row:' $ROW_DIR/04-up.xml)"
M1=$(ring_mark); T 04-up files_extract 0.3; wait_op $M1; D 05-extracted; echo "landed: last crumb [$(X 05-extracted files_crumb:2)] qa row=$(H 05-extracted files_row:qa)"
q "cd /sdcard/QA-Files/zips/qa && find . -type f | sort | xargs md5sum"; cat "$T3/f-big/zips.tsv" 2>/dev/null | head -3; ls "$T3/../gen" | head
# extract again -> the conflict, keep both
zopen qa.zip; D 06; M2=$(ring_mark); T 06 files_extract 1.5; D 07-conflict; echo "extract again: title=[$(X 07-conflict files_dialog_title)] buttons=$(ids 07-conflict | tr ' ' '\n' | grep 'files_dialog:' | xargs)"
T 07-conflict files_dialog:keep_both 0.3; wait_op $M2; q "ls /sdcard/QA-Files/zips | grep -v zip$ | xargs"
# qa-bad.zip
zopen qa-bad.zip; D 08-bad; echo "qa-bad rows:"; rows 08-bad; M3=$(ring_mark); T 08-bad files_extract 0.3; wait_op $M3
ring_since $M3 | grep -E "zip" ; q "ls /sdcard/QA-Files/zips/qa-bad; ls /sdcard/evil.txt /sdcard/QA-Files/evil.txt /sdcard/abs.txt /sdcard/QA-Files/zips/evil.txt 2>&1"
# corrupt, encrypted
M4=$(ring_mark); zopen qa-corrupt.zip; D 09-corrupt; echo "corrupt: files_error=[$(X 09-corrupt files_error)] zip root=$(H 09-corrupt files_zip_root)"; ring_since $M4 | grep "zip"
M5=$(ring_mark); zopen qa-enc.zip; D 10-enc; echo "encrypted: files_error=[$(X 10-enc files_error)] zip root=$(H 10-enc files_zip_root)"; ring_since $M5 | grep "zip"; q "ls /sdcard/QA-Files/zips | grep -c enc"
# bomb
zopen qa-bomb.zip; D 11-bomb; M6=$(ring_mark); T 11-bomb files_extract 0.3; wait_op $M6 90; D 12-bomb-after; echo "bomb: files_error=[$(X 12-bomb-after files_error)]"; ring_since $M6 | grep -E "zip" | grep -v progress; q "ls -a /sdcard/QA-Files/zips | grep -i bomb | xargs"
ring_since $M > $ROW_DIR/ring1.txt; echo crash=$(crash)

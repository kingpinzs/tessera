#!/usr/bin/env bash
# A delete that cannot reach the bin (the bin path is a FILE): files_error, the file stays.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg i-bin
M=$(ring_mark)
adb shell "mv /sdcard/.Tessera /sdcard/QA-Files/tessera-dir2 && : > /sdcard/.Tessera"; q "ls -la /sdcard/.Tessera"
before=$(q "md5sum /sdcard/QA-Files/img-2.png")
files_at $QF; scroll_to_node "$ROW_DIR/d0.xml" files_row:img-2.png; hold d0 files_row:img-2.png; D d1; T d1 files_hold:delete 1; D d2; T d2 files_dialog:ok 1.2; D 09-failed; S 09-failed
echo "files_error=[$(X 09-failed files_error)] row still=$(H 09-failed files_row:img-2.png)"; echo "before $before"; echo "after  $(q "md5sum /sdcard/QA-Files/img-2.png")"
adb shell "mv /sdcard/.Tessera /sdcard/QA-Files/tessera-file2 && mv /sdcard/QA-Files/tessera-dir2 /sdcard/.Tessera"; q "ls -a /sdcard/.Tessera"
ring $M > $ROW_DIR/ring4.txt; grep "bin " $ROW_DIR/ring4.txt; echo crash=$(crash)

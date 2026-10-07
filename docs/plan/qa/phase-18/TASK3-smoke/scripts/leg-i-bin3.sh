#!/usr/bin/env bash
# Bin legs: Delete permanently, Empty, and a delete that cannot reach the bin (the bin path is a FILE).
. "$(dirname "$0")/t3.sh"; take_device_lock; leg i-bin
M=$(ring_mark)
binsel 00k-skip.txt; T b2 files_bin_delete 1; D 03-purge; S 03-purge
echo "purge dialog: title=[$(X 03-purge files_dialog_title)] body=[$(X 03-purge files_dialog_body)] buttons=$(ids 03-purge | tr " " "\n" | grep "files_dialog:" | xargs)"
T 03-purge files_dialog:ok 1.5; D 04-purged; echo "row gone=$(H 04-purged files_bin_row:00k-skip.txt)"; q "ls -a /sdcard/.Tessera/bin | xargs; cat /sdcard/.Tessera/bin/.index.json"; echo
bin_it $QF img-1.png
files_open --es page bin; D 05; T 05 files_bar:more 1; D 06-more; S 06-more; echo "bin overflow: $(ids 06-more | tr " " "\n" | grep -E "files_more:|files_bin_empty" | xargs)"
T 06-more files_bin_empty 1; D 07-empty; S 07-empty; echo "empty dialog: title=[$(X 07-empty files_dialog_title)] body=[$(X 07-empty files_dialog_body)]"
T 07-empty files_dialog:ok 1.5; D 08-emptied; echo "rows left: $(grep -c files_bin_row $ROW_DIR/08-emptied.xml)"; q "ls -a /sdcard/.Tessera/bin | xargs; cat /sdcard/.Tessera/bin/.index.json"; echo
# the bin path is a file: the delete fails and the file stays
adb shell "mv /sdcard/.Tessera /sdcard/QA-Files/tessera-dir && : > /sdcard/.Tessera"; q "ls -la /sdcard/.Tessera"
before=$(q "md5sum /sdcard/QA-Files/img-2.png")
files_at $QF; D d0; hold d0 files_row:img-2.png; D d1; T d1 files_hold:delete 1; D d2; T d2 files_dialog:ok 1.5; D 09-failed; S 09-failed
echo "files_error=[$(X 09-failed files_error)] row still=$(H 09-failed files_row:img-2.png)"; echo "before $before"; echo "after  $(q "md5sum /sdcard/QA-Files/img-2.png")"
adb shell "mv /sdcard/.Tessera /sdcard/QA-Files/tessera-file && mv /sdcard/QA-Files/tessera-dir /sdcard/.Tessera"; q "ls -a /sdcard/.Tessera"
ring $M > $ROW_DIR/ring3.txt; grep "bin " $ROW_DIR/ring3.txt; echo crash=$(crash)

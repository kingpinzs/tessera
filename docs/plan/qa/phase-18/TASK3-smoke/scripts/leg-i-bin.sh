#!/usr/bin/env bash
# Bin legs through the app only: restore into a folder that is gone, and the three answers of a restore conflict.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg i-bin
M=$(ring_mark)
bin_it $QF/sub a.txt; bin_it $QF sub; q "ls -d /sdcard/QA-Files/sub"
binsel a.txt; T b2 files_bin_restore 1.5; q "ls -la /sdcard/QA-Files/sub; md5sum /sdcard/QA-Files/sub/a.txt; ls /sdcard/.Tessera/bin"
for ans in keep_both skip replace; do
  k=k-$ans.txt
  adb shell "printf original > /sdcard/QA-Files/$k"; bin_it $QF $k; adb shell "printf newer-one > /sdcard/QA-Files/$k"
  binsel $k; T b2 files_bin_restore 1.5; D 02-conflict-$ans; [ $ans = keep_both ] && S 02-conflict
  echo "restore conflict ($ans): title=[$(X 02-conflict-$ans files_dialog_title)] buttons=$(ids 02-conflict-$ans | tr " " "\n" | grep "files_dialog:" | xargs)"
  T 02-conflict-$ans files_dialog:$ans 1.5; q "cd /sdcard/QA-Files && for f in k-$ans*; do echo \"\$f=\$(cat \"\$f\")\"; done; ls /sdcard/.Tessera/bin | xargs"
done
ring $M > $ROW_DIR/ring1.txt; grep "bin " $ROW_DIR/ring1.txt; echo crash=$(crash)

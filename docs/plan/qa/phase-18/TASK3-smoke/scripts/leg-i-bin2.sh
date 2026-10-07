#!/usr/bin/env bash
# Bin legs through the app only: the three answers of a restore conflict.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg i-bin
M=$(ring_mark)
for ans in keep_both skip replace; do
  k=00k-$ans.txt
  adb shell "printf original > /sdcard/QA-Files/$k"; bin_it $QF $k; adb shell "printf newer-one > /sdcard/QA-Files/$k"
  binsel $k; T b2 files_bin_restore 1.5; D 02-conflict-$ans; [ $ans = keep_both ] && S 02-conflict
  echo "restore conflict ($ans): title=[$(X 02-conflict-$ans files_dialog_title)] buttons=$(ids 02-conflict-$ans | tr " " "\n" | grep "files_dialog:" | xargs)"
  T 02-conflict-$ans files_dialog:$ans 1.5; q "cd /sdcard/QA-Files && for f in 00k-$ans*; do echo \"\$f=\$(cat \"\$f\")\"; done; ls /sdcard/.Tessera/bin | xargs"
done
ring $M > $ROW_DIR/ring2.txt; grep "bin " $ROW_DIR/ring2.txt; echo crash=$(crash)

#!/usr/bin/env bash
# The cross-volume move ("Moving files…"), and a delete on the public volume landing in THAT volume's bin, restored there.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg j-pubvol
cp "$T3/f-big/fixtures.md5" "$ROW_DIR/fixtures.md5"
adb logcat -c; ensure_start
pubvol_up
echo "volume: $PUBVOL_UUID"; sleep 2
files_at $QF; S 01-before; M=$(ring_mark)
D p1; T p1 files_bar:select 1; scroll_to_node "$ROW_DIR/p2.xml" files_row:big.bin >/dev/null; T p2 files_row:big.bin 0.6; D p3; T p3 files_sel:move 1.5
D p4; T p4 files_menu 1; D p5; echo "pane rows in the picker: $(ids p5 | tr ' ' '\n' | grep '^files_pane:' | xargs)"; T p5 "files_pane:$PUBVOL_UUID" 1.5
D p6; echo "picker on the volume: crumb0=[$(X p6 files_crumb:0)] title=[$(X p6 files_pick_title)]"; T p6 files_pick_ok 0.2
mid_progress move "$M" 20 | tail -1
G 02-moving; S 02-moving
echo "progress=$(epx $(B 02-moving files_progress)) text=[$(X 02-moving files_progress_text)]; cancel/percent nodes: $(ids 02-moving | tr ' ' '\n' | grep -ciE 'cancel|percent')"
echo "pixel (540,1500) before=$(px $ROW_DIR/01-before.png 540 1500) during=$(px $ROW_DIR/02-moving.png 540 1500)"
for i in $(seq 1 60); do ring_since $M | grep -q "move 1 files .* \(done\|failed\|cancelled\)" && break; sleep 1; done
ring_since $M | grep -E "move 1 files|qa pace"; sleep 2
D 03-landed; S 03-landed; echo "landed: crumb0=[$(X 03-landed files_crumb:0)] row=$(H 03-landed files_row:big.bin) progress=$(H 03-landed files_progress)"
q "ls /sdcard/QA-Files/big.bin; md5sum /storage/$PUBVOL_UUID/big.bin"; grep " big.bin" $ROW_DIR/fixtures.md5
# a delete on the public volume
M3=$(ring_mark); hold 03-landed files_row:big.bin; D d1; T d1 files_hold:delete 1; D d2; T d2 files_dialog:ok 1.5
ring_since $M3 | grep "bin "; q "ls -a /storage/$PUBVOL_UUID/.Tessera/bin | xargs; ls -a /sdcard/.Tessera/bin 2>&1 | xargs"
files_open --es page bin; D 04-bin; echo "bin row: detail=[$(X 04-bin files_detail:big.bin)]"
T 04-bin files_bar:select 1; D b1; T b1 files_bin_row:big.bin 0.6; D b2; T b2 files_bin_restore 2
ring_since $M3 | grep "bin restore"; q "md5sum /storage/$PUBVOL_UUID/big.bin; ls -a /storage/$PUBVOL_UUID/.Tessera/bin | xargs"
# bin the file again, then pull the volume: its bin rows vanish
bin_it /storage/$PUBVOL_UUID big.bin; files_open --es page bin; D 05-bin; echo "before unmount: row=$(H 05-bin files_bin_row:big.bin)"
ring_since $M > $ROW_DIR/ring.txt
pubvol_down; sleep 2; D 06-bin; echo "after the volume is gone: row=$(H 06-bin files_bin_row:big.bin) bin page=$(H 06-bin files_bin_note)"
echo "crash=$(crash) volumes: $(q 'sm list-volumes public' | xargs) disks: $(q 'sm list-disks' | xargs)"

#!/usr/bin/env bash
# A delete on the public volume lands in THAT volume's bin, restores there; its rows vanish when the volume is pulled.
. "$(dirname "$0")/t3.sh"; take_device_lock; leg j-pubvol
adb logcat -c; ensure_start
# empty the bins first (through the app), so the row names below are this leg's own
files_open --es page bin; D e0; T e0 files_bar:more 1; D e1; T e1 files_bin_empty 1; D e2; T e2 files_dialog:ok 1.5; q "ls -a /sdcard/.Tessera/bin | xargs"
pubvol_up
sleep 2; V=/storage/$PUBVOL_UUID
files_at $QF; M=$(ring_mark)
D p1; T p1 files_bar:select 1; D p2; T p2 files_row:b.bin 0.6; D p3; T p3 files_sel:copy 1.5
D p4; T p4 files_menu 1; D p5; T p5 "files_pane:$PUBVOL_UUID" 1.5; D p6; T p6 files_pick_ok 3
q "md5sum $V/b.bin"; grep " b.bin" "$T3/f-big/fixtures.md5"
M3=$(ring_mark); bin_it $V b.bin
ring_since $M3 | grep "bin "; echo "volume bin: $(q "ls -a $V/.Tessera/bin | xargs")"; echo "primary bin: $(q "ls -a /sdcard/.Tessera/bin | xargs")"; q "cat $V/.Tessera/bin/.index.json"; echo
files_open --es page bin; D 04-bin; S 04-bin; echo "bin row: detail=[$(X 04-bin files_detail:b.bin)]"
T 04-bin files_bar:select 1; D b1; T b1 files_bin_row:b.bin 0.6; D b2; T b2 files_bin_restore 2
ring_since $M3 | grep "bin restore"; q "md5sum $V/b.bin; ls -a $V/.Tessera/bin | xargs"
bin_it $V b.bin; files_open --es page bin; D 05-bin; echo "before unmount: row=$(H 05-bin files_bin_row:b.bin)"
ring_since $M > $ROW_DIR/ring2.txt
pubvol_down; sleep 2; D 06-bin; echo "after the volume is gone: row=$(H 06-bin files_bin_row:b.bin) bin page=$(H 06-bin files_bin_note)"
echo "crash=$(crash) volumes: [$(q 'sm list-volumes public' | xargs)] disks: [$(q 'sm list-disks' | xargs)]"

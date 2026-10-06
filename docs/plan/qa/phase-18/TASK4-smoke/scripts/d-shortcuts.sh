#!/usr/bin/env bash
# E16 / E2's bursts: the Files tile's 3 satellites and their pages, dumpsys shortcut's manifest ranks, the 4th with a
# public volume up, the Music tile's burst unchanged, the process-start reconcile.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg d-shortcuts
adb logcat -c
MUSIC_TILE="tile:slot:MUSIC"
baseline_start
burst() { # tile-id name -> <name>-rest.xml, <name>.xml / .png with the burst open; BMARK = the MARK before the hold
  ensure_start >/dev/null
  scroll_to_node "$ROW_DIR/$2-rest.xml" "$1" >/dev/null || { dump_ui "$ROW_DIR/$2-rest.xml"; }
  local b; b="$(bounds "$ROW_DIR/$2-rest.xml" "$1")"; set -- $b "$2"
  BMARK=$(ring_mark)
  adb shell input swipe $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) 1000; sleep 1
  dump_ui "$ROW_DIR/$5.xml"; screencap "$ROW_DIR/$5.png"
}
labels() { for i in 0 1 2 3 4; do [ "$(H "$1" quick_sat_label:$i)" = yes ] && printf '%s|' "$(X "$1" quick_sat_label:$i)"; done; echo; }
sats() { grep -o 'resource-id="quick_sat:[0-9]*"' "$ROW_DIR/$1.xml" | wc -l; }
qline() { ring_since "$1" | grep -o '\[quick\] shortcuts for .*' | tail -1; }
shortcuts() { adb shell dumpsys shortcut | tr -d '\r' | python3 -c '
import re, sys
t = sys.stdin.read()
m = re.search(r"Package: app\.tileshell\b.*?(?=\n\s*Package: |\Z)", t, re.S)
for b in re.split(r"\n\s*ShortcutInfo \{", m.group(0) if m else "")[1:]:
    i = re.search(r"id=([^,]+),", b); r = re.search(r"rank=(\d+)", b); f = re.search(r"flags=0x[0-9a-f]+ \[([^\]]*)\]", b); a = re.search(r"activity=ComponentInfo\{([^}]*)\}", b)
    print(i.group(1), "rank=" + (r.group(1) if r else "?"), "flags=[" + (f.group(1) if f else "?") + "]", a.group(1) if a else "")
' | grep files_; }

# ---- no volume: 3 satellites
assert_eq "precondition: no public volume" "" "$(q 'sm list-volumes public' | xargs)"
shortcuts | tee "$ROW_DIR/00-shortcuts.txt"; adb shell dumpsys shortcut > "$ROW_DIR/00-dumpsys-shortcut.txt"
for n in "files_device rank=0" "files_recent rank=1" "files_bin rank=2"; do assert_contains "dumpsys shortcut: $n" "$n flags=[" "$(cat "$ROW_DIR/00-shortcuts.txt")"; done
assert_eq "dumpsys shortcut: the three are manifest shortcuts" "3" "$(grep -E 'files_(device|recent|bin) ' "$ROW_DIR/00-shortcuts.txt" | grep -c 'Man')"
assert_eq "dumpsys shortcut: no files_sdcard" "0" "$(grep -c files_sdcard "$ROW_DIR/00-shortcuts.txt")"
burst "$FILES_TILE" 01-burst3
assert_eq "Files tile: 3 satellites" "3" "$(sats 01-burst3)"
assert_eq "Files tile: the labels in order" "This Device|Recent|Recycle Bin|" "$(labels 01-burst3)"
assert_eq "Files tile: quick_sat:3 absent" "no" "$(H 01-burst3 quick_sat:3)"
assert_eq "Files tile: the [quick] line" "[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown: files_device,files_recent,files_bin)" "$(qline $BMARK)"
# ---- each satellite opens its page with the pane closed
page() { # sat-index name
  c6; burst "$FILES_TILE" "02-$2-burst"; tap_node "$ROW_DIR/02-$2-burst.xml" "quick_sat:$1"; sleep 3; D "03-$2"; S "03-$2"
  assert_eq "$2: FilesActivity resumed" "$FILES_ACTIVITY" "$(top_activity)"
  assert_eq "$2: the pane is closed" "no" "$(H "03-$2" files_pane)"
}
page 0 device; assert_eq "This Device: files_crumb:0" "This Device" "$(X 03-device files_crumb:0)"
page 1 recent; assert_eq "Recent: the page (its one row from E14's leg, or the empty line)" "yes" "$([ "$(H 03-recent files_recent_row:r1b.png)" = yes ] || [ "$(X 03-recent files_recent_empty)" = "You haven't opened any files recently." ] && echo yes)"
record "Recent page shows" "rows=[$(recent_rows 03-recent)] empty=[$(X 03-recent files_recent_empty)] crumb=[$(X 03-recent files_crumb:0)]"
page 2 bin; assert_eq "Recycle Bin: files_bin present" "yes" "$(H 03-bin files_bin)"
c6
# ---- a public volume: 4 satellites, SD card last; the Music tile unchanged
ensure_start; M=$(ring_mark); pubvol_up; sleep 3
echo "$(ring_since $M | grep -F '[files]')"
shortcuts | tee "$ROW_DIR/10-shortcuts.txt"
assert_contains "volume up: files_sdcard is a dynamic shortcut" "Dyn" "$(grep files_sdcard "$ROW_DIR/10-shortcuts.txt")"
assert_contains "volume up: files_sdcard hangs on FilesActivity" "files.FilesActivity" "$(grep files_sdcard "$ROW_DIR/10-shortcuts.txt")"
burst "$FILES_TILE" 11-burst4
assert_eq "volume up: 4 satellites, SD card last" "This Device|Recent|Recycle Bin|SD card|" "$(labels 11-burst4)"
assert_eq "volume up: the [quick] line" "[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 4 (4 shown: files_device,files_recent,files_bin,files_sdcard)" "$(qline $BMARK)"
tap_node "$ROW_DIR/11-burst4.xml" quick_sat:3; sleep 3; D 12-sdcard; S 12-sdcard
assert_eq "SD card satellite: Files on the volume's root, pane closed" "$FILES_ACTIVITY no" "$(top_activity) $(H 12-sdcard files_pane)"
record "SD card page: crumb" "$(X 12-sdcard files_crumb:0)"
c6; burst "$MUSIC_TILE" 13-music
assert_eq "Music tile: its burst is unchanged" "[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown: songs,albums,artists,playlists)" "$(qline $BMARK)"
absent_in "Music tile: no files_sdcard in its slice" "files_sdcard" "$(ring_since $BMARK)"
c6
# ---- process-start reconcile (r3 D9): mount while the shell's main process is dead
pids() { q "pidof app.tileshell" | xargs; }
ensure_start; adb shell sm unmount "$PUBVOL_ID"; sleep 2
assert_eq "unmounted with the shell running: files_sdcard gone" "0" "$(shortcuts | grep -c files_sdcard)"
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 1
P0="$(pids)"; rings_save; adb shell am force-stop app.tileshell; P1="$(pids)"; adb shell sm mount "$PUBVOL_ID"; P2="$(pids)"; sleep 3; P3="$(pids)"
record "reconcile (mount): pids before the stop / right after it / right after sm mount / 3 s later" "[$P0] / [$P1] / [$P2] / [$P3]"
M=$(ring_mark); adb shell input keyevent KEYCODE_HOME; sleep 4
diag files | grep -E "roots:|volume mounted|shortcut sdcard" | tee "$ROW_DIR/20-reconcile-mount.txt"
record "reconcile (mount): the process's first [files] lines" "$(cat "$ROW_DIR/20-reconcile-mount.txt" | sed 's/.*\[files\] //' | xargs)"
assert_contains "reconcile (mount): [files] shortcut sdcard published" "[files] shortcut sdcard published" "$(diag files)"
burst "$FILES_TILE" 21-burst4-reconciled
assert_eq "reconcile (mount): 4 satellites without opening Files" "This Device|Recent|Recycle Bin|SD card|" "$(labels 21-burst4-reconciled)"
c6
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 1
P0="$(pids)"; rings_save; adb shell am force-stop app.tileshell; P1="$(pids)"; adb shell sm unmount "$PUBVOL_ID"; P2="$(pids)"; sleep 3; P3="$(pids)"
record "reconcile (unmount): pids before the stop / right after it / right after sm unmount / 3 s later" "[$P0] / [$P1] / [$P2] / [$P3]"
adb shell input keyevent KEYCODE_HOME; sleep 4
diag files | grep -E "roots:|volume unmounted|shortcut sdcard" | tee "$ROW_DIR/22-reconcile-unmount.txt"
record "reconcile (unmount): the process's first [files] lines" "$(cat "$ROW_DIR/22-reconcile-unmount.txt" | sed 's/.*\[files\] //' | xargs)"
burst "$FILES_TILE" 23-burst3-reconciled
assert_eq "reconcile (unmount): 3 satellites" "This Device|Recent|Recycle Bin|" "$(labels 23-burst3-reconciled)"
assert_eq "reconcile (unmount): files_sdcard gone from dumpsys shortcut" "0" "$(shortcuts | grep -c files_sdcard)"
c6; ensure_start
pubvol_down
leg_end

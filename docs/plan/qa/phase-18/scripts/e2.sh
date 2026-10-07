#!/usr/bin/env bash
# Phase 18 E2 — the removable volume and the SD card shortcut. The doc's clauses → this driver's legs.
#
#   none        with the grant and no public volume (`sm list-volumes`: `emulated;0 mounted`, no `public:` line): tap
#               `files_menu` → the pane lists exactly `files_pane:recent`, `files_pane:device`, `files_pane:bin` in that
#               order; `dumpsys shortcut` holds no `files_sdcard`; hold the Files tile → 3 satellites "This Device",
#               "Recent", "Recycle Bin", `quick_sat:3` absent, the activity-keyed `[quick]` line with 3 ids; `c6`.
#   mounted     Files cold-started (the pane is open), then `pubvol_up` (`sm set-virtual-disk true`, `sm partition
#               disk:<id> public`) from a MARK before it → a fourth row `files_pane:<UUID>` between device and bin whose
#               text is non-empty, holds no drive-letter form (`\([A-Z]:\)`) and equals the PLATFORM's label recorded by
#               build task 0 (a) (BUILDSTART/records.tsv `pubvol_description`); `[files] volume mounted <name>` with the
#               same label. WITHIN 3 s: a device-side poll prints the device clock when `sm list-volumes public` first
#               reads `mounted` (it can only be LATE, by one `sm` call — about 0.1 s); the app's `volume mounted` line's
#               wall stamp and the time the pane dump returned are each asserted ≤ 3000 ms after it (the dump's own time
#               is inside that budget; RECORDED). `dumpsys shortcut` lists `files_sdcard` as a DYNAMIC shortcut on
#               FilesActivity; the Files tile's burst shows 4 satellites, "SD card" last, the `[quick]` line with 4 ids
#               (the order asserted is the burst's — BUILD-NOTES UI builder 6: dumpsys renumbers dynamic ranks).
#   Music       `c6`, hold the Music tile (the baseline's MUSIC slot) → `[quick] shortcuts for …/.music.MusicActivity/0:
#               4 (4 shown: songs,albums,artists,playlists)` and no `files_sdcard` in the slice (C-21).
#   browse      tap the volume's pane row → its root (`files_crumb:0` = the label); New folder `qa-e2` through
#               `files_dialog_input` / `files_dialog:ok` → `adb shell ls /storage/<UUID>` shows it, `[files] new folder
#               …: ok`.
#   unmount     with that folder of the volume open and the pane open over it: `sm unmount public:<x>,<y>` → the pane
#               row is gone; the pane closed, the page shows "This storage was removed" (`files_error`), `[files] volume unmounted
#               <label>`, `[files] shortcut sdcard removed`, `files_sdcard` gone from `dumpsys shortcut`, the burst back
#               to 3, no AndroidRuntime line names app.tileshell.
#   reconcile   (r3 D9) the volume mounted while the shell's main process is dead, then Home and a hold WITHOUT opening
#               Files → 4 satellites and `[files] shortcut sdcard published`; the reverse with `sm unmount` → 3
#               satellites, `files_sdcard` gone from `dumpsys shortcut`. The shell is the HOME app and Android brings its
#               process back within a second of a force-stop (BUILD-NOTES: the smoke's process was back before `sm
#               mount` returned, and its first lines read `roots: 1 … volume mounted …` — the callback's path, not the
#               process-start one). So this leg keeps the process dead THROUGH the mount: a device-side script
#               force-stops the shell in a loop while `sm mount` runs and once more after the volume reads mounted. The
#               proof that the publish came from the process-start reconcile is in the new process's own lines, both
#               asserted: its `[files] roots:` line already counts 2 volumes, and the slice holds NO `[files] volume
#               mounted` line (the callback never fired). The unmount half mirrors it (`roots: 1`, no `volume unmounted`).
#   restore     `pubvol_down` (`sm set-virtual-disk false`); the device as found.
#
# The row is E12's producer of `[files] volume mounted | unmounted <name>` and `[files] shortcut sdcard published | removed`.
# Changes on the device: a virtual disk (removed), dynamic shortcut files_sdcard (removed with the volume). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E2
row_begin E2 "the removable volume and the SD card shortcut"
LC0="$(lc_mark)"
MUSIC_TILE="tile:slot:MUSIC"
Q3="[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown: files_device,files_recent,files_bin)"
Q4="[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 4 (4 shown: files_device,files_recent,files_bin,files_sdcard)"
LABEL="$(awk -F'\t' '$1 == "pubvol_description" { print $2 }' "$P18/BUILDSTART/records.tsv")"
record "the platform's label for the public volume (task 0 (a): StorageVolume.getDescription)" "$LABEL"
assert_ne "the label is recorded" "" "$LABEL"
pane_rows() { ids_of "$1" files_pane: | xargs; }
pids() { q "pidof app.tileshell" | xargs; }
wall_of() { printf '%s\n' "$1" | sed -n 's/.*wall=\([0-9]*\).*/\1/p' | head -1; }
baseline_start
c6; ensure_start

# ------------------------------------------------------------------------------------------------- no volume
log "--- no volume"
VOLS="$(q 'sm list-volumes' | xargs)"
record "sm list-volumes" "$VOLS"
assert_contains "sm list-volumes: emulated;0 mounted" "emulated;0 mounted" "$VOLS"
assert_eq "…and no public volume, no disk" "" "$(q 'sm list-volumes public' | xargs)$(q 'sm list-disks' | xargs)"
files_at "$SD"; D 01-device
assert_eq "Files on This Device, the pane closed" "This Device no" "$(X 01-device files_crumb:0) $(H 01-device files_pane)"
T 01-device files_menu 1.2; D 02-pane; S 02-pane
assert_eq "tap files_menu: files_pane is present" "yes" "$(H 02-pane files_pane)"
assert_eq "…it lists exactly recent, device, bin in that order (no volume row)" "recent device bin" "$(pane_rows 02-pane)"
assert_eq "dumpsys shortcut: no files_sdcard" "0" "$(shortcuts | grep -c files_sdcard)"
burst "$FILES_TILE" 03-burst3
assert_eq "hold the Files tile: 3 satellites, their labels" "3 This Device|Recent|Recycle Bin|" "$(sat_count 03-burst3) $(sat_labels 03-burst3)"
assert_eq "…quick_sat:3 absent" "no" "$(H 03-burst3 quick_sat:3)"
assert_eq "…the [quick] line" "$Q3" "$(qline "$BMARK")"
c6; ensure_start

# ------------------------------------------------------------------------------------------------- mounted
log "--- the volume mounts with Files open and the pane showing"
files_open; D 10-cold
assert_eq "Files cold-started: the pane is open, three rows" "recent device bin" "$(pane_rows 10-cold)"
cat > "$ROW_DIR/poll-mounted.sh" <<'SH'
n=0
while [ $n -lt 400 ]; do
  if sm list-volumes public | grep -q ' mounted '; then date +%s%3N; exit 0; fi
  n=$((n + 1))
done
echo none
SH
adb push "$ROW_DIR/poll-mounted.sh" /data/local/tmp/p18-e2-poll.sh >/dev/null 2>&1
MARK="$(ring_mark)"
adb shell sh /data/local/tmp/p18-e2-poll.sh > "$ROW_DIR/mounted-at.txt" 2>&1 &
POLL=$!
pubvol_up
D 11-pane4; T_DUMP="$(ring_mark)"; S 11-pane4
wait "$POLL"
adb shell rm -f /data/local/tmp/p18-e2-poll.sh
T_MOUNT="$(tr -d '\r' < "$ROW_DIR/mounted-at.txt" | head -1)"
[ -n "${PUBVOL_UUID:-}" ] || { _verdict FAIL "the public volume is up" "pubvol_up gave no UUID"; pubvol_down; row_end; exit 1; }
U="$PUBVOL_UUID"
LINE="$(await_line "$MARK" "[files] volume mounted" 40)"
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/11-ring.txt"
record "the device clock when sm first read mounted / the app's line / the pane dump returned" "$T_MOUNT / $(wall_of "$LINE") / $T_DUMP"
assert_eq "the pane lists a fourth row files_pane:<UUID> between device and bin" "recent device $U bin" "$(pane_rows 11-pane4)"
ROWTEXT="$(X 11-pane4 "files_pane_label:$U")"
assert_ne "its text is non-empty" "" "$ROWTEXT"
assert_eq "…holds no drive-letter form (\\([A-Z]:\\) absent — T18-8)" "0" "$(printf '%s' "$ROWTEXT" | grep -cE '\([A-Z]:\)')"
assert_eq "…and equals the platform's label (task 0 (a)'s record)" "$LABEL" "$ROWTEXT"
assert_eq "[files] volume mounted <name>: the name is the same recorded label" "[files] volume mounted $LABEL" "$(printf '%s\n' "$LINE" | grep -o '\[files\] volume mounted .*')"
assert_le "within 3 s: the app's volume-mounted line, ms after the platform's mount" 3000 "$(( $(wall_of "$LINE") - T_MOUNT ))"
assert_le "within 3 s: the pane dump holding the row had returned, ms after the platform's mount" 3000 "$(( T_DUMP - T_MOUNT ))"
assert_contains "[files] shortcut sdcard published" "[files] shortcut sdcard published" "$(await_line "$MARK" "[files] shortcut sdcard published" 20)"
shortcuts > "$ROW_DIR/12-shortcuts.txt"
SC="$(grep '^files_sdcard ' "$ROW_DIR/12-shortcuts.txt")"
record "dumpsys shortcut: files_sdcard" "$SC"
assert_contains "dumpsys shortcut lists files_sdcard as a dynamic shortcut (flag Dyn)" "Dyn" "$(printf '%s' "$SC" | grep -o 'flags=\[[^]]*\]')"
assert_contains "…hung on FilesActivity (setActivity — C-21)" "app.tileshell/app.tileshell.files.FilesActivity" "$SC"
burst "$FILES_TILE" 13-burst4
assert_eq "the Files tile's burst: 4 satellites, SD card last" "4 This Device|Recent|Recycle Bin|SD card|" "$(sat_count 13-burst4) $(sat_labels 13-burst4)"
assert_eq "…the [quick] line with 4 ids" "$Q4" "$(qline "$BMARK")"
c6; ensure_start

# ------------------------------------------------------------------------------------------------- the Music tile
log "--- the Music tile's burst is unchanged"
burst "$MUSIC_TILE" 20-music
assert_eq "hold the Music tile: its [quick] line" "[quick] shortcuts for app.tileshell/.music.MusicActivity/0: 4 (4 shown: songs,albums,artists,playlists)" "$(qline "$BMARK")"
absent_in "…no files_sdcard anywhere in the slice from the MARK before the hold" "files_sdcard" "$(ring_since "$BMARK")"
assert_absent "…and no SD card label among its satellites" "SD card" "$(sat_labels 20-music)"
c6; ensure_start

# ------------------------------------------------------------------------------------------------- browse, new folder
log "--- browse the volume, make a folder on it"
files_open; D 30-cold
MARK="$(ring_mark)"
T 30-cold "files_pane:$U" 1.5; D 31-volume; S 31-volume
assert_eq "tap the volume's row: its root is shown (files_crumb:0 = the label), the pane closed" "$LABEL no" "$(X 31-volume files_crumb:0) $(H 31-volume files_pane)"
assert_contains "…[files] list /storage/<UUID>" "[files] list /storage/$U: " "$(ring_since "$MARK")"
MARK="$(ring_mark)"
T 31-volume files_bar:new_folder 1.2; D 32-dialog; S 32-dialog
assert_eq "New folder: the dialog with its input" "yes yes" "$(H 32-dialog files_dialog) $(H 32-dialog files_dialog_input)"
T 32-dialog files_dialog_input 0.6
adb shell input keyevent KEYCODE_MOVE_END
for _ in $(seq 1 30); do adb shell input keyevent KEYCODE_DEL; done
adb shell input text qa-e2; sleep 0.6
D 33-typed; T 33-typed files_dialog:ok 1.8; D 34-made; S 34-made
assert_contains "adb shell ls /storage/<UUID> shows the new folder" "|qa-e2|" "|$(q "ls /storage/$U" | tr '\n' '|')"
assert_eq "…it is a directory" "directory" "$(q "stat -c %F /storage/$U/qa-e2")"
assert_contains "…[files] new folder …: ok" "[files] new folder /storage/$U/qa-e2: ok" "$(ring_since "$MARK")"
record "after New folder: the crumbs / the rows" "$(X 34-made files_crumb:0)>$(X 34-made files_crumb:1) / $(ids_of 34-made files_row: | xargs)"
# Into that folder (when the page is not already there), then the pane over it.
if [ "$(X 34-made files_crumb:1)" != qa-e2 ]; then T 34-made files_row:qa-e2 1.5; fi
D 35-in-folder
assert_eq "a folder of the volume is open: the crumbs" "$LABEL|qa-e2" "$(X 35-in-folder files_crumb:0)|$(X 35-in-folder files_crumb:1)"
T 35-in-folder files_menu 1.2; D 36-pane
assert_eq "…and the pane over it holds the volume's row" "recent device $U bin" "$(pane_rows 36-pane)"

# ------------------------------------------------------------------------------------------------- unmount
log "--- sm unmount with the volume's folder open"
LCU="$(lc_mark)"; sleep 1.1
MARK="$(ring_mark)"
adb shell sm unmount "$PUBVOL_ID"
LINE="$(await_line "$MARK" "[files] volume unmounted" 40)"
sleep 1; D 40-removed; S 40-removed
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/40-ring.txt"
assert_eq "[files] volume unmounted <name>" "[files] volume unmounted $LABEL" "$(printf '%s\n' "$LINE" | grep -o '\[files\] volume unmounted .*')"
# The pane covers the sort line's place, where files_error is drawn, and a covered node is not in the dump (run 1 read no
# files_error with the pane open; scratch probe 2026-10-06: the text is there the moment the pane closes). So: the pane's
# rows first, as it stands open; then the pane closed with its own ≡, and the page read.
assert_eq "the pane is still open over the page" "yes" "$(H 40-removed files_pane)"
assert_eq "the pane row is gone: recent, device, bin" "recent device bin" "$(pane_rows 40-removed)"
T 40-removed files_pane_menu 1.2; D 41-page; S 41-page
assert_eq "the pane closed (its own ≡)" "no" "$(H 41-page files_pane)"
assert_eq "the page shows files_error" "This storage was removed" "$(X 41-page files_error)"
assert_eq "…and lists nothing" "" "$(ids_of 41-page files_row: | xargs)"
assert_contains "[files] shortcut sdcard removed" "[files] shortcut sdcard removed" "$(await_line "$MARK" "[files] shortcut sdcard removed" 20)"
assert_eq "files_sdcard is gone from dumpsys shortcut" "0" "$(shortcuts | grep -c files_sdcard)"
assert_eq "FilesActivity is still on top" "$FILES_ACTIVITY" "$(top_activity)"
assert_eq "no crash: no AndroidRuntime line names the shell since before the unmount" "0" "$(crash_since "$LCU")"
burst "$FILES_TILE" 42-burst3
assert_eq "the burst is back to 3" "3 This Device|Recent|Recycle Bin| $Q3" "$(sat_count 42-burst3) $(sat_labels 42-burst3) $(qline "$BMARK")"
c6; ensure_start

# ------------------------------------------------------------------------------------------------- reconcile
# The device-side script: the shell kept dead through `sm <verb>` (a loop of force-stops beside it), one more force-stop
# after the volume reads the wanted state, then the pids. $1 = mount|unmount, $2 = the volume id, $3 = the state to wait for.
cat > "$ROW_DIR/dead-through.sh" <<'SH'
verb="$1"; vol="$2"; want="$3"
flag=/data/local/tmp/p18-e2-done
rm -f $flag
echo "pid before: [$(pidof app.tileshell)]"
am force-stop app.tileshell
( i=0; while [ $i -lt 200 ] && [ ! -f $flag ]; do am force-stop app.tileshell; i=$((i + 1)); done; echo "force-stops beside sm $verb: $i" ) &
sm $verb "$vol"
n=0
while [ $n -lt 100 ]; do sm list-volumes public | grep -q " $want " && break; n=$((n + 1)); done
echo "sm list-volumes public: $(sm list-volumes public)"
: > $flag
wait
am force-stop app.tileshell
echo "pid right after the last force-stop: [$(pidof app.tileshell)]"
rm -f $flag
SH
adb push "$ROW_DIR/dead-through.sh" /data/local/tmp/p18-e2-dead.sh >/dev/null 2>&1
dead_through() { # verb state out
  rings_save
  adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 1   # another app in front: Home is not resumed
  RMARK="$(ring_mark)"
  q "sh /data/local/tmp/p18-e2-dead.sh $1 $PUBVOL_ID $2" > "$ROW_DIR/$3"
  note "dead_through $1: $(tr '\n' ';' < "$ROW_DIR/$3")"
  adb shell input keyevent KEYCODE_HOME; sleep 4
}
log "--- process-start reconcile: mounted while the shell's main process is dead"
dead_through mount mounted 50-dead-mount.txt
record "reconcile (mount): the script's record" "$(tr '\n' ';' < "$ROW_DIR/50-dead-mount.txt" | cut -c1-220)"
assert_contains "the volume is mounted again" " mounted $U" "$(q 'sm list-volumes public')"
PUB="$(await_line "$RMARK" "[files] shortcut sdcard published" 40)"
SL="$(ring_since "$RMARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/50-ring.txt"
record "reconcile (mount): the new process's [files] lines" "$(sed 's/.*\[files\] //' "$ROW_DIR/50-ring.txt" | tr '\n' ';' | cut -c1-220)"
assert_contains "reconcile (mount): [files] shortcut sdcard published" "[files] shortcut sdcard published" "$PUB"
assert_eq "…the process was dead through the mount: its FIRST roots line already counts both volumes" "[files] roots: 2 (Internal shared storage, $LABEL)" "$(grep -o '\[files\] roots: .*' "$ROW_DIR/50-ring.txt" | head -1)"
absent_in "…and no [files] volume mounted line: the callback never fired, the publish is the process-start reconcile's" "[files] volume mounted" "$SL"
absent_in "…Files was not opened (no FilesActivity created line)" "FilesActivity created" "$SL"
burst "$FILES_TILE" 51-burst4
assert_eq "reconcile (mount): hold without opening Files → 4 satellites, SD card last" "4 This Device|Recent|Recycle Bin|SD card| $Q4" "$(sat_count 51-burst4) $(sat_labels 51-burst4) $(qline "$BMARK")"
assert_eq "…dumpsys shortcut holds files_sdcard" "1" "$(shortcuts | grep -c '^files_sdcard ')"
log "--- process-start reconcile: unmounted while the shell's main process is dead"
dead_through unmount unmounted 52-dead-unmount.txt
record "reconcile (unmount): the script's record" "$(tr '\n' ';' < "$ROW_DIR/52-dead-unmount.txt" | cut -c1-220)"
REM="$(await_line "$RMARK" "[files] shortcut sdcard removed" 40)"
SL="$(ring_since "$RMARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/52-ring.txt"
record "reconcile (unmount): the new process's [files] lines" "$(sed 's/.*\[files\] //' "$ROW_DIR/52-ring.txt" | tr '\n' ';' | cut -c1-220)"
assert_contains "reconcile (unmount): [files] shortcut sdcard removed" "[files] shortcut sdcard removed" "$REM"
assert_eq "…the process was dead through the unmount: its FIRST roots line counts one volume" "[files] roots: 1 (Internal shared storage)" "$(grep -o '\[files\] roots: .*' "$ROW_DIR/52-ring.txt" | head -1)"
absent_in "…and no [files] volume unmounted line (the callback never fired)" "[files] volume unmounted" "$SL"
burst "$FILES_TILE" 53-burst3
assert_eq "reconcile (unmount): hold → 3 satellites" "3 This Device|Recent|Recycle Bin| $Q3" "$(sat_count 53-burst3) $(sat_labels 53-burst3) $(qline "$BMARK")"
assert_eq "…files_sdcard gone from dumpsys shortcut" "0" "$(shortcuts | grep -c files_sdcard)"
adb shell rm -f /data/local/tmp/p18-e2-dead.sh

# ------------------------------------------------------------------------------------------------- restore
log "--- restore"
c6; ensure_start
pubvol_down
sleep 2
assert_eq "after pubvol_down: no files_sdcard in dumpsys shortcut" "0" "$(shortcuts | grep -c files_sdcard)"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
assert_eq "the row left nothing on shared storage (no .Tessera, no thumbnails of its own)" "" "$(q 'ls -d /sdcard/.Tessera 2>/dev/null')"
ensure_start
end_state
row_end

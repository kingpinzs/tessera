#!/usr/bin/env bash
# Phase 18 E9: MediaStore in step. The images are the row's own, put in DCIM/Camera by media_up (r3 V12 / V3).
#
#   move      qa-photo-0.png moved from DCIM/Camera to Pictures/QA-Album in Files → `content query … --projection
#             _display_name:relative_path` shows the new path within 3 s, and the Photos tile's `[photos] refresh
#             (mediastore change)` line follows
#   rename    the MP3 renamed in Files → the audio collection shows the new _display_name within 3 s, and the slice
#             from a MARK before the rename holds `[music] library (media change): <n> tracks` (r3 V12)
#   RECORDED  (T18-4) a rename by path through FUSE with no scan — `adb shell mv` of qa-photo-1.png between the two
#             folders — and the same query 3 s later: whether MediaProvider followed it on its own. The doc's command
#             moves the file from Pictures/QA-Album to DCIM/Camera; the row's own qa-photo-1.png starts in DCIM/Camera
#             (media_up), so it is first moved out the same way (recorded too), then back with the doc's command.
#             Task 0 (d)'s row-on-create record (qa/phase-18/BUILDSTART/records.tsv) is recorded beside it.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/rowsb.sh"
PRIMARY=/storage/emulated/0
CAM=$PRIMARY/DCIM/Camera
ALBUM=$PRIMARY/Pictures/QA-Album

keep_earlier_run E9
row_begin E9 "MediaStore in step: a move and a rename made in Files reach the images and audio collections within 3 s"
assert_gate_apk
baseline_start
adb logcat -c
files_up media || { row_end; exit 1; }
adb shell mkdir -p /sdcard/Pictures/QA-Album
# This AVD also holds a /sdcard/Pictures/qa-photo-0.png that is not the row's (relative_path=Pictures/ exactly; found at
# run 1): that row is left out, the row's own file is in DCIM/Camera/ or Pictures/QA-Album/.
img_row() { q "content query --uri content://media/external/images/media --projection _display_name:relative_path" | grep -F "_display_name=$1," | grep -v 'relative_path=Pictures/$' | sed 's/^Row: [0-9]* //' | xargs; }
aud_names() { q "content query --uri content://media/external/audio/media --projection _display_name:_data" | grep -F "$QF/" | sed -n 's/.*_display_name=\([^,]*\),.*/\1/p' | LC_ALL=C sort | xargs; }
# Poll a command until its output equals a value, at most 3 s from T0 (device clock); prints the ms it took, or nothing.
within3() { # t0 expected command…
  local t0="$1" want="$2" i; shift 2
  for i in $(seq 1 15); do
    if [ "$("$@")" = "$want" ]; then echo $(( $(device_ms) - t0 )); return 0; fi
    sleep 0.15
  done
  return 1
}
ensure_start
assert_eq "precondition: qa-photo-0.png is in the images collection under DCIM/Camera/ (media_up)" "_display_name=qa-photo-0.png, relative_path=DCIM/Camera/" "$(img_row qa-photo-0.png)"
MD5_P="$(md5dev /sdcard/DCIM/Camera/qa-photo-0.png)"

# ------------------------------------------------------------------------------------------------ the move
log "--- move qa-photo-0.png from DCIM/Camera to Pictures/QA-Album in Files"
files_at "$CAM"; M="$(ring_mark)"
D p1; T p1 files_bar:select 1; scroll_to_node "$ROW_DIR/p2.xml" files_row:qa-photo-0.png >/dev/null; T p2 files_row:qa-photo-0.png 0.6; D p3; T p3 files_sel:move 1.5
D p4; T p4 files_menu 1; D p4b; T p4b files_pane:device 1.5
_pick_walk Pictures QA-Album
D p5
record "the picker before the confirm: its crumbs" "$(X p5 files_crumb:0) › $(X p5 files_crumb:1) › $(X p5 files_crumb:2)"
T0="$(ring_mark)"; T p5 files_pick_ok 0
MS="$(within3 "$T0" "_display_name=qa-photo-0.png, relative_path=Pictures/QA-Album/" img_row qa-photo-0.png)"
record "move: ms from the MARK before the confirming tap to the query showing the new path" "${MS:-not within 3 s}"
assert_eq "move: content query shows relative_path=Pictures/QA-Album/ for qa-photo-0.png" "_display_name=qa-photo-0.png, relative_path=Pictures/QA-Album/" "$(img_row qa-photo-0.png)"
assert_eq "move: …within 3 s" "yes" "$([ -n "$MS" ] && [ "$MS" -le 3000 ] && echo yes || echo no)"
SL="$(ring_since "$M")"
assert_contains "move: [files] move 1 files … -> …/Pictures/QA-Album done" " -> $ALBUM done" "$(printf '%s\n' "$SL" | grep -F '[files] move 1 files')"
PL="$(wait_line "$T0" "[photos] refresh (mediastore change)" 10)"
assert_contains "move: the Photos tile's [photos] refresh (mediastore change) line follows" "[photos] refresh (mediastore change)" "$PL"
assert_eq "move: the file is at the new path with its md5, and gone from DCIM/Camera" "$MD5_P|" "$(md5dev /sdcard/Pictures/QA-Album/qa-photo-0.png)|$(lsdev /sdcard/DCIM/Camera/qa-photo-0.png)"

# ------------------------------------------------------------------------------------------------ the rename
log "--- rename the MP3 in Files"
assert_contains "precondition: the audio collection lists 03.mp3 of QA-Files" "03.mp3" "$(aud_names)"
BEFORE_NAMES="$(aud_names)"
# Music's library watches the audio collection from the moment its store is started in the process (MusicStore.start:
# Music opened, or its service). Run 1 renamed with Music never opened since the process began and the ring held no
# [music] line at all — there was no library to tell. So Music is opened once here, then left (Home), before the rename.
M="$(ring_mark)"; adb shell am start -W -n app.tileshell/.music.MusicActivity >/dev/null 2>&1; sleep 3
record "Music opened once before the rename: its library line" "$(ring_since "$M" | grep -o '\[music\] library.*' | sed 's/ *wall=.*//' | tail -1)"
adb shell input keyevent KEYCODE_HOME; sleep 1
files_at "$QF"; scroll_to_node "$ROW_DIR/r0.xml" files_row:03.mp3 >/dev/null; hold r0 files_row:03.mp3; D r1; T r1 files_hold:rename 1.5; D r2
dialog_type 03-renamed.mp3; D r3
assert_eq "rename: the typed name" "03-renamed.mp3" "$(X r3 files_dialog_input)"
M="$(ring_mark)"; T r3 files_dialog:ok 0
WANT="$(printf '%s\n' $BEFORE_NAMES | sed 's/^03\.mp3$/03-renamed.mp3/' | LC_ALL=C sort | xargs)"
MS="$(within3 "$M" "$WANT" aud_names)"
record "rename: ms from the MARK before the confirming tap to the query showing the new name" "${MS:-not within 3 s}"
assert_eq "rename: the audio collection shows the new _display_name (and no longer the old)" "$WANT" "$(aud_names)"
assert_eq "rename: …within 3 s" "yes" "$([ -n "$MS" ] && [ "$MS" -le 3000 ] && echo yes || echo no)"
ML="$(wait_line "$M" "[music] library (media change):" 10)"
record "rename: the line" "$(printf '%s' "$ML" | grep -o '\[music\].*' | sed 's/ *wall=.*//')"
assert_contains "rename: the slice from the MARK before it holds [music] library (media change): <n> tracks" "[music] library (media change): " "$ML"
assert_contains "rename: [files] rename …/03.mp3 -> 03-renamed.mp3: ok" "[files] rename $QF/03.mp3 -> 03-renamed.mp3: ok" "$(ring_since "$M")"

# ------------------------------------------------------------------------------------------------ RECORDED (T18-4)
log "--- RECORDED: adb shell mv through FUSE, no scan"
adb shell "mv /sdcard/DCIM/Camera/qa-photo-1.png /sdcard/Pictures/QA-Album/"; sleep 3
R1="$(img_row qa-photo-1.png)"
record "mv DCIM/Camera/qa-photo-1.png → Pictures/QA-Album/ (no scan): the query 3 s later" "$R1"
record "…MediaProvider followed it on its own" "$(case "$R1" in *relative_path=Pictures/QA-Album/*) echo yes ;; *) echo no ;; esac)"
adb shell "mv /sdcard/Pictures/QA-Album/qa-photo-1.png /sdcard/DCIM/Camera/"; sleep 3
R2="$(img_row qa-photo-1.png)"
record "adb shell mv /sdcard/Pictures/QA-Album/qa-photo-1.png /sdcard/DCIM/Camera/ (the doc's command, no scan): the query 3 s later" "$R2"
record "…MediaProvider followed it on its own (Files keeps its scan either way)" "$(case "$R2" in *relative_path=DCIM/Camera/*) echo yes ;; *) echo no ;; esac)"
record "task 0 (d)'s row-on-create record (BUILDSTART/records.tsv: fuse_row_on_create)" "$(grep -m1 '^fuse_row_on_create' "$P18/BUILDSTART/records.tsv" | cut -f2)"

# ------------------------------------------------------------------------------------------------ restore
assert_eq "no crash of the shell in the row (AndroidRuntime)" "0" "$(crash)"
adb shell input keyevent KEYCODE_HOME; sleep 1
# The moved photo is no longer at the path media_up pushed it to: it is removed here, by name, before media_down.
adb shell "rm -f /sdcard/Pictures/QA-Album/qa-photo-0.png"
files_down
ensure_start
assert_gate_apk "end: the installed APK is the gate candidate"
row_end

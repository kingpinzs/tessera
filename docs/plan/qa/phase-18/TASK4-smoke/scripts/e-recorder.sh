#!/usr/bin/env bash
# E17: Voice Recorder's "Open file location" → Files on Recordings; Back returns; with Files already open deep in
# another folder one Back returns; a path extra outside every volume is ignored.
. "$(dirname "$0")/t4.sh"; take_device_lock; leg e-recorder
adb logcat -c
REC=app.tileshell/.recorder.RecorderActivity
HAD_DIR="$(q 'ls -d /sdcard/Recordings 2>/dev/null')"; record "before: /sdcard/Recordings" "[${HAD_DIR}] $(q 'ls /sdcard/Recordings 2>/dev/null' | xargs)"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=330:duration=3" -ac 1 -c:a aac -b:a 64k "$ROW_DIR/qa-take.m4a"
adb shell mkdir -p /sdcard/Recordings; adb push "$ROW_DIR/qa-take.m4a" /sdcard/Recordings/qa-take.m4a >/dev/null; media_scan
ID="$(q "content query --uri content://media/external/audio/media --projection _id:_data:is_recording" | grep 'Recordings/qa-take.m4a' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p')"
echo "qa-take.m4a: audio id $ID  ($(q "content query --uri content://media/external/audio/media --projection _id:_data:is_recording" | grep 'qa-take' | xargs))"
assert_ne "precondition: qa-take.m4a has an audio row" "" "$ID"
c6; ensure_start
to_menu() { # prefix — Voice Recorder, hold the row, the menu dumped as <prefix>-menu
  adb shell am start -W -n $REC >/dev/null 2>&1; sleep 2.5; D "$1-rec"
  assert_eq "$1: the recorder lists rec_row:$ID" "yes" "$(H "$1-rec" rec_row:$ID)"
  hold "$1-rec" "rec_row:$ID"; D "$1-menu"; S "$1-menu"
}
# ---- Files not running
to_menu 01
assert_eq "the hold menu holds rec_menu:location" "yes" "$(H 01-menu rec_menu:location)"
assert_contains "…with the text Open file location" "Open file location" "$(grep -o 'text="[^"]*"' "$ROW_DIR/01-menu.xml" | xargs)"
echo "menu: $(ids 01-menu | tr ' ' '\n' | grep rec_menu | xargs)"
M=$(ring_mark); T 01-menu rec_menu:location 3; D 02-files; S 02-files
assert_eq "tap: FilesActivity resumed" "$FILES_ACTIVITY" "$(top_activity)"
LAST="$(grep -o 'resource-id="files_crumb:[0-9]*"' "$ROW_DIR/02-files.xml" | tail -1 | sed 's/resource-id="//;s/"//')"
assert_eq "the last crumb ($LAST) reads Recordings" "Recordings" "$(X 02-files "$LAST")"
assert_eq "files_row:qa-take.m4a is present" "yes" "$(H 02-files files_row:qa-take.m4a)"
assert_eq "the pane is closed" "no" "$(H 02-files files_pane)"
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/02-ring.txt"
assert_contains "[files] open at … (from recorder)" "[files] open at /storage/emulated/0/Recordings (from recorder)" "$SL"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_eq "Back returns to Voice Recorder" "$REC" "$(top_activity)"
# ---- Files already open, deep in another folder
adb shell input keyevent KEYCODE_HOME; sleep 1
files_at $QF; D 10a; T 10a files_row:sub 1.5; D 10b
assert_eq "Files is in QA-Files/sub" "sub" "$(X 10b "$(grep -o 'resource-id="files_crumb:[0-9]*"' "$ROW_DIR/10b.xml" | tail -1 | sed 's/resource-id="//;s/"//')")"
adb shell input keyevent KEYCODE_HOME; sleep 1.5
to_menu 11
M=$(ring_mark); T 11-menu rec_menu:location 3; D 12-files; S 12-files
assert_eq "Files already open: FilesActivity resumed" "$FILES_ACTIVITY" "$(top_activity)"
LAST="$(grep -o 'resource-id="files_crumb:[0-9]*"' "$ROW_DIR/12-files.xml" | tail -1 | sed 's/resource-id="//;s/"//')"
assert_eq "Files already open: it shows Recordings" "Recordings yes" "$(X 12-files "$LAST") $(H 12-files files_row:qa-take.m4a)"
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/12-ring.txt"
assert_contains "Files already open: the line (onNewIntent)" "[files] open at /storage/emulated/0/Recordings (from recorder)" "$SL"
absent_in "Files already open: no new FilesActivity was created" "FilesActivity created" "$SL"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_eq "ONE Back returns to Voice Recorder, not to sub" "$REC" "$(top_activity)"
# ---- a path outside every mounted volume
c6; ensure_start
M=$(ring_mark); adb shell am start -W -n $FILES_ACTIVITY --es path /data/data/app.tileshell >/dev/null 2>&1; sleep 2; D 20-bad; S 20-bad
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/20-ring.txt"
assert_contains "bad path: [files] open ignored: <why>" "[files] open ignored: " "$SL"
assert_eq "bad path: the page Files shows without it (a cold start: Recent, the pane open)" "Recent yes" "$(X 20-bad files_crumb:0) $(H 20-bad files_pane)"
absent_in "bad path: nothing of the private folder is listed" "list /data" "$SL"
M=$(ring_mark); adb shell am start -W -n $FILES_ACTIVITY --es path "/storage/emulated/0/../../data/data/app.tileshell" >/dev/null 2>&1; sleep 2; D 21-bad
SL="$(ring_since $M)"; echo "$SL" | grep -F "[files]" | tee "$ROW_DIR/21-ring.txt"
assert_contains "traversal path (warm): [files] open ignored" "[files] open ignored: " "$SL"
# ---- restore
c6
adb shell rm -f /sdcard/Recordings/qa-take.m4a; [ -z "$HAD_DIR" ] && adb shell rmdir /sdcard/Recordings; media_scan
assert_eq "restore: no qa-take.m4a row" "0" "$(q "content query --uri content://media/external/audio/media --projection _data" | grep -c 'qa-take')"
record "after: /sdcard/Recordings" "[$(q 'ls -d /sdcard/Recordings 2>/dev/null')] $(q 'ls /sdcard/Recordings 2>/dev/null' | xargs)"
ensure_start
leg_end

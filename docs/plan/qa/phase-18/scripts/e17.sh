#!/usr/bin/env bash
# Phase 18 E17 — "Open file location" from Voice Recorder (T15-16). The doc's clauses → this driver's legs.
#
#   fixture    a host-made 3-s .m4a pushed to `/sdcard/Recordings/qa-take.m4a` + the scan; its audio row's id read from
#              MediaStore (the precondition, asserted).
#   menu       Voice Recorder open, hold `rec_row:<id>` → `rec_menu:location` present with the text "Open file location".
#   tap        → topResumed = `app.tileshell/.files.FilesActivity`, the last `files_crumb:` reads "Recordings",
#              `files_row:qa-take.m4a` present, the pane closed, and the slice from a MARK before the tap holds `[files]
#              open at /storage/emulated/0/Recordings (from recorder)`; Back returns to Voice Recorder.
#   already    (r3 D12) Files opened and taken into the row's own `/sdcard/QA-Files/sub`, Home, the hold-menu tap again →
#   open       Files shows `Recordings` — the same line, and NO `FilesActivity created` line in the slice (onNewIntent
#              on the running activity) — and ONE Back returns to Voice Recorder, not to `sub` (the extras reset the
#              history).
#   bad path   `c6`, then `am start -n app.tileshell/.files.FilesActivity --es path /data/data/app.tileshell` → `[files]
#              open ignored: <why>` (the reason RECORDED) and the page Files would show without the extra — a cold
#              start's: Recent, the pane open — with no `[files] list /data…` line; the same on a running Files with a
#              `..` traversal (`/storage/emulated/0/../../data/data/app.tileshell`); `c6` (C-6).
#
# The row is E12's producer of `[files] open at <path> (from <caller>)` and `[files] open ignored: <why>`.
# Changes on the device: QA-Files (files_up / files_down), /sdcard/Recordings/qa-take.m4a (removed before files_down,
# whose snapshots prove it). No wipe, no root; the microphone is never used (the take is a pushed file).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E17
row_begin E17 "Open file location from Voice Recorder"
LC0="$(lc_mark)"
REC=app.tileshell/.recorder.RecorderActivity
OPEN_LINE="[files] open at $SD/Recordings (from recorder)"
last_crumb() { X "$1" "files_crumb:$(ids_of "$1" files_crumb: | tail -1)"; }
baseline_start
files_up || { row_end; exit 1; }
trap 'adb shell rm -f /sdcard/Recordings/qa-take.m4a' EXIT

log "--- the fixture"
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=330:duration=3" -ac 1 -c:a aac -b:a 64k "$ROW_DIR/qa-take.m4a"
assert_eq "the host-made take exists (3 s of a sine, AAC in .m4a)" "yes" "$([ -s "$ROW_DIR/qa-take.m4a" ] && echo yes || echo no)"
adb shell mkdir -p /sdcard/Recordings
adb push "$ROW_DIR/qa-take.m4a" /sdcard/Recordings/qa-take.m4a >/dev/null
media_scan
ID="$(q "content query --uri content://media/external/audio/media --projection _id:_data:is_recording" | grep 'Recordings/qa-take.m4a' | sed -n 's/.*_id=\([0-9]*\),.*/\1/p' | head -1)"
record "qa-take.m4a's audio row" "$(q "content query --uri content://media/external/audio/media --projection _id:_data:is_recording" | grep 'qa-take' | xargs)"
assert_ne "precondition: qa-take.m4a has an audio row in MediaStore" "" "$ID"
c6; ensure_start

to_menu() { # prefix — Voice Recorder opened, the take's row held, the menu dumped as <prefix>-menu
  adb shell am start -W -n "$REC" >/dev/null 2>&1; sleep 2.5
  scroll_to_node "$ROW_DIR/$1-rec.xml" "rec_row:$ID" 6 >/dev/null || true
  assert_eq "$1: Voice Recorder is on top and lists rec_row:$ID" "$REC yes" "$(top_activity) $(H "$1-rec" "rec_row:$ID")"
  hold "$1-rec" "rec_row:$ID"; D "$1-menu"; S "$1-menu"
}

# ------------------------------------------------------------------------------------------------- Files not running
log "--- the hold menu, Files not running"
to_menu 01
assert_eq "the hold menu holds rec_menu:location" "yes" "$(H 01-menu rec_menu:location)"
MENU_TEXT="$(python3 - "$ROW_DIR/01-menu.xml" <<'PY'
import html, re, sys
x = open(sys.argv[1], encoding='utf-8', errors='replace').read()
nodes = re.findall(r'<node[^>]*>', x)
for i, s in enumerate(nodes):
    if 'resource-id="rec_menu:location"' in s:
        for n in nodes[i:i + 3]:
            t = html.unescape(re.search(r' text="([^"]*)"', n).group(1))
            if t: print(t); break
        break
PY
)"
assert_eq "…with the text" "Open file location" "$MENU_TEXT"
record "the hold menu's items" "$(ids_of 01-menu rec_menu: | xargs)"
MARK="$(ring_mark)"
T 01-menu rec_menu:location 3; D 02-files; S 02-files
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/02-ring.txt"
assert_eq "tap: topResumedActivity is FilesActivity" "$FILES_ACTIVITY" "$(top_activity)"
assert_eq "…the last files_crumb: reads Recordings" "Recordings" "$(last_crumb 02-files)"
assert_eq "…files_row:qa-take.m4a is present" "yes" "$(H 02-files files_row:qa-take.m4a)"
assert_eq "…the pane is closed" "no" "$(H 02-files files_pane)"
assert_contains "…the slice from the MARK before the tap holds the line" "$OPEN_LINE" "$SL"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_eq "Back returns to Voice Recorder" "$REC" "$(top_activity)"

# ------------------------------------------------------------------------------------------------- Files already open
log "--- with Files already open, deep in another folder (r3 D12)"
adb shell input keyevent KEYCODE_HOME; sleep 1
files_at "$QF"; D 10-qa
T 10-qa files_row:sub 1.5; D 11-sub
assert_eq "Files is in QA-Files/sub (reached by a tap: one step of history)" "This Device|QA-Files|sub" "$(X 11-sub files_crumb:0)|$(X 11-sub files_crumb:1)|$(X 11-sub files_crumb:2)"
adb shell input keyevent KEYCODE_HOME; sleep 1.5
to_menu 12
MARK="$(ring_mark)"
T 12-menu rec_menu:location 3; D 13-files; S 13-files
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/13-ring.txt"
assert_eq "Files already open: FilesActivity resumed" "$FILES_ACTIVITY" "$(top_activity)"
assert_eq "…it shows Recordings with the take" "Recordings yes" "$(last_crumb 13-files) $(H 13-files files_row:qa-take.m4a)"
assert_contains "…the line again" "$OPEN_LINE" "$SL"
absent_in "…on the RUNNING activity: no FilesActivity created line (onNewIntent)" "FilesActivity created" "$SL"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
assert_eq "ONE Back returns to Voice Recorder, not to sub" "$REC" "$(top_activity)"

# ------------------------------------------------------------------------------------------------- a path outside
log "--- a path extra outside every mounted volume"
c6; ensure_start
MARK="$(ring_mark)"
adb shell am start -W -n "$FILES_ACTIVITY" --es path /data/data/app.tileshell >/dev/null 2>&1; sleep 2; D 20-bad; S 20-bad
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/20-ring.txt"
WHY="$(printf '%s\n' "$SL" | grep -o '\[files\] open ignored: .*' | head -1)"
record "path /data/data/app.tileshell: the line" "$WHY"
assert_contains "path /data/data/app.tileshell: [files] open ignored: <why>" "[files] open ignored: " "$WHY"
assert_ne "…the reason is not empty" "[files] open ignored: " "$WHY"
assert_eq "…the page Files would show without the extra (a cold start: Recent, the pane open)" "Recent yes $FILES_ACTIVITY" "$(X 20-bad files_crumb:0) $(H 20-bad files_pane) $(top_activity)"
absent_in "…nothing of the private folder is listed (no [files] list /data line)" "[files] list /data" "$SL"
absent_in "…and no [files] open at line" "[files] open at " "$SL"
MARK="$(ring_mark)"
adb shell am start -W -n "$FILES_ACTIVITY" --es path "$SD/../../data/data/app.tileshell" >/dev/null 2>&1; sleep 2; D 21-traversal
SL="$(ring_since "$MARK")"; printf '%s\n' "$SL" | grep -F '[files]' > "$ROW_DIR/21-ring.txt"
WHY="$(printf '%s\n' "$SL" | grep -o '\[files\] open ignored: .*' | head -1)"
record "a .. traversal on the running Files: the line" "$WHY"
assert_contains "a .. traversal (the running Files): [files] open ignored: <why>" "[files] open ignored: " "$WHY"
absent_in "…nothing of the private folder is listed" "[files] list /data" "$SL"
assert_eq "…the page is unchanged (Recent)" "Recent" "$(X 21-traversal files_crumb:0)"

# ------------------------------------------------------------------------------------------------- restore
log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6
adb shell rm -f /sdcard/Recordings/qa-take.m4a; media_scan
trap - EXIT
assert_eq "restore: no qa-take row is left in the audio collection" "0" "$(q "content query --uri content://media/external/audio/media --projection _data" | grep -c 'qa-take')"
ensure_start
files_down
end_state
row_end

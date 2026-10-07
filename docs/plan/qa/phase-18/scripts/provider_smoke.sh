#!/usr/bin/env bash
# Phase 18, the lead's smoke of the review fix M6 before the rows run on a new build: does an open THROUGH the shell's
# FileProvider still work on FUSE now that every open compares the opened descriptor (/proc/self/fd/<n>) with the checked
# canonical path? An image in a .nomedia folder has no MediaStore row, so Files hands the viewer a provider URI. If the
# two paths disagree on this image the line "share refused: not the file that was checked" appears and nothing opens.
#
#   provider_smoke.sh        (not a graded row: E6 and E7 are; this only saves a wasted run of them)
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p18.sh"

row_begin PROVIDER_SMOKE "a provider open still works after the descriptor check (review fix M6)"
baseline_start
files_up

MARK="$(ring_mark)"
adb shell am start -n app.tileshell/.files.FilesActivity --es path /storage/emulated/0/QA-Files/hidden >/dev/null
sleep 3
dump_ui "$ROW_DIR/01-hidden.xml"
assert_eq "the hidden folder lists qa-hidden.png" "yes" "$(has_node "$ROW_DIR/01-hidden.xml" "files_row:qa-hidden.png")"
tap_node "$ROW_DIR/01-hidden.xml" "files_row:qa-hidden.png"
sleep 4
SLICE="$(ring_since "$MARK")"
printf '%s\n' "$SLICE" > "$ROW_DIR/ring-launcher.txt"
assert_eq "the open went through the provider" "1" "$(printf '%s\n' "$SLICE" | grep -c 'open /storage/emulated/0/QA-Files/hidden/qa-hidden.png via provider')"
assert_eq "no descriptor-mismatch refusal" "0" "$(printf '%s\n' "$SLICE" | grep -c 'not the file that was checked')"
assert_eq "no share refusal at all" "0" "$(printf '%s\n' "$SLICE" | grep -c 'share refused')"
assert_eq "the viewer is on top" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
adb exec-out screencap -p > "$ROW_DIR/02-viewer.png"
record "the viewer's screenshot" "$ROW_DIR/02-viewer.png"

c6
files_down
row_end

#!/usr/bin/env bash
# Phase 18 E16 — App Shortcuts (phase 11 Q1's standing rule; C-8). The SD card half is E2's.
#
#   dumpsys    `dumpsys shortcut` lists `files_device`, `files_recent`, `files_bin` as MANIFEST shortcuts with ranks
#              0, 1, 2, hung on FilesActivity, and no `files_sdcard` (no public volume is up: asserted first).
#   burst      from the phase baseline (the Files tile pinned): hold → exactly 3 satellites, labels "This Device",
#              "Recent", "Recycle Bin" in rank order (`quick_sat_label:0..2`; `quick_sat:3` absent), and the slice from a
#              MARK before the hold holds `[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown:
#              files_device,files_recent,files_bin)`.
#   each       tap each satellite → FilesActivity resumed on that page with the pane CLOSED: This Device —
#              `files_crumb:0` "This Device"; Recent with nothing opened — `files_recent_empty` reading exactly "You
#              haven't opened any files recently."; Recycle Bin — `files_bin` present. `c6` between holds (C-6).
#   opened     the row opens its own `recent/r1.png` through Files (the viewer on top, `[files] recent add <path>`), `c6`,
#              hold, Recent → `files_recent_row:r1.png`, no empty line.
#
# "Nothing opened": the Recent store (files/files-recent.json, the app's private file) is saved at the start; the row
# asserts the page is empty before it opens anything — were it not (another row's entry for a file outside QA-Files),
# that is a precondition FAIL, not something this row repairs. At the end the store's saved bytes are written back with
# the shell stopped (RV12), so the row leaves no entry of its own.
#
# Changes on the device: QA-Files (files_up / files_down), the Recent store (restored). No wipe, no root.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p18.sh"
. "$HERE/p18_a.sh"
keep_earlier_run E16
row_begin E16 "App Shortcuts: the Files tile's three satellites and their pages"
LC0="$(lc_mark)"
EMPTY="You haven't opened any files recently."
QLINE="[quick] shortcuts for app.tileshell/.files.FilesActivity/0: 3 (3 shown: files_device,files_recent,files_bin)"
recent_store() { adb shell run-as app.tileshell cat files/files-recent.json 2>/dev/null | tr -d '\r'; }
baseline_start
files_up || { row_end; exit 1; }
c6; ensure_start
recent_store > "$ROW_DIR/recent-store-at-begin.json"
record "the Recent store at the start (files/files-recent.json)" "$(cut -c1-200 "$ROW_DIR/recent-store-at-begin.json")"
assert_eq "precondition: no public volume" "" "$(q 'sm list-volumes public' | xargs)"

# ------------------------------------------------------------------------------------------------- dumpsys shortcut
log "--- dumpsys shortcut"
adb shell dumpsys shortcut > "$ROW_DIR/00-dumpsys-shortcut.txt"
shortcuts | grep ' app.tileshell/app.tileshell.files.FilesActivity$' | sort -k2,2 > "$ROW_DIR/00-shortcuts.txt"
note "FilesActivity's shortcuts: $(tr '\n' ';' < "$ROW_DIR/00-shortcuts.txt")"
assert_eq "dumpsys shortcut: FilesActivity's ids and ranks" "files_device rank=0|files_recent rank=1|files_bin rank=2|" "$(awk '{ printf "%s %s|", $1, $2 }' "$ROW_DIR/00-shortcuts.txt")"
assert_eq "dumpsys shortcut: all three are manifest shortcuts (flag Man), none dynamic (Dyn)" "3 0" "$(grep -c 'flags=\[[^]]*Man' "$ROW_DIR/00-shortcuts.txt") $(grep -c 'flags=\[[^]]*Dyn' "$ROW_DIR/00-shortcuts.txt")"
assert_eq "dumpsys shortcut: no files_sdcard" "0" "$(shortcuts | grep -c files_sdcard)"

# ------------------------------------------------------------------------------------------------- the burst
log "--- the burst"
burst "$FILES_TILE" 01-burst
assert_eq "hold the Files tile: 3 satellites" "3" "$(sat_count 01-burst)"
assert_eq "…labels in rank order" "This Device|Recent|Recycle Bin|" "$(sat_labels 01-burst)"
assert_eq "…quick_sat:3 absent" "no" "$(H 01-burst quick_sat:3)"
assert_eq "…the [quick] line in the slice from the MARK before the hold" "$QLINE" "$(qline "$BMARK")"

# ------------------------------------------------------------------------------------------------- each satellite
page() { # sat-index name
  c6
  burst "$FILES_TILE" "02-$2-burst"
  assert_eq "$2: the burst again holds 3 satellites and the line" "3 $QLINE" "$(sat_count "02-$2-burst") $(qline "$BMARK")"
  PMARK="$(ring_mark)"
  T "02-$2-burst" "quick_sat:$1" 3; D "03-$2"; S "03-$2"
  ring_since "$PMARK" | grep -F '[files]' > "$ROW_DIR/03-$2-ring.txt"
  assert_eq "$2: FilesActivity resumed" "$FILES_ACTIVITY" "$(top_activity)"
  assert_eq "$2: the pane is closed (no files_pane)" "no" "$(H "03-$2" files_pane)"
}
log "--- This Device"
page 0 device
assert_eq "This Device: files_crumb:0" "This Device" "$(X 03-device files_crumb:0)"
assert_contains "This Device: the volume's root was listed" "[files] list $SD: " "$(cat "$ROW_DIR/03-device-ring.txt")"
log "--- Recent, nothing opened"
page 1 recent
assert_eq "Recent, nothing opened: files_recent_empty reads exactly" "$EMPTY" "$(X 03-recent files_recent_empty)"
assert_eq "…no files_recent_row: node; the crumb reads Recent" "|Recent" "$(ids_of 03-recent files_recent_row: | xargs)|$(X 03-recent files_crumb:0)"
assert_contains "…[files] recent: 0" "[files] recent: 0" "$(cat "$ROW_DIR/03-recent-ring.txt")"
log "--- Recycle Bin"
page 2 bin
assert_eq "Recycle Bin: files_bin present" "yes" "$(H 03-bin files_bin)"
record "Recycle Bin: the crumb / the note" "$(X 03-bin files_crumb:0) / $(X 03-bin files_bin_note)"

# ------------------------------------------------------------------------------------------------- after an open
log "--- Recent after the row opens r1.png through Files"
c6; ensure_start
files_at "$QF/recent"; D 04-folder
MARK="$(ring_mark)"
T 04-folder files_row:r1.png 2.5
assert_eq "open r1.png through Files: the shell's viewer is on top" "app.tileshell/.photos.ViewerActivity" "$(top_activity)"
assert_contains "open r1.png: [files] recent add <path>" "[files] recent add $QF/recent/r1.png" "$(ring_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 1.2
page 1 recent2
assert_eq "Recent after the open: files_recent_row:r1.png, and only it" "r1.png" "$(ids_of 03-recent2 files_recent_row: | xargs)"
assert_eq "…and no empty line" "no" "$(H 03-recent2 files_recent_empty)"
assert_contains "…[files] recent: 1" "[files] recent: 1" "$(cat "$ROW_DIR/03-recent2-ring.txt")"

# ------------------------------------------------------------------------------------------------- restore
log "--- restore"
assert_eq "no AndroidRuntime line names the shell since the row began" "0" "$(crash_since "$LC0")"
c6
adb shell am start -W -n com.android.settings/.Settings >/dev/null 2>&1; sleep 0.5
adb shell am force-stop app.tileshell; sleep 0.5
adb shell "run-as app.tileshell sh -c 'cat > files/files-recent.json'" < "$ROW_DIR/recent-store-at-begin.json"
adb shell am force-stop app.tileshell; sleep 0.5
assert_eq "restore: the Recent store holds its bytes from the start (RV12)" "$(cat "$ROW_DIR/recent-store-at-begin.json")" "$(recent_store)"
ensure_start
files_down
end_state
row_end

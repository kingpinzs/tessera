#!/usr/bin/env bash
# L13-2 overlay audit (fix planning, Jeremy's Q2 (c), 2026-09-25): every overlay drawn inside a swipeable page, probed
# the way l13_2_scope.sh probed the app list's band and jump grid — DOWN on the overlay, a 60-px sideways MOVE toward
# the pager's neighbour page, the page's bounds read with the finger down, UP. The two overlays the code read found
# beyond the app list's: (3) Music's letter jump grid (phase 10, drawn inside a page of `music_pivot`), and (4) Start's
# quick-action burst (phase 11, over page 0 of Start's pivot; it opens in edit mode). Setups are E4's (Music) and
# E7(c)'s (the burst: phase 11's fixtures and baseline, phase 02's baseline restored after). Runs on the installed APK.
#   usage: l13_2_audit.sh <label>
# LEAK = the page moved while the finger was down.
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
P01="$(cd "$P13/../../phase-01/scripts" && pwd)"
P11="$(cd "$P13/../../phase-11/scripts" && pwd)"
source "$P01/ui.sh"
source "$P01/music_lib.sh"

row_begin "L13-2-audit-$1" "L13-2 audit on $1: Music's jump grid and Start's quick burst under a sideways drag"
note "repro apk: ${REPRO_APK:-the build of HEAD} (the header's 'apk built' is the repo's own build, not necessarily this one)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"

# <name> <dumper> <page id> <x> <y> <dx>
drag_probe() {
  local name="$1" dumper="$2" page="$3" x="$4" y="$5" dx="$6" p0 p1 p2
  p0="$(bounds "$ROW_DIR/$name-open.xml" "$page")"
  adb shell input motionevent DOWN $x $y; sleep 0.6
  adb shell input motionevent MOVE $(( x + dx )) $y; sleep 0.6
  $dumper "$ROW_DIR/$name-moved.xml"; p1="$(bounds "$ROW_DIR/$name-moved.xml" "$page")"
  adb shell input motionevent UP $(( x + dx )) $y; sleep 1.5
  $dumper "$ROW_DIR/$name-up.xml"; p2="$(bounds "$ROW_DIR/$name-up.xml" "$page")"
  note "$name: $page open [$p0], finger moved $dx px [$p1], after UP [$p2]"
  assert_ne "$name: the page's bounds are in the dump" "" "$p0"
  record "$name: LEAK (the page moved with the finger down)" "$([ "$p0" != "$p1" ] && echo yes || echo no)"
}

# (3) Music's jump grid, on the songs pivot (albums, artists, SONGS, playlists: its right neighbour is playlists).
music_mute
music_fixtures
music_open
goto_pivot songs "$ROW_DIR/m-songs.xml"
HID="$(python3 - "$ROW_DIR/m-songs.xml" <<'EOF'
import re, sys
# The pager keeps a neighbour page composed, so both pages' headers are in the dump: take the first on screen.
for m in re.finditer(r'resource-id="(music_header:[^"]*)"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', open(sys.argv[1]).read()):
    if 0 <= int(m.group(2)) < 1080:
        print(m.group(1)); break
EOF
)"
assert_ne "(3) an on-screen letter header on the songs pivot" "" "$HID"
set -- $(bounds "$ROW_DIR/m-songs.xml" "$HID"); adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 1.5
dump_ui "$ROW_DIR/music-grid-open.xml"
assert_eq "(3) Music's jump grid is open" "yes" "$(has_node "$ROW_DIR/music-grid-open.xml" music_jump_grid)"
set -- $(bounds "$ROW_DIR/music-grid-open.xml" music_jump_grid); GX=$(( ($1 + $3) / 2 )); GY=$(( $4 - 150 ))
# The grid covers the songs list, so the list is not in the dump while it is open (run 1, kept in
# L13-2-audit-run1-driver-pageid/); the grid is drawn inside the pivot's page and moves with it, so its own bounds are read.
drag_probe music-grid dump_ui music_jump_grid $GX $GY -60
record "(3) Music's jump grid after UP" "$(has_node "$ROW_DIR/music-grid-up.xml" music_jump_grid)"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_HOME; sleep 3

# (4) Start's quick burst, E7(c)'s setup (the Seeding exception): phase 11's fixtures and baseline.
. "$P11/q.sh"
QA11="$QROOT/phase-11"
seed_fixtures
restore baseline_layout.json
qdump "$ROW_DIR/.rest.xml"
read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "tile:$A_KEY")"
hold "$FX" "$FY" 1.0
sleep 1
qdump "$ROW_DIR/burst-open.xml"
assert_eq "(4) the burst is open" "yes" "$(has_node "$ROW_DIR/burst-open.xml" quick_burst)"
set -- $(bounds "$ROW_DIR/burst-open.xml" quick_sat:0)
# E7(c)'s touch point (left + 10, top + 10); the move is toward the app list (page 1, to the left).
drag_probe burst qdump start_page $(( $1 + 10 )) $(( $2 + 10 )) -60
record "(4) the burst after UP" "$(has_node "$ROW_DIR/burst-up.xml" quick_burst)"
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
# Phase 02's baseline back (the Seeding exception's restore), with C-3's check, as E7 does.
MARK="$(ring_mark)"
layout_restore "$QROOT/phase-02/baseline_layout.json"
assert_absent "phase 02's baseline restored: no slot re-assigned (C-3)" "-> assigned" "$(ring_since "$MARK" | grep assignSlotOnce)"
show_start 3
row_end

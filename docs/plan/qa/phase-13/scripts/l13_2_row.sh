#!/usr/bin/env bash
# L13-2 regression row (fix plan review/2026-09-25-L13-2-fix-plan.md, "The gate"): every overlay drawn inside a pivot's
# page is modal — a gesture that starts on it never moves the pivot — and its items keep one press rule (held while
# the finger stays on the item, ended for good when it leaves, run only by a lift on it). Run on the pre-fix build (must
# fail) and on the fix's build (must pass), on one driver blob. Overlays: the app list's hold band (phase 02: its item
# and its scrim), the app list's letter jump grid (phase 01: a cell and the scrim), Music's letter jump grid (phase 10: a
# cell and the scrim). The other half: once each overlay is closed (a tap off it, and Back) the same sideways swipe DOES
# move its pivot. The quick burst (phase 11, the control) is l13_2_audit.sh's, run before and after beside this row.
#   usage: l13_2_row.sh <label>
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
P01="$(cd "$P13/../../phase-01/scripts" && pwd)"
source "$P01/ui.sh"
source "$P01/music_lib.sh"

row_begin "L13-2-row-$1" "L13-2 row on $1: an open overlay inside a pivot page holds the pivot still; its items keep one press rule"
note "apk under test: ${REPRO_APK:-the build of HEAD} (the header's 'apk built' is the repo's own build, not necessarily this one)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"

W=1080
FAST=120   # ms for a fast swipe: one `input swipe`, so the pager sees a fling's velocity

pivot_lines() { grep -cF '[motion] pivot' "$1"; }
settled_lines() { grep -cF 'pivot settled on' "$1"; }
pins() { grep -cF 'pin to Start' "$1"; }

# ---------------------------------------------------------------- Start's app list page
open_applist() { show_start 6; to_app_list 3; dump_ui "$ROW_DIR/$1"; }
open_band() { # <prefix>: the hold band over the third row; sets TX TY (the item's centre) SY (a scrim point) P0
  open_applist "$1-list.xml"
  local rowid; rowid="$(grep -o 'resource-id="applist_row:[^"]*"' "$ROW_DIR/$1-list.xml" | sed -n 3p | sed 's/resource-id="//; s/"$//')"
  set -- "$1" $(bounds "$ROW_DIR/$1-list.xml" "$rowid")
  adb shell input swipe $(( ($2 + $4) / 2 )) $(( ($3 + $5) / 2 )) $(( ($2 + $4) / 2 )) $(( ($3 + $5) / 2 )) 1000; sleep 1.5
  dump_ui "$ROW_DIR/$1-band.xml"
  assert_eq "$1: the hold band is open" "yes" "$(has_node "$ROW_DIR/$1-band.xml" applist_menu_pin)"
  set -- "$1" $(bounds "$ROW_DIR/$1-band.xml" applist_menu_pin); TX=$(( ($2 + $4) / 2 )); TY=$(( ($3 + $5) / 2 ))
  set -- "$1" $(bounds "$ROW_DIR/$1-band.xml" applist_menu); SY=$(( $5 + 300 ))
  P0="$(bounds "$ROW_DIR/$1-band.xml" app_list)"
}
# the other half on Start: a sideways swipe toward Start moves the pivot (start_page back at the origin, a settle logged)
pivot_moves_to_start() { # <name>
  local mark; mark="$(ring_mark)"
  adb shell input swipe 150 1200 950 1200 250; sleep 2
  dump_ui "$ROW_DIR/$1-start.xml"; ring_since "$mark" > "$ROW_DIR/$1-start-slice.txt"
  assert_eq "$1: the swipe moves the pivot to Start" "0 0 1080 2196" "$(bounds "$ROW_DIR/$1-start.xml" start_page)"
  assert_ne "$1: the pivot logged its settle" "0" "$(pivot_lines "$ROW_DIR/$1-start-slice.txt")"
}

log "--- (B1) the band's item: a 60-px move within it, then off it; the page holds, the press holds then ends ---"
open_band b1
screencap "$ROW_DIR/b1-U.png"; MARK="$(ring_mark)"
adb shell input motionevent DOWN $TX $TY; sleep 0.6
adb shell input motionevent MOVE $(( TX + 60 )) $TY; sleep 0.6
screencap "$ROW_DIR/b1-M.png"; dump_ui "$ROW_DIR/b1-moved.xml"
adb shell input motionevent MOVE $(( TX + 60 )) $(( TY + 300 )); sleep 0.6
screencap "$ROW_DIR/b1-A.png"
adb shell input motionevent UP $(( TX + 60 )) $(( TY + 300 )); sleep 1.5
dump_ui "$ROW_DIR/b1-up.xml"; ring_since "$MARK" > "$ROW_DIR/b1-slice.txt"
assert_eq "(B1) the page holds still with the finger moved on the item" "$P0" "$(bounds "$ROW_DIR/b1-moved.xml" app_list)"
assert_eq "(B1) and after UP" "$P0" "$(bounds "$ROW_DIR/b1-up.xml" app_list)"
assert_eq "(B1) no pivot settle logged" "0" "$(pivot_lines "$ROW_DIR/b1-slice.txt")"
# The far pixel: 460 px right of the touch (400 after the move), on the item's centre line, clear of its left-set text
# and of both lights (r + 2 epx = 126 px). M: the ROW_PRESS_ALPHA held look; A: U, the press ended on leaving.
FX=$(( TX + 460 )); [ $FX -gt $(( W - 20 )) ] && FX=$(( W - 20 ))
assert_within "(B1) the press holds through the move within the item (M = U + 0.15 x (255 - U) at the far pixel)" 0 "$(python3 "$P13/lights.py" at "$ROW_DIR/b1-U.png" "$ROW_DIR/b1-M.png" p15 $FX $TY 0 | cut -d' ' -f1)" 4
assert_within "(B1) the press ends when the finger leaves the item (A = U at the far pixel)" 0 "$(python3 "$P13/lights.py" at "$ROW_DIR/b1-U.png" "$ROW_DIR/b1-A.png" u $FX $TY 0 | cut -d' ' -f1)" 2
assert_eq "(B1) the lift off the item pins nothing" "0" "$(pins "$ROW_DIR/b1-slice.txt")"
assert_eq "(B1) the band is still open after UP" "yes" "$(has_node "$ROW_DIR/b1-up.xml" applist_menu)"

log "--- (B2) the band's item: a fast diagonal swipe that leaves the item ---"
MARK="$(ring_mark)"
adb shell input swipe $TX $TY $(( TX + 500 )) $(( TY + 300 )) $FAST; sleep 1.5
dump_ui "$ROW_DIR/b2-up.xml"; ring_since "$MARK" > "$ROW_DIR/b2-slice.txt"
assert_eq "(B2) the page is where it was" "$P0" "$(bounds "$ROW_DIR/b2-up.xml" app_list)"
assert_eq "(B2) no pivot settle logged" "0" "$(pivot_lines "$ROW_DIR/b2-slice.txt")"
assert_eq "(B2) nothing pinned" "0" "$(pins "$ROW_DIR/b2-slice.txt")"
assert_eq "(B2) the band is still open" "yes" "$(has_node "$ROW_DIR/b2-up.xml" applist_menu)"

log "--- (B3) the band's scrim: a 60-px move, then a fast swipe ---"
MARK="$(ring_mark)"
adb shell input motionevent DOWN 300 $SY; sleep 0.6
adb shell input motionevent MOVE 360 $SY; sleep 0.6
dump_ui "$ROW_DIR/b3-moved.xml"
adb shell input motionevent UP 360 $SY; sleep 1.5
dump_ui "$ROW_DIR/b3-up.xml"
assert_eq "(B3) the page holds still with the finger moved on the scrim" "$P0" "$(bounds "$ROW_DIR/b3-moved.xml" app_list)"
assert_eq "(B3) a drag on the scrim leaves the band open" "yes" "$(has_node "$ROW_DIR/b3-up.xml" applist_menu)"
adb shell input swipe 200 $SY 800 $SY $FAST; sleep 1.5
dump_ui "$ROW_DIR/b3-fast.xml"; ring_since "$MARK" > "$ROW_DIR/b3-slice.txt"
assert_eq "(B3) after a fast swipe on the scrim the page is where it was" "$P0" "$(bounds "$ROW_DIR/b3-fast.xml" app_list)"
assert_eq "(B3) no pivot settle logged" "0" "$(pivot_lines "$ROW_DIR/b3-slice.txt")"
assert_eq "(B3) the band is still open" "yes" "$(has_node "$ROW_DIR/b3-fast.xml" applist_menu)"

log "--- (B4) the other half: a tap off the band closes it, then the pivot moves ---"
# A fresh band: on a build with the defect (B2)/(B3) have already swung the pivot to Start, and the tap would land on
# Start instead of the band's scrim (run 1, kept in L13-2-row-before1-driver-cascade/).
open_band b4
adb shell input tap 300 $SY; sleep 1.5
dump_ui "$ROW_DIR/b4-closed.xml"
assert_eq "(B4) a tap off the band closes it" "no" "$(has_node "$ROW_DIR/b4-closed.xml" applist_menu)"
pivot_moves_to_start b4
log "--- (B5) the other half: Back closes the band, then the pivot moves ---"
open_band b5
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/b5-closed.xml"
assert_eq "(B5) Back closes the band" "no" "$(has_node "$ROW_DIR/b5-closed.xml" applist_menu)"
pivot_moves_to_start b5

# ---------------------------------------------------------------- the app list's jump grid
open_grid() { # <prefix>: sets TGT (a live letter below the first), CX CY (its cell's centre), GY (a scrim point), P0
  open_applist "$1-list.xml"
  local hdr; hdr="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/$1-list.xml" | sed 's/resource-id="applist_header://; s/"$//')"
  TGT="$(echo "$hdr" | sed -n 2p)"
  set -- "$1" $(bounds "$ROW_DIR/$1-list.xml" "applist_header:$(echo "$hdr" | sed -n 1p)")
  adb shell input tap $(( ($2 + $4) / 2 )) $(( ($3 + $5) / 2 )); sleep 1.5
  dump_ui "$ROW_DIR/$1-grid.xml"
  assert_eq "$1: the jump grid is open" "yes" "$(has_node "$ROW_DIR/$1-grid.xml" jump_grid)"
  set -- "$1" $(bounds "$ROW_DIR/$1-grid.xml" "jump_cell:$TGT"); CX=$(( ($2 + $4) / 2 )); CY=$(( ($3 + $5) / 2 ))
  set -- "$1" $(bounds "$ROW_DIR/$1-grid.xml" jump_grid); GY=$(( $5 - 150 ))
  P0="$(bounds "$ROW_DIR/$1-grid.xml" app_list)"
}
first_header() { grep -o 'resource-id="applist_header:[^"]*"' "$1" | head -1 | sed 's/resource-id="applist_header://; s/"$//'; }

log "--- (J1) a cell: a 30-px move within it, lifted on it, picks the letter; the page holds ---"
open_grid j1
note "(J1) target letter [$TGT], its cell at $CX,$CY"
assert_ne "(J1) a second letter group exists to jump to" "" "$TGT"
MARK="$(ring_mark)"
adb shell input motionevent DOWN $CX $CY; sleep 0.6
adb shell input motionevent MOVE $(( CX + 30 )) $CY; sleep 0.6
dump_ui "$ROW_DIR/j1-moved.xml"
adb shell input motionevent UP $(( CX + 30 )) $CY; sleep 1.5
dump_ui "$ROW_DIR/j1-up.xml"; ring_since "$MARK" > "$ROW_DIR/j1-slice.txt"
assert_eq "(J1) the page holds still with the finger moved on the cell" "$P0" "$(bounds "$ROW_DIR/j1-moved.xml" app_list)"
assert_eq "(J1) the lift on the cell picks: the grid closes" "no" "$(has_node "$ROW_DIR/j1-up.xml" jump_grid)"
assert_contains "(J1) and the list jumps to the letter" "jump to $TGT" "$(cat "$ROW_DIR/j1-slice.txt")"
assert_eq "(J1) no pivot settle logged" "0" "$(pivot_lines "$ROW_DIR/j1-slice.txt")"

log "--- (J2) a cell: the finger leaves it, the lift picks nothing ---"
open_grid j2
FIRST0="$(first_header "$ROW_DIR/j2-list.xml")"
MARK="$(ring_mark)"
adb shell input motionevent DOWN $CX $CY; sleep 0.6
adb shell input motionevent MOVE $(( CX + 250 )) $CY; sleep 0.6
dump_ui "$ROW_DIR/j2-moved.xml"
adb shell input motionevent UP $(( CX + 250 )) $CY; sleep 1.5
dump_ui "$ROW_DIR/j2-up.xml"; ring_since "$MARK" > "$ROW_DIR/j2-slice.txt"
assert_eq "(J2) the page holds still" "$P0" "$(bounds "$ROW_DIR/j2-moved.xml" app_list)"
assert_eq "(J2) nothing picked: the grid stays open" "yes" "$(has_node "$ROW_DIR/j2-up.xml" jump_grid)"
assert_absent "(J2) no jump" "jump to" "$(cat "$ROW_DIR/j2-slice.txt")"

log "--- (J3) a cell: a fast diagonal swipe that leaves it ---"
MARK="$(ring_mark)"
adb shell input swipe $CX $CY $(( CX + 500 )) $(( CY + 300 )) $FAST; sleep 1.5
dump_ui "$ROW_DIR/j3-up.xml"; ring_since "$MARK" > "$ROW_DIR/j3-slice.txt"
assert_eq "(J3) the page is where it was" "$P0" "$(bounds "$ROW_DIR/j3-up.xml" app_list)"
assert_eq "(J3) no pivot settle logged" "0" "$(pivot_lines "$ROW_DIR/j3-slice.txt")"
assert_eq "(J3) the grid is still open" "yes" "$(has_node "$ROW_DIR/j3-up.xml" jump_grid)"

log "--- (J4) the grid's scrim: a 60-px move, then a fast swipe ---"
MARK="$(ring_mark)"
adb shell input motionevent DOWN 540 $GY; sleep 0.6
adb shell input motionevent MOVE 600 $GY; sleep 0.6
dump_ui "$ROW_DIR/j4-moved.xml"
adb shell input motionevent UP 600 $GY; sleep 1.5
dump_ui "$ROW_DIR/j4-up.xml"
assert_eq "(J4) the page holds still with the finger moved on the scrim" "$P0" "$(bounds "$ROW_DIR/j4-moved.xml" app_list)"
assert_eq "(J4) a drag on the scrim leaves the grid open" "yes" "$(has_node "$ROW_DIR/j4-up.xml" jump_grid)"
adb shell input swipe 200 $GY 800 $GY $FAST; sleep 1.5
dump_ui "$ROW_DIR/j4-fast.xml"; ring_since "$MARK" > "$ROW_DIR/j4-slice.txt"
assert_eq "(J4) after a fast swipe on the scrim the page is where it was" "$P0" "$(bounds "$ROW_DIR/j4-fast.xml" app_list)"
assert_eq "(J4) no pivot settle logged" "0" "$(pivot_lines "$ROW_DIR/j4-slice.txt")"
assert_eq "(J4) the grid is still open" "yes" "$(has_node "$ROW_DIR/j4-fast.xml" jump_grid)"

log "--- (J5) the other half: a tap off the cells closes the grid, then the pivot moves ---"
open_grid j5   # a fresh grid, as (B4)
adb shell input tap 540 $GY; sleep 1.5
dump_ui "$ROW_DIR/j5-closed.xml"
assert_eq "(J5) a tap on the scrim closes the grid" "no" "$(has_node "$ROW_DIR/j5-closed.xml" jump_grid)"
pivot_moves_to_start j5
log "--- (J6) the other half: Back closes the grid, then the pivot moves ---"
open_grid j6
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/j6-closed.xml"
assert_eq "(J6) Back closes the grid" "no" "$(has_node "$ROW_DIR/j6-closed.xml" jump_grid)"
pivot_moves_to_start j6
adb shell input keyevent KEYCODE_HOME; sleep 2

# ---------------------------------------------------------------- Music's jump grid (songs pivot; right neighbour: playlists)
music_mute
music_fixtures
music_grid() { # <prefix>: sets MT (a live letter below the first), CX CY, GY, G0 (the grid's bounds: it moves with its page)
  music_open
  goto_pivot songs "$ROW_DIR/$1-songs.xml"
  local hdr
  # The pager keeps a neighbour page composed: only the on-screen headers count.
  hdr="$(python3 - "$ROW_DIR/$1-songs.xml" <<'EOF'
import re, sys
for m in re.finditer(r'resource-id="music_header:([^"]*)"[^>]*bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', open(sys.argv[1]).read()):
    if 0 <= int(m.group(2)) < 1080:
        print(m.group(1))
EOF
)"
  MT="$(echo "$hdr" | sed -n 2p)"
  set -- "$1" $(bounds "$ROW_DIR/$1-songs.xml" "music_header:$(echo "$hdr" | sed -n 1p)")
  adb shell input tap $(( ($2 + $4) / 2 )) $(( ($3 + $5) / 2 )); sleep 1.5
  dump_ui "$ROW_DIR/$1-grid.xml"
  assert_eq "$1: Music's jump grid is open" "yes" "$(has_node "$ROW_DIR/$1-grid.xml" music_jump_grid)"
  set -- "$1" $(bounds "$ROW_DIR/$1-grid.xml" "music_jump:$MT"); CX=$(( ($2 + $4) / 2 )); CY=$(( ($3 + $5) / 2 ))
  set -- "$1" $(bounds "$ROW_DIR/$1-grid.xml" music_jump_grid); GY=$(( $5 - 150 ))
  G0="$(bounds "$ROW_DIR/$1-grid.xml" music_jump_grid)"
}
# the other half in Music: a sideways swipe toward playlists settles the pivot there
pivot_moves_to_playlists() { # <name>
  local mark; mark="$(ring_mark)"
  adb shell input swipe 950 1500 150 1500 250; sleep 2
  ring_since "$mark" > "$ROW_DIR/$1-pl-slice.txt"
  assert_contains "$1: the swipe settles Music's pivot on playlists" "pivot settled on playlists" "$(cat "$ROW_DIR/$1-pl-slice.txt")"
}

log "--- (M1) a Music cell: a 30-px move within it, lifted on it, picks; the page holds ---"
music_grid m1
note "(M1) target letter [$MT], its cell at $CX,$CY"
assert_ne "(M1) a second letter group exists to jump to" "" "$MT"
MARK="$(ring_mark)"
adb shell input motionevent DOWN $CX $CY; sleep 0.6
adb shell input motionevent MOVE $(( CX + 30 )) $CY; sleep 0.6
dump_ui "$ROW_DIR/m1-moved.xml"
adb shell input motionevent UP $(( CX + 30 )) $CY; sleep 1.5
dump_ui "$ROW_DIR/m1-up.xml"; ring_since "$MARK" > "$ROW_DIR/m1-slice.txt"
assert_eq "(M1) the page holds still with the finger moved on the cell" "$G0" "$(bounds "$ROW_DIR/m1-moved.xml" music_jump_grid)"
# Nothing else closes the grid on this gesture: the cell took the down, so it is not a tap off the cells.
assert_eq "(M1) the lift on the cell picks: the grid closes" "no" "$(has_node "$ROW_DIR/m1-up.xml" music_jump_grid)"
assert_eq "(M1) no pivot settle logged" "0" "$(settled_lines "$ROW_DIR/m1-slice.txt")"

log "--- (M2) a Music cell: the finger leaves it, the lift picks nothing ---"
music_grid m2
adb shell input motionevent DOWN $CX $CY; sleep 0.6
adb shell input motionevent MOVE $(( CX + 250 )) $CY; sleep 0.6
dump_ui "$ROW_DIR/m2-moved.xml"
adb shell input motionevent UP $(( CX + 250 )) $CY; sleep 1.5
dump_ui "$ROW_DIR/m2-up.xml"
assert_eq "(M2) the page holds still" "$G0" "$(bounds "$ROW_DIR/m2-moved.xml" music_jump_grid)"
assert_eq "(M2) nothing picked: the grid stays open" "yes" "$(has_node "$ROW_DIR/m2-up.xml" music_jump_grid)"

log "--- (M3) a Music cell: a fast diagonal swipe that leaves it ---"
MARK="$(ring_mark)"
adb shell input swipe $CX $CY $(( CX + 500 )) $(( CY + 300 )) $FAST; sleep 1.5
dump_ui "$ROW_DIR/m3-up.xml"; ring_since "$MARK" > "$ROW_DIR/m3-slice.txt"
assert_eq "(M3) the page is where it was" "$G0" "$(bounds "$ROW_DIR/m3-up.xml" music_jump_grid)"
assert_eq "(M3) no pivot settle logged" "0" "$(settled_lines "$ROW_DIR/m3-slice.txt")"

log "--- (M4) Music's grid scrim: a 60-px move, then a fast swipe ---"
MARK="$(ring_mark)"
adb shell input motionevent DOWN 540 $GY; sleep 0.6
adb shell input motionevent MOVE 480 $GY; sleep 0.6
dump_ui "$ROW_DIR/m4-moved.xml"
adb shell input motionevent UP 480 $GY; sleep 1.5
dump_ui "$ROW_DIR/m4-up.xml"
assert_eq "(M4) the page holds still with the finger moved on the scrim" "$G0" "$(bounds "$ROW_DIR/m4-moved.xml" music_jump_grid)"
assert_eq "(M4) a drag on the scrim leaves the grid open" "yes" "$(has_node "$ROW_DIR/m4-up.xml" music_jump_grid)"
adb shell input swipe 900 $GY 200 $GY $FAST; sleep 1.5
dump_ui "$ROW_DIR/m4-fast.xml"; ring_since "$MARK" > "$ROW_DIR/m4-slice.txt"
assert_eq "(M4) after a fast swipe on the scrim the page is where it was" "$G0" "$(bounds "$ROW_DIR/m4-fast.xml" music_jump_grid)"
assert_eq "(M4) no pivot settle logged" "0" "$(settled_lines "$ROW_DIR/m4-slice.txt")"

log "--- (M5) the other half: a tap off the cells closes the grid, then the pivot moves ---"
music_grid m5
adb shell input tap 540 $GY; sleep 1.5
dump_ui "$ROW_DIR/m5-closed.xml"
assert_eq "(M5) a tap on the scrim closes the grid" "no" "$(has_node "$ROW_DIR/m5-closed.xml" music_jump_grid)"
pivot_moves_to_playlists m5
log "--- (M6) the other half: Back closes the grid, then the pivot moves ---"
music_grid m6
adb shell input keyevent KEYCODE_BACK; sleep 1.5
dump_ui "$ROW_DIR/m6-closed.xml"
assert_eq "(M6) Back closes the grid" "no" "$(has_node "$ROW_DIR/m6-closed.xml" music_jump_grid)"
pivot_moves_to_playlists m6

adb shell input keyevent KEYCODE_HOME; sleep 2
show_start 3
row_end

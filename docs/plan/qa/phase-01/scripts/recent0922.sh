#!/usr/bin/env bash
# Jeremy, 2026-09-22, with a photo of the empty band above the bottom tile row:
#   "the last active app goes right above the bottom row in this black space"
#
# The promotion already existed (INDEX Change Log 2026-09-21 item 7) but moved the tile to the END OF
# THE GRID ORDER, on the assumption that the end of the grid is the row above the bottom row. It is
# not: the grid ends wherever its tiles end, so on a Start that does not fill the screen the tile
# landed part-way up the page and that band stayed black. This row measures the band itself.
. "$(dirname "$0")/lib.sh"
source "$(dirname "$0")/ui.sh"

show_start() { adb shell am force-stop $PKG; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 5; }
come_back()  { adb shell input keyevent KEYCODE_HOME; sleep 4; }
count_id()   { grep -o "resource-id=\"$2\"" "$1" | wc -l | tr -d ' '; }

row_begin RECENT0922 "the last opened app sits in the band directly above the bottom tile row"

# ---- nothing opened yet: the band is empty, exactly as it was before ------------------------------
# A promotion read back off disk would be a lie the first time Start drew, so RecentApp is in memory
# only and a force-stopped shell comes back with nothing promoted.
show_start
dump "$ROW_DIR/fresh.xml"
adb exec-out screencap -p > "$ROW_DIR/fresh.png"
assert_eq "a freshly started shell promotes nothing" no "$(has_node "$ROW_DIR/fresh.xml" recent_app_row)"
BROWSER_HOME="$(bounds "$ROW_DIR/fresh.xml" tile:slot:BROWSER)"
note "the Browser tile starts at [$BROWSER_HOME]"
assert_ne "the Browser tile is on the grid to begin with" "" "$BROWSER_HOME"

# ---- open it from its tile, then come back --------------------------------------------------------
tap_id "$ROW_DIR/fresh.xml" tile:slot:BROWSER
sleep 5
adb shell dumpsys activity activities | grep -m1 'ResumedActivity' > "$ROW_DIR/opened.txt" 2>&1 || true
note "resumed after the tap: $(cat "$ROW_DIR/opened.txt")"
assert_absent "the tap actually left Start" "$PKG/.StartActivity" "$(cat "$ROW_DIR/opened.txt")"

come_back
dump "$ROW_DIR/promoted.xml"
adb exec-out screencap -p > "$ROW_DIR/promoted.png"
assert_eq "coming back promotes the app that was opened" yes "$(has_node "$ROW_DIR/promoted.xml" recent_app_row)"

# ---- and it is in the BAND, not at the end of the grid ---------------------------------------------
# Measured against the bottom tile row itself: the promoted tile's underside sits one gutter above the
# row's top edge. A gutter is a fraction of a small tile, so the check is "touching it, not merely
# somewhere below the grid" — which is precisely what the end-of-the-grid version failed.
TILE="$(bounds "$ROW_DIR/promoted.xml" tile:slot:BROWSER)"
DOCK="$(bounds "$ROW_DIR/promoted.xml" tile:dock:slot:PHONE)"
note "promoted Browser tile [$TILE] · bottom tile row [$DOCK]"
assert_ne "the promoted tile is on screen" "" "$TILE"
assert_ne "the bottom tile row is on screen" "" "$DOCK"
GAP="$(python3 - "$TILE" "$DOCK" <<'PY'
import sys
t = list(map(int, sys.argv[1].split()))
d = list(map(int, sys.argv[2].split()))
small = (d[3] - d[1]) / 1.5          # the bottom row is 1.5 small tiles high
print(int(d[1] - t[3]), int(small))  # dock top minus tile bottom, and a small tile for scale
PY
)"
set -- $GAP; GAP_PX="$1"; SMALL_PX="$2"
note "the promoted tile's underside is ${GAP_PX}px above the bottom tile row (a small tile is ${SMALL_PX}px)"
assert_eq "it sits ON the bottom tile row, one gutter clear of it" ok \
  "$([ "$GAP_PX" -gt 0 ] && [ "$GAP_PX" -lt $((SMALL_PX / 2)) ] && echo ok || echo "${GAP_PX}px of ${SMALL_PX}px")"
assert_eq "and it is drawn once, not left in the grid as well" 1 "$(count_id "$ROW_DIR/promoted.xml" tile:slot:BROWSER)"
assert_ne "so it is no longer where it was on the grid" "$BROWSER_HOME" "$TILE"

# ---- one of the three bottom apps is left alone ----------------------------------------------------
# Jeremy's original carve-out: "unless the last open app is the 3 bottom apps". A dock key is never
# found in the grid order, so this needs no special case — and opening one puts the previous tile back.
tap_id "$ROW_DIR/promoted.xml" tile:dock:slot:PHONE
sleep 5
come_back
dump "$ROW_DIR/dock.xml"
adb exec-out screencap -p > "$ROW_DIR/dock.png"
assert_eq "opening a bottom-row app promotes nothing" no "$(has_node "$ROW_DIR/dock.xml" recent_app_row)"
assert_eq "and the tile that was promoted is back exactly where it was" "$BROWSER_HOME" \
  "$(bounds "$ROW_DIR/dock.xml" tile:slot:BROWSER)"

diag start > "$ROW_DIR/diag.txt" 2>/dev/null || true
adb shell dumpsys activity service $PKG/.feeds.TileNotificationListener 2>/dev/null | grep -F '[start]' > "$ROW_DIR/diag.txt" || true
note "diagnostics: $(grep -c . "$ROW_DIR/diag.txt") line(s)"

row_end

#!/usr/bin/env bash
# E4 — the right shortcut launches, from the tile launch path: ShortcutActivity resumed showing the tapped id, the
# `ok` line ≥ Motion.EXIT_TOTAL_MS after the `launch` close (startShortcut runs after the Start exit, T11-24), the
# Start exit on a recording, and on return Start plain with the held tile promoted. Satellites 2, 0, 1, 3.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E4 "each satellite launches its own shortcut from the tile launch path; Start comes back plain, tile promoted"
seed_fixtures
restore baseline_layout.json
ids=(qa_one qa_two qa_three qa_four)

for i in 2 0 1 3; do
  log "--- satellite $i (${ids[$i]}) ---"
  qdump "$ROW_DIR/rest-$i.xml"
  read -r X Y <<< "$(center "$ROW_DIR/rest-$i.xml" "tile:$A_KEY")"
  hold "$X" "$Y" 1.0
  qdump "$ROW_DIR/burst-$i.xml"
  [ "$i" = 2 ] && { adb shell screenrecord --time-limit 4 /sdcard/Download/e4_exit.mp4 & REC=$!; sleep 0.8; }
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/burst-$i.xml" "quick_sat:$i"; sleep 3.5
  [ "$i" = 2 ] && { wait "$REC"; adb pull /sdcard/Download/e4_exit.mp4 "$ROW_DIR/exit-sat2.mp4" >/dev/null 2>&1; }
  assert_contains "sat $i: ShortcutActivity resumed" "$A_PKG/app.tileshell.testclient.ShortcutActivity" "$(resumed)"
  qdump "$ROW_DIR/launched-$i.xml"
  assert_eq "sat $i: shortcut_id reads ${ids[$i]}" "${ids[$i]}" "$(node_text "$ROW_DIR/launched-$i.xml" "$A_PKG:id/shortcut_id")"
  S="$(ring_since "$MARK")"
  assert_contains "sat $i: tap line ok" "[quick] tap satellite $i $A_PKG/${ids[$i]}: startShortcut ok" "$S"
  assert_contains "sat $i: burst closed: launch" "[quick] burst closed: launch" "$S"
  assert_contains "sat $i: the Start exit ran for the held tile" "[motion] start exit finished tile=$A_KEY" "$S"
  gap="$(echo "$S" | python3 -c '
import re, sys
closed = ok = None
for l in sys.stdin:
    w = re.search(r"wall=(\d+)", l)
    if not w: continue
    if "[quick] burst closed: launch" in l and closed is None: closed = int(w.group(1))
    if "startShortcut ok" in l and ok is None: ok = int(w.group(1))
print(ok - closed if ok and closed else -1)')"
  assert_within "sat $i: startShortcut ≥ 250 ms after the close (runs after the Start exit)" 400 "$gap" 150
  adb shell input keyevent KEYCODE_HOME; sleep 3
  qdump "$ROW_DIR/back-$i.xml"
  assert_eq "sat $i: back on Start, no edit_disc" no "$(grep -q 'resource-id="edit_disc' "$ROW_DIR/back-$i.xml" && echo yes || echo no)"
  assert_eq "sat $i: back on Start, no quick_burst" no "$(has_node "$ROW_DIR/back-$i.xml" quick_burst)"
  # RECENT0922's method: recent_app_row present, and the fixture's underside one gutter above the bottom tile row.
  assert_eq "sat $i: recent_app_row present" yes "$(has_node "$ROW_DIR/back-$i.xml" recent_app_row)"
  gap="$(python3 -c 'import sys; t=sys.argv[1].split(); d=sys.argv[2].split(); print(int(d[1])-int(t[3]) if len(t)==4 and len(d)==4 else -999)' \
    "$(bounds "$ROW_DIR/back-$i.xml" "tile:$A_KEY")" "$(bounds "$ROW_DIR/back-$i.xml" tile:dock:slot:PHONE)")"
  assert_within "sat $i: the fixture promoted onto the bottom row (one gutter, 13 px)" 13 "$gap" 3
  c6
done
row_end

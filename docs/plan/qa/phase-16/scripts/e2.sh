#!/usr/bin/env bash
# Phase 16 E2 — the two apps in the app list, their windows, the exported surface and the baseline (X14; r3 D6; C-3).
#   child     phase 02's regress.sh pattern on this build, seeded with phase 02's baseline carrying this phase's two
#             markers (REGRESS_BASELINE; the script takes no device lock, but it runs before the row for a clean ring)
#   baseline  layout_restore of qa/phase-16/baseline_layout.json: the slice holds assignSlotOnce lines and no
#             `-> assigned` one; the restored addedOnce equals the file's
#   list      "Calendar" under C and "People" under P, no "New" caption, each hold menu Pin to Start and NOT Uninstall,
#             the jump grid's C and P cells jump
#   windows   cal_view in Calendar's dump, people_letter:A in People's (the row makes its own fixture contact)
#   exported  the APK's exported components equal qa/phase-03/exported-allowlist.txt (phase 03 E5's exported.py), the
#             three ADDs on it, and the device holds that APK
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p16.sh"
QAR="$(cd "$QA/.." && pwd)"
OUT="$QA/E2"; mkdir -p "$OUT"

# ---- the child first
adb shell input keyevent KEYCODE_HOME; sleep 2
R_MARK="$(ring_mark)"
( REGRESS_BASELINE="$P16/baseline_layout-p02.json" bash "$P02S/regress.sh" "$OUT/p02-REGRESS" > "$OUT/p02-regress.out" 2>&1; echo $? > "$OUT/p02-regress.rc" )
ring_since "$R_MARK" > "$OUT/p02-regress.ring.txt" 2>/dev/null
adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5

row_begin E2 "Calendar and People in the app list, their windows, the exported surface, the baseline"
declare -A NAME=( [cal]="$CALENDAR_ACTIVITY" [people]="$PEOPLE_ACTIVITY" )
declare -A LABEL=( [cal]="Calendar" [people]="People" )
declare -A LETTER=( [cal]=C [people]=P )
open_applist() { ensure_start; adb shell input swipe 900 1200 150 1200 250; sleep 2; }
goto_letter() { # letter
  local h i
  open_applist
  for i in 1 2 3 4 5; do
    dump_ui "$ROW_DIR/.nav.xml"
    h="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/.nav.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
    [ -n "$h" ] && break
    adb shell input swipe 540 800 540 1700 300; sleep 1
  done
  tap_node "$ROW_DIR/.nav.xml" "$h"; sleep 1.5
  dump_ui "$ROW_DIR/.grid.xml"
  tap_node "$ROW_DIR/.grid.xml" "jump_cell:$1"; sleep 1.5
}

# ---------------------------------------------------------------- the phase-02 regress.sh pattern
log "--- phase 02's regress.sh on this build (seeded with phase 02's baseline + this phase's two markers)"
assert_eq "regress.sh exits 0" "0" "$(cat "$OUT/p02-regress.rc")"
R_SUM="$(grep -E '^[0-9]+ passed, [0-9]+ failed' "$OUT/p02-REGRESS/REGRESS.txt" 2>/dev/null | tail -1)"
note "regress.sh: $R_SUM"
assert_contains "regress.sh: 0 failed" " passed, 0 failed" "$R_SUM"
assert_ne "regress.sh: its slice covers a restore (assignSlotOnce lines in it)" "0" "$(grep -cF 'assignSlotOnce' "$OUT/p02-regress.ring.txt")"
assert_eq "regress.sh: zero assignSlotOnce -> assigned after its restores (C-3)" "0" "$(grep -F 'assignSlotOnce' "$OUT/p02-regress.ring.txt" | grep -cF -- '-> assigned')"

# ---------------------------------------------------------------- C-3: the baseline
log "--- the baseline (C-3)"
ring_save
MARK="$(ring_mark)"
layout_restore "$BASELINE"; assert_eq "layout_restore of qa/phase-16/baseline_layout.json" "0" "$?"
sleep 2
SLICE="$(ring_since "$MARK")"; printf '%s\n' "$SLICE" > "$ROW_DIR/ring_restore.txt"
# First: the slice covers this load (p14.sh:76's form) — the zero below is then about this load, not an unreadable ring.
assert_ne "the slice holds at least one assignSlotOnce line" "0" "$(grep -cF 'assignSlotOnce' "$ROW_DIR/ring_restore.txt")"
absent_in "ZERO assignSlotOnce … -> assigned lines after the restore" "-> assigned" "$(grep -F 'assignSlotOnce' "$ROW_DIR/ring_restore.txt")"
for m in slot:calendar:v1 slot:people:v1 slot:music:v1; do
  assert_contains "$m reports already run" "already run" "$(grep -F "assignSlotOnce $m " "$ROW_DIR/ring_restore.txt" | tail -1)"
done
layout_json | tr -d '\r' > "$ROW_DIR/layout_after_restore.json"
FILE_ONCE="$(python3 -c 'import json, sys; print(sorted(json.load(open(sys.argv[1]))["addedOnce"]))' "$BASELINE")"
assert_eq "the restored addedOnce equals the file's" "$FILE_ONCE" "$(python3 -c 'import json, sys; print(sorted(json.load(open(sys.argv[1]))["addedOnce"]))' "$ROW_DIR/layout_after_restore.json")"
assert_contains "… with slot:calendar:v1" "slot:calendar:v1" "$FILE_ONCE"
assert_contains "… and slot:people:v1" "slot:people:v1" "$FILE_ONCE"

# ---------------------------------------------------------------- the app list: walk, letters, captions
log "--- the app list"
open_applist
: > "$ROW_DIR/walk_order.txt"
prev=""
for i in $(seq 1 30); do
  dump_ui "$ROW_DIR/walk.xml"
  python3 - "$ROW_DIR/walk.xml" "$ROW_DIR/walk_order.txt" <<'PY'
import re, sys
s = open(sys.argv[1]).read(); seen = open(sys.argv[2]).read().split("\n")
items = []
for m in re.finditer(r'resource-id="(applist_(?:header|name|new):[^"]*)"[^>]*bounds="\[\d+,(\d+)\]', s):
    items.append((int(m.group(2)), m.group(1)))
with open(sys.argv[2], "a") as o:
    for _, rid in sorted(items):
        if rid not in seen:
            o.write(rid + "\n"); seen.append(rid)
PY
  last="$(grep -o 'resource-id="applist_name:[^"]*"' "$ROW_DIR/walk.xml" | tail -1)"
  [ "$last" = "$prev" ] && break; prev="$last"
  adb shell input swipe 540 1900 540 700 1500; sleep 1.2   # slow: no fling past a row
done
rm -f "$ROW_DIR/walk.xml"
for k in cal people; do
  group="$(python3 - "$ROW_DIR/walk_order.txt" "applist_name:${NAME[$k]}" <<'PY'
import sys
order = open(sys.argv[1]).read().split("\n"); letter = "?"
for rid in order:
    if rid.startswith("applist_header:"): letter = rid.split(":", 1)[1]
    if rid == sys.argv[2]: print(letter); break
PY
)"
  assert_eq "${LABEL[$k]} sits under ${LETTER[$k]}" "${LETTER[$k]}" "$group"
done
assert_eq "no New caption on any in-APK row (X14)" "0" "$(grep -c '^applist_new:app.tileshell' "$ROW_DIR/walk_order.txt")"

for k in cal people; do
  goto_letter "${LETTER[$k]}"
  scroll_to_node "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}" 3
  assert_eq "${LABEL[$k]} row found by applist_name" "${LABEL[$k]}" "$(node_text "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}" | python3 -c 'import html, sys; print(html.unescape(sys.stdin.read().strip()))')"
  # The jump scrolls smoothly, and a touch while the list still moves only stops it: hold once the row has stopped.
  prevb=""; b=""
  for i in 1 2 3 4 5 6; do
    b="$(bounds "$ROW_DIR/row_$k.xml" "applist_name:${NAME[$k]}")"
    [ -n "$b" ] && [ "$b" = "$prevb" ] && break
    prevb="$b"; sleep 0.7; dump_ui "$ROW_DIR/row_$k.xml"
  done
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1000; sleep 1.5
  dump_ui "$ROW_DIR/menu_$k.xml"
  assert_eq "${LABEL[$k]} hold menu offers Pin to Start" "yes" "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu_pin)"
  assert_eq "${LABEL[$k]} hold menu does NOT offer Uninstall" "no" "$(has_node "$ROW_DIR/menu_$k.xml" applist_menu_uninstall)"
  adb shell input keyevent KEYCODE_BACK; sleep 1
done

# The jump grid marks C and P: each cell takes the tap and the list lands on its app (a dimmed cell takes no tap).
for k in cal people; do
  open_applist
  dump_ui "$ROW_DIR/applist_top_$k.xml"
  H="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/applist_top_$k.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
  tap_node "$ROW_DIR/applist_top_$k.xml" "$H"; sleep 2
  dump_ui "$ROW_DIR/jump_grid_$k.xml"
  [ "$k" = cal ] && screencap "$ROW_DIR/jump_grid.png"
  JM="$(ring_mark)"
  tap_node "$ROW_DIR/jump_grid_$k.xml" "jump_cell:${LETTER[$k]}"; sleep 2
  assert_contains "the jump grid's ${LETTER[$k]} cell is available (a tap jumps)" "jump to ${LETTER[$k]}" "$(ring_since "$JM")"
  scroll_to_node "$ROW_DIR/after_jump_$k.xml" "applist_name:${NAME[$k]}" 2 >/dev/null 2>&1 || true
  assert_eq "after the jump, ${LABEL[$k]}'s row is on screen" "yes" "$(has_node "$ROW_DIR/after_jump_$k.xml" "applist_name:${NAME[$k]}")"
done
ensure_start

# ---------------------------------------------------------------- each window carries resource-ids
log "--- the two windows"
people_fixtures_up
adb shell am start -W -n "$CALENDAR_ACTIVITY" >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/window_cal.xml"
assert_eq "Calendar's window dump carries resource-id cal_view" "yes" "$(has_node "$ROW_DIR/window_cal.xml" cal_view)"
c6; ensure_start
adb shell am start -W -n "$PEOPLE_ACTIVITY" >/dev/null 2>&1; sleep 3
dump_ui "$ROW_DIR/window_people.xml"
assert_eq "People's window dump carries resource-id people_letter:A" "yes" "$(has_node "$ROW_DIR/window_people.xml" people_letter:A)"
c6; ensure_start
people_fixtures_down

# ---------------------------------------------------------------- the exported surface
log "--- the exported surface against qa/phase-03/exported-allowlist.txt"
ALLOW="$QAR/phase-03/exported-allowlist.txt"
python3 "$P03S/exported.py" "$APK" "$ALLOW" > "$ROW_DIR/exported.txt" 2>&1; echo $? > "$ROW_DIR/exported.rc"
cat "$ROW_DIR/exported.txt" >> "$LOG"
assert_eq "exported.py: the APK's exported components equal the allow-list (rc)" "0" "$(cat "$ROW_DIR/exported.rc")"
for c in app.tileshell.calendar.CalendarActivity app.tileshell.people.PeopleActivity app.tileshell.calendar.CalendarReminderReceiver; do
  assert_eq "the allow-list carries the ADD $c" "1" "$(grep -c "^$c	" "$ALLOW")"
done
assert_contains "the device holds the APK that was checked" "yes" "$(apk_matches)"
D="$(adb shell dumpsys package app.tileshell | tr -d '\r')"
# A filter with a data scheme is listed under the receiver table's "content:" scheme, the component line first.
RCV="$(printf '%s\n' "$D" | grep -B1 -A2 'Action: "android.intent.action.EVENT_REMINDER"' | head -6)"
assert_contains "dumpsys package: the reminder receiver is registered for EVENT_REMINDER" "app.tileshell/.calendar.CalendarReminderReceiver" "$RCV"
assert_contains "… with the content scheme (r3 D6: a filter with no data never matches the provider's broadcast)" 'Scheme: "content"' "$RCV"
assert_contains "… and the calendar provider's host" 'Authority: "com.android.calendar"' "$RCV"

# ---------------------------------------------------------------- restore
layout_restore "$BASELINE"; assert_eq "restore: layout_restore of the baseline" "0" "$?"
ensure_start
row_end

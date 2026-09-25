#!/usr/bin/env bash
# EDGE_SPINNER — the looping spinner's gestures (3.4–3.7, 4.8), after the owner reported on 2026-09-24 that the timer
# numbers do not scroll correctly. Driven on the timer editor's minutes column (60 values); the alarm editor's columns
# are the same LoopSpinner. Three behaviours, each able to fail on the build that had the fault:
#   1. a tap after a change rolls from the value shown (the tap handler had kept the value the editor opened with);
#   2. a long drag keeps the column full (the rows drawn were fixed around the value selected, so a drag of more than
#      two rows past the frame showed blank rows);
#   3. a drag catches a rolling column (a drag during the roll was ignored until the roll ended): caught, the fling's
#      roll never completes, so exactly one `[motion] spinner` line follows (the catch's own).
# Restore: the editor left without saving, no timer created, Home.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"
row_begin EDGE_SPINNER "the looping spinner: taps roll from the value shown, a long drag stays full, a drag catches a roll"

ROW=96 # one 32-epx row at 3 px/epx
desc_of() { # dump.xml resource-id -> the node's content-desc (the column's selected value)
  python3 - "$1" "$2" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    if f'resource-id="{sys.argv[2]}"' in s:
        m = re.search(r'content-desc="([^"]*)"', s)
        print(m.group(1) if m else "")
        break
PY
}
rows_in() { # dump.xml L T R B -> how many two-digit rows are drawn inside the column's frame (at least half a row tall)
  python3 - "$@" <<'PY'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
L, T, R, B = (int(v) for v in sys.argv[2:6])
n = 0
for node in re.finditer(r'<node[^>]*>', xml):
    s = node.group(0)
    t = re.search(r' text="(\d\d)"', s)
    b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', s)
    if not t or not b:
        continue
    l, top, r, bot = (int(v) for v in b.groups())
    if l >= L and r <= R and max(top, T) < min(bot, B) and min(bot, B) - max(top, T) >= 48:
        n += 1
print(n)
PY
}

TIMERS_BEFORE="$(store_timers | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo "?")"
note "timers before: $TIMERS_BEFORE"
open_clock timer
dump_ui "$ROW_DIR/t_list.xml"
tap_node "$ROW_DIR/t_list.xml" 'clock_bar:add'; sleep 1.5
dump_ui "$ROW_DIR/editor.xml"; screencap "$ROW_DIR/editor.png"
assert_eq "the timer editor is open (NEW TIMER)" "NEW TIMER" "$(node_text "$ROW_DIR/editor.xml" timer_editor_title)"
M="$(bounds "$ROW_DIR/editor.xml" 'timer_editor_field:minutes')"; note "minutes column $M"
set -- $M; L=$1; T=$2; R=$3; B=$4
X=$(( (L + R) / 2 )); CY=$(( (T + B) / 2 ))
V0="$(desc_of "$ROW_DIR/editor.xml" 'timer_editor_field:minutes')"; note "minutes at the start: $V0"
assert_ne "the minutes column names its value" "" "$V0"
v() { printf '%02d' $(( (10#$V0 + $1) % 60 )); }

# ---- 1. taps roll from the value shown
adb shell input tap "$X" $(( CY + 2 * ROW )); sleep 1.2
dump_ui "$ROW_DIR/tap1.xml"
assert_eq "1. a tap two rows below the centre rolls to $(v 2)" "$(v 2)" "$(desc_of "$ROW_DIR/tap1.xml" 'timer_editor_field:minutes')"
adb shell input tap "$X" $(( CY + ROW )); sleep 1.2
dump_ui "$ROW_DIR/tap2.xml"
assert_eq "1. then a tap one row below rolls on to $(v 3) (from the value shown, not the one the editor opened with)" "$(v 3)" "$(desc_of "$ROW_DIR/tap2.xml" 'timer_editor_field:minutes')"
adb shell input tap "$X" $(( CY - ROW )); sleep 1.2
dump_ui "$ROW_DIR/tap3.xml"
assert_eq "1. and a tap one row above rolls back to $(v 2)" "$(v 2)" "$(desc_of "$ROW_DIR/tap3.xml" 'timer_editor_field:minutes')"

# ---- 2. a long drag keeps the column full (the finger held while the dump is taken)
Y=$(( CY + 3 * ROW ))
adb shell input motionevent DOWN "$X" "$Y"
for _ in $(seq 1 21); do Y=$(( Y - 32 )); adb shell input motionevent MOVE "$X" "$Y"; done # 7 rows up, slowly
dump_ui "$ROW_DIR/held.xml"; screencap "$ROW_DIR/held.png"
HELD="$(rows_in "$ROW_DIR/held.xml" "$L" "$T" "$R" "$B")"
adb shell input motionevent UP "$X" "$Y"; sleep 1.5
assert_within "2. held 7 rows into a drag, the frame still shows a row in every slot (7.97 rows: 7 or 8 drawn rows)" 8 "$HELD" 1
dump_ui "$ROW_DIR/after_drag.xml"
AFTER="$(desc_of "$ROW_DIR/after_drag.xml" 'timer_editor_field:minutes')"
assert_eq "2. released, it settles 7 rows on ($(v 9))" "$(v 9)" "$AFTER"
assert_within "2. and the settled frame is full" 8 "$(rows_in "$ROW_DIR/after_drag.xml" "$L" "$T" "$R" "$B")" 1

# ---- 3. a drag catches a rolling column: a fast fling, then at once a slow one-row drag from the centre
CAUGHT=""
for attempt in 1 2 3; do
  MARK="$(ring_mark)"
  adb shell "t0=\$(date +%s%3N); input swipe $X $(( CY + 3 * ROW )) $X $(( CY - 3 * ROW )) 30; t1=\$(date +%s%3N); input motionevent DOWN $X $CY; t2=\$(date +%s%3N); input motionevent MOVE $X $(( CY - 40 )); input motionevent MOVE $X $(( CY - ROW )); input motionevent UP $X $(( CY - ROW )); echo \$t0 \$t1 \$t2" > "$ROW_DIR/catch$attempt.times" 2>&1
  sleep 1.5
  ring_since "$MARK" > "$ROW_DIR/ring_catch${attempt}_launcher.txt"
  read -r t0 t1 t2 < "$ROW_DIR/catch$attempt.times"
  GAP=$(( t2 - t1 ))
  LINES="$(grep -c '\[motion\] spinner' "$ROW_DIR/ring_catch${attempt}_launcher.txt")"
  note "catch $attempt: fling ended at +$(( t1 - t0 )) ms, the catch's DOWN $GAP ms later; [motion] spinner lines: $LINES"
  # The fling's roll lasts 400 ms (its cap); a DOWN more than 300 ms after the fling ended may have come after the roll.
  if [ "$GAP" -le 300 ]; then CAUGHT="$LINES"; break; fi
done
if [ -n "$CAUGHT" ]; then
  assert_eq "3. a drag during the roll catches it: the fling's roll never completes, one [motion] spinner line (the catch's)" 1 "$CAUGHT"
else
  record "3. a drag during the roll" "NOT RUN: in 3 attempts the catch's DOWN came more than 300 ms after the fling (adb input latency)"
fi

# ---- restore: leave without saving
adb shell input keyevent KEYCODE_BACK; sleep 1
TIMERS_AFTER="$(store_timers | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo "?")"
assert_eq "restore: no timer was created" "$TIMERS_BEFORE" "$TIMERS_AFTER"
adb shell input keyevent KEYCODE_HOME
row_end

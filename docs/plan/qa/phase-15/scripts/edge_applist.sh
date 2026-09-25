#!/usr/bin/env bash
# EDGE_APPLIST — the phase doc's "App list:" edge-case bullet: the three apps under `wm size 720x1560` (HD+, 2 px/epx,
# RV10); pin each to Start and unpin it; the pinned Alarms & Clock tile when no alarm is set (the static face) and when
# the next alarm is a snoozed one.
#   - At 1080x2340 (3 px/epx) each app-list row's label `applist_name:<component>` is measured (left, width, height) and a
#     MEDIUM Start tile's size; under `wm size 720x1560` (2 px/epx) the same label reads the same text and measures the
#     same in epx (± 1 epx) and lies inside the 720-px screen — RV10's rule, phase 11 edge.sh's method.
#   - At 720 each row's hold menu (at its label's own bounds) pins it: `[applist] pin to Start <component> -> pinned`, its
#     key in start_layout.json, its tile on Start at the MEDIUM size in epx; each is unpinned through Start's edit mode
#     (`edit_disc:unpin`): `[edit] unpin <key>`, the key gone.
#   - The pinned Alarms & Clock tile with no alarm: no `tile_clock_headline:` (ClockTiles publishes nothing; the tile's
#     own face) and its label. With the next alarm a SNOOZED one (an alarm rung and snoozed through the toast): the Next
#     alarm clock is the snooze instant, the ring says `[clock] tile: next alarm <id> at <snooze instant>`, the tile shows
#     the live face (`tile_clock_headline:`) naming the alarm; which time its headline reads is RECORDED (the set time vs
#     the snooze time — no source says, H14); after the alarm is deleted the face is cleared again.
# Restore: RV12's clock restore, the alarm deleted through the app, the three unpinned, `wm size reset`, the Start layout
# as found (layout_restore of the file saved at the start), the baseline re-asserted.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"; . "$(dirname "$0")/mic_guard.sh"
. "$QROOT/phase-02/scripts/layout.sh"

row_begin EDGE_APPLIST "the three apps under wm size 720x1560; pin and unpin each; the Clock tile's static and snoozed faces"
mic_guard_begin
assert_clock_empty "baseline"
dismiss_any_ring
TILES="$(dirname "$0")/tiles.py"
declare -A COMP=( [clock]="app.tileshell/.clock.ClockActivity" [calc]="app.tileshell/.calculator.CalculatorActivity" [rec]="app.tileshell/.recorder.RecorderActivity" )
declare -A KEY=( [clock]="app:app.tileshell/app.tileshell.clock.ClockActivity:0" [calc]="app:app.tileshell/app.tileshell.calculator.CalculatorActivity:0" [rec]="app:app.tileshell/app.tileshell.recorder.RecorderActivity:0" )
declare -A LABEL=( [clock]="Alarms & Clock" [calc]="Calculator" [rec]="Voice Recorder" )
declare -A LETTER=( [clock]=A [calc]=C [rec]=V )
CLOCK_ID="${KEY[clock]}"
wall_of() { printf '%s\n' "$1" | grep -oE 'wall=[0-9]+' | head -1 | cut -d= -f2; }
unesc() { python3 -c 'import html,sys; print(html.unescape(sys.stdin.read().strip()))'; }
# start_layout.json is written by org.json, which escapes every "/" as "\/": a key is looked for in the file's JSON
# re-serialised by Python (no escaping) — runs 1 and 2 grepped the raw file and never saw an app key at all.
layout_text() { layout_json | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)))'; }
layout_save "$ROW_DIR/layout_before.json"
PRE="$(for k in clock calc rec; do python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1]))))' "$ROW_DIR/layout_before.json" | grep -qF "\"${KEY[$k]}\"" && echo "$k"; done | paste -sd,)"
assert_contains "baseline: the layout file is read (positive control: its MUSIC slot)" "\"slot:MUSIC\"" "$(python3 -c 'import json,sys; print(json.dumps(json.load(open(sys.argv[1]))))' "$ROW_DIR/layout_before.json")"
assert_eq "baseline: none of the three is pinned on Start (the pin can be seen)" "" "$PRE"
WM0="$(adb shell wm size | tr -d '\r' | paste -sd' ')"; note "wm size before: $WM0"
assert_absent "baseline: no wm size override" "Override size" "$WM0"

# ---- screen-relative gestures (the device's current size W x H) ------------------------------------------------------------------------
W=1080; H=2340
swipe_pm() { # x1 y1 x2 y2 (per mille of W / H) ms
  adb shell input swipe $(( W * $1 / 1000 )) $(( H * $2 / 1000 )) $(( W * $3 / 1000 )) $(( H * $4 / 1000 )) "$5"
}
open_applist() { adb shell input keyevent KEYCODE_HOME; sleep 2; swipe_pm 833 513 139 513 250; sleep 2; }
goto_letter() { # letter
  local h i
  open_applist
  for i in 1 2 3 4 5; do
    dump_ui "$ROW_DIR/.nav.xml"
    h="$(grep -o 'resource-id="applist_header:[^"]*"' "$ROW_DIR/.nav.xml" | head -1 | sed 's/resource-id="//; s/"$//')"
    [ -n "$h" ] && break
    swipe_pm 500 342 500 726 300; sleep 1
  done
  tap_node "$ROW_DIR/.nav.xml" "$h"; sleep 1.5
  dump_ui "$ROW_DIR/.grid.xml"
  tap_node "$ROW_DIR/.grid.xml" "jump_cell:$1"; sleep 1.5
}
find_down() { # out.xml resource-id max — scroll down (content up) until the node is laid out
  local i=0
  dump_ui "$1"
  while [ "$(has_node "$1" "$2")" = no ] && [ "$i" -lt "$3" ]; do swipe_pm 500 726 500 342 400; sleep 1.2; i=$((i + 1)); dump_ui "$1"; done
  note "find_down $2: $i swipe(s), found=$(has_node "$1" "$2")"
  [ "$(has_node "$1" "$2")" = yes ]
}
# The app-list row of k, reached through the jump grid, once its bounds are still between two dumps (E1 run 3).
applist_row() { # k out.xml -> prints "l t r b"
  local k="$1" d="$2" b prev="" i
  goto_letter "${LETTER[$k]}"
  find_down "$d" "applist_name:${COMP[$k]}" 3 >/dev/null
  for i in 1 2 3 4 5 6; do
    b="$(bounds "$d" "applist_name:${COMP[$k]}")"
    [ -n "$b" ] && [ "$b" = "$prev" ] && break
    prev="$b"; sleep 0.7; dump_ui "$d"
  done
  echo "$b"
}
epx() { # "l t r b" px_per_epx -> "left width height" in epx (one decimal)
  python3 -c 'import sys; l, t, r, b = map(int, sys.argv[1].split()); p = float(sys.argv[2]); print("%.1f %.1f %.1f" % (l / p, (r - l) / p, (b - t) / p))' "$1" "$2" 2>/dev/null
}
same_epx() { # "a b c" "a b c" tol -> yes / no (…)
  python3 -c '
import sys
a = [float(x) for x in sys.argv[1].split()]; b = [float(x) for x in sys.argv[2].split()]; t = float(sys.argv[3])
print("yes" if len(a) == len(b) == 3 and all(abs(x - y) <= t for x, y in zip(a, b)) else "no (%s vs %s)" % (sys.argv[1], sys.argv[2]))' "$1" "$2" "$3" 2>/dev/null || echo "no ($1 vs $2)"
}

# ---- the 1080 reference ---------------------------------------------------------------------------------------------------------------
declare -A REF
for k in clock calc rec; do
  b="$(applist_row "$k" "$ROW_DIR/row1080_$k.xml")"
  REF[$k]="$(epx "$b" 3)"
  note "1080: ${LABEL[$k]} label bounds [$b] -> epx (left width height) ${REF[$k]}"
  assert_ne "1080: ${LABEL[$k]}'s row is found by its label's tag" "" "$b"
done
adb shell input keyevent KEYCODE_HOME; sleep 2
MED="$(python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(next((o["key"] for o in d["order"] if o["size"]=="MEDIUM"), ""))' "$ROW_DIR/layout_before.json")"
gdump "$ROW_DIR/start1080.xml"
MEDB="$(bounds "$ROW_DIR/start1080.xml" "tile:$MED")"
MEDREF="$(epx "$MEDB" 3)"
note "1080: the MEDIUM tile $MED bounds [$MEDB] -> epx ${MEDREF}"

# ---- wm size 720x1560 ------------------------------------------------------------------------------------------------------------------
adb shell wm size 720x1560; sleep 6; adb shell input keyevent KEYCODE_HOME; sleep 5
W=720; H=1560
assert_contains "wm size override 720x1560 is in force" "Override size: 720x1560" "$(adb shell wm size | tr -d '\r' | paste -sd' ')"
for k in clock calc rec; do
  d="$ROW_DIR/row720_$k.xml"
  b="$(applist_row "$k" "$d")"
  screencap "$ROW_DIR/row720_$k.png"
  note "720: ${LABEL[$k]} label bounds [$b] -> epx $(epx "$b" 2)"
  assert_eq "720: ${LABEL[$k]}'s row is found by applist_name and reads its label" "${LABEL[$k]}" "$(node_text "$d" "applist_name:${COMP[$k]}" | unesc)"
  assert_eq "720: … its label measures the same in epx as at 1080 (left, width, height ± 1 epx; RV10)" yes "$(same_epx "$(epx "$b" 2)" "${REF[$k]}" 1)"
  assert_eq "720: … and lies inside the 720-px screen" yes "$(set -- $b; [ -n "$3" ] && [ "$3" -le 720 ] && [ "$1" -ge 0 ] && echo yes || echo "no ($b)")"
  # Pin to Start from the row's hold menu, at the label's own bounds.
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 1000; sleep 1.5
  dump_ui "$ROW_DIR/menu720_$k.xml"
  assert_eq "720: ${LABEL[$k]}'s hold menu offers Pin to Start" yes "$(has_node "$ROW_DIR/menu720_$k.xml" applist_menu_pin)"
  MARK="$(ring_mark)"
  tap_node "$ROW_DIR/menu720_$k.xml" applist_menu_pin; sleep 1.5
  ring_since "$MARK" launcher > "$ROW_DIR/ring_pin_${k}_launcher.txt"
  assert_contains "720: pin ${LABEL[$k]} → [applist] pin to Start … -> pinned" "pin to Start ${COMP[$k]} -> pinned" "$(cat "$ROW_DIR/ring_pin_${k}_launcher.txt")"
  # The layout store writes start_layout.json after the pin, not with it (run 1 read the file 1.5 s after the tap and
  # found the key absent while the tile was already on Start): polled up to 10 s.
  LJ=""; for _ in $(seq 1 20); do LJ="$(layout_text)"; case "$LJ" in *"\"${KEY[$k]}\""*) break ;; esac; sleep 0.5; done
  assert_contains "720: … its key is in start_layout.json (polled 10 s)" "\"${KEY[$k]}\"" "$LJ"
done
adb shell input keyevent KEYCODE_HOME; sleep 2
for k in clock calc rec; do
  d="$ROW_DIR/start720_$k.xml"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  find_down "$d" "tile:${KEY[$k]}" 6 >/dev/null
  tb="$(bounds "$d" "tile:${KEY[$k]}")"
  note "720: the ${LABEL[$k]} tile [$tb] -> epx $(epx "$tb" 2)"
  assert_ne "720: the ${LABEL[$k]} tile is on Start" "" "$tb"
  if [ -n "$MEDREF" ]; then
    assert_eq "720: … at the MEDIUM size in epx (the 1080 MEDIUM tile's width and height ± 1 epx)" yes "$(python3 -c '
import sys
a = sys.argv[1].split(); b = sys.argv[2].split()
ok = len(a) == 3 and len(b) == 3 and abs(float(a[1]) - float(b[1])) <= 1 and abs(float(a[2]) - float(b[2])) <= 1
print("yes" if ok else "no (%s vs %s)" % (sys.argv[1], sys.argv[2]))' "$(epx "$tb" 2)" "$MEDREF")"
  fi
done
screencap "$ROW_DIR/start720_pinned.png"

# ---- the pinned Alarms & Clock tile: no alarm → the static face --------------------------------------------------------------------------
clock_tile_dump() { # out.xml : Start, scrolled to the Clock tile, dumped through the gesture driver
  adb shell input keyevent KEYCODE_HOME; sleep 2
  find_down "$1" "tile:$CLOCK_ID" 6 >/dev/null
  gdump "$1"; python3 "$TILES" "$1" "$CLOCK_ID" > "${1%.xml}.tiles.txt"
}
clock_tile_dump "$ROW_DIR/clock_static.xml"; screencap "$ROW_DIR/clock_static.png"
note "the Clock tile with no alarm: $(cat "$ROW_DIR/clock_static.tiles.txt")"
assert_ne "no alarm: the Clock tile is in the dump" "" "$(cat "$ROW_DIR/clock_static.tiles.txt")"
assert_eq "no alarm: the static face — no tile_clock_headline" no "$(has_node "$ROW_DIR/clock_static.xml" "tile_clock_headline:$CLOCK_ID")"
assert_contains "no alarm: … the tile carries its label (tiles.py reads the tile's own subtree)" "Alarms & Clock" "$(cat "$ROW_DIR/clock_static.tiles.txt")"

# ---- the next alarm a snoozed one ----------------------------------------------------------------------------------------------------------
NOW="$(device_ms)"; read -r AH AM_ <<< "$(device_hm $(( NOW + 120000 )))"
AID="$(api_alarm "$AH" "$AM_" "Snoozy")"
assert_ne "an alarm 2 min ahead was created" "" "$AID"
AT="$(alarm_trigger_ms | head -1)"; AT="${AT:-0}"
adb shell input keyevent KEYCODE_HOME; sleep 1
MARK="$(ring_mark)"
jump_clock $(( AT - 3000 )) >/dev/null
assert_ne "it rings" "" "$(wait_ring "$MARK" "[alarms] fired $AID kind=alarm" 20)"
sleep 2
gdump_for "$ROW_DIR/snooze_ring.xml" ring_snooze; screencap "$ROW_DIR/snooze_ring.png"
MARK="$(ring_mark)"
gtap "$ROW_DIR/snooze_ring.xml" ring_snooze; sleep 3
assert_contains "Snooze on the toast: ring ended $AID: snooze" "[alarms] ring ended $AID: snooze" "$(ring_since "$MARK")"
S="$(alarm_field "$AID" snoozedUntilMs)"
note "snoozedUntilMs=$S (set time $(time_12h "$AH" "$AM_"), snooze time $(read -r sh sm <<< "$(device_hm "${S:-0}")"; time_12h "$sh" "$sm"))"
assert_ne "the alarm holds a snooze instant" "null" "${S:-null}"
assert_eq "the next alarm is the snoozed one (Next alarm clock = the snooze instant)" "$S" "$(next_alarm_clock_ms)"
sleep 2
ring_since "$MARK" launcher > "$ROW_DIR/ring_snooze_tile_launcher.txt"
assert_contains "the tile publisher took it: [clock] tile: next alarm $AID at <the snooze instant>" "[clock] tile: next alarm $AID at $S" "$(cat "$ROW_DIR/ring_snooze_tile_launcher.txt")"
clock_tile_dump "$ROW_DIR/clock_snoozed.xml"; screencap "$ROW_DIR/clock_snoozed.png"
note "the Clock tile with the snoozed alarm next: $(cat "$ROW_DIR/clock_snoozed.tiles.txt")"
assert_eq "snoozed: the tile shows the live face (tile_clock_headline)" yes "$(has_node "$ROW_DIR/clock_snoozed.xml" "tile_clock_headline:$CLOCK_ID")"
assert_contains "snoozed: … naming the alarm" "Snoozy" "$(cat "$ROW_DIR/clock_snoozed.tiles.txt")"
HL="$(node_text "$ROW_DIR/clock_snoozed.xml" "tile_clock_headline:$CLOCK_ID")"
read -r SH SM <<< "$(device_hm "${S:-0}")"
record "snoozed: the headline reads (set time / snooze time)" "[$HL] ($(time_12h "$AH" "$AM_") / $(time_12h "$SH" "$SM"))"
ring_save launcher
clock_restore
app_delete_all
assert_eq "the snoozed alarm is deleted through the app" "" "$(alarm_ids | paste -sd,)"
sleep 2
clock_tile_dump "$ROW_DIR/clock_after.xml"
assert_eq "after the delete the face is cleared again (no tile_clock_headline)" no "$(has_node "$ROW_DIR/clock_after.xml" "tile_clock_headline:$CLOCK_ID")"

# ---- unpin each through Start's edit mode (still 720) --------------------------------------------------------------------------------------
for k in clock calc rec; do
  d="$ROW_DIR/unpin720_$k.xml"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  find_down "$d" "tile:${KEY[$k]}" 6 >/dev/null
  hold_node "$d" "tile:${KEY[$k]}" 1000; sleep 1.5
  dump_ui "$d"
  assert_eq "720: holding the ${LABEL[$k]} tile shows the unpin disc" yes "$(has_node "$d" 'edit_disc:unpin')"
  MARK="$(ring_mark)"
  tap_node "$d" 'edit_disc:unpin'; sleep 1.5
  adb shell input keyevent KEYCODE_BACK; sleep 1
  ring_since "$MARK" launcher > "$ROW_DIR/ring_unpin_${k}_launcher.txt"
  assert_contains "720: unpin → [edit] unpin ${KEY[$k]}" "[edit] unpin ${KEY[$k]}" "$(cat "$ROW_DIR/ring_unpin_${k}_launcher.txt")"
  LJ=""; for _ in $(seq 1 20); do LJ="$(layout_text)"; case "$LJ" in *"\"${KEY[$k]}\""*) sleep 0.5 ;; *) break ;; esac; done
  assert_absent "720: … its key is gone from start_layout.json (polled 10 s)" "\"${KEY[$k]}\"" "$LJ"
done

# ---- restore ----------------------------------------------------------------------------------------------------------------------------------
adb shell wm size reset; sleep 6; adb shell input keyevent KEYCODE_HOME; sleep 5
W=1080; H=2340
assert_absent "restore: wm size reset (no override)" "Override size" "$(adb shell wm size | tr -d '\r' | paste -sd' ')"
ring_save launcher
layout_restore "$ROW_DIR/layout_before.json"
assert_eq "restore: the Start layout as found (layout_restore proved it)" 0 $?
assert_clock_empty "restore"
mic_guard_end
row_end

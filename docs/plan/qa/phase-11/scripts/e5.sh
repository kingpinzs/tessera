#!/usr/bin/env bash
# E5 — taps and events while a burst is open, each from a fresh burst on the fixture in its grid cell, each with a
# MARK before its action (C-20). Phase doc E5 (T11-16, T11-17, T11-21, T11-23, T11-26, T11-28, T11-31, T11-34).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E5 "taps while open: elsewhere, another tile, the held tile, the name strip, a label, Back, the discs, Home, sleep, scroll, press"
seed_fixtures
restore baseline_layout.json

# fresh_burst [tile id]: from a plain Start, hold the tile (default the fixture) and release; B.xml is the burst.
fresh_burst() {
  local id="${1:-tile:$A_KEY}"
  qdump "$ROW_DIR/.rest.xml"
  read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "$id")"
  hold "$FX" "$FY" 1.0
  qdump "$ROW_DIR/B.xml"
  [ "$(has_node "$ROW_DIR/B.xml" quick_burst)" = yes ] || note "fresh_burst: no burst on $id"
}
disc_of() { bounds "$1" edit_disc:unpin; }
edit_on() { grep -q 'resource-id="edit_disc' "$1" && echo yes || echo no; }

log "--- tap empty grid space: the burst closes, edit mode stays; a second tap exits (R6 §1.5.1) ---"
fresh_burst
read -r EX EY <<< "$(empty_point "$ROW_DIR/B.xml" 300 1850)"; note "empty point $EX $EY"
tileb="$(bounds "$ROW_DIR/B.xml" "tile:$A_KEY")"
MARK="$(ring_mark)"; tap_xy "$EX" "$EY"; sleep 1
qdump "$ROW_DIR/elsewhere1.xml"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/elsewhere1.xml" quick_burst)"
assert_eq "edit mode stays" yes "$(edit_on "$ROW_DIR/elsewhere1.xml")"
assert_eq "the held tile unchanged" "$tileb" "$(bounds "$ROW_DIR/elsewhere1.xml" "tile:$A_KEY")"
assert_contains "ring: tap elsewhere" "[quick] burst closed: tap elsewhere" "$(quick_since "$MARK")"
tap_xy "$EX" "$EY"; sleep 1.5
qdump "$ROW_DIR/elsewhere2.xml"
assert_eq "a second tap on empty space exits edit mode" no "$(edit_on "$ROW_DIR/elsewhere2.xml")"
c6

log "--- tap another tile (Tess, outside every satellite): the burst closes, the selection stays ---"
fresh_burst
free="$(python3 - "$ROW_DIR/B.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
def rects(prefix):
    out = []
    for n in re.finditer(r"<node[^>]*>", s):
        n = n.group(0); i = re.search(r'resource-id="(%s[^"]*)"' % prefix, n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if i and b: out.append(tuple(map(int, b.groups())))
    return out
t = rects("tile:shell:cortana")
if not t: print("none"); sys.exit()
l, tp, r, b = t[0]; cx, cy = (l + r) // 2, (tp + b) // 2
hit = any(L <= cx <= R and T <= cy <= B for L, T, R, B in rects("quick_sat"))
print(f"{cx} {cy}" if not hit else "covered")
PY
)"
assert_ne "Tess's centre is outside every satellite and label" covered "$free"
disc0="$(disc_of "$ROW_DIR/B.xml")"
read -r TX TY <<< "$free"
MARK="$(ring_mark)"; tap_xy "$TX" "$TY"; sleep 1
qdump "$ROW_DIR/another.xml"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/another.xml" quick_burst)"
assert_eq "the discs are still on the fixture" "$disc0" "$(disc_of "$ROW_DIR/another.xml")"
S="$(ring_since "$MARK")"
assert_contains "ring: tap elsewhere" "[quick] burst closed: tap elsewhere" "$S"
assert_absent "ring: the selection did not move" "[edit] selection moves to" "$S"
c6

log "--- tap the held tile: the burst closes, edit mode stays (not today's exit) ---"
fresh_burst
MARK="$(ring_mark)"; tap_xy "$FX" "$FY"; sleep 1
qdump "$ROW_DIR/heldtap.xml"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/heldtap.xml" quick_burst)"
assert_eq "the discs still on it" yes "$(edit_on "$ROW_DIR/heldtap.xml")"
S="$(ring_since "$MARK")"
assert_contains "ring: tap elsewhere" "[quick] burst closed: tap elsewhere" "$S"
assert_absent "ring: no exit" "[edit] tap on the held tile" "$S"
c6

log "--- a label counts as its satellite (T11-26) ---"
fresh_burst
MARK="$(ring_mark)"; tap_node "$ROW_DIR/B.xml" quick_sat_label:0; sleep 3.5
qdump "$ROW_DIR/label.xml"
assert_eq "tapping label 0 ran qa_one" qa_one "$(node_text "$ROW_DIR/label.xml" "$A_PKG:id/shortcut_id")"
assert_contains "ring: satellite 0 ran" "[quick] tap satellite 0 $A_PKG/qa_one: startShortcut ok" "$(quick_since "$MARK")"
c6

log "--- Back closes the burst only; a second Back exits edit mode ---"
fresh_burst
MARK="$(ring_mark)"; adb shell input keyevent KEYCODE_BACK; sleep 1
qdump "$ROW_DIR/back1.xml"
assert_eq "Back: no quick_burst" no "$(has_node "$ROW_DIR/back1.xml" quick_burst)"
assert_eq "Back: edit mode stays" yes "$(edit_on "$ROW_DIR/back1.xml")"
assert_contains "ring: back" "[quick] burst closed: back" "$(quick_since "$MARK")"
adb shell input keyevent KEYCODE_BACK; sleep 1.5
qdump "$ROW_DIR/back2.xml"
assert_eq "second Back: edit mode off" no "$(edit_on "$ROW_DIR/back2.xml")"
c6

log "--- the resize disc: the tile resizes AND the burst closes (Q2 A, T11-17) ---"
fresh_burst
MARK="$(ring_mark)"; tap_node "$ROW_DIR/B.xml" edit_disc:resize; sleep 1.5
qdump "$ROW_DIR/resize.xml"
S="$(ring_since "$MARK")"
assert_contains "ring: the size cycled" "[edit] resize $A_KEY MEDIUM -> SMALL" "$S"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/resize.xml" quick_burst)"
assert_eq "edit mode stays" yes "$(edit_on "$ROW_DIR/resize.xml")"
corner="$(python3 -c 'import sys; t=list(map(int,sys.argv[1].split())); d=list(map(int,sys.argv[2].split())); print(abs((d[0]+d[2])//2-t[2]), abs((d[1]+d[3])//2-t[1]))' "$(bounds "$ROW_DIR/resize.xml" "tile:$A_KEY")" "$(disc_of "$ROW_DIR/resize.xml")")"
note "unpin disc centre off the new top-right corner by (dx dy) = $corner"
assert_within "the unpin disc on the new top-right corner (x)" 0 "${corner% *}" 3
assert_contains "ring: resize" "[quick] burst closed: resize" "$S"
restore baseline_layout.json

log "--- the unpin disc: the tile and the burst go ---"
fresh_burst
MARK="$(ring_mark)"; tap_node "$ROW_DIR/B.xml" edit_disc:unpin; sleep 1.5
qdump "$ROW_DIR/unpin.xml"
assert_eq "the fixture tile is gone" no "$(has_node "$ROW_DIR/unpin.xml" "tile:$A_KEY")"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/unpin.xml" quick_burst)"
assert_contains "ring: unpin" "[quick] burst closed: unpin" "$(quick_since "$MARK")"
restore baseline_layout.json

log "--- Home (the drawn Windows key; this AVD never re-delivers KEYCODE_HOME to a resumed Start — P6) ---"
fresh_burst
MARK="$(ring_mark)"; tap_node "$ROW_DIR/B.xml" nav_windows; sleep 1.5
qdump "$ROW_DIR/home.xml"
assert_eq "start_page" yes "$(has_node "$ROW_DIR/home.xml" start_page)"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/home.xml" quick_burst)"
assert_eq "no edit mode" no "$(edit_on "$ROW_DIR/home.xml")"
S="$(ring_since "$MARK")"
assert_contains "ring: home" "[quick] burst closed: home" "$S"
assert_contains "ring: [start] home:" "[start] home:" "$S"
c6

log "--- screen off: Start stops, the burst closes (stop) ---"
fresh_burst
MARK="$(ring_mark)"; adb shell input keyevent KEYCODE_SLEEP; sleep 2
assert_eq "wake_device after the screen-off (C-25)" Awake "$(wake_device)"
sleep 1.5
qdump "$ROW_DIR/stop.xml"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/stop.xml" quick_burst)"
assert_contains "ring: stop" "[quick] burst closed: stop" "$(quick_since "$MARK")"
c6

log "--- no hold timer in edit mode (T11-23): a 1.0-s still press on another tile opens no burst ---"
fresh_burst
read -r EX EY <<< "$(empty_point "$ROW_DIR/B.xml" 300 1850)"
tap_xy "$EX" "$EY"; sleep 1          # close the burst; edit mode stays
qdump "$ROW_DIR/nohold-pre.xml"
read -r SX SY <<< "$(center "$ROW_DIR/nohold-pre.xml" tile:shell:settings)"
MARK="$(ring_mark)"; hold_down "$SX" "$SY"; sleep 1.0
qdump "$ROW_DIR/nohold-held.xml"
assert_eq "no quick_burst while the finger rests" no "$(has_node "$ROW_DIR/nohold-held.xml" quick_burst)"
hold_up "$SX" "$SY"; sleep 1
qdump "$ROW_DIR/nohold-up.xml"
S="$(ring_since "$MARK")"
assert_absent "ring: no burst on" "[quick] burst on" "$S"
assert_contains "the release moved the selection to Start settings" "[edit] selection moves to shell:settings" "$S"
c6

log "--- a scroll on empty space: Start scrolls, the satellites ride the tile, fresh rest= lines (T11-28) ---"
restore baseline_layout-tall.json
fresh_burst
sleep 1.5
read -r EX EY <<< "$(empty_point "$ROW_DIR/B.xml" 900 1850)"; note "tall: empty point $EX $EY"
before_tile="$(bounds "$ROW_DIR/B.xml" "tile:$A_KEY")"
MARK="$(ring_mark)"
adb shell input swipe "$EX" "$EY" "$EX" $((EY - 160)) 1500; sleep 1.5
qdump "$ROW_DIR/scrolled.xml"
python3 - "$ROW_DIR/B.xml" "$ROW_DIR/scrolled.xml" "$A_KEY" > "$ROW_DIR/scroll-deltas.txt" <<'PY'
import re, sys
def rs(p):
    s = open(p).read(); out = {}
    for n in re.finditer(r"<node[^>]*>", s):
        n = n.group(0); i = re.search(r'resource-id="((?:tile:|quick_sat:)[^"]*)"', n); b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
        if i and b: out[i.group(1)] = tuple(map(int, b.groups()))
    return out
a, b = rs(sys.argv[1]), rs(sys.argv[2]); t = "tile:" + sys.argv[3]
dy = b[t][1] - a[t][1]
print("tile_dy", dy)
for i in range(4):
    k = "quick_sat:%d" % i
    print(k, (b[k][1] - a[k][1]) - dy if k in a and k in b else "missing")
PY
note "$(tr '\n' ' ' < "$ROW_DIR/scroll-deltas.txt")"
tdy="$(awk '/tile_dy/{print $2}' "$ROW_DIR/scroll-deltas.txt")"
assert_within "Start scrolled: the tile moved (|dy| ≥ 60 px)" 110 "${tdy#-}" 50
for i in 0 1 2 3; do assert_within "quick_sat:$i moved with the tile (± 1 px)" 0 "$(awk -v k=quick_sat:$i '$1==k{print $2}' "$ROW_DIR/scroll-deltas.txt")" 1; done
assert_contains "fresh rest= lines after the scroll (T11-28)" "[quick] satellite 0 rest=" "$(quick_since "$MARK")"
restore baseline_layout.json

log "--- the folder-name strip (band variant): the burst on a member closes, no name box ---"
restore baseline_layout-band.json
qdump "$ROW_DIR/band-rest.xml"
tap_node "$ROW_DIR/band-rest.xml" tile:folder:qa; sleep 1.5          # a tap opens the band
fresh_burst "tile:member:$A_KEY"
assert_eq "burst on the member" yes "$(has_node "$ROW_DIR/B.xml" quick_burst)"
MARK="$(ring_mark)"; tap_node "$ROW_DIR/B.xml" folder_name_placeholder:qa; sleep 1.2
qdump "$ROW_DIR/namestrip.xml"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/namestrip.xml" quick_burst)"
assert_eq "no folder_name_box" no "$(has_node "$ROW_DIR/namestrip.xml" folder_name_box)"
assert_contains "ring: tap elsewhere" "[quick] burst closed: tap elsewhere" "$(quick_since "$MARK")"
restore baseline_layout.json

log "--- press feedback (T11-7): on this build a pressed satellite looks exactly as at rest, under every press style ---"
for style in tilt p4; do
  set_press "$style"
  fresh_burst
  sleep 1
  r0="$(bounds "$ROW_DIR/B.xml" quick_sat:0)"
  screencap "$ROW_DIR/press-$style-rest.png"
  read -r L T R Bm <<< "$r0"
  hold_down $((L + 10)) $((T + 10)); sleep 0.6
  screencap "$ROW_DIR/press-$style-held.png"
  hold_move $((L - 200)) $((T + 10)); sleep 0.2; hold_up $((L - 200)) $((T + 10)); sleep 1
  # shellcheck disable=SC2086
  eq="$(python3 "$(dirname "$0")/qpix.py" equal "$ROW_DIR/press-$style-rest.png" "$ROW_DIR/press-$style-held.png" $L $T $R $Bm 1)"
  assert_eq "press_$style: the held satellite equals its rest pixels ± 1 (no Q6 style)" EQUAL "${eq%% *}"
  qdump "$ROW_DIR/press-$style-after.xml"
  assert_eq "press_$style: sliding off ran nothing, the burst stays" yes "$(has_node "$ROW_DIR/press-$style-after.xml" quick_burst)"
  c6
done
set_press none
row_end

#!/usr/bin/env bash
# E3 — the selection rule and counts, over every tile kind: the fixture's four in corner order; tileclient-b's one;
# an icon that fails (drawn with no glyph, still runs); and the no-burst reasons for a shell tile with none, a
# folder, an Unassigned slot and a secondary tile. Phase doc E3 (T11-12, T11-14, T11-22, T11-30, T11-33).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E3 "selection and counts: the fixture's four in order, b's one, a failed icon, and every no-burst kind"
seed_fixtures
restore baseline_layout.json

# hold_at <tile resource-id> <tag>: hold its centre 1.0 s and release; the dump before the release is <tag>.xml.
hold_at() {
  local c
  qdump "$ROW_DIR/$2-rest.xml"
  c="$(center "$ROW_DIR/$2-rest.xml" "$1")" || { log "no node $1 on Start"; return 1; }
  read -r X Y <<< "$c"
  hold_down "$X" "$Y"; sleep 1.0
  qdump "$ROW_DIR/$2.xml"; screencap "$ROW_DIR/$2.png"
  hold_up "$X" "$Y"; sleep 0.8
}

log "--- the fixture: qa_one..qa_four at top-left, top-right, bottom-left, bottom-right ---"
MARK="$(ring_mark)"
hold_at "tile:$A_KEY" fixture
order="$(python3 - "$ROW_DIR/fixture.xml" "$A_KEY" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
def r(i):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(i), s)
    return tuple(map(int, m.groups())) if m else None
t = r("tile:" + sys.argv[2])
want = [("left", "above"), ("right", "above"), ("left", "below"), ("right", "below")]
out = []
for i, (h, v) in enumerate(want):
    q = r("quick_sat:%d" % i)
    ok = q and t and ((q[2] <= t[0]) if h == "left" else (q[0] >= t[2])) and ((q[3] <= t[1]) if v == "above" else (q[1] >= t[3]))
    out.append("ok" if ok else "BAD")
print(",".join(out))
PY
)"
assert_eq "satellites 0-3 diagonally outside TL, TR, BL, BR" "ok,ok,ok,ok" "$order"
labels="$(for i in 0 1 2 3; do node_text "$ROW_DIR/fixture.xml" "quick_sat_label:$i"; done | tr '\n' ',')"
assert_eq "satellite i's label = rank i's label" "One,Two,Three,Four," "$labels"
c6

log "--- tileclient-b: one dynamic shortcut, one satellite at top-left ---"
verb_b_start reset
MARK="$(ring_mark)"
hold_at "tile:$B_KEY" b-one
assert_eq "quick_sat:0 present" yes "$(has_node "$ROW_DIR/b-one.xml" quick_sat:0)"
for i in 1 2 3; do assert_eq "quick_sat:$i absent" no "$(has_node "$ROW_DIR/b-one.xml" "quick_sat:$i")"; done
assert_contains "shortcuts line (T11-12)" "[quick] shortcuts for app.tileshell.testclient.b/app.tileshell.testclient.VerbActivity/0: 1 (1 shown: qa_dyn)" "$(quick_since "$MARK")"
c6

log "--- a failed icon (T11-14 / T11-33): drawn with its fill and no glyph, and it still runs ---"
verb_b_start badicon
MARK="$(ring_mark)"
hold_at "tile:$B_KEY" b-badicon
assert_eq "quick_sat:0 present" yes "$(has_node "$ROW_DIR/b-badicon.xml" quick_sat:0)"
assert_eq "quick_sat:1 present" yes "$(has_node "$ROW_DIR/b-badicon.xml" quick_sat:1)"
s1="$(bounds "$ROW_DIR/b-badicon.xml" quick_sat:1)"; s0="$(bounds "$ROW_DIR/b-badicon.xml" quick_sat:0)"
# shellcheck disable=SC2086
u1="$(python3 "$(dirname "$0")/qpix.py" uniform "$ROW_DIR/b-badicon.png" $s1 6)"
# shellcheck disable=SC2086
u0="$(python3 "$(dirname "$0")/qpix.py" uniform "$ROW_DIR/b-badicon.png" $s0 6)"
note "quick_sat:1 $s1 -> $u1; quick_sat:0 $s0 -> $u0"
assert_eq "quick_sat:1's interior is its fill only (± 2): no glyph" UNIFORM "${u1%% *}"
assert_eq "quick_sat:0's interior is NOT uniform: its glyph is drawn" VARIED "${u0%% *}"
S="$(quick_since "$MARK")"
assert_contains "icon failed line" "[quick] satellite 1 icon failed app.tileshell.testclient.b/qa_noicon: " "$S"
MARK="$(ring_mark)"
tap_node "$ROW_DIR/b-badicon.xml" quick_sat:1; sleep 3
assert_contains "the no-icon satellite still runs" "[quick] tap satellite 1 app.tileshell.testclient.b/qa_noicon: startShortcut ok" "$(quick_since "$MARK")"
verb_b_start reset

log "--- no burst: Weather (shell, declares none), folder:qa, the Unassigned MAIL slot, a secondary tile ---"
for spec in "tile:shell:weather|weather|shell:weather: no shortcuts" "tile:folder:qa|folder|folder:qa: folder" "tile:slot:MAIL|unassigned|slot:MAIL: no app"; do
  IFS='|' read -r node tag reason <<< "$spec"
  MARK="$(ring_mark)"
  hold_at "$node" "$tag"
  assert_eq "$tag: edit mode on" yes "$(has_node "$ROW_DIR/$tag.xml" edit_disc:unpin)"
  assert_eq "$tag: no quick_burst" no "$(has_node "$ROW_DIR/$tag.xml" quick_burst)"
  S="$(quick_since "$MARK")"
  assert_contains "$tag: reason" "[quick] no burst on $reason" "$S"
  [ "$tag" = weather ] && assert_contains "weather: its activity was asked (T11-22)" "[quick] shortcuts for app.tileshell/.weather.WeatherActivity/0: 0 (0 shown)" "$S"
  c6
done

# A secondary tile, pinned through phase 02 E5's route: tileclient-a asks, Start shows the band, accept.
adb shell am start -n "$A_PKG/app.tileshell.testclient.VerbActivity" --es verb secondary.requestCreate --es tileId qa11 \
  --es displayName QA11 --es arguments "x=1" --es size medium --ez logo true >/dev/null 2>&1
sleep 2.5
adb shell input keyevent KEYCODE_HOME; sleep 3
qdump "$ROW_DIR/pin-prompt.xml"
acc="$(center "$ROW_DIR/pin-prompt.xml" secondary_pin_prompt_accept 2>/dev/null || python3 - "$ROW_DIR/pin-prompt.xml" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for m in re.finditer(r'<node[^>]*>', s):
    n = m.group(0); t = re.search(r'text="([^"]*)"', n); b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and b and t.group(1).strip().lower() == "pin":
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)"
note "pin prompt accept at [$acc]"
if [ -n "$acc" ]; then read -r X Y <<< "$acc"; tap_xy "$X" "$Y"; sleep 2; fi
SEC="secondary:$A_PKG:qa11"
MARK="$(ring_mark)"
qdump "$ROW_DIR/secondary-rest.xml"
if [ "$(has_node "$ROW_DIR/secondary-rest.xml" "tile:$SEC")" = no ]; then
  adb shell input swipe 540 1600 540 700 400; sleep 1.5   # it is appended at the end of Start
fi
hold_at "tile:$SEC" secondary
assert_eq "secondary: edit mode on" yes "$(has_node "$ROW_DIR/secondary.xml" edit_disc:unpin)"
assert_eq "secondary: no quick_burst" no "$(has_node "$ROW_DIR/secondary.xml" quick_burst)"
assert_contains "secondary: reason" "[quick] no burst on $SEC: secondary tile" "$(quick_since "$MARK")"
restore baseline_layout.json
row_end

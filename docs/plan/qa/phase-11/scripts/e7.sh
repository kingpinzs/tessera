#!/usr/bin/env bash
# E7 — geometry, `[quick] satellite i rest=` lines primary, the dump as corroboration (C-10), in every arrangement:
# corner (the main baseline), line above (bottom row; a WIDE tile in the 2-column grid's first row), line below
# (the grid's top-left tile), corner above a band's rules, and after a scroll. In every case no satellite or label
# lies on the held tile (T11-27). Phase doc E7 (T11-18, T11-27, T11-28, T11-35).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E7 "geometry: corner, line above, line below, band, scrolled; rest= equals the dump; nothing on the held tile"
seed_fixtures
G="python3 $(dirname "$0")/e7_geom.py"
v() { awk -v k="$1" '$1==k{$1=""; sub(/^ /,""); print}' "$2"; }

# burst_geom <case> <tile id>: hold the tile, wait for rest (rest= lines come once the entry has settled), measure.
burst_geom() {
  local c="$1" id="$2" mark
  qdump "$ROW_DIR/$c-rest.xml"
  read -r X Y <<< "$(center "$ROW_DIR/$c-rest.xml" "$id")"
  mark="$(ring_mark)"
  hold "$X" "$Y" 1.0; sleep 1.2
  qdump "$ROW_DIR/$c.xml"; screencap "$ROW_DIR/$c.png"
  rest_lines "$mark" > "$ROW_DIR/$c.rest"
  $G "$ROW_DIR/$c.xml" "$id" "$ROW_DIR/$c.rest" > "$ROW_DIR/$c.geom"
  note "$c: $(tr '\n' ';' < "$ROW_DIR/$c.geom")"
  assert_eq "$c: every rest= line equals the dump (± 1 px)" ok "$(r="$(v rest_vs_dump "$ROW_DIR/$c.geom")"; [ -n "$r" ] && [ "$r" != missing ] && [ "$r" -le 1 ] && echo ok || echo "$r")"
  assert_eq "$c: no satellite or label on the held tile" 0 "$(v on_tile "$ROW_DIR/$c.geom")"
}

log "--- corner: the fixture MEDIUM in the grid's middle column ---"
restore baseline_layout.json
burst_geom corner "tile:$A_KEY"
assert_eq "corner: arrangement" corner "$(v arrangement "$ROW_DIR/corner.geom")"
assert_eq "corner: every satellite diagonally outside its corner (nothing clamped)" yes "$(v diagonal "$ROW_DIR/corner.geom")"
for i in 0 1 2 3; do
  read -r w h <<< "$(v "size_$i" "$ROW_DIR/corner.geom")"
  assert_within "corner: satellite $i width 164 ± 3" 164 "$w" 3
  assert_within "corner: satellite $i height 164 ± 3" 164 "$h" 3
  read -r sx sy <<< "$(v "standoff_$i" "$ROW_DIR/corner.geom")"
  assert_within "corner: satellite $i inner corner 48 ± 3 px out on x (16 epx)" 48 "$sx" 3
  assert_within "corner: satellite $i inner corner 48 ± 3 px out on y (16 epx)" 48 "$sy" 3
done
assert_eq "corner: labels above the top pair, below the bottom pair" "above above below below" \
  "$(for i in 0 1 2 3; do v "label_side_$i" "$ROW_DIR/corner.geom"; done | tr '\n' ' ' | sed 's/ $//')"
cp "$ROW_DIR/corner.geom" "$ROW_DIR/corner-reference.geom"
c6

log "--- line above: the fixture in the bottom tile row ---"
restore baseline_layout-bottomrow.json
burst_geom bottomrow "tile:dock:$A_KEY"
assert_eq "bottomrow: arrangement" line_above "$(v arrangement "$ROW_DIR/bottomrow.geom")"
rowtop="$(rects "$ROW_DIR/bottomrow.xml" "tile:dock:" | awk 'NR==1||$3<m{m=$3} END{print m}')"
satbot="$(rects "$ROW_DIR/bottomrow.xml" "quick_sat:" | awk '{if($5>m)m=$5} END{print m}')"
note "row top (smallest top of tile:dock:*) $rowtop, satellite bottoms $satbot"
assert_within "bottomrow: satellites' bottoms one gutter (13 px) above the row's top" 13 "$((rowtop - satbot))" 2
assert_eq "bottomrow: labels above" "above above above above" "$(for i in 0 1 2 3; do v "label_side_$i" "$ROW_DIR/bottomrow.geom"; done | tr '\n' ' ' | sed 's/ $//')"
assert_eq "bottomrow: one gutter between neighbours" "13 13 13" "$(v row_gaps "$ROW_DIR/bottomrow.geom" | awk '{for(i=1;i<=NF;i++) printf "%s%d", (i>1?" ":""), ($i>=12&&$i<=14?13:$i)}')"
c6

log "--- line below: the fixture at the grid's top-left (T11-35) ---"
restore baseline_layout-topleft.json
burst_geom topleft "tile:$A_KEY"
assert_eq "topleft: arrangement" line_below "$(v arrangement "$ROW_DIR/topleft.geom")"
assert_eq "topleft: labels below (the outer side)" "below below below below" "$(for i in 0 1 2 3; do v "label_side_$i" "$ROW_DIR/topleft.geom"; done | tr '\n' ' ' | sed 's/ $//')"
read -r el et er eb <<< "$(v extent "$ROW_DIR/topleft.geom")"
dtop="$(rects "$ROW_DIR/topleft.xml" "tile:dock:" | awk 'NR==1||$3<m{m=$3} END{print m}')"
assert_eq "topleft: inside the page area (below the 84-px status bar, above the row, inside the margins)" ok \
  "$([ "$et" -ge 84 ] && [ "$eb" -le $((dtop - 12)) ] && [ "$el" -ge 9 ] && [ "$er" -le 1065 ] && echo ok || echo "$el $et $er $eb vs row $dtop")"
c6

log "--- the 2-column grid, the fixture WIDE in the first row: a line (build finding: the top pair cannot clear the bar) ---"
set_more_tiles off
restore baseline_layout-wide.json
qdump "$ROW_DIR/wide-check.xml"
read -r wl wt wr wb <<< "$(bounds "$ROW_DIR/wide-check.xml" "tile:$A_KEY")"
assert_eq "wide: the fixture spans the grid's width" ok "$([ "$wl" -le 12 ] && [ "$wr" -ge 1060 ] && echo ok || echo "$wl..$wr")"
burst_geom wide "tile:$A_KEY"
case "$(v arrangement "$ROW_DIR/wide.geom")" in line_above|line_below) r=PASS ;; *) r=FAIL ;; esac
_verdict "$r" "wide: a line arrangement" "$(v arrangement "$ROW_DIR/wide.geom")"
c6
set_more_tiles on

log "--- band: the fixture inside folder:qa's expanded band ---"
restore baseline_layout-band.json
qdump "$ROW_DIR/band-pre.xml"; tap_node "$ROW_DIR/band-pre.xml" tile:folder:qa; sleep 1.5
burst_geom band "tile:member:$A_KEY"
assert_eq "band: corner arrangement" corner "$(v arrangement "$ROW_DIR/band.geom")"
assert_eq "band: the band's rules are there" yes "$(has_node "$ROW_DIR/band.xml" folder_band_top:qa)"
c6

log "--- scrolled: the tall baseline scrolled by a slow drag, then the hold (T11-18) ---"
restore baseline_layout-tall.json
qdump "$ROW_DIR/tall-before.xml"; b0="$(bounds "$ROW_DIR/tall-before.xml" "tile:$A_KEY")"
adb shell input swipe 540 1700 540 1580 1500; sleep 1.5
qdump "$ROW_DIR/tall-after.xml"; b1="$(bounds "$ROW_DIR/tall-after.xml" "tile:$A_KEY")"
dy="$(python3 -c 'import sys; print(int(sys.argv[1].split()[1]) - int(sys.argv[2].split()[1]))' "$b0" "$b1")"
assert_within "tall: Start scrolled by the slow drag (60-120 px)" 90 "$dy" 30
burst_geom tall "tile:$A_KEY"
rel="$(python3 - "$ROW_DIR/corner-reference.geom" "$ROW_DIR/tall.geom" <<'PY'
import sys
def load(p):
    d = {}
    for l in open(p):
        k, *v = l.split()
        d[k] = v
    return d
a, b = load(sys.argv[1]), load(sys.argv[2])
def rel(d, i):
    t = list(map(int, d["tile"])); s = list(map(int, d["sat_%d" % i]))
    return [s[0] - t[0], s[1] - t[1], s[2] - t[0], s[3] - t[1]]
worst = max(abs(x - y) for i in range(4) for x, y in zip(rel(a, i), rel(b, i)))
print(worst)
PY
)"
assert_within "tall: satellites relative to the tile equal the corner case (± 1 px)" 0 "$rel" 1
restore baseline_layout.json
row_end

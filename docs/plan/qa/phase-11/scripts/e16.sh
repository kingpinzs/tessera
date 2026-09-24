#!/usr/bin/env bash
# E16 — the promoted tile (T11-2, T11-27, T11-36): a hold on the last-opened app's tile enters edit mode, the
# promotion is suspended, and the burst opens around the tile in its grid cell — at the bounds E1's HELD dump gives
# it, with E7's corner offsets. Off screen: with Start scrolled until that cell is off the page, the hold opens no
# burst and says so.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E16 "the promoted tile bursts in its grid cell; a cell off the page opens none (off screen)"
seed_fixtures
restore baseline_layout.json
promote() {
  qdump "$ROW_DIR/.pre.xml"
  read -r X Y <<< "$(center "$ROW_DIR/.pre.xml" "tile:$A_KEY")"
  tap_xy "$X" "$Y"; sleep 3
  adb shell input keyevent KEYCODE_HOME; sleep 3.5
}
promote
qdump "$ROW_DIR/promoted.xml"
assert_eq "the fixture is promoted (recent_app_row)" yes "$(has_node "$ROW_DIR/promoted.xml" recent_app_row)"
read -r X Y <<< "$(center "$ROW_DIR/promoted.xml" "tile:$A_KEY")"
MARK="$(ring_mark)"
hold_down "$X" "$Y"; sleep 1.0; sleep 1.2
qdump "$ROW_DIR/held.xml"; screencap "$ROW_DIR/held.png"
assert_eq "recent_app_row gone (edit mode suspends the promotion)" no "$(has_node "$ROW_DIR/held.xml" recent_app_row)"
assert_eq "edit mode on" yes "$(has_node "$ROW_DIR/held.xml" edit_disc:unpin)"
ref="$(bounds "$QA11/E1/held-reference.xml" "tile:$A_KEY")"
now="$(bounds "$ROW_DIR/held.xml" "tile:$A_KEY")"
r="$(python3 -c '
import sys
a=sys.argv[1].split(); b=sys.argv[2].split()
ok=len(a)==4 and len(b)==4 and all(abs(int(x)-int(y))<=1 for x,y in zip(a,b))
print(("PASS" if ok else "FAIL")+"|E1 held "+" ".join(a)+" vs now "+" ".join(b))' "$ref" "$now")"
_verdict "${r%%|*}" "the fixture at its grid cell as E1's held dump gives it (± 1 px, T11-36)" "${r##*|}"
S="$(quick_since "$MARK")"
assert_contains "burst on the fixture" "[quick] burst on $A_KEY: 4 satellites" "$S"
rest_lines "$MARK" > "$ROW_DIR/held.rest"
python3 "$(dirname "$0")/e7_geom.py" "$ROW_DIR/held.xml" "tile:$A_KEY" "$ROW_DIR/held.rest" > "$ROW_DIR/held.geom"
assert_eq "no satellite or label on the held tile" 0 "$(awk '$1=="on_tile"{print $2}' "$ROW_DIR/held.geom")"
rel="$(python3 - "$QA11/E7/corner-reference.geom" "$ROW_DIR/held.geom" <<'PY'
import sys
def load(p):
    d = {}
    for l in open(p):
        k, *v = l.split(); d[k] = v
    return d
a, b = load(sys.argv[1]), load(sys.argv[2])
def rel(d, i):
    t = list(map(int, d["tile"])); s = list(map(int, d["sat_%d" % i]))
    return [s[0] - t[0], s[1] - t[1], s[2] - t[0], s[3] - t[1]]
print(max(abs(x - y) for i in range(4) for x, y in zip(rel(a, i), rel(b, i))))
PY
)"
assert_within "satellites relative to the tile equal E7's corner case (± 1 px)" 0 "$rel" 1
hold_up "$X" "$Y"; sleep 0.8
c6

log "--- off screen: the tall layout scrolled until the fixture's grid cell is off the page ---"
restore baseline_layout-tall.json
promote
for _ in 1 2 3 4 5 6; do adb shell input swipe 540 1500 540 700 600; sleep 1; done
qdump "$ROW_DIR/offscreen-pre.xml"; screencap "$ROW_DIR/offscreen-pre.png"
assert_eq "the fixture is still promoted" yes "$(has_node "$ROW_DIR/offscreen-pre.xml" recent_app_row)"
# The scroll, from any grid tile still on screen: GridPack's first fit over the tall layout WITHOUT the promoted fixture
# gives each tile's content y (84 + unitY x 177.94), and its dump top is that minus the scroll. The fixture's cell in
# edit mode (content y 439.9-782.6, the grid re-packed WITH it) contracted 0.90 about y 1111.5 must end above the bar.
cellb="$(python3 - "$QA11/baseline_layout-tall.json" "$ROW_DIR/offscreen-pre.xml" "$A_KEY" <<'PY'
import json, re, sys
d = json.load(open(sys.argv[1])); dump = open(sys.argv[2]).read(); A = sys.argv[3]
span = {"SMALL": (1, 1), "MEDIUM": (2, 2), "WIDE": (4, 2)}
def pack(items, across=6):
    rows, out = [], {}
    def row(y):
        while len(rows) <= y: rows.append([False] * across)
        return rows[y]
    for key, size in items:
        w, h = span[size]; y = 0
        while True:
            x = next((x for x in range(across - w + 1) if all(not row(y + dy)[x + dx] for dy in range(h) for dx in range(w))), None)
            if x is not None:
                for dy in range(h):
                    for dx in range(w): row(y + dy)[x + dx] = True
                out[key] = y; break
            y += 1
    return out
promoted = pack([(o["key"], o["size"]) for o in d["order"] if o["key"] != A])
s = None
for key, uy in promoted.items():
    m = re.search(r'resource-id="tile:%s"[^>]*bounds="\[(-?\d+),(-?\d+)\]' % re.escape(key), dump)
    if m and int(m.group(2)) > 84:
        s = 84 + uy * 177.9375 - int(m.group(2)); break
if s is None: print("unknown"); sys.exit()
c = 1111.5 + (439.9 + 171.4 - s - 1111.5) * 0.9
print(int(c + 171.4), int(s))
PY
)"
note "scroll ${cellb#* } px -> the fixture cell's edit-mode bottom at ${cellb% *} px"
cellb="${cellb% *}"
assert_eq "precondition: the fixture's grid cell lies wholly above the page area ($cellb < 84)" ok "$([ "$cellb" -lt 84 ] && echo ok || echo no)"
read -r X Y <<< "$(center "$ROW_DIR/offscreen-pre.xml" "tile:$A_KEY")"
MARK="$(ring_mark)"
hold_down "$X" "$Y"; sleep 1.0; qdump "$ROW_DIR/offscreen.xml"; hold_up "$X" "$Y"; sleep 0.8
S="$(ring_since "$MARK")"
assert_contains "edit mode entered" "[edit] hold 783ms on $A_KEY: edit mode on" "$S"
assert_eq "no quick_burst" no "$(has_node "$ROW_DIR/offscreen.xml" quick_burst)"
assert_contains "reason: off screen" "[quick] no burst on $A_KEY: off screen" "$S"
c6
restore baseline_layout.json
row_end

#!/usr/bin/env bash
# DRAG — the burst across a drag (Jeremy 2026-09-25, "(a)": the satellites hide while the held tile is dragged and come
# back around it where it is dropped, as the discs do; a drag of ANOTHER tile closes the burst, `drag`). His report:
# "once I start moving the tile they disappear and when I stop moving in a blank spot they don't come back but the two
# original ones do". Sub-steps: (a) the held tile dragged to a blank spot; (b) another tile dragged with a burst open.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin DRAG "the burst hides while its tile is dragged and comes back where it is dropped; another tile's drag closes it"
seed_fixtures
restore baseline_layout.json
drag_to() { # x0 y0 x1 y1: press, move in steps, release
  local i n=8
  hold_down "$1" "$2"; sleep 0.3
  for i in $(seq 1 $n); do hold_move $(( $1 + ($3 - $1) * i / n )) $(( $2 + ($4 - $2) * i / n )); sleep 0.08; done
  sleep 0.4
}
around() { # dump tile-id -> "ok" when every satellite square is outside the tile and its centre within 1.6 tile diagonals
  python3 - "$1" "$2" <<'PY'
import math, re, sys
s = open(sys.argv[1]).read()
def rect(i):
    m = re.search(r'resource-id="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(i), s); return tuple(map(int, m.groups())) if m else None
t = rect(sys.argv[2]); sats = [rect("quick_sat:%d" % i) for i in range(4)]; sats = [r for r in sats if r]
if not t or not sats: print("no tile or no satellites"); sys.exit()
tc = ((t[0] + t[2]) / 2, (t[1] + t[3]) / 2); diag = math.hypot(t[2] - t[0], t[3] - t[1])
bad = []
for i, r in enumerate(sats):
    c = ((r[0] + r[2]) / 2, (r[1] + r[3]) / 2)
    inside = r[0] < t[2] and r[2] > t[0] and r[1] < t[3] and r[3] > t[1]
    if inside or math.hypot(c[0] - tc[0], c[1] - tc[1]) > 1.6 * diag: bad.append(i)
print("ok" if not bad else f"satellites {bad} not around the tile {t}")
PY
}

log "--- (a) the held tile dragged to a blank spot: hidden during the drag, back around its new cell after the drop ---"
qdump "$ROW_DIR/a-rest.xml"
read -r AX AY <<< "$(center "$ROW_DIR/a-rest.xml" "tile:$A_KEY")"
hold "$AX" "$AY" 1.0
qdump "$ROW_DIR/a-burst.xml"
assert_eq "the hold opened the burst" yes "$(has_node "$ROW_DIR/a-burst.xml" quick_burst)"
read -r BX BY <<< "$(empty_point "$ROW_DIR/a-burst.xml" 1350 1800)"
note "the blank spot: ($BX,$BY); the fixture from ($AX,$AY)"
MARK="$(ring_mark)"
drag_to "$AX" "$AY" "$BX" "$BY"
qdump "$ROW_DIR/a-dragging.xml"; screencap "$ROW_DIR/a-dragging.png"
assert_eq "while dragging: no satellites" no "$(has_node "$ROW_DIR/a-dragging.xml" quick_burst)"
assert_contains "ring: burst hidden: drag" "[quick] burst hidden: drag $A_KEY" "$(quick_since "$MARK")"
hold_up "$BX" "$BY"; sleep 1.5
qdump "$ROW_DIR/a-dropped.xml"; screencap "$ROW_DIR/a-dropped.png"
S="$(ring_since "$MARK")"
note "drop: $(echo "$S" | grep -o '\[edit\] drop: .*' | tail -1)"
assert_ne "the fixture moved to a new cell" "$(bounds "$ROW_DIR/a-burst.xml" "tile:$A_KEY")" "$(bounds "$ROW_DIR/a-dropped.xml" "tile:$A_KEY")"
assert_eq "after the drop: the satellites are back" yes "$(has_node "$ROW_DIR/a-dropped.xml" quick_burst)"
assert_eq "…around the fixture at its new cell" ok "$(around "$ROW_DIR/a-dropped.xml" "tile:$A_KEY")"
assert_eq "…with the discs (edit mode, the fixture held)" yes "$(has_node "$ROW_DIR/a-dropped.xml" edit_disc:unpin)"
assert_contains "ring: burst back after the drop" "[quick] burst back after the drop: $A_KEY" "$S"
assert_contains "ring: it opened again (burst on)" "[quick] burst on $A_KEY" "$(echo "$S" | awk '/burst back after the drop/{f=1} f')"
assert_contains "ring: the open motion played again" "[quick] motion open $A_KEY" "$(echo "$S" | awk '/burst back after the drop/{f=1} f')"
assert_absent "ring: nothing closed it" "burst closed" "$(echo "$S" | grep -F '[quick]')"
c6
restore baseline_layout.json

log "--- (b) another tile dragged while the burst is open: the burst closes (drag) and does not come back ---"
qdump "$ROW_DIR/b-rest.xml"
read -r AX AY <<< "$(center "$ROW_DIR/b-rest.xml" "tile:$A_KEY")"
hold "$AX" "$AY" 1.0
qdump "$ROW_DIR/b-burst.xml"
assert_eq "the hold opened the burst" yes "$(has_node "$ROW_DIR/b-burst.xml" quick_burst)"
read -r TX TY <<< "$(center "$ROW_DIR/b-burst.xml" tile:shell:cortana)"
read -r BX BY <<< "$(empty_point "$ROW_DIR/b-burst.xml" 1350 1800)"
MARK="$(ring_mark)"
drag_to "$TX" "$TY" "$BX" "$BY"; hold_up "$BX" "$BY"; sleep 1.5
qdump "$ROW_DIR/b-dropped.xml"
S="$(quick_since "$MARK")"
assert_contains "ring: burst closed: drag" "burst closed: drag" "$S"
assert_absent "ring: not hidden (it was not its own tile)" "burst hidden" "$S"
assert_eq "after the drop: no satellites" no "$(has_node "$ROW_DIR/b-dropped.xml" quick_burst)"
c6
restore baseline_layout.json
row_end

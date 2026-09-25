#!/usr/bin/env bash
# E1 — the burst at the hold: at 783 ms edit mode enters as phase 02 built it AND four satellites open around the
# held tile; letting go leaves both. Phase doc E1 (T11-12, T11-18, T11-44).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E1 "burst at the hold: four satellites One..Four, edit mode on, both stay after UP"
seed_fixtures
restore baseline_layout.json

qdump "$ROW_DIR/rest.xml"
b="$(bounds "$ROW_DIR/rest.xml" "tile:$A_KEY")"
assert_within_rect() { python3 - "$@" <<'PY'
import sys
name, want, got, tol = sys.argv[1], sys.argv[2].split(), sys.argv[3].split(), float(sys.argv[4])
ok = len(got) == 4 and all(abs(int(w) - int(g)) <= tol for w, g in zip(want, got))
print(("PASS" if ok else "FAIL") + "|" + f"want {want} ± {tol:g}, got {got}")
PY
}
r="$(assert_within_rect "fixture cell" "365 439 708 782" "$b" 1)"; _verdict "${r%%|*}" "fixture tile at its middle-column cell at rest (T11-18)" "${r##*|}"
read -r cx cy <<< "$(center "$ROW_DIR/rest.xml" "tile:$A_KEY")"
note "fixture centre $cx $cy"

MARK="$(ring_mark)"
hold_down "$cx" "$cy"; sleep 1.0
qdump "$ROW_DIR/held.xml"; screencap "$ROW_DIR/held.png"
H="$ROW_DIR/held.xml"
assert_eq "quick_burst present while held" yes "$(has_node "$H" quick_burst)"
for i in 0 1 2 3; do assert_eq "quick_sat:$i present" yes "$(has_node "$H" "quick_sat:$i")"; done
labels="$(for i in 0 1 2 3; do node_text "$H" "quick_sat_label:$i"; done | tr '\n' ',')"
assert_eq "labels are ranks 0-3, Five absent" "One,Two,Three,Four," "$labels"
assert_absent "no label Five" 'text="Five"' "$(cat "$H")"
assert_eq "edit_disc:unpin present" yes "$(has_node "$H" edit_disc:unpin)"
assert_eq "edit_disc:resize present" yes "$(has_node "$H" edit_disc:resize)"
assert_contains "dim:* present (edit mode dims the others)" 'resource-id="dim:' "$(cat "$H")"
assert_contains "StartActivity still resumed" "app.tileshell/.StartActivity" "$(resumed)"
S="$(quick_since "$MARK")"
assert_contains "shortcuts line (T11-12)" "[quick] shortcuts for app.tileshell.testclient.a/app.tileshell.testclient.VerbActivity/0: 5 (4 shown: qa_one,qa_two,qa_three,qa_four)" "$S"
assert_contains "burst line" "[quick] burst on $A_KEY: 4 satellites" "$S"

hold_up "$cx" "$cy"; sleep 1.0
qdump "$ROW_DIR/after_up.xml"
A2="$ROW_DIR/after_up.xml"
dd="$(python3 "$QROOT/phase-02/scripts/dumpdiff.py" "$H" "$A2" | tail -1)"
note "dumpdiff held -> after_up: $dd"
assert_eq "tile rectangles unchanged by the UP (dumpdiff)" IDENTICAL "$dd"
same="$(python3 - "$H" "$A2" <<'PY'
import re, sys
def nodes(p):
    s = open(p).read(); out = {}
    for n in re.finditer(r"<node[^>]*>", s):
        n = n.group(0)
        i = re.search(r'resource-id="((?:quick_|edit_disc)[^"]*)"', n)
        b = re.search(r'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"', n)
        t = re.search(r'text="([^"]*)"', n)
        if i and b: out[i.group(1)] = (tuple(map(int, b.groups())), t.group(1) if t else "")
    return out
a, b = nodes(sys.argv[1]), nodes(sys.argv[2])
bad = [k for k in a if k not in b or a[k][1] != b[k][1] or any(abs(x - y) > 1 for x, y in zip(a[k][0], b[k][0]))]
print("SAME" if a and not bad and set(a) == set(b) else "DIFF " + ",".join(sorted(bad or set(a) ^ set(b))))
PY
)"
assert_eq "burst and discs unchanged by the UP (± 1 px, same labels)" SAME "$same"
cp "$H" "$ROW_DIR/held-reference.xml"   # E16 compares its promoted-then-held tile with this (T11-36)
screencap "$ROW_DIR/after_up.png"
c6
row_end

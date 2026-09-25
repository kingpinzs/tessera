#!/usr/bin/env bash
# E2 — the fork, bracketed in time (740 / 830 ms around Edit.HOLD_MS = 783) and in space (a 4-px wobble keeps the
# burst; a 40-px glide is a drag that closes it). Phase doc E2 (RV11; T11-19, T11-45).
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E2 "the fork: 740 ms taps, 830 ms bursts; a 4-px wobble keeps it, a 40-px glide drags and closes it"
seed_fixtures
restore baseline_layout.json
qdump "$ROW_DIR/rest.xml"
read -r cx cy <<< "$(center "$ROW_DIR/rest.xml" "tile:$A_KEY")"

log "--- 740 ms: a tap (the fixture app launches, no hold, no burst) ---"
MARK="$(ring_mark)"
adb shell input swipe "$cx" "$cy" "$cx" "$cy" 740; sleep 2.5
assert_contains "the fixture app launched" "$A_PKG/" "$(resumed)"
# (A dump here would show the fixture app, so it could not fail: the ring slice carries this sub-step, G-E2-1.)
S="$(ring_since "$MARK")"
assert_absent "ring: no [quick] burst on (T11-45)" "[quick] burst on" "$S"
assert_absent "ring: no [edit] hold (T11-45)" "[edit] hold" "$S"
c6

log "--- 830 ms: a hold (edit mode and the burst, nothing launched) ---"
MARK="$(ring_mark)"
adb shell input swipe "$cx" "$cy" "$cx" "$cy" 830; sleep 1.5
qdump "$ROW_DIR/t830.xml"
assert_eq "quick_burst present" yes "$(has_node "$ROW_DIR/t830.xml" quick_burst)"
assert_eq "edit mode on (edit_disc:unpin)" yes "$(has_node "$ROW_DIR/t830.xml" edit_disc:unpin)"
assert_contains "nothing launched: StartActivity resumed" "app.tileshell/.StartActivity" "$(resumed)"
assert_contains "ring: burst on" "[quick] burst on $A_KEY: 4 satellites" "$(ring_since "$MARK")"
c6

log "--- DOWN; 0.9 s; MOVE +4 px; UP: under touchSlop (≈22 px), the burst stays and the tile does not move ---"
MARK="$(ring_mark)"
hold_down "$cx" "$cy"; sleep 0.9
qdump "$ROW_DIR/wobble_pre.xml"
hold_move $((cx + 4)) "$cy"; sleep 0.3
hold_up $((cx + 4)) "$cy"; sleep 1
qdump "$ROW_DIR/wobble_post.xml"
assert_eq "quick_burst still present after the wobble" yes "$(has_node "$ROW_DIR/wobble_post.xml" quick_burst)"
assert_eq "tile bounds unchanged by the wobble" "$(bounds "$ROW_DIR/wobble_pre.xml" "tile:$A_KEY")" "$(bounds "$ROW_DIR/wobble_post.xml" "tile:$A_KEY")"
assert_absent "ring: no drag" "[quick] burst closed: drag" "$(ring_since "$MARK")"
c6

log "--- DOWN; 0.9 s; glide +40 px (asserted BEFORE the UP): a drag, the burst closes; after UP the tile is home ---"
MARK="$(ring_mark)"
hold_down "$cx" "$cy"; sleep 0.9
qdump "$ROW_DIR/glide_pre.xml"
held="$(bounds "$ROW_DIR/glide_pre.xml" "tile:$A_KEY")"
for dx in 10 20 30 40; do hold_move $((cx + dx)) "$cy"; sleep 0.1; done
sleep 0.5
qdump "$ROW_DIR/glide_during.xml"; screencap "$ROW_DIR/glide_during.png"
assert_eq "no quick_burst while dragging" no "$(has_node "$ROW_DIR/glide_during.xml" quick_burst)"
dragged="$(bounds "$ROW_DIR/glide_during.xml" "tile:drag:$A_KEY")"
moved="$(python3 -c 'import sys; a=sys.argv[1].split(); b=sys.argv[2].split(); print(abs(int(b[0])-int(a[0])) if len(a)==4 and len(b)==4 else -1)' "$held" "$dragged")"
note "held $held, dragged copy $dragged"
assert_within "the dragged tile follows the finger (≥ 30 px from its held bounds)" 40 "$moved" 10
S="$(ring_since "$MARK")"
assert_contains "ring: [edit] drag start" "[edit] drag start $A_KEY" "$S"
assert_contains "ring: burst closed: drag" "[quick] burst closed: drag" "$S"
hold_up $((cx + 40)) "$cy"; sleep 1.2
qdump "$ROW_DIR/glide_post.xml"
r="$(python3 -c '
import sys
a=sys.argv[1].split(); b=sys.argv[2].split()
ok=len(a)==4 and len(b)==4 and all(abs(int(x)-int(y))<=1 for x,y in zip(a,b))
print(("PASS" if ok else "FAIL")+"|held "+" ".join(a)+" -> after UP "+" ".join(b))' "$held" "$(bounds "$ROW_DIR/glide_post.xml" "tile:$A_KEY")")"
_verdict "${r%%|*}" "after UP the fixture is back in its own cell (± 1 px)" "${r##*|}"
c6
row_end

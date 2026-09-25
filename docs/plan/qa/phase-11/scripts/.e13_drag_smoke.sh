. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin E13-dragsmoke "E13's hold-and-drag sub-step only (the 2026-09-25 drag ruling)"
seed_fixtures
log "--- phase 02's hold and drag as one gesture: no burst during the drag (hidden), back after the drop ---"
restore baseline_layout-pre-11.json
qdump "$ROW_DIR/p02-rest.xml"
read -r X Y <<< "$(center "$ROW_DIR/p02-rest.xml" tile:slot:PEOPLE)"
MARK="$(ring_mark)"
hold_down "$X" "$Y"; sleep 0.9
for dy in 60 120 180 240 300; do hold_move "$X" $((Y + dy)); sleep 0.1; done
sleep 0.4; qdump "$ROW_DIR/p02-dragging.xml"
assert_eq "no quick_burst during the drag" no "$(has_node "$ROW_DIR/p02-dragging.xml" quick_burst)"
hold_up "$X" $((Y + 300)); sleep 1.5
qdump "$ROW_DIR/p02-dropped.xml"
assert_contains "ring: burst hidden: drag" "[quick] burst hidden: drag slot:PEOPLE" "$(quick_since "$MARK")"
assert_contains "ring: burst back after the drop" "[quick] burst back after the drop: slot:PEOPLE" "$(quick_since "$MARK")"
assert_eq "after the drop the burst is back around PEOPLE" yes "$(has_node "$ROW_DIR/p02-dropped.xml" quick_burst)"
c6

row_end

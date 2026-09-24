#!/usr/bin/env bash
# The cold-entry finding behind E6's warm rule (G-README-1): the first hold after the shell starts, with and without a
# burst, measured by `dumpsys gfxinfo app.tileshell` (frame stats) — a hold on folder:qa (no burst) and on the fixture.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin COLDJANK "gfxinfo of the first hold after the shell starts: folder (no burst) vs the fixture (burst)"
seed_fixtures
restore baseline_layout.json
for run in 1 2; do
  for target in "folder:tile:folder:qa" "fixture:tile:$A_KEY"; do
    name="${target%%:*}"; id="${target#*:}"
    adb shell am force-stop app.tileshell; adb shell input keyevent KEYCODE_HOME; sleep 5
    qdump "$ROW_DIR/rest-$name-$run.xml"
    read -r X Y <<< "$(center "$ROW_DIR/rest-$name-$run.xml" "$id")"
    adb shell dumpsys gfxinfo app.tileshell reset >/dev/null 2>&1
    hold_down "$X" "$Y"; sleep 1.3; hold_up "$X" "$Y"; sleep 0.5
    adb shell dumpsys gfxinfo app.tileshell > "$ROW_DIR/gfxinfo-$name-$run.txt"
    note "$name run $run: $(grep -E 'Total frames rendered|Janky frames:|90th percentile|99th percentile' "$ROW_DIR/gfxinfo-$name-$run.txt" | head -4 | tr -s ' ' | tr '\n' ';')"
    assert_ne "$name run $run: gfxinfo captured" 0 "$(grep -c 'Total frames rendered' "$ROW_DIR/gfxinfo-$name-$run.txt")"
  done
done
c6
row_end

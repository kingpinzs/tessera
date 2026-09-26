#!/usr/bin/env bash
# L13-3 regression row (INDEX ledger; review/2026-09-26-L13-345-fix-plan.md): a hold right after Back closed the app
# list's band opens the band again — no down is lost to the band's stale scrim in the frame between the Back and the
# band leaving. Before the fix, 5 of 70 holds at ~4 ms after Back were lost on an idle host
# (qa/phase-13/L13-3-investigation/). The trials are the investigator's (scripts/l13_3_race.sh, copied): hold A opens
# the band, Back, then hold B at once; B must open it. Then EDGE_RAPID's own host loop, three times.
#   l13_3_row.sh [n]    (n trials at gap 0, default 70)
. "$(dirname "$0")/lib.sh"
. "$(dirname "$0")/p13.sh"
N="${1:-70}"
RACE="$(dirname "$0")/l13_3_race.sh"

row_begin L13_3 "a hold right after Back closes the band opens it again (n=$N at gap 0, then EDGE_RAPID's loop x3)"
assert_eq "wake: the device is awake" "Awake" "$(wake_device)"
bash "$RACE" setup - checker > "$ROW_DIR/setup.txt" 2>&1
note "setup: $(tail -1 "$ROW_DIR/setup.txt")"
bash "$RACE" trials "$ROW_DIR/g0" 0 "$N" 100 > "$ROW_DIR/trials.out" 2>&1
RES="$(grep '^RESULT' "$ROW_DIR/g0/summary.txt")"
note "$RES"
read -r OK TOTAL BAD <<< "$(echo "$RES" | python3 -c "
import re, sys
m = re.search(r'B opened (\d+) / (\d+) \(bad setups (\d+)\)', sys.stdin.read())
print(*(m.groups() if m else ('', '', '')))")"
assert_eq "every trial set up (hold A opened the band)" "0" "${BAD:-x}"
assert_eq "every hold right after Back opened the band: $OK of $TOTAL" "$TOTAL" "${OK:-x}"
assert_eq "all $N trials counted" "$N" "${TOTAL:-x}"
for k in 1 2 3; do
  bash "$RACE" hostloop "$ROW_DIR/hostloop-$k" 10 > /dev/null 2>&1
  S="$(cat "$ROW_DIR/hostloop-$k/summary.txt")"
  note "hostloop $k: $S"
  assert_contains "EDGE_RAPID's loop $k: every hold opened the band" "bands 10 / 10" "$S"
  assert_contains "EDGE_RAPID's loop $k: still on the app list after" "on app list after: yes" "$S"
done
clear_background
show_start 3
row_end

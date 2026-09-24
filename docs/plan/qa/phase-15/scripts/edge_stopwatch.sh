#!/usr/bin/env bash
# EDGE_STOPWATCH — the phase 15 Stopwatch edge cases: 1,000 laps (the list stays scrollable and a gesture-driver dump
# of the top rows is available within 3 s), and the app killed between two laps (the laps survive, the lap after the
# kill continues the count). The laps are taken through the gesture driver's timed `script` op (100 taps per script)
# — real taps on the Flag button. Restore: the stopwatch reset.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin EDGE_STOPWATCH "stopwatch: 1,000 laps; killed between laps"
assert_clock_empty "baseline"
stopwatch_baseline "baseline"
open_clock stopwatch
gdump "$ROW_DIR/sw0.xml"
gtap "$ROW_DIR/sw0.xml" stopwatch_play; sleep 1
LB=""
for _ in 1 2 3; do gdump "$ROW_DIR/sw_run.xml"; LB="$(bounds "$ROW_DIR/sw_run.xml" stopwatch_lap)"; [ -n "$LB" ] && break; sleep 1; done
assert_ne "the running stopwatch shows the Flag button (stopwatch_lap in the dump)" "" "$LB"
if [ -z "$LB" ]; then log "      ROW STOPPED: no Flag button could be dumped (run 1: an unbound \$1 here)"; row_end; exit $?; fi
# shellcheck disable=SC2086
set -- $LB; LX=$(( ($1 + $3) / 2 )); LY=$(( ($2 + $4) / 2 ))
laps_now() { store_stopwatch | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["laps"]))'; }
tap_burst() { # n taps 60 ms apart through the gesture driver
  local script="" i
  for i in $(seq 1 "$1"); do script="${script}tap $LX $LY; sleep 60; "; done
  adb shell am instrument -r -w -e op script -e script "\"${script%; }\"" "$DRV_RUNNER" > "$ROW_DIR/.burst.txt" 2>&1
  grep -q 'gesture.ok=true' "$ROW_DIR/.burst.txt"
}

# ---- killed between two laps (early, so the continuity is visible) -------------------------------------------------------------------------
CRASHES0="$(shell_crash_count)"
gtap "$ROW_DIR/sw_run.xml" stopwatch_lap; sleep 1.5
if [ "$(shell_crash_count)" != "$CRASHES0" ]; then
  # PRODUCT DEFECT (E7/DEFECT.md): the first lap crashes the app, so neither edge case can be driven. Stopped.
  close_crash_dialog
  assert_eq "the first lap did not crash the app (E7/DEFECT.md: $(shell_last_crash))" "$CRASHES0" "$(shell_crash_count)"
  log "      ROW STOPPED at the lap-row crash (E7/DEFECT.md): 1,000 laps and killed-between-laps not run in this pass"
  stopwatch_store_reset
  assert_clock_empty "restore"
  row_end
  exit $?
fi
tap_burst 4; sleep 1
assert_eq "five laps taken" 5 "$(laps_now)"
kill9_shell >/dev/null
open_clock stopwatch; sleep 1
gdump "$ROW_DIR/after_kill.xml"
assert_eq "after the kill the five laps are listed (stopwatch_lap:5)" yes "$(has_node "$ROW_DIR/after_kill.xml" 'stopwatch_lap:5')"
gtap "$ROW_DIR/after_kill.xml" stopwatch_lap; sleep 1
assert_eq "the next lap after the kill is lap 6" 6 "$(laps_now)"
gdump "$ROW_DIR/lap6.xml"
assert_eq "… listed on top as stopwatch_lap:6" yes "$(has_node "$ROW_DIR/lap6.xml" 'stopwatch_lap:6')"
assert_eq "the laps are strictly increasing splits" yes "$(store_stopwatch | python3 -c 'import json,sys; l=json.load(sys.stdin)["laps"]; print("yes" if all(b > a for a, b in zip(l, l[1:])) else "no")')"

# ---- 1,000 laps ----------------------------------------------------------------------------------------------------------------------------
T0="$(date +%s)"
while [ "$(laps_now)" -lt 1000 ]; do
  tap_burst 100 || note "a burst reported not ok: $(grep -m1 'gesture.error' "$ROW_DIR/.burst.txt")"
  note "laps: $(laps_now) at +$(( $(date +%s) - T0 )) s"
done
N="$(laps_now)"
assert_eq "1,000 laps (or more) are in the store" yes "$([ "$N" -ge 1000 ] && echo yes || echo "no ($N)")"
D0="$(date +%s%3N)"
gdump "$ROW_DIR/thousand.xml"; screencap "$ROW_DIR/thousand.png"
D1="$(date +%s%3N)"
assert_eq "a gesture-driver dump of the list is available within 3 s ($(( D1 - D0 )) ms)" yes "$([ $(( D1 - D0 )) -le 3000 ] && echo yes || echo no)"
assert_eq "the top row is the newest lap (stopwatch_lap:$N)" yes "$(has_node "$ROW_DIR/thousand.xml" "stopwatch_lap:$N")"
TOP1="$(top_of() { echo "$1" | cut -d' ' -f2; }; top_of "$(bounds "$ROW_DIR/thousand.xml" "stopwatch_lap:$N")")"
adb shell input swipe 540 1900 540 700 300; sleep 1
gdump "$ROW_DIR/thousand_scrolled.xml"
assert_eq "the list scrolls (the newest lap left the top / older laps came in)" yes "$([ "$(has_node "$ROW_DIR/thousand_scrolled.xml" "stopwatch_lap:$(( N - 12 ))")" = yes ] && echo yes || echo no)"
assert_eq "the store still reads as valid JSON with $N laps" "$N" "$(laps_now)"

# ---- restore ---------------------------------------------------------------------------------------------------------------------------------
open_clock stopwatch
gdump "$ROW_DIR/restore.xml"
gtap "$ROW_DIR/restore.xml" stopwatch_play; sleep 1
gdump "$ROW_DIR/restore2.xml"
gtap "$ROW_DIR/restore2.xml" stopwatch_reset; sleep 1.5
assert_eq "restore: the stopwatch is reset" "false []" "$(store_stopwatch | python3 -c 'import json,sys; s=json.load(sys.stdin); print(str(s["running"]).lower(), s["laps"])')"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end

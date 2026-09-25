#!/usr/bin/env bash
# E7 — Stopwatch (phase 15 T15-9, T15-56). Start; the `[stopwatch] elapsed=<ms> uptime=<ms>` lines of two moments
# ≥ 10 s apart satisfy Δelapsed = Δuptime ± 100 ms, and `stopwatch_elapsed` (gesture-driver dump) at each moment agrees
# with its line ± 100 ms plus the dump's own latency (≤ 1 s); Lap adds `stopwatch_lap:1` with the split; the pre-kill
# slice saved; `kill -9` and reopen → still running, the first post-kill line's elapsed minus the last pre-kill line's
# equals their wall= difference ± 1 s; `adb reboot`, the boot poll, `wake_device` = Awake → still running from the
# wall-clock start, the same continuity ± 2 s over the reboot; Reset clears to 0:00.00 and the laps.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/p15.sh"; . "$(dirname "$0")/clock.sh"

row_begin E7 "stopwatch: ring clock vs dump, lap, kill -9, reboot, reset"
assert_clock_empty "baseline"
stopwatch_baseline "baseline"

sw_lines() { printf '%s\n' "$1" | grep -F '[stopwatch]' | grep -F 'elapsed='; }
# The periodic line only ("[stopwatch] elapsed=… uptime=…", StopwatchTab.logStopwatch): the store's own lines carry a
# verb first ("start elapsed=", "lap elapsed=", "reset elapsed=0") and are not the running clock's reading (run 4 on
# 5558 compared a dump against "reset elapsed=0").
tick_line() { printf '%s\n' "$1" | grep -E '\[stopwatch\] elapsed=[0-9]+ uptime=[0-9]+' | head -1; }
# One moment: wait for a NEW periodic line after MARK, then dump at once. The dump that is compared is the gesture
# driver's attempt that returned nodes — gdump retries while the driver returns an EMPTY hierarchy (roots=0; the gate
# pass's moment 2: 20 such attempts, then the plain-dump fallback), and those empty attempts are separate dumps that
# read nothing. That attempt's own clock comes from its status lines in the .drv: `gesture.uptime` (its start, on
# SystemClock.uptimeMillis — the same clock as the line's uptime=) and `gesture.dump.elapsed_ms` (its dump's own
# latency). So the dump's elapsed must equal the line's plus (dump start + half its latency − the line's uptime),
# ± 100 ms + half its latency; the latency itself is asserted ≤ 1 s, and a plain-dump fallback (no timing of its own,
# ≈ 10 s idle wait) fails it. The number of empty attempts before it and the whole retry window are logged as the
# tooling fact they are.
moment() { # label mark -> "line_elapsed dump_elapsed line_uptime dump_uptime dump_latency|fallback"
  local line l d t0 t1 u e
  line="$(tick_line "$(wait_ring "$2" "[stopwatch] elapsed=" 12)")"
  t0="$(device_ms)"; gdump "$ROW_DIR/$1.xml"; t1="$(device_ms)"
  l="$(field_of "$line" elapsed)"; d="$(hms_to_ms "$(node_text "$ROW_DIR/$1.xml" stopwatch_elapsed)")"
  if grep -q 'falling back' "$ROW_DIR/$1.xml.drv"; then u=0; e=fallback
  else u="$(grep -o 'gesture.uptime=[0-9]*' "$ROW_DIR/$1.xml.drv" | tail -1 | cut -d= -f2)"; e="$(grep -o 'gesture.dump.elapsed_ms=[0-9]*' "$ROW_DIR/$1.xml.drv" | tail -1 | cut -d= -f2)"; fi
  note "$1: line [${line#*] }] dump [$(node_text "$ROW_DIR/$1.xml" stopwatch_elapsed)] by the attempt at uptime ${u} taking ${e} ms; $(grep -c 'read no nodes' "$ROW_DIR/$1.xml.drv") empty gesture dumps before it, the whole retry window $(( t1 - t0 )) ms"
  printf '%s %s %s %s %s\n' "${l:-0}" "${d:-0}" "$(field_of "$line" uptime)" "${u:-0}" "${e:-fallback}"
}
moment_checks() { # label line_elapsed dump_elapsed line_uptime dump_uptime dump_latency
  if [ "$6" = fallback ] || [ -z "$6" ]; then
    _verdict FAIL "$1: the dump's elapsed = the line's + (dump − line) on the uptime clock (± 100 ms + half the dump's latency)" "no gesture-driver dump returned nodes; the plain-dump fallback has no timing of its own"
    assert_eq "$1: the dump's own latency <= 1 s (the gesture driver's dump.elapsed_ms)" yes "no (plain-dump fallback)"
    return
  fi
  assert_within "$1: the dump's elapsed = the line's + (dump − line) on the uptime clock (± 100 ms + half the dump's latency)" $(( $5 + $6 / 2 - $4 )) $(( $3 - $2 )) $(( $6 / 2 + 100 ))
  assert_eq "$1: the dump's own latency <= 1 s (the gesture driver's dump.elapsed_ms = $6)" yes "$([ "$6" -le 1000 ] && echo yes || echo no)"
}

open_clock stopwatch
gdump "$ROW_DIR/idle.xml"
assert_eq "the stopwatch starts at 00:00:00.00" "00:00:00.00" "$(node_text "$ROW_DIR/idle.xml" stopwatch_elapsed)"
MARK="$(ring_mark)"
gtap "$ROW_DIR/idle.xml" stopwatch_play; sleep 1
assert_contains "start is logged: [stopwatch] start elapsed=" "[stopwatch] start elapsed=" "$(ring_since "$MARK")"
assert_eq "the store says running" "true" "$(store_stopwatch | python3 -c 'import json,sys; print(str(json.load(sys.stdin)["running"]).lower())')"

# ---- two moments >= 10 s apart -----------------------------------------------------------------------------------------
sleep 4; MARK="$(ring_mark)"
read -r L1 D1 U1 DU1 E1 <<< "$(moment moment1 "$MARK")"
moment_checks "moment 1" "$L1" "$D1" "$U1" "$DU1" "$E1"
sleep 9; MARK="$(ring_mark)"
read -r L2 D2 U2 DU2 E2 <<< "$(moment moment2 "$MARK")"
moment_checks "moment 2" "$L2" "$D2" "$U2" "$DU2" "$E2"
assert_eq "the two moments are >= 10 s apart" yes "$([ $(( L2 - L1 )) -ge 10000 ] && echo yes || echo no)"
assert_within "Δelapsed = Δuptime ± 100 ms between the two lines" $(( U2 - U1 )) $(( L2 - L1 )) 100

# ---- Lap ------------------------------------------------------------------------------------------------------------------
CRASHES0="$(shell_crash_count)"
MARK="$(ring_mark)"
gtap "$ROW_DIR/moment2.xml" stopwatch_lap; sleep 1.5
if [ "$(shell_crash_count)" != "$CRASHES0" ]; then
  # PRODUCT DEFECT (E7/DEFECT.md): the lap row's check throws; the app is gone and the tab crashes on every open
  # while the lap is stored. The row stops here, as the brief says; the store is cleared only as the baseline restore.
  close_crash_dialog
  assert_eq "Lap: the app did not crash (DEFECT.md: $(shell_last_crash))" "$CRASHES0" "$(shell_crash_count)"
  log "      ROW STOPPED at the lap-row crash (E7/DEFECT.md); the kill -9 / reboot / Reset clauses were not run in this pass"
  stopwatch_store_reset
  assert_clock_empty "restore"
  row_end
  exit $?
fi
LAPLINE="$(ring_since "$MARK" | grep -F '[stopwatch] lap elapsed=' | head -1)"
assert_ne "Lap is logged: [stopwatch] lap elapsed=" "" "$LAPLINE"
gdump "$ROW_DIR/lap.xml"; screencap "$ROW_DIR/lap.png"
LAP1="$(node_text "$ROW_DIR/lap.xml" 'stopwatch_lap:1')"; note "stopwatch_lap:1 = [$LAP1]"
assert_ne "stopwatch_lap:1 is listed" "" "$LAP1"
LAPMS="$(field_of "$LAPLINE" elapsed)"
assert_eq "the lap row shows the split (index, lap time, split — equal for lap 1)" "1  $(python3 -c 'import sys; ms=int(sys.argv[1]); s=ms//1000; print("%02d:%02d:%02d.%02d" % (s//3600, (s//60)%60, s%60, (ms//10)%100))' "$LAPMS")" "$(echo "$LAP1" | cut -d' ' -f1-3 | sed 's/  */  /')"
assert_eq "the store holds one lap at that elapsed" "$LAPMS" "$(store_stopwatch | python3 -c 'import json,sys; l=json.load(sys.stdin)["laps"]; print(l[0] if len(l)==1 else l)')"

# ---- kill -9, reopen: still running, continuous ---------------------------------------------------------------------------
PRE="$(ring_since "$ROW_MARK")"; printf '%s\n' "$PRE" > "$ROW_DIR/ring-launcher-prekill.txt"
LAST_PRE="$(sw_lines "$PRE" | tail -1)"
kill9_shell >/dev/null
MARK="$(ring_mark)"
open_clock stopwatch; sleep 1.5
POST="$(ring_since "$MARK")"
FIRST_POST="$(sw_lines "$POST" | head -1)"; note "last pre-kill [${LAST_PRE#*] }] first post-kill [${FIRST_POST#*] }]"
assert_ne "after the kill the tab logs a [stopwatch] line" "" "$FIRST_POST"
assert_within "kill -9: first post-kill elapsed − last pre-kill elapsed = their wall difference ± 1 s" \
  $(( $(field_of "$FIRST_POST" wall) - $(field_of "$LAST_PRE" wall) )) $(( $(field_of "$FIRST_POST" elapsed) - $(field_of "$LAST_PRE" elapsed) )) 1000
gdump "$ROW_DIR/after_kill_a.xml"; sleep 2; gdump "$ROW_DIR/after_kill_b.xml"
assert_ne "kill -9: still running (the digits advance between two dumps)" "$(node_text "$ROW_DIR/after_kill_a.xml" stopwatch_elapsed)" "$(node_text "$ROW_DIR/after_kill_b.xml" stopwatch_elapsed)"
assert_eq "kill -9: the lap survived" yes "$(has_node "$ROW_DIR/after_kill_b.xml" 'stopwatch_lap:1')"

# ---- reboot: still running from the wall-clock start ---------------------------------------------------------------------
PRE="$(ring_since "$ROW_MARK")"; printf '%s\n' "$PRE" >> "$ROW_DIR/ring-launcher-prekill.txt"
LAST_PRE="$(sw_lines "$PRE" | tail -1)"
reboot_and_wait >/dev/null
assert_eq "after the reboot wake_device prints Awake (C-25)" "Awake" "$(wake_device)"
sleep 2
MARK="$(ring_mark)"
open_clock stopwatch; sleep 1.5
FIRST_POST="$(sw_lines "$(ring_since "$MARK")" | head -1)"; note "last pre-reboot [${LAST_PRE#*] }] first post-boot [${FIRST_POST#*] }]"
assert_ne "after the reboot the tab logs a [stopwatch] line" "" "$FIRST_POST"
assert_within "reboot: first post-boot elapsed − last pre-reboot elapsed = their wall difference ± 2 s" \
  $(( $(field_of "$FIRST_POST" wall) - $(field_of "$LAST_PRE" wall) )) $(( $(field_of "$FIRST_POST" elapsed) - $(field_of "$LAST_PRE" elapsed) )) 2000
gdump "$ROW_DIR/after_boot_a.xml"; sleep 2; gdump "$ROW_DIR/after_boot_b.xml"; screencap "$ROW_DIR/after_boot.png"
assert_ne "reboot: still running (the digits advance)" "$(node_text "$ROW_DIR/after_boot_a.xml" stopwatch_elapsed)" "$(node_text "$ROW_DIR/after_boot_b.xml" stopwatch_elapsed)"

# ---- Reset clears to 0:00.00 and the laps ------------------------------------------------------------------------------------
gtap "$ROW_DIR/after_boot_b.xml" stopwatch_play; sleep 1   # stop
gdump "$ROW_DIR/stopped.xml"
assert_eq "stopped: the Reset button is offered" yes "$(has_node "$ROW_DIR/stopped.xml" stopwatch_reset)"
MARK="$(ring_mark)"
gtap "$ROW_DIR/stopped.xml" stopwatch_reset; sleep 1.5
assert_contains "reset is logged" "[stopwatch] reset elapsed=0" "$(ring_since "$MARK")"
gdump "$ROW_DIR/reset.xml"; screencap "$ROW_DIR/reset.png"
assert_eq "reset: the digits read 00:00:00.00" "00:00:00.00" "$(node_text "$ROW_DIR/reset.xml" stopwatch_elapsed)"
assert_eq "reset: no lap listed" no "$(has_node "$ROW_DIR/reset.xml" 'stopwatch_lap:1')"
assert_eq "reset: the store is back at RESET (not running, no laps)" "false []" "$(store_stopwatch | python3 -c 'import json,sys; s=json.load(sys.stdin); print(str(s["running"]).lower(), s["laps"])')"
adb shell input keyevent KEYCODE_HOME; sleep 1
assert_clock_empty "restore"
row_end

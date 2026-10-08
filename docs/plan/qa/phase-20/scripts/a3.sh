#!/usr/bin/env bash
# Phase 20 A3 — The network changes mid-station.
#
#   1  station playing; `cmd netpolicy set metered-network AndroidWifi true` (build task 1's id form: the bare SSID)
#   2  airplane mode on; wait 20 s; airplane mode off
#   3  restore the netpolicy setting (`undefined`, the value it had) — and prove it
#
# Pass (a)–(c) as the phase doc lists them, with INDEX Change Log 2026-10-07's amendments: the netpolicy command exits
# 255 even when it worked, so the state is READ BACK; setting it drops Wi-Fi for about 6 s on this AVD, so (a)'s "still
# plays" is read AFTER the reconnect that step causes (the first sample at 15 s or later that is PLAYING).
# (b) and (c) are read from the ring slice that starts at the airplane-mode step (MARK2), so they are about THAT loss
# and not about step 1's.
#
# Changes on the device: the Wi-Fi network's metered override (set, then restored — also from the EXIT trap), airplane
# mode (on for 20 s, off — also from the EXIT trap). Fixtures up / down here.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p20.sh"
# The list prints the AVD's one SSID on two lines (two saved configurations of AndroidWifi); the first A3 run compared
# both lines with one value and failed three checks on its own reader — a driver fault, that row re-run. Distinct lines:
netpol() { adb shell cmd netpolicy list wifi-networks </dev/null | tr -d '\r' | grep . | sort -u | tr '\n' ' ' | sed 's/ $//'; }
defnet() { adb shell dumpsys connectivity </dev/null | tr -d '\r' | grep -m1 'Active default network' | xargs; }
wifi_caps() { adb shell dumpsys connectivity </dev/null | tr -d '\r' | grep -m1 -i 'NetworkAgentInfo.*WIFI' | grep -oE 'NOT_METERED|TEMPORARILY_NOT_METERED' | sort -u | tr '\n' ' '; }
a3_exit() {
  adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1
  adb shell cmd netpolicy set metered-network AndroidWifi undefined >/dev/null 2>&1
  fixtures_down
}
trap a3_exit EXIT
sample() { # label -> one line: track | metered | state
  d "$1"; echo "$(nt "$1" nowplaying_track) | $(nt "$1" nowplaying_metered) | $(sstate)"
}

p20_begin A3 "the network changes mid-station"
prefs_guard a b c
fixtures_up
baseline_start
NP0="$(netpol)"
record "the metered override before the row" "$NP0"
assert_eq "the Wi-Fi network's metered override starts at none" "AndroidWifi;none" "$NP0"

log "--- 0: a station playing (QA Jazz One), its now-playing page in front"
play_station "$J1" 25; assert_eq "QA Jazz One is PLAYING before the network is touched" "0 PLAYING" "$? $(sstate)"
sleep 4; d np-before; shot 00-playing-before
assert_yes "the now-playing page is in front" "$(has np-before nowplaying_root)"
assert_eq "no metered line before the override" "no" "$(has np-before nowplaying_metered)"
record "before: track | metered | state" "$(sample np-before)"

# ----------------------------------------------------------------------------------------------- 1: metered
log "--- 1: cmd netpolicy set metered-network AndroidWifi true"
letter a
MARK1="$(ring_mark)"
adb shell cmd netpolicy set metered-network AndroidWifi true > "$ROW_DIR/netpolicy-set.out" 2>&1; echo $? > "$ROW_DIR/netpolicy-set.rc"
record "(a) the command's exit code (255 even when it works) / the state read back" "$(cat "$ROW_DIR/netpolicy-set.rc") / $(netpol)"
assert_eq "(a) the override is set (read back)" "AndroidWifi;true" "$(netpol)"
A_OK=""; A_LINE=""; T0=$(date +%s)
for t in 5 10 15 20 25 35 45; do
  while [ $(( $(date +%s) - T0 )) -lt "$t" ]; do sleep 0.5; done
  S="$(sample "metered-$t")"; log "      metered t+$t s: $S"
  if [ "$t" -ge 15 ] && [ "${S##* | }" = PLAYING ] && [ -z "$A_OK" ]; then A_OK="$t"; A_LINE="$S"; shot 01-metered; break; fi
done
[ -n "$A_OK" ] || shot 01-metered
record "(a) the sample read after that step's reconnect (t+${A_OK:-none} s): track | metered | state" "${A_LINE:-$S}"
LAST="metered-${A_OK:-45}"
assert_eq "(a) nowplaying_metered reads \"Streaming over mobile data\"" "Streaming over mobile data" "$(nt "$LAST" nowplaying_metered)"
assert_eq "(a) …and it still plays (after the reconnect the override itself causes)" "PLAYING" "$(sstate)"
mring "$MARK1" > "$ROW_DIR/slice-metered.txt"
record "(a) the [music] stream lines of this step" "$(grep -F 'stream:' "$ROW_DIR/slice-metered.txt" | stripped | cut -c1-110 | tr '\n' ';')"

# ----------------------------------------------------------------------------------------------- 2: airplane mode
log "--- 2: airplane mode on; wait 20 s; airplane mode off"
letter b
MARK2="$(ring_mark)"; T0=$(date +%s)
adb shell cmd connectivity airplane-mode enable
RECON=no
for t in 6 12 18; do
  while [ $(( $(date +%s) - T0 )) -lt "$t" ]; do sleep 0.5; done
  S="$(sample "airplane-$t")"; log "      airplane t+$t s: $S"
  [ "${S%% | *}" = "Reconnecting…" ] && RECON=yes
  [ "$t" = 12 ] && shot 02-reconnecting
done
record "(b) airplane mode while waiting" "$(airplane)"
assert_yes "(b) nowplaying_track reads \"Reconnecting…\" while the network is gone" "$RECON"
while [ $(( $(date +%s) - T0 )) -lt 20 ]; do sleep 0.5; done
LOST="$(mring "$MARK2" | grep -F 'stream: lost, retrying' | head -1 | stripped)"
record "(b) the line" "$LOST"
assert_contains "(b) [music] stream: lost, retrying is in the slice (since airplane mode went on)" "[music] stream: lost, retrying" "$LOST"

letter c
adb shell cmd connectivity airplane-mode disable; T1=$(date +%s)
BACK=""
for t in 5 10 15 20 30 40 50 60; do
  while [ $(( $(date +%s) - T1 )) -lt "$t" ]; do sleep 0.5; done
  S="$(sample "back-$t")"; log "      back t+$t s: $S"
  if [ "${S##* | }" = PLAYING ] && [ "${S%% | *}" != "Reconnecting…" ]; then BACK="$t"; break; fi
done
shot 03-playing-again
mring "$MARK2" > "$ROW_DIR/slice-airplane.txt"
record "(c) playing again at (no tap, no key between airplane-off and this read)" "back t+${BACK:-never} s: $S"
assert_eq "(c) playing again with no tap" "PLAYING" "$(sstate)"
assert_ne "(c) …and the page no longer reads Reconnecting…" "Reconnecting…" "$(nt "back-${BACK:-60}" nowplaying_track)"
RECO="$(grep -F 'stream: reconnected after' "$ROW_DIR/slice-airplane.txt" | tail -1 | stripped)"
record "(c) the line" "$RECO"
assert_contains "(c) [music] stream: reconnected after is in the slice" "[music] stream: reconnected after" "$RECO"
record "the [music] stream lines since airplane mode went on" "$(grep -F 'stream:' "$ROW_DIR/slice-airplane.txt" | stripped | cut -c1-110 | tr '\n' ';')"
session_save session-after-reconnect

# ----------------------------------------------------------------------------------------------- 3: restore
log "--- 3: restore the netpolicy setting (undefined) and prove it"
letter restore
adb shell cmd netpolicy set metered-network AndroidWifi undefined > "$ROW_DIR/netpolicy-restore.out" 2>&1; echo $? > "$ROW_DIR/netpolicy-restore.rc"
sleep 14
record "the restore's exit code / the state read back / Wi-Fi's capability" "$(cat "$ROW_DIR/netpolicy-restore.rc") / $(netpol) / $(wifi_caps)"
assert_eq "the metered override is back at none (read back)" "AndroidWifi;none" "$(netpol)"
assert_contains "…and the Wi-Fi network is NOT_METERED again" "NOT_METERED" "$(wifi_caps)"
assert_eq "airplane mode is off" "0" "$(airplane)"
wait_network; assert_eq "the emulator reaches the radio fixture after the restore" "0" "$?"
sleep 6; d np-after-restore; shot 04-after-restore
record "after the restore: track | metered | state" "$(nt np-after-restore nowplaying_track) | $(nt np-after-restore nowplaying_metered) | $(sstate)"

rings_save
adb shell am force-stop app.tileshell; sleep 1; adb shell input keyevent KEYCODE_HOME; sleep 3
fixtures_down
flog "$RLOG.jsonl" 'True' > "$ROW_DIR/radio-requests.txt"
ensure_start
p20_end a b c

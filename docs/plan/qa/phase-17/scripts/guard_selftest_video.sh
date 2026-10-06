#!/usr/bin/env bash
# Phase 17 — the egress guard's first run and its control (C-29; r3 V1, V2; the QA brief: "prove it can fail with one
# control packet inside a throwaway guard span before you rely on it, and report exactly what it did").
#
#   span A   egress_guard_on; what it did is recorded (the uid, adbd's uid, the OUTPUT chain, the location grants); one
#            packet from the shell's uid to 10.0.2.2 (allowed: the count stays 0); a restart of the shell inside the
#            span (the weather refresh ends with no coordinates, no fetch); egress_guard_off passes
#   span B   egress_guard_on; ONE control packet from the shell's uid to 10.0.2.3 (a TCP connect and a ping): the
#            rule's count is ≥ 1 and egress_guard_off's own zero-assertion FAILS — counted here by hand and taken back,
#            so the row stays green; the rule is gone, adbd is back to the shell uid, location is granted back
#
# Needs adb root for each span (the guard's own), undone inside the script. Changes nothing else.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p17.sh"
. "$HERE/p17_video.sh"
trap video_cleanup EXIT

video_row_begin GUARD_SELF "the egress guard: what it does, and that one packet elsewhere fails it"
record "before: adbd's uid" "$(adb shell id -u | tr -d '\r')"
record "before: location grants (coarse fine)" "$(perm_granted ACCESS_COARSE_LOCATION) $(perm_granted ACCESS_FINE_LOCATION)"
as_app() { adb shell "su $APP_UID $1" 2>&1 | tr -d '\r' | tail -2 | tr '\n' ' '; }

log "--- span A: nothing but 10.0.2.2"
guard_on
record "A: the shell's uid the rule names" "$APP_UID"
record "A: location grants inside the span (coarse fine)" "$(perm_granted ACCESS_COARSE_LOCATION) $(perm_granted ACCESS_FINE_LOCATION)"
adb shell iptables -L OUTPUT -v -n | tr -d '\r' > "$D/A-output-chain.txt"
record "A: the guard's rule in the OUTPUT chain" "$(grep -F "owner UID match $APP_UID" "$D/A-output-chain.txt" | xargs)"
record "A: a ping from the shell's uid to 10.0.2.2" "$(as_app 'ping -c 1 -W 2 10.0.2.2')"
assert_eq "A: a packet to 10.0.2.2 is not counted" "0" "$(egress_guard_count)"
guard_restart "A"
record "A: the launcher's [weather] lines since the row began" "$(ring_since "$ROW_MARK" launcher | grep -F '[weather]' | sed 's/.*\[weather\]/[weather]/' | sort | uniq -c | tr '\n' '|')"
assert_eq "A: the shell's own restart sent nothing elsewhere" "0" "$(egress_guard_count)"
guard_off

log "--- span B: one control packet to 10.0.2.3"
guard_on
record "B: a TCP connect from the shell's uid to 10.0.2.3:80" "$(as_app 'toybox nc -w 2 10.0.2.3 80 </dev/null'; echo "rc=$?")"
N1="$(egress_guard_count)"
record "B: the rule's count after the TCP connect" "$N1"
record "B: a ping from the shell's uid to 10.0.2.3" "$(as_app 'ping -c 1 -W 2 10.0.2.3')"
N2="$(egress_guard_count)"
record "B: the rule's count after the ping as well" "$N2"
assert_eq "B: the control packet IS counted (the count is ≥ 1)" "yes" "$([ "${N2:-0}" -ge 1 ] 2>/dev/null && echo yes || echo no)"
BEFORE=$FAIL
log "(the next FAIL line is the control: egress_guard_off's own zero-assertion, counted by hand)"
guard_off > /dev/null 2>&1
if [ "$FAIL" -eq $((BEFORE + 1)) ]; then FAIL=$BEFORE; _verdict PASS "B: egress_guard_off FAILS on the counted packet (control)" "it counted one FAIL, taken back"
else _verdict FAIL "B: egress_guard_off FAILS on the counted packet (control)" "it counted $((FAIL - BEFORE)) FAILs, wanted exactly 1"; fi
assert_absent "after: no rule of the guard is left" "owner UID match $APP_UID" "$(adb shell iptables -L OUTPUT -v -n 2>&1 | tr -d '\r')"
record "after: adbd's uid" "$(adb shell id -u | tr -d '\r')"
record "after: location grants (coarse fine)" "$(perm_granted ACCESS_COARSE_LOCATION) $(perm_granted ACCESS_FINE_LOCATION)"
assert_eq "after: adbd is the shell uid again" "2000" "$(adb shell id -u | tr -d '\r')"
c6; ensure_start
row_end

#!/usr/bin/env bash
# EDGE — the phase doc's Edge Cases list, run as a list (the re-judge, R2-1; the skill's gate rule and Hard Rule 5).
# Each bullet the other rows do not already carry is a sub-step here; EDGE/EDGE.txt's index (written by this row, from
# edge_index.tsv) maps EVERY bullet to its evidence: a sub-step of this row, another row or JVM test by name, or a
# recorded NEEDS-HUMAN / phone row. Each sub-step starts from a plain Start and takes its MARK just before its action.
. "$(dirname "$0")/lib.sh"; . "$(dirname "$0")/q.sh"
row_begin EDGE-callsmoke "the call sub-step only (smoke of the answer retry): the Edge Cases list: flip, fling, call, keyguard, listener restart, process death, second finger, show more tiles, folder dissolve, labels, theme, transparency, RV10"
seed_fixtures
restore baseline_layout.json

# fresh_burst [tile id] [dump name]: hold the tile (default the fixture) from a plain Start and release; the dump is the burst.
fresh_burst() {
  local id="${1:-tile:$A_KEY}" out="$ROW_DIR/${2:-B}.xml"
  qdump "$ROW_DIR/.rest.xml"
  read -r FX FY <<< "$(center "$ROW_DIR/.rest.xml" "$id")"
  hold "$FX" "$FY" 1.0
  qdump "$out"
  [ "$(has_node "$out" quick_burst)" = yes ] || note "fresh_burst: no burst on $id"
}
sat_rects() { # dump -> "id l t r b" per satellite square and label
  python3 - "$1" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
for m in re.finditer(r'resource-id="(quick_sat(?:_label)?:\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    print(m.group(1), *m.groups()[1:])
PY
}
epx_same() { # base.xml base_width other.xml other_width -> "PASS <max d>" or "FAIL <why>": every satellite rect in epx within 1.0
  python3 - "$@" <<'PY'
import re, sys
def load(fn, w):
    s = open(fn).read(); k = int(w) / 360.0
    return {m.group(1): tuple(int(v) / k for v in m.groups()[1:])
            for m in re.finditer(r'resource-id="(quick_sat(?:_label)?:\d)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)}
a, b = load(sys.argv[1], sys.argv[2]), load(sys.argv[3], sys.argv[4])
if not a: print("FAIL no satellites in the base"); sys.exit()
if set(a) != set(b): print("FAIL ids differ", sorted(a), sorted(b)); sys.exit()
d = max(max(abs(x - y) for x, y in zip(a[k], b[k])) for k in a)
print(("PASS" if d <= 1.0 else "FAIL") + f" max |d| = {d:.2f} epx over {len(a)} rects")
PY
}
# The emulator's touchscreen (virtio multi-touch, slotted protocol B; adb's shell is root on this AVD), for what
# `input` cannot do: a second pointer, and a press landing within milliseconds of a fling.
TS=/dev/input/event2
mt_x() { echo $(( $1 * 32767 / 1080 )); }
mt_y() { echo $(( $1 * 32767 / 2340 )); }
# The device has an ABS_MT_PRESSURE axis and no BTN_TOUCH key: a contact with pressure 0 reads as hovering, so every
# down carries a pressure and a touch-major (the first run of this row sent none, and nothing touched).
mt_down() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 $(( 100 + $1 )); sendevent $TS 3 53 $(mt_x "$2"); sendevent $TS 3 54 $(mt_y "$3"); sendevent $TS 3 58 512; sendevent $TS 3 48 8; sendevent $TS 0 0 0;"; }
mt_move() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 53 $(mt_x "$2"); sendevent $TS 3 54 $(mt_y "$3"); sendevent $TS 3 58 512; sendevent $TS 0 0 0;"; }
mt_up() { echo "sendevent $TS 3 47 $1; sendevent $TS 3 57 4294967295; sendevent $TS 0 0 0;"; }

log "--- the burst open at an incoming call: ringing is a heads-up over Start; answering brings the call in front (stop) ---"
# The Decision's rule is "Start stopping closes the burst (stop)". An incoming call on an unlocked phone rings as a
# heads-up: Start stays resumed and the burst stays, as under any notification; the call's own window in front (answer
# it, or a full-screen ring on a locked phone) stops Start and closes it (first run of this sub-step, 2026-09-24).
fresh_burst
MARK="$(ring_mark)"
adb emu gsm call 5551234 >/dev/null 2>&1; sleep 4
qdump "$ROW_DIR/call-ringing.xml"; screencap "$ROW_DIR/call-ringing.png"
assert_contains "ringing: a heads-up (its Answer button is on screen)" "ANSWER" "$(grep -o 'text="ANSWER"' "$ROW_DIR/call-ringing.xml")"
assert_contains "ringing: Start is still the resumed activity" "app.tileshell/.StartActivity" "$(resumed)"
assert_absent "ringing: nothing closed the burst" "burst closed" "$(quick_since "$MARK")"
# Answer from the heads-up (the modem's accept and KEYCODE_CALL answer without bringing the call's window up): a fresh dump
# before each tap, up to 3, until the call's window is in front (the round-2 smoke's first tap left the call ringing out).
for try in 1 2 3; do
  qdump "$ROW_DIR/call-answer-$try.xml"
  read -r AX AY <<< "$(text_xy "$ROW_DIR/call-answer-$try.xml" ANSWER)"
  [ -n "$AX" ] || { note "answer try $try: no ANSWER on screen"; break; }
  tap_xy "$AX" "$AY"; sleep 3
  note "answer try $try at ($AX,$AY): top $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//')"
  resumed | grep -q InCallActivity && break
done
sleep 1
screencap "$ROW_DIR/call-answered.png"
note "answered: top activity $(adb shell dumpsys activity activities | grep -m1 topResumedActivity | sed 's/.*u0 //;s/ .*//')"
assert_absent "answered: the call's window is in front of Start" "app.tileshell/.StartActivity" "$(resumed)"
assert_contains "answered: Start stopped, the burst closed (stop)" "burst closed: stop" "$(quick_since "$MARK")"
adb emu gsm cancel 5551234 >/dev/null 2>&1; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 2
c6

row_end

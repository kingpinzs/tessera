#!/usr/bin/env bash
# L14-1 fix — the edge cases around the "Unlock to continue" card's Unlock button (fix plan
# review/2026-09-30-L14-1-fix-plan.md; Jeremy's Q1 (a): Tess steps aside for the PIN pad; Q2 (a): the tap keeps the card
# and says nothing new). E8 is the happy path; these are the rest:
#   A  Back on the PIN pad: Tess back over the keyguard with the SAME card, silent, nothing run; Unlock again + PIN runs it.
#   B  phase 03 E10's clause form ("Open Clock." → the card → Unlock → PIN → DeskClock resumes), a wrong PIN first:
#      the PIN pad stays, nothing runs until the right PIN.
#   C  the screen going off while the PIN pad is up: observed and recorded (what Tess holds after the wake), then the
#      invariant — nothing runs that the user did not unlock for.
# Spoken through the emulator's gRPC audio route only (never host audio).
set -uo pipefail
export AUDIO_ROUTE=emu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin L14-1-EDGES "the Unlock button's edge cases: Back on the PIN pad, a wrong PIN, the screen off on the PIN pad"
require_emu_audio || { row_end; exit 1; }
PIN=1234
pin_set=no
restore() {
  [ "$pin_set" = yes ] && adb shell locksettings clear --old $PIN >/dev/null 2>&1
  adb shell locksettings set-disabled true >/dev/null 2>&1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  adb shell wm dismiss-keyguard >/dev/null 2>&1
  echo "      restored: PIN cleared, lock screen disabled ($(adb shell locksettings get-disabled | tr -d '\r')), awake" >> "$LOG"
}
trap restore EXIT
keyguard() { adb shell dumpsys window | grep -m1 -oE 'isKeyguardShowing=(true|false)'; }
pin_pad() { grep -qE 'resource-id="com.android.systemui:id/(pinEntry|keyguard_pin_view|pin_view)"' "$1" && echo yes || echo no; }
DOORS="I'm afraid I can't do that, Dave."

# Locked, Tess over the keyguard, one spoken request, its Unlock card on screen (E8's form). $1 utterance, $2 label.
locked_card() {
  local id="$1" label="$2" i
  ensure_start
  adb shell input keyevent KEYCODE_SLEEP; sleep 2
  adb shell input keyevent KEYCODE_WAKEUP; sleep 3
  assert_eq "$label: the keyguard is showing" "isKeyguardShowing=true" "$(keyguard)"
  cortana_assist; sleep 5
  dump_ui "$ROW_DIR/$label-00-tess.xml"
  if [ "$(has_node "$ROW_DIR/$label-00-tess.xml" cortana_session)" != yes ]; then
    adb shell cmd voiceinteraction show >/dev/null 2>&1; sleep 5
  fi
  voice_step "$id" 11 "$label"
  for i in 1 2 3 4 5; do
    dump_ui "$ROW_DIR/$label-01-card.xml"
    [ "$(has_node "$ROW_DIR/$label-01-card.xml" cortana_card_button:unlock)" = yes ] && break
    sleep 2
  done
  assert_eq "$label: the Unlock card" "yes" "$(has_node "$ROW_DIR/$label-01-card.xml" cortana_card:unlock)"
  assert_eq "$label: reply_since MARK" "Unlock your phone to continue." "$VS_REPLY"
}

adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell locksettings set-pin $PIN >/dev/null 2>&1 && pin_set=yes
assert_eq "a PIN is set" "yes" "$pin_set"

# ---- A: Back on the PIN pad, then Unlock again -----------------------------------------------------------------------
locked_card pod_bay_doors A
MA="$(ring_mark)"
tap_node "$ROW_DIR/A-01-card.xml" cortana_card_button:unlock; sleep 3
dump_ui "$ROW_DIR/A-02-pin-pad.xml"
assert_eq "A: the Unlock button shows the PIN pad" "yes" "$(pin_pad "$ROW_DIR/A-02-pin-pad.xml")"
assert_eq "A: Tess has stepped aside" "no" "$(has_node "$ROW_DIR/A-02-pin-pad.xml" cortana_session)"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump_ui "$ROW_DIR/A-03-after-back.xml"; screencap "$ROW_DIR/A-03-after-back.png"
s="$(ring_since "$MA")"; printf '%s\n' "$s" > "$ROW_DIR/A-03-slice.txt"
assert_eq "A: after Back the keyguard is still showing" "isKeyguardShowing=true" "$(keyguard)"
assert_eq "A: Tess is back" "yes" "$(has_node "$ROW_DIR/A-03-after-back.xml" cortana_session)"
assert_eq "A: with the Unlock card" "yes" "$(has_node "$ROW_DIR/A-03-after-back.xml" cortana_card:unlock)"
assert_eq "A: its caption still restates the request" "Open the pod bay" "$(node_text "$ROW_DIR/A-03-after-back.xml" cortana_card_caption)"
assert_contains "A: the unlock was cancelled" "unlock bridge: cancelled" "$s"
assert_contains "A: the request stays pending" "unlock cancelled; the pending request stays on the card" "$s"
assert_eq "A: nothing new was said (Q2 (a))" "" "$(reply_since "$MA")"
assert_absent "A: nothing ran" "[podbay] opened" "$s"
assert_absent "A: the session was not hidden" "session hidden" "$s"
note "A: listening after Back: $(has_node "$ROW_DIR/A-03-after-back.xml" cortana_listening_box)"
MA2="$(ring_mark)"
tap_node "$ROW_DIR/A-03-after-back.xml" cortana_card_button:unlock; sleep 3
dump_ui "$ROW_DIR/A-04-pin-pad-again.xml"
assert_eq "A: the second Unlock shows the PIN pad" "yes" "$(pin_pad "$ROW_DIR/A-04-pin-pad-again.xml")"
adb shell input text $PIN
adb shell input keyevent KEYCODE_ENTER
sleep 20
s="$(ring_since "$MA2")"; printf '%s\n' "$s" > "$ROW_DIR/A-05-slice.txt"
assert_eq "A: unlocked" "isKeyguardShowing=false" "$(keyguard)"
assert_eq "A: the first reply after the second Unlock is the doors line" "$DOORS" "$(reply_since "$MA2")"
assert_contains "A: the pod bay opens" "[podbay] opened by voice (doors)" "$s"
dump_ui "$ROW_DIR/A-06-after.xml"
assert_eq "A: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/A-06-after.xml" pod_bay)"
adb shell input keyevent KEYCODE_BACK; sleep 1.5

# ---- B: E10's clause form with a wrong PIN first ---------------------------------------------------------------------
locked_card open_clock B
# The name is the matcher's (lower case: run 1 read "Open clock"); no row pins its case, so it is compared without it.
assert_eq "B: the caption restates the request (case aside)" "open clock" "$(node_text "$ROW_DIR/B-01-card.xml" cortana_card_caption | tr 'A-Z' 'a-z')"
MB="$(ring_mark)"
tap_node "$ROW_DIR/B-01-card.xml" cortana_card_button:unlock; sleep 3
dump_ui "$ROW_DIR/B-02-pin-pad.xml"
assert_eq "B: the Unlock button shows the PIN pad" "yes" "$(pin_pad "$ROW_DIR/B-02-pin-pad.xml")"
adb shell input text 0000
adb shell input keyevent KEYCODE_ENTER
sleep 3
dump_ui "$ROW_DIR/B-03-wrong-pin.xml"
s="$(ring_since "$MB")"; printf '%s\n' "$s" > "$ROW_DIR/B-03-slice.txt"
assert_eq "B: a wrong PIN keeps the keyguard" "isKeyguardShowing=true" "$(keyguard)"
assert_eq "B: and the PIN pad" "yes" "$(pin_pad "$ROW_DIR/B-03-wrong-pin.xml")"
assert_absent "B: no result delivered yet (dismissed)" "unlock bridge: dismissed" "$s"
assert_absent "B: no result delivered yet (cancelled)" "unlock bridge: cancelled" "$s"
assert_absent "B: nothing ran" "unlocked; running the pending" "$s"
adb shell input text $PIN
adb shell input keyevent KEYCODE_ENTER
sleep 8
s="$(ring_since "$MB")"; printf '%s\n' "$s" > "$ROW_DIR/B-04-slice.txt"
screencap "$ROW_DIR/B-04-after.png"
assert_eq "B: unlocked" "isKeyguardShowing=false" "$(keyguard)"
assert_contains "B: the request that was on the card runs" "unlocked; running the pending OpenApp" "$s"
assert_contains "B: DeskClock resumes (E10's check)" "deskclock" "$(top_activity | tr 'A-Z' 'a-z')"
adb shell input keyevent KEYCODE_HOME; sleep 2

# ---- C: the screen off while the PIN pad is up -----------------------------------------------------------------------
locked_card pod_bay_doors C
MC="$(ring_mark)"
tap_node "$ROW_DIR/C-01-card.xml" cortana_card_button:unlock; sleep 3
dump_ui "$ROW_DIR/C-02-pin-pad.xml"
assert_eq "C: the Unlock button shows the PIN pad" "yes" "$(pin_pad "$ROW_DIR/C-02-pin-pad.xml")"
adb shell input keyevent KEYCODE_SLEEP; sleep 3
adb shell input keyevent KEYCODE_WAKEUP; sleep 3
dump_ui "$ROW_DIR/C-03-after-wake.xml"; screencap "$ROW_DIR/C-03-after-wake.png"
s="$(ring_since "$MC")"; printf '%s\n' "$s" > "$ROW_DIR/C-03-slice.txt"
assert_eq "C: after the wake the keyguard is showing" "isKeyguardShowing=true" "$(keyguard)"
record "C: the unlock result after the screen went off" "$(printf '%s\n' "$s" | grep -oE 'unlock bridge: [a-z ]+' | tr '\n' ';')"
record "C: the session after the wake" "shown=$(session_window) card=$(has_node "$ROW_DIR/C-03-after-wake.xml" cortana_card:unlock) hidden-line=$(printf '%s\n' "$s" | grep -c 'session hidden')"
assert_absent "C: nothing ran before any unlock" "[podbay] opened" "$s"
assert_absent "C: nothing ran before any unlock (model)" "unlocked; running the pending" "$s"
# The user unlocks from the lock screen (not from the card): wm dismiss-keyguard to the PIN pad, the PIN.
adb shell wm dismiss-keyguard; sleep 2
adb shell input text $PIN
adb shell input keyevent KEYCODE_ENTER
sleep 15
s="$(ring_since "$MC")"; printf '%s\n' "$s" > "$ROW_DIR/C-04-slice.txt"
dump_ui "$ROW_DIR/C-04-after-unlock.xml"; screencap "$ROW_DIR/C-04-after-unlock.png"
assert_eq "C: unlocked" "isKeyguardShowing=false" "$(keyguard)"
record "C: after the user's own unlock" "ran=$(printf '%s\n' "$s" | grep -c 'unlocked; running the pending') podbay=$(printf '%s\n' "$s" | grep -c '\[podbay\] opened') nothing-pending=$(printf '%s\n' "$s" | grep -c 'unlocked with nothing pending') top=$(top_activity)"
# The invariant: the screen going off ended the unlock the card asked for, so the user's own unlock runs nothing (run 1:
# the dismiss was cancelled and the session hid, dropping the request — H12).
assert_absent "C: the user's own unlock runs nothing (model)" "unlocked; running the pending" "$s"
assert_absent "C: the user's own unlock runs nothing (pod bay)" "[podbay] opened" "$s"

restore
pin_set=no
trap - EXIT
adb shell input keyevent KEYCODE_BACK; sleep 1.5
ensure_start
RINGS="launcher speech" row_end

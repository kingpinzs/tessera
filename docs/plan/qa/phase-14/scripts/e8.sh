#!/usr/bin/env bash
# Phase 14 E8 — the phrase over the keyguard: OpenPodBay is gated (phase 03's PQ3 rule): the "Unlock to continue" card
# restates "Open the pod bay", Tess says "Unlock your phone to continue.", the keyguard stays and nothing opens; after
# the Unlock button and the PIN, the same request continues — the doors line is spoken THEN and the pod bay opens (E6's
# checks on that slice). The lock screen is the row's point, so the wake is KEYCODE_WAKEUP alone (C-25's exception).
set -uo pipefail
export AUDIO_ROUTE=emu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HERE/lib.sh"
. "$HERE/p14.sh"

row_begin E8 "locked: gated with its caption, then unlocked, the doors line and the pane"
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
awake() { adb shell dumpsys power | grep -m1 'mWakefulness=' | tr -d '\r '; }
DOORS="I'm afraid I can't do that, Dave."

voice_volume_up
ensure_start
adb shell locksettings set-disabled false >/dev/null 2>&1
adb shell locksettings set-pin $PIN >/dev/null 2>&1 && pin_set=yes
assert_eq "a PIN is set" "yes" "$pin_set"
assert_eq "the lock screen is enabled" "false" "$(adb shell locksettings get-disabled | tr -d '\r')"
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 3
assert_eq "awake (KEYCODE_WAKEUP alone, the keyguard kept)" "mWakefulness=Awake" "$(awake)"
assert_eq "the keyguard is showing" "isKeyguardShowing=true" "$(keyguard)"

# Tess over the keyguard: phase 03 e10.sh's lock_and_open form (KEYCODE_ASSIST, then `cmd voiceinteraction show`).
cortana_assist
sleep 5
dump_ui "$ROW_DIR/01-tess-locked.xml"
if [ "$(has_node "$ROW_DIR/01-tess-locked.xml" cortana_session)" != yes ]; then
  adb shell cmd voiceinteraction show >/dev/null 2>&1
  sleep 5
  dump_ui "$ROW_DIR/01-tess-locked.xml"
fi
assert_eq "Tess is open over the keyguard" "yes" "$(has_node "$ROW_DIR/01-tess-locked.xml" cortana_session)"
note "listening already (the locked session opens listening): $(has_node "$ROW_DIR/01-tess-locked.xml" cortana_listening_box)"

voice_step pod_bay_doors 11 locked
assert_contains "locked: the match" "-> OpenPodBay(doors=true)" "$(match_line "$VS_SLICE")"
dump_ui "$ROW_DIR/02-card.xml"
screencap "$ROW_DIR/02-card.png"
assert_eq "locked: the Unlock card" "yes" "$(has_node "$ROW_DIR/02-card.xml" cortana_card:unlock)"
assert_eq "locked: its caption restates the request" "Open the pod bay" "$(node_text "$ROW_DIR/02-card.xml" cortana_card_caption)"
assert_eq "locked: reply_since MARK" "Unlock your phone to continue." "$VS_REPLY"
assert_eq "locked: the keyguard is still showing" "isKeyguardShowing=true" "$(keyguard)"
assert_absent "locked: no [podbay] opened" "[podbay] opened" "$(ring_since "$VS_MARK")"

MARK2="$(ring_mark)"
tap_node "$ROW_DIR/02-card.xml" cortana_card_button:unlock
sleep 3
assert_eq "the Unlock button raises the keyguard" "isKeyguardShowing=true" "$(keyguard)"
# L14-1 (fixed 2026-09-30): the Unlock button itself brings up the PIN pad, with Tess stepped aside. Runs 1-3 fell back
# to `wm dismiss-keyguard` here because the button's own request was cancelled; that fallback is gone — E8's text has
# none, and it would pass a Unlock button that shows no PIN pad. (The PNG is black: the PIN pad blocks screen capture.)
dump_ui "$ROW_DIR/03-after-unlock-button.xml"
screencap "$ROW_DIR/03-after-unlock-button.png"
assert_eq "the Unlock button shows the PIN pad" "yes" "$(grep -qE 'resource-id="com.android.systemui:id/(pinEntry|keyguard_pin_view|pin_view)"' "$ROW_DIR/03-after-unlock-button.xml" && echo yes || echo no)"
assert_eq "Tess has stepped aside for it" "no" "$(has_node "$ROW_DIR/03-after-unlock-button.xml" cortana_session)"
adb shell input text $PIN
adb shell input keyevent KEYCODE_ENTER
sleep 20
s="$(ring_since "$MARK2")"; printf '%s\n' "$s" > "$ROW_DIR/03-unlock-slice.txt"
assert_eq "unlocked" "isKeyguardShowing=false" "$(keyguard)"
assert_eq "after unlock: reply_since MARK2 is the doors line" "$DOORS" "$(reply_since "$MARK2")"
uid="$(printf '%s\n' "$s" | grep -F '[speech] speak[' | grep -F "text=\"$DOORS\"" | head -1 | sed -E 's/.*speak\[([^]]+)\].*/\1/')"
done_wall="$(wall_of_first "$s" "[speech] speaking done $uid cancelled=false")"
open_wall="$(wall_of_first "$s" "[podbay] opened by voice (doors)")"
note "after unlock: utterance $uid speaking done wall=$done_wall; opened by voice (doors) wall=$open_wall"
assert_ne "after unlock: [podbay] opened by voice (doors)" "" "$open_wall"
assert_eq "after unlock: the pane opened AFTER the reply was spoken" "yes" "$([ -n "$done_wall" ] && [ -n "$open_wall" ] && [ "$open_wall" -gt "$done_wall" ] && echo yes || echo no)"
assert_eq "after unlock: no session window" "no" "$(session_window)"
dump_ui "$ROW_DIR/04-after.xml"
assert_eq "after unlock: the dump shows pod_bay" "yes" "$(has_node "$ROW_DIR/04-after.xml" pod_bay)"
screencap "$ROW_DIR/04-after.png"

restore
pin_set=no
trap - EXIT
adb shell input keyevent KEYCODE_BACK
sleep 1.5
ensure_start
voice_volume_restore
RINGS="launcher speech" row_end
